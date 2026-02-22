"""Pydantic models for the AUGE Adaptive Engine.

These models define the data contracts for the Bayesian recovery system,
Gaussian Process fatigue modeling, Banister ODE fitness-fatigue model,
and the self-improvement feedback loop.
"""
from __future__ import annotations
from pydantic import BaseModel, Field
from typing import Optional


# ═══════════════════════════════════════════════════════════════════
# BAYESIAN RECOVERY MODEL
# ═══════════════════════════════════════════════════════════════════

class GammaPrior(BaseModel):
    """Gamma distribution hyperparameters for a single muscle's recovery rate.

    The recovery time τ_m follows a Gamma(alpha, beta) posterior.
    Expected recovery time (hours) = beta / alpha.
    Variance decreases as alpha grows (more observations = more confidence).
    """
    alpha: float = 2.0
    beta: float = 96.0


class UserRecoveryPriors(BaseModel):
    """Per-user, per-muscle Bayesian priors for recovery rates.

    Keys are normalized muscle group names (e.g. 'cuádriceps', 'pectorales').
    Initialized from population defaults (RECOVERY_PROFILES), then updated
    with each observed recovery event.
    """
    muscle_priors: dict[str, GammaPrior] = Field(default_factory=dict)
    total_observations: int = 0
    last_updated: str = ""


class RecoveryObservation(BaseModel):
    """A single observed recovery event used to update Bayesian priors.

    After training, we compare AUGE's predicted battery to actual feedback
    (DOMS, readiness check, or calibration) to derive the true recovery speed.
    """
    muscle: str
    session_stress: float
    hours_since_session: float
    predicted_battery: float
    actual_battery: float
    sleep_quality: Optional[float] = None
    nutrition_status: Optional[str] = None
    stress_level: Optional[float] = None


class BayesianUpdateRequest(BaseModel):
    user_id: str
    observations: list[RecoveryObservation]
    current_priors: UserRecoveryPriors = Field(default_factory=UserRecoveryPriors)


class BayesianUpdateResponse(BaseModel):
    updated_priors: UserRecoveryPriors
    personalized_recovery_hours: dict[str, float]
    confidence_intervals: dict[str, tuple[float, float]]
    improvement_delta: dict[str, float]


# ═══════════════════════════════════════════════════════════════════
# GAUSSIAN PROCESS FATIGUE MODEL
# ═══════════════════════════════════════════════════════════════════

class FatigueDataPoint(BaseModel):
    """A single observation for GP fatigue curve training.

    Each point captures the context at a specific moment and the
    measured fatigue level, allowing the GP to learn non-linear patterns
    like delayed onset, supercompensation dips, and context-dependent decay.
    """
    hours_since_session: float
    session_stress: float
    sleep_hours: float = 7.5
    nutrition_status: float = 0.0
    stress_level: float = 3.0
    age: float = 25.0
    is_compound_dominant: bool = False
    observed_fatigue_fraction: float


class GPFatigueRequest(BaseModel):
    user_id: str
    training_data: list[FatigueDataPoint]
    prediction_hours: list[float] = Field(
        default_factory=lambda: [0, 6, 12, 18, 24, 36, 48, 60, 72, 96, 120]
    )
    session_stress: float = 50.0
    context: Optional[dict] = None


class GPFatiguePrediction(BaseModel):
    hours: list[float]
    mean_fatigue: list[float]
    upper_bound: list[float]
    lower_bound: list[float]
    peak_fatigue_hour: float
    supercompensation_hour: Optional[float] = None
    full_recovery_hour: float


# ═══════════════════════════════════════════════════════════════════
# BANISTER FITNESS-FATIGUE ODE MODEL
# ═══════════════════════════════════════════════════════════════════

class BanisterParams(BaseModel):
    """Parameters for the Banister impulse-response ODE model.

    The model: Performance(t) = p0 + k1*Fitness(t) - k2*Fatigue(t)
    where Fitness and Fatigue follow exponential decay with training impulses.
    """
    p0: float = 50.0
    k1: float = 1.0
    k2: float = 2.0
    tau1: float = 45.0
    tau2: float = 15.0


class TrainingImpulse(BaseModel):
    """A single training session represented as a TRIMP-like impulse."""
    timestamp_hours: float
    impulse: float
    cns_impulse: float = 0.0
    spinal_impulse: float = 0.0


class BanisterRequest(BaseModel):
    user_id: str
    training_history: list[TrainingImpulse]
    params: BanisterParams = Field(default_factory=BanisterParams)
    forecast_hours: float = 168.0
    optimize_params: bool = False
    performance_observations: Optional[list[dict]] = None


class BanisterResponse(BaseModel):
    timeline_hours: list[float]
    fitness: list[float]
    fatigue: list[float]
    performance: list[float]
    optimal_params: Optional[BanisterParams] = None
    next_optimal_session_hour: Optional[float] = None
    predicted_peak_performance_hour: Optional[float] = None


# ═══════════════════════════════════════════════════════════════════
# SELF-IMPROVEMENT LOOP
# ═══════════════════════════════════════════════════════════════════

class PredictionRecord(BaseModel):
    """A single AUGE prediction to be validated against reality."""
    prediction_id: str
    timestamp: str
    muscle: Optional[str] = None
    system: str
    predicted_value: float
    context: dict = Field(default_factory=dict)


class OutcomeRecord(BaseModel):
    """The actual observed outcome that validates a prediction."""
    prediction_id: str
    actual_value: float
    feedback_source: str


class SelfImprovementRequest(BaseModel):
    user_id: str
    predictions: list[PredictionRecord]
    outcomes: list[OutcomeRecord]
    current_model_state: Optional[dict] = None


class ModelAccuracy(BaseModel):
    system: str
    mae: float
    rmse: float
    bias: float
    r_squared: float
    sample_size: int


class SelfImprovementResponse(BaseModel):
    accuracy_by_system: list[ModelAccuracy]
    suggested_adjustments: dict[str, float]
    overall_prediction_score: float
    improvement_trend: list[float]
    recommendations: list[str]
