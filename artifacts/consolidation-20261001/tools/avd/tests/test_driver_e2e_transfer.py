"""Offline end-to-end runs of the transfer driver (APPEND / REPLACE / CREATE) on the fake device.

The androidTest rematerialization (a separate adb run) is replaced by its recorded-PASSED result; the
simulated transfer applies the documented semantics, so this validates the driver's UI flow, the
snapshot wiring and the ported verifier call - not the product's PlanMaterializer.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import ui_editor_transfer  # noqa: E402
from test_driver_e2e import E2EBase  # noqa: E402

PASSED = {"status": "PASSED", "evidence": "simulated", "reasons": [], "exit": 0}


class TransferE2E(E2EBase):
    def run_scenario(self, scenario):
        with mock.patch.object(ui_editor_transfer, "run_rematerialization", return_value=PASSED):
            return self.run_driver(f"editor-transfer-{scenario.lower()}", lambda s: ui_editor_transfer.run(s, scenario), scenario=scenario)

    def test_each_scenario_passes(self):
        for scenario in ("APPEND", "REPLACE", "CREATE"):
            with self.subTest(scenario):
                code, result = self.run_scenario(scenario)
                self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
                self.assertEqual(self.statuses(result)["verify_room_transfer"], "PASS")

    def test_failed_rematerialization_blocks_the_verdict(self):
        failed = {"status": "FAILED_OR_INCOMPLETE", "evidence": "sim", "reasons": ["boom"], "exit": 1}
        with mock.patch.object(ui_editor_transfer, "run_rematerialization", return_value=failed):
            code, result = self.run_driver("editor-transfer-append", lambda s: ui_editor_transfer.run(s, "APPEND"), scenario="APPEND")
        self.assertEqual(code, 1)
        steps = self.statuses(result)
        self.assertEqual(steps["rematerialization_instrumentation"], "FAIL")
        self.assertEqual(steps["verify_room_transfer"], "NOT_RUN")

    def test_transfer_that_changes_the_source_session_is_detected(self):
        original = self.device.app._apply_transfer

        def damaging(program):
            original(program)
            import room_verify as rv

            source = rv.require_session(rv.find_week(program, __import__("fixtures").WEEK_ID), __import__("fixtures").MONDAY_SESSION_ID)
            source["parts"][-1]["exercises"].pop()

        self.device.app._apply_transfer = damaging
        code, result = self.run_scenario("APPEND")
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["verify_room_transfer"], "FAIL")


if __name__ == "__main__":
    unittest.main()
