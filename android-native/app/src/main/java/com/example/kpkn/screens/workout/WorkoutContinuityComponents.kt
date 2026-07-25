package com.example.kpkn.screens.workout

/**
 * Continuity transition UI retained for androidTest fixtures.
 * Production post-exercise feedback uses [WorkoutPostExerciseFeedbackContent] via rest overlay only.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.supersetGroupRefOrLegacyId
import kotlinx.coroutines.delay

internal enum class WorkoutContinuityPhase {
    CURRENT_EXERCISE,
    SUPERSET,
    NEXT_EXERCISE,
    NEXT_BLOCK,
    SESSION_FINISH,
}

internal data class WorkoutContinuityState(
    val phase: WorkoutContinuityPhase,
    val eyebrow: String,
    val title: String,
    val body: String,
    val progressLabel: String,
    val nextExerciseName: String? = null,
    val nextSetLabel: String? = null,
    val accentHex: String? = null,
    val feedbackPrompt: String? = null,
)

data class WorkoutContinuityTransitionTarget(
    val key: String,
    val eyebrow: String,
    val title: String,
    val body: String,
    val accentHex: String? = null,
)

internal fun buildWorkoutContinuityState(
    session: Session,
    visibleExercises: List<Exercise>,
    currentExerciseIdx: Int,
    currentSetIdx: Int,
    feedbackPrompt: String? = null,
): WorkoutContinuityState? {
    val currentExercise = visibleExercises.getOrNull(currentExerciseIdx) ?: return null
    val currentPart = session.partForExercise(currentExercise.id)
    val visibleParts = session.visibleParts(visibleExercises)
    val currentPartIndex = currentPart?.let { part -> visibleParts.indexOfFirst { it.id == part.id }.takeIf { it >= 0 } }
    val blockLabel = when {
        currentPart != null && currentPartIndex != null -> "Bloque ${currentPartIndex + 1} de ${visibleParts.size}"
        currentPart != null -> "Bloque activo"
        else -> "Sesion activa"
    }
    val progressLabel = "Ejercicio ${currentExerciseIdx + 1} de ${visibleExercises.size}"
    val currentSetNumber = currentSetIdx + 1

    val supersetTarget = nextSupersetHop(visibleExercises, currentExerciseIdx, currentSetIdx)
    if (supersetTarget != null) {
        val nextExercise = visibleExercises[supersetTarget.first]
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.SUPERSET,
            eyebrow = blockLabel,
            title = "Empalma con ${nextExercise.name}",
            body = "Superset en la misma ronda. Despues de cerrar aqui, sigues con Serie ${supersetTarget.second + 1}.",
            progressLabel = progressLabel,
            nextExerciseName = nextExercise.name,
            nextSetLabel = "Serie ${supersetTarget.second + 1}",
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val lastSetIndex = currentExercise.sets.lastIndex.coerceAtLeast(0)
    if (currentSetIdx < lastSetIndex) {
        val remainingSets = lastSetIndex - currentSetIdx
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.CURRENT_EXERCISE,
            eyebrow = blockLabel,
            title = if (remainingSets == 1) {
                "Ultima serie para cerrar ${currentExercise.name}"
            } else {
                "Quedan $remainingSets series en ${currentExercise.name}"
            },
            body = "Mantienes el foco en ${currentPart?.name ?: session.name}. Ahora vas por Serie $currentSetNumber.",
            progressLabel = progressLabel,
            nextExerciseName = currentExercise.name,
            nextSetLabel = "Serie ${currentSetNumber + 1}",
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val nextExercise = visibleExercises.getOrNull(currentExerciseIdx + 1)
    if (nextExercise == null) {
        return WorkoutContinuityState(
            phase = WorkoutContinuityPhase.SESSION_FINISH,
            eyebrow = blockLabel,
            title = "Cierras ${currentPart?.name ?: session.name}",
            body = "Esta es la ultima estacion de la sesion. Al completar la serie, pasas al cierre final.",
            progressLabel = progressLabel,
            accentHex = currentPart?.color,
            feedbackPrompt = feedbackPrompt,
        )
    }

    val nextPart = session.partForExercise(nextExercise.id)
    val entersNewBlock = currentPart?.id != nextPart?.id && nextPart != null
    return WorkoutContinuityState(
        phase = if (entersNewBlock) WorkoutContinuityPhase.NEXT_BLOCK else WorkoutContinuityPhase.NEXT_EXERCISE,
        eyebrow = if (entersNewBlock) "Cambio de bloque" else blockLabel,
        title = if (entersNewBlock) {
            "Se abre ${nextPart.name}"
        } else {
            "Sigue con ${nextExercise.name}"
        },
        body = if (entersNewBlock) {
            "Cierras ${currentExercise.name} y entras al siguiente bloque con una transicion limpia."
        } else {
            "Despues de esta serie, el flujo continua sin salir del ritmo actual."
        },
        progressLabel = progressLabel,
        nextExerciseName = nextExercise.name,
        nextSetLabel = "Serie 1",
        accentHex = nextPart?.color ?: currentPart?.color,
        feedbackPrompt = feedbackPrompt,
    )
}

internal fun buildWorkoutContinuityTransitionTarget(
    session: Session,
    visibleExercises: List<Exercise>,
    currentExerciseIdx: Int,
): WorkoutContinuityTransitionTarget? {
    if (currentExerciseIdx <= 0) return null
    val currentExercise = visibleExercises.getOrNull(currentExerciseIdx) ?: return null
    val previousExercise = visibleExercises.getOrNull(currentExerciseIdx - 1)
    val currentPart = session.partForExercise(currentExercise.id)
    val previousPart = previousExercise?.let { session.partForExercise(it.id) }
    val entersNewBlock = currentPart?.id != previousPart?.id && currentPart != null
    return WorkoutContinuityTransitionTarget(
        key = "${currentExercise.id}_${currentExerciseIdx}",
        eyebrow = if (entersNewBlock) "Nuevo bloque" else "Siguiente estación",
        title = if (entersNewBlock) {
            "Entras a ${currentPart.name}"
        } else {
            "Ahora sigue ${currentExercise.name}"
        },
        body = if (entersNewBlock) {
            "El flujo cambia de bloque, pero mantienes el ritmo de la sesión."
        } else {
            "La transición se siente limpia: cambias de ejercicio sin perder contexto."
        },
        accentHex = currentPart?.color,
    )
}

internal fun pendingWorkoutFeedbackHandoffExercise(
    visibleExercises: List<Exercise>,
    completedSets: Map<String, CompletedSet>,
    postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
    loggedSetKey: String?,
    currentExerciseId: String?,
): Exercise? {
    val sourceKey = loggedSetKey ?: return null
    val loggedExercise = visibleExercises
        .sortedByDescending { it.id.length }
        .firstOrNull { sourceKey == it.id || sourceKey.startsWith("${it.id}_") }
        ?: return null
    if (loggedExercise.id == currentExerciseId) return null
    if (postExerciseFeedbackByExerciseId.containsKey(loggedExercise.id)) return null
    val isComplete = loggedExercise.sets.isNotEmpty() && loggedExercise.sets.indices.all { setIdx ->
        completedSets.containsKey("${loggedExercise.id}_$setIdx") ||
            (loggedExercise.isUnilateral &&
                completedSets.containsKey("${loggedExercise.id}_${setIdx}_L") &&
                completedSets.containsKey("${loggedExercise.id}_${setIdx}_R"))
    }
    return loggedExercise.takeIf { isComplete }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutContinuityCard(
    state: WorkoutContinuityState,
    onOpenFeedback: (() -> Unit)? = null,
    onDismissFeedbackPrompt: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = continuityAccentColor(state.accentHex, MaterialTheme.colorScheme.primary)
    val icon = when (state.phase) {
        WorkoutContinuityPhase.CURRENT_EXERCISE -> Icons.Default.AutoAwesome
        WorkoutContinuityPhase.SUPERSET -> Icons.Default.SwapHoriz
        WorkoutContinuityPhase.NEXT_EXERCISE, WorkoutContinuityPhase.NEXT_BLOCK -> Icons.AutoMirrored.Filled.TrendingFlat
        WorkoutContinuityPhase.SESSION_FINISH -> Icons.Default.Flag
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier.background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            ),
        ) {
            val isCompactWidth = maxWidth < 360.dp

            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isCompactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = accent)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = state.eyebrow,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                                Text(
                                    text = state.progressLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = continuityPhaseLabel(state.phase),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = accent)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = state.eyebrow,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
                                )
                                Text(
                                    text = state.progressLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = continuityPhaseLabel(state.phase),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = state.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.nextExerciseName != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ) {
                        if (isCompactWidth) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Siguiente punto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = state.nextExerciseName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                state.nextSetLabel?.let { label ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Siguiente punto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = state.nextExerciseName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                state.nextSetLabel?.let { label ->
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                state.feedbackPrompt?.let { prompt ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.10f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                            )
                            if (onOpenFeedback != null || onDismissFeedbackPrompt != null) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    onDismissFeedbackPrompt?.let { dismiss ->
                                        TextButton(onClick = dismiss) {
                                            Text("Luego")
                                        }
                                    }
                                    onOpenFeedback?.let { open ->
                                        TextButton(onClick = open) {
                                            Text("Abrir feedback")
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
}

@Composable
internal fun WorkoutContinuityTransitionBanner(
    target: WorkoutContinuityTransitionTarget?,
    onConsumed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember(target?.key) { mutableStateOf(target != null) }

    LaunchedEffect(target?.key) {
        visible = target != null
        if (target != null) {
            delay(1650L)
            visible = false
            onConsumed?.invoke()
        }
    }

    AnimatedVisibility(
        visible = target != null && visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val accent = continuityAccentColor(target?.accentHex, MaterialTheme.colorScheme.primary)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accent.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = target?.eyebrow.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = target?.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = target?.body.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun nextSupersetHop(
    exercises: List<Exercise>,
    currentIdx: Int,
    currentSetIdx: Int,
): Pair<Int, Int>? {
    val groupId = exercises.getOrNull(currentIdx)?.supersetGroupRefOrLegacyId() ?: return null
    val group = exercises.indices.filter { exercises[it].supersetGroupRefOrLegacyId() == groupId }
    if (group.size <= 1) return null

    val position = group.indexOf(currentIdx)
    if (position < 0) return null

    for (groupPos in (position + 1) until group.size) {
        val candidateExerciseIdx = group[groupPos]
        if (currentSetIdx in exercises[candidateExerciseIdx].sets.indices) {
            return candidateExerciseIdx to currentSetIdx
        }
    }

    return null
}

private fun Session.partForExercise(exerciseId: String): SessionPart? =
    parts.firstOrNull { part -> part.exercises.any { it.id == exerciseId } }

private fun Session.visibleParts(visibleExercises: List<Exercise>): List<SessionPart> {
    val visibleIds = visibleExercises.map { it.id }.toSet()
    return parts.filter { part -> part.exercises.any { it.id in visibleIds } }
}

private fun continuityAccentColor(accentHex: String?, fallback: Color): Color =
    accentHex
        ?.takeIf { it.isNotBlank() }
        ?.let { hex -> runCatching { Color(hex.toColorInt()) }.getOrNull() }
        ?: fallback

private fun continuityPhaseLabel(phase: WorkoutContinuityPhase): String = when (phase) {
    WorkoutContinuityPhase.CURRENT_EXERCISE -> "En curso"
    WorkoutContinuityPhase.SUPERSET -> "Superset"
    WorkoutContinuityPhase.NEXT_EXERCISE -> "Siguiente"
    WorkoutContinuityPhase.NEXT_BLOCK -> "Nuevo bloque"
    WorkoutContinuityPhase.SESSION_FINISH -> "Cierre"
}
