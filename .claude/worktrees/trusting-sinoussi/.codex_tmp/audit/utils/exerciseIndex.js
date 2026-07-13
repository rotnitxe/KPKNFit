// utils/exerciseIndex.ts
// O(1) lookup index for the exercise database.
// Replaces O(n) linear scans via exerciseList.find() in hot paths.
import { EXERCISE_ID_ALIASES } from '../data/exerciseDatabaseMerged';
export function buildExerciseIndex(exerciseList) {
    const byId = new Map();
    const byName = new Map();
    for (const ex of exerciseList) {
        byId.set(ex.id, ex);
        byName.set(ex.name.toLowerCase(), ex);
    }
    // Registrar aliases: IDs de duplicados eliminados apuntan al ejercicio canónico
    for (const [aliasId, canonicalId] of EXERCISE_ID_ALIASES) {
        const canonical = byId.get(canonicalId);
        if (canonical)
            byId.set(aliasId, canonical);
    }
    return { byId, byName };
}
/**
 * Finds an exercise by ID first, then by name. O(1) average.
 * Drop-in replacement for: exerciseList.find(e => e.id === id || e.name === name)
 */
export function findExercise(index, idOrDbId, name) {
    if (idOrDbId) {
        const byId = index.byId.get(idOrDbId);
        if (byId)
            return byId;
    }
    if (name) {
        return index.byName.get(name.toLowerCase());
    }
    return undefined;
}
/**
 * Fallback para logs antiguos: busca por coincidencia parcial de nombre.
 * Útil cuando el ejercicio fue renombrado, tiene variantes (ej. "Press de Banca (Táctil)")
 * o el log guardó un nombre ligeramente distinto.
 */
export function findExerciseByPartialName(index, name) {
    if (!name || name.trim().length < 4)
        return undefined;
    const normalized = name.toLowerCase().trim();
    let best;
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
 * Para que el cálculo de batería retroactivo incluya ejercicios de logs antiguos.
 */
export function findExerciseWithFallback(index, idOrDbId, name) {
    const exact = findExercise(index, idOrDbId, name);
    if (exact)
        return exact;
    return findExerciseByPartialName(index, name);
}
