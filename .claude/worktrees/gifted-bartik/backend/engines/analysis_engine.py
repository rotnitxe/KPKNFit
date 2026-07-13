"""Analysis service – faithful port of services/analysisService.ts"""
from __future__ import annotations
import math
from datetime import datetime, timezone, timedelta
from models.common import (
    Program, ExerciseMuscleInfo, Settings, Session, MuscleHierarchy,
    WorkoutLog, ProgramWeek, NutritionLog, BodyProgressLog,
)
from engines.exercise_index import ExerciseIndex
from engines.fatigue_engine import is_set_effective, calculate_completed_session_stress
from engines.volume_engine import MUSCLE_ROLE_MULTIPLIERS


def _parse_date_ms(d: str) -> float:
    try:
        return datetime.fromisoformat(d.replace("Z", "+00:00")).timestamp() * 1000
    except Exception:
        return 0


def _create_child_to_parent(hierarchy: MuscleHierarchy) -> dict[str, str]:
    m: dict[str, str] = {}
    if not hierarchy or not hierarchy.bodyPartHierarchy:
        return m
    for body_part, subgroups in hierarchy.bodyPartHierarchy.items():
        for item in subgroups:
            if isinstance(item, dict):
                for sg_name, children in item.items():
                    for child in children:
                        m[child] = sg_name
    return m


# ── Average volume for weeks ─────────────────────────────

def calculate_average_volume_for_weeks(
    weeks: list[ProgramWeek],
    exercise_list: list[ExerciseMuscleInfo],
    muscle_hierarchy: MuscleHierarchy,
    mode: str = "complex",
) -> list[dict]:
    if not weeks:
        return []

    idx = ExerciseIndex(exercise_list)
    child_map = _create_child_to_parent(muscle_hierarchy)
    get_group = lambda m: child_map.get(m, m)

    totals: dict[str, dict] = {}

    for week in weeks:
        for session in week.sessions:
            exercises = (
                [e for p in session.parts for e in p.exercises]
                if session.parts
                else session.exercises
            )
            session_freq: dict[str, dict] = {}

            for exercise in exercises:
                ex_data = idx.find(exercise.exerciseDbId, exercise.name)
                if not ex_data or not ex_data.involvedMuscles:
                    continue
                eff_sets = sum(1 for s in exercise.sets if is_set_effective(s))
                if eff_sets == 0:
                    continue

                has_direct = any(
                    _is_direct_effective(s) for s in exercise.sets
                )

                highest: dict[str, dict] = {}
                for m in ex_data.involvedMuscles:
                    grp = get_group(m.muscle)

                    freq = session_freq.setdefault(grp, {"direct": 0, "indirect": 0})
                    if m.role.value in ("primary", "secondary") and has_direct:
                        freq["direct"] = max(freq["direct"], 1.0 if m.role.value == "primary" else 0.5)
                    elif m.role.value in ("stabilizer", "neutralizer"):
                        freq["indirect"] = 1.0

                    if mode == "simple" and m.role.value != "primary":
                        continue
                    mult = 1.0 if mode == "simple" else MUSCLE_ROLE_MULTIPLIERS.get(m.role.value, 0.5)
                    ex_best = highest.get(grp)
                    if not ex_best or ex_best["mult"] < mult:
                        highest[grp] = {"mult": mult, "role": m.role.value}

                for grp, data in highest.items():
                    t = totals.setdefault(grp, {"vol": 0, "freq_d": 0, "freq_i": 0})
                    t["vol"] += eff_sets * data["mult"]

            for grp, impact in session_freq.items():
                t = totals.setdefault(grp, {"vol": 0, "freq_d": 0, "freq_i": 0})
                t["freq_d"] += impact["direct"]
                if impact["direct"] == 0:
                    t["freq_i"] += impact["indirect"]

    n = len(weeks)
    return sorted(
        [
            {
                "muscleGroup": mg,
                "displayVolume": round((d["vol"] / n) * 10) / 10,
                "totalSets": round(d["vol"] / n),
                "frequency": round((d["freq_d"] / n) * 10) / 10,
                "indirectFrequency": round((d["freq_i"] / n) * 10) / 10,
            }
            for mg, d in totals.items()
            if d["vol"] > 0 or d["freq_i"] > 0
        ],
        key=lambda x: x["displayVolume"],
        reverse=True,
    )


def _is_direct_effective(s) -> bool:
    if not is_set_effective(s):
        return False
    d = s.model_dump() if hasattr(s, "model_dump") else s
    rpe = d.get("rpe") or d.get("completedRPE") or d.get("targetRPE")
    rir = d.get("rir") if d.get("rir") is not None else d.get("completedRIR") if d.get("completedRIR") is not None else d.get("targetRIR")
    if d.get("isFailure") or d.get("intensityMode") == "failure" or d.get("isAmrap") or d.get("performanceMode") == "failed":
        return True
    if rpe is not None and rpe >= 6:
        return True
    if rir is not None and rir <= 4:
        return True
    if rpe is None and rir is None:
        return True
    return False


# ── Session volume ────────────────────────────────────────

def calculate_session_volume(
    session: Session,
    exercise_list: list[ExerciseMuscleInfo],
    muscle_hierarchy: MuscleHierarchy,
    mode: str = "complex",
) -> list[dict]:
    temp = ProgramWeek(id="temp", name="temp", sessions=[session])
    return calculate_average_volume_for_weeks([temp], exercise_list, muscle_hierarchy, mode)


# ── ACWR ──────────────────────────────────────────────────

def calculate_acwr(
    history: list[WorkoutLog],
    settings: Settings,
    exercise_list: list[ExerciseMuscleInfo],
) -> dict:
    if len(history) < 7:
        return {"acwr": 0, "interpretation": "Datos insuficientes", "color": "text-slate-400"}

    today = datetime.now(timezone.utc)
    stress_by_day: dict[str, float] = {}
    for log in history:
        ds = log.date[:10]
        stress = log.sessionStressScore if log.sessionStressScore is not None else calculate_completed_session_stress(log.completedExercises, exercise_list)
        stress_by_day[ds] = stress_by_day.get(ds, 0) + stress

    def daily(d: datetime) -> float:
        return stress_by_day.get(d.strftime("%Y-%m-%d"), 0)

    acute = sum(daily(today - timedelta(days=i)) for i in range(7))
    weekly = [sum(daily(today - timedelta(days=w * 7 + d)) for d in range(7)) for w in range(4)]
    chronic = sum(weekly) / 4

    if chronic < 10:
        return {"acwr": 0, "interpretation": "Carga baja", "color": "text-sky-400"}

    acwr = acute / chronic
    if acwr < 0.8:
        interp, color = "Sub-entrenando", "text-sky-400"
    elif acwr <= 1.3:
        interp, color = "Zona Segura", "text-green-400"
    elif acwr <= 1.5:
        interp, color = "Zona de Riesgo", "text-yellow-400"
    else:
        interp, color = "Alto Riesgo", "text-red-400"

    return {"acwr": round(acwr, 2), "interpretation": interp, "color": color}


# ── Weekly tonnage comparison ────────────────────────────

def _get_week_id(d: datetime, start_on: int | None = None) -> str:
    """ISO-like week ID matching the TS getWeekId logic."""
    start = start_on or 1  # default Monday
    diff = (d.weekday() - (start - 1)) % 7
    week_start = d - timedelta(days=diff)
    return week_start.strftime("%Y-%m-%d")


def calculate_weekly_tonnage_comparison(
    history: list[WorkoutLog],
    settings: Settings,
) -> dict:
    today = datetime.now(timezone.utc)
    cw_id = _get_week_id(today, settings.startWeekOn)
    pw_start = datetime.fromisoformat(cw_id) - timedelta(days=7)
    pw_id = pw_start.strftime("%Y-%m-%d")

    current = previous = 0.0
    for log in history:
        try:
            ld = datetime.fromisoformat(log.date.replace("Z", "+00:00"))
        except Exception:
            continue
        lid = _get_week_id(ld, settings.startWeekOn)
        vol = 0.0
        for ex in log.completedExercises:
            for s in ex.sets:
                w = s.weight or 0
                r = s.completedReps or 0
                dur = s.completedDuration or 0
                if dur > 0:
                    vol += dur * (w if w > 0 else 1)
                else:
                    bw = settings.userVitals.weight or 0 if ex.useBodyweight else 0
                    vol += (w + bw) * r
        if lid == cw_id:
            current += vol
        elif lid == pw_id:
            previous += vol

    return {"current": round(current), "previous": round(previous)}
