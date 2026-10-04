package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-005 / §15.3 — caché de sesión de evaluaciones: ≤32 por clave, invalidación
 * por revisión de catálogo y «catálogo en carga» que jamás se cachea.
 *
 * Paquete A · D3 (B-05): los rechazos TRANSITORIOS (`INTERNAL_MATERIALIZATION` y
 * `CATALOG_NOT_READY`) tampoco se cachean, para que «Reintentar» vuelva a evaluar en lugar de
 * reproducir el mismo fallo.
 */
class PlanCandidateSessionCacheTest {

    private fun rejected(id: String) = PlanCandidateEvaluation.Rejected(
        planId = id,
        stage = PlanEvaluationStage.FREQUENCY_SPLIT,
        reasonCode = PlanRejectionReason.FREQUENCY,
    )

    private fun rejected(id: String, reason: PlanRejectionReason) = PlanCandidateEvaluation.Rejected(
        planId = id,
        stage = PlanEvaluationStage.MATERIALIZATION,
        reasonCode = reason,
    )

    private fun ready(id: String) = PlanCandidateEvaluation.Ready(
        planId = id,
        preparedPlan = Program(id = "program-$id", name = "Plan $id"),
        recipeSnapshot = null,
        provenance = null,
        durationBreakdown = PlanDurationBreakdown(emptyList()),
        inputKey = "input",
        unresolvedWorkoutLoads = emptyList(),
        coverage = PlanCoverage(
            frequency = 3,
            hasStrength = true,
            hasHypertrophy = false,
            hasPower = false,
            hasCardio = false,
        ),
    )

    @Test
    fun neverCachesTransientRejections() {
        val cache = PlanCandidateSessionCache()
        cache.put("rev-a", "input|plan-1", rejected("plan-1", PlanRejectionReason.INTERNAL_MATERIALIZATION))
        cache.put("rev-a", "input|plan-2", rejected("plan-2", PlanRejectionReason.CATALOG_NOT_READY))

        // Ninguno de los dos se guarda: el siguiente intento los evalúa de nuevo.
        assertEquals(0, cache.size())
        assertNull(cache.get("rev-a", "input|plan-1"))
        assertNull(cache.get("rev-a", "input|plan-2"))
    }

    @Test
    fun aTransientRejectionDoesNotDisplaceWhatIsAlreadyCached() {
        val cache = PlanCandidateSessionCache()
        cache.put("rev-a", "input|plan-1", ready("plan-1"))
        cache.put("rev-a", "input|plan-2", rejected("plan-2", PlanRejectionReason.INTERNAL_MATERIALIZATION))

        assertEquals(1, cache.size())
        assertTrue(cache.get("rev-a", "input|plan-1") is PlanCandidateEvaluation.Ready)
    }

    @Test
    fun stillCachesEveryRejectionThatDescribesThePlanAndEveryReadyEvaluation() {
        val cache = PlanCandidateSessionCache()
        val durable = listOf(
            PlanRejectionReason.APPARATUS_ABSENT,
            PlanRejectionReason.APPARATUS_UNKNOWN,
            PlanRejectionReason.TIME_BUDGET,
            PlanRejectionReason.PROFILE_MISMATCH,
            PlanRejectionReason.COMPOSITION,
            PlanRejectionReason.NO_VALID_SUBSTITUTION,
        )
        durable.forEachIndexed { index, reason -> cache.put("rev-a", "input|plan-$index", rejected("plan-$index", reason)) }
        cache.put("rev-a", "input|plan-ready", ready("plan-ready"))

        assertEquals(durable.size + 1, cache.size())
        durable.forEachIndexed { index, reason ->
            val hit = cache.get("rev-a", "input|plan-$index") as PlanCandidateEvaluation.Rejected
            assertEquals(reason, hit.reasonCode)
        }
        assertTrue(cache.get("rev-a", "input|plan-ready") is PlanCandidateEvaluation.Ready)
    }

    @Test
    fun theTransientReasonsAreExactlyTheTwoThatDescribeAMomentaryFailure() {
        assertEquals(
            setOf(PlanRejectionReason.INTERNAL_MATERIALIZATION, PlanRejectionReason.CATALOG_NOT_READY),
            PlanCandidateSessionCache.TRANSIENT_REASONS,
        )
    }

    @Test
    fun cachesTheEvaluationForTheSameRevision() {
        val cache = PlanCandidateSessionCache()
        cache.put("rev-a", "input|plan-1", rejected("plan-1"))

        val hit = cache.get("rev-a", "input|plan-1")
        assertNotNull(hit)
        assertEquals("plan-1", (hit as PlanCandidateEvaluation.Rejected).planId)
        assertNull(cache.get("rev-a", "input|plan-2"))
        assertEquals(1, cache.size())
    }

    @Test
    fun neverCachesCatalogLoading() {
        val cache = PlanCandidateSessionCache()
        cache.put("rev-a", "input|plan-1", PlanCandidateEvaluation.CatalogLoading)

        assertEquals(0, cache.size())
        assertNull(cache.get("rev-a", "input|plan-1"))
    }

    @Test
    fun keepsAtMost32EvaluationsEvictingTheLeastRecentlyUsed() {
        val cache = PlanCandidateSessionCache(maxSize = 32)
        repeat(32) { index -> cache.put("rev-a", "input|plan-$index", rejected("plan-$index")) }
        // Tocar plan-0 para que sea el menos reciente el 1.
        assertNotNull(cache.get("rev-a", "input|plan-0"))
        cache.put("rev-a", "input|plan-32", rejected("plan-32"))

        assertEquals(32, cache.size())
        assertNotNull(cache.get("rev-a", "input|plan-0"))
        assertNull(cache.get("rev-a", "input|plan-1"))
        assertNotNull(cache.get("rev-a", "input|plan-32"))
    }

    @Test
    fun aCatalogRevisionChangeInvalidatesEveryCachedEvaluation() {
        val cache = PlanCandidateSessionCache()
        cache.put("plan-rev-1|ex-1", "input|plan-1", rejected("plan-1"))

        // Misma revisión → hit.
        assertNotNull(cache.get("plan-rev-1|ex-1", "input|plan-1"))
        // Revisión de catálogo distinta → todo cae (nunca responde una
        // pregunta nueva con el resultado de otra revisión).
        assertNull(cache.get("plan-rev-1|ex-2", "input|plan-1"))
        assertEquals(0, cache.size())

        cache.put("plan-rev-1|ex-2", "input|plan-1", rejected("plan-1"))
        assertEquals(1, cache.size())
        assertTrue(cache.keys().contains("input|plan-1"))
    }

    @Test
    fun invalidateClearsRevisionAndEntries() {
        val cache = PlanCandidateSessionCache()
        cache.put("rev-a", "input|plan-1", rejected("plan-1"))
        cache.invalidate()

        assertEquals(0, cache.size())
        assertNull(cache.get("rev-a", "input|plan-1"))
    }
}
