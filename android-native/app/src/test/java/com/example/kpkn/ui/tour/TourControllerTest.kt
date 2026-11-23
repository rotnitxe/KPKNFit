package com.example.kpkn.ui.tour

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TourControllerTest {
    private class FakeSeen : TourSeenStore {
        val marked = mutableListOf<String>()

        override fun seenIds(): Set<String> = marked.toSet()

        override fun markSeen(tourId: String) {
            marked += tourId
        }
    }

    private fun step(
        anchor: String,
        kind: TourStepKind = TourStepKind.NEXT,
        tryAnchor: String? = null,
    ) = TourStep(
        anchorId = anchor,
        title = "Paso $anchor",
        body = "Texto $anchor",
        kind = kind,
        tryAnchorId = tryAnchor,
    )

    @Test
    fun avanza_con_siguiente_y_al_terminar_marca_visto_y_notifica_completado() = runTest {
        val seen = FakeSeen()
        var finished = 0
        var completedTour: String? = null
        val tour = TourController(
            scope = this,
            seen = seen,
            hasAnchor = { true },
            onFinished = { finished++ },
            onCompleted = { completedTour = it },
        )

        assertTrue(tour.start("home", listOf(step("a"), step("b"))))
        assertEquals(0, tour.state.value.index)
        assertFalse(tour.state.value.canGoBack)
        tour.next()
        assertEquals(1, tour.state.value.index)
        assertTrue(tour.state.value.canGoBack)
        tour.next()

        assertFalse(tour.state.value.isActive)
        assertEquals(listOf("home"), seen.marked)
        assertEquals(1, finished)
        assertEquals("home", completedTour)
    }

    @Test
    fun saltar_cierra_el_recorrido_lo_marca_visto_y_no_notifica_completado() = runTest {
        val seen = FakeSeen()
        var completed = 0
        val tour = TourController(
            scope = this,
            seen = seen,
            hasAnchor = { true },
            onCompleted = { completed++ },
        )
        tour.start("cuerpo", listOf(step("a"), step("b")))

        tour.skip()

        assertFalse(tour.state.value.isActive)
        assertEquals(listOf("cuerpo"), seen.marked)
        assertEquals(0, completed)
    }

    @Test
    fun pruebalo_muestra_pulso_de_exito_y_avanza_con_anchor_o_tryAnchor() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start(
            "entreno",
            listOf(
                step("sesion", TourStepKind.TRY, tryAnchor = "ex1"),
                step("cuerpo"),
                step("fin"),
            ),
        )

        tour.onAnchorTapped("otro")
        assertEquals(0, tour.state.value.index)
        assertFalse(tour.state.value.trySuccessPulse)

        tour.onAnchorTapped("ex1")
        assertTrue(tour.state.value.trySuccessPulse)
        assertEquals(0, tour.state.value.index)

        advanceTimeBy(TOUR_TRY_SUCCESS_DELAY_MS)
        runCurrent()
        assertEquals(1, tour.state.value.index)
        assertFalse(tour.state.value.trySuccessPulse)
    }

    @Test
    fun siguiente_funciona_como_escape_seguro_en_pasos_pruebalo() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start("home", listOf(step("a", TourStepKind.TRY), step("b")))

        tour.next()

        assertEquals(1, tour.state.value.index)
        assertFalse(tour.state.value.trySuccessPulse)
    }

    @Test
    fun retroceder_vuelve_al_paso_anterior_y_no_pasa_de_cero() = runTest {
        val tour = TourController(scope = this, seen = FakeSeen(), hasAnchor = { true })
        tour.start("home", listOf(step("a"), step("b")))

        tour.previous()
        assertEquals(0, tour.state.value.index)

        tour.next()
        assertEquals(1, tour.state.value.index)

        tour.previous()
        assertEquals(0, tour.state.value.index)
    }

    @Test
    fun reveal_anchor_se_ejecuta_antes_del_timeout_y_activa_isScrollingToAnchor() = runTest {
        val revealed = mutableListOf<String>()
        var anchorAppeared = false
        val tour = TourController(
            scope = this,
            seen = FakeSeen(),
            hasAnchor = { id -> id == "a" || (id == "b" && anchorAppeared) },
            hasScroller = { id -> id == "b" },
            revealAnchor = { id ->
                revealed += id
                kotlinx.coroutines.delay(300)
                anchorAppeared = true
            },
        )
        tour.start("home", listOf(step("a"), step("b"), step("c")))
        tour.next()

        assertTrue(tour.state.value.isScrollingToAnchor)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("b"), revealed)
        assertFalse(tour.state.value.isScrollingToAnchor)
        advanceTimeBy(TOUR_ANCHOR_TIMEOUT_MS)
        runCurrent()
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

    @Test
    fun no_arranca_mientras_store_no_esta_listo() = runTest {
        val unreadyStore = object : TourSeenStore {
            override fun isReady(): Boolean = false
            override fun seenIds(): Set<String> = emptySet()
            override fun markSeen(tourId: String) = Unit
        }
        val tour = TourController(scope = this, seen = unreadyStore, hasAnchor = { true })

        assertFalse(tour.start("home", listOf(step("a"))))
        assertFalse(tour.state.value.isActive)
    }

    @Test
    fun fallo_en_reveal_anchor_no_crashea_y_permite_continuar_o_saltar() = runTest {
        val seen = FakeSeen()
        val tour = TourController(
            scope = this,
            seen = seen,
            hasAnchor = { true },
            hasScroller = { id -> id == "b" },
            revealAnchor = { throw IllegalStateException("No MonotonicFrameClock") },
        )
        assertTrue(tour.start("home", listOf(step("a"), step("b"), step("c"))))

        tour.next()
        runCurrent()

        assertEquals(1, tour.state.value.index)
        assertFalse(tour.state.value.isScrollingToAnchor)

        tour.next()
        assertEquals(2, tour.state.value.index)

        tour.skip()
        assertFalse(tour.state.value.isActive)
        assertEquals(listOf("home"), seen.marked)
    }
}
