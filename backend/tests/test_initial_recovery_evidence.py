from datetime import datetime, timezone

from engines.recovery_engine import _initial_recovery_contribution
from models.common import InitialRecoveryEvidence, Settings, WorkoutLog


def evidence(captured: int = 1_000_000):
    return InitialRecoveryEvidence(
        capturedAtMs=captured,
        coveredFromMs=captured - 72 * 3_600_000,
        coveredToMs=captured,
        expiresAtMs=captured + 14 * 24 * 3_600_000,
        sessions=2,
        activityType="MIXED",
        intensity="HARD",
        muscularScore=60,
        systemScore=70,
        structureScore=80,
        confidence=77,
        sourceId="wizard-source",
    )


def log_at(ms: int) -> WorkoutLog:
    date = datetime.fromtimestamp(ms / 1000, tz=timezone.utc).isoformat()
    return WorkoutLog(id=str(ms), date=date)


def test_missing_evidence_is_neutral():
    result = _initial_recovery_contribution(Settings(), 2_000_000, [])
    assert result["estimated"] is False
    assert result["muscular"] is None


def test_expired_evidence_is_ignored():
    settings = Settings(initialRecoveryEvidence=evidence())
    result = _initial_recovery_contribution(settings, settings.initialRecoveryEvidence.expiresAtMs, [])
    assert result["estimated"] is False
    assert result["muscular"] is None


def test_real_log_inside_covered_interval_suppresses_bootstrap():
    captured = 10 * 24 * 3_600_000
    settings = Settings(initialRecoveryEvidence=evidence(captured))
    result = _initial_recovery_contribution(settings, captured + 3_600_000, [log_at(captured - 3_600_000)])
    assert result["estimated"] is False
    assert result["confidence"] == 65


def test_log_after_covered_interval_does_not_reinterpret_evidence():
    captured = 10 * 24 * 3_600_000
    settings = Settings(initialRecoveryEvidence=evidence(captured))
    result = _initial_recovery_contribution(settings, captured + 3 * 24 * 3_600_000, [log_at(captured + 24 * 3_600_000)])
    assert result["estimated"] is True
    assert result["confidence"] == 77
    assert result["muscular"] > 60
