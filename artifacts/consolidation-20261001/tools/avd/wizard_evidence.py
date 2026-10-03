#!/usr/bin/env python3
"""Post-run evidence for the wizard phase (SetupWizardFullJourneyUiTest on the wizard AVD, QA user 10).

Sub-commands (all run on the allowlisted wizard AVD, default ``emulator-5582``, as Android user 10)::

    wizard_evidence.py pull-captures [--instrumentation-dir DIR]
        Pull the PNGs written by ``captureQ6Screenshot`` (app-specific *external* files of the target app,
        ``/storage/emulated/<user>/Android/data/com.example.kpkn/files/q6-ui-captures/<scenario>-<uuid>/<name>.png``)
        into ``device-evidence/wizard/<run>/captures/``; validates PNG signature/size, hashes every file and
        checks the expected capture names (material -> goals -> schedule -> plan -> review -> home).

    wizard_evidence.py room-check
        ``run-as com.example.kpkn --user 10`` copy of kpkn.db (+WAL/SHM) and a read-only inspection: activated
        programs (macrocycles -> blocks -> mesocycles -> weeks -> sessions -> exercises), ``active_program``,
        ``setup_commit_receipts`` joined to ``programs``, ``setup_drafts`` left behind.

    wizard_evidence.py logcat-scan --logcat FILE
        Counts FATAL EXCEPTION / ANR / StrictMode / SQLiteException / IllegalStateException(com.example.kpkn).

Every sub-command writes ``result.json`` in a fresh evidence directory (PASS / FAIL / NOT_RUN semantics of
``evidence.Recorder``).  Nothing here clears, uninstalls or otherwise touches user 0.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from avd import Avd, OwnedDeviceError
from evidence import EXIT_MISSING_INPUT, Recorder, new_run_dir, sha256_bytes, sha256_file, write_json
from qa_paths import PACKAGE
from room_inspect import has_table, open_copy_ro, table_counts

EXPECTED_CAPTURES = (
    "material-options", "material-declared", "goal-profiles", "schedule-frequency", "schedule-weekdays",
    "session-time", "plan-candidates", "review-preview", "home-active-program",
)
# Scenario directory prefixes (PlanScenario.name.lowercase() + "-" + UUID) that capture screenshots.
EXPECTED_SCENARIOS = ("powerbuilding_five_day", "muscle_e0_three_day")
_Q6_DIR_RE = re.compile(r"Q6_UI_ARTIFACT_DIR=(\S+)")
_Q6_SHOT_RE = re.compile(r"Q6_UI_SCREENSHOT=(\S+)")
_PNG_MAGIC = b"\x89PNG\r\n\x1a\n"

LOGCAT_PATTERNS = {
    "FATAL EXCEPTION": re.compile(r"FATAL EXCEPTION"),
    "ANR": re.compile(r"\bANR in\b|Application Not Responding|am_anr"),
    "StrictMode": re.compile(r"StrictMode"),
    "SQLiteException": re.compile(r"SQLiteException|android\.database\.sqlite\.\w*Exception"),
    "IllegalStateException(com.example.kpkn)": re.compile(r"IllegalStateException"),
}


def png_dimensions(payload: bytes) -> tuple[int, int] | None:
    if len(payload) < 24 or not payload.startswith(_PNG_MAGIC) or payload[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", payload[16:24])
    return width, height


def remote_capture_root(user: int) -> str:
    return f"/storage/emulated/{user}/Android/data/{PACKAGE}/files/q6-ui-captures"


def artifact_paths_from_text(text: str) -> dict[str, list[str]]:
    """``Q6_UI_ARTIFACT_DIR`` / ``Q6_UI_SCREENSHOT`` markers printed by the test (stdout of am instrument or logcat)."""
    return {"dirs": sorted(set(_Q6_DIR_RE.findall(text))), "screenshots": sorted(set(_Q6_SHOT_RE.findall(text)))}


def scenario_of(directory_name: str) -> str:
    return re.sub(r"-[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}$", "", directory_name)


# ---------------------------------------------------------------------------
# pull-captures
# ---------------------------------------------------------------------------

def pull_captures(avd: Avd, out_dir: Path, rec: Recorder, instrumentation_dir: Path | None) -> None:
    user = avd.user if avd.user is not None else 0
    root = remote_capture_root(user)
    markers: dict[str, list[str]] = {"dirs": [], "screenshots": []}
    if instrumentation_dir is not None:
        for name in ("stdout.txt", "logcat.txt"):
            file = instrumentation_dir / name
            if file.is_file():
                found = artifact_paths_from_text(file.read_text(encoding="utf-8", errors="replace"))
                markers["dirs"] = sorted(set(markers["dirs"]) | set(found["dirs"]))
                markers["screenshots"] = sorted(set(markers["screenshots"]) | set(found["screenshots"]))
    with rec.step("capture_directories_listed") as step:
        found = avd.raw("shell", "find", root, "-type", "f", "-name", "*.png", timeout=45)
        listing = found.stdout.decode("utf-8", errors="replace")
        denied = "Permission denied" in (listing + found.stderr.decode("utf-8", errors="replace"))
        remote_files = sorted(line.strip() for line in listing.splitlines() if line.strip().endswith(".png"))
        # Evidence first, so a failing check still records what the test itself announced (Q6_UI_* markers).
        step.evidence(root=root, remoteFiles=remote_files, stdoutMarkers=markers, listingPermissionDenied=denied)
        if not remote_files and denied:
            # The adb shell (user 0, uid shell) cannot enter /storage/emulated/<user> of a secondary Android user on a
            # non-root image, so "no PNG listed" says nothing about whether the test wrote any.
            announced = len(markers["screenshots"])
            step.check(False, (
                f"{root} is not readable from the adb shell (Permission denied; secondary-user storage isolation, no root). "
                f"The test announced {len(markers['dirs'])} capture dir(s) and {announced} screenshot(s) "
                f"(Q6_UI_SCREENSHOT markers); the PNGs cannot be pulled with this tool in this configuration"))
        step.check(remote_files, f"no PNG under {root} (the test did not reach captureQ6Screenshot, or the user/package differ); "
                                 f"markers: {len(markers['dirs'])} dir(s), {len(markers['screenshots'])} shot(s)")
        step.detail(f"{len(remote_files)} PNG(s) under {root}; markers: {len(markers['dirs'])} dir(s), {len(markers['screenshots'])} shot(s) in stdout/logcat")
    pulled: list[dict[str, Any]] = []
    with rec.step("png_files_pulled_and_valid") as step:
        for remote in remote_files:
            relative = remote[len(root):].lstrip("/")
            local = out_dir / "captures" / relative
            local.parent.mkdir(parents=True, exist_ok=True)
            avd.adb("pull", remote, str(local), timeout=120)
            payload = local.read_bytes()
            dims = png_dimensions(payload)
            step.check(dims is not None and dims[0] > 0 and dims[1] > 0, f"{relative}: not a valid PNG ({len(payload)} bytes)")
            pulled.append({"remote": remote, "local": str(local), "relative": relative, "bytes": len(payload),
                           "sha256": sha256_bytes(payload), "width": dims[0], "height": dims[1]})
            rec.ledger.add(local, kind="screenshot")
        hashes = [item["sha256"] for item in pulled]
        step.check(len(set(hashes)) > 1, "all captures are byte-identical (a frozen screen?)")
        step.detail(f"{len(pulled)} valid PNG(s)")
        step.evidence(files=pulled)
    with rec.step("expected_capture_names_per_scenario") as step:
        by_scenario: dict[str, set[str]] = {}
        for item in pulled:
            parts = item["relative"].split("/")
            by_scenario.setdefault(scenario_of(parts[0]), set()).add(Path(parts[-1]).stem)
        missing: dict[str, list[str]] = {}
        for scenario in EXPECTED_SCENARIOS:
            have = by_scenario.get(scenario, set())
            gap = [name for name in EXPECTED_CAPTURES if name not in have]
            if gap:
                missing[scenario] = gap
        step.evidence(found={key: sorted(value) for key, value in by_scenario.items()}, missing=missing)
        step.check(not missing, f"missing captures: {missing}")
        step.detail("both scenarios have all %d expected captures" % len(EXPECTED_CAPTURES))


# ---------------------------------------------------------------------------
# room-check
# ---------------------------------------------------------------------------

def walk_program(program: dict[str, Any]) -> dict[str, Any]:
    """macrocycles -> blocks -> mesocycles -> weeks -> sessions (+ exercises) summary of one Program JSON."""
    weeks: list[dict[str, Any]] = []
    for macro in program.get("macrocycles") or []:
        for block in macro.get("blocks") or []:
            for meso in block.get("mesocycles") or []:
                for week in meso.get("weeks") or []:
                    sessions = week.get("sessions") or []
                    weeks.append({
                        "id": week.get("id"), "sessions": len(sessions),
                        "days": sorted(s.get("dayOfWeek") for s in sessions if isinstance(s.get("dayOfWeek"), int)),
                        "exercises": sum(len(s.get("exercises") or []) + sum(len(p.get("exercises") or []) for p in s.get("parts") or [])
                                         for s in sessions),
                        "executionKind": week.get("executionKind"),
                    })
    return {
        "id": program.get("id"), "name": program.get("name"), "isDraft": bool(program.get("isDraft")),
        "sourceProtocolId": program.get("sourceProtocolId"), "weeks": len(weeks),
        "sessionsTotal": sum(w["sessions"] for w in weeks), "exercisesTotal": sum(w["exercises"] for w in weeks),
        "firstWeek": weeks[0] if weeks else None,
        "trainingWeeksWithoutSessions": sum(1 for w in weeks if w["sessions"] == 0 and w["executionKind"] in (None, "TRAINING")),
    }


def parse_since_utc(text: str | None) -> int | None:
    """``--since-utc`` (ISO-8601, ``Z`` or offset; naive = UTC) -> epoch milliseconds, ``None`` when not given."""
    if not text:
        return None
    parsed = datetime.fromisoformat(text.strip().replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp() * 1000)


def select_run_receipts(receipts: list[dict[str, Any]], since_epoch_ms: int | None) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Split the commit receipts into (this run, older). Without a cut-off every receipt belongs to the run.

    The user-10 database is shared by every wizard class: ``PostSetup*`` / ``WorkoutWarmup*`` leave
    ``m9-psu-*`` receipts whose programs they delete again, so judging all receipts would blame the
    FullJourney run for rows it did not write.
    """
    if since_epoch_ms is None:
        return list(receipts), []
    mine = [r for r in receipts if (r.get("committedAtEpochMs") or 0) >= since_epoch_ms]
    older = [r for r in receipts if (r.get("committedAtEpochMs") or 0) < since_epoch_ms]
    return mine, older


def room_check(avd: Avd, out_dir: Path, rec: Recorder, since_utc: str | None = None,
               expect_receipts: int | None = None, ignore_commit_prefixes: tuple[str, ...] = ()) -> None:
    since_epoch_ms = parse_since_utc(since_utc)
    capture_dir = out_dir / "room"
    with rec.step("room_copy_user_db") as step:
        manifest = avd.room_pull(capture_dir)
        step.check(manifest["database"]["db"] is not None, "kpkn.db missing from the capture")
        step.detail(f"user {avd.user}: db {manifest['database']['db']['bytes']} B, consistent={manifest['consistent']}")
        step.evidence(manifest=str(capture_dir / "manifest.json"), consistent=manifest["consistent"],
                      db=manifest["database"])
        rec.ledger.add(capture_dir / "manifest.json", kind="room-manifest")
        rec.ledger.add(capture_dir / "app-data" / "databases" / "kpkn.db", kind="room-db")
    db_path = capture_dir / "app-data" / "databases" / "kpkn.db"
    summary: dict[str, Any] = {}
    with rec.step("activated_programs_with_weeks_and_sessions") as step:
        with open_copy_ro(db_path) as con:
            summary["tableCounts"] = {k: v for k, v in table_counts(con).items()
                                      if k in {"programs", "active_program", "setup_commit_receipts", "setup_drafts",
                                               "nutrition_plans", "nutrition_active_state", "body_goals", "settings"}}
            programs = []
            for row in con.execute("SELECT id, name, data FROM programs ORDER BY rowid"):
                data = json.loads(row["data"])
                programs.append(walk_program(data))
            summary["programs"] = programs
            summary["receipts"] = [dict(row) for row in con.execute(
                "SELECT commitId, draftId, programId, nutritionPlanId, bodyGoalIdsJson, committedAtEpochMs "
                "FROM setup_commit_receipts ORDER BY committedAtEpochMs")] if has_table(con, "setup_commit_receipts") else []
            summary["activeProgramRows"] = [dict(row) for row in con.execute("SELECT rowId, data FROM active_program")] \
                if has_table(con, "active_program") else []
            summary["draftsLeft"] = [dict(row) for row in con.execute(
                "SELECT draftId, revision, updatedAtEpochMs FROM setup_drafts")] if has_table(con, "setup_drafts") else []
        step.check(programs, "no program in Room for this user: the activation did not persist")
        by_id = {p["id"]: p for p in programs}
        step.check(summary["receipts"], "no setup_commit_receipts row: no activation was committed")
        judged_receipts = [r for r in summary["receipts"]
                           if not (ignore_commit_prefixes and str(r["commitId"]).startswith(ignore_commit_prefixes))]
        summary["ignoredReceipts"] = [r["commitId"] for r in summary["receipts"] if r not in judged_receipts]
        run_receipts, older_receipts = select_run_receipts(judged_receipts, since_epoch_ms)
        summary["sinceUtc"] = since_utc
        summary["sinceEpochMs"] = since_epoch_ms
        summary["receiptsOlderThanRun"] = [
            {"commitId": r["commitId"], "programId": r["programId"], "committedAtEpochMs": r["committedAtEpochMs"],
             "programStillInRoom": r["programId"] in by_id} for r in older_receipts]
        summary["runReceipts"] = [r["commitId"] for r in run_receipts]
        step.check(run_receipts, f"no setup_commit_receipts row at or after --since-utc {since_utc}: this run committed nothing")
        if expect_receipts is not None:
            step.check(len(run_receipts) == expect_receipts,
                       f"expected {expect_receipts} activations in this run, found {len(run_receipts)}: {summary['runReceipts']}")
        joined = []
        for receipt in run_receipts:
            program = by_id.get(receipt["programId"])
            step.check(program is not None, f"receipt {receipt['commitId']} points at a program that is not in Room")
            step.check(program["weeks"] > 0 and program["sessionsTotal"] > 0 and program["exercisesTotal"] > 0,
                       f"program {program['name']!r} has weeks={program['weeks']} sessions={program['sessionsTotal']} exercises={program['exercisesTotal']}")
            step.check(program["trainingWeeksWithoutSessions"] == 0, f"program {program['name']!r} has training weeks without sessions")
            joined.append({"receipt": receipt["commitId"], "program": program["name"], "weeks": program["weeks"],
                           "sessions": program["sessionsTotal"], "exercises": program["exercisesTotal"],
                           "firstWeek": program["firstWeek"]})
        summary["receiptPrograms"] = joined
        step.detail("; ".join(f"{j['program']}: {j['weeks']} semanas / {j['sessions']} sesiones / {j['exercises']} ejercicios" for j in joined))
        step.evidence(**summary)
    with rec.step("active_program_points_at_an_activated_program") as step:
        rows = summary["activeProgramRows"]
        step.check(rows, "active_program is empty")
        receipt_program_ids = {r["programId"] for r in select_run_receipts(summary["receipts"], since_epoch_ms)[0]
                               if r["programId"]}
        blob = " ".join(row["data"] for row in rows)
        step.check(any(pid in blob for pid in receipt_program_ids), "active_program references none of the activated programs")
        step.detail("active_program references an activated program")
    write_json(out_dir / "room-summary.json", summary)
    rec.ledger.add(out_dir / "room-summary.json", kind="room-summary")


# ---------------------------------------------------------------------------
# logcat-scan
# ---------------------------------------------------------------------------

def logcat_scan(logcat: Path, rec: Recorder) -> None:
    with rec.step("logcat_clean_of_crashes") as step:
        text = logcat.read_text(encoding="utf-8", errors="replace")
        counts: dict[str, int] = {}
        examples: dict[str, list[str]] = {}
        lines = text.splitlines()
        for label, pattern in LOGCAT_PATTERNS.items():
            hits = [ln for ln in lines if pattern.search(ln) and (label != "IllegalStateException(com.example.kpkn)" or "kpkn" in ln)]
            counts[label] = len(hits)
            examples[label] = [h[:260] for h in hits[:5]]
        step.evidence(file=str(logcat), sha256=sha256_file(logcat), counts=counts, examples=examples)
        bad = {k: v for k, v in counts.items() if v and k in {"FATAL EXCEPTION", "ANR", "SQLiteException"}}
        step.check(not bad, f"logcat shows {bad}")
        step.detail(f"counts={counts}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default="emulator-5582")
    parser.add_argument("--user", type=int, default=10)
    parser.add_argument("--label", default=None)
    sub = parser.add_subparsers(dest="command", required=True)
    pull = sub.add_parser("pull-captures")
    pull.add_argument("--instrumentation-dir", type=Path, default=None)
    room = sub.add_parser("room-check")
    room.add_argument("--since-utc", default=None,
                      help="only judge receipts committed at/after this ISO-8601 UTC instant (older receipts of other "
                           "classes sharing the user-10 database are listed, not judged)")
    room.add_argument("--expect-receipts", type=int, default=None,
                      help="exact number of activations the judged run must have committed")
    room.add_argument("--ignore-commit-prefix", action="append", default=[],
                      help="skip receipts whose commitId starts with this prefix (repeatable); e.g. m9-psu- = the PostSetup* "
                           "classes, which delete their programs again and run inside the same suite window")
    scan = sub.add_parser("logcat-scan")
    scan.add_argument("--logcat", type=Path, required=True)
    args = parser.parse_args(argv)

    out_dir = new_run_dir("wizard", args.command, args.label)
    rec = Recorder(f"wizard-{args.command}", None, out_dir, extra={"androidUser": args.user, "serial": args.serial})
    try:
        if args.command == "logcat-scan":
            logcat_scan(args.logcat, rec)
        else:
            avd = Avd(args.serial, user=args.user)
            with rec.step("device_verified") as step:
                step.evidence(device=avd.verify())
            if args.command == "pull-captures":
                pull_captures(avd, out_dir, rec, args.instrumentation_dir)
            else:
                room_check(avd, out_dir, rec, since_utc=args.since_utc, expect_receipts=args.expect_receipts,
                           ignore_commit_prefixes=tuple(args.ignore_commit_prefix))
    except OwnedDeviceError as error:
        rec.extra["reason"] = "REFUSED_DEVICE"
        rec.notes.append(str(error))
    except Exception as error:  # noqa: BLE001
        if rec.aborted is None:
            rec.crash(error)
    _, code = rec.finish()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
