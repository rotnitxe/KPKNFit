"""Fatigue service – faithful port of services/fatigueService.ts (AUGE v2.0)"""
from __future__ import annotations
import math
from models.common import (
    ExerciseMuscleInfo, ExerciseSet, Session, CompletedExercise, Settings,
)
from engines.exercise_index import ExerciseIndex


# ── Dynamic AUGE metrics ───────────────────────────────────

def get_dynamic_auge_metrics(
    info: ExerciseMuscleInfo | None,
    custom_name: str | None = None,
) -> dict[str, float]:
    if info:
        efc = info.efc if info.efc is not None else (4.0 if info.type.value == "Básico" else 2.5 if info.type.value == "Accesorio" else 1.5)
        ssc = info.ssc if info.ssc is not None else (info.axialLoadFactor if info.axialLoadFactor is not None else (1.0 if info.type.value == "Básico" else 0.1))
        cnc = info.cnc if info.cnc is not None else (4.0 if info.type.value == "Básico" else 2.5 if info.type.value == "Accesorio" else 1.5)
    else:
        return {"efc": 2.5, "ssc": 0.1, "cnc": 2.5}

    if info.efc is not None and info.cnc is not None and info.ssc is not None:
        return {"efc": info.efc, "ssc": info.ssc, "cnc": info.cnc}

    name = (custom_name or info.name).lower()

    # Base dictionary
    if "peso muerto" in name or "deadlift" in name:
        efc, ssc, cnc = 5.0, 2.0, 5.0
        if "rumano" in name or "rdl" in name:
            efc, ssc, cnc = 4.2, 1.8, 4.0
        if "sumo" in name:
            efc, ssc, cnc = 4.8, 1.6, 4.8
    elif "sentadilla" in name or "squat" in name:
        efc, ssc, cnc = 4.5, 1.5, 4.5
        if "frontal" in name or "front" in name:
            efc, ssc, cnc = 4.2, 1.2, 4.5
        if "búlgara" in name or "bulgarian" in name:
            efc, ssc, cnc = 3.8, 0.8, 3.5
        if "hack" in name:
            efc, ssc, cnc = 3.5, 0.4, 3.0
    elif "press militar" in name or "ohp" in name:
        efc, ssc, cnc = 4.0, 1.5, 4.2
    elif "press banca" in name or "bench press" in name:
        efc, ssc, cnc = 3.8, 0.3, 3.8
    elif "dominada" in name or "pull-up" in name:
        efc, ssc, cnc = 4.0, 0.2, 4.0
    elif "remo" in name or "row" in name:
        efc, ssc, cnc = 4.2, 1.6, 4.0
        if "seal" in name or "pecho apoyado" in name:
            efc, ssc, cnc = 3.2, 0.1, 2.5
    elif "hip thrust" in name or "puente" in name:
        efc, ssc, cnc = 3.5, 0.5, 3.0
    elif "clean" in name or "snatch" in name:
        efc, ssc, cnc = 4.8, 1.8, 5.0

    # Equipment modifiers
    eq = info.equipment.value if info else ""
    if "mancuerna" in name or eq == "Mancuerna":
        cnc = min(5.0, cnc + 0.2)
        ssc = max(0.0, ssc - 0.2)
    elif "smith" in name or "multipower" in name:
        cnc = max(1.0, cnc - 0.5)
        efc = max(1.0, efc - 0.2)
    elif "polea" in name or "cable" in name or eq == "Polea":
        cnc = max(1.0, cnc - 0.3)
        efc = min(5.0, efc + 0.2)

    # Technique modifiers
    if "pausa" in name or "paused" in name:
        cnc = min(5.0, cnc + 0.3)
        efc = min(5.0, efc + 0.5)
    if "déficit" in name or "deficit" in name:
        ssc = min(2.0, ssc + 0.2)
        efc = min(5.0, efc + 0.3)
    if "parcial" in name or "rack pull" in name or "block" in name:
        ssc = min(2.0, ssc + 0.2)
        efc = max(1.0, efc - 0.2)

    return {"efc": efc, "ssc": ssc, "cnc": cnc}


# ── Helpers ───────────────────────────────────────────────

def _get_effective_rpe(s: dict | ExerciseSet) -> float:
    """Translate any intensity representation to an effective RPE."""
    if isinstance(s, ExerciseSet):
        s = s.model_dump()

    base = 7.0
    if s.get("completedRPE") is not None:
        base = s["completedRPE"]
    elif s.get("targetRPE") is not None:
        base = s["targetRPE"]
    elif s.get("completedRIR") is not None:
        base = 10 - s["completedRIR"]
    elif s.get("targetRIR") is not None:
        base = 10 - s["targetRIR"]

    if s.get("isFailure") or s.get("performanceMode") == "failure" or s.get("intensityMode") == "failure" or s.get("isAmrap"):
        base = max(base, 11)

    technique_bonus = 0.0
    ds = s.get("dropSets")
    if ds and len(ds) > 0:
        technique_bonus += len(ds) * 1.5
    rp = s.get("restPauses")
    if rp and len(rp) > 0:
        technique_bonus += len(rp) * 1.0
    pr = s.get("partialReps")
    if pr and pr > 0:
        technique_bonus += 0.5

    if technique_bonus > 0 and base < 10:
        base = 10

    return base + technique_bonus


# ── Battery tanks ─────────────────────────────────────────

def calculate_personalized_battery_tanks(settings: Settings | None) -> dict[str, float]:
    base_musc, base_cns, base_spinal = 300.0, 250.0, 4000.0

    level = "Advanced"
    style = "bodybuilder"
    if settings:
        if settings.athleteScore:
            level = settings.athleteScore.profileLevel or "Advanced"
            style = (settings.athleteScore.trainingStyle or settings.athleteType.value or "bodybuilder").lower()
        else:
            style = (settings.athleteType.value or "bodybuilder").lower()

    level_mult = 0.8 if level == "Beginner" else 1.2

    cns_m, musc_m, spine_m = 1.0, 1.0, 1.0
    if "powerlift" in style:
        cns_m, spine_m, musc_m = 1.3, 1.4, 0.9
    elif "bodybuild" in style or "aesthetics" in style:
        cns_m, spine_m, musc_m = 0.9, 0.9, 1.3
    else:
        cns_m, spine_m, musc_m = 1.15, 1.15, 1.15

    return {
        "muscularTank": base_musc * level_mult * musc_m,
        "cnsTank": base_cns * level_mult * cns_m,
        "spinalTank": base_spinal * level_mult * spine_m,
    }


# ── Single set drain ─────────────────────────────────────

def calculate_set_battery_drain(
    set_data: dict | ExerciseSet,
    info: ExerciseMuscleInfo | None,
    tanks: dict[str, float],
    accumulated_sets: int = 0,
    rest_time: float = 90,
) -> dict[str, float]:
    if isinstance(set_data, ExerciseSet):
        set_data = set_data.model_dump()

    auge = get_dynamic_auge_metrics(info, set_data.get("exerciseName") or (info.name if info else None))
    rpe = _get_effective_rpe(set_data)
    reps = set_data.get("completedReps") or set_data.get("targetReps") or set_data.get("reps") or 10
    is_compound = info and (info.type.value == "Básico" or (info.tier and info.tier.value == "T1"))

    # Biomechanical U-curve
    reps_cns, reps_musc, reps_spine = 1.0, 1.0, 1.0
    if reps <= 4:
        if is_compound:
            reps_cns, reps_spine, reps_musc = 1.8, 1.6, 0.7
        else:
            reps_cns, reps_spine, reps_musc = 1.2, 0.1, 0.8
    elif reps >= 16:
        reps_cns, reps_spine, reps_musc = 0.7, 0.5, 1.4

    # Intensity multiplier
    if rpe >= 11:
        intensity_mult = 1.8
    elif rpe >= 10:
        intensity_mult = 1.5
    elif rpe >= 9:
        intensity_mult = 1.15
    elif rpe >= 8:
        intensity_mult = 1.0
    elif rpe >= 6:
        intensity_mult = 0.7
    else:
        intensity_mult = 0.4

    # Junk volume toxicity
    junk_mult = 1.0
    if accumulated_sets >= 6:
        junk_mult = 1.0 + (accumulated_sets - 5) * 0.35

    # Rest factor
    if rest_time <= 45:
        rest_factor = 1.3
    elif rest_time >= 180:
        rest_factor = 0.85
    else:
        rest_factor = 1.0

    raw_musc = auge["efc"] * reps_musc * intensity_mult * junk_mult * rest_factor * 8.0
    raw_cns = auge["cnc"] * reps_cns * intensity_mult * rest_factor * 6.0
    weight_factor = (set_data.get("weight") or 0) * 0.05 if set_data.get("weight") else auge["efc"] * 2.0
    raw_spinal = auge["ssc"] * reps_spine * intensity_mult * weight_factor * 4.0

    return {
        "muscularDrainPct": (raw_musc / tanks["muscularTank"]) * 100 if tanks["muscularTank"] else 0,
        "cnsDrainPct": (raw_cns / tanks["cnsTank"]) * 100 if tanks["cnsTank"] else 0,
        "spinalDrainPct": (raw_spinal / tanks["spinalTank"]) * 100 if tanks["spinalTank"] else 0,
    }


# ── Predicted session drain ──────────────────────────────

def calculate_predicted_session_drain(
    session: Session,
    exercise_list: list[ExerciseMuscleInfo],
    settings: Settings | None = None,
) -> dict:
    tanks = calculate_personalized_battery_tanks(settings)
    idx = ExerciseIndex(exercise_list)
    total_cns, total_musc, total_spinal = 0.0, 0.0, 0.0
    muscle_vol: dict[str, int] = {}

    exercises = [e for p in session.parts for e in p.exercises] if session.parts else session.exercises

    for ex in (exercises or []):
        info = idx.find(ex.exerciseDbId, ex.name)
        primary_muscle = "General"
        if info:
            pm = next((m for m in info.involvedMuscles if m.role.value == "primary"), None)
            if pm:
                primary_muscle = pm.muscle

        acc = muscle_vol.get(primary_muscle, 0)
        for s in (ex.sets or []):
            acc += 1
            drain = calculate_set_battery_drain(s, info, tanks, acc, ex.restTime or 90)
            total_musc += drain["muscularDrainPct"]
            total_cns += drain["cnsDrainPct"]
            total_spinal += drain["spinalDrainPct"]

        muscle_vol[primary_muscle] = acc

    return {
        "cnsDrain": round(min(100, total_cns)),
        "muscleBatteryDrain": round(min(100, total_musc)),
        "spinalDrain": round(min(100, total_spinal)),
        "totalSpinalScore": round(total_spinal * 10),
    }


# ── Legacy helpers ────────────────────────────────────────

def calculate_set_stress(set_data: dict | ExerciseSet, info: ExerciseMuscleInfo | None, rest_time: float = 90) -> float:
    tanks = calculate_personalized_battery_tanks(None)
    drain = calculate_set_battery_drain(set_data, info, tanks, 0, rest_time)
    return drain["muscularDrainPct"]


def is_set_effective(set_data: dict | ExerciseSet) -> bool:
    return _get_effective_rpe(set_data) >= 6


# ── Completed session stress ─────────────────────────────

def calculate_completed_session_stress(
    completed_exercises: list[CompletedExercise],
    exercise_list: list[ExerciseMuscleInfo],
) -> float:
    tanks = calculate_personalized_battery_tanks(None)
    idx = ExerciseIndex(exercise_list)
    total = 0.0
    muscle_vol: dict[str, int] = {}

    for ex in completed_exercises:
        info = idx.find(ex.exerciseDbId, ex.exerciseName)
        primary = "General"
        if info:
            pm = next((m for m in info.involvedMuscles if m.role.value == "primary"), None)
            if pm:
                primary = pm.muscle

        acc = muscle_vol.get(primary, 0)
        for s in ex.sets:
            acc += 1
            drain = calculate_set_battery_drain(s, info, tanks, acc, 90)
            total += drain["cnsDrainPct"] + drain["muscularDrainPct"] + drain["spinalDrainPct"]
        muscle_vol[primary] = acc

    return total
