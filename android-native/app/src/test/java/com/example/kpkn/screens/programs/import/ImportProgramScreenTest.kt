package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.ui.entitlements.LocalPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Programas → Importar: un programa de dos semanas no se guarda en Free (aviso de Pro y nada escrito) y sí en Pro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportProgramScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val saved = mutableListOf<Program>()

    private fun newViewModel(): ImportProgramViewModel = ImportProgramViewModel(app).also { vm ->
        vm.saveProgram = { program ->
            saved += program
            Result.success(Unit)
        }
    }

    /** Dos semanas: una hoja por semana, cada una con una sentadilla. */
    private fun twoWeeks(): List<ImportSheet> = listOf(
        ImportSheet("Semana 1", listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6"))),
        ImportSheet("Semana 2", listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6"))),
    )

    /** Deja «propio» todo lo que el emparejador no decidió solo, como haría la persona con «Dejarlo como está». */
    private fun decideAll(vm: ImportProgramViewModel) {
        val imported = vm.state.value.imported ?: error("sin programa importado")
        imported.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .map { it.rawName }
            .distinct()
            .filterNot { vm.state.value.resolutions.containsKey(it) }
            .forEach { vm.resolve(it, null) }
    }

    /** Deja que la lectura del archivo termine: el ViewModel publica en el bucle principal de Robolectric. */
    private fun settle(vm: ImportProgramViewModel, done: (ImportProgramState) -> Boolean) {
        repeat(600) {
            ShadowLooper.idleMainLooper()
            if (done(vm.state.value)) return
            Thread.sleep(10)
        }
        error("la importación no terminó: ${vm.state.value}")
    }

    @Test
    fun aTwoWeekProgramIsNotSavedOnFreeAndTheProNoticeAppears() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)

        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.FREE) {
                ImportProgramScreen(onBack = {}, onSaved = {}, viewModel = vm)
            }
        }
        rule.onNodeWithText("Guardar programa").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { vm.state.value.notice != null }

        rule.onNodeWithText(IMPORT_PRO_NOTICE).assertExists()
        assertTrue("en Free no se escribe nada", saved.isEmpty())
    }

    @Test
    fun aTwoWeekProgramIsSavedOnPro() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)

        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, viewModel = vm)
            }
        }
        rule.onNodeWithText("Guardar programa").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { saved.isNotEmpty() }

        assertEquals(1, saved.size)
        assertEquals("Prueba de dos semanas", saved.single().name)
        assertEquals(null, vm.state.value.notice)
    }
}
