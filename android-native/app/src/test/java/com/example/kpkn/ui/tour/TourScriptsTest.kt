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

    @Test
    fun el_recorrido_de_inicio_usa_las_cuatro_anclas_actualizadas_y_no_menciona_perfil_en_la_barra() {
        val steps = TourScripts.home()
        assertEquals(
            listOf(
                TourAnchors.HOME_SESION,
                TourAnchors.HOME_PROGRAMAS,
                TourAnchors.HOME_BARRA,
                TourAnchors.HOME_PERFIL,
            ),
            steps.map { it.anchorId },
        )
        assertEquals(TourAnchors.HOME_MUSCLES_BTN, steps.first().tryAnchorId)
        val barraStep = steps.first { it.anchorId == TourAnchors.HOME_BARRA }
        assertFalse("La barra inferior no incluye Perfil", barraStep.body.contains("Perfil", ignoreCase = true))
    }

    @Test
    fun los_guiones_de_entreno_y_primera_sesion_siguen_el_orden_actualizado() {
        assertEquals(
            listOf(
                TourAnchors.TRAINING_ESTRUCTURA,
                TourAnchors.TRAINING_SEMANA,
                TourAnchors.TRAINING_SESION,
                TourAnchors.TRAINING_EMPEZAR,
            ),
            TourScripts.training.map { it.anchorId },
        )
        assertEquals(TourAnchors.TRAINING_EX1, TourScripts.training[2].tryAnchorId)

        assertEquals(
            listOf(
                TourAnchors.WORKOUT_SERIE,
                TourAnchors.WORKOUT_DESCANSO,
                TourAnchors.WORKOUT_SIGUIENTE,
            ),
            TourScripts.firstWorkout.map { it.anchorId },
        )
        assertEquals(TourAnchors.WORKOUT_LOG_BTN, TourScripts.firstWorkout[0].tryAnchorId)
    }

    @Test
    fun nutricion_y_cuerpo_adaptan_su_texto_cuando_no_hay_plan_ni_registros() {
        val planSinMeta = TourScripts.nutrition(hasActivePlan = false)
            .first { it.anchorId == TourAnchors.NUTRITION_PLAN }
        assertTrue(planSinMeta.body.contains("Crear plan"))

        val cuerpoVacio = TourScripts.body(hasEntries = false)
            .first { it.anchorId == TourAnchors.BODY_TENDENCIA }
        assertTrue(cuerpoVacio.body.contains("3 pesajes"))
    }
}
