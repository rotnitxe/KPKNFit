package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WeekVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2.1: el resumen final avisa de las series de fuerza/cardio que quedan sin hacer.
 * Solo lectura: la navegación automática (nextSet / nextIncompleteStepAfter) no cambia.
 */
class WorkoutPendingSeriesTest {
    private fun strength(id: String, name: String, sets: Int) = Exercise(
        id = id,
        name = name,
        sets = List(sets) { index -> ExerciseSet("$id-$index") },
    )

    private fun cardio(id: String, name: String, sets: Int) = Exercise(
        id = id,
        name = name,
        sets = List(sets) { index -> ExerciseSet("$id-$index") },
        cardioDetails = CardioDetails(type = CardioType.RUN_OUTDOOR),
    )

    private val displayNames = mapOf(
        "press" to "Press de banca",
        "row" to "Remo",
        "curl" to "Curl",
        "squat" to "Sentadilla",
        "lunge" to "Zancada",
        "run" to "Carrera",
    )

    // El nombre de los pasos pasa por el catálogo; el aviso real usa el mismo parámetro
    // para mostrar el nombre de pantalla, y aquí lo fijamos para no depender del catálogo.
    private fun noticeFor(pending: List<WorkoutStep>): PendingSeriesNotice? =
        buildPendingSeriesNotice(pending) { step -> displayNames.getValue(step.exerciseId) }

    private fun doneStrength(exerciseId: String, vararg setIdx: Int): Map<String, CompletedSet> =
        setIdx.associate { "${exerciseId}_$it" to CompletedSet(id = "${exerciseId}_$it") }

    private fun doneCardio(exerciseId: String, vararg setIdx: Int): Map<String, CompletedSet> =
        setIdx.associate { WorkoutStepRules.cardioCompletionKey(exerciseId, it) to CompletedSet(id = "${exerciseId}_$it") }

    private fun navigatorFor(state: WorkoutUiState): WorkoutStepNavigator = WorkoutStepNavigator(
        scope = CoroutineScope(Dispatchers.Unconfined),
        getState = { state },
        updateState = { },
        ports = object : WorkoutStepNavigator.Ports {
            override fun visibleExercises(state: WorkoutUiState): List<Exercise> =
                state.session?.exercises.orEmpty().filterNot { it.id in state.skippedExerciseIds }
            override fun sessionForActiveMode(base: Session, mode: WeekVariant): Session = base
            override fun isSetDone(
                completedSets: Map<String, CompletedSet>,
                exerciseId: String,
                setIdx: Int,
                isUnilateral: Boolean,
            ) = completedSets.containsKey("${exerciseId}_$setIdx")
            override fun buildEditingStateForPosition(
                completedSets: Map<String, CompletedSet>,
                exercise: Exercise?,
                setIdx: Int,
                preferredSide: String?,
            ): WorkoutEditingState? = null
            override fun stopRestTimer() = Unit
            override fun persistOngoingState(immediate: Boolean) = Unit
            override suspend fun persistOngoingStateAndAwait() = WorkoutPersistResult.Ok
            override fun refreshLoadSuggestions(state: WorkoutUiState) = Unit
            override fun clearDraftForSet(exerciseId: String, setIdx: Int, side: String?) = Unit
            override fun computeImbalanceNotice(
                exercise: Exercise,
                setIdx: Int,
                completedSets: Map<String, CompletedSet>,
            ): String? = null
            override fun openFinishSheet() = Unit
            override fun speakCurrentStepAnnouncementIfEnabled() = Unit
            override fun isRecordingBusy(): Boolean = false
            override fun onRecordingBusyBlocked(message: String) = Unit
            override fun announcePostExerciseFeedback(exerciseIds: List<String>) = Unit
            override fun announceFinalPostExerciseFeedback(exerciseIds: List<String>) = Unit
        },
    )

    private fun stateOf(
        exercises: List<Exercise>,
        completedSets: Map<String, CompletedSet> = emptyMap(),
        skippedExerciseIds: Set<String> = emptySet(),
        omittedSetKeys: Set<String> = emptySet(),
        activeStepKey: String? = null,
        currentExerciseIdx: Int = 0,
        currentSetIdx: Int = 0,
    ) = WorkoutUiState(
        session = Session(id = "session", name = "Sesión", exercises = exercises),
        currentExerciseIdx = currentExerciseIdx,
        currentSetIdx = currentSetIdx,
        activeStepKey = activeStepKey,
        completedSets = completedSets,
        skippedExerciseIds = skippedExerciseIds,
        omittedSetKeys = omittedSetKeys,
    )

    @Test
    fun cardioDoneFirst_withStrengthPending_reportsTheStrengthSeriesAndKeepsAutoNavigation() {
        // La fuerza va antes que el cardio en el carril; se hizo el cardio primero.
        val press = strength("press", "Press de banca", 3)
        val row = strength("row", "Remo", 2)
        val run = cardio("run", "Carrera", 2)
        val state = stateOf(
            exercises = listOf(press, row, run),
            completedSets = doneCardio("run", 0, 1),
            activeStepKey = WorkoutStepRules.cardioStepKey("run", 1),
            currentExerciseIdx = 2,
            currentSetIdx = 1,
        )
        val navigator = navigatorFor(state)

        val pending = navigator.pendingSeriesSteps(state)
        val notice = noticeFor(pending)

        assertEquals(
            listOf("press_0", "press_1", "press_2", "row_0", "row_1"),
            pending.map { it.stepKey },
        )
        // La navegación hacia adelante sigue sin mirar atrás: por eso el resumen avisa.
        assertNull(navigator.nextIncompleteStepAfter(state))
        assertNotNull(notice)
        assertEquals(5, notice!!.totalSeries)
        assertEquals("press_0", notice.firstStepKey)
        assertEquals("Te quedan 5 series sin hacer: Press de banca (3), Remo (2).", notice.message)
        assertEquals("Seguir con ellas", notice.actionLabel)
    }

    @Test
    fun nothingPending_reportsNoNotice() {
        val press = strength("press", "Press de banca", 2)
        val run = cardio("run", "Carrera", 1)
        val state = stateOf(
            exercises = listOf(press, run),
            completedSets = doneStrength("press", 0, 1) + doneCardio("run", 0),
        )

        val pending = navigatorFor(state).pendingSeriesSteps(state)

        assertTrue(pending.isEmpty())
        assertNull(noticeFor(pending))
    }

    @Test
    fun exercisesSkippedOnPurposeAndOmittedSeries_doNotCount() {
        val press = strength("press", "Press de banca", 3)
        val row = strength("row", "Remo", 3)
        val curl = strength("curl", "Curl", 2)
        val state = stateOf(
            exercises = listOf(press, row, curl),
            completedSets = doneStrength("row", 0),
            skippedExerciseIds = setOf("press"),
            omittedSetKeys = setOf(WorkoutStepRules.omittedSetKey("row", 2)),
        )

        val pending = navigatorFor(state).pendingSeriesSteps(state)

        // Press saltado: fuera. Remo: serie 0 hecha y serie 2 omitida, queda la 1. Curl: 2 series.
        assertEquals(listOf("row_1", "curl_0", "curl_1"), pending.map { it.stepKey })
        assertEquals(
            "Te quedan 3 series sin hacer: Remo (1), Curl (2).",
            noticeFor(pending)!!.message,
        )
    }

    @Test
    fun finishingUpToHere_countsEarlierUnfinishedExerciseButNotTheOnesSkippedOnPurpose() {
        // «Terminar hasta acá» marca como saltados el ejercicio actual y los siguientes,
        // pero no los anteriores que quedaron sin hacer.
        val squat = strength("squat", "Sentadilla", 2)
        val press = strength("press", "Press de banca", 2)
        val row = strength("row", "Remo", 2)
        val state = stateOf(
            exercises = listOf(squat, press, row),
            completedSets = doneStrength("press", 0),
            skippedExerciseIds = setOf("row"),
            currentExerciseIdx = 1,
        )

        val pending = navigatorFor(state).pendingSeriesSteps(state)

        assertEquals(listOf("squat_0", "squat_1", "press_1"), pending.map { it.stepKey })
        assertEquals(
            "Te quedan 3 series sin hacer: Sentadilla (2), Press de banca (1).",
            noticeFor(pending)!!.message,
        )
    }

    @Test
    fun pendingWarmupOrMobility_isNotASeries() {
        val press = Exercise(
            id = "press",
            name = "Press de banca",
            sets = listOf(ExerciseSet("press-0")),
            warmupSets = listOf(
                WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 8),
            ),
        )
        val state = stateOf(
            exercises = listOf(press),
            completedSets = doneStrength("press", 0),
        )
        val navigator = navigatorFor(state)

        // El calentamiento sigue pendiente para el cursor, pero no cuenta como serie.
        assertNotNull(navigator.firstIncompleteStep(state))
        assertTrue(navigator.pendingSeriesSteps(state).isEmpty())
    }

    @Test
    fun pendingCardioSeries_countsAsASeries() {
        val press = strength("press", "Press de banca", 1)
        val run = cardio("run", "Carrera", 2)
        val state = stateOf(
            exercises = listOf(press, run),
            completedSets = doneStrength("press", 0) + doneCardio("run", 0),
        )

        val pending = navigatorFor(state).pendingSeriesSteps(state)

        assertEquals(listOf(WorkoutStepRules.cardioStepKey("run", 1)), pending.map { it.stepKey })
        assertEquals(
            "Te queda 1 serie sin hacer: Carrera.",
            noticeFor(pending)!!.message,
        )
        assertEquals("Seguir con ella", noticeFor(pending)!!.actionLabel)
    }

    @Test
    fun unilateralSeriesWithBothSidesPending_countsOnce() {
        val lunge = Exercise(
            id = "lunge",
            name = "Zancada",
            isUnilateral = true,
            sets = listOf(ExerciseSet("lunge-0"), ExerciseSet("lunge-1")),
        )
        val state = stateOf(exercises = listOf(lunge))

        val pending = navigatorFor(state).pendingSeriesSteps(state)
        val notice = noticeFor(pending)!!

        assertTrue("hay un paso por lado", pending.size > notice.totalSeries)
        assertEquals(2, notice.totalSeries)
        assertEquals("Te quedan 2 series sin hacer: Zancada (2).", notice.message)
    }

    @Test
    fun message_namesAtMostThreeExercisesAndSummarisesTheRest() {
        val steps = listOf("a", "b", "c", "d", "e").map { id ->
            WorkoutStep(
                type = WorkoutStepType.WORKING_SET,
                exerciseId = id,
                exerciseName = "Ejercicio ${id.uppercase()}",
                stepKey = "${id}_0",
                setIndex = 0,
            )
        }

        val notice = buildPendingSeriesNotice(steps)!!

        assertEquals(5, notice.totalSeries)
        assertEquals(
            "Te quedan 5 series sin hacer: Ejercicio A (1), Ejercicio B (1), Ejercicio C (1) y 2 ejercicios más.",
            notice.message,
        )
        assertEquals("a_0", notice.firstStepKey)
    }

    @Test
    fun message_usesTheResolverForDisplayNames() {
        val step = WorkoutStep(
            type = WorkoutStepType.WORKING_SET,
            exerciseId = "press",
            exerciseName = "nombre hablado",
            stepKey = "press_0",
            setIndex = 0,
        )

        val notice = buildPendingSeriesNotice(listOf(step)) { "Press · Barra" }!!

        assertEquals("Te queda 1 serie sin hacer: Press · Barra.", notice.message)
    }
}
