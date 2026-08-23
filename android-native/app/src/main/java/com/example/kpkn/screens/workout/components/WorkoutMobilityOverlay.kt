package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.kpknHazeEffect
import com.example.kpkn.ui.components.kpknSheetDialogTextButtonColors
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.screens.sessioneditor.components.formatSeriesEquivalent
import com.example.kpkn.screens.sessioneditor.components.formatVolumePercent
import com.example.kpkn.screens.sessioneditor.components.oneSeriesVolumeContributions
import com.example.kpkn.screens.sessioneditor.components.roleVolumeLabel

/**
 * Full-screen DarkMica overlay for mobility preparation in live sessions.
 * Displays global countdown timer, target joint involvement, biomechanical focus,
 * full-name checklist items, and optional complementary mobility drills.
 */
@Composable
fun WorkoutMobilityOverlay(
    exercise: Exercise,
    mobilityItems: List<WorkoutMobilityChecklistItem>,
    completedExerciseIds: Set<String>,
    activeMobilityKey: String?,
    globalTimerMinutes: Int,
    globalTimerRemainingSeconds: Int?,
    globalTimerRunning: Boolean,
    onStartGlobalTimer: () -> Unit,
    onPauseGlobalTimer: () -> Unit,
    onAddTimerSeconds: (Int) -> Unit = {},
    onResetGlobalTimer: () -> Unit = {},
    onToggleComplete: (item: WorkoutMobilityChecklistItem, completed: Boolean) -> Unit,
    onAddOptionalMobility: (MobilityExercise) -> Unit,
    onClose: () -> Unit,
    onSkip: () -> Unit = onClose,
    onContinue: () -> Unit = onClose,
    hazeState: HazeState,
    sessionAccentColor: Color = Color(0xFF66BB6A),
    catalog: ExerciseCatalogV2? = null,
) {
    val scrollState = rememberScrollState()
    val configuredSeconds = globalTimerMinutes.coerceAtLeast(1) * 60
    val remainingSeconds = globalTimerRemainingSeconds ?: configuredSeconds
    val allDone = mobilityItems.isNotEmpty() && mobilityItems.all { item ->
        item.stepKey in completedExerciseIds
    }

    // Resolve joint involvement from catalog v2 definition/configuration if available
    val resolvedJoints = remember(exercise.id, exercise.catalogConfigurationId, catalog) {
        resolveJointInvolvementForExercise(exercise, catalog)
    }


    // Filter recommended complementary mobility exercises for relevant body regions
    val complementaryMobility = remember(resolvedJoints, mobilityItems) {
        val existingNames = mobilityItems.map { it.mobility.name.trim().lowercase() }.toSet()
        val relevantRegions = resolvedJoints.map { mapJointIdToBodyRegion(it.jointId) }.toSet()
        MobilityExerciseCatalog.getAllMobilityExercises()
            .filter { it.bodyRegion in relevantRegions || relevantRegions.isEmpty() }
            .filter { it.name.trim().lowercase() !in existingNames }
            .take(4)
    }

    var showComplementarySection by remember { mutableStateOf(false) }
    var selectedKnowledge by remember(exercise.id) { mutableStateOf<CanonicalKnowledge?>(null) }
    val patternKnowledge = remember(exercise.id, exercise.selectedMovementPattern) {
        canonicalPatternKnowledge(exercise.selectedMovementPattern.orEmpty())
    }
    val resolvedMuscleInfo = remember(exercise.id, exercise.catalogConfigurationId) {
        exercise.catalogConfigurationId
            ?.takeIf { it.isNotBlank() }
            ?.let { configurationId ->
                resolveCatalogExerciseInfo(
                    catalogConfigurationId = configurationId,
                    exerciseDbId = null,
                    exerciseId = null,
                    exerciseName = null,
                )
            }
    }
    val muscleContributions = remember(resolvedMuscleInfo) {
        resolvedMuscleInfo?.let(::oneSeriesVolumeContributions).orEmpty()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(6f),
    ) {
        // Fullscreen DarkMica backdrop — sibling of content + sticky footer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .kpknHazeEffect(hazeState),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── 1. Cabecera y Contexto de Fase ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PREPARACIÓN DE MOVILIDAD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = sessionAccentColor,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = exercise.displayNameWithSelectedChips(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (allDone) Color(0xFF66BB6A).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                ) {
                    val completedCount = mobilityItems.count { it.stepKey in completedExerciseIds }
                    Text(
                        text = "$completedCount/${mobilityItems.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (allDone) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.80f),
                    )
                }
            }

            patternKnowledge?.let { pattern ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { selectedKnowledge = pattern },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Text(
                        "Patrón · ${pattern.name}",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.80f),
                    )
                }
            }

            // ─── 2. Timer de Movilidad Principal ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = sessionAccentColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Tiempo de preparación",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.70f),
                            )
                        }
                        if (globalTimerRunning) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = sessionAccentColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "EN CURSO",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sessionAccentColor,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }

                    // Contador digital prominente (números más grandes)
                    Text(
                        text = formatTimerMinutesSeconds(remainingSeconds),
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (remainingSeconds <= 10 && remainingSeconds > 0) Color(0xFFFF5252) else Color.White,
                            letterSpacing = 2.5.sp,
                        ),
                    )

                    // Controles de tiempo compactos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onAddTimerSeconds(30) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF19212C).copy(alpha = 0.85f),
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        ) {
                            Text("+30s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = if (globalTimerRunning) onPauseGlobalTimer else onStartGlobalTimer,
                            modifier = Modifier.weight(1.3f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = sessionAccentColor,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(
                                imageVector = if (globalTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (globalTimerRunning) "Pausar" else "Iniciar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                            )
                        }

                        Button(
                            onClick = onResetGlobalTimer,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF19212C).copy(alpha = 0.85f),
                                contentColor = Color.White.copy(alpha = 0.75f),
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ─── 3. Ejercicios Programados de Movilidad (checklist puro) ───
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Ejercicios Programados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                mobilityItems.forEach { item ->
                    val isCompleted = item.stepKey in completedExerciseIds
                    val isActive = activeMobilityKey == item.stepKey ||
                        (activeMobilityKey?.contains(item.mobility.id) == true)
                    val mob = item.mobility

                    Surface(
                        onClick = { onToggleComplete(item, !isCompleted) },
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isActive -> sessionAccentColor.copy(alpha = 0.12f)
                            isCompleted -> Color(0xFF66BB6A).copy(alpha = 0.07f)
                            else -> Color.White.copy(alpha = 0.04f)
                        },
                        border = BorderStroke(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = when {
                                isActive -> sessionAccentColor.copy(alpha = 0.50f)
                                isCompleted -> Color(0xFF66BB6A).copy(alpha = 0.30f)
                                else -> Color.White.copy(alpha = 0.08f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(
                                checked = isCompleted,
                                onCheckedChange = { onToggleComplete(item, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = sessionAccentColor,
                                    uncheckedColor = Color.White.copy(alpha = 0.30f),
                                    checkmarkColor = Color.Black,
                                ),
                                modifier = Modifier.size(24.dp),
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = mob.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) Color.White.copy(alpha = 0.65f) else Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // ─── 4. Involucramiento Articular del Ejercicio (HorizontalPager con vista previa parcial) ───
            if (resolvedJoints.isNotEmpty()) {
                var selectedJoint by remember(exercise.id) { mutableStateOf<JointInvolvementV2?>(null) }
                val jointPagerState = rememberPagerState(pageCount = { resolvedJoints.size })
                LaunchedEffect(exercise.id, resolvedJoints.map { it.jointId }) {
                    selectedJoint = null
                    if (jointPagerState.currentPage != 0) {
                        jointPagerState.scrollToPage(0)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Involucramiento Articular del Ejercicio",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            if (resolvedJoints.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    resolvedJoints.indices.forEach { index ->
                                        val isCurrent = jointPagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .size(if (isCurrent) 6.dp else 4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCurrent) sessionAccentColor else Color.White.copy(alpha = 0.3f)
                                                ),
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalPager(
                            state = jointPagerState,
                            contentPadding = PaddingValues(end = if (resolvedJoints.size > 1) 48.dp else 0.dp),
                            pageSpacing = 10.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                        ) { pageIdx ->
                            val joint = resolvedJoints[pageIdx]
                            val roleLabel = when (joint.role) {
                                JointRoleV2.PRIMARY -> "Principal"
                                JointRoleV2.SECONDARY -> "Secundario"
                                JointRoleV2.STABILIZER -> "Estabilizador"
                            }
                            Surface(
                                onClick = {
                                    if (canonicalJointKnowledge(joint.jointId) != null) selectedJoint = joint
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics {
                                        contentDescription = "Tarjeta ${pageIdx + 1} de ${resolvedJoints.size}: ${formatJointName(joint.jointId)}"
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            formatJointName(joint.jointId),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.95f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Visible,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                    ) {
                                        Text(
                                            roleLabel,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.62f),
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            "Desliza y toca cada articulación para ver su descripción canónica.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                }
                selectedJoint?.let { joint ->
                    canonicalJointKnowledge(joint.jointId)?.let { knowledge ->
                        CanonicalKnowledgeOverlay(
                            knowledge = knowledge,
                            onDismiss = { selectedJoint = null },
                        )
                    }
                }
            }

            if (muscleContributions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Involucramiento muscular",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.86f),
                        )
                        Text(
                            "Aporte equivalente por una serie planificada.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.48f),
                        )
                        muscleContributions.take(6).forEach { contribution ->
                            val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .then(if (knowledge != null) Modifier.clickable { selectedKnowledge = knowledge } else Modifier),
                                shape = RoundedCornerShape(9.dp),
                                color = Color.White.copy(alpha = 0.045f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(
                                            contribution.muscle,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.90f),
                                        )
                                        Text(
                                            roleVolumeLabel(contribution.role).substringBefore(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.52f),
                                        )
                                    }
                                    Text(
                                        "${formatSeriesEquivalent(contribution.seriesEquivalent)} · ${formatVolumePercent(contribution.seriesEquivalent)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            selectedKnowledge?.let { knowledge ->
                CanonicalKnowledgeOverlay(
                    knowledge = knowledge,
                    onDismiss = { selectedKnowledge = null },
                )
            }

            // ─── 5. Movilidad Recomendada Adicional (Opcional) ───
            if (complementaryMobility.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.025f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showComplementarySection = !showComplementarySection }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = sessionAccentColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Movilidad complementaria sugerida",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                            Icon(
                                imageVector = if (showComplementarySection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        AnimatedVisibility(visible = showComplementarySection) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                complementaryMobility.forEach { comp ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.03f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    comp.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White.copy(alpha = 0.90f),
                                                )
                                                Text(
                                                    comp.objective,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.50f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    comp.instructions.orEmpty(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.68f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            IconButton(
                                                onClick = { onAddOptionalMobility(comp) },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Agregar",
                                                    tint = sessionAccentColor,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── 6. Sticky footer DarkMica (sibling of haze backdrop; no opaque gradient) ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .kpknGlass(hazeState, WorkoutUiTokens.DockShape)
                .navigationBarsPadding()
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 16.dp, bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Text(
                        "Saltar",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Button(
                    onClick = onContinue,
                    enabled = allDone,
                    modifier = Modifier.weight(1.3f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = Color.Black,
                        disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                        disabledContentColor = Color.White.copy(alpha = 0.38f),
                    ),
                    border = if (!allDone) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                ) {
                    Text(
                        if (allDone) "Listo" else "Completa la movilidad",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

private fun formatTimerMinutesSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}

private fun formatJointName(jointId: String): String = when (jointId.trim().lowercase()) {
    "shoulder", "glenohumeral" -> "Hombro"
    "scapulothoracic", "scapula" -> "Escápula"
    "elbow" -> "Codo"
    "wrist", "wrist_hand" -> "Muñeca"
    "hip", "coxofemoral" -> "Cadera"
    "knee" -> "Rodilla"
    "ankle" -> "Tobillo"
    "spine", "lumbar", "thoracic", "cervical" -> "Columna"
    else -> jointId.replaceFirstChar { it.uppercase() }
}

private fun mapJointIdToBodyRegion(jointId: String): String = when (jointId.trim().lowercase()) {
    "shoulder", "glenohumeral", "scapula" -> "shoulder"
    "elbow" -> "elbow"
    "wrist", "wrist_hand" -> "wrist"
    "hip", "coxofemoral" -> "hip"
    "knee" -> "knee"
    "ankle" -> "ankle"
    "spine", "lumbar", "thoracic" -> "spine"
    else -> "hip"
}

private fun resolveJointInvolvementForExercise(
    exercise: Exercise,
    catalog: ExerciseCatalogV2?,
): List<JointInvolvementV2> {
    catalog ?: return emptyList()
    val configurationId = exercise.catalogConfigurationId?.takeIf { it.isNotBlank() }
    val definitionId = exercise.catalogDefinitionId?.takeIf { it.isNotBlank() }

    val definition = catalog.families
        .asSequence()
        .flatMap { it.definitions.asSequence() }
        .firstOrNull { def ->
            (definitionId != null && def.id == definitionId) ||
                def.canonicalName.equals(exercise.name.trim(), ignoreCase = true) ||
                def.configurations.any { it.id.equals(exercise.exerciseDbId, ignoreCase = true) }
        } ?: return emptyList()

    val config = definition.configurations.firstOrNull { it.id == configurationId }
        ?: definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
        ?: definition.configurations.firstOrNull()
        ?: return emptyList()

    return config.profile.jointInvolvement
}
