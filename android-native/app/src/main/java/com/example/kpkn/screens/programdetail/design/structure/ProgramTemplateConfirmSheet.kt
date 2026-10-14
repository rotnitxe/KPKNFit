package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Confirmación de una plantilla (P3c, sustituye al diálogo con la casilla): el nombre, el resumen y la descripción, y, si el programa ya
 * tiene sesiones, elegir entre reemplazarlo (con copia en Historial) o crear una copia en borrador. La acción principal dice lo que hace.
 */
@Composable
internal fun ProgramTemplateConfirmSheet(
    template: ProgramTemplateOption,
    hasSessions: Boolean,
    onConfirm: (overwrite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramTemplateConfirmContent(
            name = template.name,
            summary = templateSummaryLine(template),
            description = template.description,
            hasSessions = hasSessions,
            onConfirm = onConfirm,
            onCancel = onDismiss,
        )
    }
}

/**
 * El contenido de la confirmación, sin la hoja. [onConfirm] recibe `true` solo si se reemplaza el programa: sin sesiones, nunca hay
 * nada que reemplazar y siempre llega `false`. Por defecto se reemplaza, como antes.
 */
@Composable
internal fun ProgramTemplateConfirmContent(
    name: String,
    summary: String,
    description: String,
    hasSessions: Boolean,
    onConfirm: (overwrite: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var replace by remember(name) { mutableStateOf(true) }
    val overwrite = hasSessions && replace
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = name, style = KpknType.title, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(text = summary, style = KpknType.caption, color = WizardColors.textMuted)
        if (description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = description, style = KpknType.body, color = WizardColors.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Las semanas se rellenan con sesiones sugeridas según el reparto.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (hasSessions) {
            Spacer(Modifier.height(18.dp))
            ProgramSectionLabel(text = "Tu programa actual")
            Spacer(Modifier.height(4.dp))
            KpknRadioRow(
                selected = replace,
                title = "Reemplazar el programa (se guarda una copia en Historial)",
                onClick = { replace = true },
            )
            KpknRadioRow(
                selected = !replace,
                title = "Crear una copia en borrador",
                subtitle = "El original se queda como está.",
                onClick = { replace = false },
            )
        }
        Spacer(Modifier.height(22.dp))
        KpknPrimaryPill(
            text = if (overwrite) "Reemplazar todo" else "Aplicar plantilla",
            onClick = { onConfirm(overwrite) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
            KpknTextAction(text = "Cancelar", onClick = onCancel)
        }
    }
}

/** «Programa · 3 bloques · 12 semanas»: el resumen de la plantilla, con su tipo de programa si lo tiene. */
internal fun templateSummaryLine(template: ProgramTemplateOption): String =
    listOf(
        template.trackLabel?.takeIf { it.isNotBlank() } ?: "Programa",
        numberWithUnit(SpanishPlurals.blocks(template.blockNames.size)),
        numberWithUnit(SpanishPlurals.weeks(template.weeks)),
    ).joinToString(" · ")
