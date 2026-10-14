"""Summarize sanitized PressMeasurement logcat output; refuses incomplete runs."""
import collections
import csv
import math
import re
import sys
from pathlib import Path

lines = Path(sys.argv[1]).read_text().splitlines()
events = []
for line in lines:
    match = re.match(r"\s*(\d+\.\d+).*PressMeasurement: (.*)", line)
    if match:
        events.append((float(match[1]) * 1000, match[2]))
starts = [i for i, (_, msg) in enumerate(events) if msg.startswith("run_start ")]
starts = starts[-9:]
assert len(starts) == 9, "Expected nine runs"
rows = []
for start in starts:
    t0, msg = events[start]
    fields = dict(re.findall(r"(\w+)=([^ ]+)", msg))
    end = next(i for i in range(start + 1, len(events)) if events[i][1].startswith("run_end "))
    assert not any(m.startswith("run_start ") for _, m in events[start + 1:end]), "Incomplete run"
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
