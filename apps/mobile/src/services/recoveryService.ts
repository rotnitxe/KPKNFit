// apps/mobile/src/services/recoveryService.ts
// Motor de Recuperación AUGE — Ported from PWA
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

// --- LOCAL TYPE DEFINITIONS (Mirroring PWA/Migration) ---

export interface SleepLog {
    id: string;
    startTime: string;
    endTime: string;
    duration: number; // Horas
    quality?: number; // 1-5
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

// --- CONSTANTES & CONFIGURACIÓN ---

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

const clamp = (val: number, min: number, max: number) => Math.min(max, Math.max(min, val));

// Asegura que la función exponencial no devuelva NaN o Infinity
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
    // In mobile, we might not have a cache yet, or it's handled differently
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

// --- 1. CAPACIDAD DE TRABAJO DINÁMICA ---
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
                    // calculateSetBatteryDrain needs tanks
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

// --- CORE: CÁLCULO DE BATERÍA MUSCULAR ---

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
        // nutritionRecoveryMultiplier logic would go here, using 1.0 as fallback
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

// --- CORE: CÁLCULO DE BATERÍAS GLOBALES ---

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

    const todayStr = getLocalDateString();
    const wellbeingArray = Array.isArray(dailyWellbeingLogs) ? dailyWellbeingLogs : [];
    const recentWellbeing = wellbeingArray.find(l => l.date === todayStr) || wellbeingArray[wellbeingArray.length - 1];

    // MODULADORES DE NUTRICIÓN, SUEÑO Y ESTRÉS (Simplified for mobile porto)
    let cnsPenalty = 0;
    if (recentWellbeing && recentWellbeing.stressLevel >= 4) cnsPenalty += 12;

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

        cnsFatigue += logCns * Math.exp(-(Math.LN2 / cnsHalfLife) * hoursAgo);
        muscFatigue += logMusc * Math.exp(-(Math.LN2 / muscHalfLife) * hoursAgo);
        spinalFatigue += logSpinal * Math.exp(-(Math.LN2 / spinalHalfLife) * hoursAgo);
    });

    const cnsFatiguePct = transformFatigueToPercent(cnsFatigue, 65);
    const muscFatiguePct = transformFatigueToPercent(muscFatigue, 80);
    const spinalFatiguePct = transformFatigueToPercent(spinalFatigue, 110);

    const finalCns = clamp(100 - cnsFatiguePct - cnsPenalty, 0, 100);
    const finalMusc = clamp(100 - muscFatiguePct, 0, 100);
    const finalSpinal = clamp(100 - spinalFatiguePct, 0, 100);

    let verdict = "Todos tus sistemas están óptimos.";
    if (finalCns < 30) verdict = "Tu sistema nervioso está frito. Prioriza máquinas.";
    else if (finalSpinal < 35) verdict = "Tu columna está sobrecargada. Evita Pesos Muertos.";

    return {
        cns: Math.round(finalCns),
        muscular: Math.round(finalMusc),
        spinal: Math.round(finalSpinal),
        verdict
    };
};
