package com.example.kpkn.screens.wikilab

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.CustomExerciseRepository
import com.example.kpkn.screens.wikilab.components.ExerciseFatigueScenarios

private fun muscleColor(name: String): Color = wikilabMuscleColor(name)

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
    val primaryMuscles = canonicalInvolved.filter { it.role == MuscleRole.PRIMARY }
    val secondaryMuscles = canonicalInvolved.filter { it.role == MuscleRole.SECONDARY }
    val stabilizerMuscles = canonicalInvolved.filter { it.role == MuscleRole.STABILIZER }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val customExercises by CustomExerciseRepository.customExercises.collectAsState()
    val catalog = remember(customExercises) {
        (EXERCISE_DATABASE + customExercises)
            .associateBy { it.id.lowercase() }
            .values
            .toList()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(exercise.name, fontWeight = FontWeight.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            exercise.category?.let { cat ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        cat,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            exercise.type?.let { t ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        t,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Header ──────────────────────────────────────────────────
            item {
                HeaderSection(exercise)
            }

            // ─── Fatiga AUGE por escenario ──────────────────────────────
            if (exercise.involvedMuscles.isNotEmpty()) {
                item {
                    ExerciseFatigueScenarios(exercise = exercise)
                }
            }

            // ─── Detalles técnicos ──────────────────────────────────────
            item {
                ExerciseTechnicalDetails(exercise)
            }

            // ─── Ejercicios similares ───────────────────────────────────
            item {
                ExerciseSimilarThreeBand(
                    info = exercise,
                    catalog = catalog,
                    onOpenExercise = { id -> onNavigateToExercise?.invoke(id) },
                )
            }

            // ─── Musculos (navegación) ──────────────────────────────────
            if (primaryMuscles.isNotEmpty()) {
                item {
                    MuscleSection("MÚSCULOS PRIMARIOS", primaryMuscles, onNavigateToMuscle)
                }
            }
            if (secondaryMuscles.isNotEmpty()) {
                item {
                    MuscleSection("MÚSCULOS SECUNDARIOS", secondaryMuscles, onNavigateToMuscle)
                }
            }
            if (stabilizerMuscles.isNotEmpty()) {
                item {
                    MuscleSection("ESTABILIZADORES", stabilizerMuscles, onNavigateToMuscle)
                }
            }

            // ─── Consideraciones anatomicas (compacto) ─────────────────
            if (exercise.anatomicalConsiderations != null) {
                item {
                    AnatomicalSection(exercise.anatomicalConsiderations)
                }
            }

            // ─── Errores comunes (compacto) ─────────────────────────────
            if (exercise.commonMistakes != null) {
                item {
                    MistakesSection(exercise.commonMistakes)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── HEADER ────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(exercise: ExerciseMuscleInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Category/type chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            exercise.category?.let { cat ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        cat,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            exercise.type?.let { t ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        t,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            exercise.tier?.let { tier ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (tier) {
                        "T1" -> Color(0xFFE53935).copy(alpha = 0.12f)
                        "T2" -> Color(0xFFFF8F00).copy(alpha = 0.12f)
                        else -> Color(0xFF43A047).copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        tier,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when (tier) {
                            "T1" -> Color(0xFFE53935)
                            "T2" -> Color(0xFFFF8F00)
                            else -> Color(0xFF43A047)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Title
        Text(
            exercise.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        // Alias
        exercise.alias?.let { alias ->
            Text(
                alias,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Equipment + Force + Chain
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.equipment?.let { eq ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        eq,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            exercise.force?.let { f ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                ) {
                    Text(
                        f,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32),
                    )
                }
            }
        }

        // Description
        exercise.description?.let { desc ->
            Spacer(Modifier.height(8.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

// ─── MUSCLE INVOLVEMENT ───────────────────────────────────────────────────

@Composable
private fun MuscleSection(
    title: String,
    muscles: List<InvolvedMuscle>,
    onNavigateToMuscle: ((String) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "Aporte al volumen = cuánto cuenta este músculo dentro de tus series efectivas.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        muscles.forEach { m ->
            val color = muscleColor(m.muscle)
            val dotSize = when (m.role) {
                MuscleRole.PRIMARY -> 12
                MuscleRole.SECONDARY -> 10
                else -> 8
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onNavigateToMuscle != null && canonicalWikiLabMuscleId(m.muscle, m.emphasis) != null) {
                            Modifier.clickable {
                                canonicalWikiLabMuscleId(m.muscle, m.emphasis)?.let(onNavigateToMuscle)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 4.dp),
            ) {
                Surface(
                    modifier = Modifier.size(dotSize.dp),
                    shape = RoundedCornerShape(50),
                    color = color,
                ) {}

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        m.muscle,
                        style = when (m.role) {
                            MuscleRole.PRIMARY -> MaterialTheme.typography.titleSmall
                            MuscleRole.SECONDARY -> MaterialTheme.typography.bodyMedium
                            else -> MaterialTheme.typography.bodySmall
                        },
                        fontWeight = if (m.role == MuscleRole.PRIMARY) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                    )
                    m.emphasis?.let { emp ->
                        Text(
                            "Porción: $emp",
                            style = MaterialTheme.typography.labelSmall,
                            color = color.copy(alpha = 0.7f),
                        )
                    }
                }

                m.volumeContribution?.let { act ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color.copy(alpha = 0.1f),
                    ) {
                        Text(
                            "Aporte al volumen ${"%.2f".format(act)}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

// ─── ANATOMICAL CONSIDERATIONS ─────────────────────────────────────────────

@Composable
private fun AnatomicalSection(considerations: List<AnatomicalConsideration>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "CONSIDERACIONES ANATÓMICAS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        considerations.forEach { c ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    c.trait,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    c.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
            if (c != considerations.last()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

// ─── COMMON MISTAKES ──────────────────────────────────────────────────────

@Composable
private fun MistakesSection(mistakes: List<CommonMistake>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "ERRORES COMUNES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        mistakes.forEachIndexed { i, m ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFE53935),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        m.mistake,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53935),
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF43A047),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        m.correction,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF43A047),
                    )
                }
            }
            if (i < mistakes.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}
