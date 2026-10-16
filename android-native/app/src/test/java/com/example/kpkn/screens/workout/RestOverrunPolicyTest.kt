package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Descanso cumplido» (LV6, F6): cuándo nace la capa que queda al llegar a 0, cómo cuenta el tiempo extra desde el `endsAtMs` guardado
 * y cuándo se va. La capa no toca el cierre del descanso: es solo lo que se ve encima.
 */
class RestOverrunPolicyTest {

    private val endedAt = 1_249_000L

    private fun runningState(
        kind: RestTimerKind = RestTimerKind.STANDARD,
        skipExercise: Boolean = false,
    ) = WorkoutUiState(
        isRestTimerRunning = true,
        restModalState = WorkoutRestModalState(
            kind = kind,
            plannedSeconds = 249,
            activeSeconds = 249,
            startedAtMs = 1_000_000L,
            endsAtMs = endedAt,
            skipCurrentExerciseOnFinish = skipExercise,
        ),
        currentExerciseIdx = 1,
        currentSetIdx = 2,
        activeStepKey = "ex_2",
        setJustLoggedKey = "ex_1",
        completedSets = mapOf("ex_1" to CompletedSet(id = "a", weight = 95.0, reps = 6)),
    )

    private fun captured(): RestOverrun = RestOverrunPolicy.captureOrNull(runningState(), nowMs = endedAt + 500L)!!

    /** El estado tras acabar el descanso: ya cerrado, con la capa encima. */
    private fun finished(overrun: RestOverrun = captured()) =
        runningState().copy(isRestTimerRunning = false, restModalState = null, restOverrun = overrun)

    @Test
    fun aNaturalFinishBeforeANormalSetLeavesTheLayerWithWhatItNeeds() {
        val overrun = captured()
        assertEquals("cuenta desde el endsAtMs guardado", endedAt, overrun.endedAtMs)
        assertEquals("sobre lo que duró el descanso entero", 249, overrun.totalSeconds)
        assertEquals(RestTimerKind.STANDARD, overrun.kind)
        assertEquals(1, overrun.exerciseIdx)
        assertEquals(2, overrun.setIdx)
        assertEquals("ex_2", overrun.activeStepKey)
        assertEquals("ex_1", overrun.setJustLoggedKey)
        assertEquals(1, overrun.completedCount)
    }

    @Test
    fun theLayerKeepsTheKindOfTheRestThatEnded() {
        val overrun = RestOverrunPolicy.captureOrNull(runningState(kind = RestTimerKind.SUPERSET_INTRA), nowMs = endedAt)
        assertEquals(RestTimerKind.SUPERSET_INTRA, overrun?.kind)
        assertNotNull(RestOverrunPolicy.captureOrNull(runningState(kind = RestTimerKind.BETWEEN_SIDES), nowMs = endedAt))
    }

    @Test
    fun withoutAnEndMomentItCountsFromNow() {
        val state = runningState().let { it.copy(restModalState = it.restModalState!!.copy(endsAtMs = 0L)) }
        assertEquals(777L, RestOverrunPolicy.captureOrNull(state, nowMs = 777L)?.endedAtMs)
    }

    @Test
    fun noLayerWhenWhatFollowsIsNotANormalSet() {
        // Calentamiento: va dentro de su tarjeta.
        assertNull(RestOverrunPolicy.captureOrNull(runningState(kind = RestTimerKind.WARMUP), endedAt))
        // Saltar lo que queda del ejercicio al acabar.
        assertNull(RestOverrunPolicy.captureOrNull(runningState(skipExercise = true), endedAt))
        // Feedback de la última serie o cierre de la sesión.
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(pendingPostExerciseIdx = 0), endedAt))
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(pendingPostExerciseIdx = -2), endedAt))
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(showPostExerciseSheet = true), endedAt))
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(showFinishSheet = true), endedAt))
        // Sesión terminada o cancelada.
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(isComplete = true), endedAt))
        assertNull(RestOverrunPolicy.captureOrNull(runningState().copy(wasCancelled = true), endedAt))
        // Sin descanso no hay capa.
        assertNull(RestOverrunPolicy.captureOrNull(WorkoutUiState(), endedAt))
    }

    @Test
    fun theExtraTimeCountsUpFromTheEndOfTheRest() {
        val overrun = captured()
        assertEquals(0, RestOverrunPolicy.extraSeconds(overrun, endedAt))
        assertEquals(24, RestOverrunPolicy.extraSeconds(overrun, endedAt + 24_900L))
        assertEquals(75, RestOverrunPolicy.extraSeconds(overrun, endedAt + 75_000L))
        assertEquals("nunca negativo", 0, RestOverrunPolicy.extraSeconds(overrun, endedAt - 5_000L))
    }

    @Test
    fun theExtraTimeIsWrittenWithAPlusAndTwoDigitSeconds() {
        assertEquals("+0:24", RestOverrunPolicy.extraLabel(24))
        assertEquals("+0:00", RestOverrunPolicy.extraLabel(0))
        assertEquals("+1:15", RestOverrunPolicy.extraLabel(75))
        assertEquals("+0:00", RestOverrunPolicy.extraLabel(-3))
    }

    @Test
    fun theLayerStaysWhileNothingHappens() {
        val overrun = captured()
        assertFalse(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun), nowMs = endedAt + 24_000L))
        assertFalse(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun), nowMs = endedAt + 299_000L))
    }

    @Test
    fun theLayerGoesAwayAfterFiveMinutes() {
        val overrun = captured()
        assertEquals(300_000L, RestOverrunPolicy.remainingLifeMs(overrun, endedAt))
        assertEquals(1_000L, RestOverrunPolicy.remainingLifeMs(overrun, endedAt + 299_000L))
        assertEquals(0L, RestOverrunPolicy.remainingLifeMs(overrun, endedAt + 400_000L))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun), nowMs = endedAt + 300_000L))
    }

    @Test
    fun theLayerGoesAwayWhenASetIsRecordedOrAnotherIsTouched() {
        val overrun = captured()
        val now = endedAt + 10_000L
        // Se registró una serie.
        val recorded = finished(overrun).copy(
            completedSets = finished(overrun).completedSets + ("ex_2" to CompletedSet(id = "b", weight = 95.0, reps = 6)),
        )
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, recorded, now))
        // Se editó la última serie registrada (la clave cambia aunque el número no).
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(setJustLoggedKey = "ex_9"), now))
        // Se tocó otra serie del mismo ejercicio o se cambió de ejercicio.
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(activeStepKey = "ex_3"), now))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(currentSetIdx = 3), now))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(currentExerciseIdx = 2), now))
    }

    @Test
    fun theLayerGoesAwayWhenANewRestStartsOrTheSessionHeadsToTheEnd() {
        val overrun = captured()
        val now = endedAt + 10_000L
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(isRestTimerRunning = true), now))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(showPostExerciseSheet = true), now))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(showFinishSheet = true), now))
        assertTrue(RestOverrunPolicy.shouldDismiss(overrun, finished(overrun).copy(isComplete = true), now))
    }

    @Test
    fun theExtensionIsThirtySeconds() {
        assertEquals(30, RestOverrunPolicy.EXTEND_SECONDS)
    }
}
