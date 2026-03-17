// apps/mobile/src/services/volumeCalculator.ts
// Motor de Cálculo de Volumen KPKN — Ported from PWA
import type { Mesocycle, Session, Program, ExerciseCatalogEntry } from '../types/workout';
import type { AthleteProfileScore, VolumeRecommendation, PostSessionFeedback, MuscleVolumeThresholds } from '../types/volume';
import { buildExerciseIndex, findExerciseWithFallback } from '../utils/exerciseIndex';
import { getMuscleDisplayId, normalizeCanonicalMuscle } from '../utils/canonicalMuscles';
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';

const PHASE_FACTORS: Record<string, number> = {
    'Acumulación': 1.0,
    'Intensificación': 0.75,
    'Realización': 0.50,
    'Descarga': 0.40,
    'Custom': 1.0
};

const INTENSITY_FACTORS: Record<string, number> = {
    'Failure': 0.6,
    'RIR_High': 1.0,
    'RIR_Low': 1.2
};

export const calculateWeeklyVolume = (
    athleteScore: AthleteProfileScore | null | undefined,
    settings: any,
    phase: Mesocycle['goal'] = 'Acumulación'
): VolumeRecommendation => {
    if (!athleteScore) {
        return {
            minSets: 10, maxSets: 20, optimalSets: 15, type: 'sets',
            reasoning: "Perfil no calibrado. Usando estándar genérico (10-20 series)."
        };
    }

    const { profileLevel } = athleteScore;
    const fFase = PHASE_FACTORS[phase] || 1.0;
    const intensityPref = settings?.preferredIntensity || 'RIR_High';
    const fInt = INTENSITY_FACTORS[intensityPref] || 1.0;

    let baseMin = profileLevel === 'Advanced' ? 14 : 10;
    let baseMax = profileLevel === 'Advanced' ? 22 : 14;

    const calcMin = baseMin * fFase * fInt;
    const calcMax = baseMax * fFase * fInt;

    const finalMin = Math.max(1, Math.round(calcMin));
    const finalMax = Math.max(finalMin + 2, Math.round(calcMax));

    return {
        minSets: finalMin,
        maxSets: finalMax,
        optimalSets: Math.round((finalMin + finalMax) / 2),
        type: 'sets',
        reasoning: `Motor Hipertrofia: ${profileLevel} * Fase ${phase} * Intensidad ${intensityPref}.`
    };
};

export const validateSessionVolume = (
    setsInSession: number,
    muscleGroup: string,
    options?: { deficitRegime?: boolean }
) => {
    const maxSets = options?.deficitRegime ? 10 : 12;
    if (setsInSession > maxSets) {
        return { isValid: false, message: `⚠️ Volumen excesivo (${setsInSession}/${maxSets}) para ${muscleGroup}.` };
    }
    return { isValid: true };
};

export const calculateVolumeAdjustment = (
    muscle: string,
    feedbackHistory: PostSessionFeedback[]
) => {
    if (!feedbackHistory?.length) return { factor: 1.0, status: 'optimal', suggestion: '' };
    // Simplified logic for mobile port
    return { factor: 1.0, status: 'optimal', suggestion: `✅ Carga óptima para ${muscle}.` };
};
// ... Additional exported functions from PWA would go here ...
