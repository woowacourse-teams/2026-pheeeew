#!/usr/bin/env python3
"""Host-side observer for proxy signals and iOS WS diagnostic log lines.

The collector follows an existing ``simctl launch --console`` output file.
Both command/signal timestamps and app-line observation timestamps use this
process's monotonic clock. The measurement ends when this process reads the
diagnostic line, so it includes app logging, file visibility, and polling delay.
It does not compare clocks from the app and proxy processes.
An in-place truncate followed by regrowth beyond the prior size between polls
may escape file-integrity detection.

Type ``offline`` / ``restore`` to signal the verified dev_connect_proxy.py
process, and ``quit`` to stop capture. If stdin reaches EOF, capture continues;
use Ctrl-C to stop it. The proxy is left running when capture exits.
"""

import argparse
import os
import queue
import re
import shlex
import signal
import subprocess
import sys
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace


MARKER = "[PressRaceMeasure]"
FIELD = re.compile(r"\b([A-Za-z][A-Za-z0-9_]*)=([^\s]+)")
ALLOWED_APP_FIELDS = (
    "source", "kind", "sample", "ms", "observedAt", "path",
    "pathAtSocketLoss", "presses", "failedAttempts", "outcome",
)
DEFAULT_POLL_INTERVAL_MS = 20
LOG_ERROR_EXIT_CODE = 2


def host_ns():
    """Return the one monotonic clock used for command and file observations."""
    return time.monotonic_ns()


def elapsed_ms(start_ns, end_ns):
    """Return a nonnegative elapsed duration, or None for reversed stamps."""
    if end_ns < start_ns:
        return None
    return (end_ns - start_ns) / 1_000_000


def matches_proxy_script(command_line, expected_script, cwd=None):
    """Check the actual executable/script argument, not arbitrary later text."""
    expected = Path(expected_script).resolve()
    base = Path(cwd or os.getcwd())
    try:
        tokens = shlex.split(command_line)
    except ValueError:
        return False
    if tokens and Path(tokens[0]).name == expected.name:
        # Direct shebang execution: the script itself is the process command.
        direct = Path(tokens[0])
        if not direct.is_absolute():
            direct = base / direct
        return direct.resolve() == expected

    # Python execution: the interpreter must be argv[0]. Parse options until
    # the first positional script; never accept a filename in a later argument.
    if not tokens or not Path(tokens[0]).name.lower().startswith("python"):
        return False
    index = 1
    no_value_options = {
        "-b", "-bb", "-B", "-d", "-E", "-h", "-i", "-I", "-O", "-OO",
        "-P", "-q", "-R", "-s", "-S", "-u", "-v", "-V", "-x",
    }
    while index < len(tokens):
        option = tokens[index]
        if option in ("-c", "-m"):
            return False
        if option == "--":
            index += 1
            break
        if option in no_value_options or (option.startswith(("-W", "-X")) and len(option) > 2):
            index += 1
            continue
        if option in ("-W", "-X", "--check-hash-based-pycs"):
            index += 2
            continue
        if option.startswith("-"):
            return False
        break
    if index >= len(tokens):
        return False
    candidate = Path(tokens[index])
    if not candidate.is_absolute():
        candidate = base / candidate
    try:
        return candidate.resolve() == expected
    except OSError:
        return False


def parse_process_identity(ps_line):
    """Parse ``ps -o lstart= -o command=`` into a stable start stamp and argv."""
    parts = ps_line.split(None, 5)
    if len(parts) != 6:
        return None
    start_stamp = " ".join(parts[:5])
    command_line = parts[5]
    if not command_line:
        return None
    return start_stamp, command_line


def identity_matches(expected_identity, current_identity, expected_script):
    """Reject changed PID start time/argv and require the exact proxy script."""
    return (
        expected_identity == current_identity
        and matches_proxy_script(current_identity[1], expected_script)
    )


def file_integrity_fault(opened_stat, current_stat, position, last_size):
    """Identify replaced/rotated files and observed truncation."""
    if (opened_stat.st_dev, opened_stat.st_ino) != (current_stat.st_dev, current_stat.st_ino):
        return "rotation"
    if current_stat.st_size < last_size or position > current_stat.st_size:
        return "truncation"
    return None


def pending_window_status(pending_window, invalid_reason=None):
    """Return final state for an unfinished observer window."""
    if pending_window is None:
        return None
    if invalid_reason:
        return "observer_measurement_invalidated"
    return "observer_window_ended_without_kind_line"


def readiness_result(event_kind):
    """Gate capture controls until the tail file has been opened and positioned."""
    if event_kind == "log_ready":
        return "ready"
    if event_kind in ("log_error", "log_fault"):
        return "error"
    return "wait"


def log_capture_exit_code(event_kind):
    """Log read/integrity failures must produce a nonzero process status."""
    if event_kind in ("log_error", "log_fault", "log_truncated"):
        return LOG_ERROR_EXIT_CODE
    return 0


def parse_app_fields(line):
    """Return only approved, sanitized diagnostic fields; never return raw text."""
    allowed = set(ALLOWED_APP_FIELDS)
    values = {}
    for key, value in FIELD.findall(line):
        if key in allowed and key not in values:
            values[key] = re.sub(r"[^A-Za-z0-9_.:/+-]", "_", value)[:64]
    return values


def observer_measurement(start_ns, observed_ns):
    """Create an observer interval without claiming causal event timing."""
    duration = elapsed_ms(start_ns, observed_ns)
    if duration is None:
        return None
    return {
        "observer_interval_ms": duration,
        "causal_link": "unknown",
        "bound_claim": "none",
        "app_generation": "unknown",
    }


def stop_requested(event_kind, value):
    """Return whether an input/log event explicitly ends capture."""
    return (event_kind == "command" and value == "quit") or event_kind == "log_error"


def process_identity(pid):
    """Read PID start time and argv together; never signal it here."""
    result = subprocess.run(
        ["ps", "-p", str(pid), "-o", "lstart=", "-o", "command="],
        check=False, capture_output=True, text=True,
    )
    if result.returncode != 0 or not result.stdout.strip():
        raise RuntimeError(f"could not inspect PID {pid} with ps: {result.stderr.strip()}")
    identity = parse_process_identity(result.stdout.strip())
    if identity is None:
        raise RuntimeError(f"could not parse PID {pid} start time and command from ps")
    return identity


def pump_log_file(path, events, stop_event, poll_interval_s):
    """Follow appended lines, timestamping each line when read by this process."""
    try:
        with path.open("rb") as stream:
            opened_stat = os.fstat(stream.fileno())
            stream.seek(0, os.SEEK_END)
            initial_position = stream.tell()
            try:
                initial_path_stat = path.stat()
            except FileNotFoundError:
                events.put(("log_error", host_ns(), f"log file disappeared: {path}"))
                return
            fault = file_integrity_fault(
                opened_stat, initial_path_stat, initial_position, initial_path_stat.st_size,
            )
            if fault:
                events.put(("log_fault", host_ns(), fault))
                return
            last_size = initial_path_stat.st_size
            # The main thread must observe this before announcing capture or
            # starting stdin control, so every accepted command has an open tail.
            events.put(("log_ready", host_ns(), initial_position))
            while not stop_event.is_set():
                line = stream.readline()
                if line:
                    try:
                        current_stat = path.stat()
                    except FileNotFoundError:
                        events.put(("log_error", host_ns(), f"log file disappeared: {path}"))
                        return
                    fault = file_integrity_fault(
                        opened_stat, current_stat, stream.tell(), last_size,
                    )
                    if fault:
                        events.put(("log_fault", host_ns(), fault))
                        return
                    last_size = current_stat.st_size
                    events.put(("app", host_ns(), line.rstrip(b"\r\n").decode("utf-8", errors="replace")))
                    continue

                try:
                    current_stat = path.stat()
                except FileNotFoundError:
                    events.put(("log_error", host_ns(), f"log file disappeared: {path}"))
                    return
                fault = file_integrity_fault(
                    opened_stat, current_stat, stream.tell(), last_size,
                )
                if fault:
                    events.put(("log_fault", host_ns(), fault))
                    return
                last_size = current_stat.st_size
                stop_event.wait(poll_interval_s)
    except OSError as exc:
        events.put(("log_error", host_ns(), f"cannot read {path}: {exc}"))


def pump_commands(events):
    for line in sys.stdin:
        events.put(("command", host_ns(), line.strip().lower()))
    # EOF is informational. Capture remains active until `quit` or Ctrl-C.
    events.put(("command_eof", host_ns(), ""))


def run_self_tests():
    """Check clock arithmetic, reversed timestamps, PID matching, and EOF policy."""

    class CollectorTests(unittest.TestCase):
        def test_elapsed_ms_uses_monotonic_nanosecond_difference(self):
            self.assertEqual(elapsed_ms(5_000_000_000, 5_125_000_000), 125.0)

        def test_reversed_observation_is_not_a_duration(self):
            self.assertIsNone(elapsed_ms(5_000_000_001, 5_000_000_000))

        def test_proxy_pid_command_matches_only_expected_script(self):
            expected = Path("/tmp/evidence/tools/dev_connect_proxy.py")
            self.assertTrue(matches_proxy_script(
                "python3 -u /tmp/evidence/tools/dev_connect_proxy.py", expected, "/tmp",
            ))
            self.assertTrue(matches_proxy_script(
                "/tmp/evidence/tools/dev_connect_proxy.py", expected, "/tmp",
            ))
            self.assertFalse(matches_proxy_script(
                "python3 /tmp/evidence/tools/other_proxy.py", expected, "/tmp",
            ))
            self.assertFalse(matches_proxy_script(
                "python3 dev_connect_proxy.py", expected, "/var/tmp",
            ))
            self.assertFalse(matches_proxy_script(
                "python3 -c 'open(\"/tmp/evidence/tools/dev_connect_proxy.py\")'", expected, "/tmp",
            ))
            self.assertFalse(matches_proxy_script(
                "python3 -W /tmp/evidence/tools/dev_connect_proxy.py", expected, "/tmp",
            ))

        def test_process_identity_parser_keeps_start_stamp_and_argv(self):
            identity = parse_process_identity("Wed Nov 5 12:34:56 2026 python3 -u /tmp/evidence/tools/dev_connect_proxy.py")
            self.assertEqual(identity, (
                "Wed Nov 5 12:34:56 2026",
                "python3 -u /tmp/evidence/tools/dev_connect_proxy.py",
            ))
            self.assertTrue(identity_matches(
                identity, identity, Path("/tmp/evidence/tools/dev_connect_proxy.py"),
            ))
            reused_pid = ("Wed Nov 5 12:35:01 2026", identity[1])
            self.assertFalse(identity_matches(
                identity, reused_pid, Path("/tmp/evidence/tools/dev_connect_proxy.py"),
            ))

        def test_file_rotation_and_truncation_are_integrity_faults(self):
            original = SimpleNamespace(st_dev=1, st_ino=10, st_size=500)
            same_file = SimpleNamespace(st_dev=1, st_ino=10, st_size=600)
            rotated = SimpleNamespace(st_dev=1, st_ino=11, st_size=600)
            truncated = SimpleNamespace(st_dev=1, st_ino=10, st_size=400)
            self.assertIsNone(file_integrity_fault(original, same_file, 500, 500))
            self.assertEqual(file_integrity_fault(original, rotated, 500, 500), "rotation")
            self.assertEqual(file_integrity_fault(original, truncated, 450, 500), "truncation")
            regrown_copytruncate = SimpleNamespace(st_dev=1, st_ino=10, st_size=700)
            self.assertIsNone(file_integrity_fault(original, regrown_copytruncate, 500, 500))
            # A truncate followed by regrowth beyond the previous size between polls is not observable.
            pending = {"window": 2}
            self.assertEqual(pending_window_status(pending, "rotation"), "observer_measurement_invalidated")
            self.assertEqual(pending_window_status(pending, "truncation"), "observer_measurement_invalidated")
            self.assertEqual(pending_window_status(pending), "observer_window_ended_without_kind_line")

        def test_diagnostic_output_is_allowlisted_and_sanitized(self):
            fields = parse_app_fields(
                "[PressRaceMeasure] source=WebSocket kind=SocketLossDetected sample=4 "
                "ms=0 path=Available authToken=secret value=private"
            )
            self.assertEqual(fields, {
                "source": "WebSocket", "kind": "SocketLossDetected", "sample": "4",
                "ms": "0", "path": "Available",
            })

        def test_observer_interval_never_claims_causality_or_bound(self):
            measurement = observer_measurement(1_000_000, 3_000_000)
            self.assertEqual(measurement["observer_interval_ms"], 2.0)
            self.assertEqual(measurement["causal_link"], "unknown")
            self.assertEqual(measurement["bound_claim"], "none")
            self.assertEqual(measurement["app_generation"], "unknown")
            self.assertIsNone(observer_measurement(3_000_000, 1_000_000))

        def test_stdin_eof_does_not_stop_capture(self):
            self.assertFalse(stop_requested("command_eof", ""))
            self.assertTrue(stop_requested("command", "quit"))
            self.assertTrue(stop_requested("log_error", "cannot read file"))

        def test_capture_waits_for_log_ready_and_fails_early_on_error(self):
            self.assertEqual(readiness_result("command"), "wait")
            self.assertEqual(readiness_result("log_ready"), "ready")
            self.assertEqual(readiness_result("log_error"), "error")
            self.assertEqual(readiness_result("log_fault"), "error")

        def test_log_integrity_errors_return_nonzero_exit_status(self):
            for kind in ("log_error", "log_fault", "log_truncated"):
                self.assertEqual(log_capture_exit_code(kind), LOG_ERROR_EXIT_CODE)
            self.assertEqual(log_capture_exit_code("app"), 0)

    suite = unittest.defaultTestLoader.loadTestsFromTestCase(CollectorTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true", help="run pure local checks and exit")
    parser.add_argument("--proxy-pid", type=int, help="PID of the running dev_connect_proxy.py")
    parser.add_argument("--log-file", type=Path,
                        help="existing file receiving simctl launch --console output")
    parser.add_argument("--poll-interval-ms", type=int, default=DEFAULT_POLL_INTERVAL_MS,
                        help=f"file polling interval (default: {DEFAULT_POLL_INTERVAL_MS} ms)")
    args = parser.parse_args()

    if args.self_test:
        return run_self_tests()
    if args.proxy_pid is None or args.log_file is None:
        parser.error("--proxy-pid and --log-file are required unless --self-test is used")
    if args.proxy_pid <= 0:
        parser.error("--proxy-pid must be positive")
    if args.poll_interval_ms < 1:
        parser.error("--poll-interval-ms must be at least 1")

    log_file = args.log_file.expanduser().resolve()
    if not log_file.is_file():
        parser.error(f"--log-file must name an existing file: {log_file}")

    expected_proxy = Path(__file__).with_name("dev_connect_proxy.py").resolve()
    try:
        proxy_identity = process_identity(args.proxy_pid)
    except RuntimeError as exc:
        parser.error(str(exc))
    if not identity_matches(proxy_identity, proxy_identity, expected_proxy):
        parser.error(
            f"PID {args.proxy_pid} is not the expected proxy script; "
            f"expected script={str(expected_proxy)!r}"
        )
    try:
        os.kill(args.proxy_pid, 0)
    except OSError as exc:
        parser.error(f"proxy PID {args.proxy_pid} is not signalable: {exc}")

    events = queue.Queue()
    stop_event = threading.Event()
    poll_interval_s = args.poll_interval_ms / 1000
    log_thread = threading.Thread(
        target=pump_log_file,
        args=(log_file, events, stop_event, poll_interval_s),
        daemon=True,
    )
    log_thread.start()

    # Do not announce capture or accept proxy controls until open+seek has
    # completed. Errors before readiness return a failure without signaling.
    while True:
        ready_kind, ready_ns, ready_value = events.get()
        readiness = readiness_result(ready_kind)
        if readiness == "ready":
            break
        if readiness == "error":
            if ready_kind == "log_fault":
                print(f"error=log_integrity_before_ready fault={ready_value} signal_capture_started=false", flush=True)
            else:
                print("error=log_file_before_ready signal_capture_started=false", flush=True)
            stop_event.set()
            log_thread.join(timeout=1)
            return LOG_ERROR_EXIT_CODE

    print(f"event=log_reader_ready host_monotonic_ns={ready_ns} initial_position=EOF", flush=True)
    print("capture=started clock=host_monotonic_ns", flush=True)
    print(f"log_file={log_file} initial_position_bytes={ready_value}", flush=True)
    print(f"poll_interval_ms={args.poll_interval_ms}", flush=True)
    print("commands=offline,restore,quit", flush=True)
    print(
        "limits=observer_interval_only; includes signal delivery, app logging, "
        "file visibility, and polling delay; causality=unknown; bound_claim=none; "
        "app_generation=unknown; pid_reuse_race=possible_between_identity_check_and_signal; "
        "file_integrity_limit=rapid_copytruncate_regrowth_may_escape_detection",
        flush=True,
    )
    threading.Thread(target=pump_commands, args=(events,), daemon=True).start()

    window = 0
    observer_generation = 0
    offline_generation = None
    offline_ns = None
    loss_observed_ns = None
    pending_restore = None
    invalid_reason = None
    stdin_eof_reported = False
    exit_reason = "quit"
    exit_code = 0

    try:
        while True:
            kind, input_observed_ns, value = events.get()
            if kind == "command":
                if stop_requested(kind, value):
                    break
                if value not in ("offline", "restore"):
                    if value:
                        print(f"error=unknown_command value={value!r}", flush=True)
                    continue

                try:
                    current_identity = process_identity(args.proxy_pid)
                    if not identity_matches(proxy_identity, current_identity, expected_proxy):
                        print(
                            f"error=proxy_pid_identity_changed pid={args.proxy_pid} "
                            "signal_not_sent=true",
                            flush=True,
                        )
                        continue
                    os.kill(args.proxy_pid, 0)
                except (OSError, RuntimeError) as exc:
                    print(f"error=proxy_pid_not_signalable pid={args.proxy_pid} detail={exc}", flush=True)
                    continue
                sig = signal.SIGUSR1 if value == "offline" else signal.SIGUSR2
                # The elapsed interval starts immediately before signal delivery.
                signal_send_ns = host_ns()
                try:
                    os.kill(args.proxy_pid, sig)
                except OSError as exc:
                    print(
                        f"error=proxy_signal command={value} pid={args.proxy_pid} "
                        f"detail={exc}",
                        flush=True,
                    )
                    continue
                dispatch_delay = elapsed_ms(input_observed_ns, signal_send_ns)
                print(
                    f"event={value}_signal_sent command_input_host_ns={input_observed_ns} "
                    f"signal_send_host_ns={signal_send_ns} "
                    f"input_to_signal_send_ms={dispatch_delay:.3f}",
                    flush=True,
                )

                if value == "offline":
                    if pending_restore is not None:
                        print(
                            f"status=observer_window_closed_without_kind_line "
                            f"window={pending_restore['window']} "
                            "reason=next_offline_signal",
                            flush=True,
                        )
                    observer_generation += 1
                    offline_generation = observer_generation
                    offline_ns = signal_send_ns
                    loss_observed_ns = None
                    pending_restore = None
                    print(
                        f"event=offline_observer_generation_started generation={offline_generation} "
                        "generation_scope=host_observer_only app_generation=unknown",
                        flush=True,
                    )
                else:
                    if pending_restore is not None:
                        print(
                            f"status=observer_window_closed_without_kind_line "
                            f"window={pending_restore['window']} "
                            "reason=next_restore_signal",
                            flush=True,
                        )
                    window += 1
                    pending_restore = {
                        "window": window,
                        "observer_generation": offline_generation,
                        "signal_ns": signal_send_ns,
                        "loss_observed_before_restore": (
                            loss_observed_ns is not None and loss_observed_ns <= signal_send_ns
                        ),
                    }
                    generation_text = str(offline_generation) if offline_generation is not None else "none"
                    print(
                        f"event=restore_observer_window_started window={window} "
                        f"observer_generation={generation_text} "
                        "app_generation=unknown generation_correlation=unavailable",
                        flush=True,
                    )
                continue

            if kind == "command_eof":
                if not stdin_eof_reported:
                    print(
                        "input=stdin_eof commands_disabled=true capture_continues=true "
                        "stop_with=SIGINT",
                        flush=True,
                    )
                    stdin_eof_reported = True
                continue
            if kind == "log_error":
                print("error=log_file_read signal_capture_stopped=true", flush=True)
                exit_reason = "log_error"
                invalid_reason = "log_read_error"
                exit_code = log_capture_exit_code(kind)
                break
            if kind == "log_fault":
                print(f"error=log_integrity fault={value} signal_capture_stopped=true", flush=True)
                exit_reason = f"log_{value}"
                invalid_reason = f"log_{value}"
                exit_code = log_capture_exit_code(kind)
                break
            if kind == "log_truncated":
                print("error=log_truncated signal_capture_stopped=true", flush=True)
                exit_reason = "log_truncated"
                invalid_reason = "log_truncated"
                exit_code = log_capture_exit_code(kind)
                break
            if kind != "app":
                continue
            if MARKER not in value:
                continue

            fields = parse_app_fields(value)
            if not fields:
                print(f"status=diagnostic_line_without_allowlisted_fields host_monotonic_ns={input_observed_ns}", flush=True)
                continue
            fields_text = ",".join(f"{key}={fields[key]}" for key in ALLOWED_APP_FIELDS if key in fields)
            app_kind = fields.get("kind")
            print(
                f"event=app_diagnostic_log_observed host_monotonic_ns={input_observed_ns} "
                f"fields={fields_text}",
                flush=True,
            )
            if app_kind == "SocketLossDetected" and offline_ns is not None and input_observed_ns >= offline_ns:
                loss_observed_ns = input_observed_ns
                offline_measurement = observer_measurement(offline_ns, input_observed_ns)
                if offline_measurement is not None:
                    generation_text = str(offline_generation) if offline_generation is not None else "none"
                    print(
                        f"event=offline_to_loss_kind_log_observed "
                        f"observer_generation={generation_text} "
                        f"observer_interval_ms={offline_measurement['observer_interval_ms']:.3f} "
                        "loss_kind=SocketLossDetected causal_link=unknown bound_claim=none "
                        "app_generation=unknown fields="
                        + fields_text,
                        flush=True,
                    )
                if pending_restore is not None and input_observed_ns <= pending_restore["signal_ns"]:
                    pending_restore["loss_observed_before_restore"] = True
            if app_kind == "SocketLossToValidatedBaseline" and pending_restore is not None:
                measurement = observer_measurement(pending_restore["signal_ns"], input_observed_ns)
                if measurement is None:
                    print(
                        f"status=kind_line_observed_before_restore window={pending_restore['window']} "
                        f"host_observed_ns={input_observed_ns}",
                        flush=True,
                    )
                    continue
                generation = pending_restore["observer_generation"]
                generation_text = str(generation) if generation is not None else "none"
                loss_flag = str(pending_restore["loss_observed_before_restore"]).lower()
                print(
                    f"event=restore_to_app_kind_log_observed window={pending_restore['window']} "
                    f"observer_generation={generation_text} "
                    f"restore_signal_host_ns={pending_restore['signal_ns']} "
                    f"app_log_line_host_observed_ns={input_observed_ns} "
                    f"observer_interval_ms={measurement['observer_interval_ms']:.3f} "
                    f"loss_kind_observed_before_restore={loss_flag} "
                    f"app_kind={app_kind} causal_link=unknown bound_claim=none "
                    "app_generation=unknown generation_correlation=unavailable "
                    f"fields={fields_text}",
                    flush=True,
                )
                pending_restore = None
    except KeyboardInterrupt:
        exit_reason = "keyboard_interrupt"
    finally:
        stop_event.set()
        pending_status = pending_window_status(pending_restore, invalid_reason)
        if pending_status:
            reason = f" reason={invalid_reason}" if invalid_reason else ""
            print(f"status={pending_status} window={pending_restore['window']}{reason}", flush=True)
        print(f"capture=stopped reason={exit_reason} proxy_left_running=true", flush=True)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
