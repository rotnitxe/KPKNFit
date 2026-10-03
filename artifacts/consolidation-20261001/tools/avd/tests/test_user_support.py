"""Secondary Android user support (--user N) of the allowlisted adb wrapper, installer and instrumentation runner."""

from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import avd  # noqa: E402
import run_instrumentation  # noqa: E402
import wizard_evidence  # noqa: E402
from avd import Avd, OwnedDeviceError  # noqa: E402

PM_LIST_USERS = "Users:\n\tUserInfo{0:Owner:4c13} running\n\tUserInfo{10:QA10:410}\n"
DUMPSYS_USER = (
    "Current user: 0\n\nUsers:\n"
    "  UserInfo{0:null:4c13} serialNo=0 isPrimary=true\n    Type: android.os.usertype.full.SYSTEM\n    State: RUNNING_UNLOCKED\n"
    "  UserInfo{10:QA10:410} serialNo=10 isPrimary=false\n    Type: android.os.usertype.full.SECONDARY\n    State: -1\n"
)


class Recording:
    def __init__(self) -> None:
        self.calls: list[list[str]] = []

    def __call__(self, argv, *, timeout, input_bytes=None):
        self.calls.append(list(argv))
        return subprocess.CompletedProcess(argv, 0, b"", b"")


class UserParsingTests(unittest.TestCase):
    def test_pm_list_users(self):
        rows = avd.parse_pm_list_users(PM_LIST_USERS)
        self.assertEqual([r["id"] for r in rows], [0, 10])
        self.assertEqual(rows[1]["name"], "QA10")
        self.assertEqual([r["running"] for r in rows], [True, False])

    def test_user_states(self):
        self.assertEqual(avd.parse_user_states(DUMPSYS_USER), {0: "RUNNING_UNLOCKED", 10: "-1"})


class UserAddressingTests(unittest.TestCase):
    def test_default_user_keeps_historical_commands(self):
        runner = Recording()
        device = Avd("emulator-5582", runner=runner)
        device.pm_paths()
        device.run_as_sh("true")
        device.shell("am", "force-stop", *device.user_args, "com.example.kpkn")
        argv = [" ".join(call[3:]) for call in runner.calls]
        self.assertIn("shell pm path com.example.kpkn", argv)
        self.assertTrue(any(a.startswith("shell run-as com.example.kpkn sh -c") for a in argv))
        self.assertIn("shell am force-stop com.example.kpkn", argv)

    def test_user_10_addresses_the_secondary_user_everywhere(self):
        runner = Recording()
        device = Avd("emulator-5582", runner=runner, user=10)
        device.pm_paths()
        device.run_as_sh("true")
        device.run_as_cat("databases/kpkn.db")
        try:
            device.force_stop()
        except RuntimeError:
            pass  # the recording runner never reports the process as gone
        joined = [" ".join(call[3:]) for call in runner.calls]
        self.assertIn("shell pm path --user 10 com.example.kpkn", joined)
        self.assertTrue(any("run-as com.example.kpkn --user 10 sh -c" in a for a in joined), joined)
        self.assertTrue(any("run-as com.example.kpkn --user 10 cat databases/kpkn.db" in a for a in joined), joined)
        self.assertIn("shell am force-stop --user 10 com.example.kpkn", joined)

    def test_rejects_bad_user_ids_and_never_unlocks_other_serials(self):
        for bad in (-1, 1000, True):
            with self.assertRaises(ValueError, msg=repr(bad)):
                Avd("emulator-5582", runner=Recording(), user=bad)
        runner = Recording()
        with self.assertRaises(OwnedDeviceError):
            Avd("emulator-5554", runner=runner, user=10)
        self.assertEqual(runner.calls, [])

    def test_pm_clear_of_a_foreign_package_is_still_refused_for_any_user(self):
        with self.assertRaises(OwnedDeviceError):
            avd.guard_adb_args("emulator-5582", ["shell", "pm", "clear", "com.android.settings", "--user", "10"])
        with self.assertRaises(OwnedDeviceError):
            avd.guard_adb_args("emulator-5582", ["shell", "pm clear --user 10 com.android.settings"])


class InstrumentationCommandTests(unittest.TestCase):
    def test_default_command_has_no_user(self):
        command = run_instrumentation.build_command(["a.B"], None, [])
        self.assertNotIn("--user", command)
        self.assertEqual(command[:5], ["shell", "am", "instrument", "-w", "-r"])

    def test_user_command_puts_user_before_the_class_filter(self):
        command = run_instrumentation.build_command(["a.B", "c.D"], None, [], user=10)
        self.assertEqual(command[:7], ["shell", "am", "instrument", "-w", "-r", "--user", "10"])
        self.assertEqual(command[7:10], ["-e", "class", "a.B,c.D"])
        self.assertEqual(command[-1], run_instrumentation.RUNNER)


class WizardEvidenceHelpersTests(unittest.TestCase):
    def test_artifact_markers_and_scenario_names(self):
        text = (
            "INSTRUMENTATION_STATUS: stream=Q6_UI_ARTIFACT_DIR=/storage/emulated/10/Android/data/com.example.kpkn/files/"
            "q6-ui-captures/powerbuilding_five_day-6b2f2b1e-1111-4c2d-9a3b-0123456789ab\n"
            "Q6_UI_SCREENSHOT=/storage/emulated/10/Android/data/com.example.kpkn/files/q6-ui-captures/x/review-preview.png\n"
        )
        found = wizard_evidence.artifact_paths_from_text(text)
        self.assertEqual(len(found["dirs"]), 1)
        self.assertEqual(len(found["screenshots"]), 1)
        name = found["dirs"][0].rsplit("/", 1)[1]
        self.assertEqual(wizard_evidence.scenario_of(name), "powerbuilding_five_day")

    def test_png_dimensions(self):
        header = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\x0dIHDR" + (1080).to_bytes(4, "big") + (2400).to_bytes(4, "big") + b"\x08\x06\x00\x00\x00"
        self.assertEqual(wizard_evidence.png_dimensions(header), (1080, 2400))
        self.assertIsNone(wizard_evidence.png_dimensions(b"not a png at all, just bytes"))

    def test_walk_program_counts_nested_weeks_sessions_and_exercises(self):
        program = {"id": "p", "name": "Plan", "macrocycles": [{"blocks": [{"mesocycles": [{"weeks": [
            {"id": "w1", "executionKind": "TRAINING", "sessions": [
                {"dayOfWeek": 1, "exercises": [{"id": "e1"}, {"id": "e2"}]},
                {"dayOfWeek": 3, "exercises": [], "parts": [{"exercises": [{"id": "e3"}]}]}]},
            {"id": "w2", "executionKind": "REST", "sessions": []},
        ]}]}]}]}
        summary = wizard_evidence.walk_program(program)
        self.assertEqual((summary["weeks"], summary["sessionsTotal"], summary["exercisesTotal"]), (2, 2, 3))
        self.assertEqual(summary["firstWeek"]["days"], [1, 3])
        self.assertEqual(summary["trainingWeeksWithoutSessions"], 0)


if __name__ == "__main__":
    unittest.main()
