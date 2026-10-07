package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.EntrenoStepValues
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/** Atajos de tiempo por sesión (minutos). */
internal val SESSION_MINUTES_SHORTCUTS = listOf(30, 45, 60, 90, 120)

/**
 * SESSION_TIME · «¿Cuánto tiempo tienes por sesión?»: de 20 a 180 minutos, de 5 en 5.
 *
 * Punto de enganche del control animado (`SessionClockDial`): sustituir el cuerpo de este archivo. Lee
 * `state.draft.minutesPerSession` y escribe SOLO con `vm.setSessionMinutes(minutes)`, que redondea al múltiplo de 5
 * más cercano dentro del rango. No hay campo de texto: el valor llega siempre del control.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntrenoSessionTimeStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val minutes = state.draft.minutesPerSession
    // Mientras se arrastra, el valor vive aquí; al soltar se escribe en el borrador (una escritura, no una por marca).
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragging.isNaN()) minutes else EntrenoStepValues.roundSessionMinutes(dragging.toDouble())
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = shown?.let(::formatSessionMinutes) ?: "Elige un tiempo",
            style = WizardTypography.controlValue,
            color = if (shown != null) WizardColors.text else WizardColors.textFaint,
            modifier = Modifier.testTag("entreno-session-minutes"),
        )
        shown?.let(::formatSessionHoursMinutes)?.let { EntrenoCaption(it) }
        Slider(
            value = (dragging.takeUnless { it.isNaN() } ?: (minutes ?: DEFAULT_DIAL_MINUTES).toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (!dragging.isNaN()) vm.setSessionMinutes(dragging.toInt())
                dragging = Float.NaN
            },
            valueRange = EntrenoStepValues.SESSION_MINUTES_MIN.toFloat()..EntrenoStepValues.SESSION_MINUTES_MAX.toFloat(),
            steps = (EntrenoStepValues.SESSION_MINUTES_MAX - EntrenoStepValues.SESSION_MINUTES_MIN) /
                EntrenoStepValues.SESSION_MINUTES_STEP - 1,
            colors = SliderDefaults.colors(
                thumbColor = WizardColors.text,
                activeTrackColor = WizardColors.text,
                inactiveTrackColor = WizardColors.progressTrack,
            ),
            modifier = Modifier
                .testTag("entreno-session-slider")
                .semantics { contentDescription = "Minutos por sesión" },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SESSION_MINUTES_SHORTCUTS.forEach { shortcut ->
                EntrenoChip(
                    label = "$shortcut",
                    selected = minutes == shortcut,
                    onClick = { vm.setSessionMinutes(shortcut) },
                    description = formatSessionMinutes(shortcut),
                    tag = "entreno-session-shortcut-$shortcut",
                )
            }
        }
        sessionTimeHint(shown)?.let { EntrenoCaption(it) }
    }
}

/** Dónde arranca el control mientras no hay valor: no es una respuesta hasta que la persona lo mueve o toca un atajo. */
private const val DEFAULT_DIAL_MINUTES = 60

/** «75 min». */
internal fun formatSessionMinutes(minutes: Int): String = "$minutes min"

/** «1 h 15 min»; null por debajo de la hora (la primera lectura ya basta). */
internal fun formatSessionHoursMinutes(minutes: Int): String? {
    if (minutes < 60) return null
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}

/** La pista que acompaña al tiempo: con poco se va a lo esencial, con mucho se suman aproximaciones y descansos. */
internal fun sessionTimeHint(minutes: Int?): String? = when {
    minutes == null -> null
    minutes <= 30 -> "Con poco tiempo vamos a lo esencial."
    minutes >= 90 -> "Con más tiempo sumamos aproximaciones, movilidad y descansos más largos."
    else -> null
}
