package com.example.kpkn.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.kpkn.domain.biomechanics.NormalizedPoint
import com.example.kpkn.domain.biomechanics.PoseFrame
import com.example.kpkn.domain.biomechanics.PoseJoint
import com.example.kpkn.domain.biomechanics.PoseTrack
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Offline pose analysis: decode a recorded video at ~10 fps, run ML Kit Pose (base),
 * write landmarks to `<id>.pose.json`. Never used as a live camera overlay.
 */
class PoseTrajectoryAnalyzer(
    private val detectorFactory: () -> PoseDetector = {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    },
) {
    fun analyzeVideo(
        videoFile: File,
        mediaId: String,
        family: TrajectoryFamily,
        sidecarFile: File,
    ): PoseTrack? {
        if (!videoFile.isFile || videoFile.length() <= 0L) return null
        val retriever = MediaMetadataRetriever()
        val detector = detectorFactory()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val srcWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val srcHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            val timestamps = PoseTrajectoryAnalysis.sampleTimestampsMs(durationMs)
            val frames = ArrayList<PoseFrame>(timestamps.size)
            for (tMs in timestamps) {
                val bitmap = frameAt(retriever, tMs) ?: continue
                val scaled = scaleForPose(bitmap)
                if (scaled !== bitmap) bitmap.recycle()
                try {
                    val image = InputImage.fromBitmap(scaled, 0)
                    val pose = Tasks.await(detector.process(image), 5, TimeUnit.SECONDS)
                    val points = mapLandmarks(pose, scaled.width, scaled.height)
                    if (points.isNotEmpty()) {
                        frames += PoseFrame(timestampMs = tMs, points = points)
                    }
                } catch (_: Exception) {
                    // Skip undecodable / detector failures; keep the rest of the track.
                } finally {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            }
            if (frames.isEmpty()) return null
            val track = PoseTrajectoryAnalysis.buildTrack(
                mediaId = mediaId,
                family = family,
                frames = frames,
                videoWidth = srcWidth,
                videoHeight = srcHeight,
            )
            PoseTrajectoryAnalysis.writeSidecar(sidecarFile, track)
            return track
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { detector.close() }
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val MAX_EDGE_PX = 480

        internal fun mapLandmarks(pose: Pose, width: Int, height: Int): Map<PoseJoint, NormalizedPoint> {
            val w = width.coerceAtLeast(1).toFloat()
            val h = height.coerceAtLeast(1).toFloat()
            val out = HashMap<PoseJoint, NormalizedPoint>(JOINT_TO_MLKIT.size)
            for ((joint, type) in JOINT_TO_MLKIT) {
                val landmark = pose.getPoseLandmark(type) ?: continue
                out[joint] = NormalizedPoint(
                    x = landmark.position.x / w,
                    y = landmark.position.y / h,
                    inFrameLikelihood = landmark.inFrameLikelihood,
                )
            }
            return out
        }

        internal fun scaleForPose(src: Bitmap, maxEdge: Int = MAX_EDGE_PX): Bitmap {
            val longest = max(src.width, src.height)
            if (longest <= maxEdge) return src
            val scale = maxEdge.toFloat() / longest
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, w, h, true)
        }

        private fun frameAt(retriever: MediaMetadataRetriever, timestampMs: Long): Bitmap? {
            val timeUs = timestampMs * 1_000L
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    MAX_EDGE_PX,
                    MAX_EDGE_PX,
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }
        }

        private val JOINT_TO_MLKIT: Map<PoseJoint, Int> = mapOf(
            PoseJoint.NOSE to PoseLandmark.NOSE,
            PoseJoint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER,
            PoseJoint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseJoint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW,
            PoseJoint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
            PoseJoint.LEFT_WRIST to PoseLandmark.LEFT_WRIST,
            PoseJoint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
            PoseJoint.LEFT_HIP to PoseLandmark.LEFT_HIP,
            PoseJoint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
            PoseJoint.LEFT_KNEE to PoseLandmark.LEFT_KNEE,
            PoseJoint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
            PoseJoint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE,
            PoseJoint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
        )
    }
}
