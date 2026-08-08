package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.BodyMeasurementEntry
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.screens.sessioneditor.DarkEditorChip
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.screens.sessioneditor.sessionBackgroundPresets
import com.example.kpkn.screens.sessioneditor.sessionGradients
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.components.kpknGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Same corner language as the roadmap dock (28dp) — reads as a real pill, not a card. */
private val HeroPillShape = RoundedCornerShape(28.dp)
private val HeroCompactShape = RoundedCornerShape(999.dp)

@Composable
internal fun SessionHero(
    session: Session,
    hasChanges: Boolean,
    autoSaveEnabled: Boolean,
    latestBodyMeasurement: BodyMeasurementEntry?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onMeetBodyweightChange: (Double?) -> Unit,
    onSyncMeetBodyweight: () -> Unit,
    onSave: () -> Unit,
    onOpenCoverSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    roadmapContent: @Composable () -> Unit = {},
    activeDayOfWeek: Int? = null,
    weekStartDay: Int = 1,
    onSelectDay: ((Int) -> Unit)? = null,
) {
    // Local HazeState: glass samples ONLY the cover under the pill.
    // Screen-level hazeState stays reserved for dock/FAB chrome (Blur KPKN.md).
    val heroHazeState = remember { HazeState() }
    val background = session.background
    val glowColor = remember(background?.value) { resolveHeroGlowColor(background) }
    var roadmapExpanded by remember { mutableStateOf(false) }
    val orderedDaysForSwipe = remember(weekStartDay) {
        val safeStart = weekStartDay.coerceIn(1, 7)
        val base = listOf(1, 2, 3, 4, 5, 6, 7)
        val offset = safeStart - 1
        base.drop(offset) + base.take(offset)
    }

    // ── Swipe-to-change-day animation ─────────────────────────────────────
    val swipeScope = rememberCoroutineScope()
    val heroDragOffset = remember { Animatable(0f) }
    val heroWidthPx = remember { mutableStateOf(0) }
    // Tracks the latest target day so the transition coroutine can wait for the switch.
    val currentDayOfWeek by rememberUpdatedState(activeDayOfWeek)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // CRITICAL: clip the OUTER box to the pill silhouette.
        // Hierarchy: portada first (elevated), actions below.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 148.dp)
                .onSizeChanged { heroWidthPx.value = it.width }
                .graphicsLayer { translationX = heroDragOffset.value }
                .pointerInput(activeDayOfWeek, weekStartDay, onSelectDay) {
                    if (onSelectDay == null) return@pointerInput
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragX += dragAmount
                            // Follow the finger while dragging.
                            swipeScope.launch { heroDragOffset.snapTo(dragX) }
                        },
                        onDragEnd = {
                            val threshold = 80f
                            val width = heroWidthPx.value.toFloat().coerceAtLeast(1f)
                            var targetDay: Int? = null
                            var direction = 0
                            val idx = activeDayOfWeek?.let { orderedDaysForSwipe.indexOf(it) } ?: -1
                            when {
                                dragX < -threshold -> {
                                    // swipe left -> día siguiente
                                    if (idx in 0 until orderedDaysForSwipe.lastIndex) {
                                        targetDay = orderedDaysForSwipe[idx + 1]
                                        direction = -1
                                    }
                                }
                                dragX > threshold -> {
                                    // swipe right -> día anterior
                                    if (idx > 0) {
                                        targetDay = orderedDaysForSwipe[idx - 1]
                                        direction = 1
                                    }
                                }
                            }
                            if (targetDay != null && direction != 0) {
                                swipeScope.launch {
                                    // Slide out in the swipe direction, switch day, then slide in from the opposite side.
                                    heroDragOffset.animateTo(direction * width, tween(180))
                                    onSelectDay(targetDay)
                                    try {
                                        withTimeout(2500) {
                                            while (currentDayOfWeek != targetDay) { delay(16) }
                                        }
                                    } catch (_: Exception) {
                                        // Fallthrough: if the switch didn't happen, just bounce back.
                                    }
                                    heroDragOffset.snapTo(-direction * width)
                                    heroDragOffset.animateTo(0f, tween(220))
                                }
                            } else {
                                // Not enough to switch: spring back to center.
                                swipeScope.launch {
                                    heroDragOffset.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            }
                            dragX = 0f
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .clip(HeroPillShape)
                    .background(glowColor.copy(alpha = 0.16f))
                    .blur(28.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HeroPillShape),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeSource(state = heroHazeState),
                ) {
                    SessionBackgroundLayer(background = background, blurDp = 0.dp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .kpknGlass(heroHazeState, HeroPillShape)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Botón "Ver semana" centrado en la parte superior.
                        Text(
                            text = "Ver semana",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(HeroCompactShape)
                                .clickable { roadmapExpanded = !roadmapExpanded }
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                        )
                        // Roadmap: se despliega agrandando levemente el hero header.
                        AnimatedVisibility(
                            visible = roadmapExpanded,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(glowColor.copy(alpha = 0.10f))
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    roadmapContent()
                                }
                            }
                        }
                        // Título/descripción.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val titleFontSize = when {
                                session.name.length < 15 -> 34.sp
                                session.name.length < 25 -> 28.sp
                                else -> 22.sp
                            }
                            BasicTextField(
                                value = session.name,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = titleFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                ),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (session.name.isBlank()) {
                                            Text(
                                                "Nueva sesión",
                                                color = Color.White.copy(alpha = 0.55f),
                                                fontSize = titleFontSize,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            BasicTextField(
                                value = session.description.orEmpty(),
                                onValueChange = onDescriptionChange,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                maxLines = 1,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontWeight = FontWeight.Medium,
                                ),
                                cursorBrush = SolidColor(Color.White),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (session.description.isNullOrBlank()) {
                                            Text(
                                                "Añadir descripción",
                                                color = Color.White.copy(alpha = 0.45f),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Expanded actions: primary actions left, utility actions right.
        val heroBreakpoint = rememberSessionEditorBreakpoint()
        if (heroBreakpoint == SessionEditorBreakpoint.Compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var showSecondaryMenu by remember { mutableStateOf(false) }
                SessionHeroActionChip("Reglas", Icons.Default.Settings, onOpenRules)
                Box {
                    SessionHeroActionChip("Más", Icons.Default.MoreVert) { showSecondaryMenu = true }
                    KpknDropdownMenu(
                        expanded = showSecondaryMenu,
                        onDismissRequest = { showSecondaryMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Transferir") },
                            onClick = { showSecondaryMenu = false; onOpenTransfer() },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Versiones") },
                            onClick = { showSecondaryMenu = false; onOpenHistory() },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                        )
                    }
                }
                Text(
                    text = sessionSaveStatusLabel(session, hasChanges),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                HeroSolidActionIcon(
                    icon = Icons.Default.Palette,
                    contentDescription = "Editar fondo",
                    onClick = onOpenCoverSheet,
                )
                HeroGlassIconButton(
                    icon = if (autoSaveEnabled) Icons.Default.SaveAlt else Icons.Default.Save,
                    contentDescription = if (autoSaveEnabled) {
                        "Guardado automático activo"
                    } else {
                        "Guardar sesión"
                    },
                    onClick = onSave,
                    showUnsavedDot = hasChanges,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sessionSaveStatusLabel(session, hasChanges),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                    )
                    SessionHeroActionChip("Transferir", Icons.Default.SwapHoriz, onOpenTransfer)
                    SessionHeroActionChip("Versiones", Icons.Default.History, onOpenHistory)
                    SessionHeroActionChip("Reglas", Icons.Default.Settings, onOpenRules)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroSolidActionIcon(
                        icon = Icons.Default.Palette,
                        contentDescription = "Editar fondo",
                        onClick = onOpenCoverSheet,
                    )
                    HeroGlassIconButton(
                        icon = if (autoSaveEnabled) Icons.Default.SaveAlt else Icons.Default.Save,
                        contentDescription = if (autoSaveEnabled) {
                            "Guardado automático activo"
                        } else {
                            "Guardar sesión"
                        },
                        onClick = onSave,
                        showUnsavedDot = hasChanges,
                    )
                }
            }
        }

        if (session.isMeetDay) {
            MeetDayHeroFields(
                session = session,
                latestBodyMeasurement = latestBodyMeasurement,
                onMeetBodyweightChange = onMeetBodyweightChange,
                onSyncMeetBodyweight = onSyncMeetBodyweight,
            )
        }
    }
}

private fun sessionSaveStatusLabel(session: Session, hasChanges: Boolean): String {
    if (hasChanges) return "Cambios locales pendientes"
    val timestamp = session.lastModifiedAtMs
    if (timestamp <= 0L) return "Aún no guardada localmente"
    val ageMinutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000L)
    return when {
        ageMinutes == 0L -> "Guardada localmente · ahora"
        ageMinutes == 1L -> "Guardada localmente · hace 1 min"
        ageMinutes < 60L -> "Guardada localmente · hace $ageMinutes min"
        else -> "Guardada localmente · hace ${ageMinutes / 60L} h"
    }
}

@Composable
internal fun SessionHeroCompactOverlay(
    session: Session,
    hasChanges: Boolean,
    autoSaveEnabled: Boolean,
    hazeState: HazeState?,
    onSave: () -> Unit,
    onOpenCoverSheet: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (hazeState == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                CompactSessionTitlePill(
                    title = session.name.ifBlank { "Nueva sesión" },
                    hazeState = hazeState,
                )
            }
            HeroGlassIconButton(
                icon = Icons.Default.Palette,
                contentDescription = "Editar fondo",
                onClick = onOpenCoverSheet,
            )
            HeroGlassIconButton(
                icon = if (autoSaveEnabled) Icons.Default.SaveAlt else Icons.Default.Save,
                contentDescription = if (autoSaveEnabled) "Guardado automático activo" else "Guardar sesión",
                onClick = onSave,
                showUnsavedDot = hasChanges,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroGlassActionChip(
                label = "Transferir",
                icon = Icons.Default.SwapHoriz,
                hazeState = hazeState,
                onClick = onOpenTransfer,
            )
            HeroGlassActionChip(
                label = "Versiones",
                icon = Icons.Default.History,
                hazeState = hazeState,
                onClick = onOpenHistory,
            )
            HeroGlassActionChip(
                label = "Reglas",
                icon = Icons.Default.Settings,
                hazeState = hazeState,
                onClick = onOpenRules,
            )
        }
    }
}

@Composable
private fun CompactSessionTitlePill(
    title: String,
    hazeState: HazeState,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 112.dp)
            .widthIn(max = 260.dp)
            .kpknGlass(hazeState, HeroCompactShape)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}

@Composable
private fun MeetDayHeroFields(
    session: Session,
    latestBodyMeasurement: BodyMeasurementEntry?,
    onMeetBodyweightChange: (Double?) -> Unit,
    onSyncMeetBodyweight: () -> Unit,
) {
    OutlinedTextField(
        value = session.meetBodyweight?.let(::formatEditableNumber).orEmpty(),
        onValueChange = { onMeetBodyweightChange(it.safeDoubleOrNull()) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Peso corporal objetivo (kg)", color = Color.White.copy(alpha = 0.72f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Black.copy(alpha = 0.22f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.22f),
            focusedBorderColor = Color.White.copy(alpha = 0.38f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = Color.White.copy(alpha = 0.82f),
            unfocusedLabelColor = Color.White.copy(alpha = 0.62f),
            cursorColor = Color.White,
        ),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val measurementText = latestBodyMeasurement?.weight?.let { weight ->
            "Medición reciente: ${formatEditorOneDecimal(weight)} kg (${latestBodyMeasurement.date})"
        } ?: "Sin medición corporal reciente"
        Text(
            text = measurementText,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
        )
        OutlinedButton(
            onClick = onSyncMeetBodyweight,
            enabled = latestBodyMeasurement?.weight != null,
        ) {
            Text("Usar medición")
        }
    }
}

@Composable
private fun HeroGlassActionChip(
    label: String,
    icon: ImageVector,
    hazeState: HazeState,
    onClick: () -> Unit,
) {
    // Same construction as HeroGlassFab: Box + kpknGlass + clickable. No Material Surface.
    Box(
        modifier = Modifier
            .kpknGlass(hazeState, HeroCompactShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HeroSolidActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DarkEditorChip,
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun resolveHeroGlowColor(background: SessionBackground?): Color {
    val preset = sessionBackgroundPresets.firstOrNull { it.id == background?.value } ?: sessionGradients.first()
    val a = preset.colors.firstOrNull() ?: Color.White
    val b = preset.colors.getOrNull(preset.colors.lastIndex / 2) ?: a
    val c = preset.colors.lastOrNull() ?: b
    return Color(
        red = (a.red + b.red + c.red) / 3f,
        green = (a.green + b.green + c.green) / 3f,
        blue = (a.blue + b.blue + c.blue) / 3f,
        alpha = 1f,
    )
}
