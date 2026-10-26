package com.example.kpkn.screens.workout

/** Selects the actual duration used by both confirmation and persistence. */
internal fun cardioRecordedDurationSeconds(gpsMode: Boolean, gpsSeconds: Long?, timerSeconds: Int): Int =
    if (gpsMode && gpsSeconds != null && gpsSeconds > 0L) gpsSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    else timerSeconds.coerceAtLeast(0)
