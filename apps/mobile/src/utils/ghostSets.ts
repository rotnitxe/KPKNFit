import type { WorkoutLog, CompletedExercise, CompletedSet } from '../types/workout';

/**
 * Obtiene los datos del fantasma (última vez) para un ejercicio y un índice de serie específico.
 * Busca en el historial de entrenamientos la última vez que se completó el ejercicio y devuelve
 * el peso, repeticiones y RPE de la serie correspondiente al índice dado.
 *
 * @param exerciseId - ID del ejercicio a buscar
 * @param setIndex - Índice de la serie (0-based) dentro del ejercicio
 * @param history - Array de registros de entrenamiento
 * @returns Objeto con weight, reps y opcionalmente rpe, o null si no se encuentra
 */
export function getGhostForSet(
  exerciseId: string,
  setIndex: number,
  history: WorkoutLog[]
): { weight: number; reps: number; rpe?: number } | null {
  // Buscar desde el entrenamiento más reciente hacia atrás
  for (let i = history.length - 1; i >= 0; i--) {
    const log = history[i];
    const completedExercise = log.completedExercises.find(
      (ex) => ex.exerciseId === exerciseId || ex.exerciseDbId === exerciseId
    );

    if (completedExercise) {
      const set = completedExercise.sets[setIndex];
      if (set) {
        return {
          weight: set.weight,
          reps: set.completedReps ?? 0,
          rpe: set.completedRPE,
        };
      }
    }
  }

  return null;
}