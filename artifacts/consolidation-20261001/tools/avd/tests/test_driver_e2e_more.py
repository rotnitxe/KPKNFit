"""More offline end-to-end driver runs: cardio, cardio cancel, media import, in-place migration."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import migrate_27_28  # noqa: E402
import ui_cancel_workout  # noqa: E402
import ui_cardio_two_series  # noqa: E402
import ui_media_import  # noqa: E402
from test_apk_evidence import build_axml, make_apk  # noqa: E402
from test_driver_e2e import E2EBase  # noqa: E402


class CardioCancelE2E(E2EBase):
    def test_cardio_cancel_stops_service_and_clears_snapshots(self):
        code, result = self.run_driver("cancel-cardio", lambda s: ui_cancel_workout.run(s, "cardio"))
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
        steps = self.statuses(result)
        self.assertEqual(steps["gps_snapshot_files_present_before_cancel"], "PASS")
        self.assertEqual(steps["no_partial_log_or_media"], "PASS")

    def test_snapshot_that_survives_cancel_is_detected(self):
        original = self.device.app._abandon

        def abandon_but_keep_snapshots():
            keep = {k: v for k, v in self.device.fs_app.items() if k.startswith("files/cardio-gps/")}
            original()
            self.device.fs_app.update(keep)

        self.device.app._abandon = abandon_but_keep_snapshots
        code, result = self.run_driver("cancel-cardio", lambda s: ui_cancel_workout.run(s, "cardio"))
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["no_partial_log_or_media"], "FAIL")


class CardioE2E(E2EBase):
    def test_two_series_flow(self):
        code, result = self.run_driver("cardio-two-series", ui_cardio_two_series.run)
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] != "PASS"])
        steps = self.statuses(result)
        for name in ("gps_snapshot_s1_present", "room_checkpoint_paused_s1", "room_checkpoint_after_s1", "gps_snapshot_s2_distinct_key",
                     "room_checkpoint_after_s2_reopened"):
            self.assertEqual(steps[name], "PASS", name)

    def test_series_switch_while_running_is_a_failed_gate(self):
        original = self.device.app._cardio_tap

        def switching(key):
            if key == "chip1":
                self.device.app.active_series = 1  # buggy product: tapping Serie 2 abandons S1
            original(key)

        self.device.app._cardio_tap = switching
        code, result = self.run_driver("cardio-two-series", ui_cardio_two_series.run)
        self.assertEqual(code, 1)


class MediaImportE2E(E2EBase):
    def test_import_late_and_finish(self):
        code, result = self.run_driver("media-import", ui_media_import.run)
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] not in ("PASS",)])
        steps = self.statuses(result)
        self.assertEqual(steps["room_post_finish_binding"], "PASS")
        self.assertEqual(steps["finalized_set_equals_ongoing_s1"], "PASS")
        self.assertEqual(steps["late_ingest_after_finish_instrumented_only"], "NOT_RUN")  # honest, optional

    def test_finish_that_does_not_bind_media_is_detected(self):
        original = self.device.app._finish

        def finish_without_binding():
            original()
            connection = self.device.app._db()
            connection.execute("UPDATE workout_media SET workoutLogId=NULL")
            self.device.app._commit(connection)

        self.device.app._finish = finish_without_binding
        code, result = self.run_driver("media-import", ui_media_import.run)
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["room_post_finish_binding"], "FAIL")


class MigrationE2E(E2EBase):
    def setUp(self):
        super().setUp()
        self.baseline = make_apk(Path(self.tmp.name), "kpkn-base-debug.apk", build_axml([
            ("manifest", {"package": "com.example.kpkn", "versionCode": 34, "versionName": "KPKN Beta 15"}),
            ("application", {"debuggable": True}), ("marker", {"name": "baseline"})]))
        self.device.apk_sha = "f" * 64

    def run_migration(self, **extra):
        options = dict(baseline_apk=self.baseline, validate_only=False, probe_baseline=False, check_signers=False,
                       launch_timeout=20.0)
        options.update(extra)
        return self.run_driver("migration-27-28", lambda s: migrate_27_28.run(s, s.args), **options)

    def test_in_place_update_migrates_and_everything_is_compared(self):
        code, result = self.run_migration()
        self.assertEqual(code, 0, [(s["name"], s["detail"]) for s in result["steps"] if s["status"] not in ("PASS",)])
        steps = self.statuses(result)
        for name in ("data_survives_inplace_update", "room_identity_hash_matches_schema_28", "workout_logs_semantic_retention",
                     "media_associations_exact", "strict_verifier_migration", "wal_before_after_comparison",
                     "installed_apk_is_integrated_build"):
            self.assertEqual(steps[name], "PASS", name)
        self.assertEqual(steps["baseline_probe_keeps_v27"], "NOT_RUN")
        self.assertEqual([i["flags"] for i in self.device.installs], [["-r", "-d"], ["-r"]])
        last_clear = max(i for i, line in enumerate(self.device.log) if "pm clear" in line)
        update = max(i for i, line in enumerate(self.device.log) if " install -r " in line)
        self.assertGreater(update, last_clear, "the integrated APK must be installed over seeded data, never before a wipe")
        self.assertFalse(any(" uninstall " in line for line in self.device.log))

    def test_missing_baseline_is_a_clear_missing_input_and_touches_nothing(self):
        code, result = self.run_migration(baseline_apk=Path(self.tmp.name) / "absent.apk")
        self.assertEqual(code, 4)
        self.assertEqual(result["reason"], "MISSING_INPUT")
        self.assertFalse(any("install" in line or "pm clear" in line for line in self.device.log))

    def test_migration_that_loses_a_media_association_fails(self):
        original = self.device.app._migrate

        def lossy(connection):
            original(connection)
            connection.execute("DELETE FROM workout_media_session_associations")

        self.device.app._migrate = lossy
        code, result = self.run_migration()
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["after_snapshot_v28"], "FAIL")

    def test_migration_that_drops_a_set_fails_the_semantic_check(self):
        original = self.device.app._migrate

        def dropping(connection):
            original(connection)
            for log_id, raw in connection.execute("SELECT id, data FROM workout_logs").fetchall():
                data = json.loads(raw)
                data["completedExercises"][0]["sets"].pop()
                connection.execute("UPDATE workout_logs SET data=? WHERE id=?", (json.dumps(data), log_id))

        self.device.app._migrate = dropping
        code, result = self.run_migration()
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["workout_logs_semantic_retention"], "FAIL")

    def test_wrong_identity_hash_fails(self):
        original = self.device.app._migrate

        def wrong_hash(connection):
            original(connection)
            connection.execute("UPDATE room_master_table SET identity_hash='deadbeef'")

        self.device.app._migrate = wrong_hash
        code, result = self.run_migration()
        self.assertEqual(code, 1)
        self.assertEqual(self.statuses(result)["room_identity_hash_matches_schema_28"], "FAIL")

    def test_validate_only_never_touches_the_device(self):
        self.avd.offline = True
        code, result = self.run_migration(validate_only=True)
        self.assertEqual(code, 0)
        self.assertEqual(len(result["steps"]), 1)
        self.assertEqual(self.device.log, [])


if __name__ == "__main__":
    unittest.main()
