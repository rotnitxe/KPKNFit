package com.example.kpkn.ui.design

import java.util.Locale

/**
 * Formatos de la tabla de series que el proyecto no tenía (SE1). Las cargas se escriben con `NativeProgressionText.formatKg` («82,5») y
 * los rangos con `RepRange.format()` («6–8»): esos se reutilizan tal cual. Aquí solo el descanso.
 */
object KpknSetFormat {
    /** Descanso «2:30»: minutos sin cero a la izquierda y segundos con dos cifras. Lo que sea menor que cero cuenta como 0. */
    fun rest(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        return String.format(Locale.forLanguageTag("es"), "%d:%02d", safe / 60, safe % 60)
    }
}
