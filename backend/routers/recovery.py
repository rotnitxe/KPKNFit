"""Recovery service endpoints."""
from fastapi import APIRouter
from pydantic import BaseModel
from models.common import (
    Settings, WorkoutLog, ExerciseMuscleInfo, MuscleHierarchy,
    SleepLog, PostSessionFeedback, DailyWellbeingLog, WaterLog, NutritionLog,
)
from engines.recovery_engine import (
    calculate_muscle_battery,
    calculate_systemic_fatigue,
    calculate_daily_readiness,
    calculate_global_batteries,
    learn_recovery_rate,
)

router = APIRouter(prefix="/recovery", tags=["recovery"])


class MuscleBatteryRequest(BaseModel):
    muscleName: str
    history: list[WorkoutLog]
    exerciseList: list[ExerciseMuscleInfo]
    sleepLogs: list[SleepLog]
    settings: Settings
    muscleHierarchy: MuscleHierarchy
    postSessionFeedback: list[PostSessionFeedback] = []
    waterLogs: list[WaterLog] = []
    dailyWellbeingLogs: list[DailyWellbeingLog] = []
    nutritionLogs: list[NutritionLog] = []


class SystemicFatigueRequest(BaseModel):
    history: list[WorkoutLog]
    sleepLogs: list[SleepLog]
    dailyWellbeingLogs: list[DailyWellbeingLog]
    exerciseList: list[ExerciseMuscleInfo]
    settings: Settings | None = None


class DailyReadinessRequest(BaseModel):
    sleepLogs: list[SleepLog]
    dailyWellbeingLogs: list[DailyWellbeingLog]
    settings: Settings
    cnsBattery: float


class GlobalBatteriesRequest(BaseModel):
    history: list[WorkoutLog]
    sleepLogs: list[SleepLog]
    dailyWellbeingLogs: list[DailyWellbeingLog]
    nutritionLogs: list[NutritionLog]
    settings: Settings
    exerciseList: list[ExerciseMuscleInfo]


class LearnRecoveryRequest(BaseModel):
    currentMultiplier: float
    calculatedScore: float
    manualFeel: float


@router.post("/muscle-battery")
def muscle_battery(req: MuscleBatteryRequest):
    return calculate_muscle_battery(
        req.muscleName, req.history, req.exerciseList, req.sleepLogs,
        req.settings, req.muscleHierarchy, req.postSessionFeedback,
        req.waterLogs, req.dailyWellbeingLogs, req.nutritionLogs,
    )


@router.post("/systemic-fatigue")
def systemic_fatigue(req: SystemicFatigueRequest):
    return calculate_systemic_fatigue(
        req.history, req.sleepLogs, req.dailyWellbeingLogs, req.exerciseList, req.settings,
    )


@router.post("/daily-readiness")
def daily_readiness(req: DailyReadinessRequest):
    return calculate_daily_readiness(
        req.sleepLogs, req.dailyWellbeingLogs, req.settings, req.cnsBattery,
    )


@router.post("/global-batteries")
def global_batteries(req: GlobalBatteriesRequest):
    return calculate_global_batteries(
        req.history, req.sleepLogs, req.dailyWellbeingLogs,
        req.nutritionLogs, req.settings, req.exerciseList,
    )


@router.post("/learn-rate")
def learn_rate(req: LearnRecoveryRequest):
    return {"newMultiplier": learn_recovery_rate(req.currentMultiplier, req.calculatedScore, req.manualFeel)}
