package com.example.kpkn.data.media

import com.example.kpkn.domain.biomechanics.PoseFrame
import com.example.kpkn.domain.biomechanics.PoseTrack
import com.example.kpkn.domain.biomechanics.TrajectoryFamily
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Pure offline pose-trajectory pipeline: ~10 fps timestamps, sidecar `<id>.pose.json`.
 * Frame decoding and ML Kit live in [PoseTrajectoryAnalyzer] (Android).
 */
object PoseTrajectoryAnalysis {
    const val TARGET_FPS = 10
    const val SIDECAR_SUFFIX = ".pose.json"
    const val MAX_SAMPLES = 1_200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun sampleTimestampsMs(durationMs: Long, fps: Int = TARGET_FPS): List<Long> {
        val clippedDuration = durationMs.coerceAtLeast(0L)
        if (clippedDuration == 0L) return listOf(0L)
        val safeFps = fps.coerceIn(1, 30)
        val step = (1000L / safeFps).coerceAtLeast(1L)
        val times = ArrayList<Long>(minOf(((clippedDuration / step) + 2).toInt(), MAX_SAMPLES))
        var t = 0L
        while (t <= clippedDuration && times.size < MAX_SAMPLES) {
            times += t
            t += step
        }
        if (times.last() != clippedDuration && times.size < MAX_SAMPLES) {
            times += clippedDuration
        }
        return times
    }

    fun sidecarFile(mediaFile: File): File {
        val parent = mediaFile.parentFile
        val id = mediaFile.nameWithoutExtension
        return if (parent != null) File(parent, "$id$SIDECAR_SUFFIX") else File("$id$SIDECAR_SUFFIX")
    }

    fun sidecarFile(directory: File, mediaId: String): File =
        File(directory, "$mediaId$SIDECAR_SUFFIX")

    fun buildTrack(
        mediaId: String,
        family: TrajectoryFamily,
        frames: List<PoseFrame>,
        videoWidth: Int? = null,
        videoHeight: Int? = null,
        sampleFps: Int = TARGET_FPS,
    ): PoseTrack = PoseTrack(
        mediaId = mediaId,
        family = family,
        sampleFps = sampleFps,
        videoWidth = videoWidth,
        videoHeight = videoHeight,
        frames = frames.sortedBy { it.timestampMs },
        version = 1,
    )

    fun encode(track: PoseTrack): String = json.encodeToString(PoseTrack.serializer(), track)

    fun decode(text: String): PoseTrack? =
        runCatching { json.decodeFromString(PoseTrack.serializer(), text) }.getOrNull()

    fun writeSidecar(file: File, track: PoseTrack) {
        file.parentFile?.mkdirs()
        file.writeText(encode(track))
    }

    fun readSidecar(file: File): PoseTrack? {
        if (!file.isFile || file.length() <= 0L) return null
        return decode(file.readText())
    }
}
