package com.example.kpkn.screens.sessioneditor.components

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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
) {
    // Local HazeState: glass samples ONLY the cover under the pill.
    // Screen-level hazeState stays reserved for dock/FAB chrome (Blur KPKN.md).
    val heroHazeState = remember { HazeState() }
    val background = session.background
    val glowColor = remember(background?.value) { resolveHeroGlowColor(background) }

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
                .heightIn(min = 148.dp),
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
                    .matchParentSize()
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
                        .matchParentSize()
                        .kpknGlass(heroHazeState, HeroPillShape)
                        .padding(horizontal = 22.dp, vertical = 26.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
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
                            text = { Text("Historial") },
                            onClick = { showSecondaryMenu = false; onOpenHistory() },
                            leadingIcon = { Icon(Icons.Default.History, null) },
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                HeroSolidActionIcon(
                    icon = Icons.Default.Palette,
                    contentDescription = "Editar fondo",
                    onClick = onOpenCoverSheet,
                )
                HeroGlassIconButton(
                    icon = if (autoSaveEnabled) Icons.Default.CloudDone else Icons.Default.Save,
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
                    SessionHeroActionChip("Transferir", Icons.Default.SwapHoriz, onOpenTransfer)
                    SessionHeroActionChip("Historial", Icons.Default.History, onOpenHistory)
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
                        icon = if (autoSaveEnabled) Icons.Default.CloudDone else Icons.Default.Save,
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
                icon = if (autoSaveEnabled) Icons.Default.CloudDone else Icons.Default.Save,
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
                label = "Historial",
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
