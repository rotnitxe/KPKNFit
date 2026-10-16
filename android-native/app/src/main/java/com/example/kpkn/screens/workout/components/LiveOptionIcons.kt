package com.example.kpkn.screens.workout.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Los pocos iconos de línea que las hojas de opciones de la sesión en vivo (LV11) necesitan y el set `KpknLineIcons` no tiene. Misma
 * regla que el set: retícula de 24 dp, trazo de 1,75 dp con extremos y uniones redondeados, y el color lo pone quien los usa.
 */
internal object LiveOptionIcons {

    /** Flecha con cola: saltar (la serie o el ejercicio). */
    val Skip: ImageVector by lazy { line("LiveOption.Skip", "M5 12h14", "M13 6l6 6-6 6") }

    /** Rayo: modo ultrarrápido. */
    val Bolt: ImageVector by lazy { line("LiveOption.Bolt", "M13 2.75 5 13.5h6.25l-.75 7.75 8-10.75h-6.25z") }

    /** Globo de texto: la nota del ejercicio. */
    val Note: ImageVector by lazy { line("LiveOption.Note", "M4.75 4.75h14.5v11.5h-9.5l-5 4z") }

    /** Candado: lo hecho y lo que está en curso queda fijo. */
    val Lock: ImageVector by lazy {
        line("LiveOption.Lock", "M6.5 11h11a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V12a1 1 0 0 1 1-1z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    }

    /** Dos flechas verticales opuestas: intercambiar el orden de los miembros de una superserie. */
    val Swap: ImageVector by lazy { line("LiveOption.Swap", "M8 5v14", "M4.75 8.25 8 5l3.25 3.25", "M16 19V5", "M12.75 15.75 16 19l3.25-3.25") }

    private fun line(name: String, vararg paths: String): ImageVector {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = ink,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
