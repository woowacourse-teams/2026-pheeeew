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


def labels(values):
    return "{" + ",".join(f'{key}="{value}"' for key, value in sorted(values.items())) + "}"


def map_request_fixture(environment, scenario):
    rows = []

    def add(query, decision, step, outcome="success", instance="a", source="one",
            env=environment, version="v4", job="pheeeew-api"):
        tags = dict(job=job, environment=env, api_version=version, instance=instance,
                    source=source, route="pins", query=query, page="first", decision=decision, outcome=outcome)
        rows.append((tags, 0 if scenario == "idle" else step))

    if scenario != "absent":
        add("month_current_default", "bypass_period", 2)
        add("all_default", "cache_hit", 6)
        # 검증된 query가 분모다. 이후 처리의 실패도 HTTP 성공만으로 제한하지 않는다.
        add("month_current_default", "cache_fallback", 2, "server_error")
        add("other", "bypass_filter", 2, "client_error")
        add("unclassified", "unclassified", 10, "client_error")
        if scenario == "weighted":
            add("month_current_default", "bypass_period", 6, source="cloud-extra")
            add("all_default", "cache_hit", 3, instance="b")
        if scenario == "no_month":
            rows = [row for row in rows if row[0]["query"] == "all_default"]
        if scenario == "unclassified_only":
            rows = [row for row in rows if row[0]["query"] == "unclassified"]
        add("month_current_default", "bypass_period", 100, env="prod" if environment == "dev" else "dev")
        add("month_current_default", "bypass_period", 100, version="v2")
        add("month_current_default", "bypass_period", 100, job="other")
    series = [{"series": "pheeeew_map_read_requests_total" + labels(tags),
               "values": f"0+{step}x10"} for tags, step in rows]
    return rows, series


def map_request_tests(panels, environment):
    tests = []
    count_target, = panels[161]["targets"]
    for scenario in ("absent", "idle", "observed", "weighted", "no_month", "unclassified_only"):
        rows, series = map_request_fixture(environment, scenario)
        selected = [(tags, step) for tags, step in rows
                    if tags["job"] == "pheeeew-api" and tags["environment"] == environment
                    and tags["api_version"] == "v4"]
        expected_counts = {}
        for tags, step in selected:
            dimensions = {key: tags[key] for key in ("instance", "route", "query", "page", "decision", "outcome")}
            key = labels(dimensions)
            expected_counts[key] = expected_counts.get(key, 0) + step * 5
        expressions = [{"expr": count_target["expr"].replace("$__range", "5m"), "eval_time": "10m",
                        "exp_samples": [{"labels": key, "value": value} for key, value in expected_counts.items()]}]
        for target in panels[162]["targets"]:
            expected = []
            for instance in sorted({tags["instance"] for tags, _ in selected}):
                valid = [(tags, step) for tags, step in selected
                         if tags["instance"] == instance and tags["query"] != "unclassified"]
                month = [(tags, step) for tags, step in valid if tags["query"] == "month_current_default"]
                denominator = sum(step for _, step in (month if target["refId"] == "C" else valid))
                numerator = sum(step for tags, step in (month if target["refId"] != "B" else valid)
                                if target["refId"] == "A" or tags["decision"].startswith("bypass_"))
                if denominator > 0:
                    expected.append({"labels": labels(dict(instance=instance, route="pins")),
                                     "value": numerator / denominator * 100})
            expressions.append({"expr": target["expr"].replace("$__range", "5m"), "eval_time": "10m",
                                "exp_samples": expected})
        tests.append({"name": f"{environment}/v4-map-requests/{scenario}", "interval": "1m",
                      "input_series": series, "promql_expr_test": expressions})
    return tests


def map_latency_tests(panels, environment):
    tests = []
    for panel_id in (163, 164, 165):
        panel = panels[panel_id]
        assert panel["fieldConfig"]["defaults"]["noValue"] == "데이터 없음"
        assert panel["fieldConfig"]["defaults"]["custom"]["spanNulls"] is False
        assert panel["options"]["legend"]["calcs"] == ["last"]
        for target in panel["targets"]:
            for scenario in ("absent", "idle", "weighted", "errors_only", "single_sample"):
                series, expected = [], []
                if panel_id in (163, 164):
                    prefix = "pheeeew_map_read_stage_seconds"
                    dimensions = {"api_version": "v4", "stage": "map_pins_db_path", "outcome": "success"}
                    output = {"instance": "a", "stage": "map_pins_db_path"}
                elif target["refId"] == "A":
                    prefix = "http_server_requests_seconds"
                    dimensions = {"method": "GET", "uri": "/api/v4/emotions/map", "status": "200"}
                    output = {"instance": "a", "uri": "/api/v4/emotions/map"}
                else:
                    prefix = "pheeeew_map_response_write_seconds"
                    dimensions = {"api_version": "v4", "route": "pins", "outcome": "success"}
                    output = {"instance": "a", "route": "pins"}

                def add(count, total, lower, source, env=environment, job="pheeeew-api", **overrides):
                    tags = dict(job=job, environment=env, instance="a", source=source, **dimensions)
                    tags.update(overrides)
                    for suffix, step, extra in (("count", count, {}), ("sum", total, {}),
                                                ("bucket", lower, {"le": "0.01"}),
                                                ("bucket", count, {"le": "1.0"}),
                                                ("bucket", count, {"le": "+Inf"})):
                        values = "_x9 1" if scenario == "single_sample" else f"0+{step}x10"
                        series.append({"series": f"{prefix}_{suffix}" + labels(tags | extra), "values": values})

                if scenario != "absent":
                    if scenario == "idle":
                        add(0, 0, 0, "one")
                    elif scenario == "errors_only":
                        add(10, 100, 0, "one", **({"status": "500"} if prefix == "http_server_requests_seconds"
                                                   else {"outcome": "error"}))
                    else:
                        add(1, 1, 1, "one")
                        add(3, 9, 0, "two")
                    add(100, 1000, 0, "other-env", env="prod" if environment == "dev" else "dev")
                    add(100, 1000, 0, "other-job", job="other")
                    add(100, 1000, 0, "v2", **({"uri": "/api/v2/emotions/map"}
                                               if prefix == "http_server_requests_seconds" else {"api_version": "v2"}))
                    if scenario == "weighted":
                        expected = [{"labels": labels(output),
                                     "value": 0.01 + (4 * 0.95 - 1) / 3 * 0.99 if panel_id == 164 else 2.5}]
                tests.append({"name": f"{environment}/v4-map-latency/{panel_id}/{target['refId']}/{scenario}",
                              "interval": "1m", "input_series": series, "promql_expr_test": [{
                                  "expr": target["expr"].replace("$__rate_interval", "5m"),
                                  "eval_time": "10m", "exp_samples": expected}]})
    return tests


def map_diagnostic_tests(panel, environment):
    target, = panel["targets"]
    tests = []
    for scenario in ("absent", "idle", "observed"):
        series, expected = [], []
        if scenario != "absent":
            for result, step in (("sampled", 2), ("suppressed", 3)):
                step = 0 if scenario == "idle" else step
                tags = dict(job="pheeeew-api", environment=environment, instance="a", result=result)
                series.append({"series": "pheeeew_map_diagnostic_logs_total" + labels(tags),
                               "values": f"0+{step}x10"})
                expected.append({"labels": labels(dict(instance="a", result=result)), "value": step * 5})
            for extra in (dict(environment="prod" if environment == "dev" else "dev"), dict(job="other")):
                series.append({"series": "pheeeew_map_diagnostic_logs_total" + labels(tags | extra),
                               "values": "0+100x10"})
        tests.append({"name": f"{environment}/v4-map-diagnostic/{scenario}", "interval": "1m",
                      "input_series": series, "promql_expr_test": [{
                          "expr": target["expr"].replace("$__range", "5m"), "eval_time": "10m",
                          "exp_samples": expected}]})
    return tests


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


def connection_time_tests(panel, environment):
    assert panel["fieldConfig"]["defaults"]["unit"] == "s"
    assert panel["fieldConfig"]["defaults"]["noValue"] == "데이터 없음"
    assert panel["fieldConfig"]["defaults"]["custom"]["spanNulls"] is False
    assert panel["options"]["legend"]["calcs"] == ["last"]
    tests = []
    for target, metric in zip(panel["targets"], ("acquire", "usage"), strict=True):
        assert target["range"] and not target["instant"] and target["interval"] == "1m"
        for scenario in ("absent", "idle", "weighted", "reset", "single_sample"):
            series, expected = [], []
            def add(instance, pool, source, count, total, env=environment, job="pheeeew-api"):
                for suffix, step in (("count", count), ("sum", total)):
                    values = f"0+{step}x10"
                    if scenario == "single_sample":
                        values = "_x9 1"
                    if scenario == "reset":
                        values = f"0+{step}x5 0+{step}x4"
                    series.append({"series": f'hikaricp_connections_{metric}_seconds_{suffix}'
                                   f'{{job="{job}",environment="{env}",instance="{instance}",'
                                   f'pool="{pool}",source="{source}"}}', "values": values})
            if scenario != "absent":
                step = 0 if scenario == "idle" else 1
                add("a", "main", "one", step, step)
                add("a", "main", "two", step * 3, step * 9)
                add("b", "main", "one", step, step * 4)
                add("a", "other", "one", step, step * 6)
                add("a", "main", "other-env", 10, 1000, env="dev" if environment == "prod" else "prod")
                add("a", "main", "other-job", 10, 1000, job="other")
                if scenario in ("weighted", "reset"):
                    expected = [{"labels": f'{{instance="{instance}",pool="{pool}"}}', "value": value}
                                for instance, pool, value in (("a", "main", 2.5), ("b", "main", 4), ("a", "other", 6))]
            tests.append({"name": f"{environment}/connection/{metric}/{scenario}", "interval": "1m",
                          "input_series": series, "promql_expr_test": [{
                              "expr": target["expr"].replace("$__rate_interval", "5m"),
                              "eval_time": "10m", "exp_samples": expected}]})
    return tests


def connection_timeout_tests(panel, environment):
    assert panel["type"] == "stat" and panel["fieldConfig"]["defaults"]["unit"] == "short"
    assert panel["fieldConfig"]["defaults"]["noValue"] == "데이터 없음"
    assert panel["options"]["reduceOptions"]["calcs"] == ["last"]
    target, = panel["targets"]
    assert target["instant"] and not target["range"] and target["interval"] == "1m"
    tests = []
    for scenario in ("absent", "idle", "observed", "reset", "single_sample", "gap", "partial_gap"):
        series, expected = [], []
        if scenario != "absent":
            for instance, pool, source, step in (("a", "main", "one", 1), ("a", "main", "two", 2),
                                                 ("b", "main", "one", 2), ("a", "other", "one", 3)):
                values = f"0+{step}x10"
                if scenario == "idle":
                    values = "0+0x10"
                if scenario == "reset":
                    values = f"0+{step}x7 0+{step}x2"
                if scenario == "single_sample":
                    values = "_x9 1"
                if scenario == "gap":
                    values = "0+1x4 stale _x4"
                if scenario == "partial_gap":
                    values = "0+0x8 stale _"
                series.append({"series": 'hikaricp_connections_timeout_total'
                               f'{{job="pheeeew-api",environment="{environment}",instance="{instance}",'
                               f'pool="{pool}",source="{source}"}}', "values": values})
            other = "dev" if environment == "prod" else "prod"
            for env, job in ((other, "pheeeew-api"), (environment, "other")):
                series.append({"series": 'hikaricp_connections_timeout_total'
                               f'{{job="{job}",environment="{env}",instance="a",pool="main"}}',
                               "values": "0+100x10"})
            if scenario in ("idle", "observed", "reset", "partial_gap"):
                factor = {"observed": 5, "reset": 3.75}.get(scenario, 0)
                expected = [{"labels": f'{{instance="{instance}",pool="{pool}"}}', "value": step * factor}
                            for instance, pool, step in (("a", "main", 3), ("b", "main", 2), ("a", "other", 3))]
        tests.append({"name": f"{environment}/connection-timeout/{scenario}", "interval": "1m",
                      "input_series": series, "promql_expr_test": [{
                          "expr": target["expr"].replace("$__range", "5m"),
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
        assert len(panels) == len(dashboard["panels"])
        assert not ({24, 25, 30, 31, 32, 33} & panels.keys())
        assert "pheeeew_activity_" not in json.dumps(dashboard, ensure_ascii=False)
        assert not ({18, 19, 123} & panels.keys())
        assert "pheeeew_app_version_checks" not in json.dumps(dashboard, ensure_ascii=False)
        assert panels[161]["type"] == "table"
        assert all(target["instant"] and not target["range"] for target in panels[162]["targets"])
        assert panels[162]["fieldConfig"]["defaults"]["unit"] == "percent"
        if environment == "dev":
            assert panels[167]["type"] == "logs"
            assert panels[167]["targets"][0]["expr"] == (
                '{job="pheeeew-api",environment="dev",event="map_read_sample"}'
                ' | json | apiVersion = "v4" | verification != "true"')
        else:
            assert 167 not in panels
        tests.extend(map_request_tests(panels, environment))
        tests.extend(map_latency_tests(panels, environment))
        tests.extend(map_diagnostic_tests(panels[166], environment))
        tests.extend(connection_time_tests(panels[141], environment))
        tests.extend(connection_timeout_tests(panels[142], environment))
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
