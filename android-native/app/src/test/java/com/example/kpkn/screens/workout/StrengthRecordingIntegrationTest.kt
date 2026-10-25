package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetEntryV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba la unidad desde el registrador hasta el motor, con el ViewModel real. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class StrengthRecordingIntegrationTest {
    @get:Rule
    val roomRule = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        try {
            fixture?.close()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun open(): WorkoutViewModel {
        val exercise = Exercise("press", "Press", sets = List(2) {
            ExerciseSet("press-$it", targetReps = 5, weight = 100.0)
        })
        val session = Session("t3-esfuerzo", "Sesión", exercises = listOf(exercise))
        val fx = LiveSessionVmFixture(session).also { fixture = it }.prepare()
        return fx.open()
    }

    @Test
    fun `registrar RIR dos homologa con dos repeticiones en reserva`() = runBlocking {
        val vm = open()
        val result = vm.recordSetV2(
            weight = 100.0, value = 5.0, intensity = null,
            advanced = SetAdvancedFeedback(rir = 2, actualIntensityMode = IntensityMode.RIR, actualIntensityValue = 2.0),
            loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
        )
        assertTrue(result is RecordSetResult.Created)
        val set = vm.uiState.value.completedSets.values.single()
        assertEquals(2, set.rir)
        assertEquals(IntensityMode.RIR, set.recordedPayloadV3!!.actualIntensityMode)
        assertEquals(120.0, set.homologatedResultV3!!.estimatedRm!!, 0.0)
    }

    @Test
    fun `registrar RPE ocho equivale a RIR dos en la homologacion`() = runBlocking {
        val vm = open()
        val result = vm.recordSetV2(
            weight = 100.0, value = 5.0, intensity = 8.0,
            advanced = SetAdvancedFeedback(actualIntensityMode = IntensityMode.RPE, actualIntensityValue = 8.0),
            loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
        )
        assertTrue(result is RecordSetResult.Created)
        val set = vm.uiState.value.completedSets.values.single()
        assertEquals(8.0, set.rpe!!, 0.0)
        assertEquals(IntensityMode.RPE, set.recordedPayloadV3!!.actualIntensityMode)
        assertEquals(120.0, set.homologatedResultV3!!.estimatedRm!!, 0.0)
    }

    @Test
    fun `sin unidad de esfuerzo se conserva la homologacion heredada`() = runBlocking {
        val vm = open()
        // El registrador exige esfuerzo en series de fuerza: sin él no se guarda nada.
        val result = vm.recordSetV2(
            weight = 100.0, value = 5.0, intensity = null,
            loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
        )
        assertTrue(result is RecordSetResult.Rejected)
        assertTrue(vm.uiState.value.completedSets.isEmpty())
        // Un esfuerzo numérico sin unidad (entrada directa al motor) conserva la cifra heredada.
        val entry = SetEntryV2(
            exerciseId = "press", setIndex = 1, loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS, actualValue = 5.0, loggedLoad = 100.0,
            actualIntensity = 2.0, contextKey = "t3-sin-unidad",
        )
        assertEquals(112.5, vm.computeSetOutcomeV2(entry).estimatedRm!!, 0.0)
    }
}
