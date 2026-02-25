// services/volumeCalculator.ts
import { AthleteProfileScore, Settings, Mesocycle, Session, ExerciseMuscleInfo, MuscleRole, WorkoutLog } from '../types';
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
export const normalizeMuscleGroup = (specificMuscle: string): string => {
    const lower = specificMuscle.toLowerCase().trim();

    // --- 1. HOMBROS (Separación por Cabezas - CRÍTICO PARA ESTÉTICA) ---
    if (lower.includes('posterior') && (lower.includes('deltoides') || lower.includes('hombro'))) {
        return 'Deltoides Posterior';
    }
    if ((lower.includes('lateral') || lower.includes('medio')) && (lower.includes('deltoides') || lower.includes('hombro'))) {
        return 'Deltoides Lateral';
    }
    if ((lower.includes('anterior') || lower.includes('frontal')) && (lower.includes('deltoides') || lower.includes('hombro'))) {
        return 'Deltoides Anterior';
    }
    // Si solo dice "Hombro" o "Deltoides" sin especificar (ej: Press Militar compuesto)
    if (lower.includes('deltoides') || lower.includes('hombro')) {
        return 'Deltoides Anterior'; // Asumimos Anterior por defecto en compuestos de empuje, o podrías usar 'Hombros (General)'
    }

    // --- 2. ESPALDA (Separación Amplitud vs Densidad) ---
    // Trapecio y Espalda Alta (Densidad)
    if (lower.includes('trapecio') || lower.includes('romboides') || lower.includes('espinal') || lower.includes('alta')) {
        return 'Trapecio';
    }
    // Dorsal (Amplitud)
    if (lower.includes('dorsal') || lower.includes('lat') || lower.includes('redondo') || lower.includes('ancho')) {
        return 'Dorsal';
    }
    // Espalda Baja (Salud/Core)
    if (lower.includes('erector') || lower.includes('lumbar') || lower.includes('baja')) {
        return 'Espalda Baja';
    }
    // Fallback genérico de espalda
    if (lower.includes('espalda')) {
        return 'Dorsal'; // Por defecto a amplitud
    }

    // --- 3. BRAZOS (Separación Estricta) ---
    if (lower.includes('tríceps') || lower.includes('triceps')) {
        return 'Tríceps';
    }
    // Bíceps (Excluyendo femoral)
    if ((lower.includes('bíceps') || lower.includes('biceps') || lower.includes('braquial')) && !lower.includes('femoral')) {
        return 'Bíceps';
    }
    if (lower.includes('antebrazo')) {
        return 'Antebrazo';
    }

    // --- 4. PIERNA (Mantenemos la lógica que ya funcionaba) ---
    if (lower.includes('femoral') || lower.includes('semitendinoso') || lower.includes('semimembranoso') || lower.includes('isquio')) {
        return 'Isquiosurales';
    }
    if (lower.includes('cuádriceps') || lower.includes('cuadriceps') || lower.includes('recto femoral') || lower.includes('vasto')) {
        return 'Cuádriceps';
    }
    if (lower.includes('glúteo') || lower.includes('gluteo')) {
        return 'Glúteos';
    }
    if (lower.includes('gemelo') || lower.includes('sóleo') || lower.includes('soleo') || lower.includes('pantorrilla')) {
        return 'Gemelos';
    }

    // --- 5. CORE / OTROS ---
    if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectoral';
    if (lower.includes('abdominal') || lower.includes('oblicuo') || lower.includes('core')) return 'Abdominales';

    // Fallback final: Capitalizar nombre original
    return specificMuscle.charAt(0).toUpperCase() + specificMuscle.slice(1);
    
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