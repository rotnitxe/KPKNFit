package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.cardioPart
import com.example.kpkn.data.models.hasCardioPart
import com.example.kpkn.data.models.isCardio
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorCardioSpaceTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        repository.clearPrograms()
        repository.clearActiveProgram()
        repository.clearOngoingWorkout()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun testCardioPartModelExtensions() {
        val regularPart = SessionPart(id = "p1", name = "Pecho", color = "#EF4444")
        assertFalse(regularPart.isCardioPart())

        val cardioFlagPart = SessionPart(id = "p2", name = "Cardio Final", isCardioGroup = true)
        assertTrue(cardioFlagPart.isCardioPart())

        val cardioNamePart = SessionPart(id = "p3", name = "Espacio de cardio")
        assertTrue(cardioNamePart.isCardioPart())

        val sessionWithoutCardio = Session(id = "s1", name = "Fuerza", parts = listOf(regularPart))
        assertFalse(sessionWithoutCardio.hasCardioPart())
        assertEquals(null, sessionWithoutCardio.cardioPart())

        val sessionWithCardio = Session(id = "s2", name = "Mixto", parts = listOf(regularPart, cardioFlagPart))
        assertTrue(sessionWithCardio.hasCardioPart())
        assertEquals(cardioFlagPart.id, sessionWithCardio.cardioPart()?.id)
    }

    @Test
    fun testAddingCardioCreatesDedicatedCardioSpaceAfterStrength() = runBlocking {
        val programId = "prog_cardio_test"
        val sessionId = "session_test_1"
        val strengthExercise = Exercise(
            id = "str_1",
            exerciseId = "bench_press",
            name = "Press Banca",
            sets = emptyList(),
        )
        val initialSession = Session(
            id = sessionId,
            name = "Push Day",
            exercises = listOf(strengthExercise),
            parts = emptyList(),
        )
        val program = Program(
            id = programId,
            name = "Test Program",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week",
                                            name = "Semana",
                                            sessions = listOf(initialSession),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(program)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )

        withTimeout(5_000) {
            while (vm.uiState.value.session == null) {
                vm.retryLoadSession()
                delay(50)
            }
        }

        val sessionBefore = vm.uiState.value.session
        assertNotNull(sessionBefore)
        assertFalse(sessionBefore!!.hasCardioPart())

        // Add a cardio exercise via CardioCatalogItem
        val cardioItem1 = CardioCatalogItem(
            id = "cardio_treadmill",
            name = "Cinta de correr",
            type = CardioType.TREADMILL,
            description = "Cinta",
        )
        vm.addCardioToPart(cardioItem1)

        val updatedSession = vm.uiState.value.session
        assertNotNull(updatedSession)
        assertTrue(updatedSession!!.hasCardioPart())
        assertEquals(1, updatedSession.parts.size)

        val cardioPart = updatedSession.parts.first()
        assertTrue(cardioPart.isCardioPart())
        assertEquals("Espacio de cardio", cardioPart.name)
        assertEquals(1, cardioPart.exercises.size)
        assertTrue(cardioPart.exercises.first().isCardio)

        // Loose strength exercises remain intact and separate
        assertEquals(1, updatedSession.exercises.size)
        assertEquals("str_1", updatedSession.exercises.first().id)

        // Adding a second cardio item appends to the same cardio space
        val cardioItem2 = CardioCatalogItem(
            id = "cardio_bike",
            name = "Bicicleta estática",
            type = CardioType.BIKE_STATIONARY,
            description = "Bici",
        )
        vm.addCardioToPart(cardioItem2)

        val sessionWithTwoCardio = vm.uiState.value.session
        assertNotNull(sessionWithTwoCardio)
        assertEquals(1, sessionWithTwoCardio!!.parts.size)
        assertEquals(2, sessionWithTwoCardio.parts.first().exercises.size)
    }

    @Test
    fun testCardioQuickActionOpensCardioPickerAndReplacesCardioExercise() = runBlocking {
        val programId = "prog_cardio_replace_test"
        val sessionId = "session_replace_test"
        val cardioDetails = com.example.kpkn.data.models.CardioDetails(
            type = CardioType.TREADMILL,
            intensity = com.example.kpkn.data.models.CardioIntensity.MEDIA,
            targetDurationSeconds = 20 * 60,
        )
        val cardioExercise = Exercise(
            id = "cardio_ex_1",
            exerciseDbId = "cardio_treadmill",
            name = "Cinta de correr",
            cardioDetails = cardioDetails,
        )
        val cardioPart = SessionPart(
            id = "part_cardio_1",
            name = "Espacio de cardio",
            isCardioGroup = true,
            exercises = listOf(cardioExercise),
        )
        val initialSession = Session(
            id = sessionId,
            name = "Cardio Session",
            parts = listOf(cardioPart),
        )
        val program = Program(
            id = programId,
            name = "Cardio Program",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week",
                                            name = "Semana",
                                            sessions = listOf(initialSession),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(program)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )

        withTimeout(5_000) {
            while (vm.uiState.value.session == null) {
                vm.retryLoadSession()
                delay(50)
            }
        }

        // Long press opens quick actions
        vm.openExerciseQuickActions(cardioPart.id, cardioExercise.id)
        assertEquals(SessionEditorSheet.QUICK_ACTIONS, vm.uiState.value.sheet)
        assertEquals(cardioPart.id, vm.uiState.value.quickActionsPartId)
        assertEquals(cardioExercise.id, vm.uiState.value.quickActionsExerciseId)

        // Triggering picker replacement from quick actions directs to CARDIO_PICKER
        vm.triggerQuickActionOpenPicker()
        assertEquals(SessionEditorSheet.CARDIO_PICKER, vm.uiState.value.sheet)
        assertEquals(cardioPart.id, vm.uiState.value.pickerTargetPartId)
        assertEquals(cardioExercise.id, vm.uiState.value.pickerTargetExerciseId)

        // Select replacement modality (Row machine)
        val replacementItem = CardioCatalogItem(
            id = "cardio_row",
            name = "Remo ergómetro",
            type = CardioType.ROW_MACHINE,
            description = "Intervalos o trabajo continuo en remo.",
        )
        vm.addCardioToPart(replacementItem)

        // Verify replacement happened in place without duplicating parts or exercises
        val sessionAfterReplace = vm.uiState.value.session
        assertNotNull(sessionAfterReplace)
        assertEquals(1, sessionAfterReplace!!.parts.size)
        val updatedPart = sessionAfterReplace.parts.first()
        assertEquals(1, updatedPart.exercises.size)
        val updatedExercise = updatedPart.exercises.first()
        assertEquals("cardio_ex_1", updatedExercise.id)
        assertEquals("Remo ergómetro", updatedExercise.name)
        assertEquals(CardioType.ROW_MACHINE, updatedExercise.cardioDetails?.type)
        assertEquals(SessionEditorSheet.NONE, vm.uiState.value.sheet)
    }

    @Test
    fun testCardioDetailsIntensityAndTargetGoalFlexibility() {
        // Test optional duration / distance
        val distanceOnly = com.example.kpkn.data.models.CardioDetails(
            type = CardioType.RUN_OUTDOOR,
            targetDurationSeconds = null,
            targetDistanceKm = 5.0,
            intensityLevel = 8,
            intensity = com.example.kpkn.data.models.CardioIntensity.fromLevel(8),
        )
        assertEquals(null, distanceOnly.targetDurationSeconds)
        assertEquals(5.0, distanceOnly.targetDistanceKm)
        assertEquals(8, distanceOnly.resolvedIntensityLevel())
        assertEquals(com.example.kpkn.data.models.CardioIntensity.ALTA, distanceOnly.intensity)

        // Test 1..10 scale mapping
        assertEquals(com.example.kpkn.data.models.CardioIntensity.BAJA, com.example.kpkn.data.models.CardioIntensity.fromLevel(1))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.BAJA, com.example.kpkn.data.models.CardioIntensity.fromLevel(4))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.MEDIA, com.example.kpkn.data.models.CardioIntensity.fromLevel(5))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.MEDIA, com.example.kpkn.data.models.CardioIntensity.fromLevel(6))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.ALTA, com.example.kpkn.data.models.CardioIntensity.fromLevel(7))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.ALTA, com.example.kpkn.data.models.CardioIntensity.fromLevel(8))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.MUY_ALTA, com.example.kpkn.data.models.CardioIntensity.fromLevel(9))
        assertEquals(com.example.kpkn.data.models.CardioIntensity.MUY_ALTA, com.example.kpkn.data.models.CardioIntensity.fromLevel(10))
    }

    @Test
    fun createCardioSpace_opensPickerDirectlyWhenNoCardioExists() = runBlocking {
        val programId = "prog_cardio_placement_dialog"
        val sessionId = "session_placement_dialog"
        seedSimpleSession(programId, sessionId, Session(id = sessionId, name = "Empty Day"))

        val vm = loadEditor(programId, sessionId)
        vm.createCardioSpace()
        assertEquals(SessionEditorSheet.CARDIO_PICKER, vm.uiState.value.sheet)
    }

    @Test
    fun addCardioToPart_respectsStartAndEndPlacement() = runBlocking {
        val programId = "prog_cardio_placement_order"
        val sessionId = "session_placement_order"
        val strengthPart = SessionPart(
            id = "p_strength",
            name = "Pecho",
            exercises = listOf(Exercise(id = "e1", name = "Press")),
        )
        seedSimpleSession(
            programId,
            sessionId,
            Session(id = sessionId, name = "Mix", parts = listOf(strengthPart)),
        )

        val vm = loadEditor(programId, sessionId)
        val cardioItem = CardioCatalogItem(
            id = "cardio_treadmill",
            name = "Cinta",
            type = CardioType.TREADMILL,
            description = "Cinta",
        )

        vm.confirmCardioPlacement(CardioSpacePlacement.START)
        vm.addCardioToPart(cardioItem)
        val afterStart = vm.uiState.value.session
        assertNotNull(afterStart)
        assertTrue(afterStart!!.parts.first().isCardioPart())
        assertEquals("p_strength", afterStart.parts.last().id)

        // Remove cardio and place at end
        vm.updateCurrentSession { s ->
            s.copy(parts = s.parts.filterNot { it.isCardioPart() })
        }
        vm.confirmCardioPlacement(CardioSpacePlacement.END)
        vm.addCardioToPart(cardioItem)
        val afterEnd = vm.uiState.value.session
        assertNotNull(afterEnd)
        assertEquals("p_strength", afterEnd!!.parts.first().id)
        assertTrue(afterEnd.parts.last().isCardioPart())
    }

    @Test
    fun commitStrengthSpace_setsFlag() = runBlocking {
        val programId = "prog_strength_commit"
        val sessionId = "session_strength_commit"
        seedSimpleSession(programId, sessionId, Session(id = sessionId, name = "Empty"))
        val vm = loadEditor(programId, sessionId)
        assertFalse(vm.uiState.value.strengthSpaceCommitted)
        vm.commitStrengthSpace()
        assertTrue(vm.uiState.value.strengthSpaceCommitted)
    }

    @Test
    fun testToggleCardioPlacementFlipsCardioFirst() = runBlocking {
        val programId = "prog_toggle_cardio"
        val sessionId = "session_toggle_cardio"
        val initialSession = Session(
            id = sessionId,
            name = "Mixed",
            exercises = listOf(Exercise(id = "e1", name = "Press")),
            parts = listOf(SessionPart(id = "p_cardio", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(Exercise(id = "c1", name = "Cinta")))),
            cardioFirst = false,
        )
        seedSimpleSession(programId, sessionId, initialSession)
        val vm = loadEditor(programId, sessionId)
        assertEquals(false, vm.uiState.value.session?.cardioFirst)

        vm.toggleCardioPlacement()
        assertEquals(true, vm.uiState.value.session?.cardioFirst)
        assertEquals(CardioSpacePlacement.START, vm.uiState.value.cardioSpacePlacement)

        vm.toggleCardioPlacement()
        assertEquals(false, vm.uiState.value.session?.cardioFirst)
        assertEquals(CardioSpacePlacement.END, vm.uiState.value.cardioSpacePlacement)
    }

    private suspend fun seedSimpleSession(programId: String, sessionId: String, session: Session) {
        val program = Program(
            id = programId,
            name = "Test Program",
            structure = ProgramStructure.SIMPLE,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Block",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week",
                                            name = "Semana",
                                            sessions = listOf(session),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(program)
    }

    private suspend fun loadEditor(programId: String, sessionId: String): SessionEditorViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SessionEditorViewModel(
            application = app,
            programId = programId,
            sessionId = sessionId,
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )
        withTimeout(5_000) {
            while (vm.uiState.value.session == null) {
                vm.retryLoadSession()
                delay(50)
            }
        }
        return vm
    }
}
