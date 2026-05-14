package com.example.kpkn.screens.wikilab

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.InferredSuggestions
import com.example.kpkn.domain.exercises.findBestMatches
import com.example.kpkn.domain.exercises.inferFromMatches
import kotlinx.coroutines.launch
import java.util.UUID

private data class EditableMuscle(
    var muscle: String,
    var role: MuscleRole,
    var contribution: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseCreatorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var force by remember { mutableStateOf("Otro") }
    var equipment by remember { mutableStateOf("Otro") }
    var type by remember { mutableStateOf("Accesorio") }
    var category by remember { mutableStateOf("Hipertrofia") }
    var bodyPart by remember { mutableStateOf("full") }
    var chain by remember { mutableStateOf("full") }
    var tier by remember { mutableStateOf("T2") }

    var isAxialLoaded by remember { mutableStateOf(false) }
    var technicalDifficulty by remember { mutableStateOf("3.0") }
    var setupTime by remember { mutableStateOf("60") }
    var averageRestSeconds by remember { mutableStateOf("90") }
    var coreInvolvement by remember { mutableStateOf("medium") }
    var bracingRecommended by remember { mutableStateOf(true) }
    var strapsRecommended by remember { mutableStateOf(false) }

    var functionalTransfer by remember { mutableStateOf("") }
    var sportsRelevanceCsv by remember { mutableStateOf("") }
    var recommendedMobilityText by remember { mutableStateOf("") }
    var setupCuesText by remember { mutableStateOf("") }
    var executionCuesText by remember { mutableStateOf("") }
    var imagesText by remember { mutableStateOf("") }
    var videosText by remember { mutableStateOf("") }

    val anatomical = remember { mutableStateListOf<AnatomicalConsideration>() }
    val mistakes = remember { mutableStateListOf<CommonMistake>() }
    val editableMuscles = remember { mutableStateListOf<EditableMuscle>() }
    var muscleSearch by remember { mutableStateOf("") }

    var detailsExpanded by remember { mutableStateOf(false) }
    var cuesExpanded by remember { mutableStateOf(false) }
    var mediaExpanded by remember { mutableStateOf(false) }

    // Smart matching — deterministic, no AI
    val suggestions = remember(name, equipment, force, category, type, bodyPart, chain, isAxialLoaded) {
        val database = EXERCISE_DATABASE
        val matches = if (name.isNotBlank() || equipment.isNotBlank() || force.isNotBlank()) {
            findBestMatches(
                database = database,
                name = name,
                equipment = equipment,
                force = force,
                category = category,
                type = type,
                bodyPart = bodyPart,
                chain = chain,
            )
        } else {
            emptyList()
        }
        if (matches.isNotEmpty() || (equipment.isNotBlank() && force.isNotBlank())) {
            inferFromMatches(
                matches = matches,
                name = name,
                equipment = equipment,
                force = force,
                category = category,
                isAxialLoaded = isAxialLoaded,
            )
        } else {
            null
        }
    }

    val muscleOptions = remember {
        EXERCISE_DATABASE
            .flatMap { it.involvedMuscles.map { muscle -> muscle.muscle } }
            .distinct()
            .sorted()
    }
    val filteredMuscles = remember(muscleSearch, muscleOptions) {
        val normalized = muscleSearch.trim()
        if (normalized.isBlank()) muscleOptions.take(12)
        else muscleOptions.filter { it.contains(normalized, ignoreCase = true) }.take(12)
    }

    val matchCount = suggestions?.matchCount ?: 0

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Crear ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Name / Alias / Description ──
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), singleLine = true, maxLines = 2)

            HorizontalDivider()

            // ── Equipment ──
            Text("Equipo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Barra", "Mancuerna", "Máquina", "Polea", "Peso Corporal", "Kettlebell", "Otro").forEach { eq ->
                    AssistChip(
                        onClick = { equipment = eq },
                        label = { Text(eq, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                    )
                }
            }
            if (equipment == "Otro") {
                OutlinedTextField(value = equipment, onValueChange = { equipment = it }, label = { Text("Especificar equipo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            // ── Movement pattern ──
            Text("Patrón de movimiento", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Empuje", "Tirón", "Sentadilla", "Bisagra", "Anti-Extensión", "Flexión", "Otro").forEach { f ->
                    AssistChip(
                        onClick = { force = f },
                        label = { Text(f, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp) },
                    )
                }
            }
            if (force == "Otro") {
                OutlinedTextField(value = force, onValueChange = { force = it }, label = { Text("Especificar patrón") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            // ── Category + Type in same row ──
            Text("Categoría", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Fuerza", "Potencia", "Hipertrofia", "Isometría").forEach { cat ->
                    AssistChip(
                        onClick = { category = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                    )
                }
            }
            Text("Tipo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Básico", "Variante", "Accesorio", "Aislamiento").forEach { t ->
                    AssistChip(
                        onClick = { type = t },
                        label = { Text(t, fontSize = 12.sp) },
                    )
                }
            }

            HorizontalDivider()

            // ── Suggestions card ──
            if (suggestions != null && matchCount > 0) {
                SuggestionsCard(
                    suggestions = suggestions,
                    onApply = {
                        if (editableMuscles.isEmpty()) {
                            editableMuscles.addAll(suggestions.suggestedMuscles.take(5).map {
                                EditableMuscle(it.muscle, it.role, (it.volumeContribution ?: 1.0).toString())
                            })
                        }
                        averageRestSeconds = suggestions.suggestedRestSeconds.toString()
                        tier = suggestions.suggestedTier
                    },
                    onDismiss = {
                        editableMuscles.clear()
                    },
                )
                HorizontalDivider()
            }

            // ── AUGE display ──
            val displayEfc = suggestions?.efc ?: 2.5
            val displayCnc = suggestions?.cnc ?: 2.5
            val displaySsc = suggestions?.ssc ?: 0.3
            Text(
                "EFC ${"%.1f".format(displayEfc)} · CNC ${"%.1f".format(displayCnc)} · SSC ${"%.1f".format(displaySsc)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (matchCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Carga axial", style = MaterialTheme.typography.labelMedium)
                Switch(checked = isAxialLoaded, onCheckedChange = { isAxialLoaded = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = technicalDifficulty, onValueChange = { technicalDifficulty = it }, label = { Text("Dif.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = averageRestSeconds, onValueChange = { averageRestSeconds = it }, label = { Text("Desc (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = setupTime, onValueChange = { setupTime = it }, label = { Text("Setup (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            }

            HorizontalDivider()

            // ── Muscles ──
            Text("Músculos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(
                "Elige desde la lista interna. Las predicciones son sugerencias y no se guardan si no las aceptas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = muscleSearch,
                onValueChange = { muscleSearch = it },
                label = { Text("Buscar músculo") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filteredMuscles.forEach { muscle ->
                    FilterChip(
                        selected = editableMuscles.any { it.muscle == muscle },
                        onClick = {
                            if (editableMuscles.none { it.muscle == muscle }) {
                                editableMuscles.add(EditableMuscle(muscle, MuscleRole.PRIMARY, "1.0"))
                            }
                        },
                        label = { Text(muscle, fontSize = 12.sp) },
                    )
                }
            }

            if (editableMuscles.isEmpty() && suggestions != null && suggestions.suggestedMuscles.isNotEmpty()) {
                OutlinedButton(onClick = {
                    editableMuscles.addAll(suggestions.suggestedMuscles.take(5).map {
                        EditableMuscle(it.muscle, it.role, (it.volumeContribution ?: 1.0).toString())
                    })
                }) {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aplicar (${suggestions.suggestedMuscles.size})", fontSize = 12.sp)
                }
            }

            editableMuscles.forEachIndexed { idx, row ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), shape = MaterialTheme.shapes.small) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(row.muscle, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { editableMuscles.removeAt(idx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            OutlinedTextField(
                                value = row.role.name.lowercase().take(4),
                                onValueChange = { input ->
                                    val r = when (input.trim().lowercase().take(3)) {
                                        "pri", "pri" -> MuscleRole.PRIMARY
                                        "sec", "sec" -> MuscleRole.SECONDARY
                                        "sta", "est" -> MuscleRole.STABILIZER
                                        else -> MuscleRole.NEUTRALIZER
                                    }
                                    editableMuscles[idx] = row.copy(role = r)
                                },
                                label = { Text("Rol", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = row.contribution,
                                onValueChange = { editableMuscles[idx] = row.copy(contribution = it) },
                                label = { Text("Volumen", fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (editableMuscles.size < 6) {
                OutlinedButton(onClick = { filteredMuscles.firstOrNull()?.let { editableMuscles.add(EditableMuscle(it, MuscleRole.PRIMARY, "1.0")) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Text("  Músculo", fontSize = 12.sp)
                }
            }
            if (editableMuscles.none { it.role == MuscleRole.PRIMARY }) {
                Text("Advertencia: todavía no hay músculos principales.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            HorizontalDivider()

            // ── Collapsible: Technical details ──
            SectionHeader("Detalles técnicos", expanded = detailsExpanded, onToggle = { detailsExpanded = !detailsExpanded })
            if (detailsExpanded) {
                OutlinedTextField(value = functionalTransfer, onValueChange = { functionalTransfer = it }, label = { Text("Transferencia funcional") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = sportsRelevanceCsv, onValueChange = { sportsRelevanceCsv = it }, label = { Text("Deportes (csv)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = recommendedMobilityText, onValueChange = { recommendedMobilityText = it }, label = { Text("Movilidad recomendada") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = coreInvolvement, onValueChange = { coreInvolvement = it }, label = { Text("Core") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bracing", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = bracingRecommended, onCheckedChange = { bracingRecommended = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Straps", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = strapsRecommended, onCheckedChange = { strapsRecommended = it })
                    }
                }
            }

            // ── Collapsible: Cues ──
            SectionHeader("Cues", expanded = cuesExpanded, onToggle = { cuesExpanded = !cuesExpanded })
            if (cuesExpanded) {
                OutlinedTextField(value = setupCuesText, onValueChange = { setupCuesText = it }, label = { Text("Setup (una por línea)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                OutlinedTextField(value = executionCuesText, onValueChange = { executionCuesText = it }, label = { Text("Ejecución (una por línea)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            }

            // ── Collapsible: Media ──
            SectionHeader("Media", expanded = mediaExpanded, onToggle = { mediaExpanded = !mediaExpanded })
            if (mediaExpanded) {
                OutlinedTextField(value = imagesText, onValueChange = { imagesText = it }, label = { Text("URLs de imágenes") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                OutlinedTextField(value = videosText, onValueChange = { videosText = it }, label = { Text("URLs de videos") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }

            HorizontalDivider()

            // ── Save button ──
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val musclePayload = editableMuscles
                        .mapNotNull { row ->
                            val muscleName = row.muscle.trim()
                            if (muscleName.isBlank()) return@mapNotNull null
                            InvolvedMuscle(muscleName, row.role, row.contribution.toDoubleOrNull() ?: 1.0)
                        }
                        .ifEmpty {
                            emptyList()
                        }

                    val exercise = ExerciseMuscleInfo(
                        id = "custom_${UUID.randomUUID()}",
                        name = name.trim(),
                        alias = alias.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        involvedMuscles = musclePayload,
                        equipment = equipment.ifBlank { null },
                        category = category.ifBlank { null },
                        type = type.ifBlank { null },
                        force = force.ifBlank { null },
                        chain = chain.ifBlank { suggestions?.suggestedChain ?: "full" },
                        bodyPart = bodyPart.ifBlank { suggestions?.suggestedBodyPart ?: "full" },
                        tier = tier.ifBlank { null },
                        isCustom = true,
                        efc = suggestions?.efc ?: 2.5,
                        cnc = suggestions?.cnc ?: 2.5,
                        ssc = suggestions?.ssc ?: 0.3,
                        technicalDifficulty = technicalDifficulty.toDoubleOrNull(),
                        coreInvolvement = coreInvolvement.ifBlank { null },
                        bracingRecommended = bracingRecommended,
                        strapsRecommended = strapsRecommended,
                        setupCues = setupCuesText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        executionCues = executionCuesText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        anatomicalConsiderations = anatomical.toList().ifEmpty { null },
                        commonMistakes = mistakes.toList().ifEmpty { null },
                        recommendedMobility = recommendedMobilityText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        functionalTransfer = functionalTransfer.ifBlank { null },
                        sportsRelevance = sportsRelevanceCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        images = imagesText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        videos = videosText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        setupTime = setupTime.toIntOrNull(),
                        averageRestSeconds = averageRestSeconds.toIntOrNull(),
                    )
                    CustomExerciseRepository.upsert(exercise)
                    onSaved(exercise.id)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Guardar ejercicio", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = {
                    val body = buildString {
                        appendLine("Sugerencia de ejercicio")
                        appendLine("Nombre: $name")
                        appendLine("Equipo: $equipment")
                        appendLine("Patrón: $force")
                        appendLine("Categoría: $category")
                        appendLine("EFC/CNC/SSC: ${"%.1f".format(displayEfc)} / ${"%.1f".format(displayCnc)} / ${"%.1f".format(displaySsc)}")
                        appendLine("Músculos: ${editableMuscles.joinToString { "${it.muscle}:${it.role.name}" }}")
                    }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("equipo@kpkn.fit"))
                        putExtra(Intent.EXTRA_SUBJECT, "Sugerencia de ejercicio KPKN")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Sugerir al equipo"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Text("  Sugerir al equipo")
            }

            Spacer(Modifier.padding(bottom = 32.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Contraer" else "Expandir",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SuggestionsCard(
    suggestions: InferredSuggestions,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val matchText = suggestions.topMatches.take(3).joinToString(", ") { it.exercise.name }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${suggestions.matchCount} similares",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                matchText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "EFC ${"%.1f".format(suggestions.efc)} · CNC ${"%.1f".format(suggestions.cnc)} · ${suggestions.suggestedMuscles.size} músc · ${suggestions.suggestedRestSeconds}s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply) {
                    Text("Aceptar sugerencias")
                }
                TextButton(onClick = onDismiss) {
                    Text("Ignorar")
                }
            }
        }
    }
}
