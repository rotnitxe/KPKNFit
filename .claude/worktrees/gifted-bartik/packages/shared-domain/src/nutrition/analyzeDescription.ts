import type {
  LocalAiNutritionAnalysisItem,
  LocalAiNutritionAnalysisRequest,
  LocalAiNutritionAnalysisResult,
  NutritionKnownFoodHint,
} from '@kpkn/shared-types';
import { LOCAL_CHILEAN_FOOD_CATALOG } from './localChileanFoods';
import { HEURISTIC_FOOD_CATALOG } from './heuristicFoodCatalog';

// COMMON_FALLBACKS fue eliminado — los fallbacks ahora provienen de HEURISTIC_FOOD_CATALOG,
// que es la fuente única de verdad compartida con la capa Java.
// Ver: packages/shared-domain/src/nutrition/heuristicFoodCatalog.ts


function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function round(value: number): number {
  return Math.round(value * 10) / 10;
}

function scoreFoodMatch(fragment: string, food: NutritionKnownFoodHint): number {
  const normalizedFood = normalizeText(food.name);
  if (fragment === normalizedFood) return 1;
  if (fragment.includes(normalizedFood) || normalizedFood.includes(fragment)) return 0.88;
  const fragmentTerms = fragment.split(' ');
  const foodTerms = new Set(normalizedFood.split(' '));
  const hits = fragmentTerms.filter(term => foodTerms.has(term)).length;
  return hits / Math.max(fragmentTerms.length, 1);
}

function findBestFood(fragment: string, knownFoods: NutritionKnownFoodHint[]): LocalAiNutritionAnalysisItem | null {
  const pool = [...knownFoods, ...LOCAL_CHILEAN_FOOD_CATALOG];
  let bestScore = 0;
  let bestFood: NutritionKnownFoodHint | null = null;

  for (const food of pool) {
    const score = scoreFoodMatch(fragment, food);
    if (score > bestScore) {
      bestScore = score;
      bestFood = food;
    }
  }

  if (!bestFood || bestScore < 0.55) return null;

  return {
    rawText: fragment,
    canonicalName: bestFood.name,
    quantity: 1,
    grams: bestFood.servingSize,
    source: bestScore > 0.8 ? 'database' : 'local-ai-estimate',
    confidence: bestScore > 0.8 ? 0.9 : 0.72,
    reviewRequired: bestScore < 0.65,
    calories: bestFood.calories,
    protein: bestFood.protein,
    carbs: bestFood.carbs,
    fats: bestFood.fats,
    nutritionPer100g: {
      calories: round((bestFood.calories / Math.max(bestFood.servingSize || 100, 1)) * 100),
      protein: round((bestFood.protein / Math.max(bestFood.servingSize || 100, 1)) * 100),
      carbs: round((bestFood.carbs / Math.max(bestFood.servingSize || 100, 1)) * 100),
      fats: round((bestFood.fats / Math.max(bestFood.servingSize || 100, 1)) * 100),
    },
  };
}

function fallbackEstimate(fragment: string): LocalAiNutritionAnalysisItem {
  const normalized = normalizeText(fragment);

  // Buscar en el catálogo heurístico canónico (= misma fuente que LocalAiModule.java)
  for (const food of HEURISTIC_FOOD_CATALOG) {
    const allAliases = food.aliases.map(a => normalizeText(a));
    if (allAliases.some(alias => normalized.includes(alias))) {
      const grams = food.defaultGrams;
      const multiplier = grams / 100;
      return {
        rawText: fragment,
        canonicalName: food.canonicalName,
        quantity: 1,
        grams,
        preparation: food.preparation,
        source: 'local-heuristic',
        confidence: 0.72,
        reviewRequired: false,
        nutritionPer100g: food.nutritionPer100g,
        calories: round(food.nutritionPer100g.calories * multiplier),
        protein: round(food.nutritionPer100g.protein * multiplier),
        carbs: round(food.nutritionPer100g.carbs * multiplier),
        fats: round(food.nutritionPer100g.fats * multiplier),
      };
    }
  }

  // Si ningún heurístico coincide, estimación genérica
  const baseCalories = normalized.includes('ensalada') ? 180 : normalized.includes('pollo') ? 320 : 260;
  return {
    rawText: fragment,
    canonicalName: fragment.trim(),
    quantity: 1,
    grams: 180,
    source: 'fallback-estimate',
    confidence: 0.48,
    reviewRequired: false,
    calories: baseCalories,
    protein: round(baseCalories * 0.08),
    carbs: round(baseCalories * 0.1),
    fats: round(baseCalories * 0.04),
  };
}

export function analyzeNutritionDescriptionLocally(
  request: LocalAiNutritionAnalysisRequest,
): LocalAiNutritionAnalysisResult {
  const startedAt = Date.now();
  const normalized = normalizeText(request.description);
  const fragments = normalized
    .split(/\s*(?:,|\+| y | con )\s*/gi)
    .map(fragment => fragment.trim())
    .filter(Boolean);

  const items = (fragments.length > 0 ? fragments : [normalized]).map(fragment => {
    return findBestFood(fragment, request.knownFoods ?? []) ?? fallbackEstimate(fragment);
  });

  const overallConfidence = items.length === 0
    ? 0
    : round(items.reduce((acc, item) => acc + item.confidence, 0) / items.length);

  return {
    items,
    overallConfidence,
    containsEstimatedItems: items.some(item => item.source !== 'database'),
    requiresReview: false,
    elapsedMs: Date.now() - startedAt,
    modelVersion: null,
    engine: 'heuristics',
    runtimeError: null,
  };
}

export function summarizeNutritionAnalysis(result: LocalAiNutritionAnalysisResult) {
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
