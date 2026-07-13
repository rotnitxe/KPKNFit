"""API Router for the AUGE Adaptive Engine.

Endpoints for Bayesian recovery learning, GP fatigue prediction,
Banister fitness-fatigue modeling, and self-improvement analytics.
"""
from __future__ import annotations
from fastapi import APIRouter
from pydantic import BaseModel, Field
from typing import Optional

from models.adaptive import (
    BayesianUpdateRequest,
    BayesianUpdateResponse,
    GPFatigueRequest,
    GPFatiguePrediction,
    BanisterRequest,
    BanisterResponse,
    BanisterParams,
    SelfImprovementRequest,
    SelfImprovementResponse,
    TrainingImpulse,
    UserRecoveryPriors,
)
from engines.adaptive_engine import (
    bayesian_update_recovery,
    get_personalized_recovery_time,
    train_gp_fatigue_model,
    predict_fatigue_curve,
    evaluate_prediction_accuracy,
    compute_adaptive_corrections,
)
from engines.banister_model import (
    solve_banister,
    optimize_banister_params,
    solve_auge_banister,
)

router = APIRouter(prefix="/adaptive", tags=["Adaptive Engine"])


# ─── Bayesian Recovery ────────────────────────────────────────────

@router.post("/recovery/update", response_model=BayesianUpdateResponse)
def update_recovery_priors(req: BayesianUpdateRequest):
    """Update personalized recovery rate estimates from observed data.

    Send recovery observations (predicted vs actual battery) and receive
    updated Bayesian priors with personalized recovery times per muscle.
    The model converges to the individual's true recovery dynamics over time.
    """
    return bayesian_update_recovery(req.observations, req.current_priors)


class PersonalizedRecoveryRequest(BaseModel):
    muscle: str
    priors: UserRecoveryPriors = Field(default_factory=UserRecoveryPriors)
    context: Optional[dict] = None


class PersonalizedRecoveryResponse(BaseModel):
    muscle: str
    estimated_recovery_hours: float
    is_personalized: bool
    confidence: str


@router.post("/recovery/estimate", response_model=PersonalizedRecoveryResponse)
def estimate_recovery(req: PersonalizedRecoveryRequest):
    """Get the current best estimate of recovery time for a muscle.

    Uses Bayesian posterior if available, falls back to population defaults.
    Context (sleep, nutrition, stress) modulates the learned base rate.
    """
    hours = get_personalized_recovery_time(req.muscle, req.priors, req.context)
    is_personalized = req.muscle.lower() in req.priors.muscle_priors
    n = req.priors.total_observations

    if n >= 20:
        confidence = "alta"
    elif n >= 10:
        confidence = "media"
    elif n >= 3:
        confidence = "baja"
    else:
        confidence = "poblacional"

    return PersonalizedRecoveryResponse(
        muscle=req.muscle,
        estimated_recovery_hours=round(hours, 1),
        is_personalized=is_personalized,
        confidence=confidence,
    )


# ─── Gaussian Process Fatigue ─────────────────────────────────────

@router.post("/fatigue/predict", response_model=GPFatiguePrediction)
def predict_fatigue(req: GPFatigueRequest):
    """Predict the fatigue curve using a Gaussian Process model.

    Provide training data (previous fatigue observations) and get a
    non-linear fatigue prediction with uncertainty bounds. The GP captures:
    - Delayed onset (fatigue peaks 24-48h after training)
    - Supercompensation (temporary performance boost)
    - Context-dependent recovery (sleep, nutrition effects)
    """
    gp, scaler = train_gp_fatigue_model(req.training_data)
    return predict_fatigue_curve(
        gp, scaler,
        prediction_hours=req.prediction_hours,
        session_stress=req.session_stress,
        context=req.context,
    )


# ─── Banister Fitness-Fatigue Model ──────────────────────────────

@router.post("/banister/solve", response_model=BanisterResponse)
def solve_banister_endpoint(req: BanisterRequest):
    """Solve the Banister fitness-fatigue ODE model.

    Given training impulse history and model parameters, integrates
    the differential equations forward in time to predict performance.
    Optionally optimizes parameters from observed performance data.
    """
    params = req.params

    if req.optimize_params and req.performance_observations:
        params = optimize_banister_params(
            req.training_history,
            req.performance_observations,
            params,
        )

    result = solve_banister(req.training_history, params, req.forecast_hours)

    if req.optimize_params:
        result.optimal_params = params

    return result


class AugeBanisterRequest(BaseModel):
    training_history: list[TrainingImpulse]
    forecast_hours: float = 168.0


@router.post("/banister/auge")
def solve_auge_banister_endpoint(req: AugeBanisterRequest):
    """Extended 3-system Banister model (muscular + CNS + spinal).

    Each biological system has its own fitness and fatigue time constants:
    - Muscular: Slow to adapt, fast to recover
    - CNS: Moderate adaptation, fastest recovery
    - Spinal: Slowest adaptation, lingering fatigue

    Returns combined performance prediction, per-system breakdown,
    optimal next session timing, and a human-readable verdict.
    """
    return solve_auge_banister(req.training_history, req.forecast_hours)


# ─── Self-Improvement Loop ───────────────────────────────────────

@router.post("/self-improve", response_model=SelfImprovementResponse)
def self_improve(req: SelfImprovementRequest):
    """Evaluate AUGE prediction accuracy and generate corrections.

    Compare past predictions to actual outcomes across all systems
    (muscular, CNS, spinal, readiness). Returns accuracy metrics,
    systematic bias detection, and suggested parameter adjustments.
    """
    return evaluate_prediction_accuracy(req.predictions, req.outcomes)


class CorrectionRequest(BaseModel):
    predictions: list[dict]
    outcomes: list[dict]
    current_calibration: Optional[dict] = None


@router.post("/self-improve/corrections")
def get_corrections(req: CorrectionRequest):
    """Get concrete batteryCalibration corrections from prediction errors.

    Returns cnsDelta, muscularDelta, spinalDelta values that can be
    directly applied to the frontend AUGE system to immediately
    improve prediction accuracy.
    """
    from models.adaptive import PredictionRecord, OutcomeRecord

    preds = [PredictionRecord(**p) for p in req.predictions]
    outs = [OutcomeRecord(**o) for o in req.outcomes]

    accuracy = evaluate_prediction_accuracy(preds, outs)
    corrections = compute_adaptive_corrections(accuracy, req.current_calibration)

    return {
        "corrections": corrections,
        "accuracy_score": accuracy.overall_prediction_score,
        "details": accuracy.model_dump(),
    }


# ─── Health & Status ─────────────────────────────────────────────

@router.get("/status")
def adaptive_status():
    """Check adaptive engine availability and capabilities."""
    return {
        "status": "operational",
        "engines": {
            "bayesian_recovery": True,
            "gaussian_process_fatigue": True,
            "banister_ode": True,
            "self_improvement": True,
        },
        "version": "AUGE Adaptive v1.0.0",
    }
