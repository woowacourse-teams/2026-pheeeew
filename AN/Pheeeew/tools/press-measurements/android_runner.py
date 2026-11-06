#!/usr/bin/env python3
"""Prepare or execute one Android press-input round and preserve its clock domains."""

from __future__ import annotations

import argparse
import json
import math
import re
import shutil
import statistics
import subprocess
import sys
import threading
import time
import uuid
import shlex
from pathlib import Path
from typing import Any


DEFAULT_X = 694
DEFAULT_Y = 1160
DEFAULT_WARMUP = 50
DEFAULT_MEASURED = 500
DEFAULT_IDLE_SECONDS = 30
DEFAULT_TOLERANCE_PERCENT = 20
HTTP_ACK_TIMEOUT_SECONDS = 20
DEFAULT_INJECTOR_DEX = "/data/local/tmp/1114-tap-injector.dex"
APP_ID = "com.pheeeew"
PROBE_MARKER = "[EmotionTapDrawProbe]"
RACE_MARKER = "[PressRaceMeasure]"
SAMPLE_RE = re.compile(r"\bsample=(\d+)\b")
OUTCOME_RE = re.compile(r"\boutcome=([A-Za-z_]+)\b")
START_RE = re.compile(r"\bcallbackStartUs=(\d+)\b")
ACCEPTED_RE = re.compile(r"\baccepted=(true|false)\b")
FIELDS_RE = re.compile(r"\b(source|kind|sample|presses|outcome)=([A-Za-z0-9_]+)")
THREADTIME_RE = re.compile(
    r"^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+(\d+)\s+\d+\s+[VDIWEF]\s+[^:]+:\s?(.*)$"
)


class RunnerError(RuntimeError):
    pass


def percentile(values: list[int], fraction: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1))
    return ordered[index]


def interval_summary(values: list[int]) -> dict[str, int | float | None]:
    if not values:
        return {"count": 0, "min": None, "p50": None, "p95": None, "max": None, "mean": None}
    return {
        "count": len(values),
        "min": min(values),
        "p50": percentile(values, 0.50),
        "p95": percentile(values, 0.95),
        "max": max(values),
        "mean": round(statistics.fmean(values), 2),
    }


def parse_probe_event(line: str) -> dict[str, Any] | None:
    if PROBE_MARKER not in line:
        return None
    sample = SAMPLE_RE.search(line)
    outcome = OUTCOME_RE.search(line)
    start = START_RE.search(line)
    if not sample or not outcome:
        return {"parse_error": True, "raw": line.rstrip("\n")}
    accepted = ACCEPTED_RE.search(line)
    return {
        "sample": int(sample.group(1)),
        "outcome": outcome.group(1),
        "accepted": None if not accepted else accepted.group(1) == "true",
        "callback_start_us": None if not start else int(start.group(1)),
        "raw": line.rstrip("\n"),
    }


def parse_race_event(line: str) -> dict[str, Any] | None:
    if RACE_MARKER not in line:
        return None
    values = {key: value for key, value in FIELDS_RE.findall(line)}
    if not {"source", "kind", "sample"}.issubset(values):
        return {"parse_error": True, "raw": line.rstrip("\n")}
    return {
        "source": values["source"],
        "kind": values["kind"],
        "sample": int(values["sample"]),
        "presses": int(values.get("presses", "0")),
        "outcome": values.get("outcome"),
        "raw": line.rstrip("\n"),
    }


def evaluate_ack_gate(
    probe_events: list[dict[str, Any]],
    race_events: list[dict[str, Any]],
    target_presses: int,
) -> dict[str, Any]:
    invalid: list[str] = []
    if any(event.get("parse_error") for event in probe_events + race_events):
        invalid.append("malformed_or_overflow_diagnostic")
    if any(event.get("kind") == "SinkLimitReached" for event in race_events):
        invalid.append("sink_limit_reached")
    if any(event.get("kind") == "CoverageGap" for event in race_events):
        invalid.append("coverage_gap")
    if any(
        event.get("source") == "Http"
        and event.get("kind") == "FinalAck"
        and event.get("outcome") == "Rejected"
        for event in race_events
    ):
        invalid.append("http_ack_rejected")

    final_acks: dict[int, dict[str, Any]] = {}
    duplicate_ack_ids: set[int] = set()
    conflicting_ack_ids: set[int] = set()
    for event in race_events:
        if event.get("source") != "Http" or event.get("kind") != "FinalAck":
            continue
        sample_id = event["sample"]
        previous = final_acks.get(sample_id)
        if previous is not None:
            duplicate_ack_ids.add(sample_id)
            if (previous.get("outcome"), previous.get("presses")) != (event.get("outcome"), event.get("presses")):
                conflicting_ack_ids.add(sample_id)
        else:
            final_acks[sample_id] = event
    if conflicting_ack_ids:
        invalid.append("conflicting_duplicate_http_ack_samples")
    applied_presses = sum(
        event.get("presses", 0)
        for event in final_acks.values()
        if event.get("outcome") == "Applied"
    )
    rejected_presses = sum(
        event.get("presses", 0)
        for event in final_acks.values()
        if event.get("outcome") == "Rejected"
    )
    if applied_presses > target_presses:
        invalid.append("unexpected_extra_applied_presses")

    callback_by_id: dict[int, dict[str, Any]] = {}
    duplicate_callbacks: set[int] = set()
    for event in probe_events:
        if "sample" not in event:
            continue
        sample_id = event["sample"]
        if sample_id in callback_by_id:
            duplicate_callbacks.add(sample_id)
        callback_by_id[sample_id] = event
    if duplicate_callbacks:
        invalid.append("duplicate_probe_sample_ids")
    unexpected_callback_ids = sorted(sample for sample in callback_by_id if sample < 1 or sample > target_presses)
    if unexpected_callback_ids:
        invalid.append("unexpected_probe_sample_ids")
    rejected_callback_ids = sorted(
        sample for sample, event in callback_by_id.items() if event.get("accepted") is False
    )
    unclassified_callback_ids = sorted(
        sample for sample, event in callback_by_id.items() if event.get("accepted") is None
    )
    if rejected_callback_ids:
        invalid.append("app_callback_rejected")
    if unclassified_callback_ids:
        invalid.append("app_callback_unclassified")
    if len(callback_by_id) > target_presses:
        invalid.append("unexpected_extra_probe_samples")

    missing_callback_ids = sorted(set(range(1, target_presses + 1)) - set(callback_by_id))
    complete = applied_presses == target_presses and not missing_callback_ids and not invalid
    return {
        "target_applied_presses": target_presses,
        "unique_http_final_ack_samples": len(final_acks),
        "duplicate_http_final_ack_sample_ids": sorted(duplicate_ack_ids),
        "conflicting_http_final_ack_sample_ids": sorted(conflicting_ack_ids),
        "http_final_ack_applied_presses": applied_presses,
        "http_final_ack_rejected_presses": rejected_presses,
        "callback_samples_observed": len(callback_by_id),
        "callback_samples_accepted": sum(event.get("accepted") is True for event in callback_by_id.values()),
        "callback_sample_ids_missing": missing_callback_ids,
        "callback_sample_ids_unexpected": unexpected_callback_ids,
        "duplicate_callback_sample_ids": sorted(duplicate_callbacks),
        "rejected_callback_sample_ids": rejected_callback_ids,
        "unclassified_callback_sample_ids": unclassified_callback_ids,
        "invalid_reasons": sorted(set(invalid)),
        "complete": complete,
    }


def validate_final_capture(
    probe_events: list[dict[str, Any]],
    race_events: list[dict[str, Any]],
    target_presses: int,
    log_boundary_observed: bool,
    log_stream_complete: bool,
) -> dict[str, Any]:
    snapshot = evaluate_ack_gate(probe_events, race_events, target_presses)
    invalid = list(snapshot["invalid_reasons"])
    incomplete: list[str] = []
    if snapshot["http_final_ack_applied_presses"] != target_presses:
        incomplete.append("captured_http_ack_press_sum_incomplete")
    if snapshot["callback_sample_ids_missing"]:
        incomplete.append("captured_callback_samples_incomplete")
    if not log_boundary_observed:
        invalid.append("log_start_boundary_unverified")
    if not log_stream_complete:
        invalid.append("log_stream_incomplete")
    return {
        "target_applied_presses": target_presses,
        "http_final_ack_applied_presses": snapshot["http_final_ack_applied_presses"],
        "unique_http_final_ack_samples": snapshot["unique_http_final_ack_samples"],
        "callback_samples_observed": snapshot["callback_samples_observed"],
        "log_boundary_observed": log_boundary_observed,
        "log_stream_complete": log_stream_complete,
        "invalid_reasons": sorted(set(invalid)),
        "incomplete_reasons": sorted(set(incomplete)),
        "complete": snapshot["complete"] and not invalid and not incomplete,
    }


def all_gates_valid(
    warmup_gate: dict[str, Any] | None,
    final_gate: dict[str, Any] | None,
    final_capture: dict[str, Any],
    process_unchanged: bool,
) -> bool:
    return bool(
        warmup_gate is not None
        and warmup_gate.get("complete")
        and final_gate is not None
        and final_gate.get("complete")
        and final_capture.get("complete")
        and process_unchanged
    )


def injector_shell_command(
    adb: str,
    serial: str,
    remote_dex: str,
    count: int,
    interval_ms: int,
    x: int,
    y: int,
) -> list[str]:
    remote = (
        f"CLASSPATH={shlex.quote(remote_dex)} app_process /system/bin PressTapInjector "
        f"{count} {interval_ms} {x} {y}"
    )
    return [adb, "-s", serial, "shell", remote]


def live_logcat_command(adb: str, serial: str) -> list[str]:
    return [adb, "-s", serial, "logcat", "-b", "main", "-v", "threadtime"]


class LogBoundaryFilter:
    """Discard logcat backlog until a unique shell marker, then retain one app PID."""

    def __init__(self, marker_nonce: str, app_pid: str, end_nonce: str | None = None):
        self.marker_nonce = marker_nonce
        self.app_pid = app_pid
        self.boundary_observed = False
        self.backlog_lines_discarded = 0
        self.end_nonce = end_nonce
        self.end_observed = False

    def consume(self, line: str) -> tuple[bool, str | None]:
        if self.end_observed:
            return False, None
        if not self.boundary_observed:
            if self.marker_nonce in line:
                self.boundary_observed = True
            else:
                self.backlog_lines_discarded += 1
            return False, None
        if self.end_nonce is not None and self.end_nonce in line:
            self.end_observed = True
            return False, None
        match = THREADTIME_RE.match(line.rstrip("\n"))
        if match is None or match.group(1) != self.app_pid:
            return False, None
        return True, line.rstrip("\n")


def parse_injector_output(stdout: str) -> tuple[list[dict[str, Any]], int | None, list[str]]:
    events: list[dict[str, Any]] = []
    completed_count: int | None = None
    parse_errors: list[str] = []
    for line in stdout.splitlines():
        try:
            value = json.loads(line)
        except json.JSONDecodeError:
            parse_errors.append(line)
            continue
        if "index" in value:
            events.append(value)
        elif "completed" in value:
            completed_count = int(value["completed"])
        else:
            parse_errors.append(line)
    return events, completed_count, parse_errors


def make_plan(args: argparse.Namespace) -> dict[str, Any]:
    total = args.warmup_count + args.count
    phase_ms = max(0, args.warmup_count - 1) * args.interval_ms
    measured_ms = max(0, args.count - 1) * args.interval_ms
    return {
        "schema_version": 1,
        "mode": "dry_run",
        "measurement_results": None,
        "condition": {
            "label": args.condition_label,
            "round_id": args.round_id,
            "serial": args.serial,
            "package": args.package,
            "coordinate_px": {"x": args.x, "y": args.y},
            "interval_ms_requested": args.interval_ms,
            "warmup_count_requested": args.warmup_count,
            "measured_count_requested": args.count,
            "total_input_commands_planned": total,
            "idle_before_seconds_requested": args.idle_before_seconds,
            "ack_timeout_seconds_fixed": HTTP_ACK_TIMEOUT_SECONDS,
            "backend": "persistent-app_process" if args.injector_dex else "adb-shell-per-tap",
            "injector_dex_on_device": args.injector_dex,
        },
        "planned_host_duration_ms_excluding_command_overhead": {
            "warmup": phase_ms,
            "measurement": measured_ms,
            "total_with_idle": args.idle_before_seconds * 1000 + phase_ms + measured_ms,
        },
        "required_ack_gates": {"after_warmup_applied_presses": args.warmup_count, "after_measurement_applied_presses": args.warmup_count + args.count},
        "clock_domains": {
            "host": "time.monotonic_ns; records adb dispatch schedule only",
            "device_callback": "callbackStartUs from device monotonic probe clock; compare within this fresh process only",
            "warning": "Do not subtract host and device timestamps or treat adb dispatch cadence as callback cadence.",
        },
        "dry_run_note": "No adb command, device input, result, or measurement sample was produced.",
    }


def inject_phase(
    adb: str,
    serial: str,
    x: int,
    y: int,
    count: int,
    interval_ms: int,
) -> dict[str, Any]:
    requested_interval_ns = interval_ms * 1_000_000
    schedule_start = time.monotonic_ns() + 100_000_000
    dispatch_start_ns: list[int] = []
    dispatch_end_ns: list[int] = []
    failures: list[dict[str, Any]] = []
    for index in range(count):
        deadline = schedule_start + index * requested_interval_ns
        delay_ns = deadline - time.monotonic_ns()
        if delay_ns > 0:
            time.sleep(delay_ns / 1_000_000_000)
        started = time.monotonic_ns()
        dispatch_start_ns.append(started)
        try:
            completed = subprocess.run(
                [adb, "-s", serial, "shell", "input", "tap", str(x), str(y)],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError as exc:
            completed = None
            failures.append({"index": index + 1, "error": str(exc)})
        ended = time.monotonic_ns()
        dispatch_end_ns.append(ended)
        if completed is not None and completed.returncode != 0:
            failures.append({
                "index": index + 1,
                "return_code": completed.returncode,
                "stderr": completed.stderr.strip(),
            })
    intervals_ms = [round((later - earlier) / 1_000_000, 3) for earlier, later in zip(dispatch_start_ns, dispatch_start_ns[1:])]
    duration_ms = None
    if dispatch_start_ns and dispatch_end_ns:
        duration_ms = round((dispatch_end_ns[-1] - dispatch_start_ns[0]) / 1_000_000, 3)
    return {
        "backend": "adb-shell-per-tap",
        "requested": count,
        "input_events_succeeded": count - len({failure["index"] for failure in failures}),
        "commands_failed": len({failure["index"] for failure in failures}),
        "failures": failures,
        "host_dispatch_start_monotonic_ns": dispatch_start_ns,
        "host_dispatch_end_monotonic_ns": dispatch_end_ns,
        "host_dispatch_interval_ms": interval_summary_millis(intervals_ms),
        "host_command_span_ms": duration_ms,
    }


def inject_persistent_phase(
    adb: str,
    serial: str,
    remote_dex: str,
    x: int,
    y: int,
    count: int,
    interval_ms: int,
) -> dict[str, Any]:
    command = injector_shell_command(adb, serial, remote_dex, count, interval_ms, x, y)
    started = time.monotonic_ns()
    try:
        completed = subprocess.run(command, check=False, capture_output=True, text=True)
    except OSError as exc:
        ended = time.monotonic_ns()
        return {
            "backend": "persistent-app_process",
            "requested": count,
            "input_events_succeeded": 0,
            "commands_failed": count,
            "host_command_start_monotonic_ns": started,
            "host_command_end_monotonic_ns": ended,
            "injector_events": [],
            "device_injector_interval_ms": interval_summary_millis([]),
            "failures": [str(exc)],
        }
    ended = time.monotonic_ns()
    events, completed_count, parse_errors = parse_injector_output(completed.stdout)
    successfully_injected = sum(event.get("downInjected") is True and event.get("upInjected") is True for event in events)
    down_times = [int(event["downUptimeMs"]) for event in events if "downUptimeMs" in event]
    down_intervals = [later - earlier for earlier, later in zip(down_times, down_times[1:])]
    failures: list[str] = list(parse_errors)
    if completed.returncode != 0:
        failures.append(f"app_process exited {completed.returncode}: {completed.stderr.strip()}")
    if completed_count != count:
        failures.append(f"injector completed={completed_count!r}, requested={count}")
    if len(events) != count:
        failures.append(f"injector event count={len(events)}, requested={count}")
    return {
        "backend": "persistent-app_process",
        "requested": count,
        "input_events_succeeded": successfully_injected,
        "commands_failed": max(0, count - successfully_injected),
        "host_command_start_monotonic_ns": started,
        "host_command_end_monotonic_ns": ended,
        "device_injector_events": events,
        "device_injector_interval_ms": interval_summary(down_intervals),
        "injector_completed_count": completed_count,
        "failures": failures,
    }


def read_app_pid(adb: str, serial: str, package: str = APP_ID) -> str | None:
    result = subprocess.run(
        [adb, "-s", serial, "shell", "pidof", package],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    pids = result.stdout.split()
    return pids[0] if len(pids) == 1 else None


def ensure_remote_dex(adb: str, serial: str, remote_dex: str) -> None:
    result = subprocess.run(
        [adb, "-s", serial, "shell", "test", "-r", remote_dex],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RunnerError(f"injector dex is not readable on device: {remote_dex}")


def wait_for_ack_gate(
    target_presses: int,
    timeout_seconds: int,
    serial: str,
    adb: str,
    package: str,
    expected_pid: str,
    snapshot_events: Any,
    pid_reader: Any = read_app_pid,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    latest: dict[str, Any] = {}
    while True:
        current_pid = pid_reader(adb, serial, package)
        if current_pid != expected_pid:
            latest = evaluate_ack_gate(*snapshot_events(), target_presses)
            latest["invalid_reasons"] = sorted(set(latest["invalid_reasons"] + ["app_pid_changed"]))
            latest["complete"] = False
            latest["expected_pid"] = expected_pid
            latest["observed_pid"] = current_pid
            return latest
        probe_events, race_events = snapshot_events()
        latest = evaluate_ack_gate(probe_events, race_events, target_presses)
        latest["expected_pid"] = expected_pid
        latest["observed_pid"] = current_pid
        if latest["invalid_reasons"]:
            latest["complete"] = False
            return latest
        if latest["complete"]:
            latest["timed_out"] = False
            return latest
        if time.monotonic() >= deadline:
            latest["invalid_reasons"] = ["http_ack_or_callback_timeout"]
            latest["complete"] = False
            latest["timed_out"] = True
            return latest
        time.sleep(0.25)


def input_phase_complete(phase: dict[str, Any] | None, requested: int) -> bool:
    return (
        phase is not None
        and phase.get("input_events_succeeded") == requested
        and not phase.get("failures")
    )


def interval_summary_millis(values: list[float]) -> dict[str, int | float | None]:
    if not values:
        return {"count": 0, "min": None, "p50": None, "p95": None, "max": None, "mean": None}
    ordered = sorted(values)
    return {
        "count": len(ordered),
        "min": ordered[0],
        "p50": ordered[math.ceil(len(ordered) * 0.50) - 1],
        "p95": ordered[math.ceil(len(ordered) * 0.95) - 1],
        "max": ordered[-1],
        "mean": round(statistics.fmean(ordered), 3),
    }


def callback_metrics(events: list[dict[str, Any]], warmup_count: int, measured_count: int) -> dict[str, Any]:
    usable = [event for event in events if "sample" in event]
    by_sample: dict[int, dict[str, Any]] = {}
    duplicates: list[int] = []
    for event in usable:
        sample = event["sample"]
        if sample in by_sample:
            duplicates.append(sample)
        by_sample[sample] = event
    total_expected = warmup_count + measured_count
    expected_ids = set(range(1, total_expected + 1))
    missing_ids = sorted(expected_ids - set(by_sample))
    unexpected_ids = sorted(set(by_sample) - expected_ids)
    warmup = [by_sample[index] for index in range(1, warmup_count + 1) if index in by_sample]
    measured = [by_sample[index] for index in range(warmup_count + 1, total_expected + 1) if index in by_sample]
    timed_measured = [event for event in measured if event["callback_start_us"] is not None]
    callback_times = [event["callback_start_us"] for event in timed_measured]
    callback_intervals = [later - earlier for earlier, later in zip(callback_times, callback_times[1:])]
    measured_accepted = sum(event["accepted"] is True for event in measured)
    measured_rejected = sum(event["accepted"] is False for event in measured)
    unclassified = sum(event["accepted"] is None for event in measured)
    return {
        "sample_events_observed": len(usable),
        "parse_errors": sum(event.get("parse_error", False) for event in events),
        "duplicate_sample_ids": sorted(set(duplicates)),
        "missing_expected_sample_ids": missing_ids,
        "unexpected_sample_ids": unexpected_ids,
        "warmup": {
            "expected": warmup_count,
            "observed": len(warmup),
            "accepted": sum(event["accepted"] is True for event in warmup),
            "rejected": sum(event["accepted"] is False for event in warmup),
            "acceptance_unclassified": sum(event["accepted"] is None for event in warmup),
        },
        "measurement": {
            "expected": measured_count,
            "observed": len(measured),
            "accepted": measured_accepted,
            "rejected": measured_rejected,
            "acceptance_unclassified": unclassified,
            "outcomes": count_outcomes(measured),
            "callback_start_timestamp_count": len(timed_measured),
            "callback_interval_us": interval_summary(callback_intervals),
        },
        "outcomes_all_samples": count_outcomes(usable),
    }


def count_outcomes(events: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for event in events:
        name = event["outcome"]
        counts[name] = counts.get(name, 0) + 1
    return counts


def cadence_check(summary: dict[str, Any], target_ms: int, tolerance_percent: int) -> dict[str, Any]:
    interval = summary["measurement"]["callback_interval_us"]
    p50 = interval["p50"]
    p95 = interval["p95"]
    lower = target_ms * 1000 * (1 - tolerance_percent / 100)
    upper = target_ms * 1000 * (1 + tolerance_percent / 100)
    within = p50 is not None and p95 is not None and lower <= p50 <= upper and p95 <= upper
    return {
        "target_interval_ms": target_ms,
        "screening_tolerance_percent": tolerance_percent,
        "p50_and_p95_within_screening_tolerance": within,
        "threshold_note": "A screening threshold only; preserve measured values and review condition suitability separately.",
    }


def run_round(args: argparse.Namespace) -> dict[str, Any]:
    adb = shutil.which("adb")
    if not adb:
        raise RunnerError("adb was not found on PATH")
    output_path = Path(args.output).expanduser()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    log_path = output_path.with_suffix(output_path.suffix + ".logcat.txt")
    device_state = subprocess.run([adb, "-s", args.serial, "get-state"], check=False, capture_output=True, text=True)
    if device_state.returncode != 0 or device_state.stdout.strip() != "device":
        raise RunnerError(f"adb target {args.serial!r} is not ready: {device_state.stdout.strip()} {device_state.stderr.strip()}")

    pid_before = read_app_pid(adb, args.serial, args.package)
    if pid_before is None:
        raise RunnerError(f"expected one running app process for {args.package}; restart it and open Press before this round")
    if args.injector_dex:
        ensure_remote_dex(adb, args.serial, args.injector_dex)

    marker_nonce = uuid.uuid4().hex
    end_nonce = uuid.uuid4().hex
    log_filter = LogBoundaryFilter(marker_nonce, pid_before, end_nonce)
    log_boundary_seen = threading.Event()
    log_end_seen = threading.Event()
    log_boundary_info: dict[str, Any] = {}
    logcat = subprocess.Popen(
        live_logcat_command(adb, args.serial),
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )
    selected_lines: list[str] = []
    probe_events: list[dict[str, Any]] = []
    race_events: list[dict[str, Any]] = []
    event_lock = threading.Lock()

    def read_logcat() -> None:
        assert logcat.stdout is not None
        for line in logcat.stdout:
            matched, current_line = log_filter.consume(line)
            if log_filter.end_observed:
                log_end_seen.set()
            if log_filter.boundary_observed and not log_boundary_seen.is_set():
                log_boundary_info["host_monotonic_ns"] = time.monotonic_ns()
                log_boundary_info["marker_nonce"] = marker_nonce
                log_boundary_info["backlog_lines_discarded"] = log_filter.backlog_lines_discarded
                log_boundary_seen.set()
            if not matched or current_line is None:
                continue
            if PROBE_MARKER in current_line:
                parsed = parse_probe_event(current_line)
                with event_lock:
                    selected_lines.append(current_line)
                    if parsed is not None:
                        probe_events.append(parsed)
            elif RACE_MARKER in current_line:
                parsed = parse_race_event(current_line)
                with event_lock:
                    selected_lines.append(current_line)
                    if parsed is not None:
                        race_events.append(parsed)

    reader = threading.Thread(target=read_logcat, name="android-measurement-logcat", daemon=True)
    reader.start()
    time.sleep(0.25)
    marker_sent_host_ns = time.monotonic_ns()
    try:
        marker_result = subprocess.run(
            [adb, "-s", args.serial, "shell", "log", "-t", "PHEEEW_MEASURE_BOUNDARY", marker_nonce],
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )
        marker_verified = marker_result.returncode == 0 and log_boundary_seen.wait(timeout=5)
    except (OSError, subprocess.TimeoutExpired):
        marker_verified = False
    if not marker_verified:
        logcat.terminate()
        try:
            logcat.wait(timeout=3)
        except subprocess.TimeoutExpired:
            logcat.kill()
            logcat.wait(timeout=3)
        reader.join(timeout=3)
        raise RunnerError("could not verify the explicit logcat start marker; no device input was sent")
    boundaries: dict[str, int] = {
        "logcat_marker_sent_host_monotonic_ns": marker_sent_host_ns,
        "logcat_live_boundary_host_monotonic_ns": int(log_boundary_info["host_monotonic_ns"]),
    }
    warmup: dict[str, Any] | None = None
    measured: dict[str, Any] | None = None
    warmup_gate: dict[str, Any] | None = None
    final_ack_gate: dict[str, Any] | None = None

    def snapshot_events() -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        with event_lock:
            return list(probe_events), list(race_events)

    def dispatch(count: int) -> dict[str, Any]:
        if args.injector_dex:
            return inject_persistent_phase(adb, args.serial, args.injector_dex, args.x, args.y, count, args.interval_ms)
        return inject_phase(adb, args.serial, args.x, args.y, count, args.interval_ms)

    try:
        boundaries["idle_start_host_monotonic_ns"] = time.monotonic_ns()
        if args.idle_before_seconds:
            time.sleep(args.idle_before_seconds)
        boundaries["idle_end_host_monotonic_ns"] = time.monotonic_ns()
        boundaries["warmup_start_host_monotonic_ns"] = time.monotonic_ns()
        warmup = dispatch(args.warmup_count)
        boundaries["warmup_end_host_monotonic_ns"] = time.monotonic_ns()
        boundaries["warmup_ack_gate_start_host_monotonic_ns"] = time.monotonic_ns()
        if not input_phase_complete(warmup, args.warmup_count):
            warmup_gate = {
                "target_applied_presses": args.warmup_count,
                "complete": False,
                "timed_out": False,
                "invalid_reasons": ["warmup_injection_incomplete"],
            }
        else:
            warmup_gate = wait_for_ack_gate(
                args.warmup_count, HTTP_ACK_TIMEOUT_SECONDS, args.serial, adb, args.package, pid_before, snapshot_events
            )
        boundaries["warmup_ack_gate_end_host_monotonic_ns"] = time.monotonic_ns()

        if warmup_gate["complete"]:
            boundaries["measurement_start_host_monotonic_ns"] = time.monotonic_ns()
            measured = dispatch(args.count)
            boundaries["measurement_end_host_monotonic_ns"] = time.monotonic_ns()
            boundaries["final_ack_gate_start_host_monotonic_ns"] = time.monotonic_ns()
            if input_phase_complete(measured, args.count):
                final_ack_gate = wait_for_ack_gate(
                    args.warmup_count + args.count,
                    HTTP_ACK_TIMEOUT_SECONDS,
                    args.serial,
                    adb,
                    args.package,
                    pid_before,
                    snapshot_events,
                )
            else:
                final_ack_gate = {
                    "target_applied_presses": args.warmup_count + args.count,
                    "complete": False,
                    "timed_out": False,
                    "invalid_reasons": ["measurement_injection_incomplete"],
                }
            boundaries["final_ack_gate_end_host_monotonic_ns"] = time.monotonic_ns()
    finally:
        # Drain all main-buffer entries preceding this device-side marker before
        # stopping adb. Host pipe EOF alone does not prove device logs arrived.
        try:
            end_result = subprocess.run(
                [adb, "-s", args.serial, "shell", "log", "-t", "PHEEEW_MEASURE_BOUNDARY", end_nonce],
                check=False, capture_output=True, text=True, timeout=5,
            )
            end_verified = end_result.returncode == 0 and log_end_seen.wait(timeout=5)
        except (OSError, subprocess.TimeoutExpired):
            end_verified = False
        boundaries["logcat_end_marker_observed"] = int(end_verified)
        logcat_was_running_at_stop = logcat.poll() is None
        logcat_forced_kill = False
        pid_after = read_app_pid(adb, args.serial, args.package)
        logcat.terminate()
        try:
            logcat.wait(timeout=3)
        except subprocess.TimeoutExpired:
            logcat.kill()
            logcat.wait(timeout=3)
            logcat_forced_kill = True
        reader.join(timeout=5)
    log_stream_complete = end_verified and not reader.is_alive() and logcat_was_running_at_stop and not logcat_forced_kill
    logcat_return_code = logcat.returncode

    process_unchanged = pid_before == pid_after
    if not process_unchanged:
        for gate in (warmup_gate, final_ack_gate):
            if gate is not None:
                gate["complete"] = False
                gate["invalid_reasons"] = sorted(set(gate.get("invalid_reasons", []) + ["app_pid_changed"]))
                gate["expected_pid"] = pid_before
                gate["observed_pid_after_round"] = pid_after

    with event_lock:
        captured_probe_events = list(probe_events)
        captured_race_events = list(race_events)
        captured_lines = list(selected_lines)
    with log_path.open("w", encoding="utf-8") as log_file:
        for line in captured_lines:
            log_file.write(line + "\n")

    callbacks = callback_metrics(captured_probe_events, args.warmup_count, args.count)
    cadence = cadence_check(callbacks, args.interval_ms, args.cadence_tolerance_percent)
    final_capture = validate_final_capture(
        captured_probe_events,
        captured_race_events,
        args.warmup_count + args.count,
        log_boundary_seen.is_set(),
        log_stream_complete,
    )
    if not process_unchanged:
        final_capture["invalid_reasons"] = sorted(set(final_capture["invalid_reasons"] + ["app_pid_changed"]))
        final_capture["complete"] = False
    input_events_complete = (
        warmup is not None
        and measured is not None
        and input_phase_complete(warmup, args.warmup_count)
        and input_phase_complete(measured, args.count)
    )
    callback_counts_complete = (
        callbacks["warmup"]["observed"] == args.warmup_count
        and callbacks["measurement"]["observed"] == args.count
        and not callbacks["missing_expected_sample_ids"]
        and not callbacks["unexpected_sample_ids"]
        and not callbacks["duplicate_sample_ids"]
        and callbacks["parse_errors"] == 0
    )
    accepted_complete = callbacks["measurement"]["accepted"] == args.count
    timestamps_complete = callbacks["measurement"]["callback_start_timestamp_count"] == args.count
    cadence_complete = cadence["p50_and_p95_within_screening_tolerance"] is True
    warmup_ack_complete = warmup_gate is not None and warmup_gate["complete"]
    final_ack_complete = final_ack_gate is not None and final_ack_gate["complete"]
    gate_history_valid = all_gates_valid(warmup_gate, final_ack_gate, final_capture, process_unchanged)
    result = {
        "schema_version": 1,
        "mode": "device_input",
        "condition": {
            "label": args.condition_label,
            "round_id": args.round_id,
            "serial": args.serial,
            "package": args.package,
            "process_pid": pid_before,
            "coordinate_px": {"x": args.x, "y": args.y},
            "interval_ms_requested": args.interval_ms,
            "warmup_count_requested": args.warmup_count,
            "measured_count_requested": args.count,
            "idle_before_seconds_requested": args.idle_before_seconds,
            "ack_timeout_seconds_fixed": HTTP_ACK_TIMEOUT_SECONDS,
            "backend": "persistent-app_process" if args.injector_dex else "adb-shell-per-tap",
            "injector_dex_on_device": args.injector_dex,
        },
        "clock_domains": {
            "host": "time.monotonic_ns for adb dispatch and phase boundaries",
            "device_injector": "SystemClock.uptimeMillis from injector output; not subtracted from host or app callback time",
            "device_callback": "callbackStartUs from app monotonic probe clock; relative to probe creation and compared only within this app process",
            "comparison_rule": "Host dispatch, injector uptime and app callback intervals remain separate clock domains.",
        },
        "phase_boundaries_host_monotonic_ns": boundaries,
        "logcat_start_boundary": {
            "method": "discard backlog until unique shell log marker, then filter app PID",
            "marker_nonce": marker_nonce,
            "marker_sent_host_monotonic_ns": marker_sent_host_ns,
            "marker_observed_host_monotonic_ns": log_boundary_info.get("host_monotonic_ns"),
            "backlog_lines_discarded": log_boundary_info.get("backlog_lines_discarded"),
            "verified": log_boundary_seen.is_set(),
            "stream_complete": log_stream_complete,
            "logcat_stop_return_code": logcat_return_code,
        },
        "host_dispatch": {"warmup": warmup, "measurement": measured},
        "http_ack_gates": {"warmup_50": warmup_gate, "complete_550": final_ack_gate},
        "final_log_recheck": final_capture,
        "device_callback_summary": callbacks,
        "device_callback_events": [
            {key: value for key, value in event.items() if key != "raw"}
            for event in sorted(captured_probe_events, key=lambda item: item.get("sample", 2**63))
        ],
        "http_ack_events": [
            {key: value for key, value in event.items() if key != "raw"}
            for event in sorted(captured_race_events, key=lambda item: (item.get("sample", 2**63), item.get("kind", "")))
        ],
        "cadence_screen": cadence,
        "round_integrity": {
            "app_pid_unchanged": process_unchanged,
            "warmup_50_ack_gate_complete": warmup_ack_complete,
            "complete_550_ack_gate_complete": final_ack_complete,
            "final_capture_recheck_complete": final_capture["complete"],
            "input_events_complete": input_events_complete,
            "callback_samples_complete": callback_counts_complete,
            "measured_samples_all_accepted": accepted_complete,
            "measured_callback_timestamps_complete": timestamps_complete,
            "device_callback_cadence_within_screening_tolerance": cadence_complete,
            "probe_sample_ids_start_at_1_and_end_at_requested_total": callbacks["sample_events_observed"] == args.warmup_count + args.count and not callbacks["missing_expected_sample_ids"] and not callbacks["unexpected_sample_ids"] and not callbacks["duplicate_sample_ids"],
            "valid_configured_measurement_round": gate_history_valid and input_events_complete and callback_counts_complete and accepted_complete and timestamps_complete,
            "cadence_interpretation": "Callback cadence is an observed app outcome, not an exclusion criterion. Compare device_injector_interval_ms to the requested input schedule separately; retain slow callbacks.",
        },
        "logcat_excerpt_path": str(log_path),
        "diagnostic_events_captured": len(captured_lines),
        "runner_note": "HTTP FinalAck confirms client-observed Applied presses, not server storage or UI freshness. The runner did not send inputs unless explicitly armed.",
    }
    with output_path.open("w", encoding="utf-8") as output_file:
        json.dump(result, output_file, indent=2, ensure_ascii=False)
        output_file.write("\n")
    return result


def self_test() -> None:
    assert percentile([100, 200, 300, 400], 0.50) == 200
    assert interval_summary([100, 200, 300])["p95"] == 300
    event = parse_probe_event(
        "I/System.out: [EmotionTapDrawProbe] sample=51 outcome=drawn accepted=true callbackStartUs=9000 callbackUs=8"
    )
    assert event == {
        "sample": 51,
        "outcome": "drawn",
        "accepted": True,
        "callback_start_us": 9000,
        "raw": "I/System.out: [EmotionTapDrawProbe] sample=51 outcome=drawn accepted=true callbackStartUs=9000 callbackUs=8",
    }
    assert parse_probe_event("I/System.out: [EmotionTapDrawProbe] outcome=overflow reason=pending_limit") == {
        "parse_error": True,
        "raw": "I/System.out: [EmotionTapDrawProbe] outcome=overflow reason=pending_limit",
    }
    applied_50 = parse_race_event(
        "I/System.out: [PressRaceMeasure] source=Http kind=FinalAck sample=7 ms=21 observedAt=null "
        "path=null pathAtSocketLoss=null presses=50 attempt=null attemptResult=null retryAfterMs=null "
        "recoveryResult=null failedAttempts=0 outcome=Applied"
    )
    assert applied_50 is not None and applied_50["presses"] == 50
    warmup_callbacks = [
        {"sample": sample, "outcome": "drawn", "accepted": True, "callback_start_us": sample * 100_000}
        for sample in range(1, 51)
    ]
    warmup_ack = [applied_50]
    assert evaluate_ack_gate(warmup_callbacks, warmup_ack, 50)["complete"]
    # Repeated sink/log lines for the same sample must not inflate the press total.
    deduplicated = evaluate_ack_gate(warmup_callbacks, [applied_50, applied_50], 50)
    assert deduplicated["complete"] and deduplicated["http_final_ack_applied_presses"] == 50
    assert deduplicated["duplicate_http_final_ack_sample_ids"] == [7]

    applied_500 = dict(applied_50, sample=8, presses=500)
    all_callbacks = warmup_callbacks + [
        {"sample": sample, "outcome": "drawn", "accepted": True, "callback_start_us": sample * 100_000}
        for sample in range(51, 551)
    ]
    full_gate = evaluate_ack_gate(all_callbacks, [applied_50, applied_500], 550)
    assert full_gate["complete"] and full_gate["http_final_ack_applied_presses"] == 550

    rejected = parse_race_event(
        "I/System.out: [PressRaceMeasure] source=Http kind=FinalAck sample=9 ms=21 presses=1 outcome=Rejected"
    )
    assert "http_ack_rejected" in evaluate_ack_gate(
        [{"sample": 1, "outcome": "rejected", "accepted": False, "callback_start_us": 1}], [rejected], 1
    )["invalid_reasons"]
    coverage_gap = parse_race_event(
        "I/System.out: [PressRaceMeasure] source=Http kind=CoverageGap sample=10 ms=0 presses=1 outcome=null"
    )
    assert "coverage_gap" in evaluate_ack_gate([], [coverage_gap], 0)["invalid_reasons"]
    sink_limit = parse_race_event(
        "I/System.out: [PressRaceMeasure] source=Recorder kind=SinkLimitReached sample=11 ms=0 presses=0 outcome=null"
    )
    assert "sink_limit_reached" in evaluate_ack_gate([], [sink_limit], 0)["invalid_reasons"]
    assert "malformed_or_overflow_diagnostic" in evaluate_ack_gate(
        [{"parse_error": True}], [], 0
    )["invalid_reasons"]

    # A late event captured after an initially successful gate must veto final round validity.
    late_invalid_events = [coverage_gap, sink_limit, rejected]
    for late_event in late_invalid_events:
        final_capture = validate_final_capture(
            all_callbacks,
            [applied_50, applied_500, late_event],
            550,
            log_boundary_observed=True,
            log_stream_complete=True,
        )
        assert not final_capture["complete"]
        assert not all_gates_valid(warmup_gate={"complete": True}, final_gate=full_gate, final_capture=final_capture, process_unchanged=True)
    # A successful final snapshot must not erase an earlier timeout gate.
    successful_snapshot = validate_final_capture(
        all_callbacks, [applied_50, applied_500], 550, True, True
    )
    earlier_timeout = {"complete": False, "timed_out": True, "invalid_reasons": ["http_ack_or_callback_timeout"]}
    assert successful_snapshot["complete"] and not all_gates_valid(
        warmup_gate={"complete": True}, final_gate=earlier_timeout, final_capture=successful_snapshot, process_unchanged=True
    )
    assert earlier_timeout["timed_out"]
    timeout = wait_for_ack_gate(
        1, 0, "unused-serial", "unused-adb", "com.pheeeew", "pid-1", lambda: ([], []),
        pid_reader=lambda _adb, _serial, _package: "pid-1",
    )
    assert timeout["timed_out"] and not timeout["complete"]
    changed_pid = wait_for_ack_gate(
        1, 1, "unused-serial", "unused-adb", "com.pheeeew", "pid-1", lambda: ([], []),
        pid_reader=lambda _adb, _serial, _package: "pid-2",
    )
    assert "app_pid_changed" in changed_pid["invalid_reasons"]

    command = injector_shell_command("adb", "serial-1", DEFAULT_INJECTOR_DEX, 50, 500, 694, 1160)
    assert command == [
        "adb", "-s", "serial-1", "shell",
        "CLASSPATH=/data/local/tmp/1114-tap-injector.dex app_process /system/bin PressTapInjector 50 500 694 1160",
    ]
    assert live_logcat_command("adb", "serial-1") == ["adb", "-s", "serial-1", "logcat", "-b", "main", "-v", "threadtime"]
    boundary_filter = LogBoundaryFilter("nonce-abc", "123", "nonce-end")
    historical = "11-06 12:00:00.000  123  123 I System.out: [EmotionTapDrawProbe] stale"
    marker = "11-06 12:00:00.010  999  999 I PHEEEW_MEASURE_BOUNDARY: nonce-abc"
    other_process = "11-06 12:00:00.020  456  456 I System.out: not-app"
    current_app = "11-06 12:00:00.030  123  123 I System.out: [EmotionTapDrawProbe] current"
    assert boundary_filter.consume(historical) == (False, None)
    assert boundary_filter.consume(marker) == (False, None) and boundary_filter.boundary_observed
    assert boundary_filter.consume(other_process) == (False, None)
    matched, captured = boundary_filter.consume(current_app)
    assert matched and captured == current_app
    assert boundary_filter.backlog_lines_discarded == 1
    late_diagnostic = current_app.replace("[EmotionTapDrawProbe] current", "[PressRaceMeasure] source=Http kind=CoverageGap sample=999")
    assert boundary_filter.consume(late_diagnostic) == (True, late_diagnostic)
    assert not boundary_filter.end_observed
    assert boundary_filter.consume(marker.replace("nonce-abc", "nonce-end")) == (False, None)
    assert boundary_filter.end_observed
    assert boundary_filter.consume(current_app) == (False, None)
    assert not validate_final_capture([], [], 0, True, False)["complete"]
    injected, completed_count, errors = parse_injector_output(
        '{"index":1,"dueUptimeMs":100,"downUptimeMs":101,"upUptimeMs":111,"downInjected":true,"upInjected":true}\n'
        '{"completed":1}\n'
    )
    assert len(injected) == 1 and completed_count == 1 and not errors

    metric = callback_metrics(
        [
            {"sample": 1, "outcome": "drawn", "accepted": True, "callback_start_us": 1000},
            {"sample": 2, "outcome": "drawn", "accepted": True, "callback_start_us": 1100},
            {"sample": 3, "outcome": "rejected", "accepted": False, "callback_start_us": 1200},
        ],
        warmup_count=1,
        measured_count=2,
    )
    assert metric["warmup"]["accepted"] == 1
    assert metric["measurement"]["accepted"] == 1
    assert metric["measurement"]["rejected"] == 1
    assert metric["measurement"]["callback_interval_us"]["count"] == 1
    print("Self-test passed: marker/PID log boundary, final-log invalidation, preserved gate failures, ACK gates, injector command and cadence parser. Synthetic events only; no adb or device inputs were used.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("self-test", help="validate local parser and summary logic without adb")

    def round_options(round_parser: argparse.ArgumentParser) -> None:
        round_parser.add_argument("--condition-label", default="unspecified")
        round_parser.add_argument("--round-id", type=int, default=1)
        round_parser.add_argument("--serial", help="adb device serial; omitted for --dry-run")
        round_parser.add_argument("--package", default=APP_ID, help=f"installed application id (default: {APP_ID})")
        round_parser.add_argument("--x", type=int, default=DEFAULT_X)
        round_parser.add_argument("--y", type=int, default=DEFAULT_Y)
        round_parser.add_argument("--interval-ms", type=int, default=500)
        round_parser.add_argument("--warmup-count", type=int, default=DEFAULT_WARMUP)
        round_parser.add_argument("--count", type=int, default=DEFAULT_MEASURED)
        round_parser.add_argument("--idle-before-seconds", type=int, default=DEFAULT_IDLE_SECONDS)
        round_parser.add_argument("--cadence-tolerance-percent", type=int, default=DEFAULT_TOLERANCE_PERCENT)
        round_parser.add_argument("--injector-dex", help="remote app_process DEX path; enables the persistent injector backend")
        round_parser.add_argument("--output", default="/private/tmp/1114-physical-20261106/android-round.json")

    run_parser = subparsers.add_parser("run", help="issue one explicit device-input round and collect logs")
    round_options(run_parser)
    run_parser.add_argument("--dry-run", action="store_true", help="emit the schedule only; does not call adb")
    run_parser.add_argument("--send-inputs", action="store_true", help="required to send real tap input")
    run_parser.add_argument("--confirm-press-screen", action="store_true", help="confirm the target app is foreground on its Press screen")

    plan_parser = subparsers.add_parser("plan", help="print the legacy #1114 condition list, not results")
    plan_parser.add_argument("--json", action="store_true")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "self-test":
        self_test()
        return 0
    if args.command == "plan":
        plan = [
            {"condition": "idle", "duration_seconds": 300, "rounds": 3},
            {"condition": "normal_input", "interval_ms": 500, "taps": 500, "rounds": 3},
            {"condition": "fast_input", "interval_ms": 100, "taps": 500, "rounds": 3},
            {"condition": "other_input", "interval_ms": 500, "taps": 500, "rounds": 3},
            {"condition": "simultaneous_A_B", "A": {"interval_ms": 100, "taps": 500}, "B": {"interval_ms": 500, "taps": 100}, "rounds": 3},
            {"condition": "isolated_B_wait_known_change", "B_taps": 50, "rounds": 3},
            {"condition": "reentry_background", "background_seconds": 10, "B_taps": {"interval_ms": 500, "count": 5}, "repetitions": 10, "rounds": 3},
            {"condition": "network_disconnect", "disconnect_seconds": 15, "B_taps": {"interval_ms": 500, "count": 5}, "repetitions": 10, "rounds": 3},
        ]
        print(json.dumps({"mode": "condition_plan_only", "results": None, "conditions": plan}, indent=2, ensure_ascii=False))
        return 0
    if args.interval_ms <= 0 or args.count < 0 or args.warmup_count < 0 or args.idle_before_seconds < 0:
        raise RunnerError("interval must be positive and counts/durations cannot be negative")
    if args.warmup_count + args.count > 550:
        raise RunnerError("a fresh probe instance records at most 550 callbacks per round")
    if args.round_id < 1 or not 0 <= args.cadence_tolerance_percent < 100:
        raise RunnerError("round-id must be positive and cadence tolerance must be between 0 and 99 percent")
    if args.injector_dex and args.interval_ms < 20:
        raise RunnerError("persistent injector supports intervals of at least 20ms")
    if args.dry_run:
        print(json.dumps(make_plan(args), indent=2, ensure_ascii=False))
        return 0
    if not args.send_inputs or not args.confirm_press_screen:
        raise RunnerError("real input requires both --send-inputs and --confirm-press-screen")
    if not args.serial:
        raise RunnerError("--serial is required for a real run")
    if args.interval_ms < 200 and not args.injector_dex:
        raise RunnerError("intervals under 200ms require --injector-dex for a persistent app_process backend")
    result = run_round(args)
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result["round_integrity"]["valid_configured_measurement_round"] else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RunnerError as exc:
        print(f"android_runner: {exc}", file=sys.stderr)
        raise SystemExit(2)
