import { 
    Program, 
    Session, 
    Exercise, 
    AthleteProfile, 
    TrainingPhase, 
    IntensityTier, 
    VolumeRecommendation 
} from '../types';

// --- MÓDULO 3: MATRIZ DE SUPERPOSICIÓN MUSCULAR ---
// Definición de roles musculares decimales.
// Agonista = 1.0 (Implícito). Sinergista = 0.5.
const SYNERGIST_MAPPING: Record<string, string[]> = {
    'Chest': ['Triceps', 'Shoulders'], 
    'Back': ['Biceps', 'Forearms'],    
    'Quads': ['Glutes'],               
    'Hamstrings': ['Glutes'],          
    'Shoulders': ['Triceps'],          
};

// Músculos pequeños recuperan más rápido y toleran mayor volumen numérico 
const SMALL_MUSCLES = ['Biceps', 'Triceps', 'Shoulders', 'Calves', 'Abs', 'Forearms'];
const SMALL_MUSCLE_MULTIPLIER = 1.2; 

// --- MÓDULO 4: VARIABLES DE AJUSTE DINÁMICO ---

// 4.3 Variable de Fase (Periodización)
const PHASE_FACTORS: Record<TrainingPhase, number> = {
    'accumulation': 1.0,    
    'transformation': 0.75, 
    'realization': 0.50     
};

// 4.1 Variable de Intensidad (El Costo Fisiológico)
const INTENSITY_FACTORS: Record<IntensityTier, number> = {
    'failure': 0.6,  
    'rpe_8_9': 1.0,  
    'rpe_6_7': 1.2   
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
    // 1. Experiencia Técnica (1-3)
    score += profile.technicalScore;
    // 2. Consistencia (1-3)
    score += profile.consistencyScore;
    // 3. Fuerza Relativa (1-3)
    score += profile.strengthStandard;
    // 4. Movilidad/Recuperación (Adaptado a 1-3)
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
        // Perfil Avanzado
        return { min: 14, max: 22 }; 
    } else {
        // Perfil Principiante/Intermedio
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
    
    // 1. Determinar Capacidad Base (CB)
    const score = calculateAthleteScore(profile);
    const cb = getBaseCapacity(score);

    // 2. Obtener Factores
    const fFase = PHASE_FACTORS[phase];
    const fInt = INTENSITY_FACTORS[intensity];
    
    // Ajuste extra para músculos pequeños
    const muscleFactor = SMALL_MUSCLES.includes(muscle) ? SMALL_MUSCLE_MULTIPLIER : 1.0;

    // 3. Cálculo Preliminar: V_Rec = CB * F_Fase * F_Int
    let minEffectiveVolume = Math.round(cb.min * fFase * fInt * muscleFactor);
    let maxAdaptiveVolume = Math.round(cb.max * fFase * fInt * muscleFactor); 

    // 4. Validación de Frecuencia (Check Final)
    // Tope Max Semanal = 10 series * Días de frecuencia
    // "Más allá de 8-12 series es volumen basura" -> Usamos 10 como promedio seguro.
    const sessionCap = 10;
    const weeklyFrequencyCap = sessionCap * frequency;

    // V_Final = Mínimo entre (V_Rec y Tope_Max)
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
            // Acceso seguro a la propiedad dinámica
            const target = (ex as any).targetMuscleGroup;
            if (!target) return;

            const sets = ex.sets.length;

            // 1. Agonista (Principal): Valor 1.0
            plannedVolume[target] = (plannedVolume[target] || 0) + sets;
            musclesHitInSession.add(target);

            // 2. Sinergista (Secundario): Valor 0.5
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