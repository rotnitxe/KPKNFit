# Competitive acceptance input contract

`competitive_acceptance.py` evaluates raw observations only. It does not load
bank files, create labels, or trust submitted aggregate metrics. The manifest,
truth JSONL, and prediction JSONL must each be pinned by a SHA-256 supplied
separately on the command line. The hashes inside `manifest.inputs` must match
the pinned byte streams. The evaluator refuses to overwrite `--out`.

This tool is a post-delivery annex. It is not a release, signing, or delivery
hook. A diagnostic run may be `INCOMPLETE`; neither incomplete evidence nor
synthetic/testdata can be promoted to competitive acceptance.

## Manifest

Required top-level fields:

```json
{
  "schema_version": 1,
  "benchmark_id": "acceptance_v1",
  "evidence_class": "external_real",
  "inputs": {
    "truth_sha256": "<64 lowercase hex>",
    "predictions_sha256": "<64 lowercase hex>"
  },
  "privacy": {"pseudonymous": true, "direct_identifiers": false},
  "provenance": {
    "truth": {
      "method_id": "independent_video_annotation",
      "protocol_id": "protocol_v1",
      "protocol_sha256": "<64 lowercase hex>",
      "independent": true
    },
    "prediction": {
      "model_id": "model_name",
      "model_version": "version",
      "engine_version": "engine_version",
      "apk_sha256": "<64 lowercase hex>",
      "device_model": "device_model",
      "device_os": "os_version",
      "capture_setup_id": "setup_id"
    },
    "physical_references": {
      "mcv_mps": {
        "instrument_id": "instrument_id",
        "calibration_id": "calibration_id",
        "calibration_sha256": "<64 lowercase hex>",
        "protocol_id": "reference_protocol",
        "protocol_sha256": "<64 lowercase hex>",
        "unit": "m/s"
      }
    }
  },
  "exercise_matrix": {
    "exercise_token": {
      "class": "basic",
      "conditions": {
        "condition_token": {
          "capture_quality": "good",
          "disk_calibrated": false,
          "phase_scheme_id": "profile_a"
        }
      }
    }
  },
  "phase_schemes": {
    "profile_a": {
      "phase_ids": ["depart_s", "far_turn_pause_s", "return_s"],
      "tut_phase_ids": ["depart_s", "return_s"],
      "boundary_ids": ["far_turn_start_s", "far_turn_end_s"]
    }
  },
  "statistics": {
    "cluster_unit": "participant_id",
    "nested_unit": "session_id",
    "method": "paired_person_cluster_bootstrap",
    "sample_size_plan_sha256": "<64 lowercase hex>"
  }
}
```

`evidence_class` is `external_real`, `synthetic`, or `testdata`. The latter two
are accepted for evaluator tests and diagnostics only, and force competitive
gates to `INCOMPLETE`. Each exercise must be `basic` or `other`; each condition
must declare `capture_quality` as `good` or `adverse`, and explicitly set
`disk_calibrated`. Adverse-view results are diagnostics because §6 has no
universal acceptance threshold for that capture condition.

Phase IDs are neutral, profile-specific keys. Their semantics and order are
defined by the manifest, not inferred by the evaluator. Phase times are seconds
relative to the observed `start_s` of the repetition. `tut_phase_ids` is an
explicit subset of `phase_ids`; TUT sums only those phases, so pauses and rests
are excluded unless the manifest explicitly selects them. If the field is absent,
the TUT gate is `INCOMPLETE`. Prediction and independent truth must declare the
same `phase_scheme_id`; missing schemes and fallback segmenter anchors are
unavailable evidence. The evaluator does not infer bottom/concentric/lockout
semantics.

To bind an external physical truth value, `physical_references` must identify an
instrument, calibration, reference protocol and hashes, plus the required unit
(`m/s` for velocity, `cm` for vertical ROM). A source string by itself is not
provenance. Phase/event truth uses `independent_annotation`; physical truth uses
`reference_instrument`. Prediction measurements use `engine_output`.

## JSONL records

There is one truth row for each set. Prediction rows may be absent (counted as
missing output), or declare `status` `predicted`, `abstain`, or `error`. A
prediction with no matching truth record is rejected. Every record includes:

```json
{
  "schema_version": 1,
  "evidence_class": "external_real",
  "record_id": "set_anon_001",
  "participant_id": "person_anon_001",
  "session_id": "session_anon_001",
  "exercise_id": "exercise_token",
  "condition_id": "condition_token",
  "status": "observed",
  "events": []
}
```

Truth status is `observed`. Prediction rows also have `confidence_band`
(`ALTA`, `MEDIA`, `BAJA`, or null for abstention/error) and boolean `safe_claim`.
`safe_claim=true` requires a predicted `ALTA` result. Every predicted `ALTA`
result enters the safe-subset denominator even if `safe_claim=false`.

Events have a unique per-record `event_id`, `kind` (`full`, `partial`, or
`failed`), `event_source`, `start_s`, `end_s`, optional `phase_scheme_id`, and
optional `metrics`. Truth events use `event_source: independent_annotation`;
prediction events use `engine_output`. Only `full` and `partial` events enter
their separate temporal precision/recall gates. Matching is one-to-one and
requires both observed start and end to be within ±0.30 seconds.
`failed` event counts are reported separately and are not folded into full or
partial precision/recall.

Measurement fields use `{ "available": true, "value": ..., "source": ... }`.
Unavailable fields must use `{ "available": false, "value": null, "source": ... }`.
Supported metric fields are `mcv_mps`, `peak_velocity_mps`, `rom_vertical_cm`,
`partial_zone`, `partial_amplitude_pct`, `phase_durations_s`, and
`phase_boundaries_s`. Phase maps contain the profile-specific phase IDs, each
with its own measurement object. Precomputed TUT, VL, count accuracy, precision,
recall, or other aggregate scores are rejected. MCV is admitted only for FULL
events; TUT and VL are derived from raw full-event phase/velocity pairs.

The evaluator reports exact-series accuracy over every truth set, so abstentions,
errors, and missing prediction rows are non-exact. Count MAE is shown only for
paired outputs, with its paired denominator and missing-output count; it cannot
pass when output is missing. Event false positives and false negatives are
reported separately. Metrics and gates are grouped by exercise and condition.

## Gate interpretation

The fixed count/event thresholds are copied from `docs/video-technique/MERCADO_FIABILIDAD.md`
§6: basic exercises use exact-series ≥95%, count MAE ≤0.10, event precision and
recall ≥99%, and safe coverage ≥80%; other admitted exercises use ≥90%, ≤0.25,
≥98%/≥98%, and ≥70%. Safe claims additionally require ≥99% exact series,
≥99% event precision, and a 95% lower confidence bound ≥99%.

Physical limits are applied only to manifest-bound paired references: MCV
MAE/RMSE/absolute bias/95% limits are 0.03/0.04/0.02/0.10 m/s; peak-speed
MAE/RMSE are 0.10/0.15 m/s; vertical-ROM MAE/limits of agreement are 2/5 cm;
phase duration MAE is 0.15 s and phase-boundary P95 absolute error is 0.30 s;
TUT is 0.20 s per repetition and `max(1.0 s, 5% of reference set duration)`
per set; VL uses `100*(1 - last valid full-rep MCV / max valid full-rep MCV)`
with MAE/P95 absolute error/absolute bias limits 5/10/2 percentage points.

There is no preregistered §6 numeric limit for partial-zone/amplitude agreement;
the evaluator reports those diagnostics and leaves that gate `INCOMPLETE`. The
evaluator also leaves the safe 95% CI gate `INCOMPLETE`: a validated paired
participant-cluster bootstrap is not implemented, and reps are not treated as
independent observations. `NOT_ESTIMABLE` is used for zero denominators. These
states never count as approval. Only a fully supported external-real result can
produce `PASS`; a threshold failure produces `FAIL`.

## Command line

Run from any directory with an explicit interpreter and all three independent
pins. Obtain the expected hashes from the preregistration/review process; never
copy a file's current hash into its own manifest as an automatic trust step.

```text
python scripts/video_technique/competitive_acceptance.py \
  --manifest acceptance-manifest.json \
  --truth truth.jsonl \
  --predictions predictions.jsonl \
  --expected-manifest-sha256 <reviewed-sha256> \
  --expected-truth-sha256 <reviewed-sha256> \
  --expected-predictions-sha256 <reviewed-sha256> \
  --out acceptance-report.json
```

Exit codes: `0` PASS, `1` FAIL, `3` INCOMPLETE/NOT_ESTIMABLE, `2` invalid or
unverified input. The report is created exclusively; existing output is never
overwritten.
