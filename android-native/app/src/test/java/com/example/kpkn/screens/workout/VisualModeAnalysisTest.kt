package com.example.kpkn.screens.workout

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.VideoAnalysisSessionRow
import com.example.kpkn.data.media.WorkoutMediaStore
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.data.repository.VideoAnalysisRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.data.work.TechniqueWorkTestKit
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.VisualModeAnalysisAction
import com.example.kpkn.screens.workout.design.videoThumbBadgeFor
import com.example.kpkn.screens.workout.design.videoThumbContentDescription
import com.example.kpkn.screens.workout.design.visualModeAnalysisLine
import com.example.kpkn.screens.workout.design.workoutVideoRepsText
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class VisualModeAnalysisTest {

    // Cada estado mantiene el texto y la acción explícita de su frase.

    @Test
    fun line_analyzing_shows_progress_without_action() {
        val state = SetVideoAnalysisState.Analyzing(42)
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("Analizando el vídeo de la serie 1… 42\u00A0%", line.text)
        assertNull(line.actionLabel)
        assertEquals(VisualModeAnalysisAction.NONE, line.action)
    }

    @Test
    fun line_suggest_alta_shows_counts_partials_and_open_action() {
        val state = SetVideoAnalysisState.Suggest(
            reps = 8,
            partials = 1,
            failed = 0,
            confidence = CountConfidence.ALTA,
        )
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("El vídeo cuenta 8 · +1 parcial · ver análisis", line.text)
        assertEquals("ver análisis", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_mismatch_alta_with_differing_counts_shows_comparison_and_open_action() {
        val state = SetVideoAnalysisState.Mismatch(
            logged = 8,
            counted = 7,
            partials = 0,
            failed = 0,
            confidence = CountConfidence.ALTA,
        )
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("Registraste 8; el vídeo cuenta 7 · ver análisis", line.text)
        assertEquals("ver análisis", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_suggest_media_shows_approximate_and_review_action() {
        val state = SetVideoAnalysisState.Suggest(
            reps = 8,
            partials = 0,
            failed = 0,
            confidence = CountConfidence.MEDIA,
        )
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("El vídeo cuenta ≈8 · revisar", line.text)
        assertEquals("revisar", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_mismatch_media_shows_approximate_and_review_action() {
        val state = SetVideoAnalysisState.Mismatch(
            logged = 8,
            counted = 8,
            partials = 0,
            failed = 0,
            confidence = CountConfidence.MEDIA,
        )
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("El vídeo cuenta ≈8 · revisar", line.text)
        assertEquals("revisar", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_agrees_shows_confirmation_and_open_action() {
        val state = SetVideoAnalysisState.Agrees(counted = 8)
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("El vídeo confirma 8 · ver análisis", line.text)
        assertEquals("ver análisis", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_inconclusive_shows_explanation_request_and_open_action() {
        val state = SetVideoAnalysisState.Inconclusive
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("Vídeo no concluyente · ver por qué", line.text)
        assertEquals("ver por qué", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.OPEN, line.action)
    }

    @Test
    fun line_failed_shows_error_and_retry_action() {
        val state = SetVideoAnalysisState.Failed(errorCode = "ERR_CODEC")
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("No se pudo analizar el vídeo · reintentar", line.text)
        assertEquals("reintentar", line.actionLabel)
        assertEquals(VisualModeAnalysisAction.RETRY, line.action)
    }

    @Test
    fun line_unsupported_shows_reason_without_action() {
        val state = SetVideoAnalysisState.Unsupported("Este ejercicio todavía no admite análisis de técnica")
        val line = visualModeAnalysisLine(state, setIndex = 0)
        assertEquals("Este ejercicio todavía no admite análisis de técnica", line.text)
        assertNull(line.actionLabel)
        assertEquals(VisualModeAnalysisAction.NONE, line.action)
    }

    @Test
    fun line_low_confidence_never_shows_an_authoritative_number() {
        val states = listOf(
            SetVideoAnalysisState.Suggest(27, 2, 1, CountConfidence.BAJA),
            SetVideoAnalysisState.Mismatch(8, 27, 2, 1, CountConfidence.BAJA),
        )
        states.forEach { state ->
            assertEquals("Vídeo no concluyente · ver por qué", visualModeAnalysisLine(state, 0).text)
            assertEquals(VisualModeAnalysisAction.OPEN, visualModeAnalysisLine(state, 0).action)
        }
    }

    @Test
    fun line_preserves_partial_and_failed_attempts_at_both_confidences() {
        assertEquals("El vídeo cuenta 8 · +2 parciales · 1 al fallo · ver análisis",
            visualModeAnalysisLine(SetVideoAnalysisState.Suggest(8, 2, 1, CountConfidence.ALTA), 0).text)
        assertEquals("El vídeo cuenta ≈8 · +1 parcial · 2 al fallo · revisar",
            visualModeAnalysisLine(SetVideoAnalysisState.Suggest(8, 1, 2, CountConfidence.MEDIA), 0).text)
        assertEquals("El vídeo cuenta ≈7 · +1 parcial · 1 al fallo · revisar",
            visualModeAnalysisLine(SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.MEDIA), 0).text)
    }

    @Test
    fun line_distinguishes_zero_count_and_partial_only_mismatches_from_failed_attempts() {
        assertEquals("Registraste 8; el vídeo cuenta 0 · 1 al fallo · ver análisis",
            visualModeAnalysisLine(SetVideoAnalysisState.Mismatch(8, 0, 0, 1, CountConfidence.ALTA), 0).text)
        assertEquals("Registraste 8 + 1 parcial; el vídeo cuenta 8 · +2 parciales · ver análisis",
            visualModeAnalysisLine(SetVideoAnalysisState.Mismatch(8, 8, 2, 0, CountConfidence.ALTA, loggedPartials = 1), 0).text)
        assertEquals("El vídeo muestra 1 intento al fallo · ver análisis",
            visualModeAnalysisLine(SetVideoAnalysisState.Suggest(0, 0, 1, CountConfidence.ALTA), 0).text)
        assertEquals("El vídeo muestra 1 intento al fallo · ver análisis",
            visualModeAnalysisLine(SetVideoAnalysisState.Mismatch(8, 8, 0, 1, CountConfidence.ALTA), 0).text)
    }

    @Test
    fun line_uses_one_based_series_and_a_non_breaking_percent() {
        assertEquals("Analizando el vídeo de la serie 3… 42\u00A0%",
            visualModeAnalysisLine(SetVideoAnalysisState.Analyzing(42), 2).text)
    }

    @Test
    fun badge_queued_or_running_maps_to_analyzing() {
        assertEquals(
            VideoThumbBadge.Analyzing(35),
            videoThumbBadgeFor(status = "QUEUED", progressPct = 35, repCount = null, confidence = null),
        )
        assertEquals(
            VideoThumbBadge.Analyzing(80),
            videoThumbBadgeFor(status = "RUNNING", progressPct = 80, repCount = null, confidence = null),
        )
    }

    @Test
    fun badge_done_high_confidence_with_count_maps_to_exact_reps() {
        assertEquals(
            VideoThumbBadge.Reps(8, approximate = false),
            videoThumbBadgeFor(status = "DONE", progressPct = 100, repCount = 8, confidence = "ALTA"),
        )
    }

    @Test
    fun badge_done_medium_confidence_maps_to_approximate_reps() {
        assertEquals(
            VideoThumbBadge.Reps(8, approximate = true),
            videoThumbBadgeFor(status = "DONE", progressPct = 100, repCount = 8, confidence = "MEDIA"),
        )
    }

    @Test
    fun badge_done_low_confidence_or_no_count_maps_to_inconclusive() {
        assertEquals(
            VideoThumbBadge.Inconclusive,
            videoThumbBadgeFor(status = "DONE", progressPct = 100, repCount = 8, confidence = "BAJA"),
        )
        assertEquals(
            VideoThumbBadge.Inconclusive,
            videoThumbBadgeFor(status = "DONE", progressPct = 100, repCount = null, confidence = "ALTA"),
        )
    }

    @Test
    fun badge_failed_maps_to_failed() {
        assertEquals(
            VideoThumbBadge.Failed,
            videoThumbBadgeFor(status = "FAILED", progressPct = 0, repCount = null, confidence = null),
        )
    }

    @Test
    fun badge_unsupported_maps_to_null() {
        assertNull(
            videoThumbBadgeFor(status = "UNSUPPORTED", progressPct = 0, repCount = null, confidence = null),
        )
    }

    @Test
    fun badge_from_row_matches_table() {
        val row = VideoAnalysisSessionRow(
            mediaId = "m1",
            exerciseId = "ex1",
            setIndex = 0,
            side = null,
            createdAtMs = 1000L,
            status = "DONE",
            progressPct = 100,
            repCount = 8,
            partialReps = 0,
            failedReps = 0,
            confidence = "ALTA",
        )
        assertEquals(VideoThumbBadge.Reps(8, approximate = false), videoThumbBadgeFor(row))
    }

    @Test
    fun badge_missing_unknown_or_invalid_counts_are_not_numbers() {
        listOf(null, "BAJA", "desconocida").forEach { confidence ->
            assertEquals(VideoThumbBadge.Inconclusive, videoThumbBadgeFor("DONE", 100, 8, confidence))
        }
        listOf("ALTA", "MEDIA").forEach { confidence ->
            listOf<Int?>(null, -1).forEach { count ->
                assertEquals(VideoThumbBadge.Inconclusive, videoThumbBadgeFor("DONE", 100, count, confidence))
            }
            assertEquals(VideoThumbBadge.Reps(0, confidence == "MEDIA"), videoThumbBadgeFor("DONE", 100, 0, confidence))
        }
        assertNull(videoThumbBadgeFor("NEEDS_PROFILE", 0, null, null))
    }

    @Test
    fun badge_progress_stays_in_the_ring_range() {
        assertEquals(VideoThumbBadge.Analyzing(0), videoThumbBadgeFor("QUEUED", -10, null, null))
        assertEquals(VideoThumbBadge.Analyzing(100), videoThumbBadgeFor("RUNNING", 120, null, null))
    }

    @Test
    fun talkback_descriptions_match_brief_specifications() {
        assertEquals("Vídeo, analizando 42\u00A0%", videoThumbContentDescription(VideoThumbBadge.Analyzing(42)))
        assertEquals("Vídeo, 8 repeticiones", videoThumbContentDescription(VideoThumbBadge.Reps(8, approximate = false)))
        assertEquals("Vídeo, unas 8 repeticiones", videoThumbContentDescription(VideoThumbBadge.Reps(8, approximate = true)))
        assertEquals("Vídeo no concluyente", videoThumbContentDescription(VideoThumbBadge.Inconclusive))
        assertEquals("Vídeo, no se pudo analizar", videoThumbContentDescription(VideoThumbBadge.Failed))
    }

    @Test
    fun failed_state_is_not_inconclusive() {
        val failedState = SetVideoAnalysisState.Failed(errorCode = "ERR")
        assertNotEquals(SetVideoAnalysisState.Inconclusive, failedState)
        assertEquals("No se pudo analizar el vídeo", workoutVideoRepsText(failedState))
        assertNotEquals("Vídeo no concluyente", workoutVideoRepsText(failedState))

        // En insignias, FAILED tampoco es Inconclusive
        val badge = videoThumbBadgeFor(status = VideoAnalysisStatus.FAILED.name, progressPct = 0, repCount = null, confidence = null)
        assertEquals(VideoThumbBadge.Failed, badge)
        assertNotEquals(VideoThumbBadge.Inconclusive, badge)
    }
}

/** La fila real debe llegar como Failed; observar las insignias no duplica el flujo que lee Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class VisualModeAnalysisFlowTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: KpknDatabase
    private lateinit var store: WorkoutMediaStore
    private lateinit var scope: CoroutineScope
    private lateinit var analysis: VideoAnalysisRepository
    private lateinit var media: WorkoutMediaRepository
    private val completed = MutableStateFlow<Map<String, CompletedSet>>(emptyMap())
    private val exercises = MutableStateFlow(listOf(Exercise("exercise", "Sentadilla")))

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Application>()
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

    private suspend fun row(id: String, status: VideoAnalysisStatus) {
        TechniqueWorkTestKit.insertVideo(db, store, id) {
            it.copy(exerciseId = "exercise", setIndex = 0, sessionKey = "S1")
        }
        analysis.markState(id, status, progressPct = 42, errorCode = "ERR_CODEC")
    }

    @Test fun failed_row_reaches_the_series_and_both_observers_share_one_snapshot_source() = runBlocking<Unit> {
        row("failed", VideoAnalysisStatus.FAILED)
        row("unsupported", VideoAnalysisStatus.UNSUPPORTED)
        val subscriptions = AtomicInteger()
        val sessions = flow {
            subscriptions.incrementAndGet()
            emit("S1")
            awaitCancellation()
        }
        val controller = WorkoutVideoAnalysisController(scope, sessions, completed, exercises, analysis, media, Dispatchers.Unconfined)
        withTimeout(10_000L) {
            val series = async { controller.videoAnalysisBySetKey.first { it.isNotEmpty() } }
            val thumbs = async { controller.videoThumbBadgeByMediaId.first { "failed" in it } }
            assertEquals(VideoThumbBadge.Failed, thumbs.await()["failed"])
            assertNull(thumbs.await()["unsupported"])
            // Retirar la fila sin soporte deja la fallida como estado de la serie.
            db.videoAnalysisDao().delete("unsupported")
            val state = controller.videoAnalysisBySetKey.first { it["exercise_0"]?.mediaId == "failed" }
            assertEquals(SetVideoAnalysisState.Failed("ERR_CODEC"), state.getValue("exercise_0").state)
            series.await()
            assertEquals(1, subscriptions.get())
            assertEquals(emptyMap<String, CompletedSet>(), completed.value)
        }
    }

    @Test fun switching_session_clears_thumbnail_badges() = runBlocking<Unit> {
        row("running", VideoAnalysisStatus.RUNNING)
        val sessions = MutableStateFlow<String?>("S1")
        val controller = WorkoutVideoAnalysisController(scope, sessions, completed, exercises, analysis, media, Dispatchers.Unconfined)
        withTimeout(10_000L) {
            val badges = controller.videoThumbBadgeByMediaId.first { "running" in it }
            assertEquals(VideoThumbBadge.Analyzing(42), badges["running"])
            sessions.value = "S2"
            controller.videoThumbBadgeByMediaId.first { it.isEmpty() }
        }
    }
}
