package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorLoadAnchorTest {

    @Test
    fun firstWorkingSetUsesPreviousSessionNotToday() {
        val today = RelatorSessionSetMemory(1, 80.0, 8)
        val history = RelatorSessionSetMemory(1, 100.0, 5)
        val anchor = resolveRelatorLoadAnchor(
            currentSetIdx = 0,
            restPhase = false,
            sessionPrevious = today,
            historyFirst = history,
        )
        assertEquals(100.0, anchor.compareWeightKg)
        assertTrue(anchor.fromPreviousSession)
    }

    @Test
    fun laterSetUsesExactPreviousTodayAndIgnoresHistory() {
        val today = RelatorSessionSetMemory(2, 82.5, 7)
        val history = RelatorSessionSetMemory(1, 100.0, 5)
        val anchor = resolveRelatorLoadAnchor(
            currentSetIdx = 2,
            restPhase = false,
            sessionPrevious = today,
            historyFirst = history,
        )
        assertEquals(82.5, anchor.compareWeightKg)
        assertFalse(anchor.fromPreviousSession)
    }

    @Test
    fun restOnFirstSetUsesTheSetJustFinished() {
        val today = RelatorSessionSetMemory(1, 80.0, 8)
        val history = RelatorSessionSetMemory(1, 100.0, 5)
        val anchor = resolveRelatorLoadAnchor(
            currentSetIdx = 0,
            restPhase = true,
            sessionPrevious = today,
            historyFirst = history,
        )
        assertEquals(80.0, anchor.compareWeightKg)
        assertFalse(anchor.fromPreviousSession)
    }

    @Test
    fun previousWorkingSetTodayPicksExactIndexNotMax() {
        val sets = mapOf(
            "press_0" to completed(80.0, 8),
            "press_1" to completed(85.0, 6),
        )
        val memory = previousWorkingSetToday(
            completedSets = sets,
            exerciseId = "press",
            currentSetIdx = 2,
            side = null,
            restPhase = false,
        )
        val firstPrev = previousWorkingSetToday(sets, "press", 1, null, restPhase = false)
        assertEquals(1, firstPrev?.setNumber)
        assertEquals(80.0, firstPrev?.weightKg)
    }

    @Test
    fun failedSetCautionOnNextSetOfSameExercise() {
        val sets = mapOf("press_0" to completed(80.0, 3, failed = true))
        val caution = resolveFailedSetCaution(
            completedSets = sets,
            exerciseIds = listOf("press", "row"),
            currentExerciseId = "press",
            currentSetIdx = 1,
            restPhase = false,
        )
        assertEquals("press", caution?.sourceExerciseId)
        assertTrue(caution!!.sameExercise)
        assertEquals(1, caution.sourceSetNumber)
    }

    @Test
    fun failedSetCautionOnFirstSetOfNextExercise() {
        val sets = mapOf("press_2" to completed(90.0, 2, failed = true))
        val caution = resolveFailedSetCaution(
            completedSets = sets,
            exerciseIds = listOf("press", "row"),
            currentExerciseId = "row",
            currentSetIdx = 0,
            restPhase = false,
        )
        assertEquals("press", caution?.sourceExerciseId)
        assertFalse(caution!!.sameExercise)
    }

    @Test
    fun noCautionWhenPreviousSetWasClean() {
        val sets = mapOf("press_0" to completed(80.0, 8, failed = false))
        assertNull(
            resolveFailedSetCaution(
                completedSets = sets,
                exerciseIds = listOf("press"),
                currentExerciseId = "press",
                currentSetIdx = 1,
                restPhase = false,
            ),
        )
    }

    private fun completed(weight: Double, reps: Int, failed: Boolean = false) = CompletedSet(
        id = "s-$weight-$reps",
        weight = weight,
        reps = reps,
        isFailedSet = failed,
    )
}
