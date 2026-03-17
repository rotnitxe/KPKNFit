export interface AugeRuntimeSnapshot {
  computedAt: string;
  cnsBattery: number;
  readinessStatus: 'green' | 'yellow' | 'red';
  stressMultiplier: number;
  recommendation: string;
  diagnostics: string[];
  activeProgramName: string | null;
  weeklySessionCount: number;
  completedSetsThisWeek: number;
  plannedSetsThisWeek: number;
}

export interface AugeRuntimeDebug {
  hasWorkoutOverview: boolean;
  hasWellbeingSnapshot: boolean;
  hasSettings: boolean;
  wellbeingStressLevel: number | null;
  wellbeingSleepHours: number | null;
}
