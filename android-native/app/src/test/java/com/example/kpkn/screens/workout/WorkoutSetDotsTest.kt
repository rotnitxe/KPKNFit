package com.example.kpkn.screens.workout

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
}
