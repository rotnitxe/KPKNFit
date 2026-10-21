package com.example.kpkn.screens.nutrition.design.calibration

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardState
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardStep
import com.example.kpkn.screens.nutrition.weighingConventionLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Las convenciones de pesaje: valor que guarda el perfil → nombre que se muestra. */
internal val WEIGHING_CONVENTIONS = listOf("RAW" to "Crudo", "COOKED" to "Cocido", "DEPENDS" to "Depende")

/**
 * El asistente «Calibrar tus hábitos» (N5): pasos numerados en un módulo. La convención de pesaje es una lista de [KpknRadioRow]; las
 * medidas van en un campo de texto con su ejemplo; «Confirmar y continuar» es la acción principal y «Omitir por ahora» o «Editar y
 * reanudar» van como acción de texto al pie. Sin estado: la respuesta la sube la pantalla.
 */
@Composable
fun CalibrationWizardModule(
    wizard: NutritionCalibrationWizardState,
    answer: String,
    onAnswerChange: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onSkip: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = wizard.step
    val totalSteps = NutritionCalibrationWizardStep.entries.size
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Calibrar tus hábitos")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Puedes omitirlo y reanudarlo después. Solo se guardan respuestas confirmadas; una estimación no entrena el perfil.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Paso ${step.index + 1} de $totalSteps", style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.height(4.dp))
        Text(text = step.title, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(12.dp))
        when (step) {
            NutritionCalibrationWizardStep.WEIGHING_CONVENTION -> {
                Text(
                    text = "¿Cómo pesas normalmente los alimentos?",
                    style = KpknType.body,
                    color = WizardColors.text,
                )
                Spacer(Modifier.height(8.dp))
                WEIGHING_CONVENTIONS.forEach { (value, label) ->
                    KpknRadioRow(
                        selected = wizard.profile.weighingConvention == value,
                        title = label,
                        onClick = { onAnswer(value) },
                    )
                }
            }
            NutritionCalibrationWizardStep.REVIEW -> {
                Text(
                    text = "Revisa y guarda el perfil cuando estés conforme.",
                    style = KpknType.body,
                    color = WizardColors.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pesaje: ${weighingConventionLabel(wizard.profile.weighingConvention)}",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                Text(
                    text = "Porciones maduras: ${wizard.profile.maturePortionsGrams.size}",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                KpknPrimaryPill(
                    text = "Guardar perfil",
                    onClick = { onAnswer("listo") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                Text(
                    text = exampleOf(step),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                KpknTextField(
                    value = answer,
                    onValueChange = onAnswerChange,
                    label = "Respuesta confirmada",
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                KpknPrimaryPill(
                    text = "Confirmar y continuar",
                    onClick = {
                        onAnswer(answer.ifBlank { "confirmado" })
                        onAnswerChange("")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = answer.isNotBlank(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!wizard.isComplete) {
            KpknTextAction(text = "Omitir por ahora", onClick = onSkip)
        } else {
            KpknTextAction(text = "Editar y reanudar", onClick = onResume)
        }
    }
}

/** El ejemplo de escritura de cada paso de medidas: clave=valor, separado por comas. */
private fun exampleOf(step: NutritionCalibrationWizardStep): String = when (step) {
    NutritionCalibrationWizardStep.UTENSILS -> "Ejemplo: taza=240, vaso=250, bol=400, plato=300"
    NutritionCalibrationWizardStep.STAPLE_PORTIONS -> "Ejemplo: arroz=150, pasta=180, papas=200, legumbres=180"
    NutritionCalibrationWizardStep.PROTEIN_PORTIONS -> "Ejemplo: pollo=180, vacuno=160, pescado=170"
    NutritionCalibrationWizardStep.PREPARATION_OIL -> "Ejemplo: plancha=3, horno=2, fritura=10, air_fryer=0"
    else -> ""
}
