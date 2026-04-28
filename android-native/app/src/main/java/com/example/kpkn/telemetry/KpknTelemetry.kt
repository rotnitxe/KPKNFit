package com.example.kpkn.telemetry

import android.content.Context
import android.os.SystemClock

class KpknTelemetry private constructor(context: Context) {

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

    fun logEvent(eventName: String, vararg params: Pair<String, Any?>) {}

    fun setUserProperty(name: String, value: String?) {}

    fun logException(exception: Throwable, fatal: Boolean = false) {}

    fun logError(message: String, throwable: Throwable? = null) {}

    fun startTrace(name: String): Trace = Trace(name)

    fun setCrashlyticsEnabled(enabled: Boolean) {}

    fun setUserId(userId: String?) {}

    class Trace(private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()

        fun stop() {
            val duration = SystemClock.elapsedRealtime() - startTime
        }

        fun start() {}
        fun incrementMetric(metric: String, value: Long = 1) {}
        fun putAttribute(key: String, value: String) {}
    }
}

fun Context.logKpknEvent(eventName: String, vararg params: Pair<String, Any?>) {
    KpknTelemetry.getInstance(this).logEvent(eventName, *params)
}

fun Context.logKpknError(message: String, throwable: Throwable? = null) {
    KpknTelemetry.getInstance(this).logError(message, throwable)
}

fun Context.logKpknException(exception: Throwable, fatal: Boolean = false) {
    KpknTelemetry.getInstance(this).logException(exception, fatal)
}
