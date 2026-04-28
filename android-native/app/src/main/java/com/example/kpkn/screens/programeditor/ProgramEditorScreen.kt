package com.example.kpkn.screens.programeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.programdetail.components.MacrocycleEditor

// ─── Screen Entry Point ───────────────────────────────────────────────────────

@Composable
fun ProgramEditorScreen(
    programId: String,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ProgramEditorViewModel = viewModel(
        key = programId,
        factory = ProgramEditorViewModel.factory(programId),
    )
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    // If wizard mode → show the creation wizard
    if (uiState.isWizardMode) {
        ProgramCreatorWizard(
            uiState = uiState,
            viewModel = vm,
            onProgramCreated = { id -> onNavigateToDetail(id) },
            onCancel = onBack,
        )
        return
    }

    // Otherwise → full editor
    ProgramEditorContent(
        uiState = uiState,
        viewModel = vm,
        onBack = onBack,
    )
}

// ─── Editor Content ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorContent(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
    onBack: () -> Unit,
) {
    val program = uiState.programDraft
    if (program == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Editar Programa", fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Volver")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Programa no encontrado",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        return
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (program.name.isBlank()) "Editar Programa" else program.name,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.hasUnsavedChanges) showDiscardDialog = true else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.saveProgram(); onBack() }) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                    Box {
                        IconButton(onClick = { showMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenuExpanded,
                            onDismissRequest = { showMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Duplicar") },
                                onClick = { viewModel.duplicateProgram(); showMenuExpanded = false; onBack() },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                                onClick = { viewModel.setShowDeleteDialog(true); showMenuExpanded = false },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // Section tabs
            val sections = listOf(
                EditorSection.DETAILS   to "Detalles",
                EditorSection.STRUCTURE to "Estructura",
                EditorSection.GOALS     to "Objetivos",
                EditorSection.EVENTS    to "Eventos",
            )
            ScrollableTabRow(
                selectedTabIndex = sections.indexOfFirst { it.first == uiState.activeSection }.coerceAtLeast(0),
                edgePadding = 16.dp,
            ) {
                sections.forEach { (section, label) ->
                    Tab(
                        selected = uiState.activeSection == section,
                        onClick = { viewModel.setActiveSection(section) },
                        text = { Text(label, fontSize = 13.sp) },
                    )
                }
            }

            // Section content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState.activeSection) {
                    EditorSection.DETAILS   -> DetailsSection(program, viewModel)
                    EditorSection.STRUCTURE -> StructureSection(program, uiState, viewModel)
                    EditorSection.GOALS     -> GoalsSection(program, viewModel)
                    EditorSection.EVENTS    -> EventsSection(program, viewModel)
                    EditorSection.EXPORT    -> ExportSection()
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar cambios") },
            text = { Text("¿Salir sin guardar? Se perderán los cambios.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancelar") }
            },
        )
    }

    // Delete dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowDeleteDialog(false) },
            title = { Text("Eliminar programa") },
            text = { Text("Esta acción es permanente. ¿Eliminar «${program.name}»?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProgram(); onBack() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowDeleteDialog(false) }) { Text("Cancelar") }
            },
        )
    }

    // Split changer sheet
    if (uiState.isSplitChangerOpen) {
        SplitSelectorSheet(
            currentSplitId = program.selectedSplitId,
            currentStartDay = program.startDay ?: 1,
            showScopeSelector = true,
            onApply = { split, startDay -> viewModel.applySplitFromEditor(split, startDay) },
            onDismiss = { viewModel.setSplitChangerOpen(false) },
        )
    }
}

// ─── Details Section ──────────────────────────────────────────────────────────

@Composable
private fun DetailsSection(
    program: Program,
    viewModel: ProgramEditorViewModel,
) {
    EditorCard(title = "Información básica") {
        OutlinedTextField(
            value = program.name,
            onValueChange = viewModel::updateName,
            label = { Text("Nombre del programa") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = program.description ?: "",
            onValueChange = viewModel::updateDescription,
            label = { Text("Descripción (opcional)") },
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        )
    }

    EditorCard(title = "Modo de entrenamiento") {
        val modes = listOf(
            ProgramMode.HYPERTROPHY   to "Hipertrofia",
            ProgramMode.POWERLIFTING  to "Fuerza",
            ProgramMode.POWERBUILDING to "Powerbuilding",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                FilterChip(
                    selected = program.mode == mode,
                    onClick = { viewModel.updateMode(mode) },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
    }

    EditorCard(title = "Estructura") {
        val structures = listOf(
            ProgramStructure.SIMPLE  to "Simple",
            ProgramStructure.COMPLEX to "Compleja",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            structures.forEach { (struct, label) ->
                FilterChip(
                    selected = program.structure == struct,
                    onClick = { viewModel.updateStructure(struct) },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        Text(
            if (program.structure == ProgramStructure.SIMPLE) "Un macrociclo con semanas directas."
            else "Múltiples macrociclos, bloques y mesociclos.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Structure Section ────────────────────────────────────────────────────────

@Composable
private fun StructureSection(
    program: Program,
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    // Split card
    EditorCard(title = "Split semanal") {
        if (program.selectedSplitId != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Split activo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(program.selectedSplitId, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (program.startDay != null) {
                        val days = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
                        Text(
                            "Inicio: ${days.getOrElse(program.startDay) { "Lunes" }}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.setSplitChangerOpen(true) },
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Cambiar", fontSize = 12.sp) }
            }
        } else {
            Button(
                onClick = { viewModel.setSplitChangerOpen(true) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Asignar Split") }
        }
    }

    // Macrocycle tree editor
    MacrocycleEditor(
        program = program,
        onUpdateProgram = { updated ->
            viewModel.updateMacrocycles(updated.macrocycles)
        },
    )
}

// ─── Goals Section ────────────────────────────────────────────────────────────

@Composable
private fun GoalsSection(
    program: Program,
    viewModel: ProgramEditorViewModel,
) {
    EditorCard(title = "Metas de fuerza (1RM objetivo)") {
        Text("Define tus metas para los movimientos principales.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val bigThree = listOf("squat" to "Sentadilla", "bench" to "Press Banca", "deadlift" to "Peso Muerto")
        bigThree.forEach { (exerciseId, name) ->
            val current = program.exerciseGoals[exerciseId]
            // Include `current` in the key so the field resets when goal is deleted externally
            var textValue by remember(exerciseId, current) { mutableStateOf(current?.toString() ?: "") }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(name, Modifier.weight(1f), fontSize = 13.sp)
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    suffix = { Text("kg", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
            // Parse and save on every change
            LaunchedEffect(textValue) {
                val parsed = textValue.toDoubleOrNull()
                if (parsed != null) {
                    viewModel.addGoal(exerciseId, parsed)
                } else if (textValue.isBlank()) {
                    viewModel.removeGoal(exerciseId)
                }
            }
        }
    }

    if (program.exerciseGoals.isNotEmpty()) {
        EditorCard(title = "Objetivos activos") {
            program.exerciseGoals.forEach { (id, target) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(id, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${target}kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Delete,
                            "Eliminar",
                            Modifier
                                .size(16.dp)
                                .clickable { viewModel.removeGoal(id) },
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ─── Events Section ───────────────────────────────────────────────────────────

@Composable
private fun EventsSection(
    program: Program,
    viewModel: ProgramEditorViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Eventos del programa", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = { showAddDialog = true }) {
            Icon(Icons.Default.Add, "Agregar evento")
        }
    }

    if (program.events.isEmpty()) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Sin eventos. Agrega competencias, tests o picos de forma.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        program.events.forEach { event ->
            val eventId = event.id ?: return@forEach
            EventCard(event = event, onDelete = { viewModel.removeEvent(eventId) })
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            programWeeksCount = program.macrocycles.firstOrNull()?.blocks?.firstOrNull()?.mesocycles?.firstOrNull()?.weeks?.size ?: 0,
            onAdd = { event ->
                viewModel.addEvent(event)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun EventCard(
    event: ProgramEvent,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("${event.type} · Semana ${event.calculatedWeek}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.date.isNotBlank()) {
                    Text(event.date, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    programWeeksCount: Int,
    onAdd: (ProgramEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Competencia") }
    var date by remember { mutableStateOf("") }
    var week by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val eventTypes = listOf("Competencia", "Test 1RM", "Deload", "Custom")
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        eventTypes.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Fecha (yyyy-mm-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Semana del programa: $week", fontSize = 13.sp)
                Slider(
                    value = week.toFloat(),
                    onValueChange = { week = it.toInt() },
                    valueRange = 1f..programWeeksCount.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(ProgramEvent(
                        id = java.util.UUID.randomUUID().toString(),
                        title = title,
                        type = type,
                        date = date,
                        calculatedWeek = week,
                    ))
                },
                enabled = title.isNotBlank(),
            ) { Text("Agregar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

// ─── Export Section ───────────────────────────────────────────────────────────

@Composable
private fun ExportSection() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Exportar programa — próximamente.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Shared components ────────────────────────────────────────────────────────

@Composable
private fun EditorCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

