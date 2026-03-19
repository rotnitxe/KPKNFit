// apps/mobile/src/services/analysisService.ts
import type { 
    WorkoutLog, 
    ExerciseCatalogEntry, 
    MuscleHierarchy,
    DetailedMuscleVolumeAnalysis,
    ProgramWeek,
    Session,
    Program,
    Exercise,
    CompletedExercise
} from '../types/workout';
import type { NutritionLog } from '../types/nutrition';
import { getMuscleDisplayId } from '../utils/canonicalMuscles';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { isSetEffective, calculateCompletedSessionStress, classifyACWR } from './fatigueService';
import { getWeekId, calculateFFMI, calculateBrzycki1RM } from '../utils/calculations';
import { getLocalDateString, getDatePartFromString, parseDateStringAsLocal } from '../utils/dateUtils';
import type { Settings } from '../types/settings';

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

export interface HistoricalFatigueDataPoint {
  weekId: string;
  name: string;
  acuteLoad: number;
  chronicLoad: number;
  acwr: number;
  tonnage: number;
  avgRMI: number;
  avgSleepQuality?: number | null;
  avgSleepHours: number | null;
  avgStressLevel: number | null;
  sessionCount: number;
}

const MUSCLE_ROLE_MULTIPLIERS: Record<string, number> = { 
    primary: 1.0, 
    secondary: 0.5, 
    stabilizer: 0, 
    neutralizer: 0 
};

const createChildToParentMap = (hierarchy: MuscleHierarchy): Map<string, string> => {
    const map = new Map<string, string>();
    if (!hierarchy || !hierarchy.bodyPartHierarchy) return map;

    for (const bodyPart in hierarchy.bodyPartHierarchy) {
        const subgroups = (hierarchy.bodyPartHierarchy as any)[bodyPart];
        
        for (const item of subgroups) {
            if (typeof item === 'object' && item !== null) {
                const subgroupName = Object.keys(item)[0];
                const children = Object.values(item)[0];
                if (Array.isArray(children) && subgroupName) {
                    children.forEach(child => map.set(child, subgroupName));
                }
            }
        }
    }
    return map;
};

export const calculateAverageVolumeForWeeks = (
    weeks: ProgramWeek[],
    exerciseList: ExerciseCatalogEntry[],
    muscleHierarchy: MuscleHierarchy | null,
    mode: 'simple' | 'complex' = 'complex'
): DetailedMuscleVolumeAnalysis[] => {
    if (weeks.length === 0) return [];

    const exIndex = buildExerciseIndex(exerciseList);
    const allMuscleTotals: Record<string, { totalVol: number, direct: Map<string, number>, indirect: Map<string, {sets: number, act: number}>, freqDirect: number, freqIndirect: number }> = {};
    const childToParentMap = muscleHierarchy ? createChildToParentMap(muscleHierarchy) : new Map<string, string>();
    
    const getDisplayGroup = (muscle: string) => getMuscleDisplayId(childToParentMap.get(muscle) || muscle) || 'Core';

    weeks.forEach(week => {
        week.sessions.forEach(session => {
            const exercises = (session.parts && session.parts.length > 0) ? session.parts.flatMap(p => p.exercises) : session.exercises;
            
            const sessionFreqImpact = new Map<string, { direct: number, indirect: number }>();

            exercises?.forEach(exercise => {
                const exerciseData = findExerciseWithFallback(exIndex, exercise.exerciseDbId || (exercise as any).exerciseId, exercise.name);
                
                if (!exerciseData || !exerciseData.involvedMuscles) return;

                const effectiveSets = exercise.sets?.filter(isSetEffective).length || 0;
                if (effectiveSets === 0) return;

                const isDirectEffective = (s: any) => {
                    if (!isSetEffective(s)) return false;
                    const rpe = s.rpe || s.completedRPE || s.targetRPE;
                    const rir = s.rir ?? s.completedRIR ?? s.targetRIR;
                    
                    if (s.isFailure || s.intensityMode === 'failure' || s.isAmrap || s.performanceMode === 'failed') return true;
                    if (rpe !== undefined && rpe >= 6) return true;
                    if (rir !== undefined && rir <= 4) return true;
                    
                    if (rpe === undefined && rir === undefined) return true;
                    return false;
                };
                const hasDirectEffectiveSets = exercise.sets?.some(isDirectEffective);

                const highestRolePerGroup = new Map<string, { maxMultiplier: number, bestRole: string }>();

                exerciseData.involvedMuscles.forEach(m => {
                    if (!m || !m.muscle) return;
                    const group = getDisplayGroup(m.muscle.toString());
                    const role = m.role ?? 'primary';
                    
                    const currentFreq = sessionFreqImpact.get(group) || { direct: 0, indirect: 0 };
                    if ((role === 'primary' || role === 'secondary') && hasDirectEffectiveSets) {
                        const impactVal = MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5;
                        currentFreq.direct = Math.max(currentFreq.direct, impactVal);
                    } else if (role === 'stabilizer' || role === 'neutralizer') {
                        currentFreq.indirect = 1.0;
                    }
                    sessionFreqImpact.set(group, currentFreq);

                    if (mode === 'simple' && role !== 'primary') return;
                    
                    const multiplier = mode === 'simple' ? 1.0 : (MUSCLE_ROLE_MULTIPLIERS[role] ?? 0.5);
                    
                    const existing = highestRolePerGroup.get(group);
                    if (!existing || existing.maxMultiplier < multiplier) {
                        highestRolePerGroup.set(group, {
                            maxMultiplier: multiplier,
                            bestRole: role
                        });
                    }
                });

                highestRolePerGroup.forEach((data, groupName) => {
                    if (!allMuscleTotals[groupName]) {
                        allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                    }

                    allMuscleTotals[groupName].totalVol += effectiveSets * data.maxMultiplier;

                    if (data.bestRole === 'primary') {
                        allMuscleTotals[groupName].direct.set(exercise.name ?? '', (allMuscleTotals[groupName].direct.get(exercise.name ?? '') || 0) + effectiveSets);
                    } else if (mode === 'complex') {
                        const existing = allMuscleTotals[groupName].indirect.get(exercise.name ?? '');
                        const percentageEquivalent = data.maxMultiplier * 100; 
                        
                        if (!existing || existing.act < percentageEquivalent) {
                            allMuscleTotals[groupName].indirect.set(exercise.name ?? '', { sets: (existing?.sets || 0) + effectiveSets, act: percentageEquivalent });
                        }
                    }
                });
            });

            sessionFreqImpact.forEach((impact, groupName) => {
                if (!allMuscleTotals[groupName]) {
                    allMuscleTotals[groupName] = { totalVol: 0, direct: new Map(), indirect: new Map(), freqDirect: 0, freqIndirect: 0 };
                }
                
                allMuscleTotals[groupName].freqDirect += impact.direct;
                
                if (impact.direct === 0) {
                    allMuscleTotals[groupName].freqIndirect += impact.indirect;
                }
            });
        });
    });

    return Object.entries(allMuscleTotals)
        .filter(([muscleGroup]) => muscleGroup !== 'General')
        .map(([muscleGroup, data]) => ({
            muscleGroup,
            displayVolume: Math.round((data.totalVol / weeks.length) * 10) / 10,
            totalSets: Math.round(data.totalVol / weeks.length),
            frequency: Math.round((data.freqDirect / weeks.length) * 10) / 10,
            indirectFrequency: Math.round((data.freqIndirect / weeks.length) * 10) / 10,
            avgRestDays: null,
            directExercises: Array.from(data.direct.entries()).map(([name, sets]) => ({ name, sets: Math.round((sets / weeks.length) * 10) / 10 })),
            indirectExercises: Array.from(data.indirect.entries()).map(([name, info]) => ({ name, sets: Math.round((info.sets / weeks.length) * 10) / 10, activationPercentage: info.act })),
            avgIFI: null,
            recoveryStatus: 'N/A' as const,
        })).filter(v => v.displayVolume > 0 || (v as any).indirectFrequency > 0).sort((a, b) => b.displayVolume - a.displayVolume);
};

export const calculateSessionVolume = (
    session: Session, 
    exerciseList: ExerciseCatalogEntry[], 
    muscleHierarchy: MuscleHierarchy | null,
    mode: 'simple' | 'complex' = 'complex'
): DetailedMuscleVolumeAnalysis[] => {
    const tempWeek: ProgramWeek = { id: 'temp', name: 'temp', sessions: [session] };
    return calculateAverageVolumeForWeeks([tempWeek], exerciseList, muscleHierarchy, mode);
};

export const calculateLast7DaysMuscleVolume = (
    history: WorkoutLog[],
    exerciseList: ExerciseCatalogEntry[],
    muscleHierarchy: MuscleHierarchy | null
): DetailedMuscleVolumeAnalysis[] => {
    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    
    const recentLogs = history.filter(log => new Date(log.date) >= sevenDaysAgo);
    
    const pseudoSessions: any[] = recentLogs.map(log => ({
        id: log.id,
        name: log.sessionName,
        exercises: log.completedExercises.map(ce => ({
            exerciseDbId: ce.exerciseDbId,
            name: ce.exerciseName,
            sets: ce.sets
        }))
    }));

    const pseudoWeek: ProgramWeek = {
        id: 'last-7-days',
        name: 'Last 7 Days',
        sessions: pseudoSessions as any
    };

    return calculateAverageVolumeForWeeks([pseudoWeek], exerciseList, muscleHierarchy, 'complex');
};

export const findPlannedWeek = (programs: Program[], history: WorkoutLog[], settings: Settings): ProgramWeek | null => {
    const today = new Date();
    const todayIndex = today.getDay();

    let plannedWeek: ProgramWeek | null = null;
    
    for (const program of programs) {
        for (const macro of program.macrocycles || []) {
            for (const block of (macro.blocks || [])) {
                for (const meso of block.mesocycles) {
                    for (const week of meso.weeks) {
                        if (week.sessions.some(s => s.dayOfWeek === todayIndex)) {
                            plannedWeek = week;
                            break;
                        }
                    }
                    if (plannedWeek) break;
                }
                if (plannedWeek) break;
            }
            if (plannedWeek) break;
        }
    }
    return plannedWeek;
};

export const calculateACWR = (
    history: WorkoutLog[],
    settings: Settings,
    exerciseList: ExerciseCatalogEntry[]
): { acwr: number; interpretation: string; color: string } => {
    if (history.length < 7) {
        return { acwr: 0, interpretation: 'Datos insuficientes', color: '#94A3B8' };
    }

    const today = new Date();
    const stressByDay: { [date: string]: number } = {};

    history.forEach(log => {
        const dateStr = log.date?.slice(0, 10) || '';
        const stress = (log as any).sessionStressScore ?? calculateCompletedSessionStress(log.completedExercises, exerciseList);
        stressByDay[dateStr] = (stressByDay[dateStr] || 0) + stress;
    });

    const getDailyStress = (date: Date): number => {
        return stressByDay[getLocalDateString(date)] || 0;
    };

    let acuteLoad = 0;
    for (let i = 0; i < 7; i++) {
        const date = new Date(today);
        date.setDate(today.getDate() - i);
        acuteLoad += getDailyStress(date);
    }

    const weeklyLoads: number[] = [];
    for (let week = 0; week < 4; week++) {
        let weeklyLoad = 0;
        for (let day = 0; day < 7; day++) {
            const date = new Date(today);
            date.setDate(today.getDate() - (week * 7) - day);
            weeklyLoad += getDailyStress(date);
        }
        weeklyLoads.push(weeklyLoad);
    }
    
    const chronicLoad = weeklyLoads.reduce((sum, load) => sum + load, 0) / 4;
    if (chronicLoad < 10) return { acwr: 0, interpretation: 'Carga baja', color: '#38BDF8' };

    const acwr = acuteLoad / chronicLoad;
    const { interpretation, color } = classifyACWR(acwr);

    return { acwr: parseFloat(acwr.toFixed(2)), interpretation, color };
};

export const calculateWeeklyTonnageComparison = (
    history: WorkoutLog[],
    settings: Settings
): { current: number; previous: number } => {
    const today = new Date();
    const currentWeekId = getWeekId(today, (settings as any).startWeekOn || 1);
    const currentWeekStartDate = new Date(currentWeekId);
    const prevWeekDate = new Date(currentWeekStartDate);
    prevWeekDate.setDate(prevWeekDate.getDate() - 7);
    const previousWeekId = getWeekId(prevWeekDate, (settings as any).startWeekOn || 1);

    let current = 0;
    let previous = 0;

    history.forEach(log => {
        const logDate = new Date(log.date);
        const logWeekId = getWeekId(logDate, (settings as any).startWeekOn || 1);
        const volume = log.completedExercises.reduce((total, ex) =>
            total + ex.sets.reduce((setTotal, s) => {
                const weight = s.weight || 0;
                const reps = s.completedReps || 0;
                const duration = s.completedDuration || 0;
                if (duration > 0) return setTotal + (duration * (weight > 0 ? weight : 1));
                return setTotal + ((weight + ((ex as any).useBodyweight ? ((settings as any).userVitals?.weight || 0) : 0)) * reps);
            }, 0), 0);
        
        if (logWeekId === currentWeekId) current += volume;
        else if (logWeekId === previousWeekId) previous += volume;
    });

    return { current: Math.round(current), previous: Math.round(previous) };
};

export const calculateFFMIProgress = (bodyProgress: any[], settings: Settings): { current: number | null; initial: number | null; trend: 'up' | 'down' | 'stable' } => {
    if (!(settings as any).userVitals?.height) return { current: null, initial: null, trend: 'stable' };
    const validLogs = bodyProgress.filter(log => log.weight && log.bodyFatPercentage).sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());
    
    if (validLogs.length === 0) {
        const { height, weight, bodyFatPercentage } = (settings as any).userVitals || {};
        if (height && weight && bodyFatPercentage) {
            const ffmi = calculateFFMI(height, weight, bodyFatPercentage);
            return { current: ffmi ? parseFloat(ffmi.normalizedFfmi) : null, initial: null, trend: 'stable' };
        }
        return { current: null, initial: null, trend: 'stable' };
    }
    
    const initialFFMI = calculateFFMI((settings as any).userVitals.height, validLogs[0].weight, validLogs[0].bodyFatPercentage);
    const currentFFMI = calculateFFMI((settings as any).userVitals.height, validLogs[validLogs.length - 1].weight, validLogs[validLogs.length - 1].bodyFatPercentage);
    const initial = initialFFMI ? parseFloat(initialFFMI.normalizedFfmi) : null;
    const current = currentFFMI ? parseFloat(currentFFMI.normalizedFfmi) : null;
    
    let trend: 'up' | 'down' | 'stable' = 'stable';
    if (current && initial && current > initial) trend = 'up';
    if (current && initial && current < initial) trend = 'down';
    return { current, initial, trend };
};

export const calculatePowerliftingMetrics = (history: WorkoutLog[], settings: Settings, exerciseList: ExerciseCatalogEntry[]): { weeklyVolume: { squat: { totalReps: number; effectiveSets: number }; bench: { totalReps: number; effectiveSets: number }; deadlift: { totalReps: number; effectiveSets: number }; }; balanceRatios: { ohpToBench: number; squatToDeadlift: number; }; } | null => {
    if (history.length === 0) return null;
    const today = new Date();
    const currentWeekId = getWeekId(today, (settings as any).startWeekOn || 1);
    const thisWeekLogs = history.filter(log => {
        const logDate = new Date(log.date);
        return getWeekId(logDate, (settings as any).startWeekOn || 1) === currentWeekId;
    });
    
    const weeklyVolume = { squat: { totalReps: 0, effectiveSets: 0 }, bench: { totalReps: 0, effectiveSets: 0 }, deadlift: { totalReps: 0, effectiveSets: 0 } };
    const max1RMs = { squat: 0, bench: 0, deadlift: 0, ohp: 0 };
    const liftKeywords = { squat: ['sentadilla', 'squat'], bench: ['press de banca', 'bench press'], deadlift: ['peso muerto', 'deadlift'], ohp: ['press militar', 'overhead press', 'ohp'] };

    history.forEach(log => {
        log.completedExercises.forEach(ex => {
            const name = ex.exerciseName.toLowerCase();
            ex.sets.forEach(set => {
                if (set.weight && set.completedReps) {
                    const e1rm = calculateBrzycki1RM(set.weight, set.completedReps);
                    if (liftKeywords.squat.some(kw => name.includes(kw))) max1RMs.squat = Math.max(max1RMs.squat, e1rm);
                    if (liftKeywords.bench.some(kw => name.includes(kw))) max1RMs.bench = Math.max(max1RMs.bench, e1rm);
                    if (liftKeywords.deadlift.some(kw => name.includes(kw))) max1RMs.deadlift = Math.max(max1RMs.deadlift, e1rm);
                    if (liftKeywords.ohp.some(kw => name.includes(kw))) max1RMs.ohp = Math.max(max1RMs.ohp, e1rm);
                }
            });
        });
    });

    thisWeekLogs.forEach(log => {
        log.completedExercises.forEach(ex => {
            const name = ex.exerciseName.toLowerCase();
            const isSquat = liftKeywords.squat.some(kw => name.includes(kw));
            const isBench = liftKeywords.bench.some(kw => name.includes(kw));
            const isDeadlift = liftKeywords.deadlift.some(kw => name.includes(kw));
            
            if (isSquat || isBench || isDeadlift) {
                let liftType: 'squat' | 'bench' | 'deadlift' | null = null;
                if (isSquat) liftType = 'squat';
                else if (isBench) liftType = 'bench';
                else if (isDeadlift) liftType = 'deadlift';
                
                if (liftType) {
                    ex.sets.forEach(set => {
                        weeklyVolume[liftType].totalReps += set.completedReps || 0;
                        if (isSetEffective(set)) weeklyVolume[liftType].effectiveSets++;
                    });
                }
            }
        });
    });
    
    return {
        weeklyVolume,
        balanceRatios: {
            ohpToBench: max1RMs.bench > 0 ? max1RMs.ohp / max1RMs.bench : 0,
            squatToDeadlift: max1RMs.deadlift > 0 ? max1RMs.squat / max1RMs.deadlift : 0
        }
    };
};

export const calculateProgramAdherence = (
    program: Program,
    history: WorkoutLog[],
    settings: Settings,
    exerciseList: ExerciseCatalogEntry[]
): { adherencePercentage: number; completedSessions: number; plannedSessions: number; sessionsWithVolumeVariance: number; } | null => {
    const programHistory = history.filter(log => log.programId === program.id);
    const completedSessionIds = new Set(programHistory.map(log => log.sessionId));
    const allSessions = program.macrocycles?.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks.flatMap(w => w.sessions)))) || [];

    if (allSessions.length === 0) return { adherencePercentage: 100, completedSessions: 0, plannedSessions: 0, sessionsWithVolumeVariance: 0 };

    const allSessionIds = new Set(allSessions.map(s => s.id));
    const completedUniqueSessionsCount = Array.from(allSessionIds).filter(id => completedSessionIds.has(id)).length;
    const adherencePercentage = (completedUniqueSessionsCount / allSessionIds.size) * 100;

    return {
        adherencePercentage,
        completedSessions: completedUniqueSessionsCount,
        plannedSessions: allSessionIds.size,
        sessionsWithVolumeVariance: 0
    };
};

export const calculateIFI = (exercise: Exercise, exerciseList: ExerciseCatalogEntry[]): number | null => {
    // Stub implementation that returns null for now
    // This maintains parity with the PWA implementation
    return null;
};

export const calculateDailyNutritionSummary = (
  nutritionLogs: NutritionLog[],
  settings: Settings,
  dateStr?: string
): {
  consumed: {
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
    fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
    micronutrients?: { name: string; amount: number; unit: string }[];
  };
  goals: { calories: number; protein: number; carbs: number; fats: number };
} => {
  const targetStr = dateStr || getLocalDateString();
  const todaysLogs = nutritionLogs.filter(log => log.date && log.date.startsWith(targetStr));
  const consumed = todaysLogs.reduce(
    (acc, log) => {
      (log.foods || []).forEach(food => {
        acc.calories += food.calories || 0;
        acc.protein += food.protein || 0;
        acc.carbs += food.carbs || 0;
        acc.fats += food.fats || 0;
        if (food.fatBreakdown) {
          acc.fatBreakdown = acc.fatBreakdown || { saturated: 0, monounsaturated: 0, polyunsaturated: 0, trans: 0 };
          acc.fatBreakdown.saturated += food.fatBreakdown.saturated || 0;
          acc.fatBreakdown.monounsaturated += food.fatBreakdown.monounsaturated || 0;
          acc.fatBreakdown.polyunsaturated += food.fatBreakdown.polyunsaturated || 0;
          acc.fatBreakdown.trans += food.fatBreakdown.trans || 0;
        }
        (food.micronutrients || []).forEach(m => {
          acc.micronutrients = acc.micronutrients || [];
          const existing = acc.micronutrients.find(x => x.name === m.name);
          if (existing) existing.amount += m.amount;
          else acc.micronutrients.push({ ...m });
        });
      });
      return acc;
    },
    { calories: 0, protein: 0, carbs: 0, fats: 0 } as {
      calories: number;
      protein: number;
      carbs: number;
      fats: number;
      fatBreakdown?: { saturated: number; monounsaturated: number; polyunsaturated: number; trans: number };
      micronutrients?: { name: string; amount: number; unit: string }[];
    }
  );
  return {
    consumed,
    goals: {
      calories: (settings as any).dailyCalorieGoal || 0,
      protein: (settings as any).dailyProteinGoal || 0,
      carbs: (settings as any).dailyCarbGoal || 0,
      fats: (settings as any).dailyFatGoal || 0,
    },
  };
};

const getSessionTonnage = (session: CompletedExercise | any): number => {
  return (session?.sets || []).reduce((total: number, set: any) => {
    const weight = Number(set?.weight ?? 0);
    const reps = Number(set?.completedReps ?? set?.reps ?? set?.targetReps ?? 0);
    if (!Number.isFinite(weight) || !Number.isFinite(reps)) return total;
    return total + Math.max(0, weight) * Math.max(0, reps);
  }, 0);
};

const getSetIntensityScore = (set: any): number => {
  const rpe = Number(set?.completedRPE ?? set?.rpe ?? set?.targetRPE);
  if (Number.isFinite(rpe) && rpe > 0) return Math.min(100, Math.max(0, rpe * 10));

  const rir = Number(set?.completedRIR ?? set?.rir ?? set?.targetRIR);
  if (Number.isFinite(rir)) return Math.min(100, Math.max(0, (10 - rir) * 10));

  if (set?.isFailure || set?.intensityMode === 'failure' || set?.performanceMode === 'failed') {
    return 100;
  }

  return 70;
};

const buildWeekLabel = (weekId: string) => {
  const parsed = new Date(`${weekId}T12:00:00`);
  if (Number.isNaN(parsed.getTime())) return weekId;
  return parsed.toLocaleDateString('es-ES', { month: 'short', day: 'numeric' });
};

const sleepQualityFromLog = (log: { duration?: number; quality?: number }) => {
  if (Number.isFinite(log.quality as number)) {
    return clamp(Number(log.quality), 1, 5);
  }

  if (Number.isFinite(log.duration as number) && (log.duration ?? 0) > 0) {
    return clamp((Number(log.duration) / 8) * 5, 1, 5);
  }

  return null;
};

export const calculateHistoricalFatigueData = (
  history: WorkoutLog[],
  settings: Settings,
  exerciseList: ExerciseCatalogEntry[],
  options?: {
    sleepLogs?: { startTime?: string; endTime?: string; duration?: number }[];
    dailyWellbeingLogs?: { date: string; stressLevel?: number }[];
  }
): HistoricalFatigueDataPoint[] => {
  if (!history.length) return [];

  const startWeekOn = (settings as any).startWeekOn || 1;
  const weekBuckets = new Map<
    string,
    {
      logs: WorkoutLog[];
      stress: number;
      tonnage: number;
      intensitySamples: number[];
    }
  >();

  history.forEach(log => {
    const date = parseDateStringAsLocal(getDatePartFromString(log.date));
    const weekId = getWeekId(date, startWeekOn);
    const current = weekBuckets.get(weekId) || {
      logs: [],
      stress: 0,
      tonnage: 0,
      intensitySamples: [],
    };

    const sessionStress =
      (log as any).sessionStressScore ??
      calculateCompletedSessionStress(log.completedExercises, exerciseList);

    current.logs.push(log);
    current.stress += sessionStress;
    current.tonnage += log.completedExercises.reduce((sum, ex) => sum + getSessionTonnage(ex), 0);
    log.completedExercises.forEach(ex => {
      ex.sets.forEach(set => {
        if (isSetEffective(set)) {
          current.intensitySamples.push(getSetIntensityScore(set));
        }
      });
    });
    weekBuckets.set(weekId, current);
  });

  const sortedWeeks = Array.from(weekBuckets.entries()).sort(
    ([left], [right]) => new Date(`${left}T12:00:00`).getTime() - new Date(`${right}T12:00:00`).getTime()
  );

  const sleepLogs = options?.sleepLogs || [];
  const wellbeingLogs = options?.dailyWellbeingLogs || [];

  return sortedWeeks.slice(-12).map(([weekId, bucket], index, arr) => {
    const chronicWindow = arr.slice(Math.max(0, index - 3), index + 1);
    const chronicLoad = chronicWindow.length
      ? chronicWindow.reduce((sum, [, entry]) => sum + entry.stress, 0) / chronicWindow.length
      : bucket.stress;
    const acwr = chronicLoad > 0 ? bucket.stress / chronicLoad : 0;
    const weekStart = new Date(`${weekId}T12:00:00`);
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);
    const weekEndKey = getLocalDateString(weekEnd);

    const weekSleepLogs = sleepLogs.filter(log => {
      const dateStr = (log.endTime || log.startTime || '').slice(0, 10);
      return dateStr >= weekId && dateStr <= weekEndKey;
    });
    const avgSleepHours = weekSleepLogs.length
      ? weekSleepLogs.reduce((sum, log) => sum + (log.duration || 0), 0) / weekSleepLogs.length
      : null;
    const sleepQualitySamples = weekSleepLogs
      .map(log => sleepQualityFromLog(log))
      .filter((value): value is number => typeof value === 'number');
    const avgSleepQuality = sleepQualitySamples.length
      ? sleepQualitySamples.reduce((sum, value) => sum + value, 0) / sleepQualitySamples.length
      : null;

    const weekWellbeing = wellbeingLogs.filter(log => log.date >= weekId && log.date <= weekEndKey);
    const avgStressLevel = weekWellbeing.length
      ? weekWellbeing.reduce((sum, log) => sum + (log.stressLevel || 0), 0) / weekWellbeing.length
      : null;

    const avgRmi = bucket.intensitySamples.length
      ? bucket.intensitySamples.reduce((sum, value) => sum + value, 0) / bucket.intensitySamples.length
      : bucket.logs.length
        ? Math.min(100, Math.max(40, Math.round((bucket.stress / Math.max(1, bucket.logs.length)) * 1.2)))
        : 0;

    return {
      weekId,
      name: buildWeekLabel(weekId),
      acuteLoad: Math.round(bucket.stress),
      chronicLoad: Math.round(chronicLoad),
      acwr: Math.round(acwr * 100) / 100,
      tonnage: Math.round(bucket.tonnage),
      avgRMI: Math.round(avgRmi),
      avgSleepQuality: avgSleepQuality == null ? null : Math.round(avgSleepQuality * 10) / 10,
      avgSleepHours: avgSleepHours == null ? null : Math.round(avgSleepHours * 10) / 10,
      avgStressLevel: avgStressLevel == null ? null : Math.round(avgStressLevel * 10) / 10,
      sessionCount: bucket.logs.length,
    };
  });
};

export const calculateEffectiveWeeklyVolume = (
  history: WorkoutLog[],
  exerciseList: ExerciseCatalogEntry[],
  muscleHierarchy: MuscleHierarchy | null,
  settings: Settings
): DetailedMuscleVolumeAnalysis[] => {
  if (!history.length) return [];

  const startWeekOn = (settings as any).startWeekOn || 1;
  const currentWeekId = getWeekId(new Date(), startWeekOn);
  const currentWeekLogs = history.filter(log => {
    const logDate = parseDateStringAsLocal(getDatePartFromString(log.date));
    return getWeekId(logDate, startWeekOn) === currentWeekId;
  });

  if (currentWeekLogs.length === 0) return [];

  const pseudoWeek: ProgramWeek = {
    id: currentWeekId,
    name: buildWeekLabel(currentWeekId),
    sessions: currentWeekLogs.map(log => ({
      id: log.id,
      name: log.sessionName,
      exercises: log.completedExercises.map(ex => ({
        id: ex.exerciseId,
        exerciseDbId: ex.exerciseDbId,
        exerciseId: ex.exerciseId,
        name: ex.exerciseName,
        sets: ex.sets,
      })) as any,
    })) as any,
  };

  return calculateAverageVolumeForWeeks([pseudoWeek], exerciseList, muscleHierarchy, 'complex');
};

export const calculateDetailedVolumeForAllPrograms = (
  programs: Program[],
  settings: Settings,
  exerciseList: ExerciseCatalogEntry[],
  muscleHierarchy: MuscleHierarchy | null
): DetailedMuscleVolumeAnalysis[] => {
  const program = programs[0];
  if (!program) return [];
  const allWeeks = program.macrocycles.flatMap(m => (m.blocks || []).flatMap(b => b.mesocycles.flatMap(meso => meso.weeks)));
  return calculateAverageVolumeForWeeks(allWeeks, exerciseList, muscleHierarchy, 'complex');
};

export const calculateWeeklyEffectiveVolumeByMuscleGroup = (
  programs: Program[],
  history: WorkoutLog[],
  settings: Settings,
  exerciseList: ExerciseCatalogEntry[],
  muscleHierarchy: MuscleHierarchy | null
): { muscleGroup: string; completed: number; planned: number }[] => {
  const today = new Date();
  const currentWeekId = getWeekId(today, (settings as any).startWeekOn || 1);
  const thisWeekLogs = history.filter(log =>
    getWeekId(parseDateStringAsLocal(getDatePartFromString(log.date)), (settings as any).startWeekOn || 1) === currentWeekId
  );
  const plannedWeek = findPlannedWeek(programs, history, settings);

  const exIndex = buildExerciseIndex(exerciseList);
  const completedVolume: Record<string, number> = {};
  thisWeekLogs.forEach(log => {
    log.completedExercises.forEach(ex => {
      const exInfo = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
      if (exInfo) {
        const primaryMuscle = exInfo.involvedMuscles.find(m => m.role === 'primary')?.muscle;
        if (primaryMuscle) {
          const effectiveSets = ex.sets.filter(isSetEffective).length;
          completedVolume[primaryMuscle.toString()] = (completedVolume[primaryMuscle.toString()] || 0) + effectiveSets;
        }
      }
    });
  });

  const plannedVolume: Record<string, number> = {};
  if (plannedWeek) {
    const exercises = plannedWeek.sessions.flatMap(s =>
      (s.parts && s.parts.length > 0 ? s.parts.flatMap(p => p.exercises) : s.exercises) || []
    );
    exercises.forEach(ex => {
      const exInfo = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.name);
      if (exInfo) {
        const primaryMuscle = exInfo.involvedMuscles.find(m => m.role === 'primary')?.muscle;
        if (primaryMuscle) {
          const effectiveSets = (ex.sets || []).filter(isSetEffective).length;
          plannedVolume[primaryMuscle.toString()] = (plannedVolume[primaryMuscle.toString()] || 0) + effectiveSets;
        }
      }
    });
  }

  const allMuscles = new Set([...Object.keys(completedVolume), ...Object.keys(plannedVolume)]);
  return Array.from(allMuscles).map(muscle => ({
    muscleGroup: muscle,
    completed: completedVolume[muscle] || 0,
    planned: plannedVolume[muscle] || 0
  })).sort((a, b) => (b.completed + b.planned) - (a.completed + a.planned));
};
