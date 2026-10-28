package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La ruta de la instancia «Nuevo programa» del asistente (Registros v4, cr-wz*, cr-cat3, CR-S4). */
class SetupStepGraphNewProgramTest {

    private val instance = SetupStepContext(includeNutrition = false, newProgramInstance = true)
    private val basics = setOf(
        SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT,
        SetupStepId.EQUATION_SEX, SetupStepId.BODY_FAT, SetupStepId.MILESTONE_BASICS,
    )

    @Test
    fun laInstanciaNoVuelveAPreguntarDatosBasicosNiTieneHitoDeEntreno() {
        val route = SetupStepGraph.stepIds(instance)

        assertTrue("sin Datos básicos: $route", route.none { it in basics })
        assertFalse(SetupStepId.MILESTONE_TRAINING in route)
        assertEquals(SetupStepId.EXPERIENCE, route.first())
        // El cierre de la ruta es «Listo para empezar».
        assertEquals(SetupStepId.REVIEW_ACTIVATE, route.last())
    }

    @Test
    fun elLapizDeLoQueYaSabemosDevuelveSoloAliasEdadAlturaYPeso() {
        val route = SetupStepGraph.stepIds(instance.copy(editsKnownBasics = true))

        assertEquals(
            listOf(SetupStepId.NAME, SetupStepId.AGE, SetupStepId.HEIGHT, SetupStepId.WEIGHT),
            route.take(4),
        )
        // Sexo y % de grasa no se piden, ni hay hito de bloque.
        assertFalse(SetupStepId.EQUATION_SEX in route)
        assertFalse(SetupStepId.BODY_FAT in route)
        assertFalse(SetupStepId.MILESTONE_BASICS in route)
        assertEquals(SetupStepGraph.stepIds(instance), route.drop(4))
    }

    @Test
    fun conElPlanDelCatalogoSoloPreguntaLoQueFalta() {
        val route = SetupStepGraph.stepIds(instance.copy(catalogPlanChosen = true))

        assertEquals(
            listOf(
                SetupStepId.EQUIPMENT, SetupStepId.AVAILABILITY, SetupStepId.WEEKDAYS, SetupStepId.SESSION_TIME,
                SetupStepId.WEEK_LAYOUT, SetupStepId.REVIEW_ACTIVATE,
            ),
            route,
        )
    }

    @Test
    fun elAltaCompletaNoCambia() {
        val full = SetupStepGraph.stepIds(SetupStepContext())
        assertTrue(full.containsAll(basics))
        assertTrue(SetupStepId.MILESTONE_TRAINING in full)
        // Las marcas de la instancia no tocan el alta inicial aunque vengan puestas.
        assertEquals(full, SetupStepGraph.stepIds(SetupStepContext(editsKnownBasics = true, catalogPlanChosen = true)))
    }
}
