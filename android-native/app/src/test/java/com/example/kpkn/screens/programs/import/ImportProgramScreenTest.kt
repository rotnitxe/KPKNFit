package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.ui.entitlements.LocalPlan
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Programas → Importar: un programa de dos semanas no se guarda en Free (aviso de Pro y nada escrito) y sí en Pro.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImportProgramScreenTest {
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
            vm.saveProgram = { program ->
                saved += program
                Result.success(Unit)
            }
            vm.findExistingProgram = { null }
            viewModelStore.put("import-$key", vm)
        }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
    }

    /** Dos semanas: una hoja por semana, cada una con una sentadilla. */
    private fun twoWeeks(): List<ImportSheet> = listOf(
        ImportSheet("Semana 1", listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6"))),
        ImportSheet("Semana 2", listOf(listOf("Ejercicio", "Series", "Reps"), listOf("Sentadilla", "4", "6"))),
    )

    /** Elige una configuración real del catálogo para cada nombre que aún no tenga identidad. */
    private fun decideAll(vm: ImportProgramViewModel) {
        val imported = vm.state.value.imported ?: error("sin programa importado")
        imported.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises } }
            .map { it.rawName }
            .distinct()
            .filterNot { vm.state.value.resolutions[it] != null }
            .forEach { raw ->
                val id = runBlocking { vm.suggestions(raw).firstOrNull()?.configurationId }
                    ?: error("sin sugerencia para $raw")
                vm.resolve(raw, id)
            }
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

    /** El guardado cruza Dispatchers.IO y vuelve al Looper principal de Robolectric. */
    private fun settleSave(vm: ImportProgramViewModel) {
        repeat(1_000) {
            ShadowLooper.idleMainLooper()
            val state = vm.state.value
            if (state.savedProgramId != null || state.error != null || state.notice != null) return
            Thread.sleep(10)
        }
        val state = vm.state.value
        error(
            "el guardado no terminó: pending=${state.draftPersistencePending}; " +
                "error=${state.error}; notice=${state.notice}; id=${state.savedProgramId}",
        )
    }

    /** Ejecuta una operación suspendida desde un hilo aparte mientras Robolectric procesa el Looper principal. */
    private fun <T> runSuspendingOffMain(block: suspend () -> T): T {
        val outcome = AtomicReference<Result<T>?>(null)
        val worker = Thread {
            outcome.set(runCatching { runBlocking { block() } })
        }.apply {
            name = "import-program-test-suspend"
            isDaemon = true
            start()
        }
        repeat(1_000) {
            ShadowLooper.idleMainLooper()
            outcome.get()?.let { return it.getOrThrow() }
            Thread.sleep(10)
        }
        worker.interrupt()
        error("la operación suspendida no terminó")
    }

    private fun settleReviewPersistence(vm: ImportProgramViewModel) {
        settle(vm) { !it.busy && it.notice != IMPORT_MATCHING && !it.draftPersistencePending }
        assertNull("el borrador de revisión debe quedar guardado", vm.state.value.draftPersistenceError)
    }

    @Test
    fun aTwoWeekProgramIsNotSavedOnFreeAndTheProNoticeAppears() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        settleReviewPersistence(vm)

        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.FREE) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        rule.onNodeWithTag("import-save").assertIsEnabled()
        rule.onNodeWithTag("import-save").performClick()
        settleSave(vm)

        assertEquals(IMPORT_PRO_NOTICE, vm.state.value.notice)
        assertNull(vm.state.value.error)
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
    fun theFullSessionEditorWaitsUntilEveryImportedExerciseHasACatalogIdentity() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromSheets(twoWeeks(), "Programa sin resolver")
        settle(vm) { it.imported != null && !it.busy }
        settleReviewPersistence(vm)
        val dayId = vm.state.value.imported!!.weeks.first().days.first().id

        val target = runBlocking { vm.openSessionEditor(1, dayId) }

        assertNull(target)
        assertFalse(vm.state.value.editorStarted)
        assertEquals(importedExerciseCatalogChoiceMessage("Sentadilla"), vm.state.value.error)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun anUnmatchedOccurrenceCanOpenTheSharedCatalogBeforeTheFullEditor() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromText("Día 1\nSentadilla 3x5")
        settle(vm) { it.imported != null && !it.busy }
        settleReviewPersistence(vm)
        val exercise = vm.state.value.imported!!.weeks.single().days.single().exercises.single()

        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }

        rule.onNodeWithTag("import-exercise-catalog-${exercise.id}").assertExists()
        // The unresolved row itself should be an accessible shortcut to the shared catalog.
        rule.onNodeWithTag("import-exercise-${exercise.rawName}").performClick()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Añadir ejercicios").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Añadir ejercicios").assertExists()
        assertFalse("resolver el catálogo no debe guardar en biblioteca", vm.state.value.editorStarted)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun theFullSessionEditorStagesTheCompleteProgramWithoutSavingItToTheLibrary() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Programa importado")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        settleReviewPersistence(vm)
        val dayId = vm.state.value.imported!!.weeks.first().days.first().id

        val target = runBlocking { vm.openSessionEditor(1, dayId) }

        assertEquals("Programa importado", vm.state.value.editorProgram?.name)
        assertEquals("imported:${vm.state.value.editorProgram?.id}", vm.state.value.editorProgram?.planProvenance?.planId)
        assertEquals(4, vm.state.value.editorProgram!!.macrocycles.single().blocks.single()
            .mesocycles.single().weeks.first().sessions.first().exercises.first().sets.size)
        assertTrue(vm.state.value.editorStarted)
        assertTrue(target != null)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun saveIsBlockedWhileADayIsEmptyAndSaysWhichOne() {
        val vm = newViewModel()
        vm.importFromText("Día 1 — Pierna\nSentadilla 4x6 @120kg\nDía 2 — Empuje\nPress banca 4x8 @80kg")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        settleReviewPersistence(vm)
        val pressBanca = vm.state.value.imported!!.weeks[0].days[1].exercises[0]
        vm.edit(ImportEditOp.Remove(pressBanca.id))

        val result = runSuspendingOffMain { vm.save(PlanLevel.PRO) }

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
        settleReviewPersistence(vm)

        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm)
            }
        }
        rule.onNodeWithTag("import-save").assertIsEnabled()
        rule.onNodeWithTag("import-save").performClick()
        settleSave(vm)
        val afterSave = vm.state.value
        assertTrue(
            "No se completó el guardado: error=${afterSave.error}; " +
                "notice=${afterSave.notice}; draftError=${afterSave.draftPersistenceError}; " +
                "pending=${afterSave.draftPersistencePending}; busy=${afterSave.busy}; " +
                "savedId=${afterSave.savedProgramId}; imported=${afterSave.imported != null}; weekdays=${afterSave.weekdays}",
            saved.isNotEmpty(),
        )

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
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }

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

    @Test
    fun pickedCsvIsReadParsedAndShownInTheImportScreen() {
        val vm = newViewModel()
        val csv = File(app.cacheDir, "import-control.csv")
        csv.writeText("Ejercicio;Series;Reps\nSentadilla;3;5\nPress banca;3;8\n")
        vm.importFromPicked(Uri.fromFile(csv))
        settle(vm) { !it.busy && (it.imported != null || it.error != null) }

        assertNull("el archivo de control debe leerse", vm.state.value.error)
        val exercises = vm.state.value.imported!!.weeks.flatMap { it.days }.flatMap { it.exercises }
        assertEquals(listOf("Sentadilla", "Press banca"), exercises.map { it.rawName })
        assertEquals(listOf(3, 3), exercises.map { it.sets })
        assertEquals(listOf(5, 8), exercises.map { it.repsMax })
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithTag("import-review").assertExists()
    }

    @Test
    fun pickedWordDocumentIsReadParsedAndShownInTheImportScreen() {
        val vm = newViewModel()
        val docx = File(app.cacheDir, "import-control.docx")
        val body = com.example.kpkn.data.importer.docxParagraph("Día 1 — Pierna") +
            com.example.kpkn.data.importer.docxTable(
                com.example.kpkn.data.importer.docxRow(com.example.kpkn.data.importer.docxCell("Ejercicio"), com.example.kpkn.data.importer.docxCell("Series"), com.example.kpkn.data.importer.docxCell("Reps")),
                com.example.kpkn.data.importer.docxRow(com.example.kpkn.data.importer.docxCell("Sentadilla"), com.example.kpkn.data.importer.docxCell("4"), com.example.kpkn.data.importer.docxCell("8")),
            )
        docx.writeBytes(com.example.kpkn.data.importer.docxFile(body))
        vm.importFromPicked(Uri.fromFile(docx))
        settle(vm) { !it.busy && (it.imported != null || it.error != null) }
        assertNull("el documento de control debe leerse", vm.state.value.error)
        val exercise = vm.state.value.imported!!.weeks.single().days.single().exercises.single()
        assertEquals("Sentadilla", exercise.rawName)
        assertEquals(4, exercise.sets)
        assertEquals(8, exercise.repsMax)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithTag("import-review").assertExists()
    }

    @Test
    fun parsedExercisesRemainVisibleWhenCatalogCannotBeLoaded() {
        val vm = newViewModel()
        vm.matchNames = { throw IllegalStateException("catálogo no disponible") }
        vm.importFromText("Día 1\nSentadilla 3x5\nPress banca 3x8")
        settle(vm) { !it.busy && it.imported != null && it.notice == IMPORT_CATALOG_UNAVAILABLE }

        val exercises = vm.state.value.imported!!.weeks.flatMap { it.days }.flatMap { it.exercises }
        assertEquals(listOf("Sentadilla", "Press banca"), exercises.map { it.rawName })
        assertTrue(vm.state.value.resolutions.isEmpty())
        assertEquals(IMPORT_CATALOG_UNAVAILABLE, vm.state.value.notice)

        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithText("Sentadilla").assertExists()
        rule.onNodeWithText(IMPORT_CATALOG_UNAVAILABLE).assertExists()
    }

    @Test
    fun parsedProgramAppearsWhileMatchingAndLateResultDoesNotOverwriteAUserDecision() {
        val vm = newViewModel()
        val matching = CompletableDeferred<Map<String, String?>>()
        vm.matchNames = { matching.await() }
        vm.importFromText("Día 1\nSentadilla 3x5")
        settle(vm) { !it.busy && it.imported != null }

        assertEquals(IMPORT_MATCHING, vm.state.value.notice)
        assertEquals("Sentadilla", vm.state.value.imported!!.weeks.single().days.single().exercises.single().rawName)
        vm.resolve("Sentadilla", null)
        matching.complete(mapOf("Sentadilla" to "resultado-tardio"))
        settle(vm) { it.notice != IMPORT_MATCHING }
        assertTrue(vm.state.value.resolutions.containsKey("Sentadilla"))
        assertNull(vm.state.value.resolutions["Sentadilla"])
    }

    @Test
    fun emptyResultDoesNotNeedTheCatalogAndExplainsThatNothingWasFound() {
        val vm = newViewModel()
        vm.matchNames = { error("no se debe cargar el catálogo sin ejercicios") }
        vm.importFromText("Instrucciones generales sin una rutina")
        settle(vm) { !it.busy && it.imported != null }

        assertTrue(vm.state.value.imported!!.weeks.isEmpty())
        assertTrue(vm.state.value.imported!!.warnings.any { it.kind == com.example.kpkn.domain.training.importer.ImportWarningKind.NOTHING_FOUND })
        assertNull(vm.state.value.notice)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithText("No encontramos ejercicios en el contenido leído. Revisa los avisos o prueba otra fuente.").assertExists()
    }

    @Test
    fun failingToReadAnotherFilePreservesThePreviousDraftAndUndo() {
        val vm = newViewModel()
        vm.importFromSheets(twoWeeks(), "Programa anterior")
        settle(vm) { it.imported != null && !it.busy }
        val squat = vm.state.value.imported!!.weeks[0].days[0].exercises[0]
        vm.edit(ImportEditOp.Update(squat.id, ImportExercisePatch(sets = Change.Set(5)), allWeeks = false))
        val before = vm.state.value
        vm.readFile = { _, _ -> Result.failure(IllegalStateException("No se pudo abrir")) }

        vm.importFromPicked(Uri.parse("content://kpkn.test/otro.pdf"))
        settle(vm) { !it.busy && it.error != null }

        assertEquals(before.imported, vm.state.value.imported)
        assertEquals(before.undoLabel, vm.state.value.undoLabel)
        assertEquals("No se pudo abrir", vm.state.value.error)
        vm.undo()
        assertEquals(4, vm.state.value.imported!!.weeks[0].days[0].exercises[0].sets)
    }
}
