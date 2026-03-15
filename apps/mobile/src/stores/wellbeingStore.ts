import { create } from 'zustand';
import { buildWellbeingOverview } from '@kpkn/shared-domain';
import type { WellbeingOverview } from '@kpkn/shared-types';
import {
  getStoredWellbeingSource,
  patchStoredWellbeingPayload,
  readStoredWellbeingPayload,
  type StoredWellbeingPayload,
  type StoredWellbeingSource,
} from '../services/mobileDomainStateService';
import { generateId } from '../utils/generateId';

type WellbeingStatus = 'idle' | 'ready' | 'empty';

interface WellbeingTaskSummary {
  id: string;
  title: string;
  completed: boolean;
}

interface WellbeingStoreState {
  status: WellbeingStatus;
  source: StoredWellbeingSource;
  overview: WellbeingOverview | null;
  tasks: WellbeingTaskSummary[];
  notice: string | null;
  droppedDailyLogs: number;
  hydrateFromMigration: () => Promise<void>;
  logWater: (amountMl: number) => Promise<void>;
  toggleTask: (taskId: string) => Promise<void>;
  clearNotice: () => void;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function normalizeDailyLog(raw: unknown): Record<string, unknown> | null {
  if (!isRecord(raw)) return null;
  const date = typeof raw.date === 'string' && raw.date.trim() !== '' ? raw.date : null;
  if (!date) return null;

  const normalized: Record<string, unknown> = { date };
  const numericKeys = ['readiness', 'sleepQuality', 'stressLevel', 'doms', 'motivation'] as const;
  for (const key of numericKeys) {
    if (isFiniteNumber(raw[key])) normalized[key] = raw[key];
  }
  if (typeof raw.moodState === 'string' && raw.moodState.trim() !== '') {
    normalized.moodState = raw.moodState.trim();
  }
  return normalized;
}

function adaptTasks(rawTasks: unknown[]) {
  return rawTasks
    .filter(isRecord)
    .map(task => ({
      id: typeof task.id === 'string' ? task.id : generateId(),
      title: typeof task.title === 'string' && task.title.trim() !== '' ? task.title.trim() : 'Tarea',
      completed: task.completed === true,
      completedDate:
        typeof task.completedDate === 'string' && task.completedDate.trim() !== ''
          ? task.completedDate.trim()
          : null,
    }))
    .slice(0, 12);
}

function buildState() {
  const raw = readStoredWellbeingPayload();
  const sanitizedDailyLogs = raw.dailyWellbeingLogs
    .map(normalizeDailyLog)
    .filter((value): value is Record<string, unknown> => value !== null);
  const droppedDailyLogs = raw.dailyWellbeingLogs.length - sanitizedDailyLogs.length;
  const tasks = adaptTasks(raw.tasks);
  const hasAnyData =
    raw.sleepLogs.length > 0 ||
    raw.waterLogs.length > 0 ||
    sanitizedDailyLogs.length > 0 ||
    tasks.length > 0;

  const payload: StoredWellbeingPayload = {
    sleepLogs: raw.sleepLogs,
    waterLogs: raw.waterLogs,
    dailyWellbeingLogs: sanitizedDailyLogs,
    tasks,
  };

  return {
    source: getStoredWellbeingSource(),
    status: hasAnyData ? ('ready' as const) : ('empty' as const),
    overview: hasAnyData ? buildWellbeingOverview(payload) : null,
    tasks: tasks.map(task => ({ id: task.id, title: task.title, completed: task.completed })),
    payload,
    droppedDailyLogs,
  };
}

export const useWellbeingStore = create<WellbeingStoreState>(set => ({
  status: 'idle',
  source: 'empty',
  overview: null,
  tasks: [],
  notice: null,
  droppedDailyLogs: 0,

  hydrateFromMigration: async () => {
    const next = buildState();
    set({
      status: next.status,
      source: next.source,
      overview: next.overview,
      tasks: next.tasks,
      droppedDailyLogs: next.droppedDailyLogs,
      notice:
        next.droppedDailyLogs > 0
          ? `Ignoramos ${next.droppedDailyLogs} registro${next.droppedDailyLogs === 1 ? '' : 's'} diarios mal formados.`
          : null,
    });
  },

  logWater: async amountMl => {
    const current = readStoredWellbeingPayload();
    const today = new Date().toISOString().slice(0, 10);
    const nextWaterLogs = [
      {
        id: generateId(),
        date: today,
        amountMl,
      },
      ...current.waterLogs,
    ];

    patchStoredWellbeingPayload({ waterLogs: nextWaterLogs });
    const next = buildState();
    set({
      status: next.status,
      source: 'rn-owned',
      overview: next.overview,
      tasks: next.tasks,
      droppedDailyLogs: next.droppedDailyLogs,
      notice: `${amountMl} ml agregados.`,
    });
  },

  toggleTask: async taskId => {
    const current = readStoredWellbeingPayload();
    const now = new Date().toISOString();
    const nextTasks = current.tasks.map(task => {
      if (!isRecord(task) || task.id !== taskId) return task;
      const completed = task.completed === true;
      return {
        ...task,
        completed: !completed,
        completedDate: completed ? undefined : now,
      };
    });

    patchStoredWellbeingPayload({ tasks: nextTasks });
    const next = buildState();
    const toggledTask = next.tasks.find(task => task.id === taskId);
    set({
      status: next.status,
      source: 'rn-owned',
      overview: next.overview,
      tasks: next.tasks,
      droppedDailyLogs: next.droppedDailyLogs,
      notice: toggledTask?.completed ? 'Tarea marcada como lista.' : 'Tarea marcada como pendiente.',
    });
  },

  clearNotice: () => set({ notice: null }),
}));
