package com.example.kpkn.screens.programdetail.components

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.example.kpkn.data.models.AthleteProfileScore
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.VolumeRecommendation

private data class CoverGradient(
    val id: String,
    val name: String,
    val colors: List<Color>,
)

private data class FocusOption(
    val mode: ProgramMode,
    val label: String,
)

private val heroCoverGradients = listOf(
    CoverGradient("gradient://ember", "Ember", listOf(Color(0xFF20110F), Color(0xFF8D3D2E), Color(0xFFE08E45))),
    CoverGradient("gradient://lagoon", "Lagoon", listOf(Color(0xFF0D1B2A), Color(0xFF1B4965), Color(0xFF5FA8D3))),
    CoverGradient("gradient://velvet", "Velvet", listOf(Color(0xFF1C1024), Color(0xFF5B2A86), Color(0xFFE26D5A))),
    CoverGradient("gradient://forest", "Forest", listOf(Color(0xFF102A1F), Color(0xFF2D6A4F), Color(0xFF95D5B2))),
)

private val focusOptions = listOf(
    FocusOption(ProgramMode.POWERLIFTING, "Powerlifting"),
    FocusOption(ProgramMode.POWERBUILDING, "Powerbuilding"),
    FocusOption(ProgramMode.HYPERTROPHY, "Hipertrofia"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactHeroBanner(
    programName: String,
    programDescription: String?,
    coverValue: String?,
    isActive: Boolean,
    isPaused: Boolean,
    focusMode: String,
    muscularBattery: Int,
    sncBattery: Int,
    spinalBattery: Int,
    isVolumeCalibrated: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartPause: () -> Unit,
    onFocusChange: (String) -> Unit,
    onCoverChange: (String) -> Unit,
    onApplyVolumeCalibration: (ProgramMode, AthleteProfileScore, List<VolumeRecommendation>) -> Unit,
    onIncreaseVolumeCurrentWeek: () -> Unit,
    onReduceVolumeCurrentWeek: () -> Unit,
    openVolumeSheetToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    val coverGradient = remember(coverValue) { resolveGradient(coverValue) }
    val usesGradient = remember(coverValue) { isGradientCover(coverValue) }
    val darkIcons = remember(coverGradient, usesGradient) {
        usesGradient && coverGradient.colors.firstOrNull()?.luminance()?.let { it > 0.72f } == true
    }
    val labelColor = if (darkIcons) Color(0xFF1C1B1F).copy(alpha = 0.74f) else Color.White.copy(alpha = 0.72f)
    val primaryTextColor = if (darkIcons) Color(0xFF141218) else Color.White
    val glassColor = if (darkIcons) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f)
    val strokeColor = if (darkIcons) Color(0xFF141218).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.18f)
    val actionContainer = if (darkIcons) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.18f)
    val actionContent = if (darkIcons) Color(0xFF141218) else Color.White
    val statusText = when {
        isActive && !isPaused -> "Activo"
        isPaused -> "Pausado"
        else -> "Borrador"
    }
    val statusAccent = when {
        isActive && !isPaused -> Color(0xFF34D399)
        isPaused -> Color(0xFFFBBF24)
        else -> Color(0xFFCBD5E1)
    }
    var showFocusMenu by remember { mutableStateOf(false) }
    var showCoverSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var pendingFocusMode by remember { mutableStateOf<ProgramMode?>(null) }
    var showFocusRecalibrationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openVolumeSheetToken) {
        if (openVolumeSheetToken > 0) {
            showVolumeSheet = true
        }
    }

    HeroSystemBars(darkIcons = darkIcons)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Color.Transparent),
    ) {
        HeroBackground(
            coverValue = coverValue,
            coverGradient = coverGradient,
            usesGradient = usesGradient,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(actionContainer)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = actionContent,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatusPill(
                        label = statusText,
                        accent = statusAccent,
                        contentColor = primaryTextColor,
                        containerColor = glassColor,
                        borderColor = strokeColor,
                    )

                    HeroIconAction(
                        onClick = { showCoverSheet = true },
                        containerColor = actionContainer,
                        contentColor = actionContent,
                        icon = Icons.Default.Palette,
                        contentDescription = "Cambiar color",
                    )

                    HeroIconAction(
                        onClick = onEdit,
                        containerColor = actionContainer,
                        contentColor = actionContent,
                        icon = Icons.Default.Edit,
                        contentDescription = "Editar programa",
                    )

                    FilledIconButton(
                        onClick = onStartPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (darkIcons) MaterialTheme.colorScheme.primary else Color.White,
                            contentColor = if (darkIcons) MaterialTheme.colorScheme.onPrimary else Color(0xFF141218),
                        ),
                    ) {
                        Icon(
                            imageVector = if (isActive && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isActive && !isPaused) "Pausar programa" else "Activar programa",
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = programName,
                color = primaryTextColor,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (!programDescription.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = programDescription,
                    color = labelColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            Box {
                AssistChip(
                    onClick = { showFocusMenu = true },
                    label = {
                        Text(
                            text = focusOptions.find { it.mode.name.equals(focusMode, ignoreCase = true) }?.label
                                ?: focusMode.replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = {
                        Text(
                            text = "Enfoque",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = labelColor,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = glassColor,
                        labelColor = primaryTextColor,
                        leadingIconContentColor = primaryTextColor,
                        trailingIconContentColor = primaryTextColor,
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        borderColor = strokeColor,
                        enabled = true,
                    ),
                )

                DropdownMenu(
                    expanded = showFocusMenu,
                    onDismissRequest = { showFocusMenu = false },
                ) {
                    focusOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                if (!option.mode.name.equals(focusMode, ignoreCase = true)) {
                                    pendingFocusMode = option.mode
                                    showFocusRecalibrationDialog = true
                                }
                                showFocusMenu = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            HeroWidgetsSection(
                muscularBattery = muscularBattery,
                sncBattery = sncBattery,
                spinalBattery = spinalBattery,
                isVolumeCalibrated = isVolumeCalibrated,
                onOpenVolumeSetup = { showVolumeSheet = true },
                onIncreaseVolumeCurrentWeek = onIncreaseVolumeCurrentWeek,
                onReduceVolumeCurrentWeek = onReduceVolumeCurrentWeek,
            )
        }
    }

    if (showCoverSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCoverSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Colores de portada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Elige una base para el header del programa. Si usas una foto, esta acción volverá a una portada con gradiente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                heroCoverGradients.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCoverChange(option.id)
                                showCoverSheet = false
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 92.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(option.colors)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.name, fontWeight = FontWeight.Bold)
                                Text(
                                    if (coverValue == option.id) "Actual" else "Aplicar a la portada",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (coverValue == option.id) {
                                Text(
                                    text = "Seleccionado",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showVolumeSheet) {
        VolumeCalibrationSheet(
            currentMode = pendingFocusMode ?: runCatching { ProgramMode.valueOf(focusMode.uppercase()) }.getOrDefault(ProgramMode.HYPERTROPHY),
            onDismiss = {
                showVolumeSheet = false
                pendingFocusMode = null
            },
            onSave = { result ->
                onApplyVolumeCalibration(result.mode, result.score, result.recommendations)
                showVolumeSheet = false
                pendingFocusMode = null
            },
        )
    }

    if (showFocusRecalibrationDialog && pendingFocusMode != null) {
        AlertDialog(
            onDismissRequest = {
                showFocusRecalibrationDialog = false
                pendingFocusMode = null
            },
            title = { Text("Cambiar enfoque", fontWeight = FontWeight.Black) },
            text = {
                Text("¿Deseas recalibrar el volumen de entrenamiento después de cambiar el enfoque del programa?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newMode = pendingFocusMode ?: return@Button
                        onFocusChange(newMode.name.lowercase())
                        showFocusRecalibrationDialog = false
                        showVolumeSheet = true
                    },
                ) {
                    Text("Sí, recalibrar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val newMode = pendingFocusMode ?: return@TextButton
                            onFocusChange(newMode.name.lowercase())
                            showFocusRecalibrationDialog = false
                            pendingFocusMode = null
                        },
                    ) {
                        Text("Solo cambiar")
                    }
                    TextButton(
                        onClick = {
                            showFocusRecalibrationDialog = false
                            pendingFocusMode = null
                        },
                    ) {
                        Text("Cancelar")
                    }
                }
            },
        )
    }
}

@Composable
private fun HeroBackground(
    coverValue: String?,
    coverGradient: CoverGradient,
    usesGradient: Boolean,
) {
    Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        if (usesGradient) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = coverGradient.colors,
                            start = Offset.Zero,
                            end = Offset(960f, 640f),
                        ),
                    ),
            )
        } else {
            AsyncImage(
                model = coverValue,
                contentDescription = "Portada del programa",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (usesGradient) 0.12f else 0.22f),
                            Color.Black.copy(alpha = if (usesGradient) 0.24f else 0.38f),
                            Color.Black.copy(alpha = if (usesGradient) 0.32f else 0.52f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    accent: Color,
    contentColor: Color,
    containerColor: Color,
    borderColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(999.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = label,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HeroIconAction(
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun HeroSystemBars(darkIcons: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, darkIcons, context) {
        val activity = context.findActivity()
        val window = activity?.window
        val previousColor = window?.statusBarColor
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars

        if (window != null && controller != null) {
            window.statusBarColor = AndroidColor.TRANSPARENT
            controller.isAppearanceLightStatusBars = darkIcons
        }

        onDispose {
            if (window != null && controller != null && previousColor != null && previousLightStatus != null) {
                window.statusBarColor = previousColor
                controller.isAppearanceLightStatusBars = previousLightStatus
            }
        }
    }
}

private fun resolveGradient(coverValue: String?): CoverGradient {
    return heroCoverGradients.firstOrNull { it.id == coverValue } ?: heroCoverGradients.first()
}

private fun isGradientCover(coverValue: String?): Boolean {
    return coverValue.isNullOrBlank() || coverValue.startsWith("gradient://")
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
