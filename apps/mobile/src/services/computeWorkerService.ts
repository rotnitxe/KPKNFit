import {
  calculateMuscleBattery,
  calculateGlobalBatteries,
  calculateSystemicFatigue,
  calculateDailyReadiness,
  calculatePredictedSessionDrain,
  calculateCompletedSessionStress,
} from './auge';
import {
  calculateACWR,
  calculateAverageVolumeForWeeks,
  calculateWeeklyTonnageComparison,
} from './analysisService';

type SyncFn<TArgs extends unknown[], TResult> = (...args: TArgs) => TResult;

function withAsyncFallback<TArgs extends unknown[], TResult>(fn: SyncFn<TArgs, TResult>) {
  return (...args: TArgs): Promise<TResult> => {
    try {
      return Promise.resolve(fn(...args));
    } catch (error) {
      return Promise.reject(error);
    }
  };
}

export const calculateMuscleBatteryAsync = withAsyncFallback(calculateMuscleBattery);
export const calculateGlobalBatteriesAsync = withAsyncFallback(calculateGlobalBatteries);
export const calculateSystemicFatigueAsync = withAsyncFallback(calculateSystemicFatigue);
export const calculateDailyReadinessAsync = withAsyncFallback(calculateDailyReadiness);
export const calculatePredictedSessionDrainAsync = withAsyncFallback(calculatePredictedSessionDrain);
export const calculateCompletedSessionStressAsync = withAsyncFallback(calculateCompletedSessionStress);

export const calculateACWRAsync = withAsyncFallback(calculateACWR);
export const calculateAverageVolumeForWeeksAsync = withAsyncFallback(calculateAverageVolumeForWeeks);
export const calculateWeeklyTonnageComparisonAsync = withAsyncFallback(calculateWeeklyTonnageComparison);

export function terminateWorker() {
  // RN no usa Web Worker para este runtime; mantenemos API compatible con PWA.
}
