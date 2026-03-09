// services/volumeCalculator.ts
import { AthleteProfileScore, Settings, Mesocycle, Session, ExerciseMuscleInfo, MuscleRole, WorkoutLog, Program } from '../types';
import type { VolumeRecommendation as ProgramVolumeRec } from '../types';
import { buildExerciseIndex, findExercise } from '../utils/exerciseIndex';

// === CONSTANTES DEL INFORME (Módulos 4 y 5) ===
const PHASE_FACTORS: Record<string, number> = {
    'Acumulación': 1.0,
    'Intensificación': 0.75, // Transformación
    'Realización': 0.50,     // Peaking
    'Descarga': 0.40,
    'Custom': 1.0
};

const INTENSITY_FACTORS = {
    'Failure': 0.6,    // RPE 10 (Entrenamiento al fallo reduce volumen drásticamente)
    'RIR_High': 1.0,   // RPE 8-9 (Zona Óptima - Estándar)
    'RIR_Low': 1.2     // RPE 6-7 (Zona Moderada - Permite más volumen)
};

// === INTERFACES DE SALIDA ===
export interface VolumeRecommendation {
    minSets: number;
    maxSets: number;
    optimalSets: number;
    type: 'sets' | 'lifts'; // 'sets' para Hipertrofia, 'lifts' para Powerlifting (NL)
    reasoning: string;
}

/**
 * MÓDULO 5: EL MEGA-ALGORITMO DE CÁLCULO
 * Calcula el volumen semanal recomendado por grupo muscular o movimiento.
 */
export const calculateWeeklyVolume = (
    athleteScore: AthleteProfileScore | undefined | null,
    settings: Settings,
    phase: Mesocycle['goal'] = 'Acumulación'
): VolumeRecommendation => {

    // 1. SAFEGUARD: Si no hay perfil, devolvemos el estándar genérico
    if (!athleteScore) {
        return {
            minSets: 10,
            maxSets: 20,
            optimalSets: 15,
            type: 'sets',
            reasoning: "Perfil no calibrado. Usando estándar genérico de Schoenfeld (10-20 series)."
        };
    }

    const { totalScore, profileLevel } = athleteScore;
    const trainingProfile = settings.trainingProfile || 'Aesthetics';

    // === MOTOR B: POWERLIFTING (Módulo 2.2 - Basado en NL/Mes) ===
    if (trainingProfile === 'Powerlifting') {
        // Lógica Sheiko: NL Mensual -> Semanal
        // Intermedio (Score < 15): 1000-1300 NL/mes -> ~250-325 NL/semana
        // Avanzado (Score >= 15): 1300-2500 NL/mes -> ~325-625 NL/semana

        const isAdvanced = profileLevel === 'Advanced';
        const minMonthlyNL = isAdvanced ? 1300 : 1000;
        const maxMonthlyNL = isAdvanced ? 2500 : 1300;

        // Ajuste por Fase (En Peaking el volumen baja drásticamente)
        const phaseFactor = PHASE_FACTORS[phase] || 1.0;

        const weeklyMin = Math.round((minMonthlyNL / 4) * phaseFactor);
        const weeklyMax = Math.round((maxMonthlyNL / 4) * phaseFactor);

        return {
            minSets: weeklyMin,
            maxSets: weeklyMax,
            optimalSets: Math.round((weeklyMin + weeklyMax) / 2),
            type: 'lifts', // Number of Lifts (NL)
            reasoning: `Motor Powerlifting (Sheiko): Perfil ${profileLevel}. Fase ${phase} (${phaseFactor}x).`
        };
    }

    // === MOTOR A: HIPERTROFIA / ESTÉTICA (Módulo 2.1 y 5) ===

    // PASO 1: Determinar Capacidad Base (CB)
    // Informe: Score < 15 -> 10-14 series | Score > 15 -> 14-22 series
    let baseMin = 10;
    let baseMax = 14;

    if (profileLevel === 'Advanced') {
        baseMin = 14;
        baseMax = 22;
    }

    // PASO 2: Factor de Fase (F_Fase)
    const fFase = PHASE_FACTORS[phase] || 1.0;

    // PASO 3: Factor de Intensidad del Usuario (F_Int)
    // Si el usuario prefiere "Failure" (RPE 10), reducimos volumen.
    // Si no está definido, asumimos RIR_High (1.0)
    const intensityPref = settings.preferredIntensity || 'RIR_High';
    const fInt = INTENSITY_FACTORS[intensityPref];

    // PASO 4: Cálculo Preliminar
    // V_Rec = CB * F_Fase * F_Int
    const calcMin = baseMin * fFase * fInt;
    const calcMax = baseMax * fFase * fInt;

    // Redondeo para UX limpia
    const finalMin = Math.max(1, Math.round(calcMin));
    const finalMax = Math.max(finalMin + 2, Math.round(calcMax));
    const optimal = Math.round((finalMin + finalMax) / 2);

    return {
        minSets: finalMin,
        maxSets: finalMax,
        optimalSets: optimal,
        type: 'sets',
        reasoning: `Motor Hipertrofia: Base ${profileLevel} (${baseMin}-${baseMax}) * Fase ${phase} (${fFase}x) * Intensidad ${intensityPref} (${fInt}x).`
    };
};

/**
 * MÓDULO 4.2: VALIDACIÓN DE FRECUENCIA (Session Caps)
 * Verifica si el volumen asignado a una sesión excede el límite productivo.
 * En régimen de déficit: límites reducidos (~80%) para proteger masa muscular.
 */
export const validateSessionVolume = (
    setsInSession: number,
    muscleGroup: string,
    options?: { deficitRegime?: boolean }
): { isValid: boolean; message?: string } => {
    const MAX_PRODUCTIVE_SESSION_SETS = 12;
    const WARNING_THRESHOLD = 10;
    const deficitFactor = options?.deficitRegime ? 0.8 : 1;
    const maxSets = Math.round(MAX_PRODUCTIVE_SESSION_SETS * deficitFactor);
    const warnSets = Math.round(WARNING_THRESHOLD * deficitFactor);

    if (setsInSession > maxSets) {
        return {
            isValid: false,
            message: options?.deficitRegime
                ? `⚠️ En déficit: ${setsInSession} series de ${muscleGroup} excede el límite recomendado (${maxSets}). Reduce volumen para proteger tu masa muscular.`
                : `⚠️ Volumen Basura: ${setsInSession} series de ${muscleGroup} en una sesión excede el límite productivo (${maxSets}). Considera dividir en 2 días.`
        };
    }

    if (setsInSession >= warnSets) {
        return {
            isValid: true,
            message: options?.deficitRegime
                ? `ℹ️ En déficit: cerca del límite (${setsInSession}/${maxSets}). Prioriza recuperación.`
                : `ℹ️ Estás cerca del límite por sesión (${setsInSession}/${maxSets}). Asegúrate de nutrirte bien peri-entrenamiento.`
        };
    }

    return { isValid: true };
};

/**
 * MÓDULO 3: CÁLCULO DE VOLUMEN FRACCIONAL (Superposición)
 * Suma el volumen real considerando el impacto indirecto.
 */
export const calculateFractionalVolume = (
    exercises: { muscleRole: 'primary' | 'secondary'; sets: number }[]
): number => {
    let totalFractionalSets = 0;

    exercises.forEach(ex => {
        if (ex.muscleRole === 'primary') {
            totalFractionalSets += ex.sets * 1.0; // Agonista
        } else {
            totalFractionalSets += ex.sets * 0.5; // Sinergista (Módulo 3)
        }
    });

    return totalFractionalSets;
};

// --- MÓDULO 6: FEEDBACK LOOP & AUTO-REGULACIÓN ---

import { PostSessionFeedback } from '../types';

/**
 * Analiza el historial reciente de feedback (últimas 2-4 semanas) para un músculo
 * y sugiere ajustes de volumen.
 */
export const calculateVolumeAdjustment = (
    muscle: string,
    feedbackHistory: PostSessionFeedback[]
): { factor: number; suggestion: string; status: 'recovery_debt' | 'optimal' | 'undertraining' } => {

    if (!feedbackHistory || feedbackHistory.length === 0) {
        return { factor: 1.0, suggestion: '', status: 'optimal' };
    }

    // 1. Filtrar feedback relevante para este músculo
    const muscleLogs = feedbackHistory.filter(log => log.feedback && log.feedback[muscle]);

    if (muscleLogs.length === 0) {
        return { factor: 1.0, suggestion: '', status: 'optimal' };
    }

    // 2. Calcular promedios recientes (últimas 3 sesiones disponibles)
    const recentLogs = muscleLogs.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()).slice(0, 3);

    let totalDoms = 0;
    let totalStrength = 0;

    recentLogs.forEach(log => {
        const data = log.feedback[muscle];
        totalDoms += data.doms; // 1 (Nada) a 5 (Extremo)
        totalStrength += data.strengthCapacity; // 1 (Débil) a 10 (Fuerte)
    });

    const avgDoms = totalDoms / recentLogs.length;
    const avgStrength = totalStrength / recentLogs.length;

    // 3. Reglas de Ajuste (KPKN Algorithm Módulo 6)

    // CASO A: Sobrecarga / Daño excesivo (DOMS > 3.5 o Fuerza < 5)
    if (avgDoms >= 3.5 || avgStrength <= 5) {
        return {
            factor: 0.85, // Reducir 15%
            status: 'recovery_debt',
            suggestion: `⚠️ Recuperación lenta en ${muscle} (DOMS altos). Sugerimos reducir volumen un 15% esta semana.`
        };
    }

    // CASO B: Estancamiento / Sub-entrenamiento (DOMS < 1.5 y Fuerza alta constante)
    // "Si no duele y estás fuerte, puedes empujar más"
    if (avgDoms <= 1.5 && avgStrength >= 8) {
        return {
            factor: 1.1, // Aumentar 10%
            status: 'undertraining',
            suggestion: `🚀 ${muscle} recupera sobrado. Podrías tolerar un +10% de volumen o intensidad.`
        };
    }

    // CASO C: Zona Óptima (Sweet Spot)
    return {
        factor: 1.0,
        status: 'optimal',
        suggestion: `✅ Carga óptima para ${muscle}. Mantén el plan.`
    };
};

// --- MÓDULO AUXILIAR: NORMALIZACIÓN DE MÚSCULOS ---

/**
 * Convierte nombres anatómicos en Grupos Funcionales para Bodybuilding/Estética.
 * - Hombros: Separados por cabezas (Anterior, Lateral, Posterior).
 * - Espalda: Separada en Amplitud (Dorsal) y Densidad (Trapecio/Espalda Alta).
 * - Brazos: Estrictamente separados.
 */
/**
 * Convierte nombres anatómicos en los 15 Grupos Canónicos de KPKN.
 * Módulo de Sincronización de Datos (Crucial para Analytics y Body Map).
 */
export const normalizeMuscleGroup = (specificMuscle: string): string => {
    const lower = specificMuscle.toLowerCase().trim()
        .normalize('NFD').replace(/[\u0300-\u036f]/g, ''); // Quitar acentos

    if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectorales';
    if (lower.includes('dorsal') || lower.includes('lat') || lower.includes('redondo mayor') || lower.includes('espalda ancha') || (lower.includes('espalda') && !lower.includes('baja') && !lower.includes('lumbar'))) return 'Dorsales';
    if (lower.includes('trapecio') || lower.includes('romboides')) return 'Trapecio';
    if (lower.includes('deltoide') || lower.includes('hombro') || lower.includes('manguito rotador') || lower.includes('supraespinoso')) return 'Deltoides';
    if (lower.includes('tricep')) return 'Tríceps';
    if ((lower.includes('bicep') || lower.includes('braquial') || lower.includes('coracobraquial')) && !lower.includes('femoral') && !lower.includes('pierna')) return 'Bíceps';
    if (lower.includes('antebrazo') || lower.includes('braquiorradial') || lower.includes('pronador') || lower.includes('supinador')) return 'Antebrazo';
    if (lower.includes('cuadricep') || lower.includes('vasto') || lower.includes('recto femoral')) return 'Cuádriceps';
    if (lower.includes('isquio') || lower.includes('femoral') || lower.includes('semitendinoso') || lower.includes('semimembranoso')) return 'Isquiosurales';
    if (lower.includes('gluteo') || lower.includes('tensor de la fascia lata') || lower.includes('piriforme')) return 'Glúteos';
    if (lower.includes('aductor') || lower.includes('pectineo') || lower.includes('gracil')) return 'Aductores';
    if (lower.includes('gemelo') || lower.includes('soleo') || lower.includes('pantorrilla') || lower.includes('gastrocnemio') || lower.includes('tibial')) return 'Pantorrillas';
    if (lower.includes('erector') || lower.includes('multificos') || lower.includes('cuadrado lumbar') || lower.includes('espinal') || lower.includes('lumbar') || lower.includes('baja')) return 'Erectores Espinales';
    if (lower.includes('abdomen') || lower.includes('abdominal') || lower.includes('oblicuo') || lower.includes('recto abdominal') || lower.includes('transverso')) return 'Abdomen';
    if (lower.includes('core')) return 'Core';

    return 'Core'; // Por defecto, si es un músculo del tronco, a Core.
};

// --- LA NUEVA CALCULADORA MAESTRA DE VOLUMEN UNIFICADO ---
// Multiplicadores importados desde AUGE (Single Source of Truth)
import {
    HYPERTROPHY_ROLE_MULTIPLIERS as MUSCLE_ROLE_MULTIPLIERS,
    FATIGUE_ROLE_MULTIPLIERS,
} from './auge';

export { MUSCLE_ROLE_MULTIPLIERS, FATIGUE_ROLE_MULTIPLIERS };

// 2. Motor único: Cuenta las series reales basándose en el rol del músculo

export const calculateUnifiedMuscleVolume = (
    sessions: Session[],
    exerciseList: ExerciseMuscleInfo[]
) => {
    const volumeMap: Record<string, number> = {};
    const exIndex = buildExerciseIndex(exerciseList);

    sessions.forEach(session => {
        if (!session) return;

        const allExercises = session.parts && session.parts.length > 0
            ? session.parts.flatMap(p => p.exercises || [])
            : (session.exercises || []);

        allExercises.forEach(exercise => {
            if (!exercise || !exercise.sets) return;

            const validSetsCount = exercise.sets.filter(set =>
                set && !set.isIneffective && ((set.completedReps ?? set.targetReps ?? 0) > 0 || (set.weight ?? 0) > 0)
            ).length;

            if (validSetsCount > 0) {
                const dbInfo = findExercise(exIndex, exercise.exerciseDbId, exercise.name);

                // Si el ejercicio tiene músculos definidos en la BD o guardados en la propia sesión histórica
                const involvedMuscles = dbInfo?.involvedMuscles || (exercise as any).targetMuscles || [];

                if (involvedMuscles.length > 0) {
                    // MAPA DE BLINDAJE: Solo un multiplicador por músculo específico
                    const uniqueMuscleMultipliers = new Map<string, number>();

                    involvedMuscles.forEach((m: any) => {
                        if (!m || !m.muscle) return;
                        const muscleName = normalizeMuscleGroup(m.muscle); // Usamos tu propio normalizador
                        const multiplier = MUSCLE_ROLE_MULTIPLIERS[m.role as MuscleRole] || 0.5;

                        const currentMax = uniqueMuscleMultipliers.get(muscleName) || 0;
                        if (multiplier > currentMax) {
                            uniqueMuscleMultipliers.set(muscleName, multiplier);
                        }
                    });

                    // Ahora aplicamos matemáticamente el máximo registrado
                    uniqueMuscleMultipliers.forEach((maxMultiplier, muscleName) => {
                        if (!volumeMap[muscleName]) {
                            volumeMap[muscleName] = 0;
                        }
                        volumeMap[muscleName] += validSetsCount * maxMultiplier;
                    });
                }
            }
        });
    });

    return Object.entries(volumeMap)
        .filter(([muscleGroup]) => muscleGroup !== 'General') // Solo músculos reales, no "General"
        .map(([muscleGroup, volume]) => ({
            muscleGroup,
            displayVolume: Math.round(volume * 10) / 10
        }))
        .sort((a, b) => b.displayVolume - a.displayVolume);
};

// --- MÓDULO: UMBRALES DE VOLUMEN POR MÚSCULO (Analytics) ---
import { INITIAL_MUSCLE_GROUP_DATA } from '../data/initialMuscleGroupDatabase';

const MUSCLE_AGGREGATION_MAP: Record<string, string[]> = {
    'Cuádriceps': ['cuadriceps', 'vasto-lateral', 'vasto-medial', 'recto-femoral'],
    'Isquiosurales': ['isquiosurales', 'biceps-femoral', 'semitendinoso', 'semimembranoso', 'isquio', 'femoral'],
    'Glúteos': ['gluteos', 'gluteo-mayor', 'gluteo-medio', 'gluteo-menor', 'tensor-de-la-fascia-lata', 'piriforme'],
    'Pectorales': ['pectoral', 'pecho', 'pectoral-superior', 'pectoral-medio', 'pectoral-inferior'],
    'Bíceps': ['biceps', 'cabeza-larga-biceps', 'cabeza-corta-biceps', 'braquial', 'coracobraquial'],
    'Tríceps': ['triceps', 'cabeza-larga-triceps', 'cabeza-lateral-triceps', 'cabeza-medial-triceps'],
    'Dorsales': ['espalda', 'dorsal-ancho', 'redondo-mayor', 'lat'],
    'Trapecio': ['trapecio', 'trapecio-superior', 'trapecio-medio', 'trapecio-inferior', 'romboides'],
    'Erectores Espinales': ['erectores-espinales', 'multifidos', 'cuadrado-lumbar', 'espalda-baja', 'lumbar', 'espinal'],
    'Abdomen': ['abdomen', 'abdominal', 'recto-abdominal', 'oblicuos', 'transverso', 'transverso-abdominal'],
    'Core': ['core'],
    'Pantorrillas': ['pantorrillas', 'gastrocnemio', 'soleo', 'gemelos', 'tibial', 'gemelo'],
    'Deltoides': ['deltoides', 'deltoides-anterior', 'deltoides-lateral', 'deltoides-posterior', 'hombros', 'hombro', 'supraespinoso', 'manguito-rotador'],
    'Aductores': ['aductores', 'pectineo', 'gracil', 'aductor'],
    'Antebrazo': ['antebrazo', 'braquiorradial', 'flexores-muneca', 'pronador', 'supinador'],
};

// No necesitamos STANDALONE_DISPLAY_NAMES si usamos el canon estrictamente
const STANDALONE_DISPLAY_NAMES: Record<string, string> = {};

function parseVolumeString(s: string): { min: number; max: number } {
    if (!s || s === 'N/A') return { min: 0, max: 20 };
    const trimmed = s.trim();
    if (trimmed.includes('-')) {
        const [a, b] = trimmed.split('-').map(x => parseInt(x.trim(), 10) || 0);
        return { min: a, max: Math.max(a, b || a) };
    }
    const n = parseInt(trimmed, 10) || 0;
    return { min: n, max: n };
}

export interface MuscleVolumeThresholds {
    min: number;
    optimal: number;
    max: number;
    source: 'program' | 'israetel' | 'kpnk';
    rangeLabel: string;
}

/**
 * Obtiene umbrales de volumen para un músculo (display name).
 * Prioridad: 1) program.volumeRecommendations, 2) athleteScore + calculateWeeklyVolume, 3) Israetel (initialMuscleGroupDatabase).
 */
export const getVolumeThresholdsForMuscle = (
    muscleDisplayName: string,
    options?: {
        program?: Program | null;
        settings?: Settings | null;
        athleteScore?: AthleteProfileScore | null;
        phase?: Mesocycle['goal'];
    }
): MuscleVolumeThresholds => {
    const { program, settings, athleteScore, phase = 'Acumulación' } = options || {};

    const normalizeForMatch = (s: string) => s.toLowerCase().trim().replace(/\s+/g, ' ').normalize('NFD').replace(/[\u0300-\u036f]/g, '');

    // 1. Program.volumeRecommendations (calibración del programa)
    if (program?.volumeRecommendations?.length) {
        const displayNorm = normalizeForMatch(muscleDisplayName);
        const rec = program.volumeRecommendations.find((r: ProgramVolumeRec) =>
            normalizeForMatch(r.muscleGroup) === displayNorm ||
            normalizeForMatch(r.muscleGroup).includes(displayNorm) ||
            displayNorm.includes(normalizeForMatch(r.muscleGroup))
        );
        if (rec) {
            const optimal = Math.round((rec.minEffectiveVolume + rec.maxAdaptiveVolume) / 2);
            return {
                min: rec.minEffectiveVolume,
                optimal,
                max: rec.maxRecoverableVolume,
                source: 'program',
                rangeLabel: `${rec.minEffectiveVolume}–${rec.maxRecoverableVolume}`,
            };
        }
    }

    // 2. Israetel/Schoenfeld desde initialMuscleGroupDatabase (fallback)
    const subIds = STANDALONE_DISPLAY_NAMES[muscleDisplayName]
        ? [STANDALONE_DISPLAY_NAMES[muscleDisplayName]]
        : MUSCLE_AGGREGATION_MAP[muscleDisplayName]
            ? MUSCLE_AGGREGATION_MAP[muscleDisplayName]
            : [muscleDisplayName.toLowerCase().replace(/\s+/g, '-').replace(/í/g, 'i').replace(/á/g, 'a')];

    let bestMin = 0;
    let bestOptimal = 12;
    let bestMax = 20;

    for (const id of subIds) {
        const info = INITIAL_MUSCLE_GROUP_DATA.find(m =>
            m.id === id || normalizeForMatch(m.name) === normalizeForMatch(id));
        if (info?.volumeRecommendations) {
            const { mev, mav, mrv } = info.volumeRecommendations;
            const mevParsed = parseVolumeString(mev);
            const mavParsed = parseVolumeString(mav);
            const mrvParsed = parseVolumeString(mrv);
            const min = mevParsed.max || mevParsed.min;
            const optimal = Math.round((mavParsed.min + mavParsed.max) / 2) || mavParsed.min;
            const max = mrvParsed.max || mrvParsed.min;
            if (max > bestMax) {
                bestMin = min;
                bestOptimal = optimal;
                bestMax = max;
            }
        }
    }

    // Si no encontramos en la DB, usar valores por defecto
    if (bestMax === 20 && bestMin === 0) {
        const displayNorm = normalizeForMatch(muscleDisplayName);
        const info = INITIAL_MUSCLE_GROUP_DATA.find(m =>
            normalizeForMatch(m.name) === displayNorm ||
            normalizeForMatch(m.name).includes(displayNorm));
        if (info?.volumeRecommendations) {
            const { mev, mav, mrv } = info.volumeRecommendations;
            const mevParsed = parseVolumeString(mev);
            const mavParsed = parseVolumeString(mav);
            const mrvParsed = parseVolumeString(mrv);
            bestMin = mevParsed.max || mevParsed.min;
            bestOptimal = Math.round((mavParsed.min + mavParsed.max) / 2) || mavParsed.min;
            bestMax = mrvParsed.max || mrvParsed.min;
        }
    }

    // Escalar por athleteScore (KPKN) si está calibrado y el usuario eligió KPKN
    let finalMin = bestMin;
    let finalOptimal = bestOptimal;
    let finalMax = Math.max(bestMax, bestMin + 2);
    let source: 'program' | 'israetel' | 'kpnk' = 'israetel';
    const useKpnk = settings?.volumeSystem !== 'israetel' && !!athleteScore;

    if (athleteScore && settings && useKpnk) {
        const base = calculateWeeklyVolume(athleteScore, settings, phase || 'Acumulación');
        const scale = base.optimalSets / 15;
        finalMin = Math.max(1, Math.round(bestMin * scale));
        finalOptimal = Math.round(bestOptimal * scale);
        finalMax = Math.max(finalMin + 2, Math.round(bestMax * scale));
        source = 'kpnk';
    }

    return {
        min: finalMin,
        optimal: finalOptimal,
        max: finalMax,
        source,
        rangeLabel: `${finalMin}–${finalMax}`,
    };
};

/** Lista de grupos musculares para volumen (display) - Estrictamente Canónicos */
export const VOLUME_DISPLAY_MUSCLES = [
    'Pectorales', 'Dorsales', 'Trapecio', 'Deltoides', 'Tríceps', 'Bíceps',
    'Antebrazo', 'Abdomen', 'Cuádriceps', 'Isquiosurales', 'Glúteos',
    'Aductores', 'Pantorrillas', 'Core', 'Erectores Espinales',
] as const;

/**
 * Obtiene recomendaciones de volumen Israetel para todos los grupos musculares.
 * Usa INITIAL_MUSCLE_GROUP_DATA (MEV, MAV, MRV).
 */
export const getIsraetelVolumeRecommendations = (): ProgramVolumeRec[] => {
    return VOLUME_DISPLAY_MUSCLES.map(muscle => {
        const th = getVolumeThresholdsForMuscle(muscle, { phase: 'Acumulación' });
        return {
            muscleGroup: muscle,
            minEffectiveVolume: th.min,
            maxAdaptiveVolume: th.optimal,
            maxRecoverableVolume: th.max,
            frequencyCap: 4,
        };
    });
};

/**
 * Calcula recomendaciones KPKN personalizadas por músculo.
 * Escala los valores Israetel según el perfil del atleta.
 */
export const getKpnkVolumeRecommendations = (
    athleteScore: AthleteProfileScore,
    settings: Settings,
    phase: Mesocycle['goal'] = 'Acumulación'
): ProgramVolumeRec[] => {
    const base = calculateWeeklyVolume(athleteScore, settings, phase);
    const scale = base.optimalSets / 15;
    return getIsraetelVolumeRecommendations().map(rec => {
        const newMin = Math.max(1, Math.round(rec.minEffectiveVolume * scale));
        const newOpt = Math.round(rec.maxAdaptiveVolume * scale);
        const newMax = Math.round(rec.maxRecoverableVolume * scale);
        return {
            ...rec,
            minEffectiveVolume: newMin,
            maxAdaptiveVolume: Math.max(newOpt, newMin),
            maxRecoverableVolume: Math.max(newMax, newMin + 2),
        };
    });
};

/**
 * Convierte volumeRecommendations del programa en volumeLimits para SessionEditor.
 * maxSession = min(10, MRV/frequencyCap) o 6; max = maxRecoverableVolume; min = minEffectiveVolume.
 */
export function volumeRecommendationsToLimits(
    recs: { muscleGroup: string; minEffectiveVolume: number; maxAdaptiveVolume: number; maxRecoverableVolume: number; frequencyCap?: number }[] | undefined
): Record<string, { maxSession: number; max: number; min?: number }> {
    if (!recs?.length) return {};
    const result: Record<string, { maxSession: number; max: number; min?: number }> = {};
    for (const r of recs) {
        const freq = r.frequencyCap ?? 4;
        const maxSession = Math.min(10, Math.max(4, Math.ceil((r.maxRecoverableVolume ?? 18) / Math.max(1, freq))));
        result[r.muscleGroup] = {
            maxSession,
            max: r.maxRecoverableVolume,
            min: r.minEffectiveVolume,
        };
    }
    return result;
}

/** Convierte WorkoutLog[] a sesiones virtuales para calcular volumen desde historial completado */
export const calculateUnifiedMuscleVolumeFromLogs = (
    logs: WorkoutLog[],
    exerciseList: ExerciseMuscleInfo[]
): { muscleGroup: string; displayVolume: number }[] => {
    const virtualSessions: Session[] = (logs || []).map(log => ({
        id: log.id || '',
        name: log.sessionName || '',
        exercises: (log.completedExercises || []).map((ex: any) => ({
            id: ex.exerciseId || ex.id || '',
            name: ex.exerciseName || ex.name || '',
            exerciseDbId: ex.exerciseDbId,
            sets: ex.sets || [],
            targetMuscles: ex.targetMuscles,
        })),
    }));
    return calculateUnifiedMuscleVolume(virtualSessions, exerciseList);
};