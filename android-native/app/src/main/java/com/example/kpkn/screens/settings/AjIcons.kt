package com.example.kpkn.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos de línea de Ajustes (paquete `aj`): los trazados del lienzo (`scenes-aj.js` y la hoja común `P`) sobre la retícula de 24,
 * trazo de 1,75, extremos y uniones redondeados. El color lo pone quien los usa.
 */
internal object AjIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0z"
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    private fun icono(nombre: String, vararg trazados: String): ImageVector {
        val tinta = SolidColor(Color.Black)
        val builder = ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f)
        for (d in trazados) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = tinta,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Ruler by lazy { icono("Aj.Ruler", rect(3f, 8f, 18f, 8f, 2f), "M7 8v3M11 8v4M15 8v3M19 8v3") }
    val Bell by lazy { icono("Aj.Bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5h4") }
    val Shield by lazy { icono("Aj.Shield", "M12 3.5l7 2.5v5.5c0 4.2-2.9 7.6-7 9-4.1-1.4-7-4.8-7-9V6z", "M9 12l2.2 2.2L15.5 10") }
    val Heart by lazy { icono("Aj.Heart", "M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0 1 12 7.5 4.3 4.3 0 0 1 19.5 10c0 5.5-7.5 10-7.5 10z") }
    val Folder by lazy { icono("Aj.Folder", "M3.5 7a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z") }
    val Lifebuoy by lazy {
        icono("Aj.Lifebuoy", circle(12f, 12f, 8.5f), circle(12f, 12f, 3.4f), "M6 6l3.6 3.6M18 6l-3.6 3.6M6 18l3.6-3.6M18 18l-3.6-3.6")
    }
    val Download by lazy { icono("Aj.Download", "M12 4v11M7.5 10.5L12 15l4.5-4.5M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3") }
    val Upload by lazy { icono("Aj.Upload", "M12 15V4M7.5 8.5L12 4l4.5 4.5M5 15v3a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-3") }
    val Table by lazy { icono("Aj.Table", rect(4f, 5f, 16f, 14f, 2.5f), "M4 10h16M4 14.5h16M10 5v14") }
    val Image by lazy { icono("Aj.Image", rect(4f, 5f, 16f, 14f, 3f), circle(9f, 10f, 1.6f), "M5 17l4.5-4.5 3 3 2.5-2.5 4 4") }
    val Pulse by lazy { icono("Aj.Pulse", "M3 12h4l2-5 4 10 2-5h6") }
    val Warn by lazy { icono("Aj.Warn", "M12 4l9 15.5H3z", "M12 10v4M12 17v.01") }
    val Ok by lazy { icono("Aj.Ok", circle(12f, 12f, 9f), "M8 12.5l2.7 2.7L16 10") }
    val Moon by lazy { icono("Aj.Moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z") }
    val Fork by lazy { icono("Aj.Fork", "M7 3.5v7a2 2 0 0 0 2 2v8M11 3.5v7a2 2 0 0 1-2 2M17 3.5c-2 1.5-2.5 4-2.5 6.5H17v10.5") }
    val Gym by lazy { icono("Aj.Gym", "M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12") }
    val XMark by lazy { icono("Aj.XMark", "M7 7l10 10M17 7L7 17") }
    val Db by lazy {
        icono("Aj.Db", ellipse(12f, 6.5f, 7f, 2.8f), "M5 6.5v11c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8v-11M5 12c0 1.5 3.1 2.8 7 2.8s7-1.3 7-2.8")
    }
    val Bluetooth by lazy { icono("Aj.Bluetooth", "M7 7.5l10 9-5 4v-17l5 4-10 9") }
    val Clock by lazy { icono("Aj.Clock", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2") }
    val Mic by lazy { icono("Aj.Mic", rect(9f, 3.5f, 6f, 11f, 3f), "M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v2.5") }
    val Pin by lazy { icono("Aj.Pin", "M12 21s6-5.6 6-10.5a6 6 0 0 0-12 0C6 15.4 12 21 12 21z", circle(12f, 10.5f, 2.2f)) }
    val Camera by lazy { icono("Aj.Camera", rect(3.5f, 7f, 17f, 12.5f, 3.5f), "M9 7l1.5-2.5h3L15 7", circle(12f, 13f, 3.5f)) }
    val Check by lazy { icono("Aj.Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Close by lazy { icono("Aj.Close", "M6 6l12 12M18 6L6 18") }
    val Up by lazy { icono("Aj.Up", "M5 15l7-7 7 7") }
    val Down by lazy { icono("Aj.Down", "M5 9l7 7 7-7") }
    val Forward by lazy { icono("Aj.Forward", "M9 5l7 7-7 7") }
    val History by lazy { icono("Aj.History", "M4 12a8 8 0 1 0 2.5-5.8", "M4 4.5v4h4M12 8v4l3 2") }
    val Repeat by lazy { icono("Aj.Repeat", "M5 10V8a3 3 0 0 1 3-3h11l-3-3M19 14v2a3 3 0 0 1-3 3H5l3 3") }
    val Book by lazy { icono("Aj.Book", "M5 4.5h10a4 4 0 0 1 4 4V20H9a4 4 0 0 1-4-4z", "M5 16a4 4 0 0 1 4-4h10") }
    val Trash by lazy { icono("Aj.Trash", "M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13") }
}
