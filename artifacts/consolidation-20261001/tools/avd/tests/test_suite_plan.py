"""Suite plan, suite class lists, verdict mapping and a CLI smoke of every stage (offline, device-less)."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import kotlin_tests  # noqa: E402
import run_suite  # noqa: E402
from qa_paths import ANDROID_TEST_SRC, TOOLS_DIR  # noqa: E402


class PlanTests(unittest.TestCase):
    def names(self, flavor="base", serial="emulator-5580", **kw):
        stages = run_suite.stage_table(flavor, serial, False)
        role = "wizard" if serial == "emulator-5582" else "audit"
        return [s["name"] for s in run_suite.select(stages, role, kw.get("only", set()), kw.get("skip", set()), kw.get("full", False))]

    def test_audit_order_is_the_documented_one(self):
        self.assertEqual(self.names(), [
            "install", "instr-workout", "instr-gps", "strength", "cancel-strength", "cancel-cardio", "drafts-restore", "drafts-save",
            "drafts-discard", "transfer-append", "transfer-replace", "transfer-create", "cardio", "media-import", "camerax", "migration"])

    def test_wizard_serial_gets_only_install_and_wizard_ui(self):
        self.assertEqual(self.names(serial="emulator-5582"), ["install", "instr-wizard"])

    def test_only_skip_and_optional_full_run(self):
        self.assertEqual(self.names(only={"install", "camerax"}), ["install", "camerax"])
        self.assertNotIn("migration", self.names(skip={"migration"}))
        self.assertNotIn("instr-full", self.names())
        self.assertIn("instr-full", self.names(full=True))

    def test_migration_is_last_so_the_integrated_apk_stays_installed(self):
        self.assertEqual(self.names()[-1], "migration")

    def test_plan_cli_prints_without_touching_anything(self):
        result = subprocess.run([sys.executable, "-X", "utf8", str(TOOLS_DIR / "run_suite.py"), "--flavor", "health", "--plan"],
                                capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 0, result.stderr)
        plan = json.loads(result.stdout)
        self.assertEqual(plan[0]["stage"], "install")
        self.assertIn("health", plan[0]["command"])

    def test_forbidden_serial_is_refused_by_the_runner(self):
        result = subprocess.run([sys.executable, "-X", "utf8", str(TOOLS_DIR / "run_suite.py"), "--flavor", "base", "--serial",
                                 "emulator-5554", "--plan"], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 4)


class VerdictTests(unittest.TestCase):
    def test_mapping(self):
        v = run_suite.verdict_of
        self.assertEqual(v(json.dumps({"overall": "PASS", "evidenceDir": "x"}), 0), ("PASS", "x"))
        self.assertEqual(v(json.dumps({"status": "PASSED", "evidence": "y"}), 0), ("PASS", "y"))
        self.assertEqual(v(json.dumps({"overall": "PASS"}), 1)[0], "FAIL")           # exit code wins over a stray PASS
        self.assertEqual(v(json.dumps({"overall": "FAIL"}), 1)[0], "FAIL")
        self.assertEqual(v(json.dumps({"overall": "NOT_RUN", "reason": "MISSING_INPUT"}), 4)[0], "MISSING_INPUT")
        self.assertEqual(v(json.dumps({"overall": "NOT_RUN"}), 3)[0], "NOT_RUN")
        self.assertEqual(v("not json", 1)[0], "FAIL")
        self.assertEqual(v("", 0)[0], "NOT_RUN")


class SuiteFileTests(unittest.TestCase):
    @unittest.skipUnless(ANDROID_TEST_SRC.exists(), "integrated androidTest sources not present")
    def test_every_suite_entry_is_a_real_integrated_class(self):
        index = kotlin_tests.index_test_sources(ANDROID_TEST_SRC)
        for suite in sorted((TOOLS_DIR / "suites").glob("*.txt")):
            entries = [line.strip() for line in suite.read_text(encoding="utf-8").splitlines() if line.strip() and not line.startswith("#")]
            self.assertTrue(entries, suite.name)
            declared = kotlin_tests.resolve_declared(entries, index)  # raises if a class is missing / has no @Test
            self.assertTrue(declared, suite.name)

    @unittest.skipUnless(ANDROID_TEST_SRC.exists(), "integrated androidTest sources not present")
    def test_all_android_tests_plus_the_two_special_classes_cover_the_tree(self):
        index = kotlin_tests.index_test_sources(ANDROID_TEST_SRC)
        listed = {line.strip() for line in (TOOLS_DIR / "suites" / "all-android-tests.txt").read_text(encoding="utf-8").splitlines()
                  if line.strip() and not line.startswith("#")}
        special = {"com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest",
                   "com.example.kpkn.services.cardio.CardioGpsAvdLifecycleInstrumentedTest"}
        self.assertEqual(set(index) - special, listed, "a new androidTest class is not in suites/all-android-tests.txt (or one was removed)")


class CliSmokeTests(unittest.TestCase):
    """Every stage command parses its arguments and fails closed (exit 4) when no device/APK exists."""

    def test_every_stage_fails_closed_without_a_device(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = dict(os.environ, KPKN_QA_OFFLINE="1", KPKN_QA_EVIDENCE_ROOT=tmp, PYTHONUTF8="1")
            for stage in run_suite.stage_table("base", "emulator-5580", False):
                argv = [*stage["argv"], "--apk", str(Path(tmp) / "absent.apk")]
                result = subprocess.run(argv, capture_output=True, text=True, encoding="utf-8", env=env, timeout=120)
                self.assertNotEqual(result.returncode, 2, f"{stage['name']}: argparse rejected the stage arguments\n{result.stderr[-600:]}")
                self.assertNotIn("Traceback", result.stderr, f"{stage['name']} crashed\n{result.stderr[-800:]}")
                self.assertEqual(result.returncode, 4, f"{stage['name']} should refuse with exit 4, got {result.returncode}\n{result.stdout[-500:]}")


if __name__ == "__main__":
    unittest.main()
