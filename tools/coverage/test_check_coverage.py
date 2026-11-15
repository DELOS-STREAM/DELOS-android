#!/usr/bin/env python3
"""Tests of check_coverage.py. Run: python3 -m unittest discover -s tools/coverage"""

import io
import json
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import check_coverage  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures")
REPORT = os.path.join(FIXTURES, "report.xml")
REPORT_DROPPED = os.path.join(FIXTURES, "report_dropped.xml")
REPORT_NEW_PACKAGE = os.path.join(FIXTURES, "report_new_package.xml")


class CheckCoverageTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.dir)
        self.baseline = os.path.join(self.dir, "baseline.json")

    def run_main(self, *args):
        out = io.StringIO()
        code = check_coverage.main(list(args), out=out)
        return code, out.getvalue()

    def write_floors(self, overall, packages):
        with open(self.baseline, "w", encoding="utf-8") as handle:
            json.dump({"overall": overall, "packages": packages}, handle)

    def read_floors(self):
        with open(self.baseline, encoding="utf-8") as handle:
            return json.load(handle)

    # ---- reading the report ----

    def test_report_is_read_per_package_and_overall(self):
        measured = check_coverage.read_report(REPORT)
        self.assertEqual({"line": (55, 100), "branch": (15, 40)}, measured["overall"])
        self.assertEqual({"line": (45, 60), "branch": (15, 30)}, measured["com.example.alpha"])
        self.assertEqual({"line": (10, 30), "branch": (0, 10)}, measured["com.example.beta"])
        # A package without branches or lines has a zero total, not an error.
        self.assertEqual({"line": (0, 10), "branch": (0, 0)}, measured["com.example.empty"])

    def test_class_level_counters_are_not_mistaken_for_package_counters(self):
        # alpha's class counter says 1/1; the package counter says 45/60.
        self.assertEqual((45, 60), check_coverage.read_report(REPORT)["com.example.alpha"]["line"])

    def test_floor_percent_rounds_down_and_handles_empty(self):
        self.assertEqual(66, check_coverage.floor_percent(2, 3))
        self.assertEqual(99, check_coverage.floor_percent(999, 1000))
        self.assertEqual(100, check_coverage.floor_percent(5, 5))
        self.assertEqual(0, check_coverage.floor_percent(0, 5))
        self.assertIsNone(check_coverage.floor_percent(0, 0))

    # ---- checking ----

    def test_passes_when_every_floor_holds(self):
        self.write_floors({"line": 55, "branch": 37}, {
            "com.example.alpha": {"line": 75, "branch": 50},
            "com.example.beta": {"line": 33, "branch": 0},
            "com.example.empty": {"line": 0, "branch": None},
        })
        code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline)
        self.assertEqual(0, code, out)

    def test_fails_when_one_package_drops_below_its_floor(self):
        self.write_floors({"line": 40, "branch": 10}, {
            "com.example.alpha": {"line": 75, "branch": 50},
            "com.example.beta": {"line": 33, "branch": 0},
            "com.example.empty": {"line": 0, "branch": None},
        })
        code, out = self.run_main("--xml", REPORT_DROPPED, "--baseline", self.baseline)
        self.assertEqual(1, code, out)
        self.assertIn("com.example.alpha", out)
        # beta did not move, so it must not be blamed.
        broken_lines = [line for line in out.splitlines() if line.startswith("BELOW FLOOR")]
        self.assertEqual(1, len(broken_lines), out)
        self.assertIn("line", broken_lines[0])

    def test_fails_on_a_branch_floor_alone(self):
        self.write_floors({"line": 0, "branch": 0}, {
            "com.example.alpha": {"line": 0, "branch": 51},
        })
        code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline)
        self.assertEqual(1, code, out)

    def test_fails_on_the_overall_floor_alone(self):
        self.write_floors({"line": 56, "branch": 0}, {})
        code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline)
        self.assertEqual(1, code, out)

    def test_a_fraction_under_the_floor_fails(self):
        # 2/3 = 66.67 %: holds a floor of 66, breaks a floor of 67.
        measured = {"overall": {"line": (2, 3), "branch": (0, 0)}}
        self.assertEqual([], check_coverage.compare(
            measured, {"overall": {"line": 66, "branch": None}})[0])
        self.assertEqual(1, len(check_coverage.compare(
            measured, {"overall": {"line": 67, "branch": None}})[0]))

    def test_new_and_removed_packages_are_reported_but_do_not_fail(self):
        self.write_floors({"line": 0, "branch": 0}, {
            "com.example.alpha": {"line": 75, "branch": 50},
            "com.example.removed": {"line": 90, "branch": 90},
        })
        code, out = self.run_main("--xml", REPORT_NEW_PACKAGE, "--baseline", self.baseline)
        self.assertEqual(0, code, out)
        self.assertIn("com.example.gamma", out)
        self.assertIn("com.example.removed", out)

    def test_check_never_writes_the_baseline(self):
        self.write_floors({"line": 0, "branch": 0}, {})
        before = self.read_floors()
        self.run_main("--xml", REPORT, "--baseline", self.baseline)
        self.assertEqual(before, self.read_floors())

    # ---- updating ----

    def test_update_creates_floors_rounded_down(self):
        code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline, "--update")
        self.assertEqual(0, code, out)
        floors = self.read_floors()
        self.assertEqual({"line": 55, "branch": 37}, floors["overall"])
        self.assertEqual({"line": 75, "branch": 50}, floors["packages"]["com.example.alpha"])
        self.assertEqual({"line": 33, "branch": 0}, floors["packages"]["com.example.beta"])
        self.assertEqual({"line": 0, "branch": None}, floors["packages"]["com.example.empty"])
        # What --update wrote passes the check against the same report.
        self.assertEqual(0, self.run_main("--xml", REPORT, "--baseline", self.baseline)[0])

    def test_update_raises_floors(self):
        self.write_floors({"line": 10, "branch": 10}, {
            "com.example.alpha": {"line": 20, "branch": 20},
        })
        code, _ = self.run_main("--xml", REPORT, "--baseline", self.baseline, "--update")
        self.assertEqual(0, code)
        floors = self.read_floors()
        self.assertEqual({"line": 55, "branch": 37}, floors["overall"])
        self.assertEqual({"line": 75, "branch": 50}, floors["packages"]["com.example.alpha"])

    def test_update_refuses_to_lower_without_force(self):
        self.run_main("--xml", REPORT, "--baseline", self.baseline, "--update")
        code, out = self.run_main("--xml", REPORT_DROPPED, "--baseline", self.baseline, "--update")
        self.assertEqual(1, code, out)
        floors = self.read_floors()
        # alpha lines fell from 75 % to 50 %: the floor stays.
        self.assertEqual(75, floors["packages"]["com.example.alpha"]["line"])
        # beta lines rose from 33 % to 50 % in the same report: that floor still goes up.
        self.assertEqual(50, floors["packages"]["com.example.beta"]["line"])

    def test_update_with_force_lowers(self):
        self.run_main("--xml", REPORT, "--baseline", self.baseline, "--update")
        code, out = self.run_main(
            "--xml", REPORT_DROPPED, "--baseline", self.baseline, "--update", "--force")
        self.assertEqual(0, code, out)
        self.assertEqual(50, self.read_floors()["packages"]["com.example.alpha"]["line"])

    def test_update_adds_new_and_drops_removed_packages(self):
        self.write_floors({"line": 0, "branch": 0}, {
            "com.example.removed": {"line": 90, "branch": 90},
        })
        code, _ = self.run_main("--xml", REPORT_NEW_PACKAGE, "--baseline", self.baseline, "--update")
        self.assertEqual(0, code)
        packages = self.read_floors()["packages"]
        self.assertIn("com.example.gamma", packages)
        self.assertNotIn("com.example.removed", packages)

    # ---- bad input ----

    def test_missing_report_is_exit_code_2(self):
        self.write_floors({"line": 0, "branch": 0}, {})
        code, out = self.run_main(
            "--xml", os.path.join(self.dir, "nope.xml"), "--baseline", self.baseline)
        self.assertEqual(2, code, out)

    def test_missing_baseline_is_exit_code_2_when_checking(self):
        code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline)
        self.assertEqual(2, code, out)

    def test_malformed_report_is_exit_code_2(self):
        bad = os.path.join(self.dir, "bad.xml")
        with open(bad, "w", encoding="utf-8") as handle:
            handle.write("<report><package name='a'><counter type='LINE' missed='x'")
        self.write_floors({"line": 0, "branch": 0}, {})
        self.assertEqual(2, self.run_main("--xml", bad, "--baseline", self.baseline)[0])

    def test_a_floor_that_is_not_a_whole_percentage_is_exit_code_2(self):
        for value in (101, -1, 12.5, "50", True):
            self.write_floors({"line": value, "branch": 0}, {})
            code, out = self.run_main("--xml", REPORT, "--baseline", self.baseline)
            self.assertEqual(2, code, "%r: %s" % (value, out))

    def test_force_without_update_is_a_usage_error(self):
        with self.assertRaises(SystemExit):
            stderr, sys.stderr = sys.stderr, io.StringIO()
            try:
                self.run_main("--xml", REPORT, "--baseline", self.baseline, "--force")
            finally:
                sys.stderr = stderr


if __name__ == "__main__":
    unittest.main()
