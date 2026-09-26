package com.example.kpkn.screens.onboarding.design

/**
 * Una sola pantalla para altura y peso, con las dos reglas horizontales, solo
 * cuando el hueco real de la viewport las contiene enteras.
 *
 * Por encima de [MAX_FONT_SCALE] (fuente grande) o si la suma no cabe, se
 * quedan dos pasos: no se encoge la tipografía ni se fuerza scroll para
 * meterlas.
 *
 * El presupuesto está en dp lógicos, escalando solo las líneas de texto con
 * `fontScale`. Coincide con la columna combinada: pregunta + subtítulo, y por
 * cada regla un rótulo, el toggle de unidad y la regla de 110 dp.
 */
object WizardAnthropometryFit {
    const val MAX_FONT_SCALE = 1.15f

    fun fits(viewportHeightDp: Float, fontScale: Float): Boolean {
        if (!viewportHeightDp.isFinite() || viewportHeightDp <= 0f) return false
        if (!fontScale.isFinite() || fontScale <= 0f || fontScale > MAX_FONT_SCALE) return false
        return requiredViewportDp(fontScale) <= viewportHeightDp
    }

    fun requiredViewportDp(fontScale: Float): Float {
        val text = fontScale.coerceAtLeast(1f)
        val header = 44f + 57f * text
        val block = 214f + 44f * text
        val controls = block * 2f + 12f + 20f
        // Margen para huecos de layout que el presupuesto no modela al dp.
        return header + controls + 24f
    }
}
