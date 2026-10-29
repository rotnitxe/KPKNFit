package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Al abrir una sesión nueva, el primer paso activo es el de preparación que el ejercicio ya trae programada: primero la movilidad,
 * después las aproximaciones y solo entonces la serie efectiva 1. Antes se saltaban las dos y la sesión arrancaba en la serie 1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LivePrepFirstStepTest {

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

    private fun prepared(withMobility: Boolean, withWarmups: Boolean): Exercise = Exercise(
        id = "a",
        name = "Ejercicio A",
        sets = (0 until 3).map { ExerciseSet(id = "a-set$it", targetReps = 8, weight = 40.0) },
        restTime = 60,
        mobilitySeries = if (withMobility) listOf(MobilitySeries(id = "mob1", name = "Movilidad de cadera")) else emptyList(),
        warmupSets = if (withWarmups) {
            listOf(
                WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.4, targetReps = 8),
                WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.6, targetReps = 5),
            )
        } else {
            emptyList()
        },
    )

    private suspend fun open(first: Exercise): WorkoutViewModel {
        val session = Session(id = "lv4-session", name = "Sesión", exercises = listOf(first, liveExercise("b")))
        return LiveSessionVmFixture(session).prepare().also { fixture = it }.open()
    }

    /** Completar un paso se guarda en otro hilo: se espera a que el paso activo sea el esperado. */
    private suspend fun awaitActive(vm: WorkoutViewModel, key: String) {
        withTimeout(10_000) { vm.uiState.first { it.activeStepKey == key } }
        assertEquals(key, vm.uiState.value.activeStepKey)
    }

    @Test
    fun `una sesion nueva arranca en la movilidad y luego en las aproximaciones`() = kotlinx.coroutines.runBlocking {
        val vm = open(prepared(withMobility = true, withWarmups = true))
        assertEquals(WorkoutStepRules.mobilityStepKey("a", "mob1", 0), vm.uiState.value.activeStepKey)

        // Completar un paso lo guarda; «Continuar» (advanceAfterPreparation) mueve al siguiente pendiente.
        vm.markMobilityCompleteAndAwait("a", "mob1", 0)
        vm.advanceAfterPreparation("a")
        awaitActive(vm, WorkoutStepRules.warmupStepKey("a", "w1"))

        vm.markWarmupCompleteAndAwait("a", "w1")
        vm.advanceAfterPreparation("a")
        awaitActive(vm, WorkoutStepRules.warmupStepKey("a", "w2"))

        vm.markWarmupCompleteAndAwait("a", "w2")
        vm.advanceAfterPreparation("a")
        awaitActive(vm, WorkoutStepRules.workingStepKey("a", 0))
    }

    @Test
    fun `sin movilidad la sesion arranca en la primera aproximacion`() = kotlinx.coroutines.runBlocking {
        val vm = open(prepared(withMobility = false, withWarmups = true))
        assertEquals(WorkoutStepRules.warmupStepKey("a", "w1"), vm.uiState.value.activeStepKey)
    }

    @Test
    fun `sin preparacion la sesion arranca en la serie 1`() = kotlinx.coroutines.runBlocking {
        val vm = open(prepared(withMobility = false, withWarmups = false))
        assertEquals(WorkoutStepRules.workingStepKey("a", 0), vm.uiState.value.activeStepKey)
    }

    @Test
    fun `desde la serie 1 se puede volver a la aproximacion y a la movilidad sin haberlas hecho`() = kotlinx.coroutines.runBlocking {
        val vm = open(prepared(withMobility = true, withWarmups = true))
        // El usuario se salta la preparación y va a la serie 1 (el botón › del roadmap).
        vm.selectWorkoutStep(WorkoutStepRules.workingStepKey("a", 0))
        awaitActive(vm, WorkoutStepRules.workingStepKey("a", 0))

        // ‹ desde la serie 1: la aproximación.
        vm.selectWorkoutStep(WorkoutStepRules.warmupStepKey("a", "w1"))
        awaitActive(vm, WorkoutStepRules.warmupStepKey("a", "w1"))
        assertEquals(0, vm.uiState.value.currentExerciseIdx)

        // ‹ otra vez: la movilidad.
        vm.selectWorkoutStep(WorkoutStepRules.mobilityStepKey("a", "mob1", 0))
        awaitActive(vm, WorkoutStepRules.mobilityStepKey("a", "mob1", 0))

        // Y de vuelta a una serie efectiva.
        vm.selectWorkoutStep(WorkoutStepRules.workingStepKey("a", 1))
        awaitActive(vm, WorkoutStepRules.workingStepKey("a", 1))
        vm.selectWorkoutStep(WorkoutStepRules.warmupStepKey("a", "w2"))
        awaitActive(vm, WorkoutStepRules.warmupStepKey("a", "w2"))
    }
}
