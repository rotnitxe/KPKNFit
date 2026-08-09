package com.example.kpkn.telemetry.nutrition

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * NutriTelemetry — telemetría exclusiva de nutrición.
 *
 * Objetivos:
 *  - Detectar crashes/cerca-crashes del flujo de registro por descripción.
 *  - Medir calidad del análisis (motor usado, % de alimentos resueltos, confianza IA,
 *    uso del fallback) y cuellos de botella (duración por etapa).
 *
 * Garantías:
 *  - 100% on-device; nada sale del teléfono salvo exportación manual del usuario.
 *  - Sin texto crudo de las comidas: solo métricas (longitudes, conteos, duraciones).
 *  - Nunca lanza excepciones y nunca bloquea el hilo llamante (salvo [recordCrash]).
 *
 * Layout en disco: filesDir/kpkn_logs/nutrition/<yyyyMMdd>/event-files.jsonl
 * Esquema de evento: contrato JSONL v2 del bus central.
 */
object NutritionTelemetry {
    const val SCHEMA_VERSION = KpknDiagnosticLogger.SCHEMA_VERSION
    internal const val DIR_NAME = "nutrition_telemetry"

    private const val PREFS = "nutrition_telemetry"
    private const val KEY_ENABLED = "telemetry_enabled"
    private const val KEY_IN_FLIGHT = "in_flight_descriptor"
    private const val KEY_PENDING_CRASH_FILE = "pending_crash_file"

    @Volatile private var application: Context? = null
    @Volatile private var store: NutritionTelemetryStore? = null

    private val sessionId: String = newId()
    private val seq = AtomicLong(0L)
    private val startedAtUptimeMs = SystemClock.elapsedRealtime()

    // ─── Ciclo de vida ───────────────────────────────────────────────────────

    @Synchronized
    fun initialize(context: Context) {
        val alreadyReady = store != null
        val app = context.applicationContext
        application = app
        if (!alreadyReady) {
            val freshStore = NutritionTelemetryStore(
                baseDir = File(app.filesDir, DIR_NAME),
                sessionId = sessionId,
            )
            store = freshStore
            freshStore.executeAsync {
                freshStore.pruneIfNeeded()
                consumePendingMarkers()
                emit("session_start", mapOf("apiLevel" to Build.VERSION.SDK_INT))
            }
        }
    }

    fun isInitialized(): Boolean = store != null

    fun isEnabled(): Boolean = prefs()?.getBoolean(KEY_ENABLED, true) ?: true

    fun setEnabled(enabled: Boolean) {
        val was = isEnabled()
        prefs()?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        if (enabled && !was) emit("telemetry_enabled")
        if (!enabled && was) emit("telemetry_disabled")
    }

    // ─── API de eventos y trazas ─────────────────────────────────────────────

    /** Evento sin traza (share, guardado, exportación…). */
    fun event(name: String, fields: Map<String, Any?> = emptyMap(), traceId: String? = null) {
        emit(name, fields, traceId)
    }

    /**
     * Abre una traza por análisis (source: "manual" | "shared").
     * Devuelve manejadores para spans de etapa y cierre con resultado.
     */
    fun startTrace(source: String, fields: Map<String, Any?> = emptyMap()): NutritionTrace {
        val trace = NutritionTrace(newId())
        val payload = LinkedHashMap<String, Any?>(fields)
        payload["source"] = source
        emit("analysis_start", payload, trace.traceId)
        return trace
    }

    internal fun emit(name: String, fields: Map<String, Any?> = emptyMap(), traceId: String? = null) {
        if (!isEnabled()) return
        val merged = baseFields(name, traceId)
        merged.putAll(fields)
        val sanitized = NutritionTelemetrySanitizer.sanitize(merged)
        val session = sanitized["sessionId"] as? String
        val trace = sanitized["traceId"] as? String
        val busFields = sanitized.filterKeys { key ->
            key !in setOf("schemaVersion", "timestamp", "event", "screen", "sessionId", "traceId")
        }
        KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = name,
            fields = busFields,
            traceId = trace,
            sessionId = session,
        )
    }

    /** Traza de un análisis: spans por etapa + cierre con resultado agregado. */
    class NutritionTrace internal constructor(val traceId: String) {
        private val traceStartedAtMs = SystemClock.elapsedRealtime()
        private val stageOrder = AtomicInteger(0)
        @Volatile private var closed = false

        fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
            emit(name, fields, traceId)
        }

        fun stageEnded(
            name: String,
            durationMs: Long,
            ok: Boolean,
            fields: Map<String, Any?> = emptyMap(),
            error: Throwable? = null,
        ) {
            val payload = LinkedHashMap<String, Any?>(fields)
            payload["stage"] = name
            payload["stageOrder"] = stageOrder.incrementAndGet()
            payload["durationMs"] = durationMs.coerceAtLeast(0L)
            payload["ok"] = ok
            if (error != null) {
                payload["errorType"] = error.javaClass.name
                payload["errorMessage"] = error.message ?: ""
            }
            emit("analysis_stage", payload, traceId)
        }

        /** Mide una etapa, registra el span y propaga cualquier fallo. */
        suspend fun <T> stage(name: String, fields: Map<String, Any?> = emptyMap(), block: suspend () -> T): T {
            val startedAtMs = SystemClock.elapsedRealtime()
            try {
                val result = block()
                stageEnded(name, SystemClock.elapsedRealtime() - startedAtMs, ok = true, fields = fields)
                return result
            } catch (failure: Throwable) {
                stageEnded(name, SystemClock.elapsedRealtime() - startedAtMs, ok = false, fields = fields, error = failure)
                throw failure
            }
        }

        fun end(outcome: String, fields: Map<String, Any?> = emptyMap()) {
            if (closed) return
            closed = true
            val payload = LinkedHashMap<String, Any?>(fields)
            payload["outcome"] = outcome
            payload["durationMs"] = (SystemClock.elapsedRealtime() - traceStartedAtMs).coerceAtLeast(0L)
            payload["stageCount"] = stageOrder.get()
            emit("analysis_end", payload, traceId)
        }
    }

    // ─── Marcadores in-flight / crash previo ─────────────────────────────────

    /**
     * Persiste (con commit) la etapa en curso de un análisis. Si el proceso muere,
     * el siguiente arranque emite `previous_session_exit` con la última etapa viva.
     */
    fun markInFlight(traceId: String, stage: String) {
        val descriptor = "$traceId|$stage|${Instant.now()}"
        prefs()?.edit()?.putString(KEY_IN_FLIGHT, descriptor)?.commit()
    }

    fun clearInFlight() {
        prefs()?.edit()?.remove(KEY_IN_FLIGHT)?.commit()
    }

    private fun consumePendingMarkers() {
        val prefs = prefs() ?: return
        val inFlight = prefs.getString(KEY_IN_FLIGHT, null)
        val crashFile = prefs.getString(KEY_PENDING_CRASH_FILE, null)
        prefs.edit()
            .remove(KEY_IN_FLIGHT)
            .remove(KEY_PENDING_CRASH_FILE)
            .commit()
        if (!inFlight.isNullOrBlank()) {
            val parts = inFlight.split("|")
            emit(
                "previous_session_exit",
                mapOf(
                    "previousTraceId" to parts.getOrNull(0),
                    "lastStage" to parts.getOrNull(1),
                    "markedAt" to parts.getOrNull(2),
                ),
            )
        }
        if (!crashFile.isNullOrBlank()) {
            emit("previous_session_crash", mapOf("crashFile" to crashFile))
        }
    }


    internal fun baseFields(event: String, traceId: String?): LinkedHashMap<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["schemaVersion"] = SCHEMA_VERSION
        map["timestamp"] = Instant.now().toString()
        map["epochMs"] = System.currentTimeMillis()
        map["elapsedSinceSessionStartMs"] = SystemClock.elapsedRealtime() - startedAtUptimeMs
        map["sessionId"] = sessionId
        map["seq"] = seq.incrementAndGet()
        map["event"] = event
        map["screen"] = "nutrition"
        if (traceId != null) map["traceId"] = traceId
        return map
    }


    // ─── Crash del proceso ───────────────────────────────────────────────────

    /**
     * Registra un crash no capturado. Llamado desde [NutritionCrashHook] en el hilo
     * moribundo: escribe de forma SÍNCRONA con fsync y deja marcador para el
     * siguiente arranque. Nunca lanza.
     */
    fun recordCrash(context: Context, threadName: String, throwable: Throwable) {
        val app = context.applicationContext
        application = app
        val inFlight = prefs(app)?.getString(KEY_IN_FLIGHT, null)
        val crashFields = linkedMapOf<String, Any?>(
            "errorType" to throwable.javaClass.name,
            "message" to (throwable.message ?: ""),
            "thread" to threadName,
            "stack" to runCatching { throwable.stackTraceToString() }.getOrDefault("").take(4_000),
            "inFlight" to inFlight,
        )
        runCatching {
            // Crash events use the same v2 bus as every other nutrition event.
            // Keeping a second synchronous JSON writer here would reintroduce
            // the parallel nutrition_telemetry/crash layout this consolidation
            // explicitly removes.
            val eventId = KpknDiagnosticLogger.event(
                namespace = "nutrition",
                name = "app_crash",
                fields = crashFields,
                sessionId = sessionId,
            )
            prefs(app)?.edit()
                ?.putString(KEY_PENDING_CRASH_FILE, eventId ?: "central-log-write-failed")
                ?.commit()
        }
    }

    // ─── Exportación manual (SAF) ────────────────────────────────────────────

    /** (recuento de archivos, KB totales) para mostrar en ajustes. */
    fun localSummary(): Pair<Int, Long> {
        val files = application?.let { KpknDiagnosticLogger.filesForArea(it, "nutrition") }.orEmpty()
        return files.size to (files.sumOf { it.length() } / 1024L)
    }

    /** Copia asíncrona de todos los JSONL/JSON al árbol SAF elegido por el usuario. */
    fun exportToAsync(context: Context, treeUri: Uri, onDone: (copied: Int, total: Int) -> Unit) {
        val app = context.applicationContext
        if (store == null) {
            onDone(0, 0)
            return
        }
        store?.executeAsync {
            val result = runCatching { exportTo(app, treeUri) }.getOrDefault(0 to 0)
            onDone(result.first, result.second)
        }
    }

    private fun exportTo(context: Context, treeUri: Uri): Pair<Int, Int> {
        if (!DocumentsContract.isTreeUri(treeUri)) return 0 to 0
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val files = KpknDiagnosticLogger.filesForArea(context, "nutrition")
        var copied = 0
        files.forEach { source ->
            runCatching {
                val mime = when (source.extension.lowercase(Locale.US)) {
                    "jsonl" -> "application/x-ndjson"
                    "json" -> "application/json"
                    else -> "application/octet-stream"
                }
                val target = findChild(context, treeUri, source.name)
                    ?: createDocument(context, treeUri, source.name, mime)
                    ?: error("No se pudo crear ${source.name}")
                context.contentResolver.openOutputStream(target, "wt")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                } ?: error("No se pudo escribir ${source.name}")
                copied += 1
            }
        }
        emit("telemetry_export", mapOf("filesCopied" to copied, "filesTotal" to files.size))
        return copied to files.size
    }


    private fun createDocument(context: Context, treeUri: Uri, displayName: String, mimeType: String): Uri? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, parent, mimeType, displayName.take(120))
        }.getOrNull()
    }

    private fun findChild(context: Context, treeUri: Uri, displayName: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == displayName) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(idIndex),
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }

    // ─── Utilidades internas ─────────────────────────────────────────────────

    private fun prefs(explicit: Context? = null): SharedPreferences? =
        (explicit ?: application)?.applicationContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
}
