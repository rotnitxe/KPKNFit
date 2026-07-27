package com.example.kpkn.services.workout

/**
 * Builds optional spoken cues when entering an exercise step (PR / eRM range).
 */
object WorkoutVoiceEnteringCue {

    fun rangeHint(
        ermMin: Double,
        ermMax: Double,
        sampleCount: Int,
        showPRsInWorkout: Boolean,
    ): String? {
        if (!showPRsInWorkout || sampleCount <= 0) return null
        if (ermMin <= 0.0 && ermMax <= 0.0) return null
        val minText = formatKg(ermMin)
        val maxText = formatKg(ermMax)
        return if (minText == maxText) {
            "eRM reciente $minText."
        } else {
            "Rango eRM $minText a $maxText."
        }
    }

    private fun formatKg(kg: Double): String {
        return if (kg == kg.toLong().toDouble()) {
            "${kg.toLong()} kilos"
        } else {
            "${"%.1f".format(kg).replace(',', '.')} kilos"
        }
    }
}
