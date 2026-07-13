export interface CoreReminderSettings {
  remindersEnabled: boolean;
  reminderTime: string | null;
  mealRemindersEnabled: boolean;
  breakfastReminderTime: string;
  lunchReminderTime: string;
  dinnerReminderTime: string;
  missedWorkoutReminderEnabled: boolean;
  missedWorkoutReminderTime: string;
  augeBatteryReminderEnabled: boolean;
  augeBatteryReminderThreshold: number;
  augeBatteryReminderTime: string;
  eventRemindersEnabled: boolean;
  startWeekOn: number;
}

export interface RecoveryBatterySnapshot {
  overall: number;
  cns: number;
  muscular: number;
  spinal: number;
  source: 'wellbeing-derived' | 'fallback';
}

export interface WorkoutSessionSummary {
  id: string;
  name: string;
  dayOfWeek: number | null;
  exerciseCount: number;
  setCount: number;
  focus: string | null;
}

export interface WorkoutEventSummary {
  id: string;
  title: string;
  date: string;
  type: string | null;
  daysUntil: number;
}

export interface WorkoutLogSummary {
  id: string;
  date: string;
  programName: string;
  sessionName: string;
  exerciseCount: number;
  completedSetCount: number;
  durationMinutes: number | null;
}

export interface WorkoutOverview {
  activeProgramId: string | null;
  activeProgramName: string | null;
  activeProgramStatus: 'active' | 'paused' | 'completed' | 'none';
  currentWeekId: string | null;
  todaySession: WorkoutSessionSummary | null;
  nextSession: WorkoutSessionSummary | null;
  nextSessionOffsetDays: number | null;
  upcomingEvent: WorkoutEventSummary | null;
  recentLogs: WorkoutLogSummary[];
  plannedSetsThisWeek: number;
  completedSetsThisWeek: number;
  weeklySessionCount: number;
  hasWorkoutLoggedToday: boolean;
  battery: RecoveryBatterySnapshot | null;
}
