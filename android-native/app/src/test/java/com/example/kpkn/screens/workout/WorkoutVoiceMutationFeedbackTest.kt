package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.services.workout.VoiceSetEditPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The spoken table: success is announced only for a committed result, failures say the data is kept. */
class WorkoutVoiceMutationFeedbackTest {
    private val failure = WorkoutVoiceMutationResult.PersistenceFailed(IllegalStateException("room"))
    private val committedUndo = WorkoutVoiceMutationResult.Committed("a_1")

    private val allResults = listOf(
        committedUndo,
        WorkoutVoiceMutationResult.NoRecentSet,
        WorkoutVoiceMutationResult.NoChange,
        WorkoutVoiceMutationResult.Busy,
        WorkoutVoiceMutationResult.Closing,
        WorkoutVoiceMutationResult.Stale,
        WorkoutVoiceMutationResult.SessionChanged,
        failure,
    )

    @Test
    fun undoIsAnnouncedAsDoneOnlyWhenCommitted() {
        for (result in allResults) {
            val phrase = WorkoutVoiceMutationFeedback.forUndo(result)
            if (result.isCommitted) {
                assertEquals("Serie deshecha.", phrase)
            } else {
                assertNotEquals("Serie deshecha.", phrase)
            }
        }
    }

    @Test
    fun editIsAnnouncedAsDoneOnlyWhenCommitted() {
        for (result in allResults) {
            val phrase = WorkoutVoiceMutationFeedback.forEdit(result)
            if (result.isCommitted) {
                assertEquals("Serie actualizada.", phrase)
            } else {
                assertNotEquals("Serie actualizada.", phrase)
            }
        }
    }

    @Test
    fun persistenceFailureHasItsOwnWordingThatPromisesTheDataIsKept() {
        val undo = WorkoutVoiceMutationFeedback.forUndo(failure)!!
        val edit = WorkoutVoiceMutationFeedback.forEdit(failure)!!

        assertEquals("No pude deshacer la serie. Tus datos se conservan; repite deshacer.", undo)
        assertEquals("No pude guardar el cambio. Tus datos se conservan; repítelo.", edit)
        assertTrue(undo.contains("Tus datos se conservan"))
        assertTrue(edit.contains("Tus datos se conservan"))
        // The failure must be distinguishable from every other outcome.
        val others = allResults.filter { it !is WorkoutVoiceMutationResult.PersistenceFailed }
        assertFalse(others.mapNotNull(WorkoutVoiceMutationFeedback::forUndo).contains(undo))
        assertFalse(others.mapNotNull(WorkoutVoiceMutationFeedback::forEdit).contains(edit))
    }

    @Test
    fun staleSnapshotIsReportedAsUnconfirmedWithoutClaimingSuccess() {
        assertEquals(
            "No pude confirmar el cambio. Tus datos se conservan; repite deshacer.",
            WorkoutVoiceMutationFeedback.forUndo(WorkoutVoiceMutationResult.Stale),
        )
        assertEquals(
            "No pude confirmar el cambio. Tus datos se conservan; repítelo.",
            WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.Stale),
        )
    }

    @Test
    fun noTargetAndNoChangeUseTheirOwnPhrases() {
        assertEquals(
            "No hay una serie reciente para deshacer.",
            WorkoutVoiceMutationFeedback.forUndo(WorkoutVoiceMutationResult.NoRecentSet),
        )
        assertEquals(
            "No hay una serie reciente para editar.",
            WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.NoRecentSet),
        )
        assertEquals(
            "Esa serie ya tiene esos valores.",
            WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.NoChange),
        )
    }

    @Test
    fun busyAndClosingTellTheAthleteWhatToDo() {
        assertEquals("Espera un momento y repite deshacer.", WorkoutVoiceMutationFeedback.forUndo(WorkoutVoiceMutationResult.Busy))
        assertEquals("Espera un momento y repite el cambio.", WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.Busy))
        assertEquals("El entreno se está cerrando.", WorkoutVoiceMutationFeedback.forUndo(WorkoutVoiceMutationResult.Closing))
        assertEquals("El entreno se está cerrando.", WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.Closing))
    }

    @Test
    fun replacedExecutionStaysSilent() {
        assertNull(WorkoutVoiceMutationFeedback.forUndo(WorkoutVoiceMutationResult.SessionChanged))
        assertNull(WorkoutVoiceMutationFeedback.forEdit(WorkoutVoiceMutationResult.SessionChanged))
    }

    @Test
    fun onlyRetryableOutcomesExtendTheUndoToken() {
        assertTrue(WorkoutVoiceMutationFeedback.isRetryable(failure))
        assertTrue(WorkoutVoiceMutationFeedback.isRetryable(WorkoutVoiceMutationResult.Busy))
        assertTrue(WorkoutVoiceMutationFeedback.isRetryable(WorkoutVoiceMutationResult.Stale))
        assertFalse(WorkoutVoiceMutationFeedback.isRetryable(committedUndo))
        assertFalse(WorkoutVoiceMutationFeedback.isRetryable(WorkoutVoiceMutationResult.NoRecentSet))
        assertFalse(WorkoutVoiceMutationFeedback.isRetryable(WorkoutVoiceMutationResult.Closing))
        assertFalse(WorkoutVoiceMutationFeedback.isRetryable(WorkoutVoiceMutationResult.SessionChanged))
    }

    @Test
    fun onlyDataRelevantFailuresGetAVisibleNotice() {
        assertTrue(WorkoutVoiceMutationFeedback.needsVisibleNotice(failure))
        assertTrue(WorkoutVoiceMutationFeedback.needsVisibleNotice(WorkoutVoiceMutationResult.Stale))
        assertFalse(WorkoutVoiceMutationFeedback.needsVisibleNotice(committedUndo))
        assertFalse(WorkoutVoiceMutationFeedback.needsVisibleNotice(WorkoutVoiceMutationResult.NoRecentSet))
        assertFalse(WorkoutVoiceMutationFeedback.needsVisibleNotice(WorkoutVoiceMutationResult.Busy))
    }

    @Test
    fun committedEditIsSpokenWithTheConfirmedValuesOfTheChangedFieldsOnly() {
        val committed = storedSet(typedLoad = 82.5)
        val patch = VoiceSetEditPatch(
            weightKg = 82.5,
            metricValue = 9,
            intensityValue = 8.0,
            intensityKind = WorkoutVoiceIntensityKind.RPE,
        )

        val speech = WorkoutVoiceMutationFeedback.committedEditSpeech(patch, committed)!!

        assertEquals(82.5, speech.weightKg!!, 0.0)
        assertEquals(9, speech.reps)
        assertEquals(8.0, speech.intensityValue!!, 0.0)
        assertEquals(WorkoutVoiceIntensityKind.RPE, speech.intensityKind)
    }

    @Test
    fun committedDeltaEditSpeaksTheResultingLoadInsteadOfStayingSilentAboutIt() {
        val committed = storedSet(typedLoad = 22.5)

        val speech = WorkoutVoiceMutationFeedback.committedEditSpeech(VoiceSetEditPatch(weightDeltaKg = 2.5), committed)!!

        assertEquals(22.5, speech.weightKg!!, 0.0)
        assertNull(speech.reps)
        assertNull(speech.intensityValue)
        assertNull(speech.intensityKind)
    }

    @Test
    fun percentRmIntensityIsNeverSpokenAsAnEffortReport() {
        val committed = storedSet(typedLoad = 20.0)
        val patch = VoiceSetEditPatch(
            metricValue = 9,
            intensityValue = 80.0,
            intensityKind = WorkoutVoiceIntensityKind.PERCENT_RM,
        )

        val speech = WorkoutVoiceMutationFeedback.committedEditSpeech(patch, committed)!!

        assertNull(speech.intensityValue)
        assertNull(speech.intensityKind)
        assertEquals(9, speech.reps)
    }

    @Test
    fun bodyweightSetDoesNotSpeakAWeightItIgnored() {
        val committed = storedSet(typedLoad = null, mode = LoadModeV2.BODYWEIGHT)

        val speech = WorkoutVoiceMutationFeedback.committedEditSpeech(
            VoiceSetEditPatch(weightKg = 10.0, metricValue = 12),
            committed,
        )!!

        assertNull(speech.weightKg)
        assertEquals(12, speech.reps)
    }

    @Test
    fun withoutAReturnedSetThereIsNoDetailedSpeech() {
        assertNull(WorkoutVoiceMutationFeedback.committedEditSpeech(VoiceSetEditPatch(weightKg = 10.0), null))
    }

    private fun storedSet(typedLoad: Double?, mode: LoadModeV2 = LoadModeV2.LOAD) = CompletedSet(
        id = "set",
        weight = typedLoad ?: 75.0,
        reps = 9,
        recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "a",
            loadInputMode = mode,
            externalLoad = typedLoad,
        ),
    )
}
