package com.example.kpkn.services.diagnostics

/** A pointer sample used by the report gesture state machine. */
data class ReportGesturePointer(
    val id: Int,
    val x: Float,
    val y: Float,
)

sealed interface ReportGestureInput {
    data class Down(val timeMs: Long, val pointer: ReportGesturePointer) : ReportGestureInput
    data class PointerDown(val timeMs: Long, val pointer: ReportGesturePointer) : ReportGestureInput
    data class Move(val timeMs: Long, val pointers: List<ReportGesturePointer>) : ReportGestureInput
    data class PointerUp(val timeMs: Long, val pointerId: Int) : ReportGestureInput
    data class Up(val timeMs: Long) : ReportGestureInput
    data class Cancel(val timeMs: Long) : ReportGestureInput
    data class Tick(val timeMs: Long) : ReportGestureInput
}

sealed interface ReportGestureEffect {
    data class Progress(val value: Float) : ReportGestureEffect
    data object Confirmed : ReportGestureEffect
    data object CancelUnderlying : ReportGestureEffect
    data object Released : ReportGestureEffect
    data object Reset : ReportGestureEffect
}

/**
 * JVM-pure state machine for the two-finger report shortcut.
 *
 * Android touch dispatch and timing are deliberately kept in [ReportGestureDetector];
 * this class owns the policy and can therefore be exhaustively tested without a device.
 */
class ReportGestureStateMachine(
    private val secondPointerWindowMs: Long = SECOND_POINTER_WINDOW_MS,
    private val holdDurationMs: Long = HOLD_DURATION_MS,
    private val movementSlopPx: Float = MOVEMENT_SLOP_PX,
) {
    private var first: ReportGesturePointer? = null
    private var second: ReportGesturePointer? = null
    private var firstDownAtMs: Long = 0L
    private var armedAtMs: Long? = null
    private var confirmed = false

    val isArming: Boolean get() = armedAtMs != null && !confirmed
    val isConfirmed: Boolean get() = confirmed

    fun onInput(input: ReportGestureInput): List<ReportGestureEffect> {
        return when (input) {
        is ReportGestureInput.Down -> {
            reset()
            first = input.pointer
            firstDownAtMs = input.timeMs
            emptyList()
        }

        is ReportGestureInput.PointerDown -> {
            val firstPointer = first
            if (firstPointer == null || second != null) return resetWithEffect()
            val elapsed = input.timeMs - firstDownAtMs
            if (elapsed !in 0..secondPointerWindowMs) return resetWithEffect()
            second = input.pointer
            armedAtMs = input.timeMs
            listOf(ReportGestureEffect.Progress(0f))
        }

        is ReportGestureInput.Move -> {
            if (!isArming) return if (confirmed) emptyList() else emptyList()
            val firstPointer = first ?: return resetWithEffect()
            val secondPointer = second ?: return resetWithEffect()
            val currentFirst = input.pointers.firstOrNull { it.id == firstPointer.id }
            val currentSecond = input.pointers.firstOrNull { it.id == secondPointer.id }
            val firstMoved = currentFirst?.distanceFrom(firstPointer) ?: Float.POSITIVE_INFINITY
            val secondMoved = currentSecond?.distanceFrom(secondPointer) ?: Float.POSITIVE_INFINITY
            if (firstMoved > movementSlopPx || secondMoved > movementSlopPx) {
                return resetWithEffect()
            }
            emptyList()
        }

        is ReportGestureInput.PointerUp -> {
            if (!isArming && !confirmed) return emptyList()
            if (!confirmed) resetWithEffect() else emptyList()
        }

        is ReportGestureInput.Tick -> {
            val armedAt = armedAtMs ?: return emptyList()
            if (confirmed) return emptyList()
            val elapsed = (input.timeMs - armedAt).coerceAtLeast(0L)
            if (elapsed >= holdDurationMs) {
                confirmed = true
                listOf(
                    ReportGestureEffect.Progress(1f),
                    ReportGestureEffect.CancelUnderlying,
                    ReportGestureEffect.Confirmed,
                )
            } else {
                listOf(ReportGestureEffect.Progress(elapsed.toFloat() / holdDurationMs.toFloat()))
            }
        }

        is ReportGestureInput.Up -> {
            if (!confirmed) return if (isArming) resetWithEffect() else emptyList()
            reset()
            listOf(ReportGestureEffect.Released, ReportGestureEffect.Reset)
        }

        is ReportGestureInput.Cancel -> {
            if (!isArming && !confirmed) return emptyList()
            resetWithEffect()
        }
        }
    }

    fun cancel(): List<ReportGestureEffect> = if (isArming || confirmed) resetWithEffect() else emptyList()

    private fun resetWithEffect(): List<ReportGestureEffect> {
        reset()
        return listOf(ReportGestureEffect.Progress(0f), ReportGestureEffect.Reset)
    }

    private fun reset() {
        first = null
        second = null
        firstDownAtMs = 0L
        armedAtMs = null
        confirmed = false
    }

    private fun ReportGesturePointer.distanceFrom(other: ReportGesturePointer): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val SECOND_POINTER_WINDOW_MS = 1_500L
        const val HOLD_DURATION_MS = 2_500L
        const val MOVEMENT_SLOP_PX = 32f
    }
}
