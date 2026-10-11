"""실제 Alloy→격리 Loki의 표본 필터·시간·파일 발견·위치 복구를 검증한다."""

from collections import Counter
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import tempfile
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid

from verify import DIRECTORY, IMAGE, docker, validate

LOKI_IMAGE = "grafana/loki:3.6.0"
SELECTOR = '{job="pheeeew-db-observer"}'
COUNTS = "session_count unknown_state_count disabled_count eligible_count omitted_count active_count idle_in_transaction_count lock_wait_count io_wait_count other_wait_count no_wait_count".split()


def sample(index, status="ok"):
    timestamp = (datetime.now(timezone.utc) - timedelta(minutes=1) + timedelta(milliseconds=index)).isoformat()
    snapshot = dict.fromkeys(COUNTS, 0) | {"sampled_at": timestamp, "visibility": "visible",
                                          "read_all_stats": True, "sessions": []}
    result = {"event": "db_activity_snapshot", "schema_version": 1, "collected_at": timestamp,
              "status": status}
    result.update({"snapshot": snapshot} if status == "ok" else {"reason": "psql_failed"})
    return result


class Harness:
    """개발/운영 접근 없이 로컬 Loki query API를 사용하는 재사용 가능한 fixture."""

    def __init__(self, alloy_first=True):
        self.alloy_first = alloy_first
        self.name = "observer-loki-" + uuid.uuid4().hex[:10]
        self.loki = self.name + "-loki"
        self.alloy = self.name + "-alloy"
        self.positions = self.name + "-positions"
        self.samples = self.name + "-samples"
        self.temp = tempfile.TemporaryDirectory(prefix="observer-loki-")
        self.directory = Path(self.temp.name)
        self.url = None

    def __enter__(self):
        try:
            docker("network", "create", self.name)
            for volume in (self.samples, self.positions):
                docker("volume", "create", volume)
            # Alloy-first: empty root-owned volume; chmod/chown has not run yet.
            docker("run", "-d", "--user", "0", "--name", self.loki, "--network", self.name,
                   "-p", "127.0.0.1::3100", LOKI_IMAGE, "-config.file=/etc/loki/local-config.yaml",
                   "-validation.discover-log-levels=false")
            port = json.loads(docker("inspect", self.loki).stdout)[0]["NetworkSettings"]["Ports"]["3100/tcp"][0]["HostPort"]
            self.url = "http://127.0.0.1:" + port
            deadline = time.monotonic() + 40
            while time.monotonic() < deadline:
                try:
                    with urlopen(self.url + "/ready", timeout=2) as response:
                        if response.status == 200:
                            break
                except OSError:
                    time.sleep(0.5)
            else:
                raise AssertionError(docker("logs", self.loki).stderr)
            config = (DIRECTORY / "config.dev.alloy").read_text()
            pipeline = 'loki.source.file "db_observer" {' + config.split('loki.source.file "db_observer" {', 1)[1].split('loki.write "grafana" {', 1)[0]
            (self.directory / "config.alloy").write_text('logging { level = "warn" }\n' + pipeline +
                '\nloki.write "grafana" { endpoint { url = "http://' + self.loki + ':3100/loki/api/v1/push" } }\n')
            docker("run", "--rm", "--user", "0", "--entrypoint", "sh", "--mount",
                   f"type=volume,src={self.positions},dst=/positions", IMAGE, "-c", "chown 473:473 /positions")
            if self.alloy_first:
                self.run_alloy()
                with urlopen(self.alloy_url + "/-/ready", timeout=5) as response:
                    assert response.status == 200
                mode = docker("run", "--rm", "--entrypoint", "sh", "--mount",
                              f"type=volume,src={self.samples},dst=/samples,readonly,volume-nocopy", IMAGE,
                              "-c", "stat -c '%u:%g:%a' /samples").stdout.strip()
                assert mode == "0:0:755", mode
            docker("run", "--rm", "--user", "0", "--read-only", "--network", "none",
                   "--cap-drop", "ALL", "--cap-add", "CHOWN", "--cap-add", "FOWNER",
                   "--entrypoint", "sh", "--mount",
                   f"type=volume,src={self.samples},dst=/samples,volume-nocopy", IMAGE, "-c",
                   "chown 10001:10001 /samples && chmod 750 /samples")
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def run_alloy(self):
        docker("run", "-d", "--user", "473:473", "--group-add", "10001", "--name", self.alloy,
               "--network", self.name, "-p", "127.0.0.1::12345", "--mount", f"type=bind,src={self.directory},dst=/config,readonly",
               "--mount", f"type=volume,src={self.samples},dst=/var/log/db-observer,readonly,volume-nocopy",
               "--mount", f"type=volume,src={self.positions},dst=/positions", IMAGE,
               "run", "/config/config.alloy", "--storage.path=/positions", "--disable-reporting",
               "--server.http.listen-addr=0.0.0.0:12345")
        port = json.loads(docker("inspect", self.alloy).stdout)[0]["NetworkSettings"]["Ports"]["12345/tcp"][0]["HostPort"]
        self.alloy_url = "http://127.0.0.1:" + port
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                with urlopen(self.alloy_url + "/-/ready", timeout=2) as response:
                    if response.status == 200:
                        return
            except OSError:
                time.sleep(0.2)
        raise AssertionError(docker("logs", self.alloy).stderr)

    def stop_alloy(self):
        docker("stop", "--time", "10", self.alloy)
        docker("rm", self.alloy)

    def append(self, entries, filename="sample-2026101100.jsonl"):
        data = "\n".join(json.dumps(entry) if not isinstance(entry, str) else entry for entry in entries) + "\n"
        (self.directory / "input.jsonl").write_text(data)
        docker("run", "--rm", "--user", "10001:10001", "--entrypoint", "sh",
               "--mount", f"type=bind,src={self.directory},dst=/input,readonly",
               "--mount", f"type=volume,src={self.samples},dst=/samples", IMAGE, "-c",
               'umask 027; cat /input/input.jsonl >> /samples/' + filename)

    def query(self, expression=SELECTOR, instant=False, **params):
        params = {"query": expression, **params}
        if not instant:
            now = datetime.now(timezone.utc)
            params = {"start": str(int((now - timedelta(hours=1)).timestamp() * 1e9)),
                      "end": str(int((now + timedelta(seconds=1)).timestamp() * 1e9)),
                      "limit": "1000", **params}
        path = "/loki/api/v1/query" if instant else "/loki/api/v1/query_range"
        with urlopen(self.url + path + "?" + urlencode(params), timeout=5) as response:
            result = json.load(response)
        assert result["status"] == "success", result
        return result["data"]["result"]

    def push(self, entries, labels):
        values = [[str(int(datetime.fromisoformat(entry["collected_at"]).timestamp() * 1e6) * 1000),
                   json.dumps(entry)] for entry in entries]
        body = json.dumps({"streams": [{"stream": labels, "values": values}]}).encode()
        request = Request(self.url + "/loki/api/v1/push", data=body,
                          headers={"Content-Type": "application/json"}, method="POST")
        with urlopen(request, timeout=5) as response:
            assert response.status == 204

    def records(self):
        return [(stream["stream"], timestamp, json.loads(line)) for stream in self.query()
                for timestamp, line in stream["values"]]

    def wait_records(self, count):
        deadline = time.monotonic() + 45
        records = []
        while time.monotonic() < deadline:
            try:
                records = self.records()
            except OSError:
                time.sleep(0.5)
                continue
            if len(records) >= count:
                return records
            state = docker("inspect", "--format", "{{.State.Running}}", self.alloy).stdout.strip()
            assert state == "true", docker("logs", self.alloy).stderr
            time.sleep(0.5)
        raise AssertionError(f"Expected {count} records, got {len(records)}\n" + docker("logs", self.alloy).stderr)

    def __exit__(self, *_):
        for name in (self.alloy, self.loki):
            docker("rm", "--force", name, check=False)
        for volume in (self.samples, self.positions):
            docker("volume", "rm", volume, check=False)
        docker("network", "rm", self.name, check=False)
        self.temp.cleanup()


def verify():
    for name in ("config.alloy", "config.dev.alloy"):
        validate(name)
    pipelines = [(DIRECTORY / name).read_text().split('loki.process "db_observer" {', 1)[1]
                 .split('loki.write "grafana" {', 1)[0] for name in ("config.alloy", "config.dev.alloy")]
    assert pipelines[0] == pipelines[1], "Observer filters differ by environment"
    with Harness() as harness:
        allowed = [sample(1), sample(2, "error")]
        allowed[0]["reason"] = "secret-fixture"
        allowed[1]["snapshot"] = {"password": "secret-fixture"}
        allowed[0]["sql"] = "secret-fixture"
        allowed[0]["snapshot"]["password"] = "secret-fixture"
        allowed[0]["snapshot"]["sessions"] = [{"pid": 7, "state": "active", "query": "secret-fixture"}]
        rejected = [sample(20) | {"event": "wrong"}, sample(21) | {"schema_version": 2},
                    sample(22) | {"schema_version": "1"}, sample(23) | {"status": "unknown"},
                    sample(24, "error") | {"reason": "secret-fixture"}, "malformed"]
        for timestamp in (None, "invalid", "2026-02-30T12:00:00Z", "2026-13-01T12:00:00Z"):
            bad = sample(30)
            bad["snapshot"]["sampled_at"] = timestamp
            rejected.append(bad)
        harness.append(allowed + rejected)
        records = harness.wait_records(2)
        time.sleep(2)
        assert len(harness.records()) == 2, "Rejected fixture passed"
        for labels, timestamp, entry in records:
            # Loki enriches service_name server-side; Alloy sends the four reviewed labels.
            assert labels.pop("service_name", None) in (None, "pheeeew")
            assert labels == {"job": "pheeeew-db-observer", "application": "pheeeew",
                              "environment": "dev", "instance": "pheeeew-dev"}, labels
            assert "secret-fixture" not in json.dumps(entry), entry
            original = entry["snapshot"]["sampled_at"] if entry["status"] == "ok" else entry["collected_at"]
            expected_ns = int(datetime.fromisoformat(original).timestamp() * 1e6) * 1000
            assert abs(int(timestamp) - expected_ns) < 1000, (timestamp, original)
        print("표본 schema/filter/redaction/time/labels 및 Alloy-first 검증 통과")
        # New hourly path, including its first existing line, is discovered within sync_period.
        harness.append([sample(3)], "sample-2026101101.jsonl")
        harness.wait_records(3)
        print("시간별 새 파일 발견 검증 통과")
        harness.stop_alloy()
        harness.append([sample(4)], "sample-2026101101.jsonl")
        harness.run_alloy()
        harness.wait_records(4)
        # Verify persisted source offsets directly: new process reads only appended line.
        time.sleep(2)
        positions = docker("run", "--rm", "--entrypoint", "sh", "--mount",
                           f"type=volume,src={harness.positions},dst=/positions,readonly", IMAGE,
                           "-c", "cat /positions/loki.source.file.db_observer/positions.yml").stdout
        assert "sample-2026101100.jsonl" in positions and "sample-2026101101.jsonl" in positions
        with urlopen(harness.alloy_url + "/metrics", timeout=5) as response:
            metrics = response.read().decode()
        read_lines = re.findall(r'^loki_source_file_read_lines_total\{[^}]*\} ([0-9.e+]+)$', metrics, re.M)
        assert sum(float(value) for value in read_lines) == 1, read_lines
        logs = docker("logs", harness.alloy)
        assert "error" not in logs.stderr.lower(), logs.stderr
        print("파일 읽기 위치 유지 및 재시작 중 추가 표본 검증 통과")
        # HTTP outage keeps the process alive; retry transports a retained pending batch.
        docker("stop", "--time", "3", harness.loki)
        harness.append([sample(5)], "sample-2026101101.jsonl")
        time.sleep(3)
        assert docker("inspect", "--format", "{{.State.Running}}", harness.alloy).stdout.strip() == "true"
        docker("start", harness.loki)
        # Docker may allocate another ephemeral host port when the container starts again.
        port = json.loads(docker("inspect", harness.loki).stdout)[0]["NetworkSettings"]["Ports"]["3100/tcp"][0]["HostPort"]
        harness.url = "http://127.0.0.1:" + port
        harness.wait_records(5)
        final = harness.records()
        assert len(final) == 5, len(final)
        assert Counter(entry["collected_at"] for _, _, entry in final).most_common(1)[0][1] == 1
        print("Observer Alloy→Loki 검증 통과: schema/filter/redaction/time/labels, Alloy-first, rotation, positions restart, HTTP recovery")
    with Harness(alloy_first=False) as harness:
        harness.append([sample(6)])
        harness.run_alloy()
        assert len(harness.wait_records(1)) == 1
        print("초기 시작 이전에 보관된 파일의 첫 표본 수집 검증 통과")


if __name__ == "__main__":
    verify()
