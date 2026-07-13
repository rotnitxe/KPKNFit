import type { AugeAdaptiveCacheBase } from '@kpkn/shared-types';
import { getJsonValue, setJsonValue } from '../storage/mmkv';

const BACKEND_URL = 'http://localhost:8000';
const CACHE_KEY = 'auge_adaptive_cache';
const QUEUE_KEY = 'auge_adaptive_queue';

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

export interface AugeAdaptiveCache extends AugeAdaptiveCacheBase {
  totalObservations: number;
  modelAccuracy?: {
    mae?: number;
    rmse?: number;
    bias?: number;
  };
}

interface AugeAdaptiveQueue {
  recoveryObservations: RecoveryObservation[];
  fatigueData: FatigueDataPoint[];
  predictions: PredictionRecord[];
  outcomes: OutcomeRecord[];
  impulses: TrainingImpulse[];
}

const DEFAULT_CACHE: AugeAdaptiveCache = {
  cnsDelta: 0,
  muscularDelta: 0,
  spinalDelta: 0,
  muscleDeltas: {},
  lastCalibrated: new Date(0).toISOString(),
  personalizedRecoveryHours: {},
  totalObservations: 0,
};

const DEFAULT_QUEUE: AugeAdaptiveQueue = {
  recoveryObservations: [],
  fatigueData: [],
  predictions: [],
  outcomes: [],
  impulses: [],
};

function readCache() {
  return getJsonValue<AugeAdaptiveCache>(CACHE_KEY, DEFAULT_CACHE);
}

function writeCache(cache: AugeAdaptiveCache) {
  setJsonValue(CACHE_KEY, cache);
}

function readQueue() {
  return getJsonValue<AugeAdaptiveQueue>(QUEUE_KEY, DEFAULT_QUEUE);
}

function writeQueue(queue: AugeAdaptiveQueue) {
  setJsonValue(QUEUE_KEY, queue);
}

function enqueue<K extends keyof AugeAdaptiveQueue>(key: K, value: AugeAdaptiveQueue[K][number]) {
  const queue = readQueue();
  queue[key].push(value as never);
  writeQueue(queue);
}

export function queueRecoveryObservation(obs: RecoveryObservation): void {
  enqueue('recoveryObservations', obs);
}

export function queueFatigueDataPoint(dp: FatigueDataPoint): void {
  enqueue('fatigueData', dp);
}

export function queuePrediction(pred: PredictionRecord): void {
  enqueue('predictions', pred);
}

export function queueOutcome(outcome: OutcomeRecord): void {
  enqueue('outcomes', outcome);
}

export function queueTrainingImpulse(impulse: TrainingImpulse): void {
  enqueue('impulses', impulse);
}

export function getCachedAdaptiveData(): AugeAdaptiveCache {
  return readCache();
}

export function getConfidenceLabel(totalObservations: number): string {
  if (totalObservations >= 120) return 'Alta';
  if (totalObservations >= 40) return 'Media';
  return 'Inicial';
}

export function getConfidenceColor(totalObservations: number): string {
  if (totalObservations >= 120) return '#2E7D32';
  if (totalObservations >= 40) return '#F9A825';
  return '#616161';
}

export function getQueueSize(): number {
  const queue = readQueue();
  return (
    queue.recoveryObservations.length +
    queue.fatigueData.length +
    queue.predictions.length +
    queue.outcomes.length +
    queue.impulses.length
  );
}

export async function syncWithBackend(userId = 'local'): Promise<AugeAdaptiveCache> {
  const queue = readQueue();
  const queueSize = getQueueSize();
  if (queueSize === 0) {
    return readCache();
  }

  try {
    const response = await fetch(`${BACKEND_URL}/api/auge/adaptive/sync`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId,
        queuedAt: new Date().toISOString(),
        queue,
      }),
    });

    if (!response.ok) {
      throw new Error(`Backend sync failed (${response.status})`);
    }

    const payload = (await response.json()) as Partial<AugeAdaptiveCache>;
    const mergedCache: AugeAdaptiveCache = {
      ...readCache(),
      ...payload,
      totalObservations:
        typeof payload.totalObservations === 'number'
          ? payload.totalObservations
          : readCache().totalObservations + queueSize,
      lastCalibrated: new Date().toISOString(),
    };

    writeCache(mergedCache);
    writeQueue(DEFAULT_QUEUE);
    return mergedCache;
  } catch (error) {
    console.warn('[augeAdaptive] No se pudo sincronizar con backend, se mantiene cola local.', error);
    const cache = readCache();
    const offlineMerged: AugeAdaptiveCache = {
      ...cache,
      totalObservations: cache.totalObservations + queueSize,
      lastCalibrated: cache.lastCalibrated,
    };
    writeCache(offlineMerged);
    return offlineMerged;
  }
}
