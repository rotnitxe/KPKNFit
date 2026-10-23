#!/usr/bin/env python3
"""Synthetic-only contract tests for competitive_acceptance.py."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any
from unittest.mock import patch

sys.dont_write_bytecode = True

SCRIPTS = Path(__file__).resolve().parents[1]
MODULE = SCRIPTS / "video_technique" / "competitive_acceptance.py"
SPEC = importlib.util.spec_from_file_location("competitive_acceptance", MODULE)
if SPEC is None or SPEC.loader is None:  # pragma: no cover - defensive
    raise RuntimeError(f"cannot load {MODULE}")
acceptance = importlib.util.module_from_spec(SPEC)
sys.modules["competitive_acceptance"] = acceptance
SPEC.loader.exec_module(acceptance)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def event(
    event_id: str,
    kind: str,
    start: float,
    end: float,
    role: str,
    metrics: dict[str, Any] | None = None,
    phase_scheme_id: str | None = None,
) -> dict[str, Any]:
    value = {
        "event_id": event_id,
        "kind": kind,
        "event_source": "independent_annotation" if role == "truth" else "engine_output",
        "start_s": start,
        "end_s": end,
        "metrics": metrics or {},
    }
    if phase_scheme_id is not None:
        value["phase_scheme_id"] = phase_scheme_id
    return value


def row(record_id: str, role: str, events: list[dict[str, Any]], **overrides: Any) -> dict[str, Any]:
    value: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "synthetic",
        "record_id": record_id,
        "participant_id": "person_anon_001",
        "session_id": "session_anon_001",
        "exercise_id": "back_squat",
        "condition_id": "lateral_good",
        "status": "observed" if role == "truth" else "predicted",
        "events": events,
    }
    if role == "prediction":
        value.update({"confidence_band": "ALTA", "safe_claim": False})
    value.update(overrides)
    return value


def fixture(
    truth: list[dict[str, Any]], predictions: list[dict[str, Any]], *, phase_ids: list[str] | None = None,
    boundary_ids: list[str] | None = None, tut_phase_ids: list[str] | None = None,
) -> tuple[dict[str, Any], str, str, str]:
    truth_bytes = ("\n".join(json.dumps(record, sort_keys=True) for record in truth) + "\n").encode()
    predictions_bytes = ("\n".join(json.dumps(record, sort_keys=True) for record in predictions) + "\n").encode()
    truth_sha = digest(truth_bytes)
    predictions_sha = digest(predictions_bytes)
    phase_scheme_id = "squat_profile" if phase_ids is not None else None
    manifest: dict[str, Any] = {
        "schema_version": 1,
        "benchmark_id": "synthetic_contract_test",
        "evidence_class": "synthetic",
        "inputs": {"truth_sha256": truth_sha, "predictions_sha256": predictions_sha},
        "privacy": {"pseudonymous": True, "direct_identifiers": False},
        "provenance": {
            "truth": {
                "method_id": "synthetic_annotation",
                "protocol_id": "synthetic_protocol",
                "protocol_sha256": "a" * 64,
                "independent": True,
            },
            "prediction": {
                "model_id": "test_model",
                "model_version": "test_version",
                "engine_version": "test_engine",
                "apk_sha256": "b" * 64,
                "device_model": "test_device",
                "device_os": "test_os",
                "capture_setup_id": "test_setup",
            },
        },
        "exercise_matrix": {
            "back_squat": {
                "class": "basic",
                "conditions": {
                    "lateral_good": {
                        "capture_quality": "good",
                        "disk_calibrated": False,
                        "phase_scheme_id": phase_scheme_id,
                    }
                },
            }
        },
        "phase_schemes": {},
        "statistics": {
            "cluster_unit": "participant_id",
            "nested_unit": "session_id",
            "method": "paired_person_cluster_bootstrap",
            "sample_size_plan_sha256": "c" * 64,
        },
    }
    if phase_scheme_id:
        manifest["phase_schemes"][phase_scheme_id] = {
            "phase_ids": phase_ids,
            "boundary_ids": boundary_ids or [],
        }
        if tut_phase_ids is not None:
            manifest["phase_schemes"][phase_scheme_id]["tut_phase_ids"] = tut_phase_ids
    manifest_bytes = json.dumps(manifest, sort_keys=True).encode()
    manifest_sha = digest(manifest_bytes)
    return manifest, manifest_sha, truth_sha, predictions_sha


class CompetitiveAcceptanceTests(unittest.TestCase):
    def evaluate(self, truth: list[dict[str, Any]], predictions: list[dict[str, Any]], **kwargs: Any) -> dict[str, Any]:
        manifest, manifest_sha, truth_sha, predictions_sha = fixture(truth, predictions, **kwargs)
        return acceptance.evaluate_records(
            manifest,
            truth,
            predictions,
            manifest_sha256=manifest_sha,
            truth_sha256=truth_sha,
            predictions_sha256=predictions_sha,
        )

    def test_event_matching_is_one_to_one_and_missing_rows_are_non_exact(self) -> None:
        truth = [
            row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth")]),
            row("set_002", "truth", [event("t2", "full", 5.0, 6.0, "truth")]),
        ]
        predictions = [
            row("set_001", "prediction", [
                event("p1", "full", 1.1, 2.1, "prediction"),
                event("p_duplicate", "full", 1.2, 2.2, "prediction"),
            ]),
        ]

        report = self.evaluate(truth, predictions)["groups"][0]

        self.assertEqual(1, report["events"]["full"]["matched_one_to_one"])
        self.assertEqual(1, report["events"]["full"]["false_positives"])
        self.assertEqual(1, report["events"]["full"]["false_negatives"])
        self.assertEqual(0.0, report["exact_series_rate"])
        self.assertEqual(1, report["missing_prediction_records"])
        self.assertEqual(1, report["count_mae_paired_n"])
        self.assertEqual("INCOMPLETE", report["gates"]["count_mae"]["status"])
        self.assertEqual(1.0, report["count_mae_paired_sets"])
        self.assertEqual("INCOMPLETE", self.evaluate(truth, predictions)["overall"])

    def test_abstentions_and_errors_stay_in_series_denominator(self) -> None:
        truth = [row(f"set_00{i}", "truth", [event(f"t{i}", "full", float(i), float(i) + 1.0, "truth")]) for i in (1, 2)]
        predictions = [
            row("set_001", "prediction", [], status="abstain", confidence_band=None),
            row("set_002", "prediction", [], status="error", confidence_band=None),
        ]
        report = self.evaluate(truth, predictions)["groups"][0]
        self.assertEqual(2, report["sets"])
        self.assertEqual(0.0, report["exact_series_rate"])
        self.assertEqual(2, report["abstention_records"] + report["error_records"])
        self.assertEqual(0, report["count_mae_paired_n"])

    def test_exact_series_includes_partial_event_count(self) -> None:
        truth = [row("set_001", "truth", [
            event("t_full", "full", 1.0, 2.0, "truth"),
            event("t_partial", "partial", 3.0, 4.0, "truth"),
        ])]
        predictions = [row("set_001", "prediction", [event("p_full", "full", 1.0, 2.0, "prediction")])]

        report = self.evaluate(truth, predictions)["groups"][0]

        self.assertEqual(1.0, report["exact_full_series_rate"])
        self.assertEqual(0.0, report["exact_series_rate"])
        self.assertEqual(1, report["truth_partial_events"])
        self.assertEqual(0, report["predicted_partial_events"])
        self.assertEqual(0.0, report["safe_subset"]["exact_series_rate"])
        self.assertEqual(1, report["safe_subset"]["alta_sets_including_safe_claim_false"])
        self.assertEqual(1, report["events"]["partial"]["false_negatives"])

    def test_prediction_without_truth_is_rejected(self) -> None:
        truth = [row("set_001", "truth", [])]
        predictions = [row("other_set", "prediction", [])]
        manifest, manifest_sha, truth_sha, predictions_sha = fixture(truth, predictions)
        with self.assertRaisesRegex(acceptance.AcceptanceError, "without truth"):
            acceptance.evaluate_records(manifest, truth, predictions, manifest_sha256=manifest_sha, truth_sha256=truth_sha, predictions_sha256=predictions_sha)

    def test_alta_is_in_safe_denominator_even_without_safe_claim(self) -> None:
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth")])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction")], safe_claim=False)]
        report = self.evaluate(truth, predictions)["groups"][0]
        self.assertEqual(1, report["safe_subset"]["alta_sets_including_safe_claim_false"])

        bad_claim = row("set_001", "prediction", [], confidence_band="MEDIA", safe_claim=True)
        manifest, manifest_sha, truth_sha, predictions_sha = fixture(truth, [bad_claim])
        with self.assertRaisesRegex(acceptance.AcceptanceError, "safe_claim requires"):
            acceptance.evaluate_records(manifest, truth, [bad_claim], manifest_sha256=manifest_sha, truth_sha256=truth_sha, predictions_sha256=predictions_sha)

    def test_precomputed_tut_and_vl_are_rejected(self) -> None:
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth")])]
        bad_metrics = {"tut_s": {"available": True, "value": 1.0, "source": "engine_output"}}
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction", bad_metrics)])]
        manifest, manifest_sha, truth_sha, predictions_sha = fixture(truth, predictions)
        with self.assertRaisesRegex(acceptance.AcceptanceError, "precomputed metrics"):
            acceptance.evaluate_records(manifest, truth, predictions, manifest_sha256=manifest_sha, truth_sha256=truth_sha, predictions_sha256=predictions_sha)

    def test_unavailable_fallback_phases_cannot_pass_phase_or_tut_gate(self) -> None:
        phase_ids = ["depart_s", "far_turn_s", "return_s"]
        boundary_ids = ["far_turn_start_s", "return_start_s"]
        fallback = {"available": False, "value": None, "source": "fallback_segmenter_anchor"}
        phase_metrics = {
            "phase_durations_s": {phase_id: fallback for phase_id in phase_ids},
            "phase_boundaries_s": {boundary_id: fallback for boundary_id in boundary_ids},
        }
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth", phase_metrics, "squat_profile")])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction", phase_metrics, "squat_profile")])]
        report = self.evaluate(truth, predictions, phase_ids=phase_ids, boundary_ids=boundary_ids)["groups"][0]
        self.assertEqual("INCOMPLETE", report["physical_metrics"]["tut"]["gate"]["status"])
        self.assertEqual("INCOMPLETE", report["gates"]["physical"])

    def test_physical_truth_needs_manifest_bound_reference(self) -> None:
        metric = {"available": True, "value": 0.42, "source": "reference_instrument"}
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth", {"mcv_mps": metric})])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction", {"mcv_mps": {"available": True, "value": 0.42, "source": "engine_output"}})])]
        manifest, manifest_sha, truth_sha, predictions_sha = fixture(truth, predictions)
        with self.assertRaisesRegex(acceptance.AcceptanceError, "manifest-bound reference instrument"):
            acceptance.evaluate_records(manifest, truth, predictions, manifest_sha256=manifest_sha, truth_sha256=truth_sha, predictions_sha256=predictions_sha)

    def test_velocity_loss_zero_denominators_make_gate_incomplete(self) -> None:
        def mcv_values(record_id: str, role: str, values: list[float]) -> dict[str, Any]:
            return row(record_id, role, [
                event(
                    f"{record_id}_{index}", "full", float(index), float(index) + 0.8, role,
                    {"mcv_mps": {"available": True, "value": value, "source": "reference_instrument" if role == "truth" else "engine_output"}},
                )
                for index, value in enumerate(values, 1)
            ])

        truth = [
            mcv_values("set_valid", "truth", [0.5, 0.4]),
            mcv_values("set_truth_zero", "truth", [0.0, 0.0]),
            mcv_values("set_pred_zero", "truth", [0.5, 0.4]),
        ]
        predictions = [
            mcv_values("set_valid", "prediction", [0.5, 0.4]),
            mcv_values("set_truth_zero", "prediction", [0.3, 0.2]),
            mcv_values("set_pred_zero", "prediction", [0.0, 0.0]),
        ]
        manifest, _, truth_sha, predictions_sha = fixture(truth, predictions)
        condition = manifest["exercise_matrix"]["back_squat"]["conditions"]["lateral_good"]
        condition["disk_calibrated"] = True
        manifest["provenance"]["physical_references"] = {
            "mcv_mps": {
                "instrument_id": "test_lpt",
                "calibration_id": "cal_v1",
                "calibration_sha256": "d" * 64,
                "protocol_id": "velocity_protocol",
                "protocol_sha256": "e" * 64,
                "unit": "m/s",
            }
        }
        manifest_sha = digest(json.dumps(manifest, sort_keys=True).encode())

        group = acceptance.evaluate_records(
            manifest, truth, predictions, manifest_sha256=manifest_sha,
            truth_sha256=truth_sha, predictions_sha256=predictions_sha,
        )["groups"][0]
        vl = group["physical_metrics"]["velocity_loss_pct"]

        self.assertEqual(1, vl["eligible_sets"])
        self.assertEqual(2, vl["invalid_zero_denominator_sets"])
        self.assertEqual("INCOMPLETE", vl["gate"]["status"])
        self.assertIn("zero or negative MCV denominator", vl["gate"]["reason"])

    def test_tut_requires_explicit_phase_subset_and_validates_subset(self) -> None:
        phase_ids = ["eccentric_s", "rest_pause_s", "concentric_s"]
        boundary_ids = ["turn_start_s", "turn_end_s"]
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth", {}, "squat_profile")])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction", {}, "squat_profile")])]

        report = self.evaluate(truth, predictions, phase_ids=phase_ids, boundary_ids=boundary_ids)["groups"][0]
        self.assertEqual("INCOMPLETE", report["physical_metrics"]["tut"]["gate"]["status"])
        self.assertIn("no explicit tut_phase_ids", report["physical_metrics"]["tut"]["gate"]["reason"])

        manifest, manifest_sha, truth_sha, predictions_sha = fixture(
            truth, predictions, phase_ids=phase_ids, boundary_ids=boundary_ids, tut_phase_ids=["rest_pause_s", "outside_s"],
        )
        with self.assertRaisesRegex(acceptance.AcceptanceError, "tut_phase_ids must be a subset"):
            acceptance.evaluate_records(
                manifest, truth, predictions, manifest_sha256=manifest_sha,
                truth_sha256=truth_sha, predictions_sha256=predictions_sha,
            )

    def test_tut_limit_applies_per_set_and_excludes_rest_phase(self) -> None:
        phase_ids = ["eccentric_s", "rest_pause_s", "concentric_s"]
        boundary_ids = ["turn_start_s", "turn_end_s"]
        tut_phase_ids = ["eccentric_s", "concentric_s"]

        def metrics(role: str, eccentric: float, rest: float, concentric: float) -> dict[str, Any]:
            source = "independent_annotation" if role == "truth" else "engine_output"
            phase_durations = {
                phase_id: {"available": True, "value": value, "source": source}
                for phase_id, value in (
                    ("eccentric_s", eccentric),
                    ("rest_pause_s", rest),
                    ("concentric_s", concentric),
                )
            }
            phase_boundaries = {
                boundary_id: {"available": True, "value": value, "source": source}
                for boundary_id, value in (("turn_start_s", 0.25), ("turn_end_s", 0.75))
            }
            return {"phase_durations_s": phase_durations, "phase_boundaries_s": phase_boundaries}

        short_truth_events: list[dict[str, Any]] = []
        short_prediction_events: list[dict[str, Any]] = []
        long_truth_events: list[dict[str, Any]] = []
        long_prediction_events: list[dict[str, Any]] = []
        for index in range(20):
            start = float(index * 2)
            short_truth_events.append(event(f"short_t_{index}", "full", start, start + 1.0, "truth", metrics("truth", 0.05, 0.9, 0.05), "squat_profile"))
            short_prediction_events.append(event(f"short_p_{index}", "full", start, start + 1.0, "prediction", metrics("prediction", 0.1, 0.9, 0.1), "squat_profile"))
        for index in range(10):
            start = float(100 + index * 13)
            long_truth_events.append(event(f"long_t_{index}", "full", start, start + 12.0, "truth", metrics("truth", 4.0, 1.0, 6.0), "squat_profile"))
            long_prediction_events.append(event(f"long_p_{index}", "full", start, start + 12.0, "prediction", metrics("prediction", 4.0, 1.0, 6.0), "squat_profile"))
        truth = [row("set_short", "truth", short_truth_events), row("set_long", "truth", long_truth_events)]
        predictions = [row("set_short", "prediction", short_prediction_events), row("set_long", "prediction", long_prediction_events)]

        report = self.evaluate(
            truth, predictions, phase_ids=phase_ids, boundary_ids=boundary_ids, tut_phase_ids=tut_phase_ids,
        )["groups"][0]
        tut = report["physical_metrics"]["tut"]
        per_set = {detail["record_id"]: detail for detail in tut["per_set_errors"]}

        self.assertAlmostEqual(51.0, tut["mean_reference_set_tut_s"])
        self.assertAlmostEqual(2.0, per_set["set_short"]["reference_s"])
        self.assertAlmostEqual(2.0, per_set["set_short"]["absolute_error_s"])
        self.assertGreater(per_set["set_short"]["absolute_error_s"], per_set["set_short"]["limit_s"])
        self.assertEqual(0.0, per_set["set_long"]["absolute_error_s"])
        self.assertEqual(2.0, tut["per_set_worst_normalized_error"])
        self.assertEqual("INCOMPLETE", tut["gate"]["status"])
        self.assertIn("synthetic/testdata", tut["gate"]["reason"])

    def test_zero_event_denominators_are_not_estimable(self) -> None:
        truth = [row("set_001", "truth", [])]
        predictions = [row("set_001", "prediction", [])]
        report = self.evaluate(truth, predictions)["groups"][0]
        self.assertEqual("NOT_ESTIMABLE", report["events"]["partial"]["precision_gate"]["status"])
        self.assertEqual("NOT_ESTIMABLE", report["events"]["partial"]["recall_gate"]["status"])

    def test_synthetic_evidence_never_passes_competitive_gates(self) -> None:
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth")])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction")])]
        report = self.evaluate(truth, predictions)
        self.assertEqual("INCOMPLETE", report["overall"])
        self.assertEqual("INCOMPLETE", report["provenance_gate"]["status"])
        self.assertNotEqual("PASS", report["groups"][0]["gates"]["series_exactness"]["status"])

    def test_cli_file_hashes_are_explicit_and_report_never_overwrites(self) -> None:
        truth = [row("set_001", "truth", [])]
        predictions = [row("set_001", "prediction", [])]
        manifest, _, _, _ = fixture(truth, predictions)
        truth_bytes = (json.dumps(truth[0], sort_keys=True) + "\n").encode()
        predictions_bytes = (json.dumps(predictions[0], sort_keys=True) + "\n").encode()
        manifest["inputs"] = {"truth_sha256": digest(truth_bytes), "predictions_sha256": digest(predictions_bytes)}
        manifest_bytes = json.dumps(manifest, sort_keys=True).encode()
        with tempfile.TemporaryDirectory(dir=SCRIPTS) as temp:
            root = Path(temp)
            manifest_path = root / "manifest.json"
            truth_path = root / "truth.jsonl"
            predictions_path = root / "predictions.jsonl"
            out_path = root / "report.json"
            manifest_path.write_bytes(manifest_bytes)
            truth_path.write_bytes(truth_bytes)
            predictions_path.write_bytes(predictions_bytes)
            report = acceptance.evaluate_files(manifest_path, truth_path, predictions_path, digest(manifest_bytes), digest(truth_bytes), digest(predictions_bytes))
            self.assertEqual("INCOMPLETE", report["overall"])
            with self.assertRaisesRegex(acceptance.AcceptanceError, "SHA-256 mismatch"):
                acceptance.evaluate_files(manifest_path, truth_path, predictions_path, "0" * 64, digest(truth_bytes), digest(predictions_bytes))
            acceptance._write_new_report(out_path, report)
            with self.assertRaisesRegex(acceptance.AcceptanceError, "refusing to overwrite"):
                acceptance._write_new_report(out_path, report)

    def test_evaluate_files_hashes_and_parses_same_byte_snapshot(self) -> None:
        truth = [row("set_001", "truth", [event("t1", "full", 1.0, 2.0, "truth")])]
        predictions = [row("set_001", "prediction", [event("p1", "full", 1.0, 2.0, "prediction")])]
        manifest, _, _, _ = fixture(truth, predictions)
        truth_bytes = (json.dumps(truth[0], sort_keys=True) + "\n").encode()
        predictions_bytes = (json.dumps(predictions[0], sort_keys=True) + "\n").encode()
        manifest["inputs"] = {"truth_sha256": digest(truth_bytes), "predictions_sha256": digest(predictions_bytes)}
        manifest_bytes = json.dumps(manifest, sort_keys=True).encode()
        changed_truth = json.dumps(row("set_001", "truth", [event("t1", "partial", 1.0, 2.0, "truth")]), sort_keys=True).encode() + b"\n"
        with tempfile.TemporaryDirectory(dir=SCRIPTS) as temp:
            root = Path(temp)
            manifest_path = root / "manifest.json"
            truth_path = root / "truth.jsonl"
            predictions_path = root / "predictions.jsonl"
            manifest_path.write_bytes(manifest_bytes)
            truth_path.write_bytes(truth_bytes)
            predictions_path.write_bytes(predictions_bytes)
            original_read_bytes = Path.read_bytes
            reads: dict[Path, int] = {}

            def read_bytes_and_mutate_truth(path: Path) -> bytes:
                data = original_read_bytes(path)
                reads[path] = reads.get(path, 0) + 1
                if path == truth_path:
                    truth_path.write_bytes(changed_truth)
                return data

            with patch.object(Path, "read_bytes", read_bytes_and_mutate_truth):
                report = acceptance.evaluate_files(
                    manifest_path, truth_path, predictions_path,
                    digest(manifest_bytes), digest(truth_bytes), digest(predictions_bytes),
                )

            group = report["groups"][0]
            self.assertEqual(1, reads[truth_path])
            self.assertEqual(1, group["truth_full_reps"])
            self.assertEqual(0, group["truth_partial_events"])


if __name__ == "__main__":
    unittest.main()
