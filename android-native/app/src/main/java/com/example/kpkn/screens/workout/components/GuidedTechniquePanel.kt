package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Panel de la técnica guiada (drop-set o rest-pause, paso a paso). LS7b: módulo KPKN con cifras de [KpknTextField] y la salida
 * «Saltar técnica» confirmada con [KpknAlertDialog] (mismos textos que antes).
 */
@Composable
internal fun GuidedTechniquePanel(
    phase: GuidedTechniquePhase,
    accentColor: Color,
    dropWeightText: String,
    dropRepsText: String,
    restPauseRepsText: String,
    onDropWeightChange: (String) -> Unit,
    onDropRepsChange: (String) -> Unit,
    onRestPauseRepsChange: (String) -> Unit,
    onSkipTechnique: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmSkipTechnique by remember { mutableStateOf(false) }
    KpknModule(
        modifier = modifier.fillMaxWidth(),
        accent = accentColor,
    ) {
        when (phase) {
            is GuidedTechniquePhase.DropSet -> {
                KpknModuleHeader(label = "Drop-set ${phase.index + 1} de ${phase.total}")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Sin descanso. Baja el peso y completa ~${RestPausePlanDefaults.Reps} reps.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KpknTextField(
                        value = dropWeightText,
                        onValueChange = onDropWeightChange,
                        label = "Peso sugerido",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    KpknTextField(
                        value = dropRepsText,
                        onValueChange = onDropRepsChange,
                        label = "Repeticiones",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sugerido: ${phase.suggestedWeight} kg para ~${RestPausePlanDefaults.Reps} reps",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }

            is GuidedTechniquePhase.RestPauseCountdown -> {
                KpknModuleHeader(label = "Rest-pause ${phase.index + 1} de ${phase.total}")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = KpknLineIcons.Clock,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${phase.secondsLeft} s",
                        style = KpknType.metricM,
                        color = WizardColors.text,
                    )
                }
                Spacer(Modifier.height(10.dp))
                KpknProgressBar(
                    progress = phase.secondsLeft.toFloat() / RestPausePlanDefaults.PauseSeconds.toFloat(),
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Pausa fija de ${RestPausePlanDefaults.PauseSeconds} s. Luego harás ${RestPausePlanDefaults.Reps} reps.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
            }

            is GuidedTechniquePhase.RestPauseReps -> {
                KpknModuleHeader(label = "Rest-pause ${phase.index + 1} de ${phase.total}")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Confirma las ${RestPausePlanDefaults.Reps} reps de esta mini-serie.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                KpknTextField(
                    value = restPauseRepsText,
                    onValueChange = onRestPauseRepsChange,
                    label = "Repeticiones",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        KpknTextAction(
            text = "Saltar técnica y registrar solo la serie",
            onClick = { confirmSkipTechnique = true },
        )
    }
    if (confirmSkipTechnique) {
        KpknAlertDialog(
            onDismissRequest = { confirmSkipTechnique = false },
            title = "¿Registrar solo la serie principal?",
            text = "Las sub-series de drop o rest-pause quedarán descartadas.",
            confirmLabel = "Sí, solo la serie",
            onConfirm = {
                confirmSkipTechnique = false
                onSkipTechnique()
            },
            dismissLabel = "Seguir con la técnica",
            onDismiss = { confirmSkipTechnique = false },
        )
    }
}
