package com.example.kpkn.data.diagnostics

import kotlinx.serialization.json.*

/** Bounds untrusted diagnostics and redacts nested objects, including already encoded JSON. */
object DiagnosticRedactor {
    private const val MAX_DEPTH = 6
    private const val MAX_ITEMS = 64
    private const val MAX_TEXT = 512
    private val sensitive = Regex(
        "(?i)key|token|secret|password|authorization|cookie|email|phone|transcri|utterance|" +
            "hypothes|phrase|spoken|recognized|raw|query|inputText|message|stackTrace|" +
            "health|weight|bodyFat|calorie|meal|foodName|location|latitude|longitude|address",
    )
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._-]+")
    private val apiKey = Regex("(?i)sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]+")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    private class Budget(var left: Int = 128)
    fun field(key: String, value: Any?): JsonElement =
        if (sensitive.containsMatchIn(key)) JsonPrimitive("[REDACTED]") else encode(value, 0, Budget())

    private val technicalFields = setOf("schemaVersion", "eventId", "sequence", "timestamp", "elapsedMs", "area", "subsystem", "event",
        "screen", "sessionId", "traceId", "process", "phase", "state", "status", "code", "reason", "origin", "generation",
        "durationMs", "latencyMs", "count", "queueSize", "dropped", "bytes", "app_version", "app_version_code", "enabled", "writer")
    fun productionField(key: String, value: Any?): JsonElement {
        if (key !in technicalFields || sensitive.containsMatchIn(key)) return JsonPrimitive("[REDACTED]")
        if (value is String && !value.matches(Regex("[A-Za-z0-9_.:+-]{1,180}"))) return JsonPrimitive("[REDACTED]")
        if (value is Map<*, *> || value is Iterable<*> || value is JsonObject || value is JsonArray) return JsonPrimitive("[REDACTED]")
        return field(key, value)
    }

    fun text(value: String): String = value.take(MAX_TEXT)
        .replace(bearer, "Bearer [REDACTED]").replace(apiKey, "[REDACTED]")
        .replace(email, "[REDACTED]")

    private fun encode(value: Any?, depth: Int, budget: Budget): JsonElement {
        if (depth >= MAX_DEPTH || budget.left-- <= 0) return JsonPrimitive("[TRUNCATED]")
        fun nested(key: String, item: Any?) = if (sensitive.containsMatchIn(key))
            JsonPrimitive("[REDACTED]") else encode(item, depth + 1, budget)
        return when (value) {
            null, JsonNull -> JsonNull
            is JsonObject -> JsonObject(value.entries.take(MAX_ITEMS).associate { (k, v) -> k.take(80) to nested(k, v) })
            is JsonArray -> JsonArray(value.take(MAX_ITEMS).map { encode(it, depth + 1, budget) })
            is JsonPrimitive -> if (value.isString) encode(value.content, depth + 1, budget) else value
            is Map<*, *> -> JsonObject(value.entries.take(MAX_ITEMS).associate { (k, v) ->
                val name = k.toString().take(80)
                name to nested(name, v)
            })
            is Iterable<*> -> JsonArray(value.take(MAX_ITEMS).map { encode(it, depth + 1, budget) })
            is Array<*> -> JsonArray(value.take(MAX_ITEMS).map { encode(it, depth + 1, budget) })
            is Boolean -> JsonPrimitive(value)
            is Double -> if (value.isFinite()) JsonPrimitive(value) else JsonNull
            is Float -> if (value.isFinite()) JsonPrimitive(value) else JsonNull
            is Number -> JsonPrimitive(value)
            is String -> if (value.trimStart().let { it.startsWith("{") || it.startsWith("[") }) {
                if (value.length > 4096) JsonPrimitive("[TRUNCATED]") else runCatching {
                    encode(Json.parseToJsonElement(value), depth + 1, budget)
                }.getOrElse { JsonPrimitive("[REDACTED]") }
            } else JsonPrimitive(text(value))
            else -> JsonPrimitive("[${value.javaClass.simpleName.take(64)}]")
        }
    }
}
