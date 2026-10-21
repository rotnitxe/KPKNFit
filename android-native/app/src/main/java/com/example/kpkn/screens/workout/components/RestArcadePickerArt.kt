package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.min

private val PickerInk = Color(0xFFF2EEE6)
private val PickerPeach = Color(0xFFF49A6E)
private val PickerBlue = Color(0xFF8FB2FF)

private data class PickerArtPath(
    val path: Path, val color: Color, val strokeWidth: Float = 0f,
    val cap: StrokeCap = StrokeCap.Butt, val alpha: Float = 1f,
)

/** Exact paths/viewBox from the approved games.js tileArt; code-native, with no external image. */
@Composable
internal fun RestArcadePickerArt(mode: RestArcadeMode, modifier: Modifier = Modifier) {
    val paths = remember(mode) {
        fun art(data: String, color: Color, width: Float = 0f, cap: StrokeCap = StrokeCap.Butt, alpha: Float = 1f) =
            PickerArtPath(PathParser().parsePathString(data).toPath(), color, width, cap, alpha)
        when (mode) {
            RestArcadeMode.RUNNER -> listOf(
                art("M8 57H74", PickerInk, 1f, alpha = .25f),
                art("M24 27V40L15 53M24 40L34 51M24 32L34 36", PickerInk, 2f, StrokeCap.Round),
                art("M52 42v12M70 42v12", PickerPeach, 7f, StrokeCap.Round),
                art("M54 48H68", PickerInk, 3f),
            )
            RestArcadeMode.FLAPPY -> listOf(
                art("M15 0V22M15 49V70M66 0V32M66 59V70", PickerPeach, 10f),
                art("M27 34Q34 24 45 34L50 39L43 42H31Z", PickerBlue),
                art("M34 36L30 48L41 40", PickerInk),
            )
            RestArcadeMode.SHOOTER -> listOf(
                art("M40 20L45 39L61 51L43 48L40 59L37 48L19 51L35 39Z", PickerInk),
                art("M18 3L22 13L17 10L12 13Z M66 3L70 13L65 10L60 13Z", PickerPeach),
                art("M39 13V6M23 29V24M57 30V25", PickerBlue, 2f),
            )
        }
    }
    Canvas(modifier.size(width = 76.dp, height = 70.dp)) {
        // SVG xMidYMid meet: preserve proportions in its 76 x 70 CSS box.
        val artScale = min(size.width / 80f, size.height / 72f)
        withTransform({
            translate((size.width - 80f * artScale) / 2f, (size.height - 72f * artScale) / 2f)
            scale(artScale, artScale, pivot = Offset.Zero)
        }) {
            paths.forEach { part ->
                if (part.strokeWidth > 0f) drawPath(part.path, part.color, alpha = part.alpha,
                    style = Stroke(width = part.strokeWidth, cap = part.cap))
                else drawPath(part.path, part.color, alpha = part.alpha)
            }
            if (mode == RestArcadeMode.RUNNER) drawCircle(PickerInk, radius = 5f, center = Offset(24f, 22f), style = Stroke(width = 2f))
        }
    }
}
