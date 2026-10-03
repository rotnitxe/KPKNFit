"""Pure Room/file assertions for workout media (import + CameraX capture).

Ported from OLDROOT ``verify_workout_media_finish.py`` and the V4 controller assertions, with
the fixture-hash pins turned into explicit arguments.  All inputs are summaries produced by
``room_inspect.inspect_database`` plus optional device-measured hashes.
"""

from __future__ import annotations

import re
from typing import Any

from room_inspect import SQUAT_EXERCISE_ID, is_uuid

MEDIA_PATH_RE = re.compile(
    r"^/data/(?:user/0|data)/com\.example\.kpkn/files/workout_media/"
    r"\d{4}-\d{2}/[0-9a-fA-F-]{36}\.(?:png|jpe?g|mp4|webm|mov|m4v|3gp)$"
)
VIDEO_EXTENSIONS = {".mp4", ".webm", ".mov", ".m4v", ".3gp"}
PHOTO_EXTENSIONS = {".png", ".jpg", ".jpeg"}
STABLE_FIELDS = ("kind", "filePath", "createdAtMs", "sessionKey", "programId", "sessionId", "sessionName",
                 "exerciseId", "canonicalExerciseId", "exerciseName", "setIndex", "side", "weightKg", "reps")


def media_for_session(summary: dict[str, Any], session_key: str) -> list[dict[str, Any]]:
    return [row for row in summary.get("mediaRows", []) if row.get("sessionKey") == session_key]


def ongoing_key(summary: dict[str, Any]) -> str | None:
    ongoing = summary.get("ongoing") or {}
    return ongoing.get("sessionKey")


def private_path_problem(row: dict[str, Any]) -> str | None:
    path = row.get("filePath")
    if not isinstance(path, str) or not MEDIA_PATH_RE.fullmatch(path):
        return f"media {row.get('id')}: path outside the app-private workout_media tree: {path!r}"
    extension = "." + path.rsplit(".", 1)[-1].lower()
    if row.get("kind") == "VIDEO" and extension not in VIDEO_EXTENSIONS:
        return f"media {row.get('id')}: VIDEO row with non-video extension {extension}"
    if row.get("kind") == "PHOTO" and extension not in PHOTO_EXTENSIONS:
        return f"media {row.get('id')}: PHOTO row with non-image extension {extension}"
    if row.get("kind") not in {"PHOTO", "VIDEO"}:
        return f"media {row.get('id')}: unknown kind {row.get('kind')!r}"
    return None


def session_media_problem(summary: dict[str, Any], session_key: str, expected_kinds: list[str]) -> str | None:
    """Rows of the active execution: exact kinds, UUID ids, unique, unlinked, no association yet."""
    if summary.get("databaseVersion") != 28:
        return f"expected Room v28, got {summary.get('databaseVersion')!r}"
    rows = media_for_session(summary, session_key)
    kinds = sorted(row.get("kind") for row in rows)
    if kinds != sorted(expected_kinds):
        return f"expected media kinds {sorted(expected_kinds)} for {session_key}, got {kinds}"
    ids = [row.get("id") for row in rows]
    if len(set(ids)) != len(ids):
        return "duplicate media ids for the execution"
    bad = [i for i in ids if not is_uuid(i)]
    if bad:
        return f"media ids are not canonical UUIDs: {bad}"
    if any(row.get("workoutLogId") not in (None, "") for row in rows):
        return "media already attached to a log before Finish"
    if any(link.get("sessionKey") == session_key for link in summary.get("associations", [])):
        return "the execution already has a session-to-log association before Finish"
    for row in rows:
        problem = private_path_problem(row)
        if problem:
            return problem
    return None


def finish_binding_problem(before: dict[str, Any], after: dict[str, Any], session_key: str) -> tuple[str | None, dict[str, Any]]:
    """Pre/post-Finish comparison: same rows, frozen metadata, one durable link, all rows bound to the log."""
    info: dict[str, Any] = {"sessionKey": session_key}
    if after.get("databaseVersion") != 28:
        return f"expected Room v28 after Finish, got {after.get('databaseVersion')!r}", info
    before_rows = {row["id"]: row for row in media_for_session(before, session_key)}
    after_rows = {row["id"]: row for row in media_for_session(after, session_key)}
    if set(before_rows) != set(after_rows):
        return f"media ids changed across Finish: before={sorted(before_rows)}, after={sorted(after_rows)}", info
    if ongoing_key(after) == session_key:
        return "the finalized execution still appears in ongoing_workout", info
    changed = {
        media_id: {f: (before_rows[media_id].get(f), after_rows[media_id].get(f)) for f in STABLE_FIELDS
                   if before_rows[media_id].get(f) != after_rows[media_id].get(f)}
        for media_id in before_rows
    }
    changed = {k: v for k, v in changed.items() if v}
    if changed:
        return f"frozen media metadata changed during Finish: {changed}", info
    if any(link.get("sessionKey") == session_key for link in before.get("associations", [])):
        return "association already existed before Finish", info
    links = [link for link in after.get("associations", []) if link.get("sessionKey") == session_key]
    if len(links) != 1:
        return f"expected exactly one durable session-to-log link after Finish, found {links}", info
    log_id = links[0]["workoutLogId"]
    info["workoutLogId"] = log_id
    if log_id not in after.get("workoutLogIds", []):
        return f"association points to a missing workout log {log_id!r}", info
    unattached = [mid for mid, row in after_rows.items() if row.get("workoutLogId") != log_id]
    if unattached:
        return f"media rows not attached to log {log_id}: {unattached}", info
    info["mediaIds"] = sorted(after_rows)
    return None, info


def finalized_set_problem(after: dict[str, Any], log_id: str, s1: dict[str, Any]) -> str | None:
    """The persisted log contains the same S1 (UUID, weight, reps) that was in ongoing_workout."""
    logs = [row for row in after.get("recentTargetExerciseLogs", []) if row.get("id") == log_id]
    if len(logs) != 1:
        return f"workout log {log_id} not present exactly once in the capture"
    sets = [x for x in logs[0].get("targetExerciseSets", []) if x.get("exerciseId") == SQUAT_EXERCISE_ID]
    if len(sets) != 1:
        return f"expected one finalized squat set, found {sets}"
    row = sets[0]
    if row.get("id") != s1.get("id"):
        return f"finalized set UUID {row.get('id')!r} != ongoing S1 UUID {s1.get('id')!r}"
    if (not isinstance(row.get("weight"), (int, float)) or abs(float(row["weight"]) - float(s1["weight"])) > 1e-6
            or row.get("reps") != s1.get("reps")):
        return f"finalized set differs from ongoing S1: {row} vs {s1}"
    return None


def s1_active_problem(summary: dict[str, Any], expected_key: str, weight: float, reps: int) -> tuple[str | None, dict[str, Any]]:
    ongoing = summary.get("ongoing")
    if not ongoing:
        return "no ongoing workout in the after-S1 capture", {}
    if ongoing.get("sessionKey") != expected_key:
        return f"after-S1 capture belongs to {ongoing.get('sessionKey')}, expected {expected_key}", {}
    sets = [x for x in ongoing.get("completedSets", []) if x.get("exerciseId") == SQUAT_EXERCISE_ID]
    if len(sets) != 1 or sets[0].get("setIndex") != 0:
        return f"expected exactly one persisted squat S1, found {sets}", {}
    row = sets[0]
    if not is_uuid(row.get("id")):
        return f"persisted S1 has no UUID identity: {row.get('id')!r}", {}
    if not isinstance(row.get("weight"), (int, float)) or abs(float(row["weight"]) - weight) > 1e-6 or row.get("reps") != reps:
        return f"after-S1 row is not {weight} x {reps}: {row}", {}
    return None, row


# ---------------------------------------------------------------------------
# Origin classification: real CameraX capture vs. file import
# ---------------------------------------------------------------------------

def sniff_media_format(head: bytes) -> str:
    """Container sniffing from the first bytes of a file."""
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        return "PNG"
    if head.startswith(b"\xff\xd8\xff"):
        return "JPEG"
    if len(head) >= 12 and head[4:8] == b"ftyp":
        return "MP4"
    if head.startswith(b"\x1a\x45\xdf\xa3"):
        return "WEBM"
    return "UNKNOWN"


def classify_origin(*, kind: str, file_format: str, sha256: str, import_fixture_hashes: set[str],
                    interaction: str, created_in_window: bool, via_picker: bool) -> dict[str, Any]:
    """Decide CAMERAX_CAPTURE / IMPORT / UNKNOWN from independent signals.

    * ``interaction``    what the driver pressed: ``shutter`` (Foto / Vídeo chip) or ``picker`` (Galería)
    * ``via_picker``     the DocumentsUI picker was in the foreground during the action
    * ``file_format``    JPEG for a CameraX still, MP4 for a CameraX recording
    * ``import_fixture_hashes``  hashes of the synthetic PNG/MP4 fixtures (can never be a capture)
    """
    reasons: list[str] = []
    if sha256 in import_fixture_hashes:
        return {"origin": "IMPORT", "reasons": ["bytes equal a synthetic import fixture"]}
    if interaction == "picker" or via_picker:
        return {"origin": "IMPORT", "reasons": ["created through the Galería / document picker"]}
    expected_format = {"PHOTO": "JPEG", "VIDEO": "MP4"}.get(kind)
    ok = True
    if file_format != expected_format:
        ok = False
        reasons.append(f"file format {file_format} is not the CameraX {kind} format {expected_format}")
    if not created_in_window:
        ok = False
        reasons.append("Room createdAtMs outside the capture window")
    if interaction != "shutter":
        ok = False
        reasons.append(f"interaction {interaction!r} is not the shutter control")
    if ok:
        return {"origin": "CAMERAX_CAPTURE", "reasons": [
            f"shutter control pressed, {file_format} bytes differ from every import fixture, row created inside the capture window"]}
    return {"origin": "UNKNOWN", "reasons": reasons}
