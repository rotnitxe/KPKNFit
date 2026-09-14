package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutPersistenceDebounceTest {

    @Test
    fun twoDebouncedPersists_writeLatestState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(
                Exercise(
                    id = "ex",
                    name = "Press",
                    sets = listOf(ExerciseSet(id = "s0"), ExerciseSet(id = "s1")),
                ),
            ),
        )
        var current = WorkoutUiState(session = session, programId = "prog", currentSetIdx = 0)
        var writtenIndex: Int? = null
        val controller = WorkoutPersistenceController(
            scope = this,
            programId = "prog",
            sessionId = "sess",
            getState = { current },
            visibleExercises = { it.session?.exercises.orEmpty() },
            writeOngoing = { apply ->
                val base = OngoingWorkoutState(programId = "prog", session = session, startTime = 0L)
                writtenIndex = apply(base).activeSetIndex
            },
            persistDispatcher = dispatcher,
        )

        controller.persist(immediate = false)
        current = current.copy(currentSetIdx = 1)
        controller.persist(immediate = false)
        advanceTimeBy(WorkoutPersistenceController.DRAFT_DEBOUNCE_MS)
        runCurrent()

        assertEquals(1, writtenIndex)
    }

    @Test
    fun persistAndAwait_isNotOverwrittenByStaleDebounce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(Exercise(id = "ex", name = "Press", sets = listOf(ExerciseSet(id = "s0")))),
        )
        var current = WorkoutUiState(session = session, programId = "prog", currentSetIdx = 0)
        val writes = mutableListOf<Int>()
        val controller = WorkoutPersistenceController(
            scope = this,
            programId = "prog",
            sessionId = "sess",
            getState = { current },
            visibleExercises = { it.session?.exercises.orEmpty() },
            writeOngoing = { apply ->
                val base = OngoingWorkoutState(programId = "prog", session = session, startTime = 0L)
                writes += apply(base).activeSetIndex ?: -1
            },
            persistDispatcher = dispatcher,
        )

        controller.persist(immediate = false)
        current = current.copy(currentSetIdx = 0)
        controller.persistAndAwait()
        advanceTimeBy(WorkoutPersistenceController.DRAFT_DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf(0), writes)
    }

    @Test
    fun consecutiveImmediatePersists_coalesceToOneWrite() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(
                Exercise(
                    id = "ex",
                    name = "Press",
                    sets = listOf(ExerciseSet(id = "s0"), ExerciseSet(id = "s1")),
                ),
            ),
        )
        var current = WorkoutUiState(session = session, programId = "prog", currentSetIdx = 0)
        val writes = mutableListOf<Int>()
        val controller = WorkoutPersistenceController(
            scope = this,
            programId = "prog",
            sessionId = "sess",
            getState = { current },
            visibleExercises = { it.session?.exercises.orEmpty() },
            writeOngoing = { apply ->
                val base = OngoingWorkoutState(programId = "prog", session = session, startTime = 0L)
                writes += apply(base).activeSetIndex ?: -1
            },
            persistDispatcher = dispatcher,
        )

        controller.persist(immediate = true)
        current = current.copy(currentSetIdx = 1)
        controller.persist(immediate = true)
        runCurrent()
        assertEquals(emptyList<Int>(), writes)

        advanceTimeBy(WorkoutPersistenceController.IMMEDIATE_COALESCE_MS)
        runCurrent()
        assertEquals(listOf(1), writes)
    }

    @Test
    fun persistAndAwait_cancelsPendingImmediateCoalesce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(Exercise(id = "ex", name = "Press", sets = listOf(ExerciseSet(id = "s0")))),
        )
        var current = WorkoutUiState(session = session, programId = "prog", currentSetIdx = 0)
        val writes = mutableListOf<Int>()
        val controller = WorkoutPersistenceController(
            scope = this,
            programId = "prog",
            sessionId = "sess",
            getState = { current },
            visibleExercises = { it.session?.exercises.orEmpty() },
            writeOngoing = { apply ->
                val base = OngoingWorkoutState(programId = "prog", session = session, startTime = 0L)
                writes += apply(base).activeSetIndex ?: -1
            },
            persistDispatcher = dispatcher,
        )

        controller.persist(immediate = true)
        current = current.copy(currentSetIdx = 0)
        controller.persistAndAwait()
        advanceTimeBy(WorkoutPersistenceController.IMMEDIATE_COALESCE_MS)
        runCurrent()

        assertEquals(listOf(0), writes)
    }
}
