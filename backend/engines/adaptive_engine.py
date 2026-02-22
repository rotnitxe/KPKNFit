"""AUGE Adaptive Engine — Self-Improving Biological Intelligence

This engine replaces static formulas with learned, personalized models that
understand the human body is NOT linear:

1. BAYESIAN RECOVERY: Instead of static profiles (24h, 48h, 72h, 96h), we learn
   each user's actual recovery rate per muscle group using conjugate Bayesian
   updating. The model starts with population defaults and converges to the
   individual's true recovery dynamics as data accumulates.

2. GAUSSIAN PROCESS FATIGUE: Instead of simple exponential decay (S * e^(-kt)),
   we fit a non-parametric GP that captures delayed onset, supercompensation,
   and context-dependent recovery that linear models can never represent.

3. SELF-IMPROVEMENT LOOP: The system continuously compares its predictions to
   actual outcomes and quantifies its own accuracy, adjusting parameters to
   minimize prediction error over time. AUGE literally gets smarter with use.
"""
from __future__ import annotations

import math
import numpy as np
from scipy import stats
from scipy.optimize import minimize_scalar
from sklearn.gaussian_process import GaussianProcessRegressor
from sklearn.gaussian_process.kernels import Matern, ConstantKernel, WhiteKernel
from sklearn.preprocessing import StandardScaler

from models.adaptive import (
    GammaPrior,
    UserRecoveryPriors,
    RecoveryObservation,
    BayesianUpdateResponse,
    FatigueDataPoint,
    GPFatiguePrediction,
    PredictionRecord,
    OutcomeRecord,
    ModelAccuracy,
    SelfImprovementResponse,
)

# ═══════════════════════════════════════════════════════════════════
# POPULATION DEFAULTS (PRIORS)
# ═══════════════════════════════════════════════════════════════════

POPULATION_RECOVERY_HOURS: dict[str, float] = {
    "bíceps": 24, "tríceps": 24, "deltoides": 24,
    "pantorrillas": 24, "abdomen": 24, "antebrazo": 24,
    "pectorales": 48, "dorsales": 48, "hombros": 48,
    "trapecio": 48, "aductores": 48, "core": 48,
    "cuádriceps": 72, "glúteos": 72,
    "isquiosurales": 96, "espalda baja": 96, "erectores espinales": 96,
}

DEFAULT_ALPHA = 2.0


def _get_default_prior(muscle: str) -> GammaPrior:
    """Initialize a Gamma prior from population recovery time."""
    base_hours = POPULATION_RECOVERY_HOURS.get(muscle.lower(), 48.0)
    return GammaPrior(alpha=DEFAULT_ALPHA, beta=DEFAULT_ALPHA * base_hours)


# ═══════════════════════════════════════════════════════════════════
# 1. BAYESIAN RECOVERY ENGINE
# ═══════════════════════════════════════════════════════════════════

def bayesian_update_recovery(
    observations: list[RecoveryObservation],
    current_priors: UserRecoveryPriors,
) -> BayesianUpdateResponse:
    """Update recovery rate beliefs using conjugate Bayesian inference.

    For each muscle, we maintain a Gamma(α, β) posterior on the recovery
    rate parameter τ (hours to recover to ~90% baseline).

    When we observe a recovery event, we derive the implied recovery time
    from the prediction error and update the posterior:
        α_new = α_old + 1
        β_new = β_old + observed_τ

    The beauty of conjugate priors: no MCMC needed, updates are O(1).
    """
    priors = current_priors.model_copy(deep=True)
    improvement_delta: dict[str, float] = {}

    for obs in observations:
        muscle = obs.muscle.lower()
        if muscle not in priors.muscle_priors:
            priors.muscle_priors[muscle] = _get_default_prior(muscle)

        prior = priors.muscle_priors[muscle]
        old_expected = prior.beta / prior.alpha

        implied_tau = _derive_implied_recovery_time(obs)

        if implied_tau is not None and implied_tau > 0:
            prior.alpha += 1.0
            prior.beta += implied_tau
            priors.total_observations += 1

        new_expected = prior.beta / prior.alpha
        improvement_delta[muscle] = new_expected - old_expected

    personalized_hours: dict[str, float] = {}
    confidence_intervals: dict[str, tuple[float, float]] = {}

    for muscle, prior in priors.muscle_priors.items():
        expected = prior.beta / prior.alpha
        personalized_hours[muscle] = round(expected, 1)

        lower = stats.gamma.ppf(0.05, a=prior.alpha, scale=prior.beta / prior.alpha)
        upper = stats.gamma.ppf(0.95, a=prior.alpha, scale=prior.beta / prior.alpha)
        confidence_intervals[muscle] = (round(lower, 1), round(upper, 1))

    return BayesianUpdateResponse(
        updated_priors=priors,
        personalized_recovery_hours=personalized_hours,
        confidence_intervals=confidence_intervals,
        improvement_delta={k: round(v, 2) for k, v in improvement_delta.items()},
    )


def _derive_implied_recovery_time(obs: RecoveryObservation) -> float | None:
    """From a prediction-vs-reality pair, derive what the true τ must be.

    If AUGE predicted 60% battery but the user reports 80%, they recovered
    faster than expected. We solve for the τ that would produce the actual
    battery given the stress and time elapsed.

    Battery = 100 - (Stress * exp(-k * t) / Capacity) * 100
    where k = 2.9957 / τ

    Solving for τ:
        remaining_fraction = (100 - actual_battery) / (100 - initial_depletion)
        k = -ln(remaining_fraction) / t
        τ = 2.9957 / k
    """
    if obs.hours_since_session <= 0 or obs.session_stress <= 0:
        return None

    actual_depletion = max(1.0, 100.0 - obs.actual_battery)
    initial_depletion = max(actual_depletion, obs.session_stress)

    remaining_fraction = actual_depletion / initial_depletion
    remaining_fraction = max(0.01, min(0.99, remaining_fraction))

    k = -math.log(remaining_fraction) / obs.hours_since_session

    if k <= 0:
        return None

    implied_tau = 2.9957 / k
    return max(6.0, min(200.0, implied_tau))


def get_personalized_recovery_time(
    muscle: str,
    priors: UserRecoveryPriors,
    context: dict | None = None,
) -> float:
    """Get the current best estimate of recovery time for a muscle.

    Returns the posterior mean, optionally modulated by context
    (sleep, nutrition, stress) using the same AUGE multipliers
    but now applied to a LEARNED base instead of a static one.
    """
    muscle_lower = muscle.lower()
    prior = priors.muscle_priors.get(muscle_lower)
    if not prior:
        prior = _get_default_prior(muscle_lower)

    base_tau = prior.beta / prior.alpha

    if not context:
        return base_tau

    mult = 1.0
    if context.get("nutrition_status") == "deficit":
        mult *= 1.35
    elif context.get("nutrition_status") == "surplus":
        mult *= 0.85

    stress = context.get("stress_level", 3)
    if stress >= 4:
        mult *= 1.4

    sleep = context.get("sleep_hours", 7.5)
    if sleep < 6:
        mult *= 1.5
    elif sleep < 7:
        mult *= 1.2
    elif sleep >= 8.5:
        mult *= 0.8
    elif sleep >= 7.5:
        mult *= 0.9

    age = context.get("age", 25)
    if age > 35:
        mult *= 1 + (age - 35) * 0.01

    return base_tau * max(0.5, mult)


# ═══════════════════════════════════════════════════════════════════
# 2. GAUSSIAN PROCESS FATIGUE ENGINE
# ═══════════════════════════════════════════════════════════════════

def train_gp_fatigue_model(
    training_data: list[FatigueDataPoint],
) -> tuple[GaussianProcessRegressor, StandardScaler]:
    """Train a GP to learn the true fatigue decay curve from observations.

    The GP uses a Matérn(5/2) kernel which is infinitely differentiable
    but can capture the sharp transitions of biological systems
    (delayed onset peak, supercompensation dip).

    Input features:
        [hours_since, session_stress, sleep, nutrition, stress, age, compound]

    Output:
        observed_fatigue_fraction (0 = fully recovered, 1 = max fatigue)

    With enough data (>15-20 points), the GP will reveal patterns that
    no parametric model could capture:
        - Fatigue INCREASES for 24-48h before starting to decay
        - Supercompensation dip below baseline at ~72-96h
        - Context-dependent recovery speed (sleep debt = slower decay)
    """
    if len(training_data) < 3:
        return _build_prior_gp(), StandardScaler()

    X = np.array([
        [
            dp.hours_since_session,
            dp.session_stress,
            dp.sleep_hours,
            dp.nutrition_status,
            dp.stress_level,
            dp.age,
            float(dp.is_compound_dominant),
        ]
        for dp in training_data
    ])
    y = np.array([dp.observed_fatigue_fraction for dp in training_data])

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    kernel = (
        ConstantKernel(1.0, (1e-3, 1e3))
        * Matern(length_scale=1.0, length_scale_bounds=(1e-2, 1e2), nu=2.5)
        + WhiteKernel(noise_level=0.05, noise_level_bounds=(1e-4, 1.0))
    )

    gp = GaussianProcessRegressor(
        kernel=kernel,
        n_restarts_optimizer=5,
        alpha=1e-6,
        normalize_y=True,
    )
    gp.fit(X_scaled, y)

    return gp, scaler


def predict_fatigue_curve(
    gp: GaussianProcessRegressor,
    scaler: StandardScaler,
    prediction_hours: list[float],
    session_stress: float = 50.0,
    context: dict | None = None,
) -> GPFatiguePrediction:
    """Predict the fatigue curve over time using the trained GP.

    Returns mean prediction with uncertainty bounds (±2σ).
    Also identifies key biological events:
        - Peak fatigue hour (delayed onset)
        - Supercompensation hour (if detected)
        - Full recovery hour (fatigue < 5%)
    """
    ctx = context or {}
    sleep = ctx.get("sleep_hours", 7.5)
    nutrition = ctx.get("nutrition_status", 0.0)
    stress = ctx.get("stress_level", 3.0)
    age = ctx.get("age", 25.0)
    compound = ctx.get("is_compound_dominant", False)

    X_pred = np.array([
        [h, session_stress, sleep, nutrition, stress, age, float(compound)]
        for h in prediction_hours
    ])

    if hasattr(scaler, "mean_") and scaler.mean_ is not None:
        X_scaled = scaler.transform(X_pred)
    else:
        X_scaled = X_pred

    mean, std = gp.predict(X_scaled, return_std=True)

    mean_clamped = np.clip(mean, 0, 1).tolist()
    std_safe = np.maximum(std, 0).tolist()
    upper = np.clip(mean + 2 * std, 0, 1).tolist()
    lower = np.clip(mean - 2 * std, 0, 1).tolist()

    peak_idx = int(np.argmax(mean_clamped))
    peak_hour = prediction_hours[peak_idx]

    supercomp_hour = None
    if len(mean_clamped) > peak_idx + 1:
        post_peak = mean_clamped[peak_idx + 1:]
        for i, val in enumerate(post_peak):
            if val < -0.02:
                supercomp_hour = prediction_hours[peak_idx + 1 + i]
                break

    full_recovery_hour = prediction_hours[-1]
    for i, val in enumerate(mean_clamped):
        if i > peak_idx and val < 0.05:
            full_recovery_hour = prediction_hours[i]
            break

    return GPFatiguePrediction(
        hours=prediction_hours,
        mean_fatigue=mean_clamped,
        upper_bound=upper,
        lower_bound=lower,
        peak_fatigue_hour=peak_hour,
        supercompensation_hour=supercomp_hour,
        full_recovery_hour=full_recovery_hour,
    )


def _build_prior_gp() -> GaussianProcessRegressor:
    """Build a GP with synthetic data from AUGE's exponential decay model.

    Used when the user has insufficient real data (<3 observations).
    The GP starts by "believing" in the exponential model, then deviates
    from it as real observations contradict the assumption.
    """
    hours = [0, 3, 6, 12, 18, 24, 36, 48, 60, 72, 96, 120]
    stress = 50.0

    synthetic_X = []
    synthetic_y = []

    for h in hours:
        k = 2.9957 / 48.0
        fatigue = math.exp(-k * max(0, h - 6)) if h >= 6 else min(1.0, h / 6.0)
        fatigue *= 0.8

        for sleep in [6.0, 7.5, 9.0]:
            sleep_mod = 1.0
            if sleep < 6:
                sleep_mod = 1.3
            elif sleep >= 8.5:
                sleep_mod = 0.75

            modified_fatigue = min(1.0, fatigue * sleep_mod)
            synthetic_X.append([h, stress, sleep, 0.0, 3.0, 25.0, 1.0])
            synthetic_y.append(modified_fatigue)

    X = np.array(synthetic_X)
    y = np.array(synthetic_y)

    kernel = (
        ConstantKernel(1.0, (1e-3, 1e3))
        * Matern(length_scale=1.0, length_scale_bounds=(1e-2, 1e2), nu=2.5)
        + WhiteKernel(noise_level=0.1, noise_level_bounds=(1e-4, 1.0))
    )

    gp = GaussianProcessRegressor(
        kernel=kernel,
        n_restarts_optimizer=3,
        alpha=1e-4,
        normalize_y=True,
    )
    gp.fit(X, y)
    return gp


# ═══════════════════════════════════════════════════════════════════
# 3. SELF-IMPROVEMENT LOOP
# ═══════════════════════════════════════════════════════════════════

def evaluate_prediction_accuracy(
    predictions: list[PredictionRecord],
    outcomes: list[OutcomeRecord],
) -> SelfImprovementResponse:
    """Compare AUGE predictions to actual outcomes and quantify accuracy.

    For each prediction-outcome pair:
        error = predicted - actual

    We compute MAE, RMSE, bias, and R² per system (muscular, cns, spinal,
    readiness), identify systematic biases, and suggest parameter adjustments.
    """
    outcome_map = {o.prediction_id: o for o in outcomes}
    paired: dict[str, list[tuple[float, float]]] = {}

    for pred in predictions:
        outcome = outcome_map.get(pred.prediction_id)
        if not outcome:
            continue

        system = pred.system
        if system not in paired:
            paired[system] = []
        paired[system].append((pred.predicted_value, outcome.actual_value))

    accuracy_list: list[ModelAccuracy] = []
    all_errors: list[float] = []
    adjustments: dict[str, float] = {}
    recommendations: list[str] = []

    for system, pairs in paired.items():
        if len(pairs) < 2:
            continue

        predicted = np.array([p[0] for p in pairs])
        actual = np.array([p[1] for p in pairs])

        errors = predicted - actual
        abs_errors = np.abs(errors)

        mae = float(np.mean(abs_errors))
        rmse = float(np.sqrt(np.mean(errors ** 2)))
        bias = float(np.mean(errors))

        ss_res = float(np.sum((actual - predicted) ** 2))
        ss_tot = float(np.sum((actual - np.mean(actual)) ** 2))
        r2 = 1.0 - (ss_res / ss_tot) if ss_tot > 0 else 0.0

        accuracy_list.append(ModelAccuracy(
            system=system,
            mae=round(mae, 2),
            rmse=round(rmse, 2),
            bias=round(bias, 2),
            r_squared=round(r2, 3),
            sample_size=len(pairs),
        ))
        all_errors.extend(abs_errors.tolist())

        if abs(bias) > 5:
            direction = "sobreestimando" if bias > 0 else "subestimando"
            adjustments[f"{system}_bias_correction"] = round(-bias * 0.3, 2)
            recommendations.append(
                f"AUGE está {direction} {system} en promedio {abs(bias):.0f} puntos. "
                f"Ajuste sugerido: {-bias * 0.3:+.1f} pts."
            )

        if mae > 15:
            recommendations.append(
                f"Error promedio en {system} es alto ({mae:.0f} pts). "
                f"Más datos de feedback mejorarán la precisión."
            )

    overall_score = 0.0
    if all_errors:
        max_acceptable_error = 20.0
        avg_error = float(np.mean(all_errors))
        overall_score = max(0, min(100, (1 - avg_error / max_acceptable_error) * 100))

    if not recommendations:
        recommendations.append(
            "AUGE tiene precisión aceptable. Sigue registrando feedback para mejorar."
        )

    trend = _compute_improvement_trend(predictions, outcomes)

    return SelfImprovementResponse(
        accuracy_by_system=accuracy_list,
        suggested_adjustments=adjustments,
        overall_prediction_score=round(overall_score, 1),
        improvement_trend=trend,
        recommendations=recommendations,
    )


def _compute_improvement_trend(
    predictions: list[PredictionRecord],
    outcomes: list[OutcomeRecord],
) -> list[float]:
    """Compute rolling MAE over time to show AUGE getting smarter.

    Groups prediction-outcome pairs into windows of 5 and computes
    the MAE for each window. A decreasing trend = AUGE is learning.
    """
    outcome_map = {o.prediction_id: o for o in outcomes}
    errors_chronological: list[float] = []

    sorted_preds = sorted(predictions, key=lambda p: p.timestamp)
    for pred in sorted_preds:
        outcome = outcome_map.get(pred.prediction_id)
        if outcome:
            errors_chronological.append(abs(pred.predicted_value - outcome.actual_value))

    if len(errors_chronological) < 5:
        return errors_chronological

    window = 5
    trend: list[float] = []
    for i in range(0, len(errors_chronological) - window + 1, window):
        chunk = errors_chronological[i:i + window]
        trend.append(round(float(np.mean(chunk)), 2))

    return trend


def compute_adaptive_corrections(
    accuracy: SelfImprovementResponse,
    current_calibration: dict | None = None,
) -> dict[str, float]:
    """Translate accuracy metrics into concrete parameter corrections.

    These corrections can be applied to the frontend AUGE battery
    calibration (cnsDelta, muscularDelta, spinalDelta) to immediately
    improve prediction accuracy for the next session.
    """
    corrections = {
        "cnsDelta": 0.0,
        "muscularDelta": 0.0,
        "spinalDelta": 0.0,
    }

    for acc in accuracy.accuracy_by_system:
        if acc.sample_size < 3:
            continue

        correction = -acc.bias * 0.25

        if "cns" in acc.system.lower():
            corrections["cnsDelta"] = round(correction, 1)
        elif "muscul" in acc.system.lower():
            corrections["muscularDelta"] = round(correction, 1)
        elif "spinal" in acc.system.lower():
            corrections["spinalDelta"] = round(correction, 1)

    if current_calibration:
        for key in corrections:
            existing = current_calibration.get(key, 0)
            corrections[key] = round(corrections[key] + existing * 0.7, 1)

    return corrections
