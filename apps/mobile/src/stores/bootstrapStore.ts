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
  retry: () => Promise<void>;
}

export const useBootstrapStore = create<BootstrapStore>((set, get) => ({
  // Instant-start: la app arranca en 'ready' por defecto para mostrar el navegador inmediatamente.
  status: 'ready',
  summary: null,
  hydrationResult: null,
  error: null,

  bootstrap: async () => {
    const currentStatus = get().status;
    console.log('[Bootstrap] Iniciando rehidratación en segundo plano...');
    
    // No bloqueamos: disparamos las tareas y que terminen cuando puedan.
    (async () => {
      try {
        // ── 1. Importar snapshot (segundo plano) ───────────────────────────
        const summary = await importBridgeSnapshotIfNeeded();
        set({ summary });

        if (summary.validationError) {
          console.warn('[Bootstrap] Snapshot de migración no válido:', summary.validationError);
          return;
        }

        // ── 2. Rehidratar dominios (segundo plano) ─────────────────────────
        if (summary.source === 'snapshot') {
          const hydrationResult = await hydrateFromMigrationSnapshot();
          set({ hydrationResult });
          console.log('[Bootstrap] Datos de migración inyectados.');
        }

        // ── 3. Sincronizar stores (segundo plano) ──────────────────────────
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

        // Usamos allSettled para que un fallo en un store no detenga a los demás.
        const results = await Promise.allSettled(hydrationTasks);
        
        const failures = results.filter(r => r.status === 'rejected');
        if (failures.length > 0) {
          console.warn('[Bootstrap] Algunos stores fallaron al hidratarse:', failures);
        }

        console.log('[Bootstrap] Proceso de fondo completado.');
      } catch (err) {
        console.error('[Bootstrap] Fallo en proceso secundario:', err);
      }
    })();
  },

  retry: async () => {
    await get().bootstrap();
  },
}));
