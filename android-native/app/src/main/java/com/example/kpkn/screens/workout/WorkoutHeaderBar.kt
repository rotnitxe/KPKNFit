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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.models.ExerciseReadiness
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.data.models.WorkoutSubTag
import com.example.kpkn.data.models.WorkoutTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.kpknSheetWhiteFilterChipColors
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors
import com.example.kpkn.screens.sessioneditor.sessionCoverAccentColor
import com.example.kpkn.ui.components.kpknCoverGlass
import com.example.kpkn.services.workout.VoiceSessionState
import com.example.kpkn.domain.auge.ExerciseReadinessEngine
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.LiveViewportPolicyMath
import com.example.kpkn.data.models.SetAdjustmentSuggestion
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import coil.compose.AsyncImage
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.programCoverEntryIdOf
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.parseProgramStudioCover
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.restyled
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.components.HeroCoverKind
import com.example.kpkn.screens.programdetail.components.heroCoverKind
import com.example.kpkn.ui.components.rememberProgramCover

private const val HeaderCompactScale = 0.80f

/** Decide si el título del ejercicio debe usar el carrusel continuo lento (LV1 punto 1). */
internal fun workoutHeaderShouldUseMarquee(
    textWidth: Int,
    containerWidth: Int,
    reducedMotion: Boolean,
): Boolean = textWidth > containerWidth && !reducedMotion

/** Visible glyphs in the header title (name + " · " chips). */
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

/** Escala del visor (pantalla estrecha o baja). Vale 1 en el formato de referencia. */
@Composable
private fun headerViewportScale(): Float {
    val adapt = LocalViewportAdapt.current
    val policy = LiveViewportPolicyMath.compute(
        widthDp = adapt.widthDp,
        heightDp = adapt.heightDp,
    )
    return adapt.uniformScale * policy.headerMul
}

/** Escala compacta de la cabecera: espaciado y elementos de tamaño fijo, como antes. */
@Composable
private fun headerCompactScale(): Float = HeaderCompactScale * headerViewportScale()

/** Texto de las píldoras de la cabecera: Inter 12 (cifras SemiBold, etiquetas Medium), escalado solo por el visor. */
private fun headerChipStyle(weight: FontWeight, scale: Float): TextStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = weight,
    fontSize = 12.sp * scale,
    lineHeight = 16.sp * scale,
)

/** Escala el tamaño y el interlineado de un estilo por [k]. */
private fun TextStyle.scaledBy(k: Float): TextStyle = copy(
    fontSize = fontSize * k,
    lineHeight = lineHeight * k,
)

private val PaceChipGreen = Color(0xFF66BB6A)
private val PaceChipRed = Color(0xFFFF5252)
private val PaceChipNeutral = WizardColors.text

/** Rojo de señal de la cabecera (Superserie, Adaptar): va como texto sobre la píldora inset. */
private val HeaderSignalRed = Color(0xFFFF8A80)

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
    else -> PaceChipNeutral
}

internal fun resolveSessionAccentColor(background: SessionBackground?): Color {
    return sessionCoverAccentColor(background)
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
    val targetSeconds = (resolvedTargetMinutes * 60).coerceAtLeast(1)
    val ringProgress = if (hasLimit) {
        (displayRemaining.coerceAtLeast(0).toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val text = formatSessionChronometerText(
        hasLimit = hasLimit,
        remainingSeconds = displayRemaining,
        elapsedSeconds = elapsedSeconds,
        targetMinutes = liveTargetMinutes ?: resolvedTargetMinutes.takeIf { hasLimit },
    )

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
            .clip(KpknShape.pill)
            .background(KpknSurface.inset)
            .clickable { showAdjustDialog = true }
            .padding(horizontal = 9.dp * headerCompactScale(), vertical = 5.dp * headerCompactScale()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp * headerCompactScale()),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp * headerCompactScale())) {
            if (hasLimit) {
                CircularProgressIndicator(
                    progress = { ringProgress },
                    modifier = Modifier.size(18.dp * headerCompactScale()),
                    color = accent,
                    trackColor = WizardColors.textFaint.copy(alpha = 0.30f),
                    strokeWidth = 1.6.dp * headerCompactScale(),
                )
            }
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(11.dp * headerCompactScale()),
                tint = accent,
            )
        }
        Text(
            text = text,
            style = headerChipStyle(FontWeight.SemiBold, headerViewportScale()).copy(
                fontFeatureSettings = "tnum",
            ),
            color = accent,
        )
        if (!countdownMessage.isNullOrBlank()) {
            Text(
                text = countdownMessage,
                style = headerChipStyle(FontWeight.Medium, headerViewportScale()),
                color = accent,
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

@Composable
internal fun WorkoutHeaderBar(
    exerciseName: String,
    exerciseChips: List<String> = emptyList(),
    sessionName: String,
    groupName: String?,
    startTimeMs: Long,
    isComplete: Boolean,
    // La portada ya no pinta degradado; el parámetro se conserva para el llamador.
    @Suppress("UNUSED_PARAMETER") background: SessionBackground?,
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
    hasCreatedTags: Boolean = false,
    onTagClick: (String) -> Unit = {},
    onRemoveSubTag: (String) -> Unit = {},
    onOpenTagList: () -> Unit = {},
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
    nicknameKey: String? = null,
    nicknameValue: String = "",
    canonicalExerciseName: String = exerciseName,
    onNicknameChange: ((String) -> Unit)? = null,
    onCreateSupersetClick: (() -> Unit)? = null,
    bodyHazeState: HazeState? = null,
    protocolLabel: String? = null,
    programCover: String? = null,
    program: Program? = null,
    forcedMarqueeMoving: Boolean = false,
) {
    val headerScale = headerCompactScale()
    val typeScale = headerViewportScale()
    fun cs(dp: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = dp * headerScale

    val density = LocalDensity.current
    var headerContentHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxWidth().zIndex(8f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (headerContentHeightPx > 0) {
                        Modifier.height(with(density) { headerContentHeightPx.toDp() })
                    } else {
                        Modifier.matchParentSize()
                    },
                )
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to Color.White,
                            0.70f to Color.White,
                            1.00f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(KpknSurface.canvas),
            )
            // Portada del programa activo como fondo tenue (LV1 punto 13)
            val effectiveCover = program?.coverImage ?: programCover
            if (!effectiveCover.isNullOrBlank()) {
                val bundledCover = rememberProgramCover(programCoverEntryIdOf(effectiveCover), with(density) { 360.dp.roundToPx() })
                val coverKind = heroCoverKind(effectiveCover, hasBundledCover = bundledCover != null)
                val identity = remember(program, effectiveCover) {
                    if (program != null) {
                        coverIdentityForProgram(program)
                    } else {
                        val choice = parseProgramStudioCover(effectiveCover)
                        val base = coverIdentityFor("session_header_program", null)
                        if (choice != null) base.restyled(choice.style, choice.palette) else base
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("workout_header_cover_background")
                        .graphicsLayer { alpha = 0.18f },
                ) {
                    when {
                        coverKind == HeroCoverKind.REMOTE -> {
                            AsyncImage(
                                model = effectiveCover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        coverKind == HeroCoverKind.BUNDLED && bundledCover != null -> {
                            Image(
                                bitmap = bundledCover,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        coverKind == HeroCoverKind.STUDIO || coverKind == HeroCoverKind.GRADIENT -> {
                            CoverStudioArt(
                                identity = identity,
                                modifier = Modifier.fillMaxSize(),
                                animate = false,
                                reduced = true,
                            )
                        }
                    }
                }
            }
            if (bodyHazeState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .kpknCoverGlass(bodyHazeState, RectangleShape, withBorder = false),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerContentHeightPx = it.height }
                .statusBarsPadding()
                .padding(start = cs(16.dp), end = cs(16.dp), top = cs(13.dp), bottom = cs(9.dp)),
            verticalArrangement = Arrangement.spacedBy(cs(4.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val titleGlyphs = workoutHeaderTitleGlyphCount(exerciseName, exerciseChips)
                    val titleLengthScale = workoutHeaderTitleFontScale(titleGlyphs)
                    val titleBase = KpknType.title.scaledBy(typeScale)
                    val titleSlotHeight = with(density) {
                        (KpknType.title.lineHeight * typeScale).toDp()
                    }
                    val reducedMotion = wizardReducedMotion()
                    val titleAnnotated = remember(exerciseName, exerciseChips) {
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = WizardColors.text)) {
                                append(exerciseName)
                            }
                            exerciseChips.forEach { chip ->
                                append(" · ")
                                withStyle(SpanStyle(color = WizardColors.textMuted)) {
                                    append(chip)
                                }
                            }
                        }
                    }
                    val titleStyle = remember(titleBase, titleLengthScale) {
                        titleBase.copy(fontSize = titleBase.fontSize * titleLengthScale)
                    }
                    val measurer = rememberTextMeasurer()
                    val textLayout = remember(titleAnnotated, titleStyle, measurer) {
                        measurer.measure(titleAnnotated, style = titleStyle, maxLines = 1, softWrap = false)
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(titleSlotHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        val containerWidthPx = constraints.maxWidth
                        val textWidthPx = textLayout.size.width
                        val useMarquee = workoutHeaderShouldUseMarquee(
                            textWidth = textWidthPx,
                            containerWidth = containerWidthPx,
                            reducedMotion = reducedMotion,
                        )

                        var isMoving by remember(useMarquee, textWidthPx, containerWidthPx) { mutableStateOf(false) }
                        LaunchedEffect(useMarquee, textWidthPx, containerWidthPx) {
                            if (useMarquee) {
                                delay(1500L)
                                while (true) {
                                    isMoving = true
                                    val moveDurationMs = ((textWidthPx.toFloat() / (30f * density.density)) * 1000L).toLong().coerceAtLeast(1000L)
                                    delay(moveDurationMs)
                                    isMoving = false
                                    delay(2500L)
                                }
                            } else {
                                isMoving = false
                            }
                        }

                        val effectiveMoving = isMoving || forcedMarqueeMoving

                        // Fundido de 16 dp en el borde derecho, y en el izquierdo mientras se mueve (LV1 punto 1)
                        val fadeModifier = if (useMarquee) {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val fadePx = 16.dp.toPx()
                                    // Fundido derecho
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Black,
                                            1f to Color.Transparent,
                                            startX = (size.width - fadePx).coerceAtLeast(0f),
                                            endX = size.width,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    // Fundido izquierdo mientras se mueve
                                    if (effectiveMoving) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                1f to Color.Black,
                                                startX = 0f,
                                                endX = fadePx,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }
                        } else Modifier

                        val marqueeModifier = if (useMarquee) {
                            Modifier
                                .testTag("workout_header_title_marquee")
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    animationMode = MarqueeAnimationMode.Immediately,
                                    initialDelayMillis = 1500,
                                    repeatDelayMillis = 2500,
                                    velocity = 30.dp,
                                )
                        } else Modifier

                        Text(
                            text = titleAnnotated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(fadeModifier)
                                .then(marqueeModifier),
                            style = titleStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (useMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = buildString {
                            if (!protocolLabel.isNullOrBlank()) append("$protocolLabel · ")
                            if (!groupName.isNullOrBlank()) append("$groupName · ")
                            append(sessionName)
                        },
                        style = KpknType.caption.scaledBy(typeScale),
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(cs(8.dp)),
                ) {
                    if (onHistoryClick != null) {
                        KpknIconButton(
                            icon = KpknLineIcons.History,
                            contentDescription = "Historial",
                            onClick = onHistoryClick,
                        )
                    }
                    if (voiceCaptureMode != null && onVoiceCaptureModeChange != null) {
                        val musicSelected = voiceCaptureMode == com.example.kpkn.data.models.VoiceCaptureMode.MUSIC
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(KpknShape.pill)
                                .background(KpknSurface.inset)
                                .padding(cs(2.dp)),
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
                        KpknIconButton(
                            icon = if (voiceSessionEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (voiceSessionEnabled) {
                                "Desactivar control por voz"
                            } else {
                                "Activar control por voz"
                            },
                            onClick = onToggleVoice,
                            tint = when {
                                voiceSessionState.isListening -> WizardColors.done
                                voiceSessionEnabled -> MaterialTheme.colorScheme.secondary
                                else -> null
                            },
                        )
                    }
                }
            }
            // Fila de chips a todo el ancho de la cabecera, debajo del subtítulo: los botones de icono se quedan arriba (LR1a2).
            var openSessionTimeRequest by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Máscara de alfa ANTES del desplazamiento (LR1a2): el fundido cae sobre la ventana visible. Con la máscara después,
                    // caía sobre el contenido entero, que llega fuera de la pantalla, y el chip que no cabe se cortaba con un borde recto.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = size.width - 24.dp.toPx(),
                                endX = size.width,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(cs(6.dp)),
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
                        openSessionTimeRequest = openSessionTimeRequest,
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
                        horizontalArrangement = Arrangement.spacedBy(cs(6.dp)),
                    ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(KpknShape.pill)
                            .background(KpknSurface.inset)
                            .padding(horizontal = cs(9.dp), vertical = cs(5.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(cs(6.dp))
                                .clip(CircleShape)
                                .background(chipColor)
                        )
                        Spacer(Modifier.width(cs(5.dp)))
                        Text(
                            text = "$score %",
                            style = headerChipStyle(FontWeight.SemiBold, typeScale),
                            color = WizardColors.text,
                        )
                    }
                    val showAdapt = onAdaptClick != null && (
                        readinessAdjustment != null ||
                            score < ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD
                        )
                    if (showAdapt) {
                        Surface(
                            onClick = { onAdaptClick?.invoke() },
                            shape = KpknShape.pill,
                            color = KpknSurface.inset,
                        ) {
                            Text(
                                text = if (readinessAdjustment != null) "Adaptado" else "Adaptar",
                                modifier = Modifier.padding(horizontal = cs(9.dp), vertical = cs(5.dp)),
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = HeaderSignalRed,
                            )
                        }
                    }
                    }
                }

                if (ultraFastApplied) {
                    Surface(
                        onClick = { openSessionTimeRequest++ },
                        shape = KpknShape.pill,
                        color = KpknSurface.inset,
                    ) {
                        Text(
                            "ULTRARRÁPIDO",
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            style = headerChipStyle(FontWeight.Medium, typeScale),
                            color = PaceChipGreen,
                            maxLines = 1,
                        )
                    }
                }

                if (isSuperset) {
                    Surface(
                        shape = KpknShape.pill,
                        color = KpknSurface.inset,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = HeaderSignalRed,
                                modifier = Modifier.size(cs(12.dp)),
                            )
                            Text(
                                "Superserie",
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = HeaderSignalRed,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // Multi-tag chips (new system)
                activeMainTags.forEach { tag ->
                    Surface(
                        onClick = { onTagClick(tag.id) },
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Text(
                                text = activeMainTagLabels[tag.id] ?: tag.name,
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = WizardColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Editar",
                                modifier = Modifier.size(cs(12.dp)),
                                tint = WizardColors.textMuted,
                            )
                        }
                    }
                }
                activeSubTags.forEach { subTag ->
                    Surface(
                        onClick = { onRemoveSubTag(subTag.id) },
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(cs(3.dp)),
                        ) {
                            Text(
                                text = subTag.name,
                                style = headerChipStyle(FontWeight.Medium, typeScale),
                                color = WizardColors.textMuted,
                                maxLines = 1,
                            )
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Quitar",
                                modifier = Modifier.size(cs(10.dp)),
                                tint = WizardColors.textFaint,
                            )
                        }
                    }
                }
                if (activeMainTags.isEmpty()) {
                    Surface(
                        onClick = onOpenTagList,
                        color = KpknSurface.inset,
                        shape = KpknShape.pill,
                    ) {
                        Text(
                            text = if (hasCreatedTags) "Etiquetas" else "Sin etiquetas",
                            modifier = Modifier.padding(horizontal = cs(8.dp), vertical = cs(5.dp)),
                            style = headerChipStyle(FontWeight.Medium, typeScale),
                            color = WizardColors.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
            .clip(KpknShape.pill)
            .background(if (selected) WizardColors.text else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp * headerCompactScale(), vertical = 5.dp * headerCompactScale()),
    ) {
        Text(
            text = label,
            color = if (selected) WizardColors.ctaContent else WizardColors.text,
            fontFamily = WizardFonts.body,
            fontSize = 10.sp * headerCompactScale(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
