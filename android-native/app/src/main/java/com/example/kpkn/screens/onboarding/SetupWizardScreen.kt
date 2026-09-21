package com.example.kpkn.screens.onboarding

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.programs.TrainingMaxWizard
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.screens.nutrition.NutritionWizardStep
import com.example.kpkn.screens.nutrition.NutritionWizardUiState
import com.example.kpkn.screens.nutrition.NutritionWizardStepContent
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerSheet
import com.example.kpkn.screens.sessioneditor.components.ExerciseCatalogInfoDialog
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.kpknGlassOrFallback
import java.time.LocalDate
import kotlinx.coroutines.launch

private val Yellow = Color(0xFFF4D35E)
private val Teal = Color(0xFF58C7C1)
private val Panel = Color.White.copy(alpha = 0.07f)
private val Muted = Color.White.copy(alpha = 0.64f)
private val equipmentLabels = mapOf(
    "general_gym" to "Gimnasio completo",
    "machine" to "Máquinas",
    "bodyweight" to "Peso corporal",
    "band" to "Bandas",
    "dumbbells" to "Mancuernas",
    "cable" to "Polea",
    "smith_machine" to "Máquina Smith",
    "support" to "Apoyo estable",
    "pull_up_bar" to "Barra de dominadas",
    "ball" to "Balón",
    "cardio" to "Equipo de cardio",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    mode: SetupWizardMode = SetupWizardMode.FULL,
    nutritionMode: String = "create",
    nutritionPlanId: String? = null,
    onDone: (String?) -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nutritionState by viewModel.nutritionEditor.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showDiscard by remember { mutableStateOf(false) }
    var pickerDay by remember { mutableStateOf<Int?>(null) }
    var pickerQuery by remember { mutableStateOf("") }
    var detailExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }
    var showTrainingMax by remember { mutableStateOf(false) }
    LaunchedEffect(mode, nutritionMode, nutritionPlanId) { viewModel.initialize(mode, nutritionMode, nutritionPlanId) }
    LaunchedEffect(state.receiptId) { state.receiptId?.let(onDone) }
    val chapters = viewModel.chapters(mode)
    val index = chapters.indexOf(state.draft.chapter).coerceAtLeast(0)
    val requestExit = { if (!state.isCommitting && state.dirty) showDiscard = true else if (!state.isCommitting) onCancel() }
    BackHandler { if (index > 0) viewModel.back() else requestExit() }

    Box(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            WizardHeader(state.draft.chapter, chapters, state.draft.name, index, { if (index > 0) viewModel.back() else requestExit() })
            AnimatedContent(state.draft.chapter, modifier = Modifier.weight(1f), label = "setup-chapter") { chapter ->
                when (chapter) {
                    SetupWizardChapter.PROFILE -> ProfileChapter(state.draft, state.errors, viewModel)
                    SetupWizardChapter.VOLUME -> VolumeChapter(state.draft, state.errors, viewModel)
                    SetupWizardChapter.TRAINING -> TrainingChapter(state.draft, state.errors, viewModel)
                    SetupWizardChapter.WEEK -> WeekChapter(state.draft, state.errors, state.previewError, viewModel, onAddExercises = { day -> pickerDay = day; pickerQuery = "" }, onOpenTrainingMax = { showTrainingMax = true })
                    SetupWizardChapter.RINGS -> RingsChapter(state.draft, state.errors, viewModel)
                    SetupWizardChapter.NUTRITION -> NutritionChapter(state.draft, nutritionState, viewModel)
                    SetupWizardChapter.REVIEW -> ReviewChapter(state, nutritionState, viewModel)
                }
            }
            if (state.errors.isNotEmpty()) ErrorPanel(state.errors)
            WizardFooter(
                first = index == 0,
                last = index == chapters.lastIndex,
                enabled = viewModel.canContinue() && !state.isCommitting && !state.isLoading,
                committing = state.isCommitting || state.isLoading,
                onBack = viewModel::back,
                onNext = { if (index == chapters.lastIndex) scope.launch { viewModel.commit(context) } else viewModel.next() },
                onCancel = requestExit,
            )
        }
        if (state.isLoading || state.isCommitting) {
            Surface(color = Color.Black.copy(alpha = 0.72f), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = Teal)
                        Text(if (state.isCommitting) "Guardando configuración…" else "Cargando…", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showDiscard) KpknAlertDialog(
        title = if (state.isCommitting) "Guardando configuración" else "¿Descartar este borrador?",
        text = if (state.isCommitting) "Espera a que termine el guardado para salir." else "Tus elecciones no se aplicarán hasta confirmar.",
        confirmLabel = if (state.isCommitting) "Cerrar" else "Descartar",
        dismissLabel = if (state.isCommitting) null else "Conservar borrador",
        onDismissRequest = { if (!state.isCommitting) showDiscard = false },
        onDismiss = if (state.isCommitting) null else { { showDiscard = false; onCancel() } },
        onConfirm = { if (!state.isCommitting) { viewModel.clear(); showDiscard = false; onCancel() } },
    )
    pickerDay?.let { day ->
        ModalBottomSheet(onDismissRequest = { pickerDay = null }) {
            ExercisePickerSheet(
                query = pickerQuery,
                catalog = exerciseCatalogSnapshot(),
                workoutLogs = ProgramRepository.getInstance().history.value,
                editingExisting = false,
                onSearch = { pickerQuery = it },
                onSelect = { info -> viewModel.addExercise(day, info); pickerDay = null },
                onMultiSelect = { infos -> infos.forEach { viewModel.addExercise(day, it) }; pickerDay = null; infos.map { it.id } },
                onOpenExerciseDetail = { id -> detailExercise = exerciseCatalogSnapshot().firstOrNull { it.id == id } },
                onDismiss = { pickerDay = null },
            )
        }
    }
    detailExercise?.let { exercise ->
        ExerciseCatalogInfoDialog(exercise = exercise, catalog = exerciseCatalogSnapshot(), associatedDiscomforts = emptyList(), onOpenExercise = { id -> detailExercise = exerciseCatalogSnapshot().firstOrNull { it.id == id } }, onDismiss = { detailExercise = null })
    }
    if (showTrainingMax) TrainingMaxWizard(initial = state.draft.powerliftingProfile, onDismiss = { showTrainingMax = false }, onConfirm = { profile -> viewModel.update { it.copy(powerliftingProfile = profile) }; showTrainingMax = false })
}

@Composable
private fun WizardHeader(chapter: SetupWizardChapter, chapters: List<SetupWizardChapter>, name: String, index: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowBack, "Atrás", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("Configuración inicial", color = Teal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(chapter.title(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            if (name.isNotBlank()) Text(name, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 2, modifier = Modifier.width(96.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chapters.forEachIndexed { i, _ -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(99.dp)).background(if (i <= index) Color.White else Color.White.copy(alpha = .12f))) }
        }
    }
}

@Composable
private fun WizardFooter(first: Boolean, last: Boolean, enabled: Boolean, committing: Boolean, onBack: () -> Unit, onNext: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth().kpknGlassOrFallback(null, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).navigationBarsPadding().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!first) TextButton(onClick = onBack, enabled = !committing, modifier = Modifier.height(52.dp)) { Text("Atrás", color = Color.White) }
        Button(onClick = onNext, enabled = enabled, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color(0xFF171717))) { Text(if (committing) "Guardando…" else if (last) "Confirmar configuración" else "Continuar", fontWeight = FontWeight.Bold) }
        IconButton(onClick = onCancel, enabled = !committing, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.Close, "Salir", tint = Color.White) }
    }
}

@Composable
private fun ProfileChapter(draft: SetupWizardDraft, errors: Map<String, String>, vm: SetupWizardViewModel) {
    ScrollChapter("Empezamos por ti", "Tu experiencia nos ayuda a preparar un punto de partida adecuado.") {
        Text("¿Qué quieres llevar con KPKN?", color = Color.White, fontWeight = FontWeight.Bold)
        LabeledChoices("Módulos", SetupModuleChoice.entries, draft.moduleChoice, { it.label() }) { vm.setModuleChoice(it) }
        Text("Crear un programa ahora es opcional en ambos casos.", color = Muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(draft.name, vm::setName, Modifier.fillMaxWidth(), label = { Text("Nombre o apodo, opcional") }, singleLine = true, isError = "name" in errors)
        AgeControl(draft.ageYears, vm, errors["age"])
        BirthDateControl(draft.birthDateIso, vm, errors["birthDate"])
        LabeledChoices("Experiencia", SetupExperience.entries, draft.experience, { it.label }) { vm.update { d -> d.copy(experience = it) } }
    }
}

@Composable
private fun VolumeChapter(draft: SetupWizardDraft, errors: Map<String, String>, vm: SetupWizardViewModel) {
    val answers = draft.volumeAnswers
    ScrollChapter("Calibremos tu volumen", "Una referencia breve para que el plan empiece con un volumen sostenible.") {
        LabeledChoices("Estilo de entrenamiento", com.example.kpkn.data.models.TrainingStyle.entries, answers.style, { it.label() }) { selected ->
            vm.setVolumeAnswer { answers -> answers.copy(style = selected) }
        }
        Rating("Técnica actual", answers.technique, "aprendiendo", "muy sólida", max = 3) { value -> vm.setVolumeAnswer { it.copy(technique = value) } }
        Rating("Consistencia", answers.consistency, "irregular", "muy constante", max = 3) { value -> vm.setVolumeAnswer { it.copy(consistency = value) } }
        Rating("Fuerza", answers.strength, "inicial", "avanzada", max = 3) { value -> vm.setVolumeAnswer { it.copy(strength = value) } }
        Rating("Movilidad", answers.mobility, "limitada", "amplia", max = 3) { value -> vm.setVolumeAnswer { it.copy(mobility = value) } }
        if (answers.style != null && answers.technique != null && answers.consistency != null && answers.strength != null && answers.mobility != null) {
            val result = com.example.kpkn.domain.training.VolumeCalibrationEngine.calculate(
                answers.style,
                answers.technique,
                answers.consistency,
                answers.strength,
                answers.mobility,
            )
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Punto de partida estimado", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${result.score.profileLevel.name.lowercase().replace('_', ' ')} · ${result.recommendations.size} grupos con referencia", color = Muted)
                    Text("Podrás ajustar el volumen por músculo antes de confirmar.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        errors["volumeStyle"]?.let { Text(it, color = Color(0xFFFFB4AB)) }
    }
}

@Composable
private fun AgeControl(age: Int?, vm: SetupWizardViewModel, error: String?) {
    Text("Edad", color = Color.White, fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { age?.let { vm.setAge(it - 1) } }, enabled = age != null, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "Restar un año", tint = Color.White) }
        Text(age?.let { "$it años" } ?: "Sin indicar", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { vm.setAge((age ?: 12) + 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, "Sumar un año", tint = Color.White) }
    }
    Slider(value = (age ?: 13).toFloat(), onValueChange = { vm.setAge(it.roundToInt()) }, valueRange = 13f..100f, steps = 0, colors = SliderDefaults.colors(thumbColor = Yellow, activeTrackColor = Yellow, inactiveTrackColor = Color.White.copy(alpha = 0.24f)), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Edad ${age?.let { "$it años" } ?: "sin indicar"}" })
    if (error != null) Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BirthDateControl(value: String?, vm: SetupWizardViewModel, error: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OutlinedTextField(value.orEmpty(), {}, Modifier.fillMaxWidth().clickable { openDatePicker(context, vm) }, readOnly = true, label = { Text("Fecha de nacimiento, opcional") }, placeholder = { Text("Seleccionar fecha") }, singleLine = true, isError = error != null, supportingText = { Text(error ?: "Alternativa a introducir la edad") })
    TextButton(onClick = { openDatePicker(context, vm) }) { Text("Elegir fecha", color = Teal) }
}

private fun openDatePicker(context: android.content.Context, vm: SetupWizardViewModel) {
    val today = LocalDate.now()
    DatePickerDialog(context, { _, year, month, day -> vm.setBirthDate(LocalDate.of(year, month + 1, day).toString()) }, today.year - 25, today.monthValue - 1, today.dayOfMonth).show()
}

@Composable
private fun TrainingChapter(draft: SetupWizardDraft, errors: Map<String, String>, vm: SetupWizardViewModel) {
    ScrollChapter("¿Cómo quieres entrenar?", "Elige tu enfoque, tus días y el equipo disponible.") {
        LabeledChoices("¿Qué tipo de inicio quieres?", SetupProgramRoute.entries, draft.programRoute, { it.label() }) { vm.setProgramRoute(it) }
        if (draft.programRoute == SetupProgramRoute.LATER) {
            Text("Dejamos el programa para después. La calibración de volumen, nutrición opcional y rings siguen disponibles.", color = Muted)
        } else {
            if (draft.programRoute == SetupProgramRoute.CUSTOMIZABLE) {
                LabeledChoices("Base del programa", SetupTrainingPath.entries, draft.trainingPath, { it.label }) { vm.update { d -> d.copy(trainingPath = it) } }
            }
            LabeledChoices("Objetivo", SetupGoal.entries, draft.goal, { it.label }, compact = true) { vm.update { d -> d.copy(goal = it) } }
            LabeledChoices("Enfoque", SetupFocus.entries, draft.focus, { it.label }, compact = true) { vm.update { d -> d.copy(focus = it) } }
            val muscles = listOf("Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen")
            Text("Músculos prioritarios", color = Color.White, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.forEach { muscle -> ChoiceChip(muscle, muscle in draft.priorityMuscles) { vm.setPriorityMuscles(if (muscle in draft.priorityMuscles) draft.priorityMuscles - muscle else draft.priorityMuscles + muscle) } }
            }
            Text("Menor énfasis (sin eliminar cobertura)", color = Color.White, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.forEach { muscle -> ChoiceChip(muscle, muscle in draft.lowerEmphasisMuscles) { vm.setLowerEmphasisMuscles(if (muscle in draft.lowerEmphasisMuscles) draft.lowerEmphasisMuscles - muscle else draft.lowerEmphasisMuscles + muscle) } }
            }
        }
    }
}

@Composable
private fun WeekChapter(
    draft: SetupWizardDraft,
    errors: Map<String, String>,
    previewError: String?,
    vm: SetupWizardViewModel,
    onAddExercises: (Int) -> Unit,
    onOpenTrainingMax: () -> Unit,
) {
    val entries = remember(draft.programRoute) {
        PersonalizedPlanCatalog.entries().filter { entry ->
            when (draft.programRoute) {
                SetupProgramRoute.PROTOCOL -> entry.source == com.example.kpkn.data.programs.CatalogSource.PROTOCOL
                SetupProgramRoute.CUSTOMIZABLE -> entry.source != com.example.kpkn.data.programs.CatalogSource.PROTOCOL
                SetupProgramRoute.LATER -> false
            }
        }
    }
    var catalogFilter by remember { mutableStateOf("Todos") }
    val visibleEntries = entries.filter { catalogFilter == "Todos" || it.classification.label() == catalogFilter }
    val compatible = entries.filter { entry -> draft.daysPerWeek?.let { it in entry.supportedFrequencies } != false && entry.supportedFocuses.any { it.name == draft.focus.name } }
    val selectedEntry = entries.firstOrNull { it.id == draft.selectedCatalogId }
    ScrollChapter("Elige tu semana", "Selecciona tus días y el plan que mejor encaje contigo.") {
        if (draft.programRoute == SetupProgramRoute.LATER) {
            Text("Has elegido continuar sin programa. No necesitas completar días, equipo ni split.", color = Color.White)
        } else {
            Text("Frecuencia y tiempo", color = Color.White, fontWeight = FontWeight.Bold)
            Stepper(draft.daysPerWeek, 1..6, "días") { value -> vm.update { d -> d.copy(daysPerWeek = value, selectedWeekdays = d.selectedWeekdays.take(value).toSet()) } }
            Stepper(draft.minutesPerSession, 20..100, "minutos", 5) { value -> vm.update { d -> d.copy(minutesPerSession = value) } }
            Text("Días de entrenamiento", color = Color.White, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { listOf("L" to 1, "M" to 2, "X" to 3, "J" to 4, "V" to 5, "S" to 6, "D" to 7).forEach { (label, day) -> ChoiceChip(label, day in draft.selectedWeekdays, Modifier.size(48.dp)) { vm.update { d -> if (day in d.selectedWeekdays) d.copy(selectedWeekdays = d.selectedWeekdays - day) else if (d.selectedWeekdays.size < (d.daysPerWeek ?: 0)) d.copy(selectedWeekdays = d.selectedWeekdays + day) else d } } } }
            Text("Descanso: ${7 - draft.selectedWeekdays.size} días", color = Muted)
            Text("Equipo disponible", color = Color.White, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SetupEquipment.entries.forEach { equipment -> ChoiceChip(equipment.label, equipment in draft.equipment) { vm.update { d -> d.copy(equipment = if (equipment in d.equipment) d.equipment - equipment else d.equipment + equipment) } } } }
            if (draft.programRoute == SetupProgramRoute.CUSTOMIZABLE) {
                Text("Split personalizado", color = Color.White, fontWeight = FontWeight.Bold)
                ChoiceChip("Usar mi patrón de 7 días", draft.selectedSplitId == "custom") {
                    vm.update { d ->
                        val pattern = if (d.customSplitPattern.size == 7) d.customSplitPattern else (1..7).map { day -> if (day in d.selectedWeekdays) "Cuerpo completo" else "Descanso" }
                        d.copy(selectedSplitId = if (d.selectedSplitId == "custom") null else "custom", customSplitPattern = pattern)
                    }
                }
                if (draft.selectedSplitId == "custom") {
                    (1..7).forEach { day ->
                        LabeledChoices("Día $day", listOf("Descanso", "Tren superior", "Tren inferior", "Cuerpo completo"), draft.customSplitPattern.getOrNull(day - 1) ?: "Descanso", { it }, compact = true) { label ->
                            vm.update { d -> d.copy(customSplitPattern = (if (d.customSplitPattern.size == 7) d.customSplitPattern else List(7) { "Descanso" }).toMutableList().also { values -> values[day - 1] = label }) }
                        }
                    }
                }
            }
            if (draft.trainingPath == SetupTrainingPath.PERSONALIZE) {
                Text(if (draft.programRoute == SetupProgramRoute.PROTOCOL) "Protocolos publicables · receta fiel, sin escalar por tu volumen." else "Programas personalizables · frecuencia ${draft.daysPerWeek ?: "—"} · foco ${draft.focus.label}", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (selectedEntry?.source == com.example.kpkn.data.programs.CatalogSource.PROTOCOL) {
                    OutlinedButton(onClick = onOpenTrainingMax, modifier = Modifier.fillMaxWidth()) { Text("Definir Training Max (opcional)") }
                    Text(if (draft.powerliftingProfile == null) "Sin 1RM todavía: podrás definirlo después para completar las cargas del protocolo." else "Training Max guardado para este protocolo.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                previewError?.let { Text(it, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Simple", "Avanzado").forEach { filter -> ChoiceChip(filter, filter == catalogFilter, onClick = { catalogFilter = filter }) }
                }
                visibleEntries.forEach { entry -> CatalogPlanPanel(entry, entry in compatible, draft.selectedCatalogId == entry.id) { vm.update { d -> d.copy(selectedCatalogId = entry.id) } } }
            } else {
                draft.selectedWeekdays.sorted().forEach { day ->
                    val session = draft.sessions.firstOrNull { it.weekday == day }
                    SessionDraftPanel(day, session, vm, onAddExercises)
                }
                if (draft.selectedWeekdays.isEmpty()) Text("Selecciona al menos un día para empezar a construir sesiones.", color = Muted)
            }
        }
    }
}

@Composable
private fun CatalogPlanPanel(entry: CatalogEntry, eligible: Boolean, selected: Boolean, onClick: () -> Unit) {
    SelectablePanel(entry.title, "${entry.technicalSubtitle} · ${entry.classification.label()}\n${entry.description}\nEquipo: ${entry.requiredEquipment.map { equipmentLabels[it] ?: "Equipo específico" }.joinToString()}. ${entry.disclaimer.orEmpty()}", selected, onClick)
    if (!eligible) Text("No encaja con tus días o enfoque actuales. Puedes cambiar esos filtros para habilitarlo.", color = Color(0xFFFFD38A), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun SessionDraftPanel(day: Int, session: SetupSessionDraft?, vm: SetupWizardViewModel, onAddExercises: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Día $day · ${session?.title ?: "Sesión nueva"}", color = Color.White, fontWeight = FontWeight.Bold)
        if (session == null) {
            Button(onClick = { vm.update { d -> d.copy(sessions = d.sessions + SetupSessionDraft(day, "Sesión del día $day")) }; onAddExercises(day) }) { Text("Añadir ejercicios") }
        } else {
            session.exercises.forEachIndexed { index, item ->
                Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { vm.removeExercise(day, item.id) }) { Icon(Icons.Default.Close, "Borrar", tint = Color.White) }
                        }
                        Text("${item.sets} series · ${item.reps} repeticiones", color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { vm.moveExercise(day, item.id, -1) }, enabled = index > 0) { Text("Subir") }
                            TextButton(onClick = { vm.moveExercise(day, item.id, 1) }, enabled = index < session.exercises.lastIndex) { Text("Bajar") }
                            TextButton(onClick = { vm.changeSets(day, item.id, (item.sets ?: 3) - 1) }) { Text("− serie") }
                            TextButton(onClick = { vm.changeSets(day, item.id, (item.sets ?: 3) + 1) }) { Text("+ serie") }
                            TextButton(onClick = { vm.changeReps(day, item.id, ((item.reps ?: 10) - 1).coerceAtLeast(1).toString()) }) { Text("− rep") }
                            TextButton(onClick = { vm.changeReps(day, item.id, ((item.reps ?: 10) + 1).toString()) }) { Text("+ rep") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { onAddExercises(day) }, modifier = Modifier.fillMaxWidth()) { Text("Añadir ejercicios") }
        }
    }
}

@Composable
private fun RingsChapter(draft: SetupWizardDraft, errors: Map<String, String>, vm: SetupWizardViewModel) {
    val answers = draft.ringsAnswers ?: SetupRingsAnswers()
    val recentLabel = when (answers.recentTrainingState) {
        SetupRecentTrainingState.YES -> "Sí"
        SetupRecentTrainingState.NO -> "No"
        SetupRecentTrainingState.UNKNOWN -> "No lo sé"
        SetupRecentTrainingState.NOT_ANSWERED -> when (answers.recentTraining) { true -> "Sí"; false -> "No"; else -> null }
    }
    val muscles = listOf("Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Erectores Espinales")
    val discomforts = listOf("none" to "Sin molestias", "shoulder_anterior" to "Hombro", "upper_back" to "Espalda alta", "lumbar" to "Lumbar", "hip_front" to "Cadera", "knee_patellar" to "Rodilla")
    ScrollChapter("Tu punto de partida", "Estimación inicial, no registro clínico ni entrenamiento inventado.") {
        Text("Los tres rings separan Músculos, Energía y Columna. Son una estimación inicial que podrás corregir ahora; no sustituyen una evaluación clínica.", color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Músculos" to answers.muscleFeeling, "Energía" to answers.energy, "Columna" to answers.structureFeeling).forEach { (label, value) ->
                Surface(color = Teal.copy(alpha = .12f), shape = CircleShape, modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(progress = { ((6 - (value ?: 3)) / 5f) }, color = Teal, trackColor = Color.White.copy(alpha = .12f), modifier = Modifier.size(38.dp), strokeWidth = 5.dp)
                        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        LabeledChoices("¿Entrenaste recientemente?", listOf("Sí", "No", "No lo sé"), recentLabel, { it }) { selected ->
            vm.update { d ->
                val state = when (selected) { "Sí" -> SetupRecentTrainingState.YES; "No" -> SetupRecentTrainingState.NO; else -> SetupRecentTrainingState.UNKNOWN }
                d.copy(ringsAnswers = answers.copy(recentTraining = state == SetupRecentTrainingState.YES, recentTrainingState = state, sessionsLastSevenDays = if (state == SetupRecentTrainingState.YES) answers.sessionsLastSevenDays else null))
            }
        }
        if (answers.recentTrainingState == SetupRecentTrainingState.YES || answers.recentTraining == true) {
            Stepper(answers.sessionsLastSevenDays, 1..7, "sesiones en 7 días") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(sessionsLastSevenDays = value)) } }
            Stepper(answers.lastSessionRecencyDays ?: answers.recencyDays, 0..14, "días desde la última") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(lastSessionRecencyDays = value, recencyDays = value)) } }
        }
        val activityLabel = if (answers.activityTypeState == com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN) "No sé" else answers.activityType?.label()
        LabeledChoices("Tipo de actividad", listOf("Fuerza", "Cardio", "Mixta", "No sé"), activityLabel, { it }) { selected ->
            vm.update { d -> d.copy(ringsAnswers = answers.copy(activityType = when (selected) { "Cardio" -> com.example.kpkn.data.models.InitialRecoveryActivityType.CARDIO; "Mixta" -> com.example.kpkn.data.models.InitialRecoveryActivityType.MIXED; else -> com.example.kpkn.data.models.InitialRecoveryActivityType.STRENGTH }, activityTypeState = if (selected == "No sé") com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN else com.example.kpkn.data.models.InitialRecoveryResponseState.DECLARED)) }
        }
        Rating("Intensidad percibida", answers.intensity ?: answers.intensityLevel?.let { level -> when (level) { com.example.kpkn.data.models.InitialRecoveryIntensity.EASY -> 1; com.example.kpkn.data.models.InitialRecoveryIntensity.MODERATE -> 2; com.example.kpkn.data.models.InitialRecoveryIntensity.HARD -> 3; com.example.kpkn.data.models.InitialRecoveryIntensity.VERY_HARD -> 4 } }, "fácil", "muy exigente") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(intensity = value, intensityLevel = when { value <= 1 -> com.example.kpkn.data.models.InitialRecoveryIntensity.EASY; value == 2 -> com.example.kpkn.data.models.InitialRecoveryIntensity.MODERATE; value == 3 -> com.example.kpkn.data.models.InitialRecoveryIntensity.HARD; else -> com.example.kpkn.data.models.InitialRecoveryIntensity.VERY_HARD })) } }
        LabeledChoices("¿Qué músculos trabajaste más?", listOf("No lo sé", "Cuerpo entero", "Elegir músculos"), when (answers.muscleScope) { com.example.kpkn.data.models.InitialRecoveryMuscleScope.FULL_BODY -> "Cuerpo entero"; com.example.kpkn.data.models.InitialRecoveryMuscleScope.SELECTED -> "Elegir músculos"; else -> "No lo sé" }, { it }) { selected -> vm.update { d -> d.copy(ringsAnswers = answers.copy(muscleScope = when (selected) { "Cuerpo entero" -> com.example.kpkn.data.models.InitialRecoveryMuscleScope.FULL_BODY; "Elegir músculos" -> com.example.kpkn.data.models.InitialRecoveryMuscleScope.SELECTED; else -> com.example.kpkn.data.models.InitialRecoveryMuscleScope.UNKNOWN })) } }
        if (answers.muscleScope == com.example.kpkn.data.models.InitialRecoveryMuscleScope.SELECTED) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                muscles.forEach { muscle -> ChoiceChip(muscle, muscle in answers.recentMuscles) { vm.update { d -> d.copy(ringsAnswers = answers.copy(recentMuscles = if (muscle in answers.recentMuscles) answers.recentMuscles - muscle else answers.recentMuscles + muscle)) } } }
            }
        }
        Rating("Sensación muscular", answers.muscleFeeling, "fresco", "fatigado") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(muscleFeeling = value)) } }
        Rating("Energía", answers.energy, "fresco", "fatigado") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(energy = value)) } }
        Rating("Zona estructural", answers.structureFeeling, "fresco", "fatigado") { value -> vm.update { d -> d.copy(ringsAnswers = answers.copy(structureFeeling = value)) } }
        LabeledChoices("¿Hubo carga axial reciente?", listOf("Sí", "No", "No lo sé"), when (answers.axialExposure.state) { com.example.kpkn.data.models.InitialRecoveryResponseState.DECLARED -> if ((answers.axialExposure.sessions ?: 0) > 0) "Sí" else "No"; else -> "No lo sé" }, { it }) { selected -> vm.update { d -> d.copy(ringsAnswers = answers.copy(axialExposure = com.example.kpkn.data.models.InitialRecoveryAxialExposure(state = if (selected == "No lo sé") com.example.kpkn.data.models.InitialRecoveryResponseState.UNKNOWN else com.example.kpkn.data.models.InitialRecoveryResponseState.DECLARED, sessions = if (selected == "Sí") (answers.axialExposure.sessions ?: 1) else 0, intensity = answers.intensityLevel, recencyDays = answers.lastSessionRecencyDays ?: answers.recencyDays))) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            discomforts.forEach { (id, label) -> ChoiceChip(label, if (id == "none") answers.discomfortIds.isEmpty() || "none" in answers.discomfortIds else id in answers.discomfortIds) { vm.update { d -> d.copy(ringsAnswers = answers.copy(discomfortIds = if (id == "none") emptyList() else (answers.discomfortIds - "none").let { current -> if (id in current) current - id else current + id })) } } }
        }
        Text("Reajuste manual por músculo", color = Color.White, fontWeight = FontWeight.Bold)
        val manualMuscles = (answers.recentMuscles.ifEmpty { muscles.take(4).toSet() }).toList()
        manualMuscles.forEach { muscle -> Rating(muscle, draft.manualMuscleOverrides[muscle], "fresco", "muy fatigado") { value -> vm.update { d -> d.copy(manualMuscleOverrides = d.manualMuscleOverrides + (muscle to value)) } } }
        Rating("Ajuste final de energía", draft.manualEnergyOverride ?: answers.energy, "fresco", "fatigado") { value -> vm.update { it.copy(manualEnergyOverride = value) } }
        Rating("Ajuste final de columna", draft.manualStructureOverride ?: answers.structureFeeling, "fresca", "cargada") { value -> vm.update { it.copy(manualStructureOverride = value) } }
        Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(if (answers.energy == null && answers.muscleFeeling == null) "Sin calibrar: puedes omitir este paso." else "Estimación inicial preparada para revisar.", color = Color.White, modifier = Modifier.padding(14.dp)) }
    }
}

@Composable
private fun NutritionChapter(draft: SetupWizardDraft, nutritionState: NutritionWizardUiState, vm: SetupWizardViewModel) {
    ScrollChapter("Tu alimentación", "Paso ${nutritionState.stepIndex + 1}/${NutritionWizardStep.entries.size} · ${nutritionState.step.title()}") {
        if (draft.includeNutrition) {
            NutritionWizardStepContent(nutritionState.copy(errors = if (nutritionState.errors.isNotEmpty()) nutritionState.errors else emptyMap()), vm.nutritionEditor)
        } else {
            Text("La nutrición queda pospuesta. Podrás configurarla más adelante.", color = Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.includeNutrition, onCheckedChange = { enabled -> vm.setModuleChoice(if (enabled) SetupModuleChoice.TRAINING_AND_NUTRITION else SetupModuleChoice.TRAINING) })
            Text("Incluir nutrición en esta configuración", color = Color.White)
        }
    }
}

private fun NutritionWizardStep.title(): String = when (this) {
    NutritionWizardStep.GOAL -> "Objetivo"
    NutritionWizardStep.DATA -> "Datos"
    NutritionWizardStep.GOALS -> "Actividad y meta"
    NutritionWizardStep.REVIEW -> "Calorías y macros"
}

@Composable
private fun ReviewChapter(state: SetupWizardState, nutritionState: NutritionWizardUiState, vm: SetupWizardViewModel) {
    val draft = state.draft
    ScrollChapter("Revisa tu plan", "Comprueba sesiones, métricas y qué se activará al confirmar.") {
        SummaryRow("Nombre", draft.name.ifBlank { "Sin indicar" })
        SummaryRow("Edad", draft.ageYears?.let { "$it años" } ?: draft.birthDateIso ?: "Sin indicar")
        SummaryRow("Objetivo", draft.goal?.label ?: "Sin indicar")
        SummaryRow("Enfoque", draft.focus.label)
        SummaryRow("Semana", "${draft.selectedWeekdays.size} días · ${draft.minutesPerSession ?: "—"} min")
        SummaryRow("Plan", when {
            draft.programRoute == SetupProgramRoute.LATER -> "Para después"
            PersonalizedPlanCatalog.find(draft.selectedCatalogId.orEmpty()) != null -> PersonalizedPlanCatalog.find(draft.selectedCatalogId.orEmpty())?.title.orEmpty()
            draft.trainingPath == SetupTrainingPath.FROM_SCRATCH -> "Desde cero"
            else -> "Sin seleccionar"
        })
        SummaryRow("RINGS", if (draft.ringsAnswers == null) "Sin calibrar" else "Evidencia capturada")
        if (state.isPreviewLoading) CircularProgressIndicator(color = Teal)
        state.previewError?.let { Text(it, color = Color(0xFFFFB4AB)) }
        state.programPreview?.let { preview ->
            Text("Vista previa del programa", color = Color.White, fontWeight = FontWeight.Bold)
            Text(preview.name, color = Teal, style = MaterialTheme.typography.titleMedium)
            val sessions = preview.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }
            sessions.forEach { session ->
                Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Día ${session.dayOfWeek ?: 1} · ${session.name}", color = Color.White, fontWeight = FontWeight.Bold)
                        session.allExercises().forEach { exercise -> Text("${exercise.name} · ${exercise.sets.size} series", color = Muted) }
                    }
                }
            }
        }
        state.previewReport?.let { report ->
            Text("Volumen, frecuencia y limitaciones", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (report.executable) "Receta ejecutable · ${report.classification.name.lowercase().replaceFirstChar { it.uppercase() }}"
                else "La receta necesita ajustes antes de activarse",
                color = if (report.executable) Teal else Color(0xFFFFD38A),
                style = MaterialTheme.typography.bodySmall,
            )
            report.limitations.forEach { limitation ->
                Text("• $limitation", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            report.muscles.take(6).forEach { muscle ->
                SummaryRow(
                    muscle.muscle,
                    "${(muscle.directSets + muscle.indirectSets).toInt()} series · objetivo ${muscle.targetSets.toInt()}",
                )
            }
        }
        if (draft.includeNutrition) {
            Text("Nutrición · ${nutritionState.effectiveKcal} kcal", color = Color.White, fontWeight = FontWeight.Bold)
            nutritionState.effectiveMacros?.let { macros -> Text("Proteína ${macros.proteinG.toInt()} g · Carbohidratos ${macros.carbsG.toInt()} g · Grasas ${macros.fatG.toInt()} g", color = Muted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.includeTraining, onCheckedChange = { enabled -> vm.update { it.copy(includeTraining = enabled) } })
            Text("Incluir programa", color = Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.includeNutrition, onCheckedChange = { enabled -> vm.update { it.copy(includeNutrition = enabled) } })
            Text("Incluir nutrición", color = Color.White)
        }
        if (draft.includeTraining) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.activateProgram, onCheckedChange = { enabled -> vm.update { it.copy(activateProgram = enabled) } })
            Text("Activar programa al guardar", color = Color.White)
        }
        if (draft.includeNutrition) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.activateNutrition, onCheckedChange = { enabled -> vm.update { it.copy(activateNutrition = enabled) } })
            Text("Activar nutrición al guardar", color = Color.White)
        }
        if ((draft.includeTraining && draft.activateProgram) || (draft.includeNutrition && draft.activateNutrition)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.confirmActivation, onCheckedChange = { enabled -> vm.update { it.copy(confirmActivation = enabled) } })
                Text("Confirmo que quiero activar las selecciones marcadas", color = Color.White)
            }
        }
        if (state.errors.isNotEmpty()) ErrorPanel(state.errors)
    }
}

@Composable
private fun ErrorPanel(errors: Map<String, String>) { Surface(color = Color(0xFF481D22), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { errors.values.distinct().forEach { Text(it, color = Color(0xFFFFD5D5), style = MaterialTheme.typography.bodySmall) } } } }

@Composable
private fun ScrollChapter(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(subtitle, color = Muted); content() } }

@Composable
private fun <T> LabeledChoices(title: String, options: List<T>, selected: T?, label: (T) -> String, compact: Boolean = false, onSelected: (T) -> Unit) {
    Text(title, color = Color.White, fontWeight = FontWeight.Bold)
    if (compact) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val optionLabel = label(option)
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        this.selected = isSelected
                        role = Role.RadioButton
                        contentDescription = "$optionLabel${if (isSelected) ", seleccionado" else ""}"
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Panel,
                        labelColor = Color.White,
                        selectedContainerColor = Teal.copy(alpha = 0.20f),
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> SelectablePanel(label(option), "", option == selected) { onSelected(option) } }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) { FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = modifier.semantics { this.selected = selected; role = Role.Checkbox; contentDescription = "$label${if (selected) ", seleccionado" else ""}" }) }

@Composable
private fun Stepper(value: Int?, range: IntRange, label: String, step: Int = 1, onChange: (Int) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { value?.let { onChange((it - step).coerceAtLeast(range.first)) } }, enabled = value != null, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Remove, "Reducir $label", tint = Color.White) }; Text(value?.let { "$it $label" } ?: "Sin indicar", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center); IconButton(onClick = { onChange(((value ?: range.first - step) + step).coerceAtMost(range.last)) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Add, "Aumentar $label", tint = Color.White) } } }

@Composable
private fun Rating(label: String, value: Int?, low: String, high: String, max: Int = 5, onChange: (Int) -> Unit) { Text(label, color = Color.White, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { (1..max).forEach { score -> ChoiceChip(score.toString(), value == score, Modifier.size(48.dp)) { onChange(score) } } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("1 · $low", color = Muted, style = MaterialTheme.typography.labelSmall); Text("$max · $high", color = Muted, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun SelectablePanel(title: String, subtitle: String, selected: Boolean = false, onClick: () -> Unit) { Surface(color = if (selected) Teal.copy(alpha = .20f) else Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().border(1.dp, if (selected) Teal else Color.White.copy(alpha = .10f), RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) } } }

@Composable
private fun SummaryRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(label, color = Muted, modifier = Modifier.width(92.dp)); Text(value, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End) } }

private fun SetupWizardChapter.title(): String = when (this) { SetupWizardChapter.PROFILE -> "Tu perfil"; SetupWizardChapter.VOLUME -> "Tu volumen"; SetupWizardChapter.TRAINING -> "Tu entrenamiento"; SetupWizardChapter.WEEK -> "Tu semana"; SetupWizardChapter.RINGS -> "Tu punto de partida"; SetupWizardChapter.NUTRITION -> "Tu alimentación"; SetupWizardChapter.REVIEW -> "Tu plan" }
private fun com.example.kpkn.data.models.TrainingStyle.label(): String = when (this) { com.example.kpkn.data.models.TrainingStyle.BODYBUILDER -> "Hipertrofia"; com.example.kpkn.data.models.TrainingStyle.POWERBUILDER -> "Powerbuilding"; com.example.kpkn.data.models.TrainingStyle.POWERLIFTER -> "Powerlifting" }
private fun com.example.kpkn.data.models.InitialRecoveryActivityType.label(): String = when (this) { com.example.kpkn.data.models.InitialRecoveryActivityType.STRENGTH -> "Fuerza"; com.example.kpkn.data.models.InitialRecoveryActivityType.CARDIO -> "Cardio"; com.example.kpkn.data.models.InitialRecoveryActivityType.MIXED -> "Mixta" }
private fun SetupModuleChoice.label(): String = when (this) { SetupModuleChoice.TRAINING -> "Entrenamiento"; SetupModuleChoice.TRAINING_AND_NUTRITION -> "Entrenamiento y alimentación" }
private fun SetupProgramRoute.label(): String = when (this) { SetupProgramRoute.CUSTOMIZABLE -> "Programas personalizables"; SetupProgramRoute.PROTOCOL -> "Protocolos"; SetupProgramRoute.LATER -> "Lo decidiré después" }
private fun com.example.kpkn.data.programs.CatalogClassification.label(): String = if (this == com.example.kpkn.data.programs.CatalogClassification.SIMPLE) "Simple" else "Avanzado"
