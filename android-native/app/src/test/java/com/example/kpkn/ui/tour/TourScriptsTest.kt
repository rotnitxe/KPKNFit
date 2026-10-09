package com.example.kpkn.ui.tour

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los recorridos de la app: arrancan solo si no están vistos y sus anclas no se repiten. */
class TourScriptsTest {
    private class FakeSeen(private val seen: Set<String> = emptySet()) : TourSeenStore {
        val marked = mutableListOf<String>()

        override fun seenIds(): Set<String> = seen + marked

        override fun markSeen(tourId: String) {
            marked += tourId
        }
    }

    @Test
    fun cada_recorrido_arranca_si_no_esta_visto_y_no_si_lo_esta() = runTest {
        for ((id, steps) in TourScripts.todos()) {
            val unseen = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
            assertTrue("$id debe arrancar si no está visto", unseen.start(id, steps))

            val alreadySeen = TourController(scope = this, seen = FakeSeen(setOf(id)), hasAnchor = { true })
            assertFalse("$id no debe arrancar si ya está visto", alreadySeen.start(id, steps))
        }
    }

    @Test
    fun saltar_un_recorrido_lo_marca_visto_y_no_vuelve_a_arrancar() = runTest {
        val seen = FakeSeen()
        val tour = TourController(scope = this, seen = seen, hasAnchor = { true })
        val (id, steps) = TourScripts.todos().entries.first()

        assertTrue(tour.start(id, steps))
        tour.skip()

        assertEquals(listOf(id), seen.marked)
        assertFalse(tour.start(id, steps))
    }

    @Test
    fun los_seis_recorridos_tienen_su_id() {
        assertEquals(
            setOf(TourIds.HOME, TourIds.TRAINING, TourIds.FIRST_WORKOUT, TourIds.NUTRITION, TourIds.BODY, TourIds.PROFILE),
            TourScripts.todos().keys,
        )
    }

    @Test
    fun los_ids_de_ancla_son_unicos_en_todos_los_recorridos() {
        val anchors = TourScripts.todos().values.flatten().map { it.anchorId }

        assertEquals("Hay anclas repetidas", anchors.size, anchors.toSet().size)
    }
}
