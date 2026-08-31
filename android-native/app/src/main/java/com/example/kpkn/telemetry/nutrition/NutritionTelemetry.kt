package com.example.kpkn.telemetry.nutrition

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.diagnostics.TelemetryPriority
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    /** Legacy constant retained for source compatibility; no directory is created. */
    @Deprecated("Nutrition telemetry is stored only in kpkn_logs/nutrition")
    internal const val DIR_NAME = "nutrition_telemetry"

    private const val PREFS = "nutrition_telemetry"
    private const val KEY_IN_FLIGHT = "in_flight_descriptor"
    private const val KEY_PENDING_CRASH_FILE = "pending_crash_file"

    @Volatile private var application: Context? = null
    @Volatile private var initialized = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionId: String = newId()
    private val seq = AtomicLong(0L)
    private fun elapsedRealtimeSafe(): Long =
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }

    private val startedAtUptimeMs = elapsedRealtimeSafe()

    // ─── Ciclo de vida ───────────────────────────────────────────────────────

    @Synchronized
    fun initialize(context: Context) {
        val app = context.applicationContext
        application = app
        if (!initialized) {
            initialized = true
            ioScope.launch {
                consumePendingMarkers()
                emit("session_start", mapOf("apiLevel" to Build.VERSION.SDK_INT))
            }
        }
    }

    fun isInitialized(): Boolean = initialized

    /** Nutrition diagnostics are mandatory so parser/crash evidence cannot be disabled. */
    fun isEnabled(): Boolean = true

    @Deprecated("Nutrition telemetry is always enabled")
    fun setEnabled(enabled: Boolean) = Unit

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
        emit("interpretation_started", payload, trace.traceId)
        emit("analysis_start", payload, trace.traceId)
        return trace
    }

    // Stable local contract for the interpretation pipeline. Values are metrics
    // only; callers must never pass meal text, credentials or raw descriptions.
    fun candidateSelected(traceId: String, rank: Int, confidence: Double?, source: String) =
        event("candidate_selected", mapOf("rank" to rank, "confidence" to confidence, "source" to source), traceId)

    fun clarificationRequested(traceId: String, kind: String) =
        event("clarification_requested", mapOf("kind" to kind), traceId)

    fun clarificationAnswered(traceId: String, kind: String, answer: String) =
        event("clarification_answered", mapOf("kind" to kind, "answer" to answer), traceId)

    fun interpretationFinalized(traceId: String, status: String, candidateCount: Int) =
        event("interpretation_finalized", mapOf("status" to status, "candidateCount" to candidateCount), traceId)

    fun manualCorrection(traceId: String, field: String) =
        event("manual_correction", mapOf("field" to field), traceId)

    fun calibrationUpdated(profileVersion: Int, status: String) =
        event("calibration_updated", mapOf("profileVersion" to profileVersion, "status" to status))

    fun catalogImportStarted(datasetVersion: String? = null) =
        event("catalog_import_started", mapOf("datasetVersion" to datasetVersion))

    fun catalogImportCompleted(datasetVersion: String, acceptedRows: Int? = null) =
        event("catalog_import_completed", mapOf("datasetVersion" to datasetVersion, "acceptedRows" to acceptedRows))

    fun catalogImportFailed(errorType: String) =
        event("catalog_import_failed", mapOf("errorType" to errorType.take(120)))

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
        private val traceStartedAtMs = elapsedRealtimeSafe()
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
            // CRI-ANALYSIS: la telemetría jamás debe lanzar (contrato del objeto). Si la
            // emisión fallara, un stageEnded lanzando dentro del catch de stage() enmascararía
            // el error original y, peor, convertiría un salvage exitoso en un falso fracaso.
            runCatching {
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
        }

        /** Mide una etapa, registra el span y propaga cualquier fallo. */
        suspend fun <T> stage(name: String, fields: Map<String, Any?> = emptyMap(), block: suspend () -> T): T {
            val startedAtMs = elapsedRealtimeSafe()
            try {
                val result = block()
                stageEnded(name, elapsedRealtimeSafe() - startedAtMs, ok = true, fields = fields)
                return result
            } catch (failure: Throwable) {
                stageEnded(name, elapsedRealtimeSafe() - startedAtMs, ok = false, fields = fields, error = failure)
                throw failure
            }
        }

        fun end(outcome: String, fields: Map<String, Any?> = emptyMap()) {
            if (closed) return
            closed = true
            runCatching {
                val payload = LinkedHashMap<String, Any?>(fields)
                payload["outcome"] = outcome
                payload["durationMs"] = (elapsedRealtimeSafe() - traceStartedAtMs).coerceAtLeast(0L)
                payload["stageCount"] = stageOrder.get()
                emit("analysis_end", payload, traceId)
            }
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
        map["elapsedSinceSessionStartMs"] = elapsedRealtimeSafe() - startedAtUptimeMs
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
                priority = TelemetryPriority.CRITICAL,
            )
            KpknDiagnosticLogger.flushSync()
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
        ioScope.launch {
            val result = runCatching {
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.configure(app, treeUri)
                KpknDiagnosticLogger.flushSync()
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.mirrorRecoveryFiles(app)
                val files = KpknDiagnosticLogger.filesForArea(app, "nutrition")
                files.size to files.size
            }.getOrDefault(0 to 0)
            onDone(result.first, result.second.toInt())
        }
    }

    // ─── Utilidades internas ─────────────────────────────────────────────────

    private fun prefs(explicit: Context? = null): SharedPreferences? =
        (explicit ?: application)?.applicationContext
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
}
