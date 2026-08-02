package com.example.kpkn.screens.wikilab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.models.AnatomicalConsideration
import com.example.kpkn.data.models.CommonMistake
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.domain.exercises.ExerciseMatchResult
import com.example.kpkn.domain.exercises.InferredSuggestions
import com.example.kpkn.domain.exercises.findBestMatches
import com.example.kpkn.domain.exercises.inferFromMatches
import com.example.kpkn.ui.components.KpknExposedDropdownMenu
import java.util.UUID

private data class EditableMuscle(
    var muscle: String,
    var role: MuscleRole,
    var contribution: Double,
)

private fun calculateSimpleCreatorSearchScore(info: ExerciseMuscleInfo, query: String): Int {
    val q = query.trim().lowercase()
    if (q.isBlank()) return 0
    var score = 0
    val name = info.name.lowercase()
    val alias = info.alias?.lowercase().orEmpty()
    if (name == q || alias == q) score += 100
    if (name.startsWith(q)) score += 70
    if (name.contains(q)) score += 45
    if (alias.contains(q)) score += 35
    if (info.involvedMuscles.any { it.muscle.contains(q, ignoreCase = true) }) score += 20
    if (info.equipment?.contains(q, ignoreCase = true) == true) score += 15
    return score
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseCreatorScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Crear ejercicio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        }
    ) { padding ->
        CustomExerciseCreatorContent(
            onBack = onBack,
            onSaved = onSaved,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun CustomExerciseCreatorContent(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var force by remember { mutableStateOf("") }
    var customForce by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var customEquipment by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Accesorio") }
    var category by remember { mutableStateOf("Hipertrofia") }
    var bodyPart by remember { mutableStateOf("full") }
    var chain by remember { mutableStateOf("full") }
    var tier by remember { mutableStateOf("T2") }

    var isAxialLoaded by remember { mutableStateOf(false) }
    var technicalDifficulty by remember { mutableStateOf("") }
    var setupTime by remember { mutableStateOf("") }
    var averageRestSeconds by remember { mutableStateOf("") }
    var coreInvolvement by remember { mutableStateOf("medium") }
    var bracingRecommended by remember { mutableStateOf(true) }
    var strapsRecommended by remember { mutableStateOf(false) }

    var functionalTransfer by remember { mutableStateOf("") }
    var sportsRelevanceCsv by remember { mutableStateOf("") }
    var recommendedMobilityText by remember { mutableStateOf("") }

    val anatomical = remember { mutableStateListOf<AnatomicalConsideration>() }
    val mistakes = remember { mutableStateListOf<CommonMistake>() }
    val editableMuscles = remember { mutableStateListOf<EditableMuscle>() }
    var muscleSearch by remember { mutableStateOf("") }

    var detailsExpanded by remember { mutableStateOf(false) }
    var baseSearch by remember { mutableStateOf("") }
    var selectedBaseExercise by remember { mutableStateOf<ExerciseMuscleInfo?>(null) }

    // Smart matching — deterministic, no AI
    val hasCreationSignal = name.trim().length >= 3 ||
        selectedBaseExercise != null ||
        equipment.isNotBlank() ||
        force.isNotBlank()
    val suggestions = remember(name, equipment, force, category, type, bodyPart, chain, isAxialLoaded, selectedBaseExercise) {
        val database = exerciseCatalogSnapshot()
        val matches = when {
            selectedBaseExercise != null -> listOf(ExerciseMatchResult(selectedBaseExercise!!, 1.0))
            hasCreationSignal -> {
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
            }
            else -> emptyList()
        }
        if (hasCreationSignal && (matches.isNotEmpty() || equipment.isNotBlank() || force.isNotBlank())) {
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

    val baseExerciseOptions = remember(baseSearch) {
        val query = baseSearch.trim()
        if (query.isBlank()) {
            emptyList()
        } else {
            exerciseCatalogSnapshot()
                .map { it to calculateSimpleCreatorSearchScore(it, query) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(6)
                .map { it.first }
        }
    }

    fun contributionForRole(role: MuscleRole): Double = when (role) {
        MuscleRole.PRIMARY -> 1.0
        MuscleRole.SECONDARY -> 0.5
        MuscleRole.STABILIZER -> 0.4
        MuscleRole.NEUTRALIZER -> 0.4
    }

    fun applySuggestions(s: InferredSuggestions) {
        if (editableMuscles.isEmpty()) {
            editableMuscles.addAll(s.suggestedMuscles.take(5).map {
                val role = when (it.role) {
                    MuscleRole.NEUTRALIZER -> MuscleRole.STABILIZER
                    else -> it.role
                }
                EditableMuscle(it.muscle, role, contributionForRole(role))
            })
        }
        averageRestSeconds = s.suggestedRestSeconds.toString()
        tier = s.suggestedTier
        bodyPart = s.suggestedBodyPart
        chain = s.suggestedChain
    }

    val muscleOptions = remember {
        exerciseCatalogSnapshot()
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
    val canShowSimilarSuggestions = name.trim().length >= 2 && matchCount > 0
    val minimalistFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    )

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            // ── Name / Alias / Description ──
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = minimalistFieldColors)
            OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = minimalistFieldColors)
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), singleLine = true, maxLines = 2, colors = minimalistFieldColors)

            HorizontalDivider()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("¿A qué otro ejercicio se parece?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(
                        selectedBaseExercise?.name ?: "Detección automática",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = baseSearch,
                        onValueChange = { baseSearch = it },
                        label = { Text("Buscar ejercicio base") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = minimalistFieldColors,
                    )
                    if (selectedBaseExercise != null) {
                        TextButton(
                            onClick = {
                                selectedBaseExercise = null
                                baseSearch = ""
                            },
                        ) { Text("Volver a detección automática") }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        baseExerciseOptions.forEach { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                    selectedBaseExercise = option
                                    baseSearch = option.name
                                    if (equipment.isBlank()) equipment = option.equipment ?: equipment
                                    if (force.isBlank()) force = option.force ?: force
                                    category = option.category ?: category
                                    type = option.type ?: type
                                    },
                                color = if (selectedBaseExercise?.id == option.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    option.name,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }

            // ── Equipment ──
            CreatorDropdown(
                label = "Equipo",
                value = equipment,
                options = listOf("Barra", "Mancuerna", "Máquina", "Polea", "Peso Corporal", "Kettlebell", "Otro"),
                onValueChange = { equipment = it },
            )
            if (equipment == "Otro") {
                OutlinedTextField(value = customEquipment, onValueChange = { customEquipment = it }, label = { Text("Especificar equipo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            // ── Movement pattern ──
            CreatorDropdown(
                label = "Patrón de movimiento",
                value = force,
                options = listOf("Empuje", "Tirón", "Sentadilla", "Bisagra", "Anti-Extensión", "Flexión", "Otro"),
                onValueChange = { force = it },
            )
            if (force == "Otro") {
                OutlinedTextField(value = customForce, onValueChange = { customForce = it }, label = { Text("Especificar patrón") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            // ── Category + Type in same row ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CreatorDropdown(
                    label = "Categoría",
                    value = category,
                    options = listOf("Fuerza", "Potencia", "Hipertrofia", "Isometría"),
                    onValueChange = { category = it },
                    modifier = Modifier.weight(1f),
                )
                CreatorDropdown(
                    label = "Tipo",
                    value = type,
                    options = listOf("Básico", "Variante", "Accesorio", "Aislamiento"),
                    onValueChange = { type = it },
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            // ── Suggestions card ──
            if (suggestions != null && canShowSimilarSuggestions) {
                SuggestionsCard(
                    suggestions = suggestions,
                    onApply = { applySuggestions(suggestions) },
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
                OutlinedTextField(value = technicalDifficulty, onValueChange = { technicalDifficulty = it }, label = { Text("Dificultad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = averageRestSeconds, onValueChange = { averageRestSeconds = it }, label = { Text("Descanso (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = setupTime, onValueChange = { setupTime = it }, label = { Text("Preparación (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                filteredMuscles.forEach { muscle ->
                    val selected = editableMuscles.any { it.muscle == muscle }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                            if (editableMuscles.none { it.muscle == muscle }) {
                                editableMuscles.add(EditableMuscle(muscle, MuscleRole.PRIMARY, 1.0))
                            }
                            },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(muscle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (selected) {
                                Text("Agregado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (editableMuscles.isEmpty() && suggestions != null && suggestions.suggestedMuscles.isNotEmpty()) {
                OutlinedButton(onClick = {
                    applySuggestions(suggestions)
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
                        CreatorDropdown(
                            label = "Rol muscular",
                            value = roleLabel(row.role),
                            options = listOf("Principal", "Secundario", "Estabilizador"),
                            onValueChange = { selectedLabel ->
                                val role = roleFromLabel(selectedLabel)
                                editableMuscles[idx] = row.copy(
                                    role = role,
                                    contribution = contributionForRole(role),
                                )
                            },
                        )
                    }
                }
            }
            if (editableMuscles.size < 6) {
                OutlinedButton(onClick = { filteredMuscles.firstOrNull()?.let { editableMuscles.add(EditableMuscle(it, MuscleRole.PRIMARY, 1.0)) } }, modifier = Modifier.fillMaxWidth()) {
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

            HorizontalDivider()

            // ── Save button ──
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val musclePayload = editableMuscles
                        .mapNotNull { row ->
                            val muscleName = row.muscle.trim()
                            if (muscleName.isBlank()) return@mapNotNull null
                            InvolvedMuscle(muscleName, row.role, row.contribution)
                        }
                        .ifEmpty {
                            emptyList()
                        }
                    val equipmentPayload = when (equipment) {
                        "Otro" -> customEquipment.trim()
                        else -> equipment.trim()
                    }
                    val forcePayload = when (force) {
                        "Otro" -> customForce.trim()
                        else -> force.trim()
                    }

                    val exercise = ExerciseMuscleInfo(
                        id = "custom:${UUID.randomUUID()}",
                        name = name.trim(),
                        alias = alias.trim().ifBlank { null },
                        description = description.trim().ifBlank { null },
                        involvedMuscles = musclePayload,
                        equipment = equipmentPayload.ifBlank { null },
                        category = category.ifBlank { null },
                        type = type.ifBlank { null },
                        force = forcePayload.ifBlank { null },
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
                        setupCues = suggestions?.setupCues?.takeIf { it.isNotEmpty() },
                        executionCues = suggestions?.executionCues?.takeIf { it.isNotEmpty() },
                        anatomicalConsiderations = anatomical.toList().ifEmpty { null },
                        commonMistakes = mistakes.toList().ifEmpty { null },
                        recommendedMobility = recommendedMobilityText.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        functionalTransfer = functionalTransfer.ifBlank { null },
                        sportsRelevance = sportsRelevanceCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { null },
                        images = null,
                        videos = null,
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

            Spacer(Modifier.padding(bottom = 32.dp))
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorDropdown(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
        )
        KpknExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun roleLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Principal"
    MuscleRole.SECONDARY -> "Secundario"
    MuscleRole.STABILIZER,
    MuscleRole.NEUTRALIZER -> "Estabilizador"
}

private fun roleFromLabel(label: String): MuscleRole = when (label) {
    "Secundario" -> MuscleRole.SECONDARY
    "Estabilizador" -> MuscleRole.STABILIZER
    else -> MuscleRole.PRIMARY
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
