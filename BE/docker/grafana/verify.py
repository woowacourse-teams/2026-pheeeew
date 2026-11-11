"""실제 대시보드 쿼리와 개발 지도 캐시 알림 식의 무호출 처리와 집계 의미를 promtool로 검증한다."""

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
            env=environment, version="v4", job="pheeeew-api", scope="all", content_type="all"):
        tags = dict(job=job, environment=env, api_version=version, instance=instance,
                    source=source, route="pins", query=query, page="first", scope=scope,
                    content_type=content_type, decision=decision, outcome=outcome)
        rows.append((tags, 0 if scenario == "idle" else step))

    if scenario != "absent":
        add("month_current_default", "bypass_period", 2)
        add("all_default", "cache_hit", 6)
        # 검증된 query가 분모다. 이후 처리의 실패도 HTTP 성공만으로 제한하지 않는다.
        add("month_current_default", "cache_fallback", 2, "server_error")
        add("other", "bypass_filter", 2, "client_error")
        add("other", "cache_hit", 4, scope="my", content_type="audio")
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
            dimensions = {key: tags[key] for key in
                          ("instance", "route", "query", "page", "scope", "content_type", "decision", "outcome")}
            key = labels(dimensions)
            expected_counts[key] = expected_counts.get(key, 0) + step * 5
        expressions = [{"expr": count_target["expr"].replace("$__range", "5m"), "eval_time": "10m",
                        "exp_samples": [{"labels": key, "value": value} for key, value in expected_counts.items()]}]
        # 162는 운영 JSON에만 남아 있다(개발은 #1181에서 삭제).
        for target in panels[162]["targets"] if 162 in panels else ():
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


def map_filter_rate_tests(panel, environment):
    """필터별 캐시 응답률: 검증된 조회 중 cache_hit·cache_load 비율. 분모가 없으면 빈 값, 분자만 없으면 0%."""
    assert panel["type"] == "stat" and panel["fieldConfig"]["defaults"]["unit"] == "percent"
    target, = panel["targets"]
    assert target["instant"] and not target["range"]
    tests = []
    for scenario in ("absent", "idle", "observed"):
        rows = []

        def add(route, scope, content_type, decision, step, query="other", env=environment, instance="a"):
            tags = dict(job="pheeeew-api", environment=env, api_version="v4", instance=instance, route=route,
                        query=query, page="first", scope=scope, content_type=content_type, decision=decision,
                        outcome="success")
            rows.append((tags, 0 if scenario == "idle" else step))

        if scenario != "absent":
            add("pins", "all", "all", "cache_hit", 4, query="month_current_default")
            add("pins", "all", "all", "cache_load", 2, query="month_current_default", instance="b")
            add("pins", "all", "all", "cache_fallback", 2, query="month_current_default")
            add("pins", "my", "audio", "cache_hit", 3)
            add("pins", "my", "audio", "cache_fallback", 1)
            add("regions", "all", "memo", "bypass_level", 4)
            add("regions", "all", "memo", "cache_hit", 100, query="unclassified")
            add("pins", "my", "audio", "cache_hit", 100, env="prod" if environment == "dev" else "dev")
        expected = []
        if scenario == "observed":
            expected = [{"labels": labels(dict(route="pins", scope="all", content_type="all")), "value": 75},
                        {"labels": labels(dict(route="pins", scope="my", content_type="audio")), "value": 75},
                        {"labels": labels(dict(route="regions", scope="all", content_type="memo")), "value": 0}]
        series = [{"series": "pheeeew_map_read_requests_total" + labels(tags), "values": f"0+{step}x10"}
                  for tags, step in rows]
        tests.append({"name": f"{environment}/v4-map-filter-cache-rate/{scenario}", "interval": "1m",
                      "input_series": series,
                      "promql_expr_test": [{"expr": target["expr"].replace("$__range", "5m"), "eval_time": "10m",
                                            "exp_samples": expected}]})
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


def sample_count_tests(panels, environment):
    """운영은 한 stat 패널(130)의 B·C, 개발은 Region(130)·개인 Press(131) 막대 패널의 단일 쿼리다."""
    if environment == "prod":
        panel_ids = (130,)
        assert panels[130]["type"] == "stat"
        assert panels[130]["options"]["reduceOptions"]["calcs"] == ["last"]
        pairs = list(zip(panels[130]["targets"], ("B", "C"), strict=True))
    else:
        panel_ids = (130, 131)
        assert panels[130]["type"] == panels[131]["type"] == "barchart"
        assert all(len(panels[panel_id]["targets"]) == 1 for panel_id in panel_ids)
        pairs = [(panels[130]["targets"][0], "B"), (panels[131]["targets"][0], "C")]
        assert all(target["format"] == "table" for target, _ in pairs)
    assert all(panels[panel_id]["fieldConfig"]["defaults"]["noValue"] == "데이터 없음" for panel_id in panel_ids)
    tests = []
    for target, ref in pairs:
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


MAP_CACHE_INSTANCES = ("pheeeew-dev-blue", "pheeeew-dev-green")


def map_cache_series(metric, values, step_of, others=(dict(environment="prod"), dict(job="other"),
                                                     dict(instance="pheeeew-dev")), **extra):
    """개발 blue·green 값과, 선택되면 안 되는 다른 환경·job·인스턴스 시계열을 만든다."""
    series = []
    for instance in MAP_CACHE_INSTANCES:
        for tags, step in values(instance):
            series.append({"series": metric + labels(dict(job="pheeeew-api", environment="dev",
                                                          instance=instance) | tags | extra),
                           "values": step_of(step)})
    for other in others:
        for tags, _ in values(MAP_CACHE_INSTANCES[0]):
            series.append({"series": metric + labels(dict(job="pheeeew-api", environment="dev",
                                                          instance=MAP_CACHE_INSTANCES[0]) | tags | extra | other),
                           "values": step_of(100000)})
    return series


def map_cache_panel_tests(panels):
    """07 지도 캐시: 핀 후보 수(202), 재집계 시간(205). 미수집은 빈 값, 미호출 평균은 빈 값이다."""
    assert panels[202]["fieldConfig"]["defaults"]["unit"] == "short"
    assert panels[205]["fieldConfig"]["defaults"]["unit"] == "s"
    gauge = lambda step: f"{step}+0x10"
    counter = lambda step: f"0+{step}x10"
    tests = []
    for scenario in ("absent", "idle", "observed"):
        def value(observed):
            return 0 if scenario == "idle" else observed
        candidates = {"pheeeew-dev-blue": 120, "pheeeew-dev-green": 10000}
        scanned = {"pheeeew-dev-blue": (1, 10, 12), "pheeeew-dev-green": (3, 90, 9000)}
        rebuild = {("pheeeew-dev-blue", "pins"): (1, 2, 2.5), ("pheeeew-dev-green", "pins"): (1, 4, 4.5),
                   ("pheeeew-dev-blue", "regions"): (2, 1, 0.75)}
        series = []
        if scenario != "absent":
            series += map_cache_series("pheeeew_map_cache_candidates",
                                       lambda instance: [({"cache": "pins"}, value(candidates[instance]))], gauge)
            for suffix, index, shape in (("count", 0, counter), ("sum", 1, counter), ("max", 2, gauge)):
                series += map_cache_series(f"pheeeew_map_cache_pin_scanned_{suffix}",
                                           lambda instance: [({}, value(scanned[instance][index]))], shape)
                series += map_cache_series(
                    f"pheeeew_map_cache_rebuild_seconds_{suffix}",
                    lambda instance: [({"cache": cache}, value(numbers[index]))
                                      for (owner, cache), numbers in rebuild.items() if owner == instance], shape)

        def raw(metric, rows):
            if scenario == "absent":
                return []
            return [{"labels": labels(dict(__name__=metric, job="pheeeew-api", environment="dev",
                                           instance=instance) | tags), "value": value(number)}
                    for instance, tags, number in rows]

        observed = scenario == "observed"
        expected = {
            (202, "A"): raw("pheeeew_map_cache_candidates",
                            [(instance, {"cache": "pins"}, number) for instance, number in candidates.items()]),
            # 인스턴스를 합친 조회당 평균: (10 + 90) / (1 + 3)
            (202, "B"): [{"labels": "{}", "value": 25}] if observed else [],
            (202, "C"): raw("pheeeew_map_cache_pin_scanned_max",
                            [(instance, {}, numbers[2]) for instance, numbers in scanned.items()]),
            # cache별 평균: pins (2 + 4) / 2, regions 1 / 2
            (205, "A"): [{"labels": labels({"cache": "pins"}), "value": 3},
                         {"labels": labels({"cache": "regions"}), "value": 0.5}] if observed else [],
            (205, "B"): raw("pheeeew_map_cache_rebuild_seconds_max",
                            [(instance, {"cache": cache}, numbers[2])
                             for (instance, cache), numbers in rebuild.items()]),
        }
        for (panel_id, ref), samples in expected.items():
            target, = [target for target in panels[panel_id]["targets"] if target["refId"] == ref]
            assert target["range"] and not target["instant"]
            tests.append({"name": f"dev/map-cache/{panel_id}/{ref}/{scenario}", "interval": "1m",
                          "input_series": series, "promql_expr_test": [{
                              "expr": target["expr"].replace("$__rate_interval", "5m"),
                              "eval_time": "10m", "exp_samples": samples}]})
    return tests


MAP_CACHE_ALERTS = {
    # uid: (임계값, 대기 시간)
    "eg3yackgk9q0wc": (0, "0s"),  # 지도 캐시 갱신 실패
    "eg3yacktlqn0ge": (3, "1m"),  # 지도 캐시 등록 반영 지연
    "ag3yacl9z2olcc": (1, "0s"),  # 지도 쓰기 잠금 대기
}


def map_cache_alert_rules():
    """저장소에 기록한 Cloud 알림을 Prometheus 규칙으로 옮긴다. Grafana의 Last > 임계값과 같은 판정이다."""
    group, = json.loads((DIRECTORY / "pheeeew-dev-map-cache-alerts.json").read_text())["groups"]
    assert (group["name"], group["folder"], group["interval"]) == ("pheeeew-discord", "Pheeeew", "1m")
    rules = {}
    for rule in group["rules"]:
        query, condition = rule["data"]
        assert rule["condition"] == condition["refId"] == "C" and query["refId"] == "A"
        assert query["datasourceUid"] == "${DS_PROMETHEUS}" and query["model"]["instant"]
        model = condition["model"]
        assert model["type"] == "threshold" and model["expression"] == "A"
        evaluator, = [item["evaluator"] for item in model["conditions"]]
        threshold, wait = MAP_CACHE_ALERTS[rule["uid"]]
        assert evaluator == {"params": [threshold], "type": "gt"} and rule["for"] == wait
        assert (rule["noDataState"], rule["execErrState"]) == ("OK", "Error")
        assert rule["labels"] == {"environment": "dev", "service": "pheeeew", "severity": "warning"}
        assert rule["notification_settings"] == {"receiver": "pheeeew-dev-alerts"} and not rule["isPaused"]
        rules[rule["uid"]] = {"alert": rule["uid"], "expr": f"({query['model']['expr']}) > {threshold}",
                              "for": wait}
    assert rules.keys() == MAP_CACHE_ALERTS.keys()
    return {"groups": [{"name": "pheeeew-dev-map-cache", "interval": "1m", "rules": list(rules.values())}]}


def map_cache_alert_tests(rules):
    """알림 식은 인스턴스를 거르지 않으므로 다른 환경·job만 제외 대상이다."""
    def alert_series(metric, values, step_of):
        return map_cache_series(metric, values, step_of, (dict(environment="prod"), dict(job="other")))
    expressions = {rule["alert"]: rule["expr"].rsplit(" > ", 1)[0] for rule in rules["groups"][0]["rules"]}
    blue, green = MAP_CACHE_INSTANCES
    gauge = lambda step: f"{step}+0x20"
    flat = lambda step: "0+0x20"
    once = lambda step: f"0x5 {step}+0x15"  # 5분에 한 번 증가
    late = lambda step: f"0x7 {step}+0x12"  # 8분부터 임계값 초과
    cases = {
        "eg3yackgk9q0wc": {
            "idle": (alert_series("pheeeew_map_cache_failures_total", lambda i: [({"cache": "pins"}, 0)], flat)
                     + alert_series("pheeeew_map_cache_maintenance_seconds_count",
                                        lambda i: [({"result": "success"}, 0), ({"result": "failed"}, 0)], flat),
                     0, ()),
            # 성공·건너뜀 보정과 다른 환경의 실패는 세지 않는다.
            "maintenance_success_only": (alert_series("pheeeew_map_cache_maintenance_seconds_count",
                                                          lambda i: [({"result": "success"}, 1),
                                                                     ({"result": "busy"}, 1)], once)
                                         + alert_series("pheeeew_map_cache_failures_total",
                                                            lambda i: [({"cache": "pins"}, 0)], flat), 0, ()),
            # blue 재생성 실패 1회 + green 보정 실패 1회. 10분 창이 지나면 해소된다.
            "failed": (alert_series("pheeeew_map_cache_failures_total",
                                        lambda i: [({"cache": "regions"}, 1 if i == blue else 0)], once)
                       + alert_series("pheeeew_map_cache_maintenance_seconds_count",
                                          lambda i: [({"result": "failed"}, 1 if i == green else 0)], once),
                       2, ("8m", "9m", "10m")),
        },
        "eg3yacktlqn0ge": {
            "idle": (alert_series("pheeeew_map_cache_pending_age_seconds",
                                      lambda i: [({"cache": "pins"}, 0), ({"cache": "regions"}, 0)], gauge), 0, ()),
            "at_threshold": (alert_series("pheeeew_map_cache_pending_age_seconds",
                                              lambda i: [({"cache": "pins"}, 3)], gauge), 3, ()),
            # green만 5초. 8분부터 넘으면 8분은 대기 중, 9분부터 알림이다.
            "delayed": (alert_series("pheeeew_map_cache_pending_age_seconds",
                                         lambda i: [({"cache": "pins"}, 5 if i == green else 0.5)], late),
                        5, ("9m", "10m", "20m")),
        },
        "ag3yacl9z2olcc": {
            "idle": (alert_series("pheeeew_map_cache_lock_wait_seconds_max", lambda i: [({}, 0.2)], gauge),
                     0.2, ()),
            "at_threshold": (alert_series("pheeeew_map_cache_lock_wait_seconds_max", lambda i: [({}, 1)],
                                              gauge), 1, ()),
            "waiting": (alert_series("pheeeew_map_cache_lock_wait_seconds_max",
                                         lambda i: [({}, 1.5 if i == blue else 0.2)], gauge), 1.5, ("8m", "9m", "10m", "20m")),
        },
    }
    tests = []
    for uid, scenarios in cases.items():
        scenarios = {"absent": ([], 0, ())} | scenarios
        for scenario, (series, value, firing) in scenarios.items():
            tests.append({
                "name": f"dev/map-cache-alert/{uid}/{scenario}", "interval": "1m", "input_series": series,
                "promql_expr_test": [{"expr": expressions[uid], "eval_time": "10m",
                                      "exp_samples": [{"labels": "{}", "value": value}]}],
                "alert_rule_test": [{"eval_time": time, "alertname": uid,
                                     "exp_alerts": [{}] if time in firing else []}
                                    for time in ("8m", "9m", "10m", "20m")]})
    return tests


def verify():
    tests = []
    alert_rules = map_cache_alert_rules()
    for environment in ("prod", "dev"):
        dashboard = json.loads((DIRECTORY / f"pheeeew-{environment}-v2.json").read_text())
        panels = {panel["id"]: panel for panel in dashboard["panels"]}
        assert len(panels) == len(dashboard["panels"])
        assert not ({24, 25, 30, 31, 32, 33} & panels.keys())
        assert "pheeeew_activity_" not in json.dumps(dashboard, ensure_ascii=False)
        assert not ({18, 19, 123} & panels.keys())
        assert "pheeeew_app_version_checks" not in json.dumps(dashboard, ensure_ascii=False)
        assert panels[161]["type"] == "table"
        if environment == "dev":
            # Cloud 버전 23에서 지운 패널: 기본 한 달·캐시 우회 비중(162), 기간 결과 캐시 적중률(168)
            assert not ({162, 168} & panels.keys())
            assert [panels[panel_id]["type"] for panel_id in (217, 202, 205)] == ["row", "timeseries", "timeseries"]
            tests.extend(map_cache_panel_tests(panels))
            tests.extend(map_cache_alert_tests(alert_rules))
            assert panels[15]["targets"][0]["expr"].startswith(
                '{job="pheeeew-api", environment="dev",'
                ' event=~"http_request_failed|http_request_slow|scheduled_task_failed"}\n'
                '  | json\n  | verification != "true"\n')
            assert panels[167]["type"] == "logs"
            assert panels[167]["targets"][0]["expr"] == (
                '{job="pheeeew-api",environment="dev",event="map_read_sample"}'
                ' | json | apiVersion = "v4" | verification != "true"')
        else:
            assert all(target["instant"] and not target["range"] for target in panels[162]["targets"])
            assert panels[162]["fieldConfig"]["defaults"]["unit"] == "percent"
            assert 167 not in panels
        tests.extend(map_request_tests(panels, environment))
        tests.extend(map_filter_rate_tests(panels[169], environment))
        tests.extend(map_latency_tests(panels, environment))
        tests.extend(map_diagnostic_tests(panels[166], environment))
        tests.extend(connection_time_tests(panels[141], environment))
        tests.extend(connection_timeout_tests(panels[142], environment))
        tests.extend(sample_count_tests(panels, environment))
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
                    if ref == "A" and "clamp_min(" in expression and scenario in ("idle", "errors_only"):
                        # 개발 Cloud 117은 분모를 clamp_min으로 바꿔 성공 호출이 없으면 빈 값 대신 0을 표시한다.
                        expected = [{"labels": "{" + labels + "}", "value": 0}]
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
        (Path(temporary) / "map-cache-alerts.json").write_text(json.dumps(alert_rules))
        # promtool 공식 fuzzy_compare: 가수부 마지막 비트 차이만 허용한다.
        path.write_text(json.dumps({"rule_files": ["map-cache-alerts.json"], "evaluation_interval": "1m",
                                    "fuzzy_compare": True, "tests": tests}))
        subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "/bin/promtool",
                        "-v", f"{temporary}:/fixtures:ro", IMAGE, "test", "rules",
                        "/fixtures/latency-tests.json"], check=True, timeout=60)
        subprocess.run(["docker", "run", "--rm", "--network", "none", "--entrypoint", "/bin/promtool",
                        "-v", f"{temporary}:/fixtures:ro", IMAGE, "check", "rules",
                        "/fixtures/map-cache-alerts.json"], check=True, timeout=60)
    print(f"대시보드 쿼리·지도 캐시 알림 검증 통과: {len(tests)}개 사례")


if __name__ == "__main__":
    verify()
