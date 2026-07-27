package com.example.kpkn.screens.competitions

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.kpkn.ui.components.KpknSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.BodybuildingCompetitionDetails
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionJournal
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.CustomCompetitionMetric
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.calculations.IpfEquipment
import com.example.kpkn.domain.calculations.calculateIPFGLPoints
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class CompetitionViewModel : ViewModel() {
    private val repository = CompetitionRepository.getInstance()
    val records: StateFlow<List<CompetitionRecord>> = repository.records

    fun create(mode: CompetitionRecordMode, sport: CompetitionTemplateType) {
        repository.upsert(
            CompetitionRecord(
                id = UUID.randomUUID().toString(),
                title = defaultTitleFor(sport),
                recordMode = mode,
                sportType = sport,
                status = CompetitionRecordStatus.PLANNED,
            ).withDefaultsForSport(),
        )
    }

    fun save(record: CompetitionRecord) {
        repository.upsert(record.withDefaultsForSport().recalculatePowerlifting())
    }

    fun delete(recordId: String) {
        repository.delete(recordId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionScreen(
    onBack: () -> Unit,
    viewModel: CompetitionViewModel = viewModel { CompetitionViewModel() },
) {
    val records by viewModel.records.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<CompetitionRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Competencias", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Crear registro")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CompetitionHeroCard(onCreate = { showCreateSheet = true }) }

            if (records.isEmpty()) {
                item {
                    EmptyCompetitionsCard(onCreate = { showCreateSheet = true })
                }
            } else {
                items(records, key = { it.id }) { record ->
                    CompetitionRecordCard(
                        record = record,
                        onOpen = { editingRecord = record },
                        onDelete = { viewModel.delete(record.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(92.dp)) }
        }
    }

    if (showCreateSheet) {
        CreateCompetitionSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { mode, sport ->
                viewModel.create(mode, sport)
                showCreateSheet = false
            },
        )
    }

    editingRecord?.let { record ->
        EditCompetitionSheet(
            initial = record,
            onDismiss = { editingRecord = null },
            onSave = {
                viewModel.save(it)
                editingRecord = null
            },
        )
    }
}

@Composable
private fun CompetitionHeroCard(onCreate: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Historial competitivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Registra marcas, resultados, fotos y aprendizajes sin depender de una sesión en vivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
            }
            Button(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear registro de competición")
            }
        }
    }
}

@Composable
private fun EmptyCompetitionsCard(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Aún no hay competiciones", fontWeight = FontWeight.Black)
            Text(
                "Puedes guardar una competición técnica, una bitácora de experiencia o un registro completo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onCreate, shape = RoundedCornerShape(14.dp)) {
                Text("Crear el primero")
            }
        }
    }
}

@Composable
private fun CompetitionRecordCard(
    record: CompetitionRecord,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        record.title.ifBlank { "Competición" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(record.eventDate, record.location, record.federation)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                            .ifBlank { "Sin fecha configurada" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = {}, label = { Text(record.recordMode.label()) }) }
                item { AssistChip(onClick = {}, label = { Text(record.sportType.label()) }) }
                item { AssistChip(onClick = {}, label = { Text(record.status.label()) }) }
                record.placement?.takeIf { it.isNotBlank() }?.let {
                    item { AssistChip(onClick = {}, label = { Text("Puesto $it") }) }
                }
                record.medal?.takeIf { it.isNotBlank() }?.let {
                    item { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }

            val powerlifting = record.powerliftingDetails
            if (powerlifting?.totalKg != null || powerlifting?.ipfGlPoints != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    powerlifting.totalKg?.let { StatPill("Total", "${formatNumber(it)} kg") }
                    powerlifting.ipfGlPoints?.takeIf { it > 0.0 }?.let { StatPill("IPF GL", formatNumber(it)) }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCompetitionSheet(
    onDismiss: () -> Unit,
    onCreate: (CompetitionRecordMode, CompetitionTemplateType) -> Unit,
) {
    var mode by remember { mutableStateOf(CompetitionRecordMode.HYBRID) }
    var sport by remember { mutableStateOf(CompetitionTemplateType.POWERLIFTING) }

    KpknSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Crear registro de competición", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Tipo de registro", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            ModeSelector(selected = mode, onSelected = { mode = it })
            Text("Tipo de competición", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SportSelector(selected = sport, onSelected = { sport = it })
            Button(
                onClick = { onCreate(mode, sport) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCompetitionSheet(
    initial: CompetitionRecord,
    onDismiss: () -> Unit,
    onSave: (CompetitionRecord) -> Unit,
) {
    val context = LocalContext.current
    var record by remember(initial.id) { mutableStateOf(initial.withDefaultsForSport()) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val newPhotos = uris.map { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            CompetitionPhoto(id = UUID.randomUUID().toString(), uri = uri.toString())
        }
        record = record.copy(photos = record.photos + newPhotos)
    }

    KpknSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Registro de competición",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                IconButton(onClick = { onSave(record.recalculatePowerlifting()) }) {
                    Icon(Icons.Default.Save, contentDescription = "Guardar")
                }
            }

            GeneralFields(record = record, onChange = { record = it })

            if (record.recordMode == CompetitionRecordMode.TECHNICAL || record.recordMode == CompetitionRecordMode.HYBRID) {
                TechnicalSection(record = record, onChange = { record = it })
            }

            if (record.recordMode == CompetitionRecordMode.JOURNAL || record.recordMode == CompetitionRecordMode.HYBRID) {
                JournalSection(record = record, onChange = { record = it })
            }

            CustomMetricsSection(record = record, onChange = { record = it })

            PhotosSection(
                record = record,
                onAddPhotos = { photoPicker.launch(arrayOf("image/*")) },
                onRemovePhoto = { photoId -> record = record.copy(photos = record.photos.filterNot { it.id == photoId }) },
            )

            Button(
                onClick = { onSave(record.recalculatePowerlifting()) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar competición")
            }
        }
    }
}

@Composable
private fun GeneralFields(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    SectionCard(title = "Datos generales") {
        OutlinedTextField(
            value = record.title,
            onValueChange = { onChange(record.copy(title = it)) },
            label = { Text("Nombre del evento") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = record.eventDate.orEmpty(),
                onValueChange = { onChange(record.copy(eventDate = it.cleanNullable())) },
                label = { Text("Fecha") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("2026-08-15") },
            )
            OutlinedTextField(
                value = record.startTime.orEmpty(),
                onValueChange = { onChange(record.copy(startTime = it.cleanNullable())) },
                label = { Text("Hora") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("09:00") },
            )
        }
        OutlinedTextField(
            value = record.location.orEmpty(),
            onValueChange = { onChange(record.copy(location = it.cleanNullable())) },
            label = { Text("Ubicación") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = record.federation.orEmpty(),
            onValueChange = { onChange(record.copy(federation = it.cleanNullable())) },
            label = { Text("Federación u organización") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = record.category.orEmpty(),
                onValueChange = { onChange(record.copy(category = it.cleanNullable())) },
                label = { Text("Categoría") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = record.bodyweightKg?.toCleanString().orEmpty(),
                onValueChange = { onChange(record.copy(bodyweightKg = it.toDoubleOrNull())) },
                label = { Text("Peso corporal") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                suffix = { Text("kg") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = record.placement.orEmpty(),
                onValueChange = { onChange(record.copy(placement = it.cleanNullable())) },
                label = { Text("Puesto") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = record.medal.orEmpty(),
                onValueChange = { onChange(record.copy(medal = it.cleanNullable())) },
                label = { Text("Medalla") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = record.resultSummary.orEmpty(),
            onValueChange = { onChange(record.copy(resultSummary = it.cleanNullable())) },
            label = { Text("Resultado general") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedTextField(
            value = record.notes.orEmpty(),
            onValueChange = { onChange(record.copy(notes = it.cleanNullable())) },
            label = { Text("Notas generales") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        ReminderToggles(record = record, onChange = onChange)
    }
}

@Composable
private fun ReminderToggles(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Recordatorios", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        CheckRow("Una semana antes", record.reminderOneWeekEnabled) {
            onChange(record.copy(reminderOneWeekEnabled = it))
        }
        CheckRow("48 horas antes", record.reminder48hEnabled) {
            onChange(record.copy(reminder48hEnabled = it))
        }
        CheckRow("Al inicio del evento", record.reminderStartEnabled) {
            onChange(record.copy(reminderStartEnabled = it))
        }
    }
}

@Composable
private fun TechnicalSection(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    SectionCard(title = "Registro técnico") {
        if (record.sportType == CompetitionTemplateType.POWERLIFTING) {
            PowerliftingDetails(record = record, onChange = onChange)
        } else {
            record.technicalBlocks.forEach { block ->
                TechnicalBlockEditor(
                    block = block,
                    onChange = { updated ->
                        onChange(record.copy(technicalBlocks = record.technicalBlocks.map { if (it.id == block.id) updated else it }))
                    },
                    onDelete = {
                        onChange(record.copy(technicalBlocks = record.technicalBlocks.filterNot { it.id == block.id }))
                    },
                )
            }
            FilledTonalButton(
                onClick = {
                    onChange(
                        record.copy(
                            technicalBlocks = record.technicalBlocks + CompetitionTechnicalBlock(
                                id = UUID.randomUUID().toString(),
                                title = "Resultado técnico",
                                attempts = listOf(defaultAttempt(1)),
                            ),
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir bloque técnico")
            }
        }
    }
}

@Composable
private fun PowerliftingDetails(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    val details = record.powerliftingDetails ?: PowerliftingCompetitionDetails()
    OutlinedTextField(
        value = details.weightClass.orEmpty(),
        onValueChange = { onChange(record.copy(powerliftingDetails = details.copy(weightClass = it.cleanNullable())).recalculatePowerlifting()) },
        label = { Text("Categoría de peso") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = details.division.orEmpty(),
            onValueChange = { onChange(record.copy(powerliftingDetails = details.copy(division = it.cleanNullable()))) },
            label = { Text("División") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = details.sexCategory.orEmpty(),
            onValueChange = { onChange(record.copy(powerliftingDetails = details.copy(sexCategory = it.cleanNullable())).recalculatePowerlifting()) },
            label = { Text("Sexo/cat.") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
    EquipmentSelector(
        selected = details.equipment,
        onSelected = {
            onChange(record.copy(powerliftingDetails = details.copy(equipment = it)).recalculatePowerlifting())
        },
    )
    record.technicalBlocks.forEach { block ->
        TechnicalBlockEditor(
            block = block,
            onChange = { updated ->
                onChange(
                    record.copy(technicalBlocks = record.technicalBlocks.map { if (it.id == block.id) updated else it })
                        .recalculatePowerlifting()
                )
            },
            onDelete = {},
            lockedTitle = true,
        )
    }
    val recalculated = record.recalculatePowerlifting().powerliftingDetails
    if (recalculated?.totalKg != null || recalculated?.ipfGlPoints != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recalculated.totalKg?.let { StatPill("Total", "${formatNumber(it)} kg") }
            recalculated.ipfGlPoints?.takeIf { it > 0.0 }?.let { StatPill("IPF GL", formatNumber(it)) }
        }
    }
}

@Composable
private fun TechnicalBlockEditor(
    block: CompetitionTechnicalBlock,
    onChange: (CompetitionTechnicalBlock) -> Unit,
    onDelete: () -> Unit,
    lockedTitle: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lockedTitle) {
                    Text(block.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Black)
                } else {
                    OutlinedTextField(
                        value = block.title,
                        onValueChange = { onChange(block.copy(title = it)) },
                        label = { Text("Movimiento o métrica") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar bloque")
                    }
                }
            }
            block.attempts.forEach { attempt ->
                AttemptRow(
                    attempt = attempt,
                    onChange = { updated ->
                        onChange(
                            block.copy(
                                attempts = block.attempts.map { if (it.id == attempt.id) updated else it },
                            ).withBestValid()
                        )
                    },
                )
            }
            if (!lockedTitle || block.attempts.size < 4) {
                TextButton(
                    onClick = {
                        onChange(
                            block.copy(
                                attempts = block.attempts + defaultAttempt(block.attempts.size + 1),
                            )
                        )
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Añadir intento")
                }
            }
            OutlinedTextField(
                value = block.notes.orEmpty(),
                onValueChange = { onChange(block.copy(notes = it.cleanNullable())) },
                label = { Text("Notas técnicas") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

@Composable
private fun AttemptRow(attempt: CompetitionAttempt, onChange: (CompetitionAttempt) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${attempt.attemptNumber}", modifier = Modifier.width(28.dp), fontWeight = FontWeight.Black)
            OutlinedTextField(
                value = attempt.weightKg?.toCleanString().orEmpty(),
                onValueChange = { onChange(attempt.copy(weightKg = it.toDoubleOrNull())) },
                label = { Text("Peso") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                suffix = { Text("kg") },
            )
            OutlinedTextField(
                value = attempt.rpe?.toCleanString().orEmpty(),
                onValueChange = { onChange(attempt.copy(rpe = it.toDoubleOrNull())) },
                label = { Text("RPE") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompetitionAttemptResult.entries.forEach { result ->
                item {
                    FilterChip(
                        selected = attempt.resultType == result,
                        onClick = { onChange(attempt.copy(resultType = result)) },
                        label = { Text(result.label()) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = attempt.technicalNotes.orEmpty(),
            onValueChange = { onChange(attempt.copy(technicalNotes = it.cleanNullable())) },
            label = { Text("Comentario técnico") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
        )
    }
}

@Composable
private fun JournalSection(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    val journal = record.journal ?: CompetitionJournal()
    SectionCard(title = "Bitácora / experiencia") {
        JournalField("Sensación general", journal.overallFeeling) { onChange(record.copy(journal = journal.copy(overallFeeling = it.cleanNullable()))) }
        JournalField("Estado físico", journal.physicalState) { onChange(record.copy(journal = journal.copy(physicalState = it.cleanNullable()))) }
        JournalField("Estado mental", journal.mentalState) { onChange(record.copy(journal = journal.copy(mentalState = it.cleanNullable()))) }
        JournalField("Qué salió bien", journal.whatWentWell) { onChange(record.copy(journal = journal.copy(whatWentWell = it.cleanNullable()))) }
        JournalField("Qué salió mal", journal.whatWentWrong) { onChange(record.copy(journal = journal.copy(whatWentWrong = it.cleanNullable()))) }
        JournalField("Aprendizajes", journal.learnings) { onChange(record.copy(journal = journal.copy(learnings = it.cleanNullable()))) }
        JournalField("Feedback de jueces", journal.judgesFeedback) { onChange(record.copy(journal = journal.copy(judgesFeedback = it.cleanNullable()))) }
    }
}

@Composable
private fun JournalField(label: String, value: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@Composable
private fun CustomMetricsSection(record: CompetitionRecord, onChange: (CompetitionRecord) -> Unit) {
    SectionCard(title = "Métricas personalizadas") {
        record.customMetrics.forEach { metric ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = metric.label,
                    onValueChange = { label ->
                        onChange(record.copy(customMetrics = record.customMetrics.map { if (it.id == metric.id) it.copy(label = label) else it }))
                    },
                    label = { Text("Métrica") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = metric.value,
                    onValueChange = { value ->
                        onChange(record.copy(customMetrics = record.customMetrics.map { if (it.id == metric.id) it.copy(value = value) else it }))
                    },
                    label = { Text("Valor") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = { onChange(record.copy(customMetrics = record.customMetrics.filterNot { it.id == metric.id })) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar métrica")
                }
            }
        }
        FilledTonalButton(
            onClick = {
                onChange(record.copy(customMetrics = record.customMetrics + CustomCompetitionMetric(UUID.randomUUID().toString(), "Resultado", "")))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Añadir métrica")
        }
    }
}

@Composable
private fun PhotosSection(
    record: CompetitionRecord,
    onAddPhotos: () -> Unit,
    onRemovePhoto: (String) -> Unit,
) {
    SectionCard(title = "Fotos") {
        Text(
            if (record.photos.isEmpty()) "Sin fotos guardadas" else "${record.photos.size} fotos guardadas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        record.photos.forEach { photo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    photo.caption ?: photo.uri.substringAfterLast('/').take(28),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = { onRemovePhoto(photo.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar foto")
                }
            }
        }
        FilledTonalButton(
            onClick = onAddPhotos,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Añadir fotos")
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeSelector(selected: CompetitionRecordMode, onSelected: (CompetitionRecordMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompetitionRecordMode.entries.forEach { mode ->
            item {
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(mode.label()) },
                )
            }
        }
    }
}

@Composable
private fun SportSelector(selected: CompetitionTemplateType, onSelected: (CompetitionTemplateType) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompetitionTemplateType.entries.forEach { sport ->
            item {
                FilterChip(
                    selected = selected == sport,
                    onClick = { onSelected(sport) },
                    label = { Text(sport.label()) },
                )
            }
        }
    }
}

@Composable
private fun EquipmentSelector(selected: CompetitionEquipment, onSelected: (CompetitionEquipment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Equipamiento", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(CompetitionEquipment.RAW, CompetitionEquipment.SLEEVES, CompetitionEquipment.WRAPS, CompetitionEquipment.EQUIPPED).forEach { equipment ->
                item {
                    FilterChip(
                        selected = selected == equipment,
                        onClick = { onSelected(equipment) },
                        label = { Text(equipment.label()) },
                    )
                }
            }
        }
    }
}

private fun CompetitionRecord.withDefaultsForSport(): CompetitionRecord {
    val withJournal = if (recordMode == CompetitionRecordMode.JOURNAL || recordMode == CompetitionRecordMode.HYBRID) {
        if (journal == null) copy(journal = CompetitionJournal()) else this
    } else {
        this
    }

    return when (withJournal.sportType) {
        CompetitionTemplateType.POWERLIFTING -> {
            val existingTypes = withJournal.technicalBlocks.map { it.movementType }.toSet()
            val blocks = withJournal.technicalBlocks + listOf(
                CompetitionMovementType.SQUAT to "Sentadilla",
                CompetitionMovementType.BENCH to "Press banca",
                CompetitionMovementType.DEADLIFT to "Peso muerto",
            ).filterNot { (type, _) -> type in existingTypes }
                .map { (type, title) ->
                    CompetitionTechnicalBlock(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        movementType = type,
                        attempts = listOf(defaultAttempt(1), defaultAttempt(2), defaultAttempt(3)),
                    )
                }
            withJournal.copy(
                technicalBlocks = blocks.sortedBy { it.movementType.powerliftingOrder() },
                powerliftingDetails = withJournal.powerliftingDetails ?: PowerliftingCompetitionDetails(),
            ).recalculatePowerlifting()
        }
        CompetitionTemplateType.BODYBUILDING -> withJournal.copy(
            bodybuildingDetails = withJournal.bodybuildingDetails ?: BodybuildingCompetitionDetails(),
        )
        else -> withJournal
    }
}

private fun CompetitionRecord.recalculatePowerlifting(): CompetitionRecord {
    if (sportType != CompetitionTemplateType.POWERLIFTING) return this
    val details = powerliftingDetails ?: PowerliftingCompetitionDetails()
    val updatedBlocks = technicalBlocks.map { it.withBestValid() }
    val total = updatedBlocks
        .filter { it.movementType in setOf(CompetitionMovementType.SQUAT, CompetitionMovementType.BENCH, CompetitionMovementType.DEADLIFT) }
        .sumOf { it.bestValidWeightKg ?: 0.0 }
        .takeIf { it > 0.0 }
    val ipf = if (total != null && bodyweightKg != null && bodyweightKg > 0.0) {
        calculateIPFGLPoints(
            totalLifted = total,
            bodyWeight = bodyweightKg,
            gender = if (details.sexCategory.orEmpty().contains("fem", ignoreCase = true)) "female" else "male",
            equipment = if (details.equipment == CompetitionEquipment.EQUIPPED) IpfEquipment.EQUIPPED else IpfEquipment.CLASSIC,
        )
    } else {
        null
    }
    return copy(
        technicalBlocks = updatedBlocks,
        powerliftingDetails = details.copy(totalKg = total, ipfGlPoints = ipf),
    )
}

private fun CompetitionTechnicalBlock.withBestValid(): CompetitionTechnicalBlock {
    val best = attempts
        .filter { it.resultType == CompetitionAttemptResult.GOOD_LIFT }
        .mapNotNull { it.weightKg }
        .maxOrNull()
    return copy(bestValidWeightKg = best)
}

private fun defaultAttempt(number: Int) = CompetitionAttempt(
    id = UUID.randomUUID().toString(),
    attemptNumber = number,
)

private fun defaultTitleFor(sport: CompetitionTemplateType): String = when (sport) {
    CompetitionTemplateType.POWERLIFTING -> "Competencia de powerlifting"
    CompetitionTemplateType.BODYBUILDING -> "Competencia de físico"
    CompetitionTemplateType.WEIGHTLIFTING -> "Competencia de halterofilia"
    CompetitionTemplateType.RUNNING -> "Carrera"
    CompetitionTemplateType.STRONGMAN -> "Competencia de strongman"
    CompetitionTemplateType.CROSSFIT -> "Competencia de CrossFit"
    CompetitionTemplateType.MARTIAL_ARTS -> "Torneo"
    CompetitionTemplateType.CUSTOM -> "Competición"
}

private fun CompetitionRecordMode.label(): String = when (this) {
    CompetitionRecordMode.TECHNICAL -> "Técnico"
    CompetitionRecordMode.JOURNAL -> "Experiencia"
    CompetitionRecordMode.HYBRID -> "Completo"
}

private fun CompetitionTemplateType.label(): String = when (this) {
    CompetitionTemplateType.POWERLIFTING -> "Powerlifting"
    CompetitionTemplateType.BODYBUILDING -> "Culturismo / físico"
    CompetitionTemplateType.WEIGHTLIFTING -> "Halterofilia"
    CompetitionTemplateType.RUNNING -> "Running"
    CompetitionTemplateType.STRONGMAN -> "Strongman"
    CompetitionTemplateType.CROSSFIT -> "CrossFit"
    CompetitionTemplateType.MARTIAL_ARTS -> "Artes marciales"
    CompetitionTemplateType.CUSTOM -> "Personalizado"
}

private fun CompetitionRecordStatus.label(): String = when (this) {
    CompetitionRecordStatus.PLANNED -> "Planificada"
    CompetitionRecordStatus.COMPLETED -> "Completada"
    CompetitionRecordStatus.ARCHIVED -> "Archivada"
}

private fun CompetitionAttemptResult.label(): String = when (this) {
    CompetitionAttemptResult.GOOD_LIFT -> "Válido"
    CompetitionAttemptResult.NO_LIFT -> "Nulo"
    CompetitionAttemptResult.SKIPPED -> "Saltado"
    CompetitionAttemptResult.PENDING -> "Pendiente"
}

private fun CompetitionEquipment.label(): String = when (this) {
    CompetitionEquipment.RAW -> "Raw"
    CompetitionEquipment.SLEEVES -> "Sleeves"
    CompetitionEquipment.WRAPS -> "Wraps"
    CompetitionEquipment.EQUIPPED -> "Equipped"
    CompetitionEquipment.CLASSIC -> "Classic"
    CompetitionEquipment.CUSTOM -> "Otro"
}

private fun CompetitionMovementType.powerliftingOrder(): Int = when (this) {
    CompetitionMovementType.SQUAT -> 0
    CompetitionMovementType.BENCH -> 1
    CompetitionMovementType.DEADLIFT -> 2
    else -> 99
}

private fun String.cleanNullable(): String? = trim().takeIf { it.isNotBlank() }

private fun Double.toCleanString(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
