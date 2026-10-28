package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * «Compartir»: una flecha hacia arriba que sale de una bandeja, en trazo de 1,75 dp con extremos y uniones redondeados, sobre la
 * retícula de 24. KpknLineIcons no trae uno, así que vive aquí, junto a la cabecera que lo usa.
 */
internal val ProgramShareIcon: ImageVector = ImageVector.Builder(
    name = "KpknLine.ProgramShare",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    // Vástago y punta de la flecha, y la bandeja con sus dos esquinas de radio 2.
    for (d in listOf("M12 4v11", "M7.5 8.5L12 4l4.5 4.5", "M6 13v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-5")) {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.75f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()
