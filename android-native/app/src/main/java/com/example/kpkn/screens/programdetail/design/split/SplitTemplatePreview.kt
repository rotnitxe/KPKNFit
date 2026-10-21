package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.SplitTemplateAlternativePreview
import com.example.kpkn.domain.training.SplitTemplateSessionPreview
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/**
 * «Plantillas por día» (P5): solo con «Con ejercicios de plantilla». Cada día disponible muestra su plantilla y, debajo, las alternativas como
 * chips; el día sin plantilla compatible lo dice en `danger`. Los avisos de carga van arriba, en `danger`. Sin estado: la elección la
 * recibe [onPickTemplate] (día y plantilla).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitTemplatePreview(
    days: List<SplitTemplateSessionPreview>,
    warnings: List<String>,
    exceedsWeeklyBudget: Boolean,
    onPickTemplate: (dayIndex: Int, templateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Plantillas por día")
        if (exceedsWeeklyBudget || warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = warnings.joinToString(separator = " · ")
                    .ifBlank { "La carga semanal supera el presupuesto suave de anillos." },
                style = KpknType.caption,
                color = WizardColors.danger,
            )
        }
        days.forEach { preview ->
            Spacer(Modifier.height(14.dp))
            if (preview.isAvailable) {
                Text(
                    text = "${preview.dayLabel} → ${preview.templateName.orEmpty()} · ${preview.exerciseCount} ejercicios",
                    style = KpknType.caption,
                    color = WizardColors.text,
                )
                val options = buildList {
                    preview.templateId?.let { id ->
                        add(
                            SplitTemplateAlternativePreview(
                                templateId = id,
                                templateName = preview.templateName.orEmpty(),
                                primaryFocusMuscle = preview.primaryFocusMuscle,
                                focusLabel = preview.focusLabel ?: preview.primaryFocusMuscle ?: preview.templateName.orEmpty(),
                            ),
                        )
                    }
                    addAll(preview.alternatives)
                }.distinctBy { it.templateId }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    options.forEach { option ->
                        KpknChip(
                            text = option.focusLabel.ifBlank { option.templateName },
                            selected = preview.templateId == option.templateId,
                            onClick = { onPickTemplate(preview.dayIndex, option.templateId) },
                        )
                    }
                }
            } else {
                Text(
                    text = "${preview.dayLabel} → ${preview.unavailabilityReason ?: "Sin plantilla compatible"}",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
        }
    }
}
