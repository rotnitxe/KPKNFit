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

interface BootstrapStore {
  status: BootstrapStatus;
  summary: MigrationImportSummary | null;
  hydrationResult: HydrationResult | null;
  error: string | null;
  bootstrap: () => Promise<void>;
  /**
   * Reinicia el estado de bootstrap y vuelve a ejecutar el flujo completo.
   * Disponible cuando status === 'failed'.
   */
  retry: () => Promise<void>;
}

export const useBootstrapStore = create<BootstrapStore>((set, get) => ({
  status: 'booting',
  summary: null,
  hydrationResult: null,
  error: null,

  bootstrap: async () => {
    console.log('[Bootstrap] Inciando proceso...');
    set({ status: 'booting', error: null, summary: null, hydrationResult: null });
    try {
      // ── 1. Importar snapshot (valida antes de persistir) ──────────────────
      console.log('[Bootstrap] Pasos 1: importBridgeSnapshotIfNeeded...');
      const summary = await importBridgeSnapshotIfNeeded();
      set({ summary });
      console.log('[Bootstrap] Resumen de importación:', summary.source);

      if (summary.validationError) {
        throw new Error(`La migración local no es válida todavía. ${summary.validationError}`);
      }

      // ── 2. Rehidratar dominios si vino de un snapshot ─────────────────────
      let hydrationResult: HydrationResult | null = null;
      if (summary.source === 'snapshot') {
        console.log('[Bootstrap] Paso 2: hydrateFromMigrationSnapshot...');
        hydrationResult = await hydrateFromMigrationSnapshot();
        set({ hydrationResult });
        console.log('[Bootstrap] Hidratación completada.');
      }

      // ── 3. Hidratar stores RN ─────────────────────────────────────────────
      console.log('[Bootstrap] Paso 3: Hidratando stores...');
      const storeHydrationResults = await Promise.allSettled([
        useMobileNutritionStore.getState().hydrateFromStorage(),
        useWorkoutStore.getState().hydrateFromMigration(),
        useSettingsStore.getState().hydrateFromMigration(),
        useWellbeingStore.getState().hydrateFromMigration(),
        useMealTemplateStore.getState().hydrateFromMigration(),
        useProgramStore.getState().hydrateFromMigration(),
        useBodyStore.getState().hydrateFromMigration(),
        useExerciseStore.getState().hydrateFromMigration(),
        useMealPlannerStore.getState().hydrateFromStorage(),
        useCoachStore.getState().hydrateFromStorage(),
        useAugeRuntimeStore.getState().hydrateFromStorage(),
        useLocalAiDiagnosticsStore.getState().refreshStatus(),
      ]);

      const criticalErrors = storeHydrationResults
        .slice(0, 3)
        .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
        .map(result => (result.reason instanceof Error ? result.reason.message : 'Error crítico de hidratación.'));

      const nonCriticalErrors = storeHydrationResults
        .slice(3)
        .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
        .map(result => (result.reason instanceof Error ? result.reason.message : 'Error no crítico de hidratación.'));

      if (criticalErrors.length > 0) {
        throw new Error(criticalErrors.join(' | '));
      }

      if (nonCriticalErrors.length > 0) {
        console.warn('[Bootstrap] Stores no críticos fallaron, seguimos en modo degradado:', nonCriticalErrors);
      }

      set({ status: 'ready' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Error desconocido durante el arranque.';
      console.error('[Bootstrap] Fallo durante el arranque:', message, error);
      set({ status: 'failed', error: message });
    }
  },

  retry: async () => {
    // Solo permitimos retry si el estado actual es 'failed'.
    if (get().status === 'failed') {
      await get().bootstrap();
    }
  },
}));
