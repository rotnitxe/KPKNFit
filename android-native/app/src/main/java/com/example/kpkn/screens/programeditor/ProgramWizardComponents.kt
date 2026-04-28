package com.example.kpkn.screens.programeditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.kpkn.R
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramEvent
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.programs.PROGRAM_TEMPLATES
import com.example.kpkn.data.programs.ProgramTemplateOption
import com.example.kpkn.data.programs.resolveProgramTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import java.time.Instant
import java.util.UUID

private val wizardStepOrder = listOf(
    WizardStep.COVER,
    WizardStep.SPLIT,
)

private data class CoverGradientOption(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

private val coverGradients = listOf(
    CoverGradientOption("gradient://ember", "Ember", listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))),
    CoverGradientOption("gradient://lagoon", "Lagoon", listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))),
    CoverGradientOption("gradient://velvet", "Velvet", listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))),
    CoverGradientOption("gradient://forest", "Forest", listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))),
    CoverGradientOption("gradient://sunrise", "Sunrise", listOf(Color(0xFF2B0B3F), Color(0xFF8E2D5E), Color(0xFFFFB86C))),
    CoverGradientOption("gradient://ice", "Ice", listOf(Color(0xFF0B132B), Color(0xFF1C2541), Color(0xFF5BC0BE))),
    CoverGradientOption("gradient://obsidian", "Obsidian", listOf(Color(0xFF0A0A0A), Color(0xFF1C1C1E), Color(0xFF3A3A3C))),
    CoverGradientOption("gradient://mint", "Mint", listOf(Color(0xFF022C22), Color(0xFF0F766E), Color(0xFF99F6E4))),
    CoverGradientOption("gradient://copper", "Copper", listOf(Color(0xFF26110B), Color(0xFF8C3A1E), Color(0xFFE6A15A))),
    CoverGradientOption("gradient://storm", "Storm", listOf(Color(0xFF111827), Color(0xFF374151), Color(0xFF9CA3AF))),
    CoverGradientOption("gradient://aurora", "Aurora", listOf(Color(0xFF0B132B), Color(0xFF1D7874), Color(0xFFBEE9E8))),
    CoverGradientOption("gradient://sand", "Sand", listOf(Color(0xFF3A2E1F), Color(0xFF8C6A43), Color(0xFFE3C28B))),
)

private data class WizardDay(
    val index: Int,
    val label: String,
    val shortLabel: String,
)

private val wizardDays = listOf(
    WizardDay(1, "Lunes", "Lun"),
    WizardDay(2, "Martes", "Mar"),
    WizardDay(3, "Miércoles", "Mié"),
    WizardDay(4, "Jueves", "Jue"),
    WizardDay(5, "Viernes", "Vie"),
    WizardDay(6, "Sábado", "Sáb"),
    WizardDay(7, "Domingo", "Dom"),
)

private val wizardSplitFilters = listOf(
    null,
    SplitTag.RECOMENDADO_KPKN,
    SplitTag.ALTA_FRECUENCIA,
    SplitTag.BAJA_FRECUENCIA,
    SplitTag.BALANCEADO,
    SplitTag.ALTO_VOLUMEN,
    SplitTag.POWERLIFTING,
    SplitTag.PERSONALIZADO,
)

@Composable
internal fun WizardStepIndicator(
    currentStep: WizardStep,
    onStepClick: (WizardStep) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        wizardStepOrder.forEach { step ->
            val selected = step == currentStep
            val completed = wizardStepOrder.indexOf(step) < wizardStepOrder.indexOf(currentStep)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 18.dp else 12.dp)
                        .clip(CircleShape)
                        .clickable(enabled = selected || completed) { onStepClick(step) }
                        .background(
                            color = when {
                                selected -> Color.White
                                completed -> Color.White.copy(alpha = 0.5f)
                                else -> Color.White.copy(alpha = 0.2f)
                            },
                        ),
                ) {
                    if (completed && !selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.Center),
                            tint = Color.Black,
                        )
                    }
                }
                Text(
                    text = when (step) {
                        WizardStep.COVER -> "Portada"
                        WizardStep.SPLIT -> "Split"
                    },
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White.copy(alpha = if (selected) 0.9f else 0.5f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SimpleProgramSelectionCard(
    templates: List<ProgramTemplateOption>,
    selectedTemplateId: String,
    onSelect: (ProgramTemplateOption) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Plantillas simples",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Text(
            text = "Ideales para comenzar o rutinas cortas",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        WizardTemplateCarousel(
            templates = templates,
            selectedTemplateId = selectedTemplateId,
            showDetailedDescription = false,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AdvancedProgramSelectionCard(
    templates: List<ProgramTemplateOption>,
    selectedTemplateId: String,
    onSelect: (ProgramTemplateOption) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Plantillas avanzadas",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f),
        )
        Text(
            text = "Para competidores y entrenamientos avanzados",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
        WizardTemplateCarousel(
            templates = templates,
            selectedTemplateId = selectedTemplateId,
            showDetailedDescription = true,
            onSelect = onSelect,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CoverStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val draft = uiState.programDraft ?: return
    val selectedGradient = coverGradients.firstOrNull { it.id == draft.coverImage } ?: coverGradients.first()
    var selectedGradientState by remember { mutableStateOf(selectedGradient) }
    var showGradientSheet by rememberSaveable { mutableStateOf(false) }

    val openDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.updateCoverImage(uri.toString())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Información del programa",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )
        ProgramIdentityEditorCardSimple(
            name = draft.name,
            description = draft.description.orEmpty(),
            coverValue = draft.coverImage,
            selectedGradient = selectedGradientState,
            onNameChange = viewModel::updateName,
            onDescriptionChange = viewModel::updateDescription,
            onPickImage = { openDocument.launch(arrayOf("image/*")) },
            onSelectGradient = { selectedGradientState = it; viewModel.updateCoverImage(it.id) },
            onOpenGradientSheet = { showGradientSheet = true },
        )

        if (showGradientSheet) {
            ModalBottomSheet(onDismissRequest = { showGradientSheet = false }) {
                ProgramGradientCatalogSheet(
                    coverValue = draft.coverImage,
                    onSelect = {
                        selectedGradientState = it
                        viewModel.updateCoverImage(it.id)
                        showGradientSheet = false
                    },
                )
            }
        }
    }
}

@Composable
fun TemporalStructureStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val draft = uiState.programDraft ?: return
    val template = resolveProgramTemplate(uiState.selectedTemplateId)
    val simpleTemplates = PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.SIMPLE }
    val advancedTemplates = PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX }
    var infoDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var structureMode by rememberSaveable { mutableStateOf(ProgramStructure.SIMPLE) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Tipo de programa",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StructureModeButtonSimple(
                title = "Simple",
                selected = structureMode == ProgramStructure.SIMPLE,
                onClick = {
                    structureMode = ProgramStructure.SIMPLE
                    if (template.type != ProgramStructure.SIMPLE) {
                        viewModel.selectWizardTemplate(simpleTemplates.first { it.isDefault }.id)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            StructureModeButtonSimple(
                title = "Avanzado",
                selected = structureMode == ProgramStructure.COMPLEX,
                onClick = {
                    structureMode = ProgramStructure.COMPLEX
                    if (template.type != ProgramStructure.COMPLEX) {
                        viewModel.selectWizardTemplate(advancedTemplates.first().id)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        if (structureMode == ProgramStructure.SIMPLE) {
            SimpleProgramSelectionCard(
                templates = simpleTemplates,
                selectedTemplateId = template.id,
                onSelect = { option -> viewModel.selectWizardTemplate(option.id) },
            )
        } else {
            AdvancedProgramSelectionCard(
                templates = advancedTemplates,
                selectedTemplateId = template.id,
                onSelect = { option -> viewModel.selectWizardTemplate(option.id) },
            )
        }

        PreviewBlockStructureCardSimple(
            program = draft,
            template = template,
        )
    }

    if (infoDialog != null) {
        val title = if (infoDialog == "simple") "Programa simple" else "Programa avanzado"
        val body = if (infoDialog == "simple") {
            "Un programa simple usa un solo bloque o mesociclo que se repite en bucle. Más adelante podrás añadir eventos cíclicos y también crear tus propios loops: un concepto de KPKN para volver más dinámico un programa simple con semanas o eventos que se repiten cada ciertos ciclos."
        } else {
            "Un programa avanzado organiza el entrenamiento en varios bloques o mesociclos. Sirve para construir una progresión temporal más larga y, más adelante, podrás añadir fechas clave para ajustar el plan hacia tests, descargas o una competición."
        }

        AlertDialog(
            onDismissRequest = { infoDialog = null },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Entendido")
                }
            },
            title = { Text(title) },
            text = { Text(body) },
        )
    }
}

@Composable
fun SplitStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val draft = uiState.programDraft ?: return
    val selectedSplit = SPLIT_TEMPLATES.find { it.id == draft.selectedSplitId }
    val startDay = draft.startDay ?: 1
    val cycleDuration = draft.weekDays ?: 7
    var selectedFilter by rememberSaveable { mutableStateOf<SplitTag?>(null) }
    var showCatalog by rememberSaveable { mutableStateOf(draft.selectedSplitId != null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(5) }
    val selectedPattern = if (draft.customSplitPattern.isNotEmpty()) draft.customSplitPattern else selectedSplit?.pattern.orEmpty()

    val filteredSplits = remember(searchQuery, selectedFilter) {
        SPLIT_TEMPLATES.filter { split ->
            if (split.id == "custom") return@filter false
            val matchesTag = selectedFilter == null || split.tags.contains(selectedFilter)
            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isBlank() ||
                split.name.lowercase().contains(query) ||
                split.description.lowercase().contains(query) ||
                split.pros.any { it.lowercase().contains(query) } ||
                split.cons.any { it.lowercase().contains(query) }
            matchesTag && matchesSearch
        }
    }
    val visibleSplits = filteredSplits.take(visibleCount)

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Split semanal",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )

        if (!showCatalog) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Deseas elegir una plantilla de split semanal para comenzar a editar tus sesiones de la semana?",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Puedes hacerlo ahora o continuar y definirlo mas adelante.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showCatalog = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("No, lo vere despues")
                        }
                        Button(
                            onClick = {
                                showCatalog = true
                                visibleCount = 5
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Ver catalogo")
                        }
                    }
                }
            }
        }

        if (showCatalog) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    visibleCount = 5
                },
                label = { Text("Buscar split") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wizardSplitFilters, key = { it?.name ?: "all" }) { tag ->
                    FilterChip(
                        selected = tag == selectedFilter,
                        onClick = {
                            selectedFilter = if (selectedFilter == tag) null else tag
                            visibleCount = 5
                        },
                        label = { Text(splitTagLabel(tag)) },
                    )
                }
            }

            WizardSectionHeader(
                title = "Dia en que comienza tu semana",
                subtitle = "Este ajuste ordena la plantilla segun tus dias reales.",
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wizardDays, key = { it.index }) { day ->
                    FilterChip(
                        selected = day.index == startDay,
                        onClick = { viewModel.updateStartDay(day.index) },
                        label = { Text(day.shortLabel) },
                    )
                }
            }

            if (selectedSplit != null && selectedPattern.isNotEmpty()) {
                SelectedSplitOverviewCard(
                    split = selectedSplit,
                    pattern = selectedPattern,
                    startDay = startDay,
                    cycleDuration = cycleDuration,
                )
            }

            Text(
                text = "Mostrando ${visibleSplits.size} de ${filteredSplits.size} splits",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visibleSplits.forEach { split ->
                    WizardSplitCard(
                        split = split,
                        isSelected = split.id == draft.selectedSplitId,
                        startDay = startDay,
                        pattern = if (split.id == draft.selectedSplitId && draft.customSplitPattern.isNotEmpty()) draft.customSplitPattern else split.pattern,
                        onClick = { viewModel.applyWizardSplit(split, startDay) },
                        onMoveUp = { index ->
                            if (split.id == draft.selectedSplitId) {
                                viewModel.updateCustomSplitPattern(movePatternItem(draft.customSplitPattern, index, index - 1))
                            }
                        },
                        onMoveDown = { index ->
                            if (split.id == draft.selectedSplitId) {
                                viewModel.updateCustomSplitPattern(movePatternItem(draft.customSplitPattern, index, index + 1))
                            }
                        },
                    )
                }

                if (visibleCount < filteredSplits.size) {
                    OutlinedButton(
                        onClick = { visibleCount = (visibleCount + 5).coerceAtMost(filteredSplits.size) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cargar 5 mas")
                    }
                }

                OutlinedButton(
                    onClick = { showCatalog = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("No, lo vere despues")
                }
            }
        }
    }
}

@Composable
fun CalendarStep(
    uiState: ProgramEditorUiState,
    viewModel: ProgramEditorViewModel,
) {
    val draft = uiState.programDraft ?: return
    val template = resolveProgramTemplate(uiState.selectedTemplateId)
    var showEventComposer by rememberSaveable { mutableStateOf(draft.events.isEmpty()) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Duracion y eventos",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )

        if (template.type == ProgramStructure.SIMPLE) {
            SimpleCycleDurationCard(
                currentWeeks = draft.macrocycles.firstOrNull()
                    ?.blocks
                    ?.firstOrNull()
                    ?.mesocycles
                    ?.firstOrNull()
                    ?.weeks
                    ?.size ?: template.weeks,
                onDecrease = {
                    val next = (draft.macrocycles.firstOrNull()
                        ?.blocks
                        ?.firstOrNull()
                        ?.mesocycles
                        ?.firstOrNull()
                        ?.weeks
                        ?.size ?: template.weeks) - 1
                    viewModel.setWizardWeeks(next.coerceAtLeast(1))
                },
                onIncrease = {
                    val next = (draft.macrocycles.firstOrNull()
                        ?.blocks
                        ?.firstOrNull()
                        ?.mesocycles
                        ?.firstOrNull()
                        ?.weeks
                        ?.size ?: template.weeks) + 1
                    viewModel.setWizardWeeks(next)
                },
            )
        } else {
            ComplexBlockDurationCard(
                program = draft,
                template = template,
                onChangeDuration = viewModel::updateWizardBlockWeeks,
            )
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Eventos programados", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        Text(
                            "Tests, competencias o descargas.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { showEventComposer = !showEventComposer }) {
                        Text(if (showEventComposer) "Cerrar" else "Nuevo")
                    }
                }

                AnimatedVisibility(visible = showEventComposer) {
                    WizardEventComposer(
                        isComplex = template.type == ProgramStructure.COMPLEX,
                        onCancel = { showEventComposer = false },
                        onAdd = {
                            viewModel.addWizardEvent(it)
                            showEventComposer = false
                        },
                    )
                }

                if (draft.events.isEmpty()) {
                    EmptyStateCard(
                        title = "Sin eventos",
                        subtitle = "Puedes crear el programa así o dejar hitos listos desde ahora.",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.events.forEach { event ->
                            WizardEventRow(
                                event = event,
                                onDelete = { event.id?.let(viewModel::removeWizardEvent) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramPreviewSheet(
    uiState: ProgramEditorUiState,
    modifier: Modifier = Modifier,
) {
    val draft = uiState.programDraft ?: return
    val selectedGradient = coverGradients.firstOrNull { it.id == draft.coverImage } ?: coverGradients.first()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgramIdentityPreviewCard(
            name = draft.name,
            description = draft.description,
            coverValue = draft.coverImage,
            gradient = selectedGradient,
        )
    }
}

@Composable
private fun ProgramIdentityEditorCardSimple(
    name: String,
    description: String,
    coverValue: String?,
    selectedGradient: CoverGradientOption,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSelectGradient: (CoverGradientOption) -> Unit,
    onOpenGradientSheet: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProgramCoverArtSimple(
            coverValue = coverValue,
            gradient = selectedGradient,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nombre del programa") },
            placeholder = { Text("Ej: Base fuerza") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Descripción") },
            placeholder = { Text("Objetivo del programa y público objetivo") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPickImage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
            ) {
                Text("Subir foto")
            }
            Button(
                onClick = {
                    if (!isGradientCover(coverValue)) {
                        onSelectGradient(selectedGradient)
                    }
                    onOpenGradientSheet()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Usar gradiente")
            }
        }
    }
}

@Composable
private fun ProgramGradientCatalogSheet(
    coverValue: String?,
    onSelect: (CoverGradientOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Colores de portada",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Elige una base para el header del programa. Si usas una foto, esta accion volvera a una portada con gradiente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        coverGradients.forEach { option ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 92.dp, height = 64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(option.colors)),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.name, fontWeight = FontWeight.Bold)
                        Text(
                            if (coverValue == option.id) "Actual" else "Aplicar a la portada",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (coverValue == option.id) {
                        Text(
                            text = "Seleccionado",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProgramIdentityPreviewCard(
    name: String,
    description: String?,
    coverValue: String?,
    gradient: CoverGradientOption,
) {
    val displayName = name.ifBlank { "Nuevo programa" }
    val displayDescription = description?.takeIf { it.isNotBlank() }
        ?: "Añade una portada, un nombre claro y una descripción breve."

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            ProgramCoverArtSimple(
                coverValue = coverValue,
                gradient = gradient,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(displayName, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(displayDescription, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgramCoverArtSimple(
    coverValue: String?,
    gradient: CoverGradientOption,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradient.colors)),
    ) {
        if (isGradientCover(coverValue)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .align(Alignment.Center),
            )
            Image(
                painter = painterResource(id = R.drawable.kpknicon),
                contentDescription = "KPKN",
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.8f)),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverValue)
                    .crossfade(true)
                    .build(),
                contentDescription = "Portada",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun GradientSwatchSimple(
    option: CoverGradientOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(80.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White else Color.White.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(option.colors)),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .align(Alignment.Center),
                )
                Icon(
                    painter = painterResource(id = R.drawable.kpknicon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun WizardTemplateCard(
    modifier: Modifier = Modifier,
    template: ProgramTemplateOption,
    selected: Boolean,
    showDetailedDescription: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White else Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = template.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                if (showDetailedDescription) {
                    Text(
                        text = template.description,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    Text(
                        text = "SIMPLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.trackLabel != null) {
                    CompactBadgeSimple(text = template.trackLabel)
                }
                if (template.audienceLabel != null) {
                    CompactBadgeSimple(text = template.audienceLabel)
                }
                if (template.isDefault) {
                    CompactBadgeSimple(text = "Por defecto")
                }
                CompactBadgeSimple(text = "${template.weeks} sem")
                if (template.type == ProgramStructure.COMPLEX && template.blockNames.isNotEmpty()) {
                    CompactBadgeSimple(text = "${template.blockNames.size} bloques")
                }
            }
        }
    }
}

@Composable
private fun WizardSplitCard(
    split: SplitTemplate,
    isSelected: Boolean,
    startDay: Int,
    pattern: List<String>,
    onClick: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = split.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSelected) {
                            CompactBadge(text = "Actual")
                        }
                    }
                    Text(
                        text = split.description,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${trainingDayCount(pattern)}d",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pattern.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (day.equals("Descanso", ignoreCase = true)) {
                                    MaterialTheme.colorScheme.outlineVariant
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                },
                            ),
                    )
                }
            }

            Text(
                text = difficultyLabel(split.difficulty),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            AnimatedVisibility(visible = isSelected) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Reordena las sesiones",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                    pattern.forEachIndexed { index, day ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = shortDayLabel(rotatedDayIndex(startDay, index)),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                TextButton(
                                    onClick = { onMoveUp(index) },
                                    enabled = index > 0,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                ) {
                                    Text("↑")
                                }
                                TextButton(
                                    onClick = { onMoveDown(index) },
                                    enabled = index < pattern.lastIndex,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                ) {
                                    Text("↓")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedSplitOverviewCard(
    split: SplitTemplate,
    pattern: List<String>,
    startDay: Int,
    cycleDuration: Int,
) {
    val trainingDays = trainingDayCount(pattern)
    val restDays = (cycleDuration - trainingDays).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(split.name, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(split.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompactBadge(text = "${trainingDays} días")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pattern.forEachIndexed { index, day ->
                    val isRest = day.equals("Descanso", ignoreCase = true)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = shortDayLabel(rotatedDayIndex(startDay, index)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (isRest) MaterialTheme.colorScheme.outlineVariant
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                ),
                        )
                        Text(
                            text = if (isRest) "-" else day,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PreviewStatCard(
                    modifier = Modifier.weight(1f),
                    value = trainingDays.toString(),
                    label = "Entreno",
                )
                PreviewStatCard(
                    modifier = Modifier.weight(1f),
                    value = restDays.toString(),
                    label = "Descanso",
                )
            }
        }
    }
}

@Composable
private fun SimpleCycleDurationCard(
    currentWeeks: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Duración del ciclo",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        currentWeeks.toString(),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "semanas",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDecrease,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Reducir")
                    }
                    Button(
                        onClick = onIncrease,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aumentar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ComplexBlockDurationCard(
    program: Program,
    template: ProgramTemplateOption,
    onChangeDuration: (Int, Int) -> Unit,
) {
    val blocks = program.macrocycles.firstOrNull()?.blocks.orEmpty()
    val totalWeeks = totalProgramWeeks(program)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Duración de bloques", fontSize = 12.sp, fontWeight = FontWeight.Black)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                blocks.forEachIndexed { index, block ->
                    val weeks = block.mesocycles.firstOrNull()?.weeks?.size ?: 1
                    Box(
                        modifier = Modifier
                            .weight(weeks.toFloat())
                            .height(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(blockColor(index)),
                    )
                }
            }

            blocks.forEachIndexed { index, block ->
                val weeks = block.mesocycles.firstOrNull()?.weeks?.size ?: 1
                val goal = block.mesocycles.firstOrNull()?.goal ?: template.blockGoals.getOrElse(index) { MesocycleGoal.ACCUMULATION }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(block.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(goal.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onChangeDuration(index, (weeks - 1).coerceAtLeast(1)) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Reducir semanas")
                        }
                        Text("$weeks", fontSize = 18.sp, fontWeight = FontWeight.Black)
                        IconButton(onClick = { onChangeDuration(index, weeks + 1) }) {
                            Icon(Icons.Default.Add, contentDescription = "Aumentar semanas")
                        }
                    }
                }
            }

            Text(
                text = "Total: $totalWeeks semanas",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewBlockStructureCard(
    program: Program,
    template: ProgramTemplateOption,
) {
    val blocks = program.macrocycles.firstOrNull()?.blocks.orEmpty()

    if (blocks.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Estructura seleccionada", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactBadge(text = "${totalProgramWeeks(program)} sem")
                CompactBadge(text = "${blocks.size} bloques")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                blocks.forEachIndexed { index, block ->
                    val weeks = block.mesocycles.firstOrNull()?.weeks?.size ?: 1
                    Box(
                        modifier = Modifier
                            .weight(weeks.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(blockColor(index)),
                    )
                }
            }

            blocks.forEachIndexed { index, block ->
                val weeks = block.mesocycles.firstOrNull()?.weeks?.size ?: 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(block.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text(
                        "$weeks sem",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBlockStructureCardSimple(
    program: Program,
    template: ProgramTemplateOption,
) {
    PreviewBlockStructureCard(program, template)
}

@Composable
private fun CompactBadge(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = Color.White.copy(alpha = 0.1f),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun CompactBadgeSimple(
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.2f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun StructureModeButtonSimple(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color.White else Color.White.copy(alpha = 0.3f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun WizardTemplateCarousel(
    templates: List<ProgramTemplateOption>,
    selectedTemplateId: String,
    showDetailedDescription: Boolean,
    onSelect: (ProgramTemplateOption) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth - 24.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(templates, key = { it.id }) { option ->
                WizardTemplateCard(
                    modifier = Modifier.width(cardWidth),
                    template = option,
                    selected = option.id == selectedTemplateId,
                    showDetailedDescription = showDetailedDescription,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun WizardEventComposer(
    isComplex: Boolean,
    onCancel: () -> Unit,
    onAdd: (ProgramEvent) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var week by rememberSaveable { mutableIntStateOf(1) }
    var repeatEvery by rememberSaveable { mutableIntStateOf(4) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre del evento") },
                placeholder = { Text("Ej: Test 1RM") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            if (isComplex) {
                OutlinedTextField(
                    value = week.toString(),
                    onValueChange = { input ->
                        input.filter(Char::isDigit).toIntOrNull()?.let { week = it.coerceAtLeast(1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Semana") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
            } else {
                OutlinedTextField(
                    value = repeatEvery.toString(),
                    onValueChange = { input ->
                        input.filter(Char::isDigit).toIntOrNull()?.let { repeatEvery = it.coerceAtLeast(1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cada cuántos ciclos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (title.isBlank()) return@Button
                        onAdd(
                            ProgramEvent(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                type = "wizard_event",
                                date = Instant.now().toString(),
                                calculatedWeek = if (isComplex) week - 1 else 0,
                                repeatEveryXCycles = if (isComplex) null else repeatEvery,
                            ),
                        )
                        title = ""
                        week = 1
                        repeatEvery = 4
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Agregar")
                }
            }
        }
    }
}

@Composable
private fun WizardEventRow(
    event: ProgramEvent,
    onDelete: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (event.repeatEveryXCycles != null) {
                        "Cada ${event.repeatEveryXCycles} ciclos"
                    } else {
                        "Semana ${event.calculatedWeek + 1}"
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar evento")
                }
            }
        }
    }
}

@Composable
private fun WizardSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreviewStatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun blockColor(index: Int): Color {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.inversePrimary,
    )
    return colors[index % colors.size]
}

private fun stepTitle(step: WizardStep): String = when (step) {
    WizardStep.COVER -> "Portada"
    WizardStep.SPLIT -> "División de entrenamiento"
}

private fun stepCaption(step: WizardStep): String = when (step) {
    WizardStep.COVER -> "Identidad del programa"
    WizardStep.SPLIT -> "Selecciona cómo distribuir tus entrenamientos"
}

private fun splitTagLabel(tag: SplitTag?): String = when (tag) {
    null -> "Todos"
    SplitTag.RECOMENDADO_KPKN -> "Recomendado"
    SplitTag.ALTA_FRECUENCIA -> "Alta frecuencia"
    SplitTag.BAJA_FRECUENCIA -> "Baja frecuencia"
    SplitTag.BALANCEADO -> "Balanceado"
    SplitTag.ALTO_VOLUMEN -> "Alto volumen"
    SplitTag.ALTA_TOLERANCIA -> "Alta tolerancia"
    SplitTag.PERSONALIZADO -> "Personalizado"
    SplitTag.POWERLIFTING -> "Powerlifting"
}

private fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.PRINCIPIANTE -> "Principiante"
    Difficulty.INTERMEDIO -> "Intermedio"
    Difficulty.AVANZADO -> "Avanzado"
}

private fun dayLabel(day: Int): String {
    return wizardDays.firstOrNull { it.index == day }?.label ?: "Lunes"
}

private fun shortDayLabel(day: Int): String {
    return wizardDays.firstOrNull { it.index == day }?.shortLabel ?: "Lun"
}

private fun rotatedDayIndex(startDay: Int, offset: Int): Int {
    val normalizedStart = startDay.coerceIn(1, 7)
    return ((normalizedStart - 1 + offset) % 7) + 1
}

private fun trainingDayCount(pattern: List<String>): Int {
    return pattern.count { !it.equals("Descanso", ignoreCase = true) }
}

private fun movePatternItem(pattern: List<String>, fromIndex: Int, toIndex: Int): List<String> {
    if (pattern.isEmpty() || fromIndex !in pattern.indices || toIndex !in pattern.indices) return pattern
    val mutable = pattern.toMutableList()
    val item = mutable.removeAt(fromIndex)
    mutable.add(toIndex, item)
    return mutable.toList()
}

private fun totalProgramWeeks(program: Program): Int {
    return program.macrocycles.sumOf { macro ->
        macro.blocks.sumOf { block ->
            block.mesocycles.sumOf { it.weeks.size }
        }
    }
}

private fun isGradientCover(coverValue: String?): Boolean {
    return coverValue.isNullOrBlank() || coverValue.startsWith("gradient://")
}
