import { create } from 'zustand';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { analyzeNutritionDraft } from '../services/aiService';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '@kpkn/shared-domain';
import type { NutritionMealType, SavedNutritionEntry } from '../types/nutrition';
import type { Settings } from '../types/settings';
import { loadSavedNutritionLogs, persistNutritionLog, deleteNutritionLog, updateNutritionLogDescription } from '../services/mobilePersistenceService';
import { generateId } from '../utils/generateId';
import { syncNutritionWidgetState } from '../services/widgetSyncService';
import { rescheduleCoreNotificationsFromStorage } from '../services/mobileNotificationService';
import { useSettingsStore } from './settingsStore';

type NutritionScreenStatus = 'idle' | 'analyzing' | 'ready' | 'failed';

interface SaveCurrentOptions {
  mealType?: NutritionMealType;
  logDate?: string;
}

interface NutritionPlan {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    periodization?: {
        trainingDayCalories: number;
        restDayCalories: number;
    };
}

interface MobileNutritionStoreState {
  description: string;
  status: NutritionScreenStatus;
  lastAnalysis: LocalAiNutritionAnalysisResult | null;
  savedLogs: SavedNutritionEntry[];
  nutritionPlan: NutritionPlan;
  favorites: string[]; // food IDs
  hasHydrated: boolean;
  isDetailVisible: boolean;
  saveNotice: string | null;
  errorMessage: string | null;
  hydrateFromStorage: () => Promise<void>;
  setDescription: (value: string) => void;
  analyze: () => Promise<void>;
  replaceAnalysis: (value: LocalAiNutritionAnalysisResult | null, nextDescription?: string) => void;
  saveCurrent: (options?: SaveCurrentOptions) => Promise<void>;
  deleteLog: (id: string) => Promise<void>;
  editDescription: (id: string, newDescription: string) => Promise<void>;
  duplicateLog: (id: string) => Promise<void>;
  toggleDetail: () => void;
  clearNotice: () => void;
  updateNutritionPlan: (plan: NutritionPlan) => Promise<void>;
  toggleFavorite: (foodId: string) => void;
  getLogsForDate: (date: string) => SavedNutritionEntry[];
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

function buildLogTimestamp(logDate?: string) {
  if (!logDate) {
    return new Date().toISOString();
  }

  const now = new Date();
  const timePart = [
    now.getHours().toString().padStart(2, '0'),
    now.getMinutes().toString().padStart(2, '0'),
    now.getSeconds().toString().padStart(2, '0'),
  ].join(':');
  const candidate = new Date(`${logDate}T${timePart}`);
  return Number.isNaN(candidate.getTime()) ? now.toISOString() : candidate.toISOString();
}

function getNutritionPlanFromSettings(settings: Settings) {
  return {
    calories: settings.dailyCalorieGoal ?? 2000,
    protein: settings.dailyProteinGoal ?? 150,
    carbs: settings.dailyCarbGoal ?? 200,
    fats: settings.dailyFatGoal ?? 60,
    periodization: undefined,
  };
}

export const useMobileNutritionStore = create<MobileNutritionStoreState>((set, get) => ({
  description: '',
  status: 'idle',
  lastAnalysis: null,
  savedLogs: [],
  nutritionPlan: {
    calories: 2000,
    protein: 150,
    carbs: 200,
    fats: 60,
  },
  favorites: [],
  hasHydrated: false,
  isDetailVisible: false,
  saveNotice: null,
  errorMessage: null,
  hydrateFromStorage: async () => {
    const savedLogs = await loadSavedNutritionLogs();
    const settings = useSettingsStore.getState().getSettings();
    void syncNutritionWidgetState(savedLogs);
    void rescheduleCoreNotificationsFromStorage();
    set({
      savedLogs,
      nutritionPlan: settings ? getNutritionPlanFromSettings(settings) : get().nutritionPlan,
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
  replaceAnalysis: (value, nextDescription) => {
    set({
      description: nextDescription ?? get().description,
      status: value && value.items.length > 0 ? 'ready' : 'idle',
      lastAnalysis: value,
      isDetailVisible: false,
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
  saveCurrent: async options => {
    const { description, lastAnalysis, savedLogs } = get();
    if (!description.trim() || !lastAnalysis) return;

    // generateId() produces UUID v4 format using Math.random() — Hermes-safe, no DOM lib needed.
    // Using it instead of Date.now() prevents ID collisions from rapid successive saves.
    const entry: SavedNutritionEntry = {
      id: generateId(),
      description,
      createdAt: buildLogTimestamp(options?.logDate),
      loggedDate: options?.logDate,
      mealType: options?.mealType ?? 'lunch',
      totals: computeTotals(lastAnalysis),
      analysis: lastAnalysis,
    };

    const nextLogs = [entry, ...savedLogs].slice(0, 100);
    await persistNutritionLog(entry);
    void syncNutritionWidgetState(nextLogs);
    void rescheduleCoreNotificationsFromStorage();
    set({
      savedLogs: nextLogs,
      saveNotice: `${entry.mealType === 'breakfast'
        ? 'Desayuno'
        : entry.mealType === 'dinner'
          ? 'Cena'
          : entry.mealType === 'snack'
            ? 'Snack'
            : 'Almuerzo'} guardado.`,
    });
  },
  deleteLog: async id => {
    const { savedLogs } = get();
    const nextLogs = savedLogs.filter(log => log.id !== id);
    await deleteNutritionLog(id);
    void syncNutritionWidgetState(nextLogs);
    void rescheduleCoreNotificationsFromStorage();
    set({ savedLogs: nextLogs });
  },
  editDescription: async (id, newDescription) => {
    const { savedLogs } = get();
    const nextLogs = savedLogs.map(log =>
      log.id === id ? { ...log, description: newDescription } : log,
    );
    await updateNutritionLogDescription(id, newDescription);
    void syncNutritionWidgetState(nextLogs);
    void rescheduleCoreNotificationsFromStorage();
    set({ savedLogs: nextLogs });
  },
  duplicateLog: async id => {
    const { savedLogs } = get();
    const original = savedLogs.find(log => log.id === id);
    if (!original) return;

    const entry: SavedNutritionEntry = {
      ...original,
      id: generateId(),
      description: `${original.description} (copia)`,
      createdAt: new Date().toISOString(),
    };

    const nextLogs = [entry, ...savedLogs].slice(0, 100);
    await persistNutritionLog(entry);
    void syncNutritionWidgetState(nextLogs);
    void rescheduleCoreNotificationsFromStorage();
    set({
      savedLogs: nextLogs,
      saveNotice: 'Comida duplicada.',
    });
  },
  toggleDetail: () => set(state => ({ isDetailVisible: !state.isDetailVisible })),
  clearNotice: () => set({ saveNotice: null }),
  updateNutritionPlan: async (plan: NutritionPlan) => {
    set({ nutritionPlan: plan });
    const currentSettings = useSettingsStore.getState().getSettings();
    if (currentSettings) {
      await useSettingsStore.getState().updateSettings({
        dailyCalorieGoal: plan.calories,
        dailyProteinGoal: plan.protein,
        dailyCarbGoal: plan.carbs,
        dailyFatGoal: plan.fats,
        calorieGoalConfig: currentSettings.calorieGoalConfig,
      });
    }
  },
  toggleFavorite: (foodId: string) => {
    set(state => {
      const favorites = state.favorites.includes(foodId)
        ? state.favorites.filter(id => id !== foodId)
        : [...state.favorites, foodId];
      return { favorites };
    });
  },
  getLogsForDate: (date: string) => {
    const { savedLogs } = get();
    return savedLogs.filter(log => (log.loggedDate || log.createdAt.split('T')[0]) === date);
  },
}));
