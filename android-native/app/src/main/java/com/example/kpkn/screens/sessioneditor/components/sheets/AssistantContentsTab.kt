package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState

/** Contenido del plan: cabecera de tres cifras y volumen por músculo. Sin notas de descargo. */
@Composable
internal fun AssistantContentsTab(uiState: SessionEditorUiState) {
    val session = uiState.activeVariantSession ?: uiState.session
    val exercises = session?.allExercises().orEmpty()
    val sets = exercises.sumOf { exercise -> exercise.sets.size }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Metric("EJERCICIOS", "${exercises.size}", Modifier.weight(1f))
            Metric("SERIES", "$sets", Modifier.weight(1f))
            Metric("DURACIÓN", "${uiState.estimatedDurationMinutes} min", Modifier.weight(1f))
        }
        Column {
            Text(
                "VOLUMEN DEL PLAN",
                style = KpknType.eyebrow.copy(fontSize = 12.sp),
                color = WizardColors.textFaint,
            )
            Spacer(Modifier.height(10.dp))
            val volume = uiState.assistantReport?.volumenPorMusculo.orEmpty()
            val max = volume.values.maxOfOrNull { (it as? Number)?.toDouble() ?: 0.0 }?.takeIf { it > 0.0 } ?: 1.0
            volume.entries
                .sortedByDescending { (it.value as? Number)?.toDouble() ?: 0.0 }
                .take(8)
                .forEach { entry ->
                    val value = (entry.value as? Number)?.toDouble() ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.key,
                            style = KpknType.body.copy(fontSize = 13.sp),
                            color = WizardColors.text,
                            modifier = Modifier.width(112.dp),
                            maxLines = 1,
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(KpknSurface.inset),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((value / max).toFloat().coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .background(WizardColors.text),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (value % 1.0 == 0.0) "${value.toInt()}" else String.format("%.1f", value),
                            style = KpknType.metricM.copy(fontSize = 14.sp),
                            color = WizardColors.text,
                            modifier = Modifier.width(30.dp),
                        )
                    }
                }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KpknType.eyebrow.copy(fontSize = 12.sp), color = WizardColors.textFaint, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, style = KpknType.metricL, color = WizardColors.text, maxLines = 1)
    }
}
