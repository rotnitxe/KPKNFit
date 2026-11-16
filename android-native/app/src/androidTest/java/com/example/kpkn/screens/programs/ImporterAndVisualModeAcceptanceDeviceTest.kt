package com.example.kpkn.screens.programs

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.importer.XlsxReader
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.onboarding.AuthoredPlanMaterializer
import com.example.kpkn.domain.onboarding.AuthoredPlanRequest
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.SYMBOL_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.importer.ImportDocumentType
import com.example.kpkn.domain.training.importer.ImportSheet
import com.example.kpkn.domain.training.importer.ImportWarningKind
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.domain.training.importer.OcrLayout
import com.example.kpkn.domain.training.importer.OcrPage
import com.example.kpkn.domain.training.importer.OcrWord
import com.example.kpkn.domain.training.resolveEffectiveEquipment
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.screens.home.ConceptosClaveScreen
import com.example.kpkn.screens.onboarding.ProgramReadyChoice
import com.example.kpkn.screens.programs.importer.ImportHandoff
import com.example.kpkn.screens.programs.importer.ImportProgramScreen
import com.example.kpkn.screens.programs.importer.ImportProgramViewModel
import com.example.kpkn.screens.programs.importer.ImportRequest
import com.example.kpkn.screens.workout.RecordActionHolder
import com.example.kpkn.screens.workout.applyVoiceEditPatch
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.services.workout.VoiceSetEditPatch
import com.example.kpkn.ui.entitlements.LocalPlan
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * §5.3 + §6 acceptance for:
 * - Importer routes & regressions: Programs -> Nuevo -> Importar -> PasteText handoff, real XLSX multi-sheet parsing + omitted instruction tab, OCR OcrLayout with SABAD0/low-confidence, rapid title edit + duplicate week + undo + close/reopen resume card
 * - Req 8 remaining: Live workout permanent commit (ProgramVersionEntity + ManualSessionOverride preserved across PlanMaterializer.rematerializeWeek), VoiceSetEditPatch, free-session persistence
 * - Sessions & Visual Mode: Queued program persistence across Room restart, ConceptosClaveScreen, live APRE-6 SetInputCardV2
 */
@RunWith(AndroidJUnit4::class)
class ImporterAndVisualModeAcceptanceDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val suffix = System.nanoTime().toString().takeLast(8)
    private val createdDbNames = mutableListOf<String>()
    private val createdDraftFiles = mutableListOf<File>()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.kpkn.dbg", context.packageName)
        runBlocking { initializeExerciseDatabase(context) }
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        ImportHandoff.take()
    }

    @After
    fun tearDown() {
        runCatching { compose.activityRule.scenario.onActivity { it.viewModelStore.clear() } }
        ImportHandoff.take()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        createdDraftFiles.forEach { it.delete() }
        createdDbNames.forEach { dbName ->
            context.deleteDatabase(dbName)
            File(context.getDatabasePath(dbName).path + ".lck").delete()
        }
    }

    private fun openIsolatedRepo(prefix: String): Pair<String, ProgramRepository> {
        val dbName = "$prefix-$suffix-${createdDbNames.size}.db"
        createdDbNames += dbName
        context.deleteDatabase(dbName)
        File(context.getDatabasePath(dbName).path + ".lck").delete()
        val repo = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        runBlocking { withTimeout(30_000) { repo.isReady.first { it } } }
        return dbName to repo
    }

    @Test
    fun importer_programsSheetEntry_multiSheetXlsx_ocrLayout_undoAndResume() {
        val (_, repo) = openIsolatedRepo("qa-importer")
        val draftFile = File(context.cacheDir, "qa-import-$suffix.json").also {
            it.delete()
            createdDraftFiles += it
        }

        val app = context.applicationContext as Application
        val programsVm = ProgramsViewModel(app)
        var importNavigationTriggered = false
        var showImporter by mutableStateOf(false)
        var importVmRef by mutableStateOf<ImportProgramViewModel?>(null)

        compose.setContent {
            KPKNTheme {
                CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                    if (!showImporter) {
                        ProgramsScreen(
                            onNavigateToProgram = {},
                            onCreateProgram = {},
                            onImportProgram = { importNavigationTriggered = true },
                            onEditProgram = {},
                            viewModel = programsVm,
                            openCreateSheetOnStart = true,
                        )
                    } else {
                        val vm = importVmRef ?: remember {
                            ImportProgramViewModel(app, draftFile).apply {
                                matchNames = { emptyMap() }
                                saveProgram = { imported -> repo.addProgramNow(imported) }
                                findExistingProgram = { id -> repo.getProgramById(id) }
                            }
                        }
                        ImportProgramScreen(
                            onBack = {},
                            onSaved = {},
                            onUseCatalogProgram = {},
                            viewModel = vm,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        // 1. Entry route: Programs -> Nuevo -> Importar -> Pegar texto
        compose.onNodeWithTag("new-program-import").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("import-paste-text").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertTrue(importNavigationTriggered)
        assertEquals(ImportRequest.PasteText, ImportHandoff.take())

        // 2. Real XLSX multi-tab workbook + omitted instructions tab
        val xlsxBytes = buildMultiSheetXlsxBytes()
        val sheets = XlsxReader.read(ByteArrayInputStream(xlsxBytes))
        assertEquals(listOf("Semana 1", "Instrucciones", "Semana 2"), sheets.map { it.name })

        val vm1 = ImportProgramViewModel(app, draftFile).apply {
            matchNames = { emptyMap() }
            saveProgram = { imported -> repo.addProgramNow(imported) }
            findExistingProgram = { id -> repo.getProgramById(id) }
        }
        compose.runOnUiThread {
            importVmRef = vm1
            showImporter = true
        }
        compose.waitForIdle()
        vm1.importFromSheets(sheets, "Workbook Multihoja QA")
        compose.waitUntil(15_000) {
            vm1.state.value.imported != null && !vm1.state.value.busy
        }
        val multiDraft = requireNotNull(vm1.state.value.imported)
        assertTrue(multiDraft.weeks.size >= 2)
        assertTrue(multiDraft.warnings.any { it.kind == ImportWarningKind.SHEET_NOTE })

        // 3. OCR OcrLayout with SABAD0 token & split columns
        val ocrPage = OcrPage(
            width = 1000,
            height = 1000,
            words = listOf(
                OcrWord("SABAD0", 40, 60, 200, 95, confidence = 0.42f),
                OcrWord("Torso", 240, 60, 360, 95, confidence = 0.91f),
                OcrWord("Press", 40, 140, 140, 175, confidence = 0.90f),
                OcrWord("banca", 160, 140, 260, 175, confidence = 0.88f),
                OcrWord("3x8", 360, 140, 440, 175, confidence = 0.95f),
            ),
        )
        val ocrRows = OcrLayout.reconstruct(listOf(ocrPage))
        assertTrue(ocrRows.isNotEmpty())
        vm1.importFromSheets(
            listOf(
                ImportSheet(
                    name = "Foto Rutina OCR",
                    rows = ocrRows,
                    documentType = ImportDocumentType.IMAGE,
                    pageStarts = listOf(0),
                ),
            ),
            "Rutina OCR",
        )
        compose.waitUntil(15_000) {
            !vm1.state.value.busy &&
                vm1.state.value.imported?.title == "Rutina OCR"
        }
        compose.runOnUiThread { vm1.navigateTo(ImportWizardPage.EXERCISES) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("import-title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("import-title").performScrollTo().performTextReplacement("Rutina OCR Editada")
        compose.waitForIdle()

        if (compose.onAllNodesWithTag("import-structure-week-duplicate-1").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("import-structure-week-duplicate-1").performScrollTo().performClick()
            compose.waitForIdle()
            assertTrue(vm1.state.value.imported!!.weeks.size >= 2)
            compose.onNodeWithTag("import-undo").performClick()
            compose.waitForIdle()
            assertEquals(1, vm1.state.value.imported!!.weeks.size)
        }

        // Wait for persisted draft flush, then simulate close + recreate ViewModel -> resume card + continue sheet restores
        runBlocking { assertTrue(vm1.flushPendingReview()) }
        val vm2 = ImportProgramViewModel(app, draftFile).apply {
            matchNames = { emptyMap() }
            saveProgram = { imported -> repo.addProgramNow(imported) }
            findExistingProgram = { id -> repo.getProgramById(id) }
        }
        compose.runOnUiThread {
            importVmRef = vm2
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("import-resume-card").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("import-resume-card").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("import-resume-continue").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("import-resume-continue").performClick()
        compose.waitUntil(10_000) {
            vm2.state.value.title.contains("Rutina OCR Editada") &&
                compose.onAllNodesWithTag("import-title").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("import-title").performScrollTo().assertIsDisplayed()
        assertTrue(vm2.state.value.title.contains("Rutina OCR Editada"))
    }

    @Test
    fun req8AndRematerialization_liveCommitVoiceFreeSessionAndPlanMaterializer() {
        runBlocking {
        val (_, repo) = openIsolatedRepo("qa-req8")
        val entry = requireNotNull(PersonalizedPlanCatalog.find("adapted:phul-kpkn-r1"))
        val catalog = requireNotNull(approvedExerciseCatalogV2())
        val availability = EquipmentAvailability(
            categories = EquipmentCategory.entries.toSet(),
            apparatus = EFFECTIVE_EQUIPMENT_KEYS
                .filter { it.category in setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE, EquipmentCategory.SMITH_MACHINE) }
                .associate { it.key to ApparatusPresence.PRESENT },
            supports = EFFECTIVE_EQUIPMENT_KEYS
                .filter { it.category !in setOf(EquipmentCategory.MACHINES, EquipmentCategory.CABLE, EquipmentCategory.SMITH_MACHINE) }
                .associate { it.key to ApparatusPresence.PRESENT } +
                SYMBOL_EQUIPMENT_KEYS.associate { it.key to ApparatusPresence.PRESENT },
            machinesAsCategory = true,
        )
        val options = TrainingOptions(availability = availability)
        val prepared = AuthoredPlanMaterializer.prepare(
            AuthoredPlanRequest(
                entry = entry,
                baseProgram = Program(id = "prog-live-$suffix", name = "PHUL Vivo QA"),
                equipment = options.resolveEffectiveEquipment(emptySet()),
                availability = availability,
                catalog = catalog,
                metadata = CompositionMetadataHolder.resolve(),
                options = options,
            ),
        )
        assertTrue(repo.addProgramNow(prepared).isSuccess)
        val week = prepared.macrocycles.first().blocks.first().mesocycles.first().weeks.first()
        val session = week.sessions.first()
        val extraExercise = Exercise(id = "live-added-$suffix", name = "Curl agregado en vivo", sets = listOf(ExerciseSet("s-live-$suffix", targetReps = 12)))

        val commit = repo.commitLiveProgramSessionEditNow(
            programId = prepared.id,
            weekId = week.id,
            macroIndex = 0,
            mesoIndex = 0,
            sessionId = session.id,
            variant = WeekVariant.A,
            operationId = "op-live-$suffix",
            reason = "Ajuste estructural permanente en sesión",
        ) { currentSession ->
            currentSession.copy(exercises = currentSession.exercises + extraExercise)
        }
        assertNotNull(commit)
        assertTrue(commit!!.versions.any { it.operationId == "op-live-$suffix" })
        assertTrue(commit.program.manualSessionOverrides.any { it.sessionId == session.id })

        // Re-materializing week must preserve manually overridden live session
        val rematerialized = PlanMaterializer.rematerializeWeek(
            program = commit.program,
            weekId = week.id,
            metadata = CompositionMetadataHolder.resolve(),
        )
        val persistedSession = rematerialized.macrocycles.first().blocks.first().mesocycles.first().weeks.first().sessions.first { it.id == session.id }
        assertTrue(persistedSession.exercises.any { it.id == extraExercise.id })

        // Voice patch on completed set
        val patched = applyVoiceEditPatch(
            CompletedSet(id = "cs-$suffix", weight = 80.0, reps = 8),
            VoiceSetEditPatch(weightKg = 102.5, metricValue = 6),
        )
        assertEquals(102.5, patched.weight, 0.01)
        assertEquals(6, patched.reps)

        // Free session OngoingWorkoutState persistence
        val freeSession = Session(id = "free-sess-$suffix", name = "Sesión libre QA", exercises = listOf(extraExercise))
        val freeResult = repo.startWorkout(
            OngoingWorkoutState(
                programId = "",
                session = freeSession,
                startTime = System.currentTimeMillis(),
            ),
        )
        assertEquals(StartWorkoutResult.Started, freeResult)
        }
    }

    @Test
    fun visualModeAndQueue_conceptosClaveQueueAndLiveApreMixed() {
        val (dbName, repo) = openIsolatedRepo("qa-visual")
        val progA = Program(id = "qa-active-$suffix", name = "Programa Activo", macrocycles = emptyList())
        val progB = Program(id = "qa-queued-$suffix", name = "Programa En Cola", macrocycles = emptyList())
        runBlocking {
            assertTrue(repo.addProgramNow(progA).isSuccess)
            assertTrue(repo.addProgramNow(progB).isSuccess)
            ProgramReadyActions.apply(progA.id, ProgramReadyChoice.START_NOW)
            ProgramReadyActions.apply(progB.id, ProgramReadyChoice.QUEUE)
            repo.flushPendingWrites()
            withTimeout(10_000) {
                repo.programQueue.first { progB.id in it }
            }
        }

        // Reopen DB to verify queue durability across restart
        ProgramRepository.closeInstance()
        val reopenedRepo = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        runBlocking {
            withTimeout(30_000) { reopenedRepo.isReady.first { it } }
            assertEquals(progA.id, reopenedRepo.activeProgramState.value?.programId)
            assertTrue(progB.id in reopenedRepo.programQueue.value)
        }

        // ConceptosClaveScreen + live APRE-6 SetInputCardV2
        val apreSets = listOf(
            ExerciseSet("apre-1", targetReps = ApreVariant.APRE_6.set1Reps),
            ExerciseSet("apre-2", targetReps = ApreVariant.APRE_6.set2Reps),
            ExerciseSet("apre-3", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
            ExerciseSet("apre-4", targetReps = 6, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
        )
        val apreExercise = Exercise(
            id = "ex-apre-$suffix",
            name = "Press Militar APRE",
            sets = apreSets,
            apreBlock = ApreBlockReference(xrmReps = 6, setIds = apreSets.map { it.id }),
        )
        assertEquals(ApreVariant.APRE_6, resolveLiveApreBlock(apreExercise)?.variant)

        var showConceptos by mutableStateOf(true)
        compose.setContent {
            KPKNTheme {
                if (showConceptos) {
                    ConceptosClaveScreen(onBack = {})
                } else {
                    SetInputCardV2(
                        exercise = apreExercise,
                        setIndex = 2,
                        currentSet = apreSets[2],
                        ghostSet = null,
                        weightSuggestion = null,
                        onShowHistory = {},
                        onSetBodyWeight = {},
                        initialBodyWeight = 80.0,
                        recordActionHolder = RecordActionHolder(),
                        onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Aprende", substring = true).assertIsDisplayed()
        compose.runOnUiThread { showConceptos = false }
        compose.waitForIdle()
        compose.onNodeWithText("Avanzado").assertIsDisplayed().performClick()
        compose.waitForIdle()
    }

    private fun buildMultiSheetXlsxBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""".trimIndent(),
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""".trimIndent(),
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
                  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""".trimIndent(),
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Semana 1" sheetId="1" r:id="rId1"/>
                    <sheet name="Instrucciones" sheetId="2" r:id="rId2"/>
                    <sheet name="Semana 2" sheetId="3" r:id="rId3"/>
                  </sheets>
                </workbook>""".trimIndent(),
            )
            put(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <numFmts count="0"/>
                  <cellXfs count="1"><xf numFmtId="0"/></cellXfs>
                </styleSheet>""".trimIndent(),
            )
            put(
                "xl/sharedStrings.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="5" uniqueCount="5">
                  <si><t>Lunes - Torso</t></si>
                  <si><t>Press banca 3x5</t></si>
                  <si><t>Lee las instrucciones generales antes de empezar el bloque.</t></si>
                  <si><t>Miércoles - Pierna</t></si>
                  <si><t>Sentadilla 4x6</t></si>
                </sst>""".trimIndent(),
            )
            put(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>1</v></c></row>
                  </sheetData>
                </worksheet>""".trimIndent(),
            )
            put(
                "xl/worksheets/sheet2.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>2</v></c></row>
                  </sheetData>
                </worksheet>""".trimIndent(),
            )
            put(
                "xl/worksheets/sheet3.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>3</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>4</v></c></row>
                  </sheetData>
                </worksheet>""".trimIndent(),
            )
        }
        return out.toByteArray()
    }
}
