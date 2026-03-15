import { create } from 'zustand';
import type { CoreReminderSettings, WorkoutLogSummary, WorkoutOverview } from '@kpkn/shared-types';
import {
  cancelRestTimerNotification,
  rescheduleCoreNotificationsFromState,
  scheduleRestTimerNotification,
} from '../services/mobileNotificationService';
import { persistLocalWorkoutLog } from '../services/mobilePersistenceService';
import { loadWorkoutRuntimeState } from '../services/workoutStateService';
import { syncWorkoutWidgetState } from '../services/widgetSyncService';
import { useMobileNutritionStore } from './nutritionStore';
import { generateId } from '../utils/generateId';

type WorkoutScreenStatus = 'idle' | 'empty' | 'ready' | 'failed';

type WorkoutLoggingState = 'idle' | 'saving';

interface WorkoutStoreState {
  status: WorkoutScreenStatus;
  overview: WorkoutOverview | null;
  reminderSettings: CoreReminderSettings | null;
  hasHydrated: boolean;
  errorMessage: string | null;
  notice: string | null;
  loggingState: WorkoutLoggingState;
  hydrateFromMigration: () => Promise<void>;
  refreshInfrastructure: () => Promise<void>;
  logTodaySession: () => Promise<void>;
  startRestTimer: (seconds: number) => Promise<void>;
  cancelRestTimer: () => Promise<void>;
  clearNotice: () => void;
}

async function syncWorkoutInfra(overview: WorkoutOverview | null, reminderSettings: CoreReminderSettings) {
  await syncWorkoutWidgetState(overview);
  await rescheduleCoreNotificationsFromState({
    settings: reminderSettings,
    nutritionLogs: useMobileNutritionStore.getState().savedLogs,
    workoutOverview: overview,
  });
}

function buildQuickWorkoutLog(overview: WorkoutOverview): WorkoutLogSummary | null {
  if (!overview.todaySession) return null;
  const today = new Date().toISOString().slice(0, 10);
  return {
    id: generateId(),
    date: today,
    programName: overview.activeProgramName ?? 'KPKN',
    sessionName: overview.todaySession.name,
    exerciseCount: overview.todaySession.exerciseCount,
    completedSetCount: overview.todaySession.setCount,
    durationMinutes: null,
  };
}

export const useWorkoutStore = create<WorkoutStoreState>((set, get) => ({
  status: 'idle',
  overview: null,
  reminderSettings: null,
  hasHydrated: false,
  errorMessage: null,
  notice: null,
  loggingState: 'idle',

  hydrateFromMigration: async () => {
    set({ errorMessage: null, notice: null });
    try {
      const { overview, reminderSettings } = await loadWorkoutRuntimeState();
      set({
        status: overview ? 'ready' : 'empty',
        overview,
        reminderSettings,
        hasHydrated: true,
        errorMessage: null,
      });
      await syncWorkoutInfra(overview, reminderSettings);
    } catch (error) {
      set({
        status: 'failed',
        hasHydrated: true,
        errorMessage: error instanceof Error ? error.message : 'No pudimos abrir el módulo de entrenamiento.',
      });
    }
  },

  refreshInfrastructure: async () => {
    try {
      const { overview, reminderSettings } = await loadWorkoutRuntimeState();
      set({
        status: overview ? 'ready' : 'empty',
        overview,
        reminderSettings,
        errorMessage: null,
      });
      await syncWorkoutInfra(overview, reminderSettings);
      set({ notice: 'Widgets y recordatorios actualizados.' });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos refrescar entrenamiento.',
      });
    }
  },

  logTodaySession: async () => {
    const overview = get().overview;
    if (!overview?.todaySession) {
      set({ notice: 'Hoy no hay una sesión programada para registrar desde RN.' });
      return;
    }
    if (overview.hasWorkoutLoggedToday) {
      set({ notice: 'La sesión de hoy ya está registrada.' });
      return;
    }

    const quickLog = buildQuickWorkoutLog(overview);
    if (!quickLog) {
      set({ notice: 'No pudimos construir el registro rápido de la sesión.' });
      return;
    }

    set({ loggingState: 'saving', notice: null });
    try {
      await persistLocalWorkoutLog(quickLog);
      const { overview: nextOverview, reminderSettings } = await loadWorkoutRuntimeState();
      set({
        status: nextOverview ? 'ready' : 'empty',
        overview: nextOverview,
        reminderSettings,
        loggingState: 'idle',
        errorMessage: null,
      });
      await syncWorkoutInfra(nextOverview, reminderSettings);
      set({ notice: 'Sesión de hoy registrada en esta app RN.' });
    } catch (error) {
      set({
        loggingState: 'idle',
        errorMessage: error instanceof Error ? error.message : 'No pudimos registrar la sesión de hoy.',
      });
    }
  },

  startRestTimer: async seconds => {
    const label = get().overview?.todaySession?.name ?? 'Descanso';
    await scheduleRestTimerNotification(seconds, label);
    set({
      notice: `Temporizador listo para ${seconds}s.`,
    });
  },

  cancelRestTimer: async () => {
    await cancelRestTimerNotification();
    set({
      notice: 'Temporizador cancelado.',
    });
  },

  clearNotice: () => set({ notice: null }),
}));
