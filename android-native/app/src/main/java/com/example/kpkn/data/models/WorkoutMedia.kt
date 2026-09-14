package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutMediaKind {
    PHOTO,
    VIDEO,
}

/** Unified workout photo/video record (Room `workout_media`, v26). */
@Serializable
data class WorkoutMedia(
    val id: String,
    val kind: WorkoutMediaKind,
    val filePath: String,
    val thumbPath: String? = null,
    val createdAtMs: Long,
    val sessionKey: String? = null,
    val workoutLogId: String? = null,
    val programId: String? = null,
    val sessionId: String? = null,
    val sessionName: String? = null,
    val exerciseId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseName: String? = null,
    val setIndex: Int? = null,
    val side: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isPr: Boolean = false,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
    val poseTrackPath: String? = null,
)
