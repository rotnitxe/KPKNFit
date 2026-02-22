// services/computeWorkerService.ts
// Singleton service that manages the compute Web Worker and exposes
// Promise-based wrappers for heavy computation functions.

type PendingRequest = {
    resolve: (value: any) => void;
    reject: (reason: any) => void;
};

let worker: Worker | null = null;
let requestId = 0;
const pending = new Map<string, PendingRequest>();

function getWorker(): Worker | null {
    if (worker) return worker;
    if (typeof Worker === 'undefined') return null;

    try {
        worker = new Worker('./computeWorker.js');
        worker.addEventListener('message', (e: MessageEvent) => {
            const { id, result, error } = e.data;
            const req = pending.get(id);
            if (!req) return;
            pending.delete(id);
            if (error) req.reject(new Error(error));
            else req.resolve(result);
        });
        worker.addEventListener('error', () => {
            // If the worker fails to load, all pending requests fall back
            pending.forEach(req => req.reject(new Error('Worker error')));
            pending.clear();
            worker = null;
        });
        return worker;
    } catch {
        return null;
    }
}

function callWorker<T>(fn: string, args: any[]): Promise<T> {
    const w = getWorker();
    if (!w) return Promise.reject(new Error('Worker unavailable'));

    const id = String(++requestId);
    return new Promise<T>((resolve, reject) => {
        pending.set(id, { resolve, reject });
        w.postMessage({ id, fn, args });
    });
}

// Wraps a sync function with a worker-based async version.
// Falls back to running synchronously on the main thread if the worker is unavailable.
function withWorkerFallback<T extends (...args: any[]) => any>(
    fn: T,
    fnName: string
): (...args: Parameters<T>) => Promise<ReturnType<T>> {
    return (...args: Parameters<T>): Promise<ReturnType<T>> => {
        const w = getWorker();
        if (!w) {
            try {
                return Promise.resolve(fn(...args));
            } catch (err) {
                return Promise.reject(err);
            }
        }
        return callWorker<ReturnType<T>>(fnName, args);
    };
}

// Re-export the sync originals for fallback usage
import {
    calculateMuscleBattery,
    calculateGlobalBatteries,
    calculateSystemicFatigue,
    calculateDailyReadiness,
} from './recoveryService';

import {
    calculatePredictedSessionDrain,
    calculateCompletedSessionStress,
} from './fatigueService';

import {
    calculateACWR,
    calculateAverageVolumeForWeeks,
    calculateWeeklyTonnageComparison,
} from './analysisService';

export const calculateMuscleBatteryAsync = withWorkerFallback(
    calculateMuscleBattery, 'calculateMuscleBattery'
);

export const calculateGlobalBatteriesAsync = withWorkerFallback(
    calculateGlobalBatteries, 'calculateGlobalBatteries'
);

export const calculateSystemicFatigueAsync = withWorkerFallback(
    calculateSystemicFatigue, 'calculateSystemicFatigue'
);

export const calculateDailyReadinessAsync = withWorkerFallback(
    calculateDailyReadiness, 'calculateDailyReadiness'
);

export const calculatePredictedSessionDrainAsync = withWorkerFallback(
    calculatePredictedSessionDrain, 'calculatePredictedSessionDrain'
);

export const calculateCompletedSessionStressAsync = withWorkerFallback(
    calculateCompletedSessionStress, 'calculateCompletedSessionStress'
);

export const calculateACWRAsync = withWorkerFallback(
    calculateACWR, 'calculateACWR'
);

export const calculateAverageVolumeForWeeksAsync = withWorkerFallback(
    calculateAverageVolumeForWeeks, 'calculateAverageVolumeForWeeks'
);

export const calculateWeeklyTonnageComparisonAsync = withWorkerFallback(
    calculateWeeklyTonnageComparison, 'calculateWeeklyTonnageComparison'
);

export function terminateWorker() {
    if (worker) {
        worker.terminate();
        worker = null;
        pending.forEach(req => req.reject(new Error('Worker terminated')));
        pending.clear();
    }
}
