"""Banister Fitness-Fatigue ODE Model — Sports Science Gold Standard

The Banister impulse-response model (1975) is the most validated mathematical
model in exercise physiology. It models performance as the NET result of two
competing biological processes:

    Performance(t) = p₀ + k₁·Fitness(t) − k₂·Fatigue(t)

Where Fitness and Fatigue are governed by ODEs:
    dFitness/dt = −Fitness/τ₁ + w(t)    (slow to build, slow to decay)
    dFatigue/dt = −Fatigue/τ₂ + w(t)    (fast to build, fast to decay)

Key insight: τ₁ >> τ₂ (fitness lasts longer than fatigue).
This is WHY you get stronger AFTER a deload — fatigue drops faster than fitness.

In AUGE, we extend the classic model to 3 systems:
    - Muscular fitness/fatigue
    - CNS fitness/fatigue
    - Spinal fitness/fatigue

Each with its own time constants and coupling terms, because a deadlift
doesn't affect your biceps the same way it affects your erectors.

We use SciPy's solve_ivp (Runge-Kutta 4/5) for numerical integration and
scipy.optimize.minimize for parameter estimation from actual performance data.
"""
from __future__ import annotations

import numpy as np
from scipy.integrate import solve_ivp
from scipy.optimize import minimize

from models.adaptive import (
    BanisterParams,
    TrainingImpulse,
    BanisterResponse,
)


def _build_impulse_function(
    impulses: list[TrainingImpulse],
    sigma: float = 1.0,
) -> callable:
    """Create a continuous training impulse function w(t) from discrete sessions.

    Each session is modeled as a Gaussian pulse centered at the session time
    with width σ (default 1 hour). This makes the ODE smooth and integrable.
    """
    times = np.array([imp.timestamp_hours for imp in impulses])
    magnitudes = np.array([imp.impulse for imp in impulses])

    def w(t: float) -> float:
        if len(times) == 0:
            return 0.0
        dt = t - times
        weights = np.exp(-0.5 * (dt / sigma) ** 2) / (sigma * np.sqrt(2 * np.pi))
        return float(np.dot(magnitudes, weights))

    return w


def _banister_ode(t: float, state: np.ndarray, params: BanisterParams, w_func) -> list:
    """The Banister ODE system.

    state = [Fitness, Fatigue]
    dF/dt = -F/τ₁ + w(t)
    dG/dt = -G/τ₂ + w(t)
    """
    F, G = state
    w = w_func(t)
    dF = -F / params.tau1 + w
    dG = -G / params.tau2 + w
    return [dF, dG]


def _normalize_impulses(
    impulses: list[TrainingImpulse],
) -> tuple[list[TrainingImpulse], float]:
    """Sort impulses and shift them to a stable zero-based timeline."""
    if not impulses:
        return [], 0.0

    ordered = sorted(impulses, key=lambda imp: imp.timestamp_hours)
    first_timestamp = float(ordered[0].timestamp_hours)
    normalized = [
        TrainingImpulse(
            timestamp_hours=float(imp.timestamp_hours) - first_timestamp,
            impulse=float(imp.impulse),
            cns_impulse=float(getattr(imp, "cns_impulse", 0.0) or 0.0),
            spinal_impulse=float(getattr(imp, "spinal_impulse", 0.0) or 0.0),
        )
        for imp in ordered
    ]
    return normalized, float(normalized[-1].timestamp_hours)


def _solve_banister_trajectory(
    impulses: list[TrainingImpulse],
    params: BanisterParams,
    forecast_hours: float,
    dt: float = 1.0,
) -> dict:
    """Solve the full Banister trajectory from the first impulse through forecast."""
    forecast_window = max(float(forecast_hours), 24.0)

    if not impulses:
        timeline = np.arange(0.0, forecast_window + dt, dt)
        baseline = np.full_like(timeline, params.p0, dtype=float)
        zeros = np.zeros_like(timeline, dtype=float)
        return {
            "timeline": timeline,
            "fitness": zeros,
            "fatigue": zeros,
            "performance": baseline,
            "current_index": 0,
        }

    normalized_impulses, last_impulse_t = _normalize_impulses(impulses)
    t_end = last_impulse_t + forecast_window
    t_eval = np.arange(0.0, t_end + dt, dt)
    if len(t_eval) == 0 or t_eval[-1] < t_end:
        t_eval = np.append(t_eval, t_end)

    w_func = _build_impulse_function(normalized_impulses)

    sol = solve_ivp(
        fun=lambda t, y: _banister_ode(t, y, params, w_func),
        t_span=(0.0, t_end),
        y0=[0.0, 0.0],
        t_eval=t_eval,
        method="RK45",
        max_step=0.5,
    )

    performance = params.p0 + params.k1 * sol.y[0] - params.k2 * sol.y[1]
    current_index = int(np.searchsorted(sol.t, last_impulse_t, side="left"))
    current_index = min(max(current_index, 0), len(sol.t) - 1)

    return {
        "timeline": sol.t,
        "fitness": sol.y[0],
        "fatigue": sol.y[1],
        "performance": performance,
        "current_index": current_index,
    }


def solve_banister(
    impulses: list[TrainingImpulse],
    params: BanisterParams,
    t_end: float,
    dt: float = 1.0,
) -> BanisterResponse:
    """Solve the Banister model forward in time.

    Returns the current-to-future timeline of Fitness, Fatigue, and predicted
    Performance, anchored at the most recent training session.
    """
    trajectory = _solve_banister_trajectory(impulses, params, t_end, dt=dt)
    current_index = trajectory["current_index"]
    full_timeline = trajectory["timeline"]
    timeline = (full_timeline[current_index:] - full_timeline[current_index]).tolist()
    fitness = trajectory["fitness"][current_index:].tolist()
    fatigue = trajectory["fatigue"][current_index:].tolist()
    performance = trajectory["performance"][current_index:].tolist()

    peak_idx = int(np.argmax(performance)) if performance else 0
    peak_hour = timeline[peak_idx] if performance else None

    optimal_next = None
    for i, t in enumerate(timeline):
        if t <= 0 or i == 0:
            continue
        prev_perf = performance[i - 1]
        next_perf = performance[i + 1] if i + 1 < len(performance) else performance[i]
        if performance[i] >= prev_perf and performance[i] >= next_perf:
            optimal_next = t
            break

    return BanisterResponse(
        timeline_hours=[round(t, 1) for t in timeline],
        fitness=[round(f, 2) for f in fitness],
        fatigue=[round(f, 2) for f in fatigue],
        performance=[round(p, 1) for p in performance],
        next_optimal_session_hour=round(optimal_next, 1) if optimal_next is not None else None,
        predicted_peak_performance_hour=round(peak_hour, 1) if peak_hour is not None else None,
    )


def optimize_banister_params(
    impulses: list[TrainingImpulse],
    performance_observations: list[dict],
    initial_params: BanisterParams | None = None,
) -> BanisterParams:
    """Fit Banister parameters to actual performance data.

    Uses Nelder-Mead optimization to minimize the squared error between
    model predictions and observed performance outcomes (e.g. 1RM estimates,
    readiness scores, or battery calibration values).

    performance_observations: [{"time_hours": float, "performance": float}, ...]
    """
    params = initial_params or BanisterParams()

    if not performance_observations or len(performance_observations) < 3:
        return params

    obs_times = np.array([o["time_hours"] for o in performance_observations])
    obs_perf = np.array([o["performance"] for o in performance_observations])
    t_end = float(np.max(obs_times)) + 24

    def objective(x):
        trial = BanisterParams(
            p0=x[0],
            k1=max(0.01, x[1]),
            k2=max(0.01, x[2]),
            tau1=max(5.0, x[3]),
            tau2=max(2.0, min(x[3] - 1, x[4])),
        )

        try:
            trajectory = _solve_banister_trajectory(impulses, trial, t_end, dt=1.0)
        except Exception:
            return 1e10

        predicted = []
        for t_obs in obs_times:
            idx = int(round(t_obs))
            idx = max(0, min(idx, len(trajectory["performance"]) - 1))
            predicted.append(float(trajectory["performance"][idx]))

        predicted = np.array(predicted)
        return float(np.sum((predicted - obs_perf) ** 2))

    x0 = [params.p0, params.k1, params.k2, params.tau1, params.tau2]

    result = minimize(
        objective,
        x0,
        method="Nelder-Mead",
        options={"maxiter": 500, "xatol": 0.1, "fatol": 0.5},
    )

    if result.success or result.fun < objective(x0):
        x = result.x
        return BanisterParams(
            p0=round(float(x[0]), 1),
            k1=round(max(0.01, float(x[1])), 3),
            k2=round(max(0.01, float(x[2])), 3),
            tau1=round(max(5.0, float(x[3])), 1),
            tau2=round(max(2.0, float(x[4])), 1),
        )

    return params


# ═══════════════════════════════════════════════════════════════════
# EXTENDED 3-SYSTEM BANISTER (AUGE-SPECIFIC)
# ═══════════════════════════════════════════════════════════════════

def solve_auge_banister(
    impulses: list[TrainingImpulse],
    forecast_hours: float = 168.0,
) -> dict:
    """Solve the extended 3-system Banister model for AUGE.

    Each system (muscular, CNS, spinal) has its own time constants
    calibrated from exercise physiology literature:

    Muscular: τ₁=42d, τ₂=12d (muscle adaptation is slow, fatigue clears fast)
    CNS:      τ₁=35d, τ₂=8d  (neural adaptation moderate, fatigue clears fastest)
    Spinal:   τ₁=60d, τ₂=20d (connective tissue adapts slowest, fatigue lingers)
    """
    systems = {
        "muscular": BanisterParams(p0=100, k1=0.8, k2=1.5, tau1=42*24, tau2=12*24),
        "cns": BanisterParams(p0=100, k1=1.0, k2=2.0, tau1=35*24, tau2=8*24),
        "spinal": BanisterParams(p0=100, k1=0.5, k2=1.2, tau1=60*24, tau2=20*24),
    }

    results = {}
    for system_name, system_params in systems.items():
        system_impulses = []
        for imp in impulses:
            magnitude = imp.impulse
            if system_name == "cns":
                magnitude = imp.cns_impulse if imp.cns_impulse else imp.impulse * 0.8
            elif system_name == "spinal":
                magnitude = imp.spinal_impulse if imp.spinal_impulse else imp.impulse * 0.6

            system_impulses.append(TrainingImpulse(
                timestamp_hours=imp.timestamp_hours,
                impulse=magnitude,
            ))

        result = solve_banister(system_impulses, system_params, forecast_hours, dt=6.0)
        results[system_name] = {
            "timeline_hours": result.timeline_hours,
            "fitness": result.fitness,
            "fatigue": result.fatigue,
            "performance": result.performance,
            "next_optimal_session_hour": result.next_optimal_session_hour,
            "predicted_peak_performance_hour": result.predicted_peak_performance_hour,
        }

    combined_perf = []
    if results.get("muscular") and results["muscular"]["performance"]:
        n = len(results["muscular"]["performance"])
        for i in range(n):
            musc = results["muscular"]["performance"][i]
            cns = results["cns"]["performance"][i] if i < len(results["cns"]["performance"]) else 100
            spinal = results["spinal"]["performance"][i] if i < len(results["spinal"]["performance"]) else 100
            combined = musc * 0.4 + cns * 0.35 + spinal * 0.25
            combined_perf.append(round(combined, 1))

    optimal_window = None
    if combined_perf:
        timeline = results["muscular"]["timeline_hours"]
        for i, t in enumerate(timeline):
            if t <= 0 or i == 0 or i >= len(combined_perf):
                continue
            prev_perf = combined_perf[i - 1]
            next_perf = combined_perf[i + 1] if i + 1 < len(combined_perf) else combined_perf[i]
            if combined_perf[i] >= prev_perf and combined_perf[i] >= next_perf:
                optimal_window = timeline[i]
                break

    return {
        "systems": results,
        "combined_performance": combined_perf,
        "optimal_next_session_hour": optimal_window,
        "verdict": _generate_banister_verdict(results, combined_perf),
    }


def _generate_banister_verdict(results: dict, combined: list[float]) -> str:
    """Generate a human-readable verdict from the Banister model state."""
    if not combined:
        return "Sin datos suficientes para modelar fitness-fatiga."

    current = combined[0]
    peak_future = max(combined)
    peak_gain = peak_future - current
    end_trend = combined[-1] - current if len(combined) > 1 else 0

    return (
        "Tu fatiga acumulada domina sobre tu fitness. "
        "Necesitas un deload o reducir frecuencia para que tu cuerpo absorba las adaptaciones."
    ) if current < 92 else (
        "Tu curva proyecta una ventana clara de supercompensacion. "
        "Conviene respetar la recuperacion antes de la siguiente sesion pesada."
    ) if peak_gain >= 4 else (
        "Tu rendimiento proyectado sigue descendiendo. "
        "Acumulas mas fatiga de la que puedes procesar; considera reducir volumen un 30%."
    ) if end_trend < -4 else (
        "Tu balance fitness-fatiga es estable. "
        "Sigue con tu plan actual y ajusta segun el semaforo diario AUGE."
    )

    if current < 92:
        return (
            "Tu fatiga acumulada domina sobre tu fitness. "
            "Estás en fase de supercompensación — momento ideal para PRs."
        )
    elif peak_gain >= 4:
        return (
            "Tu fatiga acumulada domina sobre tu fitness. "
            "Necesitas un deload o reducir frecuencia para que tu cuerpo absorba las adaptaciones."
        )
    elif end_trend < -4:
        return (
            "Tu rendimiento está en tendencia descendente. "
            "Acumulas más fatiga de la que puedes procesar — considera reducir volumen un 30%."
        )
    else:
        return (
            "Tu balance fitness-fatiga es estable. "
            "Sigue con tu plan actual y ajusta según el semáforo diario AUGE."
        )
