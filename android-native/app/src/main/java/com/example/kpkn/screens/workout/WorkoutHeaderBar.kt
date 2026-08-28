package com.example.kpkn.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.kpknCoverGlass
import com.example.kpkn.ui.components.kpknSheetWhiteFilterChipColors
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors
import com.example.kpkn.screens.sessioneditor.sessionCoverAccentColor
import com.example.kpkn.screens.sessioneditor.sessionCoverColors
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.data.models.SetAdjustmentSuggestion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.RectangleShape

private val PaceChipGreen = Color(0xFF66BB6A)
private val PaceChipRed = Color(0xFFFF5252)
private val PaceChipWhite = Color.White.copy(alpha = 0.85f)

private sealed class PendingSessionTimeChange {
    data class SetMinutes(val minutes: Int) : PendingSessionTimeChange()
    data object Clear : PendingSessionTimeChange()
}

internal fun sessionTimeChipAccent(
    isExceeded: Boolean,
    coachPaceAlert: String?,
): Color = when {
    isExceeded || coachPaceAlert == "excedido" -> PaceChipRed
    coachPaceAlert == "retrasado" || coachPaceAlert == "apurar" -> PaceChipRed
    coachPaceAlert == "adelantado" -> PaceChipGreen
    else -> PaceChipWhite
}

internal fun resolveSessionAccentColor(background: SessionBackground?): Color {
    return sessionCoverAccentColor(background)
}

@Composable
internal fun WorkoutChronometer(
    startTimeMs: Long,
    isComplete: Boolean,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    onSetAbsoluteTimeLimit: (totalMinutes: Int, persistToSession: Boolean) -> Unit = { minutes, _ ->
        onAdjustTimeLimit(minutes - ((sessionTimeRemainingSeconds ?: 0) / 60).coerceAtLeast(0))
    },
    onClearTimeLimit: (persistToSession: Boolean) -> Unit = {},
    pacingAlertMode: PacingAlertMode = PacingAlertMode.FINAL,
    onPacingAlertModeChange: (PacingAlertMode) -> Unit = {},
    currentTargetMinutes: Int? = null,
    sessionHasProgrammedTime: Boolean = false,
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    onUltraFastPreview: (() -> Unit)? = null,
    ultraFastApplied: Boolean = false,
    ultraFastSavedSeconds: Int = 0,
    onRevertUltraFast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember(startTimeMs) { androidx.compose.runtime.mutableIntStateOf(0) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<PendingSessionTimeChange?>(null) }

    LaunchedEffect(startTimeMs, isComplete) {
        if (!isComplete) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(0)
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    LaunchedEffect(showAdjustDialog) {
        if (!showAdjustDialog) pendingChange = null
    }

    val hasLimit = sessionTimeRemainingSeconds != null
    val displayRemaining = sessionTimeRemainingSeconds ?: 0
    val isExceeded = hasLimit && displayRemaining < 0
    val liveTargetMinutes = currentTargetMinutes?.takeIf { it > 0 }
    val resolvedTargetMinutes = liveTargetMinutes
        ?: if (hasLimit) {
            ((elapsedSeconds + displayRemaining.coerceAtLeast(0)) / 60).coerceAtLeast(5)
        } else {
            60
        }
    val targetSeconds = (resolvedTargetMinutes * 60).coerceAtLeast(1)
    val ringProgress = if (hasLimit) {
        (displayRemaining.coerceAtLeast(0).toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val text = if (hasLimit) {
        val absSeconds = kotlin.math.abs(displayRemaining)
        val minutes = absSeconds / 60
        val seconds = absSeconds % 60
        val sign = if (isExceeded) "-" else ""
        "$sign${"%02d:%02d".format(minutes, seconds)}"
    } else {
        formatElapsed(elapsedSeconds)
    }

    val accent = sessionTimeChipAccent(isExceeded = isExceeded, coachPaceAlert = coachPaceAlert)
    val countdownMessage = pacingAlertMessage?.takeIf { it in SessionTimeCues.ALL }

    fun closeOverlay() {
        pendingChange = null
        showAdjustDialog = false
        showTimePicker = false
    }

    fun applyPending(persistToSession: Boolean) {
        when (val pending = pendingChange) {
            is PendingSessionTimeChange.SetMinutes -> onSetAbsoluteTimeLimit(pending.minutes, persistToSession)
            PendingSessionTimeChange.Clear -> onClearTimeLimit(persistToSession)
            null -> Unit
        }
        closeOverlay()
    }

    fun proposeMinutes(minutes: Int) {
        val clamped = minutes.coerceAtLeast(5)
        if (liveTargetMinutes == clamped) {
            pendingChange = null
            return
        }
        pendingChange = PendingSessionTimeChange.SetMinutes(clamped)
    }

    fun proposeClear() {
        if (!hasLimit) return
        if (sessionHasProgrammedTime) {
            pendingChange = PendingSessionTimeChange.Clear
        } else {
            onClearTimeLimit(false)
            closeOverlay()
        }
    }

    val pendingMinutes = (pendingChange as? PendingSessionTimeChange.SetMinutes)?.minutes
    val selectedPreset = pendingMinutes ?: liveTargetMinutes

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(accent.copy(alpha = 0.16f))
            .clickable { showAdjustDialog = true }
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
            if (hasLimit) {
                CircularProgressIndicator(
                    progress = { ringProgress },
                    modifier = Modifier.size(18.dp),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    strokeWidth = 1.6.dp,
                )
            }
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = accent,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
            ),
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
        )
        if (!countdownMessage.isNullOrBlank()) {
            Text(
                text = countdownMessage,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (showAdjustDialog) {
        KpknAlertDialog(
            onDismissRequest = { closeOverlay() },
            title = { Text("Tiempo de sesión", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (hasLimit) {
                            "Restante: ${displayRemaining / 60} min."
                        } else {
                            "Sin límite."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(30, 45, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = selectedPreset == mins,
                                onClick = { proposeMinutes(mins) },
                                label = { Text("$mins") },
                                modifier = Modifier.weight(1f),
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = kpknSheetWhiteTonalButtonColors(),
                    ) {
                        Icon(Icons.Default.Timer, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (hasLimit) {
                                "Tiempo asignado: ${liveTargetMinutes ?: resolvedTargetMinutes} min"
                            } else {
                                "Asignar tiempo"
                            },
                        )
                    }
                    if (hasLimit) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            listOf(-5, 5, 15).forEach { delta ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        proposeMinutes((pendingMinutes ?: resolvedTargetMinutes) + delta)
                                    },
                                    label = { Text(if (delta > 0) "+$delta" else "$delta") },
                                    colors = kpknSheetWhiteFilterChipColors(),
                                )
                            }
                            FilterChip(
                                selected = pendingChange == PendingSessionTimeChange.Clear,
                                onClick = { proposeClear() },
                                label = { Text("Quitar") },
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                        }
                    }
                    if (pendingChange != null) {
                        HorizontalDivider()
                        Text(
                            if (pendingChange == PendingSessionTimeChange.Clear) {
                                "¿Quitar este tiempo?"
                            } else {
                                "¿Guardar este tiempo?"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = { applyPending(false) },
                                label = { Text("Solo esta vez") },
                                modifier = Modifier.weight(1f),
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                            FilterChip(
                                selected = false,
                                onClick = { applyPending(true) },
                                label = { Text("Permanente") },
                                modifier = Modifier.weight(1f),
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                        }
                    }
                    Text("Avisos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            PacingAlertMode.OFF to "Nada",
                            PacingAlertMode.FINAL to "15 y 5 min",
                            PacingAlertMode.SOFT to "Ritmo",
                            PacingAlertMode.STRICT to "Por ejercicio",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = pacingAlertMode == mode,
                                onClick = { onPacingAlertModeChange(mode) },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    var activeTechniqueTooltip by remember { mutableStateOf<String?>(null) }

                    Text(
                        "¿Tienes poco tiempo hoy? Prueba el modo Ultrarrápido",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "El modo ultrarrápido automáticamente comprime tu sesión para terminarla mucho más rápido aplicando drop-sets o rest-pauses y otras técnicas para ahorrar tiempo. Sin embargo, debes considerar que esto tiene un costo mayor en fatiga y carga a tus articulaciones, así que procura recuperarte bien.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Ver conceptos:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                onClick = { activeTechniqueTooltip = if (activeTechniqueTooltip == "dropset") null else "dropset" },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    "Drop-sets ℹ️",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Surface(
                                onClick = { activeTechniqueTooltip = if (activeTechniqueTooltip == "restpause") null else "restpause" },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    "Rest-pauses ℹ️",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (activeTechniqueTooltip != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            if (activeTechniqueTooltip == "dropset") "Drop-set" else "Rest-pause",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            "✕",
                                            modifier = Modifier.clickable { activeTechniqueTooltip = null }.padding(4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = if (activeTechniqueTooltip == "dropset") {
                                            "Consiste en realizar una serie efectiva y, al llegar al fallo o fatiga, reducir el peso de inmediato (un 15-30%) para sacar 3 repeticiones más sin descansar."
                                        } else {
                                            "Consiste en realizar una serie y pausar sólo 15 a 20 segundos para luego continuar realizando mini-series adicionales con el mismo peso."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 16.sp,
                                    )
                                }
                            }
                        }
                    }

                    if (ultraFastApplied) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF66BB6A).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF66BB6A).copy(alpha = 0.28f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Activo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = Color(0xFF66BB6A))
                                    Text(
                                        if (ultraFastSavedSeconds > 0) "Ahorro ~${ultraFastSavedSeconds / 60}m ${ultraFastSavedSeconds % 60}s" else "Aplicado",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (onRevertUltraFast != null) {
                                    OutlinedButton(onClick = { closeOverlay(); onRevertUltraFast() }) { Text("Deshacer") }
                                }
                            }
                        }
                    } else if (onUltraFastPreview != null) {
                        Button(
                            onClick = { closeOverlay(); onUltraFastPreview() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) {
                            Text("Modo Ultrarrápido", fontWeight = FontWeight.Black)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { closeOverlay() }) {
                    Text("Cerrar")
                }
            },
        )
    }

    if (showTimePicker) {
        val pickerMinutes = pendingMinutes ?: resolvedTargetMinutes
        val hours = pickerMinutes / 60
        val minutes = pickerMinutes % 60
        com.example.kpkn.ui.components.KpknNativeTimePickerDialog(
            title = "Duración de sesión",
            initialHour = hours.coerceIn(0, 23),
            initialMinute = minutes.coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { h, m ->
                val total = (h * 60 + m).coerceAtLeast(5)
                showTimePicker = false
                proposeMinutes(total)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
internal fun WorkoutHeaderBar(
    exerciseName: String,
    exerciseChips: List<String> = emptyList(),
    sessionName: String,
    groupName: String?,
    startTimeMs: Long,
    isComplete: Boolean,
    background: SessionBackground?,
    sessionTimeRemainingSeconds: Int?,
    onAdjustTimeLimit: (Int) -> Unit,
    onSetAbsoluteTimeLimit: (totalMinutes: Int, persistToSession: Boolean) -> Unit = { _, _ -> },
    onClearTimeLimit: (persistToSession: Boolean) -> Unit = {},
    pacingAlertMode: PacingAlertMode = PacingAlertMode.FINAL,
    onPacingAlertModeChange: (PacingAlertMode) -> Unit = {},
    currentTargetMinutes: Int? = null,
    sessionHasProgrammedTime: Boolean = false,
    pacingAlertMessage: String? = null,
    coachPaceAlert: String? = null,
    exerciseTag: String? = null,
    isSuperset: Boolean = false,
    exerciseReadiness: ExerciseReadiness? = null,
    activeMainTags: List<WorkoutTag> = emptyList(),
    activeMainTagLabels: Map<String, String> = emptyMap(),
    activeSubTags: List<WorkoutSubTag> = emptyList(),
    onTagClick: (String) -> Unit = {},
    onRemoveSubTag: (String) -> Unit = {},
    onCreateTagClick: () -> Unit = {},
    voiceCaptureMode: com.example.kpkn.data.models.VoiceCaptureMode? = null,
    onVoiceCaptureModeChange: ((com.example.kpkn.data.models.VoiceCaptureMode) -> Unit)? = null,
    voiceSessionEnabled: Boolean = false,
    voiceSessionState: VoiceSessionState = VoiceSessionState(),
    onToggleVoice: (() -> Unit)? = null,
    onUltraFastPreview: (() -> Unit)? = null,
    ultraFastApplied: Boolean = false,
    ultraFastSavedSeconds: Int = 0,
    onRevertUltraFast: (() -> Unit)? = null,
    readinessAdjustment: SetAdjustmentSuggestion? = null,
    onAdaptClick: (() -> Unit)? = null,
    godModeActive: Boolean = false,
    onHistoryClick: (() -> Unit)? = null,
    onReplaceClick: (() -> Unit)? = null,
    onNicknameClick: (() -> Unit)? = null,
    onCreateSupersetClick: (() -> Unit)? = null,
) {
    val colors = remember(background) { sessionCoverColors(background) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val coverHaze = remember { HazeState() }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Gradient Background layer — sibling haze source for cover mica.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .hazeSource(state = coverHaze)
                .background(Brush.linearGradient(colors))
        )

        // Translucent KPKN mica — blurs the session cover, not a nested overlay source.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .kpknCoverGlass(coverHaze, RectangleShape, withBorder = false),
        )

        // Fading mask to Surface color
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to surfaceColor
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) {
                                append(exerciseName)
                            }
                            exerciseChips.forEach { chip ->
                                append(" · ")
                                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) {
                                    append(chip)
                                }
                            }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (godModeActive && onNicknameClick != null) {
                        HeaderCoverChip(label = "Apodo", onClick = onNicknameClick)
                    }
                    }
                    Text(
                        text = buildString {
                            if (!groupName.isNullOrBlank()) append("$groupName · ")
                            append(sessionName)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WorkoutChronometer(
                                startTimeMs = startTimeMs,
                                isComplete = isComplete,
                                sessionTimeRemainingSeconds = sessionTimeRemainingSeconds,
                                onAdjustTimeLimit = onAdjustTimeLimit,
                                onSetAbsoluteTimeLimit = onSetAbsoluteTimeLimit,
                                onClearTimeLimit = onClearTimeLimit,
                                pacingAlertMode = pacingAlertMode,
                                onPacingAlertModeChange = onPacingAlertModeChange,
                                currentTargetMinutes = currentTargetMinutes,
                                sessionHasProgrammedTime = sessionHasProgrammedTime,
                                pacingAlertMessage = pacingAlertMessage,
                                coachPaceAlert = coachPaceAlert,
                                onUltraFastPreview = onUltraFastPreview,
                                ultraFastApplied = ultraFastApplied,
                                ultraFastSavedSeconds = ultraFastSavedSeconds,
                                onRevertUltraFast = onRevertUltraFast,
                            )
                        }

                        // ── Chip de readiness por ejercicio ──
                        if (exerciseReadiness != null) {
                            val score = exerciseReadiness.overallScore
                            val chipColor = when {
                                score >= 75 -> Color(0xFF4CAF50)
                                score >= 50 -> Color(0xFFFFC107)
                                else -> Color(0xFFFF5252)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(chipColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "${score}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                )
                            }
                            val showAdapt = onAdaptClick != null && (
                                readinessAdjustment != null ||
                                    score < ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD
                                )
                            if (showAdapt) {
                                Surface(
                                    onClick = { onAdaptClick?.invoke() },
                                    shape = RoundedCornerShape(99.dp),
                                    color = Color(0xFFFF5252).copy(alpha = 0.18f),
                                ) {
                                    Text(
                                        text = if (readinessAdjustment != null) "Adaptado" else "Adaptar",
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8A80),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            }
                        }

                        if (godModeActive && onReplaceClick != null) {
                            HeaderCoverChip(label = "Reemplazar", onClick = onReplaceClick)
                        }
                        if (godModeActive && onCreateSupersetClick != null && !isSuperset) {
                            HeaderCoverChip(label = "Superserie", onClick = onCreateSupersetClick)
                        }

                        if (isSuperset) {
                            Surface(
                                shape = RoundedCornerShape(99.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Text(
                                        "Superserie",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        // Multi-tag chips (new system)
                        activeMainTags.forEach { tag ->
                            Surface(
                                onClick = { onTagClick(tag.id) },
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = activeMainTagLabels[tag.id] ?: tag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Editar",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                        activeSubTags.forEach { subTag ->
                            Surface(
                                onClick = { onRemoveSubTag(subTag.id) },
                                color = Color.White.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = subTag.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar",
                                        modifier = Modifier.size(10.dp),
                                        tint = Color.White.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                        // Create tag button
                        Surface(
                            onClick = onCreateTagClick,
                            color = Color.Transparent,
                            shape = RoundedCornerShape(99.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Crear etiqueta",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp).size(12.dp),
                                tint = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        // Legacy fallback: show exerciseTag if no active main tags
                        if (activeMainTags.isEmpty() && !exerciseTag.isNullOrBlank()) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(99.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            ) {
                                Text(
                                    text = exerciseTag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onHistoryClick != null) {
                        HeaderCoverIconButton(
                            onClick = onHistoryClick,
                            icon = Icons.Default.History,
                            contentDescription = "Historial",
                        )
                    }
                    if (voiceCaptureMode != null && onVoiceCaptureModeChange != null) {
                        val musicSelected = voiceCaptureMode == com.example.kpkn.data.models.VoiceCaptureMode.MUSIC
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(2.dp),
                        ) {
                            VoiceModeHeaderSegment(
                                label = "Manos libres",
                                selected = !musicSelected,
                                onClick = {
                                    if (musicSelected) onVoiceCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE)
                                },
                            )
                            VoiceModeHeaderSegment(
                                label = "Música",
                                selected = musicSelected,
                                onClick = {
                                    if (!musicSelected) onVoiceCaptureModeChange(com.example.kpkn.data.models.VoiceCaptureMode.MUSIC)
                                },
                            )
                        }
                    }
                    if (onToggleVoice != null) {
                        val voiceButtonFill = when {
                            voiceSessionState.isListening -> Color(0xFF2E7D32).copy(alpha = 0.92f)
                            voiceSessionEnabled -> Color(0xFF1B2838).copy(alpha = 0.92f)
                            else -> Color.Black.copy(alpha = 0.24f)
                        }
                        val voiceButtonBorder = when {
                            voiceSessionState.isListening -> Color(0xFF81C784).copy(alpha = 0.78f)
                            voiceSessionEnabled -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.58f)
                            else -> Color.White.copy(alpha = 0.22f)
                        }
                        HeaderCoverIconButton(
                            onClick = onToggleVoice,
                            icon = if (voiceSessionEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (voiceSessionEnabled) {
                                "Desactivar control por voz"
                            } else {
                                "Activar control por voz"
                            },
                            fill = voiceButtonFill,
                            border = voiceButtonBorder,
                            iconTint = when {
                                voiceSessionState.isListening -> Color.White
                                voiceSessionEnabled -> MaterialTheme.colorScheme.secondary
                                else -> Color.White.copy(alpha = 0.9f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCoverIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    fill: Color = Color.Black.copy(alpha = 0.24f),
    border: Color = Color.White.copy(alpha = 0.22f),
    iconTint: Color = Color.White.copy(alpha = 0.9f),
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = fill,
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun HeaderCoverChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(99.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VoiceModeHeaderSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White.copy(alpha = 0.30f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
        )
    }
}
