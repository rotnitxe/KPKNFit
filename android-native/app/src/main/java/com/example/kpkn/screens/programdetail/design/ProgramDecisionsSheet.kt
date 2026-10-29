package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.DecisionLine
import com.example.kpkn.screens.onboarding.design.WizardColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Los filtros de m-decisions, en su orden. */
internal enum class DecisionFilter(val label: String) { ALL("Todas"), PROGRESSION("Progresión"), CHECKIN("Check-in"), BLOCK("Bloque"), MANUAL("Manual") }

/** A qué filtro pertenece una decisión por su origen; null = solo sale en «Todas». */
internal fun decisionFilterOf(source: String): DecisionFilter? = when (source.substringBefore(':')) {
    "NATIVE_PROGRESSION", "AUTHOR_METHOD", "PROTOCOL" -> DecisionFilter.PROGRESSION
    "WEEKLY_CHECKIN" -> DecisionFilter.CHECKIN
    "BLOCK_REVIEW", "CONTINUATION" -> DecisionFilter.BLOCK
    "MANUAL" -> DecisionFilter.MANUAL
    else -> null
}

/** Si [line] sale con el filtro [filter]. */
internal fun decisionMatches(line: DecisionLine, filter: DecisionFilter): Boolean =
    filter == DecisionFilter.ALL || decisionFilterOf(line.source) == filter

/** La fecha de una decisión como en el lienzo: «Hoy», «Lun 27» en la última semana y «1 oct» antes. */
internal fun decisionDateLabel(date: LocalDate, today: LocalDate): String {
    val es = Locale.forLanguageTag("es")
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days == 0L -> "Hoy"
        days in 1..6 -> {
            val weekday = DateTimeFormatter.ofPattern("EEE", es).format(date).removeSuffix(".").replaceFirstChar { it.uppercase(es) }
            "$weekday ${date.dayOfMonth}"
        }
        else -> DateTimeFormatter.ofPattern("d MMM", es).format(date).replace(".", "")
    }
}

/**
 * Decisiones del sistema (lienzo v6, m-decisions): el título y su explicación, los chips de filtro por origen y la línea de tiempo (título
 * y «fecha · detalle»).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramDecisionsSheet(decisions: List<DecisionLine>, onDismiss: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(DecisionFilter.ALL) }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(title = "Decisiones del sistema", sub = "Lo que KPKN propuso o cambió en este programa, y por qué.")
        FlowRow(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DecisionFilter.entries.forEach { option ->
                LienzoChip(text = option.label, selected = filter == option, onClick = { filter = option }, tag = "decision_filter_${option.name}")
            }
        }
        val shown = decisions.filter { decisionMatches(it, filter) }
        if (shown.isEmpty()) {
            Text("Todavía no hay decisiones registradas", style = LienzoCapStyle, color = WizardColors.textFaint)
        } else {
            LienzoTimeline(
                entries = shown.map { line ->
                    val date = decisionDateLabel(Instant.ofEpochMilli(line.atMs).atZone(zone).toLocalDate(), today)
                    line.title to listOf(date, line.detail).filter { it.isNotBlank() }.joinToString(" · ")
                },
            )
        }
    }
}
