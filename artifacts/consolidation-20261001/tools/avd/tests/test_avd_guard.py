"""Allowlist, command guard and parsing of the adb wrapper (no adb is ever spawned)."""

from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import avd  # noqa: E402
from avd import Avd, OwnedDeviceError  # noqa: E402

DEVICES_OK = (
    "List of devices attached\n"
    "emulator-5554\tdevice product:sdk_gphone64_x86_64 model:Pixel transport_id:1\n"
    "emulator-5556\tdevice product:sdk model:x transport_id:2\n"
    "emulator-5580\tdevice product:sdk_gphone64_x86_64 model:sdk transport_id:3\n"
)


class FakeRunner:
    """Records argv and answers like a booted audit AVD."""

    def __init__(self, avd_name: str = "KPKNFitSessionAudit20260929", devices: str = DEVICES_OK, qemu: str = "1", boot: str = "1"):
        self.calls: list[list[str]] = []
        self.avd_name, self.devices, self.qemu, self.boot = avd_name, devices, qemu, boot

    def __call__(self, argv, *, timeout, input_bytes=None):
        self.calls.append(list(argv))
        out = b""
        if argv[1:3] == ["devices", "-l"]:
            out = self.devices.encode()
        elif argv[3:6] == ["emu", "avd", "name"]:
            out = f"{self.avd_name}\r\nOK\r\n".encode()
        elif argv[3:6] == ["shell", "getprop", "ro.kernel.qemu"]:
            out = self.qemu.encode() + b"\r\n"
        elif argv[3:6] == ["shell", "getprop", "sys.boot_completed"]:
            out = self.boot.encode() + b"\r\n"
        return subprocess.CompletedProcess(argv, 0, out, b"")


class AllowlistTests(unittest.TestCase):
    def test_forbidden_and_unknown_serials_are_refused_before_any_adb(self):
        for serial in ("emulator-5554", "emulator-5556", "emulator-5558", "emulator-5581", "R5CT1234", "", "emulator-5580; rm"):
            runner = FakeRunner()
            with self.assertRaises(OwnedDeviceError, msg=serial):
                Avd(serial, runner=runner)
            self.assertEqual(runner.calls, [])

    def test_owned_serials_construct_without_touching_adb(self):
        runner = FakeRunner()
        Avd("emulator-5580", runner=runner)
        Avd("emulator-5582", runner=runner)
        self.assertEqual(runner.calls, [])

    def test_validate_happy_path_ignores_other_emulators(self):
        info = avd.validate_owned_device(DEVICES_OK, "KPKNFitSessionAudit20260929\r\nOK\r\n", "1\n", "1\n", "emulator-5580")
        self.assertEqual(info["avdName"], "KPKNFitSessionAudit20260929")

    def test_wrong_avd_name_is_refused(self):
        with self.assertRaises(OwnedDeviceError):
            avd.validate_owned_device(DEVICES_OK, "Pixel_9_Pro_XL\r\nOK\r\n", "1", "1", "emulator-5580")
        with self.assertRaises(OwnedDeviceError):  # the wizard AVD name on the audit serial
            avd.validate_owned_device(DEVICES_OK, "KPKNWizchatQA\r\nOK\r\n", "1", "1", "emulator-5580")

    def test_wizard_contract(self):
        devices = DEVICES_OK + "emulator-5582\tdevice\n"
        info = avd.validate_owned_device(devices, "KPKNWizchatQA\nOK", "1", "1", "emulator-5582")
        self.assertEqual(info["role"], "wizard")

    def test_offline_duplicate_unbooted_or_non_emulator_are_refused(self):
        offline = DEVICES_OK.replace("emulator-5580\tdevice", "emulator-5580\toffline")
        duplicate = DEVICES_OK + "emulator-5580\tdevice\n"
        for devices, qemu, boot in ((offline, "1", "1"), (duplicate, "1", "1"), (DEVICES_OK, "0", "1"), (DEVICES_OK, "1", "")):
            with self.assertRaises(OwnedDeviceError):
                avd.validate_owned_device(devices, "KPKNFitSessionAudit20260929", qemu, boot, "emulator-5580")
        with self.assertRaises(OwnedDeviceError):
            avd.validate_owned_device("List of devices attached\n", "KPKNFitSessionAudit20260929", "1", "1", "emulator-5580")

    def test_verify_end_to_end_with_fake_runner(self):
        runner = FakeRunner()
        device = Avd("emulator-5580", runner=runner)
        self.assertEqual(device.verify()["serial"], "emulator-5580")
        serial_calls = [c for c in runner.calls if c[1:3] != ["devices", "-l"]]
        self.assertTrue(serial_calls)
        for call in serial_calls:
            self.assertEqual(call[1:3], ["-s", "emulator-5580"], call)

    def test_verify_rejects_wrong_avd_behind_allowlisted_serial(self):
        device = Avd("emulator-5580", runner=FakeRunner(avd_name="Pixel_9_Pro_XL"))
        with self.assertRaises(OwnedDeviceError):
            device.verify()

    def test_offline_mode_blocks_every_adb_path(self):
        runner = FakeRunner()
        device = Avd("emulator-5580", runner=runner)
        device.offline = True
        for call in (lambda: device.raw("shell", "ls"), lambda: device.adb("shell", "ls"), device.verify, device.list_devices,
                     lambda: device.popen("shell", "ls")):
            with self.assertRaises(OwnedDeviceError):
                call()
        self.assertEqual(runner.calls, [])


class CommandGuardTests(unittest.TestCase):
    def test_blocked_commands_and_flags(self):
        for args in (["kill-server"], ["start-server"], ["connect", "x"], ["reboot"], ["root"], ["-s", "emulator-5554", "shell", "ls"],
                     ["-d", "shell", "ls"], ["forward", "tcp:1", "tcp:2"], ["wait-for-device"], ["devices"], []):
            with self.assertRaises(OwnedDeviceError, msg=args):
                avd.guard_adb_args("emulator-5580", args)

    def test_emu_whitelist(self):
        self.assertEqual(avd.guard_adb_args("emulator-5580", ["emu", "avd", "name"])[0], "emu")
        avd.guard_adb_args("emulator-5580", ["emu", "geo", "fix", "-122.0", "37.4", "10.0"])
        avd.guard_adb_args("emulator-5580", ["emu", "kill"])
        for tail in (["sms", "send"], ["gsm", "call", "1"], ["power", "off"], ["kill-all"], []):
            with self.assertRaises(OwnedDeviceError, msg=tail):
                avd.guard_adb_args("emulator-5580", ["emu", *tail])

    def test_package_mutations_only_for_our_packages(self):
        avd.guard_adb_args("emulator-5580", ["shell", "pm", "clear", "com.example.kpkn"])
        avd.guard_adb_args("emulator-5580", ["shell", "pm", "clear", "com.example.kpkn.test"])
        avd.guard_adb_args("emulator-5580", ["uninstall", "com.example.kpkn"])
        for args in (["shell", "pm", "clear", "com.android.chrome"], ["shell", "pm", "uninstall", "-k", "com.google.android.gms"],
                     ["shell", "pm", "disable-user", "--user", "0", "com.google.android.apps.maps"], ["uninstall", "com.other"],
                     ["shell", "reboot"], ["shell", "svc power shutdown"]):
            with self.assertRaises(OwnedDeviceError, msg=args):
                avd.guard_adb_args("emulator-5580", args)

    def test_guard_requires_allowlisted_serial(self):
        with self.assertRaises(OwnedDeviceError):
            avd.guard_adb_args("emulator-5554", ["shell", "ls"])

    def test_every_command_the_wrapper_sends_carries_the_serial(self):
        runner = FakeRunner()
        device = Avd("emulator-5580", runner=runner)
        device.shell("getprop", "ro.kernel.qemu")
        device.tap(10, 20)
        device.keyevent("KEYCODE_BACK")
        for call in runner.calls:
            self.assertEqual(call[1:3], ["-s", "emulator-5580"])


class ParsingTests(unittest.TestCase):
    def test_parse_devices_skips_daemon_lines(self):
        rows = avd.parse_devices("* daemon not running; starting now at tcp:5037\n* daemon started successfully\n"
                                 "List of devices attached\nemulator-5580\tdevice\nemulator-5554\toffline\n")
        self.assertEqual(rows, [("emulator-5580", "device"), ("emulator-5554", "offline")])

    def test_parse_avd_name(self):
        self.assertEqual(avd.parse_avd_name("KPKNFitSessionAudit20260929\r\nOK\r\n"), ["KPKNFitSessionAudit20260929"])

    def test_parse_files_listing_stat_and_ls(self):
        self.assertEqual(avd.parse_files_listing("files/cardio-gps/abc.json|120|1790000000\n")[0]["bytes"], 120)
        listing = ("files/cardio-gps:\n-rw------- 1 u0_a123 u0_a123 345 2026-10-01 20:00 abc.json\n"
                   "files:\ndrwx------ 2 u0_a1 u0_a1 4096 2026-10-01 20:00 cardio-gps\n")
        rows = avd.parse_files_listing(listing)
        self.assertEqual(rows, [{"path": "files/cardio-gps/abc.json", "bytes": 345, "mtime": None}])

    def test_safe_private_path(self):
        self.assertEqual(avd.safe_private_path("files/workout_media/2026-10/x.jpg"), "files/workout_media/2026-10/x.jpg")
        for bad in ("/data/x", "../x", "a b", "files/../x", "a;b"):
            with self.assertRaises(ValueError):
                avd.safe_private_path(bad)

    def test_archive_extraction_rejects_traversal_and_foreign_roots(self):
        import io
        import tarfile
        import tempfile

        def tar_with(name: str, payload: bytes = b"x") -> bytes:
            buffer = io.BytesIO()
            with tarfile.open(fileobj=buffer, mode="w") as archive:
                info = tarfile.TarInfo(name)
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))
            return buffer.getvalue()

        with tempfile.TemporaryDirectory() as tmp:
            files = avd.extract_app_archive(tar_with("databases/kpkn.db", b"sqlite"), Path(tmp) / "ok")
            self.assertEqual(files[0]["path"], "databases/kpkn.db")
            for index, name in enumerate(("../evil", "/abs/x", "files/x.bin", "databases/../../x")):
                with self.assertRaises(ValueError, msg=name):
                    avd.extract_app_archive(tar_with(name), Path(tmp) / f"bad{index}")


if __name__ == "__main__":
    unittest.main()
