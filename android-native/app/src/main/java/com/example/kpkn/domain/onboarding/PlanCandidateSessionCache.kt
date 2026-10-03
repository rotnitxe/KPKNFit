package com.example.kpkn.domain.onboarding

/**
 * T-005 / §15.3 — caché de sesión de evaluaciones de candidatos.
 *
 * Reglas:
 * - Máximo **32** evaluaciones por clave completa (LRU por acceso); es caché
 *   de proceso, nunca persistente.
 * - Se invalida COMPLETA cuando cambia la revisión de catálogo que la produjo
 *   (una revisión vieja jamás responde una pregunta nueva).
 * - Una [PlanCandidateEvaluation.CatalogLoading] no se cachea: no es un
 *   resultado, es «todavía no se puede evaluar».
 *
 * Sincronizada porque las evaluaciones se calculan en `Dispatchers.Default` y el
 * ViewModel consulta desde Main.
 */
class PlanCandidateSessionCache(
    private val maxSize: Int = MAX_ENTRIES,
) {
    private val store = object : LinkedHashMap<String, PlanCandidateEvaluation>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, PlanCandidateEvaluation>,
        ): Boolean = size > maxSize
    }

    private var catalogRevision: String? = null

    private fun ensureRevision(revision: String) {
        if (catalogRevision != revision) {
            store.clear()
            catalogRevision = revision
        }
    }

    /** Resultado cacheado para [key]; null = fallo o clave de otra revisión. */
    @Synchronized
    fun get(revision: String, key: String): PlanCandidateEvaluation? {
        ensureRevision(revision)
        return store[key]
    }

    @Synchronized
    fun put(revision: String, key: String, value: PlanCandidateEvaluation) {
        if (value is PlanCandidateEvaluation.CatalogLoading) return
        ensureRevision(revision)
        store[key] = value
    }

    @Synchronized
    fun invalidate() {
        store.clear()
        catalogRevision = null
    }

    @Synchronized
    fun size(): Int = store.size

    @Synchronized
    fun keys(): Set<String> = store.keys.toSet()

    companion object {
        const val MAX_ENTRIES = 32
    }
}
