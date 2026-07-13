"""Fatigue service endpoints."""
from fastapi import APIRouter
from pydantic import BaseModel
from models.common import Settings, Session, ExerciseMuscleInfo, CompletedExercise
from engines.fatigue_engine import (
    get_dynamic_auge_metrics,
    calculate_personalized_battery_tanks,
    calculate_set_battery_drain,
    calculate_predicted_session_drain,
    calculate_completed_session_stress,
)

router = APIRouter(prefix="/fatigue", tags=["fatigue"])


class AugeMetricsRequest(BaseModel):
    exercise: ExerciseMuscleInfo | None = None
    customName: str | None = None


class BatteryTanksRequest(BaseModel):
    settings: Settings | None = None


class SetDrainRequest(BaseModel):
    set: dict
    exercise: ExerciseMuscleInfo | None = None
    tanks: dict
    accumulatedSets: int = 0
    restTime: float = 90


class SessionDrainRequest(BaseModel):
    session: Session
    exerciseList: list[ExerciseMuscleInfo]
    settings: Settings | None = None


class CompletedStressRequest(BaseModel):
    completedExercises: list[CompletedExercise]
    exerciseList: list[ExerciseMuscleInfo]


@router.post("/auge-metrics")
def auge_metrics(req: AugeMetricsRequest):
    return get_dynamic_auge_metrics(req.exercise, req.customName)


@router.post("/battery-tanks")
def battery_tanks(req: BatteryTanksRequest):
    return calculate_personalized_battery_tanks(req.settings)


@router.post("/set-drain")
def set_drain(req: SetDrainRequest):
    return calculate_set_battery_drain(req.set, req.exercise, req.tanks, req.accumulatedSets, req.restTime)


@router.post("/session-drain")
def session_drain(req: SessionDrainRequest):
    return calculate_predicted_session_drain(req.session, req.exerciseList, req.settings)


@router.post("/completed-stress")
def completed_stress(req: CompletedStressRequest):
    return {"totalStress": calculate_completed_session_stress(req.completedExercises, req.exerciseList)}
