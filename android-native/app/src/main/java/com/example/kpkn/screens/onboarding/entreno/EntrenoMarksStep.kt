package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.LIFT_MARK_RANGE_KG
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.TrainingNumberField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.formatTrainingNumber
import com.example.kpkn.screens.onboarding.marksLifts
import java.util.Locale

/**
 * TRAINING_MAX · «¿Conoces tus marcas?»: la marca (mejor levantamiento de una repetición, o una estimación) de cada
 * levantamiento que pregunta el objetivo. Cada una es opcional; sin marcas el programa sigue siendo válido.
 *
 * Punto de enganche del control animado (`LiftMarksPicker`): sustituir el cuerpo de este archivo. Qué levantamientos se
 * preguntan sale de `draft.marksLifts()` (`MarksContext`); las marcas están en `draft.liftMarks` (kg canónicos) y la
 * unidad visible en `draft.marksUnit` (`kg` o `lb`). Escribe SOLO con `vm.setLiftMark(mark, kg)` (null = «No la sé») y
 * `vm.setMarksUnit(unit)`.
 */
@Composable
internal fun EntrenoMarksStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val unit = draft.marksUnit
    val lifts = draft.marksLifts()
    // El texto que se está escribiendo vive aquí (una marca a medias no es un valor); se rehace al cambiar la unidad.
    val texts = remember(unit) {
        mutableStateMapOf<LiftMark, String>().also { map ->
            draft.liftMarks.forEach { (lift, kg) -> map[lift] = marksDisplayText(kg, unit) }
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("kg", "lb").forEach { option ->
                EntrenoChip(
                    label = option,
                    selected = unit == option,
                    onClick = { vm.setMarksUnit(option) },
                    description = if (option == "kg") "Kilogramos" else "Libras",
                    tag = "entreno-marks-unit-$option",
                )
            }
        }
        lifts.forEach { lift ->
            val text = texts[lift].orEmpty()
            val kg = marksKgFromText(text, unit)
            val invalid = text.isNotBlank() && kg == null
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TrainingNumberField(
                    label = "${lift.label} ($unit)",
                    value = text,
                    onValueChange = { typed ->
                        texts[lift] = typed
                        when {
                            typed.isBlank() -> vm.setLiftMark(lift, null)
                            marksKgFromText(typed, unit) != null -> vm.setLiftMark(lift, marksKgFromText(typed, unit))
                            // Un valor a medias o fuera de rango no se escribe: el campo lo señala.
                            else -> Unit
                        }
                    },
                    isError = invalid,
                    modifier = Modifier.testTag("entreno-mark-${lift.name}"),
                )
                TextButton(
                    onClick = {
                        texts[lift] = ""
                        vm.setLiftMark(lift, null)
                    },
                    modifier = Modifier.testTag("entreno-mark-unknown-${lift.name}"),
                ) {
                    Text("No la sé", color = WizardColors.textMuted, style = WizardTypography.cardSubtitle)
                }
            }
        }
        EntrenoCaption("Tu mejor levantamiento de una repetición, o una estimación.")
    }
}

/** Kilos por libra. */
private const val KG_PER_LB = 0.45359237

/** El texto de una marca en la unidad visible: «100» (kg) o «220,5» (lb), sin ceros de más. */
internal fun marksDisplayText(kg: Double, unit: String): String =
    if (unit == "lb") {
        String.format(Locale.ROOT, "%.1f", kg / KG_PER_LB).removeSuffix(".0")
    } else {
        formatTrainingNumber(kg)
    }

/** Los kilos de un texto escrito en la unidad visible; null si no es un número o queda fuera del rango válido. */
internal fun marksKgFromText(text: String, unit: String): Double? {
    val value = parseLocalizedNumber(text) ?: return null
    val kg = if (unit == "lb") value * KG_PER_LB else value
    return kg.takeIf { it.isFinite() && it in LIFT_MARK_RANGE_KG }
}
