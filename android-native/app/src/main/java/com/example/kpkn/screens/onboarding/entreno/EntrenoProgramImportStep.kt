package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.programs.importer.ImportPasteField
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportSourceChoices
import com.example.kpkn.screens.programs.importer.ImportWarningsNote
import com.example.kpkn.screens.programs.importer.importSessionsPerWeek
import com.example.kpkn.screens.programs.importer.readPickedFile
import com.example.kpkn.screens.programs.importer.rememberImportFilePicker
import kotlinx.coroutines.launch

/**
 * PROGRAM_IMPORT · «Importa tu programa»: elegir un archivo o pegar el texto; después, la vista previa por semanas y días
 * con cada ejercicio reconocido, propio o con dudas. Escribe SOLO por el ViewModel.
 */
@Composable
internal fun EntrenoProgramImportStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val imported = draft.importedProgram
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    var openedName by rememberSaveable { mutableStateOf<String?>(null) }
    val pickFile = rememberImportFilePicker { uri ->
        scope.launch {
            readPickedFile(context.contentResolver, uri).fold(
                onSuccess = { (title, sheets) -> vm.importFromSheets(sheets, title) },
                onFailure = { vm.reportImportError(it.message ?: "No pudimos leer este archivo.") },
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (imported == null) {
            ImportSourceChoices(onChooseFile = pickFile, onPaste = { pasting = true })
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
            ImportWarningsNote(warnings = imported.warningMessages)
            ImportPreviewWeeks(
                imported = imported,
                resolutions = draft.importResolutions,
                openedName = openedName,
                onOpen = { openedName = it },
                onPick = vm::resolveImportedExercise,
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntrenoTextAction(label = "Cambiar archivo", onClick = pickFile)
                EntrenoTextAction(label = "Empezar de nuevo", onClick = vm::clearImport)
            }
        }
        if (state.importBusy) EntrenoPlanNotice(text = "Leyendo el programa…", error = false)
        state.importError?.let { EntrenoPlanNotice(text = it) }
    }
}
