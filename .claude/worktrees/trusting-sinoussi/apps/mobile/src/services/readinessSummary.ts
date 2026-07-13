import { calculateCompletedSessionStress } from './fatigueService';
import { readStoredSettingsRaw, readStoredWellbeingPayload } from './mobileDomainStateService';
import { useExerciseStore } from '../stores/exerciseStore';
import { useMobileNutritionStore } from '../stores/nutritionStore';
import { useWorkoutStore } from '../stores/workoutStore';
import { getLocalDateString } from '../utils/dateUtils';

type ReadinessFactorKey = 'sleep' | 'fatigue' | 'motivation' | 'stress' | 'nutrition';

interface ReadinessHistoryPoint {
  date: string;
  score: number;
}

interface ReadinessResult {
  score: number;
  factors: Record<ReadinessFactorKey, number>;
  history: ReadinessHistoryPoint[];
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

const toDayKey = (value: string | Date) => {
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return getLocalDateString();
  return getLocalDateString(date);
};

const average = (values: number[]) => {
  if (!values.length) return null;
  return values.reduce((sum, value) => sum + value, 0) / values.length;
};

const getLast7DayKeys = () =>
  Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - index);
    return getLocalDateString(date);
  });

const getWellbeingLogs = () => {
  const payload = readStoredWellbeingPayload();
  return {
    sleepLogs: Array.isArray(payload.sleepLogs) ? payload.sleepLogs : [],
    dailyWellbeingLogs: Array.isArray(payload.dailyWellbeingLogs) ? payload.dailyWellbeingLogs : [],
  };
};

const getTodaySleepHours = (sleepLogs: unknown[], dayKey: string) => {
  const matching = sleepLogs
    .filter((log): log is { duration?: number; startTime?: string; endTime?: string } => typeof log === 'object' && log !== null)
    .filter(log => {
      const rawDate = typeof log.endTime === 'string' && log.endTime ? log.endTime : log.startTime;
      return typeof rawDate === 'string' && rawDate.slice(0, 10) === dayKey;
    });

  const duration = average(matching.map(log => Number(log.duration ?? 0)).filter(value => Number.isFinite(value) && value > 0));
  return duration;
};

const getDailyWellbeing = (dailyWellbeingLogs: unknown[], dayKey: string) => {
  const matching = dailyWellbeingLogs
    .filter((log): log is { date?: string; stressLevel?: number; doms?: number; motivation?: number } => typeof log === 'object' && log !== null)
    .filter(log => typeof log.date === 'string' && log.date.slice(0, 10) === dayKey);

  const stress = average(matching.map(log => Number(log.stressLevel ?? 0)).filter(value => Number.isFinite(value)));
  const doms = average(matching.map(log => Number(log.doms ?? 0)).filter(value => Number.isFinite(value)));
  const motivation = average(matching.map(log => Number(log.motivation ?? 0)).filter(value => Number.isFinite(value)));

  return {
    stress: stress ?? null,
    doms: doms ?? null,
    motivation: motivation ?? null,
  };
};

const getNutritionScore = (dayKey: string, settings: Record<string, unknown>) => {
  const logs = useMobileNutritionStore.getState().savedLogs || [];
  const matching = logs.filter((log: any) => typeof log?.createdAt === 'string' && log.createdAt.slice(0, 10) === dayKey);
  if (matching.length === 0) return 70;

  const totalCalories = matching.reduce((sum: number, log: any) => sum + Number(log?.totals?.calories ?? 0), 0);
  const goal = Number(settings.dailyCalorieGoal ?? 0);
  if (goal > 0) {
    const ratio = totalCalories / goal;
    return clamp(100 - Math.abs(1 - ratio) * 100, 0, 100);
  }

  const protein = matching.reduce((sum: number, log: any) => sum + Number(log?.totals?.protein ?? 0), 0);
  return clamp(60 + Math.min(40, protein / 5), 0, 100);
};

const getFatigueScore = (dayKey: string) => {
  const history = useWorkoutStore.getState().history || [];
  const exerciseList = useExerciseStore.getState().exerciseList || [];
  const dayLogs = history.filter(log => toDayKey(log.date) === dayKey);

  if (dayLogs.length === 0) return 80;

  const totalStress = dayLogs.reduce(
    (sum, log) => sum + calculateCompletedSessionStress(log.completedExercises, exerciseList),
    0,
  );
  return clamp(100 - totalStress * 1.8, 0, 100);
};

const buildCurrentReadinessScore = (dayKey: string, settings: Record<string, unknown>) => {
  const { sleepLogs, dailyWellbeingLogs } = getWellbeingLogs();
  const workoutOverview = useWorkoutStore.getState().overview;

  const sleepHours = getTodaySleepHours(sleepLogs, dayKey);
  const wellbeing = getDailyWellbeing(dailyWellbeingLogs, dayKey);

  const sleepTarget = Number(settings.sleepTargetHours ?? 7.5);
  const sleepScore = clamp(((sleepHours ?? sleepTarget) / sleepTarget) * 100, 0, 100);
  const fatigueScore = workoutOverview?.battery?.overall ?? getFatigueScore(dayKey);
  const motivationScore = clamp(((wellbeing.motivation ?? 3.5) / 5) * 100, 0, 100);
  const stressScore = clamp(100 - ((wellbeing.stress ?? 2.5) * 15) - ((wellbeing.doms ?? 2.5) * 8), 0, 100);
  const nutritionScore = getNutritionScore(dayKey, settings);

  const score = Math.round(
    (sleepScore * 0.3) +
    (fatigueScore * 0.25) +
    (motivationScore * 0.15) +
    (stressScore * 0.15) +
    (nutritionScore * 0.15),
  );

  return {
    score: clamp(score, 0, 100),
    factors: {
      sleep: Math.round(sleepScore),
      fatigue: Math.round(fatigueScore),
      motivation: Math.round(motivationScore),
      stress: Math.round(stressScore),
      nutrition: Math.round(nutritionScore),
    } satisfies Record<ReadinessFactorKey, number>,
  };
};

export const getReadiness = (): ReadinessResult => {
  const settings = readStoredSettingsRaw();
  const last7Days = getLast7DayKeys();

  const history = last7Days.map(date => {
    const dayResult = buildCurrentReadinessScore(date, settings);

    return {
      date,
      score: dayResult.score,
    };
  });

  const current = buildCurrentReadinessScore(getLocalDateString(), settings);

  return {
    score: current.score,
    factors: current.factors,
    history,
  };
};
