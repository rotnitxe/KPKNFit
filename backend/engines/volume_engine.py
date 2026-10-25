"""Descriptive set accounting; role weights are display conventions, not prescriptions."""
from __future__ import annotations
from models.common import (
    AthleteProfileScore, Settings, Session, ExerciseMuscleInfo,
    MuscleRole,
)
from engines.exercise_index import ExerciseIndex

MUSCLE_ROLE_MULTIPLIERS: dict[str, float] = {
    "primary": 1.0,
    "secondary": 0.5,
    "stabilizer": 0.0,
    "neutralizer": 0.0,
}

def validate_session_volume(sets_in_session: int, muscle_group: str) -> dict:
    # Structural validity only. No universal productive ceiling or physiological prescription.
    return {"isValid": sets_in_session >= 0, "message": "Recuento declarado; no estima tolerancia ni productividad."}


def calculate_fractional_volume(exercises: list[dict]) -> float:
    total = 0.0
    for ex in exercises:
        mult = MUSCLE_ROLE_MULTIPLIERS.get(ex.get("muscleRole"), 0.0)
        total += ex.get("sets", 0) * mult
    return total




# ── Muscle normalizer ────────────────────────────────────

def normalize_muscle_group(specific_muscle: str) -> str:
    low = specific_muscle.lower().strip()

    # The mobile volume contract aggregates deltoid heads into one bucket.
    if "deltoides" in low or "hombro" in low:
        return "Deltoides"

    # Espalda
    if "romboide" in low:
        return "Romboides"
    if any(k in low for k in ("trapecio", "alta")):
        return "Trapecio"
    if any(k in low for k in ("dorsal", "lat", "redondo", "ancho")):
        return "Dorsales"
    if any(k in low for k in ("erector", "lumbar", "baja")):
        return "Erectores Espinales"
    if "espalda" in low:
        return "Dorsales"

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
    if "glúteo" in low or "gluteo" in low or "tensor fascia" in low or "tensor de la fascia" in low:
        return "Glúteos"
    if any(k in low for k in ("gemelo", "sóleo", "soleo", "pantorrilla")):
        return "Pantorrillas"

    # Core / Otros
    if "pectoral" in low or "pecho" in low:
        return "Pectorales"
    if "core" in low or "transverso" in low or "serrato" in low:
        return "Core"
    if any(k in low for k in ("abdominal", "abdomen", "oblicuo")):
        return "Abdomen"

    return specific_muscle[0].upper() + specific_muscle[1:] if specific_muscle else specific_muscle


# ── Unified muscle volume ─────────────────────────────────

# ── Adaptive recalibration (EMA, half-life, regression) ───────

MUSCLE_ALIASES: dict[str, list[str]] = {
    "Abdomen": ["Abdominales"],
    "Pantorrillas": ["Gemelos"],
}










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
                mult = MUSCLE_ROLE_MULTIPLIERS.get(m.role.value if hasattr(m.role, 'value') else m.role, 0.0)
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
