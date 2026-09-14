package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutF1SafeReadRegressionTest {

    @Test
    fun restLiveCardExerciseName_skipsBlankAndNull() {
        assertNull(restLiveCardExerciseName(null))
        assertNull(restLiveCardExerciseName(WorkoutRestModalState(exerciseName = "")))
        assertEquals(
            "Press banca",
            restLiveCardExerciseName(WorkoutRestModalState(exerciseName = "Press banca")),
        )
    }

    @Test
    fun firstPrepMemberOrNull_doesNotAssumeNonEmpty() {
        assertNull(firstPrepMemberOrNull(emptyList()))
        val squat = Exercise(id = "sq", name = "Sentadilla")
        assertEquals("sq", firstPrepMemberOrNull(listOf(squat))?.id)
    }

    @Test
    fun cardioDetailsOrNull_isSafeRead() {
        assertNull(cardioDetailsOrNull(Exercise(id = "s", name = "Sentadilla")))
        val details = CardioDetails(type = CardioType.TREADMILL)
        assertEquals(
            details,
            cardioDetailsOrNull(Exercise(id = "c", name = "Cinta", cardioDetails = details)),
        )
    }

    @Test
    fun overlayCardioTimerTick_onlyOverlaysRunningState() {
        val paused = CardioTimerState(
            exerciseId = "c",
            totalSeconds = 60,
            remainingSeconds = 40,
            elapsedSeconds = 20,
            status = CardioExecutionStatus.PAUSED,
        )
        assertEquals(paused, overlayCardioTimerTick(paused, remainingSeconds = 1, elapsedSeconds = 99))
        val running = paused.copy(status = CardioExecutionStatus.RUNNING)
        val overlaid = overlayCardioTimerTick(running, remainingSeconds = 10, elapsedSeconds = 50)
        assertEquals(10, overlaid?.remainingSeconds)
        assertEquals(50, overlaid?.elapsedSeconds)
        assertNull(overlayCardioTimerTick(null, 1, 1))
    }

    @Test
    fun imbalanceNoticeText_andHistorySheetDbId_areNullSafe() {
        assertNull(imbalanceNoticeText(null))
        assertNull(imbalanceNoticeText(""))
        assertEquals("L/R", imbalanceNoticeText("L/R"))
        assertNull(historySheetDbId(showHistorySheet = false, historySheetExerciseDbId = "ex"))
        assertNull(historySheetDbId(showHistorySheet = true, historySheetExerciseDbId = null))
        assertEquals("ex", historySheetDbId(showHistorySheet = true, historySheetExerciseDbId = "ex"))
    }

    @Test
    fun inlineRestAndAddSet_doNotRequireBangBang() {
        assertNull(inlineRestRemainingOrNull(null, 60))
        assertNull(inlineRestRemainingOrNull(12, 0))
        assertEquals(12, inlineRestRemainingOrNull(12, 60))
        var clicked = false
        assertFalse(invokeAddSetAction(null))
        assertTrue(invokeAddSetAction { clicked = true })
        assertTrue(clicked)
    }
}
