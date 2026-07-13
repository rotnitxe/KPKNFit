import { 
    Program, 
    Session, 
    Exercise, 
    AthleteProfile, 
    TrainingPhase, 
    IntensityTier, 
    VolumeRecommendation 
} from '../types';

[cite_start]// --- MÓDULO 3: MATRIZ DE SUPERPOSICIÓN MUSCULAR [cite: 48, 51] ---
// Definición de roles musculares decimales.
// Agonista = 1.0 (Implícito). [cite_start]Sinergista = 0.5[cite: 52].
const SYNERGIST_MAPPING: Record<string, string[]> = {
    'Chest': ['Triceps', 'Shoulders'], // Press banca activa tríceps y hombro
    'Back': ['Biceps', 'Forearms'],    // Remos/Jalones activan bíceps
    'Quads': ['Glutes'],               // Sentadillas activan glúteo
    'Hamstrings': ['Glutes'],          // Peso muerto activa glúteo
    'Shoulders': ['Triceps'],          // Press militar activa tríceps
};

[cite_start]// Músculos pequeños recuperan más rápido y toleran mayor volumen numérico [cite: 53]
const SMALL_MUSCLES = ['Biceps', 'Triceps', 'Shoulders', 'Calves', 'Abs', 'Forearms'];
const SMALL_MUSCLE_MULTIPLIER = 1.2; // Ajuste implícito para músculos de rápida recuperación

// --- MÓDULO 4: VARIABLES DE AJUSTE DINÁMICO ---

[cite_start]// 4.3 Variable de Fase (Periodización) [cite: 79]
const PHASE_FACTORS: Record<TrainingPhase, number> = {
    [cite_start]'accumulation': 1.0,    // [cite: 82, 101] 100% Capacidad
    [cite_start]'transformation': 0.75, // [cite: 86, 102] 70-80% de la base
    [cite_start]'realization': 0.50     // [cite: 90, 104] 40-60% (Peaking/Taper)
};

[cite_start]// 4.1 Variable de Intensidad (El Costo Fisiológico) [cite: 56]
const INTENSITY_FACTORS: Record<IntensityTier, number> = {
    [cite_start]'failure': 0.6,  // [cite: 66, 106] RPE 10/Fallo -> Reducción drástica
    [cite_start]'rpe_8_9': 1.0,  // [cite: 63, 108] Zona Óptima -> Mantiene rango estándar
    [cite_start]'rpe_6_7': 1.2   // [cite: 60, 109] Zona Moderada -> Tolera +20% volumen
};

// --- MÓDULO 1 & 5: MOTOR LÓGICO ---

/**
 * [cite_start]Calcula el Score del Atleta basado en la Matriz de Input[cite: 5, 8].
 * Adaptamos la escala del PDF a nuestros 4 inputs (Máx teórico 12 pts).
 * El PDF usa corte en 15 pts con más preguntas, aquí escalamos proporcionalmente.
 * Escala adaptada: < 9 (Principiante/Intermedio), >= 9 (Avanzado).
 */
export const calculateAthleteScore = (profile: AthleteProfile): number => {
    let score = 0;
    [cite_start]// 1. Experiencia Técnica (1-3) [cite: 9]
    score += profile.technicalScore;
    [cite_start]// 2. Consistencia (1-3) [cite: 13]
    score += profile.consistencyScore;
    [cite_start]// 3. Fuerza Relativa (1-3) [cite: 18]
    score += profile.strengthStandard;
    [cite_start]// 4. Movilidad/Recuperación (Adaptado a 1-3) [cite: 22]
    // Mapeamos recoveryCapacity (-1, 0, 1) a puntos positivos (1, 2, 3)
    score += (profile.recoveryCapacity + 2);

    return score;
};

/**
 * [cite_start]Determina la Capacidad Base (CB) según el Score[cite: 97].
 */
const getBaseCapacity = (score: number): { min: number, max: number } => {
    // Umbral ajustado a escala 12 (aprox 75% de 15)
    if (score >= 9) {
        [cite_start]// Perfil Avanzado [cite: 27, 99]
        return { min: 14, max: 22 }; 
    } else {
        [cite_start]// Perfil Principiante/Intermedio [cite: 26, 98]
        return { min: 10, max: 14 }; 
    }
};

/**
 * [cite_start]EL MEGA-ALGORITMO DE CÁLCULO (Módulo 5) [cite: 95]
 * Calcula el volumen recomendado V_Final
 */
export const calculateOptimalVolume = (
    muscle: string,
    profile: AthleteProfile,
    phase: TrainingPhase = 'accumulation',
    intensity: IntensityTier = 'rpe_8_9',
    frequency: number = 2 // Por defecto Frecuencia 2 si no se especifica
): VolumeRecommendation => {
    
    [cite_start]// 1. Determinar Capacidad Base (CB) [cite: 97]
    const score = calculateAthleteScore(profile);
    const cb = getBaseCapacity(score);

    // 2. Obtener Factores
    const fFase = PHASE_FACTORS[phase];     [cite_start]// [cite: 100]
    const fInt = INTENSITY_FACTORS[intensity]; [cite_start]// [cite: 105]
    
    [cite_start]// Ajuste extra para músculos pequeños (No explícito en Módulo 5, pero derivado de Módulo 3 [cite: 53])
    const muscleFactor = SMALL_MUSCLES.includes(muscle) ? SMALL_MUSCLE_MULTIPLIER : 1.0;

    [cite_start]// 3. Cálculo Preliminar: V_Rec = CB * F_Fase * F_Int [cite: 110]
    // Calculamos rango Min y Max (MAV Target)
    let minEffectiveVolume = Math.round(cb.min * fFase * fInt * muscleFactor);
    let maxAdaptiveVolume = Math.round(cb.max * fFase * fInt * muscleFactor); 

    [cite_start]// 4. Validación de Frecuencia (Check Final) [cite: 111]
    [cite_start]// Tope Max Semanal = 10 series * Días de frecuencia [cite: 112]
    [cite_start]// "Más allá de 8-12 series es volumen basura" [cite: 71] -> Usamos 10 como promedio seguro.
    const sessionCap = 10;
    const weeklyFrequencyCap = sessionCap * frequency;

    [cite_start]// V_Final = Mínimo entre (V_Rec y Tope_Max) [cite: 112]
    const finalTarget = Math.min(maxAdaptiveVolume, weeklyFrequencyCap);
    
    // MRV estimado (Límite superior antes de exceso)
    const maxRecoverableVolume = Math.round(finalTarget * 1.25);

    return {
        muscleGroup: muscle,
        minEffectiveVolume: Math.max(1, minEffectiveVolume),
        maxAdaptiveVolume: finalTarget,
        maxRecoverableVolume: maxRecoverableVolume,
        frequencyCap: weeklyFrequencyCap
    };
};

/**
 * [cite_start]Análisis del Programa según Módulo 3 (Conteo Fraccional) [cite: 48]
 */
export const analyzeProgramStats = (
    program: Program, 
    history: any[] = [] 
) => {
    // Aplanar estructura compleja para iterar sesiones
    const allSessions = program.macrocycles.flatMap(m => 
        (m.blocks || []).flatMap(b => 
            b.mesocycles.flatMap(meso => 
                meso.weeks.flatMap(w => w.sessions)
            )
        )
    );

    // Tomamos una semana representativa (la primera con datos)
    const sampleWeekSessions = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks?.[0]?.sessions || [];

    const plannedVolume: Record<string, number> = {};
    const muscleFrequency: Record<string, number> = {};

    sampleWeekSessions.forEach(session => {
        const musclesHitInSession = new Set<string>();

        (session.exercises || []).forEach(ex => {
            const target = ex.targetMuscleGroup;
            if (!target) return;

            const sets = ex.sets.length;

            [cite_start]// 1. Agonista (Principal): Valor 1.0 [cite: 52]
            plannedVolume[target] = (plannedVolume[target] || 0) + sets;
            musclesHitInSession.add(target);

            [cite_start]// 2. Sinergista (Secundario): Valor 0.5 [cite: 52]
            const synergists = SYNERGIST_MAPPING[target];
            if (synergists) {
                synergists.forEach(syn => {
                    plannedVolume[syn] = (plannedVolume[syn] || 0) + (sets * 0.5);
                    // Nota: El PDF no especifica si el trabajo indirecto cuenta para frecuencia "productiva".
                    // Por seguridad, solo contamos frecuencia si es Agonista o si el volumen indirecto es muy alto.
                });
            }
        });

        // Registrar frecuencia (Días que se estimula el músculo como agonista)
        musclesHitInSession.forEach(m => {
            muscleFrequency[m] = (muscleFrequency[m] || 0) + 1;
        });
    });

    return { plannedVolume, muscleFrequency };
};