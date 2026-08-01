package com.example.kpkn.screens.wikilab

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.screens.wikilab.components.CaupolicanSquatInteractiveViewer
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios
import com.example.kpkn.screens.wikilab.components.SquatVariant
import com.example.kpkn.domain.exercises.adaptedExerciseDescription

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseMuscleInfo,
    onNavigateToMuscle: ((String) -> Unit)? = null,
    onNavigateToJoint: ((String) -> Unit)? = null,
    onNavigateToExercise: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val canonicalInvolved = remember(exercise.id, exercise.involvedMuscles) {
        collapseInvolvedMusclesToCanonical(exercise.involvedMuscles)
    }
    val fatigue = remember(exercise.id) { com.example.kpkn.domain.exercises.calculateFriendlyFatigue(exercise) }
    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val catalog = remember(customExercises) {
        (EXERCISE_DATABASE + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }

    val isSquatExercise = remember(exercise.id, exercise.name) {
        val id = exercise.id.lowercase()
        val name = exercise.name.lowercase()
        id.contains("high_bar") || id.contains("low_bar") ||
        name.contains("barra alta") || name.contains("barra baja") ||
        name.contains("high bar") || name.contains("low bar")
    }
    var selectedTechnicalOption by remember(exercise.id) { mutableStateOf<Pair<TechnicalAspect, AspectOption>?>(null) }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        exercise.name,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Title & Description ──────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    )
                    
                    exercise.alias?.let { alias ->
                        Text(
                            text = "También conocido como: $alias",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.5f),
                            ),
                        )
                    }
                    
                    exercise.description?.let { desc ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Serif,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 22.sp,
                            ),
                        )
                    }
                }
            }

            if (!exercise.technicalAspects.isNullOrEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Variantes técnicas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                exercise.technicalAspects.orEmpty().flatMap { aspect ->
                                    aspect.options.map { aspect to it }
                                },
                            ) { (aspect, option) ->
                                AssistChip(
                                    onClick = { selectedTechnicalOption = aspect to option },
                                    label = { Text(option.name) },
                                )
                            }
                        }
                        selectedTechnicalOption?.let { (aspect, option) ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            ) {
                                Text(
                                    adaptedExerciseDescription(exercise, mapOf(aspect.id to option.id)),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.86f),
                                )
                            }
                        }
                    }
                }
            }

            // ─── Caupolicán Animated Exercise Movement Viewer ──────────────
            if (isSquatExercise) {
                item {
                    val defaultVariant = if (exercise.id.lowercase().contains("low") || exercise.name.lowercase().contains("baja")) {
                        SquatVariant.LOW_BAR
                    } else {
                        SquatVariant.HIGH_BAR
                    }
                    CaupolicanSquatInteractiveViewer(
                        initialVariant = defaultVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // ─── Infobox (Wikipedia Table) ────────────────────────────────
            item {
                WikiInfobox(exercise, fatigue.overall.toDouble())
            }

            // ─── Músculos Involucrados (Wikipedia inline list) ────────────
            if (canonicalInvolved.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Músculos Involucrados")
                        Spacer(Modifier.height(8.dp))
                        WikiMuscleInvolvement(
                            muscles = canonicalInvolved,
                            onNavigateToMuscle = onNavigateToMuscle
                        )
                    }
                }
            }

            // ─── Anatomical Considerations ────────────────────────────────
            if (exercise.anatomicalConsiderations != null) {
                item {
                    Column {
                        WikiSectionHeader("Consideraciones Anatómicas")
                        Spacer(Modifier.height(8.dp))
                        WikiAnatomicalSection(exercise.anatomicalConsiderations)
                    }
                }
            }

            // ─── Common Mistakes ──────────────────────────────────────────
            if (exercise.commonMistakes != null) {
                item {
                    Column {
                        WikiSectionHeader("Errores Comunes")
                        Spacer(Modifier.height(8.dp))
                        WikiMistakesSection(exercise.commonMistakes)
                    }
                }
            }

            // ─── Fatigue Scenarios ────────────────────────────────────────
            if (exercise.involvedMuscles.isNotEmpty()) {
                item {
                    Column {
                        WikiSectionHeader("Estimación de Drenaje y Fatiga")
                        Spacer(Modifier.height(8.dp))
                        ExerciseFatigueScenarios(exercise = exercise)
                    }
                }
            }

            // ─── Ejercicios similares ─────────────────────────────────────
            item {
                Column {
                    WikiSectionHeader("Ejercicios Similares y Alternativas")
                    Spacer(Modifier.height(8.dp))
                    ExerciseSimilarThreeBand(
                        info = exercise,
                        catalog = catalog,
                        onOpenExercise = { id -> onNavigateToExercise?.invoke(id) },
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── WIKIPEDIA UI COMPONENTS ──────────────────────────────────────────────

@Composable
private fun WikiSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Color(0xFF2C2C2C), thickness = 1.dp)
    }
}

@Composable
private fun WikiInfobox(exercise: ExerciseMuscleInfo, fatigueScore: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
        border = BorderStroke(1.dp, Color(0xFF2C2C2C)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Ficha Técnica Enciclopédica",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            HorizontalDivider(color = Color(0xFF2C2C2C))
            
            InfoboxRow("Categoría", exercise.category ?: "N/A")
            InfoboxRow("Equipamiento", exercise.equipment ?: "N/A")
            InfoboxRow("Mecánica", exercise.type ?: "N/A")
            InfoboxRow("Nivel (Tier)", exercise.tier ?: "N/A")
            exercise.force?.let { InfoboxRow("Fuerza", it) }
            InfoboxRow("Fatiga General", "${"%.1f".format(fatigueScore)} / 5.0")
        }
    }
}

@Composable
private fun InfoboxRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
private fun WikiMuscleInvolvement(
    muscles: List<InvolvedMuscle>,
    onNavigateToMuscle: ((String) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        muscles.forEach { m ->
            val color = wikilabMuscleColor(m.muscle)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = RoundedCornerShape(50),
                    color = color
                ) {}
                Spacer(Modifier.width(10.dp))
                
                val canonicalId = canonicalWikiLabMuscleId(m.muscle, m.emphasis)
                val isClickable = onNavigateToMuscle != null && canonicalId != null
                
                Text(
                    text = m.muscle + (m.emphasis?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        color = if (isClickable) Color(0xFF29B6F6) else Color.White
                    ),
                    fontWeight = FontWeight.Bold,
                    modifier = if (isClickable) Modifier.clickable { canonicalId?.let(onNavigateToMuscle!!) } else Modifier
                )
                
                Spacer(Modifier.width(8.dp))
                val roleText = when (m.role) {
                    MuscleRole.PRIMARY -> "Primario"
                    MuscleRole.SECONDARY -> "Secundario"
                    else -> "Estabilizador"
                }
                Text(
                    text = "· $roleText",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.5f)
                )
                
                m.volumeContribution?.let { act ->
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Aporte: +${"%.2f".format(act)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WikiAnatomicalSection(considerations: List<AnatomicalConsideration>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        considerations.forEach { c ->
            Column {
                Text(
                    text = "• " + c.trait,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = c.advice,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun WikiMistakesSection(mistakes: List<CommonMistake>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        mistakes.forEach { m ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFE53935)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = m.mistake,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF43A047)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = m.correction,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Serif),
                        color = Color(0xFF43A047)
                    )
                }
            }
        }
    }
}
