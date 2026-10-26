package com.example.kpkn.screens.workout

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.data.media.technique.TechniqueSidecars
import com.example.kpkn.domain.biomechanics.metrics.PartialZone
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.screens.workout.design.completedVideoCorrectionPatch
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.TechniqueWorkTestKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutVideoAnalysisControllerTest {
    @Test fun stableCaptureFollowsItsSetAndRejectsAReplacementConfiguration() = runBlocking<Unit> {
        val exercise = exercises.value.single().copy(sets = listOf(ExerciseSet("s0"), ExerciseSet("s1")))
        exercises.value = listOf(exercise)
        row("stable")
        val bindings = MutableStateFlow(mapOf("stable" to executionRef(WorkoutUiState(startTimeMs = 123), exercise, 1, null)))
        val controller = WorkoutVideoAnalysisController(scope, session, completed, exercises, analysis, media,
            Dispatchers.Unconfined, executionBindings = bindings)
        controller.await { it["exercise_1"] != null }
        exercises.value = listOf(exercise.copy(sets = exercise.sets.reversed()))
        controller.await { it["exercise_0"] != null && it["exercise_1"] == null }
        exercises.value = listOf(exercise.copy(catalogConfigurationId = "replacement"))
        controller.await { it.isEmpty() }
    }

    @Test fun legacyCaptureWithoutConfigurationDoesNotReplaceAKnownSuggestion() = runBlocking<Unit> {
        row("known", at = 100L)
        row("ambiguous", at = 200L, configurationId = null)
        controller().await { it["exercise_0"]?.mediaId == "known" }
    }
    @Rule @JvmField val folder = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var scope: CoroutineScope
    private lateinit var analysis: VideoAnalysisRepository
    private lateinit var media: WorkoutMediaRepository
    private val completed = MutableStateFlow<Map<String, CompletedSet>>(emptyMap())
    private val exercises = MutableStateFlow(listOf(Exercise("exercise", "Sentadilla", trackRom = true, catalogConfigurationId = "test-config", sets = listOf(ExerciseSet("s0")))))
    private val session = MutableStateFlow<String?>("S1")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = KpknDatabase.createInMemory(context)
        store = WorkoutMediaStore(folder.newFolder("files"))
        analysis = VideoAnalysisRepository.forDatabase(context, db, store)
        media = WorkoutMediaRepository(context, db, store)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun controller() = WorkoutVideoAnalysisController(scope, session, completed, exercises, analysis, media, Dispatchers.Unconfined)
    private suspend fun WorkoutVideoAnalysisController.await(predicate: (Map<String, SetVideoAnalysisUi>) -> Boolean) =
        withTimeout(10_000L) { videoAnalysisBySetKey.first(predicate) }

    private suspend fun row(
        id: String, count: Int = 8, partials: Int = 0, side: String? = null, status: String = "DONE",
        confidence: String = "ALTA", sessionKey: String? = "S1", rom: Double = 100.0, at: Long = 100L,
        workoutLogId: String? = null,
        failed: Int = 0,
        configurationId: String? = "test-config",
    ) {
        TechniqueWorkTestKit.insertVideo(db, store, id) {
            it.copy(exerciseId = "exercise", catalogConfigurationId = configurationId, setIndex = 0, side = side, sessionKey = sessionKey, workoutLogId = workoutLogId, createdAtMs = at)
        }
        db.videoAnalysisDao().upsert(VideoAnalysisEntity(
            mediaId = id, status = status, progressPct = 42, engineVersion = 1, poseVersion = 1,
            exerciseKey = "back_squat", profileId = "SQUAT", setKey = "exercise_0", recordedAtMs = at,
            repCount = count, partialReps = partials, failedReps = failed, confidence = confidence,
            romMeanDeg = rom, flagsJson = "[]", createdAtMs = at, updatedAtMs = at,
        ))
    }

    @Test fun suggestion_never_applies_itself_and_equal_full_counts_still_detect_partial_mismatch() = runBlocking<Unit> {
        row("video", partials = 2)
        val controller = controller()
        assertTrue(controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Suggest }.isNotEmpty())
        assertTrue(completed.value.isEmpty())
        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 8, partialReps = 0))
        val mismatch = controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Mismatch }["exercise_0"]!!.state as SetVideoAnalysisState.Mismatch
        assertTrue(mismatch.countsDiffer)
        assertEquals(8, mismatch.counted)
        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 8, partialReps = 2, isPartial = true))
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Agrees }
    }

    @Test fun latest_done_wins_over_running_and_sides_are_normalized_independently() = runBlocking<Unit> {
        exercises.value = listOf(exercises.value.single().copy(isUnilateral = true))
        row("left-done", side = " L ", at = 100L)
        row("left-old-done", side = "left", count = 3, at = 50L)
        row("left-new-running", side = "left", status = "RUNNING", at = 200L)
        row("right", side = "r", count = 9, at = 300L)
        val state = controller().await { it.size == 2 }
        assertEquals("left-done", state["exercise_0_L"]!!.mediaId)
        assertEquals("right", state["exercise_0_R"]!!.mediaId)
        assertEquals(9, (state["exercise_0_R"]!!.state as SetVideoAnalysisState.Suggest).reps)
    }

    @Test fun low_confidence_hides_authoritative_counts_and_non_rep_series_never_offer_application() = runBlocking<Unit> {
        row("low", count = 27, confidence = "BAJA")
        val controller = controller()
        controller.await { it["exercise_0"]?.state == SetVideoAnalysisState.Inconclusive }
        exercises.value = listOf(exercises.value.single().copy(trainingMode = TrainingMode.TIME))
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Unsupported }
    }

    @Test fun legacy_bilateral_left_metadata_and_sidecar_key_resolve_only_to_the_bilateral_set() = runBlocking<Unit> {
        row("legacy-left", count = 7, side = "left")
        val legacy = db.videoAnalysisDao().getByMedia("legacy-left")!!
        db.videoAnalysisDao().upsert(legacy.copy(setKey = "exercise_0_L"))
        completed.value = mapOf("exercise_0" to CompletedSet("bilateral", reps = 8))

        val state = controller().await { it["exercise_0"]?.state is SetVideoAnalysisState.Mismatch }
        assertEquals(setOf("exercise_0"), state.keys)
        assertNull(state["exercise_0_L"])
        val mismatch = state["exercise_0"]!!.state as SetVideoAnalysisState.Mismatch
        assertEquals(7, mismatch.counted)
        assertTrue(mismatch.countsDiffer)
        assertEquals(8, completed.value["exercise_0"]!!.reps)
    }

    @Test fun rom_reference_requires_two_real_sessions_not_two_videos_of_one_session() = runBlocking<Unit> {
        row("first", rom = 95.0, at = 200L, workoutLogId = "log1")
        row("same-session", rom = 100.0, at = 100L)
        row("same-log-without-session-key", sessionKey = null, rom = 110.0, at = 50L, workoutLogId = "log1")
        row("same-log-other-session-alias", sessionKey = "legacy-session-alias", rom = 115.0, at = 75L, workoutLogId = "log1")
        val controller = controller()
        val initial = controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Suggest }["exercise_0"]!!.state as SetVideoAnalysisState.Suggest
        assertNull(initial.romPercent)
        row("other-session", sessionKey = "S2", rom = 120.0, at = 300L)
        controller.await { (it["exercise_0"]?.state as? SetVideoAnalysisState.Suggest)?.romPercent == 80 }
    }

    @Test fun legacy_distance_and_time_records_never_offer_rep_correction_after_exercise_changes() = runBlocking<Unit> {
        row("video", count = 7)
        completed.value = mapOf("exercise_0" to CompletedSet("legacy-distance", reps = 8, distanceKm = 2.5))
        val controller = controller()
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Unsupported }
        completed.value = mapOf("exercise_0" to CompletedSet("legacy-time", reps = 8, timeSeconds = 30))
        controller.await { (it["exercise_0"]?.state as? SetVideoAnalysisState.Unsupported)?.reasonText?.contains("tiempo") == true }
        // El payload manda sobre los restos legacy y sobre la prescripción actual.
        exercises.value = listOf(exercises.value.single().copy(trainingMode = TrainingMode.TIME))
        completed.value = mapOf("exercise_0" to CompletedSet(
            "reps", reps = 7, timeSeconds = 30, distanceKm = 2.5,
            recordedPayloadV3 = RecordedSetPayload(exerciseId = "exercise", unitMode = UnitModeV2.REPS, completedReps = 7),
        ))
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Agrees }
    }

    @Test fun medium_confidence_requires_review_and_failed_attempts_require_explicit_failure() = runBlocking<Unit> {
        row("medium", confidence = "MEDIA")
        val controller = controller()
        val suggested = controller.await { it["exercise_0"]?.mediaId == "medium" }["exercise_0"]!!.state as SetVideoAnalysisState.Suggest
        assertEquals(CountConfidence.MEDIA, suggested.confidence)
        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 8))
        val review = controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Mismatch }["exercise_0"]!!.state as SetVideoAnalysisState.Mismatch
        assertEquals(CountConfidence.MEDIA, review.confidence)
        row("failed", failed = 1, at = 200L)
        val failed = controller.await { it["exercise_0"]?.mediaId == "failed" }["exercise_0"]!!.state as SetVideoAnalysisState.Mismatch
        assertEquals(1, failed.failed)
        assertTrue(!completed.value.getValue("exercise_0").isFailure)
        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 8, isFailure = true))
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Agrees }
    }

    @Test fun omitted_series_never_offer_video_corrections_or_failure_actions() = runBlocking<Unit> {
        row("video", count = 8, partials = 1, failed = 1)
        val omitted = CompletedSet("omitted", skipped = true)
        completed.value = mapOf("exercise_0" to omitted)
        val controller = controller()
        val state = controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Unsupported }["exercise_0"]!!.state
        assertEquals(SetVideoAnalysisState.Unsupported("Esta serie está omitida"), state)
        assertEquals(omitted, completed.value.getValue("exercise_0"))
    }

    @Test fun no_done_uses_latest_running_and_session_change_removes_previous_results() = runBlocking<Unit> {
        row("queued", status = "QUEUED", at = 50L)
        row("running", status = "RUNNING", at = 100L)
        val controller = controller()
        val state = controller.await { it["exercise_0"]?.mediaId == "running" }["exercise_0"]!!.state
        assertEquals(SetVideoAnalysisState.Analyzing(42), state)
        session.value = "empty-session"
        controller.await { it.isEmpty() }
    }

    /** Un análisis DONE con parciales cuyo sidecar guarda la confianza propia de las parciales y sus zonas (lo que Room no guarda). */
    private suspend fun partialsRow(
        id: String,
        partialConfidence: CountConfidence?,
        zones: List<PartialZone?>,
        confidence: String = "ALTA",
        count: Int = 8,
    ) {
        row(id, count = count, partials = zones.size, confidence = confidence)
        val base = TechniqueReportSamples.fullReport()
        val partial = base.reps.first { it.kind == RepKind.PARTIAL }
        val report = base.copy(
            reps = base.reps.filter { it.kind != RepKind.PARTIAL } + zones.mapIndexed { i, zone -> partial.copy(index = 10 + i, zone = zone) },
            partialReps = zones.size,
            partialConfidence = partialConfidence,
        )
        val file = java.io.File(folder.root, "$id.analysis.json")
        TechniqueSidecars.writeAnalysis(file, report, TechniqueReportSamples.exercise(), 80.0, "exercise_0", 1L)
        db.videoAnalysisDao().upsert(db.videoAnalysisDao().getByMedia(id)!!.copy(reportPath = file.absolutePath))
    }

    private suspend fun WorkoutVideoAnalysisController.suggestion() =
        await { it["exercise_0"]?.state is SetVideoAnalysisState.Suggest }["exercise_0"]!!.state as SetVideoAnalysisState.Suggest

    @Test fun the_own_confidence_and_zone_of_the_partials_come_from_the_sidecar() = runBlocking<Unit> {
        partialsRow("high", CountConfidence.ALTA, listOf(PartialZone.STRETCHED, PartialZone.STRETCHED))
        val high = controller().suggestion()
        assertEquals(2, high.partials)
        assertEquals(CountConfidence.ALTA, high.partialConfidence)
        assertEquals(PartialZone.STRETCHED, high.partialZone)
    }

    @Test fun partials_in_different_zones_have_no_common_zone_and_medium_confidence_is_kept() = runBlocking<Unit> {
        partialsRow("mixed", CountConfidence.MEDIA, listOf(PartialZone.STRETCHED, PartialZone.SHORTENED))
        val state = controller().suggestion()
        assertEquals(2, state.partials)
        assertEquals(CountConfidence.MEDIA, state.partialConfidence)
        assertNull(state.partialZone)
    }

    @Test fun low_confidence_partials_are_not_offered_and_correcting_leaves_the_logged_ones() = runBlocking<Unit> {
        partialsRow("low", CountConfidence.BAJA, listOf(PartialZone.STRETCHED, PartialZone.STRETCHED))
        val controller = controller()
        val suggested = controller.suggestion()
        assertEquals("sin parciales que aplicar", 0, suggested.partials)
        assertEquals(CountConfidence.BAJA, suggested.partialConfidence)
        assertEquals("las repeticiones completas siguen ofrecidas", 8, suggested.reps)

        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 7, partialReps = 3, isPartial = true))
        val mismatch = controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Mismatch }["exercise_0"]!!.state as SetVideoAnalysisState.Mismatch
        assertEquals(3, mismatch.loggedPartials)
        assertEquals("«Corregir» deja las parciales que había", 3, mismatch.partials)
        assertNull(completedVideoCorrectionPatch(mismatch).partialReps)
        assertEquals(8, completedVideoCorrectionPatch(mismatch).edit.metricValue)

        // Con las mismas completas registradas, las parciales de BAJA ya no cuentan como diferencia.
        completed.value = mapOf("exercise_0" to CompletedSet("set", reps = 8, partialReps = 3, isPartial = true))
        controller.await { it["exercise_0"]?.state is SetVideoAnalysisState.Agrees }
    }

    @Test fun a_report_without_partial_confidence_falls_back_to_the_confidence_of_the_count() = runBlocking<Unit> {
        partialsRow("old", null, listOf(null, null), confidence = "MEDIA")
        val old = controller().suggestion()
        assertEquals(CountConfidence.MEDIA, old.partialConfidence)
        assertEquals(2, old.partials)
        assertNull(old.partialZone)
    }

    @Test fun without_a_sidecar_the_partials_follow_the_count_confidence() = runBlocking<Unit> {
        row("no-sidecar", partials = 2)
        val state = controller().suggestion()
        assertEquals(CountConfidence.ALTA, state.partialConfidence)
        assertEquals(2, state.partials)
    }

    @Test fun a_series_of_only_low_confidence_partials_is_inconclusive_instead_of_empty() = runBlocking<Unit> {
        partialsRow("only-partials", CountConfidence.BAJA, listOf(PartialZone.STRETCHED, PartialZone.STRETCHED), count = 0)
        controller().await { it["exercise_0"]?.state == SetVideoAnalysisState.Inconclusive }
    }
}
