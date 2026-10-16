package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

/** Pure ownership contract. Defined for later targeted execution; no host run is required by this patch. */
class WorkoutSetInputOwnershipTest {
    private val original = Exercise("slot", "Press militar", canonicalExerciseId = "overhead_press",
        catalogRevision = "rev", catalogDefinitionId = "overhead_press", catalogConfigurationId = "overhead_press__barbell",
        sets = listOf(ExerciseSet("s1", targetReps = 6, targetRIR = 2, loadModeV2 = LoadModeV2.LOAD)))
    private fun replace(source: Exercise, id: String) = replacementExerciseFor(source,
        ExerciseMuscleInfo(id = id, name = id, equipment = "Mancuerna", catalogRevision = "rev", catalogDefinitionId = id, catalogConfigurationId = id))

    @Test fun sameSlotAndSetIdsDoNotMakeSuccessiveExercisesTheSameInputOwner() {
        val arnold = replace(original, "arnold_press__dumbbell")
        val seated = replace(arnold, "seated_press__barbell")
        assertEquals(original.id, seated.id)
        assertEquals(original.sets.map { it.id }, seated.sets.map { it.id })
        assertNotEquals(workoutSetInputIdentity(original), workoutSetInputIdentity(arnold))
        assertNotEquals(workoutSetInputIdentity(arnold), workoutSetInputIdentity(seated))
        assertFalse(workoutSetDraftSourceIsCurrent(arnold, original, 0))
        assertFalse(workoutSetDraftSourceIsCurrent(seated, arnold, 0))
        assertFalse(workoutSetDraftSourceIsCurrent(seated, original, 0))
        assertTrue(workoutSetDraftSourceIsCurrent(seated, seated, 0))
    }
    @Test fun normalLoadRepsIntensityAndSmartLoadEditsKeepTheirInputOwner() {
        val edited = original.copy(reference1RM = 100.0, restTime = 180,
            sets = listOf(original.sets.single().copy(loadModeV2 = LoadModeV2.BODYWEIGHT, targetReps = 8, targetRIR = 1, weight = 15.0)))
        assertEquals(workoutSetInputIdentity(original), workoutSetInputIdentity(edited))
        assertTrue(workoutSetDraftSourceIsCurrent(edited, original, 0))
    }
    @Test fun anotherSlotOrAReindexedSetCannotReceiveAnOldCardDraft() {
        assertFalse(workoutSetDraftSourceIsCurrent(original.copy(id = "other"), original, 0))
        assertFalse(workoutSetDraftSourceIsCurrent(original.copy(sets = listOf(ExerciseSet("different"))), original, 0))
        assertFalse(workoutSetDraftSourceIsCurrent(original, original, 1))
        assertFalse(workoutSetDraftSourceIsCurrent(null, original, 0))
    }
    @Test fun replacementKeepsThePlanDoseAndResolvesItsOwnLoadMode() {
        val edited = original.copy(sets = List(4) { index -> original.sets.single().copy(id = "s$index", targetRepsRange = RepRange(4, 6),
            restAfterSeconds = 150, loadModeV2 = LoadModeV2.BODYWEIGHT, weight = 90.0) }, restTime = 120)
        val replacement = replace(edited, "arnold_press__dumbbell")
        assertEquals(4, replacement.sets.size)
        assertEquals(120, replacement.restTime)
        replacement.sets.forEach { set ->
            assertEquals(RepRange(4, 6), set.targetRepsRange)
            assertEquals(2, set.targetRIR)
            assertEquals(150, set.restAfterSeconds)
            assertEquals(LoadModeV2.LOAD, set.loadModeV2)
            assertNull(set.weight)
        }
    }
}
