package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.kpkn.screens.programs.importer.ImportEditOp
import com.example.kpkn.screens.programs.importer.ImportKnownProgramNotice
import com.example.kpkn.screens.programs.importer.ImportPasteField
import com.example.kpkn.screens.programs.importer.ImportPreviewWeeks
import com.example.kpkn.screens.programs.importer.ImportedProgramCanonicalReview
import com.example.kpkn.screens.programs.importer.ImportReadingNotice
import com.example.kpkn.screens.programs.importer.ImportReview
import com.example.kpkn.screens.programs.importer.ImportSourceChoices
import com.example.kpkn.screens.programs.importer.ImportUrlField
import com.example.kpkn.screens.programs.importer.ImportSharedCatalog
import com.example.kpkn.screens.programs.importer.ImportUndoLine
import com.example.kpkn.screens.programs.importer.ImportWarningsPanel
import com.example.kpkn.screens.programs.importer.ImportSessionEditorPortal
import com.example.kpkn.screens.programs.importer.editorTarget
import com.example.kpkn.screens.programs.importer.importSessionsPerWeek
import com.example.kpkn.screens.programs.importer.rememberImportFilePicker
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import kotlinx.coroutines.launch

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
    var enteringUrl by rememberSaveable { mutableStateOf(false) }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var catalogRawName by rememberSaveable { mutableStateOf<String?>(null) }
    var catalogExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    // Sube cada vez que «Ver avisos» de la revisión final: el panel de avisos se despliega y sube a la vista.
    var warningsReveal by rememberSaveable { mutableStateOf(0) }
    var activeSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeEditorStore by remember { mutableStateOf<ImportedProgramEditorStore?>(null) }
    val scope = rememberCoroutineScope()
    val resolutions = draft.importResolutions
    val issues = remember(imported, resolutions, draft.importedProgramEditor) {
        imported?.let { program ->
            draft.importedProgramEditor?.let { editor ->
                com.example.kpkn.screens.programs.importer.canonicalImportIssues(
                    program, editor.program, editor.sourceMap, draft.importResolutions,
                )
            } ?: ImportDraftValidation.checkOccurrences(program) { it.catalogSelectionId != null || resolutions.containsKey(it.rawName) }
        }.orEmpty()
    }
    fun openDay(dayId: String?) {
        scope.launch {
            val result = if (dayId == null) vm.addEmptyImportedSessionAndOpen() else vm.openImportedSessionEditor(dayId)
            if (result != null) {
                activeEditorStore = result.first
                activeSessionId = result.second.sessionId
            }
        }
    }
    fun createSessionInWeek(weekId: String?) {
        scope.launch {
            val result = vm.addEmptyImportedSessionAndOpen(weekId)
            if (result != null) {
                activeEditorStore = result.first
                activeSessionId = result.second.sessionId
            }
        }
    }
    fun openSourceExercise(exerciseId: String) {
        val dayId = imported?.weeks?.firstNotNullOfOrNull { week ->
            week.days.firstOrNull { day -> day.exercises.any { it.id == exerciseId } }?.id
        }
        if (dayId != null) openDay(dayId)
    }
    LaunchedEffect(activeSessionId, draft.importedProgramEditor?.id) {
        val sessionId = activeSessionId ?: return@LaunchedEffect
        activeEditorStore = vm.openCanonicalImportedSessionEditor(sessionId)?.first
    }
    // La lectura va en el ViewModel: así su progreso se ve y «Cancelar» la corta.
    val pickFile = rememberImportFilePicker { uri -> vm.importFromPicked(uri) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (imported == null) {
            ImportSourceChoices(
                onChooseFile = pickFile,
                onPaste = { pasting = true; enteringUrl = false },
                onPhoto = { uri, takenFile -> vm.importFromPicked(uri, photo = true, takenFile = takenFile) },
                onUrl = { enteringUrl = true; pasting = false },
            )
            if (pasting) {
                ImportPasteField(
                    text = pasted,
                    onTextChange = { pasted = it },
                    onRead = { vm.importFromText(pasted) },
                )
            } else if (enteringUrl) {
                ImportUrlField(
                    url = sourceUrl,
                    onUrlChange = { sourceUrl = it },
                    onRead = { vm.importFromUrl(sourceUrl) },
                )
            }
        } else {
            if (draft.importedProgramEditor == null && imported.weeks.none { week -> week.days.any { it.exercises.isNotEmpty() } }) {
                EntrenoPlanNotice(text = "No encontramos ejercicios en el contenido leído. Revisa los avisos o prueba otra fuente.")
            }
            SetupFormTextField(
                value = draft.importTitle,
                onValueChange = vm::setImportTitle,
                label = "Título del programa",
                testTag = "setup-import-title",
            )
            draft.importKnownPlanId?.takeUnless { draft.importKnownDismissed }?.let { planId ->
                ImportKnownProgramNotice(
                    planId = planId,
                    onUse = { vm.useKnownProgram(planId) },
                    onDismiss = vm::dismissImportKnown,
                    unavailable = state.importKnownUnavailable,
                )
            }
            state.importUndoLabel?.let { ImportUndoLine(label = it, onUndo = vm::undoImportEdit) }
            ImportWarningsPanel(
                imported = imported,
                reveal = warningsReveal,
                search = vm::searchImportCatalog,
                namesOf = vm::importNamesOf,
                useSharedCatalog = true,
                onOpenExercise = ::openSourceExercise,
                onEdit = vm::editImport,
                onOpenSessionEditor = ::openDay,
            )
            val editor = draft.importedProgramEditor
            if (editor != null) {
                ImportedProgramCanonicalReview(
                    source = imported,
                    sourceMap = editor.sourceMap,
                    program = editor.program,
                    onEditSession = { target ->
                        scope.launch {
                            val result = vm.openCanonicalImportedSessionEditor(target.sessionId)
                            if (result != null) {
                                activeEditorStore = result.first
                                activeSessionId = result.second.sessionId
                            }
                        }
                    },
                    issues = issues,
                    onShowWarnings = { warningsReveal += 1 },
                    onCreateSession = ::createSessionInWeek,
                )
            } else {
                ImportPreviewWeeks(
                    imported = imported,
                    resolutions = resolutions,
                    onEdit = vm::editImport,
                    namesOf = vm::importNamesOf,
                    onOpenCatalog = { exerciseId, rawName -> catalogExerciseId = exerciseId; catalogRawName = rawName },
                    onEditSession = { _, dayId -> openDay(dayId) },
                )
                if (imported.weeks.none { it.days.isNotEmpty() }) {
                    EntrenoTextAction(label = "Crear sesión vacía y abrir el editor", onClick = { openDay(null) })
                }
            }
            val sessions = draft.importedProgramEditor?.program?.macrocycles.orEmpty().flatMap { it.blocks }
                .flatMap { it.mesocycles }.flatMap { it.weeks }.maxOfOrNull { it.sessions.size }
                ?: importSessionsPerWeek(imported)
            val days = draft.selectedWeekdays.size
            if (sessions > days) {
                EntrenoPlanNotice(
                    text = "Tu programa tiene $sessions sesiones por semana y elegiste $days ${if (days == 1) "día" else "días"}.",
                    error = false,
                )
                EntrenoTextAction(label = "Cambiar días", onClick = { vm.editStep(SetupStepId.WEEKDAYS) })
            }
            if (editor == null) {
                ImportReview(
                    imported = imported,
                    issues = issues,
                    onOpenExercise = ::openSourceExercise,
                    onEdit = vm::editImport,
                    onShowWarnings = { warningsReveal += 1 },
                    onRestart = vm::clearImport,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntrenoTextAction(label = "Cambiar archivo", onClick = pickFile)
                EntrenoTextAction(label = "Empezar de nuevo", onClick = vm::clearImport)
            }
        }
        if (state.importBusy) ImportReadingNotice(text = state.importProgress ?: IMPORT_READING_TEXT, onCancel = vm::cancelImport)
        state.importError?.let { EntrenoPlanNotice(text = it) }
    }
    catalogRawName?.let { rawName ->
        ImportSharedCatalog(
            rawName = rawName,
            onSelect = { id -> catalogExerciseId?.let { vm.editImport(ImportEditOp.DecideOccurrence(it, id, rawName)) } },
            onClose = { catalogRawName = null; catalogExerciseId = null },
        )
    }
    val editor = draft.importedProgramEditor
    val target = activeSessionId?.let { editor?.program?.editorTarget(it) }
    if (target != null && activeEditorStore != null && editor != null) {
        ImportSessionEditorPortal(
            store = activeEditorStore!!,
            programId = target.programId,
            sessionId = target.sessionId,
            weekId = target.weekId,
            macroIndex = target.macroIndex,
            mesoIndex = target.mesoIndex,
            dayOfWeek = target.dayOfWeek,
            onClose = {
                vm.releaseImportedSessionEditor(editor.id)
                activeEditorStore = null
                activeSessionId = null
            },
        )
    }
}
