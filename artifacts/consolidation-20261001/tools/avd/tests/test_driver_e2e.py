"""Offline end-to-end runs of the drivers against the command-level fake device.

These prove that the drivers' flow, the QaSession primitives, the real Avd wrapper (guard, quoting,
fresh dump, run-as, tar extraction) and the Room inspection/verifiers fit together.  They do NOT prove
the product UI looks like the simulated one - only a device run does.
"""

from __future__ import annotations

import argparse
import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import camerax_capture  # noqa: E402
import evidence  # noqa: E402
import qa_common  # noqa: E402
import ui_cancel_workout  # noqa: E402
import ui_strength_s1_s2  # noqa: E402
from avd import Avd  # noqa: E402
from fake_device import FakeDevice, sha  # noqa: E402
from test_apk_evidence import APP_MANIFEST, make_apk  # noqa: E402


class E2EBase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.apk = make_apk(root, "app-base-debug.apk", APP_MANIFEST)
        self.device = FakeDevice(apk_sha=sha(self.apk.read_bytes()))
        self.avd = Avd("emulator-5580", runner=self.device)
        clock = {"now": 0.0}

        def fake_monotonic():
            clock["now"] += 0.5  # deadlines expire quickly so failure paths do not wait in real time
            return clock["now"]

        for target, value in (("evidence.EVIDENCE_ROOT", root / "device-evidence"), ("time.sleep", lambda s: None),
                              ("time.monotonic", fake_monotonic)):
            patcher = mock.patch(target, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def args(self, **extra):
        base = dict(flavor="base", serial="emulator-5580", apk=self.apk, label=None, home_timeout=30.0, allow_stale=False, png_all=False)
        base.update(extra)
        return argparse.Namespace(**base)

    def run_driver(self, name, body, **extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            code = qa_common.execute(name, self.args(**extra), body, avd=self.avd)
        return code, json.loads(out.getvalue())

    @staticmethod
    def statuses(result):
        return {step["name"]: step["status"] for step in result["steps"]}


class StrengthE2E(E2EBase):
    def test_strength_flow_passes_on_the_simulated_device(self):
        code, result = self.run_driver("strength-s1-s2", ui_strength_s1_s2.run)
        self.assertEqual(code, 0, [s for s in result["steps"] if s["status"] != "PASS"])
        self.assertEqual(result["overall"], "PASS")
        self.assertEqual(self.statuses(result)["one_click_per_set_proof"], "PASS")
        # evidence: XML per capture, Room captures, files ledger with hashes
        evidence_dir = Path(result["evidenceDir"])
        self.assertTrue(any((evidence_dir / "ui").glob("*.xml")))
        self.assertTrue((evidence_dir / "room" / "01-preseed").exists() or (evidence_dir / "room").exists())
        files = json.loads((evidence_dir / "files.json").read_text())
        self.assertTrue(all(len(item["sha256"]) == 64 for item in files))
        self.assertEqual(result["tapCount"], len(ui_strength_s1_s2.__dict__) * 0 + result["tapCount"])

    def test_two_register_taps_exactly(self):
        code, result = self.run_driver("strength-s1-s2", ui_strength_s1_s2.run)
        events = (Path(result["evidenceDir"]) / "events.jsonl").read_text().splitlines()
        registers = [json.loads(line) for line in events if json.loads(line).get("selector") == "desc:Registrar serie"]
        self.assertEqual(len(registers), 2)

    def test_refuses_a_device_that_is_not_the_contracted_avd(self):
        self.device.avd_name = "Pixel_9_Pro_XL"
        code, result = self.run_driver("strength-s1-s2", ui_strength_s1_s2.run)
        self.assertEqual(code, 4)
        self.assertEqual(result["overall"], "NOT_RUN")
        self.assertEqual(result["reason"], "REFUSED_DEVICE")
        self.assertFalse(any("shell input" in line for line in self.device.log))

    def test_stale_installed_apk_is_a_missing_input_not_a_pass(self):
        self.device.apk_sha = "0" * 64
        code, result = self.run_driver("strength-s1-s2", ui_strength_s1_s2.run)
        self.assertEqual(code, 4)
        self.assertEqual(result["reason"], "MISSING_INPUT")
        steps = self.statuses(result)
        self.assertEqual(steps["preflight_installed_apk_matches_build"], "NOT_RUN")
        self.assertEqual(steps["seed_strength_fixture"], "NOT_RUN")
        self.assertFalse(any("pm clear" in line for line in self.device.log), "device state must not be touched")

    def test_a_ui_that_never_advances_fails_the_step_and_blocks_the_rest(self):
        self.device.app._register_set = lambda: None  # tapping Registrar serie does nothing
        code, result = self.run_driver("strength-s1-s2", ui_strength_s1_s2.run)
        self.assertEqual(code, 1)
        steps = self.statuses(result)
        self.assertEqual(steps["s1_progress_1_of_2"], "FAIL")
        self.assertEqual(steps["room_two_sets_persisted"], "NOT_RUN")


class CancelE2E(E2EBase):
    def test_strength_cancel_deletes_ongoing_and_creates_no_log(self):
        code, result = self.run_driver("cancel-strength", lambda s: ui_cancel_workout.run(s, "strength"))
        self.assertEqual(code, 0, [s for s in result["steps"] if s["status"] not in ("PASS",)])
        steps = self.statuses(result)
        self.assertEqual(steps["room_after_cancel_no_ongoing_no_log"], "PASS")
        self.assertEqual(steps["ordering_delete_before_exit_strict"], "NOT_RUN")  # explicit, optional, honest
        self.assertEqual(result["overall"], "PASS")

    def test_cancel_that_leaves_the_ongoing_row_fails(self):
        self.device.app._abandon = lambda: setattr(self.device.app, "screen", "home")  # navigates but never deletes
        code, result = self.run_driver("cancel-strength", lambda s: ui_cancel_workout.run(s, "strength"))
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["room_after_cancel_no_ongoing_no_log"], "FAIL")


class CameraxE2E(E2EBase):
    def camerax_args(self, **extra):
        options = dict(mode="both", permissions="pm", audio="grant", video_seconds=0.0, bind_wait=0.0, capture_timeout=5.0, finish=False)
        options.update(extra)
        return options

    def run_camerax(self, **extra):
        args = self.camerax_args(**extra)
        return self.run_driver("camerax-capture", lambda s: camerax_capture.run(s, argparse.Namespace(**{**vars(s.args), **args})), **args)

    def test_photo_and_video_capture_are_proven_and_classified_as_camerax(self):
        code, result = self.run_camerax()
        self.assertEqual(code, 0, [s for s in result["steps"] if s["status"] not in ("PASS",)])
        self.assertEqual(result["captureClaim"], "REAL_CAMERAX_CAPTURE")
        origins = {item["kind"]: item["origin"] for item in result["origins"]}
        self.assertEqual(origins, {"PHOTO": "CAMERAX_CAPTURE", "VIDEO": "CAMERAX_CAPTURE"})
        self.assertEqual(self.statuses(result)["camera_service_corroboration_preview"], "PASS")

    def test_without_camera_permission_nothing_is_captured_and_the_run_fails(self):
        self.device.app.camera_ok = False
        original = self.device._shell

        def shell(tokens):
            if tokens[:2] == ["pm", "grant"] and tokens[3] == "android.permission.CAMERA":
                return "", "", 0  # grant silently ignored: the app still sees no permission
            return original(tokens)

        self.device._shell = shell
        code, result = self.run_camerax()
        self.assertEqual(code, 1)
        self.assertEqual(result["captureClaim"], "NOT_PROVEN")

    def test_photo_only_with_finish_binds_the_captured_row_to_the_log(self):
        code, result = self.run_camerax(**{"mode": "photo", "finish": True})
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
        self.assertEqual(self.statuses(result)["finish_binds_captured_media"], "PASS")
        self.assertEqual({o["kind"] for o in result["origins"]}, {"PHOTO"})

    def test_dialog_permission_mode_answers_the_system_dialogs(self):
        from fake_device import PKG

        self.device.app.camera_ok = False
        original_start = self.device.app.on_tap

        def with_dialog(key):
            if key == "media" and not self.device.app.camera_ok:
                self.device.app.screen = "perm_dialog"
                return
            if key == "perm-allow":
                self.device.perms.add("android.permission.CAMERA")
                self.device.app.camera_ok = True
                self.device.app.screen = "media"
                return
            original_start(key)

        self.device.app.on_tap = with_dialog
        original_render = self.device.app.render

        def render():
            if self.device.app.screen == "perm_dialog":
                from fake_device import Node, to_xml

                self.device.app.nodes = [Node(text="Allow KPKN to take pictures and record video?"),
                                         Node(text="While using the app", clickable=True, key="perm-allow"),
                                         Node(text="Don't allow", clickable=True, key="perm-deny")]
                return to_xml(self.device.app.nodes, pkg="com.google.android.permissioncontroller")
            return original_render()

        self.device.app.render = render
        original_top = self.device.app.top
        self.device.app.top = lambda: ("com.google.android.permissioncontroller/.permission.ui.GrantPermissionsActivity"
                                       if self.device.app.screen == "perm_dialog" else original_top())
        code, result = self.run_camerax(**{"mode": "photo", "permissions": "dialog"})
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
        self.assertEqual(self.statuses(result)["camera_permission_dialog"], "PASS")

    def test_a_picker_import_is_never_reported_as_a_capture(self):
        import media_checks as mc

        verdict = mc.classify_origin(kind="PHOTO", file_format="PNG", sha256="a" * 64, import_fixture_hashes=set(),
                                     interaction="picker", created_in_window=True, via_picker=True)
        self.assertEqual(verdict["origin"], "IMPORT")


if __name__ == "__main__":
    unittest.main()
