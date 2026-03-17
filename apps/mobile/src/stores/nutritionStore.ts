import { create } from 'zustand';
import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import { analyzeNutritionDraft } from '../services/nutritionAnalyzer';
import { LOCAL_CHILEAN_FOOD_CATALOG } from '@kpkn/shared-domain';
import type { NutritionMealType, SavedNutritionEntry } from '../types/nutrition';
import { loadSavedNutritionLogs, persistNutritionLog, deleteNutritionLog, updateNutritionLogDescription } from '../services/mobilePersistenceService';
import { generateId } from '../utils/generateId';
import { syncNutritionWidgetState } from '../services/widgetSyncService';
import { rescheduleCoreNotificationsFromStorage } from '../services/mobileNotificationService';

type NutritionScreenStatus = 'idle' | 'analyzing' | 'ready' | 'failed';

interface SaveCurrentOptions {
  mealType?: NutritionMealType;
  logDate?: string;
}

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
  replaceAnalysis: (value: LocalAiNutritionAnalysisResult | null, nextDescription?: string) => void;
  saveCurrent: (options?: SaveCurrentOptions) => Promise<void>;
  deleteLog: (id: string) => Promise<void>;
  editDescription: (id: string, newDescription: string) => Promise<void>;
  duplicateLog: (id: string) => Promise<void>;
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
    void syncNutritionWidgetState(savedLogs);
    void rescheduleCoreNotificationsFromStorage();
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
}));
