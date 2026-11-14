"""Analysis service – faithful port of services/analysisService.ts"""
from __future__ import annotations
import math
from datetime import datetime, timezone, timedelta
from models.common import (
    Program, ExerciseMuscleInfo, Settings, Session, MuscleHierarchy,
    WorkoutLog, ProgramWeek, NutritionLog,
)
from engines.exercise_index import ExerciseIndex
from engines.set_accounting import is_set_effective
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


# ── Weekly tonnage comparison ────────────────────────────

def _get_week_id(d: datetime, start_on: int | None = None) -> str:
    """ISO-like week ID matching the TS getWeekId logic."""
    start = start_on or 1  # default Monday
    diff = (d.weekday() - (start - 1)) % 7
    week_start = d - timedelta(days=diff)
    return week_start.strftime("%Y-%m-%d")


def _normalize_load_mode(raw: dict) -> str | None:
    payload = raw.get("recordedPayloadV3")
    homologated = raw.get("homologatedResultV3")
    outcome = raw.get("setOutcomeV2")
    for candidate in (
        raw.get("loadMode"),
        raw.get("loadModeV2"),
        payload.get("loadInputMode") if isinstance(payload, dict) else None,
        homologated.get("loadMode") if isinstance(homologated, dict) else None,
        outcome.get("loadMode") if isinstance(outcome, dict) else None,
    ):
        if isinstance(candidate, str) and candidate.strip():
            return candidate.strip().upper()
    return None


def _finite_non_negative(value) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return None
    return numeric if math.isfinite(numeric) and numeric >= 0 else None


def _completed_set_external_load_kg(raw: dict, mode: str | None) -> float | None:
    """Resolve external kg matching Android CompletedSetLoadSemantics (body mass excluded)."""
    if mode in {"BODYWEIGHT", "ASSISTED"}:
        return 0.0
    payload = raw.get("recordedPayloadV3")
    payload_external = _finite_non_negative(payload.get("externalLoad")) if isinstance(payload, dict) else None
    original_load = _finite_non_negative(raw.get("originalLoad"))
    raw_weight = _finite_non_negative(raw.get("weight"))
    if mode == "LASTRE":
        return payload_external if payload_external is not None else original_load
    if payload_external is not None:
        return payload_external
    if original_load is not None:
        return original_load
    return raw_weight


def _completed_set_external_tonnage(s) -> float:
    raw = s.model_dump() if hasattr(s, "model_dump") else dict(s)
    if raw.get("skipped") is True or raw.get("isWarmup") is True or raw.get("type") == "warmup":
        return 0.0
    # kg × repetitions only: timed work and current body mass are not historical tonnage.
    dur = _finite_non_negative(raw.get("completedDuration"))
    if dur is not None and dur > 0:
        return 0.0
    payload = raw.get("recordedPayloadV3")
    reps = raw.get("completedReps")
    if reps is None:
        reps = raw.get("reps")
    if reps is None and isinstance(payload, dict):
        reps = payload.get("completedReps")
    time_seconds = _finite_non_negative(raw.get("timeSeconds"))
    if (reps is None or reps <= 0) and time_seconds is not None and time_seconds > 0:
        return 0.0

    mode = _normalize_load_mode(raw)
    ext_load = _completed_set_external_load_kg(raw, mode)
    vol = 0.0
    if ext_load is not None and isinstance(reps, (int, float)) and not isinstance(reps, bool) and reps > 0:
        vol += ext_load * reps

    for drop in raw.get("dropSets") or ():
        if not isinstance(drop, dict) or mode in {"BODYWEIGHT", "ASSISTED"}:
            continue
        drop_w = _finite_non_negative(drop.get("weight"))
        drop_reps = drop.get("reps")
        if drop_w is not None and isinstance(drop_reps, (int, float)) and not isinstance(drop_reps, bool) and drop_reps > 0:
            vol += drop_w * drop_reps

    for rp in raw.get("restPauses") or ():
        if not isinstance(rp, dict) or ext_load is None:
            continue
        rp_reps = rp.get("reps")
        if isinstance(rp_reps, (int, float)) and not isinstance(rp_reps, bool) and rp_reps > 0:
            vol += ext_load * rp_reps

    return vol


def calculate_weekly_tonnage_comparison(
    history: list[WorkoutLog],
    settings: Settings,
    today: datetime,
) -> dict:
    if today.tzinfo is None:
        raise ValueError("An explicit timezone is required")
    cw_id = _get_week_id(today, settings.startWeekOn)
    pw_start = datetime.fromisoformat(cw_id) - timedelta(days=7)
    pw_id = pw_start.strftime("%Y-%m-%d")

    current = previous = 0.0
    grouped: dict[str, list[WorkoutLog]] = {}
    for item in history:
        grouped.setdefault(item.id, []).append(item)
    seen: set[str] = set()
    for log in history:
        if any(item != log for item in grouped[log.id]):
            continue  # A conflicting source identity cannot authoritatively contribute either value.
        if log.id in seen:
            continue
        seen.add(log.id)
        try:
            ld = datetime.fromisoformat(log.date.replace("Z", "+00:00"))
        except Exception:
            continue
        if ld.tzinfo is None:
            ld = ld.replace(tzinfo=timezone.utc)
        if ld > today:
            continue
        lid = _get_week_id(ld, settings.startWeekOn)
        if log.completedExercises:
            # Recompute whenever set-level evidence exists; never mix this with
            # the stored aggregate, which may include synthetic body mass.
            vol = sum(_completed_set_external_tonnage(s) for ex in log.completedExercises for s in ex.sets)
        else:
            # Legacy logs may predate per-set details. Match Android/iOS by
            # preserving their aggregate only when the log has no exercises.
            raw_log = log.model_dump()
            vol = _finite_non_negative(raw_log.get("totalVolume")) or 0.0
        if lid == cw_id:
            current += vol
        elif lid == pw_id:
            previous += vol

    return {"current": round(current), "previous": round(previous)}
