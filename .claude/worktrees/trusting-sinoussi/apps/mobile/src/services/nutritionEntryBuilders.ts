import type { LocalAiNutritionAnalysisItem, LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';
import type { FoodItem } from '../types/food';
import type { LoggedFood, SavedNutritionEntry } from '../types/nutrition';
import type { MealTemplateSummary } from '../stores/mealTemplateStore';
import { generateId } from '../utils/generateId';

export interface NutritionTotals {
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

function sumTotals(items: Array<Pick<NutritionTotals, 'calories' | 'protein' | 'carbs' | 'fats'>>) {
  return items.reduce<NutritionTotals>(
    (totals, item) => ({
      calories: totals.calories + (item.calories || 0),
      protein: totals.protein + (item.protein || 0),
      carbs: totals.carbs + (item.carbs || 0),
      fats: totals.fats + (item.fats || 0),
    }),
    { calories: 0, protein: 0, carbs: 0, fats: 0 },
  );
}

export function buildNutritionAnalysisItemFromFood(
  food: FoodItem,
  quantity = 1,
  rawText = food.name,
): LocalAiNutritionAnalysisItem {
  const servings = Math.max(0, quantity);
  const calories = round(food.calories * servings);
  const protein = round(food.protein * servings);
  const carbs = round(food.carbs * servings);
  const fats = round((food.fats ?? (food as FoodItem & { fat?: number }).fat ?? 0) * servings);
  const servingSize = food.servingSize && food.servingSize > 0 ? food.servingSize : 100;

  return {
    rawText,
    canonicalName: food.name,
    grams: round(servingSize * servings),
    quantity: servings,
    source: 'database',
    confidence: 1,
    reviewRequired: false,
    calories,
    protein,
    carbs,
    fats,
    nutritionPer100g: {
      calories: round((calories / Math.max(1, servingSize)) * 100),
      protein: round((protein / Math.max(1, servingSize)) * 100),
      carbs: round((carbs / Math.max(1, servingSize)) * 100),
      fats: round((fats / Math.max(1, servingSize)) * 100),
    },
  };
}

function buildNutritionAnalysisItemFromLoggedFood(food: LoggedFood): LocalAiNutritionAnalysisItem {
  return {
    rawText: food.foodName,
    canonicalName: food.foodName,
    grams: food.amount,
    quantity: food.quantity ?? 1,
    source: 'database',
    confidence: 1,
    reviewRequired: false,
    calories: round(food.calories),
    protein: round(food.protein),
    carbs: round(food.carbs),
    fats: round(food.fats),
  };
}

export function buildNutritionAnalysisFromFoods(
  foods: LoggedFood[],
  rawDescription: string,
  engine: LocalAiNutritionAnalysisResult['engine'] = 'heuristics',
): LocalAiNutritionAnalysisResult {
  const items = foods.map(buildNutritionAnalysisItemFromLoggedFood);
  const totals = sumTotals(items);

  return {
    items,
    overallConfidence: items.length ? 1 : 0,
    containsEstimatedItems: false,
    requiresReview: false,
    elapsedMs: 0,
    modelVersion: null,
    engine,
    runtimeError: null,
    rawDescription,
  } as LocalAiNutritionAnalysisResult;
}

export function buildNutritionAnalysisFromFood(
  food: FoodItem,
  quantity = 1,
  rawDescription = food.name,
): LocalAiNutritionAnalysisResult {
  const item = buildNutritionAnalysisItemFromFood(food, quantity, rawDescription);
  return {
    items: [item],
    overallConfidence: 1,
    containsEstimatedItems: false,
    requiresReview: false,
    elapsedMs: 0,
    modelVersion: null,
    engine: 'heuristics',
    runtimeError: null,
  };
}

export function buildNutritionAnalysisFromTemplate(
  template: MealTemplateSummary,
): LocalAiNutritionAnalysisResult {
  if (template.foods.length > 0) {
    return buildNutritionAnalysisFromFoods(template.foods, template.description || template.name);
  }

  return {
    items: [{
      rawText: template.description || template.name,
      canonicalName: template.name,
      grams: template.foodCount > 0 ? template.foodCount : undefined,
      quantity: template.foodCount > 0 ? template.foodCount : 1,
      source: 'database',
      confidence: 1,
      reviewRequired: false,
      calories: round(template.calories),
      protein: round(template.protein),
      carbs: round(template.carbs),
      fats: round(template.fats),
    }],
    overallConfidence: 1,
    containsEstimatedItems: false,
    requiresReview: false,
    elapsedMs: 0,
    modelVersion: null,
    engine: 'heuristics',
    runtimeError: null,
  };
}

export function buildSavedNutritionEntryFromAnalysis(
  description: string,
  analysis: LocalAiNutritionAnalysisResult,
  options: { mealType?: SavedNutritionEntry['mealType']; logDate?: string } = {},
): SavedNutritionEntry {
  const totals = sumTotals(analysis.items);
  const now = new Date();
  const baseTimestamp = options.logDate ? `${options.logDate}T${now.toISOString().slice(11, 19)}.000Z` : now.toISOString();

  return {
    id: generateId(),
    description,
    createdAt: baseTimestamp,
    loggedDate: options.logDate,
    mealType: options.mealType,
    totals,
    analysis,
  };
}
