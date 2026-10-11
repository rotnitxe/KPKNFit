package com.example.kpkn.telemetry

import android.content.Context
import android.os.SystemClock
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger

class KpknTelemetry private constructor(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        @Volatile
        private var instance: KpknTelemetry? = null

        fun getInstance(context: Context): KpknTelemetry {
            return instance ?: synchronized(this) {
                instance ?: KpknTelemetry(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    fun logEvent(eventName: String, vararg params: Pair<String, Any?>, traceId: String? = null) {
        KpknDiagnosticLogger.event(
            namespace = namespaceFor(eventName),
            name = eventName,
            fields = params.toMap(),
            traceId = traceId,
        )
    }

    fun setUserProperty(name: String, value: String?) {
        KpknDiagnosticLogger.event(
            "app",
            "user_property",
            mapOf(
                "name" to name,
                "hasValue" to !value.isNullOrBlank(),
                "value" to value,
            ),
        )
    }

    fun logException(exception: Throwable, fatal: Boolean = false) {
        KpknDiagnosticLogger.exception(
            namespace = "app",
            name = if (fatal) "fatal_exception" else "exception",
            error = exception,
            fields = mapOf("fatal" to fatal),
        )
    }

    fun logError(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            KpknDiagnosticLogger.event("app", "error", mapOf("message" to message))
        } else {
            KpknDiagnosticLogger.exception("app", "error", throwable, mapOf("message" to message))
        }
    }

    fun startTrace(name: String): Trace = Trace(name)

    fun setCrashlyticsEnabled(enabled: Boolean) {
        KpknDiagnosticLogger.event("app", "crash_reporting_changed", mapOf("enabled" to enabled))
    }

    fun setUserId(userId: String?) {
        KpknDiagnosticLogger.event("app", "user_id_changed", mapOf("hasUserId" to !userId.isNullOrBlank()))
    }

    private fun namespaceFor(eventName: String): String {
        val value = eventName.lowercase()
        return when {
            "nutrition" in value || "food" in value || "meal" in value -> "nutrition"
            "auge" in value -> "auge"
            "workout" in value || "set_" in value || "rest" in value -> "workout"
            "performance" in value || "rir" in value || "rpe" in value -> "performance"
            "program" in value -> "programs"
            "assistant" in value || "coach" in value -> "assistant"
            "learn" in value || "quiz" in value || "badge" in value -> "learn"
            "health" in value || "sleep" in value || "wellbeing" in value -> "health"
            "tts" in value || "speech" in value -> "tts"
            "ai" in value || "api" in value || "backend" in value -> "backend"
            else -> "app"
        }
    }

    class Trace(private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()

        fun stop() {
            val duration = SystemClock.elapsedRealtime() - startTime
            KpknDiagnosticLogger.event("performance", "trace_stopped", mapOf("name" to name, "durationMs" to duration))
        }

        fun start() {
            KpknDiagnosticLogger.event("performance", "trace_started", mapOf("name" to name))
        }

        fun incrementMetric(metric: String, value: Long = 1) {
            KpknDiagnosticLogger.event("performance", "trace_metric", mapOf("name" to name, "metric" to metric, "value" to value))
        }

        fun putAttribute(key: String, value: String) {
            KpknDiagnosticLogger.event("performance", "trace_attribute", mapOf("name" to name, "key" to key, "value" to if (key.contains("key", ignoreCase = true) || key.contains("token", ignoreCase = true)) "[REDACTED]" else value))
        }
    }
}

fun Context.logKpknEvent(eventName: String, vararg params: Pair<String, Any?>, traceId: String? = null) {
    KpknTelemetry.getInstance(this).logEvent(eventName, *params, traceId = traceId)
}

fun Context.logKpknError(message: String, throwable: Throwable? = null) {
    KpknTelemetry.getInstance(this).logError(message, throwable)
}

fun Context.logKpknException(exception: Throwable, fatal: Boolean = false) {
    KpknTelemetry.getInstance(this).logException(exception, fatal)
}
