#!/usr/bin/env python3
"""Allowlisted adb wrapper for the KPKN Fit consolidation AVDs.

Hard guarantees (all enforced before any adb process is spawned):

* Only serials listed in ``qa_paths.OWNED_DEVICES`` can be addressed.
  ``emulator-5554`` and ``emulator-5556`` (and anything else) are refused.
* Every adb invocation carries ``-s <serial>``; global commands that could
  disturb other devices (``kill-server``, ``connect``, ``reboot``, ``root``,
  ``-a/-d/-e`` transport flags ...) are rejected.
* The AVD behind the serial must answer ``adb -s <serial> emu avd name`` with
  exactly the contracted AVD name; ``ro.kernel.qemu`` and ``sys.boot_completed``
  must both be ``1``.
* ``adb devices`` (read-only listing, the single serial-less call) may show
  other emulators; they are ignored and never receive a command.

CLI examples (run with ``python -X utf8``)::

    avd.py status
    avd.py ui-dump --out C:\\tmp\\ui.xml
    avd.py screenshot --out C:\\tmp\\s.png
    avd.py top-resumed
    avd.py logcat --out C:\\tmp\\log.txt [--clear]
    avd.py room-pull --out C:\\tmp\\room1 [--no-stop]
    avd.py stop --yes            # emu kill of the allowlisted emulator only
"""

from __future__ import annotations

import argparse
import io
import json
import os
import re
import shlex
import subprocess
import sys
import tarfile
import time
from pathlib import Path, PurePosixPath
from typing import Any, Callable, Sequence

import uia
from evidence import MissingInput, sha256_bytes, sha256_file, utc_now, write_json
from qa_paths import (
    ADB, DEFAULT_SERIAL, FORBIDDEN_SERIALS, MAIN_ACTIVITY, OWNED_DEVICES, PACKAGE, TEST_PACKAGE,
)

REMOTE_UI_XML = "/sdcard/kpkn-qa-ui.xml"
SERIAL_RE = re.compile(r"^emulator-\d{4,5}$")

# adb sub-commands that must never be issued through this wrapper.
_BLOCKED_ADB_COMMANDS = frozenset({
    "kill-server", "start-server", "connect", "disconnect", "reboot", "root", "unroot", "remount",
    "disable-verity", "enable-verity", "tcpip", "usb", "reverse", "forward", "sideload", "pair", "mdns",
    "wait-for-device", "devices", "host-features", "attach", "detach",
})
_BLOCKED_ADB_FLAGS = frozenset({"-s", "-d", "-e", "-a", "-t", "-H", "-P", "-L"})
_ALLOWED_EMU = (("avd", "name"), ("geo", "fix"), ("kill",))
_PM_PACKAGE_MUTATIONS = frozenset({"clear", "uninstall", "disable", "disable-user", "suspend", "hide", "reset-permissions"})
_OUR_PACKAGES = frozenset({PACKAGE, TEST_PACKAGE})


class OwnedDeviceError(MissingInput):
    """The target is not (or not provably) one of the allowlisted AVDs (nothing was sent to it)."""

    reason = "REFUSED_DEVICE"

    def __init__(self, message: str) -> None:
        super().__init__(message)


# ---------------------------------------------------------------------------
# Pure helpers (unit-tested offline)
# ---------------------------------------------------------------------------

def parse_devices(output: str) -> list[tuple[str, str]]:
    """(serial, state) rows of ``adb devices``; daemon chatter and the header are ignored."""
    rows: list[tuple[str, str]] = []
    for line in output.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("*") or stripped.startswith("List of devices"):
            continue
        parts = stripped.split()
        if len(parts) >= 2:
            rows.append((parts[0], parts[1]))
    return rows


_USER_ROW_RE = re.compile(r"UserInfo\{(\d+):([^:}]*):([0-9a-fA-F]+)\}(?P<tail>[^\n]*)")


def parse_pm_list_users(output: str) -> list[dict[str, Any]]:
    """Rows of ``pm list users``: ``[{"id": 10, "name": "QA10", "flags": "410", "running": False}, ...]``."""
    rows: list[dict[str, Any]] = []
    for match in _USER_ROW_RE.finditer(output):
        rows.append({
            "id": int(match.group(1)), "name": match.group(2), "flags": match.group(3),
            "running": "running" in match.group("tail"),
        })
    return rows


def parse_user_states(dumpsys_user: str) -> dict[int, str]:
    """``dumpsys user`` -> ``{user id: State}`` (e.g. ``RUNNING_UNLOCKED``); users without a State line map to ``UNKNOWN``."""
    states: dict[int, str] = {}
    current: int | None = None
    for line in dumpsys_user.splitlines():
        header = re.match(r"\s*UserInfo\{(\d+):", line)
        if header:
            current = int(header.group(1))
            states.setdefault(current, "UNKNOWN")
            continue
        state = re.match(r"\s*State:\s*(\S+)", line)
        if state and current is not None:
            states[current] = state.group(1)
    return states


def parse_avd_name(output: str) -> list[str]:
    return [line.strip() for line in output.splitlines() if line.strip() and line.strip() != "OK"]


def require_allowlisted_serial(serial: str) -> None:
    if serial in FORBIDDEN_SERIALS:
        raise OwnedDeviceError(f"{serial} is explicitly forbidden (shared device)")
    if not SERIAL_RE.fullmatch(serial or ""):
        raise OwnedDeviceError(f"{serial!r} is not an emulator serial")
    if serial not in OWNED_DEVICES:
        raise OwnedDeviceError(
            f"{serial} is not in the allowlist {sorted(OWNED_DEVICES)}; refusing to send any command"
        )


def validate_owned_device(
    devices_output: str,
    avd_name_output: str,
    qemu_value: str,
    boot_completed: str,
    serial: str = DEFAULT_SERIAL,
) -> dict[str, str]:
    """Pure allowlist verification used by ``Avd.verify`` (and by tests)."""
    require_allowlisted_serial(serial)
    contract = OWNED_DEVICES[serial]
    rows = [row for row in parse_devices(devices_output) if row[0] == serial]
    if rows != [(serial, "device")]:
        raise OwnedDeviceError(f"{serial} must appear exactly once and online; found {rows!r}")
    names = parse_avd_name(avd_name_output)
    if names != [contract.avd_name]:
        raise OwnedDeviceError(f"{serial} reports AVD {names!r}; contract requires {contract.avd_name!r}")
    if qemu_value.strip() != "1":
        raise OwnedDeviceError(f"{serial} is not an emulator (ro.kernel.qemu={qemu_value.strip()!r})")
    if boot_completed.strip() != "1":
        raise OwnedDeviceError(f"{serial} has not finished booting (sys.boot_completed={boot_completed.strip()!r})")
    return {"serial": serial, "avdName": contract.avd_name, "role": contract.role,
            "roKernelQemu": "1", "bootCompleted": "1"}


def guard_adb_args(serial: str, args: Sequence[Any]) -> list[str]:
    """Validate the adb arguments that follow ``-s <serial>``; return them as strings."""
    require_allowlisted_serial(serial)
    items = [str(a) for a in args]
    if not items:
        raise OwnedDeviceError("empty adb command")
    head = items[0]
    if head in _BLOCKED_ADB_FLAGS or head in _BLOCKED_ADB_COMMANDS:
        raise OwnedDeviceError(f"adb {head!r} is not allowed through the owned-AVD wrapper")
    if head == "emu":
        tail = tuple(items[1:3])
        if not any(tail[: len(allowed)] == allowed for allowed in _ALLOWED_EMU):
            raise OwnedDeviceError(f"adb emu {' '.join(items[1:])!r} is not allowed (only avd name / geo fix / kill)")
    if head == "uninstall":
        package = items[-1]
        if package not in _OUR_PACKAGES:
            raise OwnedDeviceError(f"refusing to uninstall {package!r}; only {sorted(_OUR_PACKAGES)}")
    if head == "shell":
        shell_items = items[1:]
        flat = " ".join(shell_items)
        match = re.search(r"\bpm\s+(clear|uninstall|disable-user|disable|suspend|hide|reset-permissions)\b(?:\s+--?\S+)*\s+(\S+)", flat)
        if match and match.group(2).strip("'\"") not in _OUR_PACKAGES:
            raise OwnedDeviceError(f"refusing 'pm {match.group(1)}' on {match.group(2)!r}")
        if re.search(r"\b(reboot|poweroff|svc\s+power|settings\s+put\s+global\s+adb_enabled)\b", flat):
            raise OwnedDeviceError("power / adb-state commands are not allowed")
    return items


def shell_quote_join(args: Sequence[Any]) -> str:
    """Single device-shell string (adb on Windows joins argv with spaces)."""
    return " ".join(shlex.quote(str(a)) for a in args)


_PRIVATE_REL_RE = re.compile(r"^[A-Za-z0-9_./@+=-]+$")


def safe_private_path(relative: str) -> str:
    if (not _PRIVATE_REL_RE.fullmatch(relative) or relative.startswith("/") or ".." in relative.split("/")):
        raise ValueError(f"unsafe app-private relative path {relative!r}")
    return relative


def parse_files_listing(text: str) -> list[dict[str, Any]]:
    """Parse ``stat -c '%n|%s|%Y'`` rows (preferred) or ``ls -lR`` output into file rows."""
    rows: list[dict[str, Any]] = []
    current_dir = ""
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        pipe = line.rsplit("|", 2)
        if len(pipe) == 3 and pipe[1].isdigit():
            rows.append({"path": pipe[0], "bytes": int(pipe[1]), "mtime": int(pipe[2]) if pipe[2].isdigit() else None})
            continue
        if line.endswith(":") and not line.startswith(("-", "d", "l", "total")):
            current_dir = line[:-1].rstrip("/")
            continue
        match = re.match(r"^-[rwx-]{9}\s+\d+\s+\S+\s+\S+\s+(\d+)\s+\S+\s+\S+\s+(.+)$", line)
        if match:
            name = match.group(2)
            rows.append({"path": f"{current_dir}/{name}" if current_dir else name, "bytes": int(match.group(1)), "mtime": None})
    return rows


def safe_member_parts(name: str) -> tuple[str, ...]:
    path = PurePosixPath(name)
    parts = tuple(part for part in path.parts if part not in ("", "."))
    if path.is_absolute() or not parts or ".." in parts:
        raise ValueError(f"unsafe path in app-data archive: {name!r}")
    if parts[0] not in {"databases", "shared_prefs"}:
        raise ValueError(f"unexpected top-level in app-data archive: {name!r}")
    return parts


def extract_app_archive(archive_bytes: bytes, destination: Path) -> list[dict[str, Any]]:
    """Extract only regular files below databases/ and shared_prefs/ and hash them."""
    destination.mkdir(parents=True, exist_ok=False)
    out: list[dict[str, Any]] = []
    seen: set[tuple[str, ...]] = set()
    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:*") as archive:
        for member in archive.getmembers():
            parts = safe_member_parts(member.name)
            if parts in seen:
                raise ValueError(f"duplicate path in app-data archive: {member.name!r}")
            seen.add(parts)
            target = destination.joinpath(*parts)
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            if not member.isfile():
                raise ValueError(f"non-regular file in app-data archive: {member.name!r}")
            target.parent.mkdir(parents=True, exist_ok=True)
            source = archive.extractfile(member)
            if source is None:
                raise ValueError(f"cannot read archive member {member.name!r}")
            payload = source.read()
            target.write_bytes(payload)
            out.append({"path": "/".join(parts), "bytes": len(payload), "sha256": sha256_bytes(payload)})
    return sorted(out, key=lambda item: item["path"])


# ---------------------------------------------------------------------------
# Device wrapper
# ---------------------------------------------------------------------------

Runner = Callable[..., "subprocess.CompletedProcess[bytes]"]


class _RunnerProcess:
    """Popen look-alike used when a custom runner is injected (offline tests): runs at communicate()."""

    def __init__(self, runner: Runner, argv: list[str]) -> None:
        self._runner, self._argv = runner, argv
        self.returncode: int | None = None

    def communicate(self, timeout: float | None = None) -> tuple[bytes, bytes]:
        if self.returncode == -9:  # killed after a timeout: nothing more to collect
            return b"", b""
        result = self._runner(self._argv, timeout=timeout or 0)
        self.returncode = result.returncode
        return result.stdout, result.stderr

    def kill(self) -> None:
        self.returncode = -9


def default_runner(argv: list[str], *, timeout: float, input_bytes: bytes | None = None) -> "subprocess.CompletedProcess[bytes]":
    return subprocess.run(argv, capture_output=True, timeout=timeout, input=input_bytes, check=False)


class Avd:
    """One allowlisted emulator.  Construct, then call :meth:`verify` once."""

    def __init__(self, serial: str = DEFAULT_SERIAL, *, runner: Runner | None = None, adb_path: Path | str | None = None,
                 user: int | None = None) -> None:
        require_allowlisted_serial(serial)
        if user is not None and (not isinstance(user, int) or isinstance(user, bool) or not 0 <= user <= 999):
            raise ValueError(f"user must be an Android user id (0-999), got {user!r}")
        # Android user (profile) the app-scoped helpers address (pm path / run-as / force-stop / am start);
        # None keeps the historical behaviour (the default user, 0).  The wizard UI tests need QA user 10.
        self.user = user
        self.serial = serial
        self.contract = OWNED_DEVICES[serial]
        self.adb_path = str(adb_path or ADB)
        self._runner = runner or default_runner
        self.command_log: list[list[str]] = []
        self.verified: dict[str, str] | None = None
        # Host-only runs (--validate-only, unit tests, KPKN_QA_OFFLINE=1) must never reach adb.
        self.offline = bool(os.environ.get("KPKN_QA_OFFLINE"))

    def _require_online(self) -> None:
        if self.offline:
            raise OwnedDeviceError("offline mode: this run must not invoke adb")

    # -- low level ---------------------------------------------------------
    def raw(self, *args: Any, timeout: float = 45, input_bytes: bytes | None = None) -> "subprocess.CompletedProcess[bytes]":
        self._require_online()
        items = guard_adb_args(self.serial, args)
        argv = [self.adb_path, "-s", self.serial, *items]
        self.command_log.append(argv)
        return self._runner(argv, timeout=timeout, input_bytes=input_bytes)

    def popen(self, *args: Any) -> "subprocess.Popen[bytes]":
        """Guarded long-running adb child (stdout/stderr piped); used by the instrumentation runner."""
        self._require_online()
        items = guard_adb_args(self.serial, args)
        argv = [self.adb_path, "-s", self.serial, *items]
        self.command_log.append(argv)
        if self._runner is not default_runner:
            return _RunnerProcess(self._runner, argv)  # type: ignore[return-value]
        return subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def adb(self, *args: Any, timeout: float = 45, check: bool = True, binary: bool = False) -> Any:
        result = self.raw(*args, timeout=timeout)
        if check and result.returncode != 0:
            detail = (result.stderr + result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb -s {self.serial} {' '.join(map(str, args))[:200]} exited {result.returncode}: {detail[:800]}")
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()

    def shell(self, *args: Any, timeout: float = 45, check: bool = True) -> str:
        return self.adb("shell", *args, timeout=timeout, check=check)

    def sh(self, script: str, *, timeout: float = 45, check: bool = True) -> str:
        """Run one device-shell command line (already quoted by the caller)."""
        return self.adb("shell", script, timeout=timeout, check=check)

    @property
    def run_as_prefix(self) -> str:
        """``run-as <pkg>`` (+ ``--user N`` when this Avd addresses a secondary Android user)."""
        return f"run-as {PACKAGE}" + (f" --user {self.user}" if self.user is not None else "")

    @property
    def user_args(self) -> list[str]:
        return ["--user", str(self.user)] if self.user is not None else []

    def run_as_sh(self, script: str, *, timeout: float = 45, check: bool = True) -> str:
        return self.adb("shell", f"{self.run_as_prefix} sh -c {shlex.quote(script)}", timeout=timeout, check=check)

    def list_devices(self) -> list[tuple[str, str]]:
        """Serial-less read-only listing; other emulators are visible but never addressed."""
        self._require_online()
        result = self._runner([self.adb_path, "devices", "-l"], timeout=30)
        if result.returncode != 0:
            raise OwnedDeviceError("adb devices failed: " + result.stderr.decode("utf-8", errors="replace"))
        return parse_devices(result.stdout.decode("utf-8", errors="replace"))

    def verify(self) -> dict[str, str]:
        self._require_online()
        devices = self._runner([self.adb_path, "devices", "-l"], timeout=30)
        if devices.returncode != 0:
            raise OwnedDeviceError("adb devices failed: " + devices.stderr.decode("utf-8", errors="replace"))
        info = validate_owned_device(
            devices.stdout.decode("utf-8", errors="replace"),
            self.adb("emu", "avd", "name", timeout=30),
            self.getprop("ro.kernel.qemu"),
            self.getprop("sys.boot_completed"),
            self.serial,
        )
        self.verified = info
        return info

    def getprop(self, name: str) -> str:
        return self.shell("getprop", name, timeout=30)

    def describe(self) -> dict[str, Any]:
        info = dict(self.verify())
        info.update({
            "sdk": self.getprop("ro.build.version.sdk"),
            "release": self.getprop("ro.build.version.release"),
            "abi": self.getprop("ro.product.cpu.abi"),
            "model": self.getprop("ro.product.model"),
            "fingerprint": self.getprop("ro.build.fingerprint"),
            "bootAvdName": self.getprop("ro.boot.qemu.avd_name"),
            "density": self.shell("wm", "density", check=False),
            "size": self.shell("wm", "size", check=False),
            "atUtc": utc_now(),
        })
        return info

    # -- package / process -------------------------------------------------
    def pm_paths(self, package: str = PACKAGE) -> list[str]:
        out = self.shell("pm", "path", *self.user_args, package, check=False)
        return [line.strip()[len("package:"):] for line in out.splitlines() if line.strip().startswith("package:")]

    def is_installed(self, package: str = PACKAGE) -> bool:
        return bool(self.pm_paths(package))

    def installed_apk(self, package: str = PACKAGE) -> dict[str, str]:
        """Single base.apk path + its SHA-256 as measured on the device."""
        paths = self.pm_paths(package)
        base = [p for p in paths if p.endswith("/base.apk")]
        if len(paths) != 1 or len(base) != 1 or not re.fullmatch(r"/data/app/[A-Za-z0-9_./+=~-]+/base\.apk", base[0]):
            raise RuntimeError(f"expected exactly one installed base APK for {package}, got {paths!r}")
        digest = self.shell("sha256sum", base[0], timeout=180).split()[0].lower()
        return {"path": base[0], "sha256": digest}

    def pidof(self, package: str = PACKAGE) -> list[str]:
        return re.findall(r"\b\d+\b", self.shell("pidof", package, check=False))

    def force_stop(self, package: str = PACKAGE, *, wait: float = 15.0) -> None:
        self.shell("am", "force-stop", *self.user_args, package)
        deadline = time.monotonic() + wait
        while time.monotonic() < deadline:
            if not self.pidof(package):
                return
            time.sleep(0.25)
        raise RuntimeError(f"force-stop did not terminate {package}: {self.pidof(package)}")

    def dumpsys_package(self, package: str = PACKAGE) -> str:
        return self.shell("dumpsys", "package", package, timeout=60)

    def grant(self, permission: str, package: str = PACKAGE) -> None:
        self.shell("pm", "grant", package, permission)

    def revoke(self, permission: str, package: str = PACKAGE) -> None:
        self.shell("pm", "revoke", package, permission, check=False)

    def permission_granted(self, permission: str, package: str = PACKAGE) -> bool | None:
        text = self.dumpsys_package(package)
        match = re.search(re.escape(permission) + r": granted=(true|false)", text)
        return None if match is None else match.group(1) == "true"

    def pm_clear(self, package: str = PACKAGE) -> str:
        if package not in _OUR_PACKAGES:
            raise OwnedDeviceError(f"refusing pm clear {package}")
        return self.shell("pm", "clear", package, timeout=60)

    # -- Android users ---------------------------------------------------------
    def list_users(self) -> list[dict[str, Any]]:
        return parse_pm_list_users(self.shell("pm", "list", "users"))

    def user_states(self) -> dict[int, str]:
        return parse_user_states(self.shell("dumpsys", "user", timeout=60))

    def current_user(self) -> int:
        out = self.shell("am", "get-current-user", check=False).strip()
        if not out.isdigit():
            raise RuntimeError(f"unexpected `am get-current-user` output {out!r}")
        return int(out)

    def switch_user(self, user: int, *, timeout: float = 120) -> dict[str, Any]:
        """``am switch-user`` and wait until it is the current user, RUNNING_UNLOCKED (an activity of a background user never resumes)."""
        if not any(row["id"] == user for row in self.list_users()):
            raise RuntimeError(f"Android user {user} does not exist on {self.serial}")
        before = self.current_user()
        if before != user:
            self.shell("am", "switch-user", str(user), timeout=timeout)
        deadline = time.monotonic() + timeout
        state = "UNKNOWN"
        while time.monotonic() < deadline:
            state = self.user_states().get(user, "UNKNOWN")
            if self.current_user() == user and state == "RUNNING_UNLOCKED":
                return {"user": user, "previousUser": before, "state": state, "switched": before != user}
            time.sleep(1.0)
        raise RuntimeError(f"user {user} did not become the current RUNNING_UNLOCKED user within {timeout}s (state={state})")

    def wake_and_unlock(self, *, timeout: float = 30) -> dict[str, Any]:
        """Wake the display and dismiss a non-secure keyguard (a freshly switched user starts asleep behind the lock screen)."""
        deadline = time.monotonic() + timeout
        state: dict[str, Any] = {}
        while time.monotonic() < deadline:
            self.keyevent("KEYCODE_WAKEUP")
            self.shell("wm", "dismiss-keyguard", check=False)
            power = self.shell("dumpsys", "power", timeout=30, check=False)
            window = self.shell("dumpsys", "window", timeout=30, check=False)
            wake = re.search(r"mWakefulness=(\w+)", power)
            keyguard = re.search(r"isKeyguardShowing=(true|false)", window)
            state = {"wakefulness": wake.group(1) if wake else None,
                     "keyguardShowing": (keyguard.group(1) == "true") if keyguard else None}
            if state["wakefulness"] == "Awake" and state["keyguardShowing"] is False:
                return state
            time.sleep(1.0)
        raise RuntimeError(f"display did not wake / unlock within {timeout}s: {state}")

    # -- activity ----------------------------------------------------------
    def dumpsys_activities(self) -> str:
        return self.shell("dumpsys", "activity", "activities", timeout=60)

    def top_resumed(self) -> str | None:
        return uia.parse_top_resumed(self.dumpsys_activities())

    def is_main_top_resumed(self) -> bool:
        return uia.component_is(self.top_resumed(), PACKAGE, MAIN_ACTIVITY)

    def start_main(self, *, flags: str = "0x20000000", timeout: float = 90) -> str:
        return self.shell("am", "start", "-W", *self.user_args, "-f", flags, "-n", f"{PACKAGE}/{MAIN_ACTIVITY}", timeout=timeout)

    def start_deeplink(self, uri: str, *, flags: str = "0x20000000", timeout: float = 90) -> str:
        if not re.fullmatch(r"kpkn://[A-Za-z0-9_./:?=&%-]+", uri):
            raise ValueError(f"unexpected deep link {uri!r}")
        return self.shell(
            "am", "start", "-W", *self.user_args, "-f", flags, "-a", "android.intent.action.VIEW", "-d", uri,
            "-n", f"{PACKAGE}/{MAIN_ACTIVITY}", timeout=timeout,
        )

    def wait_main_top_resumed(self, *, timeout: float = 30, stable: int = 2) -> str:
        deadline = time.monotonic() + timeout
        hits, last = 0, None
        while time.monotonic() < deadline:
            last = self.top_resumed()
            if uia.component_is(last, PACKAGE, MAIN_ACTIVITY):
                hits += 1
                if hits >= stable:
                    return str(last)
            else:
                hits = 0
            time.sleep(0.5)
        raise RuntimeError(f"MainActivity did not become top-resumed; last={last!r}")

    # -- input -------------------------------------------------------------
    def tap(self, x: int, y: int) -> None:
        self.shell("input", "tap", int(x), int(y))

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 550) -> None:
        self.shell("input", "swipe", int(x1), int(y1), int(x2), int(y2), int(duration_ms))

    def keyevent(self, code: str) -> None:
        if not re.fullmatch(r"KEYCODE_[A-Z0-9_]+|\d{1,3}", code):
            raise ValueError(f"unexpected key {code!r}")
        self.shell("input", "keyevent", code)

    def poke_user_activity(self) -> None:
        """Reset the screen-off timer without touching the app.

        ``KEYCODE_WAKEUP`` is stripped of PASS_TO_USER by the window manager, so on an already-awake display it does NOT
        reset ``PowerManager``'s last-user-activity time (measured: ``dumpsys power`` ``lastUserActivityTime`` kept growing
        after it).  A zero-delta trackball event does reset it (900 ms ago after the call) and carries no key code, no
        pointer position and no motion for Compose to react to.  No device setting is modified.
        """
        self.shell("input", "trackball", "roll", 0, 0)

    def key_combo(self, *codes: str) -> None:
        for code in codes:
            if not re.fullmatch(r"KEYCODE_[A-Z0-9_]+", code):
                raise ValueError(f"unexpected key {code!r}")
        self.shell("input", "keycombination", *codes)

    def input_text(self, text: str) -> None:
        if not re.fullmatch(r"[A-Za-z0-9_.,@ -]{1,120}", text):
            raise ValueError("input_text only accepts bounded ASCII alphanumeric QA text")
        self.shell("input", "text", text.replace(" ", "%s"))

    def ime_observation(self) -> uia.ImeObservation:
        return uia.parse_ime_visibility(self.shell("dumpsys", "input_method", timeout=30))

    def ime_visible(self) -> bool | None:
        state = self.ime_observation().visibility
        return None if state == "unknown" else state == "shown"

    def geo_fix(self, lon: float, lat: float, alt: float = 10.0) -> None:
        self.adb("emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}", f"{alt:.1f}")

    # -- capture -----------------------------------------------------------
    def capture_ui_xml(self, remote_path: str = REMOTE_UI_XML, *, timeout: float = 90) -> tuple[bytes, dict[str, Any]]:
        return uia.dump_fresh_xml_until_ready(remote_path, lambda *a, timeout: self.raw(*a, timeout=timeout), timeout=timeout)

    def screenshot_bytes(self) -> bytes:
        data = self.adb("exec-out", "screencap", "-p", binary=True, timeout=60)
        if not data.startswith(b"\x89PNG"):
            raise RuntimeError("screencap did not return a PNG")
        return data

    def screenshot(self, path: Path) -> str:
        data = self.screenshot_bytes()
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return sha256_bytes(data)

    def logcat_clear(self) -> None:
        self.adb("logcat", "-b", "all", "-c", check=False)

    def logcat_dump(self, path: Path, *, filter_args: Sequence[str] = (), timeout: float = 90) -> dict[str, Any]:
        data = self.adb("logcat", "-b", "all", "-d", "-v", "threadtime", *filter_args, binary=True, timeout=timeout)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return {"path": str(path), "bytes": len(data), "sha256": sha256_bytes(data)}

    def device_time_ms(self) -> int:
        return int(self.shell("date", "+%s%3N").strip())

    # -- file transfer -----------------------------------------------------
    def push(self, local: Path, remote: str, *, timeout: float = 180) -> None:
        self.adb("push", str(local), remote, timeout=timeout)

    def install(self, apk: Path, *flags: str, timeout: float = 900) -> str:
        return self.adb("install", *flags, str(apk), timeout=timeout)

    def remote_sha256(self, remote: str) -> str:
        return self.shell("sha256sum", remote, timeout=120).split()[0].lower()

    # -- run-as (app-private) ----------------------------------------------
    def run_as_exists(self, relative: str, kind: str = "-e") -> bool:
        path = safe_private_path(relative)
        out = self.run_as_sh(f"if [ {kind} {path} ]; then printf PRESENT; else printf ABSENT; fi")
        if out not in {"PRESENT", "ABSENT"}:
            raise RuntimeError(f"unexpected existence response for {path!r}: {out!r}")
        return out == "PRESENT"

    def run_as_cat(self, relative: str, *, timeout: float = 120) -> bytes:
        path = safe_private_path(relative)
        return self.adb("exec-out", f"{self.run_as_prefix} cat {path}", binary=True, timeout=timeout)

    def run_as_sha256(self, relative: str) -> str:
        path = safe_private_path(relative)
        out = self.shell(f"{self.run_as_prefix} sha256sum {path}", timeout=120)
        match = re.match(r"^([0-9a-fA-F]{64})\s", out)
        if match is None:
            raise RuntimeError(f"cannot parse sha256sum for {path!r}: {out!r}")
        return match.group(1).lower()

    def run_as_files_tree(self, relative_dir: str = "files") -> list[dict[str, Any]]:
        directory = safe_private_path(relative_dir)
        if not self.run_as_exists(directory, "-d"):
            return []
        out = self.run_as_sh(f'find {directory} -type f -exec stat -c "%n|%s|%Y" {{}} +', check=False, timeout=90)
        rows = parse_files_listing(out)
        if rows:
            return sorted(rows, key=lambda r: r["path"])
        out = self.run_as_sh(f"ls -lR {directory}", check=False, timeout=90)
        return parse_files_listing(out)

    def run_as_push(self, local: Path, relative: str, *, staging_name: str | None = None) -> str:
        """Copy a host file into the app-private dir via /data/local/tmp; returns its device SHA-256."""
        path = safe_private_path(relative)
        stage = f"/data/local/tmp/kpkn-qa-{staging_name or sha256_file(local)[:16]}"
        self.push(local, stage)
        try:
            parent = str(PurePosixPath(path).parent)
            if parent not in ("", "."):
                self.shell(f"{self.run_as_prefix} mkdir -p {parent}")
            self.shell(f"{self.run_as_prefix} cp {stage} {path}")
            return self.run_as_sha256(path)
        finally:
            self.shell("rm", "-f", stage, check=False)

    # -- Room ----------------------------------------------------------------
    def room_pull(
        self,
        dest: Path,
        *,
        stop_app: bool = True,
        extra_private_files: Sequence[str] = (),
        include_files_tree: bool = True,
    ) -> dict[str, Any]:
        """Copy databases/ (db + WAL + SHM) and shared_prefs/ with SHA-256 hashes.

        With ``stop_app`` (default) the app is force-stopped first so db and WAL
        form a consistent pair; ``consistent`` in the manifest records that.
        ``dest`` must not exist.  Returns the manifest dict (also written to
        ``dest/manifest.json``).
        """
        if stop_app:
            self.force_stop(PACKAGE)
        present_dirs = [d for d in ("databases", "shared_prefs") if self.run_as_exists(d, "-d")]
        if "databases" not in present_dirs:
            raise RuntimeError("app-private databases/ directory is missing; nothing to capture")
        archive = self.adb("exec-out", f"{self.run_as_prefix} tar cf - {' '.join(present_dirs)}", binary=True, timeout=180)
        if not archive:
            raise RuntimeError("adb returned an empty app-data archive")
        dest.mkdir(parents=True, exist_ok=False)
        archive_path = dest / "app-data.tar"
        archive_path.write_bytes(archive)
        files = extract_app_archive(archive, dest / "app-data")
        by_path = {item["path"]: item for item in files}
        if "databases/kpkn.db" not in by_path:
            raise RuntimeError("captured archive lacks databases/kpkn.db")
        extras: list[dict[str, Any]] = []
        for relative in extra_private_files:
            safe_private_path(relative)
            if not self.run_as_exists(relative, "-f"):
                extras.append({"path": relative, "present": False})
                continue
            payload = self.run_as_cat(relative)
            target = dest / "extra" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(payload)
            extras.append({"path": relative, "present": True, "bytes": len(payload), "sha256": sha256_bytes(payload)})
        manifest: dict[str, Any] = {
            "schema": "kpkn-room-pull/v1",
            "capturedAtUtc": utc_now(),
            "serial": self.serial,
            "avdName": self.contract.avd_name,
            "package": PACKAGE,
            "appStoppedBeforeCapture": bool(stop_app),
            "consistent": bool(stop_app) and not self.pidof(PACKAGE),
            "archive": {"path": "app-data.tar", "bytes": len(archive), "sha256": sha256_bytes(archive)},
            "files": files,
            "database": {
                "db": by_path.get("databases/kpkn.db"),
                "wal": by_path.get("databases/kpkn.db-wal"),
                "shm": by_path.get("databases/kpkn.db-shm"),
            },
            "extra": extras,
        }
        if include_files_tree:
            manifest["filesTree"] = self.run_as_files_tree("files")
        write_json(dest / "manifest.json", manifest)
        return manifest


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default=DEFAULT_SERIAL, help=f"allowlisted serial (default {DEFAULT_SERIAL})")
    parser.add_argument("--user", type=int, default=None,
                        help="Android user id the app-scoped commands address (pm path / run-as / force-stop); default: user 0")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("users", help="pm list users + dumpsys user states + current user")
    switch = sub.add_parser("switch-user", help="am switch-user N and wait until it is RUNNING_UNLOCKED")
    switch.add_argument("target", type=int)
    sub.add_parser("status", help="verify allowlist + print device description")
    ui = sub.add_parser("ui-dump", help="fresh UiAutomator XML")
    ui.add_argument("--out", type=Path, required=True)
    shot = sub.add_parser("screenshot", help="PNG screenshot")
    shot.add_argument("--out", type=Path, required=True)
    sub.add_parser("top-resumed", help="print topResumedActivity component")
    log = sub.add_parser("logcat", help="dump (or clear) logcat")
    log.add_argument("--out", type=Path)
    log.add_argument("--clear", action="store_true")
    room = sub.add_parser("room-pull", help="copy kpkn.db + WAL + prefs with hashes")
    room.add_argument("--out", type=Path, required=True)
    room.add_argument("--no-stop", action="store_true", help="do not force-stop the app (db/WAL may be torn)")
    stop = sub.add_parser("stop", help="emu kill of the allowlisted emulator")
    stop.add_argument("--yes", action="store_true", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        device = Avd(args.serial, user=args.user)
        if args.command == "status":
            print(json.dumps(device.describe(), indent=2, ensure_ascii=False))
            return 0
        device.verify()
        if args.command == "users":
            print(json.dumps({"currentUser": device.current_user(), "users": device.list_users(),
                              "states": {str(k): v for k, v in device.user_states().items()}}, indent=2))
        elif args.command == "switch-user":
            print(json.dumps(device.switch_user(args.target), indent=2))
        elif args.command == "ui-dump":
            xml, meta = device.capture_ui_xml()
            args.out.parent.mkdir(parents=True, exist_ok=True)
            args.out.write_bytes(xml)
            print(json.dumps(meta, indent=2, ensure_ascii=False, default=str))
        elif args.command == "screenshot":
            print(json.dumps({"path": str(args.out), "sha256": device.screenshot(args.out)}))
        elif args.command == "top-resumed":
            print(device.top_resumed() or "")
        elif args.command == "logcat":
            if args.clear:
                device.logcat_clear()
            if args.out:
                print(json.dumps(device.logcat_dump(args.out)))
        elif args.command == "room-pull":
            print(json.dumps(device.room_pull(args.out, stop_app=not args.no_stop), indent=2, ensure_ascii=False))
        elif args.command == "stop":
            print(device.adb("emu", "kill", check=False))
        return 0
    except OwnedDeviceError as error:
        print(f"REFUSED: {error}", file=sys.stderr)
        return 4
    except Exception as error:  # noqa: BLE001
        print(f"FAILED: {type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
