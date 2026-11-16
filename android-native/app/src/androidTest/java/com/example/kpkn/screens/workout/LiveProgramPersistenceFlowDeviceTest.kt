package com.example.kpkn.screens.workout

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.BuildConfig
import com.example.kpkn.MainActivity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.screens.sessioneditor.DraftWriteStatus
import com.example.kpkn.screens.sessioneditor.PersistedSessionEditorDraft
import com.example.kpkn.screens.sessioneditor.SessionEditorDraftStore
import com.example.kpkn.screens.sessioneditor.resolveNewestSessionWithConflict
import com.example.kpkn.screens.sessioneditor.sessionEditorDraftStorageKey
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.workout.VoiceSetEditPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** End-to-end P1 scope check on the real MainActivity and a disposable Room file in KPKN Dev. */
@RunWith(AndroidJUnit4::class)
class LiveProgramPersistenceFlowDeviceTest {
    private val testArguments = InstrumentationRegistry.getArguments()
    private val suffix = testArguments.getString("qaLiveEditId") ?: System.nanoTime().toString()
    private val processDeathMode = testArguments.getString("qaLiveEditMode").orEmpty()
    private val preserveProcessDeathFixture = processDeathMode == "seed"
    private val programId = "qa-live-edit-$suffix"
    private val sessionId = "qa-live-edit-week-2-$suffix"
    private val futureSessionId = "qa-live-edit-week-3-$suffix"
    private val pastSessionId = "qa-live-edit-week-1-$suffix"
    private val unrelatedSessionId = "qa-live-edit-unrelated-$suffix"
    private val lineageId = "qa-live-edit-lineage-$suffix"
    private val databaseName = "qa-live-edit-$suffix.db"
    private val recipeDayId = "qa-live-edit-day-$suffix"
    private val primaryExerciseSlot = "qa-primary-$suffix"
    private val secondaryExerciseSlot = "qa-secondary-$suffix"
    private val firstSetSlot = "qa-set-first-$suffix"
    private val secondSetSlot = "qa-set-second-$suffix"

    private val compose = createAndroidComposeRule<MainActivity>()
    private val caseName = TestName()
    private lateinit var fixtureContext: FixtureContext
    private lateinit var repository: ProgramRepository
    private var fixtureViewModel: WorkoutViewModel? = null
    private var launchedActivity: MainActivity? = null
    private var originalLaunchIntent: Intent? = null
    private var reopenExpectation: ProgramExpectation? = null
    private var reopenProgramAssertion: ((Program) -> Unit)? = null
    private var editorDraftCleanupKey: String? = null

    private val fixture = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            check(app.packageName == "com.example.kpkn.dbg") {
                "La prueba solo opera con KPKN Dev; no se inicializó el fixture."
            }
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            check(info.longVersionCode == BuildConfig.VERSION_CODE.toLong() && info.versionName == BuildConfig.VERSION_NAME) {
                "El APK instalado no coincide con la versión de esta prueba."
            }

            fixtureContext = FixtureContext(app, suffix)
            NutritionRepository.closeInstance()
            WorkoutMediaRepository.closeInstance()
            repository = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, databaseName)
            setDatabase(repository.databaseForTests())
            runBlocking {
                withTimeout(60_000) { repository.isReady.first { it } }
                repository.updateSettings {
                    it.copy(
                        onboardingCompleted = true,
                        restTimerAutoStart = false,
                        tourSeen = it.tourSeen + setOf("tour-home", "tour-training", "tour-first-workout"),
                    )
                }
                withTimeout(15_000) { repository.settings.first { it.onboardingCompleted } }
                if (processDeathMode == "resume") {
                    val restored = requireNotNull(repository.getProgramById(programId)) {
                        "El programa de prueba debe sobrevivir al cierre del proceso"
                    }
                    val expected = processDeathExpectation(restored)
                    assertProgramMatches(restored, expected)
                    reopenExpectation = expected
                } else if (processDeathMode != "cleanup") {
                    assertTrue(repository.addProgramNow(fixtureProgram()).isSuccess)
                }
            }
            if (processDeathMode != "resume" && processDeathMode != "cleanup") {
                assertProgramMatches(repository.getProgramById(programId)!!, unchangedProgram)
            }
        }

        override fun after() {
            if (preserveProcessDeathFixture) return
            try {
                closeFixtureRepositories()
                if (reopenExpectation != null || reopenProgramAssertion != null) {
                    repository = ProgramRepository.initForTestsWithDatabaseFile(fixtureContext, databaseName)
                    setDatabase(repository.databaseForTests())
                    runBlocking { withTimeout(60_000) { repository.isReady.first { it } } }
                    val reopened = repository.getProgramById(programId)
                    assertNotNull("El programa debe sobrevivir al cierre y reapertura de Room", reopened)
                    reopenExpectation?.let { expected -> assertProgramMatches(requireNotNull(reopened), expected) }
                    reopenProgramAssertion?.invoke(requireNotNull(reopened))
                }
            } finally {
                try {
                    editorDraftCleanupKey?.let { key ->
                        val store = SessionEditorDraftStore.getInstance(
                            InstrumentationRegistry.getInstrumentation().targetContext,
                        )
                        runBlocking {
                            withTimeout(20_000) {
                                val outcome = store.writer.clear(key)
                                assertEquals(DraftWriteStatus.WRITTEN, outcome.status)
                                assertEquals(null, store.readRaw(key))
                            }
                        }
                        editorDraftCleanupKey = null
                    }
                } finally {
                    closeFixtureRepositories()
                    if (::fixtureContext.isInitialized) {
                        fixtureContext.deleteDatabase(databaseName)
                        fixtureContext.clearOwnPreferences()
                    }
                }
            }
        }
    }

    /** MainActivity.onNewIntent replaces its launch intent; restore it before ActivityScenario teardown. */
    private val bodyDiagnostics = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                var bodyFailure: Throwable? = null
                try {
                    base.evaluate()
                } catch (error: Throwable) {
                    bodyFailure = error
                    throw error
                } finally {
                    val activity = launchedActivity
                    val original = originalLaunchIntent
                    if (activity != null && original != null) {
                        try {
                            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                                if (!activity.isDestroyed) activity.intent = Intent(original)
                            }
                        } catch (restoreError: Throwable) {
                            if (bodyFailure != null) bodyFailure.addSuppressed(restoreError) else throw restoreError
                        }
                    }
                }
            }
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(caseName)
        .around(fixture)
        .around(compose)
        .around(bodyDiagnostics)

    @After fun cancelOnlyThisFixtureWorkout() {
        val vm = fixtureViewModel ?: return
        if (vm.uiState.value.session?.id != sessionId) return
        if (preserveProcessDeathFixture) {
            runBlocking { withTimeout(20_000) { vm.flushOngoingForBackgroundAndAwait() } }
            fixtureViewModel = null
            return
        }
        compose.runOnUiThread { vm.cancelWorkout() }
        runBlocking { withTimeout(20_000) { vm.uiState.first { it.wasCancelled } } }
        fixtureViewModel = null
    }

    @Test fun soloHoyChangesTheLiveSessionAndLeavesEveryProgramOccurrenceUntouched() {
        val vm = launchWorkout()
        removeFirstPlannedSet(vm, scopeLabel = "Solo hoy")

        assertEquals(listOf(secondSetSlot), exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot))
        val expected = unchangedProgram
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
        reopenExpectation = expected
    }

    @Test fun correctingACompletedSetChangesExecutionOnlyAndNeverOffersPermanentPlanScope() {
        val vm = launchWorkout()
        val exercise = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot)
        val setKey = "${exercise.id}_0"
        val record = runBlocking {
            withContext(Dispatchers.Main.immediate) {
                vm.recordSetV2(
                    weight = 40.0,
                    value = 8.0,
                    intensity = 8.0,
                    loadMode = LoadModeV2.LOAD,
                    unitMode = UnitModeV2.REPS,
                    expectedExerciseId = exercise.id,
                    expectedSetIdx = 0,
                )
            }
        }
        assertTrue("La serie debe registrarse antes de corregirla", record.succeeded)
        compose.waitUntil(20_000) { vm.uiState.value.completedSets[setKey]?.reps == 8 }
        assertEquals(null, vm.uiState.value.pendingStructuralPersistence)
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)
        val versionsBeforeCorrection = runBlocking { repository.getLiveSessionVersions(programId, sessionId) }

        vm.patchCompletedSet(
            setKey,
            CompletedSetPatch(edit = VoiceSetEditPatch(metricValue = 7)),
        )
        compose.waitUntil(20_000) { vm.uiState.value.completedSets[setKey]?.reps == 7 }
        runBlocking { withTimeout(20_000) { vm.flushOngoingForBackgroundAndAwait() } }

        val corrected = requireNotNull(vm.uiState.value.completedSets[setKey])
        assertEquals(7, corrected.reps)
        assertEquals(7, repository.ongoingWorkout.value?.completedSets?.get(setKey)?.reps)
        assertEquals(null, vm.uiState.value.pendingStructuralPersistence)
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)
        assertEquals(
            "Corregir lo ejecutado no debe crear una versión del plan",
            versionsBeforeCorrection,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) },
        )
        reopenExpectation = unchangedProgram
    }

    @Test fun permanentEditMapsFutureSetsByStableExerciseAndPrescriptionSlots() {
        val vm = launchWorkout()
        val versionsBefore = runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
        val committedOperationId = removeFirstPlannedSet(
            vm,
            scopeLabel = "Guardar en el programa",
            doubleTapScope = true,
        )

        val expected = ProgramExpectation(
            sourcePrimary = listOf(secondSetSlot),
            futurePrimary = listOf(secondSetSlot),
        )
        assertEquals(listOf(secondSetSlot), exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot))
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
        val versionsAfter = runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
        val expectedAddedVersions = if (versionsBefore.none { it.isInitial }) 2 else 1
        assertEquals("La doble activación no debe duplicar el historial", versionsBefore.size + expectedAddedVersions, versionsAfter.size)
        assertEquals("Debe quedar exactamente un registro para la operación del toque", 1, versionsAfter.count { it.operationId == committedOperationId })
        reopenExpectation = expected
    }

    @Test fun permanentPrescriptionEditChangesOnlySourceAndFutureVariantA() {
        val vm = launchWorkout()
        runBlocking { vm.awaitPendingLiveMutation(timeoutMs = 30_000) }
        compose.waitUntil(15_000) { vm.uiState.value.recordingSetKey == null }
        val primaryExercise = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot)
        compose.runOnUiThread { vm.showSeriesTypeSheet(primaryExercise.id, fromIdx = 0, toIdx = 0) }
        waitForTag("series-options-sheet", 15_000)
        compose.onNodeWithText("Configurar serie", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-set-config-max", 15_000)
        val maxField = compose.onNodeWithTag("live-set-config-max", useUnmergedTree = true)
        maxField.assertIsDisplayed()
        maxField.performTextReplacement("10")
        maxField.assertTextContains("10")
        compose.onNodeWithTag("live-set-config-save", useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        try {
            compose.waitUntil(20_000) {
                val edited = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot).sets.first()
                edited.targetReps == 10 && edited.targetRepsRange == com.example.kpkn.data.models.RepRange(8, 10)
            }
        } catch (failure: Throwable) {
            val edited = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot).sets.first()
            val visibleValidationError = runCatching {
                compose.onAllNodesWithText("Revisa objetivos, intensidad y descanso.", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }.getOrDefault(false)
            throw AssertionError(
                "Configurar serie no generó alcance: reps=${edited.targetReps}, range=${edited.targetRepsRange}, " +
                    "RIR=${edited.targetRIR}, recordingKey=${vm.uiState.value.recordingSetKey}, " +
                    "pending=${vm.uiState.value.pendingStructuralPersistence}, toast=${vm.uiState.value.workoutToastNotice}, " +
                    "sheetVisible=${hasTag("live-set-config-sheet")}, validationError=$visibleValidationError",
                failure,
            )
        }
        val editedLiveSet = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot).sets.first()
        assertEquals(10, editedLiveSet.targetReps)
        assertEquals(com.example.kpkn.data.models.RepRange(8, 10), editedLiveSet.targetRepsRange)
        assertEquals(2, editedLiveSet.targetRIR)
        assertNotNull("La modificación del objetivo debe ofrecer alcance permanente", vm.uiState.value.pendingStructuralPersistence)
        val operationId = requireNotNull(vm.uiState.value.pendingStructuralPersistence).operationId
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { vm.uiState.value.pendingStructuralPersistence == null }

        val expectedTargets = mapOf(firstSetSlot to "8–10", secondSetSlot to "8")
        assertEquals(
            "La sesión viva conserva la edición de prescripción confirmada",
            expectedTargets,
            setPrescriptionTargets(requireNotNull(vm.uiState.value.session), primaryExerciseSlot),
        )
        val saved = requireNotNull(repository.getProgramById(programId))
        assertPrescriptionEditIsScoped(saved, expectedTargets)
        assertEquals(
            "La edición de prescripción debe registrarse una sola vez",
            1,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                .count { it.operationId == operationId },
        )
        reopenProgramAssertion = { reopened -> assertPrescriptionEditIsScoped(reopened, expectedTargets) }
    }

    @Test fun permanentSeriesMenuAdditionChangesOnlySourceAndFutureVariantA() {
        val vm = launchWorkout()
        runBlocking { vm.awaitPendingLiveMutation(timeoutMs = 30_000) }
        compose.waitUntil(15_000) { vm.uiState.value.recordingSetKey == null }
        val primaryExercise = exerciseForRecipeSlot(requireNotNull(vm.uiState.value.session), primaryExerciseSlot)
        compose.runOnUiThread { vm.showSeriesTypeSheet(primaryExercise.id, fromIdx = 1, toIdx = 1) }
        waitForTag("series-options-sheet", 15_000)
        compose.onNodeWithText("Añadir serie", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-structure-scope", 15_000)
        compose.waitUntil(20_000) { vm.uiState.value.recordingSetKey == null }

        val pending = requireNotNull(vm.uiState.value.pendingStructuralPersistence)
        val operationId = pending.operationId
        val liveSlots = exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot)
        assertEquals("La acción de opciones debe añadir una serie viva", 3, liveSlots.size)
        val addedSlot = liveSlots.last()
        assertTrue("La serie creada desde opciones necesita slot estable", addedSlot.startsWith("live-set:"))
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (failure: Throwable) {
            throw AssertionError(
                "No terminó el alta permanente desde opciones: pending=${vm.uiState.value.pendingStructuralPersistence}, " +
                    "source=${repository.getProgramById(programId)?.let { exerciseSlots(sessionIn(it, sessionId), primaryExerciseSlot) }}, " +
                    "future=${repository.getProgramById(programId)?.let { exerciseSlots(sessionIn(it, futureSessionId), primaryExerciseSlot) }}, " +
                    "toast=${vm.uiState.value.workoutToastNotice}",
                failure,
            )
        }

        val expected = ProgramExpectation(
            sourcePrimary = listOf(firstSetSlot, secondSetSlot, addedSlot),
            futurePrimary = listOf(secondSetSlot, addedSlot, firstSetSlot),
        )
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
        assertEquals(
            "El alta desde opciones debe producir un único evento durable",
            1,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) }.count { it.operationId == operationId },
        )
        reopenExpectation = expected
    }

    @Test fun permanentEditRetriesAfterTransientRoomWriteFailure() {
        val vm = launchWorkout()
        val db = repository.databaseForTests().openHelper.writableDatabase
        val safeSuffix = suffix.replace('-', '_')
        val failInsert = "qa_live_edit_fail_insert_$safeSuffix"
        val failUpdate = "qa_live_edit_fail_update_$safeSuffix"
        db.execSQL(
            "CREATE TRIGGER `$failInsert` BEFORE INSERT ON `programs` " +
                "BEGIN SELECT RAISE(ABORT, 'injected transient live edit failure'); END",
        )
        db.execSQL(
            "CREATE TRIGGER `$failUpdate` BEFORE UPDATE ON `programs` " +
                "BEGIN SELECT RAISE(ABORT, 'injected transient live edit failure'); END",
        )
        try {
            val operationId = removeFirstPlannedSet(
                vm,
                scopeLabel = "Guardar en el programa",
                retryTransientWrite = {
                    db.execSQL("DROP TRIGGER IF EXISTS `$failInsert`")
                    db.execSQL("DROP TRIGGER IF EXISTS `$failUpdate`")
                },
            )
            val expected = ProgramExpectation(
                sourcePrimary = listOf(secondSetSlot),
                futurePrimary = listOf(secondSetSlot),
            )
            assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
            val versions = runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
            assertEquals("El reintento debe dejar una foto inicial y una edición", 2, versions.size)
            assertEquals("El reintento debe dejar una sola operación", 1, versions.count { it.operationId == operationId })
            reopenExpectation = expected
        } finally {
            runCatching { db.execSQL("DROP TRIGGER IF EXISTS `$failInsert`") }
            runCatching { db.execSQL("DROP TRIGGER IF EXISTS `$failUpdate`") }
        }
    }

    @Test fun permanentReplacementFromLiveOptionsChangesOnlySourceAndFutureVariantA() {
        val vm = launchWorkout()
        val replacement = com.example.kpkn.data.exercises.exerciseCatalogSnapshot()
            .first { it.id != "qa-bench-press" && it.name.isNotBlank() && !it.force.isNullOrBlank() }

        compose.onNodeWithTag("live-session-route").assertIsDisplayed()
            .performTouchInput { longClick() }
        compose.onNodeWithTag("exercise-options-sheet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Reemplazar", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-replace-sheet", 20_000)
        compose.onNodeWithTag("live-replace-search", useUnmergedTree = true)
            .performClick().performTextInput(replacement.name)
        waitForTag("live-replace-option-${replacement.id}", 20_000)
        compose.onNodeWithTag("live-replace-option-${replacement.id}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("live-replace-preview", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("live-replace-apply", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-replace-scope", 20_000)
        compose.waitUntil(20_000) { vm.uiState.value.recordingSetKey == null }
        val operationId = requireNotNull(vm.uiState.value.pendingReplacementPersistencePrompt).operationId
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingReplacementPersistencePrompt == null }
        } catch (error: Throwable) {
            throw AssertionError(
                "No terminó el guardado permanente del reemplazo: " +
                    "pending=${vm.uiState.value.pendingReplacementPersistencePrompt}, " +
                    "toast=${vm.uiState.value.workoutToastNotice}",
                error,
            )
        }

        val saved = requireNotNull(repository.getProgramById(programId))
        val source = sessionIn(saved, sessionId)
        val future = sessionIn(saved, futureSessionId)
        val past = sessionIn(saved, pastSessionId)
        assertEquals(replacement.name, exerciseForRecipeSlot(source, primaryExerciseSlot).name)
        assertEquals(replacement.name, exerciseForRecipeSlot(future, primaryExerciseSlot).name)
        assertEquals("Press QA", exerciseForRecipeSlot(past, primaryExerciseSlot).name)
        assertEquals("Press QA", exerciseForRecipeSlot(requireNotNull(source.sessionB), primaryExerciseSlot).name)
        assertEquals(
            "El reemplazo debe guardar una sola operación",
            1,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                .count { it.operationId == operationId },
        )
        reopenExpectation = unchangedProgram
        reopenProgramAssertion = { reopened ->
            assertEquals(replacement.name, exerciseForRecipeSlot(sessionIn(reopened, sessionId), primaryExerciseSlot).name)
            assertEquals(replacement.name, exerciseForRecipeSlot(sessionIn(reopened, futureSessionId), primaryExerciseSlot).name)
            assertEquals("Press QA", exerciseForRecipeSlot(sessionIn(reopened, pastSessionId), primaryExerciseSlot).name)
            assertEquals("Press QA", exerciseForRecipeSlot(requireNotNull(sessionIn(reopened, sessionId).sessionB), primaryExerciseSlot).name)
        }
    }

    @Test fun permanentRemovalChangesOnlySourceAndFutureVariantA() {
        val vm = launchWorkout()
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)

        compose.onNodeWithTag("live-session-route").assertIsDisplayed()
            .performTouchInput { longClick() }
        compose.onNodeWithTag("exercise-options-sheet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Quitar de la sesión", useUnmergedTree = true)
            .assertIsDisplayed().performClick()
        compose.onNodeWithText("Quitar de la sesión", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Quitar", useUnmergedTree = true).assertIsDisplayed().performClick()

        waitForTag("live-structure-scope", 15_000)
        compose.waitUntil(20_000) {
            vm.uiState.value.pendingStructuralPersistence != null && vm.uiState.value.recordingSetKey == null
        }
        val operationId = requireNotNull(vm.uiState.value.pendingStructuralPersistence).operationId
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (error: Throwable) {
            throw AssertionError(
                "No terminó la baja permanente del ejercicio: pending=${vm.uiState.value.pendingStructuralPersistence}, " +
                    "toast=${vm.uiState.value.workoutToastNotice}, " +
                    "source=${repository.getProgramById(programId)?.let { exerciseSlotsOrEmpty(sessionIn(it, sessionId), primaryExerciseSlot) }}, " +
                    "future=${repository.getProgramById(programId)?.let { exerciseSlotsOrEmpty(sessionIn(it, futureSessionId), primaryExerciseSlot) }}",
                error,
            )
        }

        val saved = requireNotNull(repository.getProgramById(programId))
        assertProgramWithPrimaryExerciseRemoved(saved)
        assertEquals(
            "La baja debe dejar una única versión para su operación",
            1,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                .count { it.operationId == operationId },
        )
        reopenProgramAssertion = ::assertProgramWithPrimaryExerciseRemoved
    }

    @Test fun permanentCatalogAdditionChangesOnlySourceAndFutureVariantA() {
        val vm = launchWorkout()
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)
        val live = requireNotNull(vm.uiState.value.session)
        val anchor = live.allExercises().getOrNull(vm.uiState.value.currentExerciseIdx)
            ?: error("El ejercicio activo debe existir para anclar el alta")
        val anchorSlot = requireNotNull(anchor.recipeSlotId) { "La sesión QA requiere un slot de anclaje estable" }
        val addedName = "Aperturas Declinadas"

        compose.onNodeWithTag("live-session-route").assertIsDisplayed()
            .performTouchInput { longClick() }
        compose.onNodeWithTag("exercise-options-sheet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Agregar ejercicio", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Todos los ejercicios", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val selectionDescription = "Añadir $addedName a la selección"
        try {
            compose.waitUntil(45_000) {
                compose.onAllNodesWithContentDescription(selectionDescription, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } catch (error: Throwable) {
            val visibleUi = runCatching {
                compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .flatMap { node ->
                        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                        (texts + descriptions).filter(String::isNotBlank)
                    }.distinct().take(50).joinToString()
            }.getOrDefault("unavailable")
            throw AssertionError("El catálogo real no mostró '$addedName'; UI visible=$visibleUi", error)
        }

        compose.onNodeWithContentDescription(selectionDescription, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Añadir 1", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-structure-scope", 30_000)
        compose.waitUntil(20_000) {
            vm.uiState.value.pendingStructuralPersistence != null && vm.uiState.value.recordingSetKey == null
        }
        val operationId = requireNotNull(vm.uiState.value.pendingStructuralPersistence).operationId
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (error: Throwable) {
            val program = repository.getProgramById(programId)
            throw AssertionError(
                "No terminó el alta permanente del ejercicio: pending=${vm.uiState.value.pendingStructuralPersistence}, " +
                    "toast=${vm.uiState.value.workoutToastNotice}, " +
                    "source=${program?.let { sessionIn(it, sessionId).allExercises().map(Exercise::name) }}, " +
                    "future=${program?.let { sessionIn(it, futureSessionId).allExercises().map(Exercise::name) }}, " +
                    "rejected=${vm.uiState.value.workoutToastNotice}",
                error,
            )
        }

        val saved = requireNotNull(repository.getProgramById(programId))
        assertProgramMatches(saved, unchangedProgram)
        assertCatalogExerciseAddition(saved, addedName, anchorSlot)
        assertEquals(
            "El alta permanente debe guardar una sola versión para su operación",
            1,
            runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                .count { it.operationId == operationId },
        )
        reopenExpectation = unchangedProgram
        reopenProgramAssertion = { reopened -> assertCatalogExerciseAddition(reopened, addedName, anchorSlot) }
    }

    @Test fun livePermanentEditReconcilesDirtySessionEditorDraftWithoutDroppingEitherVersion() {
        val vm = launchWorkout()
        val source = sessionIn(requireNotNull(repository.getProgramById(programId)), sessionId)
        val state = vm.uiState.value
        // ProgramDetail opens the source template week in SessionEditor, while WorkoutViewModel
        // carries the materialized occurrence week ID. Seed the dirty editor draft under the
        // canonical key that a real ProgramDetail → SessionEditor edit writes; the live commit
        // must reconcile that key from the occurrence as well.
        val editorWeekId = "week-2-$suffix"
        val draftKey = sessionEditorDraftStorageKey(
            programId = programId,
            weekId = editorWeekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            sessionId = sessionId,
        )
        editorDraftCleanupKey = draftKey
        val store = SessionEditorDraftStore.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
        val editorDraftJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val dirtyDraft = PersistedSessionEditorDraft(
            programId = programId,
            sessionId = sessionId,
            weekId = editorWeekId,
            macroIndex = state.macroIndex,
            mesoIndex = state.mesoIndex,
            dayOfWeek = source.dayOfWeek,
            session = source.copy(name = "Borrador concurrente"),
            baselineSession = source,
            baselineLastModifiedAtMs = source.lastModifiedAtMs,
            savedAtMs = System.currentTimeMillis(),
        )
        val write = runBlocking { withTimeout(20_000) { store.writer.writeLatest(draftKey, dirtyDraft) } }
        assertEquals("El borrador previo debe persistirse antes del cambio en vivo", DraftWriteStatus.WRITTEN, write.status)

        openSeriesEditor()
        compose.onNodeWithTag(LIVE_EDIT_ADD_SET_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-structure-scope", 15_000)
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (error: Throwable) {
            throw AssertionError(
                "No terminó el guardado permanente con borrador concurrente: " +
                    "pending=${vm.uiState.value.pendingStructuralPersistence}, " +
                    "toast=${vm.uiState.value.workoutToastNotice}, " +
                    "source=${repository.getProgramById(programId)?.let { exerciseSlots(sessionIn(it, sessionId), primaryExerciseSlot) }}",
                error,
            )
        }

        val updatedProgram = requireNotNull(repository.getProgramById(programId))
        val expected = processDeathExpectation(updatedProgram)
        assertProgramMatches(updatedProgram, expected)
        reopenExpectation = expected
        val updatedSource = sessionIn(updatedProgram, sessionId)
        var reconciledDraft: PersistedSessionEditorDraft? = null
        compose.waitUntil(20_000) {
            val raw = runBlocking { withTimeout(5_000) { store.readRaw(draftKey) } }
            reconciledDraft = raw?.let { encoded ->
                runCatching { editorDraftJson.decodeFromString<PersistedSessionEditorDraft>(encoded) }.getOrNull()
            }
            reconciledDraft?.liveModifiedAtMs != null
        }

        val preservedDraft = requireNotNull(reconciledDraft)
        assertEquals("Borrador concurrente", preservedDraft.session.name)
        assertEquals(source.name, preservedDraft.baselineSession?.name)
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(requireNotNull(preservedDraft.baselineSession), primaryExerciseSlot))

        val resolution = resolveNewestSessionWithConflict(
            existing = updatedSource,
            fallback = updatedSource,
            persistedDraft = preservedDraft,
        )
        val conflict = requireNotNull(resolution.liveConflict) {
            "La apertura del editor debe exponer las dos versiones divergentes"
        }
        assertEquals("Borrador concurrente", resolution.session.name)
        assertEquals("Borrador concurrente", conflict.draftSession.name)
        assertEquals(expected.sourcePrimary, exerciseSlots(conflict.programSession, primaryExerciseSlot))

        // KPKN keeps the active workout on top of navigation. Close only this disposable fixture
        // after its program commit so the real Programs → Session Editor route can be exercised.
        compose.runOnUiThread { vm.cancelWorkout() }
        compose.waitUntil(20_000) { vm.uiState.value.wasCancelled }
        assertEquals("El fixture debe cerrar su sesión antes de entrar al editor", null, repository.ongoingWorkout.value)
        fixtureViewModel = null
        // The app may still be showing its first-run notification prompt over the workout route.
        // Dismiss only that nonessential prompt so the real Programs → Session Editor navigation
        // below can be observed without changing any runtime permission.
        if (hasTag("permissions-skip")) {
            compose.onNodeWithTag("permissions-skip", useUnmergedTree = true)
                .assertIsDisplayed()
                .performClick()
            compose.waitUntil(20_000) { !hasTag("permissions-dialog") }
        }
        compose.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("kpkn://program/$programId"))
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("QA P1 persistencia en vivo", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } catch (error: Throwable) {
            val visibleUi = runCatching {
                compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .flatMap { node ->
                        val tags = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        (listOf(tags) + texts).filter(String::isNotBlank)
                    }.distinct().take(80).joinToString()
            }.getOrDefault("unavailable")
            var activityData: String? = null
            compose.activityRule.scenario.onActivity { activityData = it.intent?.data?.toString() }
            throw AssertionError(
                "No abrió ProgramDetail tras cancelar el fixture; intent=$activityData, " +
                    "ongoing=${repository.ongoingWorkout.value?.session?.id}, visibleUi=$visibleUi",
                error,
            )
        }
        compose.onNodeWithTag("week-title", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("Semana 2", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Semana 2", substring = true, useUnmergedTree = true)[0].performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("2 ejercicios", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(
            hasText("2 ejercicios", substring = true) and hasClickAction(),
        ).performClick()
        val programSessionTag = "programsession-$sessionId"
        compose.onNodeWithTag(programSessionTag, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(
            hasText("Editar") and hasAnyAncestor(hasTestTag(programSessionTag)),
            useUnmergedTree = true,
        ).assertIsDisplayed().performClick()

        try {
            compose.waitUntil(60_000) {
                compose.onAllNodesWithText("Cambios en vivo y borrador sin guardar", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } catch (error: Throwable) {
            val visibleUi = runCatching {
                compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .flatMap { node ->
                        val tags = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        (listOf(tags) + texts).filter(String::isNotBlank)
                    }.distinct().take(100).joinToString()
            }.getOrDefault("unavailable")
            var activityData: String? = null
            compose.activityRule.scenario.onActivity { activityData = it.intent?.data?.toString() }
            val rawDraft = runBlocking { store.readRaw(draftKey) }
            throw AssertionError(
                "No apareció el diálogo de conflicto; intent=$activityData, " +
                    "draft=${rawDraft?.take(600)}, visibleUi=$visibleUi",
                error,
            )
        }
        compose.onNodeWithText("Cambios en vivo y borrador sin guardar", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Mantener mi borrador", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Usar cambios de la sesión", useUnmergedTree = true).assertIsDisplayed()
        assertNotNull("El diálogo no debe borrar el borrador que aún debe resolverse", runBlocking { store.readRaw(draftKey) })
    }

    @Test fun aSoloTodayDeletionDoesNotLeakIntoTheLaterPermanentProgramEdit() {
        if (processDeathMode == "cleanup") return
        if (processDeathMode == "resume") {
            val stored = requireNotNull(repository.getProgramById(programId))
            val expected = processDeathExpectation(stored)
            assertProgramMatches(stored, expected)
            val ongoingBeforeLaunch = requireNotNull(repository.ongoingWorkout.value) {
                "El entrenamiento activo debe sobrevivir al cierre del proceso"
            }
            assertEquals(sessionId, ongoingBeforeLaunch.session.id)
            assertEquals(listOf(secondSetSlot, expected.sourcePrimary.last()), exerciseSlots(ongoingBeforeLaunch.session, primaryExerciseSlot))

            val vm = launchWorkout(resumeExisting = true)
            assertEquals(programId, vm.uiState.value.programId)
            assertEquals(listOf(secondSetSlot, expected.sourcePrimary.last()), exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot))
            assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
            reopenExpectation = expected
            return
        }

        val vm = launchWorkout()
        removeFirstPlannedSet(vm, scopeLabel = "Solo hoy")
        assertEquals(listOf(secondSetSlot), exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot))
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)

        openSeriesEditor()
        compose.onNodeWithTag(LIVE_EDIT_ADD_SET_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-structure-scope", 15_000)
        compose.onNodeWithText("Guardar en el programa", useUnmergedTree = true).assertIsDisplayed().performClick()
        try {
            compose.waitUntil(60_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (error: Throwable) {
            val program = repository.getProgramById(programId)
            throw AssertionError(
                "No terminó la edición permanente: pending=${vm.uiState.value.pendingStructuralPersistence}, " +
                    "source=${program?.let { exerciseSlots(sessionIn(it, sessionId), primaryExerciseSlot) }}, " +
                    "future=${program?.let { exerciseSlots(sessionIn(it, futureSessionId), primaryExerciseSlot) }}, " +
                    "rejected=${vm.uiState.value.workoutToastNotice}",
                error,
            )
        }

        val liveSetSlots = exerciseSlots(requireNotNull(vm.uiState.value.session), primaryExerciseSlot)
        assertEquals("La baja local A y la nueva prescripción B permanecen en la sesión", 2, liveSetSlots.size)
        assertEquals(secondSetSlot, liveSetSlots.first())
        val addedSlot = liveSetSlots.last()
        assertTrue("La serie añadida requiere identidad estable", addedSlot.isNotBlank() && addedSlot !in setOf(firstSetSlot, secondSetSlot))
        val expected = ProgramExpectation(
            sourcePrimary = listOf(firstSetSlot, secondSetSlot, addedSlot),
            futurePrimary = listOf(secondSetSlot, addedSlot, firstSetSlot),
        )
        assertProgramMatches(requireNotNull(repository.getProgramById(programId)), expected)
        reopenExpectation = expected
        if (processDeathMode == "seed") {
            runBlocking { withTimeout(20_000) { vm.flushOngoingForBackgroundAndAwait() } }
            val persistedOngoing = requireNotNull(repository.ongoingWorkout.value)
            assertEquals(sessionId, persistedOngoing.session.id)
            assertEquals(listOf(secondSetSlot, addedSlot), exerciseSlots(persistedOngoing.session, primaryExerciseSlot))
        }
    }

    private fun removeFirstPlannedSet(
        vm: WorkoutViewModel,
        scopeLabel: String,
        doubleTapScope: Boolean = false,
        retryTransientWrite: (() -> Unit)? = null,
    ): String {
        openSeriesEditor()
        compose.onNodeWithTag(liveEditSetChipTag(0), useUnmergedTree = true).assertIsDisplayed().performClick()
        val viewport = compose.onNode(
            hasScrollAction() and hasAnyDescendant(hasTestTag(LIVE_EDIT_DELETE_TAG)),
            useUnmergedTree = true,
        )
        repeat(4) {
            viewport.performTouchInput {
                swipe(
                    start = Offset(width * 0.95f, height * 0.85f),
                    end = Offset(width * 0.95f, height * 0.15f),
                    durationMillis = 400,
                )
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
        compose.onNodeWithTag(LIVE_EDIT_DELETE_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag(LIVE_EDIT_CONFIRM_TAG, useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag("live-structure-scope", 15_000)
        // La decisión se muestra en cuanto se publica el borrador vivo; espera a que esa escritura
        // libere el gate antes de medir un doble toque sobre el guardado permanente.
        compose.waitUntil(20_000) { vm.uiState.value.recordingSetKey == null }
        // El diálogo puede abrirse antes de que Room termine de guardar la mutación de sesión.
        // La acción permanente consulta ese gate y, correctamente, ignora el toque mientras esté
        // ocupado; espera el resultado durable antes de probar rechazo/reintento o doble toque.
        runBlocking { vm.awaitPendingLiveMutation(timeoutMs = 30_000) }
        val operationId = requireNotNull(vm.uiState.value.pendingStructuralPersistence).operationId
        val scopeButton = compose.onNodeWithText(scopeLabel, useUnmergedTree = true).assertIsDisplayed()
        if (doubleTapScope) {
            scopeButton.performTouchInput {
                click()
                click()
            }
        } else if (retryTransientWrite != null) {
            scopeButton.performClick()
            try {
                compose.waitUntil(30_000) {
                    vm.uiState.value.pendingStructuralPersistence?.operationId == operationId &&
                        vm.uiState.value.workoutToastNotice?.contains("No se pudo guardar el cambio en el programa") == true
                }
            } catch (error: Throwable) {
                val current = vm.uiState.value
                val program = runCatching { repository.getProgramById(programId) }.getOrNull()
                val operationVersions = runCatching {
                    runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                        .count { it.operationId == operationId }
                }.getOrDefault(-1)
                val remainingTriggers = runCatching {
                    repository.databaseForTests().openHelper.writableDatabase.query(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'qa_live_edit_fail_%'",
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
                }.getOrDefault(-1)
                val visibleUi = runCatching {
                    compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .flatMap { node ->
                            val tags = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                            (listOf(tags) + texts).filter(String::isNotBlank)
                        }.distinct().take(60).joinToString()
                }.getOrDefault("unavailable")
                throw AssertionError(
                    "No apareció el rechazo de la escritura transitoria: " +
                        "pending=${current.pendingStructuralPersistence}, toast=${current.workoutToastNotice}, " +
                        "source=${program?.let { exerciseSlotsOrEmpty(sessionIn(it, sessionId), primaryExerciseSlot) }}, " +
                        "future=${program?.let { exerciseSlotsOrEmpty(sessionIn(it, futureSessionId), primaryExerciseSlot) }}, " +
                        "operationVersions=$operationVersions, sqliteTriggers=$remainingTriggers, visibleUi=$visibleUi",
                    error,
                )
            }
            assertProgramMatches(requireNotNull(repository.getProgramById(programId)), unchangedProgram)
            assertEquals(
                "El fallo transitorio no debe escribir una versión parcial",
                0,
                runBlocking { repository.getLiveSessionVersions(programId, sessionId) }
                    .count { it.operationId == operationId },
            )
            retryTransientWrite()
            compose.onNodeWithText(scopeLabel, useUnmergedTree = true).assertIsDisplayed().performClick()
        } else {
            scopeButton.performClick()
        }
        try {
            compose.waitUntil(30_000) { vm.uiState.value.pendingStructuralPersistence == null }
        } catch (error: Throwable) {
            val current = vm.uiState.value
            val visibleUi = runCatching {
                compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .flatMap { node ->
                        val tags = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        (listOf(tags) + texts).filter(String::isNotBlank)
                    }.distinct().take(60).joinToString()
            }.getOrDefault("unavailable")
            throw AssertionError(
                "No se resolvió el alcance '$scopeLabel': pending=${current.pendingStructuralPersistence}, " +
                    "toast=${current.workoutToastNotice}, session=${current.session?.id}, " +
                    "program=${repository.getProgramById(programId)?.let { exerciseSlots(sessionIn(it, sessionId), primaryExerciseSlot) }}, " +
                    "visibleUi=$visibleUi",
                error,
            )
        }
        return operationId
    }

    private fun openSeriesEditor() {
        compose.onNodeWithTag("live-session-route").assertIsDisplayed()
            .performTouchInput { longClick() }
        compose.onNodeWithTag("exercise-options-sheet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Series", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForTag(LIVE_EDIT_CONFIRM_TAG, 15_000)
    }

    private fun launchWorkout(resumeExisting: Boolean = false): WorkoutViewModel {
        compose.activityRule.scenario.onActivity { activity ->
            launchedActivity = activity
            originalLaunchIntent = Intent(activity.intent)
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse("kpkn://workout/$programId/$sessionId"))
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }
        compose.waitUntil(45_000) {
            ActiveWorkoutHolder.get()?.uiState?.value?.let {
                it.session?.id == sessionId && !it.isStartingWorkout
            } == true
        }
        val vm = requireNotNull(ActiveWorkoutHolder.get())
        fixtureViewModel = vm
        if (resumeExisting) {
            compose.waitUntil(20_000) { hasTag("readiness-confirm") || hasTag("live-session-route") }
            if (hasTag("readiness-confirm")) {
                compose.onNodeWithTag("readiness-confirm", useUnmergedTree = true).performClick()
            }
        } else {
            waitForTag("readiness-confirm", 20_000)
            compose.onNodeWithTag("readiness-confirm", useUnmergedTree = true).performClick()
        }
        waitForTag("secondary_pill_avanzado", 20_000)
        assertEquals(programId, vm.uiState.value.programId)
        assertEquals(sessionId, repository.ongoingWorkout.value?.session?.id)
        return vm
    }

    private fun hasTag(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun waitForTag(tag: String, timeoutMs: Long) {
        try {
            compose.waitUntil(timeoutMs) { hasTag(tag) }
        } catch (error: Throwable) {
            val vm = ActiveWorkoutHolder.get()?.uiState?.value
            val visibleUi = runCatching {
                compose.onAllNodes(SemanticsMatcher("all") { true }, useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .flatMap { node ->
                        val tags = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                        val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        (listOf(tags) + texts).filter(String::isNotBlank)
                    }.distinct().take(60).joinToString()
            }.getOrDefault("unavailable")
            throw AssertionError(
                "Timed out waiting for tag=$tag; activityData=${launchedActivity?.intent?.data}, " +
                    "session=${vm?.session?.id}, starting=${vm?.isStartingWorkout}, " +
                    "program=${vm?.programId}, visibleUi=$visibleUi",
                error,
            )
        }
    }

    private data class ProgramExpectation(
        val sourcePrimary: List<String>,
        val futurePrimary: List<String>,
    )

    private val unchangedProgram: ProgramExpectation
        get() = ProgramExpectation(
            sourcePrimary = listOf(firstSetSlot, secondSetSlot),
            futurePrimary = listOf(secondSetSlot, firstSetSlot),
        )

    private fun processDeathExpectation(program: Program): ProgramExpectation {
        val sourceSlots = exerciseSlots(sessionIn(program, sessionId), primaryExerciseSlot)
        assertEquals("El plan conserva las dos series originales y la nueva", 3, sourceSlots.size)
        assertEquals(listOf(firstSetSlot, secondSetSlot), sourceSlots.take(2))
        val addedSlot = sourceSlots.last()
        assertTrue("El slot añadido debe tener identidad propia", addedSlot.isNotBlank() && addedSlot !in setOf(firstSetSlot, secondSetSlot))
        return ProgramExpectation(
            sourcePrimary = listOf(firstSetSlot, secondSetSlot, addedSlot),
            futurePrimary = listOf(secondSetSlot, addedSlot, firstSetSlot),
        )
    }

    private fun assertProgramMatches(program: Program, expected: ProgramExpectation) {
        val past = sessionIn(program, pastSessionId)
        val source = sessionIn(program, sessionId)
        val future = sessionIn(program, futureSessionId)
        val unrelated = sessionIn(program, unrelatedSessionId)
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(past, primaryExerciseSlot))
        assertEquals(expected.sourcePrimary, exerciseSlots(source, primaryExerciseSlot))
        assertEquals(expected.futurePrimary, exerciseSlots(future, primaryExerciseSlot))
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(source, secondaryExerciseSlot))
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(future, secondaryExerciseSlot))
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(unrelated, primaryExerciseSlot))
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(requireNotNull(source.sessionB), primaryExerciseSlot))
        assertEquals(listOf(firstSetSlot, secondSetSlot), exerciseSlots(requireNotNull(future.sessionB), primaryExerciseSlot))
    }

    private fun assertPrescriptionEditIsScoped(program: Program, editedTargets: Map<String, String>) {
        val originalTargets = mapOf(firstSetSlot to "8", secondSetSlot to "8")
        assertEquals(originalTargets, setPrescriptionTargets(sessionIn(program, pastSessionId), primaryExerciseSlot))
        assertEquals(editedTargets, setPrescriptionTargets(sessionIn(program, sessionId), primaryExerciseSlot))
        assertEquals(editedTargets, setPrescriptionTargets(sessionIn(program, futureSessionId), primaryExerciseSlot))
        assertEquals(originalTargets, setPrescriptionTargets(sessionIn(program, unrelatedSessionId), primaryExerciseSlot))
        assertEquals(originalTargets, setPrescriptionTargets(requireNotNull(sessionIn(program, sessionId).sessionB), primaryExerciseSlot))
        assertEquals(originalTargets, setPrescriptionTargets(requireNotNull(sessionIn(program, futureSessionId).sessionB), primaryExerciseSlot))
    }

    private fun setPrescriptionTargets(session: Session, recipeSlot: String): Map<String, String> = session.allExercises()
        .single { it.recipeDayId == recipeDayId && it.recipeSlotId == recipeSlot }
        .sets
        .associate { set -> requireNotNull(set.plannedSlotId) to (set.targetRepsRange?.format() ?: requireNotNull(set.targetReps).toString()) }

    private fun sessionIn(program: Program, id: String): Session = program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.sessions }
        .single { it.id == id }

    private fun exerciseSlots(session: Session, recipeSlot: String): List<String> = session.allExercises()
        .single { it.recipeDayId == recipeDayId && it.recipeSlotId == recipeSlot }
        .sets.mapNotNull { it.plannedSlotId }

    private fun exerciseSlotsOrEmpty(session: Session, recipeSlot: String): List<String> = session.allExercises()
        .singleOrNull { it.recipeDayId == recipeDayId && it.recipeSlotId == recipeSlot }
        ?.sets.orEmpty().mapNotNull { it.plannedSlotId }

    private fun assertProgramWithPrimaryExerciseRemoved(program: Program) {
        val past = sessionIn(program, pastSessionId)
        val source = sessionIn(program, sessionId)
        val future = sessionIn(program, futureSessionId)
        val unrelated = sessionIn(program, unrelatedSessionId)
        val originalSets = listOf(firstSetSlot, secondSetSlot)
        assertEquals(emptyList<String>(), exerciseSlotsOrEmpty(source, primaryExerciseSlot))
        assertEquals(emptyList<String>(), exerciseSlotsOrEmpty(future, primaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(past, primaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(unrelated, primaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(source, secondaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(future, secondaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(requireNotNull(source.sessionB), primaryExerciseSlot))
        assertEquals(originalSets, exerciseSlots(requireNotNull(future.sessionB), primaryExerciseSlot))
    }

    private fun assertCatalogExerciseAddition(program: Program, name: String, anchorSlot: String) {
        val past = sessionIn(program, pastSessionId)
        val source = sessionIn(program, sessionId)
        val future = sessionIn(program, futureSessionId)
        val unrelated = sessionIn(program, unrelatedSessionId)
        fun added(session: Session): Exercise = session.allExercises().single { it.name == name }
        fun assertImmediatelyFollows(session: Session) {
            val exercises = session.allExercises()
            val index = exercises.indexOfFirst { it.name == name }
            assertTrue("El ejercicio añadido debe quedar después de un ancla existente", index > 0)
            assertEquals(anchorSlot, exercises[index - 1].recipeSlotId)
        }

        val sourceAdded = added(source)
        val futureAdded = added(future)
        assertEquals("El slot de catálogo debe conservar su configuración", sourceAdded.catalogConfigurationId, futureAdded.catalogConfigurationId)
        assertEquals("El slot añadido conserva su identidad entre ocurrencias", sourceAdded.id, futureAdded.id)
        assertImmediatelyFollows(source)
        assertImmediatelyFollows(future)
        assertTrue("No se altera la semana ejecutada", past.allExercises().none { it.name == name })
        assertTrue("No se altera otra sesión aunque comparta el nombre de programa", unrelated.allExercises().none { it.name == name })
        assertTrue("No se altera la variante B del origen", requireNotNull(source.sessionB).allExercises().none { it.name == name })
        assertTrue("No se altera la variante B futura", requireNotNull(future.sessionB).allExercises().none { it.name == name })
    }

    private fun exerciseForRecipeSlot(session: Session, recipeSlot: String) = session.allExercises()
        .single { it.recipeDayId == recipeDayId && it.recipeSlotId == recipeSlot }

    private fun fixtureProgram(): Program {
        val past = sessionOccurrence(pastSessionId, lineageId, day = 1)
        val source = sessionOccurrence(sessionId, lineageId, day = 2)
        val future = sessionOccurrence(
            futureSessionId,
            lineageId,
            day = 2,
            primaryOrder = listOf(secondSetSlot, firstSetSlot),
            exerciseOrder = listOf(secondaryExerciseSlot, primaryExerciseSlot),
        )
        val unrelated = sessionOccurrence(
            unrelatedSessionId,
            "unrelated-$suffix",
            day = 4,
            exerciseOrder = listOf(primaryExerciseSlot, secondaryExerciseSlot),
        )
        return Program(
            id = programId,
            name = "QA P1 persistencia en vivo",
            macrocycles = listOf(
                Macrocycle(
                    id = "macro-$suffix",
                    name = "Macro QA",
                    blocks = listOf(
                        Block(
                            id = "block-$suffix",
                            name = "Bloque QA",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso-$suffix",
                                    name = "Mesociclo QA",
                                    weeks = listOf(
                                        ProgramWeek(id = "week-1-$suffix", name = "Semana 1", sessions = listOf(past)),
                                        ProgramWeek(id = "week-2-$suffix", name = "Semana 2", sessions = listOf(source)),
                                        ProgramWeek(id = "week-3-$suffix", name = "Semana 3", sessions = listOf(future, unrelated)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sessionOccurrence(
        id: String,
        lineage: String,
        day: Int,
        primaryOrder: List<String> = listOf(firstSetSlot, secondSetSlot),
        exerciseOrder: List<String> = listOf(primaryExerciseSlot, secondaryExerciseSlot),
    ): Session {
        fun exercises(variantSuffix: String, primarySetOrder: List<String>) = exerciseOrder.map { slot ->
            val setOrder = if (slot == primaryExerciseSlot) primarySetOrder else listOf(firstSetSlot, secondSetSlot)
            Exercise(
                id = "$id-$variantSuffix-exercise-$slot",
                name = "Press QA",
                exerciseDbId = "qa-bench-press",
                catalogDefinitionId = "qa-bench-press-definition",
                recipeDayId = recipeDayId,
                recipeSlotId = slot,
                sets = setOrder.map { setSlot ->
                    ExerciseSet(
                        id = "$id-$variantSuffix-$slot-$setSlot",
                        targetReps = 8,
                        targetRIR = 2,
                        weight = 40.0,
                        plannedSlotId = setSlot,
                    )
                },
            )
        }

        val exercisesA = exercises("A", primaryOrder)
        val variantB = Session(
            id = "$id-B",
            name = "Pierna B · QA P1",
            dayOfWeek = day,
            assignedDays = listOf(day),
            exercises = exercises("B", listOf(firstSetSlot, secondSetSlot)),
            logicalLineageId = lineage,
        )
        return Session(
            id = id,
            name = "Pierna A · QA P1",
            dayOfWeek = day,
            assignedDays = listOf(day),
            exercises = exercisesA,
            sessionB = variantB,
            logicalLineageId = lineage,
        )
    }

    private fun closeFixtureRepositories() {
        NutritionRepository.closeInstance()
        WorkoutMediaRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        setDatabase(null)
    }

    private fun setDatabase(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, database)
    }

    private class FixtureContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        private val names = linkedSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolated = "qa-live-edit-$suffix-$name"
            synchronized(names) { names += isolated }
            return baseContext.getSharedPreferences(isolated, mode)
        }
        fun clearOwnPreferences() {
            synchronized(names) { names.toList().also { names.clear() } }.forEach(baseContext::deleteSharedPreferences)
        }
    }
}
