package com.example.kpkn.domain.workout

/**
 * Detects which workout tag progresses fastest from e1RM-per-session series.
 * Returns null unless there is enough comparable evidence.
 */
object TagProgressionAnalyzer {
    const val MIN_SESSIONS_PER_TAG = 3
    const val MIN_COMPARABLE_TAGS = 2
    const val MIN_SLOPE_SEPARATION_KG = 0.5

    data class TagSeries(
        val tagId: String,
        val tagName: String,
        val sessionE1rmsOldestFirst: List<Double>,
    )

    data class TagProgressionInsight(
        val tagId: String,
        val tagName: String,
        val slopeKgPerSession: Double,
        val sessions: Int,
    )

    fun bestProgressTag(series: Collection<TagSeries>): TagProgressionInsight? {
        val comparable = series.mapNotNull { item ->
            val values = item.sessionE1rmsOldestFirst.filter { it > 0.0 }
            if (values.size < MIN_SESSIONS_PER_TAG) return@mapNotNull null
            val slope = slopeKgPerSession(values) ?: return@mapNotNull null
            TagProgressionInsight(
                tagId = item.tagId,
                tagName = item.tagName,
                slopeKgPerSession = slope,
                sessions = values.size,
            )
        }
        if (comparable.size < MIN_COMPARABLE_TAGS) return null
        val ranked = comparable.sortedByDescending { it.slopeKgPerSession }
        val winner = ranked.first()
        val runnerUp = ranked.getOrNull(1) ?: return null
        if (winner.slopeKgPerSession <= 0.0) return null
        if (winner.slopeKgPerSession - runnerUp.slopeKgPerSession < MIN_SLOPE_SEPARATION_KG) return null
        return winner
    }

    private fun slopeKgPerSession(valuesOldestFirst: List<Double>): Double? {
        val n = valuesOldestFirst.size
        if (n < 2) return null
        val meanX = (n - 1) / 2.0
        val meanY = valuesOldestFirst.average()
        var num = 0.0
        var den = 0.0
        valuesOldestFirst.forEachIndexed { index, y ->
            val dx = index - meanX
            num += dx * (y - meanY)
            den += dx * dx
        }
        if (den == 0.0) return null
        return num / den
    }
}
