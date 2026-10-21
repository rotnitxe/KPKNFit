package com.example.kpkn.screens.technique.importer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.screens.sessioneditor.CatalogLaunchOrigin
import com.example.kpkn.screens.sessioneditor.CatalogResult
import com.example.kpkn.screens.sessioneditor.CatalogSelectionMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VideoImportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private val source = FakeSource()
    private val handle = SavedStateHandle()
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun create(savedState: SavedStateHandle = handle) = VideoImportViewModel(application, savedState, source, dispatcher) { 42L }.also { store.put("importer", it) }
    private fun prepare(vm: VideoImportViewModel) {
        vm.open()
        vm.selectVideo(Uri.parse("content://videos/one"))
        vm.chooseExercise(exercise)
        vm.setWeight("82,5")
    }

    @Test fun submitCarriesExactCatalogIdsLoadAndRecordingDateThenRequestsExplicitAnalysis() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        vm.analyze()
        advanceUntilIdle()
        val seed = source.submissions.single().second
        assertEquals("content://videos/one", source.submissions.single().first.toString())
        assertEquals(WorkoutMediaKind.VIDEO, seed.kind)
        assertEquals("bench_press", seed.catalogDefinitionId)
        assertEquals("bench_press__barbell", seed.catalogConfigurationId)
        assertEquals(exercise.id, seed.exerciseId)
        assertEquals(exercise.name, seed.exerciseName)
        assertEquals(82.5, seed.weightKg!!, 0.0)
        assertEquals(1234L, seed.createdAtMs)
        assertNull(seed.sessionKey)
        assertNull(seed.sessionId)
        source.saved = seed.copy(filePath = "/private/${seed.id}.mp4")
        source.pending.complete(source.saved)
        advanceUntilIdle()
        assertEquals(listOf(seed.id), source.requested)
        assertEquals(seed.id, vm.uiState.value.importedMediaId)
        assertFalse(vm.uiState.value.isImporting)
    }

    @Test fun doubleTapDoesNotSubmitTwiceAndDismissDoesNotCancelTheCopy() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        vm.analyze()
        vm.analyze()
        vm.dismiss()
        advanceUntilIdle()
        assertEquals(1, source.submissions.size)
        assertTrue(vm.uiState.value.isImporting)
        assertTrue(vm.uiState.value.isOpen)
        assertFalse(source.pending.isCancelled)
    }

    @Test fun durationLimitIsTenMinutesAndHighResolutionShowsWarning() = runTest {
        source.metadataValue = ImportedVideoMetadata(600_001, 1920, 1080, 30.0)
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canAnalyze)
        assertTrue(vm.uiState.value.error!!.contains("10 minutos"))
        vm.analyze()
        assertTrue(source.submissions.isEmpty())
        source.metadataValue = ImportedVideoMetadata(600_000, 4096, 2160, 120.0)
        vm.selectVideo(Uri.parse("content://videos/two"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canAnalyze)
        assertNotNull(vm.uiState.value.metadata!!.warning)
    }

    @Test fun changingExerciseOrLoadDoesNotClearAnInvalidVideoError() = runTest {
        source.metadataValue = ImportedVideoMetadata(600_001)
        val vm = create()
        vm.open()
        vm.selectVideo(Uri.parse("content://videos/too-long"))
        advanceUntilIdle()
        val videoError = vm.uiState.value.error
        assertNotNull(videoError)
        vm.chooseExercise(exercise)
        vm.setWeight("95")
        assertEquals(videoError, vm.uiState.value.error)
        assertEquals(videoError, vm.uiState.value.videoError)
        assertFalse(vm.uiState.value.canAnalyze)
        source.metadataValue = ImportedVideoMetadata(60_000)
        vm.selectVideo(Uri.parse("content://videos/valid"))
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.videoError)
        assertTrue(vm.uiState.value.canAnalyze)
    }

    @Test fun invalidLoadAndFailedCopyStayInSheetWithUsefulError() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        vm.setWeight("NaN")
        vm.analyze()
        assertTrue(source.submissions.isEmpty())
        assertNotNull(vm.uiState.value.error)
        vm.setWeight("")
        vm.analyze()
        source.pending.complete(null)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isOpen)
        assertFalse(vm.uiState.value.isImporting)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.importedMediaId)
    }

    @Test fun restoresUriExerciseLoadAndPendingCatalogRequestAndIgnoresForeignResults() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        val request = vm.beginCatalogSelection()
        assertEquals(CatalogLaunchOrigin.TECHNIQUE, request.origin)
        assertEquals(CatalogSelectionMode.SINGLE, request.selectionMode)
        val restored = create(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals("content://videos/one", restored.uiState.value.uri)
        assertEquals(exercise, restored.uiState.value.exercise)
        assertEquals("82,5", restored.uiState.value.weightText)
        assertEquals(request, restored.beginCatalogSelection())
        val foreign = CatalogResult(requestId = "foreign", origin = request.origin, selectionMode = request.selectionMode, selectedExerciseIds = listOf("x"))
        restored.applyCatalogResult(foreign)
        assertEquals(request, restored.beginCatalogSelection())
        restored.applyCatalogResult(CatalogResult.cancel(request))
        assertEquals(exercise, restored.uiState.value.exercise)
        assertTrue(restored.beginCatalogSelection().requestId != request.requestId)
    }

    @Test fun restorationAfterCopyUsesExistingMediaWithoutResubmittingAndRetriesAnalysis() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        vm.analyze()
        advanceUntilIdle()
        val seed = source.submissions.single().second
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        store.clear()
        source.saved = seed.copy(filePath = "/private/${seed.id}.mp4")
        val restored = create(restoredHandle)
        advanceUntilIdle()
        assertEquals(1, source.submissions.size)
        assertEquals(listOf(seed.id), source.requested)
        assertEquals(seed.id, restored.uiState.value.importedMediaId)
        assertFalse(source.pending.isCancelled)
    }

    @Test fun failedCopyFreezesSelectionAndRetryKeepsTheSameUuidAndSeed() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        vm.analyze()
        source.pending.complete(null)
        advanceUntilIdle()
        val firstSeed = source.submissions.single().second
        assertTrue(vm.uiState.value.isSelectionLocked)
        vm.setWeight("95")
        vm.chooseExercise(exercise.copy(id = "other", catalogConfigurationId = "bench_press__dumbbell"))
        assertEquals("82,5", vm.uiState.value.weightText)
        assertEquals(exercise, vm.uiState.value.exercise)
        source.pending = CompletableDeferred()
        vm.analyze()
        advanceUntilIdle()
        assertEquals(listOf(firstSeed, firstSeed), source.submissions.map { it.second })
    }

    @Test fun failedAnalysisRetriesPersistedMediaWithoutCreatingAnotherAndNewVideoUnlocksDraft() = runTest {
        val vm = create()
        prepare(vm)
        advanceUntilIdle()
        source.acceptAnalysis = false
        vm.analyze()
        val seed = source.submissions.single().second
        source.saved = seed.copy(filePath = "/private/${seed.id}.mp4")
        source.pending.complete(source.saved)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelectionLocked)
        assertNotNull(vm.uiState.value.error)
        vm.setWeight("95")
        vm.chooseExercise(exercise.copy(id = "other"))
        assertEquals("82,5", vm.uiState.value.weightText)
        assertEquals(exercise, vm.uiState.value.exercise)
        source.acceptAnalysis = true
        vm.analyze()
        advanceUntilIdle()
        assertEquals(1, source.submissions.size)
        assertEquals(listOf(seed.id, seed.id), source.requested)
        vm.selectVideo(Uri.parse("content://videos/new-request"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSelectionLocked)
        vm.setWeight("95")
        vm.chooseExercise(exercise.copy(id = "new-exercise"))
        assertEquals("95", vm.uiState.value.weightText)
        assertEquals("new-exercise", vm.uiState.value.exercise!!.id)
    }

    @Test fun pickerCancelClosesEmptySheetAndVideoDateIsParsedInUtc() = runTest {
        val vm = create()
        vm.open()
        vm.pickerLaunched()
        vm.selectVideo(null)
        assertFalse(vm.uiState.value.isOpen)
        assertFalse(vm.uiState.value.pickerInFlight)
        assertEquals(0L, parseVideoDate("19700101T000000.000Z"))
        assertEquals(0L, parseVideoDate("1970-01-01T00:00:00Z"))
        assertNull(parseVideoDate("sin fecha"))
    }

    private class FakeSource : VideoImportSource {
        var metadataValue = ImportedVideoMetadata(20_000, 1920, 1080, 30.0, recordedAtMs = 1234)
        var saved: WorkoutMedia? = null
        var pending = CompletableDeferred<WorkoutMedia?>()
        var acceptAnalysis = true
        val submissions = mutableListOf<Pair<Uri, WorkoutMedia>>()
        val requested = mutableListOf<String>()
        override suspend fun metadata(uri: Uri) = metadataValue
        override suspend fun recover() = Unit
        override suspend fun findMedia(id: String) = saved?.takeIf { it.id == id }
        override fun submitUriCapture(uri: Uri, seed: WorkoutMedia): CompletableDeferred<WorkoutMedia?> { submissions += uri to seed; return pending }
        override suspend fun requestAnalysis(mediaId: String): Boolean { requested += mediaId; return acceptAnalysis }
        override suspend fun resolveSelection(result: CatalogResult) = exercise
        override suspend fun resolveRecent(exerciseKey: String) = exercise
    }
    companion object {
        private val exercise = VideoImportExercise("exact-row", "Press de banca con barra", "bench_press", "bench_press__barbell")
    }
}
