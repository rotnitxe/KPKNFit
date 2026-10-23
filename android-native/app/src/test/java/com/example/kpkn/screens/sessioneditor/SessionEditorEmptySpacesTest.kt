package com.example.kpkn.screens.sessioneditor

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioCatalogItem
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.cardioPart
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.sessioneditor.design.SessionModuleEdge
import com.example.kpkn.testsupport.RoomTestRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ED-A · A2, A2b y C1: la lista de una sesión vacía (Fuerza y Cardio, nada más), de una sesión solo de cardio, la colocación «Al inicio /
 * Al final» (solo con los dos espacios) y la superserie dentro del módulo de su grupo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionEditorEmptySpacesTest {

    @get:Rule
    val roomRule = RoomTestRule()

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
        withTimeout(10_000) { while (!repository.isReady.value) delay(25) }
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private fun strength(id: String) = Exercise(id = id, name = "Ejercicio $id", exerciseDbId = id)

    private fun cardio(id: String) = Exercise(id = id, name = "Cardio $id", cardioDetails = CardioDetails(type = CardioType.WALK))

    private fun empty() = Session(id = "s", name = "Sesión nueva")

    // ───────────────────────────── A2 · sesión vacía ─────────────────────────────

    @Test
    fun laSesionVaciaSoloOfreceLasDosOpciones() {
        val items = buildSessionListItems(empty())

        assertEquals(listOf(SessionListItem.Hero, SessionListItem.StrengthAddActions), items)
    }

    @Test
    fun alElegirFuerzaApareceSuEspacioSinColocacion() {
        val items = buildSessionListItems(empty(), showStrengthDivider = true)

        assertEquals(
            listOf(SessionListItem.Hero, SessionListItem.StrengthDivider(showCardioFirst = false, canMove = false), SessionListItem.StrengthAddActions),
            items,
        )
        assertTrue(items.none { it is SessionListItem.CardioPlacement })
    }

    @Test
    fun alElegirCardioApareceSuEspacioVacioYFuerzaQuedaPendiente() {
        val items = buildSessionListItems(empty(), cardioSpaceCommitted = true)

        // Su apartado (con «Añadir cardio») y, debajo, la opción de Fuerza pendiente; ni rótulos ni colocación.
        assertEquals(listOf(SessionListItem.Hero, SessionListItem.CardioAddActions, SessionListItem.StrengthAddActions), items)
    }

    @Test
    fun laColocacionEsElUnicoControlConLosDosEspacios() {
        val both = buildSessionListItems(empty(), showStrengthDivider = true, cardioSpaceCommitted = true, cardioAtStart = false)

        assertEquals(SessionListItem.CardioPlacement, both[1])
        val cardioDivider = both.filterIsInstance<SessionListItem.CardioDivider>().single()
        val strengthDivider = both.filterIsInstance<SessionListItem.StrengthDivider>().single()
        // Un solo control: el selector está a la vista y los rótulos de Fuerza y Cardio no ofrecen «Mover…».
        assertFalse(cardioDivider.canMove)
        assertFalse(strengthDivider.canMove)

        // Solo uno de los dos espacios: no hay colocación.
        assertTrue(buildSessionListItems(empty(), showStrengthDivider = true).none { it is SessionListItem.CardioPlacement })
        assertTrue(buildSessionListItems(empty(), cardioSpaceCommitted = true).none { it is SessionListItem.CardioPlacement })

        // Con ejercicios el selector sigue siendo el único control: la función de mover no se pierde.
        val populated = Session(id = "s", name = "Con ejercicios", exercises = listOf(strength("e1")))
        val populatedItems = buildSessionListItems(populated, cardioSpaceCommitted = true)
        assertTrue(populatedItems.any { it is SessionListItem.CardioPlacement })
        assertTrue(populatedItems.filterIsInstance<SessionListItem.CardioDivider>().none { it.canMove })
        assertTrue(populatedItems.filterIsInstance<SessionListItem.StrengthDivider>().none { it.canMove })
    }

    @Test
    fun laColocacionDecideQueEspacioVaPrimero() {
        val atStart = buildSessionListItems(empty(), showStrengthDivider = true, cardioSpaceCommitted = true, cardioAtStart = true)
        val atEnd = buildSessionListItems(empty(), showStrengthDivider = true, cardioSpaceCommitted = true, cardioAtStart = false)

        fun indexOf(items: List<SessionListItem>, type: Class<*>) = items.indexOfFirst { type.isInstance(it) }
        assertTrue(indexOf(atStart, SessionListItem.CardioDivider::class.java) < indexOf(atStart, SessionListItem.StrengthDivider::class.java))
        assertTrue(indexOf(atEnd, SessionListItem.StrengthDivider::class.java) < indexOf(atEnd, SessionListItem.CardioDivider::class.java))
    }

    // ───────────────────────────── A2b · solo cardio ─────────────────────────────

    @Test
    fun unaSesionSoloDeCardioNoTieneColocacionNiEspacioDeFuerza() {
        val cardioPart = SessionPart(id = "c", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(cardio("c1"), cardio("c2")))
        val items = buildSessionListItems(Session(id = "s", name = "Cardio Z2 + series", parts = listOf(cardioPart)))

        assertTrue(items.none { it is SessionListItem.CardioPlacement })
        assertTrue(items.none { it is SessionListItem.StrengthDivider })
        assertTrue(items.none { it is SessionListItem.CardioDivider })
        // Los cardios primero, «Añadir cardio» y, al final, la opción de Fuerza pendiente.
        assertEquals(
            listOf("c1", "c2"),
            items.filterIsInstance<SessionListItem.PartExercise>().map { it.exerciseId },
        )
        assertTrue(items.indexOfFirst { it is SessionListItem.CardioAddActions } < items.indexOfFirst { it is SessionListItem.StrengthAddActions })
        assertTrue(items.last() is SessionListItem.StrengthAddActions)
    }

    @Test
    fun laColocacionDelCardioDesplegadoSoloExisteConFuerza() {
        val cardioPart = SessionPart(id = "c", name = "Espacio de cardio", isCardioGroup = true, exercises = listOf(cardio("c1")))
        // Solo cardio: no hay colocación que ofrecer, ni arriba ni dentro de la tarjeta del cardio.
        assertFalse(Session(id = "s", name = "Cardio", parts = listOf(cardioPart)).hasStrengthContent())
        // Con un ejercicio suelto o con un grupo de fuerza (aunque esté vacío) sí.
        assertTrue(Session(id = "s", name = "Mixta", exercises = listOf(strength("e1")), parts = listOf(cardioPart)).hasStrengthContent())
        val emptyGroup = SessionPart(id = "g", name = "Grupo 1")
        assertTrue(Session(id = "s", name = "Mixta", parts = listOf(emptyGroup, cardioPart)).hasStrengthContent())
        assertFalse(empty().hasStrengthContent())
    }

    // ───────────────────────────── C1 · la superserie dentro de su grupo ─────────────────────────────

    @Test
    fun laSuperserieDeUnGrupoVaDentroDelModuloDeEseGrupo() {
        val a = strength("a").copy(supersetGroupRef = "ss", supersetId = "ss")
        val b = strength("b").copy(supersetGroupRef = "ss", supersetId = "ss")
        val part = SessionPart(id = "acc", name = "Accesorios", exercises = listOf(a, b))
        val session = Session(
            id = "s",
            name = "Pierna A",
            parts = listOf(part),
            supersetGroups = listOf(SupersetGroup(id = "ss", exerciseOrder = listOf("a", "b"))),
        )

        val items = buildSessionListItems(session)

        // No hay superserie «aparte»: es un ítem del grupo, entre su cabecera y su pie, y comparte su módulo.
        assertTrue(items.none { it is SessionListItem.LooseSuperset })
        val header = items.filterIsInstance<SessionListItem.PartHeader>().single()
        val superset = items.filterIsInstance<SessionListItem.PartSuperset>().single()
        val footer = items.filterIsInstance<SessionListItem.PartAddExercise>().single()
        assertEquals("acc", superset.partId)
        assertEquals(listOf("a", "b"), superset.memberIds)
        assertTrue(items.indexOf(header) < items.indexOf(superset))
        assertTrue(items.indexOf(superset) < items.indexOf(footer))
        assertEquals(SessionModuleEdge.TOP, header.edge)
        assertEquals(SessionModuleEdge.MIDDLE, superset.edge)
        assertEquals(SessionModuleEdge.BOTTOM, footer.edge)
    }

    @Test
    fun unaSuperserieSinGrupoSiEsUnModuloPropio() {
        val a = strength("a").copy(supersetGroupRef = "ss", supersetId = "ss")
        val b = strength("b").copy(supersetGroupRef = "ss", supersetId = "ss")
        val session = Session(
            id = "s",
            name = "Pierna A",
            exercises = listOf(a, b),
            supersetGroups = listOf(SupersetGroup(id = "ss", exerciseOrder = listOf("a", "b"))),
        )

        val loose = buildSessionListItems(session).filterIsInstance<SessionListItem.LooseSuperset>().single()

        assertEquals(SessionModuleEdge.ONLY, loose.edge)
    }

    // ───────────────────────────── el ViewModel: elegir Cardio y la colocación ─────────────────────────────

    private suspend fun loadEmptyEditor(): SessionEditorViewModel {
        val session = Session(id = "empty-session", name = "Sábado")
        repository.addProgram(
            Program(
                id = "prog_empty_spaces",
                name = "Programa",
                structure = ProgramStructure.SIMPLE,
                macrocycles = listOf(
                    Macrocycle(
                        id = "macro",
                        name = "Macro",
                        blocks = listOf(
                            Block(
                                id = "block",
                                name = "Bloque",
                                mesocycles = listOf(
                                    Mesocycle(
                                        id = "meso",
                                        name = "Meso",
                                        weeks = listOf(ProgramWeek(id = "week", name = "Semana", sessions = listOf(session))),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val vm = SessionEditorViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            programId = "prog_empty_spaces",
            sessionId = "empty-session",
            draftWeekId = "week",
            draftMacroIndex = 0,
            draftMesoIndex = 0,
            draftDayOfWeek = null,
        )
        vm.awaitSessionLoaded()
        return vm
    }

    @Test
    fun elegirCardioMuestraSuEspacioSinAbrirElSelectorNiCrearNada() = runBlocking {
        val vm = loadEmptyEditor()
        assertFalse(vm.uiState.value.cardioSpaceCommitted)

        vm.commitCardioSpace()

        assertTrue(vm.uiState.value.cardioSpaceCommitted)
        assertEquals(SessionEditorSheet.NONE, vm.uiState.value.sheet)
        assertNull(vm.uiState.value.session?.cardioPart())
        assertFalse(vm.uiState.value.strengthSpaceCommitted)
    }

    @Test
    fun elPrimerCardioCreaLaParteYLaColocacionSeConserva() = runBlocking {
        val vm = loadEmptyEditor()
        vm.commitCardioSpace()

        vm.addCardioToPart(CardioCatalogItem(id = "cardio_walk", name = "Caminata", type = CardioType.WALK, description = "Caminata"))

        val part = vm.uiState.value.session?.cardioPart()
        assertNotNull(part)
        assertTrue(part!!.isCardioPart())
        assertEquals(1, part.exercises.size)
    }

    @Test
    fun laColocacionCambiaSinAbrirElSelectorDeCardio() = runBlocking {
        val vm = loadEmptyEditor()
        vm.commitStrengthSpace()
        vm.commitCardioSpace()

        vm.setCardioPlacement(CardioSpacePlacement.START)
        assertEquals(true, vm.uiState.value.session?.cardioFirst)
        assertEquals(CardioSpacePlacement.START, vm.uiState.value.cardioSpacePlacement)
        assertEquals(SessionEditorSheet.NONE, vm.uiState.value.sheet)

        vm.setCardioPlacement(CardioSpacePlacement.END)
        assertEquals(false, vm.uiState.value.session?.cardioFirst)
        assertEquals(CardioSpacePlacement.END, vm.uiState.value.cardioSpacePlacement)
        assertEquals(SessionEditorSheet.NONE, vm.uiState.value.sheet)
    }
}
