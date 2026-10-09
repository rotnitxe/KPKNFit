package com.example.kpkn.ui.tour

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TourControllerTest {
    private class FakeSeen : TourSeenStore {
        val marked = mutableListOf<String>()

        override fun seenIds(): Set<String> = marked.toSet()

        override fun markSeen(tourId: String) {
            marked += tourId
        }
    }

    private fun step(anchor: String, kind: TourStepKind = TourStepKind.NEXT) =
        TourStep(anchorId = anchor, title = "Paso $anchor", body = "Texto $anchor", kind = kind)

    @Test
    fun avanza_con_siguiente_y_al_terminar_marca_visto() = runTest {
        val seen = FakeSeen()
        var finished = 0
        val tour = TourController(scope = this, seen = seen, hasAnchor = { true }, onFinished = { finished++ })

        assertTrue(tour.start("home", listOf(step("a"), step("b"))))
        assertEquals(0, tour.state.value.index)
        tour.next()
        assertEquals(1, tour.state.value.index)
        tour.next()

        assertFalse(tour.state.value.isActive)
        assertEquals(listOf("home"), seen.marked)
        assertEquals(1, finished)
    }

    @Test
    fun saltar_cierra_el_recorrido_y_lo_marca_visto() = runTest {
        val seen = FakeSeen()
        val tour = TourController(scope = this, seen = seen, hasAnchor = { true })
        tour.start("cuerpo", listOf(step("a"), step("b")))

        tour.skip()

        assertFalse(tour.state.value.isActive)
        assertEquals(listOf("cuerpo"), seen.marked)
    }

    @Test
    fun pruebalo_avanza_solo_con_el_toque_de_su_ancla() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start("entreno", listOf(step("rings", TourStepKind.TRY), step("cuerpo")))

        tour.onAnchorTapped("otro")
        assertEquals(0, tour.state.value.index)
        tour.onAnchorTapped("rings")
        assertEquals(1, tour.state.value.index)
    }

    @Test
    fun un_paso_siguiente_no_avanza_con_el_toque_de_su_ancla() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start("home", listOf(step("a"), step("b")))

        tour.onAnchorTapped("a")

        assertEquals(0, tour.state.value.index)
    }

    @Test
    fun ancla_ausente_se_salta_al_cumplirse_el_plazo() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { it == "b" })
        tour.start("home", listOf(step("a"), step("b")))

        advanceTimeBy(TOUR_ANCHOR_TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, tour.state.value.index)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, tour.state.value.index)
    }

    @Test
    fun ancla_presente_no_se_salta() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start("home", listOf(step("a"), step("b")))

        advanceTimeBy(TOUR_ANCHOR_TIMEOUT_MS * 3)
        runCurrent()

        assertEquals(0, tour.state.value.index)
    }

    @Test
    fun no_arranca_un_recorrido_visto_ni_otro_encima_del_activo() = runTest {
        val seen = FakeSeen().apply { markSeen("home") }
        val tour = TourController(scope = this, seen = seen, hasAnchor = { true })

        assertFalse(tour.start("home", listOf(step("a"))))
        assertTrue(tour.start("nuevo", listOf(step("a"))))
        assertFalse(tour.start("otro", listOf(step("a"))))
    }
}
