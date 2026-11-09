package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.EdColors
import com.example.kpkn.screens.sessioneditor.components.EdMenuRow
import com.example.kpkn.screens.sessioneditor.components.edText
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknRing
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.kpknAnimate
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/** Alto de la isla inferior del editor (`.ed-bar`: 64 dp). */
internal val SessionEditorFrameBottomBarHeight = 64.dp

/** Lo que el menú «Añadir» puede hacer; cada acción es opcional (`null` = esa entrada no se ofrece). */
internal data class FrameAddActions(
    val onExercise: () -> Unit,
    val onCardio: (() -> Unit)? = null,
    val onGroup: (() -> Unit)? = null,
    val onCardioSpace: (() -> Unit)? = null,
    val onStrengthSpace: (() -> Unit)? = null,
)

/**
 * Barra inferior del editor (Pulido v1 · EDITOR · A2, escenas `ed-lista`, `ed-anadir` y `ed-barra`): una isla de vidrio de 64 dp (`.ed-bar`)
 * con tres piezas. El TIEMPO (anillo de 32 dp con el avance sobre el límite y «81 min · de 90» al lado; tocarlo abre Asistente › Reglas ›
 * Tiempo); «AÑADIR» partido (el cuerpo abre el catálogo y la flecha ⌃ despliega Ejercicio · Cardio · Grupo nuevo); y el ASISTENTE, un círculo
 * con su icono y el número de sugerencias. «Reglas» ya no está aquí: es la 4.ª pestaña del Asistente. No se esconde al hacer scroll; con
 * [dimmed] (se arrastra una fila) baja al 38 % y no recibe toques. La letra se limita al 115 % (`--f: min(var(--fs), 1.15)`).
 *
 * @param estimatedMinutes minutos de la sesión; nulo si no hay ejercicios (el anillo queda neutro y marca 0).
 * @param limitMinutes el límite de la sesión; nulo si no hay (el anillo queda neutro).
 * @param changeKey cambia con la sesión abierta: reinicia la referencia con la que se calcula la burbuja «+9 min».
 * @param addActions lo que hace el menú de la flecha; sin él, la flecha no despliega nada.
 */
@Composable
internal fun SessionEditorFrameBottomBar(
    hazeState: HazeState?,
    suggestionCount: Int,
    estimatedMinutes: Int?,
    limitMinutes: Int?,
    onAdd: () -> Unit,
    onAssistant: () -> Unit,
    onTime: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    addActions: FrameAddActions? = null,
    hasStrengthSpace: Boolean = true,
    hasCardioSpace: Boolean = true,
    changeKey: Any? = null,
    initialMenuOpen: Boolean = false,
) {
    val tone = if (estimatedMinutes == null) FrameRingTone.NEUTRAL else frameTimeRingTone(estimatedMinutes, limitMinutes)
    val fraction = frameTimeRingFraction(estimatedMinutes ?: 0, limitMinutes)
    val density = LocalDensity.current
    var menuOpen by remember { mutableStateOf(initialMenuOpen) }
    var barBounds by remember { mutableStateOf<Rect?>(null) }
    var addBounds by remember { mutableStateOf<Rect?>(null) }
    // pulido:transicion ed-T06 .ed-bar opacity 0,22 s ease (38 % al arrastrar)
    val dimAlpha by animateFloatAsState(if (dimmed) 0.38f else 1f, tween(220, easing = EdMotion.Ease), label = "edBarDim")

    // Los minutos que cambian: burbuja «+9 min» (1,3 s) y rebote de la cifra (0,36 s). Un cambio de sesión reinicia la referencia.
    var baseline by remember(changeKey) { mutableStateOf(estimatedMinutes) }
    var deltaToShow by remember { mutableStateOf<Int?>(null) }
    var bumpKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(estimatedMinutes, changeKey) {
        val previous = baseline
        baseline = estimatedMinutes
        if (previous != null && estimatedMinutes != null && previous != estimatedMinutes) {
            bumpKey++
            deltaToShow = estimatedMinutes - previous
            delay(1_400)
            deltaToShow = null
        }
    }

    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = minOf(density.fontScale, 1.15f))) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(SessionEditorFrameBottomBarHeight)
                .graphicsLayer { alpha = dimAlpha }
                .onGloballyPositioned { barBounds = it.boundsInWindow() },
        ) {
            Box(modifier = Modifier.matchParentSize().edGlass(hazeState, EdBarIslandShape))
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrameTimeButton(
                    minutes = estimatedMinutes ?: 0,
                    limitMinutes = limitMinutes,
                    hasEstimate = estimatedMinutes != null,
                    tone = tone,
                    fraction = fraction,
                    bumpKey = bumpKey,
                    delta = deltaToShow,
                    onClick = onTime,
                )
                FrameSplitAdd(
                    menuOpen = menuOpen,
                    onAdd = onAdd,
                    onToggleMenu = { if (addActions != null) menuOpen = !menuOpen },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { addBounds = it.boundsInWindow() },
                )
                FrameAssistantButton(count = suggestionCount, onClick = onAssistant)
            }
            if (dimmed) {
                // `.ed-bar.dim{pointer-events:none}`: nada se suelta encima por error.
                Box(modifier = Modifier.matchParentSize().zIndex(2f).pointerInput(Unit) { detectTapGestures { } })
            }
        }
    }

    val bar = barBounds
    val add = addBounds
    if (menuOpen && addActions != null && bar != null && add != null) {
        EdGlassPopover(
            anchor = Rect(add.left, bar.top, add.right, bar.bottom),
            width = 232.dp,
            side = EdPopoverSide.AboveStart,
            onDismiss = { menuOpen = false },
            originXFraction = 0.6f,
            hazeState = hazeState,
        ) {
            frameAddEntries(hasStrengthSpace = hasStrengthSpace, hasCardioSpace = hasCardioSpace).forEach { entry ->
                when (entry) {
                    FrameAddEntry.EXERCISE -> EdMenuRow(
                        title = "Ejercicio",
                        subtitle = "Del catálogo",
                        icon = EdListIcons.Dumbbell,
                        onClick = { menuOpen = false; addActions.onExercise() },
                    )
                    FrameAddEntry.CARDIO -> addActions.onCardio?.let { action ->
                        EdMenuRow(title = "Cardio", icon = EdListIcons.Heart, onClick = { menuOpen = false; action() })
                    }
                    FrameAddEntry.CARDIO_SPACE -> addActions.onCardioSpace?.let { action ->
                        EdMenuRow(title = "Espacio de cardio", icon = EdListIcons.Heart, onClick = { menuOpen = false; action() })
                    }
                    FrameAddEntry.GROUP -> addActions.onGroup?.let { action ->
                        EdMenuRow(title = "Grupo nuevo", icon = EdListIcons.Layers, onClick = { menuOpen = false; action() })
                    }
                    FrameAddEntry.STRENGTH_SPACE -> addActions.onStrengthSpace?.let { action ->
                        EdMenuRow(title = "Fuerza", icon = EdListIcons.Dumbbell, onClick = { menuOpen = false; action() })
                    }
                }
            }
        }
    }
}

/** `.ed-time`: el anillo del tiempo y «81 min · de 90». Zona táctil de 48 dp; abre Asistente › Reglas › Tiempo. */
@Composable
private fun FrameTimeButton(
    minutes: Int,
    limitMinutes: Int?,
    hasEstimate: Boolean,
    tone: FrameRingTone,
    fraction: Float,
    bumpKey: Int,
    delta: Int?,
    onClick: () -> Unit,
) {
    val description = when {
        !hasEstimate -> "Tiempo estimado: sin ejercicios"
        limitMinutes != null && limitMinutes > 0 -> "Tiempo estimado $minutes de $limitMinutes minutos"
        else -> "Tiempo estimado $minutes minutos"
    }
    val toneColor = when (tone) {
        FrameRingTone.OK -> WizardColors.done
        FrameRingTone.ENERGIA -> SymbolPalette.energia
        FrameRingTone.DANGER -> WizardColors.danger
        FrameRingTone.NEUTRAL -> Color(0xFF8E8980)
    }
    // pulido:transicion ed-J02 stroke-dasharray 0,5 s --ed-ease · stroke 0,3 s (el anillo del tiempo)
    val animatedFraction by animateFloatAsState(fraction, tween(500, easing = EdMotion.Standard), label = "edRingFraction")
    val animatedColor by animateColorAsState(toneColor, tween(300), label = "edRingColor")
    Box(modifier = Modifier.height(48.dp)) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .testTag("session_editor_bar_time")
                .semantics { contentDescription = description }
                // pulido:animacion ed-J12 los minutos laten de escala .94 a 1 en 220 ms al tocarlos
                .edPressable(RoundedCornerShape(24.dp), tapScaleFrom = 0.94f, tapScaleMillis = 220, onClick = onClick)
                .padding(start = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(32.dp)) {
                val stroke = 3.5.dp.toPx()
                val radius = 11.5.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(center.x - radius, center.y - radius)
                drawArc(
                    color = Color(0x1CFFFFFF),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke),
                )
                if (animatedFraction > 0.001f) {
                    drawArc(
                        color = animatedColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedFraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                } else {
                    // Un trazo de longitud cero con extremo redondo se ve como un punto (SVG): el anillo vacío.
                    drawCircle(color = animatedColor, radius = stroke / 2f, center = Offset(center.x, center.y - radius))
                }
            }
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "$minutes min",
                    style = edText(15f, FontWeight.Bold, EdColors.ink, lineHeight = 16.5f, tabularNums = true),
                    // pulido:animacion ed-A16 .ed-time.tick b ed-bump 0,36 s --ed-spring (familia aparece-escala con 360 ms)
                    modifier = Modifier.kpknAnimate(
                        KpknFamilies.ApareceEscala.demo.copy(duration = KpknDuration(360)),
                        enabled = bumpKey > 0,
                        restartKey = bumpKey,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                if (limitMinutes != null && limitMinutes > 0) {
                    Text(
                        text = "de $limitMinutes",
                        style = edText(11f, FontWeight.Medium, EdColors.muted, lineHeight = 12.1f, tabularNums = true),
                        modifier = Modifier.padding(top = 1.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (delta != null) FrameDeltaBubble(delta = delta)
    }
}

/** `.ed-delta`: la burbuja «+9 min» que sube sobre los minutos al añadir (tinta) o quitar (verde `done`) y se desvanece en 1,3 s. */
@Composable
private fun FrameDeltaBubble(delta: Int) {
    Box(
        modifier = Modifier
            .offset(x = 34.dp, y = (-26).dp)
            // pulido:animacion ed-A04 .ed-delta 1,3 s --ed-ease forwards (familia aviso: entra, espera y sale)
            .kpknAnimate(KpknFamilies.Aviso.demo, restartKey = delta)
            .background(if (delta < 0) WizardColors.done else EdColors.ink, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
            .zIndex(3f),
    ) {
        Text(
            text = frameDeltaLabel(delta),
            style = edText(12f, FontWeight.Bold, EdColors.onInk, lineHeight = 14f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** `.ed-addw`: «Añadir» partido, píldora de tinta de 48 dp: el cuerpo abre el catálogo y la flecha despliega el menú. */
@Composable
private fun FrameSplitAdd(
    menuOpen: Boolean,
    onAdd: () -> Unit,
    onToggleMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = RoundedCornerShape(24.dp)
    // pulido:transicion ed-T02 .ed-addw .chv svg giro 0,22 s --ed-ease · ed-X01 rotate(180deg) con el menú abierto
    val turn by animateFloatAsState(if (menuOpen) 180f else 0f, tween(220, easing = EdMotion.Standard), label = "edAddChevron")
    Row(
        modifier = modifier
            .height(48.dp)
            .background(EdColors.ink, pill)
            .clip(pill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("session_editor_bar_add")
                .semantics { contentDescription = "Añadir ejercicio" }
                .edPressable(RectangleShape, pressedColor = Color(0x14000000), onClick = onAdd)
                .padding(start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EdListIcons.Plus, contentDescription = null, tint = EdColors.onInk, modifier = Modifier.size(18.dp))
            Text(
                text = "Añadir",
                style = edText(15f, FontWeight.SemiBold, EdColors.onInk, lineHeight = 18.75f),
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(
            Modifier
                .padding(vertical = 11.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x1F000000)),
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .testTag("session_editor_bar_add_menu")
                .semantics { contentDescription = "Más formas de añadir" }
                .edPressable(RectangleShape, pressedColor = Color(0x14000000), onClick = onToggleMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                EdListIcons.Up,
                contentDescription = null,
                tint = EdColors.onInk,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = turn },
            )
        }
    }
}

/** `.ed-asi`: el Asistente como círculo de 48 dp con su icono y, si hay sugerencias, su número (insignia `mus` con un corte de 2 dp). */
@Composable
private fun FrameAssistantButton(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("session_editor_bar_assistant"),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0x10FFFFFF), CircleShape)
                .semantics { contentDescription = if (count > 0) "Asistente, $count sugerencias" else "Asistente" }
                // pulido:animacion ed-J12 el Asistente late de escala .94 a 1 en 220 ms al tocarlo
                .edPressable(CircleShape, pressedColor = Color(0x12FFFFFF), tapScaleFrom = 0.94f, tapScaleMillis = 220, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(EdListIcons.Sparkle, contentDescription = null, tint = EdColors.ink, modifier = Modifier.size(22.dp))
        }
        if (count > 0) {
            val badge = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    // pulido:sombra ed-S01 .ed-asi em 0 0 0 2px #1b1b1d (preset corte-hoja)
                    .kpknRing(KpknShadow.CorteHoja, badge)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .background(EdColors.mus, badge)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = edText(11.5f, FontWeight.Bold, EdColors.onInk, lineHeight = 20f),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
