export type MealSlot = 'breakfast' | 'lunch' | 'dinner' | 'snack';

export interface MealPlanSlotSelection {
  slot: MealSlot;
  templateId: string | null;
}

export interface DailyMealPlan {
  dateKey: string; // YYYY-MM-DD
  slots: MealPlanSlotSelection[];
  targetCalories: number;
}

export interface WeeklyMealPlan {
  weekStartKey: string; // YYYY-MM-DD
  days: DailyMealPlan[];
  createdAt: string;
  updatedAt: string;
}

export interface MealPlannerSuggestion {
  slot: MealSlot;
  templateId: string;
  templateName: string;
  calories: number;
  reason: string;
}

export interface MealPlannerSummary {
  status: 'idle' | 'ready' | 'empty' | 'failed';
  dayCaloriesPlanned: number;
  dayCaloriesTarget: number;
  dayCompletionPct: number;
  selectedTemplateCount: number;
}
