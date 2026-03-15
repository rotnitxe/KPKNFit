import { create } from 'zustand';
import {
  getStoredMealTemplateSource,
  readStoredMealTemplatesRaw,
  type StoredTemplateSource,
} from '../services/mobileDomainStateService';

type MealTemplateStatus = 'idle' | 'ready' | 'empty';

export interface MealTemplateSummary {
  id: string;
  name: string;
  description: string;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
  quickDescription: string;
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

function buildQuickDescription(rawFoods: unknown) {
  if (!Array.isArray(rawFoods)) return '';

  const names = rawFoods
    .filter(isRecord)
    .map(food => stringOrFallback(food.name ?? food.description ?? food.canonicalName, ''))
    .filter(Boolean)
    .slice(0, 4);

  return names.join(', ');
}

function adaptTemplate(raw: unknown): MealTemplateSummary | null {
  if (!isRecord(raw)) return null;

  const id = stringOrFallback(raw.id, '');
  const name = stringOrFallback(raw.name, '');
  if (!id || !name) return null;

  const quickDescription = buildQuickDescription(raw.foods);

  return {
    id,
    name,
    description: stringOrFallback(raw.description, quickDescription || 'Plantilla migrada'),
    calories: numberOrZero(raw.totalCalories),
    protein: numberOrZero(raw.totalProtein),
    carbs: numberOrZero(raw.totalCarbs),
    fats: numberOrZero(raw.totalFats),
    quickDescription: quickDescription || name,
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
