package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV-C: lo que se registra al dar el esfuerzo de una aproximación usa la MISMA base que la tarjeta (la carga de trabajo, no el 1RM):
 * con trabajo a 100 kg y un 1RM de 130, una aproximación al 50 % se registra con 50 kg y no con 65.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WarmupRecordedLoadBaseVmTest {
    @get:Rule
    val roomRule = RoomTestRule()
    private var fixture: LiveSessionVmFixture? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    @Test
    fun theRecordedWarmupLoadFollowsTheWorkingLoadAndMatchesTheCard() = runBlocking {
        val base = liveExercise("w", sets = 3)
        val exercise = base.copy(
            sets = base.sets.map { it.copy(weight = 100.0) },
            reference1RM = 130.0,
            warmupSets = listOf(WarmupSetDefinition(id = "wu", percentageOfWorkingWeight = 0.5, targetReps = 5)),
        )
        val fx = LiveSessionVmFixture(Session(id = "lvc-warmup", name = "Sesión", exercises = listOf(exercise))).prepare().also { fixture = it }
        val vm = fx.open()

        // Lo que muestra la tarjeta ANTES de registrar (después el reporte de esfuerzo marca la fila como cumplida).
        val shownByTheCard = vm.getWarmupSuggestedWeight(exercise, 0, vm.uiState.value.exerciseTags["w"])
        val anchor = vm.getWarmupWorkingWeightAnchor(exercise, vm.uiState.value.exerciseTags["w"])
        val result = vm.recordWarmupHeavinessAndAwait("w", "wu", rpe = 7.0)
        assertTrue(result.succeeded)

        val recorded = vm.uiState.value.completedSets[WorkoutStepRules.warmupStepKey("w", "wu")]!!.weight
        println("LV-C aproximación: registrado=$recorded tarjeta=$shownByTheCard ancla=$anchor")
        // Lo registrado coincide con lo que muestra la tarjeta cuando esta da kg alcanzables…
        if (shownByTheCard != null) assertEquals(shownByTheCard, recorded, 1e-9)
        // …y nunca sale del 1RM: 65 kg (50 % de 130) sería la base equivocada.
        assertNotEquals(65.0, recorded, 1e-9)
        assertEquals("el 50 % sale del peso de trabajo", 0.5 * (anchor ?: 100.0), recorded, 2.6)
    }
}
