package com.example.kpkn.screens.competitions.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Los iconos de línea del lienzo Registros v4 (`app.js` y `base.js`: misma retícula 24 y trazo 1,75 que KpknLineIcons) que las
 * pantallas de competencias necesitan. Los que ya existen en KpknLineIcons se toman de allí.
 */
internal object CompetitionIcons {
    private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"
    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String = "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0z"

    val Back: ImageVector by lazy { icon("Comp.Back", "M15 5l-7 7 7 7") }
    val Fwd: ImageVector by lazy { icon("Comp.Fwd", "M9 5l7 7-7 7") }
    val Up: ImageVector by lazy { icon("Comp.Up", "M5 15l7-7 7 7") }
    val More: ImageVector get() = KpknLineIcons.More
    val Check: ImageVector by lazy { icon("Comp.Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Edit: ImageVector by lazy { icon("Comp.Edit", "M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4") }
    val Plus: ImageVector by lazy { icon("Comp.Plus", "M12 5v14M5 12h14") }
    val Swap: ImageVector by lazy { icon("Comp.Swap", "M7 7h11l-3-3M17 17H6l3 3") }
    val Flag: ImageVector by lazy { icon("Comp.Flag", "M6 21V4M6 4.5h11l-2.5 4 2.5 4H6") }
    val TrendUp: ImageVector by lazy { icon("Comp.TrendUp", "M4 17l6-6 4 4 6-7", "M15 8h5v5") }
    val Grip: ImageVector by lazy { icon("Comp.Grip", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01", width = 2.6f) }
    val Trash: ImageVector by lazy { icon("Comp.Trash", "M5 7h14M9 7V5h6v2M7 7l1 13h8l1-13") }
    val Repeat: ImageVector by lazy { icon("Comp.Repeat", "M5 10V8a3 3 0 013-3h11l-3-3M19 14v2a3 3 0 01-3 3H5l3 3") }
    val Upload: ImageVector by lazy { icon("Comp.Upload", "M12 15V4M7.5 8.5L12 4l4.5 4.5M5 15v3a2 2 0 002 2h10a2 2 0 002-2v-3") }
    val Spark: ImageVector by lazy { icon("Comp.Spark", "M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z") }
    val Scale: ImageVector by lazy { icon("Comp.Scale", "M8 4h8a4 4 0 014 4v8a4 4 0 01-4 4H8a4 4 0 01-4-4V8a4 4 0 014-4z", "M9 9a4 4 0 016 0l-3 3") }
    val Tower: ImageVector by lazy { icon("Comp.Tower", ellipse(12f, 7f, 4f, 1.6f), ellipse(12f, 12f, 6f, 2.2f), ellipse(12f, 17.5f, 8f, 2.6f)) }
    val Target: ImageVector by lazy { icon("Comp.Target", circle(12f, 12f, 8.5f), circle(12f, 12f, 4.5f), circle(12f, 12f, 0.8f)) }
    val Share: ImageVector by lazy { icon("Comp.Share", "M12 4v11M7.5 8.5L12 4l4.5 4.5", "M6 13v5a2 2 0 002 2h8a2 2 0 002-2v-5") }
    val Eye: ImageVector by lazy { icon("Comp.Eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f)) }
    val Dumb: ImageVector by lazy { icon("Comp.Dumb", "M3 10v4M6 8v8M18 8v8M21 10v4M6 12h12") }
    val Bolt: ImageVector by lazy { icon("Comp.Bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z") }
    val Heart: ImageVector by lazy { icon("Comp.Heart", "M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0112 7.5 4.3 4.3 0 0119.5 10c0 5.5-7.5 10-7.5 10z") }
    val Close: ImageVector by lazy { icon("Comp.Close", "M6 6l12 12M18 6L6 18") }
    val Trophy: ImageVector by lazy {
        icon("Comp.Trophy", "M8 4h8v5a4 4 0 01-8 0z", "M8 6H5a2.5 2.5 0 003 4M16 6h3a2.5 2.5 0 01-3 4M12 13v4M8.5 20h7M10 17h4")
    }
    val Camera: ImageVector by lazy {
        icon("Comp.Camera", "M7 7h10a3.5 3.5 0 013.5 3.5v5.5a3.5 3.5 0 01-3.5 3.5H7a3.5 3.5 0 01-3.5-3.5v-5.5A3.5 3.5 0 017 7z",
            "M9 7l1.5-2.5h3L15 7", circle(12f, 13f, 3.5f))
    }
    val Drop: ImageVector by lazy { icon("Comp.Drop", "M12 4s5.5 6.1 5.5 9.9a5.5 5.5 0 01-11 0C6.5 10.1 12 4 12 4z") }
    val Confetti: ImageVector by lazy { icon("Comp.Confetti", "M5 20l3.5-10.5L16 17z", "M14 4v2M19 9h2M17.5 6.5l1.5-1.5M12 8.5l1-1") }
    val Smile: ImageVector by lazy { icon("Comp.Smile", circle(12f, 12f, 8.5f), "M8.5 14a4.5 4.5 0 007 0M9 9.5v.01M15 9.5v.01") }
    val Meh: ImageVector by lazy { icon("Comp.Meh", circle(12f, 12f, 8.5f), "M8.5 14.5h7M9 9.5v.01M15 9.5v.01") }
    val Sad: ImageVector by lazy { icon("Comp.Sad", circle(12f, 12f, 8.5f), "M8.5 15.5a4.5 4.5 0 017 0M9 9.5v.01M15 9.5v.01") }
    val Wow: ImageVector by lazy { icon("Comp.Wow", circle(12f, 12f, 8.5f), circle(12f, 15f, 1.8f), "M8.5 9.5l1.5 1M15.5 9.5l-1.5 1") }
    val Fire: ImageVector by lazy {
        icon("Comp.Fire", "M12 20.5a6 6 0 006-6c0-4-3.5-5.5-3.5-10C11 6 8 8.5 8 12c-1.5-.5-2-2-2-2-.5 1.3-.5 2.4-.5 3.7a6.5 6.5 0 006.5 6.8z")
    }
    val Pin: ImageVector by lazy { icon("Comp.Pin", "M12 21s6-5.6 6-10.5a6 6 0 00-12 0C6 15.4 12 21 12 21z", circle(12f, 10.5f, 2.2f)) }
    val Users: ImageVector by lazy {
        icon("Comp.Users", circle(9f, 9f, 3.2f), "M3.5 19a5.5 5.5 0 0111 0M16 6.2a3 3 0 010 5.6M18 19a5 5 0 00-2.5-4.3")
    }

    /** El icono de una clave del lienzo (`ic('dumb')`…), para las disciplinas y las caras. */
    fun byKey(key: String): ImageVector = when (key) {
        "dumb" -> Dumb
        "bolt" -> Bolt
        "tower" -> Tower
        "flag" -> Flag
        "grip" -> Grip
        "eye" -> Eye
        "heart" -> Heart
        "pin" -> Pin
        "repeat" -> Repeat
        "drop" -> Drop
        "swap" -> Swap
        "target" -> Target
        "fire" -> Fire
        "users" -> Users
        "up" -> Up
        "plus" -> Plus
        "trophy" -> Trophy
        "sad" -> Sad
        "meh" -> Meh
        "smile" -> Smile
        "wow" -> Wow
        else -> Trophy
    }

    private fun icon(name: String, vararg paths: String, width: Float = 1.75f): ImageVector {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        paths.forEach { d ->
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
