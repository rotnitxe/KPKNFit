import { create } from 'zustand';
import { 
  DailyMealPlan, 
  MealPlannerSuggestion, 
  MealPlannerSummary, 
  MealSlot, 
  WeeklyMealPlan 
} from '../types/mealPlanner';
import { useMealTemplateStore } from './mealTemplateStore';
import { 
  persistStoredMealPlannerPayload, 
  readStoredMealPlannerPayload 
} from '../services/mobileDomainStateService';

interface MealPlannerStoreState {
  status: 'idle' | 'ready' | 'empty' | 'failed';
  activeWeekPlan: WeeklyMealPlan | null;
  suggestions: MealPlannerSuggestion[];
  summary: MealPlannerSummary | null;
  notice: string | null;
  errorMessage: string | null;
  
  hydrateFromStorage: () => Promise<void>;
  generateSuggestionsForDay: (dateKey: string, targetCalories: number) => void;
  setTemplateForSlot: (dateKey: string, slot: MealSlot, templateId: string | null) => Promise<void>;
  clearDayPlan: (dateKey: string) => Promise<void>;
  clearNotice: () => void;
}

// Helpers
const getLocalDateKey = (date = new Date()) => date.toISOString().slice(0, 10);

const getWeekStartKeyFromDate = (date: Date) => {
  const d = new Date(date);
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1); // Adjust for Monday start
  return new Date(d.setDate(diff)).toISOString().slice(0, 10);
};

const buildEmptyWeekPlan = (weekStartKey: string, defaultTargetCalories = 2200): WeeklyMealPlan => {
  const days: DailyMealPlan[] = [];
  const start = new Date(weekStartKey);
  
  for (let i = 0; i < 7; i++) {
    const d = new Date(start);
    d.setDate(start.getDate() + i);
    days.push({
      dateKey: d.toISOString().slice(0, 10),
      targetCalories: defaultTargetCalories,
      slots: [
        { slot: 'breakfast', templateId: null },
        { slot: 'lunch', templateId: null },
        { slot: 'dinner', templateId: null },
        { slot: 'snack', templateId: null },
      ]
    });
  }

  const now = new Date().toISOString();
  return {
    weekStartKey,
    days,
    createdAt: now,
    updatedAt: now
  };
};

const calculateSummary = (
  status: MealPlannerStoreState['status'], 
  activeWeekPlan: WeeklyMealPlan | null
): MealPlannerSummary | null => {
  if (!activeWeekPlan) return null;

  const todayKey = getLocalDateKey();
  const todayPlan = activeWeekPlan.days.find(d => d.dateKey === todayKey);
  
  if (!todayPlan) {
    return {
      status,
      dayCaloriesPlanned: 0,
      dayCaloriesTarget: 0,
      dayCompletionPct: 0,
      selectedTemplateCount: 0
    };
  }

  const templates = useMealTemplateStore.getState().templates;
  let plannedCals = 0;
  let selectedCount = 0;

  todayPlan.slots.forEach(s => {
    if (s.templateId) {
      selectedCount++;
      const t = (templates as any[]).find(item => item.id === s.templateId);
      if (t) {
        plannedCals += (t.calories || 0);
      }
    }
  });

  const target = todayPlan.targetCalories;
  const pct = target > 0 ? Math.min(100, Math.round((plannedCals / target) * 100)) : 0;

  return {
    status,
    dayCaloriesPlanned: plannedCals,
    dayCaloriesTarget: target,
    dayCompletionPct: pct,
    selectedTemplateCount: selectedCount
  };
};

export const useMealPlannerStore = create<MealPlannerStoreState>((set, get) => ({
  status: 'idle',
  activeWeekPlan: null,
  suggestions: [],
  summary: null,
  notice: null,
  errorMessage: null,

  hydrateFromStorage: async () => {
    try {
      const payload = readStoredMealPlannerPayload();
      let plan = payload.activeWeekPlan;
      let status: MealPlannerStoreState['status'] = 'ready';

      if (!plan) {
        const weekStart = getWeekStartKeyFromDate(new Date());
        plan = buildEmptyWeekPlan(weekStart);
        status = 'empty';
      }

      set({ 
        activeWeekPlan: plan, 
        status, 
        summary: calculateSummary(status, plan) 
      });
    } catch (err) {
      set({ status: 'failed', errorMessage: 'Error al cargar el planificador.' });
    }
  },

  generateSuggestionsForDay: (dateKey, targetCalories) => {
    const templates = useMealTemplateStore.getState().templates as any[];
    if (templates.length === 0) return;

    const distributions = {
      breakfast: 0.25,
      lunch: 0.35,
      dinner: 0.30,
      snack: 0.10
    };

    const suggestions: MealPlannerSuggestion[] = (['breakfast', 'lunch', 'dinner', 'snack'] as MealSlot[]).map(slot => {
      const targetForSlot = targetCalories * distributions[slot];
      
      // Find template with closest calories
      let closest = templates[0];
      let minDiff = Math.abs((closest.calories || 0) - targetForSlot);

      for (let i = 1; i < templates.length; i++) {
        const diff = Math.abs((templates[i].calories || 0) - targetForSlot);
        if (diff < minDiff) {
          minDiff = diff;
          closest = templates[i];
        }
      }

      return {
        slot,
        templateId: closest.id,
        templateName: closest.name,
        calories: closest.calories || 0,
        reason: `Cercano a meta de ${Math.round(targetForSlot)} kcal`
      };
    });

    set({ suggestions });
  },

  setTemplateForSlot: async (dateKey, slot, templateId) => {
    const { activeWeekPlan } = get();
    if (!activeWeekPlan) return;

    const nextPlan = { ...activeWeekPlan };
    const day = nextPlan.days.find(d => d.dateKey === dateKey);
    if (day) {
      const slotRef = day.slots.find(s => s.slot === slot);
      if (slotRef) {
        slotRef.templateId = templateId;
      }
      nextPlan.updatedAt = new Date().toISOString();
      
      persistStoredMealPlannerPayload({ activeWeekPlan: nextPlan });
      set({ 
        activeWeekPlan: nextPlan, 
        notice: 'Plantilla asignada.',
        summary: calculateSummary(get().status, nextPlan)
      });
    }
  },

  clearDayPlan: async (dateKey) => {
    const { activeWeekPlan } = get();
    if (!activeWeekPlan) return;

    const nextPlan = { ...activeWeekPlan };
    const day = nextPlan.days.find(d => d.dateKey === dateKey);
    if (day) {
      day.slots.forEach(s => s.templateId = null);
      nextPlan.updatedAt = new Date().toISOString();
      
      persistStoredMealPlannerPayload({ activeWeekPlan: nextPlan });
      set({ 
        activeWeekPlan: nextPlan,
        summary: calculateSummary(get().status, nextPlan)
      });
    }
  },

  clearNotice: () => set({ notice: null }),
}));
