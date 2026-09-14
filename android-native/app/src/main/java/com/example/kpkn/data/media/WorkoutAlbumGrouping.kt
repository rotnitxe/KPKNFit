package com.example.kpkn.data.media

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WorkoutAlbum(
    val albumKey: String,
    val title: String,
    val dateLabel: String,
    val createdAtMs: Long,
    val mediaCount: Int,
    val prCount: Int,
    val coverThumbPath: String?,
    val coverIsVideo: Boolean,
    val items: List<WorkoutMedia>,
)

object WorkoutAlbumGrouping {
    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun albumKey(media: WorkoutMedia, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val date = dateLabel(media.createdAtMs, zoneId)
        val logId = media.workoutLogId?.trim().orEmpty()
        if (logId.isNotEmpty()) return "log:$logId"
        val sessionKey = media.sessionKey?.trim().orEmpty()
        if (sessionKey.isNotEmpty()) return "live:$sessionKey"
        val sessionId = media.sessionId?.trim().orEmpty()
        if (sessionId.isNotEmpty()) return "session:$sessionId:$date"
        return "day:$date"
    }

    fun group(
        media: List<WorkoutMedia>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<WorkoutAlbum> {
        if (media.isEmpty()) return emptyList()
        return media
            .groupBy { albumKey(it, zoneId) }
            .map { (key, items) ->
                val ordered = items.sortedByDescending { it.createdAtMs }
                val newest = ordered.first()
                val cover = ordered.firstNotNullOfOrNull { it.thumbPath?.takeIf(String::isNotBlank) }
                    ?: ordered.firstOrNull { it.kind == WorkoutMediaKind.PHOTO }?.filePath
                    ?: newest.thumbPath
                    ?: newest.filePath
                WorkoutAlbum(
                    albumKey = key,
                    title = newest.sessionName?.trim()?.takeIf { it.isNotEmpty() } ?: "Sesión",
                    dateLabel = dateLabel(newest.createdAtMs, zoneId),
                    createdAtMs = newest.createdAtMs,
                    mediaCount = ordered.size,
                    prCount = ordered.count { it.isPr },
                    coverThumbPath = cover,
                    coverIsVideo = newest.kind == WorkoutMediaKind.VIDEO &&
                        ordered.none { it.kind == WorkoutMediaKind.PHOTO && it.filePath == cover },
                    items = ordered,
                )
            }
            .sortedByDescending { it.createdAtMs }
    }

    fun filterMedia(
        media: List<WorkoutMedia>,
        prOnly: Boolean,
        exerciseQuery: String,
    ): List<WorkoutMedia> {
        val query = exerciseQuery.trim()
        return media.filter { item ->
            if (prOnly && !item.isPr) return@filter false
            if (query.isEmpty()) return@filter true
            val haystack = listOfNotNull(
                item.exerciseName,
                item.canonicalExerciseId,
                item.exerciseId,
            ).joinToString(" ").lowercase()
            haystack.contains(query.lowercase())
        }
    }

    fun formatDurationMs(durationMs: Long?): String? {
        val ms = durationMs ?: return null
        if (ms <= 0L) return null
        val totalSec = (ms / 1000L).toInt().coerceAtLeast(0)
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "%d:%02d".format(minutes, seconds)
    }

    fun dateLabel(createdAtMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(createdAtMs).atZone(zoneId).toLocalDate().format(DATE)

    fun workoutMediaSessionKey(programId: String, sessionId: String, startTimeMs: Long): String =
        "$programId::$sessionId::$startTimeMs"
}
