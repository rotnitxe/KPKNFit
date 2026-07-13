export type PortionPreset = 'small' | 'medium' | 'large' | 'extra';

export type NutritionAnalysisEngine = 'runtime' | 'heuristics' | 'unavailable';

export type NutritionAnalysisSource =
  | 'database'
  | 'user-memory'
  | 'local-heuristic'
  | 'local-ai-estimate'
  | 'fallback-estimate';

export interface NutritionMacroEstimate {
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
}

export interface NutritionKnownFoodHint extends NutritionMacroEstimate {
  id: string;
  name: string;
  servingSize?: number;
  unit?: string;
  tags?: string[];
}

export interface LocalAiStatus {
  available: boolean;
  modelReady: boolean;
  modelVersion: string | null;
  deliveryMode: 'install-time-pack' | 'bundled-asset' | 'unknown';
  backend: string;
  engine: NutritionAnalysisEngine;
  lastError?: string | null;
}

export interface LocalAiNutritionAnalysisRequest {
  description: string;
  locale: 'es-CL';
  schemaVersion: string;
  knownFoods?: NutritionKnownFoodHint[];
  userMemory?: string[];
}

export interface LocalAiNutritionAnalysisItem extends NutritionMacroEstimate {
  rawText: string;
  canonicalName: string;
  grams?: number;
  quantity?: number;
  preparation?: string;
  source: NutritionAnalysisSource;
  confidence: number;
  reviewRequired: boolean;
  nutritionPer100g?: NutritionMacroEstimate;
}

export interface LocalAiNutritionAnalysisResult {
  items: LocalAiNutritionAnalysisItem[];
  overallConfidence: number;
  containsEstimatedItems: boolean;
  requiresReview: boolean;
  elapsedMs: number;
  modelVersion: string | null;
  engine: NutritionAnalysisEngine;
  runtimeError?: string | null;
}
