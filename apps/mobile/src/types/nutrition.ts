import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';

export type NutritionMealType = 'breakfast' | 'lunch' | 'dinner' | 'snack';

export interface SavedNutritionEntry {
  id: string;
  description: string;
  createdAt: string;
  loggedDate?: string;
  mealType?: NutritionMealType;
  totals: {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
  };
  analysis: LocalAiNutritionAnalysisResult;
}
