package com.example.kpkn.screens.programdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.domain.training.ExerciseLoadReferenceStore
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.workout.TrainingMaxEntryBody
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Exact per-exercise references, including lifts outside the four legacy profile fields. */
@Composable
internal fun ExerciseTrainingMaxPanel(program: Program, onConfirm: (String, Double) -> Unit, modifier: Modifier = Modifier) {
    val migrated = remember(program) { ExerciseTrainingMax.migrate(program) }
    val exercises = remember(migrated) { ExerciseTrainingMax.exercises(migrated).filter { exercise ->
        exercise.referenceTrainingMax != null || exercise.slotRole in listOf(
            com.example.kpkn.data.protocols.SlotRole.T1_MAIN, com.example.kpkn.data.protocols.SlotRole.T2_SUPPLEMENTAL) || exercise.sets.any { it.loadBasis == com.example.kpkn.data.protocols.LoadBasis.PERCENT_TM }
    }.distinctBy { listOf(it.catalogConfigurationId ?: it.exerciseDbId, it.loadQuantityConvention) } }
    var selected by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth().testTag("exercise-tm-panel")) {
        exercises.forEach { exercise ->
            val query = ExerciseTrainingMax.request(exercise, PlanLoadReferenceKind.EXERCISE_TM)
            val history = query?.let { ExerciseLoadReferenceStore.history(migrated, exercise.id, it) }.orEmpty()
            val current = query?.let { ExerciseLoadReferenceStore.resolve(history, it) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, style = KpknType.bodyStrong, color = WizardColors.text)
                Text(current?.capturedLoadKg?.let { "TM ${formatEditableNumber(it).replace('.', ',')} kg" } ?: "Falta el TM",
                    style = KpknType.body, color = WizardColors.text)
                current?.metadata?.reason?.let { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
                KpknTextAction(text = "Editar TM", onClick = { selected = exercise.id },
                    modifier = Modifier.semantics { contentDescription = "Editar TM de ${exercise.name}" })
                if (history.size > 1) history.asReversed().take(5).forEach { reference ->
                    reference.capturedLoadKg?.let { kg -> Text(
                        "${formatEditableNumber(kg).replace('.', ',')} kg · ${reference.metadata?.reason ?: "Sin procedencia registrada"}",
                        style = KpknType.caption, color = WizardColors.textMuted) }
                }
            }
        }
    }
    exercises.firstOrNull { it.id == selected }?.let { exercise ->
        val current = ExerciseTrainingMax.capturedKg(migrated, exercise, PlanLoadReferenceKind.EXERCISE_TM)
        KpknSheet(onDismissRequest = { selected = null }) {
            TrainingMaxEntryBody(exercise.name, current, null,
                onConfirm = { onConfirm(exercise.id, it); selected = null }, onDismiss = { selected = null })
        }
    }
}
