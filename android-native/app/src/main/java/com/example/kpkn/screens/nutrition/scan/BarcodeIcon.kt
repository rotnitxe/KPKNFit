package com.example.kpkn.screens.nutrition.scan

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Icono de código de barras con las reglas del set KPKN Line (retícula de 24, zona viva de 2 a 22, trazo de 1,75 con extremos
 * redondeados, sin relleno): cuatro esquinas de visor y las barras en medio. El color lo pone quien lo usa.
 */
object BarcodeIcon {
    val Barcode: ImageVector by lazy {
        val ink = SolidColor(Color.Black)
        val builder = ImageVector.Builder(
            name = "KpknLine.Barcode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        listOf(
            // Esquinas del visor.
            "M3.5 8V5.5a2 2 0 0 1 2-2H8",
            "M16 3.5h2.5a2 2 0 0 1 2 2V8",
            "M20.5 16v2.5a2 2 0 0 1-2 2H16",
            "M8 20.5H5.5a2 2 0 0 1-2-2V16",
            // Barras.
            "M8 8v8",
            "M11 8v8",
            "M14 8v8",
            "M16.5 8v8",
        ).forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = ink,
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.build()
    }
}
