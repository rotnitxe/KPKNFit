"""Read-only Room inspection of captured app data (host side, no adb).

SQLite is only ever opened on a private temporary copy of ``kpkn.db`` plus its
``-wal`` / ``-shm`` sidecars, so captured evidence files are never touched.
Ported from OLDROOT ``capture_room_owned.py`` / ``verify_workout_media_finish.py``
with the hash and path pins removed.
"""

from __future__ import annotations

import contextlib
import hashlib
import json
import re
import shutil
import sqlite3
import tempfile
import uuid
from pathlib import Path
from typing import Any, Iterator

# Synthetic fixture identities (see fixtures.py for the full contract).
SQUAT_EXERCISE_ID = "re_e3866f71f020e8820f908768bac54081"
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
SET_KEY_RE = re.compile(r"^(?P<exercise>.+)_(?P<index>\d+)(?:_(?:L|R|left|right))?$")
LOG_SET_INDEX_RE = re.compile(r"(?:_|#set:)(\d+)(?:_(?:L|R|left|right))?$")
MEDIA_COLUMNS = (
    "id", "kind", "filePath", "thumbPath", "createdAtMs", "sessionKey", "workoutLogId", "programId", "sessionId",
    "sessionName", "exerciseId", "canonicalExerciseId", "exerciseName", "setIndex", "side", "weightKg", "reps",
    "isPr", "durationMs", "width", "height", "caption", "poseTrackPath",
)


@contextlib.contextmanager
def open_copy_ro(db_path: Path) -> Iterator[sqlite3.Connection]:
    """Open a read-only connection on a private copy of db + WAL + SHM."""
    db_path = Path(db_path)
    if not db_path.is_file():
        raise FileNotFoundError(f"database missing from capture: {db_path}")
    with tempfile.TemporaryDirectory(prefix="kpkn-room-ro-") as temp_name:
        copied = Path(temp_name) / "kpkn.db"
        for suffix in ("", "-wal", "-shm"):
            source = Path(str(db_path) + suffix)
            if source.is_file():
                shutil.copy2(source, Path(str(copied) + suffix))
        connection = sqlite3.connect(copied.as_uri() + "?mode=ro", uri=True, timeout=15)
        connection.row_factory = sqlite3.Row
        try:
            connection.execute("PRAGMA query_only=ON")
            if connection.execute("PRAGMA query_only").fetchone()[0] != 1:
                raise RuntimeError("SQLite refused query_only mode")
            yield connection
        finally:
            connection.close()


def has_table(connection: sqlite3.Connection, table: str) -> bool:
    return connection.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)).fetchone() is not None


def table_counts(connection: sqlite3.Connection) -> dict[str, int]:
    tables = [row[0] for row in connection.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    counts: dict[str, int] = {}
    for table in tables:
        quoted = '"' + table.replace('"', '""') + '"'
        counts[table] = int(connection.execute(f"SELECT COUNT(*) FROM {quoted}").fetchone()[0])
    return counts


def json_object(raw: Any) -> dict[str, Any] | None:
    if not isinstance(raw, str) or not raw.strip():
        return None
    try:
        value = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return None
    return value if isinstance(value, dict) else None


def session_key(program_id: str, session_id: str, start_time_ms: int) -> str:
    return f"{program_id}::{session_id}::{int(start_time_ms)}"


def ongoing_state(raw: Any) -> dict[str, Any] | None:
    state = json_object(raw)
    if state is None:
        return None
    session = state.get("session")
    program_id = state.get("programId")
    session_id = session.get("id") if isinstance(session, dict) else state.get("sessionId")
    start = state.get("startTimeMs", state.get("startTime"))
    key = None
    try:
        if isinstance(program_id, str) and isinstance(session_id, str) and int(start) > 0:
            key = session_key(program_id, session_id, int(start))
    except (TypeError, ValueError, OverflowError):
        key = None
    return {"programId": program_id, "sessionId": session_id, "startTimeMs": start, "sessionKey": key, "raw": state}


def _set_detail(identity: str, exercise_id: str, set_index: int | None, value: dict[str, Any]) -> dict[str, Any]:
    return {"id": value.get("id") or identity, "exerciseId": exercise_id, "setIndex": set_index,
            "weight": value.get("weight"), "reps": value.get("reps")}


def ongoing_set_details(state: dict[str, Any]) -> list[dict[str, Any]]:
    completed = state.get("completedSets")
    if not isinstance(completed, dict):
        return []
    details = []
    for key, raw in completed.items():
        if not isinstance(key, str) or not isinstance(raw, dict) or raw.get("isWarmup") is True:
            continue
        match = SET_KEY_RE.match(key)
        if match:
            details.append(_set_detail(key, match.group("exercise"), int(match.group("index")), raw))
    return sorted(details, key=lambda item: (item["exerciseId"], item["setIndex"] or 0, item["id"]))


def log_set_details(data: dict[str, Any], exercise_id: str) -> list[dict[str, Any]]:
    exercises = data.get("completedExercises")
    if not isinstance(exercises, list):
        return []
    details = []
    for exercise in exercises:
        if not isinstance(exercise, dict) or exercise.get("exerciseId") != exercise_id:
            continue
        for raw in exercise.get("sets") or []:
            if not isinstance(raw, dict) or raw.get("isWarmup") is True:
                continue
            identity = raw.get("id")
            match = LOG_SET_INDEX_RE.search(identity) if isinstance(identity, str) else None
            details.append(_set_detail(identity if isinstance(identity, str) else "", exercise_id,
                                       int(match.group(1)) if match else None, raw))
    return sorted(details, key=lambda item: (item["setIndex"] is None, item["setIndex"] or 0, item["id"]))


def media_rows(connection: sqlite3.Connection) -> list[dict[str, Any]]:
    if not has_table(connection, "workout_media"):
        return []
    columns = {row[1] for row in connection.execute('PRAGMA table_info("workout_media")')}
    selected = [column for column in MEDIA_COLUMNS if column in columns]
    projection = ", ".join(f'"{column}"' for column in selected)
    return [dict(row) for row in connection.execute(
        f'SELECT {projection} FROM "workout_media" ORDER BY "createdAtMs", "id"')]


def association_rows(connection: sqlite3.Connection) -> list[dict[str, str]]:
    if not has_table(connection, "workout_media_session_associations"):
        return []
    return [dict(row) for row in connection.execute(
        'SELECT "sessionKey", "workoutLogId" FROM "workout_media_session_associations" ORDER BY "sessionKey"')]


def workout_log_ids(connection: sqlite3.Connection) -> list[str]:
    if not has_table(connection, "workout_logs"):
        return []
    return [str(row[0]) for row in connection.execute('SELECT "id" FROM "workout_logs" ORDER BY "id"')]


def inspect_database(db_path: Path, target_exercise_id: str = SQUAT_EXERCISE_ID) -> dict[str, Any]:
    """Summary used by every driver (counts, ongoing session, media, associations)."""
    with open_copy_ro(db_path) as conn:
        counts = table_counts(conn)
        ongoing_rows: list[dict[str, Any]] = []
        if has_table(conn, "ongoing_workout"):
            columns = {row[1] for row in conn.execute('PRAGMA table_info("ongoing_workout")')}
            if "data" in columns:
                for (raw,) in conn.execute('SELECT "data" FROM "ongoing_workout" ORDER BY "rowId"').fetchall():
                    state = ongoing_state(raw)
                    if state is not None:
                        ongoing_rows.append(state)
        ongoing = None
        if ongoing_rows:
            first = ongoing_rows[0]
            ongoing = {
                "programId": first["programId"], "sessionId": first["sessionId"],
                "startTimeMs": first["startTimeMs"], "sessionKey": first["sessionKey"],
                "completedSets": ongoing_set_details(first["raw"]),
                "cardioTimerState": first["raw"].get("cardioTimerState"),
                "activeExerciseId": first["raw"].get("activeExerciseId"),
                "activeSetIndex": first["raw"].get("activeSetIndex"),
            }
        logs: list[dict[str, Any]] = []
        corrupt = 0
        if has_table(conn, "workout_logs"):
            columns = {row[1] for row in conn.execute('PRAGMA table_info("workout_logs")')}
            if {"id", "date", "data"}.issubset(columns):
                extra = [name for name in ("programId", "sessionId") if name in columns]
                selected = ["id", "date", *extra, "data"]
                sql = "SELECT " + ", ".join(f'"{name}"' for name in selected) + ' FROM "workout_logs" ORDER BY "date" DESC LIMIT 200'
                for row in conn.execute(sql):
                    record = dict(row)
                    data = json_object(record.pop("data"))
                    if data is None:
                        corrupt += 1
                        continue
                    details = log_set_details(data, target_exercise_id)
                    if details:
                        record["targetExerciseSets"] = details
                        logs.append(record)
        return {
            "databaseVersion": int(conn.execute("PRAGMA user_version").fetchone()[0]),
            "counts": counts,
            "ongoing": ongoing,
            "ongoingRowCount": counts.get("ongoing_workout"),
            "recentTargetExerciseLogs": logs,
            "targetExerciseId": target_exercise_id,
            "corruptRecentLogJsonCount": corrupt,
            "mediaRows": media_rows(conn),
            "associations": association_rows(conn),
            "workoutLogIds": workout_log_ids(conn),
        }


def db_path_of(capture_dir: Path) -> Path:
    """kpkn.db inside a directory produced by ``Avd.room_pull``."""
    return Path(capture_dir) / "app-data" / "databases" / "kpkn.db"


def inspect_capture(capture_dir: Path, target_exercise_id: str = SQUAT_EXERCISE_ID) -> dict[str, Any]:
    return inspect_database(db_path_of(capture_dir), target_exercise_id)


def assert_two_squat_sets(summary: dict[str, Any], expected: list[tuple[int, float, int]] | None = None) -> list[tuple]:
    """Latest persisted state has exactly the expected target sets (default 20x6 and 22.5x6)."""
    expected = expected or [(0, 20.0, 6), (1, 22.5, 6)]
    ongoing = summary.get("ongoing") or {}
    chosen = [item for item in ongoing.get("completedSets", []) if item.get("exerciseId") == SQUAT_EXERCISE_ID]
    source = "ongoing_workout"
    if not chosen:
        logs = summary.get("recentTargetExerciseLogs") or []
        chosen = logs[0].get("targetExerciseSets", []) if logs else []
        source = "latest workout_logs row with the target exercise"
    actual = sorted(((i.get("setIndex"), i.get("weight"), i.get("reps")) for i in chosen),
                    key=lambda item: (item[0] is None, item[0] if item[0] is not None else -1))
    ok = len(actual) == len(expected) and all(
        got_index == want_index and isinstance(got_weight, (int, float))
        and abs(float(got_weight) - want_weight) <= 1e-6 and got_reps == want_reps
        for (got_index, got_weight, got_reps), (want_index, want_weight, want_reps) in zip(actual, expected)
    )
    if not ok:
        raise AssertionError(f"expected target sets {expected} in {source}; got {actual}")
    return actual


def is_uuid(value: Any) -> bool:
    if not isinstance(value, str) or not UUID_RE.fullmatch(value):
        return False
    try:
        uuid.UUID(value)
    except ValueError:
        return False
    return True


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


# ---------------------------------------------------------------------------
# Cardio (two-series GPS) helpers, ported from the OLDROOT cardio driver
# ---------------------------------------------------------------------------

CARDIO_EXERCISE_ID = SQUAT_EXERCISE_ID  # the synthetic fixture re-uses this exercise id for the cardio card
CARDIO_SERIES_IDS = ("qa-cardio-series-0", "qa-cardio-series-1")


def cardio_state(db_path: Path) -> dict[str, Any]:
    with open_copy_ro(db_path) as conn:
        version = int(conn.execute("PRAGMA user_version").fetchone()[0])
        row = conn.execute('SELECT "data" FROM "ongoing_workout" ORDER BY "rowId" LIMIT 1').fetchone() \
            if has_table(conn, "ongoing_workout") else None
        logs = int(conn.execute('SELECT COUNT(*) FROM "workout_logs"').fetchone()[0]) if has_table(conn, "workout_logs") else 0
        if row is None:
            return {"databaseVersion": version, "ongoing": None, "workoutLogCount": logs}
        raw = json.loads(row[0])
        session = raw.get("session") if isinstance(raw.get("session"), dict) else {}
        completed = raw.get("completedSets") if isinstance(raw.get("completedSets"), dict) else {}
        keys = {f"{CARDIO_EXERCISE_ID}_0", f"{CARDIO_EXERCISE_ID}_1"}
        return {
            "databaseVersion": version,
            "workoutLogCount": logs,
            "ongoing": {
                "programId": raw.get("programId"),
                "sessionId": session.get("id") or raw.get("sessionId"),
                "startTimeMs": raw.get("startTimeMs", raw.get("startTime")),
                "activeExerciseId": raw.get("activeExerciseId"),
                "activeSetIndex": raw.get("activeSetIndex"),
                "cardioTimerState": raw.get("cardioTimerState") if isinstance(raw.get("cardioTimerState"), dict) else None,
                "cardioCompletedSets": {k: v for k, v in completed.items() if k in keys},
            },
        }


def expected_gps_session_key(ongoing: dict[str, Any], series_index: int) -> str:
    if series_index not in (0, 1):
        raise ValueError("series_index must be 0 or 1")
    program_id, session_id, start = ongoing.get("programId"), ongoing.get("sessionId"), ongoing.get("startTimeMs")
    if not isinstance(program_id, str) or not isinstance(session_id, str) or start is None:
        raise ValueError("ongoing workout lacks complete session identity")
    return f"{program_id}::{session_id}::{int(start)}::{CARDIO_EXERCISE_ID}::{CARDIO_SERIES_IDS[series_index]}"


def gps_snapshot_relative_path(key: str, pending: bool = False) -> str:
    digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
    return f"files/cardio-gps/{digest}.json" + (".pending" if pending else "")
