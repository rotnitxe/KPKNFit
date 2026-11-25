package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kpkn.domain.workout.LiveInputDockEngine
import com.example.kpkn.domain.workout.LiveInputDockMode
import com.example.kpkn.domain.workout.LiveInputFieldKind
import com.example.kpkn.domain.workout.LiveKeypadDraft
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Tokens monocromáticos neutrales estrictos de KPKN Fit (cero tonos amarillos / ámbar)
private val DockSheetBg = Color(0xFF141416)
private val DockWellBg = Color(0xFF19191C)
private val DockKeyBg = Color(0xFF232327)
private val DockKeyHover = Color(0xFF2C2C31)
private val DockKeyEmpty = Color(0xFF1D1D20)
private val DockHairline = Color.White.copy(alpha = 0.08f)
private val DockHairlineStrong = Color.White.copy(alpha = 0.16f)
private val DockInk = Color(0xFFF4F4F6)
private val DockInkDark = Color(0xFF111113)
private val DockMuted = Color(0xFFA1A1AA)
private val DockFaint = Color(0xFF71717A)

private val CarouselEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * Helper tipográfico estricto para texto inline del dock.
 * Asegura alineación métrica perfecta evitando números flotantes o elevados.
 */
@Composable
private fun dockInlineTextStyle(
    sizeSp: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = DockInk,
): TextStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontSize = sizeSp.sp,
    lineHeight = sizeSp.sp,
    fontWeight = weight,
    color = color,
    fontFeatureSettings = "'tnum' 1, 'lnum' 1",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

internal data class WorkoutKeyboardField(
    val label: String,
    val title: String,
    val initialValue: String,
    val allowDecimal: Boolean,
    val allowColon: Boolean = false,
    val stepDelta: Double? = null,
    val kind: LiveInputFieldKind = LiveInputFieldKind.QUANTITY,
    val previousValue: String? = null,
    val minValue: Double = 0.0,
    val maxValue: Double = 500.0,
)

/**
 * Dock inferior compacto, adaptativo y nativo de KPKN Fit para sesión en vivo.
 * Soporta doble modo instantáneo (Deslizar ↔ Teclado) y personalización de pasos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutNumericKeyboardDialog(
    title: String,
    initialValue: String,
    allowDecimal: Boolean,
    allowColon: Boolean = false,
    contextValues: List<Pair<String, String>> = emptyList(),
    activeContextIndex: Int? = null,
    fields: List<WorkoutKeyboardField> = emptyList(),
    initialFieldIndex: Int = 0,
    initialMode: LiveInputDockMode = LiveInputDockMode.LENS,
    showFieldTabs: Boolean = fields.size > 1,
    allowCustomLoadStep: Boolean = false,
    loadStepKg: Double = 2.5,
    onLoadStepKgChange: ((Double) -> Unit)? = null,
    onApplyFields: ((List<String>) -> Unit)? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onModeChange: ((LiveInputDockMode) -> Unit)? = null,
) {
    val effectiveFields = remember(fields, title, initialValue, allowDecimal, allowColon) {
        if (fields.isNotEmpty()) fields
        else listOf(
            WorkoutKeyboardField(
                label = if (allowColon) "TIEMPO" else if (allowDecimal) "KG" else "REPS",
                title = title,
                initialValue = initialValue,
                allowDecimal = allowDecimal,
                allowColon = allowColon,
                kind = when {
                    allowColon -> LiveInputFieldKind.TIME_MM_SS
                    allowDecimal -> LiveInputFieldKind.WEIGHT
                    else -> LiveInputFieldKind.QUANTITY
                },
            ),
        )
    }

    var activeFieldIndex by remember(initialFieldIndex) {
        mutableIntStateOf(initialFieldIndex.coerceIn(0, effectiveFields.lastIndex))
    }

    // No invalidar los borradores si el padre recompone (p. ej. al cambiar `loadStepKg` en Settings).
    val stagedDrafts = remember {
        mutableStateListOf<LiveKeypadDraft>().apply {
            effectiveFields.forEach {
                add(LiveKeypadDraft(text = it.initialValue.replace('.', ','), pendingReplace = true))
            }
        }
    }
    LaunchedEffect(effectiveFields.size) {
        while (stagedDrafts.size < effectiveFields.size) {
            val f = effectiveFields[stagedDrafts.size]
            stagedDrafts.add(LiveKeypadDraft(text = f.initialValue.replace('.', ','), pendingReplace = true))
        }
    }

    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
    var currentLoadStepKg by remember(loadStepKg) { mutableDoubleStateOf(LiveInputDockEngine.clampStepKg(loadStepKg)) }
    var stepDrawerOpen by remember { mutableStateOf(false) }
    var showingCustomInput by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }

    val activeField = effectiveFields[activeFieldIndex]
    val activeDraft = stagedDrafts[activeFieldIndex]
    val activeValue = activeDraft.text
    val activeKind = activeField.kind
    val activeAllowDecimal = activeField.allowDecimal
    val activeAllowColon = activeField.allowColon
    val activeTitle = activeField.title

    fun updateDraft(next: LiveKeypadDraft) {
        stagedDrafts[activeFieldIndex] = next
    }

    val activeNumericValue by remember(activeValue, activeAllowColon) {
        derivedStateOf {
            LiveInputDockEngine.parseDockNumber(activeValue, activeAllowColon) ?: activeField.minValue
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .background(DockSheetBg)
                    .border(
                        width = 1.dp,
                        color = DockHairline,
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    )
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume tap so background scrim doesn't dismiss */ }
                    }
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp)
                    .testTag("workout-numeric-keyboard"),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 3.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                        .align(Alignment.CenterHorizontally),
                )

                // Dock Header Row (FlowRow evita solapamiento/recorte en teléfonos de 320–360 dp)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Group: Tabs or Kicker + Step Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (showFieldTabs && effectiveFields.size > 1) {
                            // Segmented Field Tabs (KG ↔ REPS)
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DockWellBg)
                                    .border(1.dp, DockHairline, CircleShape)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                effectiveFields.forEachIndexed { index, field ->
                                    val isSelected = index == activeFieldIndex
                                    val fieldDraft = stagedDrafts.getOrNull(index)
                                    val valText = fieldDraft?.text?.ifBlank { "—" } ?: "—"
                                    Row(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                            .clickable(role = Role.Tab) {
                                                activeFieldIndex = index
                                                stepDrawerOpen = false
                                            }
                                            .semantics { contentDescription = "Editar ${field.label}" }
                                            .testTag("workout-numeric-keyboard-tab-$index")
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = field.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            style = dockInlineTextStyle(
                                                sizeSp = 11f,
                                                weight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isSelected) DockInk else DockFaint,
                                            ),
                                        )
                                        Text(
                                            text = valText,
                                            maxLines = 1,
                                            softWrap = false,
                                            style = dockInlineTextStyle(
                                                sizeSp = 11f,
                                                weight = FontWeight.Bold,
                                                color = if (isSelected) DockInk else DockMuted,
                                            ),
                                        )
                                    }
                                }
                            }
                        } else {
                            // Single Field Kicker
                            Text(
                                text = activeTitle.uppercase(),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp),
                                style = dockInlineTextStyle(
                                    sizeSp = 10.5f,
                                    weight = FontWeight.Bold,
                                    color = DockMuted,
                                ),
                            )
                        }

                        // Selector de paso (± 2,5 ▾) EXCLUSIVAMENTE en modo Deslizar para Carga
                        if (currentMode == LiveInputDockMode.LENS && activeKind == LiveInputFieldKind.WEIGHT && allowCustomLoadStep) {
                            val formattedStep = LiveInputDockEngine.formatDockNumber(
                                currentLoadStepKg,
                                allowDecimal = true,
                                allowColon = false,
                            )
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (stepDrawerOpen) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.06f))
                                    .border(
                                        width = 1.dp,
                                        color = if (stepDrawerOpen) DockHairlineStrong else DockHairline,
                                        shape = CircleShape,
                                    )
                                    .clickable(role = Role.Button) {
                                        stepDrawerOpen = !stepDrawerOpen
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = "± $formattedStep",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 10.5f,
                                        weight = FontWeight.SemiBold,
                                        color = if (stepDrawerOpen) DockInk else DockMuted,
                                    ),
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Desplegar incrementos de carga",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (stepDrawerOpen) DockInk else DockMuted,
                                )
                            }
                        }
                    }

                    // Right Group: Previous Value copy + Mode Switch (Deslizar ↔ Teclado)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (activeField.previousValue != null) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable(role = Role.Button) {
                                        updateDraft(
                                            LiveKeypadDraft(
                                                text = activeField.previousValue.replace('.', ','),
                                                pendingReplace = false,
                                            ),
                                        )
                                    }
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Copiar anterior",
                                    modifier = Modifier.size(11.dp),
                                    tint = DockMuted,
                                )
                                Text(
                                    text = "Ant. ${activeField.previousValue}",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 78.dp),
                                    style = dockInlineTextStyle(
                                        sizeSp = 10.5f,
                                        weight = FontWeight.SemiBold,
                                        color = DockMuted,
                                    ),
                                )
                            }
                        }

                        // Mode Switch
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DockWellBg)
                                .border(1.dp, DockHairline, CircleShape)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isLens = currentMode == LiveInputDockMode.LENS
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isLens) Color.White.copy(alpha = 0.13f) else Color.Transparent)
                                    .clickable(role = Role.RadioButton) {
                                        currentMode = LiveInputDockMode.LENS
                                        stepDrawerOpen = false
                                        onModeChange?.invoke(LiveInputDockMode.LENS)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Deslizar",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 10.5f,
                                        weight = FontWeight.SemiBold,
                                        color = if (isLens) DockInk else DockFaint,
                                    ),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (!isLens) Color.White.copy(alpha = 0.13f) else Color.Transparent)
                                    .clickable(role = Role.RadioButton) {
                                        currentMode = LiveInputDockMode.KEYPAD
                                        stepDrawerOpen = false
                                        updateDraft(activeDraft.copy(pendingReplace = true))
                                        onModeChange?.invoke(LiveInputDockMode.KEYPAD)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Teclado",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 10.5f,
                                        weight = FontWeight.SemiBold,
                                        color = if (!isLens) DockInk else DockFaint,
                                    ),
                                )
                            }
                        }
                    }
                }

                // Drawer de selección de incrementos de carga (solo si chip abierto y modo Deslizar)
                if (stepDrawerOpen && currentMode == LiveInputDockMode.LENS && activeKind == LiveInputFieldKind.WEIGHT && allowCustomLoadStep) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(DockWellBg)
                            .border(1.dp, DockHairline, RoundedCornerShape(13.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            (listOf(0.25) + LiveInputDockEngine.PRESET_LOAD_STEPS_KG).forEach { preset ->
                                val isSelected = !showingCustomInput && abs(currentLoadStepKg - preset) < 1e-4
                                val label = if (preset % 1.0 == 0.0) "${preset.toInt()}" else preset.toString().replace('.', ',')
                                Box(
                                    modifier = Modifier
                                        .height(27.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) DockInk else Color.White.copy(alpha = 0.05f))
                                        .clickable(role = Role.Button) {
                                            currentLoadStepKg = preset
                                            showingCustomInput = false
                                            stepDrawerOpen = false
                                            onLoadStepKgChange?.invoke(preset)
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$label kg",
                                        maxLines = 1,
                                        softWrap = false,
                                        style = dockInlineTextStyle(
                                            sizeSp = 11f,
                                            weight = FontWeight.SemiBold,
                                            color = if (isSelected) DockInkDark else DockMuted,
                                        ),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .height(27.dp)
                                    .clip(CircleShape)
                                    .background(if (showingCustomInput) DockInk else Color.White.copy(alpha = 0.05f))
                                    .clickable(role = Role.Button) {
                                        showingCustomInput = !showingCustomInput
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Otro",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 11f,
                                        weight = FontWeight.SemiBold,
                                        color = if (showingCustomInput) DockInkDark else DockMuted,
                                    ),
                                )
                            }
                        }

                        if (showingCustomInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                BasicTextField(
                                    value = customInputText,
                                    onValueChange = { customInputText = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, DockHairlineStrong, CircleShape)
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    textStyle = dockInlineTextStyle(
                                        sizeSp = 12f,
                                        weight = FontWeight.SemiBold,
                                        color = DockInk,
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    cursorBrush = SolidColor(DockInk),
                                    decorationBox = { inner ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (customInputText.isBlank()) {
                                                Text(
                                                    text = "Paso e.g. 1,5 kg (0,1–25)",
                                                    style = dockInlineTextStyle(
                                                        sizeSp = 11f,
                                                        color = DockFaint,
                                                    ),
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .height(28.dp)
                                        .clip(CircleShape)
                                        .background(DockInk)
                                        .clickable(role = Role.Button) {
                                            val sanitized = LiveInputDockEngine.sanitizeCustomStepKg(customInputText)
                                            if (sanitized != null) {
                                                currentLoadStepKg = sanitized
                                                onLoadStepKgChange?.invoke(sanitized)
                                                stepDrawerOpen = false
                                                showingCustomInput = false
                                            }
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Fijar",
                                        style = dockInlineTextStyle(
                                            sizeSp = 11.5f,
                                            weight = FontWeight.Bold,
                                            color = DockInkDark,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // CUERPO MODO 1: DESLIZAR (Lente + Pastillas)
                // ==========================================
                if (currentMode == LiveInputDockMode.LENS) {
                    val step = when (activeKind) {
                        LiveInputFieldKind.WEIGHT -> currentLoadStepKg
                        LiveInputFieldKind.TIME_MM_SS -> 5.0
                        LiveInputFieldKind.REPS -> 1.0
                        LiveInputFieldKind.QUANTITY -> activeField.stepDelta ?: if (activeAllowDecimal) 2.5 else 1.0
                    }

                    // Carrusel horizontal nativo con lente central
                    LiveLensCarousel(
                        value = activeNumericValue,
                        step = step,
                        min = activeField.minValue,
                        max = activeField.maxValue,
                        kind = activeKind,
                        allowDecimal = activeAllowDecimal,
                        allowColon = activeAllowColon,
                        onValueChange = { nextVal ->
                            val formatted = LiveInputDockEngine.formatDockNumber(
                                nextVal,
                                activeAllowDecimal,
                                activeAllowColon,
                            )
                            updateDraft(LiveKeypadDraft(text = formatted, pendingReplace = false))
                        },
                    )

                    // Pastillas inferiores de salto rápido (ESPECIFICACIÓN COMPACTA ESTRICTA)
                    // Alto: 28.dp | Fuente: 11.sp | Padding lateral: 10.dp | Separación: 5.dp
                    val deltas = remember(activeKind, step) {
                        LiveInputDockEngine.quickJumpDeltas(activeKind, step)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        deltas.forEachIndexed { idx, delta ->
                            val label = LiveInputDockEngine.formatQuickDeltaPill(delta, activeKind)
                            val tag = when (idx) {
                                1 -> "workout-numeric-quick-minus"
                                2 -> "workout-numeric-quick-plus"
                                else -> null
                            }
                            val mod = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.045f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) {
                                    val next = activeDraft.onStep(
                                        delta = delta,
                                        min = activeField.minValue,
                                        max = activeField.maxValue,
                                        allowDecimal = activeAllowDecimal,
                                        allowColon = activeAllowColon,
                                    )
                                    updateDraft(next)
                                }
                            val modWithTag = if (tag != null) mod.testTag(tag) else mod

                            Box(
                                modifier = modWithTag,
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = dockInlineTextStyle(
                                        sizeSp = 11f,
                                        weight = FontWeight.SemiBold,
                                        color = if (delta > 0) DockInk else DockMuted,
                                    ),
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // CUERPO MODO 2: TECLADO (1–9 Limpio)
                // ==========================================
                if (currentMode == LiveInputDockMode.KEYPAD) {
                    val step = when (activeKind) {
                        LiveInputFieldKind.WEIGHT -> currentLoadStepKg
                        LiveInputFieldKind.TIME_MM_SS -> 5.0
                        LiveInputFieldKind.REPS -> 1.0
                        LiveInputFieldKind.QUANTITY -> activeField.stepDelta ?: if (activeAllowDecimal) 2.5 else 1.0
                    }

                    // Lectura única unificada
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(DockWellBg)
                            .border(1.dp, DockHairline, RoundedCornerShape(13.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Número + Unidad alineados por Baseline
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            val isReplace = activeDraft.pendingReplace
                            val numMod = Modifier
                                .alignByBaseline()
                                .testTag("workout-numeric-keyboard-value")
                                .then(
                                    if (isReplace) Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                    else Modifier,
                                )

                            Text(
                                text = activeValue.ifBlank { "—" },
                                modifier = numMod,
                                style = TextStyle(
                                    fontFamily = WizardFonts.display,
                                    fontSize = 24.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = DockInk,
                                    fontFeatureSettings = "'tnum' 1, 'lnum' 1",
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                            )

                            val unitText = when (activeKind) {
                                LiveInputFieldKind.WEIGHT -> "kg"
                                LiveInputFieldKind.REPS -> "reps"
                                LiveInputFieldKind.TIME_MM_SS -> "min:s"
                                LiveInputFieldKind.QUANTITY -> activeField.label.lowercase()
                            }

                            Text(
                                text = unitText,
                                modifier = Modifier
                                    .alignByBaseline()
                                    .padding(start = 5.dp),
                                style = dockInlineTextStyle(
                                    sizeSp = 11.5f,
                                    weight = FontWeight.SemiBold,
                                    color = DockFaint,
                                ),
                            )
                        }

                        // Par compacto −step / +step
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(27.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable(role = Role.Button) {
                                        updateDraft(
                                            activeDraft.onStep(
                                                delta = -step,
                                                min = activeField.minValue,
                                                max = activeField.maxValue,
                                                allowDecimal = activeAllowDecimal,
                                                allowColon = activeAllowColon,
                                            ),
                                        )
                                    }
                                    .testTag("workout-numeric-quick-minus")
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = LiveInputDockEngine.formatQuickDeltaPill(-step, activeKind),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 11f,
                                        weight = FontWeight.SemiBold,
                                        color = DockMuted,
                                    ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(27.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable(role = Role.Button) {
                                        updateDraft(
                                            activeDraft.onStep(
                                                delta = step,
                                                min = activeField.minValue,
                                                max = activeField.maxValue,
                                                allowDecimal = activeAllowDecimal,
                                                allowColon = activeAllowColon,
                                            ),
                                        )
                                    }
                                    .testTag("workout-numeric-quick-plus")
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = LiveInputDockEngine.formatQuickDeltaPill(step, activeKind),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = dockInlineTextStyle(
                                        sizeSp = 11f,
                                        weight = FontWeight.SemiBold,
                                        color = DockInk,
                                    ),
                                )
                            }
                        }
                    }

                    // Grilla 3×4
                    val sep = if (activeAllowColon) ":" else if (activeAllowDecimal) "," else ""
                    val keyRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf(sep, "0", "⌫"),
                    )

                    keyRows.forEachIndexed { rowIndex, rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            rowKeys.forEachIndexed { columnIndex, key ->
                                val isBackspace = key == "⌫"
                                val isBlank = key.isBlank()
                                val tag = "workout-numeric-key-$rowIndex-$columnIndex"

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(39.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isBlank) DockKeyEmpty else DockKeyBg)
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = if (isBackspace) "Borrar último dígito" else key
                                            if (!isBlank) {
                                                onClick {
                                                    when (key) {
                                                        "⌫" -> updateDraft(activeDraft.onBackspace())
                                                        ",", ":" -> updateDraft(activeDraft.onSeparator(activeAllowDecimal, activeAllowColon))
                                                        else -> updateDraft(activeDraft.onDigit(key[0]))
                                                    }
                                                    true
                                                }
                                            }
                                        }
                                        .pointerInput(key, activeDraft) {
                                            detectTapGestures(
                                                onTap = {
                                                    if (!isBlank) {
                                                        when (key) {
                                                            "⌫" -> updateDraft(activeDraft.onBackspace())
                                                            ",", ":" -> updateDraft(activeDraft.onSeparator(activeAllowDecimal, activeAllowColon))
                                                            else -> updateDraft(activeDraft.onDigit(key[0]))
                                                        }
                                                    }
                                                },
                                                onLongPress = {
                                                    if (isBackspace) {
                                                        updateDraft(activeDraft.onLongPressClear())
                                                    }
                                                },
                                            )
                                        }
                                        .testTag(tag),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = key,
                                        style = dockInlineTextStyle(
                                            sizeSp = 16.5f,
                                            weight = FontWeight.SemiBold,
                                            color = if (isBlank) Color.Transparent else DockInk,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // FOOTER DEL DOCK (37.dp)
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cancelar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(37.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable(role = Role.Button, onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cancelar",
                            style = dockInlineTextStyle(
                                sizeSp = 12f,
                                weight = FontWeight.SemiBold,
                                color = DockMuted,
                            ),
                        )
                    }

                    // Siguiente (solo visible si hay múltiples campos y no es el último)
                    if (showFieldTabs && activeFieldIndex < effectiveFields.lastIndex) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(37.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable(role = Role.Button) {
                                    activeFieldIndex++
                                    stepDrawerOpen = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Siguiente →",
                                maxLines = 1,
                                softWrap = false,
                                style = dockInlineTextStyle(
                                    sizeSp = 12f,
                                    weight = FontWeight.SemiBold,
                                    color = DockInk,
                                ),
                            )
                        }
                    }

                    // Aplicar
                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .height(37.dp)
                            .clip(CircleShape)
                            .background(DockInk)
                            .clickable(role = Role.Button) {
                                if (showFieldTabs && onApplyFields != null) {
                                    onApplyFields(stagedDrafts.map { it.text.replace('.', ',') })
                                } else {
                                    onApply(activeDraft.text.replace('.', ','))
                                }
                                onDismiss()
                            }
                            .testTag("workout-numeric-keyboard-apply"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aplicar",
                            maxLines = 1,
                            softWrap = false,
                            style = dockInlineTextStyle(
                                sizeSp = 12.5f,
                                weight = FontWeight.Bold,
                                color = DockInkDark,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Carrusel horizontal nativo a 60 fps con lente central sólida `#F4F4F6` e inercia física.
 * Basado en la geometría y precisión de RepsCarousel.kt.
 */
@Composable
private fun LiveLensCarousel(
    value: Double,
    step: Double,
    min: Double,
    max: Double,
    kind: LiveInputFieldKind,
    allowDecimal: Boolean,
    allowColon: Boolean,
    onValueChange: (Double) -> Unit,
) {
    val density = LocalDensity.current
    val totalTicks = remember(min, max, step) {
        if (step <= 0.0) 1 else (((max - min) / step).roundToInt()).coerceAtLeast(1)
    }

    val targetTick = remember(value, min, step, totalTicks) {
        if (step <= 0.0) 0f
        else (((value - min) / step).toFloat()).coerceIn(0f, totalTicks.toFloat())
    }

    val posAnim = remember { Animatable(targetTick) }
    val scope = rememberCoroutineScope()
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestStep by rememberUpdatedState(step)
    val latestMin by rememberUpdatedState(min)
    val latestMax by rememberUpdatedState(max)
    var isUserControlling by remember { mutableStateOf(false) }

    LaunchedEffect(targetTick, totalTicks) {
        if (!isUserControlling && abs(posAnim.value - targetTick) > 0.01f) {
            posAnim.snapTo(targetTick)
        }
    }

    val cellDp = when {
        allowColon -> 64f
        allowDecimal && step % 1.0 != 0.0 -> 60f
        else -> 54f
    }
    val cellPx = with(density) { cellDp.dp.toPx() }

    val measurer = rememberTextMeasurer()
    val digitStyle = TextStyle(
        fontFamily = WizardFonts.body,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "'tnum' 1, 'lnum' 1",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .pointerInput(totalTicks, cellPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startPos = posAnim.value
                    var lastEmittedTick = startPos.roundToInt().coerceIn(0, totalTicks)
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop != null) {
                        isUserControlling = true
                        horizontalDrag(slop.id) { change ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val raw = startPos - (change.position.x - down.position.x) / cellPx
                            val clamped = when {
                                raw < 0f -> raw * 0.35f
                                raw > totalTicks -> totalTicks + (raw - totalTicks) * 0.35f
                                else -> raw
                            }
                            val snappedTick = clamped.roundToInt().coerceIn(0, totalTicks)
                            if (snappedTick != lastEmittedTick) {
                                lastEmittedTick = snappedTick
                                val liveVal = LiveInputDockEngine.snapValueToStep(
                                    latestMin + snappedTick * latestStep,
                                    latestStep,
                                    latestMin,
                                    latestMax,
                                )
                                latestOnValueChange(liveVal)
                            }
                            scope.launch { posAnim.snapTo(clamped) }
                        }
                        val velocity = -tracker.calculateVelocity().x / cellPx
                        val projected = (posAnim.value + (velocity * 0.22f).coerceIn(-6f, 6f)).roundToInt().coerceIn(0, totalTicks)
                        val projectedVal = LiveInputDockEngine.snapValueToStep(
                            latestMin + projected * latestStep,
                            latestStep,
                            latestMin,
                            latestMax,
                        )
                        latestOnValueChange(projectedVal)
                        scope.launch {
                            try {
                                posAnim.animateTo(
                                    targetValue = projected.toFloat(),
                                    animationSpec = tween(
                                        durationMillis = (180 + 80 * abs(projected - posAnim.value)).roundToInt().coerceIn(200, 480),
                                        easing = CarouselEasing,
                                    ),
                                )
                            } finally {
                                isUserControlling = false
                            }
                        }
                    }
                }
            }
            .pointerInput(totalTicks, cellPx) {
                detectTapGestures { offset ->
                    val c = size.width / 2f
                    val deltaTicks = (offset.x - c) / cellPx
                    val tappedTick = (posAnim.value + deltaTicks).roundToInt().coerceIn(0, totalTicks)
                    val finalVal = LiveInputDockEngine.snapValueToStep(
                        latestMin + tappedTick * latestStep,
                        latestStep,
                        latestMin,
                        latestMax,
                    )
                    isUserControlling = true
                    latestOnValueChange(finalVal)
                    scope.launch {
                        try {
                            posAnim.animateTo(
                                targetValue = tappedTick.toFloat(),
                                animationSpec = tween(durationMillis = 220, easing = CarouselEasing),
                            )
                        } finally {
                            isUserControlling = false
                        }
                    }
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.toFloat(),
                    range = min.toFloat()..max.toFloat(),
                    steps = totalTicks,
                )
            },
    ) {
        val snappedAtPos = LiveInputDockEngine.snapValueToStep(
            min + posAnim.value.roundToInt() * step,
            step,
            min,
            max,
        )
        val isSettledOffGrid = !isUserControlling &&
            !posAnim.isRunning &&
            abs(posAnim.value - targetTick) < 0.02f &&
            abs(value.coerceIn(min, max) - snappedAtPos) > 1e-3

        val currentCenterValue = LiveInputDockEngine.formatDockNumber(
            if (isSettledOffGrid) value.coerceIn(min, max) else snappedAtPos,
            allowDecimal,
            allowColon,
        )

        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.12f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            val c = size.width / 2f
            val cy = size.height / 2f

            // Riel inferior de 1 dp (blanco al 8 %)
            drawRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(0f, size.height - 2f),
                size = Size(size.width, 1.dp.toPx()),
            )

            // Lente central blanca sólida (#F4F4F6) con esquinas redondeadas 13 dp
            val measuredCenter = measurer.measure(currentCenterValue, digitStyle)
            val lensW = (measuredCenter.size.width + 28.dp.toPx()).coerceIn(54.dp.toPx(), 112.dp.toPx())
            val lensH = 40.dp.toPx()
            val lensTopLeft = Offset(c - lensW / 2f, (size.height - lensH) / 2f)
            val lensRadius = CornerRadius(13.dp.toPx())

            drawRoundRect(
                color = DockInk,
                topLeft = lensTopLeft,
                size = Size(lensW, lensH),
                cornerRadius = lensRadius,
            )

            // Cifras visibles a cada lado de la lente
            val currentPos = posAnim.value
            val firstTick = (currentPos - 3.2f).toInt().coerceAtLeast(0)
            val lastTick = (currentPos + 3.2f).toInt().coerceAtMost(totalTicks)

            for (i in firstTick..lastTick) {
                val distance = i - currentPos
                val ad = abs(distance)
                if (ad > 3.2f) continue
                if (isSettledOffGrid && ad < 0.72f) continue

                val tickNum = LiveInputDockEngine.snapValueToStep(min + i * step, step, min, max)
                val text = LiveInputDockEngine.formatDockNumber(tickNum, allowDecimal, allowColon)
                val layout = measurer.measure(text, digitStyle)

                val scaleFactor = when {
                    ad <= 1f -> 1f - 0.32f * ad
                    ad <= 2f -> 0.68f - 0.16f * (ad - 1f)
                    else -> 0.52f - 0.12f * (ad - 2f)
                }

                val alpha = when {
                    ad <= 0.6f -> 1f
                    ad <= 2.2f -> 1f - 0.55f * (ad - 0.6f)
                    else -> (0.12f * (3.2f - ad)).coerceAtLeast(0f)
                }

                val textColor = if (ad < 0.45f) {
                    lerp(DockInkDark, DockInk, ad / 0.45f)
                } else {
                    DockMuted
                }

                val x = c + distance * cellPx
                scale(scaleFactor, scaleFactor, pivot = Offset(x, cy)) {
                    drawText(
                        textLayoutResult = layout,
                        color = textColor,
                        topLeft = Offset(x - layout.size.width / 2f, cy - layout.size.height / 2f),
                        alpha = alpha.coerceIn(0f, 1f),
                    )
                }
            }

            // Si el valor actual viene de teclado libre o cambio de paso fuera de la grilla actual, pintarlo exacto dentro de la lente.
            if (isSettledOffGrid) {
                drawText(
                    textLayoutResult = measuredCenter,
                    color = DockInkDark,
                    topLeft = Offset(c - measuredCenter.size.width / 2f, cy - measuredCenter.size.height / 2f),
                )
            }
        }

        // Nodo semántico y ancla de pruebas en el centro de la lente
        Text(
            text = currentCenterValue,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(0f)
                .testTag("workout-numeric-keyboard-value"),
        )
    }
}

/** Envuelve un BasicTextField de solo lectura interceptando toques antes de que el editor absorba el foco. */
@Composable
internal fun WorkoutNumericTriggerBox(
    value: String,
    enabled: Boolean,
    description: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    contentAlignment: Alignment = Alignment.CenterStart,
    onSetText: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val withTag = if (tag != null) modifier.testTag(tag) else modifier
    Box(
        modifier = withTag
            .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
            .semantics {
                contentDescription = description
                editableText = AnnotatedString(value)
                if (enabled && onSetText != null) {
                    setText { annotated ->
                        onSetText(annotated.text)
                        true
                    }
                }
            },
        contentAlignment = contentAlignment,
    ) {
        content()
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(onOpen) {
                        detectTapGestures(onTap = { onOpen() })
                    },
            )
        }
    }
}
