package com.example.kpkn.screens.workout.components

import com.example.kpkn.domain.workout.isMyoReps
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import com.example.kpkn.domain.performance.StrengthModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.calculations.estimatePercent1RM
import com.example.kpkn.domain.workout.SetTechniqueScope
import com.example.kpkn.domain.workout.scheduledTechniquePlan
import com.example.kpkn.domain.workout.isInlineEditorScheduledTechnique
import com.example.kpkn.domain.workout.techniqueScope
import com.example.kpkn.domain.workout.volumeReplacedLabel
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.*
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.workout.design.WorkoutVideoChip
import com.example.kpkn.screens.workout.design.WorkoutVideoMenuPositionProvider
import com.example.kpkn.screens.workout.design.WorkoutVideoMenuTag
import com.example.kpkn.screens.workout.design.WorkoutVideoRepsSuggestion
import java.util.Locale

/** Full range of motion; the slider's starting point only for exercises that track ROM. */
private const val DEFAULT_ROM_PERCENT = 100

private data class DropSetEntry(
    val weight: Double,
    val reps: Int,
)

@Composable
private fun VolumeReplacedTechniqueBand(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFE53935).copy(alpha = 0.85f)),
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
            ),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE53935),
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFE53935).copy(alpha = 0.85f)),
        )
    }
}

private fun activeRepRange(set: ExerciseSet, side: String?): RepRange? = when (side) {
    "left" -> set.leftTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    "right" -> set.rightTarget?.targetRepsRange ?: set.targetRepsRange ?: set.effectiveRepRange()
    else -> set.targetRepsRange ?: set.effectiveRepRange()
}

// ─── AMRAP Config Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmrapConfigSheet(
    plannedMinReps: Int?,
    plannedTargetName: String,
    initialReachFailure: Boolean,
    initialReserveReps: Int?,
    onApply: (minReps: Int?, reachFailure: Boolean, reserveReps: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minReps by remember { mutableStateOf(plannedMinReps?.toString() ?: "") }
    var reachFailure by remember { mutableStateOf(initialReachFailure) }
    var reserveReps by remember { mutableStateOf(initialReserveReps?.toString() ?: "") }
    KpknSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Configurar serie AMRAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Repeticiones mínimas", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                OutlinedTextField(value = minReps, onValueChange = { minReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text(plannedMinReps?.toString() ?: "Sin mínimo") }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Objetivo de la serie", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = reachFailure, onClick = { reachFailure = true; reserveReps = "" }, label = { Text("Llegar al fallo", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                    FilterChip(selected = !reachFailure, onClick = { reachFailure = false }, label = { Text("Reservar reps", style = MaterialTheme.typography.labelSmall) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), selectedLabelColor = MaterialTheme.colorScheme.primary, containerColor = Color(0xFF2A2A2A), labelColor = Color.White))
                }
            }
            if (!reachFailure) {
                OutlinedTextField(value = reserveReps, onValueChange = { reserveReps = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("RIR (repeticiones en reserva)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color(0xFF555555), focusedLabelColor = Color.White.copy(alpha = 0.7f), unfocusedLabelColor = Color.White.copy(alpha = 0.5f), cursorColor = Color.White, focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF2A2A2A), unfocusedContainerColor = Color(0xFF2A2A2A)))
            }
            Button(onClick = { onApply(minReps.toIntOrNull()?.takeIf { it > 0 }, reachFailure, reserveReps.toIntOrNull()) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { Text("Aplicar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun WorkoutStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    buttonsEnabled: Boolean = true,
    textInputEnabled: Boolean = true,
    isError: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    roomier: Boolean = false,
    placeholder: String? = null,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
    }
    val controlHeight = if (roomier) 60.dp else 56.dp
    val buttonWidth = if (roomier) 34.dp else 32.dp
    val buttonShape = RoundedCornerShape(12.dp)
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val enabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            },
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .height(controlHeight)
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (roomier) 4.dp else 3.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (roomier) 4.dp else 3.dp, vertical = 5.dp),
        ) {
            Surface(
                modifier = Modifier
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clip(buttonShape)
                    .clickable(enabled = buttonsEnabled, onClick = onDecrement),
                shape = buttonShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (buttonsEnabled) 0.68f else 0.34f),
                contentColor = if (buttonsEnabled) enabledIconColor else disabledColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Disminuir",
                        modifier = Modifier.size(if (roomier) 18.dp else 16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = if (roomier) 72.dp else 64.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.36f)),
                contentAlignment = Alignment.Center,
            ) {
                val resolvedFontSize = remember(value, textStyle.fontSize) {
                    when {
                        value.length >= 9 -> 11.sp
                        value.length >= 7 -> 12.sp
                        value.length >= 5 -> 14.sp
                        value.length == 4 -> 15.sp
                        else -> textStyle.fontSize
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = { if (textInputEnabled) onValueChange(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .onFocusEvent { focusState ->
                            if (focusState.isFocused) {
                                scope.launch { bringIntoViewRequester.bringIntoView() }
                            }
                        },
                    singleLine = true,
                    enabled = textInputEnabled,
                    textStyle = textStyle.copy(
                        fontSize = resolvedFontSize,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Black,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = keyboardOptions,
                    maxLines = 1,
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (value.isBlank() && !placeholder.isNullOrBlank()) {
                                Text(
                                    text = placeholder,
                                    style = textStyle.copy(
                                        fontSize = resolvedFontSize,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    ),
                                    maxLines = 1,
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Surface(
                modifier = Modifier
                    .width(buttonWidth)
                    .fillMaxHeight()
                    .clip(buttonShape)
                    .clickable(enabled = buttonsEnabled, onClick = onIncrement),
                shape = buttonShape,
                color = if (buttonsEnabled) {
                    accentColor.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.34f)
                },
                contentColor = if (buttonsEnabled) accentColor else disabledColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar",
                        modifier = Modifier.size(if (roomier) 18.dp else 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackExtraStepperRow(
    label: String,
    value: Int,
    maxValue: Int,
    accentColor: Color,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDecrement,
                enabled = value > 0,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Reducir $label",
                    modifier = Modifier.size(15.dp),
                    tint = if (value > 0) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f),
                )
            }
            Text(
                value.toString(),
                modifier = Modifier.widthIn(min = 24.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Black,
                color = if (value > 0) accentColor else Color.White.copy(alpha = 0.55f),
            )
            IconButton(
                onClick = onIncrement,
                enabled = value < maxValue,
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Aumentar $label",
                    modifier = Modifier.size(15.dp),
                    tint = if (value < maxValue) accentColor else Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun IntegratedLoadInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String?,
    options: List<QuickLoadChipOption>,
    onChipSelected: (QuickLoadChipOption) -> Unit,
    loadModeMenuExpanded: Boolean,
    onLoadModeMenuOpen: () -> Unit,
    onLoadModeMenuDismiss: () -> Unit,
    onLoadModeSelected: (LoadModeV2) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    loadMode: LoadModeV2 = LoadModeV2.LOAD,
    inlineChipsInField: Boolean = false,
    loadPopTrigger: Any? = null,
    plateHint: com.example.kpkn.domain.training.PlateLoadLabel? = null,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val popScale = remember { Animatable(1f) }

    LaunchedEffect(loadPopTrigger) {
        if (loadPopTrigger != null && loadPopTrigger != 0L) {
            popScale.snapTo(1f)
            popScale.animateTo(1.18f, tween(180, easing = FastOutSlowInEasing))
            popScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    @Composable
    fun LoadValueField(modifier: Modifier = Modifier, shortenLabelOnOverflow: Boolean = false) {
        // En la fila de los ejercicios con ROM el campo es estrecho: si «CARGA (kg)» no cabe, queda «CARGA» en vez de cortarse (LV8).
        var useShortLabel by remember(label) { mutableStateOf(false) }
        val fullLabel = label.uppercase(Locale("es")).replace("(KG)", "(kg)")
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                // La unidad va en minúscula: «CARGA (kg)», no «CARGA (KG)» (LR1a).
                text = plateHint?.compact ?: if (useShortLabel) fullLabel.substringBefore(" (") else fullLabel,
                modifier = if(plateHint == null) Modifier else Modifier.testTag("load-plate-label").semantics { contentDescription = plateHint.text },
                style = KpknType.eyebrow.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = WizardColors.textMuted,
                maxLines = if(plateHint == null) 1 else 2,
                overflow = if(plateHint == null) TextOverflow.Ellipsis else TextOverflow.Clip,
                onTextLayout = { layout ->
                    if (plateHint == null && shortenLabelOnOverflow && !useShortLabel && layout.hasVisualOverflow) useShortLabel = true
                },
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = popScale.value
                        scaleY = popScale.value
                    }
                    .onFocusEvent { focusState ->
                        if (focusState.isFocused) {
                            scope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                readOnly = false,
                enabled = true,
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                // 5.3: La misma tipografía y el mismo tamaño que las cifras de reps e intensidad de la misma tarjeta
                textStyle = KpknType.metricM.copy(
                    fontFamily = WizardFonts.display,
                    fontSize = if (value.length >= 7) 26.sp else 34.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "'tnum' 1",
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (value.isBlank() && !placeholder.isNullOrBlank()) {
                            // 5.3: Mismo estilo de marcador vacío (no cursiva, peso semi-bold y tono tenue como reps)
                            Text(
                                text = placeholder,
                                style = KpknType.metricM.copy(
                                    fontFamily = WizardFonts.display,
                                    fontSize = 34.sp,
                                    lineHeight = 44.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = FontStyle.Normal,
                                    color = WizardColors.textFaint,
                                    textAlign = TextAlign.Center,
                                    fontFeatureSettings = "'tnum' 1",
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            innerTextField()
                        }
                    }
                },
            )
        }
    }

    @Composable
    fun LoadChip(option: QuickLoadChipOption, index: Int, modifier: Modifier = Modifier, wrapContent: Boolean = false) {
        val hasKnownLoad = option.weight > 0 || option.targetLoadMode == LoadModeV2.BODYWEIGHT
        val labelText = when {
            option.isAuge || option.label.equals("Sugerido", ignoreCase = true) -> "Sugerida"
            else -> option.label
        }
        val valueText = if (!hasKnownLoad) "—" else when (option.targetLoadMode) {
            LoadModeV2.BODYWEIGHT -> if (option.weight <= 0.0) "0" else option.weight.toTrimmedNumberString()
            else -> option.weight.toTrimmedNumberString()
        }
        Surface(
            onClick = { onChipSelected(option) },
            enabled = hasKnownLoad,
            modifier = modifier.height(34.dp).testTag("quick_suggestion_chip_$index"),
            shape = RoundedCornerShape(11.dp),
            color = if (option.isAuge) Color(0xFFF49A6E).copy(alpha = .05f) else Color(0xFF111111),
            border = BorderStroke(1.dp, if (option.isAuge) Color(0xFFF49A6E).copy(alpha = .6f) else WizardColors.text.copy(alpha = .10f)),
        ) {
            Row(
                Modifier.fillMaxHeight().then(if (wrapContent) Modifier else Modifier.fillMaxWidth()).padding(horizontal = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labelText, style = KpknType.caption.copy(fontSize = 9.sp, lineHeight = 12.sp),
                    color = if (option.isAuge) Color(0xFFF49A6E) else WizardColors.textMuted,
                    maxLines = 1, softWrap = false)
                Text(valueText, style = KpknType.chip.copy(fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold, fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.text, maxLines = 1, softWrap = false)
            }
        }
    }

    @Composable
    fun QuickSuggestionRow(modifier: Modifier = Modifier) {
        // 5.2: Los tres chips en una fila horizontal, con el mismo ancho (weight(1f)), el mismo alto (<= 44 dp) y 8 dp entre ellos
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.take(3).forEachIndexed { index, option ->
                LoadChip(option = option, index = index, modifier = Modifier.weight(1f))
            }
        }
    }

    @Composable
    fun LoadModeButtonWithMenu() {
        Box {
            Row(
                Modifier.height(20.dp).semantics { contentDescription = "Cambiar tipo de carga" }
                    .clickable(role = Role.Button, onClick = onLoadModeMenuOpen),
                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(when (loadMode) {
                    LoadModeV2.LOAD -> "Externa"
                    LoadModeV2.BODYWEIGHT -> "Corporal"
                    LoadModeV2.LASTRE -> "Lastre"
                    LoadModeV2.ASSISTED -> "Asistida"
                }, style = KpknType.caption.copy(fontSize = 10.sp), color = WizardColors.textMuted)
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(12.dp), tint = WizardColors.textMuted)
            }
            KpknDropdownMenu(
                expanded = loadModeMenuExpanded,
                onDismissRequest = onLoadModeMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text("Carga") },
                    onClick = { onLoadModeSelected(LoadModeV2.LOAD) },
                )
                DropdownMenuItem(
                    text = { Text("Peso corporal") },
                    onClick = { onLoadModeSelected(LoadModeV2.BODYWEIGHT) },
                )
                DropdownMenuItem(
                    text = { Text("Lastre") },
                    onClick = { onLoadModeSelected(LoadModeV2.LASTRE) },
                )
                DropdownMenuItem(
                    text = { Text("Asistido") },
                    onClick = { onLoadModeSelected(LoadModeV2.ASSISTED) },
                )
            }
        }
    }

    // 5.1: La parte de arriba de la tarjeta ES la zona de la carga:
    // superficie #1A1A1A a todo el ancho, esquinas de arriba con radio 24 dp, sin doble borde ni márgenes laterales
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = Color(0xFF1B1B1B),
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth().heightIn(min = if (inlineChipsInField) 96.dp else 132.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (inlineChipsInField) {
                // LV8 (A3): en los ejercicios con ROM los chips van junto al valor. La fila de ROM de abajo ocupa justo el sitio de
                // la fila de chips que se libera aquí (6 dp + 38 dp), y la tarjeta mide lo mismo que la de un ejercicio sin ROM.
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LoadValueField(
                            Modifier
                                .weight(1f)
                                .widthIn(min = 64.dp),
                            shortenLabelOnOverflow = true,
                        )
                        inlineRomLoadOptions(options).forEachIndexed { index, option ->
                            LoadChip(
                                option = option,
                                index = index,
                                modifier = Modifier.widthIn(min = 56.dp),
                                wrapContent = true,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        LoadModeButtonWithMenu()
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    LoadValueField(
                        Modifier
                            .fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        LoadModeButtonWithMenu()
                    }
                }
                QuickSuggestionRow(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Relleno final de «Modo visual» (LR1a2, LV3), en una pantalla de 360 dp. El ✓ flotante mide 58 dp y queda a 16 dp del borde derecho
 * (`WorkoutRecordFabPlacement.kt`): su borde izquierdo está a 286 dp. La tarjeta (272 dp, centrada) termina a 316 dp y su contenido
 * deja 10 dp, así que la píldora acaba a 306 dp: el ✓ le pisa 20 dp. El texto debe acabar 8 dp antes del círculo: 20 + 8 = 28 dp.
 */
private val SetRecordFabEndPadding = 10.dp

/**
 * Alto de la carga (la parte de arriba de la tarjeta) que el panel guiado de drops deja a la vista al ponerse encima (LV8, ley 1): el panel
 * ocupa como máximo el resto de la tarjeta y se desplaza dentro si no cabe.
 */
private val GuidedPanelTopReserve = 112.dp

/** Iniciar o parar el temporizador de un ejercicio por tiempo (LV8): disco `inset` de 48 dp junto al valor; el icono va en tinta o, corriendo, en el acento. */
@Composable
private fun TimerToggleButton(running: Boolean, accentColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(KpknSurface.inset, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (running) "Detener" else "Iniciar",
            modifier = Modifier.size(22.dp),
            tint = if (running) accentColor else WizardColors.text,
        )
    }
}

/**
 * Botón secundario de la tarjeta de la serie (LR1a, LR1a2, LV3): píldora con borde de tinta al 24 %, sin relleno, Inter SemiBold 13 / 16
 * (`KpknType.chip`), compacta de 36 dp de alto en una sola línea («Avanzado», «Modo visual») para no alargar la tarjeta ni quedar
 * bajo el ✓ flotante. Al pulsar escala a 0,97 con resorte y sin ripple.
 */
@Composable
private fun SetSecondaryPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Relleno final de la píldora: el estándar de 10 dp, o el de «Modo visual» para no quedar bajo el ✓ (LR1a2, LV3).
    endPadding: Dp = 10.dp,
    iconOnly: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "setSecondaryPillScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(44.dp)
            .semantics { contentDescription = text }
            .clip(KpknShape.pill)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = 10.dp, end = endPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = WizardColors.text)
            if (!iconOnly) Text(text, style = KpknType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = WizardColors.text, maxLines = 1, softWrap = false)
        }
    }
}

/** V3 keeps the same single row and accessible labels when secondary actions need icons. */
@Composable
internal fun LiveSetCardActions(onAdvanced: () -> Unit, onVisual: () -> Unit, onHistory: () -> Unit, modifier: Modifier = Modifier) {
    val shortScreen = com.example.kpkn.screens.workout.LocalLiveSessionCompactLayout.current || LocalConfiguration.current.screenHeightDp < 800
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp).testTag("live-card-actions")) {
        val icons = shortScreen || maxWidth < 260.dp || fontScale > 1.2f
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SetSecondaryPill("Avanzado", LiveCardReferenceIcons.Advanced, onAdvanced,
                Modifier.weight(1f).testTag("secondary_pill_avanzado"), iconOnly = icons)
            SetSecondaryPill("Modo visual", LiveCardReferenceIcons.Camera, onVisual,
                Modifier.weight(1f).testTag("secondary_pill_modo_visual"), iconOnly = icons)
            SetSecondaryPill("Historial del ejercicio", KpknLineIcons.History, onHistory,
                Modifier.width(44.dp).testTag("secondary_pill_historial"), iconOnly = true)
        }
    }
}

private fun inlineRomLoadOptions(options: List<QuickLoadChipOption>): List<QuickLoadChipOption> {
    if (options.isEmpty()) return emptyList()
    val picked = ArrayList<QuickLoadChipOption>(2)
    options.firstOrNull { it.isAuge }?.let { picked += it }
    options.firstOrNull { option -> option.label.startsWith("+") && option !in picked }?.let { picked += it }
    if (picked.size < 2) {
        options.firstOrNull { it !in picked }?.let { picked += it }
    }
    return picked.take(2)
}

private fun quickLoadIncrementFor(exercise: Exercise, currentSet: ExerciseSet): Double =
    com.example.kpkn.screens.workout.calibrationIncrement(exercise, currentSet)

@Composable
internal fun WorkoutMiniTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = keyboardOptions,
        singleLine = true,
        modifier = modifier
            .heightIn(min = 48.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusEvent { focusState ->
                if (focusState.isFocused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
        shape = WorkoutUiTokens.InnerCardShape,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            focusedLabelColor = accentColor,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            cursorColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
            unfocusedContainerColor = WorkoutUiTokens.setInnerHighestColor(),
        )
    )
}

private enum class SetCardFace { Front, Technique, ExerciseMedia }

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun SetInputCardV2(
    exercise: Exercise,
    setIndex: Int,
    currentSet: ExerciseSet,
    ghostSet: CompletedSet?,
    sessionCompletedSet: CompletedSet? = null,
    weightSuggestion: WeightSuggestion? = null,
    previousSessionFirstSetWeight: Double? = null,
    thisSessionPreviousWorkingWeight: Double? = null,
    sessionAccentColor: Color = MaterialTheme.colorScheme.primary,
    isJustLogged: Boolean = false,
    lastOutcomeV2: SetOutcomeV2? = null,
    lastHomologatedResultV3: HomologatedPerformanceResult? = null,
    showPRsInWorkout: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    onShowHistory: () -> Unit,
    onSetBodyWeight: (Double) -> Unit,
    initialBodyWeight: Double?,
    recordActionHolder: RecordActionHolder,
    recordActionScopeOwner: Any? = null,
    recordActionPageKey: String? = null,
    recordFabHolder: RecordFabHolder? = null,
    isActivePage: Boolean = true,
    isSettledPage: Boolean = true,
    initialDraft: WorkoutSetDraft? = null,
    onDraftChange: (WorkoutSetDraft, String?) -> Unit = { _, _ -> },
    onExecutionError: ((SetAdvancedFeedback) -> Unit)? = null,
    onRevertExecutionError: (() -> Unit)? = null,
    persistedLoadModeBySet: Map<String, LoadModeV2> = emptyMap(),
    persistedLoadModeByExercise: Map<String, LoadModeV2> = emptyMap(),
    activeTag: String? = null,
    activeSide: String? = null,
    sideLocked: Boolean = false,
    rmSuggestedWeight: Double? = null,
    onRmWeightConsumed: (() -> Unit)? = null,
    adaptActionHolder: AdaptActionHolder? = null,
    onRecordV2: (
        loadMode: LoadModeV2,
        unitMode: UnitModeV2,
        weight: Double,
        value: Double,
        intensity: Double?,
        advanced: SetAdvancedFeedback,
        amrapOverride: Boolean,
        bodyWeight: Double?,
        side: String?,
        onResult: (RecordSetResult) -> Unit,
    ) -> Unit,
    godModeActionsVisible: Boolean = false,
    isDropSet: Boolean = false,
    isRestPause: Boolean = false,
    canDeleteSet: Boolean = true,
    onDropSet: (() -> Unit)? = null,
    onRestPause: (() -> Unit)? = null,
    onDeleteSet: (() -> Unit)? = null,
    onOmitSet: (() -> Unit)? = null,
    mediaCapture: com.example.kpkn.screens.workout.WorkoutMediaCaptureController? = null,
    onRetryPendingCaptures: () -> Unit = {},
    openMediaFace: Boolean = false,
    onMediaFaceConsumed: () -> Unit = {},
    sessionMilestones: List<com.example.kpkn.data.models.SessionMilestone> = emptyList(),
    loadPopTrigger: Any? = null,
    /** Fija el fotograma del fuego del slider de esfuerzo (hojas de render y pruebas); nulo, el fuego corre solo. */
    effortFireSecondsOverride: Float? = null,
    videoAnalysis: com.example.kpkn.screens.workout.SetVideoAnalysisUi? = null,
    onVideoCorrect: (com.example.kpkn.screens.workout.SetVideoAnalysisState.Mismatch) -> Unit = {},
    onVideoMarkFailure: () -> Unit = {},
    onOpenAnalysis: (String) -> Unit = {},
    onRetryAnalysis: ((String) -> Unit)? = null,
    videoThumbBadges: Map<String, com.example.kpkn.screens.workout.design.VideoThumbBadge> = emptyMap(),
    techniqueVideoSettings: com.example.kpkn.data.models.TechniqueVideoSettings = com.example.kpkn.data.models.TechniqueVideoSettings(),
    /** CAL3 may render a result inside the existing frame, without owning the recorder. */
    modeResultContent: (@Composable (LiveSetCardMode) -> Unit)? = null,
    amrapResultPanel: (@Composable (() -> Unit) -> Unit)? = null,
    clockNowMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    equipmentInventory: com.example.kpkn.data.models.EquipmentInventory? = null,
) {
    val context = LocalContext.current
    var editingAmrapResult by remember(exercise.id, setIndex, activeSide, sessionCompletedSet) { mutableStateOf(false) }
    val showAmrapResult = amrapResultPanel != null && !editingAmrapResult &&
        sessionCompletedSet?.techniqueStatus != com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN
    val myoParams = currentSet.plannedIntensityTechniques.firstOrNull { it.type == TechniqueType.REST_PAUSE }?.params.orEmpty()
    val activeRestReps = if(currentSet.isMyoReps()) (myoParams["reps"]?.toIntOrNull() ?: 4).coerceIn(3,5) else RestPausePlanDefaults.Reps
    val activeRestPauseSeconds = if(currentSet.isMyoReps()) (myoParams["pauseSeconds"]?.toIntOrNull() ?: 15).coerceIn(10,30) else RestPausePlanDefaults.PauseSeconds
    val recordActionInstanceOwner = remember { Any() }
    val isNarrowScreen = LocalLivePagerShouldReflow.current
    val resolvedWeightSuggestion = weightSuggestion.takeUnless { exercise.trainingMode == TrainingMode.SOLO_RPE }
    val suggestedWeightText: String? = resolvedWeightSuggestion?.let { suggestion ->
        suggestion.suggestedWeight.toTrimmedNumberString()
    }
    val completedWeightText = sessionCompletedSet
        ?.let { it.originalLoad ?: it.weight }
        ?.takeIf { it > 0.0 }
        ?.toLiveNumberString()
    // Confirmed assignments reach the recorder independently of their display label.
    val calibrationWeight = resolvedWeightSuggestion?.takeIf { it.isCalibrationAssignment }?.suggestedWeight
    val defaultWeight: String = when {
        completedWeightText != null -> completedWeightText
        calibrationWeight != null -> calibrationWeight.toLiveNumberString()
        exercise.trainingMode == TrainingMode.SOLO_RPE -> ""
        currentSet.targetPercentageRM != null -> {
            val baseText = liveRmValues(exercise, currentSet, currentSet.targetPercentageRM,
                liveRmReference(exercise, ghostSet)).weight?.toLiveNumberString()
                ?: currentSet.weight?.takeIf { it > 0 }?.toLiveNumberString()
            baseText.orEmpty()
        }
        else -> {
            val baseText = ghostSet?.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
                ?: currentSet.weight?.takeIf { it > 0 }?.toTrimmedNumberString()
            baseText.orEmpty()
        }
    }
    val resolvedPlannedUnitMode = liveSetUnitMode(exercise, currentSet)
    val liveMode = liveSetCardMode(exercise, currentSet, resolvedPlannedUnitMode)
    val legacyModeLayout = liveMode == LiveSetCardMode.REPS || liveMode == LiveSetCardMode.AMRAP
    val plannedRepRange = activeRepRange(currentSet, activeSide)
    val plannedAmrap = currentSet.purpose.isAmrap
    // AMRAP is not a failure report by default.  A failure is recorded only
    // after the athlete explicitly chooses "Llegar al fallo" in the sheet;
    // otherwise the result remains AMRAP with an optional RIR reserve.
    val defaultAmrapReachFailure = sessionCompletedSet?.let {
        it.actualIntensityMode == IntensityMode.FAILURE || it.isFailure
    } ?: false
    val defaultValue = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> sessionCompletedSet?.let { completedLiveQuantity(it, resolvedPlannedUnitMode) }?.toLiveNumberString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> sessionCompletedSet?.let { completedLiveQuantity(it, resolvedPlannedUnitMode) }?.takeIf { it > 0 }?.toLiveNumberString()
        UnitModeV2.REPS -> sessionCompletedSet?.let { completed ->
            completed.reps.takeIf { it > 0 || (it == 0 && (completed.partialReps ?: 0) > 0) }?.toString()
        }
    }.orEmpty()
    val plannedValueGhost = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> (currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt() ?: ghostSet?.timeSeconds)?.toString()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> currentSet.plannedTargetV2?.toLiveNumberString() ?: plannedRepRange?.max?.toString() ?: ghostSet?.reps?.toString()
        UnitModeV2.REPS -> (
            if (plannedAmrap) currentSet.amrapMinimumReps else plannedRepRange?.max
                ?: currentSet.plannedTargetV2?.toInt()
                ?: ghostSet?.reps
        )?.toString()
    }.orEmpty()
    val isTimeMode = resolvedPlannedUnitMode == UnitModeV2.TIME
    val initialValueGhost = when {
        liveMode == LiveSetCardMode.SOLO_RPE -> sequenceOf(plannedRepRange?.max, currentSet.plannedTargetV2?.toInt(), ghostSet?.reps)
            .firstOrNull { it != null && it > 0 }?.toString() ?: "8"
        resolvedPlannedUnitMode != UnitModeV2.REPS -> "0"
        else -> plannedValueGhost
    }
    val basePlannedTarget = when (resolvedPlannedUnitMode) {
        UnitModeV2.TIME -> currentSet.targetDuration ?: currentSet.plannedTargetV2?.toInt()
        UnitModeV2.DISTANCE,
        UnitModeV2.CUSTOM,
        -> currentSet.plannedTargetV2?.toInt() ?: plannedRepRange?.max
        UnitModeV2.REPS -> if (plannedAmrap) currentSet.amrapMinimumReps else plannedRepRange?.max ?: currentSet.plannedTargetV2?.toInt()
    }
    // Null means the author left the effort target unapplied.  The effort slider
    // still shows on every strength set (LV8): there the scale simply has no plan
    // mark and the athlete must still pick a value before the set is recorded.
    // IntensityMode.LOAD under TrainingMode.RM is a legacy marker, not effort reporting.
    val isRmLoadPrescription = exercise.trainingMode == TrainingMode.RM ||
        (currentSet.intensityMode == IntensityMode.LOAD && currentSet.targetPercentageRM != null)
    fun effortIntensityMode(mode: IntensityMode?): IntensityMode? =
        if (mode == IntensityMode.LOAD && isRmLoadPrescription) null else mode
    val plannedIntensityMode: IntensityMode? = when {
        plannedAmrap -> IntensityMode.AMRAP
        liveMode == LiveSetCardMode.SOLO_RPE -> IntensityMode.RPE
        effortIntensityMode(currentSet.intensityMode) != null -> effortIntensityMode(currentSet.intensityMode)
        currentSet.targetRIR != null -> IntensityMode.RIR
        currentSet.targetRPE != null -> IntensityMode.RPE
        currentSet.isFailure -> IntensityMode.FAILURE
        currentSet.targetPercentageRM != null -> IntensityMode.SOLO_RM
        effortIntensityMode(currentSet.leftTarget?.intensityMode) != null -> effortIntensityMode(currentSet.leftTarget?.intensityMode)
        effortIntensityMode(currentSet.rightTarget?.intensityMode) != null -> effortIntensityMode(currentSet.rightTarget?.intensityMode)
        currentSet.leftTarget?.targetRIR != null || currentSet.rightTarget?.targetRIR != null -> IntensityMode.RIR
        currentSet.leftTarget?.targetRPE != null || currentSet.rightTarget?.targetRPE != null -> IntensityMode.RPE
        else -> null
    }

    val supportsIndependentSides = exercise.isEffectivelyUnilateral()
    val lockedSide = activeSide?.takeIf { supportsIndependentSides && sideLocked }
    // Un parche persistido puede conservar el id. Su snapshot nuevo prevalece sobre el borrador anterior;
    // con el mismo snapshot las recomposiciones no reinicializan lo que la persona está editando.
    val draftBaseline = remember(exercise.id, setIndex, lockedSide) { sessionCompletedSet }
    val restoredDraft = initialDraft.takeIf { sessionCompletedSet == draftBaseline }
    val initialSelectedSide = restoredDraft?.selectedSide ?: lockedSide ?: "left"
    val draftWeightText = restoredDraft?.weightText?.takeIf { it.isNotBlank() }
    val draftValueText = restoredDraft?.valueText?.takeIf { it.isNotBlank() }
    val volumeReplacedTechnique = currentSet.techniqueScope() == SetTechniqueScope.VOLUME_REPLACED
    val initialTechniqueWeight = if (volumeReplacedTechnique) {
        (currentSet.weight?.takeIf { it > 0 } ?: suggestedWeightText?.toDoubleOrNull()?.takeIf { it > 0 })?.toTrimmedNumberString()
    } else null
    // Marked chains already store one three-rep follow-up per set.  An inline
    // editor plan still needs the original main-set target; its three-rep
    // follow-ups are filled only after the main phase is recorded.
    val initialTechniqueValue = if (
        volumeReplacedTechnique &&
        resolvedPlannedUnitMode == UnitModeV2.REPS &&
        !currentSet.isInlineEditorScheduledTechnique()
    ) "3" else null

    var weightText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(draftWeightText ?: completedWeightText ?: initialTechniqueWeight ?: "")
    }
    var lastAutoFilledWeight by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) { mutableStateOf(defaultWeight) }
    var hasManualWeightOverride by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(restoredDraft?.weightManuallyFixed ?: (!draftWeightText.isNullOrBlank() || completedWeightText != null))
    }
    var valueText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(draftValueText ?: initialTechniqueValue ?: defaultValue.ifBlank { initialValueGhost })
    }
    val targetLeftWeight = currentSet.leftTarget?.weight?.toTrimmedNumberString().orEmpty()
    val targetRightWeight = currentSet.rightTarget?.weight?.toTrimmedNumberString().orEmpty()
    val initialLeftWeight = if (initialSelectedSide == "left") (draftWeightText ?: completedWeightText ?: initialTechniqueWeight).orEmpty() else ""
    val initialRightWeight = if (initialSelectedSide == "right") (draftWeightText ?: completedWeightText ?: initialTechniqueWeight).orEmpty() else ""
    fun sideTargetValueText(target: UnilateralTarget?): String = when {
        target == null -> plannedValueGhost
        isTimeMode -> target.targetDuration?.toString() ?: plannedValueGhost
        currentSet.unitModeV2 == UnitModeV2.DISTANCE || currentSet.unitModeV2 == UnitModeV2.CUSTOM ->
            target.targetValue?.toTrimmedNumberString() ?: target.targetReps?.toString() ?: plannedValueGhost
        else -> target.targetRepsRange?.max?.toString() ?: target.targetReps?.toString() ?: plannedValueGhost
    }
    val initialLeftValue = ""
    val initialRightValue = ""
    val leftValueGhost = sideTargetValueText(currentSet.leftTarget)
    val rightValueGhost = sideTargetValueText(currentSet.rightTarget)
    var leftWeightText by remember(exercise.id, setIndex, sessionCompletedSet) { mutableStateOf(initialLeftWeight) }
    var rightWeightText by remember(exercise.id, setIndex, sessionCompletedSet) { mutableStateOf(initialRightWeight) }
    var leftValueText by remember(exercise.id, setIndex, sessionCompletedSet) {
        mutableStateOf(
            if (initialSelectedSide == "left") {
                (draftValueText ?: initialTechniqueValue ?: defaultValue).ifBlank { if (initialValueGhost == "0") "0" else leftValueGhost }
            } else {
                initialTechniqueValue ?: if (initialValueGhost == "0") "0" else leftValueGhost
            },
        )
    }
    var rightValueText by remember(exercise.id, setIndex, sessionCompletedSet) {
        mutableStateOf(
            if (initialSelectedSide == "right") {
                (draftValueText ?: initialTechniqueValue ?: defaultValue).ifBlank { if (initialValueGhost == "0") "0" else rightValueGhost }
            } else {
                initialTechniqueValue ?: if (initialValueGhost == "0") "0" else rightValueGhost
            },
        )
    }
    val activeSideTarget = when (lockedSide) {
        "left" -> currentSet.leftTarget
        "right" -> currentSet.rightTarget
        else -> null
    }
    val plannedTarget = if (isTimeMode) {
        activeSideTarget?.targetDuration ?: basePlannedTarget
    } else {
        basePlannedTarget
    }
    val plannedIntensityGhost = (
        activeSideTarget?.targetRPE?.toTrimmedNumberString()
            ?: activeSideTarget?.targetRIR?.toString()
            ?: currentSet.targetRPE?.toTrimmedNumberString()
            ?: currentSet.targetRIR?.toString()
    ).orEmpty()
    var intensityText by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(
            restoredDraft?.intensityText
                ?: sessionCompletedSet?.actualIntensityValue?.toTrimmedNumberString()
                ?: sessionCompletedSet?.rpe?.toTrimmedNumberString()
                ?: ""
        )
    }
    // H-VERIF (LV8): el esfuerzo es obligatorio y nunca llega preseleccionado. Una intensidad solo cuenta como «reserva cumplida» para
    // proponer una subida si la persona la marcó: un borrador con un valor o con el fallo, o una serie ya registrada, ya están reportados.
    var intensityTouched by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(
            sessionCompletedSet?.let { it.recordedPayloadV3?.intensityAdjusted ?: true }
                ?: (restoredDraft?.let { !it.intensityText.isNullOrBlank() || it.reachedFailure == true } == true)
        )
    }
    var bodyWeightText by remember(exercise.id) { mutableStateOf(initialBodyWeight?.toTrimmedNumberString().orEmpty()) }
    var showBodyWeightPrompt by remember(exercise.id) { mutableStateOf(false) }
    // ROM is reported only by exercises that track it (the slider is hidden
    // otherwise).  Without tracking there is no default: a hidden 100 would be
    // sent as a fake measurement on every set.  A value already stored on a
    // completed set is kept so editing it never erases recorded data.
    val tracksRom = exercise.trackRom
    val pristineRomValue: Int? = sessionCompletedSet?.rom ?: (if (tracksRom) DEFAULT_ROM_PERCENT else null)
    var romValue by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet, tracksRom) {
        mutableStateOf<Int?>((if (tracksRom) restoredDraft?.rom else null) ?: pristineRomValue)
    }
    var selectedSide by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(initialSelectedSide) }
    val sideKey = if (supportsIndependentSides) lockedSide ?: selectedSide else "B"
    var quantityError by remember(exercise.id, setIndex, sideKey) { mutableStateOf<String?>(null) }
    val pristineRmReference = liveRmReference(exercise, ghostSet)
    val prescribedRmPercent = currentSet.targetPercentageRM ?: estimatePercent1RM(currentSet.plannedRepAnchor() ?: 1)
    val pristineRmLoadReference = liveRmValues(exercise, currentSet, prescribedRmPercent, pristineRmReference).loadReference
    val pristineRmPercent = completedWeightText?.replace(',', '.')?.toDoubleOrNull()?.let { weight ->
        pristineRmLoadReference?.let { weight / it * 100 }
    } ?: prescribedRmPercent
    var rmReference by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) { mutableStateOf(restoredDraft?.rmReference1RM ?: pristineRmReference) }
    var rmPercent by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        val reference = liveRmValues(exercise, currentSet, pristineRmPercent, rmReference).loadReference
        val restoredWeight = draftWeightText?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        mutableDoubleStateOf(restoredDraft?.rmPercent ?: if (restoredWeight != null && reference != null) restoredWeight / reference * 100 else pristineRmPercent)
    }
    val activeRmWeightText = if (!supportsIndependentSides) weightText else if (selectedSide == "left") leftWeightText else rightWeightText
    val rmValues = liveRmValues(exercise, currentSet, rmPercent, rmReference, activeRmWeightText.replace(',', '.').toDoubleOrNull())
    val clearTechniqueStateOnSideChangeRef = remember { arrayOf<(() -> Unit)?>(null) }

    fun valueTextForSide(side: String): String = if (side == "left") leftValueText else rightValueText
    fun weightTextForSide(side: String): String = if (side == "left") leftWeightText else rightWeightText
    fun updateActiveValueText(newValue: String) {
        quantityError = null
        valueText = newValue
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftValueText = newValue
            } else {
                rightValueText = newValue
            }
        }
    }
    fun updateActiveWeightText(newWeight: String, markManual: Boolean = true) {
        quantityError = null
        if (markManual) hasManualWeightOverride = true
        weightText = newWeight
        if (supportsIndependentSides) {
            if (selectedSide == "left") {
                leftWeightText = newWeight
            } else {
                rightWeightText = newWeight
            }
        }
    }
    fun selectSide(side: String) {
        if (selectedSide == side) return
        selectedSide = side
        if (supportsIndependentSides) {
            valueText = valueTextForSide(side)
            weightText = weightTextForSide(side)
        }
        // Limpieza explícita tras cambiar de lado para evitar contaminación L→R (fix #1 ALTO)
        // Nota: los estados técnicos están keyeados por sideKey (Triple) por lo que R nace limpio;
        // esta invocación refuerza limpieza por si quedara retención.
        clearTechniqueStateOnSideChangeRef[0]?.invoke()
    }
    LaunchedEffect(lockedSide) {
        lockedSide?.let { selectSide(it) }
    }

    val persistedLoadMode = resolvePersistedLoadModeForSet(
        exerciseId = exercise.id,
        setIdx = setIndex,
        tagId = activeTag,
        persistedLoadModeBySet = persistedLoadModeBySet,
        persistedLoadModeByExercise = persistedLoadModeByExercise,
    )
    var loadMode by remember(exercise.id, setIndex, persistedLoadMode, currentSet.loadModeV2) {
        mutableStateOf(
            resolveEffectiveLoadMode(
                draftLoadMode = restoredDraft?.loadMode,
                persistedLoadMode = persistedLoadMode,
                plannedLoadMode = currentSet.loadModeV2,
                defaultCatalogMode = null,
            ),
        )
    }
    val manualMarkerForSide = currentSet.manualLoadRequiredSides.any { requiredSide ->
        requiredSide == "bilateral" || requiredSide == selectedSide
    }
    val sidePlanWeight = if (supportsIndependentSides) {
        val target = if (selectedSide == "left") currentSet.leftTarget else currentSet.rightTarget
        target?.weight ?: if (target == null) currentSet.weight else null
    } else {
        currentSet.weight
    }
    val manualLoadRequiredNow = liveMode == LiveSetCardMode.SOLO_RPE || manualMarkerForSide ||
        (exercise.nativeProgressionManaged && sidePlanWeight == null && loadMode != LoadModeV2.BODYWEIGHT)
    val plannedWeightGhost = if (manualLoadRequiredNow) {
        null
    } else if (calibrationWeight != null) {
        calibrationWeight.toLiveNumberString()
    } else if (liveMode == LiveSetCardMode.RM) {
        rmValues.weight?.toLiveNumberString() ?: sidePlanWeight?.takeIf { it > 0 }?.toLiveNumberString()
    } else {
        sidePlanWeight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
            ?: ghostSet?.weight?.takeIf { it > 0.0 }?.toTrimmedNumberString()
    }
    LaunchedEffect(loadMode) {
        if (loadMode != LoadModeV2.ASSISTED) showBodyWeightPrompt = false
    }
    LaunchedEffect(calibrationWeight, sessionCompletedSet, hasManualWeightOverride) {
        if (calibrationWeight != null && sessionCompletedSet == null && !hasManualWeightOverride) {
            updateActiveWeightText(calibrationWeight.toLiveNumberString(), markManual = false)
        }
    }
    // LV8: el fallo del plan es solo una marca sobre la pista del esfuerzo. La serie empieza sin marcar y el fallo se elige como cualquier
    // otro valor; una intensidad que la persona no eligió nunca se registra.
    var reachedFailure by remember(exercise.id, setIndex, sessionCompletedSet) {
        mutableStateOf(restoredDraft?.reachedFailure ?: (sessionCompletedSet?.isFailure == true))
    }
    LaunchedEffect(
        currentSet.isDropSet,
        currentSet.isRestPause,
        currentSet.weight,
        currentSet.targetReps,
        currentSet.isFailure,
        sessionCompletedSet,
        volumeReplacedTechnique,
    ) {
        if (sessionCompletedSet != null) return@LaunchedEffect
        if (!volumeReplacedTechnique) return@LaunchedEffect
        // A scheduled technique is a volume policy, not an implicit failure
        // report.  The effort stays unmarked until the athlete picks it (LV8):
        // not even a set planned as FAILURE starts in FALLO.
        val techniqueWeight = (
            currentSet.weight?.takeIf { it > 0.0 }
                ?: suggestedWeightText?.toDoubleOrNull()?.takeIf { it > 0.0 }
        )?.toTrimmedNumberString()
        if (techniqueWeight != null) {
            updateActiveWeightText(techniqueWeight, markManual = false)
        }
    }
    var isFailedSet by remember(exercise.id, setIndex, sideKey, sessionCompletedSet) {
        mutableStateOf(sessionCompletedSet?.isFailedSet == true)
    }
    var failedSetReason by remember(exercise.id, setIndex, sideKey, sessionCompletedSet) {
        mutableStateOf(
            sessionCompletedSet?.failureReason?.takeIf { it != "execution_error" && it != "Serie marcada como fallida" } ?: ""
        )
    }
    val initialAmrapActive = resolveAmrapActive(currentSet, requestedOverride = false,
        explicitOverride = restoredDraft?.amrapOverride, recordedSet = sessionCompletedSet)
    val initialAmrapMinimumReps = resolveAmrapMinimumReps(currentSet, restoredDraft, sessionCompletedSet)
    var isAmrap by remember(exercise.id, setIndex, sideKey, restoredDraft?.amrapOverride, sessionCompletedSet?.purpose) {
        mutableStateOf(initialAmrapActive)
    }
    var showAmrapSheet by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var amrapReachFailure by remember(
        exercise.id,
        setIndex,
        sideKey,
        sessionCompletedSet,
        restoredDraft?.amrapReachFailure,
    ) {
        mutableStateOf(restoredDraft?.amrapReachFailure ?: defaultAmrapReachFailure)
    }
    var amrapReserveReps by remember(exercise.id, setIndex, sideKey, restoredDraft?.amrapReserveReps) {
        mutableStateOf(restoredDraft?.amrapReserveReps)
    }
    var amrapMinimumReps by remember(exercise.id, setIndex, sideKey, initialAmrapMinimumReps) {
        mutableStateOf(initialAmrapMinimumReps)
    }
    var dropSetEnabled by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var restPauseEnabled by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    val restoredPartialReps = if (!supportsIndependentSides || selectedSide == initialSelectedSide) {
        (restoredDraft?.partialReps ?: sessionCompletedSet?.partialReps ?: 0).coerceAtLeast(0)
    } else 0
    var showPartialsMode by remember(exercise.id, setIndex, sideKey, sessionCompletedSet) { mutableStateOf(restoredPartialReps > 0) }
    var showFeedbackExtrasPopup by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var assistedRepsValue by remember(exercise.id, setIndex, sessionCompletedSet) {
        mutableIntStateOf(restoredDraft?.assistedReps ?: sessionCompletedSet?.assistedReps ?: 0)
    }
    var adjustmentsTab by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(-1) }
    var cardFace by remember(exercise.id, setIndex, sideKey) { mutableStateOf(SetCardFace.Front) }
    LaunchedEffect(openMediaFace) {
        if (openMediaFace) {
            cardFace = SetCardFace.ExerciseMedia
            onMediaFaceConsumed()
        }
    }
    var setNoteText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(restoredDraft?.notes.orEmpty())
    }
    var loadModeMenuExpanded by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    var dropSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(DropSetEntry(weight = 0.0, reps = 0)))
    }
    var restPauseSets by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(listOf(RestPauseData(restTime = activeRestPauseSeconds, reps = 0, style = if(currentSet.isMyoReps()) "myo" else null)))
    }
    var partialSets by remember(exercise.id, setIndex, sideKey, sessionCompletedSet) {
        mutableStateOf(listOf(restoredPartialReps))
    }
    val savedTechniqueProgress = restoredDraft?.techniqueProgress
    var guidedPhase by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.toGuidedPhaseOrNull())
    }
    var guidedMainCapture by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.mainCapture.toGuidedMainCaptureOrNull())
    }
    var guidedDropDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<DropSetEntry>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_DROP) {
                savedTechniqueProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
            } else emptyList(),
        )
    }
    var guidedRestPauseDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<RestPauseData>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE) {
                savedTechniqueProgress.restPauseRows.orEmpty()
            } else emptyList(),
        )
    }
    var guidedAwaitingCommit by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(
            savedTechniqueProgress?.awaitingCommit == true &&
                savedTechniqueProgress.kind in setOf(
                    WorkoutTechniqueDraftKind.GUIDED_DROP,
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                ),
        )
    }
    var guidedCommitDropOverride by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.commitDropRows?.map { DropSetEntry(it.weight, it.reps) })
    }
    var guidedCommitRestPauseOverride by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.commitRestPauseRows)
    }
    var guidedDropWeightText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.dropWeightText.orEmpty())
    }
    var guidedDropRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.dropRepsText ?: RestPausePlanDefaults.Reps.toString())
    }
    var guidedRestPauseRepsText by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.restPauseRepsText ?: activeRestReps.toString())
    }
    // Scheduled editor techniques run in the same card controls.  We keep the
    // phase cursor and captured rows locally until the final phase so the
    // pager never creates phantom sets or mounts the manual guided panel.
    var scheduledPhaseIndex by remember(exercise.id, setIndex, sideKey) {
        mutableIntStateOf(
            savedTechniqueProgress
                ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
                ?.phaseIndex ?: 0,
        )
    }
    var scheduledAwaitingCommit by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(savedTechniqueProgress?.awaitingCommit == true)
    }
    var scheduledMainCapture by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf(
            savedTechniqueProgress
                ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
                ?.mainCapture.toGuidedMainCaptureOrNull(),
        )
    }
    var scheduledDropDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<DropSetEntry>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP) {
                savedTechniqueProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
            } else emptyList(),
        )
    }
    var scheduledRestPauseDrafts by remember(exercise.id, setIndex, sideKey) {
        mutableStateOf<List<RestPauseData>>(
            if (savedTechniqueProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE) {
                savedTechniqueProgress.restPauseRows.orEmpty()
            } else emptyList(),
        )
    }
    var scheduledRestRemainingSeconds by remember(exercise.id, setIndex, sideKey) {
        mutableIntStateOf(savedTechniqueProgress?.restRemainingSeconds ?: 0)
    }
    var isRecordSubmissionPending by remember(exercise.id, setIndex, sideKey) { mutableStateOf(false) }
    LaunchedEffect(
        currentSet?.id,
        currentSet?.plannedIntensityTechniques,
        currentSet?.isDropSet,
        currentSet?.isRestPause,
        sideKey,
    ) {
        // Reset explícito al cambiar de lado para evitar contaminación L→R (fix #1 ALTO)
        dropSetEnabled = false
        restPauseEnabled = false
        dropSets = listOf(DropSetEntry(weight = 0.0, reps = 0))
        restPauseSets = listOf(RestPauseData(restTime = activeRestPauseSeconds, reps = activeRestReps, style = if(currentSet.isMyoReps()) "myo" else null))
        partialSets = listOf(restoredPartialReps)
        isAmrap = initialAmrapActive
        showAmrapSheet = false
        amrapReachFailure = restoredDraft?.amrapReachFailure ?: defaultAmrapReachFailure
        amrapReserveReps = restoredDraft?.amrapReserveReps
        amrapMinimumReps = initialAmrapMinimumReps
        showPartialsMode = restoredPartialReps > 0
        showFeedbackExtrasPopup = false
        isFailedSet = false
        val savedProgress = restoredDraft?.techniqueProgress
        guidedPhase = savedProgress?.toGuidedPhaseOrNull()
        guidedMainCapture = savedProgress?.mainCapture.toGuidedMainCaptureOrNull()
        guidedDropDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_DROP) {
            savedProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
        } else emptyList()
        guidedRestPauseDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE) {
            savedProgress.restPauseRows.orEmpty()
        } else emptyList()
        guidedAwaitingCommit = savedProgress?.awaitingCommit == true &&
            savedProgress.kind in setOf(
                WorkoutTechniqueDraftKind.GUIDED_DROP,
                WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
            )
        guidedCommitDropOverride = savedProgress?.commitDropRows?.map { DropSetEntry(it.weight, it.reps) }
        guidedCommitRestPauseOverride = savedProgress?.commitRestPauseRows
        guidedDropWeightText = savedProgress?.dropWeightText.orEmpty()
        guidedDropRepsText = savedProgress?.dropRepsText ?: RestPausePlanDefaults.Reps.toString()
        guidedRestPauseRepsText = savedProgress?.restPauseRepsText ?: activeRestReps.toString()
        scheduledPhaseIndex = savedProgress
            ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
            ?.phaseIndex ?: 0
        scheduledAwaitingCommit = savedProgress?.awaitingCommit == true
        scheduledMainCapture = savedProgress
            ?.takeIf { it.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP || it.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE }
            ?.mainCapture.toGuidedMainCaptureOrNull()
        scheduledDropDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_DROP) {
            savedProgress.dropRows.orEmpty().map { DropSetEntry(it.weight, it.reps) }
        } else emptyList()
        scheduledRestPauseDrafts = if (savedProgress?.kind == WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE) {
            savedProgress.restPauseRows.orEmpty()
        } else emptyList()
        scheduledRestRemainingSeconds = savedProgress?.restRemainingSeconds ?: 0
        adjustmentsTab = -1
        loadModeMenuExpanded = false

        val plannedDrop = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.DROP_SET
        }
        val plannedRestPause = currentSet?.plannedIntensityTechniques?.firstOrNull {
            it.type == TechniqueType.REST_PAUSE
        }
        val stacked = currentSet?.techniqueScope() == SetTechniqueScope.STACKED_ON_SET
        dropSetEnabled = stacked && (
            plannedDrop != null ||
                currentSet?.isDropSet == true
            )
        restPauseEnabled = stacked && (
            plannedRestPause != null ||
                currentSet?.isRestPause == true
            )
        if (dropSetEnabled) {
            val existing = currentSet?.dropSets?.takeIf { it.isNotEmpty() }?.map {
                DropSetEntry(weight = it.weight, reps = it.reps)
            }
            if (existing != null) {
                dropSets = existing
            } else {
                val count = (plannedDrop?.params?.get("count")?.toIntOrNull() ?: 1).coerceIn(1, 3)
                dropSets = List(count) { DropSetEntry(weight = 0.0, reps = 0) }
            }
        }
        if (restPauseEnabled) {
            val existing = currentSet?.restPauses?.takeIf { it.isNotEmpty() }
            if (existing != null) {
                restPauseSets = existing
            } else {
                val count = (plannedRestPause?.params?.get("count")?.toIntOrNull() ?: 2).coerceIn(1, 5)
                restPauseSets = List(count) {
                    RestPauseData(
                        restTime = activeRestPauseSeconds,
                        reps = activeRestReps,
                        style = if(currentSet.isMyoReps()) "myo" else null,
                    )
                }
            }
        }
    }
    LaunchedEffect(guidedPhase, isSettledPage) {
        if (!isSettledPage) return@LaunchedEffect
        val phase = guidedPhase
        if (phase is GuidedTechniquePhase.RestPauseCountdown) {
            var left = phase.secondsLeft
            while (left > 0) {
                delay(1_000)
                left -= 1
                val still = guidedPhase
                if (still !is GuidedTechniquePhase.RestPauseCountdown || still.index != phase.index) {
                    return@LaunchedEffect
                }
                guidedPhase = still.copy(secondsLeft = left)
            }
            val after = guidedPhase
            if (after is GuidedTechniquePhase.RestPauseCountdown && after.index == phase.index) {
                guidedPhase = GuidedTechniquePhase.RestPauseReps(after.index, after.total)
                guidedRestPauseRepsText = activeRestReps.toString()
            }
        }
    }
    LaunchedEffect(scheduledRestRemainingSeconds, isSettledPage) {
        if (!isSettledPage) return@LaunchedEffect
        if (scheduledRestRemainingSeconds <= 0) return@LaunchedEffect
        delay(1_000)
        scheduledRestRemainingSeconds = (scheduledRestRemainingSeconds - 1).coerceAtLeast(0)
    }
    fun resolveDefaultReportedIntensityMode(): IntensityMode? = when {
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        sessionCompletedSet?.actualIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        sessionCompletedSet?.actualIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        liveMode == LiveSetCardMode.SOLO_RPE -> IntensityMode.RPE
        currentSet.targetRIR != null || plannedIntensityMode == IntensityMode.RIR -> IntensityMode.RIR
        plannedIntensityMode == IntensityMode.FAILURE -> IntensityMode.FAILURE
        plannedIntensityMode == IntensityMode.RPE -> IntensityMode.RPE
        // %RM prescribes the load, not the perceived effort.  Keep the
        // effort selector empty until the athlete explicitly picks RPE, RIR
        // or FALLO; never manufacture an RPE 8 value.
        isRmLoadPrescription -> null
        else -> plannedIntensityMode
    }
    var reportedIntensityMode by remember(exercise.id, setIndex, sessionCompletedSet) {
        mutableStateOf(resolveDefaultReportedIntensityMode())
    }
    var timerState by remember(exercise.id, setIndex, sideKey, plannedTarget) {
        mutableStateOf(LiveSetClockState(accumulatedMs = if (isTimeMode) (parseLiveDurationSeconds(valueText) ?: 0) * 1000L else 0L))
    }
    var timerNowMs by remember(exercise.id, setIndex, sideKey) { mutableLongStateOf(clockNowMillis()) }
    val timerRunning = timerState.running
    val timerElapsedSeconds = timerState.seconds(timerNowMs, plannedTarget)
    val timerRemainingSeconds = ((plannedTarget ?: 0) - timerElapsedSeconds).coerceAtLeast(0)
    fun toggleSetTimer() {
        val now = clockNowMillis()
        timerNowMs = now
        if (timerState.running) {
            timerState = timerState.stop(now, plannedTarget)
            updateActiveValueText(timerState.seconds(now, plannedTarget).toString())
        } else {
            timerState = timerState.start(now, restart = timerState.complete(now, plannedTarget))
        }
    }

    // Wiring para limpiar estado técnico al cambiar de lado (fix #1 ALTO: evita contaminación L→R)
    // La limpieza se invoca desde selectSide() antes de cambiar selectedSide.
    clearTechniqueStateOnSideChangeRef[0] = {
        dropSetEnabled = false
        restPauseEnabled = false
        dropSets = listOf(DropSetEntry(weight = 0.0, reps = 0))
        restPauseSets = listOf(RestPauseData(restTime = activeRestPauseSeconds, reps = activeRestReps, style = if(currentSet.isMyoReps()) "myo" else null))
        partialSets = listOf(0)
        isAmrap = currentSet.purpose.isAmrap
        showAmrapSheet = false
        amrapReachFailure = defaultAmrapReachFailure
        amrapReserveReps = null
        showPartialsMode = false
        isFailedSet = false
        guidedPhase = null
        guidedMainCapture = null
        guidedDropDrafts = emptyList()
        guidedRestPauseDrafts = emptyList()
        guidedDropWeightText = ""
        guidedDropRepsText = RestPausePlanDefaults.Reps.toString()
        guidedRestPauseRepsText = activeRestReps.toString()
        scheduledPhaseIndex = 0
        scheduledMainCapture = null
        scheduledDropDrafts = emptyList()
        scheduledRestPauseDrafts = emptyList()
        scheduledRestRemainingSeconds = 0
        adjustmentsTab = -1
        loadModeMenuExpanded = false
        timerState = LiveSetClockState()
        timerNowMs = clockNowMillis()
    }

    val achievedValue = liveQuantityNumber(valueText, resolvedPlannedUnitMode) ?: 0.0
    val repEvaluation = if (!isTimeMode) {
        evaluateRepRange(
            actual = achievedValue,
            range = plannedRepRange ?: plannedTarget?.toInt()?.let { RepRange(it, it) },
            amrapActive = isAmrap,
            amrapMinimum = amrapMinimumReps,
        )
    } else {
        null
    }
    val targetDelta = if (isTimeMode) {
        plannedTarget?.toDouble()?.let { achievedValue - it }
    } else {
        repEvaluation?.delta
    }
    val debt = if (isTimeMode) {
        ((plannedTarget?.toDouble() ?: 0.0) - achievedValue).coerceAtLeast(0.0)
    } else {
        repEvaluation?.debt ?: 0.0
    }

    val activePlannedRpe = activeSideTarget?.targetRPE ?: currentSet.targetRPE
        ?: (activeSideTarget?.targetRIR ?: currentSet.targetRIR)?.takeIf { liveMode == LiveSetCardMode.SOLO_RPE }?.let { 10.0 - it }
    val activePlannedRir = activeSideTarget?.targetRIR ?: currentSet.targetRIR

    // ── Esfuerzo (LV8): la escala de siempre, sin preseleccionar el plan ──────────────────────────────────────────
    // La lista es la que daba el carrusel para el modo con el que EMPIEZA la serie y no cambia mientras la persona elige.
    val effortScale = remember(
        plannedIntensityMode,
        currentSet.targetRIR,
        isRmLoadPrescription,
        sessionCompletedSet,
        activePlannedRir,
        activePlannedRpe,
    ) {
        buildEffortScale(
            plannedIntensityMode = plannedIntensityMode,
            reportedIntensityMode = resolveDefaultReportedIntensityMode(),
            targetRir = activePlannedRir,
            targetRpe = activePlannedRpe,
        )
    }
    // La marca elegida (nula = sin marcar). Una serie ya registrada, o un borrador con un valor o con el fallo, llegan marcados.
    var effortIndex by remember(exercise.id, setIndex, lockedSide, sessionCompletedSet) {
        mutableStateOf(
            restoreEffortIndex(
                items = effortScale.items,
                reachedFailure = reachedFailure,
                intensityText = intensityText,
                mode = reportedIntensityMode,
            ),
        )
    }
    var effortHintVisible by remember(exercise.id, setIndex, lockedSide) { mutableStateOf(false) }
    var effortWobbleKey by remember(exercise.id, setIndex, lockedSide) { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    // Hasta que se marca el esfuerzo, el modo de la escala sigue al plan; ni el texto ni el fallo se rellenan solos.
    LaunchedEffect(
        currentSet.intensityMode,
        currentSet.targetRPE,
        currentSet.targetRIR,
        currentSet.isFailure,
        plannedIntensityMode,
        isRmLoadPrescription,
    ) {
        if (sessionCompletedSet != null || effortIndex != null) return@LaunchedEffect
        val next = resolveDefaultReportedIntensityMode()
        if (next != reportedIntensityMode) reportedIntensityMode = next
    }
    // Si el plan cambia la escala (otra lista), una marca que ya no existe se descarta.
    LaunchedEffect(effortScale.items.size) {
        if ((effortIndex ?: 0) >= effortScale.items.size) effortIndex = null
    }
    // El aviso de «marca tu esfuerzo» se retira solo a los pocos segundos.
    LaunchedEffect(effortWobbleKey) {
        if (effortWobbleKey > 0) {
            delay(3_000)
            effortHintVisible = false
        }
    }
    val repsWheelCenterIndex = remember(exercise.id, setIndex, sideKey, sessionCompletedSet) { mutableIntStateOf(0) }
    var repsInputGeneration by remember(exercise.id, setIndex, sideKey) { mutableIntStateOf(0) }
    fun onEffortSelected(index: Int) {
        val item = effortScale.items.getOrNull(index) ?: return
        val selection = intensitySelectionFromCarouselItem(item)
        effortIndex = index
        effortHintVisible = false
        intensityTouched = true
        reachedFailure = selection.reachedFailure
        if (selection.reportedIntensityMode != null) {
            reportedIntensityMode = selection.reportedIntensityMode
        }
        intensityText = selection.intensityText
        // En una serie AMRAP la marca se guarda como fallo o como reserva: el AMRAP no registra un RPE suelto.
        if (isAmrap) {
            val amrap = amrapEffortFor(item)
            amrapReachFailure = amrap.reachedFailure
            amrapReserveReps = amrap.reserveReps
        }
    }
    // El ✓ se pulsó sin haber marcado el esfuerzo: vibra suave, el slider se balancea y sale el aviso bajo él.
    fun requireEffort() {
        effortHintVisible = true
        effortWobbleKey += 1
        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.Reject)
    }
    // Tras registrar un lado, el otro empieza sin marcar: una intensidad que la persona no eligió nunca se registra.
    fun resetEffortForNextSide() {
        effortIndex = null
        effortHintVisible = false
        reachedFailure = false
        intensityText = ""
        intensityTouched = false
        if (isAmrap) {
            amrapReachFailure = false
            amrapReserveReps = null
        }
    }
    val chosenEffortItem = effortIndex?.let { effortScale.items.getOrNull(it) }
    val hasChosenReportedEffort = chosenEffortItem?.let { it.isFailure || it.numericValue != null } == true
    // Lo que se registra de la marca elegida: el fallo es 10 y la marca «—» (SOLO_RM) no lleva valor. Sin marcar, nada.
    val chosenEffortValue: Double? = when {
        chosenEffortItem == null -> null
        chosenEffortItem.isFailure -> 10.0
        else -> chosenEffortItem.numericValue
    }
    val effortAnimate = rememberEffortAnimationAllowed(isSettledPage && isActivePage)

    // El teclado y `toTrimmedNumberString` escriben la coma: se lee como punto antes de convertir.
    val registeredIntensity = intensityText.replace(',', '.').toDoubleOrNull()
    val expectedIntensity = when (reportedIntensityMode) {
        null -> null
        IntensityMode.RIR -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.toDouble()
            else -> activePlannedRpe?.let { (10.0 - it).coerceIn(0.0, 10.0) }
        }
        else -> when (plannedIntensityMode) {
            IntensityMode.FAILURE -> null
            IntensityMode.RIR -> activePlannedRir?.let { (10.0 - it).coerceIn(0.0, 10.0) }
            IntensityMode.RPE -> activePlannedRpe
            else -> null
        }
    }
    val intensityDelta = if (expectedIntensity != null && registeredIntensity != null) {
        if (reportedIntensityMode == IntensityMode.RIR) {
            expectedIntensity - registeredIntensity
        } else {
            registeredIntensity - expectedIntensity
        }
    } else {
        null
    }

    val difficultyLabel = when {
        reachedFailure -> "Fallo alcanzado"
        isFailedSet -> "Serie fallida"
        intensityDelta == null -> null
        intensityDelta <= -0.5 -> "Más fácil"
        intensityDelta >= 0.5 -> "Más difícil"
        else -> "Igual"
    }
    val expectedIntensityLabel = when {
        isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "FALLO"
        plannedIntensityMode == IntensityMode.RIR -> "RIR"
        plannedIntensityMode == IntensityMode.RPE -> "RPE"
        isRmLoadPrescription -> "%RM"
        else -> "RPE"
    }
    val expectedIntensityValue = when {
        isAmrap -> "AMRAP"
        plannedIntensityMode == IntensityMode.FAILURE -> "F"
        plannedIntensityMode == IntensityMode.RIR -> activePlannedRir?.toString() ?: "-"
        plannedIntensityMode == IntensityMode.RPE -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
        isRmLoadPrescription -> currentSet.targetPercentageRM?.toTrimmedNumberString() ?: "-"
        else -> activePlannedRpe?.toTrimmedNumberString() ?: "-"
    }
    val plannedIntensityDisplayLabel = if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
        when (reportedIntensityMode) {
            IntensityMode.RIR -> "RIR"
            else -> "RPE"
        }
    } else {
        expectedIntensityLabel
    }
    val plannedIntensityDisplayValue = if (plannedIntensityMode == IntensityMode.FAILURE && !reachedFailure) {
        intensityText.ifBlank { "-" }
    } else {
        expectedIntensityValue
    }
    val isExecutionError = isFailedSet
    val intensityFieldLabel = when {
        isExecutionError -> "Error"
        reachedFailure -> "F"
        reportedIntensityMode == IntensityMode.RIR -> "RIR"
        reportedIntensityMode == IntensityMode.RPE -> "RPE"
        else -> ""
    }
    val loadFieldLabel = when (loadMode) {
        LoadModeV2.LOAD -> "Carga (kg)"
        LoadModeV2.BODYWEIGHT -> "Peso corporal"
        LoadModeV2.LASTRE -> "Lastre (kg)"
        LoadModeV2.ASSISTED -> "Asistencia (kg)"
    }
    val timerTargetSeconds = plannedTarget ?: valueText.toIntOrNull() ?: 0
    val isPrGlobal = lastHomologatedResultV3?.isGlobalPr == true
    val isPrContext = lastHomologatedResultV3?.isContextPr == true
    val isPrSeries = LiveSetPrPolicy.isPrSeries(
        lastHomologatedResultV3 = lastHomologatedResultV3,
        sessionCompletedSet = sessionCompletedSet,
        exerciseId = exercise.id,
        milestones = sessionMilestones,
    )

    LaunchedEffect(isJustLogged, isPrGlobal, isPrContext, hapticFeedbackEnabled) {
        if (isJustLogged && showPRsInWorkout && hapticFeedbackEnabled && (isPrGlobal || isPrContext)) {
            triggerPRCelebrationHaptic(context)
        }
    }
    LaunchedEffect(rmSuggestedWeight) {
        if (rmSuggestedWeight != null) {
            updateActiveWeightText(rmSuggestedWeight.toLiveNumberString())
            onRmWeightConsumed?.invoke()
        }
    }
    LaunchedEffect(timerRunning, isSettledPage) {
        if (!isTimeMode || !isSettledPage) return@LaunchedEffect
        while (timerState.running) {
            timerNowMs = clockNowMillis()
            updateActiveValueText(timerState.seconds(timerNowMs, plannedTarget).toString())
            if (timerState.complete(timerNowMs, plannedTarget)) {
                timerState = timerState.stop(timerNowMs, plannedTarget)
            } else delay(100)
        }
    }

    val reportWeightText = if (supportsIndependentSides) weightTextForSide(selectedSide) else weightText
    val reportValueText = if (supportsIndependentSides) valueTextForSide(selectedSide) else valueText
    val timeGoalComplete = isTimeMode && !timerRunning && plannedTarget != null && plannedTarget > 0 &&
        (parseLiveDurationSeconds(reportValueText) ?: 0) >= plannedTarget
    val targetPlateLoad = reportWeightText.replace(',','.').toDoubleOrNull() ?: plannedWeightGhost?.replace(',','.')?.toDoubleOrNull()
    val plateHint by produceState<com.example.kpkn.domain.training.PlateLoadLabel?>(null,targetPlateLoad,loadMode,equipmentInventory,exercise.catalogConfigurationId,currentSet.loadQuantityConvention,exercise.loadQuantityConvention) {
        value = null
        if(loadMode == LoadModeV2.LOAD && targetPlateLoad != null && equipmentInventory?.barbellWeightKg != null) {
            value = withContext(Dispatchers.Default) {
                val catalog = try { withContext(Dispatchers.IO) { com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache.getOrLoad(context).catalog } }
                    catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch(_: Exception) { null }
                val configuration = catalog?.families?.flatMap { it.definitions }?.flatMap { it.configurations }?.firstOrNull { it.id == exercise.catalogConfigurationId }
                val equipmentId = configuration?.profile?.equipmentId
                val convention = currentSet.loadQuantityConvention.takeIf { it != com.example.kpkn.data.models.LoadQuantityConvention.UNSPECIFIED }
                    ?: exercise.loadQuantityConvention.takeIf { it != com.example.kpkn.data.models.LoadQuantityConvention.UNSPECIFIED }
                    ?: com.example.kpkn.domain.training.NativeLoadConventions.forEquipment(equipmentId)
                com.example.kpkn.domain.training.PlateLoadLabels.build(targetPlateLoad,convention,
                    com.example.kpkn.domain.training.NativeLoadConventions.stockKindFor(equipmentId,exercise.catalogConfigurationId.orEmpty()),equipmentInventory)
            }
        }
    }
    val guidedSkipAction = remember(exercise.id, setIndex, sideKey) {
        arrayOf<(() -> Unit)?>(null)
    }
    val density = LocalDensity.current
    val workingSetVisualHeightHolder = LocalLivePagerWorkingSetVisualHeightPx.current
    val stableCardHeight = liveSessionCardHeight()
    fun selectLiveLoadMode(selectedMode: LoadModeV2) {
        if (loadMode != selectedMode) updateActiveWeightText("")
        loadMode = selectedMode
        showBodyWeightPrompt = selectedMode == LoadModeV2.ASSISTED && bodyWeightText.isBlank()
        loadModeMenuExpanded = false
    }
    fun applyRmPercent(next: Double) {
        rmPercent = if (rmValues.usesTrainingMax) next.coerceAtLeast(.01) else next.coerceIn(40.0, 100.0)
        liveRmValues(exercise, currentSet, rmPercent, rmReference).weight?.let { updateActiveWeightText(it.toLiveNumberString()) }
    }
    val quickLoadOptions = quickLoadOptionsFor(
        loadMode = loadMode,
        currentWeightText = reportWeightText,
        suggestedWeight = resolvedWeightSuggestion?.takeIf { suggestion ->
            if (isBodyweightLoadSpectrum(loadMode)) suggestion.suggestedLoadMode == null || isBodyweightLoadSpectrum(suggestion.suggestedLoadMode)
            else suggestion.suggestedLoadMode == null || suggestion.suggestedLoadMode == LoadModeV2.LOAD
        }?.suggestedWeight,
        suggestedLoadMode = resolvedWeightSuggestion?.suggestedLoadMode,
        previousSessionFirstSetWeight = thisSessionPreviousWorkingWeight?.takeIf { it > 0.0 } ?: previousSessionFirstSetWeight,
        loadIncrementKg = quickLoadIncrementFor(exercise, currentSet),
        previousSessionTagLabel = activeTag,
    )
    @Composable fun ModeLoadButton() {
        LiveLoadModeButton(loadMode, loadModeMenuExpanded, { loadModeMenuExpanded = true }, { loadModeMenuExpanded = false }, ::selectLiveLoadMode,
            quickLoads = if (liveMode == LiveSetCardMode.SOLO_RPE) emptyList() else quickLoadOptions,
            onQuickLoad = { option ->
                selectLiveLoadMode(loadModeAfterChipSelection(option))
                updateActiveWeightText(if (option.targetLoadMode == LoadModeV2.BODYWEIGHT) "" else option.weight.toLiveNumberString())
                if (liveMode == LiveSetCardMode.RM && option.weight > 0) rmValues.loadReference?.let { rmPercent = option.weight / it * 100 }
            })
    }
    val flipRotation by animateFloatAsState(
        targetValue = if (cardFace != SetCardFace.Front) 180f else 0f,
        animationSpec = if (isSettledPage) tween(durationMillis = 420) else snap(),
        label = "setCardFlip",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(stableCardHeight)
            .testTag("set_execution_card"),
    ) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize()
            .testTag("live-set-input")
            .graphicsLayer {
                rotationY = flipRotation
                cameraDistance = 18f * density.density
            },
    ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        if (cardFace == SetCardFace.Front && size.height > 0) {
                            publishLivePagerWorkingSetVisualHeight(workingSetVisualHeightHolder, size.height)
                        }
                    }
                    .graphicsLayer { alpha = if (flipRotation <= 90f) 1f else 0f }
                    .blur(
                        when {
                            isFailedSet -> 7.dp
                            godModeActionsVisible -> 6.dp
                            else -> 0.dp
                        },
                    ),
                // Módulo KPKN (LR1a): radio 24 dp, relleno `module` y filete blanco al 7 %, sin sombra ni brillo.
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF111111),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, KpknSurface.moduleBorder),
            ) {
            // La caja deja poner ENCIMA de la tarjeta lo que antes se insertaba y la alargaba (ley 1, LV8): ver los avisos de abajo.
            Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .then(if (showAmrapResult) Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {}
                    } else Modifier)
                    .then(if (legacyModeLayout) Modifier.padding(bottom = 64.dp).verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
            if (currentSet.plannedIntensityTechniques.any { it.type == TechniqueType.CLUSTER_SET }) {
                Text(
                    "Cluster set: próximamente",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            if (supportsIndependentSides && !sideLocked && guidedPhase == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = WorkoutUiTokens.InnerCardShape,
                    color = KpknSurface.inset,
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("left" to "Izquierda (L)", "right" to "Derecha (R)").forEach { (side, label) ->
                            val isSel = selectedSide == side
                            // Selección en tinta (LR1a): el lado elegido, en relleno de tinta con texto negro.
                            val containerColor = if (isSel) WizardColors.text else Color.Transparent
                            val contentColor = if (isSel) WizardColors.ctaContent else WizardColors.textMuted
                            val border = if (isSel) {
                                BorderStroke(1.dp, WizardColors.text)
                            } else {
                                BorderStroke(1.dp, KpknSurface.divider)
                            }
                            
                            Surface(
                                onClick = { selectSide(side) },
                                shape = WorkoutUiTokens.InnerCardShape,
                                color = containerColor,
                                border = border,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (side == "left") Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ghost 'Última' removed - base container message eliminated
            if (isAmrap) {
                Text("${amrapMinimumReps?.let { "Mínimo $it" } ?: "Sin mínimo"} · ${if (currentSet.purpose == com.example.kpkn.data.models.SetPurpose.CALIBRATOR) "Calibrador" else "AMRAP"}",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("amrap-purpose-badge"),
                    color = Color(0xFFF49A6E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(amrapMinimumReps?.let { "Haz al menos $it. Cuenta todas las que completes." } ?: "Cuenta todas las repeticiones que completes.",
                    Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = .65f), fontSize = 11.sp, lineHeight = 15.sp)
            }

            val volumeReplacedLabel = currentSet.volumeReplacedLabel()
            if (volumeReplacedLabel != null) {
                // Ley 1 (LV8): la cuenta atrás de la pausa técnica va DENTRO de la banda; antes era una línea aparte que alargaba la tarjeta al salir.
                VolumeReplacedTechniqueBand(
                    label = if (scheduledRestRemainingSeconds > 0) {
                        "$volumeReplacedLabel · Pausa técnica ${scheduledRestRemainingSeconds}s"
                    } else {
                        volumeReplacedLabel
                    },
                )
            } else if (scheduledRestRemainingSeconds > 0) {
                Text(
                    text = "Pausa técnica · ${scheduledRestRemainingSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.64f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            if (manualLoadRequiredNow && liveMode != LiveSetCardMode.SOLO_RPE) {
                val selectedSideTarget = when (selectedSide) {
                    "left" -> currentSet.leftTarget
                    "right" -> currentSet.rightTarget
                    else -> null
                }
                val repsRange = selectedSideTarget?.targetRepsRange
                    ?: currentSet.targetRepsRange
                    ?: currentSet.effectiveRepRange()
                val rirTarget = selectedSideTarget?.targetRIR ?: currentSet.targetRIR
                val repsInstruction = repsRange?.let { "${it.format()} reps" }
                    ?: currentSet.targetReps?.let { "$it reps" }
                    ?: "las reps indicadas"
                val rirInstruction = rirTarget?.let { " dejando $it en reserva" }.orEmpty()
                Text(
                    text = "Elige una carga para $repsInstruction$rirInstruction y registra la serie.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = sessionAccentColor,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // 5.1: La carga como «héroe» de la tarjeta: superficie #1A1A1A a todo el ancho, esquinas de 24 dp, sin márgenes laterales
            if (legacyModeLayout) {
            IntegratedLoadInput(
                            value = reportWeightText,
                            onValueChange = { typed ->
                                loadMode = loadModeAfterEnteredWeight(loadMode, typed)
                                updateActiveWeightText(typed)
                            },
                            label = loadFieldLabel,
                            plateHint = plateHint,
                            placeholder = if (manualLoadRequiredNow) {
                                "Carga a elegir"
                            } else {
                                LoadSuggestionDisplayPolicy.cardPlaceholderKg(
                                    weightText = reportWeightText,
                                    loadMode = loadMode,
                                    ghostOrPlannedKg = plannedWeightGhost,
                                )
                            },
                            options = quickLoadOptions,
                            onChipSelected = { option ->
                                loadMode = loadModeAfterChipSelection(option)
                                updateActiveWeightText(weightTextAfterChipSelection(option))
                            },
                            loadModeMenuExpanded = loadModeMenuExpanded,
                            onLoadModeMenuOpen = { loadModeMenuExpanded = true },
                            onLoadModeMenuDismiss = { loadModeMenuExpanded = false },
                            onLoadModeSelected = { selectedMode ->
                                when (selectedMode) {
                                    LoadModeV2.LOAD -> {
                                        if (loadMode != LoadModeV2.LOAD) updateActiveWeightText("")
                                        loadMode = LoadModeV2.LOAD
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.BODYWEIGHT -> {
                                        loadMode = LoadModeV2.BODYWEIGHT
                                        updateActiveWeightText("")
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.LASTRE -> {
                                        if (loadMode != LoadModeV2.LASTRE) updateActiveWeightText("")
                                        loadMode = LoadModeV2.LASTRE
                                        showBodyWeightPrompt = false
                                    }
                                    LoadModeV2.ASSISTED -> {
                                        if (loadMode != LoadModeV2.ASSISTED) updateActiveWeightText("")
                                        loadMode = LoadModeV2.ASSISTED
                                        if (bodyWeightText.isBlank()) showBodyWeightPrompt = true
                                    }
                                }
                                loadModeMenuExpanded = false
                            },
                            accentColor = sessionAccentColor,
                            loadMode = loadMode,
                            inlineChipsInField = exercise.trackRom,
                            loadPopTrigger = loadPopTrigger,
                        )

            // 5.1: División limpia con la parte de abajo, más oscura (reps e intensidad)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (legacyModeLayout) Modifier.heightIn(min = stableCardHeight - (if (exercise.trackRom) 97.dp else 133.dp) - 64.dp).padding(horizontal = 16.dp, vertical = 12.dp) else Modifier.weight(1f)),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                    val roomyStepper = supportsIndependentSides || exercise.isInSuperset()

                    @Composable
                    fun ValueStepperBlock(modifier: Modifier = Modifier) {
                        Column(
                            modifier = modifier,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val maxRepsForPopup = reportValueText.toIntOrNull() ?: 0
                            val partialRepsValueForPopup = partialSets.sum().coerceAtLeast(0)
                            var showVideoMenu by remember(exercise.id, setIndex, sideKey, videoAnalysis?.mediaId) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .testTag("set_reps_header"),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        (if (isTimeMode) "Tiempo" else "Reps").uppercase(),
                                        style = KpknType.eyebrow.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                        color = WizardColors.textMuted,
                                    )
                                    if (isTimeMode) {
                                        // Ley 1 (LV8): lo que decía el temporizador debajo del todo, ya sin sitio en la tarjeta, va aquí en una línea.
                                        val timerSupport = when {
                                            timerRunning -> "Restan ${formatTime(timerRemainingSeconds)}"
                                            timerElapsedSeconds > 0 -> "Registrado ${timerElapsedSeconds}s"
                                            plannedTarget != null -> "Objetivo ${plannedTarget}s"
                                            else -> null
                                        }
                                        timerSupport?.let { support ->
                                            Text(
                                                " · $support",
                                                style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                                color = if (timerRunning) sessionAccentColor else WizardColors.textFaint,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    if (!isTimeMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .clickable { showFeedbackExtrasPopup = !showFeedbackExtrasPopup },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                KpknLineIcons.Plus,
                                                contentDescription = "Añadir reps con ayuda o parciales",
                                                modifier = Modifier.size(12.dp),
                                                tint = WizardColors.textMuted,
                                            )
                                            if (showFeedbackExtrasPopup) {
                                                Popup(
                                                    alignment = Alignment.TopStart,
                                                    offset = IntOffset(0, 28),
                                                    onDismissRequest = { showFeedbackExtrasPopup = false },
                                                    properties = PopupProperties(focusable = true),
                                                ) {
                                                    Surface(
                                                        modifier = Modifier.width(264.dp),
                                                        shape = RoundedCornerShape(16.dp),
                                                        color = Color(0xFF1F1F1F),
                                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                                        shadowElevation = 14.dp,
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            Text(
                                                                "Detalles de reps",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White.copy(alpha = 0.72f),
                                                            )
                                                            FeedbackExtraStepperRow(
                                                                label = "Con ayuda",
                                                                value = assistedRepsValue,
                                                                maxValue = maxRepsForPopup,
                                                                accentColor = sessionAccentColor,
                                                                onDecrement = { assistedRepsValue = (assistedRepsValue - 1).coerceAtLeast(0) },
                                                                onIncrement = { assistedRepsValue = (assistedRepsValue + 1).coerceAtMost(maxRepsForPopup) },
                                                            )
                                                            FeedbackExtraStepperRow(
                                                                label = "Parciales",
                                                                value = partialRepsValueForPopup,
                                                                maxValue = 20,
                                                                accentColor = sessionAccentColor,
                                                                onDecrement = {
                                                                    val next = (partialRepsValueForPopup - 1).coerceAtLeast(0)
                                                                    partialSets = listOf(next)
                                                                    if (next == 0) showPartialsMode = false
                                                                },
                                                                onIncrement = {
                                                                    showPartialsMode = true
                                                                    partialSets = listOf((partialRepsValueForPopup + 1).coerceAtMost(20))
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (resolvedPlannedUnitMode == UnitModeV2.REPS) videoAnalysis?.let { analysis ->
                                    Box {
                                        WorkoutVideoChip(
                                            value = analysis,
                                            onClick = {
                                                showFeedbackExtrasPopup = false
                                                showVideoMenu = true
                                            },
                                            sessionAccentColor = sessionAccentColor,
                                        )
                                        if (showVideoMenu && analysis.state !is SetVideoAnalysisState.Unsupported) {
                                            Popup(
                                                popupPositionProvider = remember(density) {
                                                    WorkoutVideoMenuPositionProvider(with(density) { 14.dp.roundToPx() })
                                                },
                                                onDismissRequest = { showVideoMenu = false },
                                                properties = PopupProperties(focusable = true),
                                            ) {
                                                WorkoutVideoRepsSuggestion(
                                                    value = analysis,
                                                    onUse = { result ->
                                                        if (sessionCompletedSet == null && resolvedPlannedUnitMode == UnitModeV2.REPS && !isFailedSet) {
                                                            updateActiveValueText(result.reps.toString())
                                                            repsWheelCenterIndex.intValue = result.reps
                                                            // El centro viejo no puede sobrescribir el borrador tras «Usar».
                                                            repsInputGeneration++
                                                            if (result.partialConfidence != CountConfidence.BAJA) {
                                                                partialSets = listOf(result.partials.coerceAtLeast(0))
                                                                showPartialsMode = result.partials > 0
                                                            }
                                                        }
                                                        showVideoMenu = false
                                                    },
                                                    onCorrect = { onVideoCorrect(it); showVideoMenu = false },
                                                    onMarkFailure = {
                                                        if (sessionCompletedSet != null) onVideoMarkFailure()
                                                        else effortScale.items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }?.let(::onEffortSelected)
                                                        showVideoMenu = false
                                                    },
                                                    onOpenAnalysis = { onOpenAnalysis(it); showVideoMenu = false },
                                                    onRetryAnalysis = onRetryAnalysis?.let { retry ->
                                                        { id -> retry(id); showVideoMenu = false }
                                                    },
                                                    modifier = Modifier.width(264.dp)
                                                        .testTag(WorkoutVideoMenuTag)
                                                        .background(Color(0xFF151515), RoundedCornerShape(16.dp))
                                                        .border(1.dp, WizardColors.text.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(14.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(if (roomyStepper) 8.dp else 6.dp))

                            if (isTimeMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    WorkoutStepperField(
                                        value = if (isFailedSet) "0" else reportValueText,
                                        onValueChange = { if (!isFailedSet) updateActiveValueText(it.filter { ch -> ch.isDigit() }) },
                                        onDecrement = {
                                            val ghostBase = if (supportsIndependentSides) {
                                                if (selectedSide == "left") leftValueGhost else rightValueGhost
                                            } else {
                                                plannedValueGhost
                                            }
                                            val current = reportValueText.toIntOrNull()
                                                ?: ghostBase.toIntOrNull()
                                                ?: 0
                                            updateActiveValueText((current - 1).coerceAtLeast(0).toString())
                                        },
                                        onIncrement = {
                                            val ghostBase = if (supportsIndependentSides) {
                                                if (selectedSide == "left") leftValueGhost else rightValueGhost
                                            } else {
                                                plannedValueGhost
                                            }
                                            val current = reportValueText.toIntOrNull()
                                                ?: ghostBase.toIntOrNull()
                                                ?: 0
                                            updateActiveValueText((current + 1).toString())
                                        },
                                        buttonsEnabled = !isFailedSet,
                                        textInputEnabled = !isFailedSet,
                                        isError = isFailedSet,
                                        textStyle = MaterialTheme.typography.titleLarge,
                                        accentColor = sessionAccentColor,
                                        roomier = roomyStepper,
                                        placeholder = if (supportsIndependentSides) {
                                            if (selectedSide == "left") leftValueGhost else rightValueGhost
                                        } else {
                                            plannedValueGhost
                                        }.takeIf { it.isNotBlank() },
                                        modifier = Modifier.weight(1f),
                                    )
                                    // El temporizador va junto al valor (LV8): así la tarjeta de un ejercicio por tiempo mide lo mismo que la de reps.
                                    TimerToggleButton(
                                        running = timerRunning,
                                        accentColor = sessionAccentColor,
                                        onClick = {
                                            toggleSetTimer()
                                        },
                                    )
                                }
                            } else {
                                val ghostBase = if (supportsIndependentSides) {
                                    if (selectedSide == "left") leftValueGhost else rightValueGhost
                                } else {
                                    plannedValueGhost
                                }
                                val ghostReps = ghostBase.toIntOrNull() ?: 0
                                val currentReps = if (isFailedSet) 0 else reportValueText.toIntOrNull() ?: ghostReps
                                val repsMax = buildRepsCarouselMax(currentReps, ghostReps)
                                val repsItems = remember(repsMax) { (0..repsMax).map { it.toString() } }
                                val repsSelectedIndex = repsCarouselIndexFromValue(
                                    if (isFailedSet) "0" else reportValueText.ifBlank { ghostBase },
                                    repsMax,
                                )
                                val repsCarouselTone = carouselToneForRepEvaluation(
                                    evaluateRepRange(
                                        actual = currentReps.toDouble(),
                                        range = plannedRepRange ?: plannedTarget?.toInt()?.let { RepRange(it, it) },
                                        amrapActive = isAmrap,
                                        amrapMinimum = amrapMinimumReps,
                                    ),
                                ).let { tone ->
                                    if (reportValueText.isBlank() && !isFailedSet) CarouselValueTone.OnPlan else tone
                                }
                                key(repsInputGeneration, sessionCompletedSet) {
                                    WorkoutHorizontalWheelPicker(
                                        items = repsItems,
                                        selectedIndex = repsSelectedIndex,
                                        onSelectedIndexChange = { index ->
                                            if (!isFailedSet) updateActiveValueText(index.toString())
                                        },
                                        enabled = !isFailedSet,
                                        isError = isFailedSet,
                                        accentColor = sessionAccentColor,
                                        roomier = roomyStepper,
                                        centerGlowColor = carouselGlowForTone(repsCarouselTone, sessionAccentColor),
                                        centeredIndexHolder = repsWheelCenterIndex,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    @Composable
                    fun EffortBlock() {
                        // El esfuerzo (LV8): un slider de fuego que empieza en el mínimo y marca el plan. Sale en TODAS las series de
                        // fuerza, también las que no traen objetivo de esfuerzo: registrar exige haberlo marcado.
                        EffortFireSlider(
                            scale = effortScale,
                            selectedIndex = effortIndex,
                            onSelect = { onEffortSelected(it) },
                            enabled = true,
                            hint = EffortRequiredHint.takeIf { effortHintVisible && !hasChosenReportedEffort },
                            wobbleKey = effortWobbleKey,
                            animate = effortAnimate,
                            hapticsEnabled = hapticFeedbackEnabled,
                            forcedFireSeconds = effortFireSecondsOverride,
                            prominent = liveMode == LiveSetCardMode.SOLO_RPE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    @Composable fun VideoBlock() {
                    videoAnalysis?.let { analysis ->
                        com.example.kpkn.screens.workout.design.WorkoutVideoRepsSuggestion(
                            value = analysis,
                            onUse = { result ->
                                if (sessionCompletedSet == null && resolvedPlannedUnitMode == UnitModeV2.REPS && !isFailedSet) {
                                    updateActiveValueText(result.reps.toString())
                                    repsWheelCenterIndex.intValue = result.reps
                                    // «Usar» sustituye también el scroll anterior: el centro viejo no puede sobrescribir el borrador.
                                    repsInputGeneration++
                                    if (result.partialConfidence != CountConfidence.BAJA) {
                                        partialSets = listOf(result.partials.coerceAtLeast(0))
                                        showPartialsMode = result.partials > 0
                                    }
                                }
                            },
                            onCorrect = onVideoCorrect,
                            onMarkFailure = {
                                if (sessionCompletedSet != null) onVideoMarkFailure()
                                else effortScale.items.indexOfFirst { it.isFailure }.takeIf { it >= 0 }?.let(::onEffortSelected)
                            },
                            onOpenAnalysis = onOpenAnalysis,
                            onRetryAnalysis = onRetryAnalysis,
                        )
                    }
                    }

                    @Composable fun RomBlock() {
                    if (exercise.trackRom) {
                        RomReportSlider(
                            value = romValue ?: DEFAULT_ROM_PERCENT,
                            onValueChange = { romValue = it },
                            accentColor = sessionAccentColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    }

                    @Composable fun ActionsBlock() {
                    LiveSetCardActions(
                        onAdvanced = { cardFace = SetCardFace.Technique },
                        onVisual = { cardFace = SetCardFace.ExerciseMedia },
                        onHistory = onShowHistory,
                    )
                    }

                    if (legacyModeLayout) {
                        ValueStepperBlock(Modifier.fillMaxWidth())
                        // Misma ruta de video por chip/menu que en el diseno nuevo, sin fila duplicada.
                        EffortBlock()
                        RomBlock()
                        modeResultContent?.invoke(liveMode)
                    } else {
                        LiveModeSetContent(liveMode, setIndex + 1, actions = { ActionsBlock() }) {
                            if (liveMode == LiveSetCardMode.RM) {
                                LiveRmFocus(rmValues, reportWeightText,
                                    onWeight = { typed ->
                                        loadMode = loadModeAfterEnteredWeight(loadMode, typed)
                                        updateActiveWeightText(typed)
                                        val weight = typed.replace(',', '.').toDoubleOrNull()
                                        val reference = rmValues.loadReference
                                        if (weight != null && weight.isFinite() && weight > 0 && reference != null) rmPercent = weight / reference * 100
                                    },
                                    onPercent = ::applyRmPercent,
                                    onEstimatedReps = { reps ->
                                        val percentOfRm = estimatePercent1RM(reps)
                                        val next = if (rmValues.usesTrainingMax && rmValues.oneRm != null && rmValues.trainingMax != null)
                                            percentOfRm * rmValues.oneRm / rmValues.trainingMax else percentOfRm
                                        applyRmPercent(next)
                                    },
                                    onReference = { reference ->
                                        rmReference = reference
                                        liveRmValues(exercise, currentSet, rmPercent, reference).weight?.let { updateActiveWeightText(it.toLiveNumberString()) }
                                    },
                                    prLabel = exercise.prFor1RM?.takeIf { rmReference == pristineRmReference }?.let { "PR: ${it.weight.toLiveNumberString()} kg × ${it.reps}" },
                                    fallbackWeight = plannedWeightGhost,
                                    modeButton = { ModeLoadButton() })
                            }
                            if (liveMode == LiveSetCardMode.TIME) {
                                LiveSetClockHero(timerElapsedSeconds, timerRemainingSeconds, plannedTarget, timerRunning, ::toggleSetTimer)
                            }
                            if (liveMode == LiveSetCardMode.SOLO_RPE) EffortBlock()
                            if (resolvedPlannedUnitMode == UnitModeV2.REPS) {
                                ValueStepperBlock(Modifier.fillMaxWidth())
                            } else {
                                LiveQuantityInput(reportValueText, ::updateActiveValueText, resolvedPlannedUnitMode, exercise.customUnit,
                                    if (isTimeMode) plannedTarget?.toDouble() else activeSideTarget?.targetValue ?: currentSet.plannedTargetV2 ?: plannedTarget?.toDouble(),
                                    enabled = !timerRunning)
                            }
                            if (liveMode != LiveSetCardMode.RM) {
                                LiveCompactLoadInput(reportWeightText, { typed ->
                                    loadMode = loadModeAfterEnteredWeight(loadMode, typed)
                                    updateActiveWeightText(typed)
                                }, loadFieldLabel, plannedWeightGhost, loadMode) { ModeLoadButton() }
                            }
                            // El video conserva sus acciones en el chip de la cabecera; no se duplica ni desplaza el esfuerzo.
                            if (liveMode != LiveSetCardMode.SOLO_RPE) EffortBlock()
                            RomBlock()
                            modeResultContent?.invoke(liveMode)
                            val status = quantityError ?: when {
                                timeGoalComplete -> "Objetivo cumplido · Confirma con ✓ para registrar"
                                liveMode == LiveSetCardMode.SOLO_RPE -> "Sin carga fija. Elige el peso al hacer la serie."
                                liveMode == LiveSetCardMode.CUSTOM -> "Unidad del ejercicio: ${exercise.customUnit?.takeIf { it.isNotBlank() } ?: "sin definir"}"
                                else -> "Registra el resultado realizado con ✓"
                            }
                            val statusView = remember { BringIntoViewRequester() }
                            LaunchedEffect(quantityError, timeGoalComplete) { if (quantityError != null || timeGoalComplete) statusView.bringIntoView() }
                            Text(status, Modifier.testTag("live-mode-status").bringIntoViewRequester(statusView), style = KpknType.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                color = if (quantityError != null) Color(0xFFEF6F64) else WizardColors.textMuted)
                        }
                    }
                }



            if (showAmrapSheet) {
                AmrapConfigSheet(
                    plannedMinReps = amrapMinimumReps,
                    plannedTargetName = if (isTimeMode) "s" else "reps",
                    initialReachFailure = amrapReachFailure,
                    initialReserveReps = amrapReserveReps,
                    onApply = { minReps, reachFailure, reserveReps ->
                        isAmrap = true
                        amrapMinimumReps = minReps
                        amrapReachFailure = reachFailure
                        amrapReserveReps = reserveReps
                        if (minReps != null) updateActiveValueText(minReps.toString())
                        showAmrapSheet = false
                    },
                    onDismiss = { showAmrapSheet = false },
                )
            }

            val partialRepsTotal = if (showPartialsMode) {
                partialSets.sum().coerceAtLeast(0)
            } else {
                0
            }

            val activeInitialWeight = if (supportsIndependentSides) {
                if (selectedSide == "left") targetLeftWeight else targetRightWeight
            } else {
                defaultWeight
            }
            val activeInitialValue = if (supportsIndependentSides) {
                if (selectedSide == "left") initialLeftValue else initialRightValue
            } else {
                defaultValue
            }
            // LV8: la serie empieza sin esfuerzo marcado; solo una serie ya registrada trae el suyo.
            val initialIntensityForDraft = sessionCompletedSet?.let {
                it.actualIntensityValue?.toTrimmedNumberString() ?: it.rpe?.toTrimmedNumberString()
            }.orEmpty()
            val plannedDropCountForDraft = currentSet.plannedIntensityTechniques
                .firstOrNull { it.type == TechniqueType.DROP_SET }
                ?.params?.get("count")?.toIntOrNull()
                ?: currentSet.dropSets.size
            val liveDropCountForDraft = if (dropSetEnabled) dropSets.size else 0
            val scheduledPlanForDraft = currentSet.takeIf { it.isInlineEditorScheduledTechnique() }
                ?.scheduledTechniquePlan()
            val guidedProgressCoordinates = when (val phase = guidedPhase) {
                is GuidedTechniquePhase.DropSet -> Triple(WorkoutTechniqueDraftKind.GUIDED_DROP, phase.index, phase.total)
                is GuidedTechniquePhase.RestPauseCountdown -> Triple(
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                    phase.index,
                    phase.total,
                )
                is GuidedTechniquePhase.RestPauseReps -> Triple(
                    WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                    phase.index,
                    phase.total,
                )
                null -> null
            }
            val techniqueProgressForDraft = when {
                scheduledPlanForDraft != null && scheduledMainCapture != null -> WorkoutTechniqueProgressDraft(
                    kind = if (scheduledPlanForDraft.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                        WorkoutTechniqueDraftKind.SCHEDULED_DROP
                    } else {
                        WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE
                    },
                    phaseIndex = scheduledPhaseIndex,
                    phaseCount = scheduledPlanForDraft.phaseCount,
                    mainCapture = scheduledMainCapture?.toDurableDraft(),
                    dropRows = scheduledDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = scheduledRestPauseDrafts,
                    dropWeightText = reportWeightText.takeIf { scheduledAwaitingCommit },
                    dropRepsText = reportValueText.takeIf { scheduledAwaitingCommit },
                    restPauseRepsText = reportValueText.takeIf { scheduledAwaitingCommit },
                    restRemainingSeconds = scheduledRestRemainingSeconds.takeIf { it > 0 },
                    awaitingCommit = scheduledAwaitingCommit,
                )
                guidedProgressCoordinates != null && guidedMainCapture != null -> WorkoutTechniqueProgressDraft(
                    kind = guidedProgressCoordinates.first,
                    phaseIndex = guidedProgressCoordinates.second,
                    phaseCount = guidedProgressCoordinates.third,
                    mainCapture = guidedMainCapture?.toDurableDraft(),
                    dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = guidedRestPauseDrafts,
                    dropWeightText = guidedDropWeightText,
                    dropRepsText = guidedDropRepsText,
                    restPauseRepsText = guidedRestPauseRepsText,
                    restRemainingSeconds = (guidedPhase as? GuidedTechniquePhase.RestPauseCountdown)?.secondsLeft,
                    awaitingCommit = guidedAwaitingCommit,
                    commitDropRows = guidedCommitDropOverride?.map { DropSetData(weight = it.weight, reps = it.reps) },
                    commitRestPauseRows = guidedCommitRestPauseOverride,
                )
                else -> null
            }

            fun publishTechniqueProgressDraft(progress: WorkoutTechniqueProgressDraft) {
                val base = restoredDraft ?: WorkoutSetDraft()
                onDraftChange(
                    base.copy(
                        weightText = reportWeightText,
                        weightManuallyFixed = hasManualWeightOverride,
                        valueText = reportValueText,
                        intensityText = intensityText,
                        loadMode = loadMode,
                        selectedSide = if (supportsIndependentSides) selectedSide else null,
                        partialReps = partialRepsTotal.takeIf { it > 0 },
                        reachedFailure = reachedFailure,
                        dropSetCount = liveDropCountForDraft.takeIf { it > 0 },
                        amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                        amrapMinimumReps = amrapMinimumReps,
                        amrapMinimumOverridden = true,
                        amrapReachFailure = amrapReachFailure,
                        amrapReserveReps = amrapReserveReps,
                        rom = romValue,
                        assistedReps = assistedRepsValue.takeIf { it > 0 },
                        notes = setNoteText.trim().takeIf { it.isNotEmpty() },
                        rmReference1RM = rmReference.takeIf { liveMode == LiveSetCardMode.RM },
                        rmPercent = rmPercent.takeIf { liveMode == LiveSetCardMode.RM },
                        voiceFields = base.voiceFields,
                        techniqueProgress = progress,
                        isDirty = true,
                        updatedAtMs = System.currentTimeMillis(),
                    ),
                    if (supportsIndependentSides) selectedSide else null,
                )
            }

            LaunchedEffect(
                isActivePage,
                sessionCompletedSet,
                rmReference,
                rmPercent,
                reportWeightText,
                hasManualWeightOverride,
                reportValueText,
                intensityText,
                loadMode,
                selectedSide,
                reachedFailure,
                partialRepsTotal,
                romValue,
                assistedRepsValue,
                setNoteText,
                dropSetEnabled,
                dropSets.size,
                scheduledPhaseIndex,
                scheduledMainCapture,
                scheduledDropDrafts,
                scheduledRestPauseDrafts,
                scheduledAwaitingCommit,
                guidedMainCapture,
                guidedDropDrafts,
                guidedRestPauseDrafts,
                guidedDropWeightText,
                guidedDropRepsText,
                guidedRestPauseRepsText,
                guidedAwaitingCommit,
                guidedCommitDropOverride,
                guidedCommitRestPauseOverride,
                guidedProgressCoordinates,
                isRecordSubmissionPending,
            ) {
                if (!isActivePage || isRecordSubmissionPending) return@LaunchedEffect
                val initialLoadMode = resolveEffectiveLoadMode(
                    draftLoadMode = restoredDraft?.loadMode,
                    persistedLoadMode = persistedLoadMode,
                    plannedLoadMode = currentSet.loadModeV2,
                    defaultCatalogMode = null,
                )
                val initialFailure = sessionCompletedSet?.isFailure == true
                val initialSide = lockedSide ?: "left"
                val isDirty = (liveMode == LiveSetCardMode.RM && (rmReference != pristineRmReference || rmPercent != pristineRmPercent)) ||
                    techniqueProgressForDraft != null ||
                    reportWeightText != activeInitialWeight ||
                    reportValueText != activeInitialValue ||
                    intensityText != initialIntensityForDraft ||
                    loadMode != initialLoadMode ||
                    reachedFailure != initialFailure ||
                    isAmrap != (initialAmrapActive) ||
                    amrapMinimumReps != initialAmrapMinimumReps ||
                    amrapReachFailure != (restoredDraft?.amrapReachFailure ?: defaultAmrapReachFailure) ||
                    amrapReserveReps != restoredDraft?.amrapReserveReps ||
                    partialRepsTotal != (restoredDraft?.partialReps ?: 0) ||
                    (supportsIndependentSides && selectedSide != initialSide) ||
                    romValue != pristineRomValue ||
                    assistedRepsValue != (restoredDraft?.assistedReps ?: 0) ||
                    setNoteText.trim() != (restoredDraft?.notes.orEmpty()) ||
                    liveDropCountForDraft != (restoredDraft?.dropSetCount ?: plannedDropCountForDraft)
                // Un parche persistido con el mismo id puede coincidir por completo con el estado
                // restaurado. También publicamos ese borrador limpio para sustituir el anterior.
                if (isDirty || restoredDraft != null || sessionCompletedSet != null) {
                    onDraftChange(
                        WorkoutSetDraft(
                            weightText = reportWeightText,
                            weightManuallyFixed = hasManualWeightOverride,
                            valueText = reportValueText,
                            intensityText = intensityText,
                            loadMode = loadMode,
                            selectedSide = if (supportsIndependentSides) selectedSide else null,
                            partialReps = partialRepsTotal.takeIf { it > 0 },
                            reachedFailure = reachedFailure,
                            dropSetCount = liveDropCountForDraft.takeIf { it > 0 },
                            amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                            amrapMinimumReps = amrapMinimumReps,
                        amrapMinimumOverridden = true,
                            amrapReachFailure = amrapReachFailure,
                            amrapReserveReps = amrapReserveReps,
                            isDirty = isDirty,
                            rom = romValue,
                            assistedReps = assistedRepsValue.takeIf { it > 0 },
                            notes = setNoteText.trim().takeIf { it.isNotEmpty() },
                            techniqueProgress = techniqueProgressForDraft,
                            rmReference1RM = rmReference.takeIf { liveMode == LiveSetCardMode.RM },
                            rmPercent = rmPercent.takeIf { liveMode == LiveSetCardMode.RM },
                        ),
                        if (supportsIndependentSides) selectedSide else null,
                    )
                }
            }

            val advanced = SetAdvancedFeedback(
                // El fallo en una escala RIR no es «RIR 10»: el fallo se guarda como fallo, sin reserva (LV8).
                rir = if (isAmrap && !amrapReachFailure) amrapReserveReps
                      else if (reportedIntensityMode == IntensityMode.RIR && !reachedFailure) chosenEffortValue?.toInt()
                      else null,
                reachedFailure = reachedFailure || (isAmrap && amrapReachFailure),
                isFailedSet = isFailedSet,
                executionError = isExecutionError,
                failureReason = if (isFailedSet) failedSetReason.ifBlank { "Serie marcada como fallida" } else null,
                isPartial = partialRepsTotal > 0,
                partialReps = partialRepsTotal.takeIf { it > 0 },
                dropSets = if (dropSetEnabled) {
                    dropSets.filter { it.weight > 0 && it.reps > 0 }.map { DropSetData(weight = it.weight, reps = it.reps) }
                } else {
                    emptyList()
                },
                restPauses = if (restPauseEnabled) {
                    restPauseSets.filter { it.reps > 0 }.map { it.copy(restTime = it.restTime.coerceAtLeast(0)) }
                } else {
                    emptyList()
                },
                isWarmup = false,
                actualIntensityMode = when {
                    isAmrap && amrapReachFailure -> IntensityMode.FAILURE
                    isAmrap -> IntensityMode.AMRAP
                    reachedFailure -> IntensityMode.FAILURE
                    else -> reportedIntensityMode
                },
                actualIntensityValue = when {
                    isAmrap && amrapReachFailure -> 10.0
                    isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                    reachedFailure -> 10.0
                    else -> chosenEffortValue
                },
                amrapOverride = isAmrap.takeIf { it != plannedAmrap },
                amrapMinimumReps = amrapMinimumReps,
                amrapMinimumOverridden = true,
                intensityAdjusted = intensityTouched || reachedFailure || isAmrap,
                timerElapsedSeconds = if (isTimeMode) parseLiveDurationSeconds(reportValueText) else null,
                timerTargetSeconds = if (isTimeMode) plannedTarget else null,
                rom = romValue,
                assistedReps = assistedRepsValue.takeIf { it > 0 },
                notes = setNoteText.trim().takeIf { it.isNotEmpty() },
            )

            fun submitRecord(
                recordLoadMode: LoadModeV2,
                recordUnitMode: UnitModeV2,
                recordWeight: Double,
                recordValue: Double,
                recordIntensity: Double?,
                recordAdvanced: SetAdvancedFeedback,
                recordAmrapOverride: Boolean,
                recordBodyWeight: Double?,
                recordSide: String?,
                onCommitted: () -> Unit,
            ) {
                if (isRecordSubmissionPending) return
                isRecordSubmissionPending = true
                onRecordV2(
                    recordLoadMode,
                    recordUnitMode,
                    recordWeight,
                    recordValue,
                    recordIntensity,
                    recordAdvanced,
                    recordAmrapOverride,
                    recordBodyWeight,
                    recordSide,
                ) { result ->
                    isRecordSubmissionPending = false
                    if (result.succeeded) onCommitted()
                }
            }

            fun submitOpenTechnique(
                capture: GuidedMainCapture,
                progress: WorkoutTechniqueProgressDraft,
                onCommitted: () -> Unit,
            ) {
                val durable = progress.copy(mainCapture = capture.toDurableDraft())
                submitRecord(capture.loadMode, capture.unitMode, capture.weight, capture.value,
                    capture.intensity, (capture.mainFeedback ?: advanced).copy(
                        dropSets = durable.dropRows.orEmpty(), restPauses = durable.restPauseRows.orEmpty(),
                        techniqueStatus = com.example.kpkn.data.models.RecordedTechniqueStatus.OPEN,
                        techniqueProgress = durable),
                    capture.amrapOverride, capture.bodyWeight, capture.side, onCommitted)
            }

            fun submitScheduledRecord(
                capture: GuidedMainCapture,
                dropRows: List<DropSetEntry>,
                restRows: List<RestPauseData>,
                completion: com.example.kpkn.data.models.RecordedTechniqueStatus = if (dropRows.isEmpty() && restRows.isEmpty()) com.example.kpkn.data.models.RecordedTechniqueStatus.SKIPPED else com.example.kpkn.data.models.RecordedTechniqueStatus.COMPLETED,
            ) {
                val plan = scheduledPlanForDraft ?: return
                val kind = if (plan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                    WorkoutTechniqueDraftKind.SCHEDULED_DROP
                } else {
                    WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE
                }
                val retryProgress = WorkoutTechniqueProgressDraft(
                    kind = kind,
                    phaseIndex = plan.phaseCount,
                    phaseCount = plan.phaseCount,
                    mainCapture = capture.toDurableDraft(),
                    dropRows = dropRows.map { DropSetData(weight = it.weight, reps = it.reps) },
                    restPauseRows = restRows,
                    dropWeightText = reportWeightText,
                    dropRepsText = reportValueText,
                    restPauseRepsText = reportValueText,
                    awaitingCommit = true,
                )
                // updateSetDraft is synchronous; record() flushes this exact
                // snapshot before it attempts the completed-set transaction.
                publishTechniqueProgressDraft(retryProgress)
                scheduledDropDrafts = dropRows
                scheduledRestPauseDrafts = restRows
                scheduledPhaseIndex = plan.phaseCount
                scheduledAwaitingCommit = true
                scheduledRestRemainingSeconds = 0
                val recordAdvanced = (capture.mainFeedback ?: advanced).copy(
                    dropSets = retryProgress.dropRows.orEmpty(),
                    restPauses = retryProgress.restPauseRows.orEmpty(),
                    techniqueStatus = completion,
                )
                submitRecord(
                    capture.loadMode,
                    capture.unitMode,
                    capture.weight,
                    capture.value,
                    capture.intensity,
                    recordAdvanced,
                    capture.amrapOverride,
                    capture.bodyWeight,
                    capture.side,
                ) {
                    scheduledPhaseIndex = 0
                    scheduledMainCapture = null
                    scheduledDropDrafts = emptyList()
                    scheduledRestPauseDrafts = emptyList()
                    scheduledAwaitingCommit = false
                    scheduledRestRemainingSeconds = 0
                    if (supportsIndependentSides && !sideLocked) {
                        resetEffortForNextSide()
                        selectSide(if (selectedSide == "left") "right" else "left")
                    }
                }
            }

            fun commitCapturedRecord(
                capture: GuidedMainCapture,
                dropOverride: List<DropSetData>,
                restOverride: List<RestPauseData>,
            ) {
                if (isRecordSubmissionPending) return
                val payload = (capture.mainFeedback ?: advanced).copy(
                    techniqueStatus = if (dropOverride.isEmpty() && restOverride.isEmpty()) com.example.kpkn.data.models.RecordedTechniqueStatus.SKIPPED else com.example.kpkn.data.models.RecordedTechniqueStatus.COMPLETED,
                    dropSets = dropOverride,
                    restPauses = restOverride,
                )
                val pendingPhase = guidedPhase
                val retryProgress = when {
                    pendingPhase is GuidedTechniquePhase.DropSet ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_DROP,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = (dropOverride.takeIf { it.isNotEmpty() }
                                ?: guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) }),
                            restPauseRows = guidedRestPauseDrafts,
                            dropWeightText = guidedDropWeightText,
                            dropRepsText = guidedDropRepsText,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    pendingPhase is GuidedTechniquePhase.RestPauseCountdown ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                            restPauseRows = guidedRestPauseDrafts,
                            restPauseRepsText = guidedRestPauseRepsText,
                            restRemainingSeconds = pendingPhase.secondsLeft,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    pendingPhase is GuidedTechniquePhase.RestPauseReps ->
                        WorkoutTechniqueProgressDraft(
                            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                            phaseIndex = pendingPhase.index,
                            phaseCount = pendingPhase.total,
                            mainCapture = capture.toDurableDraft(),
                            dropRows = guidedDropDrafts.map { DropSetData(weight = it.weight, reps = it.reps) },
                            restPauseRows = restOverride.takeIf { it.isNotEmpty() } ?: guidedRestPauseDrafts,
                            restPauseRepsText = guidedRestPauseRepsText,
                            awaitingCommit = true,
                            commitDropRows = dropOverride,
                            commitRestPauseRows = restOverride,
                        )
                    else -> techniqueProgressForDraft
                }
                if (retryProgress != null) {
                    guidedAwaitingCommit = retryProgress.awaitingCommit == true
                    guidedCommitDropOverride = retryProgress.commitDropRows
                        ?.map { DropSetEntry(weight = it.weight, reps = it.reps) }
                    guidedCommitRestPauseOverride = retryProgress.commitRestPauseRows
                    publishTechniqueProgressDraft(retryProgress)
                }
                submitRecord(
                    capture.loadMode,
                    capture.unitMode,
                    capture.weight,
                    capture.value,
                    capture.intensity,
                    payload,
                    capture.amrapOverride,
                    capture.bodyWeight,
                    capture.side,
                ) {
                    guidedPhase = null
                    guidedMainCapture = null
                    guidedDropDrafts = emptyList()
                    guidedRestPauseDrafts = emptyList()
                    guidedAwaitingCommit = false
                    guidedCommitDropOverride = null
                    guidedCommitRestPauseOverride = null
                    if (dropOverride.isNotEmpty()) {
                        dropSetEnabled = true
                        dropSets = dropOverride.map { DropSetEntry(weight = it.weight, reps = it.reps) }
                    }
                    if (restOverride.isNotEmpty()) {
                        restPauseEnabled = true
                        restPauseSets = restOverride
                    }
                    if (supportsIndependentSides && !sideLocked) {
                        resetEffortForNextSide()
                        selectSide(if (selectedSide == "left") "right" else "left")
                    }
                }
            }

            guidedSkipAction[0] = {
                val scheduled = scheduledMainCapture
                if (scheduled != null) {
                    submitScheduledRecord(scheduled, scheduledDropDrafts, scheduledRestPauseDrafts,
                        if (scheduledDropDrafts.isEmpty() && scheduledRestPauseDrafts.isEmpty())
                            com.example.kpkn.data.models.RecordedTechniqueStatus.SKIPPED
                        else com.example.kpkn.data.models.RecordedTechniqueStatus.COMPLETED)
                } else {
                    guidedMainCapture?.let { capture -> commitCapturedRecord(capture,
                        guidedDropDrafts.map { DropSetData(it.weight, it.reps) }, guidedRestPauseDrafts) }
                }
            }

            fun dropProgress(index: Int, total: Int, capture: GuidedMainCapture, guide: PlannedTechniqueGuide,
                rows: List<DropSetEntry> = emptyList()): WorkoutTechniqueProgressDraft = WorkoutTechniqueProgressDraft(
                kind = WorkoutTechniqueDraftKind.GUIDED_DROP, phaseIndex = index, phaseCount = total,
                dropRows = rows.map { DropSetData(it.weight, it.reps) },
                dropWeightText = suggestDropWeightForThreeReps(capture.weight, capture.value.roundToInt(), index, guide.dropPcts, capture.loadMode).toTrimmedNumberString(),
                dropRepsText = RestPausePlanDefaults.Reps.toString())

            fun beginDropPhase(index: Int, total: Int, capture: GuidedMainCapture, guide: PlannedTechniqueGuide) {
                val suggested = suggestDropWeightForThreeReps(
                    mainWeight = capture.weight,
                    mainReps = capture.value.roundToInt(),
                    dropIndex = index,
                    dropPcts = guide.dropPcts,
                    loadMode = capture.loadMode,
                )
                guidedDropWeightText = suggested.toTrimmedNumberString()
                guidedDropRepsText = RestPausePlanDefaults.Reps.toString()
                guidedPhase = GuidedTechniquePhase.DropSet(
                    index = index,
                    total = total,
                    suggestedWeight = suggested,
                )
            }

            fun beginRestPauseCountdown(index: Int, total: Int) {
                guidedPhase = GuidedTechniquePhase.RestPauseCountdown(
                    index = index,
                    total = total,
                    secondsLeft = activeRestPauseSeconds,
                )
            }

            SideEffect {
                if (isActivePage) {
                    recordFabHolder?.isUpdateMode = sessionCompletedSet != null
                }
            }

            SideEffect {
                if (isActivePage) {
                    val pageKey = recordActionPageKey
                        ?: "${exercise.id}:$setIndex:${if (supportsIndependentSides) lockedSide ?: selectedSide else "B"}"
                    recordActionHolder.bind(
                        scopeOwner = recordActionScopeOwner ?: recordActionHolder,
                        instanceOwner = recordActionInstanceOwner,
                        stepKey = WorkoutStepRules.workingStepKey(
                            exercise.id,
                            setIndex,
                            (lockedSide ?: selectedSide).takeIf { supportsIndependentSides },
                        ),
                        pageKey = pageKey,
                        action = label@{
                        if (isRecordSubmissionPending) return@label
                        // A stale manual phase must never win over a scheduled
                        // editor plan (for example after replacing a set
                        // while its old guided sheet was open).  The visual
                        // guard hides that panel; this action guard clears its
                        // route as well.
                        val scheduledPlanForAction = currentSet
                            .takeIf { it.isInlineEditorScheduledTechnique() }
                            ?.scheduledTechniquePlan()
                        val phase = guidedPhase.takeIf { scheduledPlanForAction == null }
                        if (guidedAwaitingCommit && scheduledPlanForAction == null) {
                            val capture = guidedMainCapture ?: return@label
                            val retryDropRows = guidedCommitDropOverride?.map {
                                DropSetData(weight = it.weight, reps = it.reps)
                            } ?: guidedDropDrafts.map {
                                DropSetData(weight = it.weight, reps = it.reps)
                            }
                            val retryRestRows = guidedCommitRestPauseOverride ?: guidedRestPauseDrafts
                            commitCapturedRecord(
                                capture = capture,
                                dropOverride = retryDropRows,
                                restOverride = retryRestRows,
                            )
                            return@label
                        }
                        if (phase != null) {
                            val capture = guidedMainCapture ?: return@label
                            when (phase) {
                                is GuidedTechniquePhase.DropSet -> {
                                    val dropWeight = parseGuidedDropWeightKg(guidedDropWeightText)
                                    val dropReps = guidedDropRepsText.toIntOrNull()
                                    if (dropWeight == null || dropWeight <= 0.0 || dropReps == null || dropReps <= 0) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Completa peso y reps del drop.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        return@label
                                    }
                                    val nextDrafts = guidedDropDrafts.take(phase.index) + DropSetEntry(weight = dropWeight, reps = dropReps)
                                    guidedDropDrafts = nextDrafts
                                    val nextIndex = phase.index + 1
                                    if (nextIndex < phase.total) {
                                        val guide = currentSet.resolvePlannedTechniqueGuide()
                                            ?: PlannedTechniqueGuide(
                                                kind = TechniqueType.DROP_SET,
                                                count = phase.total,
                                                dropPcts = listOf(-DropSetPlanDefaults.DropKg),
                                            )
                                        submitOpenTechnique(capture, dropProgress(nextIndex, phase.total, capture, guide, nextDrafts)) {
                                            beginDropPhase(nextIndex, phase.total, capture, guide)
                                        }
                                    } else {
                                        commitCapturedRecord(
                                            capture = capture,
                                            dropOverride = nextDrafts.map {
                                                DropSetData(weight = it.weight, reps = it.reps)
                                            },
                                            restOverride = emptyList(),
                                        )
                                    }
                                }
                                is GuidedTechniquePhase.RestPauseCountdown -> {
                                    guidedPhase = GuidedTechniquePhase.RestPauseReps(phase.index, phase.total)
                                    guidedRestPauseRepsText = activeRestReps.toString()
                                }
                                is GuidedTechniquePhase.RestPauseReps -> {
                                    val rpReps = guidedRestPauseRepsText.toIntOrNull()
                                    if (rpReps == null || rpReps <= 0) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Indica las reps del rest-pause.",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                        return@label
                                    }
                                    val nextDrafts = guidedRestPauseDrafts.take(phase.index) + RestPauseData(
                                        restTime = activeRestPauseSeconds,
                                        reps = rpReps,
                                        style = if(currentSet.isMyoReps()) "myo" else null,
                                    )
                                    guidedRestPauseDrafts = nextDrafts
                                    val nextIndex = phase.index + 1
                                    if (nextIndex < phase.total) {
                                        submitOpenTechnique(capture, WorkoutTechniqueProgressDraft(
                                            kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                                            phaseIndex = nextIndex, phaseCount = phase.total,
                                            restPauseRows = nextDrafts, restRemainingSeconds = activeRestPauseSeconds,
                                            restPauseRepsText = activeRestReps.toString())) {
                                            beginRestPauseCountdown(nextIndex, phase.total)
                                        }
                                    } else {
                                        commitCapturedRecord(
                                            capture = capture,
                                            dropOverride = emptyList(),
                                            restOverride = nextDrafts,
                                        )
                                    }
                                }
                            }
                            return@label
                        }

                        if (loadMode == LoadModeV2.ASSISTED && bodyWeightText.isBlank()) {
                            android.widget.Toast.makeText(
                                context,
                                "Ingresa tu peso corporal antes de registrar",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                            return@label
                        }
                        // Error conserva carga y reps, pero también exige el esfuerzo elegido.
                        if (!hasChosenReportedEffort && !(isAmrap && currentSet.purpose == com.example.kpkn.data.models.SetPurpose.CALIBRATOR)) {
                            requireEffort()
                            return@label
                        }
                        if (isFailedSet && onExecutionError != null) {
                            onExecutionError.invoke(advanced)
                            return@label
                        }
                        val reportingSide = if (supportsIndependentSides) selectedSide else null
                        val reportedWeightTextRaw = reportingSide?.let { weightTextForSide(it) } ?: weightText
                        val rawReportedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        val valueGhost = if (supportsIndependentSides) {
                            if (selectedSide == "left") leftValueGhost else rightValueGhost
                        } else {
                            plannedValueGhost
                        }
                        val weightGhost = if (manualLoadRequiredNow) "" else plannedWeightGhost.orEmpty()
                        if (resolvedPlannedUnitMode == UnitModeV2.REPS && !isFailedSet) {
                            val repsMax = buildRepsCarouselMax(
                                currentValue = rawReportedValueText.toIntOrNull() ?: valueGhost.toIntOrNull() ?: 0,
                                ghostValue = valueGhost.toIntOrNull(),
                            )
                            val centeredRepsIndex = effectiveCarouselSelectedIndex(
                                selectedIndex = repsCarouselIndexFromValue(
                                    rawReportedValueText.ifBlank { valueGhost },
                                    repsMax,
                                ),
                                centeredIndex = repsWheelCenterIndex.intValue,
                                itemCount = repsMax + 1,
                            )
                            updateActiveValueText(centeredRepsIndex.toString())
                        }
                        val syncedValueText = reportingSide?.let { valueTextForSide(it) } ?: valueText
                        // Un cero explícito no se sustituye por las reps del plan: solo es válido con parciales en REPS.
                        val reportedValueText = if (syncedValueText.trim() == "0") {
                            "0"
                        } else if (resolvedPlannedUnitMode == UnitModeV2.REPS) {
                            effectiveCarouselRepsText(syncedValueText, valueGhost)
                        } else {
                            syncedValueText.trim()
                        }
                        val reportedWeightText = if (reportedWeightTextRaw.isBlank()) effectiveCarouselWeightText("", weightGhost) else reportedWeightTextRaw.trim()
                        if (!isFailedSet && loadMode != LoadModeV2.BODYWEIGHT) {
                            val parsedWeight = reportedWeightText.replace(',', '.').toDoubleOrNull()
                            if (parsedWeight == null || !parsedWeight.isFinite() || parsedWeight <= 0.0) {
                                quantityError = "Carga inválida. Revisa el peso antes de registrar."
                                android.widget.Toast.makeText(
                                    context,
                                    "Carga inválida. Revisa el peso antes de registrar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                        }
                        if (!isFailedSet) {
                            val parsedValue = liveQuantityNumber(reportedValueText, resolvedPlannedUnitMode)
                            val zeroRepCalibrator = isAmrap && currentSet.purpose == com.example.kpkn.data.models.SetPurpose.CALIBRATOR &&
                                resolvedPlannedUnitMode == UnitModeV2.REPS && hasChosenReportedEffort
                            if (parsedValue == null || parsedValue < 0.0 || (parsedValue == 0.0 && !(resolvedPlannedUnitMode == UnitModeV2.REPS && partialRepsTotal > 0) && !zeroRepCalibrator)) {
                                quantityError = if (isTimeMode) "Introduce un tiempo positivo en min:s (segundos de 0 a 59)." else "Introduce una cantidad positiva."
                                android.widget.Toast.makeText(
                                    context,
                                    "Reps/tiempo inválidos. Revisa el valor antes de registrar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                        }
                        val weight = if (loadMode == LoadModeV2.BODYWEIGHT) 0.0 else (reportedWeightText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                        val typedValue = if (isFailedSet) 0.0 else (liveQuantityNumber(reportedValueText, resolvedPlannedUnitMode) ?: 0.0)
                        val intensity = when {
                            isAmrap && amrapReachFailure -> 10.0
                            isAmrap && !amrapReachFailure -> amrapReserveReps?.toDouble()
                            reachedFailure -> 10.0
                            // Solo la marca que la persona eligió: sin ella no se llega aquí, y la marca «—» (SOLO_RM) no lleva valor.
                            else -> chosenEffortValue
                        }
                        val resolvedUnitMode = resolvedPlannedUnitMode
                        val resolvedBodyWeight = bodyWeightText.toDoubleOrNull()
                        // AMRAP records the athlete's actual result.  The planned
                        // minimum is surfaced as feedback, never as a silent clamp.
                        val value = typedValue

                        val scheduledPlan = scheduledPlanForAction
                        if (scheduledPlan != null && !isFailedSet) {
                            if (scheduledRestRemainingSeconds > 0) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Espera la pausa técnica antes de continuar.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                return@label
                            }
                            val phase = scheduledPhaseIndex
                            val capture = scheduledMainCapture
                            if (scheduledAwaitingCommit && capture != null) {
                                val retryDropRows = scheduledDropDrafts.toMutableList()
                                val retryRestRows = scheduledRestPauseDrafts.toMutableList()
                                when (scheduledPlan.kind) {
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET -> {
                                        if (retryDropRows.isNotEmpty()) {
                                            retryDropRows[retryDropRows.lastIndex] = DropSetEntry(
                                                weight = weight,
                                                reps = value.toInt().coerceAtLeast(0),
                                            )
                                        }
                                    }
                                    com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> {
                                        if (retryRestRows.isNotEmpty()) {
                                            retryRestRows[retryRestRows.lastIndex] = RestPauseData(
                                                restTime = scheduledPlan.pauseSeconds,
                                                reps = value.toInt().coerceAtLeast(0),
                                                style = if(currentSet.isMyoReps()) "myo" else null,
                                            )
                                        }
                                    }
                                }
                                submitScheduledRecord(capture, retryDropRows, retryRestRows)
                                return@label
                            }
                            if (phase == 0 || capture == null) {
                                val main = GuidedMainCapture(
                                    loadMode = loadMode,
                                    unitMode = resolvedUnitMode,
                                    weight = weight,
                                    value = value,
                                    intensity = intensity,
                                    amrapOverride = isAmrap,
                                    bodyWeight = resolvedBodyWeight,
                                    side = reportingSide,
                                    mainFeedback = advanced,
                                )
                                val progress = WorkoutTechniqueProgressDraft(
                                    kind = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET)
                                        WorkoutTechniqueDraftKind.SCHEDULED_DROP else WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE,
                                    phaseIndex = 1, phaseCount = scheduledPlan.phaseCount,
                                    dropWeightText = (if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET)
                                        com.example.kpkn.domain.workout.nextDropInputLoad(weight, scheduledPlan.dropKg, loadMode) else weight).toTrimmedNumberString(),
                                    dropRepsText = scheduledPlan.followUpReps.toString(),
                                    restRemainingSeconds = scheduledPlan.pauseSeconds.takeIf { scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE })
                                submitOpenTechnique(main, progress) {
                                    scheduledMainCapture = main
                                    scheduledPhaseIndex = 1
                                    val nextLoad = when (scheduledPlan.kind) {
                                        com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET ->
                                            com.example.kpkn.domain.workout.nextDropInputLoad(weight, scheduledPlan.dropKg, loadMode)
                                        com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> weight
                                    }
                                    if (loadMode != LoadModeV2.BODYWEIGHT && nextLoad > 0.0) {
                                        updateActiveWeightText(nextLoad.toTrimmedNumberString(), markManual = false)
                                    }
                                    updateActiveValueText(scheduledPlan.followUpReps.toString())
                                    scheduledRestRemainingSeconds = if (
                                        scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE
                                    ) {
                                        scheduledPlan.pauseSeconds
                                    } else {
                                        0
                                    }
                                }
                                return@label
                            }

                            val currentDropRow = DropSetEntry(
                                weight = weight,
                                reps = value.toInt().coerceAtLeast(0),
                            )
                            val currentRestPauseRow = RestPauseData(
                                restTime = scheduledPlan.pauseSeconds,
                                reps = value.toInt().coerceAtLeast(0),
                                style = if(currentSet.isMyoReps()) "myo" else null,
                            )
                            val nextDropRows = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET) {
                                scheduledDropDrafts + currentDropRow
                            } else {
                                scheduledDropDrafts
                            }
                            val nextRestRows = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE) {
                                scheduledRestPauseDrafts + currentRestPauseRow
                            } else {
                                scheduledRestPauseDrafts
                            }
                            val nextPhase = phase + 1
                            if (nextPhase < scheduledPlan.phaseCount) {
                                val progress = WorkoutTechniqueProgressDraft(
                                    kind = if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET)
                                        WorkoutTechniqueDraftKind.SCHEDULED_DROP else WorkoutTechniqueDraftKind.SCHEDULED_REST_PAUSE,
                                    phaseIndex = nextPhase, phaseCount = scheduledPlan.phaseCount,
                                    dropWeightText = (if (scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET)
                                        com.example.kpkn.domain.workout.nextDropInputLoad(weight, scheduledPlan.dropKg, loadMode) else weight).toTrimmedNumberString(),
                                    dropRepsText = scheduledPlan.followUpReps.toString(),
                                    dropRows = nextDropRows.map { DropSetData(it.weight, it.reps) }, restPauseRows = nextRestRows,
                                    restRemainingSeconds = scheduledPlan.pauseSeconds.takeIf { scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE })
                                submitOpenTechnique(capture, progress) {
                                    scheduledDropDrafts = nextDropRows
                                    scheduledRestPauseDrafts = nextRestRows
                                    scheduledPhaseIndex = nextPhase
                                    val nextLoad = when (scheduledPlan.kind) {
                                        com.example.kpkn.domain.workout.ScheduledTechniqueKind.DROP_SET ->
                                            com.example.kpkn.domain.workout.nextDropInputLoad(weight, scheduledPlan.dropKg, loadMode)
                                        com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE -> weight
                                    }
                                    if (loadMode != LoadModeV2.BODYWEIGHT && nextLoad > 0.0) {
                                        updateActiveWeightText(nextLoad.toTrimmedNumberString(), markManual = false)
                                    }
                                    updateActiveValueText(scheduledPlan.followUpReps.toString())
                                    scheduledRestRemainingSeconds = if (
                                        scheduledPlan.kind == com.example.kpkn.domain.workout.ScheduledTechniqueKind.REST_PAUSE
                                    ) scheduledPlan.pauseSeconds else 0
                                }
                                return@label
                            }

                            submitScheduledRecord(capture, nextDropRows, nextRestRows)
                            return@label

                        }

                        if (dropSetEnabled || restPauseEnabled) {
                            val capture = GuidedMainCapture(
                                loadMode = loadMode,
                                unitMode = resolvedUnitMode,
                                weight = weight,
                                value = value,
                                intensity = intensity,
                                amrapOverride = isAmrap,
                                bodyWeight = resolvedBodyWeight,
                                side = reportingSide,
                                mainFeedback = advanced,
                            )
                            val total = if (dropSetEnabled) dropSets.size.coerceIn(1, 5) else restPauseSets.size.coerceIn(1, 5)
                            val guide = currentSet.resolvePlannedTechniqueGuide() ?: PlannedTechniqueGuide(
                                kind = TechniqueType.DROP_SET, count = total, dropPcts = listOf(-DropSetPlanDefaults.DropKg))
                            val progress = if (dropSetEnabled) dropProgress(0, total, capture, guide)
                                else WorkoutTechniqueProgressDraft(kind = WorkoutTechniqueDraftKind.GUIDED_REST_PAUSE,
                                    phaseIndex = 0, phaseCount = total, restRemainingSeconds = activeRestPauseSeconds,
                                    restPauseRepsText = activeRestReps.toString())
                            submitOpenTechnique(capture, progress) {
                                guidedMainCapture = capture
                                if (dropSetEnabled) beginDropPhase(0, total, capture, guide)
                                else beginRestPauseCountdown(0, total)
                            }
                            return@label
                        }

                        submitRecord(
                            loadMode,
                            resolvedUnitMode,
                            weight,
                            value,
                            intensity,
                            advanced,
                            isAmrap,
                            resolvedBodyWeight,
                            reportingSide,
                        ) {
                            if (supportsIndependentSides && !sideLocked) {
                                resetEffortForNextSide()
                                selectSide(if (selectedSide == "left") "right" else "left")
                            }
                        }
                        },
                    )
                } else {
                    recordActionHolder.clearIfOwner(recordActionInstanceOwner)
                }
            }
            DisposableEffect(recordActionHolder, recordActionInstanceOwner) {
                onDispose { recordActionHolder.clearIfOwner(recordActionInstanceOwner) }
            }
            }
            if (legacyModeLayout && !showAmrapResult) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFF111111))
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    LiveSetCardActions(onAdvanced = { cardFace = SetCardFace.Technique },
                        onVisual = { cardFace = SetCardFace.ExerciseMedia }, onHistory = onShowHistory)
                }
            }
            if (showAmrapResult) Box(Modifier.matchParentSize()) {
                amrapResultPanel?.invoke { editingAmrapResult = true }
            }
            // Ley 1 (LV8): la tarjeta no cambia de tamaño en ningún estado. El panel guiado de drop-set y rest-pause y el aviso del peso
            // corporal del asistido ya no se insertan arriba (la alargaban): van ENCIMA de la parte de abajo, con la carga a la vista.
            if (scheduledMainCapture != null && !isRecordSubmissionPending && !showAmrapResult) {
                androidx.compose.material3.TextButton(
                    onClick = { guidedSkipAction[0]?.invoke() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) { Text("Terminar técnica con lo realizado") }
            }
            val guidedPhaseNow = guidedPhase
            // Programmed editor techniques are VOLUME_REPLACED and use the existing top band/timeline.  The guided surface belongs
            // to manual techniques, including those selected on an originally normal set; rendering it for a scheduled plan cuts the live card (the bug shown in the
            // beta capture).
            if (!showAmrapResult && guidedPhaseNow != null && !currentSet.isInlineEditorScheduledTechnique()) {
                BoxWithConstraints(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = (maxHeight - GuidedPanelTopReserve).coerceAtLeast(160.dp))
                            .background(KpknSurface.module)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        GuidedTechniquePanel(
                            phase = guidedPhaseNow,
                            loadMode = loadMode,
                            restPauseStyle = if(currentSet.isMyoReps()) "myo" else null,
                            pauseSeconds = activeRestPauseSeconds,
                            targetRestReps = activeRestReps,
                            accentColor = sessionAccentColor,
                            dropWeightText = guidedDropWeightText,
                            dropRepsText = guidedDropRepsText,
                            restPauseRepsText = guidedRestPauseRepsText,
                            onDropWeightChange = { guidedDropWeightText = it },
                            onDropRepsChange = { guidedDropRepsText = it },
                            onRestPauseRepsChange = { guidedRestPauseRepsText = it },
                            onSkipTechnique = { guidedSkipAction[0]?.invoke() },
                        )
                    }
                }
            }
            if (!showAmrapResult && loadMode == LoadModeV2.ASSISTED && (showBodyWeightPrompt || bodyWeightText.isBlank())) {
                Box(modifier = Modifier.matchParentSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF222222),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = bodyWeightText,
                                onValueChange = { bodyWeightText = it },
                                label = { Text("Peso corporal (kg)", fontWeight = FontWeight.SemiBold) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = sessionAccentColor,
                                    unfocusedBorderColor = Color(0xFF555555),
                                    focusedLabelColor = sessionAccentColor,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF2A2A2A),
                                    unfocusedContainerColor = Color(0xFF2A2A2A),
                                ),
                            )
                            Button(
                                onClick = {
                                    bodyWeightText.toDoubleOrNull()?.let {
                                        onSetBodyWeight(it)
                                        showBodyWeightPrompt = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = sessionAccentColor,
                                    contentColor = com.example.kpkn.screens.sessioneditor.contentOn(sessionAccentColor),
                                ),
                            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            }
            }
            GodModeSetActionsOverlay(
                visible = godModeActionsVisible && flipRotation <= 90f,
                isDropSet = isDropSet,
                isRestPause = isRestPause,
                canDeleteSet = canDeleteSet,
                onDropSet = { onDropSet?.invoke() },
                onRestPause = { onRestPause?.invoke() },
                onDeleteSet = { onDeleteSet?.invoke() },
                onOmitSet = { onOmitSet?.invoke() },
                modifier = Modifier.matchParentSize(),
            )
            if (flipRotation > 90f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationY = 180f },
            ) {
                when (cardFace) {
                    SetCardFace.ExerciseMedia -> SetCardExerciseMediaBack(
                        exercise = exercise,
                        onFlipBack = { cardFace = SetCardFace.Front },
                        isSettledPage = isSettledPage,
                        setIndex = setIndex,
                        side = activeSide ?: selectedSide,
                        // Lo que muestra la tarjeta es lo que se guarda con el medio (LV8): lo escrito o, si no, la carga y las reps previstas.
                        weightKg = reportWeightText.replace(',', '.').toDoubleOrNull()
                            ?: plannedWeightGhost?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 }
                            ?: sessionCompletedSet?.weight?.takeIf { it > 0.0 },
                        reps = liveQuantityNumber(reportValueText, resolvedPlannedUnitMode)?.roundToInt()
                            ?: (if (supportsIndependentSides) { if (selectedSide == "left") leftValueGhost else rightValueGhost } else plannedValueGhost)
                                .toIntOrNull()?.takeIf { it > 0 }
                            ?: sessionCompletedSet?.reps?.takeIf { it > 0 },
                        isPr = isPrSeries,
                        mediaCapture = mediaCapture,
                        onRetryPendingCaptures = onRetryPendingCaptures,
                        effortLabel = chosenEffortItem?.let { effortValueLabel(it) }?.takeIf { it != "—" },
                        onShowHistory = onShowHistory,
                        onOpenAnalysis = onOpenAnalysis,
                        videoThumbBadges = videoThumbBadges,
                        videoAnalysis = videoAnalysis,
                        onRetryAnalysis = onRetryAnalysis,
                        techniqueVideoSettings = techniqueVideoSettings,
                    )
                    else -> SetCardTechniqueBack(
                    loadMode = loadMode,
                    currentSet = currentSet,
                    coverWeightLabel = if (reportWeightText.isBlank()) "—" else "${reportWeightText} kg",
                    coverValueLabel = if (reportValueText.isBlank()) "—" else when (resolvedPlannedUnitMode) {
                        UnitModeV2.TIME -> parseLiveDurationSeconds(reportValueText)?.let(::formatLiveDuration) ?: reportValueText
                        UnitModeV2.DISTANCE -> "$reportValueText m"
                        UnitModeV2.CUSTOM -> "$reportValueText ${exercise.customUnit.orEmpty()}"
                        UnitModeV2.REPS -> "$reportValueText reps"
                    },
                    coverIntensityLabel = intensityText.takeIf { it.isNotBlank() }?.let { "Int. $it" },
                    mainWeight = reportWeightText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    mainReps = liveQuantityNumber(reportValueText, resolvedPlannedUnitMode)?.roundToInt() ?: 0,
                    sessionAccentColor = sessionAccentColor,
                    isFailedSet = isFailedSet,
                    onFailedSetChange = { failed ->
                        isFailedSet = failed
                        if (failed) {
                            triggerFailureHaptic(context)
                            triggerFailureSound(context)
                            reachedFailure = false
                        }
                    },
                    onFlipBack = { cardFace = SetCardFace.Front },
                    noteText = setNoteText,
                    onNoteChange = { setNoteText = it },
                    initialDropEnabled = dropSetEnabled,
                    initialRestPauseEnabled = restPauseEnabled,
                    onDropEnabledChange = { enabled ->
                        dropSetEnabled = enabled
                        if (enabled) restPauseEnabled = false
                    },
                    onRestPauseEnabledChange = { enabled ->
                        restPauseEnabled = enabled
                        if (enabled) dropSetEnabled = false
                    },
                    showPrMediaShortcut = LiveSetPrPolicy.shouldShowMediaShortcut(isPrSeries),
                    onOpenPrMedia = { cardFace = SetCardFace.ExerciseMedia },
                    setNumber = setIndex + 1,
                    loadStepKg = quickLoadIncrementFor(exercise, currentSet),
                    onCompleteTechniques = { drops, pauses ->
                        dropSetEnabled = drops.isNotEmpty() || dropSetEnabled
                        dropSets = drops.map { DropSetEntry(weight = it.weight, reps = it.reps) }.ifEmpty {
                            listOf(DropSetEntry(0.0, 0))
                        }
                        restPauseEnabled = pauses.isNotEmpty() || restPauseEnabled
                        restPauseSets = pauses.ifEmpty { listOf(RestPauseData(restTime = activeRestPauseSeconds, reps = 0, style = if(currentSet.isMyoReps()) "myo" else null)) }
                        cardFace = SetCardFace.Front
                    },
                )
                }
            }
        }
    }
            androidx.compose.animation.AnimatedVisibility(
                visible = isFailedSet,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
                        androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(250)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                        androidx.compose.animation.scaleOut(targetScale = 0.96f, animationSpec = androidx.compose.animation.core.tween(200)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(WorkoutUiTokens.CardShape)
                        .background(Color(0xFF8B1E1E).copy(alpha = 0.94f))
                        .clickable { /* consume taps while the error state is visible */ }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Marcaste esta serie como fallida",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Quiere decir que tuviste una incomodidad, molestia o deformación de la técnica que te impidió completarla. Ten cuidado y siempre procura usar cargas que puedas manejar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Causa o motivo del fallo (opcional):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.90f),
                            )
                            OutlinedTextField(
                                value = failedSetReason,
                                onValueChange = { failedSetReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        "Ej. Molestia en hombro, fallo técnico, carga excesiva…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.50f),
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                singleLine = false,
                                maxLines = 2,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White.copy(alpha = 0.60f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                    unfocusedContainerColor = Color.Black.copy(alpha = 0.20f),
                                    cursorColor = Color.White,
                                ),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf("Molestia articular", "Fallo técnico", "Carga excesiva", "Pérdida de agarre").forEach { chipText ->
                                    Surface(
                                        onClick = { failedSetReason = chipText },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (failedSetReason == chipText) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                    ) {
                                        Text(
                                            chipText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = Color.White.copy(alpha = 0.90f),
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isFailedSet = false
                                failedSetReason = ""
                                onRevertExecutionError?.invoke()
                            },
                            modifier = Modifier.widthIn(min = 140.dp, max = 220.dp).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.40f),
                                contentColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        ) {
                            Text("Revertir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RomReportSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Pista fina y pulgar de 18 dp dentro de 44 dp táctiles. Los chips junto a la carga liberan su espacio;
    // las acciones permanecen ancladas y el contenido adicional se desplaza dentro del marco fijo.
    val clampedValue = value.coerceIn(10, 100)
    val sliderColors = SliderDefaults.colors(
        thumbColor = WizardColors.text,
        activeTrackColor = WizardColors.text.copy(alpha = 0.85f),
        inactiveTrackColor = KpknSurface.inset,
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
        disabledActiveTickColor = Color.Transparent,
        disabledInactiveTickColor = Color.Transparent,
    )
    Row(
        modifier = modifier.height(44.dp).testTag("live-rom-slider"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "ROM",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
            Text(
                text = "$clampedValue %",
                style = KpknType.chip.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
                maxLines = 1,
            )
        }
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 44.dp,
        ) {
            Slider(
                value = clampedValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().coerceIn(10, 100)) },
                valueRange = 10f..100f,
                steps = 0,
                colors = sliderColors,
                thumb = { Box(Modifier.size(18.dp).background(WizardColors.text, CircleShape)) },
                track = {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(KpknSurface.inset)) {
                        Box(Modifier.fillMaxWidth((clampedValue - 10) / 90f).height(4.dp).background(WizardColors.text.copy(alpha = .85f)))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
        }
    }
}
