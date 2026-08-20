package com.example.kpkn.services.workout

import com.example.kpkn.data.models.VoiceVerbosity
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind

/** Which spoken cues are allowed at each verbosity level. */
enum class VoiceAnnouncementKind {
    /** Always spoken (on/off, confirm prompts, errors, auto-confirm). */
    CRITICAL,
    /** Rest start / adaptive hint / session remaining 15-5-1-0. */
    ESSENTIAL,
    /** Ten-seconds remaining, slow-pace hints. */
    COMPLETE,
}

object WorkoutVoiceVerbosityGate {
    fun allows(verbosity: VoiceVerbosity, kind: VoiceAnnouncementKind): Boolean {
        return when (verbosity) {
            VoiceVerbosity.SILENT -> kind == VoiceAnnouncementKind.CRITICAL
            VoiceVerbosity.ESSENTIAL -> kind != VoiceAnnouncementKind.COMPLETE
            VoiceVerbosity.COMPLETE -> true
        }
    }
}

data class VoiceSetEditPatch(
    val weightKg: Double? = null,
    val weightDeltaKg: Double? = null,
    val metricValue: Int? = null,
    val intensityValue: Double? = null,
    val intensityKind: WorkoutVoiceIntensityKind? = null,
    val side: String? = null,
) {
    val hasAnyField: Boolean
        get() = weightKg != null || weightDeltaKg != null || metricValue != null ||
            intensityValue != null || side != null
}

data class VoiceUndoPayload(
    val setKey: String,
    val exerciseId: String,
    val setIdx: Int,
    val side: String?,
    val expiresAtMs: Long,
) {
    fun isActive(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs <= expiresAtMs

    companion object {
        const val WINDOW_MS = 5_000L

        fun buildSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
            "left" -> "${exerciseId}_${setIdx}_L"
            "right" -> "${exerciseId}_${setIdx}_R"
            else -> "${exerciseId}_$setIdx"
        }
    }
}
