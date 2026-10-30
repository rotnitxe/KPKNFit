package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknKeyframes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.depth.KpknGlassPreset
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.KpknShadowSpec
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow
import dev.chrisbanes.haze.HazeState

/*
 * Piezas comunes de la lista, la isla superior y la barra inferior del editor (Pulido v1 · EDITOR · tanda A2).
 * Todo sale de `ed.css`: el vidrio `.ed-glass`, las curvas `--ed-ease` / `--ed-spring` y los iconos de `scenes-ed.js`.
 */

/**
 * Curvas del editor (`--ed-ease`, `--ed-spring`, `ease`, la «cae»), tomadas del catálogo de movimiento de los cimientos (`ui/design/motion`).
 * Un solo sitio: las pantallas del editor no escriben curvas sueltas.
 */
internal object EdMotion {
    /** `--ed-ease`: `cubic-bezier(.2,.8,.2,1)`. */
    val Standard: Easing = KpknMotion.Estandar

    /** `--ed-spring`: `cubic-bezier(.3,1.5,.5,1)`. */
    val Spring: Easing = KpknMotion.Resorte

    /** `ease` de CSS (color, fondo, opacidad). */
    val Ease: Easing = KpknMotion.Suave

    /** `ease-out` de CSS (el anillo de `ed-ring`). */
    val EaseOut: Easing = KpknMotion.Salida

    /** `cubic-bezier(.5,0,.9,.5)`: las salidas del editor. */
    val Falls: Easing = KpknMotion.Cae
}

/** Familias de `@keyframes` del editor que el catálogo agrupa con otra y cuyo valor exacto de la fila difiere (se pasan con su override). */
internal object EdFamilies {
    /** `ed-lbl-r` (ed-A01): entra desde el 26 % de su ancho por la derecha, 220 ms, `--ed-ease`. */
    val LabelFromRight = KpknAnimation(
        kpknKeyframes("ed-lbl-r", "@keyframes ed-lbl-r { 0% { transform: translateX(26%); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { alpha(0f); translateXFraction(0.26f) }
            at(1f) { alpha(1f); transformNone() }
        },
        KpknDuration(220), KpknMotion.Estandar, fill = KpknFill.Both,
    )

    /** `ed-lbl-l` (ed-A02): lo mismo desde la izquierda. */
    val LabelFromLeft = KpknAnimation(
        kpknKeyframes("ed-lbl-l", "@keyframes ed-lbl-l { 0% { transform: translateX(-26%); opacity: 0; } 100% { transform: none; opacity: 1; } }") {
            at(0f) { alpha(0f); translateXFraction(-0.26f) }
            at(1f) { alpha(1f); transformNone() }
        },
        KpknDuration(220), KpknMotion.Estandar, fill = KpknFill.Both,
    )
}

/** Radio de las islas de vidrio del editor (`.ed-top` y `.ed-bar`: 30 y 32; con 56–64 dp de alto es una píldora). */
internal val EdTopIslandShape: Shape = RoundedCornerShape(30.dp)
internal val EdBarIslandShape: Shape = RoundedCornerShape(32.dp)

/** `#1b1b1d99`: el relleno del vidrio del editor (`.ed-glass`). */
internal val EdGlassFill = Color(0x991B1B1D)

/** `1px solid #ffffff22`. */
internal val EdGlassStroke = Color(0x22FFFFFF)

/** `0 10px 30px #000a` (ed-S02): la caída del vidrio. El preset `isla-vidrio` nombra y la fila manda: 10 dp de desplazamiento y 30 de desenfoque. */
// pulido:sombra ed-S02 .ed-glass
internal val EdGlassDrop: KpknShadowSpec = KpknShadowSpec(
    KpknShadow.IslaVidrio.spec.layers.first { !it.inset }.copy(y = 10.dp, blur = 30.dp),
)

/** `inset 0 1px #ffffff18` (ed-S02): el realce interior de arriba. */
// pulido:sombra ed-S02 .ed-glass
internal val EdGlassInset: KpknShadowSpec = KpknShadowSpec(
    KpknShadow.IslaVidrio.spec.layers.first { it.inset }.copy(color = Color(0x18FFFFFF)),
)

/**
 * `.ed-glass`: vidrio del editor (isla de arriba y barra de abajo). Caída `0 10 30 #000a`, desenfoque 24 / saturación 1,7 (`vidrio-editor`),
 * relleno #1b1b1d99, realce interior de 1 dp y filete blanco al 13 %. Sin [hazeState] (Robolectric, sin fuente de desenfoque) cae al velo.
 */
internal fun Modifier.edGlass(hazeState: HazeState?, shape: Shape): Modifier = this
    // pulido:vidrio ed-V01 .ed-glass blur 24 · saturate 1,7
    .kpknShadow(EdGlassDrop, shape)
    .kpknGlassOrFallback(hazeState, shape, withBorder = false, preset = KpknGlassPreset.VidrioEditor)
    .background(EdGlassFill)
    .kpknInset(EdGlassInset, shape)
    .border(1.dp, EdGlassStroke, shape)

/** Iconos del lienzo que el set KPKN Line aún no trae (trazos de `scenes-ed.js`, retícula de 24, trazo de 1,75). */
internal object EdListIcons {
    val Sparkle: ImageVector by lazy {
        icon("EdSparkle", "M12 3.5l1.9 5.1 5.1 1.9-5.1 1.9L12 17.5l-1.9-5.1L5 10.5l5.1-1.9z", "M18.5 16.5v3M17 18h3")
    }
    val Dumbbell: ImageVector by lazy { icon("EdDumbbell", "M3 10v4M6 8v8M18 8v8M21 10v4M6 12h12") }
    val Heart: ImageVector by lazy {
        icon("EdHeart", "M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0 1 12 7.5 4.3 4.3 0 0 1 19.5 10c0 5.5-7.5 10-7.5 10z")
    }
    val Back: ImageVector by lazy { icon("EdBack", "M15 5l-7 7 7 7") }
    val Forward: ImageVector by lazy { icon("EdForward", "M9 5l7 7-7 7") }
    val Down: ImageVector by lazy { icon("EdDown", "M5 9l7 7 7-7") }
    val Up: ImageVector by lazy { icon("EdUp", "M5 15l7-7 7 7") }
    val Plus: ImageVector by lazy { icon("EdPlus", "M12 5v14M5 12h14") }
    val Trash: ImageVector by lazy { icon("EdTrash", "M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13") }
    val Swap: ImageVector by lazy { icon("EdSwap", "M7 7h11l-3-3M17 17H6l3 3") }
    val History: ImageVector by lazy { icon("EdHistory", "M4 12a8 8 0 1 0 2.5-5.8", "M4 4.5v4h4M12 8v4l3 2") }
    val Layers: ImageVector by lazy {
        icon("EdLayers", "M12 4l8.5 4.5L12 13 3.5 8.5z", "M3.5 12.5L12 17l8.5-4.5M3.5 16.5L12 21l8.5-4.5")
    }
    val Grip: ImageVector by lazy {
        icon("EdGrip", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01", width = 2.6f)
    }
    val More: ImageVector by lazy {
        // Tres puntos de 1,3 de radio (`more` del lienzo): trazos de longitud cero con cap redondo.
        icon("EdMore", "M5 12h.01M12 12h.01M19 12h.01", width = 2.6f)
    }

    /** `starOn` del lienzo: la misma estrella, rellena. */
    val StarOn: ImageVector by lazy {
        val ink = SolidColor(Color.Black)
        ImageVector.Builder(name = "EdStarOn", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString("M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z").toNodes(),
                fill = ink,
                stroke = ink,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
    val Star: ImageVector by lazy { icon("EdStarOutline", "M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z") }

    private fun icon(name: String, vararg paths: String, width: Float = 1.75f): ImageVector {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = ink,
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}

/**
 * Pulsación de un botón del editor: la forma recortada y un fondo que sube a [pressedColor] mientras se pulsa (0,16 s, `ease`: `:active{background:#ffffff14}`).
 * Sin ripple, como el lienzo. Con [tapScaleFrom] distinto de 1, al tocar el botón «late»: parte de esa escala y vuelve a 1 en [tapScaleMillis] ms en
 * línea recta (`t.animate([{transform:'scale(.9)'},{transform:'none'}],{duration:200})`, ed-J10 y ed-J12).
 */
@Composable
internal fun Modifier.edPressable(
    shape: Shape,
    pressedColor: Color = Color(0x14FFFFFF),
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    tapScaleFrom: Float = 1f,
    tapScaleMillis: Int = 200,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val tap = remember { Animatable(1f) }
    val reduced = wizardReducedMotion()
    // pulido:transicion ed-T14 .ed-ib background 0,16 s ease
    val bg by animateColorAsState(
        targetValue = if (pressed && enabled) pressedColor else pressedColor.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 160, easing = EdMotion.Ease),
        label = "edPress",
    )
    return this
        .graphicsLayer {
            scaleX = tap.value
            scaleY = tap.value
        }
        .clip(shape)
        .background(bg)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClickLabel = onClickLabel) {
            if (tapScaleFrom != 1f && !reduced) {
                scope.launch {
                    tap.snapTo(tapScaleFrom)
                    tap.animateTo(1f, tween(tapScaleMillis, easing = LinearEasing))
                }
            }
            onClick()
        }
}
