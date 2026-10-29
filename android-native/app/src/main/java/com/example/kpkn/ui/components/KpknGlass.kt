package com.example.kpkn.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Root [HazeState] provided by MainActivity. Glass surfaces (sheets, dialogs, docks)
 * should read this instead of inventing per-screen states when possible.
 * Null = no blur source available; callers must fall back to a solid dark scrim.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Variante del vidrio v2 (KPKN_UI_V2 §2).
 *
 * - [ISLAND]: barras y píldoras flotantes. Desenfoque de 36 dp, tinte #0A0A0A al 55 % y filete en degradado.
 * - [SHEET]: hojas y diálogos. Desenfoque de 48 dp, tinte #0A0A0A al 66 % y filete solo en el borde superior.
 */
enum class KpknGlassVariant { ISLAND, SHEET }

/**
 * KPKN **vidrio v2** (KPKN_UI_V2 §2): el desenfoque de lo que hay detrás, sin base opaca.
 *
 * Por qué cambió: el DarkMica anterior pintaba un gris #0E0E0E al 94 % como base, y encima el desenfoque al 42 % bajo un tinte del
 * 58 %. Lo que quedaba debajo se veía NÍTIDO y apagado (solo pasaba el 6 %) y el desenfoque casi no se percibía.
 *
 * Receta: el efecto de Haze va al 100 % de opacidad, sobre fondo negro, con un tinte #0A0A0A ([IslandTint] o [SheetTint], según la
 * [KpknGlassVariant]) y ruido de 0,05. Lo que pasa por debajo queda como manchas de color suaves. Sin fuente de desenfoque (API
 * < 31 o sin HazeState) se pinta un velo de [VeilFallback] al 96 %.
 *
 * Usa siempre [Modifier.kpknGlass] o [Modifier.kpknGlassOrFallback] (o [Modifier.kpknHazeEffect]). Las llamadas existentes no cambian
 * de firma: sin `variant` son islas. `kpknCoverGlass` (la cabecera del entreno sobre el arte) no pasa a la v2.
 */
/**
 * Superficie flotante de hojas, avisos y píldoras (pedido del usuario, 29-oct): gris oscuro plano, sin desenfoque, con un filete tenue y
 * una sombra REAL de elevación de la plataforma (`graphicsLayer.shadowElevation`), no una sombra pintada.
 */
object KpknFloating {
    val Surface = Color(0xFF1B1B1D)
    val Border = Color(0x14FFFFFF)
    val SheetElevation = 28.dp
    val DialogElevation = 24.dp
    val PillElevation = 14.dp
}

/** [KpknFloating]: sombra de elevación real, relleno gris plano y filete, recortado a [shape]. */
fun Modifier.kpknFloatingSurface(shape: Shape, elevation: Dp, withBorder: Boolean = true): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    .clip(shape)
    .background(KpknFloating.Surface)
    .then(if (withBorder) Modifier.border(1.dp, KpknFloating.Border, shape) else Modifier)

object KpknGlass {
    /** Desenfoque de la isla v2: 36 dp. */
    val IslandBlur = 36.dp

    /** Desenfoque de la hoja y del diálogo v2: 48 dp. */
    val SheetBlur = 48.dp

    /** Tinte de la isla v2: #0A0A0A al 55 %. */
    val IslandTint: Color = Color(0xFF0A0A0A).copy(alpha = 0.55f)

    /** Tinte de la hoja y del diálogo v2: #0A0A0A al 66 %. */
    val SheetTint: Color = Color(0xFF0A0A0A).copy(alpha = 0.66f)

    /** Ruido de la receta v2. */
    const val V2NoiseFactor = 0.05f

    /** Filete de la isla: degradado vertical de blanco al 14 % a blanco al 4 %. */
    val IslandBorderTop: Color = Color.White.copy(alpha = 0.14f)
    val IslandBorderBottom: Color = Color.White.copy(alpha = 0.04f)

    /** Filete de la hoja: blanco al 10 %, solo en el borde superior. Se desvanece en [SheetBorderFade] hacia abajo. */
    val SheetBorder: Color = Color.White.copy(alpha = 0.10f)
    val SheetBorderFade = 40.dp

    /** Sin fuente de desenfoque (API < 31 o sin HazeState): #0E0E0E al 96 %. */
    val VeilFallback: Color = Color(0xFF0E0E0E).copy(alpha = 0.96f)

    /**
     * Desenfoque del DarkMica anterior (100 dp). Solo lo usa [kpknCoverGlassStyle]; el vidrio v2 usa [IslandBlur] y [SheetBlur].
     */
    val BlurRadius = 100.dp

    /** Ruido del DarkMica anterior. Solo lo usa [kpknCoverGlassStyle]. */
    const val NoiseFactor = 0.02f

    /** Solo compatibilidad: opacidad del DarkMica anterior. La receta v2 no tiene base opaca bajo el efecto. */
    const val EffectAlpha = 0.42f

    /** Solo compatibilidad: tinte del DarkMica anterior (#0E0E0E al 58 %). La receta v2 usa [IslandTint] o [SheetTint]. */
    val Tint = Color(0xFF0E0E0E).copy(alpha = 0.58f)

    /** Solo compatibilidad: base opaca del DarkMica anterior (#0E0E0E al 94 %). La receta v2 no la pinta. */
    val Base = Color(0xFF0E0E0E).copy(alpha = 0.94f)

    /** Solo compatibilidad: alias de [Base]. */
    val Scrim: Color get() = Base

    /** Solo compatibilidad: filete del DarkMica anterior (blanco al 5 %). Lo siguen usando el vidrio de portada y otros paneles. */
    val BorderColor = Color.White.copy(alpha = 0.05f)
    val BorderWidth = 1.dp

    /**
     * Solo compatibilidad: fondo casi opaco de antes, sin desenfoque (#0E0E0E al 97 %). Lo siguen usando otros paneles; la receta v2
     * usa [VeilFallback].
     */
    val FallbackScrim = Color(0xFF0E0E0E).copy(alpha = 0.97f)

    /** Fondo de la ventana sin desenfoque (menús y avisos de [kpknWindowGlass]). */
    val WindowFallbackScrim = Color(0xF80E0E0E)

    val SheetCornerRadius = 28.dp
    val DialogCornerRadius = 28.dp
}

/**
 * Estilo de Haze de la receta v2 para la [variant] (isla u hoja): fondo negro, tinte, desenfoque y ruido de [KpknGlass].
 */
fun kpknGlassStyle(variant: KpknGlassVariant = KpknGlassVariant.ISLAND): HazeStyle {
    val sheet = variant == KpknGlassVariant.SHEET
    return HazeStyle(
        backgroundColor = Color.Black,
        tints = listOf(HazeTint(if (sheet) KpknGlass.SheetTint else KpknGlass.IslandTint)),
        blurRadius = if (sheet) KpknGlass.SheetBlur else KpknGlass.IslandBlur,
        noiseFactor = KpknGlass.V2NoiseFactor,
        fallbackTint = HazeTint(KpknGlass.VeilFallback),
    )
}

/** Estilo de Haze del vidrio de portada (cabecera del entreno sobre el arte). Sin cambios: no pasa a la v2. */
fun kpknCoverGlassStyle(): HazeStyle = HazeStyle(
    blurRadius = KpknGlass.BlurRadius,
    tint = HazeTint(Color(0xFF0E0E0E).copy(alpha = 0.18f)),
    backgroundColor = Color.Transparent,
    noiseFactor = KpknGlass.NoiseFactor,
    fallbackTint = HazeTint(Color(0xFF0E0E0E).copy(alpha = 0.18f)),
)

/**
 * Desenfoque de la receta v2 (sin base opaca), en la [variant] indicada. Sin [variant], es la isla.
 */
fun Modifier.kpknHazeEffect(
    hazeState: HazeState,
    variant: KpknGlassVariant = KpknGlassVariant.ISLAND,
): Modifier = this.hazeEffect(state = hazeState, style = kpknGlassStyle(variant)) {
    alpha = 1f
}

/**
 * Aplica el vidrio v2 de KPKN. Por defecto es una isla; con [KpknGlassVariant.SHEET] es una hoja.
 *
 * IMPORTANT: blur source must be a sibling `hazeSource` drawn before this node.
 */
fun Modifier.kpknGlass(
    hazeState: HazeState,
    shape: Shape,
    withBorder: Boolean = true,
    additionalScrim: Color = Color.Transparent,
    variant: KpknGlassVariant = KpknGlassVariant.ISLAND,
): Modifier = this
    .clip(shape)
    .kpknHazeEffect(hazeState, variant)
    .then(additionalScrimModifier(additionalScrim))
    .kpknGlassBorder(variant, shape, withBorder)

/**
 * Cover-specific glass: preserves the selected session artwork instead of
 * applying the near-opaque DarkMica base used by sheets and dialogs.
 */
fun Modifier.kpknCoverGlass(
    hazeState: HazeState,
    shape: Shape,
    withBorder: Boolean = true,
): Modifier = this
    .clip(shape)
    .background(Color(0xFF0E0E0E).copy(alpha = 0.12f))
    .hazeEffect(state = hazeState, style = kpknCoverGlassStyle()) {
        alpha = 0.50f
    }
    .then(
        if (withBorder) {
            Modifier.border(width = KpknGlass.BorderWidth, color = KpknGlass.BorderColor, shape = shape)
        } else {
            Modifier
        },
    )

/**
 * Translucent cover mica with solid fallback — for live workout header chrome
 * that must preserve session artwork/colors.
 */
fun Modifier.kpknCoverGlassOrFallback(
    hazeState: HazeState?,
    shape: Shape,
    withBorder: Boolean = true,
): Modifier = if (hazeState != null) {
    kpknCoverGlass(hazeState, shape, withBorder = withBorder)
} else {
    this
        .clip(shape)
        .background(Color(0xFF0E0E0E).copy(alpha = 0.32f))
        .then(
            if (withBorder) {
                Modifier.border(width = KpknGlass.BorderWidth, color = KpknGlass.BorderColor, shape = shape)
            } else {
                Modifier
            },
        )
}

/**
 * Vidrio de muelle (dock) con la receta v2. Sin base opaca: el desenfoque va al 100 %. Sin [variant], es una isla.
 */
fun Modifier.kpknDockGlass(
    hazeState: HazeState,
    shape: Shape,
    withBorder: Boolean = false,
    variant: KpknGlassVariant = KpknGlassVariant.ISLAND,
): Modifier = this
    .clip(shape)
    .kpknHazeEffect(hazeState, variant)
    .kpknGlassBorder(variant, shape, withBorder)

fun Modifier.kpknDockGlassOrFallback(
    hazeState: HazeState?,
    shape: Shape,
    withBorder: Boolean = false,
    variant: KpknGlassVariant = KpknGlassVariant.ISLAND,
): Modifier = if (hazeState != null) {
    kpknDockGlass(hazeState, shape, withBorder = withBorder, variant = variant)
} else {
    this
        .clip(shape)
        .background(KpknGlass.VeilFallback)
        .kpknGlassBorder(variant, shape, withBorder)
}

/**
 * Vidrio v2 con respaldo: sin [hazeState] pinta el velo de [KpknGlass.VeilFallback] con el mismo filete. Sin [variant], es una isla.
 */
fun Modifier.kpknGlassOrFallback(
    hazeState: HazeState?,
    shape: Shape,
    withBorder: Boolean = true,
    additionalScrim: Color = Color.Transparent,
    variant: KpknGlassVariant = KpknGlassVariant.ISLAND,
): Modifier = if (hazeState != null) {
    kpknGlass(
        hazeState,
        shape,
        withBorder = withBorder,
        additionalScrim = additionalScrim,
        variant = variant,
    )
} else {
    this
        .clip(shape)
        .background(KpknGlass.VeilFallback)
        .then(additionalScrimModifier(additionalScrim))
        .kpknGlassBorder(variant, shape, withBorder)
}

/**
 * Filete del vidrio v2. La isla lleva un degradado vertical de blanco al 14 % a blanco al 4 % en todo el borde. La hoja solo lleva
 * blanco al 10 % en el borde superior, desvanecido en [KpknGlass.SheetBorderFade].
 */
private fun Modifier.kpknGlassBorder(variant: KpknGlassVariant, shape: Shape, enabled: Boolean): Modifier = when {
    !enabled -> this
    variant == KpknGlassVariant.SHEET -> this.then(sheetTopBorder(shape))
    else -> this.border(
        BorderStroke(
            KpknGlass.BorderWidth,
            Brush.verticalGradient(listOf(KpknGlass.IslandBorderTop, KpknGlass.IslandBorderBottom)),
        ),
        shape,
    )
}

/** Filete solo arriba de la hoja: necesita la densidad para medir el desvanecido en píxeles. */
private fun sheetTopBorder(shape: Shape): Modifier = Modifier.composed {
    val fadePx = with(LocalDensity.current) { KpknGlass.SheetBorderFade.toPx() }
    border(
        BorderStroke(
            KpknGlass.BorderWidth,
            Brush.verticalGradient(listOf(KpknGlass.SheetBorder, Color.Transparent), endY = fadePx),
        ),
        shape,
    )
}

private fun additionalScrimModifier(color: Color): Modifier = if (color.alpha > 0f) {
    Modifier.drawWithContent {
        drawRect(color = color)
        drawContent()
    }
} else {
    Modifier
}

fun Modifier.kpknWindowGlass(
    shape: Shape,
    withBorder: Boolean = true,
): Modifier = this
    .clip(shape)
    .background(KpknGlass.WindowFallbackScrim)
    .then(
        if (withBorder) {
            Modifier.border(
                width = KpknGlass.BorderWidth,
                color = KpknGlass.BorderColor,
                shape = shape,
            )
        } else {
            Modifier
        },
    )
