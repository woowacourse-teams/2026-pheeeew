"""Summarize sanitized PressMeasurement logcat output; refuses incomplete runs."""
import argparse
import collections
import csv
import math
import re
import sys
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("log", type=Path)
parser.add_argument(
    "--start-run", type=int, default=1,
    help="First run to analyze, numbered from 1; explicitly excludes earlier runs",
)
args = parser.parse_args()
if args.start_run < 1:
    parser.error("--start-run must be at least 1")
lines = args.log.read_text().splitlines()
events = []
for line in lines:
    match = re.match(r"\s*(\d+\.\d+).*PressMeasurement: (.*)", line)
    if match:
        events.append((float(match[1]) * 1000, match[2]))
# Inspect every run before choosing the experiment. An interrupted preliminary run
# may be excluded explicitly; incomplete selected runs must never be replaced.
runs = []
active = None
for index, (_, message) in enumerate(events):
    if message.startswith("run_start "):
        if active is not None:
            active["status"] = "interrupted by another run_start"
        active = {
            "start": index,
            "fields": dict(re.findall(r"(\w+)=([^ ]+)", message)),
            "end": None,
            "status": "missing run_end",
        }
        runs.append(active)
    elif message.startswith("run_end "):
        if active is None:
            print(f"Unpaired run_end at event {index + 1}: {message}", file=sys.stderr)
            continue
        ending = dict(re.findall(r"(\w+)=([^ ]+)", message))
        matches = all(
            key in active["fields"] and active["fields"][key] == ending.get(key)
            for key in ("pattern", "repetition")
        )
        active["end"] = index
        active["status"] = "complete" if matches else "run_end identifiers do not match run_start"
        active = None

for number, run in enumerate(runs, 1):
    fields = run["fields"]
    selection = "excluded explicitly" if number < args.start_run else "selected"
    print(
        f"Run {number}: pattern={fields.get('pattern')} repetition={fields.get('repetition')} "
        f"status={run['status']} ({selection})",
        file=sys.stderr,
    )
selected = runs[args.start_run - 1:]
if len(selected) != 9:
    parser.error(f"Expected exactly nine runs from --start-run {args.start_run}; found {len(selected)}")
if any(run["status"] != "complete" for run in selected):
    parser.error("Selected experiment contains an incomplete or mismatched run")
expected = {(pattern, str(repetition)) for pattern in ("single", "same", "mixed") for repetition in (1, 2, 3)}
actual = [(run["fields"].get("pattern"), run["fields"].get("repetition")) for run in selected]
if set(actual) != expected:
    parser.error("Expected single, same and mixed patterns with repetitions 1, 2 and 3 exactly once")
for run in selected:
    fields = run["fields"]
    if fields.get("requested") != ("1" if fields["pattern"] == "single" else "100"):
        parser.error("Expected one input for single runs and 100 inputs for same/mixed runs")

rows = []
for run in selected:
    start, end, fields = run["start"], run["end"], run["fields"]
    t0 = events[start][0]
    prior = [m for _, m in events[:start] if m.startswith("state ") and "my=null" not in m]
    baseline = int(re.search(r"my=(\d+)", prior[-1]).group(1))
    queued = collections.defaultdict(collections.deque)
    batches = {}
    delays = []
    accepted = 0
    peak = 0
    http = []
    sizes = []
    last_confirmed = t0
    input_end = None
    final_pending = None
    total = baseline
    for timestamp, message in events[start + 1:end]:
        data = dict(re.findall(r"(\w+)=([^ ]+)", message))
        if message.startswith("input "):
            accepted += 1
            queued[data['emotion']].append(timestamp)
            peak = max(peak, int(data['pending']))
        elif message.startswith("send "):
            inputs = []
            for item in data['counts'].split(';'):
                emotion, count = item.split(':')
                inputs.extend(queued[emotion].popleft() for _ in range(int(count)))
            batches[data['sequence']] = inputs
            sizes.append(len(inputs))
        elif message.startswith("result "):
            assert data['outcome'] == 'Accepted', "Failure needs separate classification"
            delays.extend(timestamp - t for t in batches.pop(data['sequence']))
            last_confirmed = timestamp
        elif message.startswith("http_end ") and data['endpoint'] == 'emotion_press_write':
            http.append(int(data['duration']))
            assert data['status'] == '200'
        elif message.startswith("input_end "):
            input_end = timestamp
            elapsed = int(data['elapsed'])
        elif message.startswith("state "):
            final_pending = int(data['pending'])
            if data['my'] != 'null':
                total = int(data['my'])
    assert input_end is not None
    assert final_pending == 0 and not batches
    assert accepted == int(fields['requested']) == len(delays) == total - baseline
    ordered = sorted(delays)
    def percentile(p):
        return round(ordered[math.ceil(len(ordered) * p) - 1])
    rows.append([fields['pattern'],fields['repetition'],accepted,elapsed,len(http),'/'.join(map(str,sizes)),peak,percentile(.5),percentile(.95),round(max(0,last_confirmed-input_end)),round(sum(http)/len(http)),total-baseline,final_pending])
writer = csv.writer(sys.stdout)
writer.writerow(['pattern','repetition','accepted','input_window_ms','http_post_attempts','batch_sizes','max_pending','confirmation_p50_ms','confirmation_p95_ms','drain_ms','mean_http_ms','personal_delta','remaining_pending'])
writer.writerows(rows)
