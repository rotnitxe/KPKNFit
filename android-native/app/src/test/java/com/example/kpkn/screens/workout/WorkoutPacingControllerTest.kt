package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.services.workout.WorkoutVoiceVerbosityGate
import com.example.kpkn.services.workout.VoiceAnnouncementKind
import com.example.kpkn.data.models.VoiceVerbosity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPacingControllerTest {

    @Test
    fun crossedThreshold_detectsSkippedTick() {
        assertFalse(SessionTimeCues.crossed(null, 899, SessionTimeCues.THRESHOLD_15))
        assertTrue(SessionTimeCues.crossed(901, 899, SessionTimeCues.THRESHOLD_15))
        assertTrue(SessionTimeCues.crossed(1, -4, SessionTimeCues.THRESHOLD_0))
        assertFalse(SessionTimeCues.crossed(500, 400, SessionTimeCues.THRESHOLD_5))
    }

    @Test
    fun skippedTick_fires15MinCue_asEssential() = runTest {
        var now = 0L
        var state = baseState(targetMinutes = 20, mode = PacingAlertMode.FINAL)
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
        )
        controller.startSessionTimer(20 * 60)
        runCurrent()
        now = 301_000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(spoken.any { it.first == SessionTimeCues.REMAINING_15 && it.second })
        assertEquals(SessionTimeCues.REMAINING_15, state.pacingAlertMessage)
        assertEquals(899, controller.sessionTimeRemainingSeconds.value)
    }

    @Test
    fun finalMode_doesNotSpeakOneMinute() = runTest {
        var now = 0L
        var state = baseState(targetMinutes = 20, mode = PacingAlertMode.FINAL)
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
        )
        controller.startSessionTimer(20 * 60)
        runCurrent()
        now = (20 * 60 - 59) * 1000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertFalse(spoken.any { it.first == SessionTimeCues.REMAINING_1 })
    }

    @Test
    fun voiceOff_doesNotSpeak() = runTest {
        var now = 0L
        var state = baseState(targetMinutes = 20, mode = PacingAlertMode.FINAL)
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = false,
            spoken = spoken,
        )
        controller.startSessionTimer(20 * 60)
        runCurrent()
        now = 301_000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(spoken.isEmpty())
        assertEquals(SessionTimeCues.REMAINING_15, state.pacingAlertMessage)
    }

    @Test
    fun overtime_setsNegativeRemaining_andExhaustedCue() = runTest {
        var now = 0L
        var state = baseState(targetMinutes = 20, mode = PacingAlertMode.FINAL)
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
        )
        controller.startSessionTimer(20 * 60)
        runCurrent()
        now = (20 * 60 + 8) * 1000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue((controller.sessionTimeRemainingSeconds.value ?: 0) < 0)
        assertTrue(spoken.any { it.first == SessionTimeCues.EXHAUSTED && it.second })
        assertEquals(SessionTimeCues.EXHAUSTED, state.pacingAlertMessage)
    }

    @Test
    fun lastMinute_isNotExhausted() = runTest {
        var now = 0L
        var state = baseState(targetMinutes = 20, mode = PacingAlertMode.SOFT).copy(
            session = Session(
                id = "s",
                name = "n",
                targetDurationMinutes = 20,
                exercises = listOf(
                    Exercise(id = "e", name = "x", sets = listOf(ExerciseSet(id = "a"), ExerciseSet(id = "b"))),
                ),
            ),
        )
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
            visible = { it.session?.exercises.orEmpty() },
        )
        controller.startSessionTimer(20 * 60)
        runCurrent()
        now = (20 * 60 - 59) * 1000L
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(SessionTimeCues.REMAINING_1, state.pacingAlertMessage)
        assertTrue(spoken.any { it.first == SessionTimeCues.REMAINING_1 })
        assertFalse(spoken.any { it.first == SessionTimeCues.EXHAUSTED })
        assertTrue(state.coachPaceAlert != "excedido")
    }

    @Test
    fun localBudget_onlySpeaksInStrict() = runTest {
        var state = baseState(targetMinutes = 45, mode = PacingAlertMode.SOFT)
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
        )
        controller.checkLocalBudgetGuide("ex:1", "Press", 0.8f, true)
        assertTrue(spoken.isEmpty())

        state = state.copy(pacingAlertMode = PacingAlertMode.STRICT)
        controller.checkLocalBudgetGuide("ex:1", "Press", 0.8f, true)
        assertEquals("75 por ciento de Press" to false, spoken.single())
        controller.checkLocalBudgetGuide("ex:1", "Press", 1f, true)
        assertEquals("Press: tiempo agotado" to true, spoken.last())
    }

    @Test
    fun adjustRest_usesTargetDurationMinutes() = runTest {
        var state = WorkoutUiState(
            session = Session(id = "s", name = "n"),
            startTimeMs = 0L,
            targetDurationMinutes = 20,
        )
        var now = 10 * 60 * 1000L
        val controller = controller(
            nowMs = { now },
            getState = { state },
            updateState = { state = it(state) },
            visible = {
                listOf(
                    Exercise(id = "e", name = "x", sets = listOf(ExerciseSet(id = "a"), ExerciseSet(id = "b"))),
                )
            },
        )
        assertEquals(60, controller.adjustRestTimeForPace(90))
    }

    @Test
    fun resolveEffectiveTarget_customZeroClearsProgramLimit() {
        assertEquals(
            null,
            resolveEffectiveSessionTargetMinutes(
                customTargetDurationMinutes = 0,
                targetDurationMinutes = 60,
                sessionTargetDurationMinutes = 60,
            ),
        )
        assertEquals(
            45,
            resolveEffectiveSessionTargetMinutes(
                customTargetDurationMinutes = 45,
                targetDurationMinutes = 60,
                sessionTargetDurationMinutes = 60,
            ),
        )
        assertEquals(
            60,
            resolveEffectiveSessionTargetMinutes(
                customTargetDurationMinutes = null,
                targetDurationMinutes = null,
                sessionTargetDurationMinutes = 60,
            ),
        )
    }

    @Test
    fun evaluatePace_marksAhead_withoutChipText() = runTest {
        val exercises = listOf(
            Exercise(
                id = "e",
                name = "x",
                sets = (0..9).map { ExerciseSet(id = "s$it") },
            ),
        )
        var state = WorkoutUiState(
            session = Session(id = "s", name = "n", targetDurationMinutes = 20, exercises = exercises),
            startTimeMs = 0L,
            targetDurationMinutes = 20,
            pacingAlertMode = PacingAlertMode.FINAL,
            completedSets = (0..4).associate { idx -> "e_$idx" to com.example.kpkn.data.models.CompletedSet(id = "c$idx") },
        )
        val controller = controller(
            nowMs = { 6 * 60 * 1000L },
            getState = { state },
            updateState = { state = it(state) },
            visible = { exercises },
        )
        controller.checkPaceCoachAlert()
        assertEquals("adelantado", state.coachPaceAlert)
        assertTrue(state.pacingAlertMessage == null || state.pacingAlertMessage in SessionTimeCues.ALL)
    }

    @Test
    fun evaluatePace_marksLate_withoutSlowChipText() = runTest {
        val exercises = listOf(
            Exercise(
                id = "e",
                name = "x",
                sets = (0..9).map { ExerciseSet(id = "s$it") },
            ),
        )
        var state = WorkoutUiState(
            session = Session(id = "s", name = "n", targetDurationMinutes = 20, exercises = exercises),
            startTimeMs = 0L,
            targetDurationMinutes = 20,
            pacingAlertMode = PacingAlertMode.SOFT,
            completedSets = mapOf("e_0" to com.example.kpkn.data.models.CompletedSet(id = "c0")),
        )
        val spoken = mutableListOf<Pair<String, Boolean>>()
        val controller = controller(
            nowMs = { 6 * 60 * 1000L },
            getState = { state },
            updateState = { state = it(state) },
            voiceOn = true,
            spoken = spoken,
            visible = { exercises },
        )
        controller.checkPaceCoachAlert()
        assertEquals("retrasado", state.coachPaceAlert)
        assertFalse(state.pacingAlertMessage?.startsWith("Ritmo lento") == true)
        assertTrue(spoken.any { it.first == "Ritmo lento" })
    }

    @Test
    fun clearSessionTimeLimit_thisTime_stopsCountdown_keepsProgramDuration() = runTest {
        var state = baseState(targetMinutes = 45, mode = PacingAlertMode.FINAL)
        val controller = controller(
            getState = { state },
            updateState = { state = it(state) },
        )
        controller.setAbsoluteSessionTimeLimit(45)
        controller.startSessionTimer(45 * 60)
        runCurrent()
        assertTrue(controller.sessionTimeRemainingSeconds.value != null)

        controller.clearSessionTimeLimit(persistToSession = false)
        runCurrent()

        assertEquals(null, controller.sessionTimeRemainingSeconds.value)
        assertEquals(0, state.customTargetDurationMinutes)
        assertEquals(45, state.session?.targetDurationMinutes)
        assertEquals(
            null,
            resolveEffectiveSessionTargetMinutes(
                state.customTargetDurationMinutes,
                state.targetDurationMinutes,
                state.session?.targetDurationMinutes,
            ),
        )
    }

    @Test
    fun essentialVerbosity_allowsSessionCues_blocksSlowPace() {
        assertTrue(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.ESSENTIAL, VoiceAnnouncementKind.ESSENTIAL),
        )
        assertFalse(
            WorkoutVoiceVerbosityGate.allows(VoiceVerbosity.ESSENTIAL, VoiceAnnouncementKind.COMPLETE),
        )
    }

    private fun baseState(targetMinutes: Int, mode: PacingAlertMode) = WorkoutUiState(
        session = Session(id = "s", name = "n", targetDurationMinutes = targetMinutes),
        startTimeMs = 0L,
        targetDurationMinutes = targetMinutes,
        pacingAlertMode = mode,
    )

    private fun kotlinx.coroutines.test.TestScope.controller(
        nowMs: () -> Long = { 0L },
        getState: () -> WorkoutUiState,
        updateState: ((WorkoutUiState) -> WorkoutUiState) -> Unit,
        voiceOn: Boolean = false,
        spoken: MutableList<Pair<String, Boolean>> = mutableListOf(),
        visible: (WorkoutUiState) -> List<Exercise> = { emptyList() },
    ) = WorkoutPacingController(
        scope = backgroundScope,
        getState = getState,
        updateState = updateState,
        persistOngoingState = {},
        visibleExercises = visible,
        isVoiceActive = { voiceOn },
        speakViaVoice = { text, essential -> spoken += text to essential },
        nowMs = nowMs,
        isAppInForeground = { true },
    )
}
