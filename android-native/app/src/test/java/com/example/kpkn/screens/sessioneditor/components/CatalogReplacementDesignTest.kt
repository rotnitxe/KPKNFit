package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.catalog
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.definition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LV5, bug 1 (H1): lo que dice el catálogo de reemplazo de cada ejercicio, con reglas puras. La tarjeta «Reemplazando» (nombre,
 * «patrón · músculo · dosis») y la línea corta de cada candidato (implemento y lo que cambia respecto al actual, solo con datos del
 * catálogo).
 */
class CatalogReplacementDesignTest {

    private val bench = definition(
        "bench_press", "Press de Banca Plano", "horizontal_push", listOf("pectoralis"), listOf("barbell", "dumbbells"),
    )

    // ------------------------------------------------------------------ la tarjeta «Reemplazando»

    @Test
    fun laTarjetaLlevaNombreMusculoYDosisDelPlan() {
        val model = catalogReplacingCardModel(
            catalog = catalog(bench),
            context = CatalogReplacementContext(exerciseName = " Press de Banca Plano · Mancuernas ", planSummary = "4 × 4–6 · RIR 2"),
            definitionId = "bench_press",
            configurationId = "bench_press__dumbbells",
        )

        assertNotNull(model)
        assertEquals("Press de Banca Plano · Mancuernas", model!!.name)
        assertEquals("pectoralis", model.mainMuscleId)
        assertTrue("termina en músculo y dosis: ${model.subtitle}", model.subtitle!!.endsWith("Pectorales · 4 × 4–6 · RIR 2"))
    }

    @Test
    fun mientrasCargaElCatalogoLaTarjetaLlevaSoloElNombreYLaDosis() {
        val model = catalogReplacingCardModel(
            catalog = null,
            context = CatalogReplacementContext(exerciseName = "Remo con barra", planSummary = "3 × 8–10 · RIR 1"),
            definitionId = "row",
            configurationId = "row__barbell",
        )

        assertNotNull(model)
        assertEquals("Remo con barra", model!!.name)
        assertNull("sin catálogo no hay músculo que filtrar", model.mainMuscleId)
        assertTrue("termina en la dosis: ${model.subtitle}", model.subtitle!!.endsWith("3 × 8–10 · RIR 1"))
    }

    @Test
    fun sinNombreSeUsaElDelCatalogoYSinNadaNoHayTarjeta() {
        val fromCatalog = catalogReplacingCardModel(catalog(bench), CatalogReplacementContext(), "bench_press", null)
        assertEquals("Press de Banca Plano", fromCatalog?.name)

        assertNull(catalogReplacingCardModel(catalog(bench), CatalogReplacementContext(exerciseName = "  "), null, null))
        assertNull(catalogReplacingCardModel(null, CatalogReplacementContext(), "bench_press", null))
    }

    @Test
    fun sinDosisLaTarjetaNoInventaNinguna() {
        val model = catalogReplacingCardModel(
            catalog = catalog(bench),
            context = CatalogReplacementContext(exerciseName = "Press de Banca Plano", planSummary = "  "),
            definitionId = "bench_press",
            configurationId = null,
        )

        assertNotNull(model)
        assertTrue("sin dosis, termina en el músculo: ${model!!.subtitle}", model.subtitle!!.endsWith("Pectorales"))
    }

    // ------------------------------------------------------------------ la línea de cada candidato

    /** Una definición de una sola configuración, con el perfil que se pide. */
    private fun candidate(
        id: String,
        implement: String,
        muscle: String,
        laterality: ExerciseLateralityV2,
    ): ExerciseDefinitionV2 {
        val base = definition(id, "Candidato $id", "knee_dominant", listOf(muscle), listOf(implement))
        return base.copy(
            configurations = base.configurations.map { configuration ->
                configuration.copy(profile = configuration.profile.copy( laterality = laterality))
            },
        )
    }

    /** La sentadilla trasera: patrón, lateralidad y músculo declarados. */
    private fun anchor(
        laterality: ExerciseLateralityV2 = ExerciseLateralityV2.BILATERAL,
    ) = CatalogReplacementAnchor(
        definitionId = "back_squat",
        movementPatternId = "knee_dominant",
        primaryMuscles = setOf("quadriceps"),
        configurationId = "back_squat__barbell",
        mainMuscle = "quadriceps",
        laterality = laterality,
        
    )

    private fun line(definition: ExerciseDefinitionV2, anchor: CatalogReplacementAnchor): String? =
        catalogReplacementCandidateLine(
            definition = definition,
            configuration = definition.configurations.first { it.id == definition.defaultConfigurationId },
            anchor = anchor,
        )

    @Test
    fun aReplacementDoesNotInventAnAxialDifference() {
        val press = candidate("leg_press", "machine", "quadriceps", laterality = ExerciseLateralityV2.BILATERAL)
        val material = exerciseCatalogOptionLabel("machine", "leg_press")

        assertEquals(material, line(press, anchor()))
    }

    @Test
    fun laLateralidadSoloSaleSiCambia() {
        val split = candidate("split_squat", "dumbbells", "quadriceps", laterality = ExerciseLateralityV2.UNILATERAL)
        val material = exerciseCatalogOptionLabel("dumbbells", "split_squat")

        assertEquals("$material · unilateral", line(split, anchor()))
        assertEquals(material, line(split, anchor(laterality = ExerciseLateralityV2.UNILATERAL)))
    }

    @Test
    fun elMusculoPrincipalSoloSaleSiEsOtro() {
        val thrust = candidate("hip_thrust", "barbell", "gluteus_maximus", laterality = ExerciseLateralityV2.BILATERAL)
        val material = exerciseCatalogOptionLabel("barbell", "hip_thrust")

        assertEquals("$material · ${exerciseCatalogMuscleLabel("gluteus_maximus")}", line(thrust, anchor()))
    }

    @Test
    fun sinImplementoLaLineaEmpiezaPorLaDiferenciaYConMayuscula() {
        val base = definition("leg_press", "Prensa de Piernas", "knee_dominant", listOf("quadriceps"), implements = emptyList())
        val press = base.copy(
            configurations = base.configurations.map { configuration ->
                configuration.copy(
                    profile = configuration.profile.copy( laterality = ExerciseLateralityV2.BILATERAL),
                )
            },
        )

        assertNull(line(press, anchor()))
    }

    @Test
    fun unaLateralidadQueNoSeDiceNoImpideDecirElMusculo() {
        val hack = candidate("hack_machine", "machine", "hamstrings", laterality = ExerciseLateralityV2.NOT_APPLICABLE)
        val material = exerciseCatalogOptionLabel("machine", "hack_machine")

        assertEquals("$material · ${exerciseCatalogMuscleLabel("hamstrings")}", line(hack, anchor()))
    }

    @Test
    fun laFilaDelPropioEjercicioActualNoLlevaDiferenciaYSinConfiguracionNoHayLinea() {
        // Aunque sus datos difieran de los del ancla, es el propio ejercicio actual: solo el implemento.
        val same = candidate("back_squat", "barbell", "quadriceps", laterality = ExerciseLateralityV2.UNILATERAL)

        assertEquals(exerciseCatalogOptionLabel("barbell", "back_squat"), line(same, anchor()))
        assertNull(catalogReplacementCandidateLine(same, null, anchor()))
    }
}
