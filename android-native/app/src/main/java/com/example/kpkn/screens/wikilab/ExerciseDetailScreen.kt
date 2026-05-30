package com.example.kpkn.screens.wikilab

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
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
    val fatigue = remember(exercise.id) { com.example.kpkn.domain.exercises.calculateFriendlyFatigue(exercise) }
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
                    Text(
                        exercise.name,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

            // ─── Carrusel de Chips Directo ──────────────────────────────
            item {
                ExerciseMinimalistChipsCarousel(
                    exercise = exercise,
                    fatigueScore = fatigue.overall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ─── Ejercicios similares ───────────────────────────────────
            item {
                ExerciseSimilarThreeBand(
                    info = exercise,
                    catalog = catalog,
                    onOpenExercise = { id -> onNavigateToExercise?.invoke(id) },
                )
            }

            // ─── Músculos involucrados ──────────────────────────────────
            if (canonicalInvolved.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Músculos involucrados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        canonicalInvolved.forEach { m ->
                            val color = muscleColor(m.muscle)
                            Row(
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(8.dp),
                                        shape = RoundedCornerShape(50),
                                        color = color,
                                    ) {}
                                    Column {
                                        Text(
                                            m.muscle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        val roleText = when (m.role) {
                                            MuscleRole.PRIMARY -> "Primario"
                                            MuscleRole.SECONDARY -> "Secundario"
                                            else -> "Estabilizador"
                                        }
                                        val subText = listOfNotNull(
                                            roleText,
                                            m.emphasis?.let { "Porción: $it" }
                                        ).joinToString(" · ")
                                        Text(
                                            subText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                m.volumeContribution?.let { act ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = color.copy(alpha = 0.08f),
                                    ) {
                                        Text(
                                            "Aporte: +${"%.2f".format(act)}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = color,
                                        )
                                    }
                                }
                            }
                        }
                    }
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

            // ─── Drenaje por intensidad de último ────────────────────────
            if (exercise.involvedMuscles.isNotEmpty()) {
                item {
                    ExerciseFatigueScenarios(exercise = exercise)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            exercise.category?.let { cat ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        cat,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            exercise.type?.let { t ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        t,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            exercise.tier?.let { tier ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        tier,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Alias
        exercise.alias?.let { alias ->
            Spacer(Modifier.height(10.dp))
            Text(
                alias,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Equipment + Force + Chain
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            exercise.equipment?.let { eq ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        eq,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            exercise.force?.let { f ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        f,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
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
