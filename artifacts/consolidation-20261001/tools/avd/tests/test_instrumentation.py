"""am-instrument output parsing, declared-vs-reported evaluation and Kotlin @Test discovery."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import kotlin_tests  # noqa: E402
import run_instrumentation as ri  # noqa: E402
from qa_paths import ANDROID_TEST_SRC  # noqa: E402

C1 = "com.example.kpkn.screens.workout.WorkoutV2UiTest"
C2 = "com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest"


def status_block(cls, test, code, extra=""):
    return (f"INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={test}\n{extra}"
            f"INSTRUMENTATION_STATUS_CODE: {code}\n")


def run_text(cases, ok=True, final=-1):
    body = "INSTRUMENTATION_STATUS: numtests=%d\n" % len(cases)
    for cls, test, code, extra in cases:
        body += status_block(cls, test, 1)
        body += status_block(cls, test, code, extra)
    body += "INSTRUMENTATION_RESULT: stream=\n\nTime: 3.2\n\n"
    body += ("OK (%d tests)\n\n" % len(cases)) if ok else "FAILURES!!!\nTests run: 2,  Failures: 1\n\n"
    body += f"INSTRUMENTATION_CODE: {final}\n"
    return body


class ParseTests(unittest.TestCase):
    def test_all_passed(self):
        report = ri.parse_instrument_output(run_text([(C1, "a", 0, ""), (C1, "b", 0, "")]))
        self.assertEqual([c["status"] for c in report["cases"]], ["PASSED", "PASSED"])
        self.assertEqual(report["okLineTests"], 2)
        self.assertEqual(report["instrumentationCode"], -1)
        self.assertEqual(report["unfinished"], [])
        self.assertEqual(report["numtests"], 2)

    def test_failure_with_multiline_stack_does_not_corrupt_fields(self):
        stack = ("INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: expected:<2> but was:<1>\n"
                 "\tat org.junit.Assert.fail(Assert.java:89)\n\tat com.example.kpkn.X.test(X.kt:10)\n")
        report = ri.parse_instrument_output(run_text([(C1, "a", 0, ""), (C1, "b", -2, stack)], ok=False))
        failed = [c for c in report["cases"] if c["status"] != "PASSED"]
        self.assertEqual(len(failed), 1)
        self.assertEqual(failed[0]["test"], "b")
        self.assertIn("AssertionError", failed[0]["stack"])
        self.assertIn("Assert.java:89", failed[0]["stack"])
        self.assertEqual(report["failureSummary"], {"run": 2, "failures": 1})

    def test_status_code_mapping(self):
        report = ri.parse_instrument_output(run_text([(C1, "e", -1, ""), (C1, "i", -3, ""), (C1, "m", -4, "")], ok=False))
        self.assertEqual([c["status"] for c in report["cases"]], ["ERROR", "SKIPPED", "SKIPPED"])

    def test_crash_leaves_started_test_unfinished(self):
        text = (status_block(C1, "a", 1) + "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n"
                "INSTRUMENTATION_CODE: 0\n")
        report = ri.parse_instrument_output(text)
        self.assertEqual(report["unfinished"], [(C1, "a")])
        self.assertTrue(report["processCrashed"])

    def test_instrumentation_failed_line(self):
        report = ri.parse_instrument_output("INSTRUMENTATION_FAILED: com.example.kpkn.test/androidx.test.runner.AndroidJUnitRunner\n")
        self.assertIn("AndroidJUnitRunner", report["instrumentationFailed"])


class EvaluateTests(unittest.TestCase):
    DECLARED = [(C1, "a"), (C1, "b")]

    def verdict(self, text, declared=None, returncode=0, timed_out=False):
        return ri.evaluate(declared or self.DECLARED, ri.parse_instrument_output(text), returncode=returncode, timed_out=timed_out)

    def test_pass(self):
        self.assertEqual(self.verdict(run_text([(C1, "a", 0, ""), (C1, "b", 0, "")]))["status"], "PASSED")

    def test_missing_declared_test_is_not_a_pass(self):
        verdict = self.verdict(run_text([(C1, "a", 0, "")]))
        self.assertEqual(verdict["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(verdict["missing"], [[C1, "b"]])

    def test_skipped_is_not_a_pass(self):
        verdict = self.verdict(run_text([(C1, "a", 0, ""), (C1, "b", -3, "")]))
        self.assertEqual(verdict["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(verdict["notPassed"][0]["status"], "SKIPPED")

    def test_unexpected_reported_test_fails(self):
        verdict = self.verdict(run_text([(C1, "a", 0, ""), (C1, "b", 0, ""), (C1, "zzz", 0, "")]))
        self.assertEqual(verdict["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(verdict["unexpected"], [[C1, "zzz"]])

    def test_parameterized_names_match_the_declared_method(self):
        verdict = self.verdict(run_text([(C1, "a[0]", 0, ""), (C1, "a[1]", 0, ""), (C1, "b", 0, "")]))
        self.assertEqual(verdict["status"], "PASSED")

    def test_timeout_nonzero_exit_and_missing_ok_line(self):
        text = run_text([(C1, "a", 0, ""), (C1, "b", 0, "")])
        self.assertEqual(self.verdict(text, timed_out=True)["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(self.verdict(text, returncode=1)["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(self.verdict(text.replace("OK (2 tests)", ""))["status"], "FAILED_OR_INCOMPLETE")
        self.assertEqual(self.verdict(text.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))["status"], "FAILED_OR_INCOMPLETE")

    def test_ok_count_must_match_parsed_cases(self):
        text = run_text([(C1, "a", 0, ""), (C1, "b", 0, "")]).replace("OK (2 tests)", "OK (3 tests)")
        self.assertIn("'OK (3 tests)' but 2 cases parsed", self.verdict(text)["reasons"])

    def test_nothing_declared_never_passes(self):
        self.assertEqual(ri.evaluate([], ri.parse_instrument_output(run_text([])), returncode=0, timed_out=False)["status"],
                         "FAILED_OR_INCOMPLETE")


class CommandTests(unittest.TestCase):
    def test_build_command_shape(self):
        command = ri.build_command([C1 + "#a", C2], "APPEND", ["foo=bar"])
        self.assertEqual(command[:7], ["shell", "am", "instrument", "-w", "-r", "-e", "class"])
        self.assertEqual(command[7], f"{C1}#a,{C2}")
        self.assertIn("scenario", command)
        self.assertEqual(command[-1], "com.example.kpkn.test/androidx.test.runner.AndroidJUnitRunner")

    def test_unsafe_extras_are_refused(self):
        for bad in ("a b=c", "x=$(id)", "=v", "1x=2"):
            with self.assertRaises(ValueError):
                ri.build_command([C1], None, [bad])


KOTLIN = '''
package com.example.demo

import org.junit.Test

// @Test fun commented() {}
class FirstTest {
    @Test fun alpha() {}
    @Test
    @Ignore("later")
    fun beta() {}
    /* @Test fun hidden() {} */
    fun helper() { val s = "@Test fun fake() {}" }
    @Test fun `with spaces`() {}
    class Nested { @Test fun nestedOnly() {} }
}

class SecondTest {
    @org.junit.Test fun gamma() {}
}
'''


class KotlinDiscoveryTests(unittest.TestCase):
    def test_owned_methods_only(self):
        parsed = {c["className"]: c["methods"] for c in kotlin_tests.parse_kotlin_test_classes(KOTLIN)}
        self.assertEqual(parsed["FirstTest"], ["alpha", "beta", "with spaces"])
        self.assertEqual(parsed["SecondTest"], ["gamma"])

    def test_resolve_declared(self):
        index = {"com.example.kpkn.A": [{"methods": ["x", "y"], "source": "A.kt"}]}
        self.assertEqual(kotlin_tests.resolve_declared(["com.example.kpkn.A"], index), [("com.example.kpkn.A", "x"), ("com.example.kpkn.A", "y")])
        self.assertEqual(kotlin_tests.resolve_declared(["com.example.kpkn.A#y"], index), [("com.example.kpkn.A", "y")])
        for bad in ("com.example.kpkn.B", "com.example.kpkn.A#z"):
            with self.assertRaises(LookupError):
                kotlin_tests.resolve_declared([bad], index)
        for bad in ("A", "com.other.A", "com.example.kpkn.A#x;y"):
            with self.assertRaises(ValueError):
                kotlin_tests.resolve_declared([bad], index)

    @unittest.skipUnless(ANDROID_TEST_SRC.exists(), "integrated androidTest sources not present")
    def test_real_integrated_classes_are_discovered(self):
        index = kotlin_tests.index_test_sources(ANDROID_TEST_SRC)
        declared = kotlin_tests.resolve_declared([C2, "com.example.kpkn.services.cardio.CardioGpsAvdLifecycleInstrumentedTest"], index)
        self.assertTrue(any(cls == C2 for cls, _ in declared))
        self.assertGreaterEqual(len(declared), 2)


if __name__ == "__main__":
    unittest.main()
