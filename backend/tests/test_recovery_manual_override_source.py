"""Oracle: el ajuste manual es fuente ÚNICA de su canal cuando coexiste con la
evidencia inicial (sin doble fatiga/mix), a paridad con Android/iOS.

Contrato que fija (backend/engines/recovery_engine.py, sin cambio de fórmula):
  * sin manuales, la evidencia inicial (sin solape de sesiones) se mezcla en
    los tres canales: `combine(resta 100 - inicial)`;
  * con un ajuste manual per-muscle (`manualMuscleOverridesV2`) ESE canal deja
    de mezclar la estimación inicial (`initial["estimated"] and not
    has_muscular_manual`, línea 590-597): fuente única, nunca manual + inicial;
  * límite documentado (no es paridad total): `models/common.py:234-243` no
    define `manualNeuralBattery` ni `manualSpinalBattery`, así que el backend
    no puede reconocer manuales de sistema/estructura y esos canales siguen
    mezclando la estimación inicial.

Los valores son deterministas: historial vacío (baterías sin fatiga = 100) y
evidencia capturada "ahora" (decaimiento ≈ 0 → 60/70/80 exactos).
"""
from __future__ import annotations

import time

from engines.recovery_engine import calculate_global_batteries
from models.common import (
    DailyWellbeingLog,
    InitialRecoveryEvidence,
    ManualMuscleBatteryOverride,
    Settings,
)

EVIDENCE_MUSCULAR = 60
EVIDENCE_SYSTEM = 70
EVIDENCE_STRUCTURE = 80


def evidence(captured: int) -> InitialRecoveryEvidence:
    return InitialRecoveryEvidence(
        capturedAtMs=captured,
        coveredFromMs=captured - 72 * 3_600_000,
        coveredToMs=captured,
        expiresAtMs=captured + 14 * 24 * 3_600_000,
        sessions=2,
        activityType="MIXED",
        intensity="HARD",
        muscularScore=EVIDENCE_MUSCULAR,
        systemScore=EVIDENCE_SYSTEM,
        structureScore=EVIDENCE_STRUCTURE,
        confidence=77,
        sourceId="wizard-source",
    )


def settings_with_evidence() -> Settings:
    return Settings(initialRecoveryEvidence=evidence(int(time.time() * 1000)))


def batteries(settings: Settings, wellbeing: list[DailyWellbeingLog] | None = None) -> dict:
    return calculate_global_batteries(
        history=[],
        sleep_logs=[],
        daily_wellbeing=wellbeing or [],
        nutrition_logs=[],
        settings=settings,
        exercise_list=[],
    )


def test_initial_evidence_mixes_into_every_channel_without_manual():
    out = batteries(settings_with_evidence())
    # Batería sin fatiga = 100; `combine` resta (100 - inicial).
    assert out["muscular"] == 100 - (100 - EVIDENCE_MUSCULAR)
    assert out["cns"] == 100 - (100 - EVIDENCE_SYSTEM)
    assert out["spinal"] == 100 - (100 - EVIDENCE_STRUCTURE)


def test_muscular_manual_override_is_single_source_no_double_mix():
    manual = DailyWellbeingLog(
        id="w-manual",
        date="2026-09-25",
        manualMuscleOverridesV2={
            "Pectorales": ManualMuscleBatteryOverride(
                battery=90, anchorEpochMs=int(time.time() * 1000)
            )
        },
    )
    out = batteries(settings_with_evidence(), [manual])

    # Canal muscular: la evidencia inicial queda SUPRIMIDA (100, no 60).
    # Fuente única del canal, sin mezclar estimación + ajuste manual.
    assert out["muscular"] == 100

    # Límite documentado: sin campos manuales de sistema/estructura en el
    # modelo, esos canales siguen mezclando (70/80), idéntico al caso sin
    # manual. No se afirma paridad total aquí.
    assert out["cns"] == 100 - (100 - EVIDENCE_SYSTEM)
    assert out["spinal"] == 100 - (100 - EVIDENCE_STRUCTURE)


def test_without_evidence_and_manual_the_channels_stay_engine_derived():
    # Sin evidencia inicial no hay nada que mezclar: manual y sin manual
    # coinciden (100 con historial vacío). El backend expone números crudos,
    # sin metadato «sin datos» (sin cobertura por canal): límite documentado.
    manual = DailyWellbeingLog(
        id="w-manual",
        date="2026-09-25",
        manualMuscleOverridesV2={
            "Pectorales": ManualMuscleBatteryOverride(
                battery=90, anchorEpochMs=int(time.time() * 1000)
            )
        },
    )
    assert batteries(Settings(), [manual]) == batteries(Settings())
