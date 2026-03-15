import { buildWorkoutOverview, extractCoreReminderSettings } from '@kpkn/shared-domain';
import type { CoreReminderSettings, WorkoutLogSummary, WorkoutOverview } from '@kpkn/shared-types';
import { loadLocalWorkoutLogs, loadPersistedDomainPayload } from './mobilePersistenceService';
import { readStoredSettingsRaw, readStoredWellbeingPayload } from './mobileDomainStateService';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toWorkoutHistoryRecord(entry: WorkoutLogSummary) {
  return {
    id: entry.id,
    date: entry.date,
    programName: entry.programName,
    sessionName: entry.sessionName,
    exerciseCount: entry.exerciseCount,
    completedSetCount: entry.completedSetCount,
    duration: entry.durationMinutes,
  } satisfies Record<string, unknown>;
}

function getHistoryKey(entry: Record<string, unknown>) {
  const date = typeof entry.date === 'string' ? entry.date : 'unknown-date';
  const programName = typeof entry.programName === 'string' ? entry.programName : 'unknown-program';
  const sessionName = typeof entry.sessionName === 'string' ? entry.sessionName : 'unknown-session';
  return `${date}::${programName}::${sessionName}`;
}

function mergeWorkoutPayload(snapshotPayload: unknown, localLogs: WorkoutLogSummary[]) {
  const localHistory = localLogs.map(toWorkoutHistoryRecord);
  if (!isRecord(snapshotPayload)) {
    return { history: localHistory };
  }

  const snapshotHistory = Array.isArray(snapshotPayload.history) ? snapshotPayload.history : [];
  const mergedHistory = new Map<string, Record<string, unknown>>();

  for (const entry of snapshotHistory) {
    if (!isRecord(entry)) continue;
    mergedHistory.set(getHistoryKey(entry), entry);
  }

  for (const entry of localHistory) {
    mergedHistory.set(getHistoryKey(entry), entry);
  }

  return {
    ...snapshotPayload,
    history: Array.from(mergedHistory.values()),
  };
}

function mergeWellbeingPayload(snapshotPayload: unknown) {
  const ownedWellbeing = readStoredWellbeingPayload();
  if (!isRecord(snapshotPayload)) {
    return ownedWellbeing;
  }

  return {
    ...snapshotPayload,
    sleepLogs: ownedWellbeing.sleepLogs,
    waterLogs: ownedWellbeing.waterLogs,
    dailyWellbeingLogs: ownedWellbeing.dailyWellbeingLogs,
    tasks: ownedWellbeing.tasks,
  };
}

export interface WorkoutRuntimeState {
  overview: WorkoutOverview | null;
  reminderSettings: CoreReminderSettings;
  settingsPayload: Record<string, unknown>;
}

export async function loadWorkoutRuntimeState(): Promise<WorkoutRuntimeState> {
  const [programsPayload, workoutPayload, wellbeingPayload, localLogs] = await Promise.all([
    loadPersistedDomainPayload('programs'),
    loadPersistedDomainPayload('workout'),
    loadPersistedDomainPayload('wellbeing'),
    loadLocalWorkoutLogs(),
  ]);

  const settingsPayload = readStoredSettingsRaw();
  const mergedWorkoutPayload = mergeWorkoutPayload(workoutPayload, localLogs);
  const mergedWellbeingPayload = mergeWellbeingPayload(wellbeingPayload);

  return {
    overview: buildWorkoutOverview({
      programsPayload,
      workoutPayload: mergedWorkoutPayload,
      wellbeingPayload: mergedWellbeingPayload,
      settingsPayload,
    }),
    reminderSettings: extractCoreReminderSettings(settingsPayload),
    settingsPayload,
  };
}
