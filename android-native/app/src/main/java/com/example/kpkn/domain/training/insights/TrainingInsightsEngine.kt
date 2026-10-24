package com.example.kpkn.domain.training.insights

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class FactOrigin { OBSERVED, SELF_REPORTED, DESCRIPTIVE_CALCULATION, ESTIMATED, HISTORICAL_AMBIGUOUS }

/** A projection of the existing activity, never a parallel workout or a physiological state. */
@Serializable
data class TrainingFact(
    val sourceId: String,
    val activityId: String,
    val occurredAtMs: Long,
    val sport: SportId,
    val metric: String,
    val value: Double,
    val unit: String,
    val origin: FactOrigin,
    val exerciseVariant: String?,
    val method: String?,
    @SerialName("protocolId") val protocol: String?,
    val context: Map<String, String> = emptyMap(),
)

data class TrainingComparison(
    val current: TrainingFact,
    val previous: List<TrainingFact>,
    val median: Double?,
    val differenceFromMedian: Double?,
    val explanation: String,
)

data class TrainingCoverage(val activities: Int, val facts: Int, val sports: Set<SportId>)

/** Explicit time, strict comparability and descriptive statistics; no load/prescription API. */
object TrainingInsightsEngine {
    const val REFERENCE_DAYS = 90L
    const val REFERENCE_COUNT = 6
    const val SUMMARY_MINIMUM = 3

    fun visible(facts: List<TrainingFact>, nowMs: Long, weeks: Int = 4): List<TrainingFact> {
        require(weeks in setOf(1, 4, 12))
        val from = nowMs - weeks * 7L * 86_400_000L
        return facts.filter { it.sourceId.isNotBlank() && it.activityId.isNotBlank() && it.occurredAtMs in from..nowMs && it.value.isFinite() }
            .groupBy { it.sourceId }.values.filter { it.distinct().size == 1 }.map { it.first() }.sortedBy { it.occurredAtMs }
    }

    fun coverage(facts: List<TrainingFact>, nowMs: Long, weeks: Int = 4): TrainingCoverage {
        val valid = visible(facts, nowMs, weeks)
        return TrainingCoverage(valid.map { it.activityId }.distinct().size, valid.size, valid.map { it.sport }.toSet())
    }

    fun compare(current: TrainingFact, history: List<TrainingFact>, nowMs: Long): TrainingComparison {
        require(current.value.isFinite() && current.occurredAtMs <= nowMs)
        val from = current.occurredAtMs - REFERENCE_DAYS * 86_400_000L
        val previous = history.groupBy { it.sourceId }.values.filter { it.distinct().size == 1 }.map { it.first() }.asSequence()
            .filter { it.value.isFinite() && it.occurredAtMs in from until current.occurredAtMs }
            .filter { it.activityId != current.activityId && it.sourceId != current.sourceId }
            .filter { comparable(current, it) }
            .distinctBy { it.sourceId }
            .sortedWith(compareByDescending<TrainingFact> { it.occurredAtMs }.thenBy { it.sourceId })
            .take(REFERENCE_COUNT).toList()
        val ordered = previous.map { it.value }.sorted()
        val median = if (ordered.size < SUMMARY_MINIMUM) null else if (ordered.size % 2 == 1)
            ordered[ordered.size / 2] else ordered[ordered.size / 2 - 1] / 2 + ordered[ordered.size / 2] / 2
        return TrainingComparison(current, previous, median, median?.let { current.value - it },
            if (median == null) "Sin suficientes registros comparables. La ausencia de datos no indica descanso."
            else "Comparación descriptiva de ${previous.size} registros anteriores; no mide recuperación ni seguridad.")
    }

    private fun known(value: String?): Boolean = !value.isNullOrBlank() &&
        value.trim().lowercase().split(':', '@').all { it.trim() !in
            setOf("", "unknown", "desconocido", "desconocida", "sin datos", "null", "unspecified", "not_recorded") }

    fun comparable(a: TrainingFact, b: TrainingFact): Boolean =
        known(a.exerciseVariant) && known(a.method) && known(a.protocol) && known(a.metric) && known(a.unit) &&
            a.context.isNotEmpty() && a.context.all { (key,value) -> known(key) && known(value) } &&
            a.sport == b.sport && a.metric == b.metric && a.unit == b.unit && a.origin == b.origin &&
            a.origin != FactOrigin.HISTORICAL_AMBIGUOUS &&
            a.exerciseVariant == b.exerciseVariant && a.method == b.method && a.protocol == b.protocol && a.context == b.context
}
