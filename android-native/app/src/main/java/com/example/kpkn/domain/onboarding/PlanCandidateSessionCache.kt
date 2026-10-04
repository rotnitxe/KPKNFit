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
 * - Paquete A · D3 (B-05): tampoco se cachea un [PlanCandidateEvaluation.Rejected] con un
 *   motivo TRANSITORIO ([PlanRejectionReason.INTERNAL_MATERIALIZATION] y
 *   [PlanRejectionReason.CATALOG_NOT_READY]): describen un fallo del momento, no una
 *   propiedad del plan. Cachearlos hacía que «Reintentar» reprodujera el mismo rechazo sin
 *   volver a evaluar. Los rechazos que sí describen al plan (material, tiempo, perfil…) siguen
 *   cacheados: con las mismas entradas la respuesta es la misma.
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
        if (value is PlanCandidateEvaluation.Rejected && value.reasonCode in TRANSIENT_REASONS) return
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

        /** Motivos de rechazo que describen un fallo del momento y por eso nunca se cachean (B-05). */
        val TRANSIENT_REASONS: Set<PlanRejectionReason> = setOf(
            PlanRejectionReason.INTERNAL_MATERIALIZATION,
            PlanRejectionReason.CATALOG_NOT_READY,
        )
    }
}
