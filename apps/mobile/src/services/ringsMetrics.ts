import type { StoredWellbeingPayload } from './mobileDomainStateService';

type NumericLog = Record<string, unknown>;

function isRecord(value: unknown): value is NumericLog {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function averageFirstSeven(values: unknown[], selector: (log: NumericLog) => unknown): number | null {
  const samples = values
    .slice(0, 7)
    .filter(isRecord)
    .map(selector)
    .map(value => Number(value))
    .filter(value => Number.isFinite(value));

  if (samples.length === 0) return null;
  return Math.round(samples.reduce((sum, value) => sum + value, 0) / samples.length);
}

export function calculateRingsMetrics(payload: StoredWellbeingPayload) {
  const sleepLogs = Array.isArray(payload.sleepLogs) ? payload.sleepLogs : [];
  const dailyWellbeingLogs = Array.isArray(payload.dailyWellbeingLogs) ? payload.dailyWellbeingLogs : [];

  return {
    avgSleepHours: averageFirstSeven(sleepLogs, log => {
      const entry = log.duration ?? log.hours;
      return Number(entry);
    }),
    avgStressLevel: averageFirstSeven(dailyWellbeingLogs, log => log.stressLevel),
    avgEnergyLevel: averageFirstSeven(dailyWellbeingLogs, log => log.motivation),
  };
}
