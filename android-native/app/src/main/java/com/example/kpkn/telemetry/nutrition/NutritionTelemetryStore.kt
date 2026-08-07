package com.example.kpkn.telemetry.nutrition

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Codificador JSON compacto (una línea por evento) sobre kotlinx.serialization.
 * Los Double/Float no finitos se serializan como null para evitar errores del codec.
 */
internal object NutritionJson {
    fun encode(fields: Map<String, Any?>): String =
        buildJsonObject {
            fields.forEach { (key, value) -> put(key, value.toJsonElement()) }
        }.toString()

    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Float -> if (isFinite()) JsonPrimitive(this) else JsonNull
        is Double -> if (isFinite()) JsonPrimitive(this) else JsonNull
        is Number -> JsonPrimitive(toDouble())
        is String -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(name)
        is Map<*, *> -> buildJsonObject {
            forEach { (key, value) -> put(key?.toString() ?: "null", value.toJsonElement()) }
        }
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }
}

/**
 * Sanitizador exclusivo de la telemetría de nutrición.
 * Redacta claves sensibles, patrones de secretos embebidos y trunca strings largos.
 * Los instrumentadores solo adjuntan métricas (longitudes, conteos, duraciones),
 * nunca el texto crudo de las comidas.
 */
internal object NutritionTelemetrySanitizer {
    private const val MAX_STRING_CHARS = 320
    private const val REDACTED = "<redacted>"

    private val SENSITIVE_KEY_FRAGMENTS = listOf(
        "apikey", "api_key", "apitoken", "token", "bearer", "authorization",
        "cookie", "password", "passwd", "secret", "credential",
    )
    private val SECRET_VALUE_PATTERNS = listOf(
        Regex("sk-[A-Za-z0-9][A-Za-z0-9_-]{6,}"),
        Regex("Bearer\\s+[A-Za-z0-9._\\-+/=]{6,}", RegexOption.IGNORE_CASE),
        Regex("(?i)(api[_-]?key|access[_-]?token|secret)(=|:)\\S+"),
    )

    fun sanitize(fields: Map<String, Any?>): Map<String, Any?> =
        fields.mapValues { (key, value) -> sanitizeValue(key, value) }

    private fun sanitizeValue(key: String, value: Any?): Any? {
        val lowered = key.lowercase(Locale.US)
        if (SENSITIVE_KEY_FRAGMENTS.any { lowered.contains(it) }) return REDACTED
        return when (value) {
            null -> null
            is String -> sanitizeString(value)
            is Map<*, *> -> value.entries.associate { (k, v) ->
                (k?.toString() ?: "null") to sanitizeValue(k?.toString() ?: "", v)
            }
            is Iterable<*> -> value.map { sanitizeValue(key, it) }
            is Array<*> -> value.map { sanitizeValue(key, it) }
            else -> value
        }
    }

    private fun sanitizeString(raw: String): String {
        var out = raw.replace('\n', ' ').replace('\r', ' ')
        SECRET_VALUE_PATTERNS.forEach { pattern -> out = pattern.replace(out, REDACTED) }
        return if (out.length > MAX_STRING_CHARS) out.take(MAX_STRING_CHARS) else out
    }
}


/**
 * Store JSONL exclusivo de la telemetría de nutrición (100% on-device).
 *
 * - Sin Context: recibe el directorio base → testeable en JVM puro.
 * - Escritura asíncrona en un único hilo (la UI nunca bloquea).
 * - [writeSync] con fsync solo para eventos de crash/cierre.
 * - Rotación por tamaño + retención por edad, tamaño total y recuento.
 */
internal class NutritionTelemetryStore(
    private val baseDir: File,
    private val sessionId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFileBytes: Long = 512L * 1024L,
    private val maxTotalBytes: Long = 10L * 1024L * 1024L,
    private val maxFileAgeMs: Long = 30L * 24L * 60L * 60L * 1000L,
    private val maxFiles: Int = 24,
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kpkn-nutri-telemetry").apply { isDaemon = true }
    }
    private val writeLock = Any()
    private var currentFile: File? = null

    /** Encola una escritura en el hilo del writer. Nunca lanza. */
    fun write(event: Map<String, Any?>) {
        runCatching {
            executor.execute { runCatching { writeNow(event, fsync = false) } }
        }
    }

    /** Escritura síncrona con fsync: reservada para crash/cierre de proceso. Nunca lanza. */
    fun writeSync(event: Map<String, Any?>) {
        runCatching { writeNow(event, fsync = true) }
    }

    /** Ejecuta trabajo de mantenimiento en el hilo del writer. */
    fun executeAsync(block: () -> Unit) {
        runCatching { executor.execute { runCatching(block) } }
    }

    fun shutdown() {
        runCatching { executor.shutdown() }
    }

    private fun writeNow(event: Map<String, Any?>, fsync: Boolean) {
        synchronized(writeLock) {
            baseDir.mkdirs()
            val file = rotateIfNeeded()
            FileOutputStream(file, true).use { out ->
                out.write(NutritionJson.encode(event).toByteArray(Charsets.UTF_8))
                out.write('\n'.code)
                if (fsync) runCatching { out.fd.sync() }
            }
        }
    }

    private fun rotateIfNeeded(): File {
        val existing = currentFile
        if (existing != null && existing.exists() && existing.length() <= maxFileBytes) return existing
        val stamp = fileStamp().format(Date(clock()))
        var candidate = File(baseDir, "nt-$stamp-$sessionId.jsonl")
        var suffix = 0
        while (candidate.exists() && candidate.length() > maxFileBytes && suffix < 64) {
            suffix += 1
            candidate = File(baseDir, "nt-$stamp-$sessionId-$suffix.jsonl")
        }
        currentFile = candidate
        return candidate
    }

    /** JSONL de telemetría + JSON standalone de crashes. */
    fun listFiles(): List<File> =
        baseDir.listFiles { file -> file.isFile && isTelemetryFile(file.name) }
            ?.sortedBy(File::lastModified)
            .orEmpty()

    private fun isTelemetryFile(name: String): Boolean =
        (name.startsWith("nt-") && name.endsWith(".jsonl")) || name.startsWith("nutrition-crash")

    /** Retención: borra viejos por edad, luego por recuento y por tamaño total. */
    fun pruneIfNeeded(now: Long = clock()) {
        synchronized(writeLock) {
            val all = listFiles().toMutableList()
            val iteratorAll = all.iterator()
            while (iteratorAll.hasNext()) {
                val file = iteratorAll.next()
                if (now - file.lastModified() > maxFileAgeMs) {
                    forgetIfDeleted(file)
                    iteratorAll.remove()
                }
            }
            while (all.size > maxFiles) {
                forgetIfDeleted(all.removeAt(0))
            }
            var total = all.sumOf { it.length() }
            while (total > maxTotalBytes && all.isNotEmpty()) {
                val victim = all.removeAt(0)
                total -= victim.length()
                forgetIfDeleted(victim)
            }
        }
    }

    private fun forgetIfDeleted(file: File) {
        runCatching { file.delete() }
        if (file == currentFile) currentFile = null
    }

    private fun fileStamp(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
}
