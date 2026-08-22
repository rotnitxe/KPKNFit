package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.domain.calculations.CardioCalorieInput
import kotlin.math.roundToInt

data class CardioGuide(
    val rpeTarget: Int,
    /** Exact programmed value used by the editor and live summary (RPE may be fractional). */
    val rpeTargetExact: Double = rpeTarget.toDouble(),
    val hrPercentRef: String? = null,
    val cadenceRef: String? = null,
)

/** Static guidance only; live sensor data is optional and never required to record manually. */
object CardioGuideEngine {
    fun guide(details: CardioDetails): CardioGuide {
        // HIIT/SIT stores the exact programmed RPE; legacy steady cardio keeps
        // its intensity level as the fallback.  The live/editor guidance must
        // use the same value that AUGE and the completed log use.
        val rpe = details.resolvedRpe().roundToInt().coerceIn(1, 10)
        val hr = when (rpe) {
            in 1..2 -> "50–60% FCmáx (referencia)"
            in 3..4 -> "60–70% FCmáx (referencia)"
            in 5..6 -> "70–80% FCmáx (referencia)"
            in 7..8 -> "80–90% FCmáx (referencia)"
            else -> ">90% FCmáx (referencia)"
        }
        val cadence = if (details.type in setOf(
                com.example.kpkn.data.models.CardioType.BIKE_STATIONARY,
                com.example.kpkn.data.models.CardioType.BIKE_OUTDOOR,
                com.example.kpkn.data.models.CardioType.AIR_BIKE,
                com.example.kpkn.data.models.CardioType.ELLIPTICAL,
            )
        ) "Cadencia según equipo" else null
        return CardioGuide(
            rpeTarget = rpe,
            rpeTargetExact = details.resolvedRpe(),
            hrPercentRef = hr,
            cadenceRef = cadence,
        )
    }

    fun rpeAnchor(rpe: Int): String = when (rpe.coerceIn(1, 10)) {
        in 1..2 -> "Muy suave"
        in 3..4 -> "Suave"
        in 5..6 -> "Algo duro"
        in 7..8 -> "Duro"
        9 -> "Muy duro"
        else -> "Máximo"
    }

    fun rpeAnchor(rpe: Double): String = rpeAnchor(rpe.roundToInt())
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

    fun skipToNextBlock(details: CardioDetails, state: CardioTimerState, nowMs: Long): CardioTimerState {
        val progress = CardioIntervalEngine.progressAt(details, state.elapsedSeconds) ?: return state.copy(
            status = CardioExecutionStatus.AWAITING_CONFIRMATION,
            updatedAtMs = nowMs,
        )
        if (progress.isComplete || progress.currentBlock == null) {
            return state.copy(status = CardioExecutionStatus.AWAITING_CONFIRMATION, updatedAtMs = nowMs)
        }
        val nextElapsed = (progress.elapsedTotal + progress.remainingInBlock).coerceIn(0, progress.totalSeconds)
        val done = nextElapsed >= progress.totalSeconds
        return state.copy(
            elapsedSeconds = nextElapsed,
            remainingSeconds = (state.totalSeconds - nextElapsed).coerceAtLeast(0),
            status = if (done) CardioExecutionStatus.AWAITING_CONFIRMATION else state.status,
            updatedAtMs = nowMs,
        )
    }
}
