package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.importer.ImportedEditorDraftPersistence
import com.example.kpkn.data.importer.ProgramUrlReader
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.importer.Change
import com.example.kpkn.domain.training.importer.ImportExercisePatch
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportReadingStage
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.ImportWizardSheet
import com.example.kpkn.domain.training.importer.ImportSourceKind
import com.example.kpkn.ui.entitlements.LocalPlan
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

    private fun newViewModel(draftFile: File? = null): ImportProgramViewModel {
        val key = nextViewModelKey++
        return ImportProgramViewModel(app, draftFile ?: File(folder.root, "draft-$key.json")).also { vm ->
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
    fun entryOffersFilePasteCameraGalleryAndPublicUrlSources() {
        val vm = newViewModel()
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }

        rule.onNodeWithTag("import-entry-file").assertExists()
        rule.onNodeWithTag("import-entry-paste").assertExists()
        rule.onNodeWithTag("import-entry-camera").assertExists()
        rule.onNodeWithTag("import-entry-gallery").assertExists()
        rule.onNodeWithTag("import-entry-url").assertExists()
        rule.onNodeWithTag("import-entry-photo-limit").assertExists()
    }

    @Test
    fun importedProgramOpensOneCombinedExerciseAndWeekReview() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromText("Día 1 — Pierna\nSentadilla 3x5")
        settle(vm) { !it.busy && it.imported != null }
        settleReviewPersistence(vm)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }

        assertEquals(ImportWizardPage.EXERCISES, vm.state.value.page)
        rule.onNodeWithTag("import-page-header").assertIsDisplayed()
        rule.onNodeWithText("Confirma las coincidencias.").assertExists()
        rule.onNodeWithText("Distribución semanal").assertExists()
        rule.onNodeWithTag("import-title").assertExists()
        rule.onNodeWithTag("import-page-scroll").performTouchInput { swipeUp() }
        rule.onNodeWithTag("import-page-header").assertIsDisplayed()
        val exercise = vm.state.value.imported!!.weeks.first().days.first().exercises.first()
        rule.onNodeWithTag("import-step-next").assertIsNotEnabled()
        rule.onNodeWithTag("import-match-own-${exercise.id}").performScrollTo().performClick()
        settleReviewPersistence(vm)
        rule.onNodeWithTag("import-step-next").assertIsEnabled()
    }

    @Test
    fun catalogRetryKeepsManualDecisionsAndDoesNotReadTheSourceAgain() {
        val vm = newViewModel()
        vm.matchNames = { error("catálogo temporalmente no disponible") }
        val sourceText = "Día 1 — Pierna\nSentadilla 3x5\nPrensa 3x8"
        vm.importFromText(sourceText)
        settle(vm) { !it.busy && it.imported != null }
        assertEquals(IMPORT_CATALOG_UNAVAILABLE, vm.state.value.notice)
        vm.navigateTo(ImportWizardPage.EXERCISES)
        vm.edit(ImportEditOp.Decide("Prensa", null, "Prensa propia"))
        settleReviewPersistence(vm)

        vm.matchNames = { names ->
            assertEquals(listOf("Sentadilla"), names)
            mapOf("Sentadilla" to "catalog:squat")
        }
        vm.retryCatalogMatching()
        settle(vm) { !it.busy && it.notice == null }
        settleReviewPersistence(vm)

        assertEquals("catalog:squat", vm.state.value.resolutions["Sentadilla"])
        assertTrue(vm.state.value.resolutions.containsKey("Prensa"))
        assertNull(vm.state.value.resolutions["Prensa"])
        assertEquals(sourceText, vm.state.value.sourceEvidence.sourceText)
        assertEquals(ImportWizardPage.EXERCISES, vm.state.value.page)
    }

    @Test
    fun catalogRetryCanRecoverFromTheExerciseMatchingStep() {
        val vm = newViewModel()
        val sourceText = "Día 1 — Pierna\nSentadilla 3x5"
        vm.matchNames = { emptyMap() }
        vm.importFromText(sourceText)
        settle(vm) { !it.busy && it.imported != null }
        vm.navigateTo(ImportWizardPage.EXERCISES)

        vm.matchNames = { error("catálogo temporalmente no disponible") }
        vm.retryCatalogMatching()
        settle(vm) { !it.busy && it.notice == IMPORT_CATALOG_UNAVAILABLE }
        vm.matchNames = { names ->
            assertEquals(listOf("Sentadilla"), names)
            mapOf("Sentadilla" to "catalog:squat")
        }
        vm.retryCatalogMatching()
        settle(vm) { !it.busy && it.notice == null }
        settleReviewPersistence(vm)

        assertEquals(ImportWizardPage.EXERCISES, vm.state.value.page)
        assertEquals("catalog:squat", vm.state.value.resolutions["Sentadilla"])
        assertEquals(sourceText, vm.state.value.sourceEvidence.sourceText)
    }

    @Test
    fun finalReviewMuscleDistributionCountsKnownPrimaryGroupsAndKeepsOwnExercisesUnassigned() {
        val vm = newViewModel()
        vm.matchNames = { emptyMap() }
        vm.importFromSheets(
            listOf(
                ImportSheet(
                    "Semana 1",
                    listOf(
                        listOf("Día 1 — Pierna"),
                        listOf("Ejercicio", "Series", "Reps"),
                        listOf("Sentadilla", "3", "5"),
                        listOf("Press vertical inventado", "2", "8"),
                    ),
                ),
            ),
            title = "Plan de prueba",
        )
        settle(vm) { !it.busy && it.imported != null }
        val suggestion = runBlocking { vm.suggestions("Sentadilla").firstOrNull() }
            ?: error("el catálogo no propuso una sentadilla")
        val unknownName = vm.state.value.imported!!.weeks.single().days.single().exercises
            .single { it.rawName != "Sentadilla" }.rawName
        vm.resolve("Sentadilla", suggestion.configurationId)
        vm.resolve(unknownName, null)
        settleReviewPersistence(vm)

        val distribution = runBlocking { vm.primaryMuscleSeriesForReview() }
            ?: error("no se calculó el reparto muscular")

        assertEquals(3, distribution.groups.sumOf { it.sets })
        assertEquals(1, distribution.unassignedExercises)
        assertTrue(distribution.groups.isNotEmpty())
    }

    @Test
    fun wizardRouteSheetAndSourceEvidenceSurviveViewModelRecreation() {
        val draftFile = File(folder.root, "wizard-route-restoration.json")
        val originalText = "Día 1 — Pierna\nSentadilla 3x5"
        val first = newViewModel(draftFile)
        first.matchNames = { emptyMap() }
        first.importFromText(originalText)
        settle(first) { !it.busy && it.imported != null }
        first.navigateTo(ImportWizardPage.REVIEW)
        settleReviewPersistence(first)
        first.showSheet(ImportWizardSheet.TEXT)
        settleReviewPersistence(first)

        viewModelStore.clear()
        val reopened = newViewModel(draftFile)
        settle(reopened) { it.imported != null || it.error != null }

        assertNull(reopened.state.value.error)
        assertEquals(ImportWizardPage.ENTRY, reopened.state.value.page)
        assertEquals(ImportWizardPage.REVIEW, reopened.state.value.resumePage)
        assertEquals(ImportWizardSheet.TEXT, reopened.state.value.resumeSheet)
        assertEquals(originalText, reopened.state.value.sourceEvidence.sourceText)
        reopened.resumeDraft()
        assertEquals(ImportWizardPage.REVIEW, reopened.state.value.page)
        assertEquals(ImportWizardSheet.TEXT, reopened.state.value.sheet)
    }

    @Test
    fun transientPasteRouteRestoresTheExistingDraftReviewPage() {
        val draftFile = File(folder.root, "transient-paste-route-restoration.json")
        val first = newViewModel(draftFile)
        first.matchNames = { emptyMap() }
        first.importFromText("Día 1 — Pierna\nSentadilla 3x5")
        settle(first) { !it.busy && it.imported != null }
        settleReviewPersistence(first)

        first.navigateTo(ImportWizardPage.PASTE)
        settleReviewPersistence(first)
        assertEquals(ImportWizardPage.EXERCISES, first.state.value.resumePage)

        val persistence = ImportedEditorDraftPersistence(draftFile)
        val persisted = runBlocking { persistence.read().getOrThrow() } ?: error("no se guardó el borrador")
        assertEquals(ImportWizardPage.EXERCISES, persisted.wizardPage)

        // Simulate a legacy draft saved while its non-durable paste form was open.
        val legacyPasteRoute = persisted.copy(
            revision = persisted.revision + 1,
            wizardPage = ImportWizardPage.PASTE,
            wizardSheet = ImportWizardSheet.TEXT,
        )
        runBlocking { persistence.write(legacyPasteRoute, persisted.revision).getOrThrow() }

        viewModelStore.clear()
        val reopened = newViewModel(draftFile)
        settle(reopened) { it.imported != null || it.error != null }

        assertNull(reopened.state.value.error)
        assertEquals(ImportWizardPage.ENTRY, reopened.state.value.page)
        assertEquals(ImportWizardPage.EXERCISES, reopened.state.value.resumePage)
        assertNull(reopened.state.value.resumeSheet)
        reopened.resumeDraft()
        assertEquals(ImportWizardPage.EXERCISES, reopened.state.value.page)
    }

    @Test
    fun legacySummaryDraftResumesAtTheCombinedReview() {
        val draftFile = File(folder.root, "legacy-summary-route.json")
        val first = newViewModel(draftFile)
        first.matchNames = { emptyMap() }
        first.importFromText("Día 1 — Pierna\nSentadilla 3x5")
        settle(first) { !it.busy && it.imported != null }
        first.navigateTo(ImportWizardPage.SUMMARY)
        settleReviewPersistence(first)

        viewModelStore.clear()
        val reopened = newViewModel(draftFile)
        settle(reopened) { it.imported != null || it.error != null }

        assertNull(reopened.state.value.error)
        assertEquals(ImportWizardPage.ENTRY, reopened.state.value.page)
        assertEquals(ImportWizardPage.EXERCISES, reopened.state.value.resumePage)
        reopened.resumeDraft()
        assertEquals(ImportWizardPage.EXERCISES, reopened.state.value.page)
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
        vm.navigateTo(ImportWizardPage.REVIEW)
        settleReviewPersistence(vm)
        rule.onNodeWithTag("import-step-next").assertIsEnabled()
        rule.onNodeWithTag("import-step-next").performClick()

        assertEquals(ImportWizardSheet.PRO, vm.state.value.sheet)
        assertNull(vm.state.value.error)
        rule.onNodeWithTag("import-pro-upgrade").assertDoesNotExist()
        rule.onNodeWithTag("import-pro-unavailable", useUnmergedTree = true).assertExists()
        assertTrue("en Free no se escribe nada", saved.isEmpty())
    }

    @Test
    fun reviewSurvivesViewModelRecreationAndCanBeSaved() {
        val draftFile = File(folder.root, "review-restoration.json")
        val first = newViewModel(draftFile)
        first.matchNames = { emptyMap() }
        first.readUrl = { url ->
            assertEquals("https://training.example/rutina?token=omitido", url)
            ProgramUrlReader.parsePayload(
                bytes = reviewWorkbookXlsx(),
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                fileName = "Rutina de fuerza.xlsx",
                source = URI(url),
            )
        }
        first.importFromUrl("https://training.example/rutina?token=omitido")
        settle(first) { it.imported != null && !it.busy }

        assertEquals("Rutina de fuerza", first.state.value.imported?.sourceTitle)
        assertEquals("https://training.example", first.state.value.imported?.sourceUrl)
        assertEquals("https://training.example", first.state.value.sourceEvidence.sourceUrl)
        val sourceWarningIds = first.state.value.imported!!.warnings.map { it.id }.toSet()
        assertTrue("la nota de procedencia web debe requerir revisión", sourceWarningIds.isNotEmpty())
        first.edit(ImportEditOp.IgnoreAllWarnings)

        val rawName = first.state.value.imported!!.weeks.single().days.single().exercises.single().rawName
        val choice = runBlocking { first.suggestions(rawName).firstOrNull() }
            ?: error("el catálogo no ofreció una opción para $rawName")
        first.resolve(rawName, choice.configurationId)
        first.setTitle("Rutina restaurada")
        val addedWeekday = (1..7).first { it !in first.state.value.weekdays }
        first.toggleWeekday(addedWeekday)
        settleReviewPersistence(first)
        val expectedWeekdays = first.state.value.weekdays

        // Simulate process recreation: cancel the old ViewModel but leave its private durable draft intact.
        viewModelStore.clear()
        val reopened = newViewModel(draftFile)
        settle(reopened) { it.imported != null || it.error != null }

        assertNull("la restauración no debe fallar", reopened.state.value.error)
        assertEquals("Rutina restaurada", reopened.state.value.title)
        assertEquals("Sentadilla", reopened.state.value.imported!!.weeks.single().days.single().exercises.single().rawName)
        assertEquals("Rutina de fuerza", reopened.state.value.imported?.sourceTitle)
        assertEquals("https://training.example", reopened.state.value.imported?.sourceUrl)
        assertTrue(reopened.state.value.imported!!.reviewedWarnings.containsAll(sourceWarningIds))
        assertEquals(choice.configurationId, reopened.state.value.resolutions[rawName])
        assertEquals(expectedWeekdays, reopened.state.value.weekdays)
        assertFalse(reopened.state.value.editorStarted)

        val savedId = runSuspendingOffMain { reopened.save(PlanLevel.PRO) }

        assertTrue("no se guardó: ${reopened.state.value.error}", savedId != null)
        assertEquals(saved.single().id, savedId)
        assertEquals("Rutina restaurada", saved.single().name)
        assertEquals("Rutina de fuerza", saved.single().planProvenance?.sourceTitle)
        assertEquals("https://training.example", saved.single().planProvenance?.sourceUrl)
        assertFalse("el borrador se elimina solo tras confirmar el guardado", draftFile.exists())
    }

    private fun reviewWorkbookXlsx(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.entryText(
                "xl/workbook.xml",
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Rutina" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            zip.entryText(
                "xl/_rels/workbook.xml.rels",
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            )
            zip.entryText(
                "xl/worksheets/sheet1.xml",
                """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Ejercicio</t></is></c><c r="B1" t="inlineStr"><is><t>Series</t></is></c><c r="C1" t="inlineStr"><is><t>Reps</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>Sentadilla</t></is></c><c r="B2"><v>3</v></c><c r="C2"><v>8</v></c></row></sheetData></worksheet>""",
            )
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.entryText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    @Test
    fun structuredDaysSupersetsAndWeekKindsSurviveReviewRestorationAndSave() {
        val draftFile = File(folder.root, "structured-review-restoration.json")
        val first = newViewModel(draftFile)
        first.importFromText(
            """
                Week 1
                Monday
                A1. Bench Press 3x8
                A2. Barbell Row 3x10
                Sunday — Rest
                Week 2 Deload
                Thursday
                Deadlift 2x5
                Week 3 Rest
            """.trimIndent(),
        )
        settle(first) { it.imported != null && !it.busy }
        decideAll(first)
        settleReviewPersistence(first)

        val importedBeforeRestart = requireNotNull(first.state.value.imported)
        assertEquals(
            listOf(WeekExecutionKind.TRAINING, WeekExecutionKind.DELOAD, WeekExecutionKind.REST),
            importedBeforeRestart.weeks.map { it.executionKind },
        )
        assertEquals(listOf("A", "A"), importedBeforeRestart.weeks[0].days[0].exercises.map { it.supersetGroup })
        assertTrue(importedBeforeRestart.weeks[0].days[1].isRestDay)

        viewModelStore.clear()
        val reopened = newViewModel(draftFile)
        settle(reopened) { it.imported != null || it.error != null }

        assertNull("la restauración no debe fallar", reopened.state.value.error)
        val restored = requireNotNull(reopened.state.value.imported)
        assertEquals(importedBeforeRestart.weeks, restored.weeks)
        assertEquals(first.state.value.resolutions, reopened.state.value.resolutions)

        val savedId = runSuspendingOffMain { reopened.save(PlanLevel.PRO) }
        assertEquals(saved.single().id, savedId)
        val savedWeeks = saved.single().macrocycles.single().blocks.single().mesocycles.single().weeks
        assertEquals(
            listOf(WeekExecutionKind.TRAINING, WeekExecutionKind.DELOAD, WeekExecutionKind.REST),
            savedWeeks.map { it.executionKind },
        )

        val firstWeek = savedWeeks.first()
        val trainingSession = firstWeek.sessions.single { it.requirement == SessionRequirement.REQUIRED }
        val restSession = firstWeek.sessions.single { it.requirement == SessionRequirement.OPTIONAL }
        assertEquals(1, trainingSession.dayOfWeek)
        assertEquals(7, restSession.dayOfWeek)
        assertTrue(restSession.exercises.isEmpty())
        val superset = trainingSession.supersetGroups.single()
        assertEquals(trainingSession.exercises.map { it.id }, superset.exerciseOrder)
        assertTrue(trainingSession.exercises.all { it.supersetGroupRef == superset.id })
        assertEquals(4, savedWeeks[1].sessions.single().dayOfWeek)
        assertTrue(savedWeeks[2].sessions.isEmpty())
        assertFalse("el borrador se elimina solo tras confirmar el guardado", draftFile.exists())
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
        var openedProgramDetailId: String? = null
        vm.importFromSheets(twoWeeks(), "Prueba de dos semanas")
        settle(vm) { it.imported != null && !it.busy }
        decideAll(vm)
        settleReviewPersistence(vm)

        rule.setContent {
            CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                ImportProgramScreen(
                    onBack = {},
                    onSaved = { openedProgramDetailId = it },
                    onUseCatalogProgram = {},
                    viewModel = vm,
                )
            }
        }
        vm.navigateTo(ImportWizardPage.REVIEW)
        settleReviewPersistence(vm)
        rule.onNodeWithTag("import-step-next").assertIsEnabled()
        rule.onNodeWithTag("import-step-next").performClick()
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
        rule.waitUntil(timeoutMillis = 5_000) { openedProgramDetailId != null }
        assertEquals(saved.single().id, openedProgramDetailId)
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
        val progress = "Leyendo la página 2 de 5…"
        val visiblePage = "Página 2 de 5"
        settle(vm) { it.progress == progress }
        rule.onNodeWithText(visiblePage).assertExists()
        rule.onNodeWithTag("import-cancel").performClick()
        settle(vm) { !it.busy }

        rule.onNodeWithText(visiblePage).assertDoesNotExist()
        rule.onNodeWithTag("import-entry-file").assertExists()
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
        vm.navigateTo(ImportWizardPage.EXERCISES)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithTag("import-step-next").assertExists()
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
        vm.navigateTo(ImportWizardPage.EXERCISES)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithTag("import-step-next").assertExists()
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

        vm.navigateTo(ImportWizardPage.EXERCISES)
        rule.setContent { ImportProgramScreen(onBack = {}, onSaved = {}, onUseCatalogProgram = {}, viewModel = vm) }
        rule.onNodeWithText("Sentadilla").assertExists()
        rule.onNodeWithText("Catálogo no disponible").assertExists()
        rule.onNodeWithTag("import-match-retry-catalog").assertExists()
    }

    @Test
    fun catalogMatchingIsAVisibleReadingStageAndWaitsForItsResult() {
        val vm = newViewModel()
        val matching = CompletableDeferred<Map<String, String?>>()
        vm.matchNames = { matching.await() }
        vm.importFromText("Día 1\nSentadilla 3x5")
        settle(vm) {
            it.busy && it.page == ImportWizardPage.READING &&
                it.readingProgress?.stage == ImportReadingStage.CATALOG
        }

        assertNull(vm.state.value.imported)
        val readingProgress = requireNotNull(vm.state.value.readingProgress)
        assertEquals(ImportReadingStage.CATALOG, readingProgress.stage)
        assertEquals(1, readingProgress.weeks)
        assertEquals(1, readingProgress.sessions)
        assertEquals(1, readingProgress.exercises)
        matching.complete(mapOf("Sentadilla" to "resultado-tardio"))
        settle(vm) { !it.busy && it.imported != null }
        assertEquals(ImportWizardPage.EXERCISES, vm.state.value.page)
        assertEquals("resultado-tardio", vm.state.value.resolutions["Sentadilla"])
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
        rule.onNodeWithText("Leímos el archivo, pero no encontramos ejercicios.").assertExists()
        rule.onNodeWithTag("import-empty-show-text").assertExists()
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
        assertEquals(ImportSourceKind.FILE, vm.state.value.sourceEvidence.kind)
        assertEquals("Archivo seleccionado", vm.state.value.sourceEvidence.displayName)
        assertNull(vm.state.value.sourceEvidence.sourceText)
        vm.undo()
        assertEquals(4, vm.state.value.imported!!.weeks[0].days[0].exercises[0].sets)
    }
}
