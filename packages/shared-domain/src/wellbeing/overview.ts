import type { WellbeingOverview, WellbeingSnapshot } from '@kpkn/shared-types';
import { getLocalDateKey, normalizeDateKey } from '../utils/dateUtils';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null;
}

function numberOrNull(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function round(value: number) {
  return Math.round(value * 10) / 10;
}

function getLast7DateKeys() {
  return new Set(
    Array.from({ length: 7 }).map((_, index) => {
      const date = new Date();
      date.setDate(date.getDate() - index);
      return getLocalDateKey(date);
    }),
  );
}

function buildLatestSnapshot(dailyLogs: unknown[]): WellbeingSnapshot | null {
  const latest = dailyLogs
    .filter(isRecord)
    .map(log => ({
      log,
      date: normalizeDateKey(log.date),
    }))
    .filter(entry => entry.date !== null)
    .sort((left, right) => String(right.date).localeCompare(String(left.date)))[0];

  if (!latest) return null;

  return {
    date: latest.date ?? getLocalDateKey(),
    readiness: numberOrNull(latest.log.readiness),
    sleepQuality: numberOrNull(latest.log.sleepQuality),
    stressLevel: numberOrNull(latest.log.stressLevel),
    doms: numberOrNull(latest.log.doms),
    motivation: numberOrNull(latest.log.motivation),
    moodState: stringOrNull(latest.log.moodState),
  };
}

export function buildWellbeingOverview(input: {
  sleepLogs: unknown;
  waterLogs: unknown;
  dailyWellbeingLogs: unknown;
  tasks: unknown;
}): WellbeingOverview {
  const sleepLogs = asArray(input.sleepLogs);
  const waterLogs = asArray(input.waterLogs);
  const dailyWellbeingLogs = asArray(input.dailyWellbeingLogs);
  const tasks = asArray(input.tasks);
  const today = getLocalDateKey();
  const last7Days = getLast7DateKeys();

  const recentSleepDurations = sleepLogs
    .filter(isRecord)
    .map(log => ({
      date: normalizeDateKey(log.date),
      duration: numberOrNull(log.duration),
    }))
    .filter(entry => entry.date !== null && entry.duration !== null && last7Days.has(entry.date))
    .map(entry => entry.duration as number);

  const latestSleep = sleepLogs
    .filter(isRecord)
    .map(log => ({
      date: normalizeDateKey(log.date),
      duration: numberOrNull(log.duration),
    }))
    .filter(entry => entry.date !== null && entry.duration !== null)
    .sort((left, right) => String(right.date).localeCompare(String(left.date)))[0];

  const waterTodayMl = waterLogs
    .filter(isRecord)
    .filter(log => normalizeDateKey(log.date) === today)
    .reduce((sum, log) => sum + (numberOrNull(log.amountMl) ?? 0), 0);

  const totalTaskCount = tasks.filter(isRecord).length;
  const completedTaskCount = tasks
    .filter(isRecord)
    .filter(task => task.completed === true).length;

  return {
    latestSleepHours: latestSleep?.duration ? round(latestSleep.duration / 60) : null,
    averageSleepHoursLast7Days: recentSleepDurations.length > 0
      ? round(recentSleepDurations.reduce((sum, duration) => sum + duration, 0) / recentSleepDurations.length / 60)
      : null,
    sleepEntriesLast7Days: recentSleepDurations.length,
    waterTodayMl,
    latestSnapshot: buildLatestSnapshot(dailyWellbeingLogs),
    pendingTaskCount: Math.max(totalTaskCount - completedTaskCount, 0),
    completedTaskCount,
    totalTaskCount,
  };
}
