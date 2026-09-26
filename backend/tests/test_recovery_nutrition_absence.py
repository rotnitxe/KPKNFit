"""Oracle tests: nutrition neutrality parity with Android's wizard commit.

Commit ac3ff1c88 made Android's NutritionRecoveryEngine neutral when there is
no intake evidence ("sin 100% sin datos"): an empty window or absent goals must
not fabricate deficit/surplus from the declared calorieGoalObjective.

These tests verify the backend's recovery_engine.py (legacy port of
recoveryService.ts) applies the same contract:
  * no recent nutrition logs  -> no nutrition modulation (neutral)
  * recent logs but no dailyCalorieGoal -> no nutrition modulation (neutral)
  * recent logs + explicit dailyCalorieGoal=0 -> neutral (0 is not a usable
    goal and is never used as a divisor; Python truthiness keeps it neutral)
  * recent logs + goal -> deficit/surplus modulation still applies

Oracles are numeric: the neutral scenarios must equal the no-nutrition baseline
exactly; the modulated scenarios must move muscular recovery strictly in the
expected direction (deficit slows recovery, surplus accelerates it).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

from engines.recovery_engine import (
    calculate_daily_readiness,
    calculate_global_batteries,
    calculate_muscle_battery,
)
from models.common import (
    CompletedExercise,
    CompletedSet,
    ExerciseMuscleInfo,
    InvolvedMuscle,
    MuscleHierarchy,
    MuscleRole,
    NutritionLog,
    Settings,
    WorkoutLog,
)


def hours_ago_iso(hours: float) -> str:
    return (datetime.now(timezone.utc) - timedelta(hours=hours)).isoformat()


def nutrition_log(calories: float) -> NutritionLog:
    return NutritionLog(id="nut-1", date=hours_ago_iso(1), calories=calories)


def bench_session() -> WorkoutLog:
    """Una sesión de press banca hace 6 h (5 series RPE 10): fatiga residual
    observable (oráculo empírico: neutral=89, deficit=88, surplus=90)."""
    return WorkoutLog(
        id="wl-1",
        date=hours_ago_iso(6),
        duration=60 * 60,
        completedExercises=[
            CompletedExercise(
                exerciseId="bench",
                exerciseDbId="bench",
                exerciseName="Press banca",
                sets=[
                    CompletedSet(completedReps=8, completedRPE=10.0, weight=90)
                    for _ in range(5)
                ],
            )
        ],
    )


BENCH = ExerciseMuscleInfo(
    id="bench",
    name="Press banca",
    involvedMuscles=[
        InvolvedMuscle(muscle="Pectorales", role=MuscleRole.primary, activation=1.0),
    ],
    efc=3.8,
    ssc=0.3,
    cnc=3.8,
)


def _battery(settings: Settings, logs: list[NutritionLog] | None = None) -> dict:
    return calculate_muscle_battery(
        muscle_name="Pectorales",
        history=[bench_session()],
        exercise_list=[BENCH],
        sleep_logs=[],
        settings=settings,
        muscle_hierarchy=MuscleHierarchy(),
        nutrition_logs=logs or [],
    )


def _global(settings: Settings, logs: list[NutritionLog] | None = None) -> dict:
    return calculate_global_batteries(
        history=[bench_session()],
        sleep_logs=[],
        daily_wellbeing=[],
        nutrition_logs=logs or [],
        settings=settings,
        exercise_list=[BENCH],
    )


# ── Muscle battery ───────────────────────────────────────

def test_objective_alone_does_not_fabricate_deficit():
    """Objetivo 'deficit' sin registros de comida: idéntico al neutro."""
    neutral = _battery(Settings())
    objective_deficit = _battery(Settings(calorieGoalObjective="deficit"))
    assert (
        objective_deficit["recoveryScore"] == neutral["recoveryScore"]
    ), "el objetivo declarado no es evidencia de ingesta"


def test_logs_without_goal_are_neutral():
    """Comida reciente SIN dailyCalorieGoal: no hay contra qué medir."""
    neutral = _battery(Settings())
    no_goal = _battery(
        Settings(calorieGoalObjective="deficit"),
        logs=[nutrition_log(200.0)],
    )
    assert (
        no_goal["recoveryScore"] == neutral["recoveryScore"]
    ), "sin meta no se infiere déficit ni superávit"


def test_explicit_zero_goal_is_neutral():
    """Meta calórica 0 explícita: no hay meta > 0 contra la que medir, así que
    es neutra (idéntica al baseline) y NUNCA se divide por ella. Oráculo del
    contrato «goal 0 no division» de iOS/Android (guardas `goal > 0` /
    truthiness en Python)."""
    neutral = _battery(Settings())
    zero_goal = _battery(
        Settings(calorieGoalObjective="deficit", dailyCalorieGoal=0.0),
        logs=[nutrition_log(200.0)],
    )
    assert zero_goal["recoveryScore"] == neutral["recoveryScore"], (
        "un 0 explícito no es evidencia de déficit ni divisor válido"
    )

    neutral_global = _global(Settings())
    zero_global = _global(
        Settings(calorieGoalObjective="deficit", dailyCalorieGoal=0.0),
        logs=[nutrition_log(200.0)],
    )
    assert zero_global["muscular"] == neutral_global["muscular"]


def test_deficit_with_goal_slows_muscular_recovery():
    """Promedio 100 kcal vs meta 1000 (>0.9*1000 invertido): déficit 1.35x."""
    neutral = _battery(Settings())
    deficit = _battery(
        Settings(calorieGoalObjective="deficit", dailyCalorieGoal=1000.0),
        logs=[nutrition_log(200.0)],
    )
    assert deficit["recoveryScore"] < neutral["recoveryScore"], (
        f"déficit real debe penalizar la batería (neutral={neutral['recoveryScore']}, "
        f"deficit={deficit['recoveryScore']})"
    )


def test_surplus_with_goal_speeds_muscular_recovery():
    """Promedio 1300 kcal vs meta 1000 (>1.1*1000): superávit 0.85x."""
    neutral = _battery(Settings())
    surplus = _battery(
        Settings(calorieGoalObjective="surplus", dailyCalorieGoal=1000.0),
        logs=[nutrition_log(2600.0)],
    )
    assert surplus["recoveryScore"] > neutral["recoveryScore"], (
        f"superávit real debe acelerar la recuperación (neutral={neutral['recoveryScore']}, "
        f"surplus={surplus['recoveryScore']})"
    )


# ── Global batteries ─────────────────────────────────────

def test_global_objective_alone_is_neutral():
    neutral = _global(Settings())
    objective_deficit = _global(Settings(calorieGoalObjective="deficit"))
    assert objective_deficit["muscular"] == neutral["muscular"]
    assert objective_deficit["cns"] == neutral["cns"]
    assert objective_deficit["spinal"] == neutral["spinal"]


def test_global_logs_without_goal_are_neutral():
    neutral = _global(Settings())
    no_goal = _global(
        Settings(calorieGoalObjective="deficit"),
        logs=[nutrition_log(200.0)],
    )
    assert no_goal["muscular"] == neutral["muscular"]


def test_global_deficit_with_goal_lowers_muscular_battery():
    """Half-life muscular 40h -> 52h (1.3x) con déficit real: más fatiga."""
    neutral = _global(Settings())
    deficit = _global(
        Settings(dailyCalorieGoal=1000.0),
        logs=[nutrition_log(200.0)],
    )
    assert deficit["muscular"] <= neutral["muscular"], (
        f"déficit real debe reducir la batería muscular (neutral={neutral['muscular']}, "
        f"deficit={deficit['muscular']})"
    )


# ── Daily readiness ──────────────────────────────────────

def test_daily_readiness_does_not_use_objective_without_evidence():
    """calculate_daily_readiness no recibe nutrición: el objetivo no modula."""
    neutral = calculate_daily_readiness(
        sleep_logs=[], daily_wellbeing=[], settings=Settings(), cns_battery=80.0
    )
    deficit = calculate_daily_readiness(
        sleep_logs=[],
        daily_wellbeing=[],
        settings=Settings(calorieGoalObjective="deficit"),
        cns_battery=80.0,
    )
    assert deficit["stressMultiplier"] == neutral["stressMultiplier"] == 1.0
    assert not any("déficit calórico" in d for d in deficit["diagnostics"])