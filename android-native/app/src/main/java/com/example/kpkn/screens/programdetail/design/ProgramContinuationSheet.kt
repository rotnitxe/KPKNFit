package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.block.ContinuationChoice
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

@Composable
internal fun ProgramContinuationSheet(preview: ProgramRepository.ContinuationPreview,busy: Boolean,onConfirm: () -> Unit,onDismiss: () -> Unit) {
    val next = preview.nextProgram ?: run {
        ProgramUnavailableSheet("Programa terminado", "No hay un programa nuevo que preparar con esta opción.", onDismiss)
        return
    }
    val weeks = ProgramHierarchyIndex(next).orderedWeeks()
    val sessionCount = weeks.sumOf { it.week.sessions.size }
    val maintenance = preview.choice == ContinuationChoice.MAINTENANCE_WEEK
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = if (maintenance) "Preparar mantenimiento" else "Repetir el programa", eyebrow = "Programa terminado", subtitle = next.name)
            Text("${weeks.size} ${if (weeks.size == 1) "semana" else "semanas"} · $sessionCount ${if (sessionCount == 1) "sesión prevista" else "sesiones previstas"}",style = KpknType.body,color = WizardColors.text)
            Text("El programa terminado conserva sus registros y decisiones. Al confirmar se crea y activa una copia nueva.",style = KpknType.body,color = WizardColors.text)
            Text(if (maintenance) "Mantenimiento de fuerza: aproximadamente la mitad de las series, RIR 4 y carga por elegir al entrenar. El cardio conserva su prescripción; revísalo antes de continuar." else
                if (preview.original.sourceRecipe != null) "Repite la receta fuente con tus marcas confirmadas. Las modificaciones manuales y fechas anteriores quedan en el programa terminado." else
                    "Repite la estructura y los ejercicios con tus referencias guardadas. Las fechas anteriores quedan en el programa terminado.",style = KpknType.body,color = WizardColors.text)
            weeks.take(1).flatMap { it.week.sessions }.forEach { session ->
                val sets = session.allExercises().flatMap { it.sets }.filterNot { it.isIneffective }
                Text("${session.name}: ${sets.size} series · RIR ${sets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "según la receta" }}",
                    style = KpknType.body,color = WizardColors.text)
            }
            if (!busy) {
                KpknPrimaryPill(text = "Crear y activar", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                KpknSecondaryPill(text = "Volver", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
        }
    }
}
