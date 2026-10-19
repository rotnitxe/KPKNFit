package com.example.kpkn.data.work

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * La puerta del análisis de técnica en vídeo (§9.4): quién puede analizar y cuándo.
 *
 * - [mutex]: un análisis cada vez en todo el proceso. El Worker lo toma entero, de principio a fin.
 * - [cameraBusy]: la cámara está grabando. El grabador (VT8a) lo pone a `true` al empezar a grabar y a `false` al parar; el
 *   análisis espera, porque decodificar y estimar la pose a la vez que se graba arruinaría los fotogramas de la grabación.
 * - Calor: con el móvil en estado térmico SEVERE o peor (API 29+) tampoco se analiza.
 *
 * El pipeline vuelve a llamar a [awaitClear] cada pocos fotogramas, así que una grabación que empieza a mitad de un análisis
 * también lo pausa.
 */
object AnalysisGate {
    /** Un análisis cada vez. */
    val mutex = Mutex()

    /** `true` mientras la cámara graba; VT8a lo mantiene. */
    val cameraBusy = MutableStateFlow(false)

    /** `PowerManager.THERMAL_STATUS_SEVERE` (API 29), para no depender del nivel de API al compilar la lógica. */
    const val THERMAL_SEVERE = 3

    /** Cada cuánto se vuelve a mirar el estado térmico mientras está demasiado caliente. */
    const val THERMAL_POLL_MS = 5_000L

    @Volatile
    internal var thermalPollMs: Long = THERMAL_POLL_MS

    /** Cómo se lee el estado térmico (`PowerManager.THERMAL_STATUS_*`, o -1 si no se sabe). Las pruebas lo sustituyen. */
    @Volatile
    internal var thermalStatus: (Context) -> Int = { context -> readThermalStatus(context) }

    /**
     * Espera, sin gastar CPU, a que la cámara esté libre y el móvil no esté en estado térmico SEVERE o peor. Se cancela con
     * la corrutina que la llama.
     */
    suspend fun awaitClear(context: Context) {
        while (true) {
            cameraBusy.first { busy -> !busy }
            if (!isTooHot(context)) return
            delay(thermalPollMs)
        }
    }

    /** Estado térmico SEVERE o peor (siempre `false` por debajo de la API 29, que no lo informa). */
    fun isTooHot(context: Context): Boolean = thermalStatus(context) >= THERMAL_SEVERE

    private fun readThermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        return try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } catch (_: RuntimeException) {
            -1
        }
    }
}
