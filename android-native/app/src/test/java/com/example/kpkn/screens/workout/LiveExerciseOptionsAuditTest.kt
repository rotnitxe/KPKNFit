package com.example.kpkn.screens.workout

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Auditoría de las opciones del ejercicio (LV4, 10.2): la hoja que se abre con la pulsación larga del carrusel. Cada opción se ejecuta
 * con el ViewModel de verdad, con las mismas llamadas que hace la hoja (`WorkoutStructureSheetsHost`), y se comprueba el efecto real en
 * el estado, en el navegador de pasos y en lo guardado.
 *
 * Opciones de un ejercicio de fuerza: Historial, Etiquetas, Editar, Reemplazar (LV5, no se toca aquí), Agregar otro, Reordenar (ver
 * `LiveReorderVmTest`), Crear superserie y Omitir. De una superserie: Ir a la superserie, Rondas, Agregar de sesión, Agregar del
 * catálogo y Disolver. De un cardio: Editar cardio, Reemplazar (LV5) y Omitir. Y las del modo dios: eliminar, omitir y ultrarrápido
 * por lotes y «Deshacer».
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class LiveExerciseOptionsAuditTest {

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

    private suspend fun open(session: Session): WorkoutViewModel =
        LiveSessionVmFixture(session).prepare().also { fixture = it }.open()

    private suspend fun openFour(): WorkoutViewModel = open(
        Session(id = "lv4-session", name = "Sesión", exercises = listOf("a", "b", "c", "d").map { liveExercise(it) }),
    )

    /** Un programa simple cíclico: el único que deja guardar los cambios de la sesión en vivo «para siempre». */
    private suspend fun openFourInACyclicProgram(): WorkoutViewModel =
        LiveSessionVmFixture(
            Session(id = "lv4-session", name = "Sesión", exercises = listOf("a", "b", "c", "d").map { liveExercise(it) }),
            simpleCyclic = true,
        ).prepare().also { fixture = it }.open()

    private fun WorkoutViewModel.exercise(id: String): Exercise = uiState.value.session!!.allExercises().first { it.id == id }

    private fun WorkoutViewModel.workingSteps(): List<Pair<String, Int?>> =
        workoutStepPositions(uiState.value).filter { it.type == WorkoutStepType.WORKING_SET }.map { it.exerciseId to it.setIndex }

    // ── Historial ──────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun historyOpensForThatExerciseAndClosesCleanly() = runBlocking {
        val vm = openFour()

        vm.showHistoryForExercise(vm.exercise("b"))

        val state = vm.uiState.value
        assertTrue(state.showHistorySheet)
        val key = state.historySheetExerciseDbId
        assertNotNull(key)
        // Lo que hace el anfitrión de la hoja para saber de qué ejercicio es.
        val hosted = state.session!!.allExercises().firstOrNull { ex ->
            key in identityKeysForExercise(ex) || ex.exerciseDbId == key || ex.exerciseId == key || ex.canonicalExerciseId == key
        }
        assertEquals("b", hosted?.id)
        assertTrue("sin sesiones anteriores, la lista va vacía y no falla", vm.getExerciseHistory(vm.exercise("b"), preferredTag = null).isEmpty())

        vm.hideHistorySheet()
        assertFalse(vm.uiState.value.showHistorySheet)
        assertEquals(null, vm.uiState.value.historySheetExerciseDbId)
    }

    // ── Etiquetas ──────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun tagsCanBeCreatedChosenAndClearedAndAreStored() = runBlocking {
        val vm = openFour()

        val created = vm.createTag("b", "Banco inclinado")
        assertTrue(created is CreateTagResult.Created)
        val tagId = (created as CreateTagResult.Created).tag.id
        assertEquals(listOf(tagId), vm.uiState.value.activeTagsByExercise["b"])
        assertEquals(listOf("Banco inclinado"), vm.tagsForExercise("b").map { it.name })
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals("lo guardado", listOf(tagId), fixture!!.storedOngoing().activeTags["b"])

        // La hoja «Etiquetas» elige por nombre; un nombre nuevo se crea y queda activo.
        vm.setExerciseTag("b", "Agarre ancho")
        assertEquals("Agarre ancho", vm.uiState.value.exerciseTags["b"])
        assertEquals(setOf("Banco inclinado", "Agarre ancho"), vm.tagsForExercise("b").map { it.name }.toSet())

        // Un nombre repetido no crea otra.
        assertTrue(vm.createTag("b", "agarre ancho") is CreateTagResult.Duplicate)

        vm.clearExerciseTag("b")
        assertFalse("b" in vm.uiState.value.exerciseTags)
        assertTrue(vm.uiState.value.activeTagsByExercise["b"].orEmpty().isEmpty())
    }

    // ── Editar ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun editingAnExerciseAppliesToTheSessionTheStepsAndTheStorage() = runBlocking {
        val vm = openFour()

        vm.updateExerciseDefinition("a") { it.copy(sets = it.sets + ExerciseSet(id = "a-nueva", targetReps = 5)) }

        assertEquals(3, vm.exercise("a").sets.size)
        assertEquals(3, vm.workingSteps().count { it.first == "a" })
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(3, fixture!!.storedOngoing().session.allExercises().first { it.id == "a" }.sets.size)
    }

    // ── Agregar otro ───────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun addingAnotherExerciseInsertsItRightAfterAndOpensItsEditor() = runBlocking {
        val vm = openFour()

        vm.addExerciseAfter("b", ExerciseMuscleInfo(id = "remo_mancuerna", name = "Remo con mancuerna"))

        val order = vm.uiState.value.liveOrder()
        assertEquals(5, order.size)
        assertEquals(listOf("a", "b"), order.take(2))
        assertEquals(listOf("c", "d"), order.drop(3))
        val added = order[2]
        assertEquals("Remo con mancuerna", vm.exercise(added).name)
        assertEquals("se abre su editor", added, vm.uiState.value.pendingEditSheetExerciseId)
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.AddExercise)
        assertEquals(order, vm.stepExerciseOrder())
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(order, fixture!!.storedOrder())
    }

    // ── Crear superserie ───────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun creatingASupersetJoinsTheExercisesAndInterleavesTheirRounds() = runBlocking {
        val vm = openFour()

        vm.createLiveSuperset(listOf("b", "c"), partId = null, restBetween = 45, restAfter = 90, rounds = null)

        val session = vm.uiState.value.session!!
        val group = session.allSupersetGroups().single()
        assertEquals(listOf("b", "c"), group.exerciseOrder)
        assertEquals(45, group.restBetweenExercises)
        assertEquals(90, group.restAfterSuperset)
        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
        assertEquals(
            listOf("a" to 0, "a" to 1, "b" to 0, "c" to 0, "b" to 1, "c" to 1, "d" to 0, "d" to 1),
            vm.workingSteps(),
        )
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.AddSuperset)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(1, fixture!!.storedOngoing().session.allSupersetGroups().size)
    }

    // ── Omitir ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun skippingTheCurrentExerciseMovesOnToTheNextAndUndoBringsItBack() = runBlocking {
        val vm = openFour()

        vm.skipExercise("a")

        var state = vm.uiState.value
        assertEquals(setOf("a"), state.skippedExerciseIds)
        assertEquals(listOf("b", "c", "d"), state.visibleOrder())
        assertEquals("b", state.visibleOrder()[state.currentExerciseIdx])
        assertEquals(WorkoutStepRules.workingStepKey("b", 0), state.activeStepKey)
        assertEquals(listOf("b", "c", "d"), vm.stepExerciseOrder())
        assertEquals("Omitir ejercicio", state.godModeUndoStack.last().label)

        vm.revertGodModeChange(state.godModeUndoStack.lastIndex)
        state = vm.uiState.value
        assertEquals(emptySet<String>(), state.skippedExerciseIds)
        assertEquals(listOf("a", "b", "c", "d"), vm.stepExerciseOrder())
    }

    @Test
    fun skippingAnotherExerciseKeepsTheCurrentOneAndItsSet() = runBlocking {
        val vm = openFour()
        vm.selectExercise(1)

        vm.skipExercise("a")

        val state = vm.uiState.value
        assertEquals("b", state.visibleOrder()[state.currentExerciseIdx])
        assertEquals(0, state.currentSetIdx)
    }

    // ── Superserie: Ir a la superserie, Rondas, Agregar de sesión, Agregar del catálogo y Disolver ───────────────────────────────

    private suspend fun openWithSuperset(): WorkoutViewModel {
        val vm = openFour()
        vm.createLiveSuperset(listOf("b", "c"), partId = null, restBetween = 45, restAfter = 90, rounds = null)
        vm.clearPendingStructuralPersistence()
        vm.selectExercise(0)
        return vm
    }

    private fun WorkoutViewModel.groupId(): String = uiState.value.session!!.allSupersetGroups().single().id

    @Test
    fun goToTheSupersetSelectsItsFirstPendingSet() = runBlocking {
        val vm = openWithSuperset()

        vm.selectSupersetGroup(vm.groupId())

        val state = vm.uiState.value
        assertEquals("b", state.visibleOrder()[state.currentExerciseIdx])
        assertEquals(WorkoutStepRules.workingStepKey("b", 0), state.activeStepKey)
    }

    @Test
    fun roundsAndRestsOfASupersetAreUpdatedAndStored() = runBlocking {
        val vm = openWithSuperset()

        vm.updateLiveSupersetRest(vm.groupId(), restBetween = 30, restAfter = 80, rounds = 3)

        val group = vm.uiState.value.session!!.allSupersetGroups().single()
        assertEquals(30, group.restBetweenExercises)
        assertEquals(80, group.restAfterSuperset)
        assertEquals(3, group.rounds)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(3, fixture!!.storedOngoing().session.allSupersetGroups().single().rounds)
    }

    @Test
    fun addingAnExerciseOfTheSessionToASupersetUsesTheCreator() = runBlocking {
        val vm = openWithSuperset()

        // «Agregar de sesión» abre el creador con el ejercicio de origen y «Actualizar superserie» crea el grupo ampliado.
        vm.createLiveSuperset(listOf("b", "c", "d"), partId = null, restBetween = 45, restAfter = 90, rounds = null)

        val group = vm.uiState.value.session!!.allSupersetGroups().single()
        assertEquals(listOf("b", "c", "d"), group.exerciseOrder)
        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
    }

    @Test
    fun addingAnExerciseOfTheCatalogToASupersetAppendsItAsAMember() = runBlocking {
        val vm = openWithSuperset()

        vm.addCatalogExerciseToLiveSuperset(vm.groupId(), ExerciseMuscleInfo(id = "curl_barra", name = "Curl con barra"))

        val group = vm.uiState.value.session!!.allSupersetGroups().single()
        assertEquals(3, group.exerciseOrder.size)
        assertEquals(listOf("b", "c"), group.exerciseOrder.take(2))
        assertTrue(vm.exercise(group.exerciseOrder.last()).name.contains("Curl"))
        val order = vm.uiState.value.liveOrder()
        assertEquals("el nuevo miembro va pegado al grupo", listOf("a", "b", "c"), order.take(3))
        assertEquals(group.exerciseOrder.last(), order[3])
    }

    @Test
    fun dissolvingASupersetMakesItsMembersIndependentAgain() = runBlocking {
        val vm = openWithSuperset()

        vm.dissolveLiveSuperset(vm.groupId(), preferredExerciseId = "b")

        val session = vm.uiState.value.session!!
        assertTrue(session.allSupersetGroups().isEmpty())
        assertTrue(session.allExercises().all { it.supersetGroupRefOrNull() == null })
        assertEquals(listOf("a", "b", "c", "d"), vm.stepExerciseOrder())
        assertEquals(
            listOf("a" to 0, "a" to 1, "b" to 0, "b" to 1, "c" to 0, "c" to 1, "d" to 0, "d" to 1),
            vm.workingSteps(),
        )
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.DissolveSuperset)
    }

    private fun Exercise.supersetGroupRefOrNull(): String? = supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }

    // ── Cardio: Editar cardio y Omitir ─────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun openWithCardio(): WorkoutViewModel = open(
        Session(
            id = "lv4-session",
            name = "Sesión",
            parts = listOf(
                SessionPart(id = "fuerza", name = "Fuerza", exercises = listOf(liveExercise("a"), liveExercise("b"))),
                SessionPart(
                    id = "cardio",
                    name = "Cardio",
                    isCardioGroup = true,
                    exercises = listOf(
                        Exercise(
                            id = "run",
                            name = "Cinta",
                            cardioDetails = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200),
                            sets = listOf(ExerciseSet(id = "run-set0")),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun editingTheCardioChangesItsDurationInTheSessionAndTheStorage() = runBlocking {
        val vm = openWithCardio()

        vm.updateExerciseDefinition("run") { it.copy(cardioDetails = it.cardioDetails!!.copy(targetDurationSeconds = 600)) }

        assertEquals(600, vm.exercise("run").cardioDetails?.targetDurationSeconds)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(600, fixture!!.storedOngoing().session.allExercises().first { it.id == "run" }.cardioDetails?.targetDurationSeconds)
    }

    @Test
    fun skippingTheCardioLeavesTheStrengthAndItsStepsAlone() = runBlocking {
        val vm = openWithCardio()

        vm.skipExercise("run")

        assertEquals(setOf("run"), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("a", "b"), vm.stepExerciseOrder())
    }

    // ── Modo dios: eliminar, omitir por lotes, ultrarrápido y deshacer ─────────────────────────────────────────────────────────

    @Test
    fun deletingAnExerciseRemovesItFromTheSessionTheStepsAndTheStorageAndUndoRestoresIt() = runBlocking {
        val vm = openFour()

        vm.removeExerciseFromSession("c")

        assertEquals(listOf("a", "b", "d"), vm.uiState.value.liveOrder())
        assertEquals(listOf("a", "b", "d"), vm.stepExerciseOrder())
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.RemoveExercise)
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals(listOf("a", "b", "d"), fixture!!.storedOrder())

        vm.revertGodModeChange(vm.uiState.value.godModeUndoStack.lastIndex)
        assertEquals(listOf("a", "b", "c", "d"), vm.uiState.value.liveOrder())
    }

    @Test
    fun theLastExerciseCannotBeDeletedAndLeavesNoUndoEntryForNothing() = runBlocking {
        val vm = open(Session(id = "lv4-session", name = "Sesión", exercises = listOf(liveExercise("a"))))

        vm.removeExerciseFromSession("a")

        assertEquals(listOf("a"), vm.uiState.value.liveOrder())
        assertTrue("no hay nada que deshacer", vm.uiState.value.godModeUndoStack.isEmpty())
    }

    @Test
    fun deletingAnExerciseForGoodAlsoRemovesItFromTheProgram() = runBlocking {
        val vm = openFourInACyclicProgram()

        vm.removeExerciseFromSession("c")
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.RemoveExercise)
        val liveWeekId = vm.uiState.value.weekId
        vm.commitStructuralPersistence(ReplacementPersistenceScopeV2.PERMANENT)

        assertEquals(
            "la próxima vez que se abra la sesión ya no trae el ejercicio (semana en vivo $liveWeekId)",
            listOf("a", "b", "d"),
            fixture!!.programSession()!!.allExercises().map { it.id },
        )
    }

    @Test
    fun creatingASupersetForGoodAlsoGroupsItsMembersInTheProgram() = runBlocking {
        val vm = openFourInACyclicProgram()

        vm.createLiveSuperset(listOf("a", "b"))
        assertTrue(vm.uiState.value.pendingStructuralPersistence is PendingStructuralChange.AddSuperset)
        val liveWeekId = vm.uiState.value.weekId
        vm.commitStructuralPersistence(ReplacementPersistenceScopeV2.PERMANENT)

        val group = fixture!!.programSession()!!.allSupersetGroups()
        assertEquals("el programa tiene la superserie (semana en vivo $liveWeekId)", 1, group.size)
        assertEquals(listOf("a", "b"), group.single().exerciseOrder)
    }

    @Test
    fun optionsThatChangeNothingLeaveNoUndoEntryBehind() = runBlocking {
        val vm = openFour()

        vm.skipExercise("no-existe")
        vm.skipExercises(listOf("no-existe"))
        vm.removeSetFromExercise("a", 7)
        vm.removeExercisesFromSession(listOf("a", "b", "c", "d"))
        vm.omitSet("a", 0)
        vm.omitSet("a", 0)
        vm.skipExercise("b")
        vm.skipExercise("b")

        assertEquals(
            "solo lo que de verdad cambió deja su «Deshacer»",
            listOf("Omitir serie", "Omitir ejercicio"),
            vm.uiState.value.godModeUndoStack.map { it.label },
        )
    }

    @Test
    fun skippingAndDeletingSeveralAtOnceWorksFromTheBatchBar() = runBlocking {
        val vm = openFour()

        vm.skipExercises(listOf("b", "c"))
        assertEquals(setOf("b", "c"), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("a", "d"), vm.stepExerciseOrder())

        vm.removeExercisesFromSession(listOf("d"))
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.liveOrder())
        assertEquals("lo omitido sigue omitido", setOf("b", "c"), vm.uiState.value.skippedExerciseIds)
        assertEquals(listOf("a"), vm.stepExerciseOrder())
    }

    @Test
    fun theBatchUltraFastTechniqueMarksTheFollowingSetsOfEachSelectedExercise() = runBlocking {
        val vm = openFour()

        // Lo que hace la barra de lotes del modo dios: todas las series de cada ejercicio elegido.
        for (id in listOf("b", "c")) {
            val exercise = vm.exercise(id)
            vm.updatePlannedSeriesTechnique(id, 0, exercise.sets.lastIndex, SeriesTechnique.DROPSET)
        }

        for (id in listOf("b", "c")) {
            val sets = vm.exercise(id).sets
            assertFalse("la primera serie no es un dropset", sets.first().isDropSet)
            assertTrue("la siguiente sí", sets.drop(1).all { it.isDropSet })
        }
        assertTrue("los demás quedan como estaban", vm.exercise("a").sets.none { it.isDropSet })
        vm.flushOngoingForBackgroundAndAwait()
        assertTrue(fixture!!.storedOngoing().session.allExercises().first { it.id == "b" }.sets.last().isDropSet)
    }

    @Test
    fun theUltraFastModePreviewsAppliesAndReverts() = runBlocking {
        val vm = openFour()
        val before = vm.uiState.value.session!!.allExercises().map { it.id to it.sets.map { set -> set.id } }

        vm.previewUltraFast()
        assertTrue(vm.uiState.value.showUltraFastSheet)
        assertNotNull(vm.uiState.value.ultraFastPreview)

        vm.applyUltraFast()
        assertFalse(vm.uiState.value.showUltraFastSheet)
        assertTrue(vm.uiState.value.ultraFastApplied)

        vm.revertUltraFast()
        assertFalse(vm.uiState.value.ultraFastApplied)
        assertEquals(before, vm.uiState.value.session!!.allExercises().map { it.id to it.sets.map { set -> set.id } })
    }

    // ── Notas de la sesión (viven en el cockpit, no en esta hoja) ──────────────────────────────────────────────────────────────

    @Test
    fun sessionNotesAreKeptAndStored() = runBlocking {
        val vm = openFour()

        vm.setSessionNotes("Subir el banco un punto", flush = true)
        vm.saveSessionNote("Subir el banco un punto")

        assertEquals("Subir el banco un punto", vm.uiState.value.sessionNotes)
        assertEquals(listOf("Subir el banco un punto"), vm.uiState.value.sessionSavedNotes.map { it.text })
        vm.flushOngoingForBackgroundAndAwait()
        assertEquals("Subir el banco un punto", fixture!!.storedOngoing().sessionNotes)
    }
}
