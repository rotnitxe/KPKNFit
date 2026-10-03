package com.example.kpkn.telemetry.nutrition

import java.util.Locale

/** Sanitiza métricas de nutrición antes de entregarlas al bus JSONL central. */
internal object NutritionTelemetrySanitizer {
    private const val MAX_STRING_CHARS = 320
    private const val REDACTED = "<redacted>"

    /** Paquete de la app: de un stack solo se conservan los frames que empiezan así (lo demás es framework o librerías). */
    private const val APP_PACKAGE = "com.example.kpkn."
    private const val MAX_APP_FRAMES = 12
    private const val MAX_CAUSE_DEPTH = 4

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

    /**
     * Resumen de un fallo que es seguro registrar: el nombre corto de la excepción y su primer frame de la app
     * (`clase.método:línea`). Nunca el mensaje: un mensaje repite lo que se intentó leer (un `NumberFormatException` cita el
     * texto) y la telemetría no lleva texto de comidas. Si el stack no tiene frames de la app queda solo el nombre. No lanza.
     */
    fun errorSummary(error: Throwable): String {
        val name = runCatching { shortName(error) }.getOrDefault("Throwable")
        val frame = runCatching { firstAppFrame(error) }.getOrNull() ?: return name
        return "$name at ${frameText(frame)}"
    }

    /**
     * Los primeros [maxFrames] frames de la app del stack de [error], uno por línea. Sin mensaje y sin los frames del
     * framework o de librerías (que son los que más pesan y menos dicen de la causa). No lanza.
     */
    fun appStack(error: Throwable, maxFrames: Int = MAX_APP_FRAMES): String =
        runCatching {
            error.stackTrace.asSequence().filter(::isAppFrame).take(maxFrames).joinToString("\n") { "at ${frameText(it)}" }
        }.getOrDefault("")

    private fun shortName(error: Throwable): String =
        error.javaClass.simpleName.ifBlank { error.javaClass.name.substringAfterLast('.') }

    /** El primer frame de la app del stack de [error] o, si ese stack no tiene ninguno, el de su causa (hasta [MAX_CAUSE_DEPTH] niveles). */
    private fun firstAppFrame(error: Throwable): StackTraceElement? =
        generateSequence(error) { current -> current.cause?.takeIf { it !== current } }
            .take(MAX_CAUSE_DEPTH + 1)
            .firstNotNullOfOrNull { throwable -> throwable.stackTrace.firstOrNull(::isAppFrame) }

    private fun isAppFrame(frame: StackTraceElement): Boolean = frame.className.startsWith(APP_PACKAGE)

    private fun frameText(frame: StackTraceElement): String =
        "${frame.className}.${frame.methodName}" + if (frame.lineNumber >= 0) ":${frame.lineNumber}" else ""

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
