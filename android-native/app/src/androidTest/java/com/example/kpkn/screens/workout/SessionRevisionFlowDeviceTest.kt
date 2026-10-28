package com.example.kpkn.screens.workout

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.MainActivity
import com.example.kpkn.BuildConfig
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.WorkoutMediaRepository
import com.example.kpkn.services.workout.ActiveWorkoutHolder
import com.example.kpkn.services.workout.WorkoutVoicePermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestName
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Modifier

/**
 * Real MainActivity + navigation + WorkoutScreen + production VM/recorder.
 * Only .dbg is allowed. Before Activity launch the repository/Room singleton is
 * replaced with an in-memory fixture, and repository preferences are namespaced.
 * No setContent, mock screen, screenshot scaling or production database writes.
 * UI drives faces, exercise menu/RM, quick form and closing notes. VM commands
 * open quick/finish and stop rest so those entry buttons are NOT asserted here.
 */
@RunWith(AndroidJUnit4::class)
class SessionRevisionFlowDeviceTest {
    private val caseName = TestName()
    private val isCardioFixture: Boolean
        get() = caseName.methodName == "realActivityCardioKeepsBoundedVisibleViewportWithoutStartingGpsOrRecording"
    private val isWeightImeFixture: Boolean
        get() = caseName.methodName == "realActivityWeightImeKeepsFrameAndReturnsToSameSeries"
    private val isLiveControlsFixture: Boolean
        get() = caseName.methodName == "realActivityLiveControlsKeepMovApproxAndEffectiveSetsWithoutScrolling" ||
            isWeightImeFixture || caseName.methodName == "realActivityReducedHeightKeepsCardAndLineComplete"
    private val suffix = System.nanoTime().toString()
    private val programId = "review-native-program-$suffix"
    private val sessionId = "review-native-session-$suffix"
    private val exerciseId = "review-native-squat-$suffix"
    private val exerciseName = "Sentadilla QA"
    private lateinit var context: FixtureContext
    private lateinit var repository: ProgramRepository
    private var fixtureVm: WorkoutViewModel? = null
    private var launchedActivity: MainActivity? = null
    private var originalLaunchIntent: Intent? = null
    private val setIds = (0 until 4).map { "review-native-set-$suffix-$it" }
    private val fixtureExercise = Exercise(
        id = exerciseId, name = exerciseName, restTime = 60,
        sets = setIds.map { ExerciseSet(id = it, weight = 95.0, targetReps = 6,
            targetRIR = 2, intensityMode = IntensityMode.RIR,
            loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS) },
    )
    private val liveControlsExercise = fixtureExercise.copy(
        warmupSets = listOf(WarmupSetDefinition(
            id = "review-native-warmup-$suffix",
            percentageOfWorkingWeight = 0.5,
            targetReps = 6,
            restBetween = 0,
        )),
        mobilitySeries = listOf(MobilitySeries(
            id = "review-native-mobility-$suffix",
            name = "Movilidad QA",
            sets = 1,
            durationSeconds = 20,
            restBetweenSeconds = 0,
        )),
    )
    private val fixtureSession = Session(id = sessionId, name = "Pierna A · QA nativa",
        exercises = listOf(fixtureExercise), targetDurationMinutes = 58)
    private val cardioExercise = Exercise(
        id = "review-native-bike-$suffix", name = "Bici estática QA",
        sets = listOf(ExerciseSet(id = "review-native-cardio-set-$suffix")),
        cardioDetails = CardioDetails(type = CardioType.BIKE_STATIONARY,
            targetDurationSeconds = 120, requiresGps = false, supportsDistance = false),
    )
    private var locationPermissionsBeforeCardio: Pair<Int, Int>? = null
    private val compose = createAndroidComposeRule<MainActivity>()

    private val fixture = object : ExternalResource() {
        override fun before() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            check(app.packageName == "com.example.kpkn.dbg") {
                "Esta prueba solo admite el paquete .dbg; no se ha inicializado ningún fixture."
            }
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            // Refuse an APK/test mismatch before replacing any repository singleton.
            // The .dbg guard above owns data isolation; releases may advance independently.
            check(info.longVersionCode == BuildConfig.VERSION_CODE.toLong() && info.versionName == BuildConfig.VERSION_NAME) {
                "Installed app does not match the version this fixture was compiled against"
            }
            context = FixtureContext(app, suffix)
            NutritionRepository.closeInstance()
            clearSingleton(AugeRepository::class.java)
            repository = ProgramRepository.initForTests(context)
            setDatabase(repository.databaseForTests())
            runBlocking {
                withTimeout(60_000) { repository.isReady.first { it } }
                repository.updateSettings { it.copy(onboardingCompleted = true,
                    restTimerAutoStart = false,
                    tourSeen = it.tourSeen + setOf("tour-home", "tour-training", "tour-first-workout")) }
                withTimeout(15_000) { repository.settings.first { it.onboardingCompleted } }
                assertTrue(repository.addProgramNow(fixtureProgram()).isSuccess)
            }
        }

        override fun after() {
            // Activity rule has already closed its real navigation/ViewModelStore.
            clearSingleton(AugeRepository::class.java)
            NutritionRepository.closeInstance()
            WorkoutMediaRepository.closeInstance()
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            setDatabase(null)
            if (::context.isInitialized) context.clearOwnPreferences()
        }
    }

    private val permissions = object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = instrumentation.targetContext.packageName
            check(packageName == "com.example.kpkn.dbg") {
                "Los permisos de esta prueba solo se conceden al paquete .dbg."
            }
            // This case only edits a text field; it must not grant/revoke any permission.
            if (isLiveControlsFixture) return
            // UiAutomation's direct API is available since Android 9. Do not use
            // shell commands or add an androidTest dependency for this fixture.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            if (isCardioFixture) {
                val app = instrumentation.targetContext
                locationPermissionsBeforeCardio = app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) to
                    app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
                instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
            }
            // Visual face requests CAMERA immediately; pregrant only this isolated .dbg fixture.
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Keep the original body failure visible before ActivityScenario teardown can replace it.
     * MainActivity.onNewIntent calls setIntent; restore the rule launch identity so ActivityScenario
     * can observe DESTROYED. Use the saved real Activity, never scenario.onActivity after dispatch.
     */
    private val bodyDiagnostics = object : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                var bodyFailure: Throwable? = null
                try {
                    base.evaluate()
                } catch (error: Throwable) {
                    bodyFailure = error
                    runCatching {
                        val app = InstrumentationRegistry.getInstrumentation().targetContext
                        check(app.packageName == "com.example.kpkn.dbg")
                        File(requireNotNull(app.getExternalFilesDir(null)), "qa14-main-body-failure.txt")
                            .appendText("\n=== ${description.displayName} ===\n${error.stackTraceToString()}\n")
                    }.exceptionOrNull()?.let { error.addSuppressed(it) }
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
        .around(permissions)
        .around(compose)
        .around(bodyDiagnostics)

    @After fun stopOnlyTheFixtureWorkout() {
        val vm = fixtureVm ?: return
        if (vm.uiState.value.session?.id != sessionId) return
        compose.runOnUiThread { vm.cancelWorkout() }
        runBlocking { withTimeout(20_000) { vm.uiState.first { it.wasCancelled } } }
    }

    @Test fun realActivityThreeFacesKeepActionsClearAndExposeNoCockpit() {
        launchWorkout()
        val front = activeTag("secondary_pill_avanzado")
        front.assertIsDisplayed()
        val originalFrame = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        capture("01-live-front")
        assertReferenceFrame(originalFrame)
        assertLiveActionsClear(originalFrame)
        assertInitialLiveStepperVisible(originalFrame)
        front.performClick()
        activeNode(hasContentDescription("Volver a la serie")).assertIsDisplayed()
        capture("02-live-technique")
        val advancedFrame = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertEquals(originalFrame.width, advancedFrame.width, 1f)
        assertEquals(originalFrame.height, advancedFrame.height, 1f)
        assertLiveActionsClear(advancedFrame)
        activeNode(hasContentDescription("Volver a la serie")).performClick()
        activeTag("secondary_pill_modo_visual").performClick()
        activeTag("media_face").assertIsDisplayed()
        capture("03-live-visual")
        val visualFrame = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertEquals(originalFrame.width, visualFrame.width, 1f)
        assertEquals(originalFrame.height, visualFrame.height, 1f)
        assertLiveActionsClear(visualFrame)
        activeNode(hasContentDescription("Volver a la serie")).performClick()
        activeTag("secondary_pill_avanzado").assertIsDisplayed()
        compose.onNodeWithTag("live-session-steps", true).assertIsDisplayed()
        capture("03b-live-series-navigation")
        val secondSeries = compose.onNode(
            (hasText("S2") or hasAnyDescendant(hasText("S2"))) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and
                hasAnyAncestor(hasTestTag("live-session-steps")), useUnmergedTree = true)
        secondSeries.assertIsDisplayed().performTouchInput { longClick() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Aplicar").fetchSemanticsNodes().isNotEmpty()
        }
        capture("03c-live-series-options")
        compose.onNodeWithText("Aplicar").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Aplicar").assertDoesNotExist()
        activeTag("secondary_pill_avanzado").assertExists()
        compose.onNodeWithContentDescription("Abrir resumen de sesión").assertDoesNotExist()
        compose.onNodeWithContentDescription("Plegar resumen de sesión").assertDoesNotExist()
        compose.onNodeWithText("Hitos de la sesión").assertDoesNotExist()
    }

    @Test fun realActivityReducedHeightKeepsCardAndLineComplete() {
        launchWorkout()
        val root = compose.onRoot(true).fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertEquals("El contenido Android real está restringido a 760dp", 760f, root.height / density, 1f)
        val card = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertReferenceFrame(card)
        assertLiveActionsClear(card)
        assertInitialLiveStepperVisible(card, listOf("MOV", "APROX", "S1", "S2", "S3", "S4"))
        val line = activeTag("live-session-line").fetchSemanticsNode().boundsInRoot
        assertTrue("Tarjeta y línea caben enteras en el viewport reducido", card.top >= root.top && card.bottom <= line.top && line.bottom <= root.bottom)
        capture("19-live-reduced-height-760")
    }

    @Test fun realActivityLiveControlsKeepMovApproxAndEffectiveSetsWithoutScrolling() {
        check(isLiveControlsFixture && !isWeightImeFixture)
        val vm = launchWorkout()
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val permissionNames = listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList())
        val permissionsBefore = permissionNames.associateWith { app.checkSelfPermission(it) }
        val frame = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertReferenceFrame(frame)
        assertLiveActionsClear(frame)
        val stepper = assertInitialLiveStepperVisible(frame,
            expectedPills = listOf("MOV", "APROX", "S1", "S2", "S3", "S4"))
        capture("18-live-controls-initial-mov-approx-series")

        val voice = compose.onNodeWithTag("workout-voice-header", useUnmergedTree = true).assertIsDisplayed()
        val register = compose.onNode(hasContentDescription("Registrar serie") and hasClickAction())
            .assertIsDisplayed().assertIsEnabled()
        val voiceBounds = voice.fetchSemanticsNode().boundsInRoot
        val registerBounds = register.fetchSemanticsNode().boundsInRoot
        assertFalse("Voz y registro conservan zonas independientes", voiceBounds.overlaps(registerBounds))
        assertFalse("El stepper no queda cubierto por voz", stepper.overlaps(voiceBounds))
        assertFalse("El stepper no queda cubierto por registrar", stepper.overlaps(registerBounds))

        val mobilityKey = WorkoutStepRules.mobilityStepKey(exerciseId, "review-native-mobility-$suffix")
        val warmupKey = WorkoutStepRules.warmupStepKey(exerciseId, "review-native-warmup-$suffix")
        val workingKey = WorkoutStepRules.workingStepKey(exerciseId, 0, null)
        assertEquals("La actividad real inicia en S1; MOV/APROX se seleccionan desde el stepper",
            workingKey, vm.uiState.value.activeStepKey)
        compose.onNode(hasContentDescription("Movilidad") and
            hasAnyAncestor(hasTestTag("live-session-steps")), useUnmergedTree = true)
            .assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.uiState.value.activeStepKey == mobilityKey }
        assertPrepCardKeepsEverythingInside("mobility", "20-live-mobility-card")
        compose.onNode(hasContentDescription("Aproximación") and
            hasAnyAncestor(hasTestTag("live-session-steps")), useUnmergedTree = true)
            .assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.uiState.value.activeStepKey == warmupKey }
        assertPrepCardKeepsEverythingInside("warmup", "21-live-warmup-card")
        compose.onNode(hasContentDescription("Movilidad") and
            hasAnyAncestor(hasTestTag("live-session-steps")), useUnmergedTree = true)
            .assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.uiState.value.activeStepKey == mobilityKey }
        compose.onNode(hasText("S1") and hasAnyAncestor(hasTestTag("live-session-steps")),
            useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { vm.uiState.value.activeStepKey == workingKey }

        val voicePermissionsNeeded = WorkoutVoicePermissionHelper.permissionsToRequestForVoiceEnable(
            context = app,
            includeNotifications = true,
        )
        if (voicePermissionsNeeded.isEmpty()) {
            compose.onNodeWithTag("workout-voice-header", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Modo de voz", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Ahora no", useUnmergedTree = true).assertIsDisplayed().performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Modo de voz", useUnmergedTree = true).assertDoesNotExist()
        }
        assertFalse("Abrir y cancelar el selector no activa la sesión de voz", vm.uiState.value.voiceSessionEnabled)
        assertEquals("El selector no cambia permisos", permissionsBefore,
            permissionNames.associateWith { app.checkSelfPermission(it) })

        val slider = activeTag("effort_fire_slider").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Registrar serie") and hasClickAction())
            .assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(30_000) { vm.uiState.value.completedSets.size == 1 }
        runBlocking { withTimeout(20_000) { repository.ongoingWorkout.first { it?.completedSets?.size == 1 } } }
        val state = vm.uiState.value
        val recorded = state.completedSets.entries.single()
        assertEquals(exerciseId, parseCompletedSetKey(recorded.key)?.exerciseId)
        assertEquals(0, recorded.value.setIndex)
        assertEquals(setIds, state.session!!.allExercises().single().sets.map { it.id })
        assertEquals("Registrar conserva una única escritura en Room", 1, repository.ongoingWorkout.value!!.completedSets.size)
        assertFalse(state.voiceSessionEnabled)
        assertEquals("Registrar tampoco cambia permisos", permissionsBefore,
            permissionNames.associateWith { app.checkSelfPermission(it) })
        capture("19-live-controls-after-fab-record")
    }

    @Test fun exerciseMenuOpensRmAndQuickRecordsOneCanonicalSetWithoutReducingFour() {
        val vm = launchWorkout()
        // The exercise's long press moved from the old roadmap card to the route heading.
        compose.onNodeWithTag("live-session-route").performTouchInput { longClick() }
        compose.onNodeWithText("Calculadora RM").assertIsDisplayed().performClick()
        compose.onNodeWithTag("exercise-rm-sheet", true).assertExists()
        compose.onNodeWithText("Todavía no hay una referencia de RM").assertIsDisplayed()
        capture("04-rm-from-exercise-menu")
        compose.onNodeWithContentDescription("Cerrar consulta de RM").performClick()
        recordThroughQuickForm(vm)
        compose.runOnUiThread { vm.stopRestTimer() }
        compose.waitUntil(15_000) { !vm.uiState.value.isRestTimerRunning }
        assertQuickRecordLineAdvanced()
        val state = vm.uiState.value
        val recorded = state.completedSets.entries.single()
        assertEquals(exerciseId, parseCompletedSetKey(recorded.key)?.exerciseId)
        assertEquals(0, recorded.value.setIndex)
        assertEquals(95.0, recorded.value.weight, 0.0)
        assertEquals(6, recorded.value.reps)
        assertEquals(IntensityMode.RIR, recorded.value.actualIntensityMode)
        assertEquals(2.0, recorded.value.actualIntensityValue!!, 0.0)
        assertTrue(recorded.value.recordedPayloadV3?.intensityAdjusted == true)
        assertEquals(setIds, state.session!!.allExercises().single().sets.map { it.id })
        assertEquals(1, repository.ongoingWorkout.value!!.completedSets.size)
        capture("06-live-after-canonical-record")
    }

    @Test fun repeatedQuickRecordsCloseTheirSheetAndAdvanceTheSameFourSeries() {
        val vm = launchWorkout()
        repeat(3) { index ->
            recordThroughQuickForm(vm, expectedCompleted = index)
            compose.runOnUiThread { vm.stopRestTimer() }
            compose.waitUntil(15_000) { !vm.uiState.value.isRestTimerRunning }
            assertQuickRecordLineAdvanced(index + 1)
            assertEquals(index + 1, vm.uiState.value.currentSetIdx)
            assertEquals(setIds, vm.uiState.value.session!!.allExercises().single().sets.map { it.id })
            assertEquals(index + 1, repository.ongoingWorkout.value!!.completedSets.size)
        }
        capture("22-repeated-quick-records-v1")
    }

    @Test fun realActivityWeightImeKeepsFrameAndReturnsToSameSeries() {
        check(isWeightImeFixture)
        val vm = launchWorkout()
        if (isLiveControlsFixture) {
            compose.onNode(hasText("S1") and hasAnyAncestor(hasTestTag("live-session-steps")),
                useUnmergedTree = true).assertIsDisplayed().performClick()
            compose.waitUntil(10_000) {
                vm.uiState.value.activeStepKey == WorkoutStepRules.workingStepKey(exerciseId, 0, null)
            }
        }
        val initialState = vm.uiState.value
        val initialStep = initialState.activeStepKey
        val initialSetIndex = initialState.currentSetIdx
        val initialExerciseIndex = initialState.currentExerciseIdx
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val permissionNames = listOf(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()) +
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList())
        val permissionsBefore = permissionNames.associateWith { app.checkSelfPermission(it) }
        val frameBefore = activeTag("live-card-frame").getUnclippedBoundsInRoot()
        val frameBeforeBounds = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertInitialLiveStepperVisible(frameBeforeBounds,
            expectedPills = listOf("MOV", "APROX", "S1", "S2", "S3", "S4"))
        assertLiveActionsClear(frameBeforeBounds)
        val loadFieldMatcher = hasSetTextAction() and hasAnyAncestor(hasTestTag("live-card-frame"))
        val initialField = activeNode(loadFieldMatcher)
        initialField.performScrollTo().assertIsDisplayed()
        assertFalse("La prueba comienza sin teclado", nativeImeViewport().first)
        capture("15-native-weight-before-ime")
        initialField.performClick()
        var lastImeBottom = -1
        var insetStableSince = SystemClock.uptimeMillis()
        compose.waitUntil(10_000) {
            val (visible, bottom) = nativeImeViewport()
            val now = SystemClock.uptimeMillis()
            if (bottom != lastImeBottom) {
                lastImeBottom = bottom
                insetStableSince = now
            }
            visible && bottom > 0 && now - insetStableSince >= 250L
        }
        activeNode(loadFieldMatcher).performTextReplacement("97,5")
        compose.waitUntil(10_000) {
            activeNode(loadFieldMatcher).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.EditableText)?.text?.replace(',', '.')?.toDoubleOrNull() == 97.5
        }
        compose.waitForIdle()
        val frameWithIme = activeTag("live-card-frame").getUnclippedBoundsInRoot()
        val fieldWithIme = activeNode(loadFieldMatcher).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val ime = nativeImeViewport()
        val keyboardBounds = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            Rect(0f, (decor.height - ime.second).toFloat(), decor.width.toFloat(), decor.height.toFloat())
        }
        capture("16-native-weight-with-ime")
        assertTrue("El teclado Android debe estar visible", ime.first)
        assertEquals("El teclado no estrecha el marco", (frameBefore.right - frameBefore.left).value, (frameWithIme.right - frameWithIme.left).value, .5f)
        assertEquals("El teclado no achata el marco", (frameBefore.bottom - frameBefore.top).value, (frameWithIme.bottom - frameWithIme.top).value, .5f)
        assertTrue("El campo de carga debe tener área visible", fieldWithIme.width > 0f && fieldWithIme.height > 0f)
        assertFalse("El teclado no puede tapar el campo de carga: campo=$fieldWithIme teclado=$keyboardBounds",
            fieldWithIme.overlaps(keyboardBounds))
        val registerWithIme = compose.onNode(hasContentDescription("Registrar serie") and hasClickAction())
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val voiceWithIme = compose.onNodeWithTag("workout-voice-header", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val stepperWithIme = compose.onNodeWithTag("live-session-steps", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInWindow
        assertFalse("El stepper no puede tapar registrar con IME", stepperWithIme.overlaps(registerWithIme))
        assertFalse("El stepper no puede tapar voz con IME", stepperWithIme.overlaps(voiceWithIme))
        val cardControls = compose.onAllNodes(hasAnyAncestor(hasTestTag("live-card-frame")) and
            (hasClickAction() or hasSetTextAction() or hasTestTag("effort_fire_slider")),
            useUnmergedTree = true)
        val controlNodes = cardControls.fetchSemanticsNodes()
        controlNodes.forEachIndexed { index, node ->
            if (cardControls[index].isDisplayed()) {
                val visibleBounds = node.boundsInWindow
                if (visibleBounds.width > 0f && visibleBounds.height > 0f) {
                    assertFalse("Registrar no puede tapar un control visible de la tarjeta con IME: ${node.id}:$visibleBounds",
                        visibleBounds.overlaps(registerWithIme))
                    assertFalse("Voz no puede tapar un control visible de la tarjeta con IME: ${node.id}:$visibleBounds",
                        visibleBounds.overlaps(voiceWithIme))
                }
            }
        }
        // Send Android's actual Back key so IME consumes it before activity navigation.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) { !nativeImeViewport().first }
        compose.waitForIdle()
        capture("17-native-weight-after-back")
        val frameAfter = activeTag("live-card-frame").getUnclippedBoundsInRoot()
        val enteredWeight = activeNode(loadFieldMatcher).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.EditableText)?.text?.replace(',', '.')?.toDoubleOrNull()
        assertNotNull("La carga escrita sigue en el mismo campo tras Atrás", enteredWeight)
        assertEquals(97.5, enteredWeight!!, .0001)
        assertEquals((frameBefore.right - frameBefore.left).value, (frameAfter.right - frameAfter.left).value, .5f)
        assertEquals((frameBefore.bottom - frameBefore.top).value, (frameAfter.bottom - frameAfter.top).value, .5f)
        val state = vm.uiState.value
        assertEquals(sessionId, state.session?.id)
        assertEquals(initialStep, state.activeStepKey)
        assertEquals(initialExerciseIndex, state.currentExerciseIdx)
        assertEquals(initialSetIndex, state.currentSetIdx)
        assertEquals(setIds, state.session!!.allExercises().single().sets.map { it.id })
        assertTrue("Editar carga no registra una serie", state.completedSets.isEmpty())
        assertFalse(state.isComplete)
        assertFalse(state.voiceSessionEnabled)
        assertEquals("Editar carga no cambia permisos", permissionsBefore,
            permissionNames.associateWith { app.checkSelfPermission(it) })
        val folder = File(context.getExternalFilesDir(null), "qa16-live-controls").apply { mkdirs() }
        File(folder, "17-native-weight-ime-geometry.json").writeText(JSONObject()
            .put("evidence", "MainActivity real; carga de fixture .dbg; teclado Android real")
            .put("frameBeforeDp", frameBefore.toString()).put("frameWithImeDp", frameWithIme.toString())
            .put("frameAfterDp", frameAfter.toString()).put("fieldVisibleWindowPx", fieldWithIme.toString())
            .put("keyboardWindowPx", keyboardBounds.toString()).put("imeBottomPx", ime.second)
            .put("sameStep", state.activeStepKey == initialStep).put("seriesCount", setIds.size)
            .put("recordedCount", state.completedSets.size).put("permissionsUnchanged", true).toString(2))
    }

    private fun nativeImeViewport(): Pair<Boolean, Int> = compose.runOnIdle {
        val insets = checkNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
        insets.isVisible(WindowInsetsCompat.Type.ime()) to insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    }

    @Test fun realActivityCardioKeepsBoundedVisibleViewportWithoutStartingGpsOrRecording() {
        check(isCardioFixture)
        val vm = launchWorkout()
        // Match the actual production CardioLiveCard scroll viewport. This checks
        // the former weight(1f) branch through MainActivity, not a replacement host.
        val cardioViewport = compose.onNode(hasScrollAction() and
            hasAnyDescendant(hasText("TIEMPO")) and hasAnyDescendant(hasText("Iniciar")),
            useUnmergedTree = true)
        capture("13-native-cardio-initial")
        cardioViewport.assertExists().assertIsDisplayed()
        val bounds = cardioViewport.fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("El viewport cardio no puede colapsar al introducir el scroll de fuerza: $bounds",
            bounds.height >= 184f * density && bounds.width >= 200f * density)
        compose.onNodeWithText("TIEMPO", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Iniciar").performScrollTo().assertIsDisplayed()
        capture("14-native-cardio-controls")
        val state = vm.uiState.value
        assertEquals(cardioExercise.id, state.session!!.allExercises().single().id)
        assertEquals(listOf("review-native-cardio-set-$suffix"), state.session!!.allExercises().single().sets.map { it.id })
        assertTrue("La prueba no registra cardio", state.completedSets.isEmpty())
        assertTrue("La prueba no inicia el cronómetro cardio", state.cardioTimerState == null ||
            state.cardioTimerState?.status == CardioExecutionStatus.READY)
        assertFalse(state.isRestTimerRunning)
        assertFalse(cardioExercise.cardioDetails!!.requiresGps)
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("La prueba cardio no concede ni revoca ubicación", locationPermissionsBeforeCardio,
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) to
                app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        compose.onNodeWithText("Iniciar GPS").assertDoesNotExist()
    }

    @Test fun migratedNotesAndTasksPersistAndReachTheRealFinalSummary() {
        val vm = launchWorkout()
        recordThroughQuickForm(vm)
        compose.runOnUiThread { vm.stopRestTimer() }
        compose.waitUntil(15_000) { !vm.uiState.value.isRestTimerRunning }
        compose.onNodeWithTag("workout-more-header").performClick()
        compose.onNodeWithTag("live-session-menu-notes").performScrollTo().performClick()
        val note = "Nota desde la hoja migrada $suffix"
        compose.onNode(hasSetTextAction() and hasText("Anotaciones generales de la sesión…", substring = true))
            .performTextReplacement(note)
        compose.onNodeWithText("Guardar nota").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Nueva tarea")).performScrollTo().performTextReplacement("Beber agua QA")
        compose.onNodeWithText("Añadir").performScrollTo().performClick()
        runBlocking { withTimeout(20_000) { repository.ongoingWorkout.first { state ->
            state?.let { it.sessionNotes == note && it.sessionChecklist.any { task -> task.text == "Beber agua QA" } } == true
        } } }
        compose.onNodeWithContentDescription("Cerrar Notas y tareas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("live-session-sheet-notes").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread { vm.showFinish() }
        waitForTag("finish-tabs")
        compose.onNodeWithText("Notas").performScrollTo().performClick()
        compose.onNodeWithTag("finish-notes", true).assertTextContains(note)
        compose.onNodeWithText("Beber agua QA").assertExists()
        assertEquals(note, vm.uiState.value.sessionSavedNotes.last().text)
        capture("20-migrated-notes-in-final-summary")
    }

    @Test fun storyFromTheHeaderOpensTheExistingStoryWithoutSavingTheWorkout() {
        val vm = launchWorkout()
        recordThroughQuickForm(vm)
        compose.runOnUiThread { vm.stopRestTimer() }
        compose.waitUntil(15_000) { !vm.uiState.value.isRestTimerRunning }
        assertQuickRecordLineAdvanced()
        val historyBefore = repository.history.value.map { it.id }
        compose.onNodeWithTag("workout-story-header").performClick()
        waitForTag("story-sheet")
        compose.onNodeWithTag("story-templates").assertIsDisplayed()
        compose.waitUntil(30_000) { compose.onNodeWithTag("story-preview", true).isDisplayed() }
        val sheet = compose.onNodeWithTag("story-sheet", true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        listOf("story-templates", "story-share-after-save", "story-save-image", "story-share").forEach { tag ->
            val bounds = compose.onNodeWithTag(tag, true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag tiene alto visible", bounds.height > 0)
            assertTrue("$tag está íntegro en Historia", bounds.top >= sheet.top && bounds.bottom <= sheet.bottom)
        }
        val preview = compose.onNodeWithTag("story-preview", true).fetchSemanticsNode().boundsInRoot
        assertEquals(9f / 16f, preview.width / preview.height, .01f)
        assertFalse("La entrada a Historia no guarda ni termina la sesión", vm.uiState.value.isComplete)
        assertEquals(sessionId, repository.ongoingWorkout.value?.session?.id)
        assertEquals(historyBefore, repository.history.value.map { it.id })
        assertEquals(1, vm.uiState.value.completedSets.size)
        capture("21-story-from-live-header")
        compose.onNodeWithContentDescription("Volver al resumen").performClick()
        compose.onNodeWithTag("finish-tabs", true).assertIsDisplayed()
        assertFalse(vm.uiState.value.isComplete)
        assertEquals(historyBefore, repository.history.value.map { it.id })
    }

    @Test fun closingBackReturnsToRealSessionAndReopenedNotesRetainPersistedDraft() {
        val vm = launchWorkout()
        recordThroughQuickForm(vm)
        compose.runOnUiThread { vm.stopRestTimer(); vm.showFinish() }
        waitForTag("finish-tabs")
        compose.onNodeWithText("Notas").performScrollTo().performClick()
        val note = "Nota sintética de QA $suffix"
        compose.onNodeWithTag("finish-notes", true).performTextReplacement(note)
        compose.runOnUiThread { compose.activity.window.decorView.clearFocus() }
        runBlocking { withTimeout(20_000) { repository.ongoingWorkout.first { it?.sessionNotes == note } } }
        capture("07-close-notes-draft")
        compose.onNodeWithTag("finish-back", true).performClick()
        compose.onNodeWithTag("finish-pager", true).assertDoesNotExist()
        activeTag("secondary_pill_avanzado").assertIsDisplayed()
        assertEquals(sessionId, ActiveWorkoutHolder.get()?.uiState?.value?.session?.id)
        assertFalse(vm.uiState.value.isComplete)
        assertEquals(note, vm.uiState.value.sessionNotes)
        capture("08-back-to-live-no-black-screen")
        compose.runOnUiThread { vm.showFinish() }
        waitForTag("finish-tabs")
        compose.onNodeWithText("Notas").performScrollTo().performClick()
        compose.onNodeWithTag("finish-notes", true).assertTextContains(note)
        assertEquals(note, repository.ongoingWorkout.value!!.sessionNotes)
        capture("09-reopened-notes")
    }

    @Test fun realRestClockKeepsItsDeadlineWhileOpeningPlayingRestartingAndReturning() {
        val vm = launchWorkout()
        compose.runOnUiThread { vm.startRestTimer(60) }
        compose.waitUntil(15_000) { vm.uiState.value.isRestTimerRunning &&
            (vm.uiState.value.restModalState?.endsAtMs ?: 0L) > 0L }
        val deadline = checkNotNull(vm.uiState.value.restModalState).endsAtMs
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("rest-intro-done", true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("rest-play", true).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("rest-intro-done", true).fetchSemanticsNodes().isNotEmpty()) {
            activeTag("rest-intro-done").performClick()
        }
        waitForTag("rest-play")
        assertRealRestTick(vm, deadline)
        capture("10-real-rest-countdown")
        activeTag("rest-play").performClick()
        waitForTag("rest-game-flappy")
        activeTag("rest-game-runner").assertExists()
        activeTag("rest-game-shooter").assertExists()
        assertRealRestTick(vm, deadline)
        activeTag("rest-game-flappy").performClick()
        waitForTag("rest-game-canvas-flappy")
        // The first tap starts the flight; the rest clock keeps its real tick.
        activeTag("rest-game-canvas-flappy").performTouchInput { click(center) }
        assertRealRestTick(vm, deadline)
        capture("11-real-rest-flappy")
        // «Volver» closes the game into the picker; a second «Volver» returns to the rest.
        activeTag("arcade-back").performClick()
        waitForTag("rest-game-flappy")
        assertRealRestTick(vm, deadline)
        activeTag("arcade-back").performClick()
        waitForTag("rest-play")
        compose.onNodeWithTag("arcade-rest-clock", true).assertDoesNotExist()
        assertRealRestTick(vm, deadline)
        capture("12-real-rest-return-same-deadline")
    }

    private fun assertRealRestTick(vm: WorkoutViewModel, deadline: Long) {
        val previous = vm.restTimerRemaining.value
        assertTrue("El descanso real debe seguir activo", vm.uiState.value.isRestTimerRunning)
        assertTrue("El fixture todavía debe tener tiempo", previous > 0)
        compose.waitUntil(8_000) { vm.restTimerRemaining.value < previous }
        assertTrue(vm.uiState.value.isRestTimerRunning)
        assertEquals("Jugar/reiniciar/volver no puede reiniciar ni extender el descanso", deadline,
            vm.uiState.value.restModalState?.endsAtMs)
    }

    private fun launchWorkout(): WorkoutViewModel {
        compose.activityRule.scenario.onActivity { activity ->
            launchedActivity = activity
            if (caseName.methodName == "realActivityReducedHeightKeepsCardAndLineComplete") {
                // Constrain the real Android content view; no setContent fixture, device resize or scaled screenshot.
                val content = activity.findViewById<android.view.View>(android.R.id.content)
                content.layoutParams = content.layoutParams.apply { height = (760f * activity.resources.displayMetrics.density).toInt() }
                content.requestLayout()
            }
            originalLaunchIntent = Intent(activity.intent)
            activity.startActivity(Intent(activity, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse("kpkn://workout/$programId/$sessionId"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        compose.waitUntil(90_000) {
            ActiveWorkoutHolder.get()?.uiState?.value?.let {
                it.session?.id == sessionId && !it.isStartingWorkout
            } == true
        }
        val vm = checkNotNull(ActiveWorkoutHolder.get())
        fixtureVm = vm
        waitForTag("readiness-confirm")
        compose.onNodeWithTag("readiness-confirm", true).performClick()
        if (isCardioFixture) {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("TIEMPO", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } else {
            waitForTag("secondary_pill_avanzado")
        }
        assertEquals(programId, vm.uiState.value.programId)
        assertEquals(sessionId, repository.ongoingWorkout.value?.session?.id)
        return vm
    }

    private fun recordThroughQuickForm(vm: WorkoutViewModel, expectedCompleted: Int = 0) {
        compose.runOnUiThread { vm.previewUltraFast() }
        waitForTag("ultra-record-submit")
        for (index in 0 until 4) compose.onNodeWithTag("ultra-record-row-$index", true).assertExists()
        assertEquals(setIds, vm.uiState.value.session!!.allExercises().single().sets.map { it.id })
        compose.onNodeWithTag("ultra-record-submit", true).assertIsNotEnabled()
        assertEquals(expectedCompleted, vm.uiState.value.completedSets.size)
        capture("05-quick-four-series-effort-required")
        compose.onNodeWithContentDescription("RIR", useUnmergedTree = true).performTextReplacement("2")
        compose.onNodeWithTag("ultra-record-submit", true).assertIsEnabled().performClick()
        compose.waitUntil(30_000) { vm.uiState.value.completedSets.size == expectedCompleted + 1 && !vm.uiState.value.showUltraFastSheet }
        compose.onNodeWithTag("ultra-record-submit", true).assertDoesNotExist()
        // The sheet closes at commit, before the command finishes starting rest/advancing.
        compose.waitUntil(30_000) { vm.uiState.value.recordingSetKey == null }
        runBlocking { withTimeout(20_000) { repository.ongoingWorkout.first { it?.completedSets?.size == expectedCompleted + 1 } } }
    }

    private fun assertQuickRecordLineAdvanced(completed: Int = 1) {
        compose.onNode(hasText("$completed/4", substring = true) and hasAnyAncestor(hasTestTag("live-session-route")), true).assertIsDisplayed()
        compose.onNodeWithText("✓S$completed", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("S${completed + 1}") and isSelected(), true).assertIsDisplayed()
    }

    /** Card/page queries are scoped to the workout window, anchored by its header voice action.
     * Nonzero bounds alone also match unplaced retained pager content. isDisplayed() checks
     * placement/window visibility; then clipped area distinguishes active card from side peeks
     * and the full modal rest control from the compact card underneath it.
     */
    private fun activeTag(tag: String): SemanticsNodeInteraction = activeNode(hasTestTag(tag),
        workoutScope = tag.startsWith("secondary_pill_") || tag == "media_face")

    private fun semanticsRootId(node: SemanticsNode): Int {
        var root = node
        while (true) {
            val parent = root.parent ?: return root.id
            root = parent
        }
    }

    private fun activeNode(matcher: SemanticsMatcher, workoutScope: Boolean = true): SemanticsNodeInteraction {
        var selectedId: Int? = null
        var diagnostic = "No displayed workout anchor/candidate yet"
        try {
            compose.waitUntil(10_000) {
                try {
                    val anchors = compose.onAllNodesWithTag("workout-voice-header", useUnmergedTree = true)
                    val anchorNodes = anchors.fetchSemanticsNodes(atLeastOneRootRequired = false)
                    val workoutRoots = anchorNodes.mapIndexedNotNull { index, node ->
                        if (anchors[index].isDisplayed()) semanticsRootId(node) else null
                    }.toSet()
                    val candidates = compose.onAllNodes(matcher, useUnmergedTree = true)
                    val nodes = candidates.fetchSemanticsNodes(atLeastOneRootRequired = false)
                    val visible = nodes.mapIndexedNotNull { index, node ->
                        val bounds = node.boundsInRoot
                        val area = bounds.width.coerceAtLeast(0f) * bounds.height.coerceAtLeast(0f)
                        if (area > 1f && (!workoutScope || semanticsRootId(node) in workoutRoots) && candidates[index].isDisplayed())
                            node to area else null
                    }.sortedByDescending { it.second }
                    diagnostic = "workoutRoots=$workoutRoots; candidates=" + nodes.joinToString {
                        "${it.id}:root=${semanticsRootId(it)}:${it.boundsInRoot}"
                    } + "; displayed=" + visible.joinToString { "${it.first.id}:${it.first.boundsInRoot}" }
                    val chosen = visible.firstOrNull()
                    if (chosen == null || visible.count { kotlin.math.abs(it.second - chosen.second) < 1f } != 1) false
                    else { selectedId = chosen.first.id; true }
                } catch (error: IllegalStateException) {
                    // Animated face replacement can briefly detach all registered Compose roots.
                    if (error.message?.contains("No compose hierarchies") == true) false else throw error
                }
            }
        } catch (error: Throwable) {
            throw AssertionError("No unique displayed active workout node for $matcher within 10s; $diagnostic", error)
        }
        val id = checkNotNull(selectedId)
        return compose.onNode(matcher and SemanticsMatcher("displayed workout node $id") {
            it.id == id
        }, useUnmergedTree = true)
    }

    private fun waitForTag(tag: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun assertReferenceFrame(frame: Rect) {
        val metrics = context.resources.displayMetrics
        val availableDp = context.resources.configuration.screenWidthDp
        val expectedWidth = (availableDp - 48).coerceAtMost(358) * metrics.density
        assertEquals("24dp gutters and original max width", expectedWidth, frame.width, 2f)
        assertEquals("Proporción V1 de 312 × 425 dp", 425f / 312f, frame.height / frame.width, .002f)
        if (availableDp == 360) {
            assertEquals("Ancho V1 exacto en 360dp", 312f * metrics.density, frame.width, 2f)
            assertEquals("Alto V1 exacto en 360dp", 425f * metrics.density, frame.height, 2f)
        }
    }

    /**
     * LV-C: las tarjetas de movilidad y aproximación miden lo mismo que la de la serie (312 × 425 dp) y llevan TODOS sus controles dentro.
     * [phase] es «mobility» o «warmup».
     */
    private fun assertPrepCardKeepsEverythingInside(phase: String, captureName: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("prep-live-card", true).fetchSemanticsNodes().isNotEmpty() }
        val frame = activeTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        assertReferenceFrame(frame)
        val card = activeTag("prep-live-card").fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertEquals("La tarjeta de $phase llena el marco: ancho", frame.width, card.width, 2f)
        assertEquals("La tarjeta de $phase llena el marco: alto", frame.height, card.height, 2f)
        val controls = buildList {
            add("prep-skip"); add("prep-status")
            if (phase == "mobility") { add("mob-global-chip"); add("mob-list-toggle"); add("mob-done"); add("mob-movement-pane") }
            else { add("warmup-add"); add("warmup-list") }
        }
        controls.forEach { tag ->
            val b = activeTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag queda dentro de la tarjeta de $phase: $b frente a $card",
                b.left >= card.left - 2f && b.right <= card.right + 2f && b.top >= card.top - 2f && b.bottom <= card.bottom + 2f)
            if (tag != "prep-status" && !tag.endsWith("pane") && !tag.endsWith("list")) {
                assertTrue("$tag conserva 48 dp táctiles", b.width / density >= 47.5f && b.height / density >= 47.5f)
            }
        }
        capture(captureName)
    }

    private fun assertLiveActionsClear(cardContent: Rect) {
        val register = compose.onNode(hasContentDescription("Registrar serie") and hasClickAction())
        val mic = compose.onNodeWithTag("workout-voice-header", true)
        register.assertIsDisplayed(); mic.assertIsDisplayed()
        val r = register.fetchSemanticsNode().boundsInRoot
        val m = mic.fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("Voz conserva al menos 48dp táctiles", m.width / density >= 48f && m.height / density >= 48f)
        assertTrue("Registrar conserva al menos 48dp táctiles", r.width / density >= 48f && r.height / density >= 48f)
        assertTrue("Voz vive en la cabecera, encima de la tarjeta", m.bottom <= cardContent.top)
        assertFalse("Voz y registrar no se solapan", r.overlaps(m))
        assertTrue("Registrar queda debajo de la tarjeta", r.top >= cardContent.bottom - 1f)
        val line = compose.onNodeWithTag("live-session-line", true).fetchSemanticsNode().boundsInRoot
        assertTrue("El registro queda integrado entero en la isla", r.left >= line.left && r.right <= line.right && r.top >= line.top && r.bottom <= line.bottom)
        val route = compose.onNodeWithTag("live-session-route", true).fetchSemanticsNode().boundsInRoot
        assertFalse("Ruta no tapa registrar", route.overlaps(r))
        assertFalse("Ruta no tapa voz", route.overlaps(m))
        compose.onNodeWithTag("workout-voice-fab", true).assertDoesNotExist()
    }

    /** V1 centers the selected step and compresses distant steps. The rail is fully visible initially;
     * all old destinations remain reachable through its horizontal viewport, including preparation icons.
     */
    private fun assertInitialLiveStepperVisible(cardContent: Rect, expectedPills: List<String> = emptyList()): Rect {
        val node = activeTag("live-session-steps").assertIsDisplayed().fetchSemanticsNode()
        val bounds = node.boundsInRoot
        val density = context.resources.displayMetrics.density
        val rootBounds = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("La línea de pasos conserva 56dp íntegros", 56f * density, bounds.height, 1f)
        val island = activeTag("live-session-line").fetchSemanticsNode().boundsInRoot
        assertEquals("La isla V1 conserva 112dp", 112f * density, island.height, 1f)
        assertTrue("Línea íntegra debajo de tarjeta: root=$rootBounds card=$cardContent line=$island",
            island.top >= cardContent.bottom - 1f && island.bottom <= rootBounds.bottom)
        expectedPills.forEach { label ->
            val matcher = when (label) {
                "MOV" -> hasContentDescription("Movilidad")
                "APROX" -> hasContentDescription("Aproximación")
                else -> hasText(label)
            }
            compose.onNodeWithTag("live-session-steps", true).performScrollToNode(matcher)
            val pill = compose.onNode(matcher and hasAnyAncestor(hasTestTag("live-session-steps")), true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$label queda entero en la ventana de la línea", pill.left >= bounds.left - 1f && pill.right <= bounds.right + 1f)
        }
        if (expectedPills.isNotEmpty()) compose.onNodeWithTag("live-session-steps", true).performScrollToNode(hasText("S1"))
        compose.onNodeWithContentDescription("Serie anterior", true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Serie siguiente", true).assertDoesNotExist()
        return bounds
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(context.getExternalFilesDir(null), "qa16-live-controls").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val nodes = compose.onAllNodes(SemanticsMatcher("all") { true }, true).fetchSemanticsNodes()
        val bounds = JSONArray()
        nodes.forEach { node ->
            val b = node.boundsInRoot
            bounds.put(JSONObject().put("id", node.id)
                .put("tag", node.config.getOrNull(SemanticsProperties.TestTag).orEmpty())
                .put("text", node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty())
                .put("description", node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty())
                .put("left", b.left).put("top", b.top).put("right", b.right).put("bottom", b.bottom))
        }
        File(folder, "$name.json").writeText(JSONObject().put("evidence", "MainActivity real; fixture .dbg; in-memory Room").put("versionCode", BuildConfig.VERSION_CODE).put("versionName", BuildConfig.VERSION_NAME)
            .put("fixtureSessionId", sessionId).put("width", bitmap.width).put("height", bitmap.height)
            .put("boundsCoordinateSpace", "Compose root px; screenshot includes system bars")
            .put("nodes", bounds).toString(2))
        bitmap.recycle()
    }

    private fun fixtureProgram() = Program(
        id = programId,
        name = "Programa QA nativa",
        macrocycles = listOf(
            Macrocycle(id = "macro-$suffix", name = "Macro", blocks = listOf(
                Block(id = "block-$suffix", name = "Bloque", mesocycles = listOf(
                    Mesocycle(id = "meso-$suffix", name = "Meso", weeks = listOf(
                        ProgramWeek(id = "week-$suffix", name = "Semana", sessions = listOf(
                            if (isCardioFixture) fixtureSession.copy(name = "Cardio · QA nativa", exercises = listOf(cardioExercise))
                            else if (isLiveControlsFixture) fixtureSession.copy(exercises = listOf(liveControlsExercise))
                            else fixtureSession)),
                    )),
                )),
            )),
        ),
    )

    private fun setDatabase(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, database)
    }

    private fun clearSingleton(owner: Class<*>) {
        val field = (sequenceOf(owner) + owner.declaredClasses.asSequence()).flatMap { it.declaredFields.asSequence() }
            .first { (it.name.equals("instance", true) || it.name == "INSTANCE") && owner.isAssignableFrom(it.type) }
        field.isAccessible = true
        val receiver = if (Modifier.isStatic(field.modifiers)) null else
            owner.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        field.set(receiver, null)
    }

    private class FixtureContext(base: Context, private val suffix: String) : ContextWrapper(base) {
        private val names = linkedSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val own = "review-native-$suffix-$name"
            synchronized(names) { names += own }
            return baseContext.getSharedPreferences(own, mode)
        }
        fun clearOwnPreferences() {
            synchronized(names) { names.toList().also { names.clear() } }.forEach(baseContext::deleteSharedPreferences)
        }
    }
}
