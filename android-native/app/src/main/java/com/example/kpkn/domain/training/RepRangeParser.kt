package com.example.kpkn.domain.training

import com.example.kpkn.data.models.RepRange

/** Pure parser/formatter for the strength editor's `min-max` input. */
object RepRangeParser {
    private val separators = Regex("[-–—−]")

    fun parse(raw: String): RepRange? {
        val normalized = raw.trim()
            .replace(" ", "")
            .replace(separators, "-")
        if (normalized.isBlank()) return null

        val parts = normalized.split('-')
        return when (parts.size) {
            1 -> parts.single().toIntOrNull()?.takeIf { it > 0 }?.let { RepRange(it, it) }
            2 -> {
                val min = parts[0].toIntOrNull()
                val max = parts[1].toIntOrNull()
                if (min == null || max == null || min <= 0 || max < min) null
                else RepRange(min, max)
            }
            else -> null
        }
    }

    fun isCompleteInput(raw: String): Boolean {
        val normalized = raw.trim().replace(" ", "").replace(separators, "-")
        return normalized.isEmpty() || parse(normalized) != null
    }

    fun format(range: RepRange?): String = range?.format().orEmpty()
}
