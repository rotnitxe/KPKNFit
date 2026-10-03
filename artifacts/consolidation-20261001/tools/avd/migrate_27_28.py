#!/usr/bin/env python3
"""REAL Room 27 -> 28 migration on the emulator, by updating the app IN PLACE.

  (a) install the v27 comparison APK (``--baseline-apk``; see discovery below), wipe the app data,
      seed the non-empty v27 fixture (1 program, 2 history logs, 4 media rows, 1 ongoing workout) +
      the four app-private PNGs + the audit preferences + an in-place marker file;
  (b) update in place to the integrated debug APK with ``adb install -r`` (no uninstall, no pm clear);
  (c) launch once, let Room migrate, and compare DB/WAL before vs after: schema 28 identity hash,
      exact retention of ``programs`` / ``workout_media``, semantic retention of ``workout_logs``
      (typed normalization may rewrite the JSON, never a set), exactly the unambiguous media
      association (qa-migration-valid -> repair-history-0; the ambiguous key creates none), private
      files and the marker byte-identical, installed APK == the integrated build.

Baseline APK discovery (first existing wins, the chosen one and its hash are recorded):
  1. ``--baseline-apk PATH``
  2. OLDROOT ``baseline-workspace/.../apk/base/debug/app-base-debug.apk`` (only if OLDROOT ever built it)
  3. ``artifacts/session-audit/2026-09-29/kpkn-base-debug.apk`` - the historical audit BaseDebug build
     (the one that created the v27 audit database; used by every OLDROOT migration run)
OLDROOT's baseline-workspace today only contains ``repairBenchmark`` (optimized) APKs, which are not
used because their variant differs from a debug build.  Without any of them the run stops with
MISSING_INPUT and a list of what was looked for.

Host-only validation of every input (no device touched)::

    python -X utf8 migrate_27_28.py --flavor base --validate-only

Real run (Base or Health final APK, always the same Base v27 baseline)::

    python -X utf8 migrate_27_28.py --flavor base
    python -X utf8 migrate_27_28.py --flavor health
"""

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
import time
from pathlib import Path
from typing import Any

import apk_info
import room_inspect
import room_verify
from evidence import MissingInput, sha256_file, write_json
from install_apks import install_verified
from qa_common import QaSession, add_common_args, execute
from qa_paths import REPO_ROOT, SCHEMAS_DIR

OLDROOT = REPO_ROOT / "artifacts" / "session-repair" / "2026-09-30-01a0eede"
BASELINE_CANDIDATES: list[tuple[str, Path]] = [
    ("OLDROOT baseline-workspace debug build",
     OLDROOT / "baseline-workspace" / "android-native" / "app" / "build" / "outputs" / "apk" / "base" / "debug" / "app-base-debug.apk"),
    ("historical audit BaseDebug APK (Room v27 era)",
     REPO_ROOT / "artifacts" / "session-audit" / "2026-09-29" / "kpkn-base-debug.apk"),
]
FIXTURE = "migration-retention-v27"
EXPECTED_BEFORE_COUNTS = {"programs": 1, "workout_logs": 2, "workout_media": 4, "ongoing_workout": 1}
EXPECTED_LOG_IDS = {"repair-history-0", "repair-history-1"}
EXPECTED_MEDIA = {
    "qa-migration-valid-a": ("qa-migration-valid", "repair-history-0", "qa-migration-valid-a.png"),
    "qa-migration-valid-b": ("qa-migration-valid", None, "qa-migration-valid-b.png"),
    "qa-migration-ambiguous-a": ("qa-migration-ambiguous", "repair-history-0", "qa-migration-ambiguous-a.png"),
    "qa-migration-ambiguous-b": ("qa-migration-ambiguous", "repair-history-1", "qa-migration-ambiguous-b.png"),
}
EXPECTED_ASSOCIATIONS = {"qa-migration-valid": "repair-history-0"}
MARKER_PATH = "files/qa-inplace-marker.txt"
LOG_SCALAR_FIELDS = ("id", "programId", "sessionId", "sessionName", "date", "durationMinutes", "totalVolume")

STEPS = (
    "host_inputs_validated", "preflight_device_allowlist", "install_baseline_v27_apk", "seed_v27_fixture_and_markers",
    "before_snapshot_equals_fixture", "baseline_probe_keeps_v27", "update_in_place_to_integrated_apk",
    "data_survives_inplace_update", "first_launch_runs_migration", "home_after_migration", "after_snapshot_v28",
    "room_identity_hash_matches_schema_28", "workout_logs_semantic_retention", "media_associations_exact",
    "strict_verifier_migration", "wal_before_after_comparison", "private_files_and_marker_unchanged",
    "installed_apk_is_integrated_build", "normalized_logs_match_junit_golden",
)


# ---------------------------------------------------------------------------
# Pure helpers (unit-tested offline)
# ---------------------------------------------------------------------------

def discover_baseline(explicit: Path | None, candidates: list[tuple[str, Path]] | None = None) -> tuple[Path, str]:
    candidates = BASELINE_CANDIDATES if candidates is None else candidates
    if explicit is not None:
        if not explicit.is_file():
            raise MissingInput(f"--baseline-apk does not exist: {explicit}")
        return explicit, "explicit --baseline-apk"
    for label, path in candidates:
        if path.is_file():
            return path, label
    raise MissingInput(
        "no Room-v27 comparison APK found",
        "looked for: " + "; ".join(f"{label}: {path}" for label, path in candidates) + ". Pass --baseline-apk PATH "
        "(a debug build of the app from before the 27->28 migration, signed with the same debug key).",
    )


def validate_fixture(path: Path) -> dict[str, Any]:
    """Structure of the v27 fixture (port of OLDROOT validate_local_fixture, hash pins removed)."""
    connection = sqlite3.connect(f"file:{Path(path).as_posix()}?mode=ro&immutable=1", uri=True)
    try:
        version = int(connection.execute("PRAGMA user_version").fetchone()[0])
        if version != 27:
            raise MissingInput(f"fixture is Room v{version}, expected 27")
        tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if "workout_media_session_associations" in tables:
            raise MissingInput("v27 fixture already contains the v28 association table")
        counts = {t: int(connection.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]) for t in EXPECTED_BEFORE_COUNTS}
        if counts != EXPECTED_BEFORE_COUNTS:
            raise MissingInput(f"fixture counts {counts} != {EXPECTED_BEFORE_COUNTS} (non-empty retention needed)")
        log_ids = {str(r[0]) for r in connection.execute('SELECT "id" FROM "workout_logs"')}
        if log_ids != EXPECTED_LOG_IDS:
            raise MissingInput(f"unexpected history identities {sorted(log_ids)}")
        media: dict[str, tuple[str, str | None, str]] = {}
        for media_id, file_path, key, log_id in connection.execute(
                'SELECT "id","filePath","sessionKey","workoutLogId" FROM "workout_media"'):
            media[str(media_id)] = (str(key), None if log_id is None else str(log_id), Path(str(file_path)).name)
        if media != EXPECTED_MEDIA:
            raise MissingInput(f"fixture media rows differ from the contract: {media}")
        return {"userVersion": version, "counts": counts, "logIds": sorted(log_ids),
                "media": {k: {"sessionKey": v[0], "workoutLogId": v[1], "file": v[2]} for k, v in media.items()}}
    finally:
        connection.close()


def schema_identity_hash(schema_path: Path) -> str:
    data = json.loads(Path(schema_path).read_text(encoding="utf-8"))
    return data["database"]["identityHash"]


def db_identity_hash(db_path: Path) -> str | None:
    with room_inspect.open_copy_ro(db_path) as conn:
        if not room_inspect.has_table(conn, "room_master_table"):
            return None
        row = conn.execute('SELECT "identity_hash" FROM "room_master_table" LIMIT 1').fetchone()
        return row[0] if row else None


def _num_equal(a: Any, b: Any) -> bool:
    if isinstance(a, (int, float)) and isinstance(b, (int, float)) and not isinstance(a, bool) and not isinstance(b, bool):
        return abs(float(a) - float(b)) < 1e-9
    return a == b


def log_semantic_problem(old: dict[str, Any], new: dict[str, Any]) -> tuple[str | None, dict[str, Any]]:
    """Typed normalization may rewrite JSON, but identity, sessions and every set must survive."""
    info: dict[str, Any] = {"addedTopLevelKeys": sorted(set(new) - set(old)), "removedTopLevelKeys": sorted(set(old) - set(new))}
    for field in LOG_SCALAR_FIELDS:
        if not _num_equal(old.get(field), new.get(field)):
            return f"{field}: {old.get(field)!r} -> {new.get(field)!r}", info
    old_ex, new_ex = old.get("completedExercises"), new.get("completedExercises")
    if not isinstance(old_ex, list) or not old_ex or not isinstance(new_ex, list):
        return "no comparable completed exercises", info
    if len(old_ex) != len(new_ex):
        return f"exercise count {len(old_ex)} -> {len(new_ex)}", info
    canonical_notes = []
    for index, (a, b) in enumerate(zip(old_ex, new_ex)):
        for field in ("exerciseId", "exerciseName", "exerciseDbId"):
            if a.get(field) != b.get(field):
                return f"completedExercises[{index}].{field}: {a.get(field)!r} -> {b.get(field)!r}", info
        canonical_notes.append({"index": index, "canonicalExerciseId": b.get("canonicalExerciseId"),
                                "equalsExerciseDbId": b.get("canonicalExerciseId") == a.get("exerciseDbId")})
        old_sets, new_sets = a.get("sets"), b.get("sets")
        if not isinstance(old_sets, list) or not old_sets or not isinstance(new_sets, list):
            return f"completedExercises[{index}] has no comparable sets", info
        if len(old_sets) != len(new_sets):
            return f"completedExercises[{index}] set count {len(old_sets)} -> {len(new_sets)}", info
        for set_index, (x, y) in enumerate(zip(old_sets, new_sets)):
            for field in ("id", "weight", "reps"):
                if not _num_equal(x.get(field), y.get(field)):
                    return f"exercise[{index}] set[{set_index}].{field}: {x.get(field)!r} -> {y.get(field)!r}", info
    info["canonicalExerciseIds"] = canonical_notes
    return None, info


def read_log_rows(db_path: Path) -> dict[str, dict[str, Any]]:
    with room_inspect.open_copy_ro(db_path) as conn:
        return {str(row["id"]): json.loads(row["data"]) for row in conn.execute('SELECT "id","data" FROM "workout_logs"')}


def normalized_before_copy(before_db: Path, after_logs: dict[str, str], destination: Path) -> Path:
    """Private copy of the v27 DB whose workout_logs.data cells are the post-migration JSON (after the
    semantic check passed), so the strict verifier compares every other byte of every row exactly."""
    shutil.copyfile(before_db, destination)
    connection = sqlite3.connect(destination)
    try:
        for log_id, raw in after_logs.items():
            connection.execute('UPDATE "workout_logs" SET "data"=? WHERE "id"=?', (raw, log_id))
        connection.commit()
    finally:
        connection.close()
    return destination


def raw_log_cells(db_path: Path) -> dict[str, str]:
    with room_inspect.open_copy_ro(db_path) as conn:
        return {str(row["id"]): row["data"] for row in conn.execute('SELECT "id","data" FROM "workout_logs"')}


def database_set_facts(manifest: dict[str, Any]) -> dict[str, Any]:
    db = manifest.get("database") or {}
    pick = lambda item: None if not item else {"bytes": item["bytes"], "sha256": item["sha256"]}
    return {"db": pick(db.get("db")), "wal": pick(db.get("wal")), "shm": pick(db.get("shm")),
            "consistent": manifest.get("consistent")}


# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------

def host_inputs(s: QaSession, args: argparse.Namespace) -> dict[str, Any]:
    info = s.host_preflight(fixtures_needed=(FIXTURE, "qa-photo", "migration-log-golden"), prefs=True)
    fixture = info["fixtures"][FIXTURE]
    info["fixtureContract"] = validate_fixture(Path(fixture["path"]))
    baseline, source = discover_baseline(args.baseline_apk)
    baseline_info = apk_info.inspect_apk(baseline)
    manifest = baseline_info["manifest"]
    if manifest["package"] != "com.example.kpkn" or not manifest["debuggable"]:
        raise MissingInput(f"baseline {baseline.name}: package={manifest['package']!r} debuggable={manifest['debuggable']}")
    final = info["apk"]["manifest"]
    if baseline_info["sha256"] == info["apk"]["sha256"]:
        raise MissingInput("baseline and integrated APK are the same file; nothing would be updated")
    if manifest["versionCode"] is not None and final["versionCode"] is not None and manifest["versionCode"] > final["versionCode"]:
        raise MissingInput(f"baseline versionCode {manifest['versionCode']} > integrated {final['versionCode']}: not an upgrade")
    signers: dict[str, Any] = {}
    if args.check_signers:
        signers = {"baseline": apk_info.signer_certs(baseline), "final": apk_info.signer_certs(s.apk_path)}
        a, b = (signers["baseline"] or {}).get("signerSha256"), (signers["final"] or {}).get("signerSha256")
        if a and b and a != b:
            raise MissingInput(f"baseline and integrated APK are signed by different keys ({a} vs {b}); adb install -r would fail")
    schema = SCHEMAS_DIR / "28.json"
    if not schema.is_file():
        raise MissingInput(f"Room schema export missing: {schema}")
    info["baseline"] = {"path": str(baseline), "discoveredFrom": source, **baseline_info, "signers": signers}
    info["schema28"] = {"path": str(schema), "identityHash": schema_identity_hash(schema), "sha256": sha256_file(schema)}
    s.rec.inputs.update(info)
    return info


def run(s: QaSession, args: argparse.Namespace) -> None:
    r = s.rec
    if args.validate_only:
        r.declare("host_inputs_validated")
        s.rec.extra["scope"] = "HOST_INPUT_VALIDATION_ONLY: inputs valid; the migration was NOT executed and no device was touched"
    else:
        r.declare(*STEPS, optional=("baseline_probe_keeps_v27", "normalized_logs_match_junit_golden"))
        s.rec.extra["claimScope"] = "synthetic v27 fixture upgraded in place on an emulator; not a user-data migration"
    ctx: dict[str, Any] = {}
    with r.step("host_inputs_validated") as step:
        info = host_inputs(s, args)
        step.detail(f"baseline [{info['baseline']['discoveredFrom']}] {info['baseline']['sha256'][:12]} "
                    f"v{info['baseline']['manifest']['versionCode']} -> integrated {info['apk']['sha256'][:12]} "
                    f"v{info['apk']['manifest']['versionCode']}; fixture {info['fixtures'][FIXTURE]['sha256'][:12]}")
        step.evidence(baseline=info["baseline"]["path"], fixtureContract=info["fixtureContract"])
    if args.validate_only:
        return
    baseline_path = Path(s.rec.inputs["baseline"]["path"])
    fixture_path = Path(s.rec.inputs["fixtures"][FIXTURE]["path"])
    photo_path = Path(s.rec.inputs["fixtures"]["qa-photo"]["path"])
    photo_sha = s.rec.inputs["fixtures"]["qa-photo"]["sha256"]

    with r.step("preflight_device_allowlist") as step:
        device = s.device_preflight()
        step.detail(f"{device['serial']} {device['avdName']} API {device['sdk']}")
        step.evidence(device=device)

    with r.step("install_baseline_v27_apk") as step:
        receipt = install_verified(s.avd, baseline_path, package="com.example.kpkn", flags=("-r", "-d"))
        step.check(receipt["matched"], "installed baseline bytes differ from the baseline APK")
        ctx["baselineReceipt"] = receipt
        step.detail(f"baseline installed: device versionCode {receipt['deviceVersionCode']} ({receipt['installedSha256'][:12]})")
        step.evidence(**receipt)

    with r.step("seed_v27_fixture_and_markers") as step:
        seeded = s.seed(FIXTURE)
        for media_id, (_key, _log, file_name) in EXPECTED_MEDIA.items():
            got = s.avd.run_as_push(photo_path, f"files/{file_name}", staging_name=f"mig-{media_id}")
            step.check(got == photo_sha, f"private PNG {file_name} copied with a different hash")
        nonce = f"qa-inplace-{int(time.time() * 1000)}"
        marker_local = s.out / "qa-inplace-marker.txt"
        marker_local.write_text(nonce, encoding="ascii")
        marker_sha = s.avd.run_as_push(marker_local, MARKER_PATH, staging_name="mig-marker")
        step.check(marker_sha == sha256_file(marker_local), "marker file copied with a different hash")
        ctx["markerSha"] = marker_sha
        step.detail(f"fixture {seeded['fixture']['sha256'][:12]} + 4 private PNGs + marker ({nonce})")
        step.evidence(seed=seeded["fixture"], marker=MARKER_PATH)

    with r.step("before_snapshot_equals_fixture") as step:
        before = s.room("before-v27")
        ctx["before"] = before
        step.check(before.summary["databaseVersion"] == 27, f"before snapshot is Room v{before.summary['databaseVersion']}")
        db_hash = before.manifest["database"]["db"]["sha256"]
        step.check(db_hash == sha256_file(fixture_path), "device DB differs from the fixture before the update")
        step.check(before.manifest["database"]["wal"] is None, "a WAL exists before the app ever opened the fixture")
        ctx["beforeFacts"] = database_set_facts(before.manifest)
        step.detail(f"device kpkn.db == fixture ({db_hash[:12]}), no WAL, v27")
        step.evidence(**ctx["beforeFacts"])

    with r.step("baseline_probe_keeps_v27", optional=True) as step:
        if not args.probe_baseline:
            step.not_run("not requested (--probe-baseline launches the v27 app once on the fixture before the update)")
        s.launch_main()
        s.wait_home("baseline-home")
        probe = s.room("baseline-probe")
        step.check(probe.summary["databaseVersion"] == 27, f"baseline app left Room v{probe.summary['databaseVersion']}")
        ctx["before"] = probe
        ctx["beforeFacts"] = database_set_facts(probe.manifest)
        step.detail("baseline app opens the fixture without migrating (still v27); this probe becomes the 'before' snapshot")

    with r.step("update_in_place_to_integrated_apk") as step:
        receipt = install_verified(s.avd, s.apk_path, package="com.example.kpkn", flags=("-r",),
                                   expect_version_code=s.rec.inputs["apk"]["manifest"]["versionCode"])
        step.check(receipt["matched"], "installed integrated bytes differ from the build output")
        step.check(receipt["versionCodeMatchesManifest"], "device versionCode differs from the APK manifest")
        ctx["updateReceipt"] = receipt
        step.detail(f"adb install -r over the baseline: {receipt['before']['sha256'][:12]} -> {receipt['installedSha256'][:12]}")
        step.evidence(**receipt)

    with r.step("data_survives_inplace_update") as step:
        step.check(s.avd.run_as_sha256(MARKER_PATH) == ctx["markerSha"], "in-place marker changed or vanished: the update wiped app data")
        db_now = s.avd.run_as_sha256("databases/kpkn.db")
        step.check(db_now == ctx["beforeFacts"]["db"]["sha256"], "kpkn.db bytes changed during the APK replacement (before first launch)")
        step.detail("marker and kpkn.db are byte-identical right after the update; migration has not run yet")

    with r.step("first_launch_runs_migration") as step:
        output = s.avd.start_main()
        top = s.avd.wait_main_top_resumed(timeout=args.launch_timeout, stable=3)
        step.detail(f"MainActivity stably top-resumed after the update ({top})")
        step.evidence(amStart=output)

    with r.step("home_after_migration") as step:
        home = s.wait_home("home-v28")
        step.detail(f"Home rendered from the migrated database ({home.name})")

    with r.step("after_snapshot_v28") as step:
        after = s.room("after-v28")
        ctx["after"] = after
        counts = after.summary["counts"]
        step.check(after.summary["databaseVersion"] == 28, f"after snapshot is Room v{after.summary['databaseVersion']}")
        for table, expected in EXPECTED_BEFORE_COUNTS.items():
            step.check(counts.get(table) == expected, f"{table}: {counts.get(table)} != {expected}")
        step.check(counts.get("workout_media_session_associations") == 1,
                   f"associations: {counts.get('workout_media_session_associations')} != 1")
        ctx["afterFacts"] = database_set_facts(after.manifest)
        step.detail(f"v28; counts {({t: counts[t] for t in EXPECTED_BEFORE_COUNTS})} + 1 association")
        step.evidence(**ctx["afterFacts"])

    with r.step("room_identity_hash_matches_schema_28") as step:
        db_hash = db_identity_hash(ctx["after"].db_path)
        expected = s.rec.inputs["schema28"]["identityHash"]
        step.check(db_hash == expected, f"room_master_table.identity_hash {db_hash!r} != schemas/28.json {expected!r}")
        step.detail(f"identity hash {expected[:12]}... equals the exported v28 schema")

    with r.step("workout_logs_semantic_retention") as step:
        old_rows = read_log_rows(fixture_path)
        new_rows = read_log_rows(ctx["after"].db_path)
        step.check(set(new_rows) == EXPECTED_LOG_IDS, f"history ids after migration: {sorted(new_rows)}")
        notes = {}
        for log_id in sorted(EXPECTED_LOG_IDS):
            problem, details = log_semantic_problem(old_rows[log_id], new_rows[log_id])
            step.check(problem is None, f"{log_id}: {problem}")
            notes[log_id] = details
        ctx["logNotes"] = notes
        step.detail("both history logs keep identity, session, volume and every set (id, weight, reps)")
        step.evidence(**notes)

    with r.step("media_associations_exact") as step:
        after_summary = ctx["after"].summary
        pairs = [(row["sessionKey"], row["workoutLogId"]) for row in after_summary["associations"]]
        step.check(len(pairs) == len({k for k, _ in pairs}), "association session keys are not unique")
        step.check(dict(pairs) == EXPECTED_ASSOCIATIONS, f"associations {dict(pairs)} != {EXPECTED_ASSOCIATIONS}")
        rows = {row["id"]: row for row in after_summary["mediaRows"]}
        step.check(set(rows) == set(EXPECTED_MEDIA), f"media ids after migration: {sorted(rows)}")
        step.detail("exactly qa-migration-valid -> repair-history-0; the ambiguous key created no association")
        step.evidence(associations=dict(pairs))

    with r.step("strict_verifier_migration") as step:
        work = s.out / "verify"
        work.mkdir(exist_ok=True)
        comparison = normalized_before_copy(ctx["before"].db_path, raw_log_cells(ctx["after"].db_path), work / "before-v27-logs-normalized.db")
        report = room_verify.verify(comparison, ctx["after"].db_path, "MIGRATION")
        payload = report.as_json()
        write_json(work / "verify-migration.json", payload)
        bad = [c for c in payload["checks"] if c["status"] != "PASS"]
        step.evidence(result=payload["result"], checks=len(payload["checks"]), nonPass=bad,
                      note="workout_logs compared semantically (previous step); programs and workout_media compared row-for-row")
        if payload["result"] == "FAIL":
            step.fail("; ".join(f"{c['name']}: {c['detail']}" for c in bad)[:1500])
        if payload["result"] != "PASS":
            step.not_run("; ".join(f"{c['name']}={c['status']}" for c in bad))
        step.detail(f"{len(payload['checks'])} checks PASS (v27->v28 pair, preserved tables, associations, non-empty retention)")

    with r.step("wal_before_after_comparison") as step:
        b, a = ctx["beforeFacts"], ctx["afterFacts"]
        step.check(a["consistent"], "after capture was not taken with the app stopped")
        step.check(a["db"]["sha256"] != b["db"]["sha256"] or (a["wal"] is not None),
                   "neither kpkn.db nor a WAL changed: Room did not touch the database")
        step.detail(f"before db {b['db']['bytes']} B, wal={b['wal']}; after db {a['db']['bytes']} B, "
                    f"wal={'%d B' % a['wal']['bytes'] if a['wal'] else None}")
        step.evidence(before=b, after=a)

    with r.step("private_files_and_marker_unchanged") as step:
        hashes = {}
        for media_id, (_key, _log, file_name) in EXPECTED_MEDIA.items():
            hashes[file_name] = s.avd.run_as_sha256(f"files/{file_name}")
            step.check(hashes[file_name] == photo_sha, f"{file_name} changed across the migration")
        step.check(s.avd.run_as_sha256(MARKER_PATH) == ctx["markerSha"], "marker changed across the migration")
        step.detail("4 private PNGs and the marker are byte-identical after migration")
        step.evidence(hashes=hashes)

    with r.step("installed_apk_is_integrated_build") as step:
        installed = s.avd.installed_apk("com.example.kpkn")
        step.check(installed["sha256"] == s.rec.inputs["apk"]["sha256"], "installed APK is not the integrated build after the run")
        step.detail(f"installed {installed['sha256'][:12]} == {s.apk_path.name}")

    with r.step("normalized_logs_match_junit_golden", optional=True) as step:
        golden = json.loads(Path(s.rec.inputs["fixtures"]["migration-log-golden"]["path"]).read_text(encoding="utf-8"))["rows"]
        after_cells = raw_log_cells(ctx["after"].db_path)
        differing = [log_id for log_id in sorted(golden) if json.loads(golden[log_id]) != json.loads(after_cells[log_id])]
        step.evidence(differing=differing)
        if differing:
            step.not_run(f"normalized JSON of {differing} differs from the OLDROOT JUnit golden; the semantic check is authoritative. "
                         "Regenerate the golden from WorkoutMediaSessionAssociationTest if the normalization intentionally changed")
        step.detail("migrated JSON equals the JUnit-derived typed-normalization golden")


def main(argv: list[str] | None = None) -> int:
    parser = add_common_args(argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter))
    parser.add_argument("--baseline-apk", type=Path, help="Room-v27 comparison debug APK (default: discovery, see above)")
    parser.add_argument("--validate-only", action="store_true", help="host-only: validate every input and exit (no device)")
    parser.add_argument("--probe-baseline", action="store_true", help="launch the v27 app once before the update (becomes the 'before')")
    parser.add_argument("--check-signers", action="store_true", help="compare APK signers with apksigner (needs Java; slow on 550 MB)")
    parser.add_argument("--launch-timeout", type=float, default=90.0)
    args = parser.parse_args(argv)
    args.label = args.label or ("validate" if args.validate_only else None)
    return execute("migration-27-28", args, lambda session: run(session, args))


if __name__ == "__main__":
    raise SystemExit(main())
