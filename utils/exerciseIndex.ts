// utils/exerciseIndex.ts
// O(1) lookup index for the exercise database.
// Replaces O(n) linear scans via exerciseList.find() in hot paths.

import { ExerciseMuscleInfo } from '../types';

export interface ExerciseIndex {
    byId: Map<string, ExerciseMuscleInfo>;
    byName: Map<string, ExerciseMuscleInfo>;
}

export function buildExerciseIndex(exerciseList: ExerciseMuscleInfo[]): ExerciseIndex {
    const byId = new Map<string, ExerciseMuscleInfo>();
    const byName = new Map<string, ExerciseMuscleInfo>();

    for (const ex of exerciseList) {
        byId.set(ex.id, ex);
        byName.set(ex.name.toLowerCase(), ex);
    }

    return { byId, byName };
}

/**
 * Finds an exercise by ID first, then by name. O(1) average.
 * Drop-in replacement for: exerciseList.find(e => e.id === id || e.name === name)
 */
export function findExercise(
    index: ExerciseIndex,
    idOrDbId: string | undefined,
    name: string | undefined
): ExerciseMuscleInfo | undefined {
    if (idOrDbId) {
        const byId = index.byId.get(idOrDbId);
        if (byId) return byId;
    }
    if (name) {
        return index.byName.get(name.toLowerCase());
    }
    return undefined;
}
