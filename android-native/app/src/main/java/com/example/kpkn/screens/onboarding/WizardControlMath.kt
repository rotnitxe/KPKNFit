package com.example.kpkn.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Hogar neutral de los helpers puros que sobreviven al retiro de la UI
 * legacy de WizChat (los propios componentes de chat se borraron).
 *
 * - [WizChatWeightScale]: escala pura kg/libras usada por la persistencia
 *   productiva (`SetupWizardViewModel`) y por `WizChatWeightScaleTest`. La
 *   regla visual que la renderizaba ya no existe; la regla productiva vive en
 *   `design/WizardWeightRule` con su propia copia `WizardWeightScale`.
 * - [wizChatReducedMotion]: lectura de la escala de animación del sistema,
 *   usada por `SetupWelcomeScreen`. El paquete `design` tiene su propio
 *   `wizardReducedMotion()`.
 *
 * Mismo paquete y mismas firmas que antes: los llamadores compilan sin cambios.
 */

/**
 * Pure scale for the graduated weight rule: internal persistence is always kg,
 * display may be kg or lb, and every displayed value keeps one-decimal precision.
 */
object WizChatWeightScale {
    const val STEP = 0.1
    const val LB_TO_KG = 0.45359237

    fun toKg(display: Double, unit: String): Double = if (unit == "lb") display * LB_TO_KG else display

    fun toDisplay(kg: Double, unit: String): Double = if (unit == "lb") kg / LB_TO_KG else kg

    fun snap(display: Double): Double = Math.round(display * 10.0) / 10.0

    fun displayRange(unit: String): ClosedFloatingPointRange<Double> {
        val low = Math.ceil(toDisplay(20.0, unit) * 10.0) / 10.0
        val high = Math.floor(toDisplay(500.0, unit) * 10.0) / 10.0
        return low..high
    }

    fun clampDisplay(display: Double, unit: String): Double {
        val range = displayRange(unit)
        return snap(display).coerceIn(range.start, range.endInclusive)
    }

    fun format(display: Double): String =
        if (display % 1.0 == 0.0) display.toInt().toString() else "%.1f".format(display).replace('.', ',')
}

/** True when the system animator duration scale is 0 (motion disabled). */
@Composable
fun wizChatReducedMotion(): Boolean = LocalView.current.context.contentResolver.let { resolver ->
    runCatching { android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
}
