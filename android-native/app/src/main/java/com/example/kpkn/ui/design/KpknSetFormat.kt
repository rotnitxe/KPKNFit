package com.example.kpkn.ui.design

import java.util.Locale

/**
 * Formatos de la tabla de series que el proyecto no tenía (SE1). Las cargas se escriben con `NativeProgressionText.formatKg` («82,5») y
 * los rangos con `RepRange.format()` («6–8»): esos se reutilizan tal cual. Aquí el descanso de las celdas (m:ss) y el tiempo de texto
 * corrido ([doseTime]).
 */
object KpknSetFormat {
    /** Descanso «2:30»: minutos sin cero a la izquierda y segundos con dos cifras. Lo que sea menor que cero cuenta como 0. */
    fun rest(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(Locale.forLanguageTag("es"), "%d:%02d", safe / 60, safe % 60)
    }

    /**
     * Tiempo en texto corrido, como «descanso 45 s» o «3 × 40 s» (SE9): por debajo de un minuto, los segundos con su unidad y un espacio
     * inseparable («45 s»); desde un minuto, m:ss como [rest] («3:15»). Lo que sea menor que cero cuenta como 0.
     */
    fun doseTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return if (safe < 60) "$safe s" else rest(safe)
    }
}
