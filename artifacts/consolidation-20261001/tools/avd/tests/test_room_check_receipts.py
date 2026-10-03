"""wizard_evidence.room-check: receipts of other classes sharing the user-10 database are listed, not judged (--since-utc)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from wizard_evidence import parse_since_utc, select_run_receipts  # noqa: E402


RECEIPTS = [
    {"commitId": "m9-psu-a-commit", "programId": "m9-psu-a-program", "committedAtEpochMs": 1_790_955_043_361},
    {"commitId": "m9-psu-b-commit", "programId": "m9-psu-b-program", "committedAtEpochMs": 1_790_957_654_820},
    {"commitId": "run-1", "programId": "run-1", "committedAtEpochMs": 1_790_958_998_061},
    {"commitId": "run-2", "programId": "run-2", "committedAtEpochMs": 1_790_959_115_607},
]


class SinceUtcTests(unittest.TestCase):
    def test_parse_none_and_empty(self):
        self.assertIsNone(parse_since_utc(None))
        self.assertIsNone(parse_since_utc(""))

    def test_parse_z_offset_and_naive_are_the_same_instant(self):
        z = parse_since_utc("2026-10-02T16:34:48Z")
        self.assertEqual(z, parse_since_utc("2026-10-02T16:34:48+00:00"))
        self.assertEqual(z, parse_since_utc("2026-10-02T16:34:48"))
        self.assertEqual(z, parse_since_utc("2026-10-02T13:34:48-03:00"))
        self.assertEqual(z, 1_790_958_888_000)

    def test_without_cutoff_every_receipt_is_judged(self):
        mine, older = select_run_receipts(RECEIPTS, None)
        self.assertEqual([r["commitId"] for r in mine], [r["commitId"] for r in RECEIPTS])
        self.assertEqual(older, [])

    def test_cutoff_splits_run_from_older_receipts(self):
        mine, older = select_run_receipts(RECEIPTS, parse_since_utc("2026-10-02T16:34:48Z"))
        self.assertEqual([r["commitId"] for r in mine], ["run-1", "run-2"])
        self.assertEqual([r["commitId"] for r in older], ["m9-psu-a-commit", "m9-psu-b-commit"])

    def test_cutoff_in_the_future_leaves_nothing_to_judge(self):
        mine, older = select_run_receipts(RECEIPTS, parse_since_utc("2026-10-03T00:00:00Z"))
        self.assertEqual(mine, [])
        self.assertEqual(len(older), 4)


if __name__ == "__main__":
    unittest.main()
