"""APK manifest parsing (synthetic binary AXML), dumpsys/apksigner parsing, staleness, Recorder semantics."""

from __future__ import annotations

import io
import json
import struct
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import apk_info  # noqa: E402
import evidence  # noqa: E402
from evidence import FAIL, NOT_RUN, PASS, MissingInput, Recorder  # noqa: E402


def build_axml(elements: list[tuple[str, dict]]) -> bytes:
    """Minimal UTF-8 binary XML with only start-element chunks (what read_manifest needs)."""
    strings: list[str] = []

    def idx(text: str) -> int:
        if text not in strings:
            strings.append(text)
        return strings.index(text)

    chunks = b""
    for tag, attrs in elements:
        tag_idx = idx(tag)
        attr_bytes = b""
        for name, value in attrs.items():
            name_idx = idx(name)
            if isinstance(value, bool):
                dtype, data, raw = 0x12, 0xFFFFFFFF if value else 0, 0xFFFFFFFF
            elif isinstance(value, int):
                dtype, data, raw = 0x10, value & 0xFFFFFFFF, 0xFFFFFFFF
            else:
                value_idx = idx(str(value))
                dtype, data, raw = 0x03, value_idx, value_idx
            attr_bytes += struct.pack("<IIIHBBI", 0xFFFFFFFF, name_idx, raw, 8, 0, dtype, data)
        body = struct.pack("<IIHHHHHH", 0xFFFFFFFF, tag_idx, 20, 20, len(attrs), 0, 0, 0) + attr_bytes
        chunks += struct.pack("<HHIII", 0x0102, 16, 16 + len(body), 1, 0xFFFFFFFF) + body

    pool_strings = b""
    offsets = []
    for text in strings:
        offsets.append(len(pool_strings))
        raw = text.encode("utf-8")
        pool_strings += bytes([len(text), len(raw)]) + raw + b"\x00"
    pool_strings += b"\x00" * (-len(pool_strings) % 4)
    header = 28 + 4 * len(strings)
    pool = struct.pack("<HHIIIIII", 0x0001, 28, header + len(pool_strings), len(strings), 0, 0x100, header, 0)
    pool += b"".join(struct.pack("<I", o) for o in offsets) + pool_strings
    total = 8 + len(pool) + len(chunks)
    return struct.pack("<HHI", 0x0003, 8, total) + pool + chunks


def make_apk(directory: Path, name: str, manifest: bytes, mtime: float | None = None) -> Path:
    path = directory / name
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", manifest)
        archive.writestr("classes.dex", b"dex")
    if mtime is not None:
        import os

        os.utime(path, (mtime, mtime))
    return path


APP_MANIFEST = build_axml([
    ("manifest", {"package": "com.example.kpkn", "versionCode": 34, "versionName": "KPKN Beta 15"}),
    ("uses-sdk", {"minSdkVersion": 24, "targetSdkVersion": 35}),
    ("application", {"debuggable": True}),
])
TEST_MANIFEST = build_axml([
    ("manifest", {"package": "com.example.kpkn.test"}),
    ("application", {"debuggable": True}),
    ("instrumentation", {"targetPackage": "com.example.kpkn"}),
])


class ManifestTests(unittest.TestCase):
    def test_app_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            facts = apk_info.read_manifest(make_apk(Path(tmp), "app.apk", APP_MANIFEST))
        self.assertEqual(facts["package"], "com.example.kpkn")
        self.assertEqual(facts["versionCode"], 34)
        self.assertEqual(facts["versionName"], "KPKN Beta 15")
        self.assertEqual((facts["minSdk"], facts["targetSdk"]), (24, 35))
        self.assertTrue(facts["debuggable"])

    def test_test_apk_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            facts = apk_info.read_manifest(make_apk(Path(tmp), "t.apk", TEST_MANIFEST))
        self.assertEqual(facts["package"], "com.example.kpkn.test")
        self.assertEqual(facts["instrumentationTarget"], "com.example.kpkn")
        self.assertIsNone(facts["versionCode"])

    def test_non_debuggable_and_garbage(self):
        plain = build_axml([("manifest", {"package": "p", "versionCode": 1}), ("application", {})])
        with tempfile.TemporaryDirectory() as tmp:
            self.assertFalse(apk_info.read_manifest(make_apk(Path(tmp), "p.apk", plain))["debuggable"])
            with self.assertRaises(apk_info.ApkInfoError):
                apk_info.read_manifest(make_apk(Path(tmp), "bad.apk", b"not axml at all"))
            with self.assertRaises(apk_info.ApkInfoError):
                apk_info.read_manifest(Path(tmp) / "missing.apk")

    def test_output_metadata_sibling(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = make_apk(Path(tmp), "app-base-debug.apk", APP_MANIFEST)
            (Path(tmp) / "output-metadata.json").write_text(json.dumps({
                "applicationId": "com.example.kpkn", "variantName": "baseDebug",
                "elements": [{"outputFile": "app-base-debug.apk", "versionCode": 34, "versionName": "x"}]}))
            meta = apk_info.read_output_metadata(apk)
        self.assertEqual(meta["variantName"], "baseDebug")
        self.assertEqual(meta["versionCode"], 34)

    def test_staleness(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "src"
            source.mkdir()
            (source / "A.kt").write_text("x")
            now = time.time()
            old = make_apk(root, "old.apk", APP_MANIFEST, mtime=now - 1000)
            new = make_apk(root, "new.apk", APP_MANIFEST, mtime=now + 1000)
            self.assertTrue(apk_info.staleness(old, [source])["apkOlderThanSources"])
            self.assertFalse(apk_info.staleness(new, [source])["apkOlderThanSources"])


class TextParserTests(unittest.TestCase):
    def test_dumpsys_package(self):
        text = ("Packages:\n  Package [com.example.kpkn] (abc):\n    versionCode=34 minSdk=24 targetSdk=35\n"
                "    versionName=KPKN Beta 15\n    flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]\n"
                "    firstInstallTime=2026-10-01 20:00:00\n    lastUpdateTime=2026-10-01 21:00:00\n")
        facts = apk_info.parse_dumpsys_package(text)
        self.assertEqual(facts["versionCode"], 34)
        self.assertEqual(facts["versionName"], "KPKN Beta 15")
        self.assertTrue(facts["debuggable"])

    def test_apksigner(self):
        text = ("Signer #1 certificate DN: C=US, O=Android, CN=Android Debug\n"
                "Signer #1 certificate SHA-256 digest: F14259A4A5DD75FFCA3732B5930CD7E30FA7F57D740E624E07CDA6DB87FE6FE4\n")
        parsed = apk_info.parse_apksigner_output(text)
        self.assertEqual(parsed["signerSha256"], ["f14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4"])


class RecorderTests(unittest.TestCase):
    def recorder(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        return Recorder("demo", "base", Path(tmp.name), stream=io.StringIO())

    def test_all_pass(self):
        r = self.recorder()
        r.declare("a", "b")
        with r.step("a") as s:
            s.detail("ok")
        with r.step("b"):
            pass
        self.assertEqual(r.overall(), PASS)
        self.assertEqual(r.finish(print_stdout=False)[1], 0)

    def test_failure_aborts_and_marks_remaining_not_run(self):
        r = self.recorder()
        r.declare("a", "b", "c")
        with self.assertRaises(evidence.RunAborted):
            with r.step("a") as s:
                s.check(False, "boom")
        with self.assertRaises(evidence.RunAborted):
            with r.step("b"):
                pass
        result = r.result()
        self.assertEqual(result["overall"], FAIL)
        self.assertEqual([x["status"] for x in result["steps"]], [FAIL, NOT_RUN, NOT_RUN])
        self.assertIn("blocked by failed step a", result["steps"][1]["detail"])
        self.assertEqual(r.finish(print_stdout=False)[1], 1)

    def test_crash_in_step_is_a_fail_with_traceback(self):
        r = self.recorder()
        r.declare("a")
        with self.assertRaises(evidence.RunAborted):
            with r.step("a"):
                {}["missing"]
        self.assertEqual(r.steps["a"]["status"], FAIL)
        self.assertIn("KeyError", r.steps["a"]["detail"])
        self.assertIn("traceback", r.steps["a"])

    def test_not_run_required_step_prevents_overall_pass(self):
        r = self.recorder()
        r.declare("a", "b")
        with r.step("a"):
            pass
        with r.step("b") as s:
            s.not_run("no evidence")
        self.assertEqual(r.overall(), NOT_RUN)
        self.assertEqual(r.finish(print_stdout=False)[1], 3)

    def test_optional_not_run_does_not_block_pass_but_optional_fail_does(self):
        r = self.recorder()
        r.declare("a", "extra", optional=("extra",))
        with r.step("a"):
            pass
        with r.step("extra", optional=True) as s:
            s.not_run("unavailable")
        self.assertEqual(r.overall(), PASS)
        r2 = self.recorder()
        r2.declare("a", optional=("a",))
        with self.assertRaises(evidence.RunAborted):
            with r2.step("a", optional=True) as s:
                s.fail("really broken")
        self.assertEqual(r2.overall(), FAIL)

    def test_missing_input_in_step_exits_4(self):
        r = self.recorder()
        r.declare("a", "b")
        with self.assertRaises(evidence.RunAborted):
            with r.step("a"):
                raise MissingInput("APK missing", "build it")
        result, code = r.finish(print_stdout=False)
        self.assertEqual(result["overall"], NOT_RUN)
        self.assertEqual(result["reason"], "MISSING_INPUT")
        self.assertEqual(code, 4)

    def test_empty_run_is_not_a_pass(self):
        self.assertEqual(self.recorder().overall(), NOT_RUN)

    def test_new_run_dir_never_reuses(self):
        with tempfile.TemporaryDirectory() as tmp:
            first = evidence.new_run_dir("k", "base", "lbl", root=Path(tmp))
            second = evidence.new_run_dir("k", "base", "lbl", root=Path(tmp))
            self.assertNotEqual(first, second)
            self.assertTrue(first.is_dir() and second.is_dir())


if __name__ == "__main__":
    unittest.main()
