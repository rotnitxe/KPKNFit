package com.example.kpkn.screens.workout.components

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Stroke paths copied from the approved Main SVG, not substituted by filled system icons. */
internal object LiveCardReferenceIcons {
    private fun line(name: String, paths: List<String>): ImageVector {
        val vector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { data -> vector.addPath(PathParser().parsePathString(data).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
        return vector.build()
    }
    val Advanced by lazy { line("LiveCard.Advanced", listOf(
        "M4 6h10", "M18 6h2", "M18 6a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        "M4 18h4", "M12 18h8", "M12 18a2 2 0 1 1-4 0a2 2 0 1 1 4 0", "M4 12h16")) }
    val Camera by lazy { line("LiveCard.Camera", listOf(
        "M6 7h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v-7a3 3 0 0 1 3-3z",
        "M15.5 13.5a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0", "M8 7l1.5-3h5L16 7")) }
}
