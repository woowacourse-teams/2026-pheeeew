"""가짜 psql 프로세스로 수집기의 제한·회수·오류 비노출 계약을 검증한다."""

import json
import os
from pathlib import Path
import sys
import tempfile
from threading import Event, Timer
import unittest
from unittest.mock import patch

import collect

EMPTY = dict.fromkeys(collect.FIELDS, 0) | {"visibility": "visible", "sessions": [],
                                         "sampled_at": "2026-10-11T00:00:00Z", "read_all_stats": False}
ROW = dict.fromkeys(collect.SESSION_FIELDS) | {"pid": 7, "state": "active", "query_elapsed_ms": 1.5}


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name)
        self.clients = []
        spawn = collect.subprocess.Popen
        def track(*args, **kwargs):
            process = spawn(*args, **kwargs)
            self.clients.append(process)
            return process
        self.spawner = patch.object(collect.subprocess, "Popen", side_effect=track)
        self.spawner.start()
        self.addCleanup(self.spawner.stop)
        self.environment = patch.dict(os.environ, {"PATH": str(self.path), "PGOPTIONS": "unsafe override"})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def fake_psql(self, body):
        client = self.path / "psql"
        client.write_text(f"#!{sys.executable} -S\nimport os,sys,time,json\n" + body)
        client.chmod(0o700)

    def assert_reaped(self):
        self.assertIsNotNone(self.clients[-1].returncode)
        with self.assertRaises(ProcessLookupError):
            os.kill(self.clients[-1].pid, 0)

    def test_success_preserves_empty_and_partial_visibility_and_enforces_options(self):
        for visibility in ("visible", "partial"):
            with self.subTest(visibility=visibility):
                sample = EMPTY | {"visibility": visibility}
                if visibility == "partial":
                    sample |= {"sessions": [ROW], "session_count": 2, "eligible_count": 1,
                               "unknown_state_count": 1, "active_count": 1, "no_wait_count": 1}
                self.fake_psql("assert os.environ['PGCONNECT_TIMEOUT']=='3'\n"
                               "assert 'default_transaction_read_only=on' in os.environ['PGOPTIONS']\n"
                               "assert 'statement_timeout=500' in os.environ['PGOPTIONS']\n"
                               "assert 'idle_session_timeout=5000' in os.environ['PGOPTIONS']\n"
                               "assert '-X' in sys.argv and '-w' in sys.argv and 'ON_ERROR_STOP=1' in sys.argv\n"
                               f"print({json.dumps(sample)!r})\n")
                result = collect.collect_once(Event())
                self.assertEqual(result["snapshot"], sample)
                self.assertEqual(result["status"], "ok")
                self.assert_reaped()

    def test_errors_never_export_raw_output_or_unexpected_fields(self):
        cases = [("print('secret-token', file=sys.stderr); sys.exit(2)", "psql_failed"),
                 ("print('secret-token')", "invalid_snapshot"),
                 (f"print({json.dumps(EMPTY | {'query': 'secret-token'})!r})", "invalid_snapshot"),
                 (f"print({json.dumps(EMPTY | {'session_count': {'query': 'secret-token'}})!r})", "invalid_snapshot"),
                 (f"print({json.dumps(EMPTY | {'eligible_count': -1})!r})", "invalid_snapshot"),
                 (f"print({json.dumps(EMPTY | {'sessions': [ROW | {'query_elapsed_ms': float('nan')}]})!r})", "invalid_snapshot"),
                 ("print('x'*40000, flush=True); time.sleep(5)", "output_limit"),
                 ("time.sleep(5)", "process_timeout")]
        for body, reason in cases:
            with self.subTest(reason=reason):
                self.fake_psql(body)
                result = collect.collect_once(Event())
                self.assertEqual(result["reason"], reason)
                self.assertNotIn("secret-token", json.dumps(result))
                self.assertEqual(set(result), {"event", "collected_at", "status", "reason"})
                self.assert_reaped()

    def test_summary_preserves_full_population_and_rejects_inconsistent_counts(self):
        sample = EMPTY | {"sessions": [ROW] * 20, "session_count": 23, "eligible_count": 21,
                          "unknown_state_count": 1, "disabled_count": 1, "visibility": "partial",
                          "active_count": 21, "no_wait_count": 21, "omitted_count": 1}
        self.assertTrue(collect.valid_snapshot(sample))
        for invalid in ({"lock_wait_count": 1}, {"omitted_count": 0}, {"visibility": "visible"},
                        {"session_count": 21}, {"active_count": 22}, {"io_wait_count": None},
                        {"other_wait_count": True}, {"sessions": [ROW] * 19}):
            with self.subTest(invalid=invalid):
                self.assertFalse(collect.valid_snapshot(sample | invalid))

    def test_stop_reaps_running_client_and_does_not_start_when_stopped(self):
        self.fake_psql("time.sleep(5)")
        stop = Event()
        timer = Timer(0.2, stop.set)
        timer.start()
        try:
            self.assertIsNone(collect.collect_once(stop))
            self.assert_reaped()
            started = len(self.clients)
            self.assertIsNone(collect.collect_once(stop))
            self.assertEqual(len(self.clients), started)
        finally:
            timer.cancel()
            timer.join()

    def test_missing_client_is_a_failure_and_loop_waits_without_overlap(self):
        self.assertEqual(collect.collect_once(Event())["reason"], "client_unavailable")
        stop = Event()
        records, waits = [], []
        def wait(seconds):
            waits.append(seconds)
            if len(waits) == 2:
                stop.set()
        with patch.object(collect, "collect_once", side_effect=[collect.event(snapshot=EMPTY), collect.event("psql_failed")]), \
                patch.object(stop, "wait", side_effect=wait):
            collect.run(records.append, stop)
        self.assertEqual(waits, [10, 30])
        self.assertEqual(len(records), 2)

    def test_sink_failure_stops_instead_of_retrying(self):
        def fail(_):
            raise OSError("disk unavailable")
        with patch.object(collect, "collect_once", return_value=collect.event(snapshot=EMPTY)) as once:
            with self.assertRaises(OSError):
                collect.run(fail, Event())
        once.assert_called_once()

    def test_selector_initialization_and_registration_failure_reap_client(self):
        self.fake_psql("time.sleep(5)")
        for stage in ("create", "register"):
            with self.subTest(stage=stage), patch.object(collect.selectors, "DefaultSelector") as selector:
                if stage == "create":
                    selector.side_effect = OSError("secret-path")
                else:
                    selector.return_value.__enter__.return_value.register.side_effect = OSError("secret-path")
                self.assertEqual(collect.collect_once(Event())["reason"], "client_io_failed")
                self.assert_reaped()


if __name__ == "__main__":
    unittest.main()
