package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.NormalizedPoint
import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.PoseTrack
import com.example.kpkn.domain.biomechanics.TrajectoryMetrics
import com.example.kpkn.domain.biomechanics.TrajectoryReport
import com.example.kpkn.ui.theme.RingBlue
import com.example.kpkn.ui.theme.RingRed
import com.example.kpkn.ui.theme.RingYellow
import java.util.Locale
import kotlin.math.min

private val SKELETON_BONES: List<Pair<PoseJoint, PoseJoint>> = listOf(
    PoseJoint.LEFT_SHOULDER to PoseJoint.RIGHT_SHOULDER,
    PoseJoint.LEFT_SHOULDER to PoseJoint.LEFT_ELBOW,
    PoseJoint.LEFT_ELBOW to PoseJoint.LEFT_WRIST,
    PoseJoint.RIGHT_SHOULDER to PoseJoint.RIGHT_ELBOW,
    PoseJoint.RIGHT_ELBOW to PoseJoint.RIGHT_WRIST,
    PoseJoint.LEFT_SHOULDER to PoseJoint.LEFT_HIP,
    PoseJoint.RIGHT_SHOULDER to PoseJoint.RIGHT_HIP,
    PoseJoint.LEFT_HIP to PoseJoint.RIGHT_HIP,
    PoseJoint.LEFT_HIP to PoseJoint.LEFT_KNEE,
    PoseJoint.LEFT_KNEE to PoseJoint.LEFT_ANKLE,
    PoseJoint.RIGHT_HIP to PoseJoint.RIGHT_KNEE,
    PoseJoint.RIGHT_KNEE to PoseJoint.RIGHT_ANKLE,
    PoseJoint.LEFT_SHOULDER to PoseJoint.NOSE,
    PoseJoint.RIGHT_SHOULDER to PoseJoint.NOSE,
)

@Composable
fun PoseTrajectoryOverlay(
    track: PoseTrack,
    positionMs: Long,
    modifier: Modifier = Modifier,
    report: TrajectoryReport? = null,
) {
    val metrics = report ?: remember(track) { TrajectoryMetrics.compute(track) }
    val frame = remember(track, positionMs) { TrajectoryMetrics.frameAt(track, positionMs) }
    val trail = remember(track, positionMs) { TrajectoryMetrics.trailUntil(track, positionMs) }
    val videoW = track.videoWidth?.toFloat()?.takeIf { it > 0f }
    val videoH = track.videoHeight?.toFloat()?.takeIf { it > 0f }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val (origin, drawn) = fitOriginAndSize(size.width, size.height, videoW, videoH)
            fun map(point: NormalizedPoint): Offset = Offset(
                origin.x + point.x * drawn.x,
                origin.y + point.y * drawn.y,
            )
            val guideX = origin.x + metrics.guideX * drawn.x
            drawLine(
                color = RingYellow.copy(alpha = 0.55f),
                start = Offset(guideX, origin.y),
                end = Offset(guideX, origin.y + drawn.y),
                strokeWidth = 3f,
            )
            if (trail.size >= 2) {
                val path = Path().apply {
                    val first = map(trail.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until trail.size) {
                        val p = map(trail[i])
                        lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    path = path,
                    color = RingBlue.copy(alpha = 0.9f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round),
                )
            }
            val points = frame?.points.orEmpty()
            for ((a, b) in SKELETON_BONES) {
                val pa = points[a] ?: continue
                val pb = points[b] ?: continue
                if (pa.inFrameLikelihood < TrajectoryMetrics.MIN_LANDMARK_LIKELIHOOD) continue
                if (pb.inFrameLikelihood < TrajectoryMetrics.MIN_LANDMARK_LIKELIHOOD) continue
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = map(pa),
                    end = map(pb),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            for (point in points.values) {
                if (point.inFrameLikelihood < TrajectoryMetrics.MIN_LANDMARK_LIKELIHOOD) continue
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = map(point),
                )
            }
            trail.lastOrNull()?.let { tip ->
                drawCircle(color = RingRed, radius = 8f, center = map(tip))
            }
        }
        Text(
            text = deviationLabel(metrics),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

internal fun fitOriginAndSize(
    viewW: Float,
    viewH: Float,
    videoW: Float?,
    videoH: Float?,
): Pair<Offset, Offset> {
    if (videoW == null || videoH == null || videoW <= 0f || videoH <= 0f) {
        return Offset.Zero to Offset(viewW, viewH)
    }
    val scale = min(viewW / videoW, viewH / videoH)
    val dw = videoW * scale
    val dh = videoH * scale
    val left = (viewW - dw) / 2f
    val top = (viewH - dh) / 2f
    return Offset(left, top) to Offset(dw, dh)
}

internal fun deviationLabel(report: TrajectoryReport): String {
    val cm = report.horizontalDeviationCm
    return if (cm != null) {
        "Δh ${"%.1f".format(Locale.US, cm)} cm"
    } else {
        "Δh ${"%.2f".format(Locale.US, report.horizontalDeviationNormalized)}"
    }
}
