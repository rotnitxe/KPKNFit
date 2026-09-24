package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.templates.SessionTemplateEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato: el split es una elección real, no una etiqueta.
 *
 * Un split visible (`SPLIT_TEMPLATES` + `isVisibleForApplication`) restringe
 * qué grupos entran cada día ANTES de rellenar las sesiones: dos splits
 * distintos producen composiciones de ejercicios distintas. Un split que no
 * concuerda con los días elegidos o con el material disponible se rechaza con
 * motivo y no reescribe ninguna receta.
 */
class OnboardingSplitSelectionTest {
    private val catalog get() = CatalogCompositionTestSupport.catalog
    private fun personalizer() = SimpleCyclePersonalizer(InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } })

    private fun machineInput(splitId: String) = PersonalizerInput(
        catalogEntryId = "native:machine-muscle",
        focus = TrainingFocus.FULL_BODY,
        frequency = 4,
        weekdays = listOf(1, 2, 4, 5),
        equipment = setOf("machine"),
        level = CatalogLevel.INTERMEDIATE,
        availableMinutes = 90,
        splitId = splitId,
    )

    private fun sessionsOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }

    private fun directMuscles(session: Session): Set<String> {
        val lookup = catalog.toLegacyConfigurationLookup()
        val volume = VolumeCalculator.calculateRoleSeparatedMuscleVolume(listOf(session), lookup.values.toList())
        return volume.filterValues { it.directSets > 0.0 }.keys
    }

    @Test
    fun differentSplitsProduceDifferentExerciseCompositionNotJustLabels() {
        val upperLowerResult = personalizer().personalize("ul", machineInput("ul_x4"))
        val pushPullResult = personalizer().personalize("pp", machineInput("push_pull_x4"))
        val upperLower = requireNotNull(upperLowerResult.program) { upperLowerResult.report.limitations.toString() }
        val pushPull = requireNotNull(pushPullResult.program) { pushPullResult.report.limitations.toString() }

        val ulDayOne = sessionsOf(upperLower).first { it.dayOfWeek == 1 }
        val ppDayOne = sessionsOf(pushPull).first { it.dayOfWeek == 1 }

        // Cambian las etiquetas...
        assertNotEquals(ulDayOne.name, ppDayOne.name)
        // ...y sobre todo la composición de ejercicios, no solo los nombres.
        val ulMuscles = directMuscles(ulDayOne)
        val ppMuscles = directMuscles(ppDayOne)
        assertTrue("Torso trabaja la espalda", "Dorsales" in ulMuscles)
        assertFalse("Torso no incluye cuádriceps", "Cuádriceps" in ulMuscles)
        assertTrue("Torso incluye trabajo directo de bíceps", "Bíceps" in ulMuscles)
        assertTrue("Empuje + Cuádriceps integra la pierna", "Cuádriceps" in ppMuscles)
        assertFalse("Empuje + Cuádriceps no incluye tracciones de espalda", "Dorsales" in ppMuscles)
        assertFalse("Empuje + Cuádriceps no incluye bíceps", "Bíceps" in ppMuscles)
        assertNotEquals(
            "Los ejercicios prescritos del día 1 son distintos",
            ulDayOne.exercises.map { it.catalogConfigurationId },
            ppDayOne.exercises.map { it.catalogConfigurationId },
        )

        // Sigue siendo ejecutable y dentro de los límites MAV/MRV y de frecuencia.
        (sessionsOf(upperLower) + sessionsOf(pushPull)).forEach { session ->
            assertTrue(session.name, SessionTemplateEngine.sessionHasCompleteExecutableContent(session))
        }
        listOf(upperLowerResult, pushPullResult).forEach { result ->
            result.report.muscles.forEach { row ->
                assertTrue("${row.muscle}", row.directSets + row.indirectSets <= minOf(row.mav, row.mrv) + 0.001)
                assertTrue("${row.muscle} frecuencia", row.frequency <= 3)
            }
        }
    }

    @Test
    fun namedSplitIsPersistedAsARealChoice() {
        val result = personalizer().personalize("ul", machineInput("ul_x4"))
        val program = requireNotNull(result.program) { result.report.limitations.toString() }
        assertEquals("ul_x4", program.selectedSplitId)
        assertEquals(listOf("Torso", "Pierna", "Torso", "Pierna"), sessionsOf(program).map { it.name })
        assertNotNull("La receta fuente se conserva", program.sourceRecipe)
    }

    @Test
    fun splitWithMismatchedTrainingDaysIsRejectedWithReason() {
        val result = personalizer().personalize(
            "ul-3",
            machineInput("ul_x4").copy(frequency = 3, weekdays = listOf(1, 3, 5)),
        )
        assertNull(result.program)
        val reason = result.report.limitations.joinToString(" ")
        assertTrue(reason, reason.contains("Upper / Lower x4"))
        assertTrue(reason, reason.contains("4 días de entrenamiento"))
        assertTrue(reason, reason.contains("has elegido 3"))
    }

    @Test
    fun splitRequiringUnavailableEquipmentIsRejectedWithReason() {
        val input = PersonalizerInput(
            catalogEntryId = "native:home-training",
            focus = TrainingFocus.FULL_BODY,
            frequency = 3,
            weekdays = listOf(1, 3, 5),
            equipment = setOf("dumbbells"),
            level = CatalogLevel.INTERMEDIATE,
            availableMinutes = 90,
            splitId = "custom",
            splitPattern = listOf("Pantorrillas", "Descanso", "Piernas", "Descanso", "Brazos", "Descanso", "Descanso"),
            splitName = "Casa",
        )
        // Sin split, el mismo material permite componer la semana.
        val withoutSplit = personalizer().personalize(
            "casa-base",
            input.copy(splitId = null, splitPattern = emptyList(), splitName = null),
        )
        assertNotNull(withoutSplit.report.limitations.toString(), withoutSplit.program)

        // Con el split, el día "Pantorrillas" exige material que no se tiene.
        val result = personalizer().personalize("casa-split", input)
        assertNull(result.program)
        val reason = result.report.limitations.joinToString(" ")
        assertTrue(reason, reason.contains("Casa"))
        assertTrue(reason, reason.contains("Pantorrillas"))
        assertTrue(reason, reason.contains("material"))
    }
}
