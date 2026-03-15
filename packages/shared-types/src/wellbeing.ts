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
