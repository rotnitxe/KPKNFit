#!/usr/bin/env python3
"""Install the integrated debug APK + androidTest APK on an allowlisted AVD and
write a JSON receipt (flavor, paths, SHA-256 source vs installed, versionCode).

Default inputs (the integrated tree, per flavor):

    android-native/app/build/outputs/apk/<flavor>/debug/app-<flavor>-debug.apk
    android-native/app/build/outputs/apk/androidTest/<flavor>/debug/app-<flavor>-debug-androidTest.apk

Examples::

    python -X utf8 install_apks.py --flavor base --dry-run     # host-only inspection, no device
    python -X utf8 install_apks.py --flavor base
    python -X utf8 install_apks.py --flavor health --no-test-apk

A receipt is PASS only when, for each installed APK, the SHA-256 measured on the
device (``sha256sum`` of the single ``base.apk``) equals the SHA-256 of the file
on the host.  The device's ``dumpsys package`` versionCode is recorded as well
and must equal the APK manifest versionCode.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path
from typing import Any

import apk_info
from avd import Avd, OwnedDeviceError
from evidence import EXIT_FAIL, EXIT_MISSING_INPUT, EXIT_PASS, MissingInput, new_run_dir, utc_now, write_json
from qa_paths import (
    APP_DIR, APP_SRC, DEFAULT_SERIAL, EVIDENCE_ROOT, FLAVORS, PACKAGE, RUNNER, TEST_PACKAGE, app_apk, test_apk,
)

SCHEMA = "kpkn-apk-install-receipt/v1"


def host_checks(apk: Path, *, flavor: str, expected_package: str, is_test: bool, allow_stale: bool = False) -> dict[str, Any]:
    """Validate an APK on the host; raises MissingInput with an actionable hint."""
    if not apk.is_file():
        raise MissingInput(f"APK not found: {apk}", "build it first (the build owner runs assembleDebug / assembleAndroidTest)")
    try:
        info = apk_info.inspect_apk(apk)
    except apk_info.ApkInfoError as error:
        raise MissingInput(f"APK unreadable: {apk}: {error}") from error
    manifest = info["manifest"]
    if manifest["package"] != expected_package:
        raise MissingInput(f"{apk.name} has package {manifest['package']!r}, expected {expected_package!r}")
    if not manifest["debuggable"]:
        raise MissingInput(f"{apk.name} is not debuggable; run-as based Room capture would fail")
    if is_test:
        if manifest.get("instrumentationTarget") != PACKAGE:
            raise MissingInput(f"{apk.name} does not declare <instrumentation targetPackage={PACKAGE}>")
    elif manifest["versionCode"] is None:
        raise MissingInput(f"{apk.name} manifest has no versionCode")
    meta = info.get("outputMetadata")
    if meta and meta.get("variantName"):
        expected_variant = flavor + ("DebugAndroidTest" if is_test else "Debug")
        if meta["variantName"] != expected_variant:
            raise MissingInput(
                f"{apk.name} was built as variant {meta['variantName']!r}, expected {expected_variant!r} "
                f"(a stale or wrong-flavor build?)"
            )
    if flavor not in apk.as_posix():
        raise MissingInput(f"APK path does not mention flavor {flavor!r}: {apk}")
    roots = [APP_SRC / "main", APP_DIR / "build.gradle.kts"] + ([APP_SRC / "androidTest"] if is_test else [])
    info["staleness"] = apk_info.staleness(apk, roots)
    if info["staleness"]["apkOlderThanSources"] and not allow_stale:
        raise MissingInput(
            f"{apk.name} is older than the integrated sources ({info['staleness']['newestSourceFile']}); "
            "it was built before the latest source change",
            "rebuild it, or pass --allow-stale to install it knowingly (receipt records the staleness)",
        )
    return info


def install_verified(
    avd: Avd,
    apk: Path,
    *,
    package: str,
    flags: tuple[str, ...] = ("-r",),
    expect_version_code: int | None = None,
    timeout: float = 900,
) -> dict[str, Any]:
    """``adb install`` then prove the installed bytes are the host file's bytes."""
    expected = apk_info.inspect_apk(apk)["sha256"]
    before: dict[str, Any] | None = None
    if avd.is_installed(package):
        try:
            before = avd.installed_apk(package)
        except RuntimeError as error:
            before = {"error": str(error)}
    timed_out = False
    started = time.monotonic()
    try:
        output = avd.install(apk, *flags, timeout=timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        output = "host wait timed out; verifying the actual installed binary"
    except RuntimeError as error:
        raise RuntimeError(f"adb install failed for {apk.name}: {error}") from error
    seconds = round(time.monotonic() - started, 1)
    installed = avd.installed_apk(package)
    package_facts = apk_info.parse_dumpsys_package(avd.dumpsys_package(package))
    matched = installed["sha256"] == expected
    version_ok = expect_version_code is None or package_facts["versionCode"] == expect_version_code
    return {
        "package": package,
        "sourcePath": str(apk),
        "sourceBytes": apk.stat().st_size,
        "sourceSha256": expected,
        "installedPath": installed["path"],
        "installedSha256": installed["sha256"],
        "matched": matched,
        "installFlags": list(flags),
        "installSeconds": seconds,
        "hostWaitTimedOut": timed_out,
        "adbOutput": output,
        "before": before,
        "deviceVersionCode": package_facts["versionCode"],
        "deviceVersionName": package_facts["versionName"],
        "deviceLastUpdateTime": package_facts["lastUpdateTime"],
        "deviceDebuggable": package_facts["debuggable"],
        "versionCodeMatchesManifest": version_ok,
    }


def run(args: argparse.Namespace) -> tuple[dict[str, Any], int]:
    user: int | None = getattr(args, "user", None)
    flavor = args.flavor
    app = Path(args.apk) if args.apk else app_apk(flavor)
    tests = None if args.no_test_apk else (Path(args.test_apk) if args.test_apk else test_apk(flavor))
    receipt: dict[str, Any] = {
        "schema": SCHEMA, "flavor": flavor, "createdAtUtc": utc_now(), "serial": args.serial,
        "dryRun": bool(args.dry_run), "result": "NOT_RUN",
    }
    problems: list[str] = []
    inspected: dict[str, Any] = {}
    for label, path, expected_pkg, is_test in (
        ("app", app, PACKAGE, False), *((("test", tests, TEST_PACKAGE, True),) if tests else ()),
    ):
        try:
            inspected[label] = host_checks(path, flavor=flavor, expected_package=expected_pkg, is_test=is_test,
                                           allow_stale=args.allow_stale)
        except MissingInput as error:
            problems.append(str(error))
    receipt["hostInspection"] = inspected
    if problems:
        receipt.update(result="MISSING_INPUT", problems=problems)
        return receipt, EXIT_MISSING_INPUT
    if args.dry_run:
        receipt["result"] = "DRY_RUN_OK"
        return receipt, EXIT_PASS
    avd = Avd(args.serial, user=user)
    receipt["device"] = avd.describe()
    receipt["androidUser"] = user
    if user is not None:
        # `adb install --user N` on a package that user N lacks; the other users' data is never touched
        # (no pm clear / uninstall is issued; a same-signature `-r` keeps every user's data).
        users = avd.list_users()
        receipt["users"] = users
        if not any(row["id"] == user for row in users):
            receipt.update(result="MISSING_INPUT", problems=[f"Android user {user} does not exist on {args.serial}"])
            return receipt, EXIT_MISSING_INPUT
    user_flags = ("--user", str(user)) if user is not None else ()
    app_flags = (("-r", "-d") if args.allow_downgrade else ("-r",)) + user_flags
    app_info = inspected["app"]["manifest"]
    receipt["app"] = install_verified(avd, app, package=PACKAGE, flags=app_flags,
                                      expect_version_code=app_info["versionCode"])
    ok = receipt["app"]["matched"] and receipt["app"]["versionCodeMatchesManifest"]
    if tests:
        receipt["test"] = install_verified(avd, tests, package=TEST_PACKAGE, flags=("-r", "-t") + user_flags)
        ok = ok and receipt["test"]["matched"]
        instrumentation = avd.shell("pm", "list", "instrumentation", check=False)
        receipt["instrumentationRegistered"] = RUNNER in instrumentation and f"target={PACKAGE}" in instrumentation
        ok = ok and receipt["instrumentationRegistered"]
    receipt["result"] = "PASS" if ok else "FAIL"
    return receipt, EXIT_PASS if ok else EXIT_FAIL


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--flavor", choices=FLAVORS, required=True)
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--apk", type=Path, help="override the integrated debug APK path")
    parser.add_argument("--test-apk", type=Path, help="override the androidTest APK path")
    parser.add_argument("--no-test-apk", action="store_true", help="install only the app APK")
    parser.add_argument("--user", type=int, default=None,
                        help="install for this Android user (adb install --user N), e.g. 10 for the wizard QA user; default: user 0")
    parser.add_argument("--allow-downgrade", action="store_true", help="pass -d (keeps data; Android refuses downgrades otherwise)")
    parser.add_argument("--allow-stale", action="store_true",
                        help="install an APK older than the integrated sources (recorded in the receipt)")
    parser.add_argument("--dry-run", action="store_true", help="host-only: validate and hash the APKs, touch no device")
    parser.add_argument("--receipt-dir", type=Path, default=None, help="default: device-evidence/installs/<flavor>/<run>")
    args = parser.parse_args(argv)
    out_dir: Path | None = None
    try:
        out_dir = args.receipt_dir or new_run_dir("installs", args.flavor, "dryrun" if args.dry_run else None)
        out_dir.mkdir(parents=True, exist_ok=True)
        receipt, code = run(args)
    except OwnedDeviceError as error:
        receipt, code = {"schema": SCHEMA, "result": "REFUSED", "error": str(error)}, EXIT_MISSING_INPUT
    except Exception as error:  # noqa: BLE001
        receipt, code = {"schema": SCHEMA, "result": "FAIL", "error": f"{type(error).__name__}: {error}"}, EXIT_FAIL
    if out_dir is not None:
        receipt["receiptPath"] = str(out_dir / "install-receipt.json")
        write_json(out_dir / "install-receipt.json", receipt)
        if not args.dry_run and receipt.get("result") == "PASS":
            pointer = EVIDENCE_ROOT / "installs" / args.flavor / "latest.json"
            write_json(pointer, {"receipt": receipt["receiptPath"], "flavor": args.flavor,
                                 "appSha256": receipt["app"]["installedSha256"], "atUtc": utc_now()})
    print(json.dumps(receipt, indent=2, ensure_ascii=False, default=str))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
