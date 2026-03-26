package com.example.kpkn.screens.programeditor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.splits.SplitTemplate

// ─── Wizard Entry Point ───────────────────────────────────────────────────────

@Composable
fun ProgramCreatorWizard(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
    onProgramCreated: (programId: String) -> Unit,
    onCancel: () -> Unit,
) {
    var showSplitSheet by remember { mutableStateOf(false) }

    val stepIndex = when (uiState.wizardStep) {
        WizardStep.NAME  -> 0
        WizardStep.MODE  -> 1
        WizardStep.SPLIT -> 2
        WizardStep.DONE  -> 3
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Nuevo Programa", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Cancelar")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            // Progress indicators
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(4) { idx ->
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (idx <= stepIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                    )
                }
            }

            AnimatedContent(
                targetState = uiState.wizardStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                },
                label = "wizard_step",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        WizardStep.NAME  -> NameStep(uiState, viewModel)
                        WizardStep.MODE  -> ModeStep(uiState, viewModel)
                        WizardStep.SPLIT -> SplitStep(
                            uiState = uiState,
                            onOpenSheet = { showSplitSheet = true },
                        )
                        WizardStep.DONE  -> DoneStep(
                            uiState = uiState,
                            onSetWeeks = viewModel::setWizardWeeks,
                            onConfirm = {
                                val id = viewModel.saveProgram()
                                if (id != null) onProgramCreated(id)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSplitSheet) {
        SplitSelectorSheet(
            currentSplitId = uiState.programDraft?.selectedSplitId,
            currentStartDay = uiState.programDraft?.startDay ?: 1,
            onApply = { split, startDay ->
                viewModel.applyWizardSplit(split, startDay)
                showSplitSheet = false
            },
            onDismiss = { showSplitSheet = false },
        )
    }
}

// ─── Step 1: Name ─────────────────────────────────────────────────────────────

@Composable
private fun NameStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val name = uiState.programDraft?.name ?: ""

    Text("¿Cómo se llama tu programa?", fontSize = 22.sp, fontWeight = FontWeight.Black)
    Text("Ponle un nombre que recuerdes fácilmente.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = name,
        onValueChange = viewModel::updateName,
        placeholder = { Text("Ej. Bloque de Masa 2025") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = viewModel::nextWizardStep,
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Continuar", fontWeight = FontWeight.Bold)
    }
}

// ─── Step 2: Mode ─────────────────────────────────────────────────────────────

@Composable
private fun ModeStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val currentMode = uiState.programDraft?.mode ?: ProgramMode.HYPERTROPHY

    Text("¿Cuál es tu objetivo?", fontSize = 22.sp, fontWeight = FontWeight.Black)
    Text("Esto configura los valores por defecto del programa.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(8.dp))

    val modes = listOf(
        Triple(ProgramMode.HYPERTROPHY,   "Hipertrofia",    "Maximizar masa muscular con volumen moderado-alto"),
        Triple(ProgramMode.POWERLIFTING,  "Fuerza",         "Enfocado en sentadilla, press y peso muerto"),
        Triple(ProgramMode.POWERBUILDING, "Powerbuilding",  "Combina fuerza e hipertrofia en un ciclo"),
    )

    modes.forEach { (mode, label, desc) ->
        ModeCard(
            label = label,
            desc = desc,
            selected = currentMode == mode,
            onClick = { viewModel.updateMode(mode) },
        )
    }

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = viewModel::nextWizardStep,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Continuar", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeCard(
    label: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─── Step 3: Split ────────────────────────────────────────────────────────────

@Composable
private fun SplitStep(
    uiState: ProgramEditorUiState,
    onOpenSheet: () -> Unit,
) {
    val splitId = uiState.programDraft?.selectedSplitId

    Text("Elige un Split", fontSize = 22.sp, fontWeight = FontWeight.Black)
    Text("El split define cuántos días entrenas y qué grupos musculares.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(8.dp))

    if (splitId != null && splitId != "custom") {
        // Show selected split info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                Modifier.padding(14.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Split seleccionado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(splitId, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenSheet,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Cambiar Split")
        }
    } else {
        Button(
            onClick = onOpenSheet,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Seleccionar Split", fontWeight = FontWeight.Bold)
        }
    }

    if (splitId != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Split elegido. Pulsa 'Continuar' para seguir.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Step 4: Done / Weeks ─────────────────────────────────────────────────────

@Composable
private fun DoneStep(
    uiState: ProgramEditorUiState,
    onSetWeeks: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    var weeks by remember { mutableIntStateOf(8) }

    Text("¿Cuántas semanas?", fontSize = 22.sp, fontWeight = FontWeight.Black)
    Text("Cuántas semanas tendrá el bloque inicial del programa.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(16.dp))

    // Week count display
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$weeks", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("semanas", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Slider(
        value = weeks.toFloat(),
        onValueChange = { weeks = it.toInt() },
        valueRange = 1f..20f,
        steps = 18,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("1", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("20", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(Modifier.height(16.dp))

    // Summary
    val draft = uiState.programDraft
    if (draft != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SummaryRow("Nombre", draft.name)
                SummaryRow("Modo", draft.mode.name.lowercase().replaceFirstChar { it.uppercase() })
                SummaryRow("Split", draft.selectedSplitId ?: "Sin split")
                SummaryRow("Semanas", "$weeks semanas")
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            onSetWeeks(weeks)
            onConfirm()
        },
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Crear Programa", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
