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
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.ui.components.kpknGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Full-screen blur overlay for mobility preparation in live sessions.
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
    hazeState: HazeState,
    sessionAccentColor: Color = Color(0xFF66BB6A),
    catalog: ExerciseCatalogV2? = null,
) {
    val scrollState = rememberScrollState()
    val configuredSeconds = globalTimerMinutes.coerceAtLeast(1) * 60
    val remainingSeconds = globalTimerRemainingSeconds ?: configuredSeconds
    val allDone = mobilityItems.isNotEmpty() && mobilityItems.all { item ->
        item.stepKey in completedExerciseIds || completedExerciseIds.any { it.startsWith("${item.exerciseId}_${item.mobility.id}") }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(state = hazeState, style = kpknGlassStyle())
            .zIndex(6f)
            .background(Color(0xFF0C1017).copy(alpha = 0.88f)),
    ) {
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
                    val completedCount = mobilityItems.count { it.stepKey in completedExerciseIds || completedExerciseIds.any { k -> k.startsWith("${it.exerciseId}_${it.mobility.id}") } }
                    Text(
                        text = "$completedCount/${mobilityItems.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (allDone) Color(0xFF66BB6A) else Color.White.copy(alpha = 0.80f),
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
                    val isCompleted = item.stepKey in completedExerciseIds ||
                        completedExerciseIds.any { it.startsWith("${item.exerciseId}_${item.mobility.id}") }
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
                                if (!mob.notes.isNullOrBlank()) {
                                    Text(
                                        text = mob.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.55f),
                                        lineHeight = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── 4. Involucramiento Articular del Ejercicio (chips grandes, neutros, con tooltip) ───
            if (resolvedJoints.isNotEmpty()) {
                var selectedJoint by remember { mutableStateOf<JointInvolvementV2?>(null) }
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
                        Text(
                            "Involucramiento Articular del Ejercicio",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            resolvedJoints.forEach { joint ->
                                val roleLabel = when (joint.role) {
                                    JointRoleV2.PRIMARY -> "Principal"
                                    JointRoleV2.SECONDARY -> "Secundario"
                                    JointRoleV2.STABILIZER -> "Estabilizador"
                                }
                                Surface(
                                    onClick = { selectedJoint = joint },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.06f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.55f)),
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                            Text(
                                                formatJointName(joint.jointId),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.95f),
                                            )
                                            Text(
                                                roleLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.55f),
                                                letterSpacing = 0.3.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "Toca cada articulación para ver su función específica en este movimiento.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                }
                selectedJoint?.let { joint ->
                    androidx.compose.ui.window.Dialog(onDismissRequest = { selectedJoint = null }) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF151B26),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = sessionAccentColor.copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            formatJointName(joint.jointId),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = sessionAccentColor,
                                        )
                                    }
                                    Text(
                                        when (joint.role) {
                                            JointRoleV2.PRIMARY -> "Rol principal"
                                            JointRoleV2.SECONDARY -> "Rol secundario"
                                            JointRoleV2.STABILIZER -> "Rol estabilizador"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.60f),
                                    )
                                }
                                Text(
                                    text = buildJointKinesiologyDescription(joint, exercise),
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 18.sp,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                                if (joint.actions.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        joint.actions.forEach { action ->
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.White.copy(alpha = 0.06f),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                            ) {
                                                Text(
                                                    action,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.75f),
                                                )
                                            }
                                        }
                                    }
                                }
                                TextButton(
                                    onClick = { selectedJoint = null },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Cerrar", fontWeight = FontWeight.Bold, color = sessionAccentColor)
                                }
                            }
                        }
                    }
                }
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
                                                    "${comp.durationSeconds}s · ${comp.description}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.50f),
                                                    maxLines = 1,
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

        // ─── 6. Botones Sticky Inferiores con Efecto KPKN Glass y Desvanecido Suave ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.25f to Color(0xFF0C1017).copy(alpha = 0.70f),
                        0.55f to Color(0xFF0C1017).copy(alpha = 0.96f),
                        1.0f to Color(0xFF0C1017),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding)
                .padding(top = 36.dp, bottom = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onClose,
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
                    onClick = onClose,
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

private fun buildJointKinesiologyDescription(joint: JointInvolvementV2, exercise: Exercise): String {
    if (!joint.note.isNullOrBlank()) return joint.note.trim()
    val actionText = joint.actions.joinToString(" + ").ifBlank { "movimiento controlado" }
    val roleText = when (joint.role) {
        JointRoleV2.PRIMARY -> "motor principal del gesto"
        JointRoleV2.SECONDARY -> "articulación que asiste y reparte carga"
        JointRoleV2.STABILIZER -> "articulación que estabiliza y sostiene la cadena"
    }
    return when (joint.jointId.trim().lowercase()) {
        "shoulder", "glenohumeral" -> when (joint.role) {
            JointRoleV2.PRIMARY -> "Glenohumeral como $roleText: dirige $actionText del húmero. En ${exercise.name} la cabeza humeral debe deslizar sin pinzamiento; la preparación busca rotación externa + elevación escapular para que el manguito rotador centre el húmero en la glena durante toda la fase excéntrica y concéntrica."
            JointRoleV2.SECONDARY -> "Hombro como $roleText ($actionText): acompaña la trayectoria y evita que el trapecio superior robe el recorrido. La movilidad previa debe liberar cápsula posterior y pectoral menor para que el húmero no se anteriorice bajo carga."
            else -> "Hombro estabilizador: fija la glenohumeral mientras el gesto produce $actionText. El objetivo es congruencia articular — coaptación del manguito y depresión humeral — para no trasladar cizalla a la articulación acromioclavicular."
        }
        "scapulothoracic", "scapula" -> "Escápula como $roleText ($actionText): el ritmo escapulohumeral sostiene la base del hombro. En ${exercise.name} la escápula debe rotar superiormente y bascular posterior sin alar; sin esa cinemática el húmero choca contra el acromion y el trapecio superior se fatiga."
        "elbow" -> when (joint.role) {
            JointRoleV2.PRIMARY -> "Codo como $roleText: la articulación húmero-cubital produce $actionText. La clave es mantener el eje troclear alineado — sin valgo/varismo — y preparar flexores/extensores del antebrazo para que el tendón no absorba la carga del bíceps/tríceps."
            else -> "Codo como $roleText ($actionText): estabiliza el brazo de palanca. La preparación busca que el olécranon no choque precozmente en extensión y que la pronosupinación acompañe sin perder congruencia radio-cubital."
        }
        "wrist", "wrist_hand" -> "Muñeca como $roleText ($actionText): transmite fuerza sin colapsar. En ${exercise.name} la muñeca debe mantenerse neutra — ni flexión ni extensión excesiva — para que la carga viaje por el eje radio-carpiano y no por ligamentos; la movilidad aquí es rigidez activa, no laxitud."
        "hip", "coxofemoral" -> when (joint.role) {
            JointRoleV2.PRIMARY -> "Cadera como $roleText: genera $actionText. En ${exercise.name} la cabeza femoral debe centrarse en el acetábulo con anteversión pélvica controlada; la dorsiflexión y la rotación externa de cadera liberan profundidad sin que el raquis compense en flexión lumbar."
            JointRoleV2.SECONDARY -> "Cadera como $roleText ($actionText): reparte la carga entre cadena anterior y posterior. La capsula anterior y aductores deben ceder para que la pelvis no bascule precozmente y el fémur no se anteriorice."
            else -> "Cadera estabilizadora: sostiene $actionText sin colapso en valgo. Glúteo medio y rotadores externos fijan el fémur para que la rodilla trackee sobre el segundo dedo y el acetábulo no reciba cizalla."
        }
        "knee" -> when (joint.role) {
            JointRoleV2.PRIMARY -> "Rodilla como $roleText: ejecuta $actionText con control del eje. En ${exercise.name} la rótula debe deslizar centrada en la tróclea femoral; la preparación apunta a que cuádriceps y cadena posterior compartan el momento sin que el ligamento cruzado anterior absorba la traslación tibial."
            else -> "Rodilla como $roleText ($actionText): estabiliza la bisagra. El menisco y el LCA agradecen que la tibia no rote bajo carga; el trabajo de movilidad busca que la dorsiflexión del tobillo y la cadera eviten que la rodilla colapse en valgo."
        }
        "ankle" -> "Tobillo como $roleText ($actionText): la dorsiflexión disponible dicta la cinemática superior. En ${exercise.name} un tobillo rígido obliga a la rodilla a avanzar o al talón a levantarse; la preparación persigue que el astrágalo deslice posterior en la mortaja para ganar 8-12° sin pronación excesiva."
        "spine", "lumbar", "thoracic", "cervical" -> "Columna como $roleText ($actionText): no es bisagra pasiva. En ${exercise.name} debe mantener lordosis neutra y disociar pelvis de tórax; la rigidez torácica o la hiperlordosis lumbar roban recorrido a cadera/hombro y trasladan compresión a discos. La movilidad aquí es control segmentario, no hipermovilidad."
        else -> "${formatJointName(joint.jointId)} como $roleText: participa con $actionText en ${exercise.name}. Su preparación específica evita que la compensación viaje a la articulación vecina."
    }
}
