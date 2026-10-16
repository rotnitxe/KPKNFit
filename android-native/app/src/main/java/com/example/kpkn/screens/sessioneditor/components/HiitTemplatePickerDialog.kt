package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitTemplate
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.CardioIntervalChart
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Plantillas HIIT en una hoja KPKN: cada plantilla en su módulo, con su nombre, su dosis, su mini gráfica y «Usar». Tocar «Usar» la
 * aplica con [onSelect]; «Cerrar» deja la hoja como estaba.
 */
@Composable
internal fun HiitTemplatePickerDialog(
    cardioType: CardioType,
    accentColor: Color,
    title: String = "Plantillas HIIT",
    onSelect: (HiitTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates = CardioHiitTemplates.forType(cardioType)
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = KpknType.dialogTitle, color = WizardColors.text)
            Text(
                "Elige un circuito listo para usar. Todo queda editable después.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            templates.forEach { template ->
                HiitTemplateModule(
                    template = template,
                    cardioType = cardioType,
                    accentColor = accentColor,
                    onSelect = { onSelect(template) },
                )
            }
            KpknTextAction(
                text = "Cerrar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Una plantilla en su módulo: nombre, nivel, descripción, mini gráfica y su dosis con «Usar». */
@Composable
private fun HiitTemplateModule(
    template: HiitTemplate,
    cardioType: CardioType,
    accentColor: Color,
    onSelect: () -> Unit,
) {
    val details = remember(template, cardioType) { template.toDetails(cardioType, CardioIntensity.MEDIA) }
    // Una plantilla de bloques irregulares no tiene forma uniforme: se dice cuántos bloques son.
    val irregular = remember(template) { CardioRepeatGrammar.inferUniform(template.blocks) == null }
    val dose = remember(details, irregular) {
        if (irregular) "${details.intervalBlocks.size} bloques" else cardioDoseLine(details)
    }
    KpknModule(contentPadding = PaddingValues(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                template.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                template.level.uppercase(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        }
        Text(
            template.description,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        CardioIntervalChart(
            details = details,
            accentColor = accentColor,
            compact = true,
            showLabels = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "$dose · ${CardioPrescriptionFormatter.formatDuration(details.effectiveDurationSeconds())}",
                style = KpknType.caption,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KpknCompactPill(text = "Usar", onClick = onSelect, filled = true)
        }
    }
}
