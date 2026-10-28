package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.DecisionLine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ProgramDecisionsSheet(decisions: List<DecisionLine>, onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = onDismiss, stableHeightFraction = 0.85f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = "Decisiones del sistema", subtitle = "Lo que KPKN propuso o cambió en este programa, y por qué.")
            if (decisions.isEmpty()) Text("Todavía no hay decisiones registradas", style = KpknType.body, color = WizardColors.textMuted)
            decisions.forEach { line ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val date = DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("es"))
                        .format(Instant.ofEpochMilli(line.atMs).atZone(ZoneId.systemDefault()))
                    Text("$date · ${decisionSourceLabel(line.source)}", style = KpknType.caption, color = WizardColors.textMuted)
                    Text(line.title, style = KpknType.bodyStrong, color = WizardColors.text)
                    if (line.detail.isNotBlank()) Text(line.detail, style = KpknType.body, color = WizardColors.text)
                }
            }
        }
    }
}

private fun decisionSourceLabel(source: String): String = when (source.substringBefore(':')) {
    "PROTOCOL" -> "Protocolo"
    "WEEKLY_CHECKIN" -> "Check-in semanal"
    "BLOCK_REVIEW" -> "Revisión de bloque"
    "CONTINUATION" -> "Continuación"
    "NATIVE_PROGRESSION" -> "Progresión"
    "AUTHOR_METHOD" -> "Método del programa"
    "MISSED_SESSION" -> "Sesiones pendientes"
    "COMPETITION" -> "Competición"
    "MANUAL" -> "Cambio manual"
    else -> "Ajuste del programa"
}
