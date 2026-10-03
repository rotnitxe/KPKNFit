#!/usr/bin/env python3
"""Read-only verifier for session-editor Room backups and SharedPreferences.

SQLite is opened only from private temporary copies of the database and present
WAL/SHM sidecars. Original capture files are hashed as evidence, never opened by
SQLite. The tool never repairs/migrates an input. Transfer scenarios require a
third, post-rematerialization backup before they can be reported PASS.

CONSOLIDATION NOTE: this module is a verbatim port of OLDROOT
``runroot/qa/editor-db/verify_editor_db.py`` (logic unchanged; it carries no path
or hash pins - the DEFAULT_* ids below describe the synthetic normal-v27 fixture
in ``fixtures/db``).  It is imported by ``ui_editor_drafts.py``,
``ui_editor_transfer.py`` and ``migrate_27_28.py`` and can still be run as a CLI:

    python -X utf8 room_verify.py BEFORE.db AFTER.db --scenario MIGRATION
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import shutil
import sqlite3
import sys
import tempfile
import urllib.parse
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable


DEFAULT_PROGRAM_ID = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f"
DEFAULT_WEEK_ID = "0d272e12-6fd9-4bf0-8525-b1fd9f45af4f-id--2147483646"
DEFAULT_SOURCE_SESSION_ID = "rs_37de122c4df863f705db0aaa1116dda6"
DEFAULT_WEDNESDAY_SESSION_ID = "rs_c81a24262257d76de6008759667e4ee6"
DEFAULT_FRIDAY_SESSION_ID = "rs_bab1ce5c683be30dae6f8df0cd87e347"
DEFAULT_DRAFT_PREFS = "session_editor_drafts.xml"
DEFAULT_SETTINGS_PREFS = "session_editor_preferences.xml"

TIMESTAMP_KEYS = frozenset(
    {
        "lastModifiedAtMs",
        "createdAtMs",
        "savedAtMs",
        "updatedAtMs",
        "updatedAtEpochMs",
    }
)
ASSOCIATION_TABLE = "workout_media_session_associations"
PRESERVED_MIGRATION_TABLES = ("programs", "workout_logs", "workout_media")


@dataclass
class Check:
    name: str
    status: str
    detail: str


@dataclass
class Report:
    scenario: str
    checks: list[Check] = field(default_factory=list)
    inputs: dict[str, Any] = field(default_factory=dict)
    created_session_id: str | None = None

    def add(self, name: str, status: str, detail: str) -> None:
        if status not in {"PASS", "FAIL", "NOT_RUN"}:
            raise ValueError(f"invalid check status: {status}")
        self.checks.append(Check(name, status, detail))

    @property
    def result(self) -> str:
        if any(check.status == "FAIL" for check in self.checks):
            return "FAIL"
        if any(check.status == "NOT_RUN" for check in self.checks):
            return "INCOMPLETE"
        return "PASS"

    def as_json(self) -> dict[str, Any]:
        return {
            "schema": "kpkn-session-editor-db-check/v1",
            "scenario": self.scenario,
            "result": self.result,
            "checks": [check.__dict__ for check in self.checks],
            "inputs": self.inputs,
            "createdSessionId": self.created_session_id,
            "readOnly": True,
            "limitations": [
                "The report proves only the supplied snapshots and preference XML; it does not prove a UI interaction.",
                "A transfer is never PASS without an explicit post-rematerialization database snapshot.",
                "A 27-to-28 migration with no pre-existing history/media is marked incomplete for non-empty retention.",
            ],
        }


class EvidenceError(Exception):
    """An input is missing or lacks the schema/data needed for an assertion."""


# Base AVD validation (2026-10-02, tool fix): the integrated build added serialised fields whose value is the
# default (Exercise.nativeProgressionManaged=false, ExerciseSet.manualLoadRequiredSides=[]).  The first editor save
# re-encodes the WHOLE program row, so every untouched session gains these keys although nothing changed
# semantically.  An absent key and a default-valued key are the same state, so exactly these two defaults are dropped
# before comparing; any non-default value (true / non-empty list) is still a difference.
DEFAULT_ADDED_FIELDS = {"nativeProgressionManaged": False, "manualLoadRequiredSides": []}


def _is_default_addition(key: str, item: Any) -> bool:
    return key in DEFAULT_ADDED_FIELDS and item == DEFAULT_ADDED_FIELDS[key] and type(item) is type(DEFAULT_ADDED_FIELDS[key])


def canonical(value: Any, *, strip_timestamps: bool = True) -> Any:
    """Normalize JSON for semantic comparisons; ignore generated timestamp fields and default-valued schema additions."""
    if isinstance(value, dict):
        return {
            key: canonical(item, strip_timestamps=strip_timestamps)
            for key, item in value.items()
            if not (strip_timestamps and key in TIMESTAMP_KEYS) and not _is_default_addition(key, item)
        }
    if isinstance(value, list):
        return [canonical(item, strip_timestamps=strip_timestamps) for item in value]
    return value


DERIVED_LEGACY_DEFAULTS = {"nativeProgressionManaged": False, "loadQuantityConvention": "UNSPECIFIED"}


def reconcile_derived_native_metadata(reference: Any, candidate: Any, found: list[dict[str, Any]] | None = None,
                                      path: str = "") -> tuple[Any, list[dict[str, Any]]]:
    """Return ``candidate`` with ONLY the recipe-derived native-progression metadata reverted to ``reference``.

    Base AVD validation (2026-10-02): ``PlanMaterializer.rematerializeWeek`` derives ``nativeProgressionManaged``
    (F/H/I slots of a plan with native progression, section 12.4) and ``loadQuantityConvention`` from the catalog
    equipment for every regenerated session, including days the user never touched.  A legacy-default value
    (``false`` / ``UNSPECIFIED`` / absent) that becomes a derived value is *reported* (returned list, written to the
    report as a visible check) and reverted in the copy that is compared; any other difference still fails.
    """
    found = [] if found is None else found
    if isinstance(candidate, dict) and isinstance(reference, dict):
        out: dict[str, Any] = {}
        for key, value in candidate.items():
            if key in DERIVED_LEGACY_DEFAULTS:
                legacy = DERIVED_LEGACY_DEFAULTS[key]
                ref_value = reference.get(key, legacy)
                if ref_value == legacy and value != legacy:
                    found.append({"path": f"{path}/{key}", "from": ref_value, "to": value})
                    if key in reference:
                        out[key] = ref_value
                    continue
            if key in reference:
                out[key], _ = reconcile_derived_native_metadata(reference[key], value, found, f"{path}/{key}")
            else:
                out[key] = value
        return out, found
    if isinstance(candidate, list) and isinstance(reference, list) and len(candidate) == len(reference):
        items = []
        for index, (ref_item, cand_item) in enumerate(zip(reference, candidate)):
            normalized, _ = reconcile_derived_native_metadata(ref_item, cand_item, found, f"{path}[{index}]")
            items.append(normalized)
        return items, found
    return candidate, found


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class StagedReadOnlyConnection:
    """Connection wrapper that owns and removes its temporary input snapshot."""

    def __init__(
        self,
        connection: sqlite3.Connection,
        temporary_directory: tempfile.TemporaryDirectory[str],
        source_path: Path,
        database_path: Path,
    ) -> None:
        self._connection = connection
        self._temporary_directory = temporary_directory
        self.source_path = source_path
        self.database_path = database_path
        self.temporary_directory_path = Path(temporary_directory.name)
        self._closed = False

    def __getattr__(self, name: str) -> Any:
        return getattr(self._connection, name)

    def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        try:
            self._connection.close()
        finally:
            self._temporary_directory.cleanup()

    def __enter__(self) -> StagedReadOnlyConnection:
        return self

    def __exit__(self, exc_type: Any, exc: Any, traceback: Any) -> None:
        self.close()

    def __del__(self) -> None:
        try:
            self.close()
        except Exception:
            # Explicit close is used on every verifier path; finalization is a
            # best-effort fallback for callers that abandon a connection.
            pass


DatabaseConnection = sqlite3.Connection | StagedReadOnlyConnection


def open_ro(path: Path) -> StagedReadOnlyConnection:
    if not path.is_file():
        raise EvidenceError(f"database backup not found: {path}")
    temporary_directory: tempfile.TemporaryDirectory[str] | None = None
    connection: sqlite3.Connection | None = None
    try:
        # SQLite may create or update -shm even with mode=ro/query_only. Give it
        # a private complete snapshot so captured evidence is never opened.
        temporary_directory = tempfile.TemporaryDirectory(prefix="kpkn-editor-db-read-")
        temporary_root = Path(temporary_directory.name)
        staged_path = temporary_root / path.name
        shutil.copyfile(path, staged_path)
        for suffix in ("-wal", "-shm"):
            source_sidecar = Path(f"{path}{suffix}")
            if source_sidecar.is_file():
                shutil.copyfile(source_sidecar, Path(f"{staged_path}{suffix}"))

        absolute = staged_path.resolve().as_posix()
        uri = "file:" + urllib.parse.quote(absolute, safe="/:\\") + "?mode=ro"
        connection = sqlite3.connect(uri, uri=True)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
        return StagedReadOnlyConnection(connection, temporary_directory, path, staged_path)
    except (OSError, sqlite3.Error) as failure:
        if connection is not None:
            connection.close()
        if temporary_directory is not None:
            temporary_directory.cleanup()
        raise EvidenceError(f"cannot open read-only SQLite backup {path}: {failure}") from failure


def database_version(connection: DatabaseConnection) -> int:
    try:
        return int(connection.execute("PRAGMA user_version").fetchone()[0])
    except (sqlite3.Error, TypeError, ValueError) as failure:
        raise EvidenceError(f"cannot read PRAGMA user_version: {failure}") from failure


def has_table(connection: DatabaseConnection, name: str) -> bool:
    return connection.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (name,)
    ).fetchone() is not None


def require_table(connection: DatabaseConnection, name: str) -> None:
    if not has_table(connection, name):
        raise EvidenceError(f"required Room table is absent: {name}")


def row_count(connection: DatabaseConnection, table: str) -> int:
    require_table(connection, table)
    return int(connection.execute(f' SELECT COUNT(*) FROM "{table}"').fetchone()[0])


def table_rows(connection: DatabaseConnection, table: str) -> list[tuple[Any, ...]]:
    require_table(connection, table)
    columns = [row[1] for row in connection.execute(f'PRAGMA table_info("{table}")')]
    if not columns:
        raise EvidenceError(f"cannot read columns for Room table: {table}")
    quoted_columns = ", ".join(f'"{column}"' for column in columns)
    rows = connection.execute(f'SELECT {quoted_columns} FROM "{table}"').fetchall()
    return sorted((tuple(row[column] for column in columns) for row in rows), key=repr)


def program_json(connection: DatabaseConnection, program_id: str) -> dict[str, Any]:
    require_table(connection, "programs")
    row = connection.execute(
        'SELECT "id", "data" FROM "programs" WHERE "id"=?', (program_id,)
    ).fetchone()
    if row is None:
        raise EvidenceError(f"Program row not found: {program_id}")
    try:
        program = json.loads(row["data"])
    except (TypeError, json.JSONDecodeError) as failure:
        raise EvidenceError(f"Program {program_id} data is not valid JSON: {failure}") from failure
    if not isinstance(program, dict):
        raise EvidenceError(f"Program {program_id} JSON root is not an object")
    if program.get("id") != program_id:
        raise EvidenceError(
            f"Program row id and serialized id differ: {row['id']} vs {program.get('id')}"
        )
    return program


def collect_weeks(program: dict[str, Any], week_id: str) -> list[dict[str, Any]]:
    matches: list[dict[str, Any]] = []

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            if value.get("id") == week_id and isinstance(value.get("sessions"), list):
                matches.append(value)
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(program)
    return matches


def find_week(program: dict[str, Any], week_id: str) -> dict[str, Any]:
    matches = collect_weeks(program, week_id)
    if not matches:
        raise EvidenceError(f"week not found in Program JSON: {week_id}")
    if len(matches) != 1:
        raise EvidenceError(f"week id is ambiguous in Program JSON: {week_id} ({len(matches)} matches)")
    return matches[0]


def find_sessions(week: dict[str, Any], session_id: str) -> list[dict[str, Any]]:
    return [
        session
        for session in week.get("sessions", [])
        if isinstance(session, dict) and session.get("id") == session_id
    ]


def require_session(week: dict[str, Any], session_id: str) -> dict[str, Any]:
    matches = find_sessions(week, session_id)
    if len(matches) != 1:
        raise EvidenceError(
            f"expected exactly one session {session_id} in week {week.get('id')}; found {len(matches)}"
        )
    return matches[0]


def sessions_for_day(week: dict[str, Any], day_of_week: int) -> list[dict[str, Any]]:
    return [
        session
        for session in week.get("sessions", [])
        if isinstance(session, dict) and session.get("dayOfWeek") == day_of_week
    ]


def all_exercises(session: dict[str, Any]) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    direct = session.get("exercises", [])
    if isinstance(direct, list):
        result.extend(item for item in direct if isinstance(item, dict))
    parts = session.get("parts", [])
    if isinstance(parts, list):
        for part in parts:
            if isinstance(part, dict) and isinstance(part.get("exercises"), list):
                result.extend(item for item in part["exercises"] if isinstance(item, dict))
    return result


def session_exercise_ids(session: dict[str, Any]) -> list[str]:
    return [str(exercise["id"]) for exercise in all_exercises(session) if exercise.get("id")]


def all_program_sessions(program: dict[str, Any]) -> list[dict[str, Any]]:
    """Collect materialized sessions from every week in this Program JSON."""
    found: list[dict[str, Any]] = []

    def visit(value: Any) -> None:
        if isinstance(value, dict):
            sessions = value.get("sessions")
            if isinstance(sessions, list):
                found.extend(
                    item
                    for item in sessions
                    if isinstance(item, dict)
                    and isinstance(item.get("id"), str)
                    and isinstance(item.get("dayOfWeek"), int)
                    and ("parts" in item or "exercises" in item)
                )
            for child in value.values():
                visit(child)
        elif isinstance(value, list):
            for child in value:
                visit(child)

    visit(program)
    return found


def session_name_counts(session: dict[str, Any]) -> collections.Counter[str]:
    return collections.Counter(
        str(exercise.get("name", "")) for exercise in all_exercises(session)
    )


def overrides(program: dict[str, Any]) -> list[dict[str, Any]]:
    raw = program.get("manualSessionOverrides", [])
    if not isinstance(raw, list):
        raise EvidenceError("manualSessionOverrides is not an array")
    if any(not isinstance(item, dict) for item in raw):
        raise EvidenceError("manualSessionOverrides contains a non-object")
    return raw


def matching_overrides(
    program: dict[str, Any], session_id: str, week_id: str, scope: str | None = None
) -> list[dict[str, Any]]:
    return [
        override
        for override in overrides(program)
        if override.get("sessionId") == session_id
        and override.get("weekId") == week_id
        and (scope is None or override.get("scope") == scope)
    ]


def day_recipe_id(program: dict[str, Any], week_id: str, day_of_week: int) -> str | None:
    source_recipe = program.get("sourceRecipe")
    if not isinstance(source_recipe, dict):
        return None
    candidate_weeks = source_recipe.get("weeks", [])
    if not isinstance(candidate_weeks, list):
        return None
    # The persisted native program week id is not encoded in recipe JSON. Match
    # the program week number when present; when absent, accept a unique day id
    # across recipe weeks, and otherwise return unknown rather than guess.
    target_week = None
    for week in collect_weeks(program, week_id):
        if isinstance(week.get("progressionIndex"), int):
            target_week = week["progressionIndex"]
            break
    matching: list[str] = []
    for recipe_week in candidate_weeks:
        if not isinstance(recipe_week, dict):
            continue
        week_number = recipe_week.get("weekNumber")
        if target_week is not None and week_number != target_week:
            continue
        days = recipe_week.get("days", [])
        if not isinstance(days, list):
            continue
        for day in days:
            if isinstance(day, dict) and day.get("weekday") == day_of_week:
                recipe_id = day.get("id")
                if isinstance(recipe_id, str) and recipe_id:
                    matching.append(recipe_id)
    return matching[0] if len(set(matching)) == 1 else None


def expected_target_recipe_id(
    baseline: dict[str, Any], session: dict[str, Any] | None, session_id: str, week_id: str, day: int
) -> str | None:
    original_overrides = matching_overrides(baseline, session_id, week_id, "SESSION")
    if len(original_overrides) == 1:
        return original_overrides[0].get("recipeDayId")
    if session is not None:
        recipe_ids = {exercise.get("recipeDayId") for exercise in all_exercises(session) if exercise.get("recipeDayId")}
        if len(recipe_ids) == 1:
            return next(iter(recipe_ids))
        if len(recipe_ids) > 1:
            raise EvidenceError(
                f"target {session_id} has multiple baseline recipeDayId values: {sorted(recipe_ids)}"
            )
    return day_recipe_id(baseline, week_id, day)


def expected_week_occurrence(
    baseline: dict[str, Any], source_id: str, target_id: str, week_id: str, week: dict[str, Any]
) -> int | None:
    for session_id in (target_id, source_id):
        found = matching_overrides(baseline, session_id, week_id, "SESSION")
        if len(found) == 1 and isinstance(found[0].get("weekOccurrence"), int):
            return found[0]["weekOccurrence"]
    known = {
        item.get("weekOccurrence")
        for item in overrides(baseline)
        if item.get("weekId") == week_id
        and item.get("scope") == "SESSION"
        and isinstance(item.get("weekOccurrence"), int)
    }
    if len(known) == 1:
        return next(iter(known))
    # progressionIndex is the fixture’s materialized occurrence only when it is
    # explicitly 1 (the supplied normal-v27 fixture). Avoid guessing for loops.
    if week.get("progressionIndex") == 1:
        return 1
    return None


def override_identity(item: dict[str, Any]) -> dict[str, Any]:
    return {
        key: canonical(value)
        for key, value in item.items()
        if key not in TIMESTAMP_KEYS
    }


def check_source_policy(report: Report, before: dict[str, Any], after: dict[str, Any], source_id: str, week_id: str) -> None:
    before_source = [
        override_identity(item)
        for item in overrides(before)
        if item.get("sessionId") == source_id and item.get("weekId") == week_id
    ]
    after_source = [
        override_identity(item)
        for item in overrides(after)
        if item.get("sessionId") == source_id and item.get("weekId") == week_id
    ]
    report.add(
        "source_override_policy",
        "PASS" if collections.Counter(map(lambda row: json.dumps(row, sort_keys=True), before_source)) == collections.Counter(map(lambda row: json.dumps(row, sort_keys=True), after_source)) else "FAIL",
        "Source SESSION/template override identities and reasons are preserved (generated timestamps ignored).",
    )

    # Every unrelated or pre-existing template-future override must remain.
    before_other = [
        override_identity(item)
        for item in overrides(before)
        if not (
            item.get("sessionId") == source_id
            and item.get("weekId") == week_id
        )
    ]
    after_other = [
        override_identity(item)
        for item in overrides(after)
        if not (
            item.get("sessionId") == source_id
            and item.get("weekId") == week_id
        )
    ]
    # Destination SESSION is the only allowed additional identity. Remove one
    # matching destination SESSION row before checking all other override policy.
    before_target_session = [
        item for item in before_other
        if item.get("sessionId") == report.inputs.get("targetSessionId")
        and item.get("weekId") == week_id
        and item.get("scope") == "SESSION"
    ]
    after_target_session = [
        item for item in after_other
        if item.get("sessionId") == report.inputs.get("targetSessionId")
        and item.get("weekId") == week_id
        and item.get("scope") == "SESSION"
    ]
    for _ in after_target_session:
        after_other.remove(_)
    for _ in before_target_session:
        before_other.remove(_)
    report.add(
        "other_override_policy",
        "PASS" if collections.Counter(map(lambda row: json.dumps(row, sort_keys=True), before_other)) == collections.Counter(map(lambda row: json.dumps(row, sort_keys=True), after_other)) else "FAIL",
        "All non-source/non-destination-session overrides, including template/future policy, are preserved.",
    )


def validate_destination_override(
    report: Report,
    baseline: dict[str, Any],
    current: dict[str, Any],
    week_id: str,
    source_id: str,
    target_id: str,
    target_day: int,
    target_before: dict[str, Any] | None,
    week_before: dict[str, Any],
) -> None:
    matches = matching_overrides(current, target_id, week_id, "SESSION")
    if len(matches) != 1:
        report.add(
            "destination_session_override",
            "FAIL",
            f"Expected one destination SESSION override for {target_id}/{week_id}; found {len(matches)}.",
        )
        return
    actual = matches[0]
    expected_recipe = expected_target_recipe_id(
        baseline, target_before, target_id, week_id, target_day
    )
    expected_occurrence = expected_week_occurrence(
        baseline, source_id, target_id, week_id, week_before
    )
    if expected_occurrence is None:
        report.add(
            "destination_override_coordinates",
            "NOT_RUN",
            "Baseline contains no reliable occurrence for this week; refusing to guess the destination occurrence.",
        )
        return
    okay = (
        actual.get("scope") == "SESSION"
        and actual.get("weekId") == week_id
        and actual.get("weekOccurrence") == expected_occurrence
        and actual.get("recipeDayId") == expected_recipe
    )
    report.add(
        "destination_override_coordinates",
        "PASS" if okay else "FAIL",
        f"Expected SESSION/{week_id}/occurrence={expected_occurrence}/recipeDayId={expected_recipe!r}; "
        f"got {actual.get('scope')}/{actual.get('weekId')}/occurrence={actual.get('weekOccurrence')}/recipeDayId={actual.get('recipeDayId')!r}.",
    )


def compare_unaffected_sessions(
    report: Report,
    before_program: dict[str, Any],
    after_program: dict[str, Any],
    week_id: str,
    source_id: str,
    target_id: str | None,
    created_id: str | None = None,
) -> None:
    before_week = find_week(before_program, week_id)
    after_week = find_week(after_program, week_id)
    expected = {
        session["id"]: canonical(session)
        for session in before_week.get("sessions", [])
        if isinstance(session, dict)
        and isinstance(session.get("id"), str)
        and session["id"] not in {source_id, target_id}
    }
    actual = {
        session["id"]: canonical(session)
        for session in after_week.get("sessions", [])
        if isinstance(session, dict)
        and isinstance(session.get("id"), str)
        and session["id"] not in {source_id, target_id, created_id}
    }
    report.add(
        "unrelated_days_preserved",
        "PASS" if expected == actual else "FAIL",
        f"All sessions except source/transfer destination are semantically unchanged; baseline IDs={sorted(expected)}, after IDs={sorted(actual)}.",
    )


def transfer_checks(
    report: Report,
    before_program: dict[str, Any],
    current_program: dict[str, Any],
    *,
    scenario: str,
    week_id: str,
    source_id: str,
    target_id: str,
    friday_id: str,
    target_day: int,
    expected_created_id: str | None = None,
) -> str | None:
    before_week = find_week(before_program, week_id)
    current_week = find_week(current_program, week_id)
    source_before = require_session(before_week, source_id)
    source_now = require_session(current_week, source_id)
    before_ids = [item.get("id") for item in before_week.get("sessions", []) if isinstance(item, dict)]
    current_ids = [item.get("id") for item in current_week.get("sessions", []) if isinstance(item, dict)]
    report.add(
        "week_session_ids_unique",
        "PASS" if len(before_ids) == len(set(before_ids)) and len(current_ids) == len(set(current_ids)) else "FAIL",
        f"Before/current week session IDs are unique; before={len(before_ids)}, after={len(current_ids)}.",
    )
    target_before_matches = [] if scenario == "CREATE" else find_sessions(before_week, target_id)
    target_before = target_before_matches[0] if len(target_before_matches) == 1 else None
    if scenario == "CREATE":
        prior_day_sessions = sessions_for_day(before_week, target_day)
        report.add(
            "create_day_was_empty",
            "PASS" if not prior_day_sessions else "FAIL",
            f"Tuesday/weekday-{target_day} had no destination before creation; found {len(prior_day_sessions)}.",
        )
        target_candidates = sessions_for_day(current_week, target_day)
        if len(target_candidates) != 1:
            report.add(
                "created_destination_count",
                "FAIL",
                f"Expected exactly one created session on weekday {target_day}; found {len(target_candidates)}.",
            )
            target_now = None
            resolved_target_id = None
        else:
            target_now = target_candidates[0]
            resolved_target_id = target_now.get("id")
            report.created_session_id = str(resolved_target_id)
            if expected_created_id is not None and resolved_target_id != expected_created_id:
                report.add(
                    "created_destination_identity",
                    "FAIL",
                    f"Created session id changed: expected {expected_created_id}, got {resolved_target_id}.",
                )
            else:
                report.add(
                    "created_destination_identity",
                    "PASS",
                    f"One created Tuesday/weekday-{target_day} destination retains id {resolved_target_id}.",
                )
            old_ids = {session.get("id") for session in all_program_sessions(before_program)}
            new_id_unique = (
                isinstance(resolved_target_id, str)
                and resolved_target_id not in old_ids
                and sum(session.get("id") == resolved_target_id for session in all_program_sessions(current_program)) == 1
            )
            report.add(
                "created_session_id_is_new",
                "PASS" if new_id_unique else "FAIL",
                f"Created session id is absent from all prior Program sessions and unique afterward: {resolved_target_id!r}.",
            )
    else:
        matches = find_sessions(current_week, target_id)
        resolved_target_id = target_id
        target_now = matches[0] if len(matches) == 1 else None
        report.add(
            "destination_session_identity",
            "PASS" if len(matches) == 1 else "FAIL",
            f"Existing destination id {target_id} appears exactly once after transfer; found {len(matches)}.",
        )
    report.inputs["targetSessionId"] = resolved_target_id
    if target_now is None:
        report.add("transfer_payload", "FAIL", "Destination session payload is missing or ambiguous.")
        return resolved_target_id

    source_count = len(all_exercises(source_before))
    expected_before_target = 0 if scenario == "CREATE" else len(all_exercises(target_before or {}))
    expected_after_count = {
        "APPEND": source_count + expected_before_target,
        "REPLACE": source_count,
        "CREATE": source_count,
    }[scenario]
    before_counts_ok = source_count == 5 and (scenario == "CREATE" or expected_before_target == 4)
    report.add(
        "fixture_preconditions",
        "PASS" if before_counts_ok else "FAIL",
        f"Expected source=5 and existing Wednesday target=4 where applicable; got source={source_count}, target={expected_before_target}.",
    )

    after_exercises = all_exercises(target_now)
    after_count = len(after_exercises)
    names_expected = session_name_counts(source_before)
    if scenario == "APPEND" and target_before is not None:
        names_expected += session_name_counts(target_before)
    report.add(
        "transfer_payload_count",
        "PASS" if after_count == expected_after_count else "FAIL",
        f"{scenario} expected {expected_after_count} exercise rows (5 source + {expected_before_target} existing for APPEND); got {after_count}.",
    )
    report.add(
        "transfer_payload_contents",
        "PASS" if session_name_counts(target_now) == names_expected else "FAIL",
        f"Destination exercise names match the source payload {'plus the four prior target exercises' if scenario == 'APPEND' else 'exactly'}; "
        f"expected={dict(names_expected)}, actual={dict(session_name_counts(target_now))}.",
    )
    ids = session_exercise_ids(target_now)
    ids_unique = len(ids) == len(set(ids))
    before_target_ids = set(session_exercise_ids(target_before or {}))
    if scenario == "APPEND":
        identity_ok = ids_unique and before_target_ids.issubset(set(ids))
        identity_detail = "APPEND keeps every pre-existing destination exercise ID and has no duplicate exercise IDs in the destination."
    else:
        identity_ok = ids_unique
        identity_detail = "Destination exercise IDs are unique within the resulting session."
    report.add("exercise_identity", "PASS" if identity_ok else "FAIL", identity_detail)

    if scenario == "REPLACE":
        expected_id = target_id
        stale_id = target_now.get("id") != expected_id
        report.add(
            "replace_destination_identity",
            "FAIL" if stale_id else "PASS",
            f"REPLACE preserves destination session ID {target_id}; actual={target_now.get('id')}.",
        )

    if target_now.get("dayOfWeek") != target_day:
        report.add(
            "destination_day_preserved",
            "FAIL",
            f"Destination weekday changed: expected {target_day}, got {target_now.get('dayOfWeek')}.",
        )
    else:
        report.add("destination_day_preserved", "PASS", f"Destination weekday remains {target_day}.")

    if canonical(source_before) != canonical(source_now):
        report.add("source_session_preserved", "FAIL", "Transfer changed the source session content (timestamps ignored).")
    else:
        report.add("source_session_preserved", "PASS", "Source session content is unchanged (generated timestamps ignored).")

    try:
        friday_before = require_session(before_week, friday_id)
        friday_now = require_session(current_week, friday_id)
    except EvidenceError as failure:
        report.add("friday_session_preserved", "FAIL", f"Required Friday sibling evidence is missing: {failure}")
    else:
        unchanged_friday = canonical(friday_before) == canonical(friday_now)
        report.add(
            "friday_session_preserved",
            "PASS" if unchanged_friday else "FAIL",
            "Friday sibling content and identity are unchanged (generated timestamps ignored)." if unchanged_friday else "Transfer changed the Friday sibling session.",
        )

    compare_unaffected_sessions(
        report,
        before_program,
        current_program,
        week_id,
        source_id,
        target_id if scenario != "CREATE" else None,
        resolved_target_id if scenario == "CREATE" else None,
    )
    validate_destination_override(
        report,
        before_program,
        current_program,
        week_id,
        source_id,
        str(resolved_target_id),
        target_day,
        target_before,
        before_week,
    )
    check_source_policy(report, before_program, current_program, source_id, week_id)
    return str(resolved_target_id) if resolved_target_id is not None else None


def xml_preferences(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise EvidenceError(f"SharedPreferences XML not found: {path}")
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as failure:
        raise EvidenceError(f"cannot parse SharedPreferences XML {path}: {failure}") from failure
    values: dict[str, str] = {}
    for child in root:
        key = child.attrib.get("name")
        if not key:
            continue
        if child.tag == "string":
            values[key] = child.text or ""
        elif child.tag in {"boolean", "int", "long", "float"}:
            values[key] = child.attrib.get("value", "")
        elif child.tag == "set":
            values[key] = json.dumps([item.text or "" for item in child], sort_keys=True)
    return values


def record_optional_file_hash(report: Report, label: str, path: Path | None) -> None:
    if path is None:
        report.add(f"input_{label}", "NOT_RUN", "No SharedPreferences XML snapshot was supplied.")
        return
    try:
        report.inputs[label] = {"path": str(path), "sha256": sha256_file(path)}
    except OSError as failure:
        report.add(f"input_{label}", "NOT_RUN", str(failure))


def check_editor_preferences_separate(
    report: Report,
    *,
    draft_prefs_before: Path | None,
    settings_before: Path | None,
    settings_after: Path | None,
) -> None:
    if settings_before is None or settings_after is None:
        report.add(
            "editor_preferences_separate",
            "NOT_RUN",
            "Both --settings-before and --settings-after XML backups are required to prove editor preferences are stored separately.",
        )
        return
    try:
        before_settings = xml_preferences(settings_before)
        after_settings = xml_preferences(settings_after)
    except EvidenceError as failure:
        report.add("editor_preferences_separate", "NOT_RUN", str(failure))
        return
    expected = before_settings.get("auto_save_enabled")
    expected_source = "session_editor_preferences.xml before action"
    if expected is None:
        try:
            old_draft_values = xml_preferences(draft_prefs_before) if draft_prefs_before is not None else {}
        except EvidenceError as failure:
            report.add("editor_preferences_separate", "NOT_RUN", str(failure))
            return
        expected = old_draft_values.get("auto_save_enabled", "true")
        expected_source = "legacy draft preference or default true"
    after_value = after_settings.get("auto_save_enabled")
    okay = after_value is not None and after_value == expected
    report.add(
        "editor_preferences_separate",
        "PASS" if okay else "FAIL",
        f"auto_save_enabled is persisted in the separate session_editor_preferences.xml and remains {expected!r} from {expected_source}; got {after_value!r}.",
    )


def draft_key(program_id: str, week_id: str, macro_index: int, meso_index: int, session_id: str) -> str:
    return f"program={program_id}|week={week_id or '__unspecified_week__'}|macro={macro_index}|meso={meso_index}|editor={session_id}"


def verify_draft_scenario(
    report: Report,
    *,
    scenario: str,
    before_connection: DatabaseConnection,
    after_connection: DatabaseConnection,
    program_id: str,
    week_id: str,
    session_id: str,
    macro_index: int,
    meso_index: int,
    draft_prefs_before: Path | None,
    draft_prefs_after: Path | None,
    settings_before: Path | None,
    settings_after: Path | None,
    expected_marker: str | None,
    reopened_ui_xml: Path | None,
) -> None:
    key = draft_key(program_id, week_id, macro_index, meso_index, session_id)
    report.inputs["draftStorageKey"] = key
    check_editor_preferences_separate(
        report,
        draft_prefs_before=draft_prefs_before,
        settings_before=settings_before,
        settings_after=settings_after,
    )
    if draft_prefs_before is None or draft_prefs_after is None:
        report.add("draft_preference_evidence", "NOT_RUN", "Both --draft-prefs-before and --draft-prefs-after XML backups are required.")
        return
    try:
        before_values = xml_preferences(draft_prefs_before)
        after_values = xml_preferences(draft_prefs_after)
    except EvidenceError as failure:
        report.add("draft_preference_evidence", "NOT_RUN", str(failure))
        return
    before_raw, after_raw = before_values.get(key), after_values.get(key)
    if scenario in {"DRAFTSAVE", "DRAFTRESTORE"}:
        if scenario == "DRAFTRESTORE":
            if reopened_ui_xml is None:
                report.add("draft_visible_after_recovery", "NOT_RUN", "A fresh post-relaunch editor UI XML backup is required to prove restored text was visible.")
            else:
                try:
                    ui_root = ET.fromstring(reopened_ui_xml.read_text(encoding="utf-8"))
                except (OSError, ET.ParseError) as failure:
                    report.add("draft_visible_after_recovery", "NOT_RUN", f"Cannot read the post-relaunch UI XML: {failure}")
                else:
                    visible_values = [
                        value
                        for node in ui_root.iter()
                        for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
                    ]
                    visible = expected_marker is not None and any(expected_marker in value for value in visible_values)
                    report.add(
                        "draft_visible_after_recovery",
                        "PASS" if visible else "FAIL",
                        f"The exact draft marker {expected_marker!r} appears in the editor UI XML captured after force-stop/reopen."
                        if visible
                        else f"The exact marker was not visible after recovery; expected={expected_marker!r}.",
                    )
        report.add("draft_written", "PASS" if after_raw is not None else "FAIL", "Draft storage key exists after save." if after_raw is not None else "Draft key is absent after requested save.")
        if after_raw is None:
            return
        try:
            draft = json.loads(after_raw)
        except json.JSONDecodeError as failure:
            report.add("draft_json", "FAIL", f"Saved draft is invalid JSON: {failure}")
            return
        coordinates = (
            draft.get("programId") == program_id
            and draft.get("weekId") == week_id
            and draft.get("sessionId") == session_id
            and draft.get("macroIndex") == macro_index
            and draft.get("mesoIndex") == meso_index
            and isinstance(draft.get("session"), dict)
        )
        report.add("draft_coordinates", "PASS" if coordinates else "FAIL", "Persisted draft contains the requested program/week/session coordinates." if coordinates else f"Persisted draft coordinates do not match: {draft}")
        try:
            baseline_program = program_json(before_connection, program_id)
            after_program = program_json(after_connection, program_id)
            baseline_session = require_session(find_week(baseline_program, week_id), session_id)
            database_session = require_session(find_week(after_program, week_id), session_id)
        except EvidenceError as failure:
            report.add("draft_content_edit", "NOT_RUN", str(failure))
        else:
            draft_session = draft.get("session")
            meaningful = canonical(draft_session) != canonical(baseline_session)
            still_unsaved = canonical(draft_session) != canonical(database_session)
            report.add("draft_content_edit", "PASS" if meaningful and still_unsaved else "FAIL", "Draft contains a real content change relative to both Room snapshots; generated timestamps alone do not count." if meaningful and still_unsaved else "No real unsaved content delta is proven; timestamps alone are ignored.")
            if before_raw is not None:
                try:
                    previous_draft = json.loads(before_raw)
                    changed_from_previous = canonical(previous_draft.get("session")) != canonical(draft_session)
                except json.JSONDecodeError:
                    changed_from_previous = True
                report.add("draft_replaced_with_new_content", "PASS" if changed_from_previous else "FAIL", "The saved draft content differs from its prior stored snapshot." if changed_from_previous else "The stored draft changed only by metadata/timestamps or not at all.")
            else:
                report.add("draft_replaced_with_new_content", "PASS", "No prior draft existed for this editor key.")
    elif scenario == "DRAFTDISCARD":
        report.add("draft_existed_before_discard", "PASS" if before_raw is not None else "FAIL", "Draft key exists before discard." if before_raw is not None else "No draft key existed before requested discard.")
        report.add("draft_removed_after_discard", "PASS" if after_raw is None else "FAIL", "Draft key is absent after discard." if after_raw is None else "Draft key remains after discard.")
        try:
            if before_raw is None:
                raise EvidenceError("no draft JSON was stored before discard")
            draft_before = json.loads(before_raw)
            if not isinstance(draft_before, dict):
                raise EvidenceError("draft JSON root is not an object")
            coordinates = (
                draft_before.get("programId") == program_id
                and draft_before.get("weekId") == week_id
                and draft_before.get("sessionId") == session_id
                and draft_before.get("macroIndex") == macro_index
                and draft_before.get("mesoIndex") == meso_index
                and isinstance(draft_before.get("session"), dict)
            )
            report.add(
                "discard_draft_coordinates",
                "PASS" if coordinates else "FAIL",
                "Pre-discard draft JSON coordinates match this editor session." if coordinates else "Pre-discard draft has mismatched coordinates or no session payload.",
            )
            if not coordinates:
                raise EvidenceError("pre-discard draft coordinates are invalid")
            before_program = program_json(before_connection, program_id)
            after_program = program_json(after_connection, program_id)
            baseline_session = require_session(find_week(before_program, week_id), session_id)
            dirty = canonical(draft_before["session"]) != canonical(baseline_session)
            report.add(
                "discard_removed_real_unsaved_edit",
                "PASS" if dirty else "FAIL",
                "Pre-discard draft contains a real content change against Room; generated timestamps alone do not count." if dirty else "Pre-discard draft has no real content delta against Room.",
            )
            unchanged = canonical(before_program) == canonical(after_program)
        except EvidenceError as failure:
            report.add("discard_kept_room_baseline", "NOT_RUN", str(failure))
        except (TypeError, json.JSONDecodeError) as failure:
            report.add("discard_kept_room_baseline", "NOT_RUN", f"Cannot validate pre-discard draft JSON: {failure}")
        else:
            report.add("discard_kept_room_baseline", "PASS" if unchanged else "FAIL", "Discard left the Room Program unchanged (generated timestamps ignored)." if unchanged else "Discard changed the Room Program.")
    else:
        report.add(
            "draft_absent_before_commit",
            "PASS" if before_raw is None else "FAIL",
            "No previous draft exists for this editor key before Save and exit." if before_raw is None else "The baseline already contained a draft for this editor key; this commit comparison is contaminated.",
        )
        report.add(
            "draft_absent_after_commit",
            "PASS" if after_raw is None else "FAIL",
            "Draft key is absent after Save and exit plus lifecycle/autosave delay." if after_raw is None else "A draft was recreated or left behind after Save and exit.",
        )
        try:
            before_program = program_json(before_connection, program_id)
            after_program = program_json(after_connection, program_id)
            before_session = require_session(find_week(before_program, week_id), session_id)
            after_session = require_session(find_week(after_program, week_id), session_id)
            content_changed = canonical(before_session) != canonical(after_session)
        except EvidenceError as failure:
            report.add("room_content_committed", "NOT_RUN", str(failure))
        else:
            expected_description = expected_marker
            actual_description = after_session.get("description")
            marker_matches = expected_description is not None and actual_description == expected_description
            committed = content_changed and (marker_matches if expected_description is not None else actual_description != before_session.get("description"))
            detail = (
                f"Room contains the real description marker {expected_description!r} after save."
                if committed and expected_description is not None
                else "Room session content changed after Save and exit."
                if committed
                else f"Room does not contain the requested saved description; expected={expected_description!r}, actual={actual_description!r}, contentChanged={content_changed}."
            )
            report.add("room_content_committed", "PASS" if committed else "FAIL", detail)


def migration_associations_expected(connection: DatabaseConnection) -> dict[str, str]:
    require_table(connection, "workout_media")
    require_table(connection, "workout_logs")
    valid_logs = {
        str(row[0])
        for row in connection.execute('SELECT "id" FROM "workout_logs"')
    }
    per_key: dict[str, set[str]] = collections.defaultdict(set)
    for row in connection.execute('SELECT "sessionKey", "workoutLogId" FROM "workout_media"'):
        session_key, log_id = row[0], row[1]
        if isinstance(session_key, str) and session_key and isinstance(log_id, str) and log_id and log_id in valid_logs:
            per_key[session_key].add(log_id)
    return {
        key: next(iter(log_ids))
        for key, log_ids in per_key.items()
        if len(log_ids) == 1
    }


def verify_migration(report: Report, before: DatabaseConnection, after: DatabaseConnection) -> None:
    before_version, after_version = database_version(before), database_version(after)
    report.inputs["beforeUserVersion"] = before_version
    report.inputs["afterUserVersion"] = after_version
    if (before_version, after_version) != (27, 28):
        report.add("migration_version_pair", "NOT_RUN", f"Requires actual v27→v28 snapshots; got {before_version}→{after_version}.")
        return
    report.add("migration_version_pair", "PASS", "Inputs are a v27 before snapshot and v28 after snapshot.")
    for table in PRESERVED_MIGRATION_TABLES:
        try:
            before_rows = table_rows(before, table)
            after_rows = table_rows(after, table)
        except EvidenceError as failure:
            report.add(f"migration_preserves_{table}", "NOT_RUN", str(failure))
        else:
            report.add(f"migration_preserves_{table}", "PASS" if before_rows == after_rows else "FAIL", f"{table} row count/content: before={len(before_rows)}, after={len(after_rows)}; exact row data comparison.")
    try:
        expected = migration_associations_expected(before)
    except EvidenceError as failure:
        report.add("migration_media_associations", "NOT_RUN", str(failure))
    else:
        if not has_table(after, ASSOCIATION_TABLE):
            report.add("migration_media_associations", "FAIL", f"v28 association table is missing: {ASSOCIATION_TABLE}")
        else:
            pairs = [
                (str(row[0]), str(row[1]))
                for row in after.execute(
                    f'SELECT "sessionKey", "workoutLogId" FROM "{ASSOCIATION_TABLE}"'
                )
            ]
            actual = dict(pairs)
            unique_keys = len(pairs) == len(actual)
            good = unique_keys and actual == expected
            report.add("migration_media_associations", "PASS" if good else "FAIL", f"Association mapping has unique session keys and matches unambiguous existing media evidence: expected={expected}, actual={actual}, uniqueSessionKeys={unique_keys}.")
    for table, check_name, label in (
        ("workout_logs", "migration_nonempty_workout_logs", "workout history"),
        ("workout_media", "migration_nonempty_workout_media", "workout media"),
    ):
        try:
            count_before = row_count(before, table)
            count_after = row_count(after, table)
        except EvidenceError as failure:
            report.add(check_name, "NOT_RUN", str(failure))
        else:
            if count_before == 0:
                report.add(check_name, "NOT_RUN", f"The v27 input has no {label}; non-empty retention for this table is not evidenced.")
            else:
                report.add(
                    check_name,
                    "PASS" if count_after >= count_before else "FAIL",
                    f"Non-empty {label} retained: before={count_before}, after={count_after}.",
                )


def verify(
    before_path: Path,
    after_path: Path,
    scenario: str,
    *,
    rematerialized_path: Path | None = None,
    program_id: str = DEFAULT_PROGRAM_ID,
    week_id: str = DEFAULT_WEEK_ID,
    source_id: str = DEFAULT_SOURCE_SESSION_ID,
    target_id: str = DEFAULT_WEDNESDAY_SESSION_ID,
    friday_id: str = DEFAULT_FRIDAY_SESSION_ID,
    target_day: int = 3,
    create_day: int = 2,
    draft_session_id: str | None = None,
    macro_index: int = 0,
    meso_index: int = 0,
    draft_prefs_before: Path | None = None,
    draft_prefs_after: Path | None = None,
    settings_before: Path | None = None,
    settings_after: Path | None = None,
    draft_marker: str | None = None,
    reopened_ui_xml: Path | None = None,
    allow_derived_native_metadata: bool = False,
) -> Report:
    scenario = scenario.upper()
    if scenario not in {"APPEND", "REPLACE", "CREATE", "DRAFTSAVE", "DRAFTRESTORE", "DRAFTDISCARD", "DRAFTCOMMIT", "MIGRATION"}:
        raise ValueError(f"unsupported scenario: {scenario}")
    report = Report(scenario)
    for label, path in (("beforeDb", before_path), ("afterDb", after_path)):
        try:
            report.inputs[label] = {"path": str(path), "sha256": sha256_file(path)}
        except OSError as failure:
            report.add(f"input_{label}", "NOT_RUN", str(failure))
    if scenario in {"DRAFTSAVE", "DRAFTRESTORE", "DRAFTDISCARD", "DRAFTCOMMIT"}:
        record_optional_file_hash(report, "draftPrefsBefore", draft_prefs_before)
        record_optional_file_hash(report, "draftPrefsAfter", draft_prefs_after)
        record_optional_file_hash(report, "settingsBefore", settings_before)
        record_optional_file_hash(report, "settingsAfter", settings_after)
        if scenario == "DRAFTRESTORE":
            record_optional_file_hash(report, "reopenedUiXml", reopened_ui_xml)
    before: StagedReadOnlyConnection | None = None
    after: StagedReadOnlyConnection | None = None
    try:
        before = open_ro(before_path)
        after = open_ro(after_path)
    except EvidenceError as failure:
        report.add("sqlite_inputs", "NOT_RUN", str(failure))
        if before is not None:
            before.close()
        return report
    try:
        before_version, after_version = database_version(before), database_version(after)
        report.inputs["beforeUserVersion"] = before_version
        report.inputs["afterUserVersion"] = after_version
        report.add("sqlite_read_only", "PASS", f"Private copies of both supplied databases opened with mode=ro/query_only; Room versions {before_version}→{after_version}.")
        if scenario == "MIGRATION":
            verify_migration(report, before, after)

        if scenario in {"APPEND", "REPLACE", "CREATE"}:
            try:
                before_program = program_json(before, program_id)
                after_program = program_json(after, program_id)
                before_week = find_week(before_program, week_id)
                source_before = require_session(before_week, source_id)
            except EvidenceError as failure:
                report.add("transfer_program_evidence", "NOT_RUN", str(failure))
                return report
            destination_id = None if scenario == "CREATE" else target_id
            destination_day = create_day if scenario == "CREATE" else target_day
            target = transfer_checks(
                report,
                before_program,
                after_program,
                scenario=scenario,
                week_id=week_id,
                source_id=source_id,
                target_id=str(destination_id or target_id),
                friday_id=friday_id,
                target_day=destination_day,
            )
            if rematerialized_path is None:
                report.add("post_rematerialization", "NOT_RUN", "Provide --rematerialized-db captured after forcing PlanMaterializer/rematerialization; without it transfer persistence cannot pass.")
            else:
                remat: StagedReadOnlyConnection | None = None
                try:
                    remat = open_ro(rematerialized_path)
                    remat_program = program_json(remat, program_id)
                    remat_version = database_version(remat)
                    report.inputs["rematerializedDb"] = {
                        "path": str(rematerialized_path),
                        "sha256": sha256_file(rematerialized_path),
                        "userVersion": remat_version,
                    }
                except (EvidenceError, OSError) as failure:
                    if remat is not None:
                        remat.close()
                    report.add("post_rematerialization", "NOT_RUN", str(failure))
                else:
                    try:
                        if allow_derived_native_metadata:
                            remat_program, upgrades = reconcile_derived_native_metadata(after_program, remat_program)
                            report.add(
                                "derived_native_metadata_upgrade",
                                "PASS",
                                f"INFO (compared as unchanged): rematerialization derived native-progression metadata on previously "
                                f"legacy-default exercises: {len(upgrades)} field change(s) "
                                f"(nativeProgressionManaged false->true / loadQuantityConvention UNSPECIFIED->derived); "
                                f"examples={[f'{u['path'][-70:]}: {u['from']}->{u['to']}' for u in upgrades[:4]]}.",
                            )
                            report.inputs["derivedNativeMetadataUpgrades"] = upgrades[:60]
                        expected_created = target if scenario == "CREATE" else None
                        before_check_count = len(report.checks)
                        remat_target = transfer_checks(
                            report,
                            before_program,
                            remat_program,
                            scenario=scenario,
                            week_id=week_id,
                            source_id=source_id,
                            target_id=str(destination_id or target_id),
                            friday_id=friday_id,
                            target_day=destination_day,
                            expected_created_id=expected_created,
                        )
                        newly_added = report.checks[before_check_count:]
                        remat_failed = any(check.status == "FAIL" for check in newly_added)
                        remat_incomplete = any(check.status == "NOT_RUN" for check in newly_added)
                        # Also require the materialized result not to change from the
                        # post-save snapshot, except generated timestamps.
                        try:
                            after_week = find_week(after_program, week_id)
                            remat_week = find_week(remat_program, week_id)
                            if scenario == "CREATE":
                                after_session = require_session(after_week, str(target))
                                remat_session = require_session(remat_week, str(remat_target))
                            else:
                                after_session = require_session(after_week, target_id)
                                remat_session = require_session(remat_week, target_id)
                            stable = canonical(after_session) == canonical(remat_session)
                        except EvidenceError:
                            stable = False
                        status = "FAIL" if remat_failed or not stable else "NOT_RUN" if remat_incomplete else "PASS"
                        detail = (
                            "Transfer destination and overrides survive rematerialization without changing ID/content or duplicating exercises."
                            if status == "PASS"
                            else "Rematerialized Program does not match the saved transfer result or one of its invariants failed."
                            if status == "FAIL"
                            else "Rematerialization data is present, but at least one required invariant lacks reliable input evidence."
                        )
                        report.add("post_rematerialization", status, detail)
                    finally:
                        remat.close()
        elif scenario in {"DRAFTSAVE", "DRAFTRESTORE", "DRAFTDISCARD", "DRAFTCOMMIT"}:
            verify_draft_scenario(
                report,
                scenario=scenario,
                before_connection=before,
                after_connection=after,
                program_id=program_id,
                week_id=week_id,
                session_id=draft_session_id or source_id,
                macro_index=macro_index,
                meso_index=meso_index,
                draft_prefs_before=draft_prefs_before,
                draft_prefs_after=draft_prefs_after,
                settings_before=settings_before,
                settings_after=settings_after,
                expected_marker=draft_marker,
                reopened_ui_xml=reopened_ui_xml,
            )
        elif scenario == "MIGRATION" and not (before_version == 27 and after_version == 28):
            # verify_migration already placed an explicit NOT_RUN version-pair check.
            pass
    finally:
        before.close()
        after.close()
    return report


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before_db", type=Path)
    parser.add_argument("after_db", type=Path)
    parser.add_argument("--scenario", required=True, choices=("APPEND", "REPLACE", "CREATE", "DRAFTSAVE", "DRAFTRESTORE", "DRAFTDISCARD", "DRAFTCOMMIT", "MIGRATION"))
    parser.add_argument("--rematerialized-db", type=Path, help="Required for transfer scenarios; backup after rematerialization.")
    parser.add_argument("--program-id", default=DEFAULT_PROGRAM_ID)
    parser.add_argument("--week-id", default=DEFAULT_WEEK_ID)
    parser.add_argument("--source-session-id", default=DEFAULT_SOURCE_SESSION_ID)
    parser.add_argument("--target-session-id", default=DEFAULT_WEDNESDAY_SESSION_ID)
    parser.add_argument("--friday-session-id", default=DEFAULT_FRIDAY_SESSION_ID)
    parser.add_argument("--target-day", type=int, default=3, help="Existing Wednesday weekday number; defaults to 3.")
    parser.add_argument("--create-day", type=int, default=2, help="New Tuesday weekday number; defaults to 2.")
    parser.add_argument("--draft-session-id")
    parser.add_argument("--draft-marker", help="exact description marker expected in Room after DRAFTCOMMIT")
    parser.add_argument("--reopened-ui-xml", type=Path, help="post-force-stop/reopen editor UI XML used by DRAFTRESTORE")
    parser.add_argument("--macro-index", type=int, default=0)
    parser.add_argument("--meso-index", type=int, default=0)
    parser.add_argument("--draft-prefs-before", type=Path, help="session_editor_drafts.xml before save/discard.")
    parser.add_argument("--draft-prefs-after", type=Path, help="session_editor_drafts.xml after save/discard.")
    parser.add_argument("--settings-before", type=Path, help="session_editor_preferences.xml before action.")
    parser.add_argument("--settings-after", type=Path, help="session_editor_preferences.xml after action.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    try:
        report = verify(
            args.before_db,
            args.after_db,
            args.scenario,
            rematerialized_path=args.rematerialized_db,
            program_id=args.program_id,
            week_id=args.week_id,
            source_id=args.source_session_id,
            target_id=args.target_session_id,
            friday_id=args.friday_session_id,
            target_day=args.target_day,
            create_day=args.create_day,
            draft_session_id=args.draft_session_id,
            macro_index=args.macro_index,
            meso_index=args.meso_index,
            draft_prefs_before=args.draft_prefs_before,
            draft_prefs_after=args.draft_prefs_after,
            settings_before=args.settings_before,
            settings_after=args.settings_after,
            draft_marker=args.draft_marker,
            reopened_ui_xml=args.reopened_ui_xml,
        )
    except (EvidenceError, ValueError, sqlite3.Error, OSError) as failure:
        print(json.dumps({"schema": "kpkn-session-editor-db-check/v1", "result": "INCOMPLETE", "error": str(failure), "readOnly": True}, indent=2, ensure_ascii=False))
        return 3
    print(json.dumps(report.as_json(), indent=2, ensure_ascii=False))
    return {"PASS": 0, "FAIL": 2, "INCOMPLETE": 3}[report.result]


if __name__ == "__main__":
    raise SystemExit(main())
