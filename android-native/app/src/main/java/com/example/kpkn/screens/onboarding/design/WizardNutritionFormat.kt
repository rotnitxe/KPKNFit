package com.example.kpkn.screens.onboarding.design

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.nutrition.PaceZone
import com.example.kpkn.domain.nutrition.PlanWarning
import com.example.kpkn.domain.nutrition.RiskSeverity
import com.example.kpkn.domain.nutrition.WizardPacePreset
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Formato y textos del panel «Tu plan de alimentación». Todo es puro (sin Compose) para poder probarlo en
 * JVM: cifras en español (coma decimal, punto de millar, signo menos tipográfico) y los textos cortos de
 * los avisos, las zonas de ritmo y las muescas.
 */

/** Signo menos tipográfico (U+2212): el que usan las cifras del panel («−0,45 kg/sem»). */
internal const val WIZARD_MINUS = "−"

/** Kcal con punto de millar siempre: 2300 → «2.300». */
fun formatKcalEs(value: Int): String {
    val grouped = abs(value).toString().reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) WIZARD_MINUS + grouped else grouped
}

/** Cifra con coma decimal y [decimals] decimales, sin signo positivo: 1.5 → «1,5». */
fun formatDecimalEs(value: Double, decimals: Int): String {
    val rounded = roundTo(value, decimals)
    val text = String.format(Locale.ROOT, "%.${decimals}f", abs(rounded)).replace('.', ',')
    return if (rounded < 0.0) WIZARD_MINUS + text else text
}

/**
 * Cambio de peso con signo explícito: «−0,45», «+0,25». Un valor que se redondea a cero no lleva signo
 * («0,00»): un cero con signo diría que hay un cambio que no existe.
 */
fun formatSignedDecimalEs(value: Double, decimals: Int): String {
    val rounded = roundTo(value, decimals)
    val body = String.format(Locale.ROOT, "%.${decimals}f", abs(rounded)).replace('.', ',')
    return when {
        rounded < 0.0 -> WIZARD_MINUS + body
        rounded > 0.0 -> "+$body"
        else -> body
    }
}

private fun roundTo(value: Double, decimals: Int): Double {
    if (!value.isFinite()) return 0.0
    val factor = 10.0.pow(decimals)
    return (value * factor).roundToLong() / factor
}

/** Etiqueta de una zona de ritmo; una sola palabra. */
fun paceZoneLabel(zone: PaceZone): String = when (zone) {
    PaceZone.NONE -> "Mantenimiento"
    PaceZone.SUSTAINABLE -> "Sostenible"
    PaceZone.DEMANDING -> "Exigente"
    PaceZone.AGGRESSIVE -> "Agresivo"
    PaceZone.EXTREME -> "Extremo"
}

/** Nombre de la muesca del control de ritmo. */
fun paceNotchLabel(preset: WizardPacePreset): String = when (preset) {
    WizardPacePreset.SLOW -> "Lento"
    WizardPacePreset.MEDIUM -> "Medio"
    WizardPacePreset.FAST -> "Rápido"
}

/**
 * Texto del aviso: icono aparte y como mucho seis palabras. [loss] distingue el ritmo al adelgazar del de
 * ganar, que no se explican igual.
 */
fun planWarningText(warning: PlanWarning, loss: Boolean): String = when (warning) {
    PlanWarning.LOW_CALORIES_SOFT -> "Calorías bajas para tu perfil"
    PlanWarning.LOW_CALORIES_HARD -> "Calorías demasiado bajas"
    PlanWarning.PACE_AGGRESSIVE ->
        if (loss) "Ritmo agresivo: puedes perder músculo" else "Ritmo alto: ganarás más grasa"
    PlanWarning.PACE_EXTREME ->
        if (loss) "Ritmo extremo: demasiado rápido" else "Ritmo extremo: ganancia excesiva"
    PlanWarning.LOW_PROTEIN -> "Proteína baja para definir"
    PlanWarning.LOW_FAT -> "Grasas muy bajas"
}

/** Inicial del día de la semana: L M X J V S D. */
fun weekdayInitialEs(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

/** Nombre completo del día (para lectores de pantalla y el pie del anillo). */
fun weekdayNameEs(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

/**
 * Colores del panel. Los de los macros son los de la pantalla Nutrición ([com.example.kpkn.ui.theme.MacroColors]);
 * aquí solo viven los de las zonas de ritmo y los avisos, sobrios y sin brillos.
 */
object WizardNutritionPalette {
    val sustainable = Color(0xFF6EDB9A)
    val demanding = Color(0xFFF2C14E)
    val aggressive = Color(0xFFFF9E57)
    val extreme = Color(0xFFFF6B6B)

    /** Pista de un deslizador y de la barra de zonas apagada. */
    val track = Color(0xFF2E2E2E)

    fun zone(zone: PaceZone): Color = when (zone) {
        PaceZone.NONE -> WizardColors.textMuted
        PaceZone.SUSTAINABLE -> sustainable
        PaceZone.DEMANDING -> demanding
        PaceZone.AGGRESSIVE -> aggressive
        PaceZone.EXTREME -> extreme
    }

    fun severity(severity: RiskSeverity): Color = when (severity) {
        RiskSeverity.INFO -> WizardColors.info
        RiskSeverity.WARNING -> demanding
        RiskSeverity.DANGER -> extreme
    }
}
