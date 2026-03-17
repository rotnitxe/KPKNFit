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

async function withTimeout<T>(promise: Promise<T>, timeoutMs: number, errorMessage: string): Promise<T> {
  let timeoutId: any;
  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error(`TIMEOUT: ${errorMessage} (${timeoutMs}ms)`));
    }, timeoutMs);
  });

  try {
    const result = await Promise.race([promise, timeoutPromise]);
    return result as T;
  } finally {
    clearTimeout(timeoutId!);
  }
}

const BOOTSTRAP_STEP_TIMEOUT = 6000; // 6 segundos por fase crítica

export const useBootstrapStore = create<BootstrapStore>((set, get) => ({
  status: 'booting',
  summary: null,
  hydrationResult: null,
  error: null,

  bootstrap: async () => {
    console.log('[Bootstrap] Iniciando proceso...');
    set({ status: 'booting', error: null, summary: null, hydrationResult: null });
    try {
      // ── 1. Importar snapshot (con timeout) ───────────────────────────────
      console.log('[Bootstrap] Pasos 1: importBridgeSnapshotIfNeeded...');
      const summary = await withTimeout(
        importBridgeSnapshotIfNeeded(),
        BOOTSTRAP_STEP_TIMEOUT,
        'No se pudo leer el snapshot de migración'
      );
      set({ summary });
      console.log('[Bootstrap] Resumen de importación:', summary.source);

      if (summary.validationError) {
        throw new Error(`La migración local no es válida todavía. ${summary.validationError}`);
      }

      // ── 2. Rehidratar dominios si vino de un snapshot (con timeout) ──────
      let hydrationResult: HydrationResult | null = null;
      if (summary.source === 'snapshot') {
        console.log('[Bootstrap] Paso 2: hydrateFromMigrationSnapshot...');
        hydrationResult = await withTimeout(
          hydrateFromMigrationSnapshot(),
          BOOTSTRAP_STEP_TIMEOUT,
          'La rehidratación de datos tomó demasiado tiempo'
        );
        set({ hydrationResult });
        console.log('[Bootstrap] Hidratación completada.');
      }

      // ── 3. Hidratar stores RN (con timeout batch) ─────────────────────────
      console.log('[Bootstrap] Paso 3: Hidratando stores...');
      const hydrationTasks = [
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
      ];

      const storeHydrationResults = await withTimeout(
        Promise.allSettled(hydrationTasks),
        BOOTSTRAP_STEP_TIMEOUT + 2000, // Margen extra para el batch
        'La sincronización de stores interna falló por tiempo'
      );

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
        console.warn('[Bootstrap] Stores no críticos fallaron o expiraron:', nonCriticalErrors);
      }

      set({ status: 'ready' });
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Error desconocido durante el arranque.';
      console.error('[Bootstrap] Fallo durante el arranque:', message, error);
      
      // Si el error es un TIMEOUT no crítico (ej. Local AI), podríamos intentar seguir
      // Pero por ahora, fallamos para que el usuario pueda reintentar o ver qué pasó.
      // EXCEPCIÓN: Si el error contiene "TIMEOUT" y logramos al menos cargar configuraciones básicas, podríamos forzar 'ready'.
      set({ status: 'failed', error: message });
    }
  },

  retry: async () => {
    if (get().status === 'failed') {
      await get().bootstrap();
    }
  },
}));
