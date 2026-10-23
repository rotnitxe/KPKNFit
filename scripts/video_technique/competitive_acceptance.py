#!/usr/bin/env python3
"""Fail-closed evaluator for preregistered video-technique acceptance data.

Inputs are raw JSONL truth/prediction records plus an independently pinned
manifest. This tool never reads benchmark banks, generates labels, or trusts
precomputed scores. Synthetic/testdata may exercise the evaluator, but can
never produce competitive PASS gates.

See ``SCHEMA.md`` in this directory for the normalized input contract.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
from collections import defaultdict
from pathlib import Path
from statistics import mean, stdev
from typing import Any, Iterable


SCHEMA_VERSION = 1
HASH_RE = re.compile(r"^[0-9a-fA-F]{64}$")
TOKEN_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$")
EMAIL_RE = re.compile(r"[^\s@]+@[^\s@]+\.[^\s@]+")
LONG_NUMBER_RE = re.compile(r"\d{8,}")
KINDS = ("full", "partial")
ALLOWED_EVIDENCE = ("external_real", "synthetic", "testdata")
PREDICTION_SOURCES = {"engine_output"}
TRUTH_ANNOTATION_SOURCES = {"independent_annotation"}
TRUTH_REFERENCE_SOURCES = {"reference_instrument"}
UNAVAILABLE_SOURCES = {
    "not_available",
    "not_measured",
    "not_annotated",
    "missing_reference",
    "fallback_segmenter_anchor",
    "unsupported_profile",
    "not_exported",
}
SCALAR_METRICS = {
    "mcv_mps",
    "peak_velocity_mps",
    "rom_vertical_cm",
    "partial_zone",
    "partial_amplitude_pct",
}
MAP_METRICS = {"phase_durations_s", "phase_boundaries_s"}
FORBIDDEN_PRECOMPUTED = {
    "tut_s",
    "tut_total_s",
    "vl_pct",
    "velocity_loss_pct",
    "precision",
    "recall",
    "exact_series_rate",
    "count_mae",
    "metrics_summary",
}


class AcceptanceError(ValueError):
    """Invalid or unpinned acceptance input."""


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _require_hash(value: Any, label: str) -> str:
    if not isinstance(value, str) or not HASH_RE.fullmatch(value):
        raise AcceptanceError(f"{label} must be an explicitly supplied SHA-256")
    return value.lower()


def verify_pinned_file(path: Path, expected_sha256: str, label: str) -> str:
    _, actual = _read_pinned_file(path, expected_sha256, label)
    return actual


def _read_pinned_file(path: Path, expected_sha256: str, label: str) -> tuple[bytes, str]:
    expected = _require_hash(expected_sha256, f"expected {label} hash")
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise AcceptanceError(f"cannot read {label}: {exc}") from exc
    actual = _sha256(data)
    if actual != expected:
        raise AcceptanceError(f"{label} SHA-256 mismatch: expected {expected}, got {actual}")
    return data, actual


def _load_json(data: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(data.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise AcceptanceError(f"cannot parse {label}: {exc}") from exc
    if not isinstance(value, dict):
        raise AcceptanceError(f"{label} root must be a JSON object")
    return value


def _load_jsonl(data: bytes, label: str) -> list[dict[str, Any]]:
    try:
        text = data.decode("utf-8")
    except UnicodeError as exc:
        raise AcceptanceError(f"cannot parse {label}: {exc}") from exc
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(text.splitlines(), 1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            raise AcceptanceError(f"{label}:{line_number}: invalid JSON: {exc}") from exc
        if not isinstance(value, dict):
            raise AcceptanceError(f"{label}:{line_number}: each row must be an object")
        rows.append(value)
    if not rows:
        raise AcceptanceError(f"{label} has no records")
    return rows


def _finite_number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise AcceptanceError(f"{label} must be a finite number")
    return float(value)


def _opaque_token(value: Any, label: str) -> str:
    if not isinstance(value, str) or not TOKEN_RE.fullmatch(value):
        raise AcceptanceError(f"{label} must be a short opaque token")
    if EMAIL_RE.search(value) or LONG_NUMBER_RE.search(value):
        raise AcceptanceError(f"{label} appears to contain direct identifying data")
    return value


def _manifest_integrity(
    manifest: dict[str, Any], truth_sha256: str, predictions_sha256: str
) -> list[str]:
    if manifest.get("schema_version") != SCHEMA_VERSION:
        raise AcceptanceError(f"manifest schema_version must be {SCHEMA_VERSION}")
    if manifest.get("evidence_class") not in ALLOWED_EVIDENCE:
        raise AcceptanceError("manifest evidence_class must be external_real, synthetic, or testdata")
    inputs = manifest.get("inputs")
    if not isinstance(inputs, dict):
        raise AcceptanceError("manifest.inputs is required")
    if not _is_sha(inputs.get("truth_sha256")) or inputs["truth_sha256"].lower() != truth_sha256:
        raise AcceptanceError("manifest truth_sha256 does not match pinned truth bytes")
    if not _is_sha(inputs.get("predictions_sha256")) or inputs["predictions_sha256"].lower() != predictions_sha256:
        raise AcceptanceError("manifest predictions_sha256 does not match pinned prediction bytes")

    issues: list[str] = []
    privacy = manifest.get("privacy")
    if not isinstance(privacy, dict) or privacy.get("pseudonymous") is not True or privacy.get("direct_identifiers") is not False:
        issues.append("privacy/anonymization attestation is incomplete")
    provenance = manifest.get("provenance")
    if not isinstance(provenance, dict):
        issues.append("provenance is missing")
    else:
        truth = provenance.get("truth")
        prediction = provenance.get("prediction")
        if not isinstance(truth, dict):
            issues.append("truth provenance is missing")
        else:
            if not truth.get("method_id") or not truth.get("protocol_id"):
                issues.append("truth method/protocol identity is missing")
            if not _is_sha(truth.get("protocol_sha256")):
                issues.append("truth protocol SHA-256 is missing")
            if truth.get("independent") is not True:
                issues.append("truth independence is not attested")
        if not isinstance(prediction, dict):
            issues.append("prediction provenance is missing")
        else:
            for key in ("model_id", "model_version", "engine_version", "apk_sha256", "device_model", "device_os", "capture_setup_id"):
                if not prediction.get(key):
                    issues.append(f"prediction provenance {key} is missing")
            if not _is_sha(prediction.get("apk_sha256")):
                issues.append("prediction APK SHA-256 is invalid")

    stats = manifest.get("statistics")
    if not isinstance(stats, dict):
        issues.append("cluster-statistics plan is missing")
    else:
        if stats.get("cluster_unit") != "participant_id" or stats.get("nested_unit") != "session_id":
            issues.append("cluster plan must resample participants with sessions nested")
        if stats.get("method") != "paired_person_cluster_bootstrap":
            issues.append("cluster method is not the preregistered paired-person bootstrap")
        if not _is_sha(stats.get("sample_size_plan_sha256")):
            issues.append("sample-size plan SHA-256 is missing")
    return issues


def _is_sha(value: Any) -> bool:
    return isinstance(value, str) and HASH_RE.fullmatch(value) is not None


def _exercise_condition(manifest: dict[str, Any], exercise_id: str, condition_id: str) -> dict[str, Any]:
    matrix = manifest.get("exercise_matrix")
    if not isinstance(matrix, dict) or exercise_id not in matrix:
        raise AcceptanceError(f"exercise {exercise_id!r} is absent from manifest.exercise_matrix")
    exercise = matrix[exercise_id]
    if not isinstance(exercise, dict) or exercise.get("class") not in {"basic", "other"}:
        raise AcceptanceError(f"exercise {exercise_id!r} must declare class basic or other")
    conditions = exercise.get("conditions")
    if not isinstance(conditions, dict) or condition_id not in conditions:
        raise AcceptanceError(f"condition {condition_id!r} for {exercise_id!r} is absent from manifest")
    condition = conditions[condition_id]
    if not isinstance(condition, dict) or condition.get("capture_quality") not in {"good", "adverse"}:
        raise AcceptanceError(f"condition {condition_id!r} must declare capture_quality good or adverse")
    if not isinstance(condition.get("disk_calibrated"), bool):
        raise AcceptanceError(f"condition {condition_id!r} must declare disk_calibrated")
    scheme_id = condition.get("phase_scheme_id")
    schemes = manifest.get("phase_schemes", {})
    if scheme_id is not None:
        if not isinstance(scheme_id, str) or not isinstance(schemes, dict) or scheme_id not in schemes:
            raise AcceptanceError(f"unknown phase scheme {scheme_id!r}")
        scheme = schemes[scheme_id]
        if not isinstance(scheme, dict) or not _valid_id_list(scheme.get("phase_ids")) or not _valid_id_list(scheme.get("boundary_ids")):
            raise AcceptanceError(f"phase scheme {scheme_id!r} has invalid phase_ids/boundary_ids")
        if "tut_phase_ids" in scheme and (
            not _valid_id_list(scheme["tut_phase_ids"])
            or not set(scheme["tut_phase_ids"]).issubset(scheme["phase_ids"])
        ):
            raise AcceptanceError(f"phase scheme {scheme_id!r} tut_phase_ids must be a subset of phase_ids")
    return exercise | {"condition": condition}


def _valid_id_list(value: Any) -> bool:
    return isinstance(value, list) and all(isinstance(item, str) and TOKEN_RE.fullmatch(item) for item in value) and len(value) == len(set(value))


def _measurement(
    item: Any,
    label: str,
    role: str,
    metric: str,
    manifest: dict[str, Any],
) -> tuple[bool, Any]:
    if not isinstance(item, dict) or not isinstance(item.get("available"), bool):
        raise AcceptanceError(f"{label} must be an available/value/source object")
    unknown = set(item) - {"available", "value", "source"}
    if unknown:
        raise AcceptanceError(f"{label} has unsupported measurement fields: {sorted(unknown)}")
    available = item["available"]
    value = item.get("value")
    source = item.get("source")
    if not isinstance(source, str) or not source:
        raise AcceptanceError(f"{label}.source is required")
    if not available:
        if value is not None:
            raise AcceptanceError(f"{label}: unavailable values must be null")
        if source not in UNAVAILABLE_SOURCES:
            raise AcceptanceError(f"{label}: unknown unavailable source {source!r}")
        return False, None

    if value is None:
        raise AcceptanceError(f"{label}: available values cannot be null")
    if metric == "partial_zone":
        value = _opaque_token(value, f"{label}.value")
    else:
        value = _finite_number(value, f"{label}.value")
        if metric in {"mcv_mps", "peak_velocity_mps", "rom_vertical_cm", "partial_amplitude_pct", "phase_durations_s", "phase_boundaries_s"} and value < 0:
            raise AcceptanceError(f"{label}.value cannot be negative")
        if metric == "partial_amplitude_pct" and value > 100:
            raise AcceptanceError(f"{label}.value must be at most 100 percent")

    if role == "prediction":
        if source not in PREDICTION_SOURCES:
            raise AcceptanceError(f"{label}: prediction source must be engine_output")
    elif metric in {"mcv_mps", "peak_velocity_mps", "rom_vertical_cm"}:
        if source not in TRUTH_REFERENCE_SOURCES or not _has_reference(manifest, metric):
            raise AcceptanceError(f"{label}: physical truth needs a manifest-bound reference instrument")
    elif source not in TRUTH_ANNOTATION_SOURCES:
        raise AcceptanceError(f"{label}: truth source must be independent_annotation")
    return True, value


def _has_reference(manifest: dict[str, Any], metric: str) -> bool:
    provenance = manifest.get("provenance")
    if not isinstance(provenance, dict):
        return False
    refs = provenance.get("physical_references", {})
    if not isinstance(refs, dict):
        return False
    ref = refs.get(metric)
    expected_unit = {
        "mcv_mps": "m/s",
        "peak_velocity_mps": "m/s",
        "rom_vertical_cm": "cm",
    }.get(metric)
    return bool(
        isinstance(ref, dict)
        and ref.get("instrument_id")
        and ref.get("calibration_id")
        and _is_sha(ref.get("calibration_sha256"))
        and ref.get("protocol_id")
        and _is_sha(ref.get("protocol_sha256"))
        and ref.get("unit") == expected_unit
    )


def _validate_measurement_map(
    raw: Any,
    label: str,
    role: str,
    metric: str,
    ids: list[str],
    manifest: dict[str, Any],
) -> dict[str, tuple[bool, Any]]:
    if not isinstance(raw, dict):
        raise AcceptanceError(f"{label} must be an object keyed by manifest phase IDs")
    unknown = set(raw) - set(ids)
    if unknown:
        raise AcceptanceError(f"{label} contains phase IDs not in the manifest: {sorted(unknown)}")
    validated: dict[str, tuple[bool, Any]] = {}
    for phase_id, item in raw.items():
        validated[phase_id] = _measurement(item, f"{label}.{phase_id}", role, metric, manifest)
    return validated


def _validate_event(
    event: Any,
    label: str,
    role: str,
    manifest: dict[str, Any],
    exercise_id: str,
    condition_id: str,
) -> dict[str, Any]:
    if not isinstance(event, dict):
        raise AcceptanceError(f"{label} must be an object")
    allowed_fields = {"event_id", "kind", "event_source", "start_s", "end_s", "phase_scheme_id", "metrics"}
    unknown_fields = set(event) - allowed_fields
    if unknown_fields:
        raise AcceptanceError(f"{label} has unsupported fields: {sorted(unknown_fields)}")
    event_id = _opaque_token(event.get("event_id"), f"{label}.event_id")
    kind = event.get("kind")
    if kind not in {"full", "partial", "failed"}:
        raise AcceptanceError(f"{label}.kind must be full, partial, or failed")
    expected_source = "independent_annotation" if role == "truth" else "engine_output"
    if event.get("event_source") != expected_source:
        raise AcceptanceError(f"{label}.event_source must be {expected_source}")
    start = _finite_number(event.get("start_s"), f"{label}.start_s")
    end = _finite_number(event.get("end_s"), f"{label}.end_s")
    if start < 0 or end <= start:
        raise AcceptanceError(f"{label}: expected 0 <= start_s < end_s")

    exercise = _exercise_condition(manifest, exercise_id, condition_id)
    condition = exercise["condition"]
    scheme_id = event.get("phase_scheme_id")
    expected_scheme = condition.get("phase_scheme_id")
    if scheme_id is not None and scheme_id != expected_scheme:
        raise AcceptanceError(f"{label}.phase_scheme_id differs from manifest condition")
    metrics = event.get("metrics", {})
    if not isinstance(metrics, dict):
        raise AcceptanceError(f"{label}.metrics must be an object")
    forbidden = FORBIDDEN_PRECOMPUTED.intersection(metrics)
    if forbidden:
        raise AcceptanceError(f"{label} contains precomputed metrics: {sorted(forbidden)}")
    allowed = SCALAR_METRICS | MAP_METRICS
    unknown_metrics = set(metrics) - allowed
    if unknown_metrics:
        raise AcceptanceError(f"{label} contains unsupported metric fields: {sorted(unknown_metrics)}")

    validated_metrics: dict[str, Any] = {}
    for metric in SCALAR_METRICS:
        if metric not in metrics:
            continue
        validated_metrics[metric] = _measurement(metrics[metric], f"{label}.metrics.{metric}", role, metric, manifest)
    if kind != "full" and validated_metrics.get("mcv_mps", (False, None))[0]:
        raise AcceptanceError(f"{label}: MCV is only admitted for FULL events")

    schemes = manifest.get("phase_schemes", {})
    scheme = schemes.get(expected_scheme) if expected_scheme else None
    for metric, key in (("phase_durations_s", "phase_ids"), ("phase_boundaries_s", "boundary_ids")):
        if metric in metrics:
            if scheme is None or scheme_id is None:
                raise AcceptanceError(f"{label}.{metric} requires an attested phase_scheme_id")
            validated_metrics[metric] = _validate_measurement_map(
                metrics[metric], f"{label}.metrics.{metric}", role, metric, scheme[key], manifest
            )
    return {
        "event_id": event_id,
        "kind": kind,
        "start_s": start,
        "end_s": end,
        "phase_scheme_id": scheme_id,
        "metrics": validated_metrics,
    }


def _validate_rows(
    rows: list[dict[str, Any]], role: str, manifest: dict[str, Any]
) -> dict[str, dict[str, Any]]:
    by_id: dict[str, dict[str, Any]] = {}
    expected_status = "observed" if role == "truth" else None
    evidence_class = manifest["evidence_class"]
    for index, row in enumerate(rows, 1):
        label = f"{role} row {index}"
        allowed_fields = {
            "schema_version", "evidence_class", "record_id", "participant_id", "session_id",
            "exercise_id", "condition_id", "status", "events",
        }
        if role == "prediction":
            allowed_fields |= {"confidence_band", "safe_claim"}
        unknown_fields = set(row) - allowed_fields
        if unknown_fields:
            raise AcceptanceError(f"{label} has unsupported fields: {sorted(unknown_fields)}")
        if row.get("schema_version") != SCHEMA_VERSION:
            raise AcceptanceError(f"{label}.schema_version must be {SCHEMA_VERSION}")
        if row.get("evidence_class") != evidence_class:
            raise AcceptanceError(f"{label}.evidence_class differs from manifest")
        record_id = _opaque_token(row.get("record_id"), f"{label}.record_id")
        if record_id in by_id:
            raise AcceptanceError(f"duplicate {role} record_id {record_id!r}")
        participant_id = _opaque_token(row.get("participant_id"), f"{label}.participant_id")
        session_id = _opaque_token(row.get("session_id"), f"{label}.session_id")
        exercise_id = _opaque_token(row.get("exercise_id"), f"{label}.exercise_id")
        condition_id = _opaque_token(row.get("condition_id"), f"{label}.condition_id")
        _exercise_condition(manifest, exercise_id, condition_id)
        status = row.get("status")
        if role == "truth" and status != expected_status:
            raise AcceptanceError(f"{label}.status must be observed")
        if role == "prediction" and status not in {"predicted", "abstain", "error"}:
            raise AcceptanceError(f"{label}.status must be predicted, abstain, or error")
        confidence = row.get("confidence_band")
        safe_claim = row.get("safe_claim")
        if role == "prediction":
            if status == "predicted" and confidence not in {"ALTA", "MEDIA", "BAJA"}:
                raise AcceptanceError(f"{label}: predicted rows need ALTA/MEDIA/BAJA confidence")
            if status != "predicted" and confidence not in {None, "ALTA", "MEDIA", "BAJA"}:
                raise AcceptanceError(f"{label}: invalid confidence_band")
            if not isinstance(safe_claim, bool):
                raise AcceptanceError(f"{label}.safe_claim must be boolean")
            if safe_claim and (status != "predicted" or confidence != "ALTA"):
                raise AcceptanceError(f"{label}: safe_claim requires a predicted ALTA result")
        events = row.get("events", [])
        if not isinstance(events, list):
            raise AcceptanceError(f"{label}.events must be an array")
        if status in {"abstain", "error"} and events:
            raise AcceptanceError(f"{label}: abstain/error rows cannot contain events")
        normalized_events = [
            _validate_event(event, f"{label}.events[{event_index}]", role, manifest, exercise_id, condition_id)
            for event_index, event in enumerate(events)
        ]
        event_ids = [event["event_id"] for event in normalized_events]
        if len(event_ids) != len(set(event_ids)):
            raise AcceptanceError(f"{label}: event_id values must be unique within a record")
        by_id[record_id] = {
            "record_id": record_id,
            "participant_id": participant_id,
            "session_id": session_id,
            "exercise_id": exercise_id,
            "condition_id": condition_id,
            "status": status,
            "confidence_band": confidence,
            "safe_claim": safe_claim,
            "events": normalized_events,
        }
    return by_id


def _percentile(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * percentile
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    weight = position - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def _gate(status: str, value: Any = None, threshold: Any = None, reason: str | None = None) -> dict[str, Any]:
    result: dict[str, Any] = {"status": status}
    if value is not None:
        result["value"] = value
    if threshold is not None:
        result["threshold"] = threshold
    if reason:
        result["reason"] = reason
    return result


def _rate_gate(numerator: int, denominator: int, minimum: float, reason_if_incomplete: str | None = None) -> dict[str, Any]:
    if denominator == 0:
        return _gate("NOT_ESTIMABLE", reason="zero denominator")
    value = numerator / denominator
    if value < minimum:
        return _gate("FAIL", value, minimum)
    if reason_if_incomplete:
        return _gate("INCOMPLETE", value, minimum, reason_if_incomplete)
    return _gate("PASS", value, minimum)


def _max_cardinality_matches(truth_events: list[dict[str, Any]], prediction_events: list[dict[str, Any]]) -> list[tuple[int, int]]:
    """Maximum one-to-one temporal matching; each boundary must be within ±0.30 s."""
    adjacency: list[list[int]] = []
    for truth in truth_events:
        candidates = []
        for pred_index, prediction in enumerate(prediction_events):
            start_error = abs(truth["start_s"] - prediction["start_s"])
            end_error = abs(truth["end_s"] - prediction["end_s"])
            if start_error <= 0.30 and end_error <= 0.30:
                candidates.append((start_error + end_error, pred_index))
        adjacency.append([index for _, index in sorted(candidates)])

    pred_to_truth: dict[int, int] = {}

    def augment(truth_index: int, seen: set[int]) -> bool:
        for pred_index in adjacency[truth_index]:
            if pred_index in seen:
                continue
            seen.add(pred_index)
            if pred_index not in pred_to_truth or augment(pred_to_truth[pred_index], seen):
                pred_to_truth[pred_index] = truth_index
                return True
        return False

    for truth_index in range(len(truth_events)):
        augment(truth_index, set())
    return sorted((truth_index, pred_index) for pred_index, truth_index in pred_to_truth.items())


def _combine(gates: Iterable[dict[str, Any]]) -> str:
    statuses = [gate.get("status") for gate in gates]
    if "FAIL" in statuses:
        return "FAIL"
    if any(status in {"INCOMPLETE", "NOT_ESTIMABLE"} for status in statuses):
        return "INCOMPLETE"
    return "PASS"


def _mark_incomplete(value: Any, reason: str) -> None:
    """Downgrade acceptance statuses when provenance cannot support a claim."""
    if isinstance(value, dict):
        for key, child in value.items():
            if key in {"status", "overall", "events", "safe_subset", "physical", "combined_gate"} and isinstance(child, str) and child in {"PASS", "FAIL"}:
                value[key] = "INCOMPLETE"
                if key == "status":
                    value.setdefault("reason", reason)
            else:
                _mark_incomplete(child, reason)
    elif isinstance(value, list):
        for child in value:
            _mark_incomplete(child, reason)


def _metric_errors(pairs: list[tuple[float, float]]) -> dict[str, Any]:
    errors = [prediction - truth for truth, prediction in pairs]
    absolute = [abs(error) for error in errors]
    if not errors:
        return {"n": 0, "mae": None, "rmse": None, "bias": None, "loa95": None, "p95_abs": None}
    bias = mean(errors)
    spread = 1.96 * stdev(errors) if len(errors) >= 2 else None
    loa = [bias - spread, bias + spread] if spread is not None else None
    return {
        "n": len(errors),
        "mae": mean(absolute),
        "rmse": math.sqrt(mean(error * error for error in errors)),
        "bias": bias,
        "loa95": loa,
        "p95_abs": _percentile(absolute, 0.95),
    }


def _metric_gate(summary: dict[str, Any], criteria: dict[str, float], complete: bool, label: str) -> dict[str, Any]:
    if summary["n"] == 0:
        return _gate("NOT_ESTIMABLE", reason=f"no paired {label} reference values")
    checks = []
    for key, limit in criteria.items():
        if key in {"loa_abs_max", "bias_abs_max"}:
            value = summary["loa95"] if key == "loa_abs_max" else summary["bias"]
            if value is None:
                return _gate("INCOMPLETE", threshold=limit, reason=f"{label} needs at least two paired values for LoA")
            passed = max(abs(v) for v in value) <= limit if key == "loa_abs_max" else abs(value) <= limit
        else:
            value = summary.get(key)
            if value is None:
                return _gate("INCOMPLETE", threshold=limit, reason=f"{label} metric {key} is unavailable")
            passed = value <= limit
        checks.append(passed)
    if not all(checks):
        return _gate("FAIL", reason=f"{label} exceeds a preregistered §6 threshold")
    if not complete:
        return _gate("INCOMPLETE", reason=f"{label} reference/prediction pairs are incomplete")
    return _gate("PASS")


def _measurement_value(event: dict[str, Any], metric: str) -> tuple[bool, Any]:
    return event["metrics"].get(metric, (False, None))


def _phase_values(event: dict[str, Any], metric: str, expected_ids: list[str]) -> tuple[bool, dict[str, float]]:
    values = event["metrics"].get(metric)
    if values is None:
        return False, {}
    result: dict[str, float] = {}
    for phase_id in expected_ids:
        available, value = values.get(phase_id, (False, None))
        if not available:
            return False, {}
        result[phase_id] = float(value)
    return True, result


def _reference_gate_available(manifest: dict[str, Any], metric: str) -> bool:
    return _has_reference(manifest, metric)


def _group_report(
    key: tuple[str, str],
    truth_rows: list[dict[str, Any]],
    predictions: dict[str, dict[str, Any]],
    manifest: dict[str, Any],
) -> dict[str, Any]:
    exercise_id, condition_id = key
    exercise = _exercise_condition(manifest, exercise_id, condition_id)
    exercise_class = exercise["class"]
    condition = exercise["condition"]
    quality = condition["capture_quality"]
    thresholds = {
        "exact_series_min": 0.95 if exercise_class == "basic" else 0.90,
        "count_mae_max": 0.10 if exercise_class == "basic" else 0.25,
        "event_precision_recall_min": 0.99 if exercise_class == "basic" else 0.98,
        "safe_coverage_min": 0.80 if exercise_class == "basic" else 0.70,
    }
    rows: list[tuple[dict[str, Any], dict[str, Any] | None]] = []
    for truth in truth_rows:
        pred = predictions.get(truth["record_id"])
        if pred is not None and (pred["participant_id"], pred["session_id"], pred["exercise_id"], pred["condition_id"]) != (
            truth["participant_id"], truth["session_id"], truth["exercise_id"], truth["condition_id"]
        ):
            raise AcceptanceError(f"prediction identity mismatch for record {truth['record_id']!r}")
        rows.append((truth, pred))

    truth_count_sum = 0
    predicted_count_sum = 0
    truth_partial_sum = 0
    predicted_partial_sum = 0
    exact_series = 0
    exact_full_series = 0
    count_diffs: list[float] = []
    predicted_rows = 0
    confidence: dict[str, dict[str, int]] = {band: {"n": 0, "exact": 0} for band in ("ALTA", "MEDIA", "BAJA")}
    alta_count = 0
    alta_exact = 0
    failed_truth_events = 0
    failed_prediction_events = 0
    event_data: dict[str, dict[str, Any]] = {}
    pair_events: dict[str, list[tuple[dict[str, Any], dict[str, Any], dict[str, Any]]]] = {kind: [] for kind in KINDS}

    for truth, pred in rows:
        failed_truth_events += sum(event["kind"] == "failed" for event in truth["events"])
        truth_full = sum(event["kind"] == "full" for event in truth["events"])
        predicted = pred is not None and pred["status"] == "predicted"
        pred_events = pred["events"] if predicted else []
        failed_prediction_events += sum(event["kind"] == "failed" for event in pred_events)
        pred_full = sum(event["kind"] == "full" for event in pred_events)
        truth_partial = sum(event["kind"] == "partial" for event in truth["events"])
        pred_partial = sum(event["kind"] == "partial" for event in pred_events)
        truth_count_sum += truth_full
        predicted_count_sum += pred_full
        truth_partial_sum += truth_partial
        predicted_partial_sum += pred_partial
        if predicted:
            predicted_rows += 1
            count_diffs.append(float(abs(pred_full - truth_full)))
        row_exact_full = predicted and pred_full == truth_full
        row_exact = row_exact_full and pred_partial == truth_partial
        exact_full_series += int(row_exact_full)
        exact_series += int(row_exact)
        if predicted:
            band = pred["confidence_band"]
            confidence[band]["n"] += 1
            confidence[band]["exact"] += int(row_exact)
            if band == "ALTA":
                alta_count += 1
                alta_exact += int(row_exact)

        for kind in KINDS:
            truths = [event for event in truth["events"] if event["kind"] == kind]
            preds = [event for event in pred_events if event["kind"] == kind]
            matches = _max_cardinality_matches(truths, preds)
            if kind not in event_data:
                event_data[kind] = {"truth": 0, "prediction": 0, "matched": 0}
            event_data[kind]["truth"] += len(truths)
            event_data[kind]["prediction"] += len(preds)
            event_data[kind]["matched"] += len(matches)
            for truth_index, pred_index in matches:
                pair_events[kind].append((truth, truths[truth_index], preds[pred_index]))

    exact_gate = _rate_gate(exact_series, len(rows), thresholds["exact_series_min"], "capture quality is adverse" if quality == "adverse" else None)
    if len(count_diffs) == 0:
        count_mae = None
        count_gate = _gate("NOT_ESTIMABLE", reason="no paired prediction counts")
    else:
        count_mae = mean(count_diffs)
        if count_mae > thresholds["count_mae_max"]:
            count_gate = _gate("FAIL", count_mae, thresholds["count_mae_max"])
        elif predicted_rows < len(rows):
            count_gate = _gate("INCOMPLETE", count_mae, thresholds["count_mae_max"], "abstentions/errors are excluded from MAE; exact-series denominator includes them")
        elif quality == "adverse":
            count_gate = _gate("INCOMPLETE", count_mae, thresholds["count_mae_max"], "no universal acceptance threshold for adverse capture")
        else:
            count_gate = _gate("PASS", count_mae, thresholds["count_mae_max"])
    if quality == "adverse":
        exact_gate = _gate("INCOMPLETE", exact_gate.get("value"), exact_gate.get("threshold"), "no universal acceptance threshold for adverse capture")
        count_gate = _gate("INCOMPLETE", count_mae, thresholds["count_mae_max"], "no universal acceptance threshold for adverse capture")

    event_reports: dict[str, Any] = {}
    event_gates: list[dict[str, Any]] = []
    for kind in KINDS:
        data = event_data.get(kind, {"truth": 0, "prediction": 0, "matched": 0})
        false_positive = data["prediction"] - data["matched"]
        false_negative = data["truth"] - data["matched"]
        precision = data["matched"] / data["prediction"] if data["prediction"] else None
        recall = data["matched"] / data["truth"] if data["truth"] else None
        minimum = thresholds["event_precision_recall_min"]
        p_gate = _rate_gate(data["matched"], data["prediction"], minimum)
        r_gate = _rate_gate(data["matched"], data["truth"], minimum)
        if quality == "adverse":
            p_gate = _gate("INCOMPLETE", precision, minimum, "no universal acceptance threshold for adverse capture")
            r_gate = _gate("INCOMPLETE", recall, minimum, "no universal acceptance threshold for adverse capture")
        event_gates.extend((p_gate, r_gate))
        event_reports[kind] = {
            "truth_events": data["truth"],
            "prediction_events": data["prediction"],
            "matched_one_to_one": data["matched"],
            "false_positives": false_positive,
            "false_negatives": false_negative,
            "precision": precision,
            "recall": recall,
            "precision_gate": p_gate,
            "recall_gate": r_gate,
        }

    confidence_report = {
        band: {
            "sets": values["n"],
            "exact_sets": values["exact"],
            "exact_series_rate": values["exact"] / values["n"] if values["n"] else None,
        }
        for band, values in confidence.items()
    }
    safe_coverage = alta_count / len(rows) if rows else None
    safe_exact_gate = _rate_gate(alta_exact, alta_count, 0.99)
    safe_coverage_gate = _rate_gate(alta_count, len(rows), thresholds["safe_coverage_min"])
    alta_precision_gate = _alta_event_precision_gate(rows)
    safe_exact_ci_gate = _gate("INCOMPLETE", reason="paired participant-cluster CI for safe exactness is not implemented")
    safe_event_ci_gate = _gate("INCOMPLETE", reason="paired participant-cluster CI for safe event precision is not implemented")
    ci_gates = [safe_exact_ci_gate, safe_event_ci_gate]
    if quality == "adverse":
        safe_coverage_gate = _gate("INCOMPLETE", safe_coverage, thresholds["safe_coverage_min"], "no universal acceptance threshold for adverse capture")
        safe_exact_gate = _gate("INCOMPLETE", alta_exact / alta_count if alta_count else None, 0.99, "no universal acceptance threshold for adverse capture")
        alta_precision_gate = _gate("INCOMPLETE", alta_precision_gate.get("value"), 0.99, "no universal acceptance threshold for adverse capture")
    safe_gates = [safe_coverage_gate, safe_exact_gate, alta_precision_gate, *ci_gates]

    partial_zone_matches = [pair for pair in pair_events["partial"]]
    zone_pairs = []
    amplitude_pairs: list[tuple[float, float]] = []
    for _, truth_event, pred_event in partial_zone_matches:
        truth_zone = _measurement_value(truth_event, "partial_zone")
        pred_zone = _measurement_value(pred_event, "partial_zone")
        if truth_zone[0] and pred_zone[0]:
            zone_pairs.append((truth_zone[1], pred_zone[1]))
        truth_amplitude = _measurement_value(truth_event, "partial_amplitude_pct")
        pred_amplitude = _measurement_value(pred_event, "partial_amplitude_pct")
        if truth_amplitude[0] and pred_amplitude[0]:
            amplitude_pairs.append((float(truth_amplitude[1]), float(pred_amplitude[1])))
    zone_accuracy = sum(t == p for t, p in zone_pairs) / len(zone_pairs) if zone_pairs else None
    amplitude_errors = _metric_errors(amplitude_pairs)
    partial_quality_gate = _gate("INCOMPLETE", reason="no preregistered §6 acceptance threshold for partial-zone/amplitude agreement")
    if len(zone_pairs) < len(partial_zone_matches) or len(amplitude_pairs) < len(partial_zone_matches):
        partial_quality_reason = "partial_zone or partial_amplitude_pct is missing from truth/prediction pairs"
    else:
        partial_quality_reason = partial_quality_gate["reason"]
    partial_quality_gate["reason"] = partial_quality_reason

    physical = _physical_metrics(key, rows, pair_events, manifest, condition)
    all_gates = [exact_gate, count_gate, *event_gates, *safe_gates, partial_quality_gate, *physical["gates"]]
    report_status = _combine(all_gates)
    force_incomplete_reason = None
    if quality == "adverse":
        force_incomplete_reason = "no universal acceptance threshold for adverse capture"
    if manifest["evidence_class"] != "external_real":
        report_status = "INCOMPLETE"
        force_incomplete_reason = "synthetic/testdata evidence cannot pass competitive acceptance"
    if force_incomplete_reason:
        for gate in all_gates:
            if gate["status"] in {"PASS", "FAIL"}:
                gate["status"] = "INCOMPLETE"
                gate["reason"] = force_incomplete_reason
    if force_incomplete_reason:
        event_gates_status = "INCOMPLETE"
        physical_gate_status = "INCOMPLETE"
        safe_gate_status = "INCOMPLETE"
        report_status = "INCOMPLETE"
    else:
        event_gates_status = _combine(event_gates)
        physical_gate_status = _combine(physical["gates"])
        safe_gate_status = _combine(safe_gates)
    missing_prediction_rows = sum(pred is None for _, pred in rows)
    abstention_rows = sum(pred is not None and pred["status"] == "abstain" for _, pred in rows)
    error_rows = sum(pred is not None and pred["status"] == "error" for _, pred in rows)
    return {
        "exercise_id": exercise_id,
        "condition_id": condition_id,
        "exercise_class": exercise_class,
        "capture_quality": quality,
        "sets": len(rows),
        "truth_full_reps": truth_count_sum,
        "predicted_full_reps": predicted_count_sum,
        "truth_partial_events": truth_partial_sum,
        "predicted_partial_events": predicted_partial_sum,
        "predicted_set_coverage": predicted_rows / len(rows) if rows else None,
        "missing_prediction_records": missing_prediction_rows,
        "abstention_records": abstention_rows,
        "error_records": error_rows,
        "failed_event_diagnostics": {
            "truth_failed_events": failed_truth_events,
            "prediction_failed_events": failed_prediction_events,
        },
        "exact_series_rate": exact_series / len(rows) if rows else None,
        "exact_full_series_rate": exact_full_series / len(rows) if rows else None,
        "count_mae_paired_sets": count_mae,
        "count_mae_paired_n": len(count_diffs),
        "confidence": confidence_report,
        "safe_subset": {
            "alta_sets_including_safe_claim_false": alta_count,
            "safe_coverage": safe_coverage,
            "safe_coverage_gate": safe_coverage_gate,
            "exact_series_rate": alta_exact / alta_count if alta_count else None,
            "exact_series_gate": safe_exact_gate,
            "event_precision_gate": alta_precision_gate,
            "exact_series_lower_95_ci_gate": safe_exact_ci_gate,
            "event_precision_lower_95_ci_gate": safe_event_ci_gate,
            "combined_gate": safe_gate_status,
        },
        "events": event_reports,
        "partial_diagnostics": {
            "matched_partial_events": len(partial_zone_matches),
            "zone_pair_n": len(zone_pairs),
            "zone_missing_pairs": len(partial_zone_matches) - len(zone_pairs),
            "zone_accuracy": zone_accuracy,
            "amplitude": amplitude_errors,
            "amplitude_missing_pairs": len(partial_zone_matches) - len(amplitude_pairs),
            "gate": partial_quality_gate,
        },
        "physical_metrics": physical["metrics"],
        "gates": {
            "series_exactness": exact_gate,
            "count_mae": count_gate,
            "events": event_gates_status,
            "safe_subset": safe_gate_status,
            "partial_zone": partial_quality_gate,
            "physical": physical_gate_status,
            "overall": report_status,
        },
    }


def _alta_event_precision_gate(rows: list[tuple[dict[str, Any], dict[str, Any] | None]]) -> dict[str, Any]:
    predicted: list[dict[str, Any]] = []
    truth: list[dict[str, Any]] = []
    matches = 0
    for truth_row, pred_row in rows:
        truth_events = [event for event in truth_row["events"] if event["kind"] in KINDS]
        truth.extend(truth_events)
        if pred_row is not None and pred_row["status"] == "predicted" and pred_row["confidence_band"] == "ALTA":
            pred_events = [event for event in pred_row["events"] if event["kind"] in KINDS]
            predicted.extend(pred_events)
            by_kind = {}
            for kind in KINDS:
                t_events = [event for event in truth_row["events"] if event["kind"] == kind]
                p_events = [event for event in pred_row["events"] if event["kind"] == kind]
                by_kind[kind] = _max_cardinality_matches(t_events, p_events)
                matches += len(by_kind[kind])
    if not predicted:
        return _gate("NOT_ESTIMABLE", reason="no ALTA events")
    return _rate_gate(matches, len(predicted), 0.99)


def _physical_metrics(
    key: tuple[str, str],
    rows: list[tuple[dict[str, Any], dict[str, Any] | None]],
    pair_events: dict[str, list[tuple[dict[str, Any], dict[str, Any], dict[str, Any]]]],
    manifest: dict[str, Any],
    condition: dict[str, Any],
) -> dict[str, Any]:
    scheme_id = condition.get("phase_scheme_id")
    scheme = manifest.get("phase_schemes", {}).get(scheme_id, {}) if scheme_id else {}
    expected_full_events = sum(
        event["kind"] == "full"
        for truth_row, _ in rows
        for event in truth_row["events"]
    )
    expected_full_sets = {
        truth_row["record_id"]
        for truth_row, _ in rows
        if any(event["kind"] == "full" for event in truth_row["events"])
    }
    metrics: dict[str, Any] = {}
    gates: list[dict[str, Any]] = []

    scalar_criteria = {
        "mcv_mps": {"mae": 0.03, "rmse": 0.04, "bias_abs_max": 0.02, "loa_abs_max": 0.10},
        "peak_velocity_mps": {"mae": 0.10, "rmse": 0.15},
        "rom_vertical_cm": {"mae": 2.0, "loa_abs_max": 5.0},
    }
    for metric, criteria in scalar_criteria.items():
        pairs: list[tuple[float, float]] = []
        for _, truth_event, pred_event in pair_events["full"]:
            truth_ok, truth_value = _measurement_value(truth_event, metric)
            pred_ok, pred_value = _measurement_value(pred_event, metric)
            if truth_ok and pred_ok:
                pairs.append((float(truth_value), float(pred_value)))
        summary = _metric_errors(pairs)
        eligible_full = len(pair_events["full"])
        complete = len(pairs) == expected_full_events and expected_full_events > 0 and _reference_gate_available(manifest, metric)
        if metric == "mcv_mps" and not condition.get("disk_calibrated"):
            gate = _gate("INCOMPLETE", reason="MCV gate requires a disk-calibrated condition")
        elif not _reference_gate_available(manifest, metric):
            gate = _gate("INCOMPLETE", reason=f"no manifest-bound external reference for {metric}")
        else:
            gate = _metric_gate(summary, criteria, complete, metric)
        metrics[metric] = {
            **summary,
            "truth_full_events": expected_full_events,
            "eligible_matched_full_events": eligible_full,
            "unpaired_or_unavailable_full_events": max(0, expected_full_events - len(pairs)),
            "gate": gate,
        }
        gates.append(gate)

    phase_gate, phase_summary, tut_summary = _phase_and_tut(
        pair_events["full"], scheme, expected_full_events, expected_full_sets
    )
    metrics["phase_durations"] = phase_summary["durations"]
    metrics["phase_boundaries"] = phase_summary["boundaries"]
    metrics["tut"] = tut_summary
    gates.extend((phase_gate, tut_summary["gate"]))

    vl_summary = _velocity_loss(rows, manifest, condition)
    metrics["velocity_loss_pct"] = vl_summary
    gates.append(vl_summary["gate"])

    if not condition.get("disk_calibrated"):
        metrics["mcv_mps"]["gate"] = _gate("INCOMPLETE", reason="MCV gate requires a disk-calibrated condition")
        gates[0] = metrics["mcv_mps"]["gate"]
    return {"metrics": metrics, "gates": gates}


def _phase_and_tut(
    full_pairs: list[tuple[dict[str, Any], dict[str, Any], dict[str, Any]]],
    scheme: dict[str, Any],
    expected_full_events: int,
    expected_full_sets: set[str],
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    phase_ids = scheme.get("phase_ids", []) if isinstance(scheme, dict) else []
    boundary_ids = scheme.get("boundary_ids", []) if isinstance(scheme, dict) else []
    tut_phase_ids = scheme.get("tut_phase_ids", []) if isinstance(scheme, dict) else []
    duration_pairs: dict[str, list[tuple[float, float]]] = {phase_id: [] for phase_id in phase_ids}
    boundary_pairs: dict[str, list[tuple[float, float]]] = {boundary_id: [] for boundary_id in boundary_ids}
    tut_pairs: list[tuple[float, float]] = []
    tut_by_record: dict[str, list[tuple[float, float]]] = defaultdict(list)
    complete_pairs = 0
    tut_complete_pairs = 0
    for truth_row, truth_event, pred_event in full_pairs:
        if truth_event["phase_scheme_id"] is None or truth_event["phase_scheme_id"] != pred_event["phase_scheme_id"]:
            continue
        t_ok, t_durations = _phase_values(truth_event, "phase_durations_s", phase_ids)
        p_ok, p_durations = _phase_values(pred_event, "phase_durations_s", phase_ids)
        if t_ok and p_ok and phase_ids:
            complete_pairs += 1
            for phase_id in phase_ids:
                duration_pairs[phase_id].append((t_durations[phase_id], p_durations[phase_id]))
        if tut_phase_ids:
            t_tut_ok, t_tut_durations = _phase_values(truth_event, "phase_durations_s", tut_phase_ids)
            p_tut_ok, p_tut_durations = _phase_values(pred_event, "phase_durations_s", tut_phase_ids)
            if t_tut_ok and p_tut_ok:
                tut_complete_pairs += 1
                truth_tut = sum(t_tut_durations.values())
                pred_tut = sum(p_tut_durations.values())
                tut_pairs.append((truth_tut, pred_tut))
                tut_by_record[truth_row["record_id"]].append((truth_tut, pred_tut))
        t_b_ok, t_boundaries = _phase_values(truth_event, "phase_boundaries_s", boundary_ids)
        p_b_ok, p_boundaries = _phase_values(pred_event, "phase_boundaries_s", boundary_ids)
        if t_b_ok and p_b_ok:
            for boundary_id in boundary_ids:
                boundary_pairs[boundary_id].append((t_boundaries[boundary_id], p_boundaries[boundary_id]))

    duration_reports = {phase_id: _metric_errors(pairs) for phase_id, pairs in duration_pairs.items()}
    boundary_reports = {boundary_id: _metric_errors(pairs) for boundary_id, pairs in boundary_pairs.items()}
    phase_complete = bool(
        phase_ids
        and expected_full_events > 0
        and len(full_pairs) == expected_full_events
        and complete_pairs == expected_full_events
        and all(report["n"] > 0 for report in duration_reports.values())
    )
    duration_fail = any(report["mae"] is not None and report["mae"] > 0.15 for report in duration_reports.values())
    boundary_available = bool(
        boundary_ids
        and expected_full_events > 0
        and len(full_pairs) == expected_full_events
        and all(report["n"] == expected_full_events for report in boundary_reports.values())
    )
    boundary_fail = any(report["p95_abs"] is not None and report["p95_abs"] > 0.30 for report in boundary_reports.values())
    if duration_fail or boundary_fail:
        phase_gate = _gate("FAIL", reason="phase duration or boundary error exceeds §6")
    elif not phase_complete or not boundary_available:
        phase_gate = _gate("INCOMPLETE", reason="independent phase values/scheme are unavailable or use fallback anchors")
    else:
        phase_gate = _gate("PASS")

    tut_rep = _metric_errors(tut_pairs)
    set_errors: list[tuple[float, float]] = []
    for values in tut_by_record.values():
        truth_total = sum(pair[0] for pair in values)
        pred_total = sum(pair[1] for pair in values)
        set_errors.append((truth_total, pred_total))
    set_mae = mean(abs(pred - truth) for truth, pred in set_errors) if set_errors else None
    mean_truth_set_tut = mean(truth for truth, _ in set_errors) if set_errors else None
    set_error_details = []
    for record_id, values in tut_by_record.items():
        truth_total = sum(pair[0] for pair in values)
        pred_total = sum(pair[1] for pair in values)
        error = abs(pred_total - truth_total)
        limit = max(1.0, 0.05 * truth_total)
        set_error_details.append({
            "record_id": record_id,
            "reference_s": truth_total,
            "prediction_s": pred_total,
            "absolute_error_s": error,
            "limit_s": limit,
            "normalized_error": error / limit,
        })
    worst_set_error = max(set_error_details, key=lambda detail: detail["normalized_error"], default=None)
    set_limit_fail = any(detail["absolute_error_s"] > detail["limit_s"] for detail in set_error_details)
    if (tut_rep["n"] and (tut_rep["mae"] or 0.0) > 0.20) or set_limit_fail:
        tut_gate = _gate("FAIL", reason="TUT error exceeds §6 per-repetition or per-set limit")
    elif not tut_phase_ids:
        tut_gate = _gate("INCOMPLETE", reason="manifest has no explicit tut_phase_ids subset")
    elif (
        expected_full_events == 0
        or len(full_pairs) != expected_full_events
        or tut_complete_pairs != expected_full_events
        or not tut_by_record
    ):
        tut_gate = _gate("INCOMPLETE", reason="TUT needs complete paired phase durations")
    elif set(tut_by_record) != expected_full_sets:
        tut_gate = _gate("INCOMPLETE", reason="some full-rep sets lack complete paired TUT")
    else:
        tut_gate = _gate("PASS")
    tut_summary = {
        "per_rep": tut_rep,
        "per_set_mae_s": set_mae,
        "per_set_worst_error_s": worst_set_error["absolute_error_s"] if worst_set_error else None,
        "per_set_worst_limit_s": worst_set_error["limit_s"] if worst_set_error else None,
        "per_set_worst_normalized_error": worst_set_error["normalized_error"] if worst_set_error else None,
        "per_set_errors": set_error_details,
        "mean_reference_set_tut_s": mean_truth_set_tut,
        "per_set_n": len(set_errors),
        "complete_paired_reps": tut_complete_pairs,
        "expected_full_rep_sets": len(expected_full_sets),
        "missing_full_rep_sets": len(expected_full_sets - set(tut_by_record)),
        "gate": tut_gate,
    }
    return phase_gate, {"durations": duration_reports, "boundaries": boundary_reports}, tut_summary


def _velocity_loss(
    rows: list[tuple[dict[str, Any], dict[str, Any] | None]], manifest: dict[str, Any], condition: dict[str, Any]
) -> dict[str, Any]:
    pairs: list[tuple[float, float]] = []
    eligible = 0
    missing = False
    invalid_zero_denominator_sets = 0
    for truth_row, pred_row in rows:
        if pred_row is None or pred_row["status"] != "predicted":
            missing = True
            continue
        truths = sorted((e for e in truth_row["events"] if e["kind"] == "full"), key=lambda event: event["start_s"])
        preds = sorted((e for e in pred_row["events"] if e["kind"] == "full"), key=lambda event: event["start_s"])
        matches = _max_cardinality_matches(truths, preds)
        if len(matches) != len(truths) or len(matches) != len(preds) or len(matches) < 2:
            missing = True
            continue
        aligned = sorted(((truths[ti], preds[pi]) for ti, pi in matches), key=lambda pair: pair[0]["start_s"])
        truth_values: list[float] = []
        pred_values: list[float] = []
        for truth_event, pred_event in aligned:
            t_ok, t_value = _measurement_value(truth_event, "mcv_mps")
            p_ok, p_value = _measurement_value(pred_event, "mcv_mps")
            if not t_ok or not p_ok:
                missing = True
                truth_values = []
                break
            truth_values.append(float(t_value))
            pred_values.append(float(p_value))
        if len(truth_values) < 2:
            continue
        truth_max = max(truth_values)
        pred_max = max(pred_values)
        if truth_max <= 0 or pred_max <= 0:
            invalid_zero_denominator_sets += 1
            missing = True
            continue
        eligible += 1
        truth_vl = 100.0 * (1.0 - truth_values[-1] / truth_max)
        pred_vl = 100.0 * (1.0 - pred_values[-1] / pred_max)
        pairs.append((truth_vl, pred_vl))
    summary = _metric_errors(pairs)
    if not condition.get("disk_calibrated"):
        gate = _gate("INCOMPLETE", reason="VL requires calibrated MCV inputs")
    elif not _reference_gate_available(manifest, "mcv_mps"):
        gate = _gate("INCOMPLETE", reason="VL truth requires manifest-bound calibrated MCV reference")
    elif summary["n"] == 0:
        if invalid_zero_denominator_sets:
            gate = _gate("INCOMPLETE", reason="VL has sets with zero or negative MCV denominator")
        else:
            gate = _gate("NOT_ESTIMABLE", reason="VL requires at least two valid FULL events per paired set")
    elif summary["mae"] > 5 or summary["p95_abs"] > 10 or abs(summary["bias"]) > 2:
        gate = _gate("FAIL", reason="VL error exceeds §6")
    elif invalid_zero_denominator_sets:
        gate = _gate("INCOMPLETE", reason="VL has sets with zero or negative MCV denominator")
    elif missing:
        gate = _gate("INCOMPLETE", reason="some sets lack complete matched calibrated FULL-event MCV")
    else:
        gate = _gate("PASS")
    summary.update({
        "eligible_sets": eligible,
        "invalid_zero_denominator_sets": invalid_zero_denominator_sets,
        "gate": gate,
        "formula": "100*(1-last_valid_full_mcv/max_valid_full_mcv)",
    })
    return summary


def evaluate_records(
    manifest: dict[str, Any], truth_rows: list[dict[str, Any]], prediction_rows: list[dict[str, Any]],
    *, manifest_sha256: str, truth_sha256: str, predictions_sha256: str
) -> dict[str, Any]:
    """Evaluate normalized raw records. Hashes must have been independently pinned by caller."""
    for label, value in (("manifest", manifest_sha256), ("truth", truth_sha256), ("predictions", predictions_sha256)):
        _require_hash(value, f"{label} SHA-256")
    provenance_issues = _manifest_integrity(manifest, truth_sha256.lower(), predictions_sha256.lower())
    truth = _validate_rows(truth_rows, "truth", manifest)
    predictions = _validate_rows(prediction_rows, "prediction", manifest)
    extra = set(predictions) - set(truth)
    if extra:
        raise AcceptanceError(f"prediction records without truth are rejected: {sorted(extra)}")
    # Missing prediction records are deliberately retained as abstentions/errors in the denominator.
    groups: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for row in truth.values():
        groups[(row["exercise_id"], row["condition_id"])].append(row)
    reports = []
    for key in sorted(groups):
        report = _group_report(key, sorted(groups[key], key=lambda row: row["record_id"]), predictions, manifest)
        if provenance_issues:
            _mark_incomplete(report, "; ".join(provenance_issues))
            report["provenance_issues"] = provenance_issues
        reports.append(report)
    overall_statuses = [report["gates"]["overall"] for report in reports]
    overall = "FAIL" if "FAIL" in overall_statuses else "INCOMPLETE" if any(status != "PASS" for status in overall_statuses) else "PASS"
    if manifest["evidence_class"] != "external_real" or provenance_issues:
        overall = "INCOMPLETE" if overall != "FAIL" else "FAIL"
    if manifest["evidence_class"] != "external_real":
        provenance_gate = _gate("INCOMPLETE", reason="synthetic/testdata evidence cannot establish competitive provenance")
    elif provenance_issues:
        provenance_gate = _gate("INCOMPLETE", reason="; ".join(provenance_issues))
    else:
        provenance_gate = _gate("PASS")
    return {
        "schema_version": SCHEMA_VERSION,
        "benchmark_id": manifest.get("benchmark_id"),
        "evidence_class": manifest["evidence_class"],
        "sha256": {"manifest": manifest_sha256.lower(), "truth": truth_sha256.lower(), "predictions": predictions_sha256.lower()},
        "provenance_gate": provenance_gate,
        "confidence_interval_policy": {
            "status": "INCOMPLETE",
            "reason": "No validated paired participant-cluster bootstrap is implemented; rep-level intervals are not used as independent evidence.",
        },
        "groups": reports,
        "overall": overall,
    }


def evaluate_files(
    manifest_path: Path,
    truth_path: Path,
    predictions_path: Path,
    expected_manifest_sha256: str,
    expected_truth_sha256: str,
    expected_predictions_sha256: str,
) -> dict[str, Any]:
    manifest_bytes, manifest_sha = _read_pinned_file(manifest_path, expected_manifest_sha256, "manifest")
    truth_bytes, truth_sha = _read_pinned_file(truth_path, expected_truth_sha256, "truth")
    predictions_bytes, predictions_sha = _read_pinned_file(predictions_path, expected_predictions_sha256, "predictions")
    manifest = _load_json(manifest_bytes, "manifest")
    truth = _load_jsonl(truth_bytes, "truth")
    predictions = _load_jsonl(predictions_bytes, "predictions")
    return evaluate_records(
        manifest,
        truth,
        predictions,
        manifest_sha256=manifest_sha,
        truth_sha256=truth_sha,
        predictions_sha256=predictions_sha,
    )


def _write_new_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    try:
        with path.open("x", encoding="utf-8", newline="\n") as handle:
            handle.write(payload)
    except FileExistsError as exc:
        raise AcceptanceError(f"refusing to overwrite existing report: {path}") from exc
    except OSError as exc:
        raise AcceptanceError(f"cannot write report: {exc}") from exc


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--truth", required=True, type=Path)
    parser.add_argument("--predictions", required=True, type=Path)
    parser.add_argument("--expected-manifest-sha256", required=True)
    parser.add_argument("--expected-truth-sha256", required=True)
    parser.add_argument("--expected-predictions-sha256", required=True)
    parser.add_argument("--out", required=True, type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        report = evaluate_files(
            args.manifest,
            args.truth,
            args.predictions,
            args.expected_manifest_sha256,
            args.expected_truth_sha256,
            args.expected_predictions_sha256,
        )
        _write_new_report(args.out, report)
    except AcceptanceError as exc:
        print(f"competitive_acceptance: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"overall": report["overall"], "report": str(args.out)}, ensure_ascii=False))
    return {"PASS": 0, "FAIL": 1, "INCOMPLETE": 3}.get(report["overall"], 3)


if __name__ == "__main__":
    raise SystemExit(main())
