package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cerrar el resumen final (LV4, 15): se vuelve a la sesión tal como estaba, con el cierre pendiente y sin perder nada. El fallo era que
 * «Terminar hasta acá» omitía el resto de la sesión ANTES de guardar su foto de «dónde estaba»: al cerrar el resumen (Atrás, tocar
 * fuera o «Volver») no volvía nada y, si aún no se había hecho el ejercicio actual, no quedaba ningún ejercicio a la vista.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveFinishCloseVmTest {

    @get:Rule
    val roomRule = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        LiveSessionVmFixture.resetTrainingObservationRepository()
    }

    @After
    fun tearDown() {
        try {
            fixture?.close()
            LiveSessionVmFixture.resetTrainingObservationRepository()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun open(vararg ids: String): WorkoutViewModel {
        val session = Session(id = "lv4-session", name = "Sesión", exercises = ids.map { liveExercise(it) })
        val fx = LiveSessionVmFixture(session).prepare().also { fixture = it }
        return fx.open()
    }

    /** Lo que hay que poder ver tras cerrar el resumen: un ejercicio actual de verdad, con su serie. */
    private fun assertSessionIsVisible(vm: WorkoutViewModel, expectedVisible: List<String>) {
        val state = vm.uiState.value
        assertFalse("el resumen se cerró", state.showFinishSheet)
        assertNull("no queda foto del resumen", state.finishResumeSnapshot)
        assertEquals("no se pierde ningún ejercicio", expectedVisible, state.visibleOrder())
        val visible = state.session!!.allExercises().filter { it.id in expectedVisible }
        val current = visible.getOrNull(state.currentExerciseIdx)
        assertNotNull("hay un ejercicio actual que mostrar", current)
        assertTrue("y una serie a la que apuntar", state.currentSetIdx in current!!.sets.indices)
    }

    @Test
    fun terminarHastaAcaAtTheStartAndClosingTheSummaryGivesBackEveryExercise() = runBlocking {
        val vm = open("a", "b", "c", "d")

        vm.finishUpToCurrentPoint()
        assertTrue(vm.uiState.value.showFinishSheet)

        vm.hideFinish()

        assertEquals("no queda nada omitido", emptySet<String>(), vm.uiState.value.skippedExerciseIds)
        assertSessionIsVisible(vm, listOf("a", "b", "c", "d"))
        assertEquals("sigue en el primer ejercicio", 0, vm.uiState.value.currentExerciseIdx)
    }

    @Test
    fun terminarHastaAcaInTheMiddleAndClosingTheSummaryKeepsThePlaceAndTheRest() = runBlocking {
        val vm = open("a", "b", "c", "d")
        vm.selectExercise(2)
        assertEquals(2, vm.uiState.value.currentExerciseIdx)

        vm.finishUpToCurrentPoint()
        vm.hideFinish()

        assertSessionIsVisible(vm, listOf("a", "b", "c", "d"))
        assertEquals("vuelve al ejercicio en el que estaba", 2, vm.uiState.value.currentExerciseIdx)
        assertEquals("c", vm.uiState.value.visibleOrder()[vm.uiState.value.currentExerciseIdx])
    }

    @Test
    fun anExerciseSkippedBeforeTheSummaryStaysSkippedWhenTheSummaryIsClosed() = runBlocking {
        val vm = open("a", "b", "c", "d")
        vm.skipExercise("b")
        // La omisión va por el carril único de escritura: se espera a que quede guardada, como hace la persona antes de terminar.
        withTimeout(10_000) { vm.uiState.first { "b" in it.skippedExerciseIds && it.recordingSetKey == null } }

        vm.finishUpToCurrentPoint()
        vm.hideFinish()

        assertEquals("lo omitido antes sigue omitido, y solo eso", setOf("b"), vm.uiState.value.skippedExerciseIds)
        assertSessionIsVisible(vm, listOf("a", "c", "d"))
    }

    @Test
    fun theSummaryOpenedByTheLastSetClosesBackToTheSessionAndItsFinishButtonOpensItAgain() = runBlocking {
        val vm = open("a", "b")
        for (exerciseId in listOf("a", "b")) for (setIdx in 0 until 2) {
            val previous = vm.uiState.value.omittedSetKeys
            vm.omitSet(exerciseId, setIdx)
            // Las omisiones comparten un único carril de escritura: la siguiente espera a la anterior.
            withTimeout(10_000) {
                vm.uiState.first { it.omittedSetKeys.size > previous.size && it.recordingSetKey == null }
            }
        }
        withTimeout(10_000) { vm.uiState.first { it.showFinishSheet } }
        assertTrue("al no quedar series, el resumen se abre solo", vm.uiState.value.showFinishSheet)

        vm.hideFinish()

        assertSessionIsVisible(vm, listOf("a", "b"))
        assertTrue("el cierre queda pendiente: la sesión enseña cómo volver al resumen", vm.uiState.value.finishClosedPending)
        vm.showFinish()
        assertTrue("el botón de terminar vuelve al resumen", vm.uiState.value.showFinishSheet)
        assertFalse("con el resumen abierto ya no está pendiente", vm.uiState.value.finishClosedPending)
        assertNotNull(vm.uiState.value.finishResumeSnapshot)
        vm.hideFinish()
        assertSessionIsVisible(vm, listOf("a", "b"))
        assertTrue(vm.uiState.value.finishClosedPending)
    }

    @Test
    fun theTerminarPillRunsTerminarHastaAcaAgainAndClosingTheSummaryRestoresEverythingAgain() = runBlocking {
        val vm = open("a", "b", "c", "d")
        vm.selectExercise(1)

        vm.finishUpToCurrentPoint()
        vm.hideFinish()
        assertTrue("el cierre queda pendiente: la sesión enseña «Terminar»", vm.uiState.value.finishClosedPending)
        assertFalse(vm.uiState.value.showFinishSheet)

        // Lo que hace la píldora «Terminar» de la sesión: el mismo «Terminar hasta acá» del aviso de salida.
        vm.finishUpToCurrentPoint()
        assertTrue(vm.uiState.value.showFinishSheet)
        assertFalse("con el resumen abierto ya no está pendiente", vm.uiState.value.finishClosedPending)
        assertEquals("el resumen deja fuera lo que queda", setOf("b", "c", "d"), vm.uiState.value.skippedExerciseIds)

        vm.hideFinish()

        assertEquals("y al cerrarlo otra vez vuelve todo", emptySet<String>(), vm.uiState.value.skippedExerciseIds)
        assertSessionIsVisible(vm, listOf("a", "b", "c", "d"))
        assertEquals(1, vm.uiState.value.currentExerciseIdx)
    }

    @Test
    fun aSessionNeverClosedHasNothingPending() = runBlocking {
        val vm = open("a", "b")

        assertFalse(vm.uiState.value.finishClosedPending)
        assertFalse(vm.uiState.value.showFinishSheet)
    }

    @Test
    fun skippingEveryExerciseAndClosingTheSummaryDoesNotLeaveAnEmptySession() = runBlocking {
        val vm = open("a", "b")
        vm.skipExercise("a")
        withTimeout(10_000) { vm.uiState.first { "a" in it.skippedExerciseIds } }
        vm.skipExercise("b")
        withTimeout(10_000) { vm.uiState.first { "b" in it.skippedExerciseIds } }
        withTimeout(10_000) { vm.uiState.first { it.showFinishSheet } }
        assertTrue("sin ejercicios por hacer, el resumen se abre solo", vm.uiState.value.showFinishSheet)

        vm.hideFinish()

        assertSessionIsVisible(vm, listOf("a", "b"))
    }

    @Test
    fun theClosedSummaryIsWhatGetsStoredSoARecreatedProcessAlsoSeesTheSession() = runBlocking {
        val vm = open("a", "b", "c")
        vm.finishUpToCurrentPoint()
        vm.hideFinish()

        val recreated = fixture!!.recreate(vm)

        assertSessionIsVisible(recreated, listOf("a", "b", "c"))
    }
}
