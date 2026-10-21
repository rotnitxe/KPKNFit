package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.importer.ImportIssue
import com.example.kpkn.domain.training.importer.ImportIssueKind
import com.example.kpkn.domain.training.importer.ImportedProgramDraft
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/**
 * La revisión final del programa importado: encima de «Guardar programa» en la app y al final del paso en el alta. Con
 * problemas, cada uno con su acción de texto; sin ninguno, una línea de «listo». [issues] lo calcula quien la usa con
 * `ImportDraftValidation.check`, para que el botón de guardar y esta revisión digan lo mismo.
 */
@Composable
internal fun ImportReview(
    imported: ImportedProgramDraft,
    issues: List<ImportIssue>,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onShowWarnings: () -> Unit,
    onRestart: () -> Unit,
    /** Sin ningún día de entrenamiento elegido (solo en la app): la revisión lo pide y el programa no se guarda. */
    weekdaysMissing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("import-review"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (issues.isEmpty() && !weekdaysMissing) {
            Text(importReadyLine(imported), style = WizardTypography.controlLabel, color = SymbolPalette.ok)
        } else {
            Text("Falta revisar", style = WizardTypography.controlLabel, color = WizardColors.text)
            issues.forEach { issue ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.testTag("import-review-issue")) {
                    Text(importIssueLine(issue, imported), style = WizardTypography.bodySmall, color = WizardColors.text)
                    ReviewAction(issue = issue, onOpenExercise = onOpenExercise, onEdit = onEdit, onShowWarnings = onShowWarnings, onRestart = onRestart)
                }
            }
            if (weekdaysMissing) {
                Text(
                    IMPORT_WEEKDAYS_MISSING,
                    style = WizardTypography.bodySmall,
                    color = WizardColors.text,
                    modifier = Modifier.testTag("import-review-weekdays"),
                )
            }
        }
    }
}

/** La acción de texto de un problema: lo que lo arregla con un toque. */
@Composable
private fun ReviewAction(
    issue: ImportIssue,
    onOpenExercise: (String) -> Unit,
    onEdit: (ImportEditOp) -> Unit,
    onShowWarnings: () -> Unit,
    onRestart: () -> Unit,
) {
    when (issue.kind) {
        ImportIssueKind.UNDECIDED_EXERCISE -> issue.exerciseId?.let { id ->
            ImportTextAction(label = "Elegir", onClick = { onOpenExercise(id) }, modifier = Modifier.testTag("import-review-choose"))
        }
        ImportIssueKind.EMPTY_DAY -> issue.dayId?.let { id ->
            ImportTextAction(
                label = "Quitar el día",
                onClick = { onEdit(ImportEditOp.RemoveDay(id)) },
                modifier = Modifier.testTag("import-review-remove-day"),
            )
        }
        ImportIssueKind.EMPTY_WEEK -> issue.weekIndex?.let { index ->
            ImportTextAction(
                label = "Quitar la semana",
                onClick = { onEdit(ImportEditOp.RemoveWeek(index)) },
                modifier = Modifier.testTag("import-review-remove-week"),
            )
        }
        ImportIssueKind.PENDING_WARNINGS -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImportTextAction(label = "Ver avisos", onClick = onShowWarnings, modifier = Modifier.testTag("import-review-warnings"))
            ImportTextAction(
                label = "Ignorar todos",
                onClick = { onEdit(ImportEditOp.IgnoreAllWarnings) },
                modifier = Modifier.testTag("import-review-ignore"),
            )
        }
        ImportIssueKind.NO_WEEKS -> ImportTextAction(label = "Empezar de nuevo", onClick = onRestart)
    }
}
