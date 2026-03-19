import { create } from 'zustand';
import type { LoggedFood } from '../types/nutrition';
import {
  getStoredMealTemplateSource,
  readStoredMealTemplatesRaw,
  type StoredTemplateSource,
} from '../services/mobileDomainStateService';
import { generateId } from '../utils/generateId';

type MealTemplateStatus = 'idle' | 'ready' | 'empty';

export interface MealTemplateSummary {
  id: string;
  name: string;
  description: string;
  foods: LoggedFood[];
  foodCount: number;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
  quickDescription: string;
  createdAt: string | null;
}

interface MealTemplateStoreState {
  status: MealTemplateStatus;
  source: StoredTemplateSource;
  templates: MealTemplateSummary[];
  discardedCount: number;
  hydrateFromMigration: () => Promise<void>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stringOrFallback(value: unknown, fallback: string) {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : fallback;
}

function numberOrZero(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function stringOrEmpty(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function sanitizeFood(raw: unknown): LoggedFood | null {
  if (!isRecord(raw)) return null;

  const foodName = stringOrFallback(raw.foodName ?? raw.name ?? raw.canonicalName, '');
  if (!foodName) return null;
  const id = typeof raw.id === 'string' && raw.id.trim() !== ''
    ? raw.id.trim()
    : generateId();

  return {
    id,
    foodName,
    amount: numberOrZero(raw.amount || raw.quantity || raw.grams || 1) || 1,
    unit: stringOrFallback(raw.unit ?? raw.servingUnit, 'g'),
    calories: numberOrZero(raw.calories),
    protein: numberOrZero(raw.protein),
    carbs: numberOrZero(raw.carbs),
    fats: numberOrZero(raw.fats),
    pantryItemId: stringOrEmpty(raw.pantryItemId) || undefined,
    tags: Array.isArray(raw.tags) ? raw.tags.filter((tag): tag is string => typeof tag === 'string') : undefined,
    portionPreset: typeof raw.portionPreset === 'string' ? raw.portionPreset as LoggedFood['portionPreset'] : undefined,
    cookingMethod: typeof raw.cookingMethod === 'string' ? raw.cookingMethod as LoggedFood['cookingMethod'] : undefined,
    quantity: typeof raw.quantity === 'number' ? raw.quantity : undefined,
    fatBreakdown: isRecord(raw.fatBreakdown) ? {
      saturated: numberOrZero(raw.fatBreakdown.saturated),
      monounsaturated: numberOrZero(raw.fatBreakdown.monounsaturated),
      polyunsaturated: numberOrZero(raw.fatBreakdown.polyunsaturated),
      trans: numberOrZero(raw.fatBreakdown.trans),
    } : undefined,
    micronutrients: Array.isArray(raw.micronutrients)
      ? raw.micronutrients.filter(isRecord).map((micro) => ({
          name: stringOrFallback(micro.name, 'Micronutriente'),
          amount: numberOrZero(micro.amount),
          unit: stringOrFallback(micro.unit, ''),
        }))
      : undefined,
  };
}

function sanitizeFoods(rawFoods: unknown): LoggedFood[] {
  if (!Array.isArray(rawFoods)) return [];
  return rawFoods.map(sanitizeFood).filter((value): value is LoggedFood => value !== null);
}

function sumFoods(foods: LoggedFood[]) {
  return foods.reduce((acc, food) => ({
    calories: acc.calories + numberOrZero(food.calories),
    protein: acc.protein + numberOrZero(food.protein),
    carbs: acc.carbs + numberOrZero(food.carbs),
    fats: acc.fats + numberOrZero(food.fats),
  }), { calories: 0, protein: 0, carbs: 0, fats: 0 });
}

function buildQuickDescription(rawFoods: unknown) {
  if (!Array.isArray(rawFoods)) return '';

  const names = rawFoods
    .filter(isRecord)
    .map(food => stringOrFallback(food.foodName ?? food.name ?? food.description ?? food.canonicalName, ''))
    .filter(Boolean)
    .slice(0, 4);

  return names.join(', ');
}

function adaptTemplate(raw: unknown): MealTemplateSummary | null {
  if (!isRecord(raw)) return null;

  const id = stringOrFallback(raw.id, '');
  const name = stringOrFallback(raw.name, '');
  if (!id || !name) return null;

  const foods = sanitizeFoods(raw.foods);
  const quickDescription = buildQuickDescription(raw.foods);
  const totals = foods.length > 0
    ? sumFoods(foods)
    : {
        calories: numberOrZero(raw.totalCalories),
        protein: numberOrZero(raw.totalProtein),
        carbs: numberOrZero(raw.totalCarbs),
        fats: numberOrZero(raw.totalFats),
      };

  return {
    id,
    name,
    description: stringOrFallback(raw.description, quickDescription || 'Plantilla migrada'),
    foods,
    foodCount: foods.length,
    calories: totals.calories,
    protein: totals.protein,
    carbs: totals.carbs,
    fats: totals.fats,
    quickDescription: quickDescription || name,
    createdAt: stringOrFallback(raw.createdAt, '') || null,
  };
}

export const useMealTemplateStore = create<MealTemplateStoreState>(set => ({
  status: 'idle',
  source: 'empty',
  templates: [],
  discardedCount: 0,

  hydrateFromMigration: async () => {
    const rawTemplates = readStoredMealTemplatesRaw();
    const adapted = rawTemplates.map(adaptTemplate);
    const templates = adapted.filter((value): value is MealTemplateSummary => value !== null).slice(0, 12);
    const discardedCount = adapted.length - templates.length;

    set({
      status: templates.length > 0 ? 'ready' : 'empty',
      source: getStoredMealTemplateSource(),
      templates,
      discardedCount,
    });
  },
}));
