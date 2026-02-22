// services/augeAdaptiveService.ts
// ============================================================================
// AUGE Adaptive Service — Pipeline Batch hacia Backend Python
// ============================================================================
// Acumula datos durante la sesión (recovery observations, fatigue data points,
// prediction records) y los envía en batch al backend al cerrar sesión.
// Cache local para funcionar offline.
// ============================================================================

const BACKEND_URL = (import.meta as any)?.env?.VITE_BACKEND_URL || 'http://localhost:8000';
const CACHE_KEY = 'auge_adaptive_cache';
const QUEUE_KEY = 'auge_adaptive_queue';

// ─── Types ──────────────────────────────────────────────────────

export interface RecoveryObservation {
    muscle: string;
    session_stress: number;
    hours_since_session: number;
    predicted_battery: number;
    actual_battery: number;
    sleep_quality?: number;
    nutrition_status?: string;
    stress_level?: number;
}

export interface FatigueDataPoint {
    hours_since_session: number;
    session_stress: number;
    sleep_hours: number;
    nutrition_status: number;
    stress_level: number;
    age: number;
    is_compound_dominant: boolean;
    observed_fatigue_fraction: number;
}

export interface PredictionRecord {
    prediction_id: string;
    timestamp: string;
    muscle?: string;
    system: string;
    predicted_value: number;
    context: Record<string, any>;
}

export interface OutcomeRecord {
    prediction_id: string;
    actual_value: number;
    feedback_source: string;
}

export interface TrainingImpulse {
    timestamp_hours: number;
    impulse: number;
    cns_impulse: number;
    spinal_impulse: number;
}

export interface GammaPrior {
    alpha: number;
    beta: number;
}

export interface GPFatiguePrediction {
    hours: number[];
    mean_fatigue: number[];
    upper_bound: number[];
    lower_bound: number[];
    peak_fatigue_hour: number;
    supercompensation_hour: number | null;
    full_recovery_hour: number;
}

export interface BanisterSystemResult {
    timeline_hours: number[];
    fitness: number[];
    fatigue: number[];
    performance: number[];
    next_optimal_session_hour: number | null;
    predicted_peak_performance_hour: number | null;
}

export interface ModelAccuracy {
    system: string;
    mae: number;
    rmse: number;
    bias: number;
    r_squared: number;
    sample_size: number;
}

export interface AugeAdaptiveCache {
    priors: Record<string, GammaPrior>;
    totalObservations: number;
    personalizedRecoveryHours: Record<string, number>;
    confidenceIntervals: Record<string, [number, number]>;
    gpCurve: GPFatiguePrediction | null;
    banister: {
        systems: Record<string, BanisterSystemResult>;
        combined_performance: number[];
        optimal_next_session_hour: number | null;
        verdict: string;
    } | null;
    selfImprovement: {
        accuracy_by_system: ModelAccuracy[];
        overall_prediction_score: number;
        improvement_trend: number[];
        recommendations: string[];
        suggested_adjustments: Record<string, number>;
    } | null;
    lastSyncTimestamp: string;
}

interface AdaptiveQueue {
    recoveryObservations: RecoveryObservation[];
    fatigueDataPoints: FatigueDataPoint[];
    predictions: PredictionRecord[];
    outcomes: OutcomeRecord[];
    trainingImpulses: TrainingImpulse[];
}

// ─── Queue Management ───────────────────────────────────────────

function loadQueue(): AdaptiveQueue {
    try {
        const raw = localStorage.getItem(QUEUE_KEY);
        if (raw) return JSON.parse(raw);
    } catch { /* corrupt data */ }
    return { recoveryObservations: [], fatigueDataPoints: [], predictions: [], outcomes: [], trainingImpulses: [] };
}

function saveQueue(queue: AdaptiveQueue): void {
    try { localStorage.setItem(QUEUE_KEY, JSON.stringify(queue)); } catch { /* quota */ }
}

function clearQueue(): void {
    localStorage.removeItem(QUEUE_KEY);
}

// ─── Cache Management ───────────────────────────────────────────

function loadCache(): AugeAdaptiveCache {
    try {
        const raw = localStorage.getItem(CACHE_KEY);
        if (raw) return JSON.parse(raw);
    } catch { /* corrupt */ }
    return {
        priors: {},
        totalObservations: 0,
        personalizedRecoveryHours: {},
        confidenceIntervals: {},
        gpCurve: null,
        banister: null,
        selfImprovement: null,
        lastSyncTimestamp: '',
    };
}

function saveCache(cache: AugeAdaptiveCache): void {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(cache)); } catch { /* quota */ }
}

// ─── Public API: Queue Data ─────────────────────────────────────

export function queueRecoveryObservation(obs: RecoveryObservation): void {
    const q = loadQueue();
    q.recoveryObservations.push(obs);
    saveQueue(q);
}

export function queueFatigueDataPoint(dp: FatigueDataPoint): void {
    const q = loadQueue();
    q.fatigueDataPoints.push(dp);
    saveQueue(q);
}

export function queuePrediction(pred: PredictionRecord): void {
    const q = loadQueue();
    q.predictions.push(pred);
    saveQueue(q);
}

export function queueOutcome(outcome: OutcomeRecord): void {
    const q = loadQueue();
    q.outcomes.push(outcome);
    saveQueue(q);
}

export function queueTrainingImpulse(impulse: TrainingImpulse): void {
    const q = loadQueue();
    q.trainingImpulses.push(impulse);
    saveQueue(q);
}

// ─── Public API: Read Cache ─────────────────────────────────────

export function getCachedAdaptiveData(): AugeAdaptiveCache {
    return loadCache();
}

export function getConfidenceLabel(totalObservations: number): string {
    if (totalObservations >= 20) return 'alta';
    if (totalObservations >= 10) return 'media';
    if (totalObservations >= 3) return 'baja';
    return 'poblacional';
}

export function getConfidenceColor(totalObservations: number): string {
    if (totalObservations >= 20) return 'text-yellow-400';
    if (totalObservations >= 10) return 'text-green-400';
    if (totalObservations >= 3) return 'text-blue-400';
    return 'text-zinc-500';
}

export function getQueueSize(): number {
    const q = loadQueue();
    return q.recoveryObservations.length + q.fatigueDataPoints.length +
           q.predictions.length + q.outcomes.length + q.trainingImpulses.length;
}

// ─── Public API: Sync with Backend ──────────────────────────────

export async function syncWithBackend(userId: string = 'local'): Promise<AugeAdaptiveCache> {
    const queue = loadQueue();
    const cache = loadCache();

    const results = await Promise.allSettled([
        syncRecovery(userId, queue, cache),
        syncFatigue(userId, queue),
        syncBanister(userId, queue),
        syncSelfImprovement(userId, queue),
    ]);

    const [recoveryResult, fatigueResult, banisterResult, improvementResult] = results;

    if (recoveryResult.status === 'fulfilled' && recoveryResult.value) {
        cache.priors = recoveryResult.value.priors;
        cache.totalObservations = recoveryResult.value.totalObservations;
        cache.personalizedRecoveryHours = recoveryResult.value.personalizedRecoveryHours;
        cache.confidenceIntervals = recoveryResult.value.confidenceIntervals;
    }
    if (fatigueResult.status === 'fulfilled' && fatigueResult.value) {
        cache.gpCurve = fatigueResult.value;
    }
    if (banisterResult.status === 'fulfilled' && banisterResult.value) {
        cache.banister = banisterResult.value;
    }
    if (improvementResult.status === 'fulfilled' && improvementResult.value) {
        cache.selfImprovement = improvementResult.value;
    }

    cache.lastSyncTimestamp = new Date().toISOString();
    saveCache(cache);
    clearQueue();

    return cache;
}

// ─── Internal: Backend Calls ────────────────────────────────────

async function syncRecovery(userId: string, queue: AdaptiveQueue, cache: AugeAdaptiveCache) {
    if (queue.recoveryObservations.length === 0) return null;

    const res = await fetch(`${BACKEND_URL}/api/adaptive/recovery/update`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            user_id: userId,
            observations: queue.recoveryObservations,
            current_priors: {
                muscle_priors: cache.priors,
                total_observations: cache.totalObservations,
                last_updated: cache.lastSyncTimestamp,
            },
        }),
    });

    if (!res.ok) throw new Error(`Recovery sync failed: ${res.status}`);
    const data = await res.json();

    return {
        priors: Object.fromEntries(
            Object.entries(data.updated_priors?.muscle_priors || {}).map(
                ([k, v]: [string, any]) => [k, { alpha: v.alpha, beta: v.beta }]
            )
        ),
        totalObservations: data.updated_priors?.total_observations || 0,
        personalizedRecoveryHours: data.personalized_recovery_hours || {},
        confidenceIntervals: data.confidence_intervals || {},
    };
}

async function syncFatigue(userId: string, queue: AdaptiveQueue) {
    if (queue.fatigueDataPoints.length === 0) return null;

    const res = await fetch(`${BACKEND_URL}/api/adaptive/fatigue/predict`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            user_id: userId,
            training_data: queue.fatigueDataPoints,
            prediction_hours: [0, 6, 12, 18, 24, 36, 48, 60, 72, 96, 120],
            session_stress: queue.fatigueDataPoints.length > 0
                ? queue.fatigueDataPoints[queue.fatigueDataPoints.length - 1].session_stress
                : 50,
        }),
    });

    if (!res.ok) throw new Error(`Fatigue sync failed: ${res.status}`);
    return await res.json() as GPFatiguePrediction;
}

async function syncBanister(userId: string, queue: AdaptiveQueue) {
    if (queue.trainingImpulses.length === 0) return null;

    const res = await fetch(`${BACKEND_URL}/api/adaptive/banister/auge`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            training_history: queue.trainingImpulses,
            forecast_hours: 168,
        }),
    });

    if (!res.ok) throw new Error(`Banister sync failed: ${res.status}`);
    return await res.json();
}

async function syncSelfImprovement(userId: string, queue: AdaptiveQueue) {
    if (queue.predictions.length === 0 || queue.outcomes.length === 0) return null;

    const res = await fetch(`${BACKEND_URL}/api/adaptive/self-improve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            user_id: userId,
            predictions: queue.predictions,
            outcomes: queue.outcomes,
        }),
    });

    if (!res.ok) throw new Error(`Self-improvement sync failed: ${res.status}`);
    return await res.json();
}
