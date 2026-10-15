package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.kpkn.screens.workout.toTrimmedNumberString
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknDangerPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Panel de la técnica guiada (LR1c2): un módulo teñido con el acento de la sesión (es lo activo). Los campos son `KpknTextField`, la
 * acción de saltar la técnica es texto, y la confirmación, un `KpknAlertDialog` destructivo porque descarta las sub-series.
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
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (phase) {
                is GuidedTechniquePhase.DropSet -> {
                    Text(
                        "DROP-SET ${phase.index + 1}/${phase.total}",
                        style = KpknType.eyebrow,
                        color = accentColor,
                    )
                    Text(
                        "Sin descanso. Baja el peso y completa ~${RestPausePlanDefaults.Reps} reps.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
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
                    Text(
                        "Sugerido: ${phase.suggestedWeight.toTrimmedNumberString()} kg para ~${RestPausePlanDefaults.Reps} reps",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }

                is GuidedTechniquePhase.RestPauseCountdown -> {
                    Text(
                        "REST-PAUSE ${phase.index + 1}/${phase.total}",
                        style = KpknType.eyebrow,
                        color = accentColor,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            KpknLineIcons.Clock,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${phase.secondsLeft} s",
                            style = KpknType.metricL,
                            color = WizardColors.text,
                        )
                    }
                    KpknProgressBar(
                        progress = phase.secondsLeft.toFloat() / RestPausePlanDefaults.PauseSeconds.toFloat(),
                        color = accentColor,
                        trackColor = Color.White.copy(alpha = 0.08f),
                        height = 6.dp,
                    )
                    Text(
                        "Pausa fija de ${RestPausePlanDefaults.PauseSeconds} s. Luego harás ${RestPausePlanDefaults.Reps} reps.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }

                is GuidedTechniquePhase.RestPauseReps -> {
                    Text(
                        "REST-PAUSE ${phase.index + 1}/${phase.total}",
                        style = KpknType.eyebrow,
                        color = accentColor,
                    )
                    Text(
                        "Confirma las ${RestPausePlanDefaults.Reps} reps de esta mini-serie.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    KpknTextField(
                        value = restPauseRepsText,
                        onValueChange = onRestPauseRepsChange,
                        label = "Repeticiones",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            KpknTextAction(
                text = "Saltar técnica y registrar solo la serie",
                onClick = { confirmSkipTechnique = true },
            )
        }
    }
    if (confirmSkipTechnique) {
        // El cuerpo de KpknAlertDialog reparte los dos botones en una sola fila: con «Seguir con la técnica», el de confirmar se aplastaba
        // hasta un círculo. Aquí la confirmación ocupa todo el ancho y «Seguir» va debajo, con los mismos textos y las mismas acciones.
        KpknAlertDialog(
            onDismissRequest = { confirmSkipTechnique = false },
            confirmButton = {},
            title = {
                Text(
                    "¿Registrar solo la serie principal?",
                    style = KpknType.dialogTitle,
                    color = WizardColors.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        "Las sub-series de drop o rest-pause quedarán descartadas.",
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                    )
                    KpknDangerPill(
                        text = "Sí, solo la serie",
                        onClick = {
                            confirmSkipTechnique = false
                            onSkipTechnique()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        KpknAlertDismissButton(
                            text = "Seguir con la técnica",
                            onClick = { confirmSkipTechnique = false },
                        )
                    }
                }
            },
        )
    }
}
