/**
 * services/oneRMPropagationService.ts
 * 
 * Servicio para propagar ajustes de 1RM a ejercicios relacionados.
 * Usa patrones de movimiento, músculos involucrados y tipo de equipamiento
 * para determinar qué ejercicios deben actualizarse cuando un calibrador cambia.
 */

import type { Exercise, ExerciseCatalogEntry } from '../types/workout';
import { useExerciseStore } from '../stores/exerciseStore';

/**
 * Mapeo de patrones de movimiento para propagación de 1RM.
 * Cuando un ejercicio calibrador se actualiza, los ejercicios con el mismo
 * patrón de movimiento reciben un ajuste proporcional.
 */
const MOVEMENT_PATTERNS: Record<string, string[]> = {
  // Empuje horizontal
  'horizontal-push': [
    'press banca', 'press plano', 'bench press', 'press inclinado',
    'press declinado', 'flexiones', 'push-up', 'press mancuernas',
    'press máquina', 'chest press'
  ],
  
  // Tracción horizontal
  'horizontal-pull': [
    'remo barra', 'remo gironda', 'seated row', 'remo mancuerna',
    'remo máquina', 'chest supported row', 'remo t', 't-bar row'
  ],
  
  // Empuje vertical
  'vertical-push': [
    'press militar', 'overhead press', 'press hombro', 'shoulder press',
    'press inclinado máquina', 'machine shoulder press', 'desarrollo'
  ],
  
  // Tracción vertical
  'vertical-pull': [
    'dominadas', 'pull-up', 'jalón pecho', 'lat pulldown',
    'remo vertical', 'upright row', 'jalón tras nuca'
  ],
  
  // Sentadilla (cuádriceps dominante)
  'squat-pattern': [
    'sentadilla', 'squat', 'prensa', 'leg press', 'sentadilla hack',
    'hack squat', 'sentadilla búlgara', 'bulgarian split squat'
  ],
  
  // Bisagra de cadera (cadena posterior)
  'hip-hinge': [
    'peso muerto', 'deadlift', 'rumano', 'rdl', 'buenos días',
    'good morning', 'hip thrust', 'empuje cadera', 'kettlebell swing'
  ],
  
  // Extensión de rodilla
  'knee-extension': [
    'extensión cuádriceps', 'leg extension', 'extension máquina'
  ],
  
  // Flexión de rodilla (isquios)
  'knee-flexion': [
    'curl femoral', 'leg curl', 'curl tumbado', 'curl sentado',
    'nordic curl', 'sliding leg curl'
  ],
  
  // Elevación de pantorrilla
  'calf-raise': [
    'elevación gemelos', 'calf raise', 'gemelo máquina', 'gemelo sentado',
    'donkey calf raise', 'gemelo de pie'
  ],
  
  // Curl de bíceps
  'bicep-curl': [
    'curl bíceps', 'bicep curl', 'curl martillo', 'hammer curl',
    'curl concentrado', 'concentration curl', 'curl araña', 'spider curl'
  ],
  
  // Extensión de tríceps
  'tricep-extension': [
    'extensión tríceps', 'tricep extension', 'press francés',
    'skull crusher', 'fondos', 'dips', 'press cerrado', 'close grip'
  ],
  
  // Core anti-extensión
  'core-anti-extension': [
    'plancha', 'plank', 'ab wheel', 'rueda abdominal', 'crunch',
    'sit-up', 'elevación piernas', 'leg raise'
  ],
  
  // Core anti-rotación
  'core-anti-rotation': [
    'pallof press', 'woodchop', 'rotación ruso', 'russian twist',
    'plancha lateral', 'side plank'
  ]
};

/**
 * Factores de correlación entre ejercicios relacionados.
 * Un factor de 0.8 significa que el 80% del cambio en el ejercicio fuente
 * se aplica al ejercicio destino.
 */
const CORRELATION_FACTORS: Record<string, Record<string, number>> = {
  // Press banca como ejercicio fuente
  'press banca': {
    'press inclinado': 0.85,
    'press declinado': 0.9,
    'flexiones': 0.75,
    'press mancuernas': 0.88,
  },
  
  // Sentadilla como ejercicio fuente
  'sentadilla': {
    'prensa': 1.15, // La prensa suele permitir más peso
    'sentadilla hack': 0.95,
    'sentadilla búlgara': 0.6, // Unilateral, menos peso
  },
  
  // Peso muerto como ejercicio fuente
  'peso muerto': {
    'rumano': 0.85,
    'buenos días': 0.6,
    'hip thrust': 1.3, // Mecánica más favorable
  },
  
  // Press militar como ejercicio fuente
  'press militar': {
    'press inclinado': 0.8,
    'elevaciones laterales': 0.35,
    'press mancuernas': 0.9,
  },
};


/**
 * Normaliza el nombre de un ejercicio para matching.
 */
function normalizeExerciseName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // Remove accents
    .toLowerCase()
    .trim();
}

/**
 * Encuentra el patrón de movimiento de un ejercicio.
 */
export function findMovementPattern(exerciseName: string): string | null {
  const normalizedName = normalizeExerciseName(exerciseName);
  
  for (const [pattern, exercises] of Object.entries(MOVEMENT_PATTERNS)) {
    if (exercises.some(ex => normalizedName.includes(ex))) {
      return pattern;
    }
  }
  
  return null;
}

/**
 * Obtiene ejercicios relacionados basados en el patrón de movimiento.
 */
export function getRelatedExercisesByPattern(
  sourceExerciseName: string,
  allExercises: Exercise[]
): { exercise: Exercise; pattern: string }[] {
  const sourcePattern = findMovementPattern(sourceExerciseName);
  if (!sourcePattern) return [];
  
  return allExercises
    .filter(ex => {
      if (ex.name === sourceExerciseName) return false; // Excluir el fuente
      const pattern = findMovementPattern(ex.name);
      return pattern === sourcePattern;
    })
    .map(ex => ({ exercise: ex, pattern: sourcePattern! }));
}

/**
 * Obtiene el factor de correlación entre dos ejercicios basándose en nombres,
 * patrones de movimiento y/o grupos musculares comunes.
 */
export function getCorrelationFactor(
  source: Exercise,
  target: Exercise,
  catalog: ExerciseCatalogEntry[] = []
): number {
  const sourceName = normalizeExerciseName(source.name);
  const targetName = normalizeExerciseName(target.name);
  
  // 1. Buscar correlación específica en el mapa (hardcoded)
  const sourceCorrelations = CORRELATION_FACTORS[sourceName];
  if (sourceCorrelations) {
    for (const [targetKey, factor] of Object.entries(sourceCorrelations)) {
      if (targetName.includes(targetKey) || targetKey.includes(targetName)) {
        return factor;
      }
    }
  }
  
  // 2. Misma categoría de movimiento (ej: press banca vs press mancuernas)
  const sourcePattern = findMovementPattern(source.name);
  const targetPattern = findMovementPattern(target.name);
  
  if (sourcePattern && sourcePattern === targetPattern) {
    return 0.75; // Correlación genérica intra-patrón
  }
  
  // 3. Empate por Grupo Muscular Primario (Transfer sutil)
  // Necesitamos buscar en el catálogo si no está en el objeto base
  const sourceEntry = catalog.find(e => e.id === source.exerciseDbId || e.id === source.exerciseId);
  const targetEntry = catalog.find(e => e.id === target.exerciseDbId || e.id === target.exerciseId);
  
  const sourceMuscles = sourceEntry?.involvedMuscles || [];
  const targetMuscles = targetEntry?.involvedMuscles || [];
  
  const sourcePrimary = sourceMuscles[0]?.muscle;
  const targetPrimary = targetMuscles[0]?.muscle;
  
  if (sourcePrimary && targetPrimary && sourcePrimary === targetPrimary) {
    return 0.4; // Transferencia sutil por compartir músculo motor primario
  }
  
  return 0; // Sin correlación detectada
}

/**
 * Calcula el nuevo 1RM para un ejercicio basado en la propagación.
 */
export function calculatePropagated1RM(
  source: Exercise,
  sourceNew1RM: number,
  sourceOld1RM: number,
  target: Exercise,
  targetCurrent1RM: number,
  catalog: ExerciseCatalogEntry[] = []
): number {
  const correlationFactor = getCorrelationFactor(source, target, catalog);
  
  if (correlationFactor === 0) {
    return targetCurrent1RM; // Sin propagación
  }
  
  // Evitar división por cero
  if (sourceOld1RM <= 0) return targetCurrent1RM;

  // Calcular cambio porcentual en el ejercicio fuente
  const percentChange = (sourceNew1RM - sourceOld1RM) / sourceOld1RM;
  
  // Aplicar cambio proporcional al ejercicio destino
  const adjustedChange = percentChange * correlationFactor;
  const new1RM = targetCurrent1RM * (1 + adjustedChange);
  
  return parseFloat(new1RM.toFixed(1));
}

/**
 * Interfaz para el resultado de propagación de 1RM.
 */
export interface PropagationResult {
  exerciseId: string;
  exerciseName: string;
  old1RM: number;
  new1RM: number;
  correlationFactor: number;
  reason: string;
  isDrop?: boolean; // Indica si la intensidad bajó (para lógica de protección)
}

/**
 * Propaga el 1RM de un ejercicio calibrador a todos los ejercicios relacionados de la sesión.
 */
export function propagate1RM(
  sourceExercise: Exercise,
  new1RM: number,
  sessionExercises: Exercise[],
  current1RMs: Record<string, number>,
  catalog: ExerciseCatalogEntry[] = []
): PropagationResult[] {
  const results: PropagationResult[] = [];
  const sourceId = sourceExercise.id;
  const old1RM = current1RMs[sourceId] || new1RM;
  
  // Registrar el ejercicio fuente
  results.push({
    exerciseId: sourceId,
    exerciseName: sourceExercise.name,
    old1RM,
    new1RM,
    correlationFactor: 1.0,
    reason: 'Ejercicio calibrador',
    isDrop: new1RM < old1RM
  });
  
  // Propagar a ejercicios relacionados
  sessionExercises.forEach(ex => {
    if (ex.id === sourceId) return;
    
    const correlationFactor = getCorrelationFactor(sourceExercise, ex, catalog);
    
    if (correlationFactor > 0) {
      const currentTarget1RM = current1RMs[ex.id] || 0;
      
      if (currentTarget1RM > 0) {
        const propagated1RM = calculatePropagated1RM(
          sourceExercise,
          new1RM,
          old1RM,
          ex,
          currentTarget1RM,
          catalog
        );
        
        const pattern = findMovementPattern(sourceExercise.name);
        results.push({
          exerciseId: ex.id,
          exerciseName: ex.name,
          old1RM: currentTarget1RM,
          new1RM: propagated1RM,
          correlationFactor,
          reason: pattern ? `Patrón: ${pattern}` : 'Reflejo muscular',
          isDrop: propagated1RM < currentTarget1RM
        });
      }
    }
  });
  
  return results;
}

/**
 * Obtiene sugerencias de 1RM inicial para ejercicios sin calibrar
 * basándose en ejercicios calibrados del mismo patrón o grupo muscular.
 */
export function suggest1RMForExercise(
  exerciseName: string,
  calibratedExercises: Array<{ name: string; oneRM: number }>,
  userBodyweight?: number
): number | null {
  const pattern = findMovementPattern(exerciseName);
  
  // Buscar ejercicios calibrados del mismo patrón
  const relatedCalibrated = calibratedExercises.filter(ex => {
    const exPattern = findMovementPattern(ex.name);
    return exPattern && exPattern === pattern;
  });
  
  if (relatedCalibrated.length === 0) {
    // Si no hay mismo patrón, buscar por palabra clave en el nombre (ej: "press")
    const words = normalizeExerciseName(exerciseName).split(' ');
    const keywordMatch = calibratedExercises.filter(ex => 
      words.some(w => w.length > 3 && normalizeExerciseName(ex.name).includes(w))
    );
    
    if (keywordMatch.length > 0) {
      const avg = keywordMatch.reduce((acc, curr) => acc + curr.oneRM, 0) / keywordMatch.length;
      return parseFloat(avg.toFixed(1));
    }

    // Fallback final: usar peso corporal
    return userBodyweight ? userBodyweight * 0.8 : null;
  }
  
  // Calcular promedio ponderado (intra-patrón se asume factor 0.75+)
  const avg = relatedCalibrated.reduce((acc, curr) => acc + curr.oneRM, 0) / relatedCalibrated.length;
  return parseFloat(avg.toFixed(1));
}

