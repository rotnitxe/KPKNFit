// services/fatigueService.ts
import { ExerciseSet, Session, ExerciseMuscleInfo, CompletedExercise, CompletedSet, Exercise, OngoingSetData } from '../types';

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

/**
 * CÁLCULO DE CARGA AXIAL Y ESTRÉS ESPINAL (SSC)
 * Basado en la fórmula: (Carga en kg * Reps) * SSC * Factor Postura
 */
export const calculateSpinalScore = (set: any, info: ExerciseMuscleInfo | undefined): number => {
    const { ssc } = getDynamicAugeMetrics(info, set.exerciseName);
    if (ssc === 0) return 0;
    
    const weight = set.weight || set.completedWeight || 0;
    const reps = set.completedReps || set.targetReps || set.reps || 0;
    const postureFactor = info?.postureFactor || 1.0;
    
    let spinalScore = 0;
    if (weight === 0) {
        // Predicción para el editor donde aún no hay peso (basado en RPE)
        const rpe = set.targetRPE || 8;
        spinalScore = reps * ssc * 10 * (rpe / 8) * postureFactor;
    } else {
        spinalScore = (weight * reps) * ssc * postureFactor;
    }
    
    // Añadir tonelaje axial de Dropsets
    if (set.dropSets) {
        set.dropSets.forEach((ds: any) => {
            spinalScore += ((ds.weight || weight) * (ds.reps || 0)) * ssc * postureFactor;
        });
    }
    
    // Regla de Fatiga Técnica Exponencial: A mayor RPE (Fallo, Dropsets), la degradación técnica en columna es exponencial
    const rpe = getEffectiveRPE(set);
    if (rpe >= 10 && ssc >= 1.2) {
        // Multiplicador base de 1.5x. Si el RPE es 13 (Técnicas de intensidad), el daño escala exponencialmente.
        spinalScore *= 1.5 * Math.pow(rpe / 10, 2);
    }
    
    return spinalScore;
};

const getEffectiveRPE = (set: any): number => {
    let baseRpe = 7; // Valor por defecto

    // 1. Traductor Universal RIR a RPE
    if (set.completedRPE !== undefined) baseRpe = set.completedRPE;
    else if (set.targetRPE !== undefined) baseRpe = set.targetRPE;
    else if (set.completedRIR !== undefined) baseRpe = 10 - set.completedRIR;
    else if (set.targetRIR !== undefined) baseRpe = 10 - set.targetRIR;

    // 2. Override por Fallo (Base RPE 11)
    // El fallo real implica mayor reclutamiento y daño que RIR 0 (RPE 10)
    if (set.isFailure || set.performanceMode === 'failure' || set.intensityMode === 'failure' || set.isAmrap) {
        baseRpe = Math.max(baseRpe, 11);
    }

    // 3. Incremento Exponencial por Técnicas Avanzadas (Más allá del fallo)
    let techniqueBonus = 0;
    
    if (set.dropSets && set.dropSets.length > 0) {
        techniqueBonus += set.dropSets.length * 1.5; // Cada DropSet añade fatiga extrema
    }
    if (set.restPauses && set.restPauses.length > 0) {
        techniqueBonus += set.restPauses.length * 1.0; 
    }
    if (set.partialReps && set.partialReps > 0) {
        techniqueBonus += 0.5; // Ir a parciales post-fallo
    }

    // Si se usaron técnicas, el usuario al menos llegó al límite
    if (techniqueBonus > 0 && baseRpe < 10) {
        baseRpe = 10; 
    }

    return baseRpe + techniqueBonus; // Un DropSet al fallo puede devolver un RPE de 12.5 o más
};

/**
/**
 * SISTEMA AUGE: Cálculo de Drenaje Muscular Local (D_musc)
 * Fórmula: Sets * (Reps^0.65) * (RPE/10) * EFC_local * K_rol
 */
export const calculateSetStress = (
    set: any, 
    info: ExerciseMuscleInfo | undefined, 
    restTime: number = 90,
    muscleRole: 'primary' | 'secondary' | 'stabilizer' | 'neutralizer' = 'primary'
): number => {
    const rpe = getEffectiveRPE(set);
    const reps = set.completedReps || set.targetReps || set.reps || 0;
    const efc = getEFC(info); 
    const partialReps = set.partialReps || 0;
    
    let effectiveVolume = reps + (partialReps * 0.5);
    
    // Sumar volumen extra de técnicas de intensidad
    if (set.dropSets) effectiveVolume += set.dropSets.reduce((acc: number, ds: any) => acc + (ds.reps || 0), 0);
    if (set.restPauses) effectiveVolume += set.restPauses.reduce((acc: number, rp: any) => acc + (rp.reps || 0), 0);

    if (effectiveVolume <= 0) return 0;

    // Ajuste no lineal del volumen: Reps^0.65
    const volumeFactor = Math.pow(effectiveVolume, 0.65);
    
    // Multiplicador Exponencial por AUGE (Intensidad Extrema)
    let intensityFactor = rpe / 10;
    if (rpe > 10) {
        // Crecimiento exponencial para esfuerzos que superan el fallo (RPE > 10)
        // Ej: RPE 12.5 -> (1.25)^1.5 = 1.39 -> Se multiplica con el volumen ya inflado por las reps extra
        intensityFactor = Math.pow(rpe / 10, 1.5);
    } else if (set.performanceMode === 'failed') {
        intensityFactor = 1.4; // Penalización estática por fallo técnico/accidental
    }
    
    // --- INTEGRACIÓN: DESCANSO ADAPTATIVO SIMULADO ---
    // AUGE asume que si la intensidad fue extrema, tomaste el descanso compensatorio de la UI
    let effectiveRestTime = restTime;
    if (rpe > 9) {
        let addedRest = (rpe - 9) * 25 * 1.25; // Misma fórmula proporcional de la UI
        effectiveRestTime += Math.min(addedRest, 120); // Cap de +120s
    }

    // --- FACTOR DE DESCANSO AUGE (SUPER-SERIES VS FUERZA) ---
    let restFactor = 1.0;
    if (effectiveRestTime > 0) {
        if (effectiveRestTime <= 30) {
            restFactor = 1.4; // Bi-Series / Super-Series extremas (Estrés altísimo)
        } else if (effectiveRestTime < 60) {
            restFactor = 1.2; // Descanso incompleto tradicional (Metabólico)
        } else if (effectiveRestTime >= 120) {
            // Recuperación de ATP-CP completada mitiga la fatiga central generada
            // 120s = 1.0 | 180s (3m) = ~0.9 | 300s (5m) = 0.8 (Tope máximo de mitigación)
            restFactor = Math.max(0.8, 1.0 - ((effectiveRestTime - 120) * 0.0015)); 
        }
    }

    // K_rol (Coeficiente de Participación Sinérgica)
    let k_rol = 1.0; // Agonista
    if (muscleRole === 'secondary') k_rol = 0.6; // Sinergista Dinámico
    if (muscleRole === 'stabilizer') k_rol = 0.3; // Estabilizador
    if (muscleRole === 'neutralizer') k_rol = 0.15; // Neutralizador

    const rawStress = volumeFactor * intensityFactor * efc * restFactor * k_rol;
    
    return Math.round(rawStress * 10) / 10;
};

/**
 * PASO 3: NORMALIZACIÓN A ESCALA 1-10
 */
export const normalizeToTenScale = (verroScore: number): number => {
    const maxThreshold = 45; // 45 pts Verro = Fatiga crítica (10/10)
    return Math.min(10, Math.max(1, parseFloat(((verroScore / maxThreshold) * 10).toFixed(1))));
};

/**
 * PASO 5: BATERÍA DUAL (SNC VS MÚSCULO)
 */
export const calculatePredictedSessionDrain = (session: Session, exerciseList: ExerciseMuscleInfo[]) => {
    let sncPoints = 0;
    let musclePoints = 0;
    let spinalPoints = 0; // Acumulador de daño biomecánico
    const exercises = session.parts ? session.parts.flatMap(p => p.exercises) : session.exercises;

    exercises?.forEach(ex => {
        const info = exerciseList.find(i => i.id === ex.exerciseDbId || i.name === ex.name);
        const efc = getEFC(info);
        
        ex.sets?.forEach(s => {
            // D_musc: Drenaje Muscular Local (Asumiendo rol primario para la sumatoria global)
            const d_musc = calculateSetStress(s, info, ex.restTime || 90, 'primary');
            musclePoints += d_musc;
            
            // D_SNC: Drenaje Sistémico (Refinado con Intensidad Relativa AUGE)
            const rpe = getEffectiveRPE(s);
            let sncIntensityFactor = 1.0;
            
            // Si tenemos el 1RM calculado del ejercicio, comparamos carga real vs potencial
            if (info?.calculated1RM && s.weight) {
                const intensityPercent = s.weight / info.calculated1RM;
                if (intensityPercent > 0.85) sncIntensityFactor = 1.6; // Cargas pesadas drenan SNC
                else if (intensityPercent > 0.70) sncIntensityFactor = 1.2;
            } else {
                // Fallback por RPE
                if (rpe >= 9) sncIntensityFactor = 1.4;
            }
            
            // Si el ejercicio es multiarticular libre (ej. Sentadilla, EFC alto)
            const structuralFactor = efc > 3.0 ? 1.3 : 0.8; 
            
            const d_snc = d_musc * sncIntensityFactor * structuralFactor;
            sncPoints += d_snc;
            
            // D_spine: Carga Espinal
            spinalPoints += calculateSpinalScore(s, info);
        });
    });

    return {
        cnsDrain: Math.round(Math.min(100, (sncPoints / 280) * 100)), // Umbral 280 pts (PDF)
        muscleBatteryDrain: Math.round(Math.min(100, (musclePoints / 350) * 100)), // Umbral 350 pts (PDF)
        spinalDrain: Math.round(Math.min(100, (spinalPoints / 8000) * 100)), // Umbral 8000 unidades (Zona Roja PDF)
        totalSpinalScore: Math.round(spinalPoints) // Dato bruto para visualización de UI
    };
};

export const calculateExerciseFatigueScale = (exercise: any, info: ExerciseMuscleInfo | undefined): number => {
    if (!exercise.sets?.length) return 0;
    const maxSetStress = Math.max(...exercise.sets.map((s: any) => calculateSetStress(s, info, exercise.restTime || 90)));
    return normalizeToTenScale(maxSetStress);
};

export const calculateCompletedSessionStress = (completedExercises: CompletedExercise[], exerciseList: ExerciseMuscleInfo[]): number => {
    return completedExercises.reduce((total, ex) => {
        const info = exerciseList.find(e => e.id === ex.exerciseDbId || e.name === ex.exerciseName);
        return total + ex.sets.reduce((sTotal, s) => sTotal + calculateSetStress(s, info, 90), 0);
    }, 0);
};

export const isSetEffective = (set: any): boolean => {
    const rpe = getEffectiveRPE(set);
    return rpe >= 6;
};