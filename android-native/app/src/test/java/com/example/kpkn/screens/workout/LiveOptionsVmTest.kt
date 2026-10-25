package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las opciones de la serie, las del ejercicio y las de la hoja de orden (LV11, A1 a A3) con el ViewModel de verdad: cada opción llama a la
 * función que ya existía, lo que cambia se ve en la sesión, en el navegador de pasos y en lo guardado, y «Deshacer» lo devuelve todo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveOptionsVmTest {

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

    private suspend fun openFour(): WorkoutViewModel = LiveSessionVmFixture(
        Session(id = "lv11-session", name = "Sesión", exercises = listOf("a", "b", "c", "d").map { liveExercise(it) }),
    ).prepare().also { fixture = it }.open()

    private fun WorkoutViewModel.exercise(id: String): Exercise = uiState.value.session!!.allExercises().first { it.id == id }

    private fun WorkoutViewModel.workingSteps(): List<Pair<String, Int?>> =
        workoutStepPositions(uiState.value).filter { it.type == WorkoutStepType.WORKING_SET }.map { it.exerciseId to it.setIndex }

    private fun WorkoutViewModel.undoLabels(): List<String> = uiState.value.godModeUndoStack.map { it.label }

    // ── Opciones de la serie ───────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun addASetFromTheSeriesOptionsGoesToTheExerciseWhereItWasPressedAndUndoRemovesIt() = runBlocking {
        val vm = openFour()
        assertEquals("el ejercicio de turno es el primero", "a", vm.uiState.value.visibleOrder()[vm.uiState.value.currentExerciseIdx])

        // Las opciones de una serie de «c» (por ejemplo, un miembro de superserie que no es el de turno) añaden la serie a «c».
        vm.addSetToCurrentExercise("c")

        assertEquals(3, vm.exercise("c").sets.size)
        assertEquals("el de turno no cambia", 2, vm.exercise("a").sets.size)
        assertEquals(listOf("c" to 0, "c" to 1, "c" to 2), vm.workingSteps().filter { it.first == "c" })
        assertEquals(listOf("Añadir serie"), vm.undoLabels())
        val pending = vm.uiState.value.pendingStructuralPersistence as PendingStructuralChange.AddSet
        assertEquals("c", pending.exerciseId)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals("lo guardado", 3, fixture!!.storedOngoing().session.allExercises().first { it.id == "c" }.sets.size)

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(2, vm.exercise("c").sets.size)
        assertTrue(vm.uiState.value.godModeUndoStack.isEmpty())
        assertNull("deshacer también cierra la pregunta de cómo guardar", vm.uiState.value.pendingStructuralPersistence)
    }

    @Test
    fun theOldCallWithoutAnExerciseStillAddsToTheCurrentOne() = runBlocking {
        val vm = openFour()

        vm.addSetToCurrentExercise()

        assertEquals(3, vm.exercise("a").sets.size)
        assertEquals(2, vm.exercise("b").sets.size)
        assertEquals(listOf("Añadir serie"), vm.undoLabels())
    }

    @Test
    fun skipASetFromTheSeriesOptionsHidesItFromTheStepsAndUndoBringsItBack() = runBlocking {
        val vm = openFour()
        assertTrue(("a" to 0) in vm.workingSteps())

        vm.omitSet("a", 0)

        assertFalse("la serie omitida ya no es un paso", ("a" to 0) in vm.workingSteps())
        assertTrue("las demás siguen", ("a" to 1) in vm.workingSteps())
        assertEquals(listOf("Omitir serie"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertTrue(("a" to 0) in vm.workingSteps())
        assertTrue(vm.uiState.value.omittedSetKeys.isEmpty())
    }

    @Test
    fun deleteASetFromTheSeriesOptionsTakesItOutOfTheSessionTheStepsAndTheStorageAndUndoRestoresIt() = runBlocking {
        val vm = openFour()
        val before = vm.exercise("a").sets.map { it.id }

        vm.removeSetFromExercise("a", 1)

        assertEquals(listOf(before.first()), vm.exercise("a").sets.map { it.id })
        assertEquals(listOf("a" to 0), vm.workingSteps().filter { it.first == "a" })
        assertEquals(listOf("Eliminar serie"), vm.undoLabels())
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.RemoveSet)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(1, fixture!!.storedOngoing().session.allExercises().first { it.id == "a" }.sets.size)

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(before, vm.exercise("a").sets.map { it.id })
        assertEquals(listOf("a" to 0, "a" to 1), vm.workingSteps().filter { it.first == "a" })
    }

    @Test
    fun theTypeOfASetIsChangedFromTheSeriesOptionsWithItsScopeAndUndoPutsItBack() = runBlocking {
        val vm = openFour()

        // «Solo S2» con «Drop-set» (el alcance que arma la hoja con el nodo pulsado).
        vm.updatePlannedSeriesTechnique("a", 1, 1, SeriesTechnique.DROPSET, selectedIndices = setOf(1))

        assertTrue(vm.exercise("a").sets[1].isDropSet)
        assertFalse(vm.exercise("a").sets[0].isDropSet)
        assertEquals(listOf("Convertir a dropset"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertFalse(vm.exercise("a").sets[1].isDropSet)
    }

    // ── Opciones del ejercicio ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun ultraFastForOneExerciseMarksItsFollowingSetsAndLeavesTheOthersAlone() = runBlocking {
        val vm = openFour()
        val b = vm.exercise("b")

        // Lo que hace el diálogo «Modo ultrarrápido» de las opciones del ejercicio.
        vm.updatePlannedSeriesTechnique("b", 0, b.sets.lastIndex, SeriesTechnique.REST_PAUSE)

        val sets = vm.exercise("b").sets
        assertFalse("la primera serie no puede ser rest-pause", sets.first().isRestPause)
        assertTrue(sets.drop(1).all { it.isRestPause })
        assertTrue(vm.exercise("a").sets.none { it.isRestPause })
        assertEquals(listOf("Convertir a rest-pause"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)
        assertTrue(vm.exercise("b").sets.none { it.isRestPause })
    }

    @Test
    fun skippingAnExerciseFromItsOptionsAndUndoingItBringsItBack() = runBlocking {
        val vm = openFour()

        vm.skipExercise("b")

        assertEquals(setOf("b"), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("Omitir ejercicio"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(emptySet<String>(), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("a", "b", "c", "d"), vm.stepExerciseOrder())
    }

    @Test
    fun removingAnExerciseFromItsOptionsLeavesTheOthersAndUndoRestoresItInItsPlace() = runBlocking {
        val vm = openFour()

        vm.removeExerciseFromSession("b")

        assertEquals(listOf("a", "c", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("Eliminar ejercicio"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
    }

    @Test
    fun anExerciseNoteIsKeptShownInTheStateAndStored() = runBlocking {
        val vm = openFour()

        vm.setExerciseNote("b", "Banco a 30 grados", flush = true)

        assertEquals("Banco a 30 grados", vm.uiState.value.exerciseNotes["b"])
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals("lo guardado con la sesión en curso", "Banco a 30 grados", fixture!!.storedOngoing().exerciseNotes["b"])

        vm.setExerciseNote("b", "   ", flush = true)

        assertFalse("una nota en blanco la borra", "b" in vm.uiState.value.exerciseNotes)
    }

    // ── Las acciones por lotes de la hoja de orden ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun createASupersetFromTheMarkedRowsAndUndoDissolvesIt() = runBlocking {
        val vm = openFour()

        vm.createLiveSuperset(listOf("b", "c"))

        assertEquals(1, vm.uiState.value.session!!.allSupersetGroups().size)
        assertEquals(listOf("Crear superserie"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertTrue(vm.uiState.value.session!!.allSupersetGroups().isEmpty())
    }

    @Test
    fun skippingSeveralMarkedRowsAtOnceIsOneUndoEntry() = runBlocking {
        val vm = openFour()

        vm.skipExercises(listOf("b", "c"))

        assertEquals(setOf("b", "c"), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("Omitir ejercicios"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(emptySet<String>(), vm.uiState.value.skippedExerciseIds)
    }

    @Test
    fun removingSeveralMarkedRowsAtOnceIsOneUndoEntryAndUndoBringsThemAllBack() = runBlocking {
        val vm = openFour()

        vm.removeExercisesFromSession(listOf("a", "c"))

        assertEquals(listOf("b", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("Eliminar ejercicios"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
    }

    @Test
    fun reorderingFromTheSheetPutsTheNewOrderEverywhereAndUndoRestoresTheOldOne() = runBlocking {
        val vm = openFour()

        vm.applyReorderAndPromptPersistence(
            listOf("d", "a", "b", "c"),
            originalPartMap = vm.uiState.value.session!!.allExercises().associate { it.id to "Sesión principal" },
            isGlobal = false,
        )

        assertEquals(listOf("d", "a", "b", "c"), vm.uiState.value.liveOrder())
        assertEquals(listOf("d", "a", "b", "c"), vm.stepExerciseOrder())
        assertEquals(listOf("Reordenar ejercicios"), vm.undoLabels())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)

        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("a", "b", "c", "d"), vm.stepExerciseOrder())
    }
}
