"""Selector resolution, fresh-dump protocol, IME and activity parsing (pure)."""

from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import uia  # noqa: E402

XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
 <node class="android.widget.FrameLayout" package="com.example.kpkn" bounds="[0,0][1080,2400]" clickable="false" enabled="true">
  <node class="android.view.View" clickable="true" enabled="true" bounds="[100,200][500,300]">
   <node class="android.widget.TextView" text="S1" bounds="[120,210][180,290]"/>
  </node>
  <node class="android.view.View" clickable="true" enabled="true" bounds="[600,200][1000,300]">
   <node class="android.widget.TextView" text="S2" bounds="[620,210][680,290]"/>
  </node>
  <node class="android.view.View" clickable="true" enabled="true" bounds="[100,400][1000,500]" content-desc="Registrar serie"/>
  <node class="android.view.View" clickable="true" enabled="false" bounds="[100,520][1000,600]" content-desc="Parar"/>
  <node class="android.widget.TextView" text="1/2" bounds="[10,10][60,40]"/>
  <node class="android.widget.TextView" text="✓" bounds="[70,10][120,40]"/>
  <node class="android.widget.EditText" text="" hint="kg" clickable="true" enabled="true" focusable="true" bounds="[100,700][700,1000]"/>
  <node class="android.widget.EditText" text="6" clickable="true" enabled="true" focusable="true" bounds="[720,700][800,760]"/>
  <node class="android.view.ViewGroup" clickable="false" enabled="true" bounds="[0,1300][1080,1500]">
   <node class="android.widget.TextView" text="Día 1" bounds="[20,1310][200,1360]"/>
   <node class="android.view.View" clickable="true" enabled="true" bounds="[900,1310][1060,1390]" content-desc="Edit"/>
  </node>
  <node class="android.view.ViewGroup" clickable="false" enabled="true" bounds="[0,1500][1080,1700]">
   <node class="android.widget.TextView" text="Día 2" bounds="[20,1510][200,1560]"/>
   <node class="android.view.View" clickable="true" enabled="true" bounds="[900,1510][1060,1590]" content-desc="Edit"/>
  </node>
  <node class="android.view.View" clickable="true" enabled="true" bounds="[100,1100][500,1200]"><node text="Dup" bounds="[100,1100][200,1200]"/></node>
  <node class="android.view.View" clickable="true" enabled="true" bounds="[600,1100][1000,1200]"><node text="Dup" bounds="[600,1100][700,1200]"/></node>
 </node>
</hierarchy>"""


class SelectorTests(unittest.TestCase):
    def test_text_and_desc_resolve_to_clickable_ancestor_center(self):
        self.assertEqual(uia.resolve_clickable_target(XML, "text:S1")["center"], [300, 250])
        self.assertEqual(uia.resolve_clickable_target(XML, "desc:Registrar serie")["bounds"], [100, 400, 1000, 500])

    def test_disabled_control_is_not_a_target(self):
        with self.assertRaises(uia.TargetResolutionError):
            uia.resolve_clickable_target(XML, "desc:Parar")

    def test_ambiguity_is_detected(self):
        with self.assertRaises(uia.TargetResolutionError) as ctx:
            uia.resolve_clickable_target(XML, "text:Dup")
        self.assertTrue(uia.is_ambiguous_target_error(ctx.exception))
        with self.assertRaises(uia.TargetResolutionError) as missing:
            uia.resolve_clickable_target(XML, "text:Nope")
        self.assertFalse(uia.is_ambiguous_target_error(missing.exception))

    def test_contains_and_unknown_attribute(self):
        self.assertEqual(uia.resolve_clickable_target(XML, "contains:egistrar")["bounds"], [100, 400, 1000, 500])
        with self.assertRaises(uia.TargetResolutionError):
            uia.resolve_clickable_target(XML, "color:red")

    def test_scoped_target_picks_the_card_of_the_label(self):
        with self.assertRaises(uia.TargetResolutionError):  # two Edit buttons: ambiguous without scope
            uia.resolve_clickable_target(XML, "desc:Edit")
        self.assertEqual(uia.resolve_scoped_clickable_target(XML, "desc:Edit", "Día 1")["center"], [980, 1350])
        self.assertEqual(uia.resolve_scoped_clickable_target(XML, "desc:Edit", "Día 2")["center"], [980, 1550])
        with self.assertRaises(uia.TargetResolutionError):
            uia.resolve_scoped_clickable_target(XML, "desc:Edit", "Día 9")

    def test_presence_helpers(self):
        self.assertTrue(uia.selector_present(XML, "text:S1"))
        self.assertTrue(uia.selector_present(XML, "contains:serie"))
        self.assertFalse(uia.selector_present(XML, "text:S9"))
        self.assertEqual(uia.selector_count(XML, "text:Dup"), 2)
        self.assertTrue(uia.has_exact_label(XML, "Registrar serie"))
        self.assertEqual(uia.progress_tokens(XML), ["1/2", "✓"])
        self.assertIn("S1", uia.ui_tokens(XML))

    def test_primary_input_requires_dominance(self):
        self.assertEqual(uia.resolve_primary_input(XML)["bounds"], [100, 700, 700, 1000])
        ambiguous = XML.replace('bounds="[720,700][800,760]"', 'bounds="[720,700][1000,1000]"')
        with self.assertRaises(uia.TargetResolutionError):
            uia.resolve_primary_input(ambiguous)

    def test_swipe_geometry(self):
        x1, y1, x2, y2 = uia.screen_swipe_up(XML)
        self.assertEqual((x1, x2), (540, 540))
        self.assertGreater(y1, y2)
        with self.assertRaises(uia.TargetResolutionError):
            uia.scroll_container_swipe(XML)  # no scrollable container
        scrollable = XML.replace('clickable="false" enabled="true">', 'clickable="false" enabled="true" scrollable="true">', 1)
        self.assertEqual(uia.scroll_container_swipe(scrollable)[0], 540)


class ActivityAndImeTests(unittest.TestCase):
    DUMP = ("  topResumedActivity=ActivityRecord{1a2b3c u0 com.example.kpkn/.MainActivity t42}\n"
            "  mResumedActivity: ActivityRecord{1a2b3c u0 com.example.kpkn/.MainActivity t42}\n")

    def test_top_resumed(self):
        component = uia.parse_top_resumed(self.DUMP)
        self.assertEqual(component, "com.example.kpkn/.MainActivity")
        self.assertTrue(uia.component_is(component, "com.example.kpkn", ".MainActivity"))
        self.assertTrue(uia.component_is("com.example.kpkn/com.example.kpkn.MainActivity", "com.example.kpkn", ".MainActivity"))
        self.assertFalse(uia.component_is("com.google.android.documentsui/.picker.PickActivity", "com.example.kpkn"))
        self.assertIsNone(uia.parse_top_resumed("nothing"))

    def test_ime_visibility_needs_consistent_evidence(self):
        self.assertEqual(uia.parse_ime_visibility("  mInputShown=true\n  mIsInputViewShown=true\n").visibility, "shown")
        self.assertEqual(uia.parse_ime_visibility("  mInputShown=false\n  isInputViewShown=false\n").visibility, "hidden")
        self.assertEqual(uia.parse_ime_visibility("  mInputShown=true\n  mIsInputViewShown=false\n").visibility, "unknown")
        self.assertEqual(uia.parse_ime_visibility("no flags here").visibility, "unknown")
        self.assertEqual(uia.parse_ime_visibility("  mShowRequested=true\n").visibility, "unknown")


class FreshDumpTests(unittest.TestCase):
    GOOD = XML.encode("utf-8")

    def runner(self, script):
        calls = []

        def run(*args, timeout):
            calls.append(args)
            return script(args)

        return run, calls

    @staticmethod
    def completed(stdout=b"", stderr=b"", code=0):
        return subprocess.CompletedProcess([], code, stdout, stderr)

    def test_successful_dump_removes_first_and_validates_xml(self):
        def script(args):
            if args[:2] == ("shell", "uiautomator"):
                return self.completed(b"UI hierarchy dumped to: /sdcard/kpkn-qa-ui.xml\n")
            if args[0] == "exec-out":
                return self.completed(self.GOOD)
            return self.completed()

        run, calls = self.runner(script)
        payload, meta = uia.dump_fresh_xml("/sdcard/kpkn-qa-ui.xml", run, timeout=5)
        self.assertEqual(payload, self.GOOD)
        self.assertEqual(calls[0], ("shell", "rm", "-f", "/sdcard/kpkn-qa-ui.xml"))
        self.assertTrue(meta["fresh"])

    def test_unconfirmed_or_wrong_path_dump_is_rejected(self):
        for message in (b"ERROR: could not get idle state\n", b"UI hierarchy dumped to: /sdcard/other.xml\n"):
            def script(args, message=message):
                if args[:2] == ("shell", "uiautomator"):
                    return self.completed(message)
                return self.completed(self.GOOD)

            run, _ = self.runner(script)
            with self.assertRaises(uia.UiDumpFreshnessError):
                uia.dump_fresh_xml("/sdcard/kpkn-qa-ui.xml", run, timeout=5)

    def test_non_owned_remote_path_is_refused(self):
        run, calls = self.runner(lambda args: self.completed())
        for path in ("/sdcard/foo.xml", "/data/local/tmp/kpkn-qa-x.xml", "/sdcard/kpkn-qa-../x.xml"):
            with self.assertRaises(ValueError):
                uia.dump_fresh_xml(path, run, timeout=5)
        self.assertEqual(calls, [])

    def test_retry_until_ready_survives_transient_failures(self):
        state = {"dump": 0}

        def script(args):
            if args[:2] == ("shell", "uiautomator"):
                state["dump"] += 1
                if state["dump"] < 3:
                    return self.completed(b"ERROR: null root node returned by UiTestAutomationBridge.\n", code=1)
                return self.completed(b"UI hierarchy dumped to: /sdcard/kpkn-qa-ui.xml\n")
            if args[0] == "exec-out":
                return self.completed(self.GOOD)
            return self.completed()

        run, _ = self.runner(script)
        payload, meta = uia.dump_fresh_xml_until_ready("/sdcard/kpkn-qa-ui.xml", run, timeout=10, sleep=lambda s: None)
        self.assertEqual(meta["retry"]["attemptCount"], 3)
        self.assertEqual(meta["retry"]["failedAttemptCount"], 2)

    def test_retry_gives_up_with_attempt_history(self):
        run, _ = self.runner(lambda args: self.completed(b"ERROR\n", code=1))
        with self.assertRaises(uia.UiDumpFreshnessError) as ctx:
            uia.dump_fresh_xml_until_ready("/sdcard/kpkn-qa-ui.xml", run, timeout=0.05, sleep=lambda s: None)
        self.assertTrue(ctx.exception.attempts)


if __name__ == "__main__":
    unittest.main()
