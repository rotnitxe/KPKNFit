package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Liquid Glass de la página larga, versión Compose.
 *
 * Dos recursos, cada uno donde tiene sentido:
 *  - [Modifier.wizardGlass]: lámina translúcida con borde de brillo superior. No
 *    desenfoca nada (las secciones se apoyan sobre el fondo ambiental, que ya es
 *    suave); es barata y sirve para secciones y filas-resumen.
 *  - [wizardHazeStyle] con `hazeEffect`: vidrio que SÍ desenfoca lo que pasa por
 *    detrás. Solo la cabecera y el botón de confirmar lo usan, porque son lo único
 *    que se queda quieto mientras el contenido se desliza bajo ellos.
 */

/** Estilo de `hazeEffect` para cabecera y botón: desenfoque medio, tinte blanco tenue y grano mínimo. */
internal fun wizardHazeStyle(): HazeStyle = HazeStyle(
    blurRadius = 28.dp,
    tint = HazeTint(Color.White.copy(alpha = 0.14f)),
    backgroundColor = Color.White.copy(alpha = 0.06f),
    noiseFactor = 0.10f,
)

/** Borde de vidrio: más claro arriba-izquierda, casi invisible en el medio y con un brillo leve abajo-derecha. */
internal fun wizardGlassBorderBrush(): Brush = Brush.linearGradient(
    0f to WizardColors.glassBorderHigh,
    0.45f to WizardColors.glassBorderLow,
    1f to WizardColors.glassBorderHigh.copy(alpha = 0.14f),
)

/**
 * Lámina de cristal: recorte, relleno que sube de intensidad hacia arriba (recibe
 * la luz) y borde de brillo superior. [strong] eleva el relleno para superficies
 * que deben destacar sobre otras láminas.
 */
fun Modifier.wizardGlass(
    shape: Shape = WizardShapes.section,
    strong: Boolean = false,
): Modifier {
    val base = if (strong) WizardColors.glassFillStrong else WizardColors.glassFill
    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                listOf(base.copy(alpha = (base.alpha * 1.45f).coerceAtMost(1f)), base),
            ),
            shape = shape,
        )
        .border(width = 1.dp, brush = wizardGlassBorderBrush(), shape = shape)
}

/**
 * Fondo de la página larga: negro pleno con dos resplandores muy suaves del color
 * del bloque actual (arriba a la derecha y abajo a la izquierda). Es lo que el
 * vidrio tiene detrás para parecer vidrio y no un gris plano. El color cambia con
 * un fundido lento al pasar de un bloque a otro.
 */
@Composable
fun WizardAmbientBackground(accent: Color, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 900),
        label = "wizard-ambient-tint",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(WizardColors.background)
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.02f),
                radius = size.width * 1.15f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.02f, size.height * 0.98f),
                radius = size.width * 0.95f,
            ),
        )
    }
}
