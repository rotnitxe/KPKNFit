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
import com.example.kpkn.domain.exercises.ExerciseMatchResult
import com.example.kpkn.domain.exercises.InferredSuggestions
import com.example.kpkn.domain.exercises.findBestMatches
import com.example.kpkn.domain.exercises.inferFromMatches
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseCreatorContent(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
        equipment != "Otro" ||
        force != "Otro"
    val suggestions = remember(name, equipment, force, category, type, bodyPart, chain, isAxialLoaded, selectedBaseExercise) {
        val database = EXERCISE_DATABASE
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
        if (hasCreationSignal && (matches.isNotEmpty() || equipment != "Otro" || force != "Otro")) {
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
            EXERCISE_DATABASE
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

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
            // ── Name / Alias / Description ──
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("Alias") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), singleLine = true, maxLines = 2)

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
                    )
                    if (selectedBaseExercise != null) {
                        TextButton(
                            onClick = {
                                selectedBaseExercise = null
                                baseSearch = ""
                            },
                        ) { Text("Volver a detección automática") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        baseExerciseOptions.forEach { option ->
                            FilterChip(
                                selected = selectedBaseExercise?.id == option.id,
                                onClick = {
                                    selectedBaseExercise = option
                                    baseSearch = option.name
                                    if (equipment == "Otro") equipment = option.equipment ?: equipment
                                    if (force == "Otro") force = option.force ?: force
                                    category = option.category ?: category
                                    type = option.type ?: type
                                },
                                label = { Text(option.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
            }

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
                                editableMuscles.add(EditableMuscle(muscle, MuscleRole.PRIMARY, 1.0))
                            }
                        },
                        label = { Text(muscle, fontSize = 12.sp) },
                    )
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                MuscleRole.PRIMARY to "Principal",
                                MuscleRole.SECONDARY to "Secundario",
                                MuscleRole.STABILIZER to "Estabilizador",
                            ).forEach { (role, label) ->
                                FilterChip(
                                    selected = row.role == role,
                                    onClick = {
                                        editableMuscles[idx] = row.copy(
                                            role = role,
                                            contribution = contributionForRole(role),
                                        )
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                )
                            }
                        }
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
