"""start_avd.ps1 -WaitBoot: a transient adb failure while the emulator boots must not abort the script.

With ``$ErrorActionPreference = 'Stop'`` the stderr of a native command (``error: device offline`` while the emulator is
still coming up) becomes a terminating ``NativeCommandError``.  The boot poll must therefore wrap the ``getprop`` call in
``try``/``catch`` and treat any failure as "not booted yet".  Host-only: reads the script text and, when Windows PowerShell
is available, runs the poll pattern against a fake adb that always answers ``device offline``.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "start_avd.ps1"


class StartAvdBootWaitTest(unittest.TestCase):
    def test_getprop_poll_is_guarded_by_try_catch(self) -> None:
        text = SCRIPT.read_text(encoding="utf-8")
        match = re.search(r"try\s*\{[^{}]*getprop sys\.boot_completed[^{}]*\}\s*catch\s*\{", text)
        self.assertIsNotNone(match, "the sys.boot_completed poll must sit inside try { ... } catch { ... }")
        self.assertIn("$adbOk -and", text)

    def test_script_keeps_stop_preference_and_crlf(self) -> None:
        raw = SCRIPT.read_bytes()
        self.assertIn(b"$ErrorActionPreference = 'Stop'", raw)
        self.assertEqual(raw.count(b"\n"), raw.count(b"\r\n"), "start_avd.ps1 uses CRLF line endings")

    @unittest.skipUnless(shutil.which("powershell"), "Windows PowerShell not available")
    def test_poll_pattern_survives_device_offline(self) -> None:
        text = SCRIPT.read_text(encoding="utf-8")
        block = re.search(r"(        \$value = ''.*?\n        if \(\$adbOk -and .*?\n)", text, re.S)
        self.assertIsNotNone(block)
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "fakeadb.cmd"
            fake.write_text("@echo off\r\necho error: device offline 1>&2\r\nexit /b 1\r\n", encoding="ascii")
            probe = Path(tmp) / "probe.ps1"
            body = block.group(1).replace("if ($adbOk -and \"$value\".Trim() -eq '1') { $booted = $true; break }",
                                          "if ($adbOk -and \"$value\".Trim() -eq '1') { 'booted' } else { 'not yet' }")
            probe.write_text(
                "$ErrorActionPreference = 'Stop'\r\n"
                f"$adbExe = '{fake}'\r\n$serial = 'emulator-5582'\r\n"
                + body.replace("\n", "\r\n"),
                encoding="ascii",
            )
            done = subprocess.run(
                ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(probe)],
                capture_output=True, text=True, timeout=60,
            )
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(done.stdout.strip(), "not yet")


if __name__ == "__main__":
    unittest.main()
