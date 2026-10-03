package com.example.kpkn.data.media

/** A failed capture retained by the process owner until the same UUID can be committed. */
data class WorkoutMediaPendingCapture(
    val entry: WorkoutMediaCaptureJournalEntry,
    val errorMessage: String,
)

/** Read-only status shared by workout capture and the albums/history surface. */
data class WorkoutMediaPendingCaptureState(
    val recoveryFinished: Boolean = false,
    val journalUnreadable: Boolean = false,
    val capturesById: Map<String, WorkoutMediaPendingCapture> = emptyMap(),
)

/** One visible retry row per workout execution, including executions with no Room media rows yet. */
data class WorkoutMediaPendingRetryAlbum(
    val sessionKey: String,
    val workoutLogId: String?,
    val programId: String,
    val sessionId: String,
    val sessionName: String?,
    val createdAtMs: Long,
    val pendingCount: Int,
    val errorMessage: String,
) {
    /** Uses the durable log identity once the execution has entered workout history. */
    val historyKey: String
        get() = workoutLogId?.takeIf(String::isNotBlank)?.let { "log:$it" } ?: "live:$sessionKey"
}
