// data/exerciseList.ts
import { FULL_EXERCISE_LIST } from './exerciseDatabaseMerged';
import { normalizeMuscleGroup } from '../services/volumeCalculator';

// This is a simplified list for quick searching in the UI.
// It's generated from the central merged list to ensure consistency.
export const EXERCISE_LIST: { name: string; primaryMuscles: string[] }[] = FULL_EXERCISE_LIST.map(ex => ({
  name: ex.name,
  primaryMuscles: ex.involvedMuscles.filter(m => m.role === 'primary').map(m => m.muscle),
}));

// Solo grupos unificados (sin porciones: Pectoral Superior, Trapecio Medio, etc.)
export const MUSCLE_GROUPS = ["Todos", ...Array.from(new Set(FULL_EXERCISE_LIST.flatMap(ex =>
  ex.involvedMuscles.filter(m => m.role === 'primary').map(m => normalizeMuscleGroup(m.muscle, m.emphasis))
))).filter(Boolean).sort()];

export const EXERCISE_TYPES = ['All', 'Básico', 'Accesorio', 'Aislamiento'];
export const CHAIN_TYPES = ['All', 'Anterior', 'Posterior', 'Full'];
