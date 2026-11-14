package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.screens.sessioneditor.freshPlannedCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutPrescriptionDeltaTest {
    @Test
    fun newPrescriptionSlotSkipsRetainedAndRemovedSlotIds() {
        val set0 = ExerciseSet(id = "set-0", plannedSlotId = "push|press|set:0")
        val set1 = ExerciseSet(id = "set-1", plannedSlotId = "push|press|set:1")
        val set2 = ExerciseSet(id = "set-2", plannedSlotId = "push|press|set:2")
        val exercise = Exercise(
            id = "press",
            name = "Press banca",
            recipeDayId = "push",
            recipeSlotId = "press",
            sets = listOf(set0, set1, set2),
        )
        // The editor's add action copies the last set and changes its ID, retaining set:2.
        val copiedAddition = set2.freshPlannedCopy(newId = { "set-added" }, preserveEmptySlot = false)
        val edited = exercise.copy(sets = listOf(set0, set2, copiedAddition))

        val normalized = assignStablePrescriptionSlots(exercise, edited)

        assertEquals(listOf("push|press|set:0", "push|press|set:2", "push|press|set:3"), normalized.sets.map { it.plannedSlotId })
    }

    @Test
    fun futureOccurrencesReceiveOnlyPrescriptionDelta() {
        val before = ExerciseSet(id = "planned", targetReps = 10, targetRPE = 8.0, targetRIR = 3, weight = 40.0)
        val after = before.copy(targetReps = 12, targetRPE = 9.0, weight = 45.0)
        val future = before.copy(
            id = "future",
            targetReps = 8,
            targetRPE = 7.0,
            targetRIR = 2,
            weight = 60.0,
            completedReps = 6,
            completedRPE = 10.0,
        )
        val delta = PrescriptionSetDelta(
            setOrdinal = 0,
            targetSetId = "planned",
            before = SetPrescriptionSnapshot.from(before),
            after = SetPrescriptionSnapshot.from(after),
        )

        val updated = delta.applyTo(future, source = false)!!

        assertEquals(10, updated.targetReps)
        assertEquals(8.0, updated.targetRPE!!, 0.0)
        assertEquals(2, updated.targetRIR)
        assertEquals(65.0, updated.weight!!, 0.0)
        assertEquals("future", updated.id)
        assertEquals(6, updated.completedReps)
        assertEquals(10.0, updated.completedRPE!!, 0.0)
    }

    @Test
    fun sourceConflictsRejectAndCompletedFieldsRemainUntouched() {
        val before = ExerciseSet(id = "planned", targetReps = 10, targetRPE = 8.0)
        val after = before.copy(targetReps = 12)
        val delta = PrescriptionSetDelta(
            setOrdinal = 0,
            targetSetId = "planned",
            before = SetPrescriptionSnapshot.from(before),
            after = SetPrescriptionSnapshot.from(after),
        )

        assertNull(delta.applyTo(before.copy(targetReps = 11), source = true))
        assertEquals(12, delta.applyTo(before, source = true)!!.targetReps)
    }

    @Test
    fun futureOccurrencesReceiveRestAndPlannedTechniqueChanges() {
        val before = ExerciseSet(
            id = "planned",
            targetReps = 10,
            restAfterSeconds = 90,
            plannedIntensityTechniques = listOf(PlannedTechnique(type = TechniqueType.DROP_SET, params = mapOf("drops" to "1"))),
        )
        val after = before.copy(
            restAfterSeconds = 150,
            plannedIntensityTechniques = listOf(PlannedTechnique(type = TechniqueType.REST_PAUSE, params = mapOf("miniSets" to "2"))),
        )
        val future = before.copy(
            id = "future",
            restAfterSeconds = 60,
            plannedIntensityTechniques = emptyList(),
            completedReps = 8,
        )
        val delta = PrescriptionSetDelta(
            setOrdinal = 0,
            targetSetId = before.id,
            before = SetPrescriptionSnapshot.from(before),
            after = SetPrescriptionSnapshot.from(after),
            targetPlannedSlotId = "day|slot|set:0",
        )

        val updated = delta.applyTo(future.copy(plannedSlotId = "day|slot|set:0"), source = false)!!

        assertEquals(150, updated.restAfterSeconds)
        assertEquals(after.plannedIntensityTechniques, updated.plannedIntensityTechniques)
        assertEquals(8, updated.completedReps)
    }
}
