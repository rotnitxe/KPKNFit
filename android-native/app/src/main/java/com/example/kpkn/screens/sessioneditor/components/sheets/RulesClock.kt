package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.ui.graphics.Color
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.frame.FrameRingTone
import com.example.kpkn.screens.sessioneditor.frame.frameTimeRingTone

/** Qué duración edita el selector de tiempo: el global de la sesión, un grupo o un ejercicio. */
internal sealed class DurationPickerTarget {
    abstract val title: String
    abstract val currentMinutes: Int?

    data class Global(
        override val currentMinutes: Int?,
    ) : DurationPickerTarget() {
        override val title: String = "Presupuesto global"
    }

    data class Part(
        val partId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()

    data class Exercise(
        val exerciseId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()
}

/** Estado del reloj de sesión frente a su límite. Sin límite, el reloj es neutro. */
internal enum class RulesClockStatus { NEUTRAL, OK, ENERGY, OVER }

/**
 * Estado del reloj: `ok` por debajo del 85 % del límite, Energía hasta el 108 % y `danger` por encima. Lo decide la única función de
 * los umbrales, [frameTimeRingTone] (ER1f: antes había una copia en coma flotante aquí). Función pura.
 */
internal fun rulesClockStatusOf(estimatedMinutes: Int, limitMinutes: Int?): RulesClockStatus = when (frameTimeRingTone(estimatedMinutes, limitMinutes)) {
    FrameRingTone.NEUTRAL -> RulesClockStatus.NEUTRAL
    FrameRingTone.OK -> RulesClockStatus.OK
    FrameRingTone.ENERGIA -> RulesClockStatus.ENERGY
    FrameRingTone.DANGER -> RulesClockStatus.OVER
}

/** Color del reloj por estado: `ok` (#43D18C), Energía (#F7CF73) o `danger`; neutro, en tinta atenuada. */
internal fun rulesClockColor(status: RulesClockStatus): Color = when (status) {
    RulesClockStatus.NEUTRAL -> WizardColors.textMuted
    RulesClockStatus.OK -> WizardColors.done
    RulesClockStatus.ENERGY -> com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette.energia
    RulesClockStatus.OVER -> WizardColors.danger
}


/** Minutos como «45 min», «1 h 30 min» o «Sin límite» (sin límite o cero). */
internal fun formatDurationMinutes(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return "Sin límite"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "%d h %02d min".format(h, m) else "$m min"
}

