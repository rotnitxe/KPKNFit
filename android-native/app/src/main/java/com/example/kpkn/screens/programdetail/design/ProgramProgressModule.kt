package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.ProgramProgressReport
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Datos de progreso mínimos; el detalle de volumen y las marcas conservan sus propias vistas. */
@Composable
internal fun ProgramProgressModule(report: ProgramProgressReport?, onDecisions: () -> Unit, modifier: Modifier = Modifier) {
    if (report == null) return
    val position = report.position
    val week = position.weekIndex?.minus(1)?.let { report.weeks.getOrNull(it) }
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Progreso del programa")
        Spacer(Modifier.height(8.dp))
        Text(position.scopeLabel, style = KpknType.caption, color = WizardColors.textMuted)
        val location = listOfNotNull(
            position.cycle?.let { "Ciclo $it" }, position.blockName,
        ).joinToString(" · ")
        if (location.isNotEmpty()) Text(location, style = KpknType.bodyStrong, color = WizardColors.text)
        position.weekIndex?.let {
            Text("Semana $it de ${position.weekCount ?: report.weeks.size}", style = KpknType.caption, color = WizardColors.textMuted)
        }
        position.nextMilestone?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
        Spacer(Modifier.height(12.dp))
        if (LocalDensity.current.fontScale >= 1.2f) {
            ProgramProgressMetricRow("Cumplimiento", "${report.adherencePercent}", "%")
            ProgramProgressMetricRow("Sesiones", week?.let { "${it.completedSessions}/${it.plannedSessions}" } ?: "—")
            ProgramProgressMetricRow("Decisiones", "${report.decisions.size}", onClick = onDecisions)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Cumplimiento", style = KpknType.caption, color = WizardColors.textMuted)
                KpknMetric("${report.adherencePercent}", "%", style = KpknType.metricM)
            }
            Column(Modifier.weight(1f)) {
                Text("Sesiones", style = KpknType.caption, color = WizardColors.textMuted)
                KpknMetric(week?.let { "${it.completedSessions}/${it.plannedSessions}" } ?: "—", style = KpknType.metricM)
            }
            Column(Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onDecisions)) {
                Text("Decisiones", style = KpknType.caption, color = WizardColors.textMuted)
                KpknMetric("${report.decisions.size}", style = KpknType.metricM)
            }
        }
        report.lifts.forEach { lift ->
            Spacer(Modifier.height(8.dp))
            Text("${lift.exerciseName} · ${progressKg(lift.firstE1rmKg)} → ${progressKg(lift.lastE1rmKg)} kg (e1RM)",
                style = KpknType.caption, color = WizardColors.text)
        }
    }
}

@Composable
private fun ProgramProgressMetricRow(label: String, value: String, unit: String? = null, onClick: (() -> Unit)? = null) {
    val action = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(action),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp), style = KpknType.caption, color = WizardColors.textMuted)
        KpknMetric(value, unit, style = KpknType.metricM)
    }
}

private fun progressKg(value: Double): String =
    (if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value)).replace('.', ',')
