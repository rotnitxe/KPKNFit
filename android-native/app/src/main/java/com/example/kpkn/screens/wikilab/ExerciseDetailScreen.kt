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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeTtcEngine

// ─── MUSCLE COLOR MAP ─────────────────────────────────────────────────────

private val MUSCLE_COLORS = mapOf(
    "Pecho" to Color(0xFFE53935), "Pecho Superior" to Color(0xFFE53935),
    "Dorsal" to Color(0xFF1E88E5), "Dorsal Ancho" to Color(0xFF1E88E5),
    "Espalda Baja" to Color(0xFF1565C0),
    "Hombro Anterior" to Color(0xFFFF8F00), "Hombro Lateral" to Color(0xFFFFA000),
    "Hombro Posterior" to Color(0xFFFFB300), "Deltoides Anterior" to Color(0xFFFF8F00),
    "Deltoides Lateral" to Color(0xFFFFA000), "Deltoides Posterior" to Color(0xFFFFB300),
    "Cuádriceps" to Color(0xFF43A047), "Isquiotibiales" to Color(0xFF2E7D32),
    "Glúteos" to Color(0xFF558B2F), "Gemelos" to Color(0xFF33691E),
    "Bíceps" to Color(0xFF8E24AA), "Tríceps" to Color(0xFF7B1FA2),
    "Braquial" to Color(0xFF9C27B0), "Manguito Rotador" to Color(0xFFFF6F00),
    "Core" to Color(0xFF00897B), "Abdomen" to Color(0xFF00897B),
    "Oblicuos" to Color(0xFF00695C), "Trapecio Medio" to Color(0xFF1976D2),
    "Trapecios" to Color(0xFF1976D2), "Trapecio Superior" to Color(0xFF1976D2),
    "Trapecio Inferior" to Color(0xFF1976D2), "Romboides" to Color(0xFF1976D2),
    "Antebrazo" to Color(0xFF795548), "Espalda" to Color(0xFF1565C0),
    "Erectores Espinales" to Color(0xFF1565C0),
    "Serrato Anterior" to Color(0xFFFF6F00),
    "Cuello" to Color(0xFF795548),
)

private fun muscleColor(name: String): Color =
    MUSCLE_COLORS[name] ?: Color(0xFF757575)

// ─── MAIN SCREEN ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    exercise: ExerciseMuscleInfo,
    onNavigateToMuscle: ((String) -> Unit)? = null,
    onNavigateToJoint: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val primaryMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.PRIMARY }
    val secondaryMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.SECONDARY }
    val stabilizerMuscles = exercise.involvedMuscles.filter { it.role == MuscleRole.STABILIZER }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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

            // ─── AUGE Metrics ────────────────────────────────────────────
            item {
                AugeMetricsSection(exercise)
            }

            // ─── Muscle Involvement ──────────────────────────────────────
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

            // ─── Kinesiology ─────────────────────────────────────────────
            if (exercise.resistanceProfile != null || exercise.setupCues != null ||
                exercise.executionCues != null) {
                item {
                    KinesiologySection(exercise)
                }
            }

            // ─── Progressions & Regressions ──────────────────────────────
            if (exercise.progressions != null || exercise.regressions != null) {
                item {
                    ProgressionsSection(exercise)
                }
            }

            // ─── Anatomical Considerations ───────────────────────────────
            if (exercise.anatomicalConsiderations != null) {
                item {
                    AnatomicalSection(exercise.anatomicalConsiderations)
                }
            }

            // ─── Periodization ───────────────────────────────────────────
            if (exercise.periodizationNotes != null) {
                item {
                    PeriodizationSection(exercise.periodizationNotes)
                }
            }

            // ─── Common Mistakes ─────────────────────────────────────────
            if (exercise.commonMistakes != null) {
                item {
                    MistakesSection(exercise.commonMistakes)
                }
            }

            // ─── Transfer & Sports ───────────────────────────────────────
            if (exercise.functionalTransfer != null || exercise.sportsRelevance != null) {
                item {
                    TransferSection(exercise)
                }
            }

            // ─── AI Coach ────────────────────────────────────────────────
            if (exercise.aiCoachAnalysis != null) {
                item {
                    AiCoachSection(exercise.aiCoachAnalysis)
                }
            }

            // ─── Community ───────────────────────────────────────────────
            if (exercise.communityOpinion != null) {
                item {
                    CommunitySection(exercise.communityOpinion)
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

// ─── AUGE METRICS ─────────────────────────────────────────────────────────

@Composable
private fun AugeMetricsSection(exercise: ExerciseMuscleInfo) {
    val hasAuge = exercise.efc != null || exercise.cnc != null ||
            exercise.ssc != null || exercise.technicalDifficulty != null
    if (!hasAuge) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "MÉTRICAS AUGE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // EFC — Fatiga Local
        exercise.efc?.let { efc ->
            MetricRow(
                label = "EFC — Fatiga Local",
                value = efc,
                maxValue = 5.0,
                description = "Costo metabólico/fatiga local",
                color = augeColor(efc, 5.0),
            )
            Spacer(Modifier.height(8.dp))
        }

        // CNC — Costo Neural
        exercise.cnc?.let { cnc ->
            MetricRow(
                label = "CNC — Costo Neural",
                value = cnc,
                maxValue = 5.0,
                description = "Costo neural central",
                color = augeColor(cnc, 5.0),
            )
            Spacer(Modifier.height(8.dp))
        }

        // SSC — Costo Estructural
        exercise.ssc?.let { ssc ->
            MetricRow(
                label = "SSC — Costo Estructural",
                value = ssc,
                maxValue = 2.0,
                description = "Costo estructural/espinal",
                color = augeColor(ssc, 2.0),
            )
            Spacer(Modifier.height(8.dp))
        }

        // Technical Difficulty
        exercise.technicalDifficulty?.let { td ->
            MetricRow(
                label = "Dificultad Técnica",
                value = td,
                maxValue = 5.0,
                description = "Complejidad del movimiento",
                color = augeColor(td, 5.0),
            )
            Spacer(Modifier.height(8.dp))
        }

        // Core Involvement
        exercise.coreInvolvement?.let { core ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Participación del Core",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Nivel de activación del core",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (core) {
                        "high" -> Color(0xFFE53935).copy(alpha = 0.12f)
                        "medium" -> Color(0xFFFF8F00).copy(alpha = 0.12f)
                        else -> Color(0xFF43A047).copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        when (core) {
                            "high" -> "Alto"
                            "medium" -> "Medio"
                            else -> "Bajo"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when (core) {
                            "high" -> Color(0xFFE53935)
                            "medium" -> Color(0xFFFF8F00)
                            else -> Color(0xFF43A047)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: Double,
    maxValue: Double,
    description: String,
    color: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${"%.1f".format(value)} / ${maxValue.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (value / maxValue).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
    }
}

private fun augeColor(value: Double, max: Double): Color {
    val ratio = value / max
    return when {
        ratio <= 0.4 -> Color(0xFF43A047)
        ratio <= 0.7 -> Color(0xFFFF8F00)
        else -> Color(0xFFE53935)
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
                        if (onNavigateToMuscle != null) {
                            Modifier.clickable { onNavigateToMuscle(m.muscle) }
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

                m.activation?.let { act ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = color.copy(alpha = 0.1f),
                    ) {
                        Text(
                            "K=${"%.2f".format(act)}",
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

// ─── KINESIOLOGY ──────────────────────────────────────────────────────────

@Composable
private fun KinesiologySection(exercise: ExerciseMuscleInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "CINESIOLOGÍA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Resistance Profile
        exercise.resistanceProfile?.let { rp ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Perfil de Resistencia",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            rp.curve?.let {
                Text(
                    "Curva: ${it}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rp.peakTensionPoint?.let {
                Text(
                    "Punto de máxima tensión: ${it}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            rp.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }

        // Setup Cues
        exercise.setupCues?.let { cues ->
            if (cues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Setup",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                cues.forEachIndexed { i, cue ->
                    Text(
                        "${i + 1}. $cue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // Execution Cues
        exercise.executionCues?.let { cues ->
            if (cues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ejecución",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                cues.forEachIndexed { i, cue ->
                    Text(
                        "${i + 1}. $cue",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // Bracing & Straps
        val features = mutableListOf<String>()
        if (exercise.bracingRecommended == true) features.add("Bracing recomendado")
        if (exercise.strapsRecommended == true) features.add("Straps recomendados")
        if (features.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            features.forEach { f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF43A047),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        f,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── PROGRESSIONS ──────────────────────────────────────────────────────────

@Composable
private fun ProgressionsSection(exercise: ExerciseMuscleInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "PROGRESIONES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        exercise.regressions?.let { regs ->
            if (regs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Regresiones",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF43A047),
                )
                regs.forEach { r ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            null,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp),
                            tint = Color(0xFF43A047),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                r.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                r.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        exercise.progressions?.let { progs ->
            if (progs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Progresiones",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8F00),
                )
                progs.forEach { p ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            null,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp),
                            tint = Color(0xFFFF8F00),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                p.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        exercise.recommendedMobility?.let { mobility ->
            if (mobility.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Movilidad Recomendada",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                mobility.forEach { m ->
                    Text(
                        "• $m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
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

// ─── PERIODIZATION ─────────────────────────────────────────────────────────

@Composable
private fun PeriodizationSection(notes: List<PeriodizationNote>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "PERIODIZACIÓN",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        notes.forEach { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Suitability indicator
                val suitColor = when {
                    note.suitability >= 0.8 -> Color(0xFF43A047)
                    note.suitability >= 0.5 -> Color(0xFFFF8F00)
                    else -> Color(0xFFE53935)
                }
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = suitColor.copy(alpha = 0.12f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${(note.suitability * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = suitColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        note.phase,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        note.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
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

// ─── TRANSFER & SPORTS ────────────────────────────────────────────────────

@Composable
private fun TransferSection(exercise: ExerciseMuscleInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "TRANSFERENCIA DEPORTIVA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        exercise.functionalTransfer?.let { ft ->
            Spacer(Modifier.height(8.dp))
            Text(
                "Transferencia Funcional",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                ft,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }

        exercise.sportsRelevance?.let { sports ->
            if (sports.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Relevancia Deportiva",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sports.forEach { sport ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9),
                        ) {
                            Text(
                                sport,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }
                }
            }
        }

        exercise.injuryRisk?.let { ir ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Riesgo de Lesión",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                val riskColor = when {
                    ir.level <= 2 -> Color(0xFF43A047)
                    ir.level <= 3.5 -> Color(0xFFFF8F00)
                    else -> Color(0xFFE53935)
                }
                Text(
                    "${"%.0f".format(ir.level)}/5",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = riskColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    ir.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─── AI COACH ──────────────────────────────────────────────────────────────

@Composable
private fun AiCoachSection(analysis: AiCoachAnalysis) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "ANÁLISIS DEL COACH IA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (0.1f).sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            analysis.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
        )

        if (analysis.pros.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Pros",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF43A047),
            )
            analysis.pros.forEach { pro ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF43A047),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        pro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (analysis.cons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Contras",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935),
            )
            analysis.cons.forEach { con ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFE53935),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        con,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─── COMMUNITY ─────────────────────────────────────────────────────────────

@Composable
private fun CommunitySection(opinions: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(
            "COMUNIDAD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (0.1f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        opinions.forEach { opinion ->
            Text(
                "• $opinion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
