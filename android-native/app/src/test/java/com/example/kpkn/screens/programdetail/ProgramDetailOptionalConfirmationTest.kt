package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramCalendarEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regresión VM de la confirmación opcional: la transformación usa el ÚLTIMO
 * programa ([ProgramRepository.mutateProgramNow]), el éxito se anuncia sólo
 * DESPUÉS de que Room tenga la fila y el fallo real de base de datos nunca se
 * anuncia como éxito ni publica el candidato en la caché.
 *
 * Ciclo de vida: un único [ViewModelStore] por test y `clear()` ANTES de
 * cerrar repositorios/base de datos (cancela el `viewModelScope` real).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramDetailOptionalConfirmationTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository
    private val viewModelStore = ViewModelStore()

    private val today: LocalDate = LocalDate.now()

    /** Próxima semana COMPLETA: ancla = lunes estrictamente posterior a hoy. */
    private val anchor: LocalDate = run {
        var candidate = today.plusDays(1)
        while (candidate.dayOfWeek != DayOfWeek.MONDAY) candidate = candidate.plusDays(1)
        candidate
    }

    /** Fechas EXACTAS de ESA semana (martes y jueves), derivadas del ancla. */
    private val tuesday: LocalDate = anchor.plusDays(1)
    private val wednesday: LocalDate = anchor.plusDays(2)
    private val thursday: LocalDate = anchor.plusDays(3)

    private fun nextId(): String = "prog_${System.nanoTime()}"

    private fun optionalSession(sessionId: String, day: LocalDate): Session =
        Session(
            id = sessionId,
            name = "Opcional $sessionId",
            dayOfWeek = day.dayOfWeek.value,
            requirement = SessionRequirement.OPTIONAL,
        )

    /** Opcionales en MARTES y JUEVES de la semana del ancla + una obligatoria el MIÉRCOLES. */
    private fun optionalProgram(id: String): Program =
        Program(
            id = id,
            name = "Opcionales $id",
            structure = ProgramStructure.COMPLEX,
            calendarization = ProgramCalendarization(
                mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
                strictStart = true,
            ),
            schedulePlan = ProgramSchedulePlan(
                anchorDate = anchor.toString(),
                weekStartDay = 1,
                mode = ScheduleMode.DATED,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "${id}_mc",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "${id}_b",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "${id}_m",
                                    name = "Meso",
                                    goal = MesocycleGoal.ACCUMULATION,
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "${id}_w",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                optionalSession("${id}_opt_a", tuesday),
                                                optionalSession("${id}_opt_b", thursday),
                                                Session(
                                                    id = "${id}_req",
                                                    name = "Obligatoria",
                                                    dayOfWeek = wednesday.dayOfWeek.value,
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun vmFor(id: String): ProgramDetailViewModel =
        ProgramDetailViewModel(id).also { viewModelStore.put("detail-$id", it) }

    private suspend fun awaitProgram(id: String) {
        withTimeout(10_000) { while (repository.getProgramById(id) == null) delay(25) }
    }

    /** Espera a que la fila DURABLE exista en Room (el alta es asíncrona). */
    private suspend fun awaitRoomRow(id: String) {
        withTimeout(10_000) { while (repository.databaseForTests().programDao().getById(id) == null) delay(25) }
    }

    /** Espera a que ROOM (no la caché) tenga la fila con sus confirmaciones. */
    private suspend fun awaitRoomConfirmations(id: String, size: Int): List<OptionalSessionConfirmation> {
        withTimeout(10_000) {
            while ((roomConfirmations(id)?.size ?: 0) < size) delay(25)
        }
        return roomConfirmations(id)!!
    }

    private suspend fun roomConfirmations(id: String): List<OptionalSessionConfirmation>? =
        repository.databaseForTests().programDao().getById(id)?.toProgram()?.optionalSessionConfirmations

    private suspend fun awaitMessage(vm: ProgramDetailViewModel) {
        withTimeout(10_000) { while (vm.uiState.value.snackbarMessage == null) delay(25) }
    }

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
        repository.resetAllStateSync()
    }

    @After
    fun tearDown() {
        // Ciclo de vida en orden: el VM PRIMERO (clear cierra viewModelScope
        // real) y sólo después repositorios/base de datos.
        viewModelStore.clear()
        CompetitionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    /**
     * Verificación de fuente de la fixture: [ProgramCalendarEngine.project]
     * arranca en el ANCLA (`start = anchorDate`) y su semana es
     * [anchor, anchor+6], con `dateForDay` resolviendo cada weekday dentro de
     * esa semana ⇒ martes = anchor+1 y jueves = anchor+3 (nunca fuera de la
     * semana ni antes del ancla).
     */
    @Test
    fun `engine places the sessions inside the anchored future week`() {
        val program = optionalProgram("probe")
        val projection = ProgramCalendarEngine.project(program)

        assertTrue("programa calendarizado con ancla", projection.enabled)
        val week = projection.weeks.first()
        assertEquals(anchor, week.startDate)
        assertEquals(anchor.plusDays(6), week.endDate)
        assertEquals(anchor.plusDays(1), week.trainingDayDates[DayOfWeek.TUESDAY.value])
        assertEquals(anchor.plusDays(3), week.trainingDayDates[DayOfWeek.THURSDAY.value])
        assertEquals(anchor.plusDays(2), week.trainingDayDates[DayOfWeek.WEDNESDAY.value])
    }

    @Test
    fun `two rapid toggles of different instances keep both and reach room before the success message`() = runBlocking {
        val id = nextId()
        repository.addProgram(optionalProgram(id))
        awaitProgram(id)
        val vm = vmFor(id)

        vm.toggleOptionalSessionConfirmation(tuesday.toString(), "${id}_opt_a")
        // Segundo toggle ANTES de que el flow eche la primera escritura: la
        // copia vieja no debe pisar la primera confirmación.
        vm.toggleOptionalSessionConfirmation(thursday.toString(), "${id}_opt_b")

        // 1) Fila DURABLE (serializada en Room) con AMBAS confirmaciones.
        val roomRows = awaitRoomConfirmations(id, 2)
        assertEquals(
            setOf("${id}_opt_a" to tuesday.toString(), "${id}_opt_b" to thursday.toString()),
            roomRows.map { it.sessionId to it.dayIso }.toSet(),
        )
        val rawRow = repository.databaseForTests().programDao().getById(id)!!
        assertTrue("la fila serializa las confirmaciones", rawRow.data.contains("${id}_opt_a"))
        assertTrue("la fila serializa las confirmaciones", rawRow.data.contains("${id}_opt_b"))

        // 2) La caché pública refleja exactamente lo mismo que Room.
        assertEquals(
            roomRows.map { it.sessionId to it.dayIso }.toSet(),
            repository.getProgramById(id)!!.optionalSessionConfirmations
                .map { it.sessionId to it.dayIso }.toSet(),
        )
        assertTrue("confirmar opcionales no crea WorkoutLog ficticios", repository.getLogsForProgram(id).isEmpty())

        // 3) El mensaje de éxito llega DESPUÉS de la BD.
        awaitMessage(vm)
        assertTrue(
            vm.uiState.value.snackbarMessage!!.startsWith("Sesión opcional confirmada"),
        )
    }

    @Test
    fun `invalid occurrence fails without a success message and leaves room untouched`() = runBlocking {
        val id = nextId()
        repository.addProgram(optionalProgram(id))
        awaitProgram(id)
        val vm = vmFor(id)

        // Sesión OBLIGATORIA: la ocurrencia no es válida ⇒ aborta sin escribir.
        vm.toggleOptionalSessionConfirmation(wednesday.toString(), "${id}_req")
        awaitMessage(vm)
        val message = vm.uiState.value.snackbarMessage!!
        assertFalse(message.startsWith("Sesión opcional confirmada"))
        assertFalse(message.startsWith("Confirmación retirada"))
        assertTrue(repository.getProgramById(id)!!.optionalSessionConfirmations.isEmpty())
        assertTrue(roomConfirmations(id).isNullOrEmpty())

        // Fecha pasada: ni siquiera se intenta escribir.
        vm.consumeSnackbarMessage()
        vm.toggleOptionalSessionConfirmation(today.minusDays(1).toString(), "${id}_opt_a")
        awaitMessage(vm)
        assertEquals("Sólo se confirman sesiones de fechas futuras.", vm.uiState.value.snackbarMessage)
        assertTrue(repository.getProgramById(id)!!.optionalSessionConfirmations.isEmpty())
        assertTrue(roomConfirmations(id).isNullOrEmpty())
    }

    @Test
    fun `removing one confirmation keeps the other one in room and cache`() = runBlocking {
        val id = nextId()
        repository.addProgram(optionalProgram(id))
        awaitProgram(id)
        val vm = vmFor(id)

        vm.toggleOptionalSessionConfirmation(tuesday.toString(), "${id}_opt_a")
        awaitRoomConfirmations(id, 1)
        vm.toggleOptionalSessionConfirmation(thursday.toString(), "${id}_opt_b")
        awaitRoomConfirmations(id, 2)

        // Se retira SOLO la primera: la otra sobrevive en Room y en caché.
        vm.toggleOptionalSessionConfirmation(tuesday.toString(), "${id}_opt_a")
        withTimeout(10_000) { while (roomConfirmations(id)?.size != 1) delay(25) }
        val remainingRoom = roomConfirmations(id)!!.single()
        assertEquals("${id}_opt_b", remainingRoom.sessionId)
        assertEquals(thursday.toString(), remainingRoom.dayIso)
        val remainingCache = repository.getProgramById(id)!!.optionalSessionConfirmations.single()
        assertEquals(remainingRoom.sessionId, remainingCache.sessionId)
        assertEquals(remainingRoom.dayIso, remainingCache.dayIso)
    }

    @Test
    fun `superseded optional confirmation reports conflict without success`() = runBlocking {
        val id = nextId()
        repository.addProgram(optionalProgram(id))
        awaitProgram(id)
        val vm = vmFor(id)

        repository.durableCommitBeforeWriteForTests = {
            repository.updateProgram(
                repository.getProgramById(id)!!.copy(description = "edición concurrente"),
            )
        }
        try {
            vm.toggleOptionalSessionConfirmation(tuesday.toString(), "${id}_opt_a")
            awaitMessage(vm)
        } finally {
            repository.durableCommitBeforeWriteForTests = null
        }

        val message = vm.uiState.value.snackbarMessage!!
        assertEquals("El programa cambió mientras se actualizaba. Revisa e inténtalo de nuevo.", message)
        assertFalse(message.startsWith("Sesión opcional confirmada"))
        assertFalse(message.startsWith("Confirmación retirada"))
        assertTrue(repository.getProgramById(id)!!.optionalSessionConfirmations.isEmpty())
        withTimeout(10_000) {
            while (repository.databaseForTests().programDao().getById(id)?.toProgram()?.description != "edición concurrente") {
                delay(25)
            }
        }
        assertTrue(roomConfirmations(id)!!.isEmpty())
    }

    @Test
    fun `real database failure never announces success and keeps the viewmodel cache untouched`() = runBlocking {
        val id = nextId()
        repository.addProgram(optionalProgram(id))
        awaitProgram(id)
        // Estado durable previo confirmado ANTES de provocar el fallo.
        awaitRoomRow(id)
        assertEquals(0, roomConfirmations(id)!!.size)

        val vm = vmFor(id)
        // Estado estable del VM antes de medir: programa ya cargado.
        withTimeout(10_000) { while (vm.program.value?.id != id) delay(25) }
        val stateBefore = vm.uiState.value

        // Fallo real de base de datos: la fila ya no puede aceptar escrituras.
        repository.databaseForTests().close()

        vm.toggleOptionalSessionConfirmation(tuesday.toString(), "${id}_opt_a")
        awaitMessage(vm)

        // Sin anuncio de éxito y con mensaje de error, no optimista.
        val message = vm.uiState.value.snackbarMessage!!
        assertFalse(message.startsWith("Sesión opcional confirmada"))
        assertFalse(message.startsWith("Confirmación retirada"))
        // La caché del ViewModel queda IGUAL: sólo cambia el mensaje.
        assertEquals(stateBefore.copy(snackbarMessage = null), vm.uiState.value.copy(snackbarMessage = null))
        // El candidato tampoco se filtró a la caché del repositorio cuando
        // Room rechazó el commit.
        assertTrue(repository.getProgramById(id)!!.optionalSessionConfirmations.isEmpty())
    }
}
