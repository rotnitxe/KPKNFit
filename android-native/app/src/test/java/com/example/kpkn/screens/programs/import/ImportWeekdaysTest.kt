package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.example.kpkn.ui.entitlements.LocalPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
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

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()

    /** Cada VM va al store de la prueba: [tearDown] lo limpia y cancela su viewModelScope antes de que acabe la prueba. */
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    private fun newViewModel(): ImportProgramViewModel = ImportProgramViewModel(app).also { vm ->
        vm.saveProgram = { program ->
            saved += program
            Result.success(Unit)
        }
        viewModelStore.put("import-${nextViewModelKey++}", vm)
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
        settle(vm) { it.imported != null && !it.busy }
    }

    /** Deja que la lectura termine: el ViewModel publica en el bucle principal de Robolectric. */
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

        val id = runBlocking { vm.save(PlanLevel.PRO) }

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

        val id = runBlocking { vm.save(PlanLevel.PRO) }

        assertNull(id)
        assertTrue("sin días no se escribe nada", saved.isEmpty())
        assertEquals("Elige al menos un día de entrenamiento.", vm.state.value.error)
    }

    @Test
    fun sinDiasElBotonDeGuardarSeDesactivaYLaRevisionLoDice() {
        val vm = newViewModel()
        leer(vm, semana(2))
        decideAll(vm)
        vm.toggleWeekday(1)
        vm.toggleWeekday(4)

        pantalla(vm)

        rule.onNodeWithTag("import-review-weekdays").assertTextEquals("Elige al menos un día de entrenamiento.")
        rule.onNodeWithText("Guardar programa").assertIsNotEnabled()
    }

    @Test
    fun tocarUnDiaLoMarcaYLaAyudaLoCuenta() {
        val vm = newViewModel()
        leer(vm, semana(2))
        decideAll(vm)
        pantalla(vm)

        rule.onNodeWithTag("import-weekdays-help").assertTextEquals("2 sesiones en 2 días.")
        // G3: los días de entrenamiento viven en el contenido, tras la revisión, no en la barra de abajo.
        rule.onNodeWithTag("import-weekday-4").performScrollTo().performClick()

        assertEquals(setOf(1), vm.state.value.weekdays)
        rule.onNodeWithTag("import-weekdays-help").assertTextEquals("Algunos días tendrán más de una sesión.")
        rule.onNodeWithTag("import-weekday-2").performScrollTo().performClick()
        assertEquals(setOf(1, 2), vm.state.value.weekdays)
        rule.onNodeWithTag("import-weekdays-help").assertTextEquals("2 sesiones en 2 días.")
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
