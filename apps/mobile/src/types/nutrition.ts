import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';

export interface SavedNutritionEntry {
  id: string;
  description: string;
  createdAt: string;
  totals: {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
  };
  analysis: LocalAiNutritionAnalysisResult;
}
