import { create } from 'zustand';
import type { NutritionFlowSmokeResult } from '../services/nutritionFlowSmokeTestService';
import { runNutritionFlowSmokeTest } from '../services/nutritionFlowSmokeTestService';

type NutritionFlowSmokeState = 'idle' | 'running';

interface NutritionFlowDiagnosticsStore {
  smokeState: NutritionFlowSmokeState;
  smokeError: string | null;
  lastResult: NutritionFlowSmokeResult | null;
  runSmokeFlow: (description?: string) => Promise<void>;
}

export const useNutritionFlowDiagnosticsStore = create<NutritionFlowDiagnosticsStore>((set, get) => ({
  smokeState: 'idle',
  smokeError: null,
  lastResult: null,
  runSmokeFlow: async (description = '2 completos italianos') => {
    if (get().smokeState === 'running') return;

    set({
      smokeState: 'running',
      smokeError: null,
    });

    try {
      const lastResult = await runNutritionFlowSmokeTest(description);
      set({
        smokeState: 'idle',
        smokeError: null,
        lastResult,
      });
    } catch (error) {
      set({
        smokeState: 'idle',
        smokeError: error instanceof Error ? error.message : 'No pudimos completar el flujo interno de nutrición.',
      });
    }
  },
}));
