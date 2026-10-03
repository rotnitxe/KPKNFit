#!/usr/bin/env python3
"""Fixture and source-hash REGISTRY (not a pin).

* ``fixtures/fixtures.lock.json`` records the SHA-256 of every synthetic input
  copied from OLDROOT into this toolset.  Drivers verify the fixture they are
  about to push against the lock so a corrupted/edited input is detected, and
  they copy the verified record into their evidence.  Nothing asserts against a
  value from the old isolated workspace.
* ``source_registry()`` hashes the integrated product sources that matter to the
  UI drivers (current tree, whatever it contains) so every run's evidence says
  *which* sources the run exercised.  It never fails on a "different" hash.

CLI::

    python -X utf8 fixtures.py list
    python -X utf8 fixtures.py verify
    python -X utf8 fixtures.py init-lock [--force]     # rewrite the lock (intentional change only)
    python -X utf8 fixtures.py sources                 # current product-source registry
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any

from evidence import MissingInput, sha256_file, utc_now, write_json
from qa_paths import FIXTURES_DIR, MAIN_JAVA, SCHEMAS_DIR

LOCK_PATH = FIXTURES_DIR / "fixtures.lock.json"

# Synthetic identities shared by every fixture derived from the audit AVD program.
PROGRAM_ID = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f"
WEEK_ID = PROGRAM_ID + "-id--2147483646"
MONDAY_SESSION_ID = "rs_37de122c4df863f705db0aaa1116dda6"
WEDNESDAY_SESSION_ID = "rs_c81a24262257d76de6008759667e4ee6"
FRIDAY_SESSION_ID = "rs_bab1ce5c683be30dae6f8df0cd87e347"
SQUAT_EXERCISE_ID = "re_e3866f71f020e8820f908768bac54081"
SQUAT_DISPLAY_NAME = "Sentadilla Trasera con Barra Baja"
CARDIO_EXERCISE_NAME = "Carrera GPS QA dos series"
WORKOUT_URI = f"kpkn://workout/{PROGRAM_ID}/{MONDAY_SESSION_ID}"
PROGRAM_URI = f"kpkn://program/{PROGRAM_ID}"
PHOTO_NAME, VIDEO_NAME = "kpkn-qa-photo.png", "kpkn-qa-video.mp4"

FIXTURES: dict[str, dict[str, Any]] = {
    "normal-v27": {
        "path": "db/normal-v27.db", "kind": "room-db", "roomVersion": 27,
        "use": "session editor drafts + transfer (APPEND/REPLACE/CREATE); Home hydration fixture",
    },
    "strength-empty-v27": {
        "path": "db/strength-empty-v27.db", "kind": "room-db", "roomVersion": 27,
        "use": "strength S1->S2 single-click flow (ongoing workout, zero completed sets)",
    },
    "finish-v2-normal-v27": {
        "path": "db/finish-v2-normal-v27.db", "kind": "room-db", "roomVersion": 27,
        "use": "workout media import/capture + cancel (first exercise warmups removed so S1 is the first card)",
    },
    "cardio-two-series-ui-v27": {
        "path": "db/cardio-two-series-ui-v27.db", "kind": "room-db", "roomVersion": 27,
        "use": "cardio GPS two-series flow + cardio cancellation (ids qa-cardio-series-0/1)",
    },
    "migration-retention-v27": {
        "path": "db/migration-retention-v27.db", "kind": "room-db", "roomVersion": 27,
        "use": "real 27->28 migration: 1 program, 2 logs, 4 media (1 valid, 2 ambiguous) , 1 ongoing",
    },
    "qa-photo": {"path": "media/" + PHOTO_NAME, "kind": "media", "use": "synthetic PNG (IMPORT fixture; never a CameraX capture)"},
    "qa-video": {"path": "media/" + VIDEO_NAME, "kind": "media", "use": "synthetic MP4 (IMPORT fixture; never a CameraX capture)"},
    "qa-media-manifest": {"path": "media/asset-manifest.json", "kind": "manifest", "use": "ffprobe metadata of the media fixtures"},
    "migration-log-golden": {
        "path": "migration/normalized-log-rows.golden.json", "kind": "golden",
        "use": "OPTIONAL expected typed-normalization rows of the two migrated workout_logs (JUnit-derived, OLDROOT)",
    },
}
PREFS_DIR = "audit_shared_prefs"

# Product sources worth recording in evidence (registry, not assertion).
SOURCE_REGISTRY_FILES = [
    "screens/workout/WorkoutV2Body.kt",
    "screens/workout/WorkoutViewModel.kt",
    "screens/workout/WorkoutScreen.kt",
    "screens/workout/WorkoutFinishHost.kt",
    "screens/workout/WorkoutSessionOverlaysHost.kt",
    "screens/workout/WorkoutMediaCaptureController.kt",
    "screens/workout/CardioLiveCard.kt",
    "screens/workout/components/SetExecutionCard.kt",
    "screens/workout/components/SetCardExerciseMediaBack.kt",
    "screens/workout/components/WorkoutMediaThumb.kt",
    "screens/workout/components/WorkoutSessionAlbumSheet.kt",
    "screens/sessioneditor/SessionEditorScreen.kt",
    "screens/sessioneditor/components/SessionHero.kt",
    "services/cardio/CardioGpsForegroundService.kt",
    "services/cardio/CardioGpsPersistence.kt",
    "data/media/WorkoutMediaStore.kt",
    "data/repository/WorkoutMediaRepository.kt",
    "data/db/KpknDatabase.kt",
    "data/db/WorkoutMediaSessionAssociation.kt",
]


def fixture_path(name: str) -> Path:
    try:
        return FIXTURES_DIR / FIXTURES[name]["path"]
    except KeyError as error:
        raise MissingInput(f"unknown fixture {name!r}", f"known: {sorted(FIXTURES)}") from error


def build_lock() -> dict[str, Any]:
    entries: dict[str, Any] = {}
    for name, spec in FIXTURES.items():
        path = FIXTURES_DIR / spec["path"]
        if not path.is_file():
            raise MissingInput(f"fixture file missing: {path}")
        entries[name] = {"path": spec["path"], "bytes": path.stat().st_size, "sha256": sha256_file(path)}
    prefs = {}
    for path in sorted((FIXTURES_DIR / PREFS_DIR).glob("*.xml")):
        prefs[path.name] = {"bytes": path.stat().st_size, "sha256": sha256_file(path)}
    return {
        "schema": "kpkn-fixtures-lock/v1",
        "createdAtUtc": utc_now(),
        "origin": "copied from artifacts/session-repair/2026-09-30-01a0eede (read-only) on 2026-10-01",
        "policy": "registry of intended input hashes; drift = corrupted/edited input, not a product assertion",
        "fixtures": entries,
        "auditSharedPrefs": prefs,
    }


def load_lock() -> dict[str, Any]:
    if not LOCK_PATH.is_file():
        raise MissingInput(f"fixture lock missing: {LOCK_PATH}", "run: python -X utf8 fixtures.py init-lock")
    return json.loads(LOCK_PATH.read_text(encoding="utf-8"))


def verify_fixture(name: str) -> dict[str, Any]:
    """Return the evidence record of a fixture after checking it against the lock."""
    lock = load_lock()
    path = fixture_path(name)
    if not path.is_file():
        raise MissingInput(f"fixture {name} is missing: {path}")
    locked = lock["fixtures"].get(name)
    if locked is None:
        raise MissingInput(f"fixture {name} is absent from fixtures.lock.json", "run: fixtures.py init-lock --force")
    actual = sha256_file(path)
    if actual != locked["sha256"]:
        raise MissingInput(
            f"fixture {name} changed since the lock was written (expected {locked['sha256'][:16]}..., got {actual[:16]}...)",
            "restore the file or refresh the lock deliberately with fixtures.py init-lock --force",
        )
    return {"name": name, "path": str(path), "bytes": path.stat().st_size, "sha256": actual,
            "use": FIXTURES[name]["use"], "lock": str(LOCK_PATH)}


def verify_prefs() -> dict[str, Any]:
    lock = load_lock()
    folder = FIXTURES_DIR / PREFS_DIR
    expected = lock.get("auditSharedPrefs") or {}
    actual = {p.name: sha256_file(p) for p in sorted(folder.glob("*.xml"))}
    if not actual:
        raise MissingInput(f"no audit preference snapshot in {folder}")
    drift = {n: h for n, h in actual.items() if expected.get(n, {}).get("sha256") != h}
    if drift or set(expected) != set(actual):
        raise MissingInput(f"audit shared_prefs drifted from the lock: {sorted(drift) or sorted(set(expected) ^ set(actual))}")
    return {"dir": str(folder), "files": {n: {"sha256": h} for n, h in actual.items()}}


def verify_all() -> dict[str, Any]:
    return {"fixtures": {name: verify_fixture(name) for name in FIXTURES}, "prefs": verify_prefs()}


def read_db_identity(path: Path) -> dict[str, Any]:
    """Facts about a (static, single-file) fixture DB, read with immutable=1."""
    connection = sqlite3.connect(f"file:{Path(path).as_posix()}?mode=ro&immutable=1", uri=True, timeout=10)
    try:
        tables = {row[0] for row in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        counts = {t: connection.execute(f'SELECT COUNT(*) FROM "{t}"').fetchone()[0]
                  for t in ("programs", "workout_logs", "workout_media", "ongoing_workout", "workout_media_session_associations")
                  if t in tables}
        info: dict[str, Any] = {
            "userVersion": connection.execute("PRAGMA user_version").fetchone()[0],
            "journalMode": connection.execute("PRAGMA journal_mode").fetchone()[0],
            "counts": counts,
        }
        if "ongoing_workout" in tables:
            row = connection.execute('SELECT "data" FROM "ongoing_workout" ORDER BY "rowId" LIMIT 1').fetchone()
            if row:
                data = json.loads(row[0])
                session = data.get("session") or {}
                parts = session.get("parts") or []
                exercises = [e for p in parts for e in (p.get("exercises") or [])]
                names = [e.get("name") for e in exercises]
                info["ongoing"] = {
                    "programId": data.get("programId"), "sessionId": session.get("id"),
                    "startTimeMs": data.get("startTime", data.get("startTimeMs")),
                    "completedSets": sorted((data.get("completedSets") or {}).keys()),
                    "exerciseNames": names,
                    "exerciseSets": {e.get("name"): len(e.get("sets") or []) for e in exercises},
                    "exerciseWarmups": {e.get("name"): len(e.get("warmupSets") or []) for e in exercises},
                    "firstExerciseId": exercises[0].get("id") if exercises else None,
                    "sessionKey": f"{data.get('programId')}::{session.get('id')}::{data.get('startTime', data.get('startTimeMs'))}",
                }
        return info
    finally:
        connection.close()


def source_registry() -> dict[str, Any]:
    """SHA-256 of the integrated product sources used by the UI drivers (informational)."""
    entries: dict[str, Any] = {}
    for relative in SOURCE_REGISTRY_FILES:
        path = MAIN_JAVA / relative
        entries[relative] = {"sha256": sha256_file(path), "bytes": path.stat().st_size} if path.is_file() else {"missing": True}
    schemas = sorted(SCHEMAS_DIR.glob("*.json"), key=lambda p: int(p.stem) if p.stem.isdigit() else -1) if SCHEMAS_DIR.is_dir() else []
    latest = schemas[-1] if schemas else None
    return {
        "capturedAtUtc": utc_now(),
        "root": str(MAIN_JAVA),
        "sources": entries,
        "roomSchema": {"latest": latest.name, "sha256": sha256_file(latest)} if latest else None,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list")
    sub.add_parser("verify")
    sub.add_parser("sources")
    init = sub.add_parser("init-lock")
    init.add_argument("--force", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "list":
            for name, spec in FIXTURES.items():
                print(f"{name:28s} {spec['path']:36s} {spec['use']}")
        elif args.command == "verify":
            print(json.dumps(verify_all(), indent=2, ensure_ascii=False))
        elif args.command == "sources":
            print(json.dumps(source_registry(), indent=2, ensure_ascii=False))
        elif args.command == "init-lock":
            if LOCK_PATH.exists() and not args.force:
                print(f"{LOCK_PATH} exists; pass --force to rewrite it deliberately", file=sys.stderr)
                return 4
            write_json(LOCK_PATH, build_lock())
            print(f"wrote {LOCK_PATH}")
        return 0
    except MissingInput as error:
        print(f"MISSING_INPUT: {error}", file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
