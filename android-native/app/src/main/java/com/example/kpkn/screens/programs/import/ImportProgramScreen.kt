package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanNotice
import com.example.kpkn.screens.onboarding.entreno.EntrenoTextAction
import com.example.kpkn.ui.entitlements.LocalPlan
import kotlinx.coroutines.launch

/**
 * Programas → Nuevo programa → Importar. La misma vista previa que el alta (semanas, días, ejercicios que se ajustan con su
 * panel, deshacer y revisión final) y «Guardar programa», que solo se activa sin problemas y respeta los límites del nivel:
 * en Free, más de una semana o más de cinco sesiones piden Pro.
 */
@Composable
fun ImportProgramScreen(
    onBack: () -> Unit,
    onSaved: (programId: String) -> Unit,
    viewModel: ImportProgramViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val level: PlanLevel = LocalPlan.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    var warningsOpen by rememberSaveable { mutableStateOf(false) }
    val pickFile = rememberImportFilePicker { uri ->
        scope.launch {
            readPickedFile(context.contentResolver, uri).fold(
                onSuccess = { (title, sheets) -> viewModel.importFromSheets(sheets, title) },
                onFailure = { viewModel.reportError(it.message ?: "No pudimos leer este archivo.") },
            )
        }
    }
    val imported = state.imported
    val resolutions = state.resolutions
    val issues = remember(imported, resolutions) {
        imported?.let { draft -> ImportDraftValidation.check(draft) { resolutions.containsKey(it) } }.orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        EntrenoTextAction(label = "Atrás", onClick = onBack)
        Text("Importar programa", style = WizardTypography.stepTitle, color = WizardColors.text)
        if (imported == null) {
            ImportSourceChoices(onChooseFile = pickFile, onPaste = { pasting = true })
            if (pasting) {
                ImportPasteField(
                    text = pasted,
                    onTextChange = { pasted = it },
                    onRead = { viewModel.importFromText(pasted) },
                )
            }
        } else {
            SetupFormTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = "Título del programa",
                testTag = "import-title",
            )
            state.undoLabel?.let { ImportUndoLine(label = it, onUndo = viewModel::undo) }
            ImportWarningsNote(
                warnings = imported.warningMessages,
                expanded = warningsOpen,
                onExpandedChange = { warningsOpen = it },
            )
            ImportPreviewWeeks(
                imported = imported,
                resolutions = resolutions,
                openedExerciseId = state.openedExerciseId,
                onOpen = viewModel::open,
                onEdit = viewModel::edit,
                suggest = viewModel::suggestions,
                search = viewModel::search,
                namesOf = viewModel::namesOf,
            )
            ImportReview(
                imported = imported,
                issues = issues,
                onOpenExercise = viewModel::open,
                onEdit = viewModel::edit,
                onShowWarnings = { warningsOpen = true },
                onRestart = viewModel::clear,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EntrenoTextAction(label = "Cambiar archivo", onClick = pickFile)
                EntrenoTextAction(label = "Empezar de nuevo", onClick = viewModel::clear)
            }
            Button(
                onClick = { scope.launch { viewModel.save(level)?.let(onSaved) } },
                enabled = issues.isEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("import-save"),
            ) {
                Text("Guardar programa")
            }
        }
        if (state.busy) EntrenoPlanNotice(text = "Leyendo el programa…", error = false)
        state.error?.let { EntrenoPlanNotice(text = it) }
        state.notice?.let { EntrenoPlanNotice(text = it, error = false) }
    }
}
