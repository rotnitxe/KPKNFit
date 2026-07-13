// apps/mobile/src/utils/exerciseIndex.ts
// O(1) lookup index for the exercise database - Ported from PWA
import type { ExerciseCatalogEntry } from '../types/workout';

export interface ExerciseIndex {
    byId: Map<string, ExerciseCatalogEntry>;
    byName: Map<string, ExerciseCatalogEntry>;
}

export function buildExerciseIndex(exerciseList: ExerciseCatalogEntry[]): ExerciseIndex {
    const byId = new Map<string, ExerciseCatalogEntry>();
    const byName = new Map<string, ExerciseCatalogEntry>();

    for (const ex of exerciseList) {
        byId.set(ex.id, ex);
        byName.set(ex.name.toLowerCase(), ex);
    }

    return { byId, byName };
}

/**
 * Finds an exercise by ID first, then by name. O(1) average.
 */
export function findExercise(
    index: ExerciseIndex,
    idOrDbId: string | undefined,
    name: string | undefined
): ExerciseCatalogEntry | undefined {
    if (idOrDbId) {
        const byId = index.byId.get(idOrDbId);
        if (byId) return byId;
    }
    if (name) {
        return index.byName.get(name.toLowerCase());
    }
    return undefined;
}

/**
 * Fallback para logs antiguos: busca por coincidencia parcial de nombre.
 */
export function findExerciseByPartialName(
    index: ExerciseIndex,
    name: string | undefined
): ExerciseCatalogEntry | undefined {
    if (!name || name.trim().length < 4) return undefined;
    const normalized = name.toLowerCase().trim();
    let best: ExerciseCatalogEntry | undefined;
    let bestLen = 0;
    for (const [dbName, info] of index.byName) {
        const dbBase = dbName.replace(/\s*\([^)]*\)/g, '').trim();
        const normDb = dbBase.toLowerCase();
        if (normDb.includes(normalized) || normalized.includes(normDb)) {
            if (dbBase.length > bestLen) {
                best = info;
                bestLen = dbBase.length;
            }
        }
    }
    return best;
}

/**
 * Busca ejercicio con fallback: primero exacto, luego por nombre parcial.
 */
export function findExerciseWithFallback(
    index: ExerciseIndex,
    idOrDbId: string | undefined,
    name: string | undefined
): ExerciseCatalogEntry | undefined {
    const exact = findExercise(index, idOrDbId, name);
    if (exact) return exact;
    return findExerciseByPartialName(index, name);
}
