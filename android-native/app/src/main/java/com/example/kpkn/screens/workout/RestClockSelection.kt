package com.example.kpkn.screens.workout

import kotlin.random.Random

/** A choice per rest event, shared by the card and full overlay and stable through time adjustments. */
internal fun restClockShapeForEvent(state: WorkoutRestModalState?, overrun: RestOverrun? = null, choice: RestClockShape? = null): RestClockShape {
    choice?.let { return it }
    val startedAt = state?.restEventStartedAtMs()?.takeIf { it > 0L }
        ?: overrun?.startedAtMs?.takeIf { it > 0L }
        ?: overrun?.let { it.endedAtMs - it.totalSeconds * 1000L }
        ?: 0L
    val seed = (startedAt xor (startedAt ushr 32)).toInt()
    return RestClockShape.entries[Random(seed).nextInt(RestClockShape.entries.size)]
}
