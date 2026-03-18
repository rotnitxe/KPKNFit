// apps/mobile/src/services/augeRuntimeService.ts
// AUGE Adaptive Service — Ported from PWA
// Acumula datos durante la sesión y los envía en batch al backend al cerrar sesión.
// Cache local para funcionar offline.

import { computeAugeReadiness } from '@kpkn/shared-domain';
import { useWorkoutStore } from '../stores/workoutStore';
import { useWellbeingStore } from '../stores/wellbeingStore';
import { readStoredSettingsRaw } from './mobileDomainStateService';
import { AugeRuntimeSnapshot, AugeRuntimeDebug } from '../types/augeRuntime';
import { getJsonValue, setJsonValue } from '../storage/mmkv';

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
  articular_battery?: number;
  combined_readiness?: number;
  joint_id?: string;
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
  articular_load?: number;
  muscle_battery?: number;
  articular_battery?: number;
  combined_readiness?: number;
}

export interface PredictionRecord {
  prediction_id: string;
  timestamp: string;
  muscle?: string;
  joint?: string;
  system: string;
  predicted_value: number;
  context: Record<string, unknown>;
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
  cnsDelta: number;
  muscularDelta: number;
  spinalDelta: number;
  lastCalibrated: string;
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
  banisterHistory: TrainingImpulse[];
  lastSyncTimestamp: string;
}

interface AdaptiveQueue {
  recoveryObservations: RecoveryObservation[];
  fatigueDataPoints: FatigueDataPoint[];
  predictions: PredictionRecord[];
  outcomes: OutcomeRecord[];
  trainingImpulses: TrainingImpulse[];
}

const createEmptyQueue = (): AdaptiveQueue => ({
  recoveryObservations: [],
  fatigueDataPoints: [],
  predictions: [],
  outcomes: [],
  trainingImpulses: [],
});

const createEmptyCache = (): AugeAdaptiveCache => ({
  priors: {},
  totalObservations: 0,
  personalizedRecoveryHours: {},
  confidenceIntervals: {},
  gpCurve: null,
  cnsDelta: 0,
  muscularDelta: 0,
  spinalDelta: 0,
  lastCalibrated: '',
  banister: null,
  selfImprovement: null,
  banisterHistory: [],
  lastSyncTimestamp: '',
});

// ─── Queue Management ───────────────────────────────────────────

function loadQueue(): AdaptiveQueue {
  try {
    const raw = getJsonValue<AdaptiveQueue | null>(QUEUE_KEY, null);
    if (raw) {
      return { ...createEmptyQueue(), ...raw };
    }
  } catch { /* corrupt data */ }
  return createEmptyQueue();
}

function saveQueue(queue: AdaptiveQueue): void {
  try {
    setJsonValue(QUEUE_KEY, queue);
  } catch { /* quota */ }
}

// ─── Cache Management ───────────────────────────────────────────

function loadCache(): AugeAdaptiveCache {
  try {
    const raw = getJsonValue<AugeAdaptiveCache | null>(CACHE_KEY, null);
    if (raw) {
      return { ...createEmptyCache(), ...raw };
    }
  } catch { /* corrupt */ }
  return createEmptyCache();
}

function saveCache(cache: AugeAdaptiveCache): void {
  try {
    setJsonValue(CACHE_KEY, cache);
  } catch { /* quota */ }
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
  const normalized = normalizeTrainingImpulse(impulse);
  if (!normalized) return;
  q.trainingImpulses = mergeTrainingHistory(q.trainingImpulses, [normalized]);
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

export function getQueueSize(): number {
  const q = loadQueue();
  return q.recoveryObservations.length + q.fatigueDataPoints.length +
    q.predictions.length + q.outcomes.length + q.trainingImpulses.length;
}

// ─── Internal Helpers ───────────────────────────────────────────

function normalizeTrainingImpulse(impulse: TrainingImpulse | null | undefined): TrainingImpulse | null {
  const timestamp = Number(impulse?.timestamp_hours);
  const total = Number(impulse?.impulse);
  const cns = Number(impulse?.cns_impulse);
  const spinal = Number(impulse?.spinal_impulse);

  if (!Number.isFinite(timestamp) || !Number.isFinite(total)) return null;

  return {
    timestamp_hours: timestamp,
    impulse: Math.max(0, total),
    cns_impulse: Number.isFinite(cns) ? Math.max(0, cns) : 0,
    spinal_impulse: Number.isFinite(spinal) ? Math.max(0, spinal) : 0,
  };
}

function getTrainingImpulseKey(impulse: TrainingImpulse): string {
  return [
    Math.round(impulse.timestamp_hours * 1000),
    impulse.impulse.toFixed(4),
    impulse.cns_impulse.toFixed(4),
    impulse.spinal_impulse.toFixed(4),
  ].join('|');
}

function mergeTrainingHistory(...segments: (TrainingImpulse[] | undefined)[]): TrainingImpulse[] {
  const merged = new Map<string, TrainingImpulse>();

  segments.flat().forEach((entry) => {
    const normalized = normalizeTrainingImpulse(entry);
    if (!normalized) return;
    merged.set(getTrainingImpulseKey(normalized), normalized);
  });

  return Array.from(merged.values())
    .sort((a, b) => a.timestamp_hours - b.timestamp_hours)
    .slice(-240);
}

function buildRelativeTrainingHistory(history: TrainingImpulse[]): TrainingImpulse[] {
  if (history.length === 0) return [];
  const firstTimestamp = history[0].timestamp_hours;

  return history.map((entry) => ({
    ...entry,
    timestamp_hours: Math.max(0, entry.timestamp_hours - firstTimestamp),
  }));
}

// ─── Runtime Snapshot Builder ─────────────────────────────────────

export function buildAugeRuntimeInputs() {
  const workoutOverview = useWorkoutStore.getState().overview;
  const wellbeingOverview = useWellbeingStore.getState().overview;
  const settings = readStoredSettingsRaw();

  const hasWorkoutOverview = !!workoutOverview;
  const hasWellbeingSnapshot = !!wellbeingOverview?.latestSnapshot;
  const hasSettings = !!settings;

  const cnsBattery = workoutOverview?.battery?.overall ?? 60;

  const adaptiveCache = getCachedAdaptiveData();

  const configForEngine = {
    settings: settings || {},
    adaptiveCache: {
      cnsDelta: adaptiveCache.cnsDelta,
      muscularDelta: adaptiveCache.muscularDelta,
      spinalDelta: adaptiveCache.spinalDelta,
      lastCalibrated: adaptiveCache.lastCalibrated,
    },
    wellbeing: wellbeingOverview?.latestSnapshot || null,
    history: [],
    cnsBattery,
  };

  const runtimeDebug: AugeRuntimeDebug = {
    hasWorkoutOverview,
    hasWellbeingSnapshot,
    hasSettings,
    wellbeingStressLevel: wellbeingOverview?.latestSnapshot?.stressLevel ?? null,
    wellbeingSleepHours: (wellbeingOverview?.latestSnapshot as any)?.sleepHours ?? null,
  };

  const summaryBase = {
    activeProgramName: workoutOverview?.activeProgramName || null,
    weeklySessionCount: workoutOverview?.weeklySessionCount || 0,
    completedSetsThisWeek: workoutOverview?.completedSetsThisWeek || 0,
    plannedSetsThisWeek: workoutOverview?.plannedSetsThisWeek || 0,
  };

  return {
    configForEngine,
    runtimeDebug,
    summaryBase,
  };
}

export async function computeAugeRuntimeSnapshot(): Promise<{
  snapshot: AugeRuntimeSnapshot;
  debug: AugeRuntimeDebug;
}> {
  const { configForEngine, runtimeDebug, summaryBase } = buildAugeRuntimeInputs();

  try {
    const result = computeAugeReadiness(configForEngine as any);

    const snapshot: AugeRuntimeSnapshot = {
      computedAt: new Date().toISOString(),
      cnsBattery: configForEngine.cnsBattery,
      readinessStatus: result.status as 'green' | 'yellow' | 'red',
      stressMultiplier: result.stressMultiplier,
      recommendation: result.recommendation,
      diagnostics: result.diagnostics || [],
      ...summaryBase,
    };

    return { snapshot, debug: runtimeDebug };
  } catch (error) {
    console.error('[AugeRuntimeService] Error computing readiness:', error);

    const fallbackSnapshot: AugeRuntimeSnapshot = {
      computedAt: new Date().toISOString(),
      cnsBattery: configForEngine.cnsBattery,
      readinessStatus: 'yellow',
      stressMultiplier: 1.0,
      recommendation: 'Proceder con precaución. No se pudo calcular el estado exacto.',
      diagnostics: ['Error en motor AUGE'],
      ...summaryBase,
    };

    return { snapshot: fallbackSnapshot, debug: runtimeDebug };
  }
}

// ─── Adaptive Metrics Functions ─────────────────────────────────────

export function recalculateAdaptiveMetrics(
  history: TrainingImpulse[],
  settings: { userVitals?: { age?: number; weight?: number } }
): {
  predictedRecoveryTime: number;
  predictedFatigue: number;
  recommendedSessionIntensity: number;
} {
  if (!history || history.length === 0) {
    return {
      predictedRecoveryTime: 48,
      predictedFatigue: 0.3,
      recommendedSessionIntensity: 0.8,
    };
  }

  const totalImpulse = history.reduce((sum, h) => sum + h.impulse, 0);
  const avgImpulse = totalImpulse / history.length;
  const age = settings?.userVitals?.age || 30;

  const recoveryFactor = Math.max(0.5, 1 - (age - 20) * 0.005);
  const predictedRecoveryTime = Math.round(48 / recoveryFactor);
  const predictedFatigue = Math.min(1, avgImpulse / 100);
  const recommendedSessionIntensity = Math.max(0.5, 1 - predictedFatigue * 0.3);

  return {
    predictedRecoveryTime,
    predictedFatigue,
    recommendedSessionIntensity,
  };
}

export function getPredictedRecoveryTime(
  muscle: string,
  adaptiveCache: AugeAdaptiveCache
): number {
  const personalized = adaptiveCache.personalizedRecoveryHours[muscle];
  if (personalized) return personalized;

  const cache = loadCache();
  if (cache.personalizedRecoveryHours[muscle]) {
    return cache.personalizedRecoveryHours[muscle];
  }

  return 48;
}

export function getTrainingReadinessScore(
  cnsBattery: number,
  muscleBatteries: Record<string, number>,
  adaptiveCache: AugeAdaptiveCache
): number {
  const cnsFactor = cnsBattery / 100;
  const muscleValues = Object.values(muscleBatteries);
  const avgMuscle = muscleValues.length > 0
    ? muscleValues.reduce((a, b) => a + b, 0) / muscleValues.length / 100
    : 0.7;

  const recoveryBonus = adaptiveCache.totalObservations >= 20 ? 0.05 : 0;
  const penalty = adaptiveCache.cnsDelta < 0 ? Math.abs(adaptiveCache.cnsDelta) * 0.1 : 0;

  return Math.min(1, Math.max(0, (cnsFactor * 0.4 + avgMuscle * 0.6 + recoveryBonus - penalty)));
}

export function calculateOptimalTrainingWindow(
  adaptiveCache: AugeAdaptiveCache
): { startHour: number; endHour: number; recommendation: string } | null {
  if (!adaptiveCache.banister?.optimal_next_session_hour) {
    return null;
  }

  const optimalHour = adaptiveCache.banister.optimal_next_session_hour;
  const startHour = Math.max(0, optimalHour - 2);
  const endHour = optimalHour + 2;

  return {
    startHour,
    endHour,
    recommendation: `Ventana óptima detectada: ${formatHour(startHour)} - ${formatHour(endHour)}`,
  };
}

export function generateAdaptiveInsights(
  adaptiveCache: AugeAdaptiveCache,
  currentReadiness: number
): string[] {
  const insights: string[] = [];

  if (adaptiveCache.totalObservations >= 20) {
    insights.push('Alta confianza en predicciones de recuperación');
  } else if (adaptiveCache.totalObservations >= 10) {
    insights.push('Confianza media en predicciones');
  } else {
    insights.push('Recopilando datos para personalizar predicciones');
  }

  if (adaptiveCache.selfImprovement?.recommendations?.length) {
    insights.push(...adaptiveCache.selfImprovement.recommendations.slice(0, 2));
  }

  if (currentReadiness < 0.5) {
    insights.push('Considera reducir intensidad en la próxima sesión');
  } else if (currentReadiness > 0.85) {
    insights.push('Estado óptimo para entrenar con intensidad');
  }

  return insights;
}

function formatHour(hours: number): string {
  const h = Math.floor(hours) % 24;
  const suffix = h >= 12 ? 'PM' : 'AM';
  const displayHour = h > 12 ? h - 12 : h === 0 ? 12 : h;
  return `${displayHour}:00 ${suffix}`;
}

export function clearAdaptiveQueue(): void {
  setJsonValue(QUEUE_KEY, createEmptyQueue());
}

export function clearAdaptiveCache(): void {
  setJsonValue(CACHE_KEY, createEmptyCache());
}
