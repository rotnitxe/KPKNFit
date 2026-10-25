package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.planning.PlannedLoadProjection
import com.example.kpkn.domain.training.planning.PlannedMuscleLoad
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

private fun loadLine(load: PlannedMuscleLoad): String {
    fun number(value: Double) = String.format(java.util.Locale.forLanguageTag("es-CL"),"%.1f",value)
    return "${load.muscleName}: ${number(load.plannedSets)} series previstas · ${number(load.highEffortTargetSets)} con objetivo exigente"
}

@Composable
internal fun ProgramPlannedLoadModule(projection: PlannedLoadProjection,onOpen: () -> Unit) {
    if (projection.loads.isEmpty()) return
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        Text(if (projection.nextCycleTemplate) "Siguiente ciclo · plantilla" else "Próxima semana",style = KpknType.bodyStrong,color = WizardColors.text)
        Text(projection.weekName,style = KpknType.body,color = WizardColors.text)
        projection.loads.take(3).forEach { Text(loadLine(it),style = KpknType.body,color = WizardColors.text) }
        Text("Series según el plan, incluidas las sesiones opcionales.",style = KpknType.caption,color = WizardColors.textMuted)
        Spacer(Modifier.height(8.dp))
        KpknCompactPill("Ver semana prevista",onOpen,fullWidth = true)
    }
}

@Composable
internal fun ProgramPlannedLoadSheet(projection: PlannedLoadProjection,onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = onDismiss,stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Semana prevista",style = KpknType.bodyStrong,color = WizardColors.text)
            Text(projection.weekName,style = KpknType.body,color = WizardColors.text)
            projection.cycleNumber?.let { Text("Ciclo $it",style = KpknType.caption,color = WizardColors.textMuted) }
            projection.loads.forEach { Text(loadLine(it),style = KpknType.body,color = WizardColors.text) }
            projection.notes.forEach { Text(it,style = KpknType.body,color = WizardColors.textMuted) }
            Text("Una misma serie puede aportar a varios músculos.",style = KpknType.caption,color = WizardColors.textMuted)
            KpknCompactPill("Cerrar",onDismiss,filled = false,fullWidth = true)
        }
    }
}
