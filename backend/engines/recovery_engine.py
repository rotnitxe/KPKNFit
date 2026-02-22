"""Recovery service – faithful port of services/recoveryService.ts"""
from __future__ import annotations
import math
from datetime import datetime, timezone
from models.common import (
    WorkoutLog, ExerciseMuscleInfo, MuscleHierarchy, SleepLog,
    PostSessionFeedback, DailyWellbeingLog, Settings, WaterLog, NutritionLog,
)
from engines.exercise_index import ExerciseIndex
from engines.fatigue_engine import (
    calculate_set_stress, get_dynamic_auge_metrics,
    calculate_personalized_battery_tanks, calculate_set_battery_drain,
)

# ── Constants ────────────────────────────────────────────

RECOVERY_PROFILES: dict[str, int] = {
    "fast": 24,
    "medium": 48,
    "slow": 72,
    "heavy": 96,
}

MUSCLE_PROFILE_MAP: dict[str, str] = {
    "bíceps": "fast", "tríceps": "fast", "deltoides": "fast",
    "pantorrillas": "fast", "abdomen": "fast", "antebrazo": "fast",
    "pectorales": "medium", "dorsales": "medium", "hombros": "medium",
    "trapecio": "medium", "aductores": "medium", "core": "medium",
    "cuádriceps": "slow", "glúteos": "slow",
    "isquiosurales": "heavy", "espalda baja": "heavy", "erectores espinales": "heavy",
}

ATHLETE_CAPACITY_FLOORS: dict[str, int] = {
    "enthusiast": 500, "hybrid": 650, "calisthenics": 600,
    "bodybuilder": 1000, "powerbuilder": 1100, "powerlifter": 1200,
    "weightlifter": 1000, "parapowerlifter": 1100,
}

MUSCLE_CATEGORY_MAP: dict[str, list[str]] = {
    "pectorales": ["pectoral", "pecho"],
    "dorsales": ["dorsal", "redondo mayor", "espalda alta", "lats"],
    "deltoides": ["deltoides", "hombro", "delts"],
    "bíceps": ["bíceps", "biceps", "braquial", "braquiorradial", "antebrazo"],
    "tríceps": ["tríceps", "triceps"],
    "cuádriceps": ["cuádriceps", "cuadriceps", "recto femoral", "vasto", "quads"],
    "isquiosurales": ["isquiosurales", "isquiotibiales", "bíceps femoral", "semitendinoso", "semimembranoso", "femoral", "hamstrings"],
    "glúteos": ["glúteo", "gluteo", "glutes"],
    "pantorrillas": ["pantorrilla", "gemelo", "gastrocnemio", "sóleo", "soleo", "calves"],
    "abdomen": ["abdomen", "abdominal", "oblicuo", "recto abdominal", "core", "transverso", "abs"],
    "espalda baja": ["erector", "espinal", "lumbar", "espalda baja", "cuadrado lumbar", "lower back"],
}

_clamp = lambda v, lo, hi: min(hi, max(lo, v))
_safe_exp = lambda v: 0 if not math.isfinite(r := math.exp(v)) else r


def _is_muscle_in_group(specific: str, target: str) -> bool:
    s, t = specific.lower(), target.lower()
    if s == t:
        return True
    kw = MUSCLE_CATEGORY_MAP.get(t)
    if kw:
        return any(k in s for k in kw)
    return s in t or t in s


def _now_ms() -> float:
    return datetime.now(timezone.utc).timestamp() * 1000


def _parse_date_ms(date_str: str) -> float:
    try:
        dt = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        return dt.timestamp() * 1000
    except Exception:
        return 0


# ── Work capacity ────────────────────────────────────────

def _calculate_user_work_capacity(
    history: list[WorkoutLog],
    muscle: str,
    exercise_list: list[ExerciseMuscleInfo],
    settings: Settings,
    idx: ExerciseIndex | None = None,
) -> float:
    now = _now_ms()
    four_weeks = 28 * 24 * 3600 * 1000
    recent = [l for l in history if _parse_date_ms(l.date) > now - four_weeks]
    base_floor = ATHLETE_CAPACITY_FLOORS.get(settings.athleteType.value, 500)

    if not recent:
        return float(base_floor)

    index = idx or ExerciseIndex(exercise_list)
    total_stress = 0.0

    for log in recent:
        for ex in log.completedExercises:
            info = index.find(ex.exerciseDbId, ex.exerciseName)
            if not info:
                continue
            involvement = next((m for m in info.involvedMuscles if _is_muscle_in_group(m.muscle, muscle)), None)
            if involvement:
                stress = sum(calculate_set_stress(s, info, 90) for s in ex.sets)
                total_stress += stress * (involvement.activation or 1.0)

    weekly_avg = total_stress / 4
    calculated = weekly_avg * 1.8
    return _clamp(max(calculated, base_floor), 500, 3500)


# ── Core: muscle battery ─────────────────────────────────

def calculate_muscle_battery(
    muscle_name: str,
    history: list[WorkoutLog],
    exercise_list: list[ExerciseMuscleInfo],
    sleep_logs: list[SleepLog],
    settings: Settings,
    muscle_hierarchy: MuscleHierarchy,
    post_session_feedback: list[PostSessionFeedback] | None = None,
    water_logs: list[WaterLog] | None = None,
    daily_wellbeing: list[DailyWellbeingLog] | None = None,
    nutrition_logs: list[NutritionLog] | None = None,
) -> dict:
    post_session_feedback = post_session_feedback or []
    daily_wellbeing = daily_wellbeing or []
    nutrition_logs = nutrition_logs or []
    now = _now_ms()
    idx = ExerciseIndex(exercise_list)

    capacity = _calculate_user_work_capacity(history, muscle_name, exercise_list, settings, idx)

    # Recovery profile
    profile_key = "medium"
    for key, val in MUSCLE_PROFILE_MAP.items():
        if _is_muscle_in_group(key, muscle_name):
            profile_key = val
            break
    base_recovery = RECOVERY_PROFILES[profile_key]

    # Lifestyle multiplier
    recovery_mult = 1.0
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    recent_wb = next((l for l in daily_wellbeing if l.date == today_str), daily_wellbeing[-1] if daily_wellbeing else None)

    # Nutrition
    if getattr(settings.algorithmSettings, "augeEnableNutritionTracking", True):
        forty_eight = now - 48 * 3600000
        recent_nut = [n for n in nutrition_logs if _parse_date_ms(n.date) > forty_eight]
        status = settings.calorieGoalObjective
        if recent_nut:
            avg_cal = sum(n.calories or 0 for n in recent_nut) / 2
            if settings.dailyCalorieGoal:
                if avg_cal < settings.dailyCalorieGoal * 0.9:
                    status = "deficit"
                elif avg_cal > settings.dailyCalorieGoal * 1.1:
                    status = "surplus"
                else:
                    status = "maintenance"
        if status == "deficit":
            recovery_mult *= 1.35
        elif status == "surplus":
            recovery_mult *= 0.85

    # Stress
    if recent_wb and recent_wb.stressLevel >= 4:
        recovery_mult *= 1.4

    # Sleep
    w_sleep = 7.5
    if getattr(settings.algorithmSettings, "augeEnableSleepTracking", True):
        sorted_sleep = sorted(sleep_logs, key=lambda s: _parse_date_ms(s.endTime), reverse=True)[:3]
        if sorted_sleep:
            w_sleep = (
                (sorted_sleep[0].duration if len(sorted_sleep) > 0 else 7) * 0.5
                + (sorted_sleep[1].duration if len(sorted_sleep) > 1 else 7) * 0.3
                + (sorted_sleep[2].duration if len(sorted_sleep) > 2 else 7) * 0.2
            )
        if w_sleep < 6:
            recovery_mult *= 1.5
        elif w_sleep < 7:
            recovery_mult *= 1.2
        elif w_sleep >= 8.5:
            recovery_mult *= 0.8
        elif w_sleep >= 7.5:
            recovery_mult *= 0.9

    # Biological factors
    age = settings.userVitals.age or 25
    if age > 35:
        recovery_mult *= 1 + (age - 35) * 0.01
    gender = settings.userVitals.gender or "male"
    if gender in ("female", "transfemale"):
        recovery_mult *= 0.85

    real_recovery = base_recovery * max(0.5, recovery_mult)

    # Accumulated fatigue (last 10 days)
    acc_fatigue = 0.0
    last_session_date = 0.0
    effective_sets = 0
    ten_days = 10 * 24 * 3600 * 1000
    relevant = [l for l in history if now - _parse_date_ms(l.date) < ten_days]

    for log in relevant:
        log_time = _parse_date_ms(log.date)
        hours_since = max(0, (now - log_time) / 3600000)
        session_stress = 0.0

        for ex in log.completedExercises:
            info = idx.find(ex.exerciseDbId, ex.exerciseName)
            if not info:
                continue
            inv = next((m for m in info.involvedMuscles if _is_muscle_in_group(m.muscle, muscle_name)), None)
            if not inv:
                continue

            raw = sum(calculate_set_stress(s, info, 90) for s in ex.sets)
            role_mult = {"primary": 1.0, "secondary": 0.5, "stabilizer": 0.15}.get(inv.role.value, 0.1)
            act = inv.activation or 1.0
            session_stress += raw * role_mult * act

            if hours_since <= 168 and inv.role.value in ("primary", "secondary") and (inv.role.value == "primary" or act > 0.6):
                effective_sets += len(ex.sets)

        if session_stress > 0:
            k = 2.9957 / max(1, real_recovery)
            remaining = session_stress * _safe_exp(-k * hours_since)
            acc_fatigue += remaining
            if log_time > last_session_date:
                last_session_date = log_time

    battery = _clamp(100 - (acc_fatigue / capacity * 100), 0, 100)

    # Background load
    bg_cap = 100.0
    ctx = next((l for l in daily_wellbeing if l.date == today_str), daily_wellbeing[-1] if daily_wellbeing else None)
    work_int = (ctx.workIntensity if ctx else None) or (settings.userVitals.workIntensity if settings.userVitals else None) or "light"
    stress_lvl = (ctx.stressLevel if ctx else 3)

    if work_int == "high":
        bg_cap -= 10
    elif work_int == "moderate":
        bg_cap -= 5
    if stress_lvl >= 4:
        bg_cap -= 10
    battery = min(battery, bg_cap)

    # DOMS override
    if recent_wb and recent_wb.doms > 1:
        d = recent_wb.doms
        if d == 5:
            battery = min(battery, 15)
        elif d == 4:
            battery = min(battery, 40)
        elif d == 3:
            battery = min(battery, 70)

    # Discomfort from logs
    for log in history:
        if now - _parse_date_ms(log.date) < 48 * 3600000 and log.discomforts:
            if any(_is_muscle_in_group(d, muscle_name) for d in log.discomforts):
                battery = min(battery, 50)

    # Post-session feedback
    if post_session_feedback:
        recent_fb = sorted(
            [f for f in post_session_feedback if now - _parse_date_ms(f.date) < 72 * 3600000],
            key=lambda f: f.date, reverse=True,
        )
        if recent_fb:
            fb = recent_fb[0]
            entry = next(((k, v) for k, v in fb.feedback.items() if _is_muscle_in_group(k, muscle_name)), None)
            if entry:
                _, data = entry
                hours_fb = (now - _parse_date_ms(fb.date)) / 3600000
                if data.doms == 5:
                    battery = min(battery, 10 + hours_fb * 1.5)
                elif data.doms == 4:
                    battery = min(battery, 40 + hours_fb * 2.0)
                elif data.doms == 3:
                    battery = min(battery, 70 + hours_fb * 2.5)

    battery = _clamp(battery, 0, 100)
    status = "exhausted" if battery < 40 else "recovering" if battery < 85 else "optimal"

    hours_to_recovery = 0.0
    target_pct = min(90, bg_cap)
    if battery < target_pct and acc_fatigue > 0:
        k = 2.9957 / real_recovery
        target_fatigue = (100 - target_pct) * capacity / 100
        if acc_fatigue > target_fatigue:
            hours_to_recovery = -math.log(target_fatigue / acc_fatigue) / k

    return {
        "recoveryScore": round(battery),
        "effectiveSets": effective_sets,
        "hoursSinceLastSession": round((now - last_session_date) / 3600000) if last_session_date > 0 else -1,
        "estimatedHoursToRecovery": round(max(0, hours_to_recovery)),
        "status": status,
    }


# ── Core: systemic fatigue ────────────────────────────────

def calculate_systemic_fatigue(
    history: list[WorkoutLog],
    sleep_logs: list[SleepLog],
    daily_wellbeing: list[DailyWellbeingLog],
    exercise_list: list[ExerciseMuscleInfo],
    settings: Settings | None = None,
) -> dict:
    now = _now_ms()
    idx = ExerciseIndex(exercise_list)
    seven_days = 7 * 24 * 3600 * 1000
    recent = [l for l in history if now - _parse_date_ms(l.date) < seven_days]
    cns_load = 0.0

    for log in recent:
        days_ago = (now - _parse_date_ms(log.date)) / (24 * 3600 * 1000)
        recency = max(0.1, math.exp(-0.4 * days_ago))
        session_cns = 0.0

        for ex in log.completedExercises:
            info = idx.find(ex.exerciseDbId, ex.exerciseName)
            cnc = get_dynamic_auge_metrics(info, ex.exerciseName)["cnc"]
            for s in ex.sets:
                stress = calculate_set_stress(s, info, 90)
                snc_ratio = cnc / 5.0
                load_mult = 1.0
                if info and info.calculated1RM and s.weight:
                    if s.weight / info.calculated1RM >= 0.90:
                        load_mult = 1.3
                session_cns += stress * snc_ratio * load_mult

        dur = log.duration or 0
        if dur > 75 * 60:
            session_cns *= 1.15
        if dur > 90 * 60:
            session_cns *= 1.25
        cns_load += session_cns * recency

    gym_fatigue = _clamp((cns_load / 4000) * 100, 0, 100)

    sleep_penalty = 0.0
    if not settings or getattr(settings.algorithmSettings, "augeEnableSleepTracking", True):
        sorted_s = sorted(sleep_logs, key=lambda s: _parse_date_ms(s.endTime), reverse=True)[:3]
        w = 7.5
        if sorted_s:
            w = (
                (sorted_s[0].duration if len(sorted_s) > 0 else 7.5) * 0.5
                + (sorted_s[1].duration if len(sorted_s) > 1 else 7.5) * 0.3
                + (sorted_s[2].duration if len(sorted_s) > 2 else 7.5) * 0.2
            )
        if w < 4.5:
            sleep_penalty = 40
        elif w < 5.5:
            sleep_penalty = 25
        elif w < 6.5:
            sleep_penalty = 15
        elif w >= 8.5:
            sleep_penalty = -15
        elif w > 7.5:
            sleep_penalty = -5

    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    wb = next((l for l in daily_wellbeing if l.date == today_str), daily_wellbeing[-1] if daily_wellbeing else None)
    life_penalty = 0.0
    if wb:
        if wb.stressLevel >= 4:
            life_penalty += 15
        elif wb.stressLevel == 3:
            life_penalty += 5
        if wb.workIntensity == "high" or wb.studyIntensity == "high":
            life_penalty += 10

    total = gym_fatigue + sleep_penalty + life_penalty
    cns_battery = _clamp(100 - total, 0, 100)

    return {
        "total": round(cns_battery),
        "gym": round(gym_fatigue),
        "life": round(max(0, sleep_penalty + life_penalty)),
    }


# ── Daily readiness ──────────────────────────────────────

def calculate_daily_readiness(
    sleep_logs: list[SleepLog],
    daily_wellbeing: list[DailyWellbeingLog],
    settings: Settings,
    cns_battery: float,
) -> dict:
    mult = 1.0
    diag: list[str] = []

    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    wb = next((l for l in daily_wellbeing if l.date == today_str), daily_wellbeing[-1] if daily_wellbeing else None)

    sorted_s = sorted(sleep_logs, key=lambda s: _parse_date_ms(s.endTime), reverse=True)
    sleep_h = sorted_s[0].duration if sorted_s else 7.5
    if sleep_h < 6:
        mult *= 1.5
        diag.append("Falta de sueño detectada (<6h). Tu recarga está severamente frenada hoy.")

    if wb and wb.stressLevel >= 4:
        mult *= 1.4
        diag.append("Tus niveles altos de estrés están liberando cortisol, bloqueando la recuperación del sistema nervioso.")

    if settings.calorieGoalObjective == "deficit":
        mult *= 1.3
        diag.append("Al estar en déficit calórico, tienes recursos limitados para reparar tejido dañado.")

    status = "green"
    rec = "Estás en condiciones óptimas. Tienes luz verde para buscar récords personales o tirar pesado."

    if cns_battery < 40 or mult >= 1.8:
        status = "red"
        rec = "Tu sistema nervioso no está listo. Tu falta de sueño/estrés está frenando tu recarga. Considera descanso total o una sesión muy ligera de movilidad."
    elif cns_battery < 70 or mult >= 1.3:
        status = "yellow"
        rec = "Tienes fatiga residual o factores externos en contra. Cambia el trabajo pesado por técnica, o reduce tu volumen planificado al 50%."

    if not diag:
        diag.append("Tus hábitos de las últimas 24 hrs fueron excelentes. La síntesis de recuperación está a tope.")

    return {
        "status": status,
        "stressMultiplier": round(mult, 2),
        "cnsBattery": cns_battery,
        "diagnostics": diag,
        "recommendation": rec,
    }


# ── Global batteries ─────────────────────────────────────

def calculate_global_batteries(
    history: list[WorkoutLog],
    sleep_logs: list[SleepLog],
    daily_wellbeing: list[DailyWellbeingLog],
    nutrition_logs: list[NutritionLog],
    settings: Settings,
    exercise_list: list[ExerciseMuscleInfo],
) -> dict:
    now = _now_ms()
    tanks = calculate_personalized_battery_tanks(settings)

    cns_hl, musc_hl, spinal_hl = 28.0, 40.0, 72.0
    audit: dict[str, list] = {"cns": [], "muscular": [], "spinal": []}

    # Nutrition modulator
    forty_eight = now - 48 * 3600000
    recent_nut = [n for n in nutrition_logs if _parse_date_ms(n.date) > forty_eight]
    nut_status = settings.calorieGoalObjective or "maintenance"
    if recent_nut:
        avg = sum(n.calories or 0 for n in recent_nut) / len(recent_nut)
        if settings.dailyCalorieGoal:
            if avg < settings.dailyCalorieGoal * 0.9:
                nut_status = "deficit"
            elif avg > settings.dailyCalorieGoal * 1.1:
                nut_status = "surplus"
    if nut_status == "deficit":
        musc_hl *= 1.3
    elif nut_status == "surplus":
        musc_hl *= 0.8

    # Sleep/stress modulator
    cns_penalty = 0.0
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    wb = next((l for l in daily_wellbeing if l.date == today_str), daily_wellbeing[-1] if daily_wellbeing else None)
    if wb and wb.stressLevel >= 4:
        cns_penalty += 12

    if getattr(settings.algorithmSettings, "augeEnableSleepTracking", True):
        sorted_s = sorted(sleep_logs, key=lambda s: _parse_date_ms(s.endTime), reverse=True)[:3]
        w = 7.5
        if sorted_s:
            w = (
                (sorted_s[0].duration if len(sorted_s) > 0 else 7.5) * 0.5
                + (sorted_s[1].duration if len(sorted_s) > 1 else 7.5) * 0.3
                + (sorted_s[2].duration if len(sorted_s) > 2 else 7.5) * 0.2
            )
        if w < 6:
            cns_penalty += 18
        elif w >= 8.5:
            cns_penalty -= 10

    # Training accumulation
    idx = ExerciseIndex(exercise_list)
    cns_f, musc_f, spinal_f = 0.0, 0.0, 0.0
    seven_days = 7 * 24 * 3600 * 1000
    recent_logs = [l for l in history if _parse_date_ms(l.date) > now - seven_days]

    for log in recent_logs:
        lc, lm, ls = 0.0, 0.0, 0.0
        hours_ago = (now - _parse_date_ms(log.date)) / 3600000
        for ex in log.completedExercises:
            info = idx.find(ex.exerciseDbId, ex.exerciseName)
            for i, s in enumerate(ex.sets):
                drain = calculate_set_battery_drain(s, info, tanks, i, 90)
                lc += drain["cnsDrainPct"]
                lm += drain["muscularDrainPct"]
                ls += drain["spinalDrainPct"]

        ln2 = math.log(2)
        cns_f += lc * math.exp(-(ln2 / cns_hl) * hours_ago)
        musc_f += lm * math.exp(-(ln2 / musc_hl) * hours_ago)
        spinal_f += ls * math.exp(-(ln2 / spinal_hl) * hours_ago)

    # Calibration
    calib = settings.batteryCalibration
    cd = md = sd = 0.0
    if calib and calib.lastCalibrated:
        ch = (now - _parse_date_ms(calib.lastCalibrated)) / 3600000
        decay = max(0, 1 - ch / 72)
        cd = (calib.cnsDelta or 0) * decay
        md = (calib.muscularDelta or 0) * decay
        sd = (calib.spinalDelta or 0) * decay

    final_cns = _clamp(100 - cns_f - cns_penalty + cd, 0, 100)
    final_musc = _clamp(100 - musc_f + md, 0, 100)
    final_spinal = _clamp(100 - spinal_f + sd, 0, 100)

    verdict = "Todos tus sistemas están óptimos. Es un buen día para buscar récords personales (PRs)."
    if final_cns < 30:
        verdict = "Tu sistema nervioso está frito. NO intentes 1RMs hoy. Prioriza máquinas y reduce el RPE."
    elif final_spinal < 35:
        verdict = "Tu columna y tejido axial están sobrecargados. Evita el Peso Muerto o Sentadillas Libres hoy."
    elif final_musc < 30:
        verdict = "Alta fatiga muscular residual. Asegúrate de comer suficiente proteína y haz rutinas de bombeo."
    elif cns_penalty > 10:
        verdict = "Tu falta de sueño/estrés está limitando tu potencial hoy. Autorregula tu peso y no vayas al fallo."

    return {
        "cns": round(final_cns),
        "muscular": round(final_musc),
        "spinal": round(final_spinal),
        "auditLogs": audit,
        "verdict": verdict,
    }


# ── Utilities ────────────────────────────────────────────

def learn_recovery_rate(current_mult: float, calculated_score: float, manual_feel: float) -> float:
    diff = manual_feel - calculated_score
    adj = diff * 0.005
    return _clamp(current_mult + adj, 0.5, 2.0)
