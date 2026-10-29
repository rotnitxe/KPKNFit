package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Iconos de línea del lienzo «Registros v4» (`base.js` y `app.js`) que KpknLineIcons no trae: misma retícula de 24 y trazo de 1,75 con
 * extremos redondeados. Los de los alimentos (huevo, pan, aguacate, taza, gota, sartén, crudo) salen en las tarjetas y en el plato.
 */
internal object LoggerIcons {
    val Egg: ImageVector by lazy { line("Egg", "M12 3.5c3.6 0 6 5.4 6 9.3a6 6 0 01-12 0c0-3.9 2.4-9.3 6-9.3z", "M14.4 13.5a2.4 2.4 0 1 1-4.8 0a2.4 2.4 0 1 1 4.8 0z") }
    val Bread: ImageVector by lazy { line("Bread", "M5 11a4 4 0 012-7.2h10A4 4 0 0119 11v8.5H5z", "M9 11v3M15 11v3") }
    val Avocado: ImageVector by lazy { line("Avocado", "M12 3.5c3 0 5.5 6 5.5 10a5.5 5.5 0 01-11 0c0-4 2.5-10 5.5-10z", "M14.6 14.5a2.6 2.6 0 1 1-5.2 0a2.6 2.6 0 1 1 5.2 0z") }
    val Cup: ImageVector by lazy { line("Cup", "M5 9h11v5a5 5 0 01-5 5h-1a5 5 0 01-5-5z", "M16 10.5h1.5a2.5 2.5 0 010 5H16M8 3.5c0 1.5 1 1.5 1 3M11.5 3.5c0 1.5 1 1.5 1 3") }
    val Drop: ImageVector by lazy { line("Drop", "M12 4s5.5 6.1 5.5 9.9a5.5 5.5 0 01-11 0C6.5 10.1 12 4 12 4z") }
    val Pan: ImageVector by lazy { line("Pan", "M16.5 13a6.5 6.5 0 1 1-13 0a6.5 6.5 0 1 1 13 0z", "M16.5 13h5") }
    val Raw: ImageVector by lazy { line("Raw", "M6 6l12 12M6 18c0-6.6 5.4-12 12-12") }
    val Bowl: ImageVector by lazy { line("Bowl", "M4 12h16a8 8 0 01-16 0z", "M12 12c0-4 2-6 5-7-1 3-2 5-5 7z") }
    val Question: ImageVector by lazy { line("Question", "M20.5 12a8.5 8.5 0 1 1-17 0a8.5 8.5 0 1 1 17 0z", "M9.6 9.5a2.5 2.5 0 114 2c-1 .6-1.6 1.1-1.6 2.3M12 16.5v.01") }
    val Ruler: ImageVector by lazy { line("Ruler", "M5 8h14a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2z", "M7 8v3M11 8v4M15 8v3M19 8v3") }
    val Copy: ImageVector by lazy { line("Copy", "M11 8h6a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-6a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3z", "M16 8V6a2 2 0 00-2-2H6a2 2 0 00-2 2v8a2 2 0 002 2h2") }
    val Bolt: ImageVector by lazy { line("Bolt", "M13 3L5 13.5h6L10 21l8-10.5h-6z") }
    val Spark: ImageVector by lazy { line("Spark", "M12 3.5l1.8 5.2 5.2 1.8-5.2 1.8L12 17.5l-1.8-5.2L5 10.5l5.2-1.8z") }
    val Barcode: ImageVector by lazy { line("Barcode", "M4 6v12M7 6v12M10.5 6v12M13 6v12M16.5 6v12M20 6v12") }
    val Star: ImageVector by lazy { line("Star", STAR) }
    /** La estrella llena de un favorito (`starOn`). */
    val StarFilled: ImageVector by lazy { filled("StarOn", STAR) }

    private const val STAR = "M12 4l2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z"

    private fun line(name: String, vararg paths: String): ImageVector = build(name, paths.toList(), fill = false)
    private fun filled(name: String, vararg paths: String): ImageVector = build(name, paths.toList(), fill = true)

    private fun build(name: String, paths: List<String>, fill: Boolean): ImageVector = ImageVector.Builder(
        name = "KpknLine.Logger$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        for (d in paths) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** El icono de línea de un alimento, por su familia o su nombre: huevo, pan, aguacate, bebida caliente, sartén o bol. */
internal fun loggerFoodIcon(name: String, family: String? = null): ImageVector {
    val text = (family.orEmpty() + " " + name).lowercase()
    return when {
        "huevo" in text || "tortilla" in text -> LoggerIcons.Egg
        "pan" in text.split(' ', '_') || "tostad" in text || "marraqueta" in text || "hallulla" in text || "galleta" in text ||
            "biscote" in text || "sandwich" in text || "sándwich" in text -> LoggerIcons.Bread
        "aguacate" in text || "palta" in text || "fruta" in text || "manzana" in text || "plátano" in text || "platano" in text ||
            "ensalada" in text || "verdura" in text -> LoggerIcons.Avocado
        "café" in text || "cafe" in text || "té" in text.split(' ') || "leche" in text || "yogur" in text || "batido" in text ||
            "jugo" in text || "zumo" in text || "bebida" in text -> LoggerIcons.Cup
        "aceite" in text -> LoggerIcons.Drop
        "pollo" in text || "carne" in text || "cerdo" in text || "lomo" in text || "pescado" in text || "chorizo" in text ||
            "salmón" in text || "atún" in text || "pechuga" in text -> LoggerIcons.Pan
        else -> LoggerIcons.Bowl
    }
}
