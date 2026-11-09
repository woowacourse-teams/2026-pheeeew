#!/usr/bin/env python3
"""Summarize callback time and OS vibration records; never infer physical vibration."""
import argparse
import collections
import datetime
import json
import math
import re
import statistics
from pathlib import Path


def summarize(values):
    values = sorted(values)
    if not values:
        return {"n": 0}
    return {
        "n": len(values), "medianMs": statistics.median(values),
        "p95NearestRankMs": values[math.ceil(len(values) * 0.95) - 1],
        "minMs": values[0], "maxMs": values[-1],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("session", type=Path)
    args = parser.parse_args()
    root = args.session
    metadata = json.loads((root / "metadata.json").read_text())
    device_time = datetime.datetime.strptime(metadata["deviceTime"], "%Y-%m-%dT%H:%M:%S%z")
    pattern = re.compile(r"startTime: ([^,]+), endTime: ([^,]+), status: ([^,]+),.*?uid: (\d+), opPkg: ([^,]+),")

    def epoch(value):
        return datetime.datetime.strptime(f"{device_time.year}-{value}", "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=device_time.tzinfo).timestamp() * 1000

    groups = collections.defaultdict(lambda: {"rows": [], "events": [], "intervals": []})
    observed_events = []
    run_results = []
    unmatched = []
    idle_snapshots = []
    for run in metadata["runs"]:
        if not run["success"]:
            continue
        folder = root / run["id"]
        data = json.loads((folder / "result.json").read_text())
        rows = data["rows"]
        if run["phase"] == "core":
            for snapshot in sorted(folder.glob("*-vibrator.txt")):
                if not snapshot.name.startswith(("screen-exit-", "background-")):
                    continue
                raw_state = snapshot.read_text()
                current_empty = bool(re.search(r"mCurrentVibration:\s*null", raw_state))
                next_empty = bool(re.search(r"mNextVibration:\s*null", raw_state))
                idle_snapshots.append({"run": run["id"], "file": snapshot.name, "currentEmpty": current_empty, "nextEmpty": next_empty})
        backend = run["backend"]
        unique = {}
        for snapshot in sorted(folder.glob("*-vibrator.txt")):
            for line in snapshot.read_text().splitlines():
                match = pattern.search(line)
                if not match or match.group(5) != data["package"]:
                    continue
                start, end, status, uid, package = match.groups()
                try:
                    start_ms = epoch(start)
                    end_ms = epoch(end) if end != "null" else None
                except ValueError:
                    continue
                key = (start, uid, package)
                event = {
                    "start": start, "end": end, "startEpochMillis": start_ms, "status": status,
                    "serviceLifetimeMs": end_ms - start_ms if end_ms else None,
                    "uid": uid, "package": package, "record": line.strip(),
                }
                # Prefer completed records if an intermediate status was captured too.
                if key not in unique or end_ms is not None:
                    unique[key] = event
        if not rows:
            continue
        low = min(r["wallTimeMillis"] - r["callbackNanos"] / 1e6 for r in rows) - 5
        high = max(r["wallTimeMillis"] for r in rows) + 25
        events = [e for e in unique.values() if low <= e["startEpochMillis"] <= high]
        for event in events:
            eligible = [r for r in rows if r["wallTimeMillis"] - r["callbackNanos"] / 1e6 - 5 <= event["startEpochMillis"] <= r["wallTimeMillis"] + (25 if r.get("measurementKind") == "project-pad-admission" else 5)]
            if not eligible:
                unmatched.append({"run": run["id"], **event})
                continue
            row = min(eligible, key=lambda r: abs(r["wallTimeMillis"] - event["startEpochMillis"]))
            scenario = "burst" if row["scenario"].startswith("burst-") else row["scenario"]
            event.update({"run": run["id"], "scenario": scenario})
            groups[backend, scenario]["events"].append(event)
            observed_events.append(event)
        by_scenario = collections.defaultdict(list)
        for row in rows:
            scenario = "burst" if row["scenario"].startswith("burst-") else row["scenario"]
            groups[backend, scenario]["rows"].append(row)
            by_scenario[row["scenario"]].append(row)
        for name, samples in by_scenario.items():
            scenario = "burst" if name.startswith("burst-") else name
            groups[backend, scenario]["intervals"].extend((b["startElapsedNanos"] - a["startElapsedNanos"]) / 1e6 for a, b in zip(samples, samples[1:]))
        run_results.append({"id": run["id"], "rows": len(rows), "serviceRecords": len(events)})

    summary = {}
    for (backend, scenario), group in sorted(groups.items()):
        rows, events = group["rows"], group["events"]
        summary.setdefault(backend, {})[scenario] = {
            "inputs": len(rows),
            "hapticCallbacks": sum(r["hapticCallbackInvoked"] is True for r in rows),
            "visualCallbacks": sum(r["visualFeedbackInvoked"] is True for r in rows),
            "serviceRecords": len(events),
            "serviceStatuses": dict(collections.Counter(e["status"] for e in events)),
            "callbackTime": summarize([r["callbackNanos"] / 1e6 for r in rows if r["hapticCallbackInvoked"]]),
            "serviceLifetime": summarize([e["serviceLifetimeMs"] for e in events if e["serviceLifetimeMs"] is not None]),
            "observedInputIntervals": summarize(group["intervals"]),
        }
    result = {
        "device": {k: metadata[k] for k in ["model", "android", "sdk", "deviceTime"]},
        "successfulInstrumentationRuns": len(run_results), "runs": run_results,
        "originalPackageIdentityUnchanged": metadata.get("originalPackageIdentityUnchanged"),
        "hapticSettingRestored": metadata.get("hapticSettingRestored"),
        "metrics": summary, "unmatchedServiceRecords": unmatched,
        "idleSnapshots": idle_snapshots,
        "limitations": [
            "callbackTime is synchronous callback execution measured with elapsedRealtimeNanos, not physical onset latency",
            "serviceLifetime is OS bookkeeping end minus start, not measured motor motion duration",
            "service events are deduplicated by start/uid/package; callback rows allow 5ms tolerance and actual EmotionPad admission rows allow 25ms after admission",
            "only successful instrumentation runs in this session are included",
            "observedInputIntervals are application callback/admission intervals, not physical touch or injector DOWN intervals",
        ],
    }
    (root / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    (root / "matched-service-records.json").write_text(json.dumps(observed_events, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
