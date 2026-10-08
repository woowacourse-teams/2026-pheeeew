import importlib.util
import unittest
from datetime import date
from pathlib import Path

spec = importlib.util.spec_from_file_location("report", Path(__file__).with_name("monitoring-user-report.py"))
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def event(identity, day, kind="personal_press"):
    return {"event": "meaningful_activity_day", "properties": {
        "anonymous_id": identity, "activity_date": day, "activity_type": kind,
        "environment": "prod", "measurement_version": "user_report_v1"}}


class ReportTest(unittest.TestCase):
    def test_fixture_and_retrospective_exclusion(self):
        events = [event("A", "2026-10-12"), event("A", "2026-10-13"),
                  event("A", "2026-10-12", "group_press"), event("A", "2026-10-12"),
                  event("B", "2026-10-12"), event("C", "2026-10-14"),
                  event("A", "2026-11-09"), event("D", "2026-11-09"),
                  event("I", "2026-10-12"), event("U", "2026-10-12")]
        audiences = report.classify([{"anonymous_id": i, "audience": "external"} for i in "ABCDI"]
                                   + [{"anonymous_id": "I", "audience": "internal"}])
        result = report.build_report(events, audiences, date(2026, 10, 12), date(2026, 11, 15), date(2026, 11, 16))
        first, last = result["weeks"][0], result["weeks"][-1]
        self.assertEqual(3, first["wau"])
        self.assertEqual(1 / 3, first["repeat_rate"])
        self.assertEqual(1 / 3, first["retention"]["W+4"]["rate"])
        self.assertEqual(1, first["unknown_users"])
        self.assertEqual(2, last["wau"])

    def test_known_test_event_cannot_be_included_by_external_registry(self):
        item = event("QA", "2026-10-12")
        item["properties"]["audience"] = "test"
        result = report.build_report([event("QA", "2026-10-13"), item], {"QA": "external"},
                                     date(2026, 10, 12), date(2026, 10, 18), date(2026, 10, 19))
        self.assertEqual(0, result["weeks"][0]["wau"])

    def test_incomplete_and_zero_denominator_are_na(self):
        result = report.build_report([], {}, date(2026, 10, 12), date(2026, 10, 14), date(2026, 10, 14))
        first = result["weeks"][0]
        self.assertFalse(first["complete"])
        self.assertIsNone(first["repeat_rate"])
        self.assertIsNone(first["retention"]["W+4"]["rate"])


if __name__ == "__main__":
    unittest.main()
