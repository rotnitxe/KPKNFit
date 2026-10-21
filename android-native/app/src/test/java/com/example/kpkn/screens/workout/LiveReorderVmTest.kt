package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reordenar ejercicios (LV4, 10.1) con el ViewModel de verdad: el orden nuevo tiene que verse en la sesión (que es lo que pinta el
 * carrusel), en el navegador de pasos, en lo guardado en Room y en la sesión hidratada por un proceso nuevo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveReorderVmTest {

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

    private suspend fun fixtureFor(session: Session, simpleCyclic: Boolean = false): LiveSessionVmFixture =
        LiveSessionVmFixture(session, simpleCyclic).prepare().also { fixture = it }

    /** El mapa «ejercicio → nombre de su parte» que arma la pantalla (`originalExercisePartMap`), con la parte por defecto si no hay partes. */
    private fun partMapOf(vm: WorkoutViewModel): Map<String, String> {
        val session = vm.uiState.value.session!!
        val parts = session.parts.ifEmpty { listOf(SessionPart(id = "default", name = "Sesión principal", exercises = session.exercises)) }
        return buildMap { parts.forEach { part -> part.exercises.forEach { put(it.id, part.name) } } }
    }

    private fun loose(vararg ids: String) = Session(id = "lv4-session", name = "Sesión", exercises = ids.map { liveExercise(it) })

    private fun inParts(parts: List<List<String>>, extra: (Session) -> Session = { it }): Session = extra(
        Session(
            id = "lv4-session",
            name = "Sesión",
            parts = parts.mapIndexed { index, ids ->
                SessionPart(id = "part-$index", name = "Grupo ${index + 1}", exercises = ids.map { liveExercise(it) })
            },
        ),
    )

    @Test
    fun looseExercisesReorderedShowInSessionStepsStorageAndTheRecreatedProcess() = runBlocking {
        val fx = fixtureFor(loose("a", "b", "c", "d"))
        val vm = fx.open()

        vm.applyReorderAndPromptPersistence(listOf("c", "a", "b", "d"), partMapOf(vm), isGlobal = false)

        val expected = listOf("c", "a", "b", "d")
        assertEquals("la sesión (lo que pinta el carrusel)", expected, vm.uiState.value.liveOrder())
        assertEquals("el navegador de pasos", expected, vm.stepExerciseOrder())
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.ReorderExercises)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals("lo guardado en Room", expected, fx.storedOrder())

        val recreated = fx.recreate(vm)
        assertEquals("la sesión hidratada por un proceso nuevo", expected, recreated.uiState.value.liveOrder())
        assertEquals(expected, recreated.stepExerciseOrder())
    }

    @Test
    fun reorderingInsideAPartKeepsItsGroup() = runBlocking {
        val fx = fixtureFor(inParts(listOf(listOf("a", "b"), listOf("c", "d"))))
        val vm = fx.open()

        vm.applyReorderAndPromptPersistence(listOf("b", "a", "c", "d"), partMapOf(vm), isGlobal = false)

        assertEquals(listOf("b", "a", "c", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("b", "a"), vm.uiState.value.session!!.parts[0].exercises.map { it.id })
        assertEquals(listOf("b", "a", "c", "d"), vm.stepExerciseOrder())
    }

    @Test
    fun movingTheFirstExerciseOfTheSecondGroupToTheTopIsApplied() = runBlocking {
        val fx = fixtureFor(inParts(listOf(listOf("a", "b"), listOf("c", "d"))))
        val vm = fx.open()

        // Lo que hace el cajón con «Guardar» cuando nada se ve sobrepuesto entre dos del mismo grupo.
        vm.applyReorderAndPromptPersistence(listOf("c", "a", "b", "d"), partMapOf(vm), isGlobal = false)

        assertEquals(listOf("c", "a", "b", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("c", "a", "b", "d"), vm.stepExerciseOrder())
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(listOf("c", "a", "b", "d"), fx.storedOrder())
    }

    @Test
    fun movingTheFirstExerciseOfTheFirstGroupToTheEndIsApplied() = runBlocking {
        val fx = fixtureFor(inParts(listOf(listOf("a", "b"), listOf("c", "d"))))
        val vm = fx.open()

        vm.applyReorderAndPromptPersistence(listOf("b", "c", "d", "a"), partMapOf(vm), isGlobal = true)

        assertEquals(listOf("b", "c", "d", "a"), vm.uiState.value.liveOrder())
        assertEquals(listOf("b", "c", "d", "a"), vm.stepExerciseOrder())
    }

    @Test
    fun anExerciseSkippedTodayIsNotLostWhenTheRestAreReordered() = runBlocking {
        val fx = fixtureFor(loose("a", "b", "c", "d"))
        val vm = fx.open()
        vm.skipExercise("b")
        assertEquals(listOf("a", "c", "d"), vm.uiState.value.visibleOrder())

        vm.applyReorderAndPromptPersistence(listOf("c", "a", "d"), partMapOf(vm), isGlobal = false)

        val state = vm.uiState.value
        assertEquals("sigue en la sesión", setOf("a", "b", "c", "d"), state.liveOrder().toSet())
        assertEquals("sigue omitido", setOf("b"), state.skippedExerciseIds)
        assertEquals("lo que se ve, en el orden nuevo", listOf("c", "a", "d"), state.visibleOrder())
        assertEquals(listOf("c", "a", "d"), vm.stepExerciseOrder())
    }

    @Test
    fun aSkippedExerciseInsideAGroupDoesNotBlockTheReorder() = runBlocking {
        val fx = fixtureFor(inParts(listOf(listOf("a", "b"), listOf("c"))))
        val vm = fx.open()
        vm.skipExercise("b")

        vm.applyReorderAndPromptPersistence(listOf("c", "a"), partMapOf(vm), isGlobal = true)

        val state = vm.uiState.value
        assertEquals(setOf("a", "b", "c"), state.liveOrder().toSet())
        assertEquals(listOf("c", "a"), state.visibleOrder())
        assertEquals(setOf("b"), state.skippedExerciseIds)
    }

    @Test
    fun swappingTheMembersOfASupersetChangesTheOrderOfItsRounds() = runBlocking {
        val session = Session(
            id = "lv4-session",
            name = "Sesión",
            exercises = listOf(
                liveExercise("x"),
                liveExercise("m1", group = "g1"),
                liveExercise("m2", group = "g1"),
                liveExercise("y"),
            ),
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("m1", "m2"))),
        )
        val fx = fixtureFor(session)
        val vm = fx.open()
        assertEquals(listOf("x", "m1", "m2", "y"), vm.stepExerciseOrder())

        vm.applyReorderAndPromptPersistence(listOf("x", "m2", "m1", "y"), partMapOf(vm), isGlobal = false)

        assertEquals(listOf("x", "m2", "m1", "y"), vm.uiState.value.liveOrder())
        assertEquals(listOf("x", "m2", "m1", "y"), vm.stepExerciseOrder())
        assertEquals(listOf("m2", "m1"), vm.uiState.value.session!!.supersetGroups.single().exerciseOrder)
    }

    @Test
    fun anExerciseSlippedBetweenTheMembersOfASupersetGoesAfterTheWholeGroup() = runBlocking {
        val session = Session(
            id = "lv4-session",
            name = "Sesión",
            exercises = listOf(
                liveExercise("m1", group = "g1"),
                liveExercise("m2", group = "g1"),
                liveExercise("x"),
            ),
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("m1", "m2"))),
        )
        val fx = fixtureFor(session)
        val vm = fx.open()

        vm.applyReorderAndPromptPersistence(listOf("m1", "x", "m2"), partMapOf(vm), isGlobal = false)

        assertEquals("la superserie sigue junta", listOf("m1", "m2", "x"), vm.uiState.value.liveOrder())
        assertEquals(listOf("m1", "m2", "x"), vm.stepExerciseOrder())
    }

    @Test
    fun savingTheOrderPermanentlyKeepsTheSkippedExerciseInTheProgram() = runBlocking {
        val fx = fixtureFor(loose("a", "b", "c", "d"), simpleCyclic = true)
        val vm = fx.open()
        vm.skipExercise("b")
        vm.applyReorderAndPromptPersistence(listOf("c", "a", "d"), partMapOf(vm), isGlobal = false)
        assertTrue("hay un cambio por guardar", vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.ReorderExercises)
        // En un programa simple cíclico la sesión en vivo trabaja con el id de la INSTANCIA de la semana, no con el de su plantilla.
        val liveWeekId = vm.uiState.value.weekId

        vm.commitStructuralPersistence(ReplacementPersistenceScopeV2.PERMANENT)

        assertNull("ya no queda nada por guardar", vm.uiState.value.pendingStructuralPersistence)
        val saved = fx.programSession()!!.allExercises().map { it.id }
        assertEquals("el ejercicio omitido hoy sigue en el programa (semana en vivo $liveWeekId)", setOf("a", "b", "c", "d"), saved.toSet())
        assertEquals("el programa guarda el orden nuevo (semana en vivo $liveWeekId)", listOf("c", "a", "d"), saved.filter { it != "b" })
    }
}
