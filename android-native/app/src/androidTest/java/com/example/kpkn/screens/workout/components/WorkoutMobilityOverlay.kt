package com.example.kpkn.screens.workout.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.example.kpkn.data.exercises.displayNameWithSelectedChips
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.data.exercises.catalogv2.canonicalJointKnowledge
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.kpknHazeEffect
import dev.chrisbanes.haze.HazeState
import com.example.kpkn.screens.sessioneditor.components.formatSeriesEquivalent
import com.example.kpkn.screens.sessioneditor.components.formatVolumePercent
import com.example.kpkn.screens.sessioneditor.components.oneSeriesVolumeContributions
import com.example.kpkn.screens.sessioneditor.components.roleVolumeLabel

/**
 * Full-screen DarkMica overlay for mobility preparation in live sessions.
 * Displays global countdown timer, target joint involvement, biomechanical focus,
 * full-name checklist items with descriptions, and optional complementary mobility drills.
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
    embedded: Boolean = false,
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

    @Composable
    fun MobilityBodyContent(contentModifier: Modifier) {
        Surface(
            modifier = contentModifier,
            shape = WorkoutUiTokens.CardShape,
            color = WorkoutUiTokens.setCardColor(),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ─── 1. Cabecera y Contexto de Fase ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "PREPARACIÓN DE MOVILIDAD",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = sessionAccentColor,
                            letterSpacing = 1.1.sp,
                        )
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
                                    pattern.name,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.80f),
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (allDone) sessionAccentColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                    ) {
                        val completedCount = mobilityItems.count { it.stepKey in completedExerciseIds }
                        Text(
                            text = "$completedCount/${mobilityItems.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Black,
                            color = if (allDone) sessionAccentColor else Color.White.copy(alpha = 0.80f),
                        )
                    }
                }

                // ─── 2. Timer de Movilidad Compacto (Una sola fila limpia) ───
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = sessionAccentColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = formatTimerMinutesSeconds(remainingSeconds),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (remainingSeconds <= 10 && remainingSeconds > 0) Color(0xFFFF5252) else Color.White,
                                    letterSpacing = 1.2.sp,
                                ),
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick = { onAddTimerSeconds(30) },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.07f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("+30s", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Button(
                                onClick = if (globalTimerRunning) onPauseGlobalTimer else onStartGlobalTimer,
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = sessionAccentColor,
                                    contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                                ),
                            ) {
                                Icon(
                                    imageVector = if (globalTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (globalTimerRunning) "Pausar" else "Iniciar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Black,
                                )
                            }

                            Surface(
                                onClick = onResetGlobalTimer,
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.70f))
                                }
                            }
                        }
                    }
                }

                // ─── 3. Ejercicios Programados de Movilidad (Checklist con acordeón de instrucciones) ───
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ejercicios Programados",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.70f),
                    )

                    val distinctExercises = remember(mobilityItems) {
                        mobilityItems.map { it.exerciseId to it.exerciseName }.distinctBy { it.first }
                    }

                    if (distinctExercises.size > 1) {
                        distinctExercises.forEachIndexed { exIdx, (exId, exName) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.padding(top = if (exIdx > 0) 6.dp else 0.dp),
                            ) {
                                Text(
                                    text = "EJERCICIO ${exIdx + 1}: ${exName.uppercase()}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = sessionAccentColor,
                                    letterSpacing = 0.8.sp,
                                )
                            }

                            val itemsForEx = mobilityItems.filter { it.exerciseId == exId }
                            itemsForEx.forEach { item ->
                                val isCompleted = item.stepKey in completedExerciseIds
                                val isActive = activeMobilityKey == item.stepKey ||
                                    (activeMobilityKey?.contains(item.mobility.id) == true)
                                val mob = item.mobility

                                val catalogMobility = remember(mob.id, mob.exerciseDbId, mob.name) {
                                    MobilityExerciseCatalog.getAllMobilityExercises().firstOrNull {
                                        it.id == mob.id || it.id == mob.exerciseDbId || it.name.equals(mob.name.trim(), ignoreCase = true)
                                    }
                                }
                                val description = catalogMobility?.description?.takeIf { it.isNotBlank() }
                                    ?: mob.notes?.takeIf { it.isNotBlank() }
                                val instructions = catalogMobility?.instructions?.takeIf { it.isNotBlank() }
                                var isExpanded by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = when {
                                        isActive -> sessionAccentColor.copy(alpha = 0.10f)
                                        isCompleted -> sessionAccentColor.copy(alpha = 0.06f)
                                        else -> Color.White.copy(alpha = 0.04f)
                                    },
                                    border = BorderStroke(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = when {
                                            isActive -> sessionAccentColor.copy(alpha = 0.45f)
                                            isCompleted -> sessionAccentColor.copy(alpha = 0.35f)
                                            else -> Color.White.copy(alpha = 0.08f)
                                        },
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Checkbox(
                                                checked = isCompleted,
                                                onCheckedChange = { onToggleComplete(item, it) },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = sessionAccentColor,
                                                    uncheckedColor = Color.White.copy(alpha = 0.30f),
                                                    checkmarkColor = Color.Black,
                                                ),
                                                modifier = Modifier.size(20.dp),
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mob.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCompleted) sessionAccentColor else Color.White,
                                                )
                                                val details = listOfNotNull(
                                                    mob.reps?.takeIf { it.isNotBlank() }?.let { "$it reps" },
                                                    mob.durationSeconds?.takeIf { it > 0 }?.let { "${it}s" },
                                                ).joinToString(" · ")
                                                if (details.isNotBlank()) {
                                                    Text(
                                                        text = details,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.60f),
                                                    )
                                                }
                                            }

                                            if (!description.isNullOrBlank() || !instructions.isNullOrBlank()) {
                                                Surface(
                                                    onClick = { isExpanded = !isExpanded },
                                                    shape = CircleShape,
                                                    color = Color.White.copy(alpha = 0.06f),
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = Color.White.copy(alpha = 0.70f),
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (!description.isNullOrBlank()) {
                                                    Text(
                                                        description,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                        color = Color.White.copy(alpha = 0.75f),
                                                    )
                                                }
                                                if (!instructions.isNullOrBlank()) {
                                                    Text(
                                                        instructions,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                        color = Color.White.copy(alpha = 0.60f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        mobilityItems.forEach { item ->
                            val isCompleted = item.stepKey in completedExerciseIds
                            val isActive = activeMobilityKey == item.stepKey ||
                                (activeMobilityKey?.contains(item.mobility.id) == true)
                            val mob = item.mobility

                            val catalogMobility = remember(mob.id, mob.exerciseDbId, mob.name) {
                                MobilityExerciseCatalog.getAllMobilityExercises().firstOrNull {
                                    it.id == mob.id || it.id == mob.exerciseDbId || it.name.equals(mob.name.trim(), ignoreCase = true)
                                }
                            }
                            val description = catalogMobility?.description?.takeIf { it.isNotBlank() }
                                ?: mob.notes?.takeIf { it.isNotBlank() }
                            val instructions = catalogMobility?.instructions?.takeIf { it.isNotBlank() }
                            var isExpanded by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isActive -> sessionAccentColor.copy(alpha = 0.10f)
                                    isCompleted -> sessionAccentColor.copy(alpha = 0.06f)
                                    else -> Color.White.copy(alpha = 0.04f)
                                },
                                border = BorderStroke(
                                    width = if (isActive) 1.5.dp else 1.dp,
                                    color = when {
                                        isActive -> sessionAccentColor.copy(alpha = 0.45f)
                                        isCompleted -> sessionAccentColor.copy(alpha = 0.35f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Checkbox(
                                            checked = isCompleted,
                                            onCheckedChange = { onToggleComplete(item, it) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = sessionAccentColor,
                                                uncheckedColor = Color.White.copy(alpha = 0.30f),
                                                checkmarkColor = Color.Black,
                                            ),
                                            modifier = Modifier.size(20.dp),
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mob.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCompleted) sessionAccentColor else Color.White,
                                            )
                                            val details = listOfNotNull(
                                                mob.reps?.takeIf { it.isNotBlank() }?.let { "$it reps" },
                                                mob.durationSeconds?.takeIf { it > 0 }?.let { "${it}s" },
                                            ).joinToString(" · ")
                                            if (details.isNotBlank()) {
                                                Text(
                                                    text = details,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.60f),
                                                )
                                            }
                                        }

                                        if (!description.isNullOrBlank() || !instructions.isNullOrBlank()) {
                                            Surface(
                                                onClick = { isExpanded = !isExpanded },
                                                shape = CircleShape,
                                                color = Color.White.copy(alpha = 0.06f),
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.70f),
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (!description.isNullOrBlank()) {
                                                Text(
                                                    description,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                    color = Color.White.copy(alpha = 0.75f),
                                                )
                                            }
                                            if (!instructions.isNullOrBlank()) {
                                                Text(
                                                    instructions,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = Color.White.copy(alpha = 0.60f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 4. Involucramiento Articular y Muscular (Acordeón desplegable opcional) ───
                if (resolvedJoints.isNotEmpty() || muscleContributions.isNotEmpty() || complementaryMobility.isNotEmpty()) {
                    var showAdditionalInfo by remember(exercise.id) { mutableStateOf(false) }
                    var selectedJoint by remember(exercise.id) { mutableStateOf<JointInvolvementV2?>(null) }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.03f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAdditionalInfo = !showAdditionalInfo }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.60f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        "Información articular y muscular",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.80f),
                                    )
                                }
                                Icon(
                                    imageVector = if (showAdditionalInfo) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            AnimatedVisibility(visible = showAdditionalInfo) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    if (resolvedJoints.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            resolvedJoints.forEach { joint ->
                                                val hasKnowledge = canonicalJointKnowledge(joint.jointId) != null
                                                Surface(
                                                    onClick = { if (hasKnowledge) selectedJoint = joint },
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = Color.White.copy(alpha = 0.05f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Text(
                                                            formatJointName(joint.jointId),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                        )
                                                        if (hasKnowledge) {
                                                            Icon(
                                                                Icons.Default.Info,
                                                                contentDescription = null,
                                                                tint = sessionAccentColor,
                                                                modifier = Modifier.size(11.dp),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (muscleContributions.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                "Músculos Involucrados",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = sessionAccentColor,
                                            )
                                            muscleContributions.take(4).forEach { contribution ->
                                                val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .then(if (knowledge != null) Modifier.clickable { selectedKnowledge = knowledge } else Modifier),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color.White.copy(alpha = 0.04f),
                                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Text(
                                                            contribution.muscle,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White.copy(alpha = 0.90f),
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Text(
                                                            "${formatSeriesEquivalent(contribution.seriesEquivalent)} · ${formatVolumePercent(contribution.seriesEquivalent)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White.copy(alpha = 0.70f),
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

                    selectedJoint?.let { joint ->
                        val jointKnowledge = canonicalJointKnowledge(joint.jointId)
                        if (jointKnowledge != null) {
                            CanonicalKnowledgeOverlay(
                                knowledge = jointKnowledge,
                                onDismiss = { selectedJoint = null },
                            )
                        }
                    }
                }

                selectedKnowledge?.let { knowledge ->
                    CanonicalKnowledgeOverlay(
                        knowledge = knowledge,
                        onDismiss = { selectedKnowledge = null },
                    )
                }

                // ─── 5. Botones de Acción (Integrados en la tarjeta) ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onSkip,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B232E).copy(alpha = 0.90f),
                            contentColor = Color.White,
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    ) {
                        Text(
                            "Saltar",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Button(
                        onClick = onContinue,
                        enabled = allDone || mobilityItems.isEmpty(),
                        modifier = Modifier.weight(1.4f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = sessionAccentColor,
                            contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                            disabledContainerColor = Color(0xFF151B24).copy(alpha = 0.85f),
                            disabledContentColor = Color.White.copy(alpha = 0.38f),
                        ),
                        border = if (!allDone && mobilityItems.isNotEmpty()) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
                    ) {
                        Text(
                            if (allDone || mobilityItems.isEmpty()) "Comenzar" else "Completa los ejercicios",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }

    if (embedded) {
        MobilityBodyContent(
            contentModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 8.dp, bottom = 16.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(6f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .kpknHazeEffect(hazeState),
            )
            MobilityBodyContent(
                contentModifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                    .padding(top = 20.dp, bottom = 110.dp)
            )
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
                            contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
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
): List<JointInvolvementV2> =
    com.example.kpkn.screens.workout.resolveJointInvolvementForExercise(exercise, catalog)
