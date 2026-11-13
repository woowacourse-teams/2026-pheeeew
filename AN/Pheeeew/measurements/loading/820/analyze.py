"""Rebuild comparisons from saved Gradle XML stdout; never rewrites raw evidence."""
import csv
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
stages = {}
for stage in ("before", "after"):
    records = {}
    for source in ("ranking-group", "detail"):
        report = ET.parse(root / "raw" / f"{stage}-{source}.xml").getroot()
        assert report.attrib["failures"] == "0" and report.attrib["errors"] == "0"
        for line in report.findtext("system-out", "").splitlines():
            fields = line.split(",")
            if fields[0] == "LOADING_PROBE":
                _, path, delay, repeat, elapsed, calls = fields
                key = (path, int(delay), int(repeat))
                assert key not in records, key
                records[key] = (int(elapsed), int(calls))
            elif fields[0] == "DETAIL_FEED_PROBE":
                _, delay, repeat, elapsed, calls = fields
                key = ("group_detail_feed_start", int(delay), int(repeat))
                assert key not in records, key
                records[key] = (int(elapsed), int(calls))
    assert len(records) == 156, (stage, len(records))
    stages[stage] = records
assert stages["before"].keys() == stages["after"].keys()
with (root / "comparison.csv").open("w", newline="", encoding="utf-8") as output:
    writer = csv.writer(output)
    writer.writerow(["path", "source_delay_ms", "repeat", "before_ms", "after_ms", "saved_ms", "before_source_calls", "after_source_calls"])
    for key in sorted(stages["before"]):
        before_ms, before_calls = stages["before"][key]
        after_ms, after_calls = stages["after"][key]
        assert before_calls == after_calls, key
        writer.writerow([*key, before_ms, after_ms, before_ms - after_ms, before_calls, after_calls])
print("Verified 156 paired observations; source call counts unchanged.")
