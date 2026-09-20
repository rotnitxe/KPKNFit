package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import kotlin.math.exp
import kotlin.math.roundToInt

data class InitialRecoveryPolicyInput(
    val evidence: InitialRecoveryEvidence?,
    val nowMs: Long,
    val workoutLogs: List<WorkoutLog> = emptyList(),
    val overrides: InitialRecoveryOverrides = InitialRecoveryOverrides(),
)

data class InitialRecoveryOverrides(
    val muscular: Int? = null,
    val system: Int? = null,
    val structure: Int? = null,
    val anchorMs: Long? = null,
)

data class InitialRecoveryContribution(
    val muscular: Int?,
    val system: Int?,
    val structure: Int?,
    val confidence: Int,
    val isEstimated: Boolean,
    val isExpired: Boolean,
    val hasRealOverlap: Boolean = false,
    val anchorMs: Long? = null,
    val label: String,
    val source: String,
)

object InitialRecoveryEvidencePolicy {
    private const val TAU_HOURS = 72.0

    fun resolve(input: InitialRecoveryPolicyInput): InitialRecoveryContribution {
        val evidence = input.evidence?.normalized()
        val expired = evidence != null && input.nowMs >= evidence.expiresAtMs
        val active = evidence?.takeUnless { expired }
        val overlap = active != null && input.workoutLogs.any { log ->
            val time = parseTime(log.date)
            time in active.coveredFromMs..active.coveredToMs
        }
        val overrideAnchor = input.overrides.anchorMs
        val anchorMs = overrideAnchor ?: active?.capturedAtMs
        val hasOverride = input.overrides.muscular != null ||
            input.overrides.system != null || input.overrides.structure != null
        val hasAnchor = hasOverride || active != null

        fun estimate(base: Int?, override: Int?): Int? {
            if (override == null && (active == null || overlap || base == null)) return null
            val anchor = overrideAnchor ?: active?.capturedAtMs ?: return null
            val anchoredScore = (override ?: base ?: return null).coerceIn(0, 100)
            val elapsedHours = ((input.nowMs - anchor).coerceAtLeast(0L) / 3_600_000.0)
            val recovered = 100.0 - (100 - anchoredScore) * exp(-elapsedHours / TAU_HOURS)
            return recovered.roundToInt().coerceIn(0, 100)
        }

        return InitialRecoveryContribution(
            muscular = estimate(active?.muscularScore, input.overrides.muscular),
            system = estimate(active?.systemScore, input.overrides.system),
            structure = estimate(active?.structureScore, input.overrides.structure),
            confidence = when {
                !hasAnchor -> 0
                overlap -> 65
                hasOverride -> 60
                else -> active?.confidence?.coerceIn(0, 100) ?: 0
            },
            isEstimated = active != null && !expired && !overlap,
            isExpired = expired,
            hasRealOverlap = overlap,
            anchorMs = anchorMs,
            label = when {
                hasOverride -> "Ajuste manual anclado"
                overlap -> "Historial real"
                active != null -> "Estimación inicial"
                else -> "Sin calibrar"
            },
            source = when {
                hasOverride -> "manual-anchor"
                overlap -> "workout-history"
                active != null -> active.sourceId
                else -> "none"
            },
        )
    }

    private fun parseTime(value: String): Long = runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrElse {
        runCatching {
            java.time.LocalDate.parse(value.take(10))
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.getOrDefault(0L)
    }
}
