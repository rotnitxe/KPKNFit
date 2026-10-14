package com.example.kpkn.screens.workout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.design.WorkoutClockLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.kpknSheetWhiteFilterChipColors
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

/** Visible glyphs in the header title (name + " · " chips). Lo usa el nombre del ejercicio de la cabecera (LS2a). */
internal fun workoutHeaderTitleGlyphCount(exerciseName: String, chips: List<String>): Int =
    exerciseName.length + chips.sumOf { 3 + it.length }

/**
 * Shrinks the header title with length so it stays on one line.
 * The header slot height never grows with the exercise name.
 */
internal fun workoutHeaderTitleFontScale(glyphCount: Int): Float = when {
    glyphCount <= 22 -> 1.00f
    glyphCount <= 32 -> 0.88f
    glyphCount <= 44 -> 0.76f
    glyphCount <= 58 -> 0.66f
    else -> 0.58f
}

private sealed class PendingSessionTimeChange {
    data class SetMinutes(val minutes: Int) : PendingSessionTimeChange()
    data object Clear : PendingSessionTimeChange()
}

/**
 * Texto del ritmo bajo la hora (LS2a): lo que ya calcula el controlador de ritmo, sin minutos que no se han calculado. Sin alerta,
 * «Vas a tiempo».
 */
internal fun workoutPaceStatusText(isExceeded: Boolean, coachPaceAlert: String?): String = when {
    isExceeded || coachPaceAlert == "excedido" -> "Tiempo agotado"
    coachPaceAlert == "retrasado" || coachPaceAlert == "apurar" -> "Vas por detrás"
    coachPaceAlert == "adelantado" -> "Vas por delante"
    else -> "Vas a tiempo"
}

/** Color del ritmo: `danger` si va por detrás o se pasó, `ok` si va por delante y `textMuted` si va a tiempo. */
internal fun workoutPaceStatusColor(isExceeded: Boolean, coachPaceAlert: String?): Color = when {
    isExceeded || coachPaceAlert == "excedido" -> WizardColors.danger
    coachPaceAlert == "retrasado" || coachPaceAlert == "apurar" -> WizardColors.danger
    coachPaceAlert == "adelantado" -> SymbolPalette.ok
    else -> WizardColors.textMuted
}

/** Hora transcurrida de la sesión: «24:31», o «1:02:05» pasada la hora. */
internal fun formatElapsedClock(elapsedSeconds: Int): String {
    val total = elapsedSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

@OptIn(ExperimentalLayoutApi::class)
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
    openSessionTimeRequest: Int = 0,
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

    LaunchedEffect(openSessionTimeRequest) {
        if (openSessionTimeRequest > 0) showAdjustDialog = true
    }

    val hasLimit = sessionTimeRemainingSeconds != null
    val displayRemaining = sessionTimeRemainingSeconds ?: 0
    val isExceeded = hasLimit && (
        pacingAlertMessage == SessionTimeCues.EXHAUSTED ||
            coachPaceAlert == "excedido" ||
            (currentTargetMinutes?.takeIf { it > 0 }?.let { elapsedSeconds >= it * 60 } == true)
    )
    val liveTargetMinutes = currentTargetMinutes?.takeIf { it > 0 }
    val resolvedTargetMinutes = liveTargetMinutes
        ?: if (hasLimit) {
            ((elapsedSeconds + displayRemaining.coerceAtLeast(0)) / 60).coerceAtLeast(5)
        } else {
            60
        }
    // La hora de la fila superior es la transcurrida (LS2a); el límite y el ritmo van en la línea de abajo.
    val clock = formatElapsedClock(elapsedSeconds)
    val countdownMessage = pacingAlertMessage?.takeIf { it in SessionTimeCues.ALL }
    // Ritmo: solo con límite de tiempo. Sin límite no hay línea.
    val paceLine = if (hasLimit) countdownMessage ?: workoutPaceStatusText(isExceeded, coachPaceAlert) else null
    val paceColor = workoutPaceStatusColor(isExceeded, coachPaceAlert)

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

    WorkoutClockLabel(
        clock = clock,
        pace = paceLine,
        paceColor = paceColor,
        onClick = { showAdjustDialog = true },
        modifier = modifier,
    )

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
                                label = { Text("$mins min") },
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
                                    label = { Text(if (delta > 0) "+$delta min" else "$delta min") },
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
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
                                colors = kpknSheetWhiteFilterChipColors(),
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    var ultraFastExpanded by remember { mutableStateOf(false) }
                    var activeTechniqueTooltip by remember { mutableStateOf<String?>(null) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ultraFastExpanded = !ultraFastExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "¿Tienes poco tiempo hoy? Prueba el modo Ultrarrápido",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (ultraFastExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (ultraFastExpanded) "Ocultar" else "Mostrar",
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }

                    if (ultraFastExpanded) {
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
                                            "Tras la serie efectiva, baja unos 5 kg —lo bastante para sacar 3 reps más, no más. Si bajas demasiado, deja de ser un dropset."
                                        } else {
                                            "Mismo peso. Solo cambia el descanso: 15 segundos, y luego 3 repeticiones. El pausa corta restringe el rango, no la carga."
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
