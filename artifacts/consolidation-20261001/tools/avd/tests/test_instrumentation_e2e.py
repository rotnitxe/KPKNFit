"""run_instrumentation.run() and install_apks.install_verified() against the command-level fake device."""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import install_apks  # noqa: E402
import run_instrumentation as ri  # noqa: E402
from avd import Avd  # noqa: E402
from fake_device import FakeDevice, sha  # noqa: E402
from test_apk_evidence import APP_MANIFEST, TEST_MANIFEST, make_apk  # noqa: E402

TRANSFER = "com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest"
GPS = "com.example.kpkn.services.cardio.CardioGpsAvdLifecycleInstrumentedTest"


class InstrumentationE2E(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.app = make_apk(root, "app-base-debug.apk", APP_MANIFEST)
        self.test_apk = make_apk(root, "app-base-debug-androidTest.apk", TEST_MANIFEST)
        self.device = FakeDevice(apk_sha=sha(self.app.read_bytes()))
        self.device.test_apk_sha = sha(self.test_apk.read_bytes())
        self.avd = Avd("emulator-5580", runner=self.device)
        patcher = mock.patch("evidence.EVIDENCE_ROOT", root / "device-evidence")
        patcher.start()
        self.addCleanup(patcher.stop)

    def args(self, **extra):
        base = dict(flavor="base", serial="emulator-5580", classes=TRANSFER, classes_file=None, scenario="APPEND", extra=[], timeout=30,
                    apk=self.app, test_apk=self.test_apk, no_apk_check=False, source_root=ri.ANDROID_TEST_SRC, prepare_gps=False,
                    geo_feed=False, label="e2e")
        base.update(extra)
        return argparse.Namespace(**base)

    def run_it(self, **extra):
        return ri.run(self.args(**extra), self.avd)

    def test_passing_run_writes_evidence_and_scenario_argument(self):
        summary, code = self.run_it()
        self.assertEqual(code, 0, summary.get("reasons"))
        self.assertEqual(summary["status"], "PASSED")
        self.assertEqual((summary["declaredTests"], summary["reportedTests"]), (1, 1))
        folder = Path(summary["evidence"])
        for name in ("declared.json", "metadata.json", "stdout.txt", "stderr.txt", "logcat.txt", "summary.json"):
            self.assertTrue((folder / name).is_file(), name)
        command = json.loads((folder / "metadata.json").read_text())["command"]
        self.assertEqual(command[command.index("scenario") + 1], "APPEND")
        self.assertTrue(any("instrument" in line and "scenario APPEND" in line for line in self.device.log))

    def test_a_declared_test_the_runner_never_reports_fails(self):
        key = (TRANSFER, "rematerializeTransferCommittedByEditorAndWriteForOfflineSnapshot")
        self.device.instrument_omit.add(key)
        summary, code = self.run_it()
        self.assertEqual(code, 1)
        self.assertEqual(summary["missing"], [list(key)])

    def test_a_failing_test_fails_the_run(self):
        self.device.instrument_codes[(TRANSFER, "rematerializeTransferCommittedByEditorAndWriteForOfflineSnapshot")] = -2
        summary, code = self.run_it()
        self.assertEqual(code, 1)
        self.assertEqual(summary["notPassed"][0]["status"], "FAILED")
        self.assertIn("simulated", summary["notPassed"][0]["stack"])

    def test_timeout_force_stops_both_packages_and_fails(self):
        self.device.hang_instrument = True
        summary, code = self.run_it(timeout=1)
        self.assertEqual(code, 1)
        self.assertTrue(summary["timedOut"])
        self.assertTrue(any("force-stop com.example.kpkn.test" in line for line in self.device.log))

    def test_installed_apps_that_differ_from_the_build_are_missing_inputs(self):
        self.device.apk_sha = "0" * 64
        summary, code = self.run_it()
        self.assertEqual((code, summary["status"]), (4, "MISSING_INPUT"))
        self.device.apk_sha = sha(self.app.read_bytes())
        self.device.test_apk_sha = "1" * 64
        summary, code = self.run_it()
        self.assertEqual((code, summary["status"]), (4, "MISSING_INPUT"))
        self.assertIn("androidTest", summary["error"])
        self.assertFalse(any("instrument -w" in line for line in self.device.log))

    def test_unknown_class_or_method_is_a_missing_input(self):
        for classes in ("com.example.kpkn.Nope", f"{TRANSFER}#nope"):
            summary, code = self.run_it(classes=classes)
            self.assertEqual((code, summary["status"]), (4, "MISSING_INPUT"))

    def test_gps_preparation_grants_and_feeds_synthetic_fixes(self):
        summary, code = self.run_it(classes=GPS, scenario=None, prepare_gps=True, geo_feed=True)
        self.assertEqual(code, 0, summary.get("reasons"))
        self.assertIn("android.permission.ACCESS_FINE_LOCATION", self.device.perms)
        self.assertTrue(any("emu geo fix" in line for line in self.device.log))
        self.assertGreaterEqual(summary["geoFeed"]["fixesSent"], 1)


class InstallE2E(unittest.TestCase):
    def test_install_verified_matches_hash_and_version(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = make_apk(Path(tmp), "app-base-debug.apk", APP_MANIFEST)
            device = FakeDevice(apk_sha="0" * 64, version_code=1)
            receipt = install_apks.install_verified(Avd("emulator-5580", runner=device), apk, package="com.example.kpkn",
                                                    expect_version_code=34)
        self.assertTrue(receipt["matched"] and receipt["versionCodeMatchesManifest"])
        self.assertEqual(receipt["sourceSha256"], receipt["installedSha256"])
        self.assertEqual(receipt["before"]["sha256"], "0" * 64)
        self.assertEqual(receipt["deviceVersionCode"], 34)

    def test_a_device_that_keeps_an_old_binary_is_reported_as_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = make_apk(Path(tmp), "app-base-debug.apk", APP_MANIFEST)
            device = FakeDevice(apk_sha="0" * 64)
            avd = Avd("emulator-5580", runner=device)
            original = device._dispatch

            def stale_install(argv):
                if argv[3:4] == ["install"]:
                    return "Performing Streamed Install\nSuccess", "", 0  # claims success, installs nothing
                return original(argv)

            device._dispatch = stale_install
            receipt = install_apks.install_verified(avd, apk, package="com.example.kpkn")
        self.assertFalse(receipt["matched"])


if __name__ == "__main__":
    unittest.main()
