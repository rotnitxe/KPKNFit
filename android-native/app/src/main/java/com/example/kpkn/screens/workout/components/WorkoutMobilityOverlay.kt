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
    val allDone = mobilityItems.isNotEmpty() && mobilityItems.all { it.stepKey in completedExerciseIds }

    // Resolve joint involvement from catalog v2 definition/configuration if available
    val resolvedJoints = remember(exercise.id, exercise.catalogConfigurationId, catalog) {
        resolveJointInvolvementForExercise(exercise, catalog)
    }

    // Resolve critical mobility/biomechanical focus
    val criticalMobilityAspect = remember(exercise.id, exercise.catalogDefinitionId, catalog, resolvedJoints) {
        resolveCriticalMobilityAspect(exercise, catalog, resolvedJoints)
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
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = WorkoutUiTokens.ScreenHorizontalPadding, vertical = 20.dp),
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
                    Text(
                        text = "${mobilityItems.count { it.stepKey in completedExerciseIds }}/${mobilityItems.size}",
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
                        OutlinedButton(
                            onClick = { onAddTimerSeconds(30) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
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

                        OutlinedButton(
                            onClick = onResetGlobalTimer,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.60f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ─── 3. Involucramiento Articular & Aspectos Críticos ───
            if (resolvedJoints.isNotEmpty() || criticalMobilityAspect.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.035f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Articulaciones Clave
                        if (resolvedJoints.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Involucramiento Articular del Ejercicio",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    resolvedJoints.forEach { joint ->
                                        val isPrimary = joint.role == JointRoleV2.PRIMARY
                                        val roleAccent = when (joint.role) {
                                            JointRoleV2.PRIMARY -> Color(0xFFF59E0B)
                                            JointRoleV2.SECONDARY -> Color(0xFF38BDF8)
                                            JointRoleV2.STABILIZER -> Color(0xFFA78BFA)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = roleAccent.copy(alpha = if (isPrimary) 0.14f else 0.08f),
                                            border = BorderStroke(1.dp, roleAccent.copy(alpha = if (isPrimary) 0.35f else 0.15f)),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(roleAccent),
                                                )
                                                Text(
                                                    formatJointName(joint.jointId),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.90f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Aspectos Críticos de Movilidad
                        if (criticalMobilityAspect.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = sessionAccentColor.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.14f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = sessionAccentColor,
                                        modifier = Modifier.size(15.dp).padding(top = 2.dp),
                                    )
                                    Text(
                                        text = criticalMobilityAspect,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 17.sp,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── 4. Tarjetas de Movilidad Programadas ───
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Ejercicios Programados",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )

                mobilityItems.forEachIndexed { index, item ->
                    val isCompleted = item.stepKey in completedExerciseIds
                    val isActive = activeMobilityKey == item.stepKey
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
                                // Nombre completo sin prefijos ambiguos
                                Text(
                                    text = mob.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) Color.White.copy(alpha = 0.65f) else Color.White,
                                )

                                val metricLabel = buildString {
                                    append("Serie ${item.mobilitySetIndex + 1} de ${mob.sets.coerceAtLeast(1)}")
                                    if (mob.unit == MobilityUnit.SECONDS && (mob.durationSeconds ?: 0) > 0) {
                                        append(" · ${mob.durationSeconds}s")
                                    } else if (!mob.reps.isNullOrBlank()) {
                                        append(" · ${mob.reps} reps")
                                    }
                                }

                                Text(
                                    text = metricLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = sessionAccentColor.copy(alpha = 0.90f),
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

            Spacer(Modifier.height(10.dp))

            // ─── 6. Botones de Acción Final ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.70f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    Text(
                        if (allDone) "Cerrar" else "Saltar movilidad",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1.2f).height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        if (allDone) "Listo" else "Continuar",
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

private fun resolveCriticalMobilityAspect(
    exercise: Exercise,
    catalog: ExerciseCatalogV2?,
    joints: List<JointInvolvementV2>,
): String {
    val primaryJoint = joints.firstOrNull { it.role == JointRoleV2.PRIMARY } ?: joints.firstOrNull()
    if (!primaryJoint?.note.isNullOrBlank()) {
        return primaryJoint.note
    }
    val configId = exercise.catalogConfigurationId
    val configDesc = catalog?.families
        ?.asSequence()
        ?.flatMap { it.definitions.asSequence() }
        ?.flatMap { it.configurations.asSequence() }
        ?.firstOrNull { it.id == configId }
        ?.profile
        ?.description
        ?.takeIf { it.isNotBlank() }
    if (configDesc != null) return configDesc

    return when {
        joints.any { it.jointId.contains("hip", ignoreCase = true) } ->
            "Foco de movilidad: apertura de cadera y dorsiflexión para asegurar profundidad sin compensación lumbar."
        joints.any { it.jointId.contains("shoulder", ignoreCase = true) } ->
            "Foco de movilidad: rotación externa y movilidad torácica para fijar el húmero sin pinzamiento subacromial."
        joints.any { it.jointId.contains("spine", ignoreCase = true) } ->
            "Foco de movilidad: disociación lumbopélvica y extensión torácica antes de recibir carga axial."
        else -> "Foco de movilidad: activar rango articular y lubricación sinovial en las articulaciones objetivo."
    }
}
