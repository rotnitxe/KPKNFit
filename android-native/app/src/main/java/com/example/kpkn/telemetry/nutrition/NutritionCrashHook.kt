package com.example.kpkn.telemetry.nutrition

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Hook de excepciones no capturadas para la telemetría de nutrición.
 *
 * Registra el crash en los JSONL locales (sync + fsync) y deja un marcador
 * persistente para que el siguiente arranque emita `previous_session_crash`.
 * Después delega en el handler previo para conservar el comportamiento del SO
 * (y de otros capturadores como ACRA/Firebase si existieran).
 */
object NutritionCrashHook {
    private val installed = AtomicBoolean(false)

    @Volatile private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                NutritionTelemetry.initialize(appContext)
                NutritionTelemetry.recordCrash(appContext, thread.name, throwable)
            }
            val prior = previousHandler
            if (prior != null) {
                prior.uncaughtException(thread, throwable)
            } else {
                exitProcess(10)
            }
        }
    }
}
