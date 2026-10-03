"""Room inspection, the ported strict verifier on the real v27 fixture, and migration helpers."""

from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import fixtures  # noqa: E402
import migrate_27_28 as mig  # noqa: E402
import room_inspect as ri  # noqa: E402
import room_verify as rv  # noqa: E402
from evidence import MissingInput  # noqa: E402

FIXTURE = fixtures.fixture_path("migration-retention-v27")
SQUAT = ri.SQUAT_EXERCISE_ID


def make_db(path: Path, *, version=28, ongoing=None, logs=(), media=(), associations=()):
    connection = sqlite3.connect(path)
    connection.executescript(
        'CREATE TABLE ongoing_workout(rowId INTEGER PRIMARY KEY, data TEXT);'
        'CREATE TABLE workout_logs(id TEXT PRIMARY KEY, date TEXT, programId TEXT, sessionId TEXT, data TEXT);'
        'CREATE TABLE workout_media(id TEXT PRIMARY KEY, kind TEXT, filePath TEXT, thumbPath TEXT, createdAtMs INTEGER, '
        'sessionKey TEXT, workoutLogId TEXT, programId TEXT, sessionId TEXT, sessionName TEXT, exerciseId TEXT, '
        'canonicalExerciseId TEXT, exerciseName TEXT, setIndex INTEGER, side TEXT, weightKg REAL, reps INTEGER, isPr INTEGER, '
        'durationMs INTEGER, width INTEGER, height INTEGER, caption TEXT, poseTrackPath TEXT);'
        'CREATE TABLE workout_media_session_associations(sessionKey TEXT PRIMARY KEY, workoutLogId TEXT NOT NULL);')
    if ongoing is not None:
        connection.execute("INSERT INTO ongoing_workout(data) VALUES (?)", (json.dumps(ongoing),))
    for row in logs:
        connection.execute("INSERT INTO workout_logs VALUES (?,?,?,?,?)", row)
    for row in media:
        connection.execute("INSERT INTO workout_media(id,kind,filePath,createdAtMs,sessionKey,workoutLogId) VALUES (?,?,?,?,?,?)", row)
    for row in associations:
        connection.execute("INSERT INTO workout_media_session_associations VALUES (?,?)", row)
    connection.execute(f"PRAGMA user_version={version}")
    connection.commit()
    connection.close()
    return path


ONGOING = {"programId": "P", "session": {"id": "S"}, "startTime": 1790720640166,
           "completedSets": {f"{SQUAT}_0": {"id": "11111111-1111-4111-8111-111111111111", "weight": 20, "reps": 6},
                             f"{SQUAT}_1": {"id": "22222222-2222-4222-8222-222222222222", "weight": 22.5, "reps": 6},
                             f"{SQUAT}_warmup_0": {"id": "w", "weight": 10, "reps": 5, "isWarmup": True}}}


class InspectTests(unittest.TestCase):
    def test_inspect_synthetic_database(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = make_db(Path(tmp) / "kpkn.db", ongoing=ONGOING,
                         media=[("33333333-3333-4333-8333-333333333333", "PHOTO", "/data/user/0/com.example.kpkn/files/workout_media/2026-10/x.jpg",
                                 1, "P::S::1790720640166", None)],
                         associations=[("P::S::1", "L1")], logs=[("L1", "2026-10-01", "P", "S", json.dumps(
                             {"completedExercises": [{"exerciseId": SQUAT, "sets": [{"id": f"{SQUAT}#set:0", "weight": 20, "reps": 6}]}]}))])
            summary = ri.inspect_database(db)
        self.assertEqual(summary["databaseVersion"], 28)
        self.assertEqual(summary["counts"]["ongoing_workout"], 1)
        self.assertEqual(summary["ongoing"]["sessionKey"], "P::S::1790720640166")
        sets = summary["ongoing"]["completedSets"]
        self.assertEqual([(s["setIndex"], s["weight"]) for s in sets if s["exerciseId"] == SQUAT], [(0, 20), (1, 22.5)])
        self.assertEqual(len(summary["mediaRows"]), 1)
        self.assertEqual(summary["associations"], [{"sessionKey": "P::S::1", "workoutLogId": "L1"}])
        self.assertEqual(summary["recentTargetExerciseLogs"][0]["targetExerciseSets"][0]["setIndex"], 0)

    def test_two_squat_sets_assertion(self):
        with tempfile.TemporaryDirectory() as tmp:
            summary = ri.inspect_database(make_db(Path(tmp) / "kpkn.db", ongoing=ONGOING))
            self.assertEqual(ri.assert_two_squat_sets(summary), [(0, 20, 6), (1, 22.5, 6)])
            broken = json.loads(json.dumps(ONGOING))
            broken["completedSets"][f"{SQUAT}_1"]["weight"] = 25
            summary = ri.inspect_database(make_db(Path(tmp) / "kpkn2.db", ongoing=broken))
            with self.assertRaises(AssertionError):
                ri.assert_two_squat_sets(summary)

    def test_wal_is_read_and_source_is_untouched(self):
        with tempfile.TemporaryDirectory() as tmp:
            db = Path(tmp) / "kpkn.db"
            connection = sqlite3.connect(db)
            connection.execute("PRAGMA journal_mode=WAL")
            connection.execute("CREATE TABLE ongoing_workout(rowId INTEGER PRIMARY KEY, data TEXT)")
            connection.execute("PRAGMA user_version=28")
            connection.commit()
            connection.execute("INSERT INTO ongoing_workout(data) VALUES ('{}')")
            connection.commit()
            wal = Path(str(db) + "-wal")
            self.assertTrue(wal.exists() and wal.stat().st_size > 0)
            snapshot = Path(tmp) / "capture"
            snapshot.mkdir()
            for suffix in ("", "-wal", "-shm"):
                source = Path(str(db) + suffix)
                if source.exists():
                    shutil.copyfile(source, snapshot / ("kpkn.db" + suffix))
            before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in snapshot.iterdir()}
            summary = ri.inspect_database(snapshot / "kpkn.db")
            after = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in snapshot.iterdir()}
            connection.close()
        self.assertEqual(summary["counts"]["ongoing_workout"], 1)  # row only present in the WAL
        self.assertEqual(before, after)

    def test_cardio_keys(self):
        ongoing = {"programId": "P", "sessionId": "S", "startTimeMs": 5}
        key0, key1 = ri.expected_gps_session_key(ongoing, 0), ri.expected_gps_session_key(ongoing, 1)
        self.assertEqual(key0, f"P::S::5::{SQUAT}::qa-cardio-series-0")
        self.assertNotEqual(key0, key1)
        self.assertEqual(ri.gps_snapshot_relative_path(key0), f"files/cardio-gps/{hashlib.sha256(key0.encode()).hexdigest()}.json")
        self.assertTrue(ri.gps_snapshot_relative_path(key0, True).endswith(".json.pending"))
        with self.assertRaises(ValueError):
            ri.expected_gps_session_key({"programId": "P"}, 0)


class MigrationFixtureTests(unittest.TestCase):
    def test_real_fixture_satisfies_the_contract(self):
        facts = mig.validate_fixture(FIXTURE)
        self.assertEqual(facts["counts"], mig.EXPECTED_BEFORE_COUNTS)
        self.assertEqual(set(facts["logIds"]), mig.EXPECTED_LOG_IDS)

    def test_fixture_registry_hash_is_verified(self):
        record = fixtures.verify_fixture("migration-retention-v27")
        self.assertEqual(record["sha256"], hashlib.sha256(FIXTURE.read_bytes()).hexdigest())
        fixtures.verify_prefs()

    def test_broken_fixtures_are_missing_inputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            empty = make_db(Path(tmp) / "empty.db", version=27)
            with self.assertRaises(MissingInput):
                mig.validate_fixture(empty)  # zero counts: "non-empty retention" not evidenced
            v28 = Path(tmp) / "v28.db"
            shutil.copyfile(FIXTURE, v28)
            connection = sqlite3.connect(v28)
            connection.execute("PRAGMA user_version=28")
            connection.commit()
            connection.close()
            with self.assertRaises(MissingInput):
                mig.validate_fixture(v28)

    def test_baseline_discovery_lists_what_it_looked_for(self):
        with tempfile.TemporaryDirectory() as tmp:
            present = Path(tmp) / "b.apk"
            present.write_bytes(b"x")
            self.assertEqual(mig.discover_baseline(present)[1], "explicit --baseline-apk")
            self.assertEqual(mig.discover_baseline(None, [("first", Path(tmp) / "nope.apk"), ("second", present)]), (present, "second"))
            with self.assertRaises(MissingInput) as ctx:
                mig.discover_baseline(None, [("only", Path(tmp) / "nope.apk")])
            self.assertIn("nope.apk", str(ctx.exception))
            with self.assertRaises(MissingInput):
                mig.discover_baseline(Path(tmp) / "absent.apk")


def migrated_copy(destination: Path, *, association=("qa-migration-valid", "repair-history-0"), drop_media=None, normalize=True) -> Path:
    """Simulate what Room 27->28 should produce from the real fixture."""
    shutil.copyfile(FIXTURE, destination)
    connection = sqlite3.connect(destination)
    connection.execute("CREATE TABLE workout_media_session_associations(sessionKey TEXT NOT NULL PRIMARY KEY, workoutLogId TEXT NOT NULL)")
    if association:
        connection.execute("INSERT INTO workout_media_session_associations VALUES (?,?)", association)
    if drop_media:
        connection.execute('DELETE FROM workout_media WHERE id=?', (drop_media,))
    if normalize:
        for log_id, raw in connection.execute('SELECT id,data FROM workout_logs').fetchall():
            data = json.loads(raw)
            for exercise in data["completedExercises"]:
                exercise["canonicalExerciseId"] = exercise.get("exerciseDbId")
            connection.execute('UPDATE workout_logs SET data=? WHERE id=?', (json.dumps(data), log_id))
    connection.execute("PRAGMA user_version=28")
    connection.commit()
    connection.close()
    return destination


class StrictMigrationVerifierTests(unittest.TestCase):
    def run_verify(self, tmp, after):
        comparison = mig.normalized_before_copy(FIXTURE, mig.raw_log_cells(after), Path(tmp) / "before-normalized.db")
        return rv.verify(comparison, after, "MIGRATION").as_json()

    def test_correct_migration_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            after = migrated_copy(Path(tmp) / "after.db")
            report = self.run_verify(tmp, after)
        self.assertEqual(report["result"], "PASS", [c for c in report["checks"] if c["status"] != "PASS"])

    def test_wrong_or_extra_association_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            for association in (("qa-migration-ambiguous", "repair-history-0"), ("qa-migration-valid", "repair-history-1"), None):
                after = migrated_copy(Path(tmp) / f"after-{association}.db".replace("'", ""), association=association)
                report = self.run_verify(tmp, after)
                self.assertEqual(report["result"], "FAIL", association)

    def test_lost_media_row_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            after = migrated_copy(Path(tmp) / "after.db", drop_media="qa-migration-valid-b")
            self.assertEqual(self.run_verify(tmp, after)["result"], "FAIL")

    def test_same_version_pair_is_incomplete_not_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = rv.verify(FIXTURE, FIXTURE, "MIGRATION").as_json()
        self.assertEqual(report["result"], "INCOMPLETE")


class LogSemanticsTests(unittest.TestCase):
    BASE = {"id": "L", "programId": "P", "sessionId": "S", "sessionName": "Día 1", "date": "2023-01-01T00:00:00Z",
            "durationMinutes": 40, "totalVolume": 1200.0,
            "completedExercises": [{"exerciseId": "e", "exerciseName": "Sentadilla", "exerciseDbId": "db",
                                    "sets": [{"id": "s0", "weight": 20, "reps": 6}, {"id": "s1", "weight": 22.5, "reps": 6}]}]}

    def normalized(self):
        data = json.loads(json.dumps(self.BASE))
        data["completedExercises"][0]["canonicalExerciseId"] = "db"
        data["completedExercises"][0]["sets"][0]["weight"] = 20.0  # int -> float is not a loss
        return data

    def test_typed_normalization_is_accepted(self):
        problem, info = mig.log_semantic_problem(self.BASE, self.normalized())
        self.assertIsNone(problem)
        self.assertTrue(info["canonicalExerciseIds"][0]["equalsExerciseDbId"])

    def test_lost_or_changed_sets_are_rejected(self):
        for mutate in (lambda d: d["completedExercises"][0]["sets"].pop(),
                       lambda d: d["completedExercises"][0]["sets"][1].update(weight=25),
                       lambda d: d["completedExercises"][0]["sets"][0].update(reps=5),
                       lambda d: d["completedExercises"][0]["sets"][0].update(id="other"),
                       lambda d: d["completedExercises"].clear(),
                       lambda d: d.update(totalVolume=1.0),
                       lambda d: d["completedExercises"][0].update(exerciseDbId="changed")):
            broken = self.normalized()
            mutate(broken)
            problem, _ = mig.log_semantic_problem(self.BASE, broken)
            self.assertIsNotNone(problem)

    def test_real_fixture_logs_are_comparable_to_the_golden(self):
        golden = json.loads(Path(fixtures.fixture_path("migration-log-golden")).read_text(encoding="utf-8"))["rows"]
        old = mig.read_log_rows(FIXTURE)
        for log_id, raw in golden.items():
            problem, info = mig.log_semantic_problem(old[log_id], json.loads(raw))
            self.assertIsNone(problem, log_id)


class IdentityHashTests(unittest.TestCase):
    def test_schema_hash_and_db_hash(self):
        with tempfile.TemporaryDirectory() as tmp:
            schema = Path(tmp) / "28.json"
            schema.write_text(json.dumps({"database": {"identityHash": "abc123"}}))
            self.assertEqual(mig.schema_identity_hash(schema), "abc123")
            db = Path(tmp) / "kpkn.db"
            connection = sqlite3.connect(db)
            connection.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            connection.execute("INSERT INTO room_master_table VALUES (42, 'abc123')")
            connection.commit()
            connection.close()
            self.assertEqual(mig.db_identity_hash(db), "abc123")


if __name__ == "__main__":
    unittest.main()
