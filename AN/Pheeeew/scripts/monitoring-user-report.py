#!/usr/bin/env python3
"""Build report metrics from PostHog JSONL events and a private audience CSV.

JSONL rows: {"event": "meaningful_activity_day", "properties": {...}}.
CSV columns: anonymous_id,audience (external/internal/test/unknown).
Coverage dates are inclusive; declare only dates with verified collection.
"""
import argparse
import csv
import json
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path


def monday(day):
    return day - timedelta(days=day.weekday())


def classify(rows):
    """Exclusion wins even if the private registry contains conflicting rows."""
    rank = {"external": 0, "unknown": 1, "test": 2, "internal": 3}
    result = {}
    for row in rows:
        identity, audience = row["anonymous_id"].strip(), row["audience"].strip()
        if not identity or audience not in rank:
            raise ValueError("Invalid classification row")
        prior = result.get(identity)
        if prior is None or rank[audience] > rank[prior]:
            result[identity] = audience
    return result


def build_report(events, audiences, start, through, as_of):
    if through < start or through > as_of:
        raise ValueError("Coverage must end between start and as_of")
    active = defaultdict(lambda: defaultdict(set))
    unknown = defaultdict(set)
    excluded = {identity for identity, audience in audiences.items() if audience in {"internal", "test"}}
    for row in events:
        props = row.get("properties", {})
        if isinstance(props, str):
            props = json.loads(props)
        if (row.get("event") != "meaningful_activity_day"
                or props.get("environment") != "prod"
                or props.get("measurement_version") != "user_report_v1"):
            continue
        if props.get("activity_type") not in {"emotion_record", "personal_press", "group_press"}:
            raise ValueError("Invalid activity type")
        identity = props.get("anonymous_id")
        if not identity:
            raise ValueError("Missing anonymous_id")
        if props.get("audience") in {"internal", "test"}:
            excluded.add(identity)
        day = date.fromisoformat(props["activity_date"])
        if day < start or day > through:
            continue
        # Registry is authoritative, including retrospective exclusions.
        audience = audiences.get(identity, "unknown")
        week = monday(day)
        if audience == "unknown":
            unknown[week].add(identity)
        elif audience == "external":
            active[week][identity].add(day)

    for users in active.values():
        for identity in excluded:
            users.pop(identity, None)
    for users in unknown.values():
        users.difference_update(excluded)

    def complete(week):
        return week >= start and week + timedelta(days=6) <= through and week + timedelta(days=7) <= as_of

    weeks = []
    week = monday(start)
    while week <= through:
        users = active[week]
        repeated = sum(len(days) >= 2 for days in users.values())
        retention = {}
        for k in range(1, 5):
            target = week + timedelta(weeks=k)
            numerator = len(set(users) & set(active[target])) if complete(week) and complete(target) else None
            retention[f"W+{k}"] = {
                "returning": numerator,
                "rate": numerator / len(users) if numerator is not None and users else None,
            }
        weeks.append({
            "week_start": week.isoformat(), "complete": complete(week),
            "wau": len(users), "repeat_users": repeated,
            "repeat_rate": repeated / len(users) if users else None,
            "unknown_users": len(unknown[week]), "retention": retention,
        })
        week += timedelta(weeks=1)
    return {"measurement_version": "user_report_v1", "timezone": "Asia/Seoul",
            "coverage_start": start.isoformat(), "coverage_through": through.isoformat(),
            "as_of": as_of.isoformat(), "weeks": weeks}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("events", type=Path)
    parser.add_argument("audiences", type=Path)
    parser.add_argument("--coverage-start", type=date.fromisoformat, required=True)
    parser.add_argument("--coverage-through", type=date.fromisoformat, required=True)
    parser.add_argument("--as-of", type=date.fromisoformat, required=True, help="Current KST date")
    args = parser.parse_args()
    with args.audiences.open() as handle:
        audiences = classify(csv.DictReader(handle))
    with args.events.open() as handle:
        report = build_report((json.loads(line) for line in handle if line.strip()), audiences,
                              args.coverage_start, args.coverage_through, args.as_of)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
