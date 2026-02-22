// workers/computeWorker.ts
// Self-contained Web Worker for offloading heavy AUGE/Volume computations
// off the main thread. Built as a separate esbuild entry (IIFE format).

import {
    calculateMuscleBattery,
    calculateGlobalBatteries,
    calculateSystemicFatigue,
    calculateDailyReadiness,
} from '../services/recoveryService';

import {
    calculatePredictedSessionDrain,
    calculateCompletedSessionStress,
} from '../services/fatigueService';

import {
    calculateACWR,
    calculateAverageVolumeForWeeks,
    calculateWeeklyTonnageComparison,
} from '../services/analysisService';

type FnMap = Record<string, (...args: any[]) => any>;

const FUNCTIONS: FnMap = {
    calculateMuscleBattery,
    calculateGlobalBatteries,
    calculateSystemicFatigue,
    calculateDailyReadiness,
    calculatePredictedSessionDrain,
    calculateCompletedSessionStress,
    calculateACWR,
    calculateAverageVolumeForWeeks,
    calculateWeeklyTonnageComparison,
};

interface WorkerRequest {
    id: string;
    fn: string;
    args: any[];
}

interface WorkerResponse {
    id: string;
    result?: any;
    error?: string;
}

self.addEventListener('message', (e: MessageEvent<WorkerRequest>) => {
    const { id, fn, args } = e.data;
    try {
        const handler = FUNCTIONS[fn];
        if (!handler) {
            (self as any).postMessage({ id, error: `Unknown function: ${fn}` } satisfies WorkerResponse);
            return;
        }
        const result = handler(...args);
        (self as any).postMessage({ id, result } satisfies WorkerResponse);
    } catch (err) {
        (self as any).postMessage({ id, error: (err as Error).message } satisfies WorkerResponse);
    }
});
