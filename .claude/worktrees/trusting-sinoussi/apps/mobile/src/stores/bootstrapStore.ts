import { create } from 'zustand';
import { importBridgeSnapshotIfNeeded, type MigrationImportSummary } from '../services/migrationImportService';
import { hydrateFromMigrationSnapshot, type HydrationResult } from '../services/migrationHydrationService';
import { useMobileNutritionStore } from './nutritionStore';
import { useLocalAiDiagnosticsStore } from './localAiDiagnosticsStore';
import { useWorkoutStore } from './workoutStore';
import { useSettingsStore } from './settingsStore';
import { useWellbeingStore } from './wellbeingStore';
import { useMealTemplateStore } from './mealTemplateStore';
import { useProgramStore } from './programStore';
import { useBodyStore } from './bodyStore';
import { useExerciseStore } from './exerciseStore';
import { useMealPlannerStore } from './mealPlannerStore';
import { useCoachStore } from './coachStore';
import { useAugeRuntimeStore } from './augeRuntimeStore';

export type BootstrapStatus = 'booting' | 'ready' | 'failed';

interface BootTask {
  name: string;
  critical: boolean;
  run: () => Promise<unknown>;
}

interface BootstrapStore {
  status: BootstrapStatus;
  summary: MigrationImportSummary | null;
  hydrationResult: HydrationResult | null;
  error: string | null;
  bootstrap: () => Promise<void>;
  retry: () => Promise<void>;
}

export const useBootstrapStore = create<BootstrapStore>((set, get) => ({
  status: 'booting',
  summary: null,
  hydrationResult: null,
  error: null,

  bootstrap: async () => {
    if (get().status === 'booting' && (get().summary !== null || get().hydrationResult !== null)) {
      return;
    }

    set({
      status: 'booting',
      error: null,
      hydrationResult: null,
    });

    try {
      const summary = await importBridgeSnapshotIfNeeded();
      set({ summary });

      if (summary.validationError) {
        set({
          status: 'failed',
          hydrationResult: null,
          error: summary.validationError,
        });
        return;
      }

      let hydrationResult: HydrationResult | null = null;
      if (summary.source === 'snapshot') {
        hydrationResult = await hydrateFromMigrationSnapshot();
        set({ hydrationResult });
        if (hydrationResult.errors.length > 0) {
          set({
            status: 'failed',
            error: hydrationResult.errors.join(' | '),
          });
          return;
        }
      }

      const tasks: BootTask[] = [
        { name: 'nutrition', critical: true, run: () => useMobileNutritionStore.getState().hydrateFromStorage() },
        { name: 'workout', critical: true, run: () => useWorkoutStore.getState().hydrateFromMigration() },
        { name: 'settings', critical: true, run: () => useSettingsStore.getState().hydrateFromMigration() },
        { name: 'wellbeing', critical: false, run: () => useWellbeingStore.getState().hydrateFromMigration() },
        { name: 'mealTemplates', critical: false, run: () => useMealTemplateStore.getState().hydrateFromMigration() },
        { name: 'program', critical: false, run: () => useProgramStore.getState().hydrateFromMigration() },
        { name: 'body', critical: false, run: () => useBodyStore.getState().hydrateFromMigration() },
        { name: 'exercise', critical: false, run: () => useExerciseStore.getState().hydrateFromMigration() },
        { name: 'mealPlanner', critical: false, run: () => useMealPlannerStore.getState().hydrateFromStorage() },
        { name: 'coach', critical: false, run: () => useCoachStore.getState().hydrateFromStorage() },
        { name: 'augeRuntime', critical: false, run: () => useAugeRuntimeStore.getState().hydrateFromStorage() },
        { name: 'localAiDiagnostics', critical: false, run: () => useLocalAiDiagnosticsStore.getState().refreshStatus() },
      ];

      const results = await Promise.allSettled(tasks.map(task => task.run()));
      const criticalFailures: string[] = [];
      const nonCriticalFailures: string[] = [];

      results.forEach((result, index) => {
        if (result.status !== 'rejected') return;
        const task = tasks[index];
        const message = result.reason instanceof Error ? result.reason.message : String(result.reason);
        const failure = `${task.name}: ${message}`;
        if (task.critical) {
          criticalFailures.push(failure);
        } else {
          nonCriticalFailures.push(failure);
        }
      });

      if (nonCriticalFailures.length > 0) {
        console.warn('[Bootstrap] Algunos stores fallaron al hidratarse:', nonCriticalFailures);
      }

      if (criticalFailures.length > 0) {
        set({
          status: 'failed',
          error: criticalFailures.join(' | '),
        });
        return;
      }

      set({
        status: 'ready',
        error: null,
        hydrationResult,
      });
    } catch (err) {
      set({
        status: 'failed',
        error: err instanceof Error ? err.message : 'Error desconocido durante bootstrap.',
      });
    }
  },

  retry: async () => {
    if (get().status !== 'failed') {
      return;
    }
    await get().bootstrap();
  },
}));
