// data/exerciseList.ts
import { DETAILED_EXERCISE_LIST } from './exerciseDatabase';

// This is a simplified list for quick searching in the UI.
// It's generated from the more detailed list to ensure consistency.
export const EXERCISE_LIST: { name: string; primaryMuscles: string[] }[] = DETAILED_EXERCISE_LIST.map(ex => ({
  name: ex.name,
  primaryMuscles: ex.involvedMuscles.filter(m => m.role === 'primary').map(m => m.muscle),
}));


export const MUSCLE_GROUPS = ["Todos", ...Array.from(new Set(DETAILED_EXERCISE_LIST.flatMap(ex => ex.involvedMuscles.filter(m => m.role === 'primary').map(m => m.muscle)))).sort()];

export const EXERCISE_TYPES = ['All', 'Básico', 'Accesorio', 'Aislamiento'];
export const CHAIN_TYPES = ['All', 'Anterior', 'Posterior', 'Full'];