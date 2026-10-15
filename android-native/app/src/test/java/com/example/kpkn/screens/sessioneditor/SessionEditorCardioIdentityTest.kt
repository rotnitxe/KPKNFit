package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.exercises.catalogv2.catalogV2SelectionIssues
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.domain.training.generator.CardioBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SE10: el cardio que añade el editor (addCardioToPart) tiene que pasar el cortafuegos del catálogo.
 * Antes salía con las cinco piezas de identidad vacías y exerciseDbId = id del catálogo de cardio (cardio_treadmill),
 * y «Guardar y salir» se bloqueaba. La convención buena es la del generador: custom:cardio-<tipo>.
 */
class SessionEditorCardioIdentityTest {

    private val treadmill = CardioCatalog.items.first { it.type == CardioType.TREADMILL }
    private val rowMachine = CardioCatalog.items.first { it.type == CardioType.ROW_MACHINE }

    @Test
    fun cardio_added_from_the_catalog_passes_the_catalog_gate() {
        val session = sessionWithCardio(newCardioExerciseFor(id = "cardio-new", item = treadmill))

        assertEquals(emptyList<String>(), session.normalizeSession().catalogV2SelectionIssues().map { it.code })
    }

    @Test
    fun cardio_replaced_in_place_passes_the_catalog_gate() {
        val original = newCardioExerciseFor(id = "cardio-existing", item = treadmill)

        val replaced = original.withCardioItem(rowMachine)

        assertEquals(emptyList<String>(), sessionWithCardio(replaced).normalizeSession().catalogV2SelectionIssues().map { it.code })
        assertEquals("cardio-existing", replaced.id)
        assertEquals(CardioType.ROW_MACHINE, replaced.cardioDetails?.type)
        assertEquals("Remo ergómetro", replaced.name)
    }

    @Test
    fun cardio_identity_is_the_generator_convention_for_every_catalog_item() {
        CardioCatalog.items.forEach { item ->
            val fromEditor = newCardioExerciseFor(id = "cardio-${item.id}", item = item)
            val fromGenerator = CardioBuilder.exercise(id = "cardio-${item.id}", details = CardioDetails(type = item.type))

            assertEquals("identity of ${item.id}", fromGenerator.exerciseDbId, fromEditor.exerciseDbId)
            assertEquals(fromGenerator.exerciseId, fromEditor.exerciseId)
            assertEquals(fromGenerator.canonicalExerciseId, fromEditor.canonicalExerciseId)
            assertTrue(fromEditor.exerciseDbId.orEmpty().startsWith("custom:cardio-"))
        }
    }

    @Test
    fun cardio_generated_by_the_generator_passes_the_catalog_gate() {
        val generated = CardioBuilder.exercise(
            id = "generated",
            details = CardioDetails(type = CardioType.TREADMILL, intensity = CardioIntensity.MEDIA, targetDurationSeconds = 20 * 60),
        )

        assertEquals(emptyList<String>(), sessionWithCardio(generated).normalizeSession().catalogV2SelectionIssues().map { it.code })
    }

    @Test
    fun replacing_cardio_type_keeps_duration_and_intervals() {
        val blocks = listOf(
            CardioIntervalBlock(id = "w", type = CardioBlockType.WORK, durationSeconds = 30, intensityLevel = 8),
            CardioIntervalBlock(id = "r", type = CardioBlockType.RECOVER, durationSeconds = 30, intensityLevel = 3),
        )
        val intervals = newCardioExerciseFor(id = "cardio-intervals", item = treadmill).copy(
            cardioDetails = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = blocks, intervalRounds = 4),
        )

        val replaced = intervals.withCardioItem(rowMachine)

        assertEquals(blocks, replaced.cardioDetails?.intervalBlocks)
        assertEquals(4, replaced.cardioDetails?.intervalRounds)
        assertEquals(240, replaced.cardioDetails?.targetDurationSeconds)
        assertEquals(4, replaced.targetDurationMinutes)
    }

    private fun sessionWithCardio(exercise: Exercise): Session = Session(
        id = "session-sab",
        name = "Pierna A",
        parts = listOf(
            SessionPart(
                id = "cardio-part",
                name = "Espacio de cardio",
                isCardioGroup = true,
                exercises = listOf(exercise),
            ),
        ),
    )
}
