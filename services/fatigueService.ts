// services/fatigueService.ts
import { ExerciseSet, Session, ExerciseMuscleInfo, CompletedExercise, CompletedSet, Exercise, OngoingSetData } from '../types';
import { buildExerciseIndex, findExercise, findExerciseWithFallback } from '../utils/exerciseIndex';
import { inferInvolvedMuscles } from '../data/inferMusclesFromName';

/**
 * Capacidad de referencia semanal para normalización de fatiga SNC (puntos).
 * Fatiga total teórica semanal de un atleta avanzado.
 */
export const WEEKLY_CNS_FATIGUE_REFERENCE = 4000;

/**
 * --- SISTEMA AUGE v2.0: MOTOR DE MÉTRICAS DINÁMICAS ---
 * Matriz Algorítmica de Componentes Principales (Patrón Base + Modificadores)
 */
export const getDynamicAugeMetrics = (info: ExerciseMuscleInfo | undefined, customName?: string) => {
    // Valores por defecto de seguridad
    let efc = info?.efc || (info?.type === 'Básico' ? 4.0 : info?.type === 'Accesorio' ? 2.5 : 1.5);
    let ssc = info?.ssc ?? info?.axialLoadFactor ?? (info?.type === 'Básico' ? 1.0 : 0.1);
    let cnc = info?.cnc || (info?.type === 'Básico' ? 4.0 : info?.type === 'Accesorio' ? 2.5 : 1.5);

    if (!info) return { efc, ssc, cnc };

    // Si el ejercicio ya tiene los 3 valores explícitamente definidos en la DB, los respetamos.
    if (info.efc !== undefined && info.cnc !== undefined && info.ssc !== undefined) {
        return { efc: info.efc, ssc: info.ssc, cnc: info.cnc };
    }

    const name = (customName || info.name).toLowerCase();
    
    // 1. DICCIONARIO BASE (Patrones Fundamentales AUGE)
    if (name.includes('peso muerto') || name.includes('deadlift')) {
        efc = 5.0; ssc = 2.0; cnc = 5.0;
        if (name.includes('rumano') || name.includes('rdl')) { efc = 4.2; ssc = 1.8; cnc = 4.0; }
        if (name.includes('sumo')) { efc = 4.8; ssc = 1.6; cnc = 4.8; }
    } else if (name.includes('sentadilla') || name.includes('squat')) {
        efc = 4.5; ssc = 1.5; cnc = 4.5;
        if (name.includes('frontal') || name.includes('front')) { efc = 4.2; ssc = 1.2; cnc = 4.5; }
        if (name.includes('búlgara') || name.includes('bulgarian')) { efc = 3.8; ssc = 0.8; cnc = 3.5; }
        if (name.includes('hack')) { efc = 3.5; ssc = 0.4; cnc = 3.0; }
    } else if (name.includes('press militar') || name.includes('ohp')) {
        efc = 4.0; ssc = 1.5; cnc = 4.2;
    } else if (name.includes('press banca') || name.includes('bench press')) {
        efc = 3.8; ssc = 0.3; cnc = 3.8;
    } else if (name.includes('dominada') || name.includes('pull-up')) {
        efc = 4.0; ssc = 0.2; cnc = 4.0;
    } else if (name.includes('remo') || name.includes('row')) {
        efc = 4.2; ssc = 1.6; cnc = 4.0;
        if (name.includes('seal') || name.includes('pecho apoyado')) { efc = 3.2; ssc = 0.1; cnc = 2.5; }
    } else if (name.includes('hip thrust') || name.includes('puente')) {
        efc = 3.5; ssc = 0.5; cnc = 3.0;
    } else if (name.includes('clean') || name.includes('snatch')) {
        efc = 4.8; ssc = 1.8; cnc = 5.0;
    }

    // 2. MODIFICADORES ALGORÍTMICOS DE HERRAMIENTA
    if (name.includes('mancuerna') || info.equipment === 'Mancuerna') {
        cnc = Math.min(5.0, cnc + 0.2); 
        ssc = Math.max(0, ssc - 0.2);
    } else if (name.includes('smith') || name.includes('multipower')) {
        cnc = Math.max(1.0, cnc - 0.5); 
        efc = Math.max(1.0, efc - 0.2);
    } else if (name.includes('polea') || name.includes('cable') || info.equipment === 'Polea') {
        cnc = Math.max(1.0, cnc - 0.3); 
        efc = Math.min(5.0, efc + 0.2); // Tensión constante
    }

    // 3. MODIFICADORES ALGORÍTMICOS DE TÉCNICA
    if (name.includes('pausa') || name.includes('paused')) {
        cnc = Math.min(5.0, cnc + 0.3);
        efc = Math.min(5.0, efc + 0.5);
    }
    if (name.includes('déficit') || name.includes('deficit')) {
        ssc = Math.min(2.0, ssc + 0.2); 
        efc = Math.min(5.0, efc + 0.3);
    }
    if (name.includes('parcial') || name.includes('rack pull') || name.includes('block')) {
        ssc = Math.min(2.0, ssc + 0.2); // Más peso soportado
        efc = Math.max(1.0, efc - 0.2); // Menor ROM
    }

    return { efc, ssc, cnc };
};

const getEFC = (info: ExerciseMuscleInfo | undefined): number => {
    return getDynamicAugeMetrics(info).efc;
};

export const getEffectiveRPE = (set: any): number => {
    let baseRpe = 7; // Valor por defecto

    // 1. Traductor Universal RIR a RPE
    if (set.completedRPE !== undefined) baseRpe = set.completedRPE;
    else if (set.targetRPE !== undefined) baseRpe = set.targetRPE;
    else if (set.completedRIR !== undefined) baseRpe = 10 - set.completedRIR;
    else if (set.targetRIR !== undefined) baseRpe = 10 - set.targetRIR;

    // 2. Override por Fallo (Base RPE 11)
    if (set.isFailure || set.performanceMode === 'failure' || set.intensityMode === 'failure' || set.isAmrap) {
        baseRpe = Math.max(baseRpe, 11);
    }

    // 3. Incremento Exponencial por Técnicas Avanzadas
    let techniqueBonus = 0;
    if (set.dropSets && set.dropSets.length > 0) techniqueBonus += set.dropSets.length * 1.5;
    if (set.restPauses && set.restPauses.length > 0) techniqueBonus += set.restPauses.length * 1.0; 
    if (set.partialReps && set.partialReps > 0) techniqueBonus += 0.5;

    if (techniqueBonus > 0 && baseRpe < 10) baseRpe = 10; 

    return baseRpe + techniqueBonus;
};

/**
 * --- KPKN ENGINE: TANQUES DE BATERÍA PERSONALIZADOS ---
 * Define el "100%" de capacidad de un atleta basado en su nivel y estilo de entrenamiento.
 */
export const calculatePersonalizedBatteryTanks = (settings: any) => {
    // Tanque Base (Usuario Intermedio Estándar)
    let baseMuscular = 300; 
    let baseCns = 250;      
    let baseSpinal = 4000;  

    // Modificador por Nivel de Experiencia (Work Capacity)
    const level = settings?.athleteScore?.profileLevel || 'Advanced';
    const levelMult = level === 'Beginner' ? 0.8 : 1.2; // Avanzados toleran 20% más volumen

    // Modificador por Estilo de Entrenamiento
    const style = (settings?.athleteScore?.trainingStyle || settings?.athleteType || 'Bodybuilder').toLowerCase();
    let cnsMult = 1.0, muscMult = 1.0, spineMult = 1.0;

    if (style.includes('powerlift')) {
        cnsMult = 1.3; spineMult = 1.4; muscMult = 0.9; // SNC y Espalda de acero, menos tolerancia metabólica
    } else if (style.includes('bodybuild') || style.includes('aesthetics')) {
        cnsMult = 0.9; spineMult = 0.9; muscMult = 1.3; // Alta tolerancia al ácido láctico y volumen local
    } else {
        cnsMult = 1.15; spineMult = 1.15; muscMult = 1.15; // Powerbuilder / Híbrido
    }

    return {
        muscularTank: baseMuscular * levelMult * muscMult,
        cnsTank: baseCns * levelMult * cnsMult,
        spinalTank: baseSpinal * levelMult * spineMult
    };
};

/**
 * --- KPKN ENGINE: SINGLE SOURCE OF TRUTH (Cálculo de Drenaje por Serie) ---
 * Retorna directamente el % exacto de batería que drena una serie individual.
 */
export const calculateSetBatteryDrain = (
    set: any, 
    info: ExerciseMuscleInfo | undefined, 
    tanks: { muscularTank: number, cnsTank: number, spinalTank: number },
    accumulatedSetsForMuscle: number = 0,
    restTime: number = 90
) => {
    const auge = getDynamicAugeMetrics(info, set.exerciseName || info?.name);
    const rpe = getEffectiveRPE(set);
    const reps = set.completedReps || set.targetReps || set.reps || 10;
    const isCompound = info?.type === 'Básico' || info?.tier === 'T1';

    // 1. CURVA BIOMECÁNICA EN "U" (Impacto por Rango de Repeticiones)
    let repsCnsMult = 1.0, repsMuscMult = 1.0, repsSpineMult = 1.0;

    if (reps <= 4) { // Zona RM (Fuerza Pura)
        if (isCompound) {
            repsCnsMult = 1.8;   // Destroza el SNC
            repsSpineMult = 1.6; // Alta carga axial
            repsMuscMult = 0.7;  // Poco tiempo bajo tensión para hipertrofia
        } else {
            repsCnsMult = 1.2; 
            repsSpineMult = 0.1;
            repsMuscMult = 0.8;
        }
    } else if (reps >= 16) { // Zona Metabólica
        repsCnsMult = 0.7;   // Menor carga neural
        repsSpineMult = 0.5;
        repsMuscMult = 1.4;  // Altísimo estrés metabólico
    }

    // 2. MULTIPLICADOR EXPONENCIAL POR RPE / RIR
    let intensityMult = 1.0;
    if (rpe >= 11) intensityMult = 1.8;       // Más allá del fallo (Dropsets/RestPause)
    else if (rpe >= 10) intensityMult = 1.5;  // Fallo Absoluto (Exponencial)
    else if (rpe >= 9) intensityMult = 1.15;  // RIR 1
    else if (rpe >= 8) intensityMult = 1.0;   // RIR 2 (Base óptima)
    else if (rpe >= 6) intensityMult = 0.7;   // RIR 3-4 (Bombeo)
    else intensityMult = 0.4;                 // RIR 5+ (Calentamiento/Recuperación)

    // 3. TOXICIDAD POR VOLUMEN ACUMULADO (Intra-Sesión)
    // Si ya hiciste 6 series de un músculo hoy, la 7ma te fatiga exponencialmente más.
    let junkVolumeMult = 1.0;
    if (accumulatedSetsForMuscle >= 6) {
        junkVolumeMult = 1.0 + ((accumulatedSetsForMuscle - 5) * 0.35); // Ej: Serie 6 = x1.35, Serie 7 = x1.70
    }

    // 4. FACTOR DE DESCANSO (Densidad)
    let restFactor = 1.0;
    if (restTime <= 45) restFactor = 1.3; // Superseries drenan más rápido
    else if (restTime >= 180) restFactor = 0.85; // Descansos largos protegen el SNC

    // 5. PARCIALES = VOLUMEN BASURA (fatiga en desmedro del estímulo)
    // Las reps parciales aumentan fatiga metabólica sin aportar estímulo de fuerza.
    // Penalizan la batería muscular (más drenaje) sin aumentar SNC/espinal proporcionalmente.
    const partialReps = set.partialReps || 0;
    const junkVolumeFromPartials = partialReps > 0 ? 1 + (partialReps * 0.2) : 1; // +20% muscular drain por cada parcial

    // 6. CÁLCULO DE DAÑO BRUTO
    const rawMuscular = auge.efc * repsMuscMult * intensityMult * junkVolumeMult * restFactor * junkVolumeFromPartials * 8.0;
    const rawCns = auge.cnc * repsCnsMult * intensityMult * restFactor * 6.0;
    
    // El estrés espinal requiere el peso. Si no hay peso, lo estimamos con RPE y EFC.
    const weightFactor = set.weight ? (set.weight * 0.05) : (auge.efc * 2.0); 
    const rawSpinal = auge.ssc * repsSpineMult * intensityMult * weightFactor * 4.0;

    // 7. CONVERSIÓN A PORCENTAJE DE BATERÍA DRENADA
    return {
        muscularDrainPct: (rawMuscular / tanks.muscularTank) * 100,
        cnsDrainPct: (rawCns / tanks.cnsTank) * 100,
        spinalDrainPct: (rawSpinal / tanks.spinalTank) * 100
    };
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
        const info = findExerciseWithFallback(exIndex, ex.exerciseDbId, ex.name ?? ex.exerciseName);
        const name = ex.name ?? (ex as any).exerciseName ?? '';
        const primaryMuscle = info?.involvedMuscles?.find(m => m.role === 'primary')?.muscle
            || inferInvolvedMuscles(name, (ex as any).equipment ?? '', 'Otro', 'upper')[0]?.muscle
            || 'Core';
        
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

export const isSetEffective = (set: any): boolean => {
    const rpe = getEffectiveRPE(set);
    if (rpe > 6) return true;
    const rir = set.completedRIR ?? set.targetRIR;
    if (rir !== undefined && rir < 4) return true;
    if (set.isFailure || set.performanceMode === 'failure' || set.intensityMode === 'failure') return true;
    if (set.intensityMode === 'solo_rm') return true;
    if (set.restPauses?.length > 0 && set.restPauses.some((rp: any) => (rp.reps ?? 0) > 0)) return true;
    if (set.dropSets?.length > 0 && set.dropSets.some((ds: any) => (ds.reps ?? 0) > 0)) return true;
    if (set.isAmrap) return true;
    return rpe >= 6;
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
        const primaryMuscle = info?.involvedMuscles?.find(m => m.role === 'primary')?.muscle
            || inferInvolvedMuscles(ex.exerciseName ?? '', '', 'Otro', 'upper')[0]?.muscle
            || 'Core';
        
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            // Ignorar series de calentamiento en el cálculo de estrés
            if (s.type === 'warmup') return;
            
            accumulatedSets += 1;
            
            // Calculamos el drenaje individual (asumimos 90s de descanso como fallback)
            const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, 90);
            
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
        const primaryMuscle = info?.involvedMuscles?.find(m => m.role === 'primary')?.muscle
            || inferInvolvedMuscles(ex.exerciseName ?? '', '', 'Otro', 'upper')[0]?.muscle
            || 'Core';
        let accumulatedSets = muscleVolumeMap[primaryMuscle] || 0;

        ex.sets.forEach((s: any) => {
            if (s.type === 'warmup') return;
            accumulatedSets += 1;
            const drain = calculateSetBatteryDrain(s, info, tanks, accumulatedSets, 90);
            totalCns += drain.cnsDrainPct;
            totalMuscular += drain.muscularDrainPct;
            totalSpinal += drain.spinalDrainPct;
        });
        muscleVolumeMap[primaryMuscle] = accumulatedSets;
    });

    const totalStress = totalCns + totalMuscular + totalSpinal;
    return { totalStress, cnsDrain: totalCns, muscularDrain: totalMuscular, spinalDrain: totalSpinal };
};