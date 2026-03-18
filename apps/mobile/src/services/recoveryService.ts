// apps/mobile/src/services/recoveryService.ts
// Motor de Recuperación AUGE — Full Port from PWA
import { 
    calculateMuscleRecovery as calculateMuscleRecoveryShared,
    computeAugeReadiness as computeAugeReadinessShared,
    AugeEngineConfig,
    calculatePersonalizedBatteryTanks
} from '@kpkn/shared-domain';
import { buildExerciseIndex, findExerciseWithFallback, ExerciseIndex } from '../utils/exerciseIndex';
import { calculateSetBatteryDrain, isSetEffective } from './fatigueService';
import { getLocalDateString } from '../utils/dateUtils';
import { getMuscleDisplayId, matchesMuscleTarget, normalizeCanonicalMuscle } from '../utils/canonicalMuscles';
import type { 
    ExerciseCatalogEntry, 
    WorkoutLog, 
    Session, 
    ExerciseSet,
    MuscleHierarchy 
} from '../types/workout';
import type { Settings } from '../types/settings';

export interface SleepLog {
    id: string;
    startTime: string;
    endTime: string;
    duration: number;
    quality?: number;
}

export interface DailyWellbeingLog {
    date: string;
    stressLevel: number;
    doms: number;
    workIntensity?: 'low' | 'medium' | 'high';
    studyIntensity?: 'low' | 'medium' | 'high';
}

export interface NutritionLog {
    id: string;
    date: string;
    description: string;
    calories: number;
    protein: number;
    carbs: number;
    fats: number;
}

export interface PostSessionFeedback {
    logId: string;
    sessionRpe?: number;
    energyAfter?: number;
    sorenessAfter?: number;
    notes?: string;
}

export interface PendingQuestionnaire {
    logId: string;
    sessionName: string;
    muscleGroups: string[];
    scheduledTime: number;
}

export interface BatteryAuditLog {
    icon: string;
    label: string;
    val: number | string;
    type: 'workout' | 'penalty' | 'bonus' | 'info';
}

export interface SpinalDrainEntry {
    exerciseName: string;
    totalSpinalDrain: number;
}

export interface PrecalibrationExerciseInput {
    exerciseDbId?: string;
    exerciseName: string;
    intensity: 'LIGERO' | 'MEDIO' | 'ALTO' | 'MUY_ALTO' | 'EXTREMO';
}

export interface PrecalibrationReadinessInput {
    sleepQuality: number;
    stressLevel: number;
    doms: number;
    motivation: number;
}

const ATHLETE_CAPACITY_FLOORS: Record<string, number> = {
    'enthusiast': 500,
    'hybrid': 650,
    'calisthenics': 600,
    'bodybuilder': 1000,
    'powerbuilder': 1100,
    'powerlifter': 1200,
    'weightlifter': 1000,
    'parapowerlifter': 1100
};

export const RECOVERY_FACTORS = {
    OPTIMAL_SLEEP: { label: 'Sueño Óptimo', factor: 1.2, icon: '😴', desc: 'Recuperación acelerada por descanso profundo.' },
    CALORIC_SURPLUS: { label: 'Superávit', factor: 1.1, icon: '🍲', desc: 'Exceso de energía para la síntesis de tejido.' },
    CALORIC_DEFICIT: { label: 'Déficit', factor: 0.85, icon: '📉', desc: 'Recursos limitados para la reparación muscular.' },
    STRESS_HIGH: { label: 'Estrés Alto', factor: 0.8, icon: '🤯', desc: 'El cortisol elevado inhibe la recuperación.' }
};

export const PRECALIBRATION_INTENSITY_TO_RPE: Record<string, number> = {
    LIGERO: 6,
    MEDIO: 7,
    ALTO: 8,
    MUY_ALTO: 9,
    EXTREMO: 10,
};

export const ACCORDION_MUSCLES: { id: string; label: string; isDeltoidPortion?: boolean }[] = [
    { id: 'Bíceps', label: 'Bíceps' },
    { id: 'Tríceps', label: 'Tríceps' },
    { id: 'Pectorales', label: 'Pectorales' },
    { id: 'Dorsales', label: 'Dorsales' },
    { id: 'Deltoides Anterior', label: 'Deltoides Anterior', isDeltoidPortion: true },
    { id: 'Deltoides Lateral', label: 'Deltoides Lateral', isDeltoidPortion: true },
    { id: 'Deltoides Posterior', label: 'Deltoides Posterior', isDeltoidPortion: true },
    { id: 'Cuádriceps', label: 'Cuádriceps' },
    { id: 'Glúteos', label: 'Glúteos' },
    { id: 'Isquiosurales', label: 'Isquiosurales' },
    { id: 'Pantorrillas', label: 'Pantorrillas' },
    { id: 'Abdomen', label: 'Abdomen' },
    { id: 'Antebrazo', label: 'Antebrazo' },
    { id: 'Trapecio', label: 'Trapecio' },
    { id: 'Core', label: 'Core' },
    { id: 'Erectores Espinales', label: 'Erectores Espinales' },
    { id: 'Aductores', label: 'Aductores' },
    { id: 'Cuello', label: 'Cuello' },
];

const clamp = (val: number, min: number, max: number) => Math.min(max, Math.max(min, val));

const safeExp = (val: number): number => {
    const res = Math.exp(val);
    return isNaN(res) || !isFinite(res) ? 0 : res;
};

const transformFatigueToPercent = (value: number, scale: number): number => {
    if (!Number.isFinite(value)) return 0;
    if (scale <= 0) return 0;
    const normalized = Math.max(0, value);
    const percent = 100 * (1 - safeExp(-normalized / scale));
    return clamp(percent, 0, 100);
};

const normalizeLookupKey = (value: string | undefined | null): string => {
    return (value || '')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLowerCase()
        .trim();
};

const shouldCountFatigueSet = (set: any): boolean => {
    return !!set && !(set.type === 'warmup' || set.isIneffective) && isSetEffective(set);
};

const getAdaptiveSystemBiasCorrection = (system: 'cns' | 'muscular' | 'spinal', adaptiveCache?: any): number => {
    const cache = adaptiveCache || {};
    const value = cache.selfImprovement?.suggested_adjustments?.[`${system}_bias_correction`];
    return typeof value === 'number' ? value : 0;
};

const isMuscleInGroup = (specificMuscle: string, targetCategory: string, specificEmphasis?: string): boolean => {
    if (!specificMuscle || !targetCategory) return false;
    if (matchesMuscleTarget(specificMuscle, targetCategory, specificEmphasis)) return true;
    const specificDisplay = getMuscleDisplayId(specificMuscle, specificEmphasis);
    return normalizeLookupKey(specificDisplay) === normalizeLookupKey(targetCategory);
};

const calculateUserWorkCapacity = (history: WorkoutLog[], muscleName: string, exerciseList: ExerciseCatalogEntry[], settings: Settings, idx?: ExerciseIndex): number => {
    const now = Date.now();
    const fourWeeksAgo = now - (28 * 24 * 60 * 60 * 1000);
    const recentLogs = history.filter(log => new Date(log.date).getTime() > fourWeeksAgo);
    const baseFloor = ATHLETE_CAPACITY_FLOORS[(settings as any).athleteType || 'enthusiast'] || 500;

    if (recentLogs.length === 0) return baseFloor;

    const index = idx || buildExerciseIndex(exerciseList);
    let totalStress = 0;

    recentLogs.forEach(log => {
        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(index, ex.exerciseDbId, ex.exerciseName);
            if (!info) return;
            const involvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle.toString(), muscleName, m.emphasis));
            if (involvement) {
                const stress = ex.sets.reduce((acc, s) => {
                    if (!shouldCountFatigueSet(s)) return acc;
                    const tanks = calculatePersonalizedBatteryTanks(settings);
                    const drain = calculateSetBatteryDrain(s, info as any, tanks, 0, (ex as any).restTime || 90);
                    return acc + drain.muscularDrainPct;
                }, 0);
                totalStress += (stress * (involvement.activation ?? 1));
            }
        });
    });

    const weeklyAvg = totalStress / 4;
    const calculatedCapacity = weeklyAvg * 1.8;
    return clamp(Math.max(calculatedCapacity, baseFloor), 500, 3500);
};

export const calculateMuscleBattery = (
    muscleName: string,
    history: WorkoutLog[],
    exerciseList: ExerciseCatalogEntry[],
    sleepLogs: SleepLog[],
    settings: Settings,
    muscleHierarchy: MuscleHierarchy | null,
    dailyWellbeingLogs: DailyWellbeingLog[] = [],
    nutritionLogs: NutritionLog[] = [],
    adaptiveCache?: any
) => {
    const todayStr = getLocalDateString();
    const wellbeingLog = dailyWellbeingLogs.find(l => l.date === todayStr) || dailyWellbeingLogs[dailyWellbeingLogs.length - 1];

    let nutritionMultiplier = 1.0;
    if ((settings as any).algorithmSettings?.augeEnableNutritionTracking !== false) {
        nutritionMultiplier = 1.0;
    } else if ((settings as any).calorieGoalObjective === 'deficit') {
        nutritionMultiplier = 1.25;
    }

    const exIndex = buildExerciseIndex(exerciseList);
    const enrichedSettings = {
        ...settings,
        athleteScore: {
            ...(settings as any).athleteScore,
            baseCapacity: calculateUserWorkCapacity(history, muscleName, exerciseList, settings, exIndex)
        }
    };

    const sharedWellness = wellbeingLog ? {
        date: wellbeingLog.date,
        sleepHours: (Array.isArray(sleepLogs) && sleepLogs.length > 0) ? (sleepLogs[0].duration || 7.5) : 7.5,
        sleepQuality: 3,
        nutritionStatus: (settings as any).calorieGoalObjective as any || 'maintenance',
        stressLevel: wellbeingLog.stressLevel,
        hydration: 'good' as any,
        doms: wellbeingLog.doms
    } : undefined;

    const result = calculateMuscleRecoveryShared(
        muscleName,
        history,
        adaptiveCache || {},
        sharedWellness,
        enrichedSettings as any,
        (ex, mName) => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            if (!info) return null;
            const involvement = info.involvedMuscles.find(m => isMuscleInGroup(m.muscle.toString(), mName, m.emphasis));
            if (!involvement) return null;
            return { role: involvement.role, activation: involvement.activation ?? 1.0 };
        },
        () => nutritionMultiplier
    );

    return {
        recoveryScore: result.recoveryScore,
        effectiveSets: result.effectiveSets,
        hoursSinceLastSession: result.hoursSinceLastSession,
        estimatedHoursToRecovery: result.estimatedHoursToRecovery,
        status: result.status === 'fresh' ? 'optimal' : result.status 
    };
};

export const calculateSystemicFatigue = (
    history: WorkoutLog[],
    sleepLogs: SleepLog[],
    dailyWellbeingLogs: DailyWellbeingLog[],
    exerciseList: ExerciseCatalogEntry[],
    settings?: Settings
) => {
    const now = Date.now();
    const exIndex = buildExerciseIndex(exerciseList);
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    const last7DaysLogs = history.filter(l => (now - new Date(l.date).getTime()) < 7 * 24 * 3600 * 1000);

    let cnsLoad = 0;

    last7DaysLogs.forEach(log => {
        const daysAgo = (now - new Date(log.date).getTime()) / (24 * 3600 * 1000);
        const recencyMultiplier = Math.max(0.1, Math.exp(-0.4 * daysAgo));

        let sessionCNS = 0;
        const muscleVolumeMap: Record<string, number> = {};
        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const primaryEntry = info?.involvedMuscles?.find(m => m.role === 'primary');
            const primaryMuscle = primaryEntry ? primaryEntry.muscle.toString() : 'Core';
            let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

            ex.sets.forEach(s => {
                if (!shouldCountFatigueSet(s)) return;
                accumulatedSets += 1;
                const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, (ex as any).restTime || 90);
                sessionCNS += drain.cnsDrainPct;
            });
            muscleVolumeMap[primaryMuscle] = accumulatedSets;
        });

        if ((log.duration || 0) > 75 * 60) sessionCNS *= 1.08;
        if ((log.duration || 0) > 90 * 60) sessionCNS *= 1.15;

        cnsLoad += sessionCNS * recencyMultiplier;
    });

    const normalizedGymFatigue = clamp(cnsLoad, 0, 100);

    let sleepPenalty = 0;
    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
        const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
        const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
        const wSleep = sortedSleep.length > 0
            ? ((sortedSleep[0]?.duration || 7.5) * 0.5) + ((sortedSleep[1]?.duration || 7.5) * 0.3) + ((sortedSleep[2]?.duration || 7.5) * 0.2)
            : 7.5;

        if (wSleep < 4.5) sleepPenalty = 40;
        else if (wSleep < 5.5) sleepPenalty = 25;
        else if (wSleep < 6.5) sleepPenalty = 15;
        else if (wSleep >= 8.5) sleepPenalty = -15;
        else if (wSleep > 7.5) sleepPenalty = -5;
    }

    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const wellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    let lifeStressPenalty = 0;
    if (wellbeing) {
        if (wellbeing.stressLevel >= 4) lifeStressPenalty += 15;
        else if (wellbeing.stressLevel === 3) lifeStressPenalty += 5;
        if (wellbeing.workIntensity === 'high' || wellbeing.studyIntensity === 'high') {
            lifeStressPenalty += 10;
        }
    }

    const totalFatigue = normalizedGymFatigue + sleepPenalty + lifeStressPenalty - getAdaptiveSystemBiasCorrection('cns');
    const cnsBattery = clamp(100 - totalFatigue, 0, 100);

    const calcLifeScore = (sPen: number, lPen: number) => Math.max(0, sPen + lPen);

    return {
        total: Math.round(cnsBattery),
        gym: Math.round(normalizedGymFatigue),
        life: Math.round(calcLifeScore(sleepPenalty, lifeStressPenalty))
    };
};

export const calculateDailyReadiness = (
    sleepLogs: SleepLog[],
    dailyWellbeingLogs: DailyWellbeingLog[],
    settings: Settings,
    cnsBattery: number,
    adaptiveCache?: any
) => {
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
    const lastSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime())[0];
    const sleepHours = lastSleep?.duration || 7.5;

    const effectiveAdaptiveCache = adaptiveCache ?? {};

    const config: AugeEngineConfig = {
        settings,
        adaptiveCache: effectiveAdaptiveCache,
        wellbeing: recentWellbeing ? {
            date: recentWellbeing.date,
            sleepHours,
            sleepQuality: 3,
            nutritionStatus: (settings as any).calorieGoalObjective as any || 'maintenance',
            stressLevel: recentWellbeing.stressLevel,
            hydration: 'good' as any,
            doms: recentWellbeing.doms
        } : undefined,
        history: [],
        cnsBattery
    };

    return computeAugeReadinessShared(config);
};

export const learnRecoveryRate = (currentMultiplier: number, calculatedScore: number, manualFeel: number): number => {
    const diff = manualFeel - calculatedScore;
    const adjustment = diff * 0.005;
    return clamp(currentMultiplier + adjustment, 0.5, 2.0);
};

export const checkPendingSurveys = (history: WorkoutLog[], feedbacks: PostSessionFeedback[]): PendingQuestionnaire[] => {
    const now = Date.now();
    return history.filter(log => {
        const timeSince = now - new Date(log.date).getTime();
        return timeSince > 2 * 3600 * 1000 && timeSince < 48 * 3600 * 1000 && !feedbacks.some(f => f.logId === log.id);
    }).slice(0, 1).map(log => ({
        logId: log.id,
        sessionName: log.sessionName,
        muscleGroups: [],
        scheduledTime: new Date(log.date).getTime() + 24 * 3600 * 1000
    }));
};

export const calculateSleepRecommendations = (
    settings: Settings,
    todayContext: DailyWellbeingLog | undefined,
    todayWorkout: WorkoutLog | undefined,
    exerciseList: ExerciseCatalogEntry[] = []
) => {
    const baseTarget = (settings as any).sleepTargetHours || 8;
    let extraTime = 0;
    const reasons: string[] = [];

    if (todayWorkout) {
        const volume = todayWorkout.completedExercises.length * 3;
        if (volume > 15) {
            extraTime += 0.75;
            reasons.push("Alta Carga Neural");
        } else if (volume > 10) {
            extraTime += 0.5;
            reasons.push("Volumen Moderado");
        }
    }

    if (todayContext) {
        if (todayContext.workIntensity === 'high' || todayContext.studyIntensity === 'high') {
            extraTime += 0.5;
            reasons.push("Desgaste Cognitivo");
        }
        if (todayContext.stressLevel > 3) {
            extraTime += 0.5;
            reasons.push("Estrés Elevado");
        }
    }

    const recommendedDuration = Math.min(10.5, baseTarget + extraTime);
    if (reasons.length === 0) reasons.push("Mantenimiento");

    const now = new Date();
    const tomorrow = new Date(now);
    tomorrow.setDate(now.getDate() + 1);
    const tomorrowDayIndex = tomorrow.getDay();

    const isTomorrowWorkDay = (settings as any).workDays?.includes(tomorrowDayIndex) ?? false;
    const wakeTime = isTomorrowWorkDay ? ((settings as any).wakeTimeWork || '07:00') : ((settings as any).wakeTimeOff || '09:00');

    const [wakeHours, wakeMinutes] = wakeTime.split(':').map(Number);
    const wakeDate = new Date();
    wakeDate.setHours(wakeHours, wakeMinutes, 0, 0);
    if (wakeDate.getTime() < now.getTime()) {
        wakeDate.setDate(wakeDate.getDate() + 1);
    }

    const bedTimeDate = new Date(wakeDate.getTime() - (recommendedDuration * 60 * 60 * 1000));
    const bedTime = bedTimeDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });

    return {
        duration: parseFloat(recommendedDuration.toFixed(1)),
        wakeTime,
        bedTime,
        reasons,
        isWorkDayTomorrow: isTomorrowWorkDay
    };
};

export const getPerMuscleBatteries = (
    history: WorkoutLog[],
    exerciseList: ExerciseCatalogEntry[],
    sleepLogs: SleepLog[],
    settings: Settings,
    muscleHierarchy: MuscleHierarchy | null,
    dailyWellbeingLogs: DailyWellbeingLog[] = [],
    nutritionLogs: NutritionLog[] = []
): Record<string, number> => {
    const result: Record<string, number> = {};
    for (const m of ACCORDION_MUSCLES) {
        const battery = calculateMuscleBattery(
            m.id,
            history,
            exerciseList,
            sleepLogs,
            settings,
            muscleHierarchy,
            dailyWellbeingLogs,
            nutritionLogs
        );
        result[m.id] = battery.recoveryScore;
    }
    return result;
};

export const applyPrecalibrationToBattery = (
    exercises: PrecalibrationExerciseInput[],
    readiness: PrecalibrationReadinessInput,
    exerciseList: ExerciseCatalogEntry[],
    settings: Settings
): { cnsDelta: number; muscularDelta: number; spinalDelta: number } => {
    const tanks = calculatePersonalizedBatteryTanks(settings);
    const exIndex = buildExerciseIndex(exerciseList);

    let totalCns = 0;
    let totalMusc = 0;
    let totalSpinal = 0;

    exercises.forEach((ex, exIdx) => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const rpe = PRECALIBRATION_INTENSITY_TO_RPE[ex.intensity] ?? 8;
        const reps = (info?.type === 'Básico' ? 5 : 8);
        const virtualSet = { completedRPE: rpe, completedReps: reps, targetReps: reps, weight: 0 };
        const drain = calculateSetBatteryDrain(virtualSet, info as any, tanks, exIdx, 90);
        totalCns += drain.cnsDrainPct;
        totalMusc += drain.muscularDrainPct;
        totalSpinal += drain.spinalDrainPct;
    });

    const readinessAvg = (readiness.sleepQuality + (6 - readiness.stressLevel) + (6 - readiness.doms) + readiness.motivation) / 4;
    const readinessFactor = readinessAvg / 5;
    const bonusFromReadiness = (readinessFactor - 0.6) * 15;
    const cnsBonus = Math.round(bonusFromReadiness);
    const muscBonus = Math.round(bonusFromReadiness * 0.5);

    return {
        cnsDelta: Math.round(-totalCns + cnsBonus),
        muscularDelta: Math.round(-totalMusc + muscBonus),
        spinalDelta: Math.round(-totalSpinal),
    };
};

export const applyPrecalibrationReadinessOnly = (
    readiness: PrecalibrationReadinessInput
): { cnsDelta: number; muscularDelta: number; spinalDelta: number } => {
    const readinessAvg = (readiness.sleepQuality + (6 - readiness.stressLevel) + (6 - readiness.doms) + readiness.motivation) / 4;
    const factor = (readinessAvg - 3) * 5;
    return {
        cnsDelta: Math.round(factor),
        muscularDelta: Math.round(factor * 0.5),
        spinalDelta: 0,
    };
};

export const getSpinalDrainByExercise = (
    history: WorkoutLog[],
    exerciseList: ExerciseCatalogEntry[],
    days: number | null,
    settings?: Settings
): SpinalDrainEntry[] => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    const exIndex = buildExerciseIndex(exerciseList);

    const cutoff = days === null ? 0 : now - (days * 24 * 60 * 60 * 1000);
    const relevantLogs = history.filter(log =>
        days === null || new Date(log.date).getTime() > cutoff
    );

    const byExercise = new Map<string, number>();

    relevantLogs.forEach(log => {
        const hoursAgo = (now - new Date(log.date).getTime()) / 3600000;
        const spinalHalfLife = 72;
        const decay = Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);

        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const name = ex.exerciseName || info?.name || 'Ejercicio';
            let total = 0;
            ex.sets.forEach((s, idx) => {
                const drain = calculateSetBatteryDrain(s, info, tanks, idx, 90);
                total += drain.spinalDrainPct * decay;
            });
            if (total > 0) {
                const prev = byExercise.get(name) || 0;
                byExercise.set(name, prev + total);
            }
        });
    });

    return Array.from(byExercise.entries())
        .map(([exerciseName, totalSpinalDrain]) => ({ exerciseName, totalSpinalDrain }))
        .sort((a, b) => b.totalSpinalDrain - a.totalSpinalDrain);
};

export const calculateGlobalBatteries = (
    history: WorkoutLog[],
    sleepLogs: SleepLog[],
    dailyWellbeingLogs: DailyWellbeingLog[],
    nutritionLogs: NutritionLog[],
    settings: Settings,
    exerciseList: ExerciseCatalogEntry[],
    adaptiveCache?: any
) => {
    const now = Date.now();
    const tanks = calculatePersonalizedBatteryTanks(settings);

    let cnsHalfLife = 28;
    let muscHalfLife = 40;
    let spinalHalfLife = 72;

    const auditLogs = { cns: [] as BatteryAuditLog[], muscular: [] as BatteryAuditLog[], spinal: [] as BatteryAuditLog[] };
    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    let cnsPenalty = 0;
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) {
        cnsPenalty += 12;
        auditLogs.cns.push({ icon: '🤯', label: 'Alto Estrés Reportado', val: -12, type: 'penalty' });
    }

    if (settings?.algorithmSettings?.augeEnableSleepTracking !== false) {
        const sleepArray = Array.isArray(sleepLogs) ? sleepLogs : [];
        const sortedSleep = [...sleepArray].sort((a, b) => new Date(b.endTime).getTime() - new Date(a.endTime).getTime()).slice(0, 3);
        let wSleep = 7.5;
        if (sortedSleep.length > 0) wSleep = ((sortedSleep[0]?.duration || 7.5) * 0.5) + ((sortedSleep[1]?.duration || 7.5) * 0.3) + ((sortedSleep[2]?.duration || 7.5) * 0.2);

        if (wSleep < 6) {
            cnsPenalty += 18;
            auditLogs.cns.push({ icon: '🥱', label: 'Deuda de Sueño Crítica', val: -18, type: 'penalty' });
        } else if (wSleep >= 8.5) {
            cnsPenalty -= 10;
            auditLogs.cns.push({ icon: '🛌', label: 'Sueño Profundo (Bonus)', val: '+10', type: 'bonus' });
        }
    }

    const exIndex = buildExerciseIndex(exerciseList);
    let cnsFatigue = 0, muscFatigue = 0, spinalFatigue = 0;
    const sevenDaysAgo = now - (7 * 24 * 3600 * 1000);
    const recentLogs = history.filter(l => new Date(l.date).getTime() > sevenDaysAgo);

    recentLogs.forEach(log => {
        let logCns = 0, logMusc = 0, logSpinal = 0;
        const hoursAgo = (now - new Date(log.date).getTime()) / 3600000;
        const muscleVolumeMap: Record<string, number> = {};

        log.completedExercises.forEach(ex => {
            const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
            const primaryEntry = info?.involvedMuscles?.find(m => m.role === 'primary');
            const primaryMuscle = primaryEntry ? primaryEntry.muscle.toString() : 'Core';
            let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;
            ex.sets.forEach((s) => {
                if (!shouldCountFatigueSet(s)) return;
                accumulatedSets += 1;
                const drain = calculateSetBatteryDrain(s, info as any, tanks, accumulatedSets, (ex as any).restTime || 90);
                logCns += drain.cnsDrainPct;
                logMusc += drain.muscularDrainPct;
                logSpinal += drain.spinalDrainPct;
            });
            muscleVolumeMap[primaryMuscle] = accumulatedSets;
        });

        const cnsDecay = logCns * Math.exp(-(Math.LN2 / cnsHalfLife) * hoursAgo);
        const muscDecay = logMusc * Math.exp(-(Math.LN2 / muscHalfLife) * hoursAgo);
        const spinalDecay = logSpinal * Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);

        cnsFatigue += cnsDecay;
        muscFatigue += muscDecay;
        spinalFatigue += spinalDecay;

        if (hoursAgo < 72) {
            if (cnsDecay > 3) auditLogs.cns.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(cnsDecay), type: 'workout' });
            if (muscDecay > 3) auditLogs.muscular.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(muscDecay), type: 'workout' });
            if (spinalDecay > 3) auditLogs.spinal.push({ icon: '🏋️', label: `Sesión: ${log.sessionName}`, val: -Math.round(spinalDecay), type: 'workout' });
        }
    });

    const calib = (settings as any).batteryCalibration || { cnsDelta: 0, muscularDelta: 0, spinalDelta: 0, lastCalibrated: '' };
    let cnsDelta = calib.cnsDelta || 0;
    let muscDelta = calib.muscularDelta || 0;
    let spinalDelta = calib.spinalDelta || 0;

    if (calib.lastCalibrated) {
        const calibHours = (now - new Date(calib.lastCalibrated).getTime()) / 3600000;
        const calibDecay = Math.max(0, 1 - (calibHours / 72));
        cnsDelta *= calibDecay;
        muscDelta *= calibDecay;
        spinalDelta *= calibDecay;
    }

    cnsDelta += getAdaptiveSystemBiasCorrection('cns');
    muscDelta += getAdaptiveSystemBiasCorrection('muscular');
    spinalDelta += getAdaptiveSystemBiasCorrection('spinal');

    const cnsFatiguePct = transformFatigueToPercent(cnsFatigue, 65);
    const muscFatiguePct = transformFatigueToPercent(muscFatigue, 80);
    const spinalFatiguePct = transformFatigueToPercent(spinalFatigue, 110);

    const finalCns = clamp(100 - cnsFatiguePct - cnsPenalty + cnsDelta, 0, 100);
    const finalMusc = clamp(100 - muscFatiguePct + muscDelta, 0, 100);
    const finalSpinal = clamp(100 - spinalFatiguePct + spinalDelta, 0, 100);

    let verdict = "Todos tus sistemas están óptimos. Es un buen día para buscar récords personales.";
    if (finalCns < 30) verdict = "Tu sistema nervioso está frito. NO intentes 1RMs hoy. Prioriza máquinas y reduce el RPE.";
    else if (finalSpinal < 35) verdict = "Tu columna y tejido axial están sobrecargados. Evita el Peso Muerto o Sentadillas Libres hoy.";
    else if (finalMusc < 30) verdict = "Alta fatiga muscular residual. Asegúrate de comer suficiente proteína y haz rutinas de bombeo.";
    else if (cnsPenalty > 10) verdict = "Tu falta de sueño/estrés está limitando tu potencial hoy. Autorregula tu peso y no vayas al fallo.";

    return {
        cns: Math.round(finalCns),
        muscular: Math.round(finalMusc),
        spinal: Math.round(finalSpinal),
        auditLogs,
        verdict,
    };
};
