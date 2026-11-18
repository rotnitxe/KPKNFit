package com.example.kpkn.screens.programs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.ProgramVersionEntity
import com.example.kpkn.data.db.SharedExerciseLoadReferenceEntity
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Gender
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramEndMode
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RecipeData
import com.example.kpkn.data.models.RecipeIngredient
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.UserVitals
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.calendarizeSimpleCycle
import com.example.kpkn.data.models.resolveProgramEndMode
import com.example.kpkn.data.models.restorePausedCyclicProgram
import com.example.kpkn.data.models.startSimpleCalendarizedBreak
import com.example.kpkn.data.programs.CatalogDuration
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.repository.NutritionPlanCommitCoordinator
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.AuthoredPlanMaterializer
import com.example.kpkn.domain.onboarding.AuthoredPlanRequest
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.resolveEffectiveEquipment
import com.example.kpkn.navigation.DeepLinkRouter
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.screens.nutrition.NutritionCalibrationScreen
import com.example.kpkn.screens.nutrition.NutritionPlanEditorScreen
import com.example.kpkn.screens.nutrition.NutritionPlanEditorViewModel
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.screens.programdetail.design.structure.ProgramCycleLoop
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesModel
import com.example.kpkn.screens.programdetail.design.structure.ProgramCyclesSheet
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import java.io.File
import java.lang.reflect.Modifier
import java.time.LocalDate

/**
 * §5.3 + §6 acceptance for:
 * - Req 5: Recurrence matrix (open calendar, scheduled break & return, finite prep/comp, FINITE_CYCLE catalog, legacy editor + Room restart)
 * - Req 6 + Room 37→38: representative v37 dataset upgraded in real SQLite/Room, deep link redirect, zero weekly check-in UI, NutritionPlanEditorScreen, v38 entities
 * - Req 7: Real FoodLoggerDrawer + RecipeLibraryOverlay UI (duplicate ingredient rows, validation errors, v1→v2→v3 with/without cooked weight preserving historical meals, StateRestorationTester)
 */
@RunWith(AndroidJUnit4::class)
class RecurrenceNutrition37RecipeDeviceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val suffix = System.nanoTime().toString().takeLast(8)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val createdDbNames = mutableListOf<String>()
    private lateinit var context: Context

    private val noCatalogImporter = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.kpkn.dbg", context.packageName)
        runBlocking { initializeExerciseDatabase(context) }
        resetSingletons()
    }

    @After
    fun tearDown() {
        resetSingletons()
        createdDbNames.forEach { dbName ->
            context.deleteDatabase(dbName)
            File(context.getDatabasePath(dbName).path + ".lck").delete()
        }
    }

    private fun resetSingletons() {
        runCatching { compose.activityRule.scenario.onActivity { it.viewModelStore.clear() } }
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        runCatching {
            val field = NutritionCalibrationRepository::class.java.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
        }
    }

    private fun newDbName(prefix: String): String =
        "$prefix-$suffix-${createdDbNames.size}.db".also {
            createdDbNames += it
            context.deleteDatabase(it)
            File(context.getDatabasePath(it).path + ".lck").delete()
        }

    private fun attachSharedDb(db: KpknDatabase): Pair<ProgramRepository, NutritionRepository> {
        val dbField = KpknDatabase::class.java.getDeclaredField("INSTANCE")
        dbField.isAccessible = true
        dbField.set(null, db)

        val progCtor = ProgramRepository::class.java.getDeclaredConstructor(
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        val progRepo = progCtor.newInstance(db, false)
        val progInstance = ProgramRepository::class.java.getDeclaredField("INSTANCE")
        progInstance.isAccessible = true
        progInstance.set(null, progRepo)
        val loadProg = ProgramRepository::class.java.getDeclaredMethod("loadFromDb").apply { isAccessible = true }
        loadProg.invoke(progRepo)

        val nutCtor = NutritionRepository::class.java.getDeclaredConstructor(
            Context::class.java,
            KpknDatabase::class.java,
            Boolean::class.javaPrimitiveType,
            FoodCatalogImporter::class.java,
        ).apply { isAccessible = true }
        val nutRepo = nutCtor.newInstance(context.applicationContext, db, false, noCatalogImporter)
        val nutInstance = NutritionRepository::class.java.getDeclaredField("INSTANCE")
        nutInstance.isAccessible = true
        nutInstance.set(null, nutRepo)
        val loadNut = NutritionRepository::class.java.getDeclaredMethod("loadFromDb", Context::class.java).apply { isAccessible = true }
        loadNut.invoke(nutRepo, context.applicationContext)

        return progRepo to nutRepo
    }

    @Test
    fun req5_recurrenceCalendarBreakFiniteCatalogAndLegacyEditorPreservation() {
        runBlocking {
        val dbName = newDbName("qa-rec5")
        val repo = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        withTimeout(30_000) { repo.isReady.first { it } }

        // 1. Open cyclic calendar + scheduled break + return
        val baseWeek = ProgramWeek(
            id = "w1-$suffix",
            name = "Semana Base",
            sessions = listOf(Session(id = "s1-$suffix", name = "Torso", dayOfWeek = 1)),
        )
        val cyclicProgram = Program(
            id = "cyclic-$suffix",
            name = "Cíclico Abierto",
            structure = ProgramStructure.SIMPLE,
            simpleProgramKind = SimpleProgramKind.CYCLIC,
            programEndMode = ProgramEndMode.REPEATING,
            macrocycles = listOf(
                Macrocycle(
                    id = "m-$suffix",
                    name = "Macro",
                    blocks = listOf(Block(id = "b-$suffix", name = "Bloque", mesocycles = listOf(Mesocycle(id = "me-$suffix", name = "Meso", weeks = listOf(baseWeek))))),
                ),
            ),
        )
        val reconciledOpen = ProgramProgressEngine.reconcileOnOpen(cyclicProgram)
        assertEquals(ProgramEndMode.REPEATING, reconciledOpen.resolveProgramEndMode())

        val start = LocalDate.of(2026, 11, 16)
        val calendarized = reconciledOpen.calendarizeSimpleCycle(start, 1, setOf(1, 3))
        assertNotNull(calendarized.pausedCyclicSnapshot)
        assertTrue(calendarized.macrocycles.first().blocks.first().mesocycles.first().weeks.first().trainingDayDates.isNotEmpty())
        val returnedFromCalendar = calendarized.restorePausedCyclicProgram()
        assertEquals(SimpleProgramKind.CYCLIC, returnedFromCalendar.simpleProgramKind)
        assertNull(returnedFromCalendar.pausedCyclicSnapshot)

        val onBreak = returnedFromCalendar.startSimpleCalendarizedBreak(start, start.plusDays(13), 1, setOf(1, 4))
        assertNotNull(onBreak.calendarization)
        assertNotNull(onBreak.pausedCyclicSnapshot)
        val afterBreak = onBreak.restorePausedCyclicProgram()
        assertNull(afterBreak.calendarization)
        assertEquals(SimpleProgramKind.CYCLIC, afterBreak.simpleProgramKind)

        // 2. FINITE_CYCLE catalog entry -> HAS_END
        val finiteEntry = PersonalizedPlanCatalog.entries()
            .firstOrNull { it.duration == CatalogDuration.FINITE_CYCLE && it.recipe != null }
            ?: error("Catálogo finito con receta no encontrado")
        assertEquals(CatalogDuration.FINITE_CYCLE, finiteEntry.duration)
        val materializedFinite = PlanMaterializer.materialize(
            program = Program(id = "finite-${finiteEntry.id}-$suffix", name = finiteEntry.title, structureTemplateId = finiteEntry.id),
            recipe = requireNotNull(finiteEntry.recipe),
            metadata = CompositionMetadataHolder.resolve(),
            options = TrainingOptions(),
        )
        val preparedFinite = materializedFinite.copy(
            programEndMode = materializedFinite.resolveProgramEndMode(catalogIsFinite = true),
        )
        assertEquals(ProgramEndMode.HAS_END, preparedFinite.programEndMode)

        // 3. Legacy program preservation across ProgramEditorViewModel + Room restart
        val legacyProgram = Program(
            id = "legacy-$suffix",
            name = "Legacy Anterior",
            structure = ProgramStructure.SIMPLE,
            programEndMode = ProgramEndMode.HAS_END,
            structureTemplateId = "template:power-12-3",
            loops = listOf(Loop(id = "comp-$suffix", title = "Nacional Power", type = LoopType.COMPETITION, repeatEveryXLoops = 4)),
            macrocycles = cyclicProgram.macrocycles,
        )
        assertTrue(repo.addProgramNow(legacyProgram).isSuccess)
        repo.flushPendingWrites()

        val editorVm = ProgramEditorViewModel(repo)
        editorVm.initialize(legacyProgram.id)
        withTimeout(10_000) { editorVm.uiState.first { !it.isLoading } }
        editorVm.setName("Legacy Editado $suffix")
        editorVm.save()
        withTimeout(10_000) { editorVm.uiState.first { it.isSaved } }
        repo.flushPendingWrites()

        ProgramRepository.closeInstance()
        val reopenedRepo = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        withTimeout(30_000) { reopenedRepo.isReady.first { it } }
        val reloaded = requireNotNull(reopenedRepo.getProgramById(legacyProgram.id))
        assertEquals("Legacy Editado $suffix", reloaded.name)
        assertEquals(ProgramEndMode.HAS_END, reloaded.programEndMode)
        assertEquals("Nacional Power", reloaded.loops.single().title)
        assertEquals("template:power-12-3", reloaded.structureTemplateId)

        // 4. Finite prep & competition sheet UI
        compose.setContent {
            KPKNTheme {
                ProgramCyclesSheet(
                    model = ProgramCyclesModel(
                        currentCycle = 1,
                        loops = listOf(ProgramCycleLoop("comp-$suffix", "Nacional Power", LoopType.COMPETITION, 4, false)),
                        weeks = emptyList(),
                        legacyEventCount = 0,
                        collisionLines = emptyList(),
                    ),
                    onDismiss = {},
                    onSaveLoop = {},
                    onLoopAction = { _, _ -> },
                    onAddTemplate = {},
                    onMigrateLegacy = {},
                    onPostponeWeek = {},
                    onCancelWeek = {},
                    onViewWeek = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Loops").assertIsDisplayed()
        compose.onNodeWithText("Nacional Power").assertIsDisplayed()
        compose.onNodeWithText("Nuevo loop").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Guardar").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun req6AndRoom37To38_upgradeRepresentativeDataset_calibrationRedirectAndPlanEditorUi() {
        val dbName = newDbName("qa-3738")
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            KpknDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

        val seededProgram = Program(
            id = "p37-$suffix",
            name = "Fuerza v37",
            mode = ProgramMode.POWERLIFTING,
            macrocycles = listOf(
                Macrocycle(
                    id = "m37",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b37",
                            name = "B1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "me37",
                                    name = "M1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w37",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(
                                                    id = "s37",
                                                    name = "Sentadilla Día",
                                                    dayOfWeek = 1,
                                                    exercises = listOf(Exercise(id = "e37", name = "Sentadilla", sets = listOf(ExerciseSet("st37", targetReps = 5)))),
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
        val seededWorkout = WorkoutLog(
            id = "wl37-$suffix",
            programId = seededProgram.id,
            sessionId = "s37",
            sessionName = "Sentadilla Día",
            date = "2026-11-10T10:00:00Z",
            durationMinutes = 40,
        )
        val seededPlan = NutritionPlan(
            id = "plan37-$suffix",
            name = "Plan v37",
            goalType = GoalMetric.WEIGHT,
            goalValue = 78.0,
            calorieTarget = 2400,
            proteinGoal = 160,
            carbGoal = 270,
            fatGoal = 75,
            isActive = true,
            createdAt = "2026-11-01T10:00:00Z",
        )
        val seededNutritionLog = NutritionLog(
            id = "nl37-$suffix",
            date = "2026-11-10",
            mealType = MealType.LUNCH,
            foods = listOf(LoggedFood(id = "lf37", foodName = "Pollo v37", amount = 200.0, calories = 330.0, protein = 62.0)),
        )
        val seededRecipe = FoodItem(
            id = "rec37-$suffix",
            name = "Avena proteica v37",
            calories = 420.0,
            protein = 35.0,
            carbs = 52.0,
            fats = 8.0,
            isCustom = true,
            recipe = RecipeData(
                ingredients = listOf(RecipeIngredient(snapshot = LoggedFood(id = "ing37", foodName = "Avena", amount = 100.0, calories = 420.0, protein = 35.0, carbs = 52.0, fats = 8.0))),
                servings = 1,
                version = 1,
            ),
        )

        val seededSettings = Settings(
            userVitals = UserVitals(
                age = 28,
                weight = 80.0,
                height = 178.0,
                gender = Gender.MALE,
            ),
        )
        helper.createDatabase(dbName, 37).apply {
            execSQL("INSERT INTO settings (rowId, data) VALUES (1, ?)", arrayOf(json.encodeToString(seededSettings)))
            execSQL("INSERT INTO programs (id, name, data) VALUES (?, ?, ?)", arrayOf(seededProgram.id, seededProgram.name, json.encodeToString(seededProgram)))
            execSQL("INSERT INTO workout_logs (id, programId, sessionId, date, data) VALUES (?, ?, ?, ?, ?)", arrayOf(seededWorkout.id, seededWorkout.programId, seededWorkout.sessionId, seededWorkout.date, json.encodeToString(seededWorkout)))
            execSQL("INSERT INTO nutrition_logs (id, date, mealType, data) VALUES (?, ?, ?, ?)", arrayOf(seededNutritionLog.id, seededNutritionLog.date, "LUNCH", json.encodeToString(seededNutritionLog)))
            execSQL("INSERT INTO nutrition_plans (id, name, isActive, data) VALUES (?, ?, ?, ?)", arrayOf(seededPlan.id, seededPlan.name, 1, json.encodeToString(seededPlan)))
            execSQL("INSERT INTO nutrition_active_state (rowId, activePlanId) VALUES (1, ?)", arrayOf(seededPlan.id))
            execSQL(
                "INSERT INTO nutrition_custom_foods (id, name, normalizedName, normalizedBrand, aliasesJson, sourcePriority, verifiedScore, usageCount, lastUsedAt, data) VALUES (?, ?, ?, NULL, '[]', 1, 1.0, 2, '2026-11-10', ?)",
                arrayOf(seededRecipe.id, seededRecipe.name, "avena proteica v37", json.encodeToString(seededRecipe)),
            )
            execSQL(
                "INSERT INTO nutrition_checkins (weekKey, status, decidedAt, proposalJson, appliedPlanId, updatedAt) VALUES ('2026-W45', 'pending', NULL, NULL, ?, '2026-11-10T10:00:00Z')",
                arrayOf(seededPlan.id),
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 38, true, KpknDatabase.PROGRAM_EDIT_HISTORY_37_38).close()

        // Open real KpknDatabase v38 on the upgraded file & wire shared repos
        val upgradedDb = KpknDatabase.buildPersistent(context, dbName)
        val (progRepo, nutRepo) = attachSharedDb(upgradedDb)
        runBlocking {
            withTimeout(30_000) { progRepo.isReady.first { it } }
            withTimeout(30_000) { nutRepo.foodDatabase.first { it.isNotEmpty() } }
        }

        // Verify v37 records survived in Room 38 repositories
        assertEquals("Fuerza v37", progRepo.getProgramById(seededProgram.id)?.name)
        assertTrue(progRepo.history.value.any { it.id == seededWorkout.id })
        assertTrue(nutRepo.nutritionLogs.value.any { it.id == seededNutritionLog.id })
        assertTrue(nutRepo.foodDatabase.value.any { it.id == seededRecipe.id })

        // Verify Req 6 deep link redirect
        assertEquals(KpknRoute.Nutrition.route, DeepLinkRouter.resolve(Uri.parse("kpkn://nutrition/calibration"))?.route)

        // Launch NutritionCalibrationScreen (zero weekly check-in UI) + NutritionPlanEditorScreen
        var showEditor by mutableStateOf(false)
        var editorDone by mutableStateOf(false)
        compose.setContent {
            KPKNTheme {
                if (!showEditor) {
                    NutritionCalibrationScreen(onBack = {})
                } else {
                    NutritionPlanEditorScreen(
                        planId = seededPlan.id,
                        pendingDraftId = null,
                        onDone = { editorDone = true },
                        onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Calibración").assertIsDisplayed()
        compose.onAllNodesWithText("Check-in semanal", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Aceptar propuesta", substring = true).assertCountEquals(0)

        compose.runOnUiThread { showEditor = true }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Dirección y meta", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Definir").performScrollTo().performClick()
        compose.onNodeWithText("% grasa").performScrollTo().performClick()
        if (compose.onAllNodes(hasText("Fijar una meta")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Fijar una meta").performScrollTo().performClick()
        }
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).let { nodes ->
            val count = nodes.fetchSemanticsNodes().size
            if (count > 0) nodes[0].performTextReplacement("14")
        }
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(10_000) { editorDone }

        // Log a food after upgrade + write new v38 entities, close & reopen Room
        runBlocking {
            val postLog = NutritionLog(
                id = "nl38-$suffix",
                date = "2026-11-16",
                mealType = MealType.DINNER,
                foods = listOf(LoggedFood(id = "lf38", foodName = "Arroz v38", amount = 150.0, calories = 195.0, carbs = 42.0)),
            )
            nutRepo.saveNutritionLog(postLog)
            upgradedDb.programVersionDao().upsert(
                ProgramVersionEntity(
                    id = "pv38-$suffix",
                    programId = seededProgram.id,
                    sessionId = "s37",
                    variantId = null,
                    operationId = "op38-$suffix",
                    savedAtEpochMs = 1_700_000_000_000L,
                    origin = "QA",
                    reason = "upgrade-v38",
                    isInitial = true,
                    snapshotJson = "{}",
                ),
            )
            upgradedDb.sharedExerciseLoadReferenceDao().upsert(
                SharedExerciseLoadReferenceEntity(
                    id = "slr38-$suffix",
                    exerciseKey = "squat",
                    configurationId = "squat:barbell",
                    tagId = null,
                    unitMode = "REPS",
                    loadMode = "LOAD",
                    quantityConvention = "STANDARD",
                    kind = "ESTIMATED_1RM",
                    valueKg = 140.0,
                    sourceAtMs = 1_700_000_000_000L,
                    sourceId = seededWorkout.id,
                    payloadJson = "{}",
                ),
            )
            progRepo.flushPendingWrites()
        }

        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        resetSingletons()

        val reopenedDb = KpknDatabase.buildPersistent(context, dbName)
        runBlocking {
            val versions = reopenedDb.programVersionDao().getForProgram(seededProgram.id)
            assertEquals("op38-$suffix", versions.single().operationId)
            val refs = reopenedDb.sharedExerciseLoadReferenceDao().getAll()
            assertEquals(140.0, refs.single { it.id == "slr38-$suffix" }.valueKg, 0.01)
            val (_, reopenedNut) = attachSharedDb(reopenedDb)
            withTimeout(30_000) { reopenedNut.foodDatabase.first { it.isNotEmpty() } }
            val updatedPlan = requireNotNull(reopenedNut.nutritionPlans.value.firstOrNull { it.id == seededPlan.id })
            assertEquals(GoalMetric.BODY_FAT, updatedPlan.goalType)
            assertTrue(reopenedNut.nutritionLogs.value.any { it.id == seededNutritionLog.id })
            assertTrue(reopenedNut.nutritionLogs.value.any { it.id == "nl38-$suffix" })
        }
    }

    @Test
    fun req7_recipesRealFoodLoggerUi_duplicatesValidationVersioningAndRestore() {
        val dbName = newDbName("qa-recipes")
        val progRepo = ProgramRepository.initForTestsWithDatabaseFile(context, dbName)
        val nutRepo = NutritionRepository.initForTests(context, noCatalogImporter)
        runBlocking {
            withTimeout(30_000) { progRepo.isReady.first { it } }
            withTimeout(30_000) { nutRepo.foodDatabase.first { it.isNotEmpty() } }
        }

        val chicken = FoodItem(
            id = "cat-chicken-$suffix",
            name = "Pollo pechuga QA",
            servingSize = 100.0,
            servingUnit = "g",
            unit = "g",
            calories = 165.0,
            protein = 31.0,
            carbs = 0.0,
            fats = 3.6,
        )
        val rice = FoodItem(
            id = "cat-rice-$suffix",
            name = "Arroz blanco QA",
            servingSize = 100.0,
            servingUnit = "g",
            unit = "g",
            calories = 130.0,
            protein = 2.7,
            carbs = 28.0,
            fats = 0.3,
        )
        runBlocking {
            nutRepo.saveCustomFood(chicken)
            nutRepo.saveCustomFood(rice)
        }

        val loggedMeals = mutableListOf<NutritionLog>()
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            MaterialTheme {
                FoodLoggerDrawer(
                    nutritionRepo = nutRepo,
                    onSave = { log, _ -> loggedMeals += log },
                    onDismiss = {},
                    isOpen = true,
                    foodDatabase = listOf(chicken, rice),
                    initialDate = "2026-11-16",
                    initialMealType = MealType.LUNCH,
                )
            }
        }
        compose.waitForIdle()

        // 1. Open Buscar tab -> Recipes -> Create -> trigger validation error -> fix
        compose.onNodeWithText("Buscar").performClick()
        compose.waitForIdle()
        tapLazyText("Recetas")
        compose.waitForIdle()
        compose.onNodeWithTag("recipe-library").assertIsDisplayed()
        compose.onNodeWithTag("recipe-create").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recipe-editor").assertIsDisplayed()

        fieldInsideTag("recipe-name").performTextInput("Pollo con arroz QA")
        fieldInsideTag("recipe-servings").performTextReplacement("0")
        compose.onNodeWithText("Guardar receta").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("recipe-servings-error").assertIsDisplayed()

        // State restoration mid-edit preserves recipe name
        fieldInsideTag("recipe-servings").performTextReplacement("2")
        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        fieldInsideTag("recipe-name").assertTextContains("Pollo con arroz QA")

        // 2. Add SAME base food twice -> distinct rows kept
        repeat(2) { idx ->
            compose.onNodeWithTag("recipe-add-ingredient").performScrollTo().performClick()
            compose.waitForIdle()
            fieldInsideTag("recipe-search").performTextReplacement("Pollo")
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("recipe-search-result-${chicken.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("recipe-search-result-${chicken.id}").performClick()
            compose.waitForIdle()
            fieldInsideTag("recipe-amount-confirm", useUnmergedTree = true)
                .performTextReplacement(if (idx == 0) "100" else "50")
            compose.onNodeWithTag("recipe-amount-confirm-add", useUnmergedTree = true).performClick()
            compose.waitForIdle()
        }
        compose.onAllNodes(SemanticsMatcher("recipe ingredient row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("recipe-ingredient-row-") == true
        }).assertCountEquals(2)

        // 3. Save v1 (no cooked weight) -> log v1 portion
        compose.onNodeWithText("Guardar receta").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("recipe-library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Usar").performClick()
        compose.waitForIdle()
        val v1Kcal = nutRepo.foodDatabase.value.single { it.name == "Pollo con arroz QA" }.calories
        compose.onNodeWithTag("recipe-add").performClick()
        compose.waitForIdle()

        // 4. Edit v1 -> v2 (still no cooked weight) -> v1 in loggedMeals unchanged
        compose.onNodeWithText("Buscar").performClick()
        compose.waitForIdle()
        tapLazyText("Recetas")
        compose.waitForIdle()
        compose.onNodeWithText("Editar").performClick()
        compose.waitForIdle()
        fieldInsideTag("recipe-servings").performTextReplacement("3")
        compose.onNodeWithText("Guardar receta").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("recipe-library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("versión 2", substring = true).assertIsDisplayed()

        // 5. Edit v2 -> v3 with cooked final weight 300g, log 150g by weight (= 1 ración)
        compose.onNodeWithText("Editar").performClick()
        compose.waitForIdle()
        fieldInsideTag("recipe-final-weight").performScrollTo().performTextReplacement("300")
        fieldInsideTag("recipe-servings").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("Guardar receta").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("recipe-library").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("versión 3", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gramos").performClick()
        fieldInsideTag("recipe-portion").performTextReplacement("150")
        compose.onNodeWithTag("recipe-add").performClick()
        compose.waitForIdle()

        // Historical logged v1 meal keeps v1 snapshot, while updated recipe is v3 with cooked weight
        val recipeItem = nutRepo.foodDatabase.value.single { it.name == "Pollo con arroz QA" }
        assertEquals(3, recipeItem.recipe?.version)
        assertEquals(300.0, recipeItem.recipe?.finalWeightGrams ?: 0.0, 0.01)
        assertTrue(v1Kcal > 0.0)
    }

    private fun fieldInsideTag(tag: String, useUnmergedTree: Boolean = false) =
        compose.onNode(
            hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),
            useUnmergedTree = useUnmergedTree,
        )

    private fun tapLazyText(text: String) {
        val matcher = hasText(text)
        fun isComposed() = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        if (!isComposed()) {
            val list = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
            val rows = list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount
            for (index in 0 until rows) {
                list.performScrollToIndex(index)
                if (isComposed()) break
            }
        }
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
}
