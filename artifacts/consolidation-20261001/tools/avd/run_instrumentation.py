#!/usr/bin/env python3
"""Run exact androidTest classes/methods with ``am instrument -w -r`` on an
allowlisted AVD and compare *declared* (static @Test discovery) against
*reported* (instrumentation output) tests.

Examples::

    python -X utf8 run_instrumentation.py --flavor base \\
        --classes com.example.kpkn.screens.workout.WorkoutV2UiTest
    python -X utf8 run_instrumentation.py --flavor base --scenario APPEND \\
        --classes com.example.kpkn.screens.sessioneditor.SessionEditorTransferMaterializationAvdTest
    python -X utf8 run_instrumentation.py --flavor health \\
        --classes com.example.kpkn.services.cardio.CardioGpsAvdLifecycleInstrumentedTest \\
        --prepare-gps --geo-feed
    python -X utf8 run_instrumentation.py --flavor base --classes-file classes.txt --timeout 2400
    python -X utf8 run_instrumentation.py --flavor base --serial emulator-5582 --user 10 --switch-user \\
        --classes-file suites/wizard-ui.txt --screencap-every 3

``--screencap-every N`` saves an ``adb exec-out screencap -p`` frame of the display every N seconds while ``am instrument``
runs (``device-evidence/wizard/frames/<run name>/frame-NNNN-<UTC stamp>.png`` + ``manifest.json``, at most 400 frames, never
blocking or failing the run); it is the way to see the screens when the test's own PNGs sit in secondary-user storage.

A run is PASSED only if: every declared test was reported, every reported test
PASSED (skips and assumption failures are NOT passes), the runner printed
``OK (N tests)`` and ``INSTRUMENTATION_CODE: -1``, nothing crashed, and the
installed app APK is byte-identical to the requested build output.

Evidence: ``device-evidence/instrumentation/<flavor>/<run>/`` with
metadata.json, declared.json, stdout.txt, stderr.txt, logcat.txt, summary.json.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
from pathlib import Path
import threading
from typing import Any

from avd import Avd, OwnedDeviceError
from gps_tools import GeoFeeder, prepare_gps
from evidence import (
    EXIT_FAIL, EXIT_MISSING_INPUT, EXIT_PASS, new_run_dir, sha256_bytes, sha256_file, utc_now, utc_stamp, write_json,
)
from kotlin_tests import index_test_sources, resolve_declared
from qa_paths import (
    ADB, ANDROID_TEST_SRC, DEFAULT_SERIAL, FLAVORS, PACKAGE, RUNNER, TEST_PACKAGE, app_apk, test_apk,
)

STATUS_NAMES = {0: "PASSED", -1: "ERROR", -2: "FAILED", -3: "SKIPPED", -4: "SKIPPED"}
_STATUS_RE = re.compile(r"^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$")
_CODE_RE = re.compile(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$")
_RESULT_RE = re.compile(r"^INSTRUMENTATION_RESULT: ([^=]+)=(.*)$")
_FINAL_RE = re.compile(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$")
_OK_RE = re.compile(r"^OK \((\d+) tests?\)\s*$", re.MULTILINE)
_FAIL_SUMMARY_RE = re.compile(r"^Tests run:\s*(\d+),\s*Failures:\s*(\d+)", re.MULTILINE)


class AwakeKeeper(threading.Thread):
    """Every ``interval`` seconds send ``KEYCODE_WAKEUP`` (wakes a display that already slept) and a zero-delta trackball
    event (``Avd.poke_user_activity``: resets the screen-off timer of an awake display).

    ``KEYCODE_WAKEUP`` alone is NOT enough: the window manager strips ``ACTION_PASS_TO_USER`` from it, so on an awake display
    it never reaches ``PowerManager.userActivity`` and the 60 s timeout of the secondary user still fires (wizard-ui-fixed
    run: ``Going to sleep due to timeout`` at 16:10:42, 16:11:47, 16:12:49, 16:13:50 UTC, each followed ~2-5 s later by a
    ``WAKE_KEY`` wake-up; the sleep during test 2 left the activity stopped and the Compose rule with no hierarchy).  The
    app under test sees no key code and no device setting is modified.  Transient adb failures are counted, not fatal.
    """

    def __init__(self, avd: Avd, interval: float = 15.0) -> None:
        super().__init__(daemon=True)
        self.avd, self.interval = avd, interval
        self.stop_event = threading.Event()
        self.sent = 0
        self.pokes = 0
        self.error: str | None = None

    def run(self) -> None:
        while not self.stop_event.is_set():
            try:
                self.avd.keyevent("KEYCODE_WAKEUP")
                self.sent += 1
                poke = getattr(self.avd, "poke_user_activity", None)
                if poke is not None:
                    poke()
                    self.pokes += 1
            except Exception as error:  # noqa: BLE001
                self.error = f"{type(error).__name__}: {error}"
            self.stop_event.wait(self.interval)

    def stop(self) -> None:
        self.stop_event.set()
        if self.is_alive():
            self.join(timeout=10)


SCREENCAP_MAX_FRAMES = 400


def screencap_frames_dir(out_dir: Path) -> Path:
    """``<evidence root>/wizard/frames/<instrumentation run name>``; the root is derived from ``out_dir``
    (``<root>/instrumentation/<flavor>/<run>``) so it follows whatever ``evidence.new_run_dir`` used."""
    return out_dir.parents[2] / "wizard" / "frames" / out_dir.name


def positive_seconds(text: str) -> float:
    """argparse ``type=`` for ``--screencap-every``: a finite number of seconds > 0."""
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"not a number of seconds: {text!r}") from None
    if not value > 0 or value == float("inf"):
        raise argparse.ArgumentTypeError(f"seconds must be a finite number > 0, got {text!r}")
    return value


class ScreencapRecorder(threading.Thread):
    """Host thread that saves a numbered, time-stamped PNG of the emulator display every ``interval`` seconds.

    Needed when the test's own screenshots cannot be pulled (secondary Android user storage is not readable from the
    adb shell on a non-root image).  Frames come from ``Avd.screenshot_bytes`` (``adb -s <serial> exec-out screencap -p``,
    a read-only command that the allowlisted wrapper already accepts), run in parallel to ``am instrument`` without ever
    blocking it: a failing capture is counted (``failed`` / ``error``), never raised, and the thread is a daemon.
    The first frame is taken immediately (even if ``stop`` is already set) and capture ends by itself after ``max_frames``
    (``limit_reached``).  Names are ``frame-NNNN-<UTC stamp>.png``; ``manifest.json`` lists them with size and SHA-256.
    """

    def __init__(self, avd: Avd, directory: Path, interval: float, max_frames: int = SCREENCAP_MAX_FRAMES) -> None:
        super().__init__(daemon=True, name="screencap-recorder")
        if not interval > 0:
            raise ValueError(f"interval must be > 0, got {interval!r}")
        self.avd, self.directory, self.interval = avd, Path(directory), float(interval)
        self.max_frames = max(1, min(int(max_frames), SCREENCAP_MAX_FRAMES))
        self.stop_event = threading.Event()
        self.saved = 0
        self.failed = 0
        self.error: str | None = None
        self.limit_reached = False
        self.frames: list[dict[str, Any]] = []

    def capture_once(self) -> None:
        try:
            data = self.avd.screenshot_bytes()
            number = self.saved + 1
            name = f"frame-{number:04d}-{utc_stamp()}.png"
            self.directory.mkdir(parents=True, exist_ok=True)
            (self.directory / name).write_bytes(data)
            self.frames.append({"name": name, "bytes": len(data), "sha256": sha256_bytes(data)})
            self.saved = number
        except Exception as error:  # noqa: BLE001 - a lost frame must never affect the instrumentation run
            self.failed += 1
            self.error = f"{type(error).__name__}: {error}"

    def run(self) -> None:
        next_at = time.monotonic()
        while True:
            self.capture_once()
            if self.saved >= self.max_frames:
                self.limit_reached = True
                break
            next_at += self.interval
            # A slow capture (or an adb stall) must not queue up bursts: re-anchor on the clock.
            now = time.monotonic()
            if next_at < now:
                next_at = now
            if self.stop_event.wait(next_at - now):
                break

    def stop(self) -> None:
        self.stop_event.set()
        if self.is_alive():
            self.join(timeout=10)
        if self.frames:
            write_json(self.directory / "manifest.json", {
                "schema": "kpkn-screencap-frames/v1", "everySeconds": self.interval, "maxFrames": self.max_frames,
                "saved": self.saved, "failed": self.failed, "limitReached": self.limit_reached, "frames": list(self.frames),
            })

    def summary(self) -> dict[str, Any]:
        return {
            "everySeconds": self.interval, "directory": str(self.directory), "maxFrames": self.max_frames,
            "saved": self.saved, "failed": self.failed, "limitReached": self.limit_reached, "error": self.error,
        }


# ---------------------------------------------------------------------------
# Pure parsing / evaluation (unit-tested)
# ---------------------------------------------------------------------------

def parse_instrument_output(text: str) -> dict[str, Any]:
    """Parse ``am instrument -r`` raw output, including multi-line ``stack=`` values."""
    cases: list[dict[str, Any]] = []
    started: list[tuple[str, str]] = []
    block: dict[str, str] = {}
    last_key: str | None = None
    result_fields: dict[str, str] = {}
    last_result_key: str | None = None
    final_code: int | None = None
    failed_message: str | None = None
    in_result = False

    for raw_line in text.splitlines():
        line = raw_line.rstrip("\r")
        code = _CODE_RE.match(line)
        if code:
            value = int(code.group(1))
            cls, test = block.get("class"), block.get("test")
            if cls and test:
                if value == 1:
                    started.append((cls, test))
                else:
                    case = {"class": cls, "test": test, "statusCode": value, "status": STATUS_NAMES.get(value, "FAILED")}
                    if block.get("stack"):
                        case["stack"] = block["stack"][:4000]
                    cases.append(case)
            block, last_key, in_result = {}, None, False
            continue
        status = _STATUS_RE.match(line)
        if status:
            block[status.group(1)] = status.group(2)
            last_key, in_result = status.group(1), False
            continue
        result = _RESULT_RE.match(line)
        if result:
            result_fields[result.group(1)] = result.group(2)
            last_result_key, in_result, last_key = result.group(1), True, None
            continue
        final = _FINAL_RE.match(line)
        if final:
            final_code = int(final.group(1))
            in_result, last_key = False, None
            continue
        if line.startswith("INSTRUMENTATION_FAILED:"):
            failed_message = line[len("INSTRUMENTATION_FAILED:"):].strip()
            continue
        # continuation of the previous multi-line value
        if last_key is not None and not line.startswith("INSTRUMENTATION_"):
            block[last_key] = block.get(last_key, "") + "\n" + line
        elif in_result and last_result_key is not None and not line.startswith("INSTRUMENTATION_"):
            result_fields[last_result_key] = result_fields.get(last_result_key, "") + "\n" + line

    finished = {(c["class"], c["test"]) for c in cases}
    unfinished = [pair for pair in started if pair not in finished]
    ok_match = _OK_RE.search(text)
    fail_match = _FAIL_SUMMARY_RE.search(text)
    numtests = None
    for match in re.finditer(r"^INSTRUMENTATION_STATUS: numtests=(\d+)", text, re.MULTILINE):
        numtests = int(match.group(1))
    return {
        "cases": cases,
        "started": started,
        "unfinished": unfinished,
        "numtests": numtests,
        "okLineTests": int(ok_match.group(1)) if ok_match else None,
        "failureSummary": {"run": int(fail_match.group(1)), "failures": int(fail_match.group(2))} if fail_match else None,
        "instrumentationCode": final_code,
        "instrumentationFailed": failed_message,
        "processCrashed": bool(re.search(r"Process crashed|shortMsg=Process crashed|FATAL EXCEPTION", text)),
        "resultFields": {k: v[:2000] for k, v in result_fields.items()},
    }


def name_matches(declared_method: str, reported: str) -> bool:
    return reported == declared_method or reported.startswith(declared_method + "[") or reported.startswith(declared_method + "(")


def evaluate(declared: list[tuple[str, str]], report: dict[str, Any], *, returncode: int, timed_out: bool) -> dict[str, Any]:
    """Compare declared vs reported and decide PASSED / FAILED_OR_INCOMPLETE."""
    cases = report["cases"]
    missing = [
        pair for pair in declared
        if not any(c["class"] == pair[0] and name_matches(pair[1], c["test"]) for c in cases)
    ]
    declared_by_class: dict[str, list[str]] = {}
    for cls, method in declared:
        declared_by_class.setdefault(cls, []).append(method)
    unexpected = [
        (c["class"], c["test"]) for c in cases
        if not any(name_matches(m, c["test"]) for m in declared_by_class.get(c["class"], []))
    ]
    not_passed = [c for c in cases if c["status"] != "PASSED"]
    reasons: list[str] = []
    if timed_out:
        reasons.append("timed out")
    if returncode != 0:
        reasons.append(f"adb exit code {returncode}")
    if missing:
        reasons.append(f"{len(missing)} declared test(s) not reported")
    if unexpected:
        reasons.append(f"{len(unexpected)} reported test(s) were not declared")
    if not_passed:
        reasons.append(f"{len(not_passed)} reported test(s) not PASSED")
    if report["unfinished"]:
        reasons.append("test(s) started but never finished")
    if report["okLineTests"] is None:
        reasons.append("runner did not print 'OK (N tests)'")
    elif report["okLineTests"] != len(cases):
        reasons.append(f"'OK ({report['okLineTests']} tests)' but {len(cases)} cases parsed")
    if report["instrumentationCode"] != -1:
        reasons.append(f"INSTRUMENTATION_CODE={report['instrumentationCode']!r} (expected -1)")
    if report["instrumentationFailed"]:
        reasons.append("INSTRUMENTATION_FAILED: " + report["instrumentationFailed"])
    if report["processCrashed"]:
        reasons.append("process crash marker in output")
    if not declared:
        reasons.append("nothing declared")
    status = "PASSED" if not reasons else "FAILED_OR_INCOMPLETE"
    return {
        "status": status,
        "reasons": reasons,
        "declaredTests": len(declared),
        "reportedTests": len(cases),
        "missing": [list(pair) for pair in missing],
        "unexpected": [list(pair) for pair in unexpected],
        "notPassed": [{k: v for k, v in c.items() if k != "stack"} | ({"stack": c["stack"][:1500]} if "stack" in c else {})
                      for c in not_passed],
    }


def build_command(classes: list[str], scenario: str | None, extras: list[str], user: int | None = None) -> list[str]:
    """Arguments after ``adb -s <serial>`` (the allowlisted wrapper adds the serial)."""
    command = ["shell", "am", "instrument", "-w", "-r"]
    if user is not None:
        command += ["--user", str(user)]
    command += ["-e", "class", ",".join(classes)]
    if scenario:
        command += ["-e", "scenario", scenario]
    for pair in extras:
        key, _, value = pair.partition("=")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.]*", key) or not re.fullmatch(r"[A-Za-z0-9_.,:-]*", value):
            raise ValueError(f"unsafe -e extra {pair!r}")
        command += ["-e", key, value]
    command.append(RUNNER)
    return command


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

def load_selections(args: argparse.Namespace) -> list[str]:
    selections: list[str] = []
    if args.classes:
        selections += [item.strip() for item in args.classes.split(",") if item.strip()]
    if args.classes_file:
        for line in Path(args.classes_file).read_text(encoding="utf-8").splitlines():
            line = line.split("#", 1)[0].strip()
            if line:
                selections.append(line)
    return selections


def run(args: argparse.Namespace, avd: Avd | None = None) -> tuple[dict[str, Any], int]:
    user: int | None = getattr(args, "user", None)
    selections = load_selections(args)
    if not selections:
        raise SystemExit("pass --classes and/or --classes-file")
    index = index_test_sources(Path(args.source_root))
    try:
        declared = resolve_declared(selections, index)
    except (ValueError, LookupError) as error:
        return {"status": "MISSING_INPUT", "error": str(error)}, EXIT_MISSING_INPUT
    flavor = args.flavor
    apk = Path(args.apk) if args.apk else app_apk(flavor)
    out_dir = new_run_dir("instrumentation", flavor, args.label)
    write_json(out_dir / "declared.json", [list(pair) for pair in declared])

    avd = avd or Avd(args.serial, user=user)
    device = avd.verify()
    metadata: dict[str, Any] = {
        "schema": "kpkn-instrumentation-run/v1", "flavor": flavor, "startedAtUtc": utc_now(), "device": device,
        "selections": selections, "scenario": args.scenario, "extras": args.extra,
        "declaredTests": len(declared), "timeoutSeconds": args.timeout,
    }
    # 1. instrumentation + app must be installed, and the app must be the requested build
    # `pm list instrumentation` rejects `--user` on Android 16 ("Unknown option: --user"), so list it without the flag
    # and, for a secondary Android user, additionally require both packages to be installed for that user.
    instrumentation = avd.shell("pm", "list", "instrumentation", check=False)
    metadata["instrumentationRegistered"] = RUNNER in instrumentation and f"target={PACKAGE}" in instrumentation
    if user is not None and metadata["instrumentationRegistered"]:
        installed_for_user = {PACKAGE: avd.is_installed(PACKAGE), TEST_PACKAGE: avd.is_installed(TEST_PACKAGE)}
        metadata["installedForUser"] = {"user": user, **installed_for_user}
        metadata["instrumentationRegistered"] = all(installed_for_user.values())
    if not metadata["instrumentationRegistered"]:
        metadata["status"] = "MISSING_INPUT"
        metadata["error"] = f"{RUNNER} is not registered; run install_apks.py --flavor {flavor} first"
        write_json(out_dir / "metadata.json", metadata)
        return metadata, EXIT_MISSING_INPUT
    if args.no_apk_check:
        metadata["apkCheck"] = "SKIPPED (--no-apk-check)"
    else:
        if not apk.is_file():
            metadata.update(status="MISSING_INPUT", error=f"requested app APK not found: {apk}")
            write_json(out_dir / "metadata.json", metadata)
            return metadata, EXIT_MISSING_INPUT
        installed = avd.installed_apk(PACKAGE)
        expected = sha256_file(apk)
        metadata["apkCheck"] = {"path": str(apk), "expectedSha256": expected, "installedSha256": installed["sha256"],
                                "matched": expected == installed["sha256"]}
        if expected != installed["sha256"]:
            metadata.update(status="MISSING_INPUT",
                            error="installed app APK differs from the requested build; reinstall with install_apks.py")
            write_json(out_dir / "metadata.json", metadata)
            return metadata, EXIT_MISSING_INPUT
        test_apk_path = Path(args.test_apk) if args.test_apk else test_apk(flavor)
        if not test_apk_path.is_file():
            metadata.update(status="MISSING_INPUT", error=f"androidTest APK not found: {test_apk_path}")
            write_json(out_dir / "metadata.json", metadata)
            return metadata, EXIT_MISSING_INPUT
        installed_test = avd.installed_apk(TEST_PACKAGE)
        expected_test = sha256_file(test_apk_path)
        metadata["testApkCheck"] = {"path": str(test_apk_path), "expectedSha256": expected_test,
                                    "installedSha256": installed_test["sha256"], "matched": expected_test == installed_test["sha256"]}
        if expected_test != installed_test["sha256"]:
            metadata.update(status="MISSING_INPUT",
                            error="installed androidTest APK differs from the build output; reinstall with install_apks.py")
            write_json(out_dir / "metadata.json", metadata)
            return metadata, EXIT_MISSING_INPUT
    if args.prepare_gps:
        metadata["gpsPreparation"] = prepare_gps(avd)
    metadata["androidUser"] = user
    previous_user: int | None = None
    if user is not None:
        users = avd.list_users()
        metadata["users"] = users
        if not any(row["id"] == user for row in users):
            metadata.update(status="MISSING_INPUT", error=f"Android user {user} does not exist on {args.serial}")
            write_json(out_dir / "metadata.json", metadata)
            return metadata, EXIT_MISSING_INPUT
        if getattr(args, "switch_user", False):
            # An activity of a background user never resumes, so Compose rules need the user in the foreground.
            previous_user = avd.current_user()
            metadata["userSwitch"] = avd.switch_user(user)
            metadata["wakeUnlock"] = avd.wake_and_unlock()
    command = build_command(selections, args.scenario, args.extra, user)
    metadata["command"] = [str(ADB), "-s", args.serial, *command]
    screencap_every = getattr(args, "screencap_every", None)
    metadata["screencapEverySeconds"] = screencap_every
    write_json(out_dir / "metadata.json", metadata)

    avd.logcat_clear()
    feeder = GeoFeeder(avd) if args.geo_feed else None
    # A freshly created secondary user keeps the 60 s default screen timeout (user 0 of this AVD has it disabled): the
    # display slept ~60 s into the run, the keyguard came back and every later Compose rule failed with "No compose
    # hierarchies found". KEYCODE_WAKEUP is consumed by the window manager (never delivered to the app) and no persistent
    # device setting is changed.
    keeper = AwakeKeeper(avd) if (getattr(args, "keep_awake", False) or getattr(args, "switch_user", False)) else None
    # Optional host-side display frames (the test's own PNGs live in secondary-user storage that adb cannot read here).
    recorder = None
    if screencap_every:
        frames_dir = Path(args.screencap_dir) if getattr(args, "screencap_dir", None) else screencap_frames_dir(out_dir)
        recorder = ScreencapRecorder(avd, frames_dir, float(screencap_every))
    timed_out = False
    started = time.monotonic()
    if keeper:
        keeper.start()
    if feeder:
        feeder.start()
    process = avd.popen(*command)
    if recorder:  # after popen: a refused/failed launch must not leave a capture thread behind
        recorder.start()
    try:
        stdout_b, stderr_b = process.communicate(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        timed_out = True
        process.kill()
        stdout_b, stderr_b = process.communicate()
        for package in (TEST_PACKAGE, PACKAGE):
            avd.shell("am", "force-stop", *avd.user_args, package, check=False)
    finally:
        if keeper:
            keeper.stop()
        if feeder:
            feeder.stop_event.set()
            feeder.join(timeout=10)
        if recorder:
            recorder.stop()
    seconds = round(time.monotonic() - started, 1)
    output = stdout_b.decode("utf-8", errors="replace")
    (out_dir / "stdout.txt").write_text(output, encoding="utf-8")
    (out_dir / "stderr.txt").write_bytes(stderr_b)
    log_info = avd.logcat_dump(out_dir / "logcat.txt")
    if previous_user is not None and avd.current_user() != previous_user:
        try:
            metadata["userRestore"] = avd.switch_user(previous_user)
        except Exception as error:  # noqa: BLE001
            metadata["userRestore"] = {"error": f"{type(error).__name__}: {error}", "wantedUser": previous_user}
        write_json(out_dir / "metadata.json", metadata)
    report = parse_instrument_output(output)
    verdict = evaluate(declared, report, returncode=process.returncode if not timed_out else -1, timed_out=timed_out)
    summary = {
        "schema": "kpkn-instrumentation-summary/v1",
        **verdict,
        "flavor": flavor,
        "scenario": args.scenario,
        "returnCode": process.returncode,
        "timedOut": timed_out,
        "seconds": seconds,
        "cases": report["cases"],
        "instrumentationCode": report["instrumentationCode"],
        "geoFeed": {"fixesSent": feeder.sent, "error": feeder.error} if feeder else None,
        "keepAwake": {"wakeupsSent": keeper.sent, "userActivityPokes": keeper.pokes, "error": keeper.error} if keeper else None,
        "screencap": recorder.summary() if recorder else None,
        "androidUser": user,
        "userSwitch": metadata.get("userSwitch"),
        "userRestore": metadata.get("userRestore"),
        "evidence": str(out_dir),
        "stdoutSha256": sha256_file(out_dir / "stdout.txt"),
        "logcat": log_info,
    }
    if feeder and feeder.error:
        summary["status"] = "FAILED_OR_INCOMPLETE"
        summary["reasons"] = summary["reasons"] + [f"geo feed failed: {feeder.error}"]
    write_json(out_dir / "summary.json", summary)
    return summary, EXIT_PASS if summary["status"] == "PASSED" else EXIT_FAIL


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--flavor", choices=FLAVORS)
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--user", type=int, default=None,
                        help="run the instrumentation as this Android user (am instrument --user N), e.g. 10 for the wizard QA user")
    parser.add_argument("--switch-user", action="store_true",
                        help="with --user: make that user the foreground user for the run and restore the previous one afterwards")
    parser.add_argument("--keep-awake", action="store_true",
                        help="send KEYCODE_WAKEUP every 15 s during the run (implied by --switch-user)")
    parser.add_argument("--list", action="store_true", help="host-only: print every discovered androidTest class with its @Test methods")
    parser.add_argument("--classes", help="comma separated exact FQCN or FQCN#method")
    parser.add_argument("--classes-file", type=Path, help="one selection per line (# comments allowed)")
    parser.add_argument("--scenario", choices=("APPEND", "REPLACE", "CREATE"),
                        help="-e scenario (SessionEditorTransferMaterializationAvdTest)")
    parser.add_argument("--extra", action="append", default=[], metavar="KEY=VALUE", help="additional -e key value (repeatable)")
    parser.add_argument("--timeout", type=int, default=1800, help="seconds before the run is killed (default 1800)")
    parser.add_argument("--apk", type=Path, help="app APK expected to be installed (default: integrated build output)")
    parser.add_argument("--test-apk", type=Path, help="androidTest APK expected to be installed (default: integrated build output)")
    parser.add_argument("--no-apk-check", action="store_true", help="skip installed-vs-built APK hash check (recorded)")
    parser.add_argument("--source-root", type=Path, default=ANDROID_TEST_SRC)
    parser.add_argument("--prepare-gps", action="store_true", help="grant location/notification perms + enable location (GPS lifecycle classes)")
    parser.add_argument("--geo-feed", action="store_true", help="feed synthetic 'emu geo fix' points while the run executes")
    parser.add_argument("--screencap-every", type=positive_seconds, default=None, metavar="SECONDS",
                        help="while 'am instrument' runs, save an 'adb exec-out screencap -p' PNG of the display every SECONDS "
                             "(e.g. 3) as frame-NNNN-<UTC stamp>.png, at most %d frames, never blocking the run; default "
                             "directory: device-evidence/wizard/frames/<run name>/" % SCREENCAP_MAX_FRAMES)
    parser.add_argument("--screencap-dir", type=Path, default=None,
                        help="with --screencap-every: write the frames here instead of the default frames directory")
    parser.add_argument("--label", help="short label appended to the evidence directory name")
    args = parser.parse_args(argv)
    if args.list:
        index = index_test_sources(Path(args.source_root))
        listing = {name: entries[0]["methods"] for name, entries in sorted(index.items())}
        print(json.dumps({"classes": len(listing), "methods": sum(map(len, listing.values())), "tests": listing}, indent=2, ensure_ascii=False))
        return EXIT_PASS
    if not args.flavor:
        parser.error("--flavor is required unless --list is used")
    try:
        summary, code = run(args)
    except OwnedDeviceError as error:
        summary, code = {"status": "REFUSED", "error": str(error)}, EXIT_MISSING_INPUT
    print(json.dumps(summary, indent=2, ensure_ascii=False, default=str))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
