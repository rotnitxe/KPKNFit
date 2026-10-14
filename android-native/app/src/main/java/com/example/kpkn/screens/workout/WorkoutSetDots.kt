package com.example.kpkn.screens.workout

import com.example.kpkn.screens.workout.design.WorkoutSetDot
import com.example.kpkn.screens.workout.design.WorkoutSetDotState

/**
 * Traduce los elementos de la tira de series (KPKN LS2b) a puntos de [WorkoutSetStrip]. Solo cuentan las series: una serie
 * bilateral es un punto y una unilateral, otro (su estado es el del lado activo, o el peor de los dos si ambos están hechos). Las
 * rondas de superserie, las pastillas de calentamiento y movilidad no son puntos.
 *
 * - Hecha: [WorkoutSetDotState.DONE] y su dato («80 × 8») de [captionFor], si lo hay.
 * - La actual: la que está `ACTIVE`; si ninguna lo está, la del [activeElementIndex] si es una serie.
 * - El resto: [WorkoutSetDotState.FUTURE]. Una serie omitida también cuenta como futura, sin dato.
 *
 * [captionFor] recibe el índice de página de la serie. El número del punto sale de la etiqueta («2», o «2/3» → 2); sin número, el de
 * posición en la lista (1, 2, …).
 */
internal fun toWorkoutSetDots(
    elements: List<TimelineElement>,
    activeElementIndex: Int,
    captionFor: (pageIndex: Int) -> String? = { null },
): List<WorkoutSetDot> {
    val rawDots = elements.mapIndexedNotNull { elementIndex, element ->
        when (element) {
            is TimelineElement.BilateralSet -> RawDot(
                elementIndex = elementIndex,
                pageIndex = element.pageIndex,
                label = element.label,
                state = element.state,
            )
            is TimelineElement.UnilateralSet -> {
                val page = element.leftPageIndex ?: element.rightPageIndex ?: return@mapIndexedNotNull null
                RawDot(
                    elementIndex = elementIndex,
                    pageIndex = page,
                    label = element.setLabel.orEmpty(),
                    state = unilateralState(element.leftState, element.rightState),
                )
            }
            else -> null
        }
    }
    val activeOrdinal = rawDots.indexOfFirst { it.state == WorkoutSetCardVisualState.ACTIVE }
        .takeIf { it >= 0 }
        ?: rawDots.indexOfFirst { it.elementIndex == activeElementIndex }
    return rawDots.mapIndexed { ordinal, raw ->
        val state = when {
            ordinal == activeOrdinal -> WorkoutSetDotState.CURRENT
            raw.state == WorkoutSetCardVisualState.COMPLETED -> WorkoutSetDotState.DONE
            else -> WorkoutSetDotState.FUTURE
        }
        WorkoutSetDot(
            id = "page-${raw.pageIndex}",
            label = raw.label.firstIntegerOrNull() ?: (ordinal + 1).toString(),
            state = state,
            caption = if (state == WorkoutSetDotState.DONE) captionFor(raw.pageIndex) else null,
        )
    }
}

/** Una serie ya traducida, antes de decidir cuál es la actual. */
private data class RawDot(
    val elementIndex: Int,
    val pageIndex: Int,
    val label: String,
    val state: WorkoutSetCardVisualState,
)

/** Un lado activo manda; si los dos están hechos, la serie está hecha; en cualquier otro caso, pendiente. */
private fun unilateralState(left: WorkoutSetCardVisualState, right: WorkoutSetCardVisualState): WorkoutSetCardVisualState = when {
    left == WorkoutSetCardVisualState.ACTIVE || right == WorkoutSetCardVisualState.ACTIVE -> WorkoutSetCardVisualState.ACTIVE
    left == WorkoutSetCardVisualState.COMPLETED && right == WorkoutSetCardVisualState.COMPLETED -> WorkoutSetCardVisualState.COMPLETED
    else -> WorkoutSetCardVisualState.FUTURE
}

/** El primer número de la etiqueta («2» o «2/3» dan «2»); null si no hay ninguno. */
private fun String.firstIntegerOrNull(): String? = Regex("\\d+").find(this)?.value
