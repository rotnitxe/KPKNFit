package com.example.kpkn.screens.workout.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos propios del paquete «En vivo · más» (`P.lx*` del lienzo, `src/scenes-lx.js`): mismos trazados en 24 × 24, mismo trazo
 * (1,75 salvo el de pausa, que lleva 3) y puntas redondas. Los que ya existen en `KpknLineIcons` (campana, ajustes, flecha) no se repiten.
 */
internal object FreeClockIcons {
    private fun icon(name: String, vararg paths: String, stroke: Float = 1.75f, filled: Boolean = false): ImageVector {
        val ink = SolidColor(Color.Black)
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            val nodes = PathParser().parsePathString(d).toNodes()
            if (filled) b.addPath(pathData = nodes, fill = ink)
            else b.addPath(pathData = nodes, stroke = ink, strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        return b.build()
    }

    /** Cronómetro (`lxStop`). */
    val Stopwatch: ImageVector by lazy {
        icon("Lx.Stopwatch", "M12 6a7.5 7.5 0 1 0 0 15a7.5 7.5 0 1 0 0-15z", "M12 13.5V9.5M9.5 3.5h5M12 3.5v2.5M18.4 7.1l1.3-1.3")
    }

    /** Reloj de arena (`lxGlass`). */
    val Hourglass: ImageVector by lazy {
        icon("Lx.Hourglass", "M7 3.5h10M7 20.5h10M8 3.5v3.2c0 2.3 4 3.9 4 5.3s-4 3-4 5.3v3.2M16 3.5v3.2c0 2.3-4 3.9-4 5.3s4 3 4 5.3v3.2")
    }

    val Expand: ImageVector by lazy { icon("Lx.Expand", "M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7") }
    val Fold: ImageVector by lazy { icon("Lx.Fold", "M20 10h-6V4M4 14h6v6M14 10l7-7M10 14l-7 7") }
    val Vibrate: ImageVector by lazy { icon("Lx.Vibrate", "M10.2 3.5h3.6a2.2 2.2 0 0 1 2.2 2.2v12.6a2.2 2.2 0 0 1-2.2 2.2h-3.6A2.2 2.2 0 0 1 8 18.3V5.7a2.2 2.2 0 0 1 2.2-2.2z", "M4 8v8M20 8v8") }
    val Sound: ImageVector by lazy { icon("Lx.Sound", "M4 9.5h4l5-4v13l-5-4H4z", "M16.5 9a4 4 0 0 1 0 6M19 6.5a7.5 7.5 0 0 1 0 11") }
    val Flag: ImageVector by lazy { icon("Lx.Flag", "M6 21V4M6 4.5h11l-2.5 4 2.5 4H6") }
    val Reset: ImageVector by lazy { icon("Lx.Reset", "M4 12a8 8 0 1 0 2.6-5.9M4 4.5v4.2h4.2") }
    val Note: ImageVector by lazy { icon("Lx.Note", "M6 3.5h8.5L18.5 8v12.5H6z", "M14 3.5V8h4.5M9 12.5h6M9 16h4") }
    val Image: ImageVector by lazy { icon("Lx.Image", "M6.5 5h11A3 3 0 0 1 20.5 8v8a3 3 0 0 1-3 3h-11A3 3 0 0 1 3.5 16V8a3 3 0 0 1 3-3z", "M10.6 10a1.6 1.6 0 1 1-3.2 0a1.6 1.6 0 0 1 3.2 0z", "M4 17l5-4.5 3.5 3 3-2.5 4.5 4") }
    val Download: ImageVector by lazy { icon("Lx.Download", "M12 4v12M7 11l5 5 5-5M5 20h14") }
    val Share: ImageVector by lazy { icon("Lx.Share", "M17.5 3.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 0 1 0-5z", "M6.5 9.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 0 1 0-5z", "M17.5 15.5a2.5 2.5 0 1 1 0 5a2.5 2.5 0 0 1 0-5z", "M8.7 10.8l6.6-4M8.7 13.2l6.6 4") }
    val Play: ImageVector by lazy { icon("Lx.Play", "M8 5.5v13l10.5-6.5z", filled = true) }
    val Pause: ImageVector by lazy { icon("Lx.Pause", "M8.5 5.5v13M15.5 5.5v13", stroke = 3f) }

    /** Borrar del teclado (`lxBack`). */
    val Backspace: ImageVector by lazy { icon("Lx.Backspace", "M9 5.5h10a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-6-6.5z", "M12.5 9.5l5 5M17.5 9.5l-5 5") }
}
