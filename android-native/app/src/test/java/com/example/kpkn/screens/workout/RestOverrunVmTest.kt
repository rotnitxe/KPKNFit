package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
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
 * El descanso con los RINGS y «Descanso cumplido» (LV6) contra el ViewModel REAL de una sesión (Room en memoria y el controlador del
 * descanso de verdad). Lo que se comprueba es que el cierre natural del descanso sigue siendo el de siempre y que la capa de
 * interfaz nace encima sin tocarlo; que «Necesito +30 s» arranca un descanso por la vía de siempre; y que el total del reloj no
 * se reinicia al ajustar el tiempo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class RestOverrunVmTest {

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
        val session = Session(id = "lv6-session", name = "Sesión", exercises = ids.map { liveExercise(it, sets = 3) })
        val fx = LiveSessionVmFixture(session).prepare().also { fixture = it }
        return fx.open()
    }

    /** Deja que el descanso de [seconds] segundos llegue a 0 por su cuenta: el reloj del controlador mira el reloj real. */
    private fun letTheRestEndOnItsOwn(seconds: Int) {
        Thread.sleep(seconds * 1000L + 400L)
        dispatcher.scheduler.advanceTimeBy(1_500L)
        dispatcher.scheduler.runCurrent()
    }

    @Test
    fun aRestThatEndsOnItsOwnClosesAsAlwaysAndLeavesTheOverrunLayerOnTop() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(1)
        val rest = vm.uiState.value.restModalState
        assertNotNull(rest)
        assertTrue(vm.uiState.value.isRestTimerRunning)
        assertTrue("el descanso guarda cuándo empezó", rest!!.startedAtMs > 0L)

        letTheRestEndOnItsOwn(seconds = 1)

        val state = vm.uiState.value
        // El cierre de siempre: el descanso termina y no queda temporizador ni modal.
        assertFalse(state.isRestTimerRunning)
        assertNull(state.restModalState)
        assertEquals(0, vm.restTimerRemaining.value)
        // Y encima, la capa de «Descanso cumplido» con lo necesario para contar desde que acabó.
        val overrun = state.restOverrun
        assertNotNull("tras un descanso natural entre series sale la capa", overrun)
        assertEquals("cuenta desde el endsAtMs guardado", rest.endsAtMs, overrun!!.endedAtMs)
        assertEquals(1, overrun.totalSeconds)
        assertEquals(RestTimerKind.STANDARD, overrun.kind)
        assertEquals("el cursor ya está en la serie que toca", state.currentSetIdx, overrun.setIdx)
    }

    @Test
    fun skippingTheRestNeverLeavesTheLayer() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(60)
        vm.stopRestTimer()
        assertNull("saltar el descanso no es pasarse de tiempo", vm.uiState.value.restOverrun)
        assertFalse(vm.uiState.value.isRestTimerRunning)
    }

    @Test
    fun needingThirtyMoreSecondsStartsANewRestThroughTheUsualPath() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(1)
        letTheRestEndOnItsOwn(seconds = 1)
        assertNotNull(vm.uiState.value.restOverrun)

        vm.extendRestAfterOverrun()

        val state = vm.uiState.value
        assertNull("la capa se va al arrancar el descanso nuevo", state.restOverrun)
        assertTrue(state.isRestTimerRunning)
        val rest = state.restModalState
        assertNotNull(rest)
        assertEquals(30, rest!!.activeSeconds)
        assertEquals(RestTimerKind.STANDARD, rest.kind)
        assertEquals(30, vm.restTimerRemaining.value)
        // Es un descanso de verdad: el total del reloj es de 30 s y se puede saltar como cualquiera.
        assertEquals(30, rest.totalRestSeconds())
        vm.stopRestTimer()
        assertFalse(vm.uiState.value.isRestTimerRunning)
    }

    @Test
    fun startingTheSetClosesTheLayerAndNothingElse() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(1)
        letTheRestEndOnItsOwn(seconds = 1)
        val before = vm.uiState.value
        assertNotNull(before.restOverrun)

        vm.dismissRestOverrun()

        val after = vm.uiState.value
        assertNull(after.restOverrun)
        assertEquals(before.currentExerciseIdx, after.currentExerciseIdx)
        assertEquals(before.currentSetIdx, after.currentSetIdx)
        assertEquals(before.completedSets, after.completedSets)
        assertFalse(after.isRestTimerRunning)
    }

    @Test
    fun movingOnToAnotherExerciseClosesTheLayer() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(1)
        letTheRestEndOnItsOwn(seconds = 1)
        assertNotNull(vm.uiState.value.restOverrun)

        vm.selectExercise(1)

        assertNull(vm.uiState.value.restOverrun)
    }

    @Test
    fun theTotalOfTheRestDoesNotResetWhenItIsAdjusted() = runBlocking {
        val vm = open("a", "b")
        vm.startRestTimer(60)
        val first = vm.uiState.value.restModalState!!
        assertEquals(60, first.totalRestSeconds())

        vm.addRestTime(15)
        val plus = vm.uiState.value.restModalState!!
        assertEquals("el inicio se conserva al ajustar", first.startedAtMs, plus.startedAtMs)
        assertEquals("el total crece lo que se suma, no se reinicia", 75, plus.totalRestSeconds())
        assertEquals(75, plus.activeSeconds)

        vm.addRestTime(-15)
        val minus = vm.uiState.value.restModalState!!
        assertEquals(first.startedAtMs, minus.startedAtMs)
        assertEquals(60, minus.totalRestSeconds())

        vm.stopRestTimer()
    }

    @Test
    fun theShapeThePersonPicksIsSharedAndStaysForTheRestOfTheSession() = runBlocking {
        val vm = open("a")
        assertNull(vm.restClockShapeChoice.value)
        vm.chooseRestClockShape(RestClockShape.SPIRAL)
        assertEquals(RestClockShape.SPIRAL, vm.restClockShapeChoice.value)
        // Cualquier descanso posterior toma la elegida, sea cual sea su orden.
        assertEquals(
            RestClockShape.SPIRAL,
            RestClockMath.shapeFor(restOrdinal = 5, chosen = vm.restClockShapeChoice.value),
        )
    }
}
