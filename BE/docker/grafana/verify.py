"""실제 대시보드 지연·표본 수 쿼리의 무호출 처리와 집계 의미를 promtool로 검증한다."""

import json
from pathlib import Path
import subprocess
import tempfile


DIRECTORY = Path(__file__).resolve().parent
IMAGE = "prom/prometheus:v3.14.0"
FAMILIES = {
    "A": ("spring_data_repository_invocations_seconds", {"repository": "FixtureRepository", "method": "find"}),
    "B": ("pheeeew_region_query_seconds", {"operation": "intersecting_regions"}),
    "C": ("pheeeew_emotion_press_query_seconds", {"operation": "increase"}),
}


def fixture(prefix, dimensions, environment, outcome, instance, count, total, lower_bucket):
    labels = {"job": "pheeeew-api", "environment": environment, "instance": instance, **dimensions}
    labels["state" if "repository" in dimensions else "outcome"] = outcome
    series = []
    for suffix, step, extra in [("count", count, {}), ("sum", total, {}),
                                ("bucket", lower_bucket, {"le": "1.0"}),
                                ("bucket", count, {"le": "3.0"}),
                                ("bucket", count, {"le": "300.0"}),
                                ("bucket", count, {"le": "+Inf"})]:
        if "repository" in dimensions and extra.get("le") == "300.0":
            continue
        tag = ",".join(f'{key}="{value}"' for key, value in (labels | extra).items())
        series.append({"series": f"{prefix}_{suffix}{{{tag}}}", "values": f"0+{step}x10"})
    return series


def sample_count_tests(panel, environment):
    assert panel["type"] == "stat"
    assert panel["fieldConfig"]["defaults"]["noValue"] == "데이터 없음"
    assert panel["options"]["reduceOptions"]["calcs"] == ["last"]
    tests = []
    for target, ref in zip(panel["targets"], ("B", "C"), strict=True):
        assert target["instant"] and not target["range"]
        assert target["interval"] == "1m"
        prefix, dimensions = FAMILIES[ref]
        for scenario in ("absent", "idle", "observed", "errors_only", "single_sample", "gap", "partial_gap"):
            series, expected = [], []
            if scenario != "absent":
                args = (prefix, dimensions, environment)
                series += fixture(*args, "success", "a", 1 if scenario == "observed" else 0, 0, 0)
                series += fixture(*args, "success", "b", 3 if scenario == "observed" else 0, 0, 0)
                series += fixture(*args, "error", "failed", 2 if scenario in ("observed", "errors_only") else 0, 0, 0)
                if scenario in ("single_sample", "gap"):
                    for item in series:
                        item["values"] = "_x9 1" if scenario == "single_sample" else "0+1x4 stale _x4"
                if scenario == "partial_gap":
                    for item in series:
                        item["values"] = "0+0x8 stale _"
                other = "dev" if environment == "prod" else "prod"
                series += fixture(prefix, dimensions, other, "success", "other", 10, 0, 0)
                wrong_job = fixture(*args, "success", "wrong-job", 10, 0, 0)
                for item in wrong_job:
                    item["series"] = item["series"].replace('job="pheeeew-api"', 'job="other"')
                series += wrong_job
                if scenario in ("idle", "observed", "errors_only", "partial_gap"):
                    for outcome, value in [("success", 20 if scenario == "observed" else 0),
                                           ("error", 10 if scenario in ("observed", "errors_only") else 0)]:
                        labels = dimensions | {"outcome": outcome}
                        tag = ",".join(f'{key}="{value}"' for key, value in sorted(labels.items()))
                        expected.append({"labels": "{" + tag + "}", "value": value})
            tests.append({"name": f"{environment}/sample-count/{ref}/{scenario}",
                          "interval": "1m", "input_series": series,
                          "promql_expr_test": [{"expr": target["expr"].replace("$__range", "5m"),
                                                "eval_time": "10m", "exp_samples": expected}]})
            if scenario == "observed":
                longer = json.loads(json.dumps(tests[-1]))
                longer["name"] += "/10m"
                longer["promql_expr_test"][0]["expr"] = target["expr"].replace("$__range", "10m")
                for sample in longer["promql_expr_test"][0]["exp_samples"]:
                    sample["value"] *= 2
                tests.append(longer)
    return tests


def verify():
    tests = []
    for environment in ("prod", "dev"):
        dashboard = json.loads((DIRECTORY / f"pheeeew-{environment}-v2.json").read_text())
        panels = {panel["id"]: panel for panel in dashboard["panels"]}
        tests.extend(sample_count_tests(panels[130], environment))
        for panel_id in (117, 118, 119):
            panel = panels[panel_id]
            assert panel["fieldConfig"]["defaults"]["unit"] == "s"
            assert panel["fieldConfig"]["defaults"]["noValue"] == "데이터 없음"
            assert [target["refId"] for target in panel["targets"]] == ["A", "B", "C"]
            for target in panel["targets"]:
                ref = target["refId"]
                prefix, dimensions = FAMILIES[ref]
                success, error = ("SUCCESS", "ERROR") if ref == "A" else ("success", "error")
                expression = target["expr"].replace("$__rate_interval", "5m")
                if ref != "A":
                    assert target["interval"] == "1m"
                    assert target["datasource"] == panel["datasource"]
                labels = ",".join(f'{key}="{value}"' for key, value in sorted(dimensions.items()))
                for scenario in ("absent", "idle", "errors_only", "weighted", "overflow"):
                    # 기존 Spring Data 분위수의 NaN 처리 계약은 이번 변경 대상이 아니다.
                    if ref == "A" and panel_id != 117 and scenario in ("idle", "errors_only"):
                        continue
                    series, expected = [], []
                    if scenario != "absent":
                        args = (prefix, dimensions, environment)
                        series += fixture(*args, success, "idle", 0, 0, 0)
                        if scenario != "idle":
                            series += fixture(*args, error, "failed", 10, 1000, 0)
                        other = "dev" if environment == "prod" else "prod"
                        series += fixture(prefix, dimensions, other, success, "other", 10, 1000, 0)
                        wrong_job = fixture(*args, success, "wrong-job", 10, 1000, 0)
                        for item in wrong_job:
                            item["series"] = item["series"].replace('job="pheeeew-api"', 'job="other"')
                        series += wrong_job
                    if scenario == "weighted":
                        series += fixture(*args, success, "a", 1, 1, 1)
                        series += fixture(*args, success, "b", 3, 9, 0)
                        value = {117: 2.5, 118: 1 + (4 * 0.95 - 1) / 3 * 2,
                                 119: 1 + (4 * 0.99 - 1) / 3 * 2}[panel_id]
                        expected = [{"labels": "{" + labels + "}", "value": value}]
                    if scenario == "overflow":
                        overflow = fixture(*args, success, "overflow", 1, 400, 0)
                        for item in overflow:
                            if "_bucket" in item["series"] and 'le="+Inf"' not in item["series"]:
                                item["values"] = "0+0x10"
                        series += overflow
                        expected = [{"labels": "{" + labels + "}",
                                     "value": 400 if panel_id == 117 else (3 if ref == "A" else 300)}]
                    tests.append({"name": f"{environment}/{panel_id}/{ref}/{scenario}",
                                  "interval": "1m", "input_series": series,
                                  "promql_expr_test": [{"expr": expression, "eval_time": "10m",
                                                        "exp_samples": expected}]})
    with tempfile.TemporaryDirectory(prefix="pheeeew-grafana-") as temporary:
        path = Path(temporary) / "latency-tests.json"
        # promtool 공식 fuzzy_compare: 가수부 마지막 비트 차이만 허용한다.
        path.write_text(json.dumps({"evaluation_interval": "1m", "fuzzy_compare": True, "tests": tests}))
        subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "/bin/promtool",
                        "-v", f"{temporary}:/fixtures:ro", IMAGE, "test", "rules",
                        "/fixtures/latency-tests.json"], check=True, timeout=60)
    print(f"대시보드 지연·표본 수 쿼리 검증 통과: {len(tests)}개 사례")


if __name__ == "__main__":
    verify()
