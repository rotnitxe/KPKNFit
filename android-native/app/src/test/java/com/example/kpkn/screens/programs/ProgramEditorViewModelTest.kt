package com.example.kpkn.screens.programs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.OptionalSessionConfirmation
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.repository.ProgramRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramEditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(dispatcher)
        ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        withTimeout(10_000) {
            while (!ProgramRepository.getInstance().isReady.value) delay(25)
        }
        ProgramRepository.getInstance().resetAllStateSync()
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private fun repository() = ProgramRepository.getInstance()

    /**
     * Puerto de edición por defecto: delega en `mutateProgramNow` (motor real,
     * transform sobre el ÚLTIMO programa). El orden de parámetros deja la
     * persistencia el ÚLTIMO para que `newViewModel { ... }` siga inyectando
     * el fallo de persistencia de siempre.
     */
    private fun newViewModel(
        edit: (suspend (String, (Program) -> Program?) -> Boolean)? = null,
        persist: (suspend (Program) -> Result<Unit>)? = null,
    ): ProgramEditorViewModel = ProgramEditorViewModel(
        repository(),
        persistProgram = persist ?: { repository().addProgramNow(it) },
        editProgram = edit ?: { programId, transform -> repository().mutateProgramNow(programId, transform) },
    )

    /** Espera a que el guardado en curso (busy) haya terminado. */
    private fun runUntilIdle(vm: ProgramEditorViewModel) = runBlocking {
        withTimeout(10_000) { while (vm.uiState.value.isSaving) delay(25) }
    }

    /** Espera a que la carga inicial del editor haya terminado. */
    private fun waitUntilLoaded(vm: ProgramEditorViewModel) = runBlocking {
        withTimeout(10_000) { while (vm.uiState.value.isLoading) delay(25) }
    }

    /** Espera (con tope) a que Room deje de tener la fila de un programa. */
    private fun waitUntilRoomRowGone(programId: String) = runBlocking {
        withTimeout(10_000) {
            while (repository().databaseForTests().programDao().getById(programId) != null) {
                delay(25)
            }
        }
    }

    @Test
    fun cancel_without_save_persists_nothing() {
        val vm = newViewModel()
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Rutina")
        vm.cancel()

        assertTrue(repository().programs.value.isEmpty())
        assertFalse(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.savedProgramId)
        assertEquals(ProgramMode.HYPERTROPHY, vm.uiState.value.mode)
    }

    @Test
    fun save_new_program_persists_to_room_before_success_and_does_not_activate() {
        val vm = newViewModel()
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Mi rutina")
        vm.setDescription("Descripción guardada")
        vm.setMode(ProgramMode.POWERLIFTING)
        vm.save()
        runUntilIdle(vm)

        val savedId = vm.uiState.value.savedProgramId
        assertNotNull(savedId)
        val created = repository().getProgramById(savedId!!)
        assertNotNull(created)
        assertEquals("Mi rutina", created!!.name)
        // Descripción persistida en el alta nueva (buildNewProgram la recibe).
        assertEquals("Descripción guardada", created.description)
        assertEquals(ProgramMode.POWERLIFTING, created.mode)
        assertEquals(ProgramStructure.SIMPLE, created.structure)
        assertEquals("Macrociclo 1", created.macrocycles.first().name)
        // Éxito SOLO tras fila existente en Room, no solo en la caché en memoria.
        val roomHeaders = runBlocking { repository().databaseForTests().programDao().getAllHeaders() }
        assertTrue(roomHeaders.any { it.id == savedId })
        assertEquals(1, repository().programs.value.size)
        // El alta nueva NO auto-activa: la activación ocurre desde el detalle.
        assertNull(repository().activeProgramState.value)
    }

    @Test
    fun save_failure_keeps_form_and_stable_id_retry_does_not_duplicate() {
        var failRemaining = 1
        val persistedIds = mutableListOf<String>()
        val vm = newViewModel { program ->
            persistedIds += program.id
            if (failRemaining-- > 0) {
                Result.failure(IllegalStateException("disco lleno"))
            } else {
                repository().addProgramNow(program)
            }
        }
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Mi rutina")
        vm.setDescription("Desc")
        vm.save()
        runUntilIdle(vm)

        // Fallo: sin navegación (savedProgramId null), error visible, formulario
        // e id (UUID pendiente) conservados, nada persistido.
        assertFalse(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.savedProgramId)
        assertEquals("disco lleno", vm.uiState.value.error)
        assertEquals("Mi rutina", vm.uiState.value.name)
        assertEquals("Desc", vm.uiState.value.description)
        assertEquals(ProgramMode.HYPERTROPHY, vm.uiState.value.mode)
        assertTrue(repository().programs.value.isEmpty())
        assertEquals(1, persistedIds.size)
        val stableId = persistedIds.first()
        assertEquals(stableId, vm.uiState.value.pendingNewProgramId)
        // El fallo no dejó NINGUNA fila en Room (no solo ausente de la caché).
        val headersAfterFailure = runBlocking {
            repository().databaseForTests().programDao().getAllHeaders()
        }
        assertTrue(headersAfterFailure.none { it.id == stableId })

        // Retry: el MISMO UUID → exactamente una fila, nunca duplica.
        vm.save()
        runUntilIdle(vm)
        assertTrue(vm.uiState.value.isSaved)
        assertEquals(stableId, vm.uiState.value.savedProgramId)
        assertEquals(2, persistedIds.size)
        assertEquals(stableId, persistedIds[1])
        val all = repository().programs.value
        assertEquals(1, all.size)
        assertEquals(stableId, all.first().id)
        // La fila durable existe exactamente una vez.
        val headersAfterRetry = runBlocking {
            repository().databaseForTests().programDao().getAllHeaders()
        }
        assertEquals(1, headersAfterRetry.count { it.id == stableId })
        assertNull(repository().activeProgramState.value)
    }

    @Test
    fun no_success_flag_while_persistence_in_flight() {
        var isSavedWhilePersisting = true
        // El lambda de persistencia NO puede capturar `vm` dentro de su propio
        // inicializador (aún no asignado): portador asignado tras crearlo. Si
        // el portador fuera null el flag conservaría `true` y assertFalse falla.
        var editorDuringPersist: ProgramEditorViewModel? = null
        val vm = newViewModel { program ->
            // Mientras la persistencia está en curso el estado NO está guardado.
            val saved = editorDuringPersist?.uiState?.value?.isSaved
            if (saved != null) isSavedWhilePersisting = saved
            repository().addProgramNow(program)
        }
        editorDuringPersist = vm
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("X")
        vm.save()
        runUntilIdle(vm)

        assertFalse(isSavedWhilePersisting)
        assertTrue(vm.uiState.value.isSaved)
        assertNotNull(vm.uiState.value.savedProgramId)
    }

    @Test
    fun double_save_does_not_duplicate() {
        val vm = newViewModel()
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Rutina")
        vm.save()
        runUntilIdle(vm)
        val firstId = vm.uiState.value.savedProgramId
        assertNotNull(firstId)

        vm.setName("Rutina")
        vm.save() // isSaved → guard sin re-persistir

        assertEquals(firstId, vm.uiState.value.savedProgramId)
        assertEquals(1, repository().programs.value.size)
    }

    @Test
    fun cancel_after_commit_keeps_idempotency() {
        val vm = newViewModel()
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Rutina")
        vm.save()
        runUntilIdle(vm)
        assertTrue(vm.uiState.value.isSaved)

        vm.cancel()

        // Un commit ya realizado no se revoca con cancelar.
        assertTrue(vm.uiState.value.isSaved)
        assertNotNull(vm.uiState.value.savedProgramId)
        assertEquals(1, repository().programs.value.size)
    }

    @Test
    fun save_requires_name() {
        val vm = newViewModel()
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.save()

        assertTrue(repository().programs.value.isEmpty())
        assertFalse(vm.uiState.value.isSaved)
        assertEquals("Escribe un nombre para el programa", vm.uiState.value.error)
    }

    @Test
    fun edit_in_place_updates_same_program_preserving_description() {
        runBlocking {
            repository().addProgramNow(
                buildNewProgram(id = "p-edit", name = "Antes", description = "vieja")
            )
        }
        val vm = newViewModel()
        vm.initialize("p-edit")
        waitUntilLoaded(vm)
        assertEquals("Antes", vm.uiState.value.name)
        assertEquals("vieja", vm.uiState.value.description)
        assertTrue(vm.uiState.value.isEditMode)

        vm.setName("Después")
        vm.setDescription("nueva")
        vm.setCoverImage("gradient://lagoon")
        vm.save()
        runUntilIdle(vm)

        val all = repository().programs.value
        assertEquals(1, all.size)
        val updated = all.first()
        assertEquals("p-edit", updated.id)
        assertEquals("Después", updated.name)
        assertEquals("nueva", updated.description)
        assertEquals("gradient://lagoon", updated.coverImage)
        assertNull(repository().activeProgramState.value)
    }

    @Test
    fun edit_save_room_failure_keeps_cached_program_and_never_reports_success() {
        val original = buildNewProgram(id = "p-edit-room-failure", name = "Persistido", description = "Original")
        runBlocking { repository().addProgramNow(original) }
        val vm = newViewModel()
        vm.initialize(original.id)
        waitUntilLoaded(vm)
        vm.setName("No persistido")

        repository().databaseForTests().close()
        vm.save()
        runUntilIdle(vm)

        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.savedProgramId)
        assertNotNull(vm.uiState.value.error)
        assertFalse("un fallo Room no significa que el programa desapareció", vm.uiState.value.missingProgram)
        assertEquals("No persistido", vm.uiState.value.name)
        assertEquals(original, repository().getProgramById(original.id))
    }

    @Test
    fun edit_conflict_is_not_misreported_as_missing_or_success() {
        val original = buildNewProgram(id = "p-edit-conflict", name = "Original")
        runBlocking { repository().addProgramNow(original) }
        val vm = newViewModel(edit = { targetId, _ ->
            repository().updateProgram(repository().getProgramById(targetId)!!.copy(description = "cambio paralelo"))
            false
        })
        vm.initialize(original.id)
        waitUntilLoaded(vm)
        vm.setName("Nombre del editor")

        vm.save()
        runUntilIdle(vm)

        assertFalse(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.savedProgramId)
        assertFalse(vm.uiState.value.missingProgram)
        assertNotNull(vm.uiState.value.error)
        assertEquals("Nombre del editor", vm.uiState.value.name)
        assertEquals("cambio paralelo", repository().getProgramById(original.id)?.description)
    }

    @Test
    fun missing_program_reports_error_without_writes() {
        val vm = newViewModel()
        vm.initialize("no-existe")
        waitUntilLoaded(vm)
        assertTrue(vm.uiState.value.missingProgram)

        vm.save()

        assertTrue(repository().programs.value.isEmpty())
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun initialize_does_not_report_missing_while_repository_loading() {
        // Repositorio recién creado (su carga desde Room aún en vuelo): NO
        // esperamos isReady a propósito. El editor espera a la carga del repo
        // antes de decidir missing, así que un id inexistente no da falso
        // missing por llegar antes que la carga.
        ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        val vm = newViewModel()
        vm.initialize("fantasma")

        // Determinista: el chequeo de existencia corre SOLO tras esperar
        // isReady (cambio de dispatcher); en este instante la corrutina sigue
        // suspendida en esa espera.
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.missingProgram)

        // Cuando el repositorio termina de cargar, resuelve al estado real.
        waitUntilLoaded(vm)
        assertEquals(false, vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.missingProgram)
    }

    @Test
    fun fields_are_locked_while_saving_and_success_matches_persisted_snapshot() {
        val gate = CompletableDeferred<Unit>()
        val vm = newViewModel { program ->
            gate.await()
            repository().addProgramNow(program)
        }
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Snapshot")
        vm.setDescription("Original")

        vm.save()
        assertTrue(vm.uiState.value.isSaving)

        // Guardado en curso: el formulario NO admite mutaciones, si no el
        // éxito informaría el snapshot persistido mientras la UI muestra
        // cambios nunca guardados.
        vm.setName("Editado sin persistir")
        vm.setDescription("También sin persistir")
        vm.setMode(ProgramMode.POWERLIFTING)
        vm.setCoverImage("gradient://lagoon")

        assertEquals("Snapshot", vm.uiState.value.name)
        assertEquals("Original", vm.uiState.value.description)
        assertEquals(ProgramMode.HYPERTROPHY, vm.uiState.value.mode)
        assertEquals("gradient://ember", vm.uiState.value.coverImage)

        gate.complete(Unit)
        runUntilIdle(vm)

        assertTrue(vm.uiState.value.isSaved)
        val saved = repository().getProgramById(vm.uiState.value.savedProgramId!!)
        assertEquals("Snapshot", saved?.name)
        assertEquals("Original", saved?.description)
        assertEquals(ProgramMode.HYPERTROPHY, saved?.mode)
        assertEquals("gradient://ember", saved?.coverImage)
    }

    @Test
    fun persist_throwing_is_reported_and_retry_reuses_the_same_uuid() {
        var attempts = 0
        val vm = newViewModel { program ->
            attempts += 1
            if (attempts == 1) throw IllegalStateException("disco lleno")
            repository().addProgramNow(program)
        }
        vm.initialize(null)
        waitUntilLoaded(vm)
        vm.setName("Reintento")

        vm.save()
        runUntilIdle(vm)

        // Un throw (no un Result) también termina el busy y expone el error.
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isSaved)
        assertNull(vm.uiState.value.savedProgramId)
        assertEquals("disco lleno", vm.uiState.value.error)
        assertTrue(repository().programs.value.isEmpty())
        val stableId = vm.uiState.value.pendingNewProgramId
        assertNotNull(stableId)

        vm.save()
        runUntilIdle(vm)

        assertTrue(vm.uiState.value.isSaved)
        assertEquals(stableId, vm.uiState.value.savedProgramId)
        assertEquals(2, attempts)
        assertEquals(1, repository().programs.value.count { it.id == stableId })
        assertNull(repository().activeProgramState.value)
    }

    @Test
    fun edit_save_after_program_vanished_reports_missing_without_new_row() {
        runBlocking {
            repository().addProgramNow(
                buildNewProgram(id = "p-gone", name = "Temporal")
            )
        }
        val vm = newViewModel()
        vm.initialize("p-gone")
        waitUntilLoaded(vm)
        assertTrue(vm.uiState.value.isEditMode)

        repository().deleteProgram("p-gone")
        vm.save()
        runUntilIdle(vm)

        // No se resucita un programa en blanco con el id de uno borrado.
        assertFalse(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.missingProgram)
        assertTrue(repository().programs.value.none { it.id == "p-gone" })
        // Room también queda sin fila: el guardado abortado no escribe nada.
        waitUntilRoomRowGone("p-gone")
    }

    /**
     * Carrera de EDICIÓN: entre el snapshot del formulario (initialize) y el
     * commit del guardado entra una mutación REAL de calendario (misma API que
     * el toggle de M6) que añade una confirmación opcional fechada. Guardar
     * solo el nombre debe conservar fecha Y confirmación, porque el transform
     * corre sobre el ÚLTIMO programa y no sobre la copia leída al abrir.
     *
     * Sin sleeps: la barrera es el propio puerto de edición, que se ejecuta
     * exactamente en esa ventana (mutación real y luego el commit del editor).
     */
    @Test
    fun editSaveKeepsCalendarConfirmationAddedBetweenFormSnapshotAndCommit() {
        val programId = "p-edit-calendar"
        val anchorDate = LocalDate.now().plusDays(7).toString()
        val confirmation = OptionalSessionConfirmation(dayIso = anchorDate, sessionId = "opt-editor")
        val schedulePlan = ProgramSchedulePlan(
            anchorDate = anchorDate,
            weekStartDay = 1,
            trainingDays = setOf(1, 3, 5),
            mode = ScheduleMode.DATED,
        )
        val calendarization = ProgramCalendarization(
            mode = ProgramCalendarizationMode.ADVANCED_COMPETITION,
            strictStart = true,
        )

        runBlocking {
            repository().addProgramNow(
                buildNewProgram(id = programId, name = "Antes", description = "vieja")
            )
        }
        assertTrue(
            repository().programs.value.first { it.id == programId }.optionalSessionConfirmations.isEmpty(),
        )

        val vm = newViewModel(edit = { targetId, transform ->
            // Mutación real de calendario que entra DESPUÉS de leer el
            // formulario y ANTES del commit del nombre.
            repository().mutateProgramNow(targetId) { latest ->
                latest.copy(
                    schedulePlan = schedulePlan,
                    calendarization = calendarization,
                    optionalSessionConfirmations = latest.optionalSessionConfirmations + confirmation,
                )
            }
            // El guardado del editor se aplica encima, sobre ese latest.
            repository().mutateProgramNow(targetId, transform)
        })
        vm.initialize(programId)
        waitUntilLoaded(vm)
        assertEquals("Antes", vm.uiState.value.name)
        assertEquals("vieja", vm.uiState.value.description)

        vm.setName("Después")
        vm.save()
        runUntilIdle(vm)

        // Éxito durable y navegación con el id real.
        assertTrue(vm.uiState.value.isSaved)
        assertEquals(programId, vm.uiState.value.savedProgramId)

        val saved = repository().getProgramById(programId)
        // El nombre del formulario se aplicó...
        assertEquals("Después", saved?.name)
        assertEquals("vieja", saved?.description)
        // ...y la mutación intermedia del calendario y la confirmación NO se perdió.
        assertEquals(schedulePlan, saved?.schedulePlan)
        assertEquals(calendarization, saved?.calendarization)
        val confirmations = saved?.optionalSessionConfirmations.orEmpty()
        assertEquals(1, confirmations.size)
        assertEquals(anchorDate, confirmations.single().dayIso)
        assertEquals("opt-editor", confirmations.single().sessionId)

        // Durable en Room, no solo en la caché.
        val roomProgram = runBlocking {
            repository().databaseForTests().programDao().getById(programId)?.toProgram()
        }
        assertEquals("Después", roomProgram?.name)
        assertEquals(schedulePlan, roomProgram?.schedulePlan)
        assertEquals(calendarization, roomProgram?.calendarization)
        assertEquals(anchorDate, roomProgram?.optionalSessionConfirmations?.single()?.dayIso)
        assertEquals("opt-editor", roomProgram?.optionalSessionConfirmations?.single()?.sessionId)
    }
}
