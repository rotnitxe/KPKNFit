import { create } from 'zustand';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { analyzeNutritionDraft } from '../services/nutritionAnalyzer';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '@kpkn/shared-domain';
import { widgetModule } from '../modules/widgets';
import type { SavedNutritionEntry } from '../types/nutrition';
import { loadSavedNutritionLogs, persistNutritionLog } from '../services/mobilePersistenceService';
import { generateId } from '../utils/generateId';

type NutritionScreenStatus = 'idle' | 'analyzing' | 'ready' | 'failed';

interface MobileNutritionStoreState {
  description: string;
  status: NutritionScreenStatus;
  lastAnalysis: LocalAiNutritionAnalysisResult | null;
  savedLogs: SavedNutritionEntry[];
  hasHydrated: boolean;
  isDetailVisible: boolean;
  saveNotice: string | null;
  errorMessage: string | null;
  hydrateFromStorage: () => Promise<void>;
  setDescription: (value: string) => void;
  analyze: () => Promise<void>;
  saveCurrent: () => Promise<void>;
  toggleDetail: () => void;
  clearNotice: () => void;
}

function computeTotals(result: LocalAiNutritionAnalysisResult) {
  return result.items.reduce(
    (totals, item) => ({
      calories: totals.calories + item.calories,
      protein: totals.protein + item.protein,
      carbs: totals.carbs + item.carbs,
      fats: totals.fats + item.fats,
    }),
    { calories: 0, protein: 0, carbs: 0, fats: 0 },
  );
}

export const useMobileNutritionStore = create<MobileNutritionStoreState>((set, get) => ({
  description: '',
  status: 'idle',
  lastAnalysis: null,
  savedLogs: [],
  hasHydrated: false,
  isDetailVisible: false,
  saveNotice: null,
  errorMessage: null,
  hydrateFromStorage: async () => {
    const savedLogs = await loadSavedNutritionLogs();
    set({
      savedLogs,
      hasHydrated: true,
    });
  },
  setDescription: value => {
    set({
      description: value,
      status: 'idle',
      lastAnalysis: null,
      saveNotice: null,
      errorMessage: null,
    });
  },
  analyze: async () => {
    const description = get().description.trim();
    if (!description) {
      set({
        status: 'failed',
        errorMessage: 'Escribe algo antes de analizar.',
      });
      return;
    }

    set({
      status: 'analyzing',
      errorMessage: null,
      saveNotice: null,
    });

    try {
      const result = await analyzeNutritionDraft({
        description,
        locale: 'es-CL',
        schemaVersion: 'rn-v1',
        knownFoods: LOCAL_CHILEAN_FOOD_CATALOG,
        userMemory: get()
          .savedLogs
          .slice(0, 20)
          .map(log => log.description)
          .filter(Boolean),
      });

      set({
        status: result.items.length > 0 ? 'ready' : 'failed',
        lastAnalysis: result,
        isDetailVisible: false,
        errorMessage: result.items.length > 0 ? null : 'No pudimos estimar esa comida todavía.',
      });
    } catch (error) {
      set({
        status: 'failed',
        errorMessage: error instanceof Error ? error.message : 'No pudimos analizar esta comida.',
      });
    }
  },
  saveCurrent: async () => {
    const { description, lastAnalysis, savedLogs } = get();
    if (!description.trim() || !lastAnalysis) return;

    // generateId() produces UUID v4 format using Math.random() — Hermes-safe, no DOM lib needed.
    // Using it instead of Date.now() prevents ID collisions from rapid successive saves.
    const entry: SavedNutritionEntry = {
      id: generateId(),
      description,
      createdAt: new Date().toISOString(),
      totals: computeTotals(lastAnalysis),
      analysis: lastAnalysis,
    };

    const nextLogs = [entry, ...savedLogs].slice(0, 100);
    await persistNutritionLog(entry);

    // Single pass to compute today's totals instead of 4× filter/reduce
    const todayDate = entry.createdAt.slice(0, 10);
    const { caloriesToday, proteinToday, carbsToday, fatsToday } = nextLogs
      .filter(log => log.createdAt.slice(0, 10) === todayDate)
      .reduce(
        (acc, log) => ({
          caloriesToday: acc.caloriesToday + log.totals.calories,
          proteinToday: acc.proteinToday + log.totals.protein,
          carbsToday: acc.carbsToday + log.totals.carbs,
          fatsToday: acc.fatsToday + log.totals.fats,
        }),
        { caloriesToday: 0, proteinToday: 0, carbsToday: 0, fatsToday: 0 },
      );
    void widgetModule.syncDashboardState({
      nutritionCaloriesToday: caloriesToday,
      nutritionProteinToday: proteinToday,
      nutritionCarbsToday: carbsToday,
      nutritionFatsToday: fatsToday,
    });
    set({
      savedLogs: nextLogs,
      saveNotice: 'Comida guardada.',
    });
  },
  toggleDetail: () => set(state => ({ isDetailVisible: !state.isDetailVisible })),
  clearNotice: () => set({ saveNotice: null }),
}));
