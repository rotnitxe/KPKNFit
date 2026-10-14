package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.workout.design.WorkoutSetDotState
import org.junit.Assert.assertEquals
import org.junit.Test

/** La tira de series (KPKN LS2b): cada serie es un punto con su estado y su dato; las rondas y las pastillas no. */
class WorkoutSetDotsTest {

    private fun bilateral(page: Int, label: String, state: WorkoutSetCardVisualState) =
        TimelineElement.BilateralSet(pageIndex = page, label = label, state = state)

    @Test
    fun hechasConSuDatoLaActualYLasFuturas() {
        val elements = listOf(
            bilateral(0, "1", WorkoutSetCardVisualState.COMPLETED),
            bilateral(1, "2", WorkoutSetCardVisualState.ACTIVE),
            bilateral(2, "3", WorkoutSetCardVisualState.FUTURE),
        )

        val dots = toWorkoutSetDots(elements, activeElementIndex = 1) { page -> if (page == 0) "80 × 8" else null }

        assertEquals(
            listOf(WorkoutSetDotState.DONE, WorkoutSetDotState.CURRENT, WorkoutSetDotState.FUTURE),
            dots.map { it.state },
        )
        assertEquals(listOf("80 × 8", null, null), dots.map { it.caption })
        assertEquals(listOf("1", "2", "3"), dots.map { it.label })
        assertEquals(listOf("page-0", "page-1", "page-2"), dots.map { it.id })
    }

    @Test
    fun superseriePorRondasUnPuntoPorSerieSinLasInsignias() {
        val elements = listOf(
            TimelineElement.RoundBadge(roundIndex = 0, isCurrentRound = false, isAllDone = true, firstPageIndex = 0),
            bilateral(0, "1", WorkoutSetCardVisualState.COMPLETED),
            bilateral(1, "2", WorkoutSetCardVisualState.COMPLETED),
            TimelineElement.RoundBadge(roundIndex = 1, isCurrentRound = true, isAllDone = false, firstPageIndex = 2),
            bilateral(2, "3", WorkoutSetCardVisualState.ACTIVE),
            bilateral(3, "4", WorkoutSetCardVisualState.FUTURE),
        )

        val dots = toWorkoutSetDots(elements, activeElementIndex = 4)

        assertEquals(4, dots.size)
        assertEquals(
            listOf(
                WorkoutSetDotState.DONE,
                WorkoutSetDotState.DONE,
                WorkoutSetDotState.CURRENT,
                WorkoutSetDotState.FUTURE,
            ),
            dots.map { it.state },
        )
    }

    @Test
    fun unaSerieUnilateralEsUnPuntoConElLadoActivo() {
        val elements = listOf(
            TimelineElement.UnilateralSet(
                setLabel = "2",
                leftPageIndex = 3,
                leftState = WorkoutSetCardVisualState.ACTIVE,
                rightPageIndex = 4,
                rightState = WorkoutSetCardVisualState.FUTURE,
            ),
        )

        val dots = toWorkoutSetDots(elements, activeElementIndex = 0)

        assertEquals(1, dots.size)
        assertEquals("page-3", dots[0].id)
        assertEquals("2", dots[0].label)
        assertEquals(WorkoutSetDotState.CURRENT, dots[0].state)
    }

    @Test
    fun sinNumeroEnLaEtiquetaSeUsaLaPosicion() {
        val elements = listOf(bilateral(0, "", WorkoutSetCardVisualState.FUTURE))

        val dots = toWorkoutSetDots(elements, activeElementIndex = -1)

        assertEquals(listOf("1"), dots.map { it.label })
        assertEquals(listOf(WorkoutSetDotState.FUTURE), dots.map { it.state })
    }

    @Test
    fun textoDeLaSerieHechaConCargaRepeticionesOTiempo() {
        assertEquals("80 × 8", workoutSetDoneCaption(weightKg = 80.0, reps = 8, timeSeconds = null))
        assertEquals("8 reps", workoutSetDoneCaption(weightKg = 0.0, reps = 8, timeSeconds = null))
        assertEquals(formatTime(45), workoutSetDoneCaption(weightKg = 0.0, reps = 0, timeSeconds = 45))
        assertEquals(null, workoutSetDoneCaption(weightKg = 0.0, reps = 0, timeSeconds = null))
    }

    @Test
    fun laTiraRecibeElTextoDeCadaSerieHechaPorPagina() {
        val pages = listOf(
            WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 0),
            WorkoutSetSwipePage(type = LivePageType.NORMAL, setIndex = 1),
            WorkoutSetSwipePage(type = LivePageType.WARMUP, setIndex = 0),
        )
        val completed = mapOf(
            "ex-1_0" to CompletedSet(id = "s0", weight = 80.0, reps = 8),
            "ex-1_1" to CompletedSet(id = "s1"),
        )

        val captions = workoutSetDoneCaptions(pages, completed, fallbackExerciseId = "ex-1")

        assertEquals(mapOf(0 to "80 × 8"), captions)
    }
}
