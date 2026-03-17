// services/fatigueService.ts
import { ExerciseSet, Session, ExerciseMuscleInfo, CompletedExercise, CompletedSet, Exercise, OngoingSetData } from '../types';
import { buildExerciseIndex, findExercise, findExerciseWithFallback } from '../utils/exerciseIndex';
import { inferInvolvedMuscles } from '../data/inferMusclesFromName';
import { getMuscleDisplayId } from '../utils/canonicalMuscles';
import { 
    getDynamicAugeMetrics as _getDynamicAugeMetrics,
    getEffectiveRPE as _getEffectiveRPE,
    calculatePersonalizedBatteryTanks as _calculatePersonalizedBatteryTanks,
    calculateSetBatteryDrain as _calculateSetBatteryDrain,
    isSetEffective as _isSetEffective,
    WEEKLY_CNS_FATIGUE_REFERENCE as _WEEKLY_CNS_FATIGUE_REFERENCE
} from '@kpkn/shared-domain';

export const getDynamicAugeMetrics = _getDynamicAugeMetrics;
export const getEffectiveRPE = _getEffectiveRPE;
export const calculatePersonalizedBatteryTanks = _calculatePersonalizedBatteryTanks;
export const calculateSetBatteryDrain = _calculateSetBatteryDrain;
export const isSetEffective = (set: any): boolean => !!_isSetEffective(set);

/**
 * Capacidad de referencia semanal para normalización de fatiga SNC (puntos).
 * Canonical source: @kpkn/shared-domain
 */
export const WEEKLY_CNS_FATIGUE_REFERENCE = _WEEKLY_CNS_FATIGUE_REFERENCE;

const getPrimaryDisplayMuscle = (
    info: ExerciseMuscleInfo | undefined,
    exerciseName: string,
    equipment: string = ''
): string => {
    const primary = info?.involvedMuscles?.find(m => m.role === 'primary');
    if (primary) {
        return getMuscleDisplayId(primary.muscle, primary.emphasis);
    }

    const inferred = inferInvolvedMuscles(exerciseName ?? '', equipment ?? '', 'Otro', 'upper')[0];
    return inferred ? getMuscleDisplayId(inferred.muscle, (inferred as any).emphasis) : 'Core';
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
export const calculatePredictedSessionDrain = (session: Session, exerciseList: ExerciseMuscleInfo[], settings?: any) => {
    const tanks = calculatePersonalizedBatteryTanks(settings);
    const exIndex = buildExerciseIndex(exerciseList);

    let totalCnsPct = 0;
    let totalMuscularPct = 0;
    let totalSpinalPct = 0;

    const muscleVolumeMap: Record<string, number> = {};

    const exercises = session.parts ? session.parts.flatMap(p => p.exercises) : session.exercises;

    exercises?.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.name ?? (ex as any).exerciseName);
        const primaryMuscle = getPrimaryDisplayMuscle(
            info,
            ex.name ?? (ex as any).exerciseName ?? '',
            (ex as any).equipment ?? ''
        );

        // Contamos cuántas series lleva este músculo ANTES de empezar este ejercicio
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets?.forEach(s => {
            if ((s as any).type === 'warmup') return;

            accumulatedSets += 1; // Incrementamos el track en vivo

            const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, ex.restTime || 90);

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

// Funciones Legacy Mantenidas para Retrocompatibilidad temporal en otras vistas
export const calculateSetStress = (set: any, info: ExerciseMuscleInfo | undefined, restTime: number = 90): number => {
    const defaultTanks = calculatePersonalizedBatteryTanks({});
    const drain = calculateSetBatteryDrain(set, info, defaultTanks, 0, restTime);
    return drain.muscularDrainPct;
};

export const calculateSpinalScore = (set: any, info: ExerciseMuscleInfo | undefined): number => {
    const defaultTanks = calculatePersonalizedBatteryTanks({});
    const drain = calculateSetBatteryDrain(set, info, defaultTanks, 0, 90);
    return drain.spinalDrainPct;
};

export const normalizeToTenScale = (verroScore: number): number => {
    return Math.min(10, Math.max(1, parseFloat(((verroScore / 10) * 10).toFixed(1)))); // Aproximación
};

export const calculateExerciseFatigueScale = (exercise: any, info: ExerciseMuscleInfo | undefined): number => {
    if (!exercise.sets?.length) return 0;
    const defaultTanks = calculatePersonalizedBatteryTanks({});
    let totalDrain = 0;
    exercise.sets.forEach((s: any, idx: number) => {
        const drain = calculateSetBatteryDrain(s, info, defaultTanks, idx, exercise.restTime || 90);
        totalDrain += drain.cnsDrainPct + (drain.muscularDrainPct * 0.5);
    });
    return Math.min(10, Math.round(totalDrain / 2));
};

/**
 * --- CÁLCULO DE ESTRÉS DE SESIÓN COMPLETADA ---
 * Calcula el estrés total de una sesión finalizada sumando el impacto en el SNC, muscular y espinal.
 * Usado para métricas de fatiga histórica y para disparar recomendaciones de recuperación (PCE).
 */
export const calculateCompletedSessionStress = (
    completedExercises: CompletedExercise[],
    exerciseList: ExerciseMuscleInfo[]
): number => {
    // Obtenemos los tanques de batería estándar (sin settings explícitos para cálculo histórico base)
    const tanks = calculatePersonalizedBatteryTanks({});
    let totalStress = 0;

    // Mapa para rastrear la fatiga acumulada por músculo en la misma sesión
    const muscleVolumeMap: Record<string, number> = {};

    const exIndex = buildExerciseIndex(exerciseList);

    completedExercises.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const primaryMuscle = getPrimaryDisplayMuscle(info, ex.exerciseName ?? '');

        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            // Ignorar series de calentamiento en el cálculo de estrés
            if (shouldSkipCompletedSet(s)) return;

            accumulatedSets += 1;

            // Calculamos el drenaje individual (asumimos 90s de descanso como fallback)
            const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, (ex as any).restTime || 90);

            // El score total es la suma bruta del desgaste
            totalStress += drain.cnsDrainPct + drain.muscularDrainPct + drain.spinalDrainPct;
        });

        // Actualizamos el contador de series de este músculo para penalizar volumen basura después
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    return totalStress;
};

/**
 * --- DESGLOSE DE DRENAJE POR SISTEMA (AUGE → Banister/Bayesian) ---
 * Retorna el drenaje por SNC, muscular y espinal para alimentar Banister y métricas de recuperación.
 * Lo que el usuario ingresa en WorkoutSession (reps, peso, RPE, parciales, dropsets, etc.)
 * fluye aquí y afecta AUGE, Banister y Bayesian.
 */
export const calculateCompletedSessionDrainBreakdown = (
    completedExercises: CompletedExercise[],
    exerciseList: ExerciseMuscleInfo[],
    settings?: any
): { totalStress: number; cnsDrain: number; muscularDrain: number; spinalDrain: number } => {
    const tanks = calculatePersonalizedBatteryTanks(settings || {});
    let totalCns = 0, totalMuscular = 0, totalSpinal = 0;
    const muscleVolumeMap: Record<string, number> = {};
    const exIndex = buildExerciseIndex(exerciseList);

    completedExercises.forEach(ex => {
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.exerciseName);
        const primaryMuscle = getPrimaryDisplayMuscle(info, ex.exerciseName ?? '');
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            if (shouldSkipCompletedSet(s)) return;
            accumulatedSets += 1;
            const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, (ex as any).restTime || 90);
            totalCns += drain.cnsDrainPct;
            totalMuscular += drain.muscularDrainPct;
            totalSpinal += drain.spinalDrainPct;
        });
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    const totalStress = totalCns + totalMuscular + totalSpinal;
    return { totalStress, cnsDrain: totalCns, muscularDrain: totalMuscular, spinalDrain: totalSpinal };
};
