package com.example.kpkn.ui.components

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.adapt.adapt
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Cierra la [KpknSheet] que lo contiene con su transición de salida y después llama a su `onDismissRequest`. Null fuera de una hoja.
 * Lo usan los botones de la propia hoja («Listo») para salir igual que al tocar fuera o arrastrar.
 */
val LocalKpknSheetDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Transición de todas las hojas (Arreglos v1, escena a-motion): el fondo se oscurece en 220 ms; la hoja sube desde abajo (el 100 % de
 * su alto) en 420 ms con freno suave (cubic-bezier 0.22, 1, 0.36, 1) y su contenido aparece 120 ms después subiendo 10 dp con un
 * fundido de 340 ms (ease-out). Al cerrar, lo mismo al revés y algo más rápido (260 ms). Con «reducir movimiento», sin desplazamiento:
 * solo un fundido corto.
 */
internal object KpknSheetMotion {
    const val SCRIM_IN_MS = 220
    const val SHEET_IN_MS = 420
    const val CONTENT_DELAY_MS = 120
    const val CONTENT_IN_MS = 340
    const val SHEET_OUT_MS = 260
    const val SCRIM_OUT_MS = 220
    const val CONTENT_OUT_MS = 160
    const val REDUCED_MS = 150
    val SheetInEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** La misma curva al revés: arranca suave y acelera hacia fuera. */
    val SheetOutEasing = CubicBezierEasing(0.64f, 0f, 0.78f, 0f)
    /** `ease-out` de CSS. */
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    /** `ease-in` de CSS. */
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val ContentRise = 10.dp
}

/**
 * Canonical KPKN DarkMica bottom sheet.
 *
 * Height is **content-proportional** (wrap), capped by [maxHeightFraction] so tall content
 * can still scroll without covering the whole screen by default.
 *
 * When [stableHeightFraction] is set, the sheet uses that fraction of the screen as a fixed
 * height so tab switches (RULES / ASSISTANT) do not resize the panel.
 */
@Composable
fun KpknSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    showDragHandle: Boolean = true,
    /** Adds top safe-area padding for edge-to-edge sheets whose content reaches the status bar. */
    safeTopInset: Boolean = false,
    /**
     * Maximum fraction of the screen the sheet may occupy. Does NOT force-fill —
     * short content stays short.
     */
    maxHeightFraction: Float = 0.92f,
    /** Alias treated as [maxHeightFraction] only (never forces full-height fill). */
    heightFraction: Float? = null,
    /**
     * When non-null, sheet height is fixed to this fraction of the screen (capped by max),
     * so swapping tab content does not change the sheet size.
     */
    stableHeightFraction: Float? = null,
    hazeState: HazeState? = null,
    /** Optional black veil drawn over the blur for a scoped contrast adjustment. */
    additionalGlassScrim: Color = Color.Transparent,
    /** Velo detrás de la hoja (la sesión en vivo usa el negro al 60 % del prototipo 02). Transparente por defecto. */
    backdropScrim: Color = Color.Transparent,
    /**
     * Hoja plana (Arreglos v1, `.np-sheet`): con un color, la hoja es ese color liso, sin vidrio ni degradado, con un filete superior de
     * 1 dp blanco al 7 % y el asa del lienzo (36 × 4). Null: la hoja de vidrio de siempre.
     */
    surfaceColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A caller may own a local source (for example the workout overlay).  The
    // previous implementation silently discarded it and always sampled the
    // activity-wide haze source, which made every sheet blur unrelated content.
    val resolvedHaze = hazeState ?: LocalHazeState.current
    val cap = (heightFraction ?: maxHeightFraction).coerceIn(0.35f, 1f)
    // Mientras la hoja está en composición, la isla superior se oculta (I12).
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    KpknPortal {
        KpknSheetBody(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            dismissible = dismissible,
            showDragHandle = showDragHandle,
            safeTopInset = safeTopInset,
            maxHeightFraction = cap,
            stableHeightFraction = stableHeightFraction?.coerceIn(0.35f, cap),
            hazeState = resolvedHaze,
            additionalGlassScrim = additionalGlassScrim,
            backdropScrim = backdropScrim,
            surfaceColor = surfaceColor,
            content = content,
        )
    }
}

@Composable
private fun KpknSheetBody(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    dismissible: Boolean,
    showDragHandle: Boolean,
    safeTopInset: Boolean,
    maxHeightFraction: Float,
    stableHeightFraction: Float?,
    hazeState: HazeState?,
    additionalGlassScrim: Color,
    backdropScrim: Color,
    surfaceColor: Color?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(
        topStart = KpknGlass.SheetCornerRadius,
        topEnd = KpknGlass.SheetCornerRadius,
    )
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val panelInteraction = remember { MutableInteractionSource() }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val settleAnim = remember { Animatable(0f) }
    /** 1 = la hoja entera por debajo del borde (100 % de su alto); 0 = en su sitio. */
    val enterOffset = remember { Animatable(if (reduceMotion) 0f else 1f) }
    val scrimAlpha = remember { Animatable(0f) }
    /** 0 → 1: el contenido aparece (fundido y 10 dp de subida); con reducir movimiento, toda la hoja se funde. */
    val contentIn = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            launch { scrimAlpha.animateTo(1f, tween(KpknSheetMotion.REDUCED_MS)) }
            contentIn.animateTo(1f, tween(KpknSheetMotion.REDUCED_MS))
        } else {
            launch { scrimAlpha.animateTo(1f, tween(KpknSheetMotion.SCRIM_IN_MS, easing = KpknSheetMotion.EaseOut)) }
            launch { enterOffset.animateTo(0f, tween(KpknSheetMotion.SHEET_IN_MS, easing = KpknSheetMotion.SheetInEasing)) }
            contentIn.animateTo(
                1f,
                tween(KpknSheetMotion.CONTENT_IN_MS, delayMillis = KpknSheetMotion.CONTENT_DELAY_MS, easing = KpknSheetMotion.EaseOut),
            )
        }
    }

    fun dismissAnimated() {
        if (!dismissible || dismissing) return
        dismissing = true
        scope.launch {
            if (reduceMotion) {
                launch { scrimAlpha.animateTo(0f, tween(KpknSheetMotion.REDUCED_MS)) }
                contentIn.animateTo(0f, tween(KpknSheetMotion.REDUCED_MS))
            } else {
                launch { scrimAlpha.animateTo(0f, tween(KpknSheetMotion.SCRIM_OUT_MS, easing = KpknSheetMotion.EaseIn)) }
                launch { contentIn.animateTo(0f, tween(KpknSheetMotion.CONTENT_OUT_MS, easing = KpknSheetMotion.EaseIn)) }
                enterOffset.animateTo(1f, tween(KpknSheetMotion.SHEET_OUT_MS, easing = KpknSheetMotion.SheetOutEasing))
            }
            currentOnDismiss()
        }
    }

    BackHandler(enabled = dismissible) { dismissAnimated() }
    BackHandler(enabled = !dismissible) { /* Non-dismissible sheets consume Back. */ }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(350f),
    ) {
        val maxHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val sheetCapPx = maxHeightPx * maxHeightFraction
        val sheetCapDp = with(density) { sheetCapPx.toDp() }
        val stableHeightDp = stableHeightFraction?.let { frac ->
            with(density) { (maxHeightPx * frac).toDp() }
        }
        val dismissThresholdPx = with(density) {
            minOf(150.dp.toPx(), sheetCapPx * 0.25f)
        }

        fun settle() {
            if (!dismissible) {
                scope.launch {
                    settleAnim.snapTo(dragOffsetPx)
                    settleAnim.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { dragOffsetPx = value }
                }
                return
            }
            scope.launch {
                settleAnim.snapTo(dragOffsetPx)
                if (dragOffsetPx >= dismissThresholdPx && !dismissing) {
                    dismissing = true
                    launch { scrimAlpha.animateTo(0f, tween(KpknSheetMotion.SCRIM_OUT_MS, easing = KpknSheetMotion.EaseIn)) }
                    settleAnim.animateTo(sheetCapPx, animationSpec = tween(durationMillis = 220)) {
                        dragOffsetPx = value
                    }
                    currentOnDismiss()
                } else {
                    settleAnim.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { dragOffsetPx = value }
                }
            }
        }

        val nestedScrollConnection = remember(dismissible, sheetCapPx, dismissThresholdPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!dismissible || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y < 0f && dragOffsetPx > 0f) {
                        val newOffset = (dragOffsetPx + available.y).coerceAtLeast(0f)
                        val consumed = newOffset - dragOffsetPx
                        dragOffsetPx = newOffset
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Focus/bring-into-view and accessibility scrolls must never drag the panel.
                    // They have no user fling to settle a leftover positive offset afterwards.
                    if (!dismissible || source != NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y > 0f) {
                        dragOffsetPx += available.y
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!dismissible) return Velocity.Zero
                    settle()
                    return Velocity.Zero
                }
            }
        }

        fun dragBy(delta: Float) {
            if (!dismissible) return
            dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
        }

        val dragModifier = if (dismissible) {
            Modifier.pointerInput(sheetCapPx, dismissThresholdPx) {
                detectVerticalDragGestures(
                    onDragEnd = { settle() },
                    onDragCancel = { settle() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragBy(dragAmount)
                    },
                )
            }
        } else {
            Modifier
        }

        // Tap-outside lives on a full-screen layer; the sheet itself is clipped
        // to rounded corners so no rectangular scrim peeks above the handle.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backdropScrim.alpha > 0f) {
                        Modifier
                            .graphicsLayer { alpha = scrimAlpha.value }
                            .background(backdropScrim)
                    } else Modifier,
                )
                .then(
                    if (dismissible) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dismissAnimated() },
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        val flat = surfaceColor != null
        Box(
            modifier = modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (stableHeightDp != null) Modifier.height(stableHeightDp)
                    else Modifier.wrapContentHeight().heightIn(max = sheetCapDp),
                )
                // La hoja entra y sale desplazándose el 100 % de su propio alto (más lo que la arrastra el dedo).
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        val travel = enterOffset.value * placeable.height
                        placeable.placeRelative(0, (dragOffsetPx + travel).roundToInt())
                    }
                }
                .then(
                    // `.np-sheet`: `box-shadow: 0 -1px 0 #ffffff12`, un filete de 1 dp por encima del borde redondeado.
                    if (flat) Modifier.background(Color.White.copy(alpha = 0.07f), sheetShape).padding(top = 1.dp) else Modifier,
                )
                .graphicsLayer {
                    // La hoja con color propio se recorta aquí; la flotante se recorta ella misma después de proyectar su sombra (si se
                    // recortase antes, la sombra real desaparecería).
                    if (flat) {
                        clip = true
                        shape = sheetShape
                    }
                    if (reduceMotion) alpha = contentIn.value
                }
                .then(
                    if (surfaceColor != null) {
                        Modifier.background(surfaceColor, sheetShape)
                    } else {
                        // Gris oscuro plano con sombra real de elevación (KpknFloating); sin vidrio ni desenfoque (Refinamiento 02).
                        Modifier.kpknFloatingSurface(sheetShape, KpknFloating.SheetElevation)
                    },
                )
                .clickable(
                    interactionSource = panelInteraction,
                    indication = null,
                    onClick = {},
                ),
        ) {
            KpknSheetContentTheme {
                CompositionLocalProvider(LocalKpknSheetDismiss provides { dismissAnimated() }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (stableHeightDp != null) Modifier.fillMaxSize()
                                else Modifier.wrapContentHeight().heightIn(max = sheetCapDp),
                            )
                            .graphicsLayer {
                                if (!reduceMotion) {
                                    alpha = contentIn.value
                                    translationY = (1f - contentIn.value) * KpknSheetMotion.ContentRise.toPx()
                                }
                            }
                            .then(if (safeTopInset) Modifier.statusBarsPadding() else Modifier)
                            .navigationBarsPadding()
                            .padding(bottom = 8.dp)
                            .nestedScroll(nestedScrollConnection)
                            .then(if (dismissible) dragModifier else Modifier),
                    ) {
                        if (showDragHandle) {
                            if (flat) {
                                // `.grab`: 36 × 4, blanco al 23 %, 10 dp por encima y 14 por debajo.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .then(if (dismissible) dragModifier else Modifier)
                                        .semantics {
                                            contentDescription = if (dismissible) "Arrastra hacia abajo para cerrar" else "Hoja"
                                        },
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 10.dp)
                                            .size(width = 36.dp, height = 4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.227f)),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp.adapt())
                                        .then(if (dismissible) dragModifier else Modifier)
                                        .semantics {
                                            contentDescription = if (dismissible) {
                                                "Arrastra hacia abajo para cerrar"
                                            } else {
                                                "Hoja"
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 42.dp.adapt(), height = 5.dp.adapt())
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(KpknSheetTokens.Handle),
                                    )
                                }
                            }
                        }
                        content()
                    }
                }
            }
        }
    }
}
