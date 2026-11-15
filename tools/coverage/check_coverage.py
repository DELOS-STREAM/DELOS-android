#!/usr/bin/env python3
"""Unit-test coverage ratchet for Delos.

Reads the JaCoCo XML report of the debug unit tests, prints line and branch
coverage overall and per package, and compares every package with the floors
committed in tools/coverage/baseline.json. A floor is a whole percentage; it
may only go up.

Usage (from the repository root):

    ./gradlew :app:jacocoDebugUnitTestReport
    python3 tools/coverage/check_coverage.py              # check, exit 1 when below a floor
    python3 tools/coverage/check_coverage.py --update     # raise floors to today's values
    python3 tools/coverage/check_coverage.py --update --force   # also allow lowering

Options:

    --xml PATH        JaCoCo XML report (default: the report of
                      :app:jacocoDebugUnitTestReport)
    --baseline PATH   floors file (default: tools/coverage/baseline.json)
    --update          rewrite the floors file from the report: floors go up to
                      the measured value rounded down, new packages are added,
                      packages that no longer exist are removed. A floor that
                      would go down is kept (and the run fails) unless --force
                      is given.
    --force           with --update: accept lower floors. Needs the owner's
                      approval in review; say why in the commit message.

Rules of the check:

    * "overall" and every package have a line floor and a branch floor.
    * A package with no lines (or no branches) has nothing to measure; that
      counter is skipped and stored as null.
    * A package that is in the report but not in the baseline is reported as
      new and does not fail the check; run --update to give it a floor.
    * A package that is in the baseline but no longer in the report (deleted
      code) is reported and does not fail the check; --update removes it.

Exit codes: 0 all floors hold, 1 at least one floor is broken (or --update
refused to lower one), 2 the report or the baseline cannot be read.

Only the Python standard library is used. Tests: python3 -m unittest discover
-s tools/coverage
"""

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_XML = os.path.join(
    REPO_ROOT, "app", "build", "reports", "jacoco", "jacocoDebugUnitTestReport",
    "jacocoDebugUnitTestReport.xml")
DEFAULT_BASELINE = os.path.join(REPO_ROOT, "tools", "coverage", "baseline.json")

OVERALL = "overall"
KINDS = (("line", "LINE"), ("branch", "BRANCH"))


class InputError(Exception):
    """The report or the baseline cannot be used."""


def _counters(element):
    """Returns {"line": (covered, total), "branch": (covered, total)} from the direct counters."""
    found = {}
    for counter in element.findall("counter"):
        found[counter.get("type")] = counter
    result = {}
    for kind, jacoco_type in KINDS:
        counter = found.get(jacoco_type)
        if counter is None:
            result[kind] = (0, 0)
            continue
        try:
            covered = int(counter.get("covered"))
            missed = int(counter.get("missed"))
        except (TypeError, ValueError):
            raise InputError("counter %s has no numeric covered/missed" % jacoco_type)
        if covered < 0 or missed < 0:
            raise InputError("counter %s is negative" % jacoco_type)
        result[kind] = (covered, covered + missed)
    return result


def read_report(path):
    """Returns {name: {"line": (covered, total), "branch": (covered, total)}} incl. "overall"."""
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise InputError("cannot read JaCoCo report %s: %s" % (path, error))
    if root.tag != "report":
        raise InputError("%s is not a JaCoCo XML report" % path)
    measured = {OVERALL: _counters(root)}
    for package in root.findall("package"):
        name = (package.get("name") or "").replace("/", ".") or "(default)"
        if name == OVERALL:
            raise InputError("a package named '%s' collides with the total" % OVERALL)
        measured[name] = _counters(package)
    return measured


def floor_percent(covered, total):
    """Whole percentage rounded down, or None when there is nothing to measure."""
    if total == 0:
        return None
    return covered * 100 // total


def read_baseline(path):
    """Returns {name: {"line": int|None, "branch": int|None}} incl. "overall"."""
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
    except (OSError, ValueError) as error:
        raise InputError("cannot read baseline %s: %s" % (path, error))
    if not isinstance(data, dict) or not isinstance(data.get("packages"), dict) \
            or not isinstance(data.get(OVERALL), dict):
        raise InputError("baseline %s needs an 'overall' and a 'packages' object" % path)
    floors = {OVERALL: data[OVERALL]}
    floors.update(data["packages"])
    for name, entry in floors.items():
        if not isinstance(entry, dict):
            raise InputError("baseline entry %s is not an object" % name)
        for kind, _ in KINDS:
            value = entry.get(kind)
            if value is not None and (isinstance(value, bool) or not isinstance(value, int)
                                      or not 0 <= value <= 100):
                raise InputError("baseline floor %s.%s must be a whole number 0-100 or null"
                                 % (name, kind))
    return {name: {kind: entry.get(kind) for kind, _ in KINDS} for name, entry in floors.items()}


def write_baseline(path, floors):
    data = {
        OVERALL: floors[OVERALL],
        "packages": {name: floors[name] for name in sorted(floors) if name != OVERALL},
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def compare(measured, floors):
    """Returns (broken, new, gone).

    broken: [(name, kind, floor, covered, total)] where the measured value is under the floor.
    new: names in the report without a floor. gone: names with a floor that left the report.
    """
    broken = []
    for name in sorted(measured):
        entry = floors.get(name)
        if entry is None:
            continue
        for kind, _ in KINDS:
            floor = entry.get(kind)
            covered, total = measured[name][kind]
            if floor is None or total == 0:
                continue
            # Whole-number comparison: covered/total >= floor/100.
            if covered * 100 < floor * total:
                broken.append((name, kind, floor, covered, total))
    new = sorted(name for name in measured if name not in floors)
    gone = sorted(name for name in floors if name not in measured)
    return broken, new, gone


def updated_floors(measured, floors, force):
    """Returns (new_floors, refused): floors after --update and the entries that would go down.

    refused: [(name, kind, old_floor, new_floor)]. Without force those keep the old floor.
    """
    result = {}
    refused = []
    for name in measured:
        old = floors.get(name, {})
        entry = {}
        for kind, _ in KINDS:
            value = floor_percent(*measured[name][kind])
            previous = old.get(kind)
            if previous is not None and value is not None and value < previous:
                refused.append((name, kind, previous, value))
                if not force:
                    value = previous
            entry[kind] = value
        result[name] = entry
    return result, sorted(refused)


def _percent_text(covered, total):
    if total == 0:
        return "n/a"
    return "%5.1f%% (%d/%d)" % (covered * 100.0 / total, covered, total)


def format_table(measured, floors):
    names = [OVERALL] + sorted(name for name in measured if name != OVERALL)
    width = max(len(name) for name in names)
    lines = ["%-*s  %-24s %-6s %-24s %-6s" % (width, "package", "lines", "floor", "branches", "floor")]
    for name in names:
        entry = floors.get(name)
        cells = []
        for kind, _ in KINDS:
            cells.append(_percent_text(*measured[name][kind]))
            floor = None if entry is None else entry.get(kind)
            cells.append("new" if entry is None else ("-" if floor is None else str(floor)))
        lines.append("%-*s  %-24s %-6s %-24s %-6s" % ((width, name) + tuple(cells)))
    return "\n".join(lines)


def main(argv=None, out=sys.stdout):
    parser = argparse.ArgumentParser(
        description="Check the JaCoCo unit-test coverage against the committed floors.")
    parser.add_argument("--xml", default=DEFAULT_XML, help="JaCoCo XML report")
    parser.add_argument("--baseline", default=DEFAULT_BASELINE, help="floors file")
    parser.add_argument("--update", action="store_true", help="raise the floors to today's values")
    parser.add_argument("--force", action="store_true", help="with --update: allow lower floors")
    args = parser.parse_args(argv)
    if args.force and not args.update:
        parser.error("--force only makes sense with --update")

    try:
        measured = read_report(args.xml)
        if args.update and not os.path.exists(args.baseline):
            floors = {}
        else:
            floors = read_baseline(args.baseline)
    except InputError as error:
        print("error: %s" % error, file=out)
        return 2

    print(format_table(measured, floors), file=out)
    broken, new, gone = compare(measured, floors)
    for name in new:
        print("note: %s has no floor yet (run --update)" % name, file=out)
    for name in gone:
        print("note: %s has a floor but is not in the report (run --update to remove it)" % name,
              file=out)

    if args.update:
        result, refused = updated_floors(measured, floors, args.force)
        for name, kind, old, value in refused:
            print("%s: %s %s floor %d -> %d" % (
                "lowered" if args.force else "REFUSED to lower", name, kind, old, value), file=out)
        write_baseline(args.baseline, result)
        print("baseline written: %s" % args.baseline, file=out)
        if refused and not args.force:
            print("FAILED: coverage is below %d floor(s); fix the tests or use --force"
                  % len(refused), file=out)
            return 1
        return 0

    for name, kind, floor, covered, total in broken:
        print("BELOW FLOOR: %s %s coverage %.1f%% (%d/%d) is under the floor of %d%%" % (
            name, kind, covered * 100.0 / total, covered, total, floor), file=out)
    if broken:
        print("FAILED: %d floor(s) broken" % len(broken), file=out)
        return 1
    print("OK: every floor holds", file=out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
