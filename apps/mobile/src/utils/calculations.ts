import type { 
  Exercise,
  WorkoutLog,
  ExerciseMuscleInfo,
  Session,
  ExerciseSet,
  CompletedExercise,
  CompletedSet
} from '../types/workout';
import { Settings } from '../types/settings';
import type { WorkoutLogSummary, WorkoutOverview } from '@kpkn/shared-types';

/** Cuenta ejercicios en sesión (parts o exercises) */
export const getSessionExerciseCount = (session: Session): number => {
  if (session.parts && session.parts.length > 0) {
    return session.parts.reduce((acc, p) => acc + (p.exercises?.length || 0), 0);
  }
  return session.exercises?.length || 0;
};

/** Estima duración de sesión (min) */
export const estimateSessionDurationMinutes = (
  session: Session,
  exerciseList: { id?: string; name?: string; axialLoadFactor?: number }[]
): number => {
  const allEx = [...(session.exercises || [])];
  (session.parts || []).forEach((p: any) => allEx.push(...(p.exercises || [])));
  let totalSec = 0;
  const SEC_PER_WORK_SET = 40;
  const SEC_PER_WARMUP_SET = 45;
  const SEC_SETUP_COMPOUND = 20;
  const SEC_TRANSITION = 45;
  allEx.forEach((ex: any, i: number) => {
    const info = exerciseList.find((e: any) => e.id === ex.exerciseDbId || e.name === ex.name);
    const validSets = ex.sets?.filter((s: any) => (s as any).type !== 'warmup') || [];
    const warmupCount = (ex.warmupSets?.length ?? 0); // Mobile warmup structure might differ
    const workSets = validSets.length;
    const restSec = Math.min(300, ex.restTime ?? 90);
    const isCompound = (info?.axialLoadFactor ?? 0) > 0;
    totalSec += workSets * SEC_PER_WORK_SET
      + Math.max(0, workSets - 1) * restSec
      + warmupCount * SEC_PER_WARMUP_SET
      + (isCompound ? workSets * SEC_SETUP_COMPOUND : 0)
      + (i > 0 ? SEC_TRANSITION : 0);
  });
  return Math.max(1, Math.round(totalSec / 60));
};

export const REP_TO_PERCENT_1RM: { [key: number]: number } = {
    1: 100, 2: 95, 3: 93, 4: 90, 5: 87, 6: 85, 7: 83, 8: 80, 9: 77, 10: 75,
    11: 73, 12: 70, 13: 68, 14: 67, 15: 65,
};

export const rpeToRir = (rpe: number): number => Math.max(0, 10 - Math.max(0, rpe));
export const rirToRpe = (rir: number): number => Math.max(0, 10 - Math.max(0, rir));

/** Calcula 1RM estimado con fórmula Brzycki. */
export const calculateBrzycki1RM = (weight: number, reps: number, isAmrap: boolean = false): number => {
  if (!weight || weight <= 0 || !reps || reps <= 0) return 0;
  if (reps === 1) return weight;
  const effectiveReps = Math.min(reps, 30);
  let e1rm = weight * (36 / (37 - effectiveReps));
  if (isAmrap && reps > 3) e1rm = e1rm * 1.025;
  return parseFloat(e1rm.toFixed(1));
};

export const calculateWeightFrom1RM = (e1rm: number, reps: number): number => {
  if (reps <= 0 || e1rm <= 0) return 0;
  if (reps === 1) return e1rm;
  const effectiveReps = Math.min(reps, 30);
  return Math.max(0, e1rm * ((37 - effectiveReps) / 36));
};

/** Epley formula */
export const calculateEpley1RM = (weight: number, reps: number): number => {
  if (!weight || weight <= 0 || !reps || reps <= 0) return 0;
  if (reps === 1) return weight;
  const e1rm = weight * (1 + reps / 30);
  return parseFloat(e1rm.toFixed(1));
};

/** Hybrid 1RM */
export const calculateHybrid1RM = (weight: number, reps: number, isAmrap: boolean = false): number => {
  if (!weight || weight <= 0 || !reps || reps <= 0) return 0;
  if (reps === 1) return weight;
  const r = Math.min(reps, 50);
  let e1rm: number;
  if (r <= 10) {
    e1rm = weight * (36 / (37 - r));
  } else if (r <= 20) {
    e1rm = weight * (1 + r / 30);
  } else {
    e1rm = weight * (1 + 20 / 30) * Math.pow(1 + (r - 20) / 80, 0.9);
  }
  if (isAmrap && reps > 3) e1rm = e1rm * 1.025;
  return parseFloat(e1rm.toFixed(1));
};

/**
 * Inverse Hybrid 1RM
 */
export const calculateWeightFrom1RMHybrid = (e1rm: number, reps: number): number => {
  if (reps <= 0 || e1rm <= 0) return 0;
  if (reps === 1) return e1rm;
  const r = Math.min(reps, 50);
  let weight: number;
  if (r <= 10) {
    weight = e1rm * ((37 - r) / 36);
  } else if (r <= 20) {
    weight = e1rm / (1 + r / 30);
  } else {
    const base = e1rm / (1 + 20 / 30) / Math.pow(1 + (r - 20) / 80, 0.9);
    weight = base;
  }
  return Math.max(0, parseFloat(weight.toFixed(1)));
};

/**
 * Calcula el peso sugerido a partir de 1RM, reps objetivo e intensidad (RPE/RIR/failure).
 */
export const calculateWeightFrom1RMAndIntensity = (
  reference1RM: number,
  set: { targetReps?: number; targetRPE?: number; targetRIR?: number; intensityMode?: string }
): number | null => {
  if (!reference1RM || reference1RM <= 0) return null;
  const reps = set.targetReps ?? 0;
  if (reps <= 0) return null;
  let effectiveReps: number;
  if (set.intensityMode === 'failure' || set.intensityMode === 'amrap' || set.intensityMode === 'solo_rm') {
    effectiveReps = reps;
  } else if (set.targetRIR !== undefined && set.targetRIR !== null) {
    effectiveReps = reps + set.targetRIR;
  } else if (set.targetRPE !== undefined && set.targetRPE !== null) {
    effectiveReps = reps + (10 - set.targetRPE);
  } else {
    effectiveReps = reps + 2; // default RPE 8
  }
  if (effectiveReps <= 0) return null;
  const weight = calculateWeightFrom1RMHybrid(reference1RM, effectiveReps);
  return weight > 0 ? weight : null;
};

/** Sugiere descanso entre series (segundos) */
const roundRestTo30 = (seconds: number): number => {
  const clamped = Math.min(300, Math.max(60, seconds));
  return Math.round(clamped / 30) * 30;
};

export const suggestRestSeconds = (
  setsCount: number,
  avgRPE?: number,
  avgPercent1RM?: number,
  augeDrainNormalized?: number
): number => {
  const rpe = Math.min(10, Math.max(0, avgRPE ?? 8));
  const percent = Math.min(100, Math.max(0, avgPercent1RM ?? 0));
  const nearFailure = rpe >= 9;
  const highIntensity = rpe >= 8 || percent >= 80;
  const near1RM = percent >= 85;

  let baseSeconds = 150;
  if (nearFailure || near1RM) baseSeconds = 180;
  else if (highIntensity) baseSeconds = 165;
  else if (setsCount > 6) baseSeconds = 120;
  else if (setsCount <= 3) baseSeconds = 180;

  if (augeDrainNormalized != null && augeDrainNormalized > 0.5) baseSeconds += 30;

  return roundRestTo30(baseSeconds);
};

export const getOrderedDaysOfWeek = (startWeekOn: number) => {
    const days = [
        { label: 'Domingo', value: 0 },
        { label: 'Lunes', value: 1 },
        { label: 'Martes', value: 2 },
        { label: 'Miércoles', value: 3 },
        { label: 'Jueves', value: 4 },
        { label: 'Viernes', value: 5 },
        { label: 'Sábado', value: 6 }
    ];
    const startIndex = days.findIndex(d => d.value === startWeekOn);
    return [...days.slice(startIndex), ...days.slice(0, startIndex)];
};

export const getWeekId = (date: Date, startWeekOn: number): string => {
    const d = new Date(date.getTime());
    d.setHours(0, 0, 0, 0);
    const day = d.getDay();
    let diff = day - startWeekOn;
    if (diff < 0) diff += 7;
    d.setDate(d.getDate() - diff);
    return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
};

export const formatLargeNumber = (num: number): string => {
    if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`;
    if (num >= 10000) return `${(num / 1000).toFixed(0)}k`;
    if (num >= 1000) return `${(num / 1000).toFixed(1)}k`;
    return num.toLocaleString('es-ES');
};

export const roundWeight = (weight: number, unit: 'kg' | 'lbs') => {
    if (!weight || weight <= 0) return 0;
    const step = unit === 'kg' ? 1.25 : 2.5;
    const rounded = Math.round(weight / step) * step;
    return parseFloat(rounded.toFixed(1));
};

export const calculateIPFGLPoints = (totalLifted: number, bodyWeight: number, options: { gender: string; equipment: 'classic' | 'equipped'; lift: 'total' | 'bench' | 'squat' | 'deadlift'; weightUnit: 'kg' | 'lbs'; }): number => {
    if (!totalLifted || totalLifted <= 0 || !bodyWeight || bodyWeight <= 0) return 0;
    const { gender, equipment, weightUnit } = options;
    const genderKey = (gender === 'female' || gender === 'transfemale') ? 'female' : 'male';
    let bwInKg = weightUnit === 'lbs' ? bodyWeight * 0.45359237 : bodyWeight;
    const totalInKg = weightUnit === 'lbs' ? totalLifted * 0.45359237 : totalLifted;
    if (genderKey === 'male' && bwInKg < 40) bwInKg = 40;
    if (genderKey === 'female' && bwInKg < 35) bwInKg = 35;
    const COEFFS = {
        male: { 'equipped-total': { A: 1236.25115, B: 1449.21864, C: 0.01644 }, 'classic-total': { A: 1199.72839, B: 1025.18162, C: 0.00921 } },
        female: { 'equipped-total': { A: 758.63878, B: 949.31382, C: 0.02435 }, 'classic-total': { A: 610.32796, B: 1045.59282, C: 0.03048 } }
    };
    const liftKey = `${equipment}-total`;
    const coeffs = (COEFFS[genderKey] as any)[liftKey];
    if (!coeffs) return 0;
    const { A, B, C } = coeffs;
    const denominator = A - B * Math.exp(-C * bwInKg);
    if (denominator === 0) return 0;
    const coefficient = 100 / denominator;
    return parseFloat((coefficient * totalInKg).toFixed(2));
};

export const calculateFFMI = (heightCm: number, weightKg: number, bodyFatPercent: number) => {
    if (!heightCm || heightCm <= 0 || !weightKg || weightKg <= 0 || bodyFatPercent === undefined || bodyFatPercent < 0) return null;
    const heightM = heightCm / 100;
    const leanBodyMass = weightKg * (1 - (bodyFatPercent / 100));
    const ffmi = leanBodyMass / (heightM * heightM);
    const normalizedFfmi = ffmi + 6.1 * (1.8 - heightM);
    let interpretation = 'Novato';
    if (normalizedFfmi >= 26) interpretation = 'Superior/Elite';
    else if (normalizedFfmi >= 22) interpretation = 'Excelente';
    else if (normalizedFfmi >= 20) interpretation = 'Promedio';
    return { ffmi: ffmi.toFixed(1), normalizedFfmi: normalizedFfmi.toFixed(1), interpretation, leanBodyMass: leanBodyMass.toFixed(1) };
};

export const isMachineOrCableExercise = (exerciseInfo: any): boolean => {
    if (!exerciseInfo) return false;
    return exerciseInfo.equipment === 'Máquina' || exerciseInfo.equipment === 'Polea';
};

/**
 * Sugerencia de carga inteligente: considera últimas N sesiones, set anterior en sesión,
 * tendencia (estancado → pequeño bump), y 1RM como fallback.
 */
export const getWeightSuggestionForSet = (
    exercise: Exercise,
    exerciseInfo: ExerciseMuscleInfo | undefined,
    setIndex: number,
    completedSetsForExercise: { reps?: number, weight: number, machineBrand?: string }[],
    settings: Settings,
    history: WorkoutLog[],
    selectedTag?: string,
    currentSession1RMOverride?: number
): number | undefined => {
    const set = exercise.sets[setIndex];
    const reference1RM = currentSession1RMOverride || (exerciseInfo as any)?.calculated1RM || exercise.reference1RM;

    if (exercise.trainingMode === 'percent' && reference1RM && set.targetPercentageRM) {
        const weight = (reference1RM * set.targetPercentageRM) / 100;
        return roundWeight(weight, settings.weightUnit as any);
    }
    
    if (setIndex > 0) {
        const prevSetCompleted = completedSetsForExercise[setIndex - 1];
        if (prevSetCompleted?.weight && prevSetCompleted.weight > 0) return roundWeight(prevSetCompleted.weight, settings.weightUnit as any);
    }
    
    if (setIndex === 0) {
        const currentTagName = selectedTag || 'Base';
        const lastN = 5;
        const weightsFromHistory: number[] = [];
        for (let i = history.length - 1; i >= 0 && weightsFromHistory.length < lastN; i--) {
            const log = history[i];
            const completedEx = log.completedExercises.find(ce => ce.exerciseDbId === exercise.exerciseDbId || ce.exerciseName === exercise.name);
            if (completedEx && (completedEx.machineBrand || 'Base') === currentTagName) {
                const w = completedEx.sets[0]?.weight;
                if (w && w > 0) weightsFromHistory.push(w);
            }
        }
        if (weightsFromHistory.length > 0) {
            const lastWeight = weightsFromHistory[0];
            const isStagnant = weightsFromHistory.length >= 3 && weightsFromHistory.slice(0, 3).every(w => Math.abs(w - lastWeight) < 0.1);
            const step = settings.weightUnit === 'kg' ? 1.25 : 2.5;
            const bump = isStagnant ? step : 0;
            return roundWeight(lastWeight + bump, settings.weightUnit as any);
        }
    }

    if (reference1RM) {
        const rpe = set.targetRPE || 8;
        const reps = set.targetReps || 8;
        const rir = set.targetRIR;
        const effectiveReps = rir !== undefined ? reps + rir : reps + (10 - rpe);
        const weight = calculateWeightFrom1RMHybrid(reference1RM, effectiveReps);
        return roundWeight(weight, settings.weightUnit as any);
    }
    return undefined;
};

export const getEffectiveRepsForRM = (set: ExerciseSet): number | undefined => {
    const reps = set.targetReps;
    if (reps) {
        if (set.intensityMode === 'failure') return reps;
        if (set.targetRIR !== undefined) return reps + set.targetRIR;
        if (set.targetRPE !== undefined) return reps + (10 - set.targetRPE);
    }
    return undefined;
};

export const estimatePercent1RM = (repsToFailure: number): number | undefined => {
    const roundedReps = Math.round(repsToFailure);
    if (REP_TO_PERCENT_1RM[roundedReps]) {
        return REP_TO_PERCENT_1RM[roundedReps];
    }
    if (repsToFailure > 15) {
        return Math.round(100 / (1 + (repsToFailure / 30)));
    }
    return undefined;
};

export const calculateStreak = (history: WorkoutLog[], settings: Settings): { streak: number } => {
    if (!history || history.length === 0) return { streak: 0 };
    const sortedHistory = [...history].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
    const logsByWeek: Record<string, number> = {};
    sortedHistory.forEach(log => {
        const weekId = getWeekId(new Date(log.date), (settings as any).startWeekOn || 1);
        logsByWeek[weekId] = (logsByWeek[weekId] || 0) + 1;
    });
    let currentStreak = 0;
    const today = new Date();
    const currentWeekId = getWeekId(today, (settings as any).startWeekOn || 1);
    const prevWeek = new Date(today);
    prevWeek.setDate(prevWeek.getDate() - 7);
    const prevWeekId = getWeekId(prevWeek, (settings as any).startWeekOn || 1);
    if (!logsByWeek[currentWeekId] && !logsByWeek[prevWeekId]) return { streak: 0 };
    let checkDate = new Date(today);
    if (!logsByWeek[currentWeekId]) checkDate = prevWeek;
    while (true) {
        const wId = getWeekId(checkDate, (settings as any).startWeekOn || 1);
        if (logsByWeek[wId] && logsByWeek[wId] >= 3) {
            currentStreak++;
            checkDate.setDate(checkDate.getDate() - 7);
        } else break;
    }
    return { streak: currentStreak };
};

export const getRepDebtContextKey = (set: ExerciseSet): string => {
    return `reps-${set.targetReps || 0}-rpe-${set.targetRPE || 'x'}-rir-${set.targetRIR || 'x'}`;
};

export const calculateDynamicLoad = (baseWeight: number, factor: number): number => {
    return parseFloat((baseWeight * factor).toFixed(1));
};

export const calculateDynamicRatio = (current: number, reference: number): number => {
    if (reference === 0) return 1;
    return current / reference;
}

export const calculateAdvancedProjections = (program: any, startDateString: string): any[] => {
    const startDate = new Date(startDateString);
    const projections: any[] = [];
    let totalWeeks = 0;

    program.macrocycles?.forEach((macro: any) => {
        (macro.blocks || []).forEach((block: any) => {
            block.mesocycles?.forEach((meso: any, mesoIdx: number) => {
                totalWeeks += meso.weeks?.length || 0;
                if (mesoIdx === block.mesocycles.length - 1) {
                    const projectedEnd = new Date(startDate);
                    projectedEnd.setDate(projectedEnd.getDate() + (totalWeeks * 7));
                    projections.push({
                        type: 'block_end',
                        name: `Fin de Bloque: ${block.name}`,
                        date: projectedEnd.toISOString()
                    });
                }
            });
        });
    });

    const programEndDate = new Date(startDate);
    programEndDate.setDate(programEndDate.getDate() + (totalWeeks * 7));
    projections.push({ type: 'program_end', name: 'Fin del Programa', date: programEndDate.toISOString() });

    return projections;
};

export const resolveEventDate = (targetDate: Date, rules?: { avoidDaysOfWeek?: number[], avoidEndOfMonth?: boolean }): Date => {
    let resolved = new Date(targetDate);
    let safeGuard = 0;
    while (safeGuard < 30) {
        let hasConflict = false;
        if (rules?.avoidDaysOfWeek && rules.avoidDaysOfWeek.includes(resolved.getDay())) {
            hasConflict = true;
        }
        if (rules?.avoidEndOfMonth) {
            const lastDayOfMonth = new Date(resolved.getFullYear(), resolved.getMonth() + 1, 0).getDate();
            if (resolved.getDate() >= lastDayOfMonth - 1) {
                hasConflict = true;
            }
        }
        if (!hasConflict) break;
        resolved.setDate(resolved.getDate() + 1); 
        safeGuard++;
    }
    return resolved;
};

export const parseDateStringAsLocal = (dateStr: string): Date => {
    const [year, month, day] = dateStr.slice(0, 10).split('-').map(Number);
    return new Date(year, month - 1, day);
};

export const getDatePartFromString = (dateStr: string): string => {
    return dateStr?.slice(0, 10) || '';
};

/**
 * Compute linear regression (least squares) for a set of points.
 * Returns slope and intercept.
 */
export const computeLinearRegression = (points: { x: number; y: number }[]): { slope: number; intercept: number } => {
    const n = points.length;
    if (n < 2) return { slope: 0, intercept: 0 };

    let sumX = 0;
    let sumY = 0;
    let sumXY = 0;
    let sumXX = 0;

    for (const p of points) {
        sumX += p.x;
        sumY += p.y;
        sumXY += p.x * p.y;
        sumXX += p.x * p.x;
    }

    const denominator = n * sumXX - sumX * sumX;
    if (denominator === 0) return { slope: 0, intercept: sumY / n };

    const slope = (n * sumXY - sumX * sumY) / denominator;
    const intercept = (sumY - slope * sumX) / n;

    return { slope, intercept };
};

/**
 * Determine trend direction based on recent values.
 * Returns 'up', 'down', or 'stable'.
 * Stability threshold: 0.5% change considered stable.
 */
export const computeTrendDirection = (values: number[], threshold = 0.005): 'up' | 'down' | 'stable' => {
    if (values.length < 2) return 'stable';
    const recent = values.slice(-7); // consider last 7
    const first = recent[0];
    const last = recent[recent.length - 1];
    const change = (last - first) / first;
    if (Math.abs(change) < threshold) return 'stable';
    return change > 0 ? 'up' : 'down';
};
