package com.example.kpkn.screens.drag

/** One insertion boundary at an item's visual midpoint. */
internal data class DropInsertionStop(
    val midpointY: Float,
    val targetIndex: Int,
    val targetKey: String? = null,
)

internal data class DropInsertionResult(
    val targetIndex: Int,
    val targetKey: String?,
)

/** Resolve a before/after slot using the same midpoint rule in agenda and editor drags. */
internal fun resolveDropInsertion(
    pointerY: Float,
    stops: List<DropInsertionStop>,
    appendIndex: Int,
): DropInsertionResult {
    val target = stops.sortedBy { it.midpointY }.firstOrNull { pointerY < it.midpointY }
    return if (target == null) DropInsertionResult(appendIndex, null)
    else DropInsertionResult(target.targetIndex, target.targetKey)
}
