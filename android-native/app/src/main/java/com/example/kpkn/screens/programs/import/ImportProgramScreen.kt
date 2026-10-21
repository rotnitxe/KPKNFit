package com.example.kpkn.screens.programs.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportDraftValidation
import com.example.kpkn.screens.onboarding.SetupFormTextField
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.entreno.EntrenoPlanNotice
import com.example.kpkn.screens.programs.pluralDias
import com.example.kpkn.screens.programs.pluralSesiones
import com.example.kpkn.ui.entitlements.LocalPlan
import kotlinx.coroutines.launch

/**
 * Programas → Nuevo programa → Importar. La misma vista previa que el alta (semanas, días, ejercicios que se ajustan con su
 * panel, deshacer y revisión final). Abajo, fija sobre la barra de navegación, van los días de entrenamiento y «Guardar
 * programa», que solo se activa sin problemas y con al menos un día; respeta los límites del nivel: en Free, más de una semana o
 * más de cinco sesiones piden Pro.
 */
@Composable
fun ImportProgramScreen(
    onBack: () -> Unit,
    onSaved: (programId: String) -> Unit,
    onUseCatalogProgram: (planId: String) -> Unit,
    viewModel: ImportProgramViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val level: PlanLevel = LocalPlan.current
    val scope = rememberCoroutineScope()
    var pasting by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    // Sube cada vez que «Ver avisos» de la revisión final: el panel de avisos se despliega y sube a la vista.
    var warningsReveal by rememberSaveable { mutableStateOf(0) }
    // La lectura va en el ViewModel: así su progreso se ve y «Cancelar» la corta.
    val pickFile = rememberImportFilePicker { uri -> viewModel.importFromPicked(uri) }
    val imported = state.imported
    val resolutions = state.resolutions
    val issues = remember(imported, resolutions) {
        imported?.let { draft -> ImportDraftValidation.check(draft) { resolutions.containsKey(it) } }.orEmpty()
    }
    // Sin ningún día de entrenamiento no se guarda: la revisión lo dice y «Guardar programa» queda desactivado.
    val weekdaysMissing = imported != null && state.weekdays.isEmpty()
    val canSave = issues.isEmpty() && !weekdaysMissing
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Lo que se desplaza: bajo la barra de estado. Con la barra de guardar, termina justo encima de ella; sin ella, sobre la
        // barra de navegación del sistema.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .then(if (imported == null) Modifier.navigationBarsPadding() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ImportTextAction(label = "Atrás", onClick = onBack)
            Text("Importar programa", style = WizardTypography.stepTitle, color = WizardColors.text)
            if (imported == null) {
                ImportSourceChoices(
                    onChooseFile = pickFile,
                    onPaste = { pasting = true },
                    onPhoto = { uri, takenFile -> viewModel.importFromPicked(uri, photo = true, takenFile = takenFile) },
                )
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
                state.importKnownPlanId?.takeUnless { state.importKnownDismissed }?.let { planId ->
                    ImportKnownProgramNotice(
                        planId = planId,
                        onUse = { onUseCatalogProgram(planId) },
                        onDismiss = viewModel::dismissImportKnown,
                    )
                }
                state.undoLabel?.let { ImportUndoLine(label = it, onUndo = viewModel::undo) }
                ImportWarningsPanel(
                    imported = imported,
                    reveal = warningsReveal,
                    search = viewModel::search,
                    onOpenExercise = viewModel::open,
                    onEdit = viewModel::edit,
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
                    onShowWarnings = { warningsReveal += 1 },
                    onRestart = viewModel::clear,
                    weekdaysMissing = weekdaysMissing,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ImportTextAction(label = "Cambiar archivo", onClick = pickFile)
                    ImportTextAction(label = "Empezar de nuevo", onClick = viewModel::clear)
                }
            }
            if (state.busy) ImportReadingNotice(text = state.progress ?: IMPORT_READING_TEXT, onCancel = viewModel::cancelImport)
            state.error?.let { EntrenoPlanNotice(text = it) }
            state.notice?.let { EntrenoPlanNotice(text = it, error = false) }
        }
        if (imported != null) {
            ImportSaveBar(
                selected = state.weekdays,
                sessionsPerWeek = importSessionsPerWeek(imported),
                onToggleWeekday = viewModel::toggleWeekday,
                canSave = canSave,
                onSave = { scope.launch { viewModel.save(level)?.let(onSaved) } },
            )
        }
    }
}

/**
 * La barra fija de abajo, solo en la app: los días de entrenamiento y «Guardar programa». Va sobre la barra de navegación del
 * sistema, que nunca la tapa; el contenido de arriba termina justo encima de ella.
 */
@Composable
private fun ImportSaveBar(
    selected: Set<Int>,
    sessionsPerWeek: Int,
    onToggleWeekday: (Int) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ImportWeekdaysSection(selected = selected, sessionsPerWeek = sessionsPerWeek, onToggle = onToggleWeekday)
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("import-save"),
        ) {
            Text("Guardar programa")
        }
    }
}

/** Los días de la semana con su letra, en el orden de la semana (ISO: 1 = lunes). */
private val WEEKDAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

/** El nombre de cada día, para el lector de pantalla. */
private val WEEKDAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/**
 * La selección inicial, según los días de la semana con más sesiones: 1 → L; 2 → L J; 3 → L X V; 4 → L M J V; 5 → L M X J V; 6 →
 * L a S; 7 → todos. Con más de 7, todos; con 0, como con 1.
 */
internal fun importInitialWeekdays(sessionsPerWeek: Int): Set<Int> = when (sessionsPerWeek.coerceIn(1, 7)) {
    1 -> setOf(1)
    2 -> setOf(1, 4)
    3 -> setOf(1, 3, 5)
    4 -> setOf(1, 2, 4, 5)
    5 -> setOf(1, 2, 3, 4, 5)
    6 -> setOf(1, 2, 3, 4, 5, 6)
    else -> setOf(1, 2, 3, 4, 5, 6, 7)
}

/**
 * La línea bajo los días: con tantos días como sesiones, «3 sesiones en 3 días.»; con menos, que algunos días tendrán más de una
 * sesión; con más, nada. Sin ningún día elegido, nada: lo dice la revisión.
 */
internal fun importWeekdaysHelp(sessionsPerWeek: Int, chosen: Int): String? = when {
    chosen <= 0 -> null
    chosen == sessionsPerWeek -> "${pluralSesiones(sessionsPerWeek)} en ${pluralDias(chosen)}."
    chosen < sessionsPerWeek -> "Algunos días tendrán más de una sesión."
    else -> null
}

/**
 * «Días de entrenamiento»: siete interruptores, L M X J V S D, con 48 dp de alto táctil. El elegido va con la tinta de la
 * pantalla (como el conmutador de opciones del paquete).
 */
@Composable
internal fun ImportWeekdaysSection(
    selected: Set<Int>,
    sessionsPerWeek: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("import-weekdays"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Días de entrenamiento",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            (1..7).forEach { day ->
                ImportWeekdayToggle(
                    day = day,
                    on = day in selected,
                    onToggle = { onToggle(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        importWeekdaysHelp(sessionsPerWeek, selected.size)?.let { help ->
            Text(
                help,
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("import-weekdays-help"),
            )
        }
    }
}

/** Un día: un símbolo de 40 dp dentro de su objetivo táctil de 48 dp; elegido, relleno con la tinta de la pantalla. */
@Composable
private fun ImportWeekdayToggle(day: Int, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag("import-weekday-$day")
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = WEEKDAY_NAMES[day - 1] },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) WizardColors.cta else Color.Transparent)
                .border(1.dp, if (on) WizardColors.cta else WizardColors.textFaint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                WEEKDAY_LETTERS[day - 1],
                style = WizardTypography.cardTitle,
                color = if (on) WizardColors.ctaContent else WizardColors.textMuted,
            )
        }
    }
}
