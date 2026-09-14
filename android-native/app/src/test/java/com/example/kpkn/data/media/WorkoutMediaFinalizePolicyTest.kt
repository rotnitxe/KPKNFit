package com.example.kpkn.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMediaFinalizePolicyTest {
    @Test
    fun keepValidFileWhenFinalizeSucceeds() {
        assertTrue(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 1_024L,
                hasError = false,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_NONE,
            ),
        )
    }

    @Test
    fun keepValidFileWhenUnbindCausesSourceInactive() {
        assertTrue(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 8_192L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_SOURCE_INACTIVE,
            ),
        )
    }

    @Test
    fun keepValidFileOnUnknownOrGarbageCollected() {
        assertTrue(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 100L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_UNKNOWN,
            ),
        )
        assertTrue(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 100L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_RECORDING_GARBAGE_COLLECTED,
            ),
        )
    }

    @Test
    fun dropEmptyFileEvenWithoutError() {
        assertFalse(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 0L,
                hasError = false,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_NONE,
            ),
        )
    }

    @Test
    fun dropInvalidBitstreamEvenIfFileHasBytes() {
        assertFalse(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 40L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_NO_VALID_DATA,
            ),
        )
        assertFalse(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 40L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_ENCODING_FAILED,
            ),
        )
        assertFalse(
            WorkoutMediaFinalizePolicy.shouldRetainFile(
                fileLengthBytes = 40L,
                hasError = true,
                errorCode = WorkoutMediaFinalizePolicy.ERROR_INVALID_OUTPUT_OPTIONS,
            ),
        )
    }
}
