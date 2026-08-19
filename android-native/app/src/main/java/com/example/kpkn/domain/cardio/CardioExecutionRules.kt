package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.domain.calculations.CardioCalorieInput

data class CardioGuide(
    val zoneName: String,
    val heartRatePercent: String,
    val heartRateBpm: String,
    val cadenceRpm: String,
)

/** Static guidance only; live sensor data is optional and never required to record manually. */
object CardioGuideEngine {
    fun guide(details: CardioDetails): CardioGuide {
        return when (details.intensity) {
            CardioIntensity.BAJA -> CardioGuide(
                zoneName = "Calentamiento",
                heartRatePercent = "50-60%",
                heartRateBpm = "110–130 bpm",
                cadenceRpm = "50-60 RPM",
            )
            CardioIntensity.MEDIA -> CardioGuide(
                zoneName = "Quema grasa",
                heartRatePercent = "60-70%",
                heartRateBpm = "130–150 bpm",
                cadenceRpm = "60-70 RPM",
            )
            CardioIntensity.ALTA -> CardioGuide(
                zoneName = "Aeróbico",
                heartRatePercent = "70-80%",
                heartRateBpm = "150–170 bpm",
                cadenceRpm = "70-85 RPM",
            )
            CardioIntensity.MUY_ALTA -> CardioGuide(
                zoneName = "Anaeróbico",
                heartRatePercent = "80-90%",
                heartRateBpm = "170+ bpm",
                cadenceRpm = "85+ RPM",
            )
        }
    }
}

object CardioCalorieTargetEngine {
    fun estimate(details: CardioDetails, bodyWeightKg: Double?): Double? {
        val weight = bodyWeightKg?.takeIf { it > 0.0 } ?: return null
        return CardioCalorieEngine.estimate(
            CardioCalorieInput(
                details = details,
                weightKg = weight,
                durationSeconds = details.effectiveDurationSeconds(),
            ),
        )
    }
}

object CardioTimerEngine {
    fun start(state: CardioTimerState, nowMs: Long): CardioTimerState = state.copy(
        status = CardioExecutionStatus.RUNNING,
        updatedAtMs = nowMs,
    )

    fun pause(state: CardioTimerState, nowMs: Long): CardioTimerState = state.copy(
        status = CardioExecutionStatus.PAUSED,
        updatedAtMs = nowMs,
    )

    fun requestConfirmation(state: CardioTimerState, nowMs: Long): CardioTimerState = state.copy(
        status = CardioExecutionStatus.AWAITING_CONFIRMATION,
        updatedAtMs = nowMs,
    )

    fun cancelConfirmation(state: CardioTimerState, nowMs: Long): CardioTimerState = state.copy(
        status = if (state.elapsedSeconds > 0) CardioExecutionStatus.PAUSED else CardioExecutionStatus.READY,
        updatedAtMs = nowMs,
    )

    fun tick(state: CardioTimerState, elapsedSeconds: Int = 1, nowMs: Long): CardioTimerState {
        if (state.status != CardioExecutionStatus.RUNNING) return state
        val seconds = elapsedSeconds.coerceAtLeast(0)
        val elapsed = (state.elapsedSeconds + seconds).coerceAtMost(state.totalSeconds)
        val remaining = (state.remainingSeconds - seconds).coerceAtLeast(0)
        return state.copy(
            elapsedSeconds = elapsed,
            remainingSeconds = remaining,
            status = if (remaining == 0) CardioExecutionStatus.AWAITING_CONFIRMATION else CardioExecutionStatus.RUNNING,
            updatedAtMs = nowMs,
        )
    }

    fun applyElapsedWallClock(state: CardioTimerState, elapsedSeconds: Int, nowMs: Long): CardioTimerState =
        tick(state, elapsedSeconds.coerceAtLeast(0), nowMs)
}
