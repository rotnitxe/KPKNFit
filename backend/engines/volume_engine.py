"""Volume calculator – faithful port of services/volumeCalculator.ts"""
from __future__ import annotations
from models.common import (
    AthleteProfileScore, Settings, Session, ExerciseMuscleInfo,
    MuscleRole, PostSessionFeedback,
)
from engines.exercise_index import ExerciseIndex

# ── Constants (Módulos 4 y 5) ────────────────────────────

PHASE_FACTORS: dict[str, float] = {
    "Acumulación": 1.0,
    "Intensificación": 0.75,
    "Realización": 0.50,
    "Descarga": 0.40,
    "Custom": 1.0,
}

INTENSITY_FACTORS: dict[str, float] = {
    "Failure": 0.6,
    "RIR_High": 1.0,
    "RIR_Low": 1.2,
}

MUSCLE_ROLE_MULTIPLIERS: dict[str, float] = {
    "primary": 1.0,
    "secondary": 0.5,
    "stabilizer": 0.0,
    "neutralizer": 0.0,
}

FATIGUE_ROLE_MULTIPLIERS: dict[str, float] = {
    "primary": 1.0,
    "secondary": 0.6,
    "stabilizer": 0.3,
    "neutralizer": 0.15,
}

MAX_PRODUCTIVE_SESSION_SETS = 12
WARNING_THRESHOLD = 10


# ── Public API ────────────────────────────────────────────

def calculate_weekly_volume(
    athlete_score: AthleteProfileScore | None,
    settings: Settings,
    phase: str = "Acumulación",
) -> dict:
    if not athlete_score:
        return {
            "minSets": 10, "maxSets": 20, "optimalSets": 15,
            "type": "sets",
            "reasoning": "Perfil no calibrado. Usando estándar genérico de Schoenfeld (10-20 series).",
        }

    profile_level = athlete_score.profileLevel
    training_profile = settings.trainingProfile or "Aesthetics"

    if training_profile == "Powerlifting":
        is_adv = profile_level == "Advanced"
        min_nl = 1300 if is_adv else 1000
        max_nl = 2500 if is_adv else 1300
        pf = PHASE_FACTORS.get(phase, 1.0)
        wk_min = round((min_nl / 4) * pf)
        wk_max = round((max_nl / 4) * pf)
        return {
            "minSets": wk_min, "maxSets": wk_max,
            "optimalSets": round((wk_min + wk_max) / 2),
            "type": "lifts",
            "reasoning": f"Motor Powerlifting (Sheiko): Perfil {profile_level}. Fase {phase} ({pf}x).",
        }

    base_min, base_max = (14, 22) if profile_level == "Advanced" else (10, 14)
    f_fase = PHASE_FACTORS.get(phase, 1.0)
    intensity_pref = settings.preferredIntensity or "RIR_High"
    f_int = INTENSITY_FACTORS.get(intensity_pref, 1.0)

    calc_min = base_min * f_fase * f_int
    calc_max = base_max * f_fase * f_int
    final_min = max(1, round(calc_min))
    final_max = max(final_min + 2, round(calc_max))
    optimal = round((final_min + final_max) / 2)

    return {
        "minSets": final_min, "maxSets": final_max, "optimalSets": optimal,
        "type": "sets",
        "reasoning": (
            f"Motor Hipertrofia: Base {profile_level} ({base_min}-{base_max}) "
            f"* Fase {phase} ({f_fase}x) * Intensidad {intensity_pref} ({f_int}x)."
        ),
    }


def validate_session_volume(sets_in_session: int, muscle_group: str) -> dict:
    if sets_in_session > MAX_PRODUCTIVE_SESSION_SETS:
        return {
            "isValid": False,
            "message": (
                f"Volumen Basura: {sets_in_session} series de {muscle_group} en una sesión "
                f"excede el límite productivo ({MAX_PRODUCTIVE_SESSION_SETS}). Considera dividir en 2 días."
            ),
        }
    if sets_in_session >= WARNING_THRESHOLD:
        return {
            "isValid": True,
            "message": (
                f"Estás cerca del límite por sesión ({sets_in_session}/{MAX_PRODUCTIVE_SESSION_SETS}). "
                "Asegúrate de nutrirte bien peri-entrenamiento."
            ),
        }
    return {"isValid": True}


def calculate_fractional_volume(exercises: list[dict]) -> float:
    total = 0.0
    for ex in exercises:
        mult = 1.0 if ex.get("muscleRole") == "primary" else 0.5
        total += ex.get("sets", 0) * mult
    return total


def calculate_volume_adjustment(
    muscle: str,
    feedback_history: list[PostSessionFeedback],
) -> dict:
    if not feedback_history:
        return {"factor": 1.0, "suggestion": "", "status": "optimal"}

    muscle_logs = [f for f in feedback_history if f.feedback and muscle in f.feedback]
    if not muscle_logs:
        return {"factor": 1.0, "suggestion": "", "status": "optimal"}

    recent = sorted(muscle_logs, key=lambda f: f.date, reverse=True)[:3]
    avg_doms = sum(r.feedback[muscle].doms for r in recent) / len(recent)
    avg_str = sum(r.feedback[muscle].strengthCapacity for r in recent) / len(recent)

    if avg_doms >= 3.5 or avg_str <= 5:
        return {
            "factor": 0.85, "status": "recovery_debt",
            "suggestion": f"Recuperación lenta en {muscle} (DOMS altos). Sugerimos reducir volumen un 15% esta semana.",
        }
    if avg_doms <= 1.5 and avg_str >= 8:
        return {
            "factor": 1.1, "status": "undertraining",
            "suggestion": f"{muscle} recupera sobrado. Podrías tolerar un +10% de volumen o intensidad.",
        }
    return {"factor": 1.0, "status": "optimal", "suggestion": f"Carga óptima para {muscle}. Mantén el plan."}


# ── Muscle normalizer ────────────────────────────────────

def normalize_muscle_group(specific_muscle: str) -> str:
    low = specific_muscle.lower().strip()

    # Hombros
    if "posterior" in low and ("deltoides" in low or "hombro" in low):
        return "Deltoides Posterior"
    if ("lateral" in low or "medio" in low) and ("deltoides" in low or "hombro" in low):
        return "Deltoides Lateral"
    if ("anterior" in low or "frontal" in low) and ("deltoides" in low or "hombro" in low):
        return "Deltoides Anterior"
    if "deltoides" in low or "hombro" in low:
        return "Deltoides Anterior"

    # Espalda
    if any(k in low for k in ("trapecio", "romboides", "espinal", "alta")):
        return "Trapecio"
    if any(k in low for k in ("dorsal", "lat", "redondo", "ancho")):
        return "Dorsal"
    if any(k in low for k in ("erector", "lumbar", "baja")):
        return "Espalda Baja"
    if "espalda" in low:
        return "Dorsal"

    # Brazos
    if "tríceps" in low or "triceps" in low:
        return "Tríceps"
    if ("bíceps" in low or "biceps" in low or "braquial" in low) and "femoral" not in low:
        return "Bíceps"
    if "antebrazo" in low:
        return "Antebrazo"

    # Pierna
    if any(k in low for k in ("femoral", "semitendinoso", "semimembranoso", "isquio")):
        return "Isquiosurales"
    if any(k in low for k in ("cuádriceps", "cuadriceps", "recto femoral", "vasto")):
        return "Cuádriceps"
    if "glúteo" in low or "gluteo" in low:
        return "Glúteos"
    if any(k in low for k in ("gemelo", "sóleo", "soleo", "pantorrilla")):
        return "Gemelos"

    # Core / Otros
    if "pectoral" in low or "pecho" in low:
        return "Pectoral"
    if any(k in low for k in ("abdominal", "oblicuo", "core")):
        return "Abdominales"

    return specific_muscle[0].upper() + specific_muscle[1:] if specific_muscle else specific_muscle


# ── Unified muscle volume ─────────────────────────────────

def calculate_unified_muscle_volume(
    sessions: list[Session],
    exercise_list: list[ExerciseMuscleInfo],
) -> list[dict]:
    volume_map: dict[str, float] = {}
    idx = ExerciseIndex(exercise_list)

    for session in sessions:
        if not session:
            continue
        all_exercises = (
            [e for p in session.parts for e in p.exercises]
            if session.parts
            else session.exercises
        )
        for exercise in all_exercises:
            if not exercise or not exercise.sets:
                continue
            valid_count = sum(
                1 for s in exercise.sets
                if s and (
                    (s.reps and s.reps > 0)
                    or (s.weight and s.weight > 0)
                    or s.targetReps
                )
            )
            if valid_count == 0:
                continue

            db_info = idx.find(exercise.exerciseDbId, exercise.name)
            involved = (
                db_info.involvedMuscles if db_info else (exercise.targetMuscles or [])
            )
            if not involved:
                continue

            unique: dict[str, float] = {}
            for m in involved:
                if not m or not m.muscle:
                    continue
                name = normalize_muscle_group(m.muscle)
                mult = MUSCLE_ROLE_MULTIPLIERS.get(m.role.value if hasattr(m.role, 'value') else m.role, 0.5)
                if mult > unique.get(name, 0):
                    unique[name] = mult

            for name, mult in unique.items():
                volume_map[name] = volume_map.get(name, 0) + valid_count * mult

    result = [
        {"muscleGroup": mg, "displayVolume": round(vol * 10) / 10}
        for mg, vol in volume_map.items()
    ]
    result.sort(key=lambda x: x["displayVolume"], reverse=True)
    return result
