"""Offline end-to-end runs of the editor drafts driver (RESTORE / SAVE / DISCARD) on the fake device."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import ui_editor_drafts  # noqa: E402
from test_driver_e2e import E2EBase  # noqa: E402


class EditorDraftsE2E(E2EBase):
    def run_scenario(self, scenario):
        return self.run_driver(f"editor-drafts-{scenario.lower()}", lambda s: ui_editor_drafts.run(s, scenario), scenario=scenario)

    def assert_passes(self, scenario, verifier_step):
        code, result = self.run_scenario(scenario)
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
        self.assertEqual(self.statuses(result)[verifier_step], "PASS")
        return result

    def test_restore(self):
        self.assert_passes("RESTORE", "verify_draftrestore")

    def test_save(self):
        self.assert_passes("SAVE", "verify_draftcommit")

    def test_discard(self):
        self.assert_passes("DISCARD", "verify_draftdiscard")

    def test_a_product_that_loses_the_draft_on_process_death_fails_restore(self):
        original = self.device.app._open_editor

        def forget_draft():
            raw = self.device.fs_app.pop(self.device.app.PREFS, None)  # draft file vanishes: nothing to restore
            original()

        self.device.app._open_editor = forget_draft
        code, result = self.run_scenario("RESTORE")
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["force_stop_reopen_restores_marker"], "FAIL")

    def test_a_save_that_never_reaches_room_fails_commit(self):
        self.device.app._save_editor = lambda: setattr(self.device.app, "screen", "program")  # claims success, writes nothing
        code, result = self.run_scenario("SAVE")
        self.assertEqual(code, 1)


if __name__ == "__main__":
    unittest.main()
