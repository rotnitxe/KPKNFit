export interface WellbeingSnapshot {
  date: string;
  readiness: number | null;
  sleepQuality: number | null;
  stressLevel: number | null;
  doms: number | null;
  motivation: number | null;
  moodState: string | null;
}

export interface WellbeingOverview {
  latestSleepHours: number | null;
  averageSleepHoursLast7Days: number | null;
  sleepEntriesLast7Days: number;
  waterTodayMl: number;
  latestSnapshot: WellbeingSnapshot | null;
  pendingTaskCount: number;
  completedTaskCount: number;
  totalTaskCount: number;
}

export interface NutritionRecoveryInput {
  nutritionLogs: any[];
  settings: any;
  stressLevel?: number; // 1-5, del wellbeing
  hoursWindow?: number; // ventana en horas (default 48)
}

export interface NutritionRecoveryResult {
  /** Multiplicador de tiempo de recuperación: 1.0 = neutro, <1 = acelera, >1 = ralentiza */
  recoveryTimeMultiplier: number;
  /** Estado inferido: deficit | maintenance | surplus */
  status: 'deficit' | 'maintenance' | 'surplus';
  /** Razones para el multiplicador (auditoría) */
  factors: string[];
}

