package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPercentRule
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknStepperMetric
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Contenido de «Opciones de la serie» (KPKN LS7b): el rango de movimiento con la regla KPKN, las repeticiones con ayuda y las
 * parciales con su ± y la entrada a «Drop-sets, descanso y notas». Sin estado: los valores vienen de la tarjeta de la serie, que
 * es quien los guarda. [onAssistedStep] y [onPartialStep] reciben el paso (−1 o 1) y la tarjeta aplica sus límites.
 */
@Composable
internal fun WorkoutSetOptionsBody(
    showRom: Boolean,
    romPercent: Int,
    onRomChange: (Int) -> Unit,
    assistedReps: Int,
    maxAssistedReps: Int,
    onAssistedStep: (Int) -> Unit,
    partialReps: Int,
    onPartialStep: (Int) -> Unit,
    onOpenTechnique: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showRom) {
            KpknModule(modifier = Modifier.fillMaxWidth()) {
                KpknModuleHeader(
                    label = "Rango de movimiento",
                    action = { KpknMetric(value = "$romPercent", unit = "%", style = KpknType.metricM) },
                )
                Spacer(Modifier.height(14.dp))
                KpknPercentRule(
                    value = romPercent,
                    onValueChange = onRomChange,
                    color = accentColor,
                    contentDescription = "Rango de movimiento",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Repeticiones")
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                KpknStepperMetric(
                    label = "Con ayuda".uppercase(Locale("es")),
                    value = "$assistedReps",
                    onMinus = { onAssistedStep(-1) },
                    onPlus = { onAssistedStep(1) },
                    contentDescription = "$assistedReps repeticiones con ayuda",
                    keyboardType = KeyboardType.Number,
                    minusEnabled = assistedReps > 0,
                    plusEnabled = assistedReps < maxAssistedReps,
                    modifier = Modifier.weight(1f),
                )
                KpknStepperMetric(
                    label = "Parciales".uppercase(Locale("es")),
                    value = "$partialReps",
                    onMinus = { onPartialStep(-1) },
                    onPlus = { onPartialStep(1) },
                    contentDescription = "$partialReps repeticiones parciales",
                    keyboardType = KeyboardType.Number,
                    minusEnabled = partialReps > 0,
                    plusEnabled = partialReps < MaxPartialReps,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        KpknSecondaryPill(
            text = "Drop-sets, descanso y notas",
            onClick = onOpenTechnique,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Tope de parciales que la tarjeta acepta (como antes de LS7b). */
internal const val MaxPartialReps = 20
