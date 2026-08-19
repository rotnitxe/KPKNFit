package com.example.kpkn.screens.sessioneditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.ui.components.KpknSheetTokens
import kotlin.math.roundToInt

/** Solid or gradient accent stored in [SessionPart.color]. */
internal data class PartAccentPreset(
    val id: String,
    val colors: List<Color>,
) {
    /** Brightest stop — used for chips/borders/text so cover darks stay readable. */
    val primary: Color
        get() = colors.maxByOrNull { it.luminance() } ?: Color(0xFF00F0FF)

    val isGradient: Boolean get() = colors.distinct().size > 1

    fun brush(alpha: Float = 1f): Brush {
        val tinted = colors.map { it.copy(alpha = (it.alpha * alpha).coerceIn(0f, 1f)) }
        return if (isGradient) Brush.linearGradient(tinted) else SolidColor(tinted.first())
    }
}

internal val partAccentSolids = listOf(
    PartAccentPreset("#00F0FF", listOf(Color(0xFF00F0FF))),
    PartAccentPreset("#3B82F6", listOf(Color(0xFF3B82F6))),
    PartAccentPreset("#00F19F", listOf(Color(0xFF00F19F))),
    PartAccentPreset("#A855F7", listOf(Color(0xFFA855F7))),
    PartAccentPreset("#EAB308", listOf(Color(0xFFEAB308))),
    PartAccentPreset("#F43F5E", listOf(Color(0xFFF43F5E))),
    PartAccentPreset("#06B6D4", listOf(Color(0xFF06B6D4))),
    PartAccentPreset("#8B5CF6", listOf(Color(0xFF8B5CF6))),
    PartAccentPreset("#F97316", listOf(Color(0xFFF97316))),
    PartAccentPreset("#EC4899", listOf(Color(0xFFEC4899))),
    PartAccentPreset("#22C55E", listOf(Color(0xFF22C55E))),
    PartAccentPreset("#14B8A6", listOf(Color(0xFF14B8A6))),
    PartAccentPreset("#F59E0B", listOf(Color(0xFFF59E0B))),
    PartAccentPreset("#EF4444", listOf(Color(0xFFEF4444))),
    PartAccentPreset("#6366F1", listOf(Color(0xFF6366F1))),
    PartAccentPreset("#84CC16", listOf(Color(0xFF84CC16))),
)

internal val partAccentGradients = listOf(
    PartAccentPreset("gradient://cyber", listOf(Color(0xFF00F0FF), Color(0xFFA855F7))),
    PartAccentPreset("gradient://ocean", listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
    PartAccentPreset("gradient://sunset", listOf(Color(0xFFF97316), Color(0xFFF43F5E))),
    PartAccentPreset("gradient://aurora", listOf(Color(0xFF00F19F), Color(0xFF06B6D4), Color(0xFF8B5CF6))),
    PartAccentPreset("gradient://ember", listOf(Color(0xFFEAB308), Color(0xFFF97316), Color(0xFFEF4444))),
    PartAccentPreset("gradient://violet", listOf(Color(0xFFA855F7), Color(0xFFEC4899))),
    PartAccentPreset("gradient://mint", listOf(Color(0xFF22C55E), Color(0xFF14B8A6))),
    PartAccentPreset("gradient://indigo-rose", listOf(Color(0xFF6366F1), Color(0xFFEC4899))),
    PartAccentPreset("gradient://lime-cyan", listOf(Color(0xFF84CC16), Color(0xFF00F0FF))),
    PartAccentPreset("gradient://gold-violet", listOf(Color(0xFFF59E0B), Color(0xFF8B5CF6))),
)

internal val PART_ACCENTS: List<PartAccentPreset> = partAccentSolids + partAccentGradients

/** IDs used when assigning a default color to a new group (cycles solids + gradients). */
internal val PART_COLORS: List<String> = PART_ACCENTS.map { it.id }

internal fun resolvePartAccent(id: String?): PartAccentPreset {
    if (id.isNullOrBlank()) return partAccentSolids.first()
    PART_ACCENTS.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { return it }
    // Cover / session background presets (used by loose exercises).
    sessionBackgroundPresets.firstOrNull { it.id == id }?.let { preset ->
        return PartAccentPreset(preset.id, preset.colors)
    }
    // Legacy / custom hex stored directly in SessionPart.color
    val parsed = runCatching {
        Color(android.graphics.Color.parseColor(id))
    }.getOrNull()
    return if (parsed != null) PartAccentPreset(id, listOf(parsed)) else partAccentSolids.first()
}

/** Soft tinted panel for set cards — subtle accent, always dark enough for white text. */
internal fun Color.toSetCardBackground(): Color {
    val base = Color(0xFF1A1A20)
    val mix = 0.16f
    return Color(
        red = (base.red + (red - base.red) * mix).coerceIn(0f, 1f),
        green = (base.green + (green - base.green) * mix).coerceIn(0f, 1f),
        blue = (base.blue + (blue - base.blue) * mix).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/** Gentle cover/group wash for the exercise card container. */
internal fun PartAccentPreset.exerciseCardBrush(): Brush = brush(alpha = 0.08f)

internal fun resolveCoverAccentId(background: SessionBackground?): String =
    background?.value?.takeIf { it.isNotBlank() }
        ?: sessionBackgroundPresets.firstOrNull()?.id
        ?: partAccentSolids.first().id

/**
 * Single accent source for exercise/set cards — whether the exercise lives in a part
 * or loose under the session. Callers must not invent a second palette path.
 */
internal fun resolveExerciseAccentHex(
    session: Session,
    partColor: String?,
): String = partColor?.takeIf { it.isNotBlank() } ?: resolveCoverAccentId(session.background)

@Composable
internal fun DragLiftPreview(
    exercise: Exercise,
    rect: Rect,
    offsetProvider: () -> Offset,
    rootBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    val root = rootBounds ?: return
    val density = LocalDensity.current
    val baseX = rect.left - root.left
    val baseY = rect.top - root.top
    var lifted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { lifted = true }
    val liftScale by animateFloatAsState(
        targetValue = if (lifted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "drag-lift-scale",
    )
    Surface(
        modifier = modifier
            .offset {
                val currentOffset = offsetProvider()
                IntOffset((baseX + currentOffset.x).roundToInt(), (baseY + currentOffset.y).roundToInt())
            }
            .width(with(density) { rect.width.toDp() })
            .heightIn(min = 70.dp)
            .zIndex(100f)
            .graphicsLayer {
                scaleX = liftScale
                scaleY = liftScale
                alpha = 0.96f
                shadowElevation = 18.dp.toPx()
            },
        shape = RoundedCornerShape(20.dp),
        color = DarkEditorSurface.copy(alpha = 0.98f),
        shadowElevation = 28.dp,
        tonalElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name.ifBlank { "Ejercicio" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${exercise.sets.size} series · ${trainingModeLabel(exercise.trainingMode)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DragPartLiftPreview(
    partName: String,
    rect: Rect,
    offsetYProvider: () -> Float,
    rootBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    val root = rootBounds ?: return
    val density = LocalDensity.current
    val baseX = rect.left - root.left
    val baseY = rect.top - root.top
    var lifted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { lifted = true }
    val liftScale by animateFloatAsState(
        targetValue = if (lifted) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "drag-part-lift-scale",
    )
    Surface(
        modifier = modifier
            .offset { IntOffset(baseX.roundToInt(), (baseY + offsetYProvider()).roundToInt()) }
            .width(with(density) { rect.width.toDp() })
            .heightIn(min = 56.dp)
            .zIndex(100f)
            .graphicsLayer {
                scaleX = liftScale
                scaleY = liftScale
                alpha = 0.96f
                shadowElevation = 18.dp.toPx()
            },
        shape = RoundedCornerShape(16.dp),
        color = DarkEditorSurface.copy(alpha = 0.98f),
        shadowElevation = 28.dp,
        tonalElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                partName.ifBlank { "Grupo" }.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** DayView-style drop cue: thin primary bar between items. */
@Composable
internal fun SessionEditorDropIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(90)) + expandVertically(animationSpec = tween(140)),
        exit = fadeOut(tween(80)) + shrinkVertically(animationSpec = tween(120)),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Deprecated("Use SessionEditorDropIndicator", ReplaceWith("SessionEditorDropIndicator(visible)"))
@Composable
internal fun DropGapProjection(
    visible: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    SessionEditorDropIndicator(visible = visible, modifier = modifier)
}

internal val DarkEditorSurface = Color(0xE61B1B20)

internal val DarkEditorSurfaceSoft = Color(0xB8232329)

internal val DarkEditorChip = Color(0xFF2A2A31)

internal val DarkEditorChipSelected = Color(0xFF333A42)

@Composable
internal fun SheetHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
internal fun DarkChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    // Same dark chip family as Descanso / Modo (no white wells in exercise config).
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DarkEditorChipSelected else DarkEditorChip,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
