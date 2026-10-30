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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.ui.text.AnnotatedString
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.domain.exercises.exerciseDisplayParts
import com.example.kpkn.screens.onboarding.design.entreno.plan.exercisePatternOf
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.EdExerciseRow
import com.example.kpkn.screens.sessioneditor.components.Thumbnail
import com.example.kpkn.screens.sessioneditor.components.edDashedBorder
import com.example.kpkn.screens.sessioneditor.components.edRowTitle
import com.example.kpkn.screens.sessioneditor.frame.EdMotion
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.kpknRing
import com.example.kpkn.ui.design.depth.kpknShadow
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import kotlin.math.roundToInt

/** Solid or gradient accent stored in [SessionPart.color]. */
internal data class PartAccentPreset(
    val id: String,
    val colors: List<Color>,
) {
    /** Brightest stop — used for chips/borders/text so cover darks stay readable. */
    val primary: Color
        get() = colors.maxByOrNull { it.luminance() } ?: Color(0xFF6E8A95)

    val isGradient: Boolean get() = colors.distinct().size > 1

    fun brush(alpha: Float = 1f): Brush {
        val tinted = colors.map { it.copy(alpha = (it.alpha * alpha).coerceIn(0f, 1f)) }
        return if (isGradient) Brush.linearGradient(tinted) else SolidColor(tinted.first())
    }
}

internal val partAccentSolids = listOf(
    // IDs remain stable for persisted sessions; the rendered palette is now
    // graphite/muted so accents guide hierarchy without a neon glow.
    PartAccentPreset("#00F0FF", listOf(Color(0xFF5D8390))),
    PartAccentPreset("#3B82F6", listOf(Color(0xFF5877A0))),
    PartAccentPreset("#00F19F", listOf(Color(0xFF5A9A84))),
    PartAccentPreset("#A855F7", listOf(Color(0xFF7D6C9B))),
    PartAccentPreset("#EAB308", listOf(Color(0xFFA68F54))),
    PartAccentPreset("#F43F5E", listOf(Color(0xFFA6626E))),
    PartAccentPreset("#06B6D4", listOf(Color(0xFF557F8C))),
    PartAccentPreset("#8B5CF6", listOf(Color(0xFF756B9A))),
    PartAccentPreset("#F97316", listOf(Color(0xFFA4775A))),
    PartAccentPreset("#EC4899", listOf(Color(0xFFA26786))),
    PartAccentPreset("#22C55E", listOf(Color(0xFF5C8E6D))),
    PartAccentPreset("#14B8A6", listOf(Color(0xFF568C84))),
    PartAccentPreset("#F59E0B", listOf(Color(0xFFA48656))),
    PartAccentPreset("#EF4444", listOf(Color(0xFFA56666))),
    PartAccentPreset("#6366F1", listOf(Color(0xFF6D729D))),
    PartAccentPreset("#84CC16", listOf(Color(0xFF78915A))),
)

internal val partAccentGradients = listOf(
    PartAccentPreset("gradient://cyber", listOf(Color(0xFF5D8390), Color(0xFF7D6C9B))),
    PartAccentPreset("gradient://ocean", listOf(Color(0xFF557F8C), Color(0xFF5877A0))),
    PartAccentPreset("gradient://sunset", listOf(Color(0xFFA4775A), Color(0xFFA6626E))),
    PartAccentPreset("gradient://aurora", listOf(Color(0xFF5A9A84), Color(0xFF557F8C), Color(0xFF756B9A))),
    PartAccentPreset("gradient://ember", listOf(Color(0xFFA68F54), Color(0xFFA4775A), Color(0xFFA56666))),
    PartAccentPreset("gradient://violet", listOf(Color(0xFF7D6C9B), Color(0xFFA26786))),
    PartAccentPreset("gradient://mint", listOf(Color(0xFF5C8E6D), Color(0xFF568C84))),
    PartAccentPreset("gradient://indigo-rose", listOf(Color(0xFF6D729D), Color(0xFFA26786))),
    PartAccentPreset("gradient://lime-cyan", listOf(Color(0xFF78915A), Color(0xFF5D8390))),
    PartAccentPreset("gradient://gold-violet", listOf(Color(0xFFA48656), Color(0xFF756B9A))),
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

/** Soft tinted panel for set cards — visible enough to retain the selected cover. */
internal fun Color.toSetCardBackground(): Color {
    val base = Color(0xFF1A1A20)
    val mix = 0.14f
    return Color(
        red = (base.red + (red - base.red) * mix).coerceIn(0f, 1f),
        green = (base.green + (green - base.green) * mix).coerceIn(0f, 1f),
        blue = (base.blue + (blue - base.blue) * mix).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

/** Gentle cover/group wash for the exercise card container. */
internal fun PartAccentPreset.exerciseCardBrush(alpha: Float = 0.10f): Brush = brush(alpha = alpha)

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

/**
 * La fila levantada al ordenar (`.ed-ex.lifted`, Pulido v1 · EDITOR · A2): la propia fila (asa en tinta, miniatura, nombre, dosis, estrella y
 * flecha) sobre una capa gris #1B1B1D de radio 20, con la sombra `levantado` (`0 24 48 #000d` y un anillo de 1 dp), que crece a 1,06 y se
 * asienta en 1,025 (0,34 s, resorte) y lanza una vez un anillo de tinta que se expande 14 dp y se apaga (0,46 s). Sigue al dedo por
 * [offsetProvider]. Un bloque de superserie se levanta como una sola ficha con [titleOverride] y [subtitleOverride].
 */
@Composable
internal fun DragLiftPreview(
    exercise: Exercise,
    rect: Rect,
    offsetProvider: () -> Offset,
    rootBounds: Rect?,
    modifier: Modifier = Modifier,
    subtitleOverride: String? = null,
    titleOverride: String? = null,
    exerciseInfo: ExerciseMuscleInfo? = null,
    /** Pasos congelados de la animación (hojas de render y pruebas): escala de la ficha y avance del anillo (0..1). Nulo = corre sola. */
    frozenScale: Float? = null,
    frozenRing: Float? = null,
    /** La fila ya se ha soltado y se asienta en el hueco: sin escala, sin sombra y sin anillo (`.ed-ex.settle`, ed-S04). */
    settled: Boolean = false,
) {
    val root = rootBounds ?: return
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    val baseX = rect.left - root.left
    val baseY = rect.top - root.top
    val scale = remember { Animatable(frozenScale ?: 1f) }
    val ring = remember { Animatable(frozenRing ?: 0f) }
    LaunchedEffect(Unit) {
        if (frozenScale != null || frozenRing != null || reduced) {
            scale.snapTo(frozenScale ?: 1.025f)
            ring.snapTo(frozenRing ?: 1f)
            return@LaunchedEffect
        }
        // pulido:animacion ed-A07 .ed-list.drag .ed-ex.lifted ed-lift 0,34 s --ed-spring (escala 1 → 1,06 al 35 % → 1,025)
        launch { ring.animateTo(1f, tween(460, easing = EdMotion.EaseOut)) }
        scale.animateTo(1.06f, tween(119, easing = EdMotion.Spring))
        scale.animateTo(1.025f, tween(221, easing = EdMotion.Spring))
    }
    val shape = RoundedCornerShape(20.dp)
    val displayParts = remember(exercise, exerciseInfo) { exerciseDisplayParts(exercise, exerciseInfo) }
    val thumbnailRes = remember(exercise.id, exercise.exerciseDbId, exercise.exerciseId) {
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = null,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.exerciseId ?: exercise.id,
        )
    }
    val pattern = remember(exercise.name) { exercisePatternOf(exercise.name) }
    val dose = remember(exercise) { sessionExerciseListDose(exercise, includeRest = true) }
    val title = remember(displayParts, titleOverride) {
        if (titleOverride != null) {
            AnnotatedString(titleOverride)
        } else {
            edRowTitle(displayParts.parentName, displayParts.chips, exercise.isEffectivelyUnilateral())
        }
    }
    // El anillo de tinta (`.ed-pulse`, ed-A09): 0 → 14 dp de extensión y 0,47 → 0 de opacidad.
    // pulido:animacion ed-A09 .ed-pulse ed-ring 0,46 s ease-out forwards (0 0 0 0 #f2eee677 → 0 0 0 14px #f2eee600)
    val ringSpec = KpknShadowSpec.of(spread = (14f * ring.value).dp, color = Color(0xFFF2EEE6).copy(alpha = 0.467f * (1f - ring.value)))
    Box(
        modifier = modifier
            .offset {
                val currentOffset = offsetProvider()
                IntOffset((baseX + currentOffset.x + with(density) { 16.dp.toPx() }).roundToInt(), (baseY + currentOffset.y).roundToInt())
            }
            .width(with(density) { (rect.width - 32.dp.toPx()).toDp() })
            .zIndex(100f)
            .graphicsLayer {
                val s = if (settled) 1f else scale.value
                scaleX = s
                scaleY = s
            }
            // pulido:sombra ed-S03 .ed-list.drag .ed-ex.lifted 0 24px 48px #000d + anillo de 1 px (preset levantado) · ed-S04 .settle sin sombra (preset ninguna)
            .then(if (settled) Modifier else Modifier.kpknShadow(KpknShadow.Levantado, shape).kpknRing(ringSpec, shape))
            .background(Color(0xFF1B1B1D), shape),
    ) {
        EdExerciseRow(
            title = title,
            dose = subtitleOverride ?: dose,
            thumbnail = { Thumbnail(cardio = exercise.cardioDetails?.type, imageRes = thumbnailRes, pattern = pattern) },
            expanded = false,
            onToggle = {},
            onLongPress = {},
            gripLifted = true,
            starActive = if (exercise.cardioDetails == null && titleOverride == null) (exercise.isStarTarget || exercise.goal1RM != null) else null,
        )
    }
}

/**
 * El hueco donde caerá la fila (`.ed-slot`): recuadro de radio 18 con filete punteado de 1,5 dp (#f2eee688) y un velo de tinta al 6 %, a 8 dp
 * de los lados del módulo. Se desplaza al nuevo destino en 260 ms con `--ed-ease` (ed-T25).
 */
@Composable
internal fun DragSlotHole(
    slot: Rect?,
    rootBounds: Rect?,
    modifier: Modifier = Modifier,
) {
    val root = rootBounds ?: return
    val target = slot ?: return
    val density = LocalDensity.current
    val reduced = wizardReducedMotion()
    // pulido:transicion ed-T25 .ed-slot transform 0,26 s --ed-ease
    val y by animateFloatAsState(
        targetValue = target.top - root.top,
        animationSpec = if (reduced) snap() else tween(260, easing = EdMotion.Standard),
        label = "edSlotY",
    )
    val inset = with(density) { 25.dp.toPx() }
    Box(
        modifier = modifier
            .offset { IntOffset((target.left - root.left + inset).roundToInt(), y.roundToInt()) }
            .size(
                width = with(density) { (target.width - 2 * inset).toDp() },
                height = with(density) { target.height.toDp() },
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x0FF2EEE6))
            .edDashedBorder(Color(0x88F2EEE6), radius = 18.dp, width = 1.5.dp, dash = 4.5.dp, gap = 3.dp),
    )
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
        shape = RoundedCornerShape(24.dp),
        color = KpknSurface.module,
        shadowElevation = 28.dp,
        tonalElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(KpknLineIcons.Grip, contentDescription = null, tint = WizardColors.textMuted)
            Text(
                partName.ifBlank { "Grupo" }.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** DayView-style drop cue: thin primary bar between items (overlay — no layout height). */
@Composable
internal fun SessionEditorDropIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(90)),
            exit = fadeOut(tween(80)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true, align = Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(WizardColors.text),
            )
        }
    }
}

/** Inline chip while the pointer is outside a valid strength/cardio drop zone (N8). */
@Composable
internal fun SessionEditorInvalidDropBanner(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(90)) + expandVertically(),
        exit = fadeOut(tween(80)) + shrinkVertically(),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = WizardColors.danger.copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("session_editor_invalid_drop_banner"),
        ) {
            Text(
                text = "Zona no válida",
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = WizardColors.ctaContent,
            )
        }
    }
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
