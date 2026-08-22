package com.example.kpkn.telemetry.nutrition

import java.util.Locale

/** Sanitiza métricas de nutrición antes de entregarlas al bus JSONL central. */
internal object NutritionTelemetrySanitizer {
    private const val MAX_STRING_CHARS = 320
    private const val REDACTED = "<redacted>"
    private val sensitiveKeyFragments = listOf(
        "apikey", "api_key", "apitoken", "token", "bearer", "authorization",
        "cookie", "password", "passwd", "secret", "credential",
    )
    private val secretValuePatterns = listOf(
        Regex("sk-[A-Za-z0-9][A-Za-z0-9_-]{6,}"),
        Regex("Bearer\\s+[A-Za-z0-9._\\-+/=]{6,}", RegexOption.IGNORE_CASE),
        Regex("(?i)(api[_-]?key|access[_-]?token|secret)(=|:)\\S+"),
    )

    fun sanitize(fields: Map<String, Any?>): Map<String, Any?> =
        fields.mapValues { (key, value) -> sanitizeValue(key, value) }

    private fun sanitizeValue(key: String, value: Any?): Any? {
        if (sensitiveKeyFragments.any { key.lowercase(Locale.US).contains(it) }) return REDACTED
        return when (value) {
            null -> null
            is String -> sanitizeString(value)
            is Map<*, *> -> value.entries.associate { (nestedKey, nestedValue) ->
                (nestedKey?.toString() ?: "null") to sanitizeValue(nestedKey?.toString() ?: "", nestedValue)
            }
            is Iterable<*> -> value.map { sanitizeValue(key, it) }
            is Array<*> -> value.map { sanitizeValue(key, it) }
            else -> value
        }
    }

    private fun sanitizeString(raw: String): String {
        var value = raw.replace('\n', ' ').replace('\r', ' ')
        secretValuePatterns.forEach { pattern -> value = pattern.replace(value, REDACTED) }
        return value.take(MAX_STRING_CHARS)
    }
}
