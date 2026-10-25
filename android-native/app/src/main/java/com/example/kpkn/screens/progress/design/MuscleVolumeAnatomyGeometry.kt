package com.example.kpkn.screens.progress.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.MuscleArtPaths

/** Independent anatomy geometry for recorded muscle-volume counts. */
internal enum class MuscleVolumeBodyView { FRONT, BACK }
internal val MuscleVolumeBodyRegions = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps",
    "Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas",
    "Abdomen", "Trapecio", "Erectores Espinales", "Core",
)
internal const val MuscleVolumeBodyWidth = 220f
internal const val MuscleVolumeBodyHeight = 360f
private val BodyEdge = Color(0xFF363636)
private val BodyTop = Color(0xFF282828)
private val BodyBottom = Color(0xFF171717)
private val Ink = Color(0xFFF2EEE6)
private val ZoneTouchRadius = 24.dp

internal class BodyPaths(
    val silhouette: List<Path>,
    val front: Map<String, List<Path>>,
    val back: Map<String, List<Path>>,
    val frontDetails: List<Path>,
    val backDetails: List<Path>,
) {
    fun of(view: MuscleVolumeBodyView): Map<String, List<Path>> = if (view == MuscleVolumeBodyView.FRONT) front else back
}

internal fun buildBodyPaths(): BodyPaths {
    // Same proportional native vectors used by the reviewed HTML; no polygon smoothing/distortion.
    val native = MuscleArtPaths
    fun path(data: String, mirrored: Boolean = false): Path = PathParser().parsePathString(data).toPath().also {
        val transform = android.graphics.Matrix().apply {
            setScale(if (mirrored) -0.86f else 0.86f, 0.86f)
            postTranslate(MuscleVolumeBodyWidth / 2f, 6f)
        }
        it.asAndroidPath().transform(transform)
    }
    fun pair(data: String) = listOf(path(data), path(data, true))
    val arms = "M46 116 C49 113 54 114 56 120 C59 130 57 143 54 153 C53 158 50 164 47 165 C45 158 44 148 44 139 C43 129 44 121 46 116 Z"
    val rectus = "M2.5 123 C8 121 13 122 15 128 C17 139 15 153 13.5 165 C12 177 9.5 190 3 196 C1.5 179 2.2 162 2 148 C1.8 137 1.5 129 2.5 123 Z"
    val oblique = "M19 120 C25 123 30 133 30 148 C30 166 31 180 31 190 L17 204 C18 185 20 167 20 151 C20 139 20 129 19 120 Z"
    val front = linkedMapOf(
        "Pectorales" to listOf(path(native.CHEST_REGION)),
        "Deltoides" to listOf(path(native.SHOULDERS_REGION)),
        "Bíceps" to pair(arms), "Abdomen" to pair(rectus), "Core" to pair(oblique),
        "Cuádriceps" to listOf(path(native.QUADS_REGION)),
        "Pantorrillas" to pair("M14 316 C18 315 23 319 25 327 C27 336 25 346 22 354 C20 361 18 369 15 371 C13 360 12 349 12 339 C11 329 12 321 14 316 Z"),
    )
    val back = linkedMapOf(
        "Trapecio" to pair("M9 57 C16 63 30 68 43 77 C37 80 29 86 22 94 C13 101 6 110 2 120 L2 74 C4 67 7 62 9 57 Z"),
        "Dorsales" to listOf(path(native.BACK_REGION)),
        "Deltoides" to listOf(path(native.SHOULDERS_REGION)),
        "Tríceps" to pair(arms),
        "Erectores Espinales" to pair("M2 121 C5 121 7 133 8 150 L10 180 L6 198 L2 202 Z"),
        "Glúteos" to listOf(path(native.GLUTES_REGION)),
        "Isquiosurales" to listOf(path(native.HAMSTRINGS_REGION)),
        "Pantorrillas" to listOf(path(native.CALVES_REGION)),
    )
    val armsDetail = pair("M47 120 C49 131 49 145 48 154 M47 167 C50 170 54 170 58 167 M54 182 C56 194 58 209 59 223 M60 238 L61 250 M63 238 L64 249")
    return BodyPaths(
        listOf(path(native.OUTLINE + "Z"), path(native.HEAD)), front, back,
        (native.TORSO_FRONT_CONTEXT + native.LEG_FRONT_CONTEXT + native.CHEST_DETAIL + native.SHOULDERS_DETAIL + native.ABS_DETAIL + native.QUADS_DETAIL).map { path(it) } + armsDetail + pair("M3 138 C7 140 11 140 15 138 M3 155 C7 157 10 157 14 155 M3 173 C6 175 9 175 12 172"),
        (native.TORSO_BACK_CONTEXT + native.LEG_BACK_CONTEXT + native.BACK_DETAIL + native.SHOULDERS_DETAIL + native.GLUTES_DETAIL + native.HAMSTRINGS_DETAIL + native.CALVES_DETAIL).map { path(it) } + armsDetail,
    )
}

internal fun bodyPathContains(path: Path, x: Float, y: Float): Boolean {
    val bounds = path.getBounds()
    val region = android.graphics.Region()
    region.setPath(path.asAndroidPath(), android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 2, bounds.bottom.toInt() + 2))
    return region.contains(x.toInt(), y.toInt())
}

internal fun DrawScope.drawBodyShell(paths: BodyPaths, view: MuscleVolumeBodyView) {
    paths.silhouette.forEach { drawPath(it, BodyEdge, style = Stroke(width = 1f)) }
    val fill = Brush.verticalGradient(listOf(BodyTop, BodyBottom), startY = 0f, endY = MuscleVolumeBodyHeight)
    paths.silhouette.forEach { drawPath(it, brush = fill) }
    val mid = MuscleVolumeBodyWidth / 2f
    if (view == MuscleVolumeBodyView.FRONT) {
        drawLine(Color.White.copy(alpha = 0.07f), Offset(mid, 76f), Offset(mid, 176f), strokeWidth = 1f)
    } else {
        drawLine(Color.White.copy(alpha = 0.07f), Offset(mid, 66f), Offset(mid, 214f), strokeWidth = 1f)
    }
}

/** Trazos finos de detalle anatómico de [view], encima de las regiones; pide el mismo recorte a la silueta que [drawBodyShell]. */
internal fun DrawScope.drawBodyDetails(paths: BodyPaths, view: MuscleVolumeBodyView) {
    (if (view == MuscleVolumeBodyView.FRONT) paths.frontDetails else paths.backDetails).forEach {
        drawPath(it, Ink.copy(alpha = 0.24f), style = Stroke(width = 0.55f, cap = StrokeCap.Round))
    }
}

/** Tinta de los trazos de selección del cuerpo. */
internal val MuscleVolumeBodyInk: Color get() = Ink

/** Radio de toque de una región pequeña (medio objetivo táctil de 48 dp), en dp. */
internal val MuscleVolumeBodyTouchRadius: Dp get() = ZoneTouchRadius
