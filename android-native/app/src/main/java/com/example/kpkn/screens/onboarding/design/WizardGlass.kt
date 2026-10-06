package com.example.kpkn.screens.onboarding.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Cristal de la página larga, a propósito discreto.
 *
 * La página es una sola superficie negra continua: las secciones no son tarjetas ni láminas, no hay
 * resplandores de color ni brillos en las esquinas. El vidrio existe únicamente donde algo se queda
 * quieto mientras el contenido pasa por debajo (la cabecera y el botón de confirmar), y ahí es
 * desenfoque real de lo que tiene detrás con un tinte blanco tenue y un filete uniforme.
 */

/** Estilo de `hazeEffect` para cabecera y botón: desenfoque marcado y fondo oscuro, para que lo que pasa por debajo no compita con el texto. */
internal fun wizardHazeStyle(): HazeStyle = HazeStyle(
    blurRadius = 28.dp,
    tint = HazeTint(Color.White.copy(alpha = 0.05f)),
    backgroundColor = Color.Black.copy(alpha = 0.6f),
    noiseFactor = 0f,
)
