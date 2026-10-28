package com.example.kpkn.screens.programdetail.design

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.MAX_CHANGES
import com.example.kpkn.screens.programdetail.MAX_PLAN_NOTES
import com.example.kpkn.screens.programdetail.ProgramPlanDisplaySummary
import com.example.kpkn.screens.programdetail.moreLabel
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknType

/**
 * «Plan y procedencia» (P1): la hoja que abre «Ver más» en «Sobre este programa». Enseña lo que tenía la tarjeta del plan:
 * procedencia, qué haces, notas, material, calendario, sesión más larga, volumen real y cambios registrados. Los textos los
 * redacta [ProgramPlanDisplaySummary]; aquí solo se pintan.
 */
@Composable
internal fun ProgramPlanSheet(
    summary: ProgramPlanDisplaySummary,
    onDismiss: () -> Unit,
    /** Abre la hoja de solo lectura «Cómo funciona» de la entrada del catálogo (su id). Sin él, el botón no aparece. */
    onOpenPlanInfo: ((String) -> Unit)? = null,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ProgramSheetHeader(title = summary.planName, eyebrow = "Sobre el programa", subtitle = summary.provenanceLabel)
            Spacer(Modifier.height(4.dp))
            summary.sourceLine?.let { line ->
                Text(text = line, style = KpknType.caption, color = WizardColors.textMuted)
            }
            summary.summary?.let { text ->
                Spacer(Modifier.height(18.dp))
                SheetTitle("Qué haces")
                Text(text = text, style = KpknType.body, color = WizardColors.text)
            }
            if (summary.planNotes.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SheetList(
                    title = "Notas del plan",
                    shown = summary.planNotes.take(MAX_PLAN_NOTES),
                    hidden = summary.planNotes.size - MAX_PLAN_NOTES,
                    hiddenSingular = "nota",
                    hiddenPlural = "notas",
                )
            }
            Spacer(Modifier.height(18.dp))
            SheetLine("Material requerido", summary.requiredMaterial)
            SheetLine("Calendario", summary.daysPerWeek)
            SheetLine("Sesión más larga", summary.maxSessionMinutes?.let { "~$it min (estimación)" } ?: "Sin sesiones materializadas")
            SheetLine("Volumen real", summary.weeklyVolume)
            if (summary.pendingLoadCount > 0) {
                SheetLine("Cargas de referencia", "${summary.pendingLoadCount} pendientes de resolver")
            }
            if (summary.changes.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SheetList(
                    title = "Cambios registrados",
                    shown = summary.changes.take(MAX_CHANGES),
                    hidden = summary.changes.size - MAX_CHANGES,
                    hiddenSingular = "cambio",
                    hiddenPlural = "cambios",
                )
            }
            // «Ver cómo funciona» al final, solo si el plan viene del catálogo.
            summary.catalogEntry?.let { entry ->
                if (onOpenPlanInfo != null) {
                    Spacer(Modifier.height(18.dp))
                    KpknCompactPill(
                        text = "Ver cómo funciona",
                        onClick = { onOpenPlanInfo(entry.id) },
                        filled = false,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text = text, style = KpknType.bodyStrong, color = WizardColors.text)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetList(
    title: String,
    shown: List<String>,
    hidden: Int,
    hiddenSingular: String,
    hiddenPlural: String,
) {
    SheetTitle(title)
    shown.forEach { item ->
        Text(text = item, style = KpknType.body, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
    }
    if (hidden > 0) {
        Text(
            text = moreLabel(hidden, hiddenSingular, hiddenPlural),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}

@Composable
private fun SheetLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = KpknType.body,
            color = WizardColors.text,
        )
    }
}
