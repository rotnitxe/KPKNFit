package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El feedback tras el ejercicio contra el ViewModel REAL de una sesión (LV6, B1–B3): el borrador es el mismo para la pantalla completa y
 * la tarjeta hasta que se guarda o se descarta, y las entradas del motor de molestias salen de la sesión en vivo sin romper nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackVmTest {

    @get:Rule
    val roomRule = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        LiveSessionVmFixture.resetAugeRepository()
    }

    @After
    fun tearDown() {
        try {
            fixture?.close()
            LiveSessionVmFixture.resetAugeRepository()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun open(vararg ids: String): WorkoutViewModel {
        val session = Session(id = "lv6-feedback", name = "Sesión", exercises = ids.map { liveExercise(it, sets = 3) })
        val fx = LiveSessionVmFixture(session).prepare().also { fixture = it }
        return fx.open()
    }

    private fun feedbackOf(id: String) = PostExerciseFeedback(
        exerciseId = id,
        exerciseName = "Ejercicio ${id.uppercase()}",
        technicalQuality = 6,
        discomfortIds = listOf("none"),
    )

    @Test
    fun theFullScreenAndTheCardGetTheSameDraftUntilTheFeedbackIsSaved() = runBlocking {
        val vm = open("a", "b")
        val exercises = vm.visibleExercises(vm.uiState.value).take(1)
        val fullScreen = vm.feedbackDraftFor(exercises, emptyMap())
        fullScreen.setTechnical("a", 5)
        fullScreen.select("lumbar")
        val card = vm.feedbackDraftFor(exercises, emptyMap())
        assertSame("minimizar no pierde lo elegido: es el mismo borrador", fullScreen, card)
        assertEquals(5, card.technical("a"))

        vm.savePostExerciseFeedbacks(listOf(feedbackOf("a")))

        val next = vm.feedbackDraftFor(exercises, vm.uiState.value.postExerciseFeedbackByExerciseId)
        assertNotSame("guardar cierra el borrador", fullScreen, next)
        assertEquals("el nuevo arranca de lo guardado", 6, next.technical("a"))
        assertTrue(next.discomfortIds.isEmpty())
    }

    @Test
    fun dismissingTheFeedbackAlsoDropsTheDraft() = runBlocking {
        val vm = open("a", "b")
        val exercises = vm.visibleExercises(vm.uiState.value).take(1)
        val before = vm.feedbackDraftFor(exercises, emptyMap())
        before.setTechnical("a", 2)
        vm.dismissPostExerciseSheet()
        assertNotSame(before, vm.feedbackDraftFor(exercises, emptyMap()))
    }

    @Test
    fun theEngineInputsComeFromTheLiveSessionAndNothingIsInvented() = runBlocking {
        val vm = open("a", "b")
        val request = FeedbackInsightsRequest.of(listOf("a"), mapOf("a" to 8), completedSetsCount = 0)
        val insights = vm.feedbackInsightSource.load(request)
        // Un ejercicio sin músculos conocidos ni historial: el motor no tiene nada que decir.
        assertNull(insights.notice)
        assertTrue(insights.reported.isEmpty())
        // La segunda vez sale de la caché: es el mismo resultado.
        assertEquals(insights, vm.feedbackInsightSource.peek(request))
        assertNull(vm.feedbackInsightSource.explain(request, "knee_patellar"))
    }
}
