"""Volume calculator endpoints."""
from fastapi import APIRouter
from pydantic import BaseModel
from models.common import Settings, Session, ExerciseMuscleInfo, AthleteProfileScore, PostSessionFeedback
from engines.volume_engine import (
    calculate_weekly_volume,
    validate_session_volume,
    calculate_fractional_volume,
    calculate_volume_adjustment,
    calculate_unified_muscle_volume,
)

router = APIRouter(prefix="/volume", tags=["volume"])


class WeeklyVolumeRequest(BaseModel):
    athleteScore: AthleteProfileScore | None = None
    settings: Settings
    phase: str = "Acumulación"


class SessionVolumeValidation(BaseModel):
    setsInSession: int
    muscleGroup: str


class FractionalVolumeRequest(BaseModel):
    exercises: list[dict]


class VolumeAdjustmentRequest(BaseModel):
    muscle: str
    feedbackHistory: list[PostSessionFeedback]


class UnifiedVolumeRequest(BaseModel):
    sessions: list[Session]
    exerciseList: list[ExerciseMuscleInfo]


@router.post("/weekly")
def weekly_volume(req: WeeklyVolumeRequest):
    return calculate_weekly_volume(req.athleteScore, req.settings, req.phase)


@router.post("/validate-session")
def validate_session(req: SessionVolumeValidation):
    return validate_session_volume(req.setsInSession, req.muscleGroup)


@router.post("/fractional")
def fractional_volume(req: FractionalVolumeRequest):
    return {"fractionalSets": calculate_fractional_volume(req.exercises)}


@router.post("/adjustment")
def volume_adjustment(req: VolumeAdjustmentRequest):
    return calculate_volume_adjustment(req.muscle, req.feedbackHistory)


@router.post("/unified")
def unified_volume(req: UnifiedVolumeRequest):
    return calculate_unified_muscle_volume(req.sessions, req.exerciseList)
