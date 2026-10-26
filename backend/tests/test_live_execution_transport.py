from models.common import WorkoutLog


def test_execution_evidence_survives_history_roundtrip():
    ref = {"executionStartedAtMs": 123, "variant": "B", "exerciseOccurrenceId": "e", "setId": "s"}
    evidence = {
        "id": "log", "date": "2026-10-25",
        "preparationReports": {"warmup": {"value": 6, "unit": "REPS"}},
        "omittedSeries": ["e_1"], "mediaSetBindings": {"video": ref},
        "completedExercises": [{"exerciseId": "e", "sets": [{"id": "actual", "reps": 6,
            "sourceRef": ref, "techniqueStatus": "SKIPPED", "recordedPayloadV3": {"actualValue": 6}}]}],
    }
    result = WorkoutLog.model_validate(evidence).model_dump()
    assert result["preparationReports"] == evidence["preparationReports"]
    assert result["omittedSeries"] == ["e_1"]
    assert result["mediaSetBindings"]["video"] == ref
    assert result["completedExercises"][0]["sets"][0]["sourceRef"] == ref
