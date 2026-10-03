#!/usr/bin/env python3
"""Run the whole device validation for one flavor, in the documented order, one subprocess per stage.

    python -X utf8 run_suite.py --flavor base --plan                  # print the plan, touch nothing
    python -X utf8 run_suite.py --flavor base                         # all stages, stop at the first non-PASS
    python -X utf8 run_suite.py --flavor base --continue-on-fail
    python -X utf8 run_suite.py --flavor base --only install,strength,camerax
    python -X utf8 run_suite.py --flavor health --skip instr-full
    python -X utf8 run_suite.py --flavor base --serial emulator-5582  # wizard AVD: install + instr-wizard

Each stage is an independent script that writes its own evidence under ``device-evidence/``; this
runner only sequences them, records command / exit code / overall verdict / evidence directory of
every stage in ``device-evidence/suites/<flavor>/<run>/suite.json`` and prints that summary.

The runner never starts the emulator (use start_avd.ps1) and never builds (the build owner does).
A stage verdict comes from the stage's JSON: PASS only when the stage printed overall PASS /
status PASSED; NOT_RUN and MISSING_INPUT are reported as such, never as PASS.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from typing import Any

from avd import OwnedDeviceError, require_allowlisted_serial
from evidence import new_run_dir, utc_now, write_json
from qa_paths import DEFAULT_SERIAL, FLAVORS, OWNED_DEVICES, TOOLS_DIR

SUITES = TOOLS_DIR / "suites"
AUDIT_ONLY = "audit"
WIZARD_ONLY = "wizard"


def stage_table(flavor: str, serial: str, allow_stale: bool) -> list[dict[str, Any]]:
    common = ["--flavor", flavor, "--serial", serial]
    stale = ["--allow-stale"] if allow_stale else []
    ui = lambda script, *extra: [sys.executable, "-X", "utf8", str(TOOLS_DIR / script), *common, *extra, *stale]
    instr = lambda *extra: [sys.executable, "-X", "utf8", str(TOOLS_DIR / "run_instrumentation.py"), *common, *extra]
    return [
        {"name": "install", "roles": ("audit", "wizard"),
         "argv": [sys.executable, "-X", "utf8", str(TOOLS_DIR / "install_apks.py"), "--flavor", flavor, "--serial", serial, *stale]},
        {"name": "instr-workout", "roles": ("audit",), "argv": instr("--classes-file", str(SUITES / "workout-avd.txt"), "--label", "workout-avd")},
        {"name": "instr-gps", "roles": ("audit",),
         "argv": instr("--classes-file", str(SUITES / "gps-lifecycle.txt"), "--prepare-gps", "--geo-feed", "--label", "gps-lifecycle")},
        {"name": "strength", "roles": ("audit",), "argv": ui("ui_strength_s1_s2.py")},
        {"name": "cancel-strength", "roles": ("audit",), "argv": ui("ui_cancel_workout.py", "--mode", "strength")},
        {"name": "cancel-cardio", "roles": ("audit",), "argv": ui("ui_cancel_workout.py", "--mode", "cardio")},
        {"name": "drafts-restore", "roles": ("audit",), "argv": ui("ui_editor_drafts.py", "--scenario", "RESTORE")},
        {"name": "drafts-save", "roles": ("audit",), "argv": ui("ui_editor_drafts.py", "--scenario", "SAVE")},
        {"name": "drafts-discard", "roles": ("audit",), "argv": ui("ui_editor_drafts.py", "--scenario", "DISCARD")},
        {"name": "transfer-append", "roles": ("audit",), "argv": ui("ui_editor_transfer.py", "--scenario", "APPEND")},
        {"name": "transfer-replace", "roles": ("audit",), "argv": ui("ui_editor_transfer.py", "--scenario", "REPLACE")},
        {"name": "transfer-create", "roles": ("audit",), "argv": ui("ui_editor_transfer.py", "--scenario", "CREATE")},
        {"name": "cardio", "roles": ("audit",), "argv": ui("ui_cardio_two_series.py")},
        {"name": "media-import", "roles": ("audit",), "argv": ui("ui_media_import.py")},
        {"name": "camerax", "roles": ("audit",), "argv": ui("camerax_capture.py", "--finish")},
        {"name": "migration", "roles": ("audit",), "argv": ui("migrate_27_28.py")},
        {"name": "instr-wizard", "roles": ("wizard",),
         "argv": instr("--classes-file", str(SUITES / "wizard-ui.txt"), "--label", "wizard-ui")},
        {"name": "instr-full", "roles": ("audit",), "optional": True,
         "argv": instr("--classes-file", str(SUITES / "all-android-tests.txt"), "--timeout", "3600", "--label", "all-android-tests")},
    ]


def verdict_of(stdout: str, returncode: int) -> tuple[str, str | None]:
    """(PASS | FAIL | NOT_RUN | MISSING_INPUT, evidence dir) from a stage's printed JSON and exit code."""
    try:
        data = json.loads(stdout)
    except json.JSONDecodeError:
        return ("FAIL" if returncode else "NOT_RUN"), None
    evidence = data.get("evidenceDir") or data.get("evidence") or data.get("receiptPath")
    overall = data.get("overall") or data.get("status") or data.get("result")
    if returncode == 4 or data.get("reason") in ("MISSING_INPUT", "REFUSED_DEVICE"):
        return "MISSING_INPUT", evidence
    if overall in ("PASS", "PASSED") and returncode == 0:
        return "PASS", evidence
    if overall in ("NOT_RUN", "DRY_RUN_OK"):
        return "NOT_RUN", evidence
    return "FAIL", evidence


def select(stages: list[dict[str, Any]], role: str, only: set[str], skip: set[str], with_full: bool) -> list[dict[str, Any]]:
    chosen = []
    for stage in stages:
        if role not in stage["roles"]:
            continue
        if stage.get("optional") and not with_full and stage["name"] not in only:
            continue
        if only and stage["name"] not in only:
            continue
        if stage["name"] in skip:
            continue
        chosen.append(stage)
    return chosen


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--flavor", choices=FLAVORS, required=True)
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--only", default="", help="comma separated stage names")
    parser.add_argument("--skip", default="", help="comma separated stage names")
    parser.add_argument("--with-full-instrumentation", action="store_true", help="also run the optional instr-full stage")
    parser.add_argument("--continue-on-fail", action="store_true")
    parser.add_argument("--allow-stale", action="store_true")
    parser.add_argument("--plan", action="store_true", help="print the plan and exit (no device, no files)")
    parser.add_argument("--stage-timeout", type=int, default=7200)
    args = parser.parse_args(argv)
    try:
        require_allowlisted_serial(args.serial)
    except OwnedDeviceError as error:
        print(f"REFUSED: {error}", file=sys.stderr)
        return 4
    role = OWNED_DEVICES[args.serial].role
    only = {x for x in args.only.split(",") if x}
    skip = {x for x in args.skip.split(",") if x}
    stages = select(stage_table(args.flavor, args.serial, args.allow_stale), role, only, skip, args.with_full_instrumentation)
    if args.plan:
        print(json.dumps([{"stage": s["name"], "command": s["argv"]} for s in stages], indent=2))
        return 0
    out_dir = new_run_dir("suites", args.flavor, role)
    summary: dict[str, Any] = {"schema": "kpkn-avd-suite/v1", "flavor": args.flavor, "serial": args.serial, "role": role,
                               "startedAtUtc": utc_now(), "stages": []}
    worst = 0
    for stage in stages:
        started = time.monotonic()
        print(f"=== {stage['name']} ===", file=sys.stderr, flush=True)
        try:
            result = subprocess.run(stage["argv"], capture_output=True, text=True, encoding="utf-8", errors="replace",
                                    timeout=args.stage_timeout)
            stdout, code = result.stdout, result.returncode
            (out_dir / f"{stage['name']}.stdout.json").write_text(stdout, encoding="utf-8")
            (out_dir / f"{stage['name']}.stderr.txt").write_text(result.stderr, encoding="utf-8")
        except subprocess.TimeoutExpired:
            stdout, code = "", 124
        verdict, evidence = verdict_of(stdout, code)
        summary["stages"].append({"stage": stage["name"], "verdict": verdict, "exitCode": code, "evidence": evidence,
                                  "seconds": round(time.monotonic() - started, 1), "command": stage["argv"]})
        write_json(out_dir / "suite.json", summary)
        print(f"    -> {verdict}  {evidence or ''}", file=sys.stderr, flush=True)
        if verdict != "PASS":
            worst = max(worst, 1 if verdict == "FAIL" else 3)
            if not args.continue_on_fail:
                break
    ran = {s["stage"] for s in summary["stages"]}
    summary["notRun"] = [s["name"] for s in stages if s["name"] not in ran]
    summary["overall"] = "PASS" if worst == 0 and not summary["notRun"] else "FAIL" if worst == 1 else "NOT_RUN"
    summary["finishedAtUtc"] = utc_now()
    write_json(out_dir / "suite.json", summary)
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0 if summary["overall"] == "PASS" else 1 if summary["overall"] == "FAIL" else 3


if __name__ == "__main__":
    raise SystemExit(main())
