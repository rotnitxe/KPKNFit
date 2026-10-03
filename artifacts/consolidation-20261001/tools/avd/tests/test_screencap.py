"""ScreencapRecorder / ``--screencap-every`` (run_instrumentation): numbered, time-stamped display frames taken with the
allowlisted ``adb -s <serial> exec-out screencap -p`` while ``am instrument`` runs; bounded at 400 frames; a failing or
stalled capture never blocks or fails the instrumentation run."""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import re
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import run_instrumentation as ri  # noqa: E402
from avd import Avd  # noqa: E402
from fake_device import FakeDevice, png_bytes, sha  # noqa: E402
from test_apk_evidence import APP_MANIFEST, TEST_MANIFEST, make_apk  # noqa: E402

TRANSFER = "com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest"
FRAME_RE = re.compile(r"^frame-(\d{4})-(\d{8}T\d{12}Z)\.png$")
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


def wait_for(predicate, timeout: float = 5.0) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(0.005)
    return predicate()


class _StubAvd:
    """Only ``screenshot_bytes``: first ``fail_first`` calls raise, the rest return a PNG; optional stall on an event."""

    def __init__(self, fail_first: int = 0, stall: threading.Event | None = None) -> None:
        self.fail_first, self.stall, self.calls = fail_first, stall, 0

    def screenshot_bytes(self) -> bytes:
        self.calls += 1
        if self.stall is not None:
            self.stall.wait(30)
        if self.calls <= self.fail_first:
            raise RuntimeError("adb hiccup")
        return png_bytes()


class OptionTests(unittest.TestCase):
    def test_positive_seconds_accepts_only_finite_positive_numbers(self) -> None:
        self.assertEqual(ri.positive_seconds("3"), 3.0)
        self.assertEqual(ri.positive_seconds("0.5"), 0.5)
        for bad in ("0", "-1", "abc", "", "inf", "nan"):
            with self.assertRaises(argparse.ArgumentTypeError, msg=bad):
                ri.positive_seconds(bad)

    def test_the_option_is_advertised_by_the_cli(self) -> None:
        out = io.StringIO()
        with contextlib.redirect_stdout(out), self.assertRaises(SystemExit) as raised:
            ri.main(["--help"])
        self.assertEqual(raised.exception.code, 0)
        self.assertIn("--screencap-every", out.getvalue())
        self.assertIn("--screencap-dir", out.getvalue())

    def test_default_frames_directory_follows_the_evidence_root(self) -> None:
        run = Path("E:/evidence/instrumentation/base/20261002T000000000000Z-abc123-wizard-ui")
        self.assertEqual(ri.screencap_frames_dir(run),
                         Path("E:/evidence/wizard/frames/20261002T000000000000Z-abc123-wizard-ui"))

    def test_the_frame_limit_is_400_and_cannot_be_raised(self) -> None:
        self.assertEqual(ri.SCREENCAP_MAX_FRAMES, 400)
        self.assertEqual(ri.ScreencapRecorder(_StubAvd(), Path("x"), 3, max_frames=10_000).max_frames, 400)  # type: ignore[arg-type]
        with self.assertRaises(ValueError):
            ri.ScreencapRecorder(_StubAvd(), Path("x"), 0)  # type: ignore[arg-type]


class RecorderTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name) / "frames" / "run-1"
        self.device = FakeDevice(apk_sha="0" * 64)
        self.avd = Avd("emulator-5580", runner=self.device)

    def test_saves_numbered_time_stamped_pngs_through_the_allowlisted_screencap(self) -> None:
        recorder = ri.ScreencapRecorder(self.avd, self.directory, 0.01)
        recorder.start()
        self.assertTrue(wait_for(lambda: recorder.saved >= 3))
        recorder.stop()
        self.assertFalse(recorder.is_alive())
        frames = sorted(path.name for path in self.directory.glob("frame-*.png"))
        self.assertEqual(len(frames), recorder.saved)
        matches = [FRAME_RE.match(name) for name in frames]
        self.assertTrue(all(matches), frames)
        self.assertEqual([int(m.group(1)) for m in matches if m], list(range(1, recorder.saved + 1)))
        stamps = [m.group(2) for m in matches if m]
        self.assertEqual(stamps, sorted(stamps))
        for name in frames:
            self.assertTrue((self.directory / name).read_bytes().startswith(PNG_MAGIC), name)
        manifest = json.loads((self.directory / "manifest.json").read_text(encoding="utf-8"))
        self.assertEqual([item["name"] for item in manifest["frames"]], frames)
        self.assertEqual(manifest["frames"][0]["sha256"], sha((self.directory / frames[0]).read_bytes()))
        self.assertEqual((manifest["saved"], manifest["failed"], manifest["limitReached"]), (recorder.saved, 0, False))
        shots = [line for line in self.device.log if "screencap" in line]
        self.assertTrue(shots)
        self.assertEqual(set(shots), {"-s emulator-5580 exec-out screencap -p"})
        self.assertEqual(recorder.summary()["saved"], recorder.saved)

    def test_the_first_frame_is_taken_even_if_the_run_ends_immediately(self) -> None:
        recorder = ri.ScreencapRecorder(self.avd, self.directory, 60)
        recorder.stop_event.set()
        recorder.start()
        recorder.join(5)
        self.assertFalse(recorder.is_alive())
        self.assertEqual(recorder.saved, 1)
        self.assertEqual(len(list(self.directory.glob("frame-0001-*.png"))), 1)

    def test_capture_ends_by_itself_at_the_frame_limit(self) -> None:
        recorder = ri.ScreencapRecorder(self.avd, self.directory, 0.001, max_frames=3)
        recorder.start()
        recorder.join(5)
        self.assertFalse(recorder.is_alive())
        recorder.stop()
        self.assertEqual((recorder.saved, recorder.limit_reached), (3, True))
        self.assertEqual(len(list(self.directory.glob("frame-*.png"))), 3)
        self.assertEqual(json.loads((self.directory / "manifest.json").read_text(encoding="utf-8"))["limitReached"], True)

    def test_a_failed_capture_is_counted_and_never_stops_the_thread(self) -> None:
        stub = _StubAvd(fail_first=2)
        recorder = ri.ScreencapRecorder(stub, self.directory, 0.01)  # type: ignore[arg-type]
        recorder.start()
        self.assertTrue(wait_for(lambda: recorder.saved >= 1))
        recorder.stop()
        self.assertEqual(recorder.failed, 2)
        self.assertIn("adb hiccup", recorder.error or "")
        self.assertEqual(sorted(p.name for p in self.directory.glob("frame-*.png"))[0][:10], "frame-0001")

    def test_offline_mode_never_reaches_adb(self) -> None:
        self.avd.offline = True
        recorder = ri.ScreencapRecorder(self.avd, self.directory, 0.01, max_frames=2)
        recorder.start()
        self.assertTrue(wait_for(lambda: recorder.failed >= 2))
        recorder.stop()
        self.assertEqual(recorder.saved, 0)
        self.assertEqual([line for line in self.device.log if "screencap" in line], [])
        self.assertFalse(self.directory.exists() and any(self.directory.iterdir()))

    def test_a_stalled_capture_never_blocks_the_caller(self) -> None:
        release = threading.Event()
        self.addCleanup(release.set)
        recorder = ri.ScreencapRecorder(_StubAvd(stall=release), self.directory, 0.01)  # type: ignore[arg-type]
        began = time.monotonic()
        recorder.start()
        self.assertLess(time.monotonic() - began, 1.0)
        self.assertTrue(recorder.daemon)
        self.assertTrue(recorder.is_alive())
        release.set()
        recorder.stop()


class RunIntegrationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.app = make_apk(self.root, "app-base-debug.apk", APP_MANIFEST)
        self.test_apk = make_apk(self.root, "app-base-debug-androidTest.apk", TEST_MANIFEST)
        self.device = FakeDevice(apk_sha=sha(self.app.read_bytes()))
        self.device.test_apk_sha = sha(self.test_apk.read_bytes())
        self.avd = Avd("emulator-5580", runner=self.device)
        patcher = mock.patch("evidence.EVIDENCE_ROOT", self.root / "device-evidence")
        patcher.start()
        self.addCleanup(patcher.stop)

    def args(self, **extra) -> argparse.Namespace:
        base = dict(flavor="base", serial="emulator-5580", classes=TRANSFER, classes_file=None, scenario="APPEND", extra=[],
                    timeout=30, apk=self.app, test_apk=self.test_apk, no_apk_check=False, source_root=ri.ANDROID_TEST_SRC,
                    prepare_gps=False, geo_feed=False, label="screencap")
        base.update(extra)
        return argparse.Namespace(**base)

    def test_frames_are_written_under_wizard_frames_and_the_verdict_is_unchanged(self) -> None:
        summary, code = ri.run(self.args(screencap_every=0.01), self.avd)
        self.assertEqual(code, 0, summary.get("reasons"))
        self.assertEqual(summary["status"], "PASSED")
        run_dir = Path(summary["evidence"])
        frames_dir = self.root / "device-evidence" / "wizard" / "frames" / run_dir.name
        self.assertEqual(Path(summary["screencap"]["directory"]), frames_dir)
        self.assertGreaterEqual(summary["screencap"]["saved"], 1)
        self.assertEqual(len(list(frames_dir.glob("frame-*.png"))), summary["screencap"]["saved"])
        self.assertTrue((frames_dir / "manifest.json").is_file())
        self.assertEqual(json.loads((run_dir / "metadata.json").read_text(encoding="utf-8"))["screencapEverySeconds"], 0.01)
        self.assertEqual(json.loads((run_dir / "summary.json").read_text(encoding="utf-8"))["screencap"]["failed"], 0)
        # the real wrapper ran `am instrument` and the frames in the same session
        self.assertTrue(any("instrument -w" in line for line in self.device.log))
        self.assertTrue(any(line == "-s emulator-5580 exec-out screencap -p" for line in self.device.log))

    def test_explicit_directory_overrides_the_default(self) -> None:
        custom = self.root / "my-frames"
        summary, code = ri.run(self.args(screencap_every=0.01, screencap_dir=custom), self.avd)
        self.assertEqual(code, 0)
        self.assertTrue(list(custom.glob("frame-0001-*.png")))
        self.assertFalse((self.root / "device-evidence" / "wizard").exists())

    def test_without_the_option_nothing_is_captured(self) -> None:
        summary, code = ri.run(self.args(), self.avd)
        self.assertEqual(code, 0)
        self.assertIsNone(summary["screencap"])
        self.assertFalse(any("screencap" in line for line in self.device.log))
        self.assertFalse((self.root / "device-evidence" / "wizard").exists())

    def test_failing_captures_are_reported_but_do_not_fail_a_passing_run(self) -> None:
        with mock.patch.object(Avd, "screenshot_bytes", side_effect=RuntimeError("screencap did not return a PNG")):
            summary, code = ri.run(self.args(screencap_every=0.01), self.avd)
        self.assertEqual((code, summary["status"]), (0, "PASSED"))
        self.assertEqual(summary["screencap"]["saved"], 0)
        self.assertGreaterEqual(summary["screencap"]["failed"], 1)
        self.assertIn("did not return a PNG", summary["screencap"]["error"])

    def test_a_failing_run_keeps_its_verdict_and_its_frames(self) -> None:
        self.device.instrument_codes[(TRANSFER, "rematerializeTransferCommittedByEditorAndWriteForOfflineSnapshot")] = -2
        summary, code = ri.run(self.args(screencap_every=0.01), self.avd)
        self.assertEqual(code, 1)
        self.assertEqual(summary["notPassed"][0]["status"], "FAILED")
        self.assertGreaterEqual(summary["screencap"]["saved"], 1)


if __name__ == "__main__":
    unittest.main()
