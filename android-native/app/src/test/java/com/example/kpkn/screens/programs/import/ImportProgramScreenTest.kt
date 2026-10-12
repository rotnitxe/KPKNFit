package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.ui.entitlements.LocalPlan
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun anEditCanBeUndoneAndTheDraftComesBackAsItWas() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        val before = vm.state.value.imported
        val squat = before!!.weeks[0].days[0].exercises[0]

        vm.edit(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(5)), allWeeks = false))
        assertEquals(5, vm.state.value.imported!!.weeks[0].days[0].exercises[0].sets)
        assertEquals("Cambiaste «Sentadilla».", vm.state.value.undoLabel)

        vm.undo()
        assertEquals(before, vm.state.value.imported)
        assertNull(vm.state.value.undoLabel)
    }

    @Test
    fun saveIsBlockedWhileADayIsEmptyAndSaysWhichOne() {
        val vm = newViewModel()
        vm.importFromText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nDía 2 — Empuje\nPress banca 4x8 @80kg")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        val pressBanca = vm.state.value.imported!!.weeks[0].days[1].exercises[0]
        vm.edit(ImportEditOp.Remove(pressBanca.id))

        val result = runBlocking { vm.save(PlanLevel.PRO) }

        assertNull(result)
        assertTrue("con un día vacío no se escribe nada", saved.isEmpty())
        assertEquals("El día «Día 2 — Empuje» de la semana 1 no tiene ejercicios.", vm.state.value.error)
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

    @Test
    fun readingAPdfShowsItsPageAndCancelLeavesTheScreenAsItWas() {
        val vm = newViewModel()
        vm.readFile = { _, onProgress ->
            onProgress(2, 5)
            awaitCancellation()
        }
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, viewModel = vm) }

        vm.importFromPicked(Uri.parse("content://kpkn.test/programa.pdf"))
        rule.onNodeWithText("Leyendo la página 2 de 5…").assertExists()
        rule.onNodeWithTag("import-cancel").performClick()

        rule.onNodeWithText("Leyendo la página 2 de 5…").assertDoesNotExist()
        rule.onNodeWithTag("import-choose-file").assertExists()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test
    fun cancellingANewReadKeepsTheProgramAndItsUndoAsTheyWere() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        val squat = vm.state.value.imported!!.weeks[0].days[0].exercises[0]
        vm.edit(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(5)), allWeeks = false))
        val before = vm.state.value
        vm.readFile = { _, _ -> awaitCancellation() }

        vm.importFromPicked(Uri.parse("content://kpkn.test/otro.xlsx"))
        assertTrue(vm.state.value.busy)
        vm.cancelImport()

        assertEquals(before.imported, vm.state.value.imported)
        assertEquals("Cambiaste «Sentadilla».", vm.state.value.undoLabel)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test
    fun aPhotoIsReadAndTheCameraFileIsDeletedAfterwards() {
        val vm = newViewModel()
        val taken = File(app.cacheDir, "import_photos/rutina-prueba.jpg").apply {
            parentFile?.mkdirs()
            writeText("foto")
        }
        vm.readFile = { _, _ -> Result.success("Foto" to twoWeeks()) }

        vm.importFromPicked(Uri.parse("content://kpkn.test/rutina.jpg"), photo = true, takenFile = taken)
        settle(vm) { it.imported != null && !it.busy }

        assertFalse("la foto de la cámara se borra al leerla", taken.exists())
        assertEquals("Mi programa importado", vm.state.value.title)
    }
}
