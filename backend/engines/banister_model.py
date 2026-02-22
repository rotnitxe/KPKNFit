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


def solve_banister(
    impulses: list[TrainingImpulse],
    params: BanisterParams,
    t_end: float,
    dt: float = 1.0,
) -> BanisterResponse:
    """Solve the Banister model forward in time.

    Returns timeline of Fitness, Fatigue, and predicted Performance.
    """
    if not impulses:
        n = int(t_end / dt) + 1
        timeline = [i * dt for i in range(n)]
        baseline = [params.p0] * n
        return BanisterResponse(
            timeline_hours=timeline,
            fitness=[0.0] * n,
            fatigue=[0.0] * n,
            performance=baseline,
        )

    t_start = min(imp.timestamp_hours for imp in impulses) - 1
    t_span = (t_start, t_start + t_end)
    t_eval = np.arange(t_start, t_start + t_end, dt)

    w_func = _build_impulse_function(impulses)

    sol = solve_ivp(
        fun=lambda t, y: _banister_ode(t, y, params, w_func),
        t_span=t_span,
        y0=[0.0, 0.0],
        t_eval=t_eval,
        method="RK45",
        max_step=0.5,
    )

    fitness = sol.y[0].tolist()
    fatigue = sol.y[1].tolist()
    timeline = (sol.t - t_start).tolist()

    performance = [
        params.p0 + params.k1 * f - params.k2 * g
        for f, g in zip(fitness, fatigue)
    ]

    peak_idx = int(np.argmax(performance))
    peak_hour = timeline[peak_idx] if timeline else None

    last_impulse_t = max(imp.timestamp_hours for imp in impulses)
    optimal_next = None
    for i, t in enumerate(sol.t.tolist()):
        if t > last_impulse_t and i > 0:
            if performance[i] > performance[i - 1] and (
                i + 1 >= len(performance) or performance[i] >= performance[i + 1]
            ):
                optimal_next = timeline[i]
                break

    return BanisterResponse(
        timeline_hours=[round(t, 1) for t in timeline],
        fitness=[round(f, 2) for f in fitness],
        fatigue=[round(f, 2) for f in fatigue],
        performance=[round(p, 1) for p in performance],
        next_optimal_session_hour=round(optimal_next, 1) if optimal_next else None,
        predicted_peak_performance_hour=round(peak_hour, 1) if peak_hour else None,
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
            result = solve_banister(impulses, trial, t_end, dt=1.0)
        except Exception:
            return 1e10

        predicted = []
        for t_obs in obs_times:
            idx = int(round(t_obs))
            idx = max(0, min(idx, len(result.performance) - 1))
            predicted.append(result.performance[idx])

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
        peak_idx = int(np.argmax(combined_perf))
        last_impulse = max((imp.timestamp_hours for imp in impulses), default=0)
        timeline = results["muscular"]["timeline_hours"]
        for i in range(len(timeline)):
            t = timeline[i] if i < len(timeline) else 0
            if t > last_impulse and i > 0 and i < len(combined_perf):
                if combined_perf[i] >= combined_perf[i - 1]:
                    if i + 1 >= len(combined_perf) or combined_perf[i] >= combined_perf[i + 1]:
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

    current = combined[0] if combined else 100
    trend = combined[-1] - combined[0] if len(combined) > 1 else 0

    if current > 105 and trend > 0:
        return (
            "Tu fitness acumulada supera tu fatiga residual. "
            "Estás en fase de supercompensación — momento ideal para PRs."
        )
    elif current < 90:
        return (
            "Tu fatiga acumulada domina sobre tu fitness. "
            "Necesitas un deload o reducir frecuencia para que tu cuerpo absorba las adaptaciones."
        )
    elif trend < -5:
        return (
            "Tu rendimiento está en tendencia descendente. "
            "Acumulas más fatiga de la que puedes procesar — considera reducir volumen un 30%."
        )
    else:
        return (
            "Tu balance fitness-fatiga es estable. "
            "Sigue con tu plan actual y ajusta según el semáforo diario AUGE."
        )
