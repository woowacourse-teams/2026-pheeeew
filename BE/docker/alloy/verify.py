"""운영 자격 증명 없이 지표 허용 목록과 실제 Alloy 로그 필터를 검증한다."""

import datetime
import itertools
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid


DIRECTORY = Path(__file__).resolve().parent
IMAGE = re.search(r"image: (grafana/alloy:[^\s]+)",
                  (DIRECTORY.parent / "compose.monitoring.yml").read_text()).group(1)
CONFIG = (DIRECTORY / "config.alloy").read_text()


def docker(*args, check=True):
    return subprocess.run(["docker", *args], capture_output=True, text=True,
                          check=check, timeout=30)


def verify_metrics():
    pattern = re.search(r'regex\s*=\s*"([^"]+)"', CONFIG).group(1)
    accepted = ["up", "scrape_duration_seconds", "scrape_samples_scraped",
                "pheeeew_app_version_checks_total", "jvm_threads_live_threads",
                "process_cpu_usage", "process_uptime_seconds", "system_cpu_usage",
                "hikaricp_connections"]
    families = {
        "http_server_requests_seconds_": "count sum bucket max",
        "pheeeew_activity_": "dau mau last_aggregated_seconds collection_started_seconds "
                             "record_failures_total aggregation_failures_total",
        "jvm_gc_pause_seconds_": "count sum max",
        "hikaricp_connections_": "active idle pending max min timeout_total "
                                 "acquire_seconds_count acquire_seconds_sum acquire_seconds_max",
    }
    for kind in ("map", "list"):
        families[f"pheeeew_sigh_{kind}_query_seconds_"] = "count sum bucket max"
        families[f"pheeeew_sigh_{kind}_results_"] = "count sum max"
    for prefix, suffixes in families.items():
        accepted.extend(prefix + suffix for suffix in suffixes.split())
    accepted.extend(f"jvm_memory_{kind}_bytes" for kind in ("used", "committed", "max"))
    rejected = ["pheeeew_activity_device_id", "pheeeew_activity_dau_created",
                "pheeeew_app_version_checks_created", "pheeeew_sigh_list_results_bucket",
                "pheeeew_unreviewed_total", "unreviewed_metric"]
    rejected.extend(name + "_extra" for name in accepted)
    for name, expected in [(name, True) for name in accepted] + [(name, False) for name in rejected]:
        if bool(re.fullmatch(pattern, name)) != expected:
            raise AssertionError(f"지표 허용 목록 불일치: {name}")
    print(f"지표 이름 검증 통과: 허용 {len(accepted)}, 차단 {len(rejected)}")


def verify_logs():
    http = "com.pheeeew.common.logging.RequestLogWriter"
    recorder = "com.pheeeew.activity.infra.DeviceActivityRecorder"
    scheduler = "com.pheeeew.activity.infra.DeviceActivityAggregationScheduler"
    contracts = [(http, "http_request_failed", "ERROR"), (http, "http_request_slow", "WARN"),
                 (recorder, "device_activity_record_failed", "ERROR"),
                 (scheduler, "device_activity_aggregation_failed", "ERROR")]
    events = {event for _, event, _ in contracts} | {"unknown", None}
    cases = []
    for logger, event in itertools.product((http, recorder, scheduler, "unknown", None), sorted(events, key=str)):
        cases.append(({"logger_name": logger, "event": event, "level": "ERROR"},
                      any(logger == owner and event == allowed for owner, allowed, _ in contracts)))
    for logger, event, _ in contracts[2:]:
        for level in ("WARN", "INFO", None):
            cases.append(({"logger_name": logger, "event": event, "level": level}, False))
        cases.append(({"logger_name": logger.replace(".", "x"), "event": event, "level": "ERROR"}, False))
    cases.append(({"logger_name": http, "event": "http_request_slow", "level": "WARN"}, True))
    timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    entries, expected = [], set()
    for index, (fields, allowed) in enumerate(cases):
        marker = f"fixture-{index:03}"
        entry = {key: value for key, value in fields.items() if value is not None}
        entry.update({"@timestamp": timestamp, "message": marker, "correlationId": "fixture-id"})
        entries.append(json.dumps(entry))
        if allowed:
            expected.add(marker)
    entries.append("malformed fixture-999")

    # 실제 필터를 그대로 사용하고 전송 대상만 컨테이너 표준 출력으로 바꾼다.
    pipeline = CONFIG.split('loki.process "http_requests" {', 1)[1].split('loki.write "grafana" {', 1)[0]
    pipeline = 'loki.process "http_requests" {' + pipeline.replace(
        "loki.write.grafana.receiver", "loki.echo.accepted.receiver")
    fixture_config = '''logging {
    level = "info"
    format = "json"
}
loki.source.file "fixture" {
    targets = [{__path__ = "/fixtures/application.json", job = "pheeeew-api"}]
    forward_to = [loki.process.http_requests.receiver]
}
loki.echo "accepted" {}
''' + pipeline
    name = "pheeeew-alloy-verify-" + uuid.uuid4().hex[:12]
    with tempfile.TemporaryDirectory(prefix="pheeeew-alloy-") as temp:
        directory = Path(temp)
        (directory / "application.json").write_text("\n".join(entries) + "\n")
        (directory / "config.alloy").write_text(fixture_config)
        try:
            docker("run", "--detach", "--network", "none", "--name", name,
                   "--mount", f"type=bind,src={temp},dst=/fixtures,readonly", IMAGE,
                   "run", "/fixtures/config.alloy", "--storage.path=/tmp/alloy",
                   "--disable-reporting", "--server.http.listen-addr=127.0.0.1:12345")
            deadline = time.monotonic() + 15
            while time.monotonic() < deadline:
                output = docker("logs", name)
                messages = [json.loads(line) for line in (output.stdout + output.stderr).splitlines()]
                echoes = [message for message in messages if message.get("component_id") == "loki.echo.accepted"
                          and message.get("msg") == "received log entry"]
                received = set(re.findall(r"fixture-\d{3}", "\n".join(message["entry"] for message in echoes)))
                for message in echoes:
                    labels = set(re.findall(r"(\w+)=", message["labels"]))
                    if labels != {"job", "event", "level"}:
                        raise AssertionError(f"예상하지 않은 로그 라벨: {labels}")
                if received - expected:
                    raise AssertionError(f"허용하지 않은 로그 통과: {received - expected}")
                if docker("inspect", "--format", "{{.State.Running}}", name).stdout.strip() != "true":
                    raise AssertionError(output.stdout + output.stderr)
                time.sleep(0.5)
            if received != expected:
                raise AssertionError(f"로그 누락: {expected - received}\n{output.stdout}{output.stderr}")
            print(f"실제 Alloy 로그 검증 통과: 허용 {len(expected)}, 차단 {len(entries) - len(expected)}")
        finally:
            docker("rm", "--force", name, check=False)


def main():
    verify_metrics()
    # 실제 설정 전체를 가짜 환경 변수로 검증한다. 컨테이너 외부 통신은 차단한다.
    command = ["run", "--rm", "--network", "none", "--mount",
               f"type=bind,src={DIRECTORY},dst=/config,readonly"]
    for key, value in {"GRAFANA_METRICS_URL": "http://127.0.0.1:9999/api/prom/push",
                       "GRAFANA_LOGS_URL": "http://127.0.0.1:9999/loki/api/v1/push",
                       "GRAFANA_METRICS_USER": "fixture", "GRAFANA_LOGS_USER": "fixture",
                       "GRAFANA_CLOUD_TOKEN": "fixture"}.items():
        command.extend(["--env", f"{key}={value}"])
    result = docker(*command, IMAGE, "validate", "/config/config.alloy", check=False)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    print(f"전체 Alloy 설정 검증 통과: {IMAGE}")
    verify_logs()


if __name__ == "__main__":
    main()
