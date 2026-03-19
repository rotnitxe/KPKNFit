import type { LocalAiNutritionAnalysisResult } from '@kpkn/shared-types';

export type NutritionMealType = 'breakfast' | 'lunch' | 'dinner' | 'snack';

export type NutritionGoalMetric = 'weight' | 'bodyFat' | 'muscleMass';
export type NutritionRiskSeverity = 'info' | 'warning' | 'danger';
export type NutritionTrendStatus = 'on_track' | 'behind' | 'ahead' | 'unknown';

export interface NutritionGoal {
  metric: NutritionGoalMetric;
  value: number;
  label: string;
  unit: string;
  priority: 'primary' | 'secondary';
}

export interface NutritionRiskFlag {
  id: string;
  code: string;
  severity: NutritionRiskSeverity;
  message: string;
  hardStop?: boolean;
}

export interface NutritionCalculationSnapshot {
  formula: 'mifflin' | 'harris' | 'katch';
  activityFactor: number;
  bmr: number | null;
  tdee: number | null;
  calorieTarget: number;
  generatedAt: string;
}

export interface NutritionProjection {
  etaDate: string | null;
  trendStatus: NutritionTrendStatus;
  weeklyDelta: number | null;
  confidence: number;
}

export interface CalorieGoalConfig {
  formula: 'mifflin' | 'harris' | 'katch';
  activityLevel: number;
  goal: 'lose' | 'maintain' | 'gain';
  weeklyChangeKg?: number;
  healthMultiplier?: number;
  customActivityFactor?: number;
  activityDaysPerWeek?: number;
  activityHoursPerDay?: number;
}

export interface NutritionPlan {
  id: string;
  name: string;
  goalType: 'weight' | 'bodyFat' | 'muscleMass';
  goalValue: number;
  trendMode: 'kg_per_week' | 'pct_fat_per_week';
  trendValue: number;
  startDate: string;
  estimatedEndDate?: string;
  calorieGoalConfig: CalorieGoalConfig;
  isActive: boolean;
  createdAt: string;
  primaryGoal?: NutritionGoal;
  secondaryGoals?: NutritionGoal[];
  calculationSnapshot?: NutritionCalculationSnapshot;
  riskFlags?: NutritionRiskFlag[];
  projection?: NutritionProjection;
}

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

export interface LoggedFood {
  id: string;
  foodName: string;
  amount: number;
  unit: string;
  calories: number;
  protein: number;
  carbs: number;
  fats: number;
  pantryItemId?: string;
  tags?: string[];
  fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
  micronutrients?: { name: string; amount: number; unit: string }[];
  portionPreset?: 'small' | 'medium' | 'large' | 'extra';
  cookingMethod?: 'crudo' | 'cocido' | 'plancha' | 'horno' | 'frito' | 'empanizado_frito';
  quantity?: number;
}

export interface NutritionLog {
  id: string;
  date: string;
  mealType: 'breakfast' | 'lunch' | 'dinner' | 'snack';
  foods: LoggedFood[];
  notes?: string;
  description?: string;
  status?: 'planned' | 'consumed';
  calories?: number;
  protein?: number;
  carbs?: number;
  fats?: number;
}
