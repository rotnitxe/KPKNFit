package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.programs.importer.IMPORT_READING_TEXT
import com.example.kpkn.screens.programs.importer.ImportPasteField
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportReadingNotice
import com.example.kpkn.screens.programs.importer.ImportReview
import com.example.kpkn.screens.programs.importer.ImportSourceChoices
import com.example.kpkn.screens.programs.importer.ImportUndoLine
import com.example.kpkn.screens.programs.importer.ImportWarningsPanel
import com.example.kpkn.screens.programs.importer.importSessionsPerWeek
import com.example.kpkn.screens.programs.importer.rememberImportFilePicker

/**
 * PROGRAM_IMPORT · «Importa tu programa»: elegir un archivo o pegar el texto; después, la vista previa por semanas y días
 * con cada ejercicio reconocido, propio o con dudas, y su panel de ajuste, deshacer y revisión final. Escribe SOLO por el
 * ViewModel.
 */
@Composable
internal fun EntrenoProgramImportStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val imported = draft.importedProgram
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    var openedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    // Sube cada vez que «Ver avisos» de la revisión final: el panel de avisos se despliega y sube a la vista.
    var warningsReveal by rememberSaveable { mutableStateOf(0) }
    val resolutions = draft.importResolutions
    val issues = remember(imported, resolutions) {
        imported?.let { program -> ImportDraftValidation.check(program) { resolutions.containsKey(it) } }.orEmpty()
    }
    // La lectura va en el ViewModel: así su progreso se ve y «Cancelar» la corta.
    val pickFile = rememberImportFilePicker { uri -> vm.importFromPicked(uri) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (imported == null) {
            ImportSourceChoices(
                onChooseFile = pickFile,
                onPaste = { pasting = true },
                onPhoto = { uri, takenFile -> vm.importFromPicked(uri, photo = true, takenFile = takenFile) },
            )
            if (pasting) {
                ImportPasteField(
                    text = pasted,
                    onTextChange = { pasted = it },
                    onRead = { vm.importFromText(pasted) },
                )
            }
        } else {
            SetupFormTextField(
                value = draft.importTitle,
                onValueChange = vm::setImportTitle,
                label = "Título del programa",
                testTag = "setup-import-title",
            )
            state.importUndoLabel?.let { ImportUndoLine(label = it, onUndo = vm::undoImportEdit) }
            ImportWarningsPanel(
                imported = imported,
                reveal = warningsReveal,
                search = vm::searchImportCatalog,
                onOpenExercise = { openedExerciseId = it },
                onEdit = vm::editImport,
            )
            ImportPreviewWeeks(
                imported = imported,
                resolutions = resolutions,
                openedExerciseId = openedExerciseId,
                onOpen = { openedExerciseId = it },
                onEdit = vm::editImport,
                suggest = vm::importSuggestions,
                search = vm::searchImportCatalog,
                namesOf = vm::importNamesOf,
            )
            val sessions = importSessionsPerWeek(imported)
            val days = draft.selectedWeekdays.size
            if (sessions > days) {
                EntrenoPlanNotice(
                    text = "Tu programa tiene $sessions sesiones por semana y elegiste $days ${if (days == 1) "día" else "días"}.",
                    error = false,
                )
                EntrenoTextAction(label = "Cambiar días", onClick = { vm.editStep(SetupStepId.WEEKDAYS) })
            }
            ImportReview(
                imported = imported,
                issues = issues,
                onOpenExercise = { openedExerciseId = it },
                onEdit = vm::editImport,
                onShowWarnings = { warningsReveal += 1 },
                onRestart = vm::clearImport,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntrenoTextAction(label = "Cambiar archivo", onClick = pickFile)
                EntrenoTextAction(label = "Empezar de nuevo", onClick = vm::clearImport)
            }
        }
        if (state.importBusy) ImportReadingNotice(text = state.importProgress ?: IMPORT_READING_TEXT, onCancel = vm::cancelImport)
        state.importError?.let { EntrenoPlanNotice(text = it) }
    }
}
