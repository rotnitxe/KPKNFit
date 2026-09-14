package com.example.kpkn.data.media

/**
 * Pure CameraX Finalize policy: keep a recorded file when it has bytes,
 * even if Finalize reports an error (unbind / SOURCE_INACTIVE), unless the
 * error means the bitstream is unusable.
 *
 * Integer codes match `androidx.camera.video.VideoRecordEvent.Finalize`.
 */
object WorkoutMediaFinalizePolicy {
    const val ERROR_NONE = 0
    const val ERROR_UNKNOWN = 1
    const val ERROR_FILE_SIZE_LIMIT_REACHED = 2
    const val ERROR_INSUFFICIENT_STORAGE = 3
    const val ERROR_SOURCE_INACTIVE = 4
    const val ERROR_INVALID_OUTPUT_OPTIONS = 5
    const val ERROR_ENCODING_FAILED = 6
    const val ERROR_RECORDER_ERROR = 7
    const val ERROR_NO_VALID_DATA = 8
    const val ERROR_DURATION_LIMIT_REACHED = 9
    const val ERROR_RECORDING_GARBAGE_COLLECTED = 10

    fun shouldRetainFile(
        fileLengthBytes: Long,
        hasError: Boolean,
        errorCode: Int,
    ): Boolean {
        if (fileLengthBytes <= 0L) return false
        if (!hasError || errorCode == ERROR_NONE) return true
        return when (errorCode) {
            ERROR_NO_VALID_DATA,
            ERROR_ENCODING_FAILED,
            ERROR_INVALID_OUTPUT_OPTIONS,
            -> false
            else -> true
        }
    }
}
