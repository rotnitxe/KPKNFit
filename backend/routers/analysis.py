"""Analysis service endpoints."""
from fastapi import APIRouter
from pydantic import BaseModel
from models.common import (
    Settings, WorkoutLog, ExerciseMuscleInfo, MuscleHierarchy,
    Session, ProgramWeek, Program,
)
from engines.analysis_engine import (
    calculate_average_volume_for_weeks,
    calculate_session_volume,
    calculate_acwr,
    calculate_weekly_tonnage_comparison,
)

router = APIRouter(prefix="/analysis", tags=["analysis"])


class AverageVolumeRequest(BaseModel):
    weeks: list[ProgramWeek]
    exerciseList: list[ExerciseMuscleInfo]
    muscleHierarchy: MuscleHierarchy
    mode: str = "complex"


class SessionVolumeRequest(BaseModel):
    session: Session
    exerciseList: list[ExerciseMuscleInfo]
    muscleHierarchy: MuscleHierarchy
    mode: str = "complex"


class ACWRRequest(BaseModel):
    history: list[WorkoutLog]
    settings: Settings
    exerciseList: list[ExerciseMuscleInfo]


class TonnageRequest(BaseModel):
    history: list[WorkoutLog]
    settings: Settings


@router.post("/average-volume")
def average_volume(req: AverageVolumeRequest):
    return calculate_average_volume_for_weeks(req.weeks, req.exerciseList, req.muscleHierarchy, req.mode)


@router.post("/session-volume")
def session_volume(req: SessionVolumeRequest):
    return calculate_session_volume(req.session, req.exerciseList, req.muscleHierarchy, req.mode)


@router.post("/acwr")
def acwr(req: ACWRRequest):
    return calculate_acwr(req.history, req.settings, req.exerciseList)


@router.post("/tonnage-comparison")
def tonnage_comparison(req: TonnageRequest):
    return calculate_weekly_tonnage_comparison(req.history, req.settings)
