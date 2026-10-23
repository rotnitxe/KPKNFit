package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.RepRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cabecera y lo que se puede hacer desde las opciones de una serie (LV11, A1; prototipo C1): «Serie 2 · 95 kg × 6 · RIR 2» sale de la
 * serie real o, si no está hecha, de su plan; y «Saltar» y «Borrar» solo valen cuando no dejan el estado en vivo desalineado.
 */
class SeriesOptionsModelTest {

    private fun exercise(id: String = "e", sets: List<ExerciseSet>) = Exercise(id = id, name = "Ejercicio $id", sets = sets)

    private fun planned(id: String, weight: Double? = 95.0, reps: Int? = 6, rir: Int? = 2) =
        ExerciseSet(id = id, targetReps = reps, targetRIR = rir, weight = weight)

    private val three = exercise(sets = listOf(planned("s0"), planned("s1"), planned("s2")))

    // ── Cabecera ───────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun anUndoneSetShowsItsPlan() {
        val summary = seriesOptionsSummary(three, 1, emptyMap())

        assertEquals("Serie 2", summary.title)
        assertEquals("95 kg × 6 · RIR 2", summary.detail)
    }

    @Test
    fun aDoneSetShowsWhatWasReallyDoneInsteadOfThePlan() {
        val done = CompletedSet(
            id = "c1",
            weight = 100.5,
            reps = 5,
            actualIntensityMode = IntensityMode.RIR,
            actualIntensityValue = 1.0,
        )

        val summary = seriesOptionsSummary(three, 1, mapOf("e_1" to done))

        assertEquals("100,5 kg × 5 · RIR 1", summary.detail)
    }

    @Test
    fun anEffortReportedAsRpeIsShownAsRpe() {
        val done = CompletedSet(id = "c", weight = 80.0, reps = 8, actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0)

        assertEquals("80 kg × 8 · RPE 8", seriesOptionsSummary(three, 0, mapOf("e_0" to done)).detail)
    }

    @Test
    fun aSetToFailureSaysSo() {
        val done = CompletedSet(id = "c", weight = 60.0, reps = 12, actualIntensityMode = IntensityMode.FAILURE)

        assertEquals("60 kg × 12 · al fallo", seriesOptionsSummary(three, 0, mapOf("e_0" to done)).detail)
    }

    @Test
    fun aPlanWithARepRangeAndNoWeightShowsOnlyWhatItHas() {
        val ranged = exercise(sets = listOf(ExerciseSet(id = "r", targetRepsRange = RepRange(min = 4, max = 6), targetRPE = 8.5)))

        assertEquals("4–6 · RPE 8,5", seriesOptionsSummary(ranged, 0, emptyMap()).detail)
    }

    @Test
    fun aTimedPlanShowsItsDuration() {
        val timed = exercise(sets = listOf(ExerciseSet(id = "t", targetDuration = 45)))

        assertEquals("45 s", seriesOptionsSummary(timed, 0, emptyMap()).detail)
    }

    @Test
    fun activeCustomUnitDoesNotShowAResidualDuration() {
        val custom = Exercise(
            id = "e",
            name = "Press Militar",
            trainingMode = com.example.kpkn.data.models.TrainingMode.CUSTOM,
            customUnit = "pasos",
            sets = listOf(ExerciseSet(id = "s", unitModeV2 = com.example.kpkn.data.models.UnitModeV2.CUSTOM,
                plannedTargetV2 = 12.0, targetDuration = 45)),
        )
        assertEquals("12 pasos", seriesOptionsSummary(custom, 0, emptyMap()).detail)
    }

    @Test
    fun aSetWithNothingPlannedHasAnEmptyDetailInsteadOfInventedData() {
        val bare = exercise(sets = listOf(ExerciseSet(id = "b")))

        assertEquals("Serie 1", seriesOptionsSummary(bare, 0, emptyMap()).title)
        assertEquals("", seriesOptionsSummary(bare, 0, emptyMap()).detail)
    }

    // ── Qué se puede hacer ─────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aPendingSetCanBeSkippedAndDeletedWhenNothingBehindItIsTouched() {
        val availability = seriesOptionsAvailability(three, 1, completedSets = emptyMap(), omittedSetKeys = emptySet())

        assertTrue(availability.canAdd)
        assertTrue(availability.canSkip)
        assertTrue(availability.canDelete)
    }

    @Test
    fun aDoneSetCanNeitherBeSkippedNorDeleted() {
        val availability = seriesOptionsAvailability(
            three, 0, completedSets = mapOf("e_0" to CompletedSet(id = "c", weight = 90.0, reps = 6)), omittedSetKeys = emptySet(),
        )

        assertFalse(availability.canSkip)
        assertFalse(availability.canDelete)
        assertTrue("añadir sigue valiendo", availability.canAdd)
    }

    @Test
    fun aSetAlreadyOmittedCannotBeSkippedAgain() {
        val availability = seriesOptionsAvailability(three, 1, emptyMap(), omittedSetKeys = setOf("e_1"))

        assertFalse(availability.canSkip)
    }

    @Test
    fun deletingASetIsRefusedWhenThatWouldShiftSomethingDoneOmittedOrTypedBehindIt() {
        val done = mapOf("e_2" to CompletedSet(id = "c", weight = 90.0, reps = 6))

        assertFalse("hay una serie hecha detrás", seriesOptionsAvailability(three, 1, done, emptySet()).canDelete)
        assertFalse("hay una serie omitida detrás", seriesOptionsAvailability(three, 1, emptyMap(), setOf("e_2")).canDelete)
        assertFalse(
            "hay un borrador escrito detrás",
            seriesOptionsAvailability(three, 1, emptyMap(), emptySet(), setDraftKeys = listOf("e_2")).canDelete,
        )
        assertFalse(
            "hay una carga elegida a mano detrás",
            seriesOptionsAvailability(three, 1, emptyMap(), emptySet(), manualLoadKeys = listOf("e_2_L")).canDelete,
        )
        assertTrue("la última sí se puede borrar", seriesOptionsAvailability(three, 2, emptyMap(), emptySet()).canDelete)
    }

    @Test
    fun theOnlySetOfAnExerciseCannotBeDeleted() {
        val single = exercise(sets = listOf(planned("only")))

        assertFalse(seriesOptionsAvailability(single, 0, emptyMap(), emptySet()).canDelete)
    }

    @Test
    fun aCardioExerciseOffersNoNewSet() {
        val cardio = Exercise(
            id = "run",
            name = "Cinta",
            cardioDetails = CardioDetails(type = CardioType.TREADMILL),
            sets = listOf(ExerciseSet(id = "run-0")),
        )

        assertFalse(seriesOptionsAvailability(cardio, 0, emptyMap(), emptySet()).canAdd)
    }
}
