"""대시보드 원본 LogQL을 실제 Loki에서 평가해 최신값·관측 공백을 검증한다."""

from datetime import datetime, timezone
import json
from pathlib import Path
import sys
import time

DIRECTORY = Path(__file__).resolve().parent
sys.path.insert(0, str(DIRECTORY.parent / "alloy"))
from verify_observer import Harness, sample  # noqa: E402


def verify_layout(dashboard):
    panels = dashboard["panels"]
    assert len({p["id"] for p in panels}) == len(panels)
    for index, panel in enumerate(panels):
        a = panel["gridPos"]
        for other in panels[index + 1:]:
            b = other["gridPos"]
            assert not (a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"]
                        and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]), (panel["id"], other["id"])
    for panel in panels:
        if panel["id"] in (154, 155):
            assert panel["fieldConfig"]["defaults"]["custom"]["spanNulls"] is False
            assert panel["options"]["legend"]["calcs"] == ["last"]
            assert all("count_over_time" not in target["expr"] for target in panel["targets"])
        if panel["id"] in (151, 152, 153):
            assert panel["options"]["reduceOptions"]["calcs"] == ["last"]
            assert all(target["queryType"] == "instant" for target in panel["targets"])


def verify():
    base = int(time.time()) - 600
    # 각 시점의 대기 분류4개와 상태/비가시/생략5개. 누적 Counter가 아니다.
    observations = [
        (0, "ok", [0, 0, 0, 0], [0, 0, 0, 0, 0]),
        (10, "ok", [5, 6, 7, 3], [21, 0, 0, 0, 1]),
        (20, "ok", [2, 0, 0, 0], [2, 0, 0, 0, 0]),
        (30, "error", None, None),
        (40, "partial", [1, 0, 2, 0], [1, 2, 4, 1, 0]),
        (50, "ok", [0, 0, 0, 0], [0, 0, 0, 0, 0]),
    ]
    waits = "lock_wait_count io_wait_count other_wait_count no_wait_count".split()
    coverage = "active_count idle_in_transaction_count unknown_state_count disabled_count omitted_count".split()
    cases = 0
    with Harness(alloy_first=False) as harness:
        for environment in ("dev", "prod"):
            dashboard = json.loads((DIRECTORY / f"pheeeew-{environment}-v2.json").read_text())
            verify_layout(dashboard)
            panels = {p["id"]: p for p in dashboard["panels"]}
            instance = "fixture-" + environment
            entries = []
            for offset, status, counts, missing in observations:
                entry = sample(1, "error" if status == "error" else "ok")
                at = datetime.fromtimestamp(base + offset, timezone.utc).isoformat()
                entry["collected_at"] = at
                if status != "error":
                    entry["snapshot"].update(sampled_at=at, visibility="partial" if status == "partial" else "visible",
                                             **dict(zip(waits, counts)), **dict(zip(coverage, missing)))
                entries.append(entry)
            labels = {"job": "pheeeew-db-observer", "application": "pheeeew", "environment": environment,
                      "instance": instance}
            harness.push(entries, labels)
            harness.push(entries, labels | {"job": "wrong-job", "instance": "excluded"})
            # 과거 큰 값으로 남지 않고 최신값이 작아지는 경우, 실패 후 숨김, 부분 관측, 복구를 확인한다.
            for offset, status, counts, missing in observations:
                stamp = base + offset + 1
                for id in (151, 152, 153, 154, 155):
                    for index, target in enumerate(panels[id]["targets"]):
                        result = harness.query(target["expr"], instant=True, time=str(stamp * 10**9))
                        values = {item["metric"]["instance"]: float(item["value"][1]) for item in result}
                        assert set(values) <= {instance}, (environment, id, result)
                        if id == 151:
                            expected = {"ok": 1, "error": 0, "partial": 2}[status]
                        elif id == 152:
                            expected = (base + offset) * 1000
                        elif id == 153:
                            expected = 1 if offset >= 30 else 0
                        elif id == 154:
                            expected = counts[index] if status == "ok" else None
                        else:
                            expected = missing[index] if status != "error" else None
                        assert values == ({} if expected is None else {instance: expected}), (environment, id, offset, values, expected)
                        cases += 1
            # 표본이 전혀 없는 시점, 추이만 stale인 시점, 상태도 stale, 24시간 lookback 밖.
            for offset in (-1, 85, 115, 365, 86451):
                for id in (151, 152, 153, 154, 155):
                    for target in panels[id]["targets"]:
                        result = harness.query(target["expr"], instant=True, time=str((base + offset) * 10**9))
                        expected = None
                        if id == 151 and offset == 85:
                            expected = 1
                        elif id == 152 and 0 < offset < 86450:
                            expected = (base + 50) * 1000
                        elif id == 153 and 0 < offset < 330:
                            expected = 1
                        values = {item["metric"]["instance"]: float(item["value"][1]) for item in result}
                        assert values == ({} if expected is None else {instance: expected}), (environment, id, offset, values, expected)
                        cases += 1
            logs = harness.query(panels[156]["targets"][0]["expr"])
            assert sum(len(stream["values"]) for stream in logs) == len(entries)
            assert all(stream["stream"]["instance"] == instance for stream in logs)
            # 실제 range API에서도 모든 시간별 쿼리를 평가한다.
            for id in (154, 155):
                for target in panels[id]["targets"]:
                    harness.query(target["expr"], start=str(base * 10**9),
                                  end=str((base + 120) * 10**9), step="10")
    print(f"DB 표본 대시보드 검증 통과: {cases}개 instant 평가, dev/prod range·logs·배치 검증")


if __name__ == "__main__":
    verify()
