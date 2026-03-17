// apps/mobile/src/services/fatigueService.ts
// Motor de Fatiga AUGE — Ported from PWA
import { 
    getDynamicAugeMetrics as _getDynamicAugeMetrics,
    getEffectiveRPE as _getEffectiveRPE,
    calculatePersonalizedBatteryTanks as _calculatePersonalizedBatteryTanks,
    calculateSetBatteryDrain as _calculateSetBatteryDrain,
    isSetEffective as _isSetEffective,
    WEEKLY_CNS_FATIGUE_REFERENCE as _WEEKLY_CNS_FATIGUE_REFERENCE
} from '@kpkn/shared-domain';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import type { 
    ExerciseCatalogEntry, 
    Session, 
    ExerciseSet, 
    CompletedExercise,
    CompletedSet 
} from '../types/workout';

export const getDynamicAugeMetrics = _getDynamicAugeMetrics;
export const getEffectiveRPE = _getEffectiveRPE;
export const calculatePersonalizedBatteryTanks = _calculatePersonalizedBatteryTanks;
export const calculateSetBatteryDrain = _calculateSetBatteryDrain;
export const isSetEffective = (set: any): boolean => !!_isSetEffective(set);

/**
 * Capacidad de referencia semanal para normalización de fatiga SNC.
 * Canonical source: @kpkn/shared-domain
 */
export const WEEKLY_CNS_FATIGUE_REFERENCE = _WEEKLY_CNS_FATIGUE_REFERENCE; 

const getPrimaryDisplayMuscle = (
    info: ExerciseCatalogEntry | undefined,
    exerciseName: string
): string => {
    const primary = info?.involvedMuscles?.find(m => m.role === 'primary');
    if (primary) {
        return primary.muscle.toString();
    }
    // Mobile fallback if catalog is missing: assume Core for unknown
    return 'Core';
};

const shouldSkipCompletedSet = (set: any): boolean => {
    if (!set) return true;
    if (set.type === 'warmup' || set.isIneffective) return true;
    return !isSetEffective(set);
};

/**
 * --- PREDICTOR GLOBAL DE LA SESIÓN ---
 * Suma todos los porcentajes para dar el total de batería que costará la sesión.
 */
export const calculatePredictedSessionDrain = (session: Session, exerciseList: ExerciseCatalogEntry[], settings?: any) => {
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    const exIndex = buildExerciseIndex(exerciseList);

    let totalCnsPct = 0;
    let totalMuscularPct = 0;
    let totalSpinalPct = 0;

    const muscleVolumeMap: Record<string, number> = {};
    const exercises = session.parts ? session.parts.flatMap(p => p.exercises) : session.exercises;

    exercises?.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId || (ex as any).exerciseId, ex.name);
        const primaryMuscle = getPrimaryDisplayMuscle(info as any, ex.name ?? '');

        // Contamos cuántas series lleva este músculo ANTES de empezar este ejercicio
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets?.forEach(s => {
            if ((s as any).type === 'warmup') return;

            accumulatedSets += 1; // Incrementamos el track en vivo

            const drain = calculateSetBatteryDrain(s, info as any, tanks, accumulatedSets, ex.restTime || 90);

            totalMuscularPct += drain.muscularDrainPct;
            totalCnsPct += drain.cnsDrainPct;
            totalSpinalPct += drain.spinalDrainPct;
        });

        // Guardamos el nuevo total para el siguiente ejercicio
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    return {
        cnsDrain: Math.round(Math.min(100, totalCnsPct)),
        muscleBatteryDrain: Math.round(Math.min(100, totalMuscularPct)),
        spinalDrain: Math.round(Math.min(100, totalSpinalPct)),
        totalSpinalScore: Math.round(totalSpinalPct * 10) // Valor de referencia extra
    };
};

/**
 * --- CÁLCULO DE ESTRÉS DE SESIÓN COMPLETADA ---
 */
export const calculateCompletedSessionStress = (
    completedExercises: CompletedExercise[],
    exerciseList: ExerciseCatalogEntry[]
): number => {
    const tanks = calculatePersonalizedBatteryTanks({});
    let totalStress = 0;
    const muscleVolumeMap: Record<string, number> = {};
    const exIndex = buildExerciseIndex(exerciseList);

    completedExercises.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const primaryMuscle = getPrimaryDisplayMuscle(info as any, ex.exerciseName ?? '');
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            if (shouldSkipCompletedSet(s)) return;
            accumulatedSets += 1;
            const drain = calculateSetBatteryDrain(s, info as any, tanks, accumulatedSets, (ex as any).restTime || 90);
            totalStress += drain.cnsDrainPct + drain.muscularDrainPct + drain.spinalDrainPct;
        });
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    return totalStress;
};

/**
 * --- DESGLOSE DE DRENAJE POR SISTEMA ---
 */
export const calculateCompletedSessionDrainBreakdown = (
    completedExercises: CompletedExercise[],
    exerciseList: ExerciseCatalogEntry[],
    settings?: any
): { totalStress: number; cnsDrain: number; muscularDrain: number; spinalDrain: number } => {
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    let totalCns = 0, totalMuscular = 0, totalSpinal = 0;
    const muscleVolumeMap: Record<string, number> = {};
    const exIndex = buildExerciseIndex(exerciseList);

    completedExercises.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const primaryMuscle = getPrimaryDisplayMuscle(info as any, ex.exerciseName ?? '');
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            if (shouldSkipCompletedSet(s)) return;
            accumulatedSets += 1;
            const drain = calculateSetBatteryDrain(s, info as any, tanks, accumulatedSets, (ex as any).restTime || 90);
            totalCns += drain.cnsDrainPct;
            totalMuscular += drain.muscularDrainPct;
            totalSpinal += drain.spinalDrainPct;
        });
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    const totalStress = totalCns + totalMuscular + totalSpinal;
    return { totalStress, cnsDrain: totalCns, muscularDrain: totalMuscular, spinalDrain: totalSpinal };
};

export const calculateExerciseFatigueScale = (exercise: any, info: ExerciseCatalogEntry | undefined): number => {
    if (!exercise.sets?.length) return 0;
    const defaultTanks = calculatePersonalizedBatteryTanks({});
    let totalDrain = 0;
    exercise.sets.forEach((s: any, idx: number) => {
        const drain = calculateSetBatteryDrain(s, info as any, defaultTanks, idx, exercise.restTime || 90);
        totalDrain += drain.cnsDrainPct + (drain.muscularDrainPct * 0.5);
    });
    return Math.min(10, Math.round(totalDrain / 2));
};
