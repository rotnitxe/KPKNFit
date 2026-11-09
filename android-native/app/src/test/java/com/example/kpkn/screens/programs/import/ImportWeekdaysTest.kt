package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.ui.entitlements.LocalPlan
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Los días de entrenamiento de la app: la selección inicial según la semana con más sesiones, que el builder recibe esos días,
 * que sin días no se guarda, y que la línea de ayuda cuenta bien.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportWeekdaysTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()

    /** Cada VM va al store de la prueba: [tearDown] lo limpia y cancela su viewModelScope antes de que acabe la prueba. */
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    private fun newViewModel(): ImportProgramViewModel {
        val key = nextViewModelKey++
        return ImportProgramViewModel(app, File(folder.root, "draft-$key.json")).also { vm ->
            val catalogIds = mapOf(
                "Sentadilla" to "high_bar_back_squat__barbell",
                "Press banca" to "bench_press__barbell",
                "Remo con barra" to "conventional_row__barbell",
            )
            vm.matchNames = { names -> names.mapNotNull { name -> catalogIds[name]?.let { name to it } }.toMap() }
            vm.findExistingProgram = { null }
            vm.saveProgram = { program ->
                saved += program
                Result.success(Unit)
            }
            viewModelStore.put("import-$key", vm)
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    /** Una semana con [sesiones] días, cada uno con un ejercicio distinto. */
    private fun semana(sesiones: Int): String = (1..sesiones).joinToString("\n") { k ->
        "Día $k — ${NOMBRES[k - 1]}\n${EJERCICIOS[k - 1]}"
    }

    private fun leer(vm: ImportProgramViewModel, texto: String) {
        vm.importFromText(texto)
        settle(vm) { it.imported != null && !it.busy && it.notice != IMPORT_MATCHING && !it.draftPersistencePending }
    }

    /** Espera lectura, emparejamiento y persistencia; el ViewModel publica el resultado en el Main Looper. */
    private fun settle(vm: ImportProgramViewModel, done: (ImportProgramState) -> Boolean) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            if (done(vm.state.value)) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    /** Deja «propio» todo lo que el emparejador no decidió solo. */
    private fun decideAll(vm: ImportProgramViewModel) {
        val imported = vm.state.value.imported ?: error("sin programa importado")
        imported.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .map { it.rawName }
            .distinct()
            .filterNot { vm.state.value.resolutions.containsKey(it) }
            .forEach { vm.resolve(it, null) }
    }

    private fun pantalla(vm: ImportProgramViewModel) {
        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
    }

    /** Ejecuta el suspendible en un hilo de fondo mientras Robolectric procesa el Main Looper. */
    private fun <T> runSuspendingWhilePumpingMain(block: suspend () -> T): T {
        val result = CompletableFuture<T>()
        val worker = Thread {
            try {
                result.complete(runBlocking { block() })
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }.apply {
            name = "import-weekdays-save"
            isDaemon = true
            start()
        }

        repeat(6_000) {
            ShadowLooper.idleMainLooper()
            try {
                return result.get(10, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // A VM save can switch to Dispatchers.Main.immediate; keep the Robolectric queue moving.
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
        worker.interrupt()
        throw AssertionError("la operación suspendible no terminó")
    }

    @Test
    fun nombresDeSesionYContadoresRespetanNumeroYGenero() {
        assertEquals("Día 1 · Pierna", importSessionDisplayName(1, "Día 1 — Pierna"))
        assertEquals("Día 2 · Empuje", importSessionDisplayName(2, "Empuje"))
        assertEquals("Día 1", importSessionDisplayName(3, "Día 1"))
        assertEquals("Día 5 · Superior", importSessionDisplayName(4, "Día 5 — Superior"))
        assertEquals("1 día", importCountLabel(1, "día", "días"))
        assertEquals("2 días", importCountLabel(2, "día", "días"))
        assertEquals("1 de 1 sesión revisada", importReviewedSessionsLabel(1, 1))
        assertEquals("0 de 2 sesiones revisadas", importReviewedSessionsLabel(0, 2))
        assertEquals("1 sesión ubicada por semana", importSessionsLocatedLabel(1))
        assertEquals("2 sesiones ubicadas por semana", importSessionsLocatedLabel(2))
    }

    @Test
    fun diasExplicitosPorSemanaSobrevivenAlSelectorGlobal() {
        val draft = com.example.kpkn.domain.training.importer.ProgramImportParser.parseText(
            "Semana 1\nLunes\nSentadilla 3x5\nSemana 2\nJueves\nSentadilla 3x6", "Alternado",
        )
        val builder = com.example.kpkn.domain.training.importer.ImportedProgramBuilder
        val program = builder.build(draft, emptyMap(), listOf(1, 3, 5), "alternado")
        val sourceMap = builder.sourceMap(draft, emptyMap(), program.id)
        val after = program.withImportedWeekdays(setOf(2, 6), explicitImportedSessionIds(draft, sourceMap))
        val weeks = after.macrocycles.single().blocks.single().mesocycles.single().weeks
        assertEquals(listOf(1, 4), weeks.map { it.sessions.single().dayOfWeek })
        assertEquals(listOf(1, 4), draft.weeks.map { it.days.single().dayOfWeek })
    }

    @Test
    fun aplicarElRepartoDeUnaSemanaACadaUnaEsUnSoloCambioReversible() {
        val vm = newViewModel()
        leer(vm, "Semana 1\nLunes\nSentadilla 3x5\nSemana 2\nJueves\nSentadilla 3x6")
        val source = vm.state.value.imported!!
        val firstWeekDay = source.weeks.first().days.single()

        vm.assignImportedDay(firstWeekDay.id, 5)
        settle(vm) { !it.draftPersistencePending }
        vm.applyWeekdayPatternToAllWeeks(1)
        settle(vm) { !it.draftPersistencePending && it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 5 }

        assertEquals(5, vm.state.value.imported!!.weeks[1].days.single().dayOfWeek)
        assertEquals(setOf(5), vm.state.value.weekdays)
        vm.undo()
        settle(vm) { !it.draftPersistencePending && it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 4 }
        assertEquals(4, vm.state.value.imported!!.weeks[1].days.single().dayOfWeek)
        assertEquals(setOf(4, 5), vm.state.value.weekdays)
    }

    @Test
    fun igualarSemanaUnoSoloCambiaLaSemanaDestinoYDeshacerLaRestaura() {
        val vm = newViewModel()
        leer(
            vm,
            "Semana 1\nLunes\nSentadilla 3x5\nSemana 2\nJueves\nPress banca 3x6\nSemana 3\nDomingo\nRemo con barra 3x8",
        )
        vm.assignImportedDay(vm.state.value.imported!!.weeks[0].days.single().id, 5)
        settle(vm) { !it.draftPersistencePending }

        vm.applyWeekdayPatternToWeek(sourceWeekIndex = 1, targetWeekIndex = 2)
        settle(vm) { !it.draftPersistencePending && it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 5 }

        val days = vm.state.value.imported!!.weeks.map { it.days.single().dayOfWeek }
        assertEquals(listOf(5, 5, 7), days)
        vm.undo()
        settle(vm) { !it.draftPersistencePending && it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 4 }
        assertEquals(listOf(5, 4, 7), vm.state.value.imported!!.weeks.map { it.days.single().dayOfWeek })
    }

    @Test
    fun aplicarATodasPideConfirmacionYElCambioSePuedeDeshacer() {
        val vm = newViewModel()
        leer(vm, "Semana 1\nLunes\nSentadilla 3x5\nSemana 2\nJueves\nPress banca 3x6")
        vm.navigateTo(ImportWizardPage.DAYS)
        settle(vm) { !it.draftPersistencePending && !it.busy }
        pantalla(vm)

        rule.onNodeWithTag("import-week-apply-all").performScrollTo().performClick()
        rule.onNodeWithTag("import-week-apply-all-confirmation").assertIsDisplayed()
        assertEquals(4, vm.state.value.imported!!.weeks[1].days.single().dayOfWeek)
        rule.onNodeWithTag("import-week-apply-all-confirm").performScrollTo().performClick()
        settle(vm) { it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 1 && !it.draftPersistencePending }

        assertEquals(1, vm.state.value.imported!!.weeks[1].days.single().dayOfWeek)
        assertEquals("Actualizaste el reparto semanal.", vm.state.value.undoLabel)
        vm.undo()
        settle(vm) { it.imported?.weeks?.getOrNull(1)?.days?.singleOrNull()?.dayOfWeek == 4 && !it.draftPersistencePending }
        assertEquals(4, vm.state.value.imported!!.weeks[1].days.single().dayOfWeek)
    }

    @Test
    fun calendarioNoSugiereDiasCuandoLaFuenteYaLosIndica() {
        val vm = newViewModel()
        leer(vm, "Semana 1\nLunes\nSentadilla 3x5\nSemana 2\nJueves\nPress banca 3x6")
        vm.navigateTo(ImportWizardPage.DAYS)
        settle(vm) { !it.draftPersistencePending && !it.busy }
        pantalla(vm)

        rule.onNodeWithTag("import-week-suggest").assertDoesNotExist()
    }

    @Test
    fun sugerirRepartoSoloCompletaLosDiasSinAsignacionDeFuente() {
        val vm = newViewModel()
        leer(
            vm,
            "Semana 1\nLunes\nSentadilla 3x5\nDía 2 — Empuje\nPress banca 3x8",
        )
        val before = vm.state.value.imported!!.weeks.single().days
        assertEquals(1, before.first().dayOfWeek)
        assertNull(before.last().dayOfWeek)

        vm.suggestWeekdaysForWeek(1)

        val after = vm.state.value.imported!!.weeks.single().days
        assertEquals(1, after.first().dayOfWeek)
        assertEquals(1, after.last().dayOfWeek)
    }

    @Test
    fun unaCabeceraOCRPendienteNoRecibeUnDiaAutomaticoHastaResolverElAviso() {
        val vm = newViewModel()
        vm.importFromSheets(
            listOf(
                ImportSheet(
                    name = "Rutina OCR",
                    rows = listOf(
                        listOf("VIERNES"), listOf("Curl 3x10"),
                        listOf("M0N0AY"), listOf("Press militar 3x8"),
                    ),
                    pageStarts = listOf(0),
                    documentType = ImportDocumentType.PDF,
                ),
            ),
            "Rutina OCR",
        )
        settle(vm) { it.imported != null && !it.busy && !it.draftPersistencePending }
        val imported = vm.state.value.imported!!
        val unassigned = imported.weeks.single().days.last()
        val structureWarning = imported.warnings.single { it.kind == ImportWarningKind.STRUCTURE_REVIEW }

        assertNull(unassigned.dayOfWeek)
        assertNull(effectiveImportedWeekdays(imported, vm.state.value.weekdays)[unassigned.id])

        vm.edit(ImportEditOp.IgnoreWarning(structureWarning.id))
        settle(vm) { !it.draftPersistencePending }
        assertTrue(requireNotNull(effectiveImportedWeekdays(vm.state.value.imported!!, vm.state.value.weekdays)[unassigned.id]) in 1..7)
    }

    @Test
    fun laSeleccionInicialSigueLasSesionesDeLaSemanaConMasDias() {
        val vm = newViewModel()
        val esperado = mapOf(
            1 to setOf(1),
            2 to setOf(1, 4),
            3 to setOf(1, 3, 5),
            4 to setOf(1, 2, 4, 5),
            5 to setOf(1, 2, 3, 4, 5),
            6 to setOf(1, 2, 3, 4, 5, 6),
            7 to setOf(1, 2, 3, 4, 5, 6, 7),
        )
        esperado.forEach { (sesiones, dias) ->
            leer(vm, semana(sesiones))
            assertEquals("con $sesiones sesiones", dias, vm.state.value.weekdays)
        }
    }

    @Test
    fun conTresSesionesElBuilderRecibeLunesMiercolesYViernes() {
        val vm = newViewModel()
        leer(vm, semana(3))
        decideAll(vm)

        val id = runSuspendingWhilePumpingMain { vm.save(PlanLevel.PRO) }

        assertNotNull(id)
        val program = saved.single()
        assertEquals(setOf(1, 3, 5), program.schedulePlan?.trainingDays)
        val sesiones = program.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions
        assertEquals(listOf(1, 3, 5), sesiones.map { it.dayOfWeek })
    }

    @Test
    fun sinDiasNoSeGuardaYLoDiceLaRevision() {
        val vm = newViewModel()
        leer(vm, semana(2))
        decideAll(vm)
        vm.toggleWeekday(1)
        vm.toggleWeekday(4)
        assertTrue(vm.state.value.weekdays.isEmpty())

        val id = runSuspendingWhilePumpingMain { vm.save(PlanLevel.PRO) }

        assertNull(id)
        assertTrue("sin días no se escribe nada", saved.isEmpty())
        assertEquals("Elige al menos un día de entrenamiento.", vm.state.value.error)
    }

    @Test
    fun sinDiasElBotonParaAbrirProgramDetailSeDesactiva() {
        val vm = newViewModel()
        leer(vm, semana(2))
        decideAll(vm)
        vm.toggleWeekday(1)
        vm.toggleWeekday(4)
        vm.navigateTo(ImportWizardPage.DAYS)

        pantalla(vm)

        rule.onNodeWithText("Guardar y abrir programa").assertIsDisplayed()
        rule.onNodeWithTag("import-step-note").assertTextEquals(
            "2 sesiones · 5 descansos · Completa la distribución semanal.",
        )
        rule.onNodeWithTag("import-step-next").assertIsNotEnabled()
    }

    @Test
    fun seleccionarUnaSesionYUnDiaActualizaElRepartoYSePuedeDeshacer() {
        val vm = newViewModel()
        leer(vm, semana(2))
        decideAll(vm)
        vm.navigateTo(ImportWizardPage.DAYS)
        settle(vm) { !it.draftPersistencePending && !it.busy }
        pantalla(vm)

        val days = vm.state.value.imported!!.weeks.single().days
        rule.onNodeWithTag("import-week-planner").assertExists()
        rule.onNodeWithTag("import-week-session-" + days[0].id).performScrollTo().performClick()
        rule.onNodeWithTag("import-weekday-4").performScrollTo().performClick()

        assertEquals(4, vm.state.value.imported!!.weeks.single().days[0].dayOfWeek)
        assertEquals(setOf(4), vm.state.value.weekdays)
        rule.onNodeWithTag("import-week-collisions").assertExists()

        rule.onNodeWithTag("import-week-session-" + days[1].id).performScrollTo().performClick()
        assertEquals(ImportWizardPage.DAYS, vm.state.value.page)
        rule.onNodeWithTag("import-week-selected-session").assertTextEquals("Sesión seleccionada · toca un día de la semana")
        rule.onNodeWithTag("import-weekday-2").performScrollTo().performClick()
        assertEquals(setOf(2, 4), vm.state.value.weekdays)
        assertEquals("Actualizaste el reparto semanal.", vm.state.value.undoLabel)
        vm.undo()
        assertEquals(setOf(4), vm.state.value.weekdays)
        assertEquals(4, vm.state.value.imported!!.weeks.single().days[1].dayOfWeek)
    }

    @Test
    fun laAyudaDeLosDiasCuentaSoloCuandoCuadranOQuedanCortos() {
        assertEquals("3 sesiones en 3 días.", importWeekdaysHelp(sessionsPerWeek = 3, chosen = 3))
        assertEquals("1 sesión en 1 día.", importWeekdaysHelp(sessionsPerWeek = 1, chosen = 1))
        assertEquals("Algunos días tendrán más de una sesión.", importWeekdaysHelp(sessionsPerWeek = 3, chosen = 2))
        assertNull(importWeekdaysHelp(sessionsPerWeek = 3, chosen = 4))
        assertNull(importWeekdaysHelp(sessionsPerWeek = 3, chosen = 0))
    }

    private companion object {
        val NOMBRES = listOf("Pierna", "Empuje", "Tirón", "Torso", "Brazos", "Hombros", "Abdomen")
        val EJERCICIOS = listOf(
            "Sentadilla 4x6 @120kg",
            "Press banca 4x8 @80kg",
            "Remo con barra 3x10 @50kg",
            "Peso muerto 3x5 @140kg",
            "Dominadas 3x8",
            "Curl de bíceps 3x12 @12kg",
            "Elevaciones laterales 3x15 @8kg",
        )
    }
}
