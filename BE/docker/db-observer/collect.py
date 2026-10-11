"""DB 표본 수집 코어. 실행 진입점·파일 보관은 별도 구성한다."""

from collections.abc import Callable
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import selectors
import subprocess
from threading import Event
import time

PROCESS_TIMEOUT = 4
MAX_OUTPUT = 32 * 1024
SQL = Path(__file__).with_name("snapshot.sql")
FIELDS = set("sampled_at visibility read_all_stats session_count unknown_state_count disabled_count "
             "eligible_count omitted_count sessions".split())
SESSION_FIELDS = set("pid backend_start backend_type state wait_event_type wait_event query_elapsed_ms "
                     "transaction_elapsed_ms state_elapsed_ms blocker_pids blocker_count".split())


def event(reason: str | None = None, snapshot: dict | None = None) -> dict:
    result = {"event": "db_activity_snapshot", "collected_at": datetime.now(timezone.utc).isoformat(),
              "status": "error" if reason else "ok"}
    result.update({"reason": reason} if reason else {"snapshot": snapshot})
    return result


def valid_snapshot(value: object) -> bool:
    number = lambda v: type(v) in (int, float) and math.isfinite(v) and v >= 0
    text = lambda v: v is None or isinstance(v, str)
    if not (isinstance(value, dict) and set(value) == FIELDS
            and isinstance(value["sampled_at"], str) and type(value["read_all_stats"]) is bool
            and value["visibility"] in ("visible", "partial")
            and all(type(value[k]) is int and value[k] >= 0 for k in FIELDS if k.endswith("count"))
            and isinstance(value["sessions"], list) and len(value["sessions"]) <= 20):
        return False
    for row in value["sessions"]:
        if not (isinstance(row, dict) and set(row) == SESSION_FIELDS
                and type(row["pid"]) is int and row["pid"] > 0
                and all(text(row[k]) for k in ("backend_start", "backend_type", "state", "wait_event_type", "wait_event"))
                and all(row[k] is None or number(row[k]) for k in SESSION_FIELDS if k.endswith("_ms"))
                and (row["blocker_count"] is None or type(row["blocker_count"]) is int and row["blocker_count"] >= 0)
                and (row["blocker_pids"] is None or isinstance(row["blocker_pids"], list)
                     and len(row["blocker_pids"]) <= 5
                     and all(type(pid) is int and pid >= 0 for pid in row["blocker_pids"]))):
            return False
    return True


def collect_once(stop: Event) -> dict | None:
    if stop.is_set():
        return None
    env = os.environ | {"PGCONNECT_TIMEOUT": "3", "PGAPPNAME": "pheeeew-db-observer",
                        "PGOPTIONS": "-c default_transaction_read_only=on -c statement_timeout=500 "
                                     "-c lock_timeout=100 -c idle_session_timeout=5000"}
    try:
        process = subprocess.Popen(["psql", "-X", "-w", "-qAt", "-v", "ON_ERROR_STOP=1", "-f", str(SQL)],
                                   env=env, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL)
    except OSError:
        return event("client_unavailable")
    try:
        with selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            output = bytearray()
            deadline = time.monotonic() + PROCESS_TIMEOUT
            while True:
                if stop.is_set():
                    return None
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return event("process_timeout")
                for key, _ in selector.select(min(0.1, remaining)):
                    chunk = os.read(key.fd, min(4096, MAX_OUTPUT + 1 - len(output)))
                    if chunk:
                        output.extend(chunk)
                    else:
                        selector.unregister(key.fileobj)
                if len(output) > MAX_OUTPUT:
                    return event("output_limit")
                if process.poll() is not None and not selector.get_map():
                    break
        if process.returncode != 0:
            return event("psql_failed")
        try:
            snapshot = json.loads(output)
            if valid_snapshot(snapshot):
                return event(snapshot=snapshot)
        except (ValueError, RecursionError, OverflowError):
            pass
        return event("invalid_snapshot")
    except OSError:
        return event("client_io_failed")
    finally:
        # 다음 획득 전에 직접 실행한 psql을 반드시 종료·회수한다.
        if process.poll() is None:
            process.kill()
        process.wait()
        process.stdout.close()


def run(emit: Callable[[dict], None], stop: Event) -> None:
    while not stop.is_set():
        result = collect_once(stop)
        if result is None:
            break
        emit(result)  # 보관 실패는 호출자에게 전파해 수집을 중단한다.
        stop.wait(30 if result["status"] == "error" else 10)
