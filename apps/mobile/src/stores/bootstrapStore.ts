import { create } from 'zustand';
import { importBridgeSnapshotIfNeeded, type MigrationImportSummary } from '../services/migrationImportService';
import { hydrateFromMigrationSnapshot, type HydrationResult } from '../services/migrationHydrationService';
import { useMobileNutritionStore } from './nutritionStore';
import { useLocalAiDiagnosticsStore } from './localAiDiagnosticsStore';

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
    set({ status: 'booting', error: null, summary: null, hydrationResult: null });
    try {
      // ── 1. Importar snapshot (valida antes de persistir) ──────────────────
      const summary = await importBridgeSnapshotIfNeeded();
      set({ summary });

      if (summary.validationError) {
        throw new Error(`La migración local no es válida todavía. ${summary.validationError}`);
      }

      // ── 2. Rehidratar dominios si vino de un snapshot ─────────────────────
      let hydrationResult: HydrationResult | null = null;
      if (summary.source === 'snapshot') {
        hydrationResult = await hydrateFromMigrationSnapshot();
        set({ hydrationResult });
        if (hydrationResult.errors.length > 0) {
          throw new Error(hydrationResult.errors.join(' | '));
        }
      }

      // ── 3. Hidratar stores RN ─────────────────────────────────────────────
      await useMobileNutritionStore.getState().hydrateFromStorage();
      await useLocalAiDiagnosticsStore.getState().refreshStatus();

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
