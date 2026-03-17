
import { 
    MuscleRole, 
    AugeExerciseMetrics, 
    AugeAdaptiveCache,
    HYPERTROPHY_ROLE_MULTIPLIERS
} from '@kpkn/shared-types';

/**
 * Capacidad de referencia semanal para normalización de fatiga SNC (puntos).
 */
export const WEEKLY_CNS_FATIGUE_REFERENCE = 4000;

/**
 * --- SISTEMA AUGE v2.0: MOTOR DE MÉTRICAS DINÁMICAS ---
 * Matriz Algorítmica de Componentes Principales (Patrón Base + Modificadores)
 */
export const getDynamicAugeMetrics = (info: any | undefined, customName?: string): AugeExerciseMetrics => {
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
 */
export const calculatePersonalizedBatteryTanks = (settings: any) => {
    let baseMuscular = 300;
    let baseCns = 250;
    let baseSpinal = 4000;

    const level = settings?.athleteScore?.profileLevel || 'Advanced';
    const levelMult = level === 'Beginner' ? 0.8 : 1.2;

    const style = (settings?.athleteScore?.trainingStyle || settings?.athleteType || 'Bodybuilder').toLowerCase();
    let cnsMult = 1.0, muscMult = 1.0, spineMult = 1.0;

    if (style.includes('powerlift')) {
        cnsMult = 1.3; spineMult = 1.4; muscMult = 0.9;
    } else if (style.includes('bodybuild') || style.includes('aesthetics')) {
        cnsMult = 0.9; spineMult = 0.9; muscMult = 1.3;
    } else {
        cnsMult = 1.15; spineMult = 1.15; muscMult = 1.15;
    }

    return {
        muscularTank: baseMuscular * levelMult * muscMult,
        cnsTank: baseCns * levelMult * cnsMult,
        spinalTank: baseSpinal * levelMult * spineMult
    };
};

export const isSetEffective = (set: any): number | boolean => {
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
 * --- KPKN ENGINE: SINGLE SOURCE OF TRUTH (Cálculo de Drenaje por Serie) ---
 */
export const calculateSetBatteryDrain = (
    set: any,
    info: any | undefined,
    tanks: { muscularTank: number, cnsTank: number, spinalTank: number },
    accumulatedSetsForMuscle: number = 0,
    restTime: number = 90
) => {
    if (set?.type === 'warmup' || set?.isIneffective) {
        return { muscularDrainPct: 0, cnsDrainPct: 0, spinalDrainPct: 0 };
    }

    const auge = getDynamicAugeMetrics(info, set.exerciseName || info?.name);
    const rpe = getEffectiveRPE(set);
    const reps = set.completedReps || set.targetReps || set.reps || 10;
    const isCompound = info?.type === 'Básico' || info?.tier === 'T1';

    let repsCnsMult = 1.0, repsMuscMult = 1.0, repsSpineMult = 1.0;
    if (reps <= 4) {
        if (isCompound) {
            repsCnsMult = 1.8; repsSpineMult = 1.6; repsMuscMult = 0.7;
        } else {
            repsCnsMult = 1.2; repsSpineMult = 0.1; repsMuscMult = 0.8;
        }
    } else if (reps >= 16) {
        repsCnsMult = 0.7; repsSpineMult = 0.5; repsMuscMult = 1.4;
    }

    if (reps >= 12) {
        repsCnsMult = reps >= 16 ? 0.5 : 0.65;
        repsSpineMult = reps >= 16 ? 0.45 : 0.6;
        repsMuscMult = reps >= 16 ? 1.4 : 1.2;
    }

    let intensityMult = 1.0;
    if (rpe >= 11) intensityMult = 1.8;
    else if (rpe >= 10) intensityMult = 1.5;
    else if (rpe >= 9) intensityMult = 1.15;
    else if (rpe >= 8) intensityMult = 1.0;
    else if (rpe >= 6) intensityMult = 0.7;
    else intensityMult = 0.4;

    let junkVolumeMult = 1.0;
    if (accumulatedSetsForMuscle >= 6) {
        junkVolumeMult = 1.0 + ((accumulatedSetsForMuscle - 5) * 0.35);
    }

    let restFactor = 1.0;
    if (restTime <= 45) restFactor = 1.3;
    else if (restTime >= 180) restFactor = 0.85;

    let cnsRestFactor = 1.0;
    if (restTime <= 45) cnsRestFactor = 1.15;
    else if (restTime >= 180) cnsRestFactor = 0.92;

    const partialReps = set.partialReps || 0;
    const junkVolumeFromPartials = partialReps > 0 ? 1 + (partialReps * 0.2) : 1;
    const advancedTechniqueNeuralFactor = 1
        + ((set.restPauses?.length || 0) * 0.05)
        + ((set.dropSets?.length || 0) * 0.04)
        + (partialReps > 0 ? 0.03 : 0);
    const compoundNeuralFactor = isCompound ? 1.15 : 0.72;

    const rawMuscular = (auge.efc || 2.5) * repsMuscMult * intensityMult * junkVolumeMult * restFactor * junkVolumeFromPartials * 7.2;
    const rawCns = (auge.cnc || 2.5) * repsCnsMult * intensityMult * cnsRestFactor * compoundNeuralFactor * advancedTechniqueNeuralFactor * 3.4;
    const weightFactor = set.weight ? (set.weight * 0.05) : ((auge.efc || 2.5) * 2.0);
    const rawSpinal = (auge.ssc || 0.1) * repsSpineMult * intensityMult * weightFactor * 4.0;

    return {
        muscularDrainPct: (rawMuscular / tanks.muscularTank) * 100,
        cnsDrainPct: (rawCns / tanks.cnsTank) * 100,
        spinalDrainPct: (rawSpinal / tanks.spinalTank) * 100
    };
};
