package com.example.kpkn.screens.workout

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.exercises.catalogv2.CatalogV2ProcessCache
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.exercises.ExerciseHistorySummary
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import com.example.kpkn.screens.sessioneditor.components.ExerciseFichaContent
import com.example.kpkn.screens.sessioneditor.components.resolveCatalogExerciseV2
import com.example.kpkn.screens.sessioneditor.components.sessionExerciseDetailModel
import com.example.kpkn.screens.workout.components.EffortSliderTag
import com.example.kpkn.screens.workout.components.SetInputCardV2
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Native review screenshots only; example data, no acceptance suite or persisted workout mutations. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class RedesignReviewSetCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Before fun configureProductionWindow() {
        rule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
            if (android.os.Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
        }
    }

    private val accent = Color(0xFFF49A6E)
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun save(tag: String, name: String, fixture: String) {
        rule.waitForIdle()
        val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "review-set").apply { mkdirs() }
        val file = File(directory, "$name.png")
        val width = bitmap.width
        val height = bitmap.height
        val written = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(written && file.length() > 0L) { "PNG not written: $file" }
        File(directory, "$name.json").writeText(JSONObject()
            .put("file", file.name).put("widthPx", width).put("heightPx", height)
            .put("capturedAtMs", System.currentTimeMillis()).put("package", context.packageName)
            .put("source", "Native production Compose component rendered on physical device")
            .put("fixture", fixture).put("persistedUserData", false).toString(2))
        println("KPKN_REVIEW_SET_PNG=${file.absolutePath}")
        Log.i("KPKN_REVIEW_SET", "${file.absolutePath} ${width}x$height")
    }

    @Test fun tarjetaEsfuerzoYTecnicas() {
        val set = ExerciseSet(id = "review-set", targetReps = 8, targetRPE = 8.0, intensityMode = IntensityMode.RPE,
            weight = 60.0, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS)
        val exercise = Exercise(id = "review-press", name = "Press de banca", sets = listOf(set))
        val holder = RecordActionHolder()
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
                    // Same 272dp card width as the existing LV8 native/render fixture; crop includes only 8dp surround.
                    Box(Modifier.width(288.dp).background(Color.Black).testTag("review-card").padding(8.dp)) {
                        SetInputCardV2(
                            exercise = exercise, setIndex = 0, currentSet = set, ghostSet = null,
                            initialBodyWeight = 80.0, recordActionHolder = holder,
                            onShowHistory = {}, onSetBodyWeight = {}, sessionAccentColor = accent,
                            initialDraft = WorkoutSetDraft(weightText = "60", valueText = "8", intensityText = "8", isDirty = true),
                            effortFireSecondsOverride = 0.45f,
                            onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> },
                        )
                    }
                }
            }
        }
        save("review-card", "01-serie-frontal", "Press banca; carga60kg,8reps,RPE8; card production width272dp")
        val items = buildIntensityCarouselItems(IntensityMode.RPE, IntensityMode.RPE, null, 8.0)
        val closeToFailure = items.indexOfFirst { it.mode == IntensityMode.RPE && it.numericValue == 9.5 }
        check(closeToFailure >= 0)
        rule.onNodeWithTag(EffortSliderTag).performSemanticsAction(SemanticsActions.SetProgress) { it(closeToFailure.toFloat()) }
        save("review-card", "02-esfuerzo-fuego", "Same card; actual selector moved toRPE9.5; existing flame frame0.45")
        rule.onNodeWithTag("secondary_pill_avanzado").performClick()
        rule.onNodeWithText("Drop-set").performClick()
        save("review-card", "03-avanzado-drop", "Real flip and Drop-set segment; default horizontal mini entries; same card dimensions")
        rule.onNodeWithText("Rest-pause").performClick()
        save("review-card", "04-avanzado-rest-pause", "Real Rest-pause segment after Drop-set; default mini entries; no persistence")
    }

    @Test fun relatorReposoAccionProcesoYHecho() {
        var offered by mutableStateOf(false)
        var phase by mutableStateOf("working")
        var workingFrame by mutableStateOf<Float?>(null)
        var sparkFrame by mutableStateOf<Float?>(null)
        val idle = "Respira, apoya bien los pies y prepara la siguiente serie."
        val offer = "Hoy conviene ajustar. Ajusta carga."
        val action = RelatorAssistAction(RelatorAssistActionKind.ADJUST_LOAD, "Ajusta carga",
            exerciseId = "review-press", setIndex = 0, span = "Ajusta carga", loadDeltaPercent = -5.0)
        var draft = 60.0
        var calls = 0
        rule.setContent {
            KPKNTheme {
                val band = WorkoutUiTokens.liveRelatorBandHeight(LocalDensity.current)
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.TopCenter) {
                    WorkoutLiveRelatorLine(
                        text = if (offered) offer else idle, phaseKey = phase,
                        actions = if (offered) listOf(action) else emptyList(), accentColor = accent,
                        modifier = Modifier.width(360.dp).height(band).background(Color.Black).testTag("review-relator"),
                        forceShimmerSweepProgress = 0.32f, forceWorkingProgress = workingFrame, forceSparkProgress = sparkFrame,
                        actionContext = RelatorActionUiContext("review-press:0") { clicked ->
                            calls += 1
                            val previous = draft
                            draft = LoadSuggestionEngine.roundLoad(draft * (1.0 + (clicked.loadDeltaPercent ?: 0.0) / 100.0))
                            phase = "assist_confirm"
                            RelatorAssistAck(clicked.kind, applied = true, appliedWeightKg = draft,
                                previousWeightKg = previous, canUndoLoad = true)
                        },
                    )
                }
            }
        }
        save("review-relator", "05-relator-reposo", "Production relator360dp and liveRelatorBandHeight; example preparation phrase")
        rule.runOnIdle { offered = true }
        rule.onNodeWithText(offer, useUnmergedTree = true).assertExists()
        save("review-relator", "06-relator-accion", "Example inline percentage action; no separate button/label")
        rule.mainClock.autoAdvance = false
        val node = rule.onNodeWithText(offer, useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val hit = layouts.single().getBoundingBox(offer.indexOf("Ajusta carga") + 4).center
        node.performTouchInput { click(hit) }
        rule.mainClock.advanceTimeBy(32)
        rule.runOnIdle { check(calls == 1 && draft == 57.0); workingFrame = 0.45f }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithText("El equipo está cargando los discos de 57\u00A0kg…", useUnmergedTree = true).assertExists()
        save("review-relator", "07-relator-procesando", "Real inline touch and applied Ack60→57kg; existing working progress frozen0.45 after dispatch")
        rule.runOnIdle { workingFrame = null }
        rule.mainClock.advanceTimeBy(2100)
        rule.mainClock.autoAdvance = true
        val done = "Listo: la serie 1 va con 57\u00A0kg. ↶ vuelve a 60."
        rule.waitUntil(5_000) { rule.onAllNodesWithText(done, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.runOnIdle { sparkFrame = 0.35f }
        save("review-relator", "08-relator-hecho-deshacer", "Same real action reached done; previous example load60kg; existing spark progress0.35")
    }

    @Test fun fichaUnificadaArribaYDesplazada() {
        val catalog = runBlocking(Dispatchers.IO) { CatalogV2ProcessCache.getOrLoad(context).catalog }
        val resolved = requireNotNull(resolveCatalogExerciseV2(Exercise(id = "review-press", name = "Press de banca",
            catalogDefinitionId = ExerciseTechniqueImageLookup.FLAT_BENCH_PRESS_DEFINITION_ID), catalog, null))
        val model = sessionExerciseDetailModel(resolved).copy(historySummary = ExerciseHistorySummary(
            bestSet = CompletedSet(id = "review-history", weight = 65.0, reps = 8, rpe = 8.0),
            estimatedRmKg = 82.3, lastDate = LocalDate.now().minusDays(3),
        ))
        check(model.imageVariants.isNotEmpty()) { "No canonical image available; do not substitute" }
        rule.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false) }
        var density = 1f
        rule.setContent {
            KPKNTheme {
                density = LocalDensity.current.density
                ExerciseFichaContent(
                    model = model, onDismiss = {}, onToggleSelection = {}, onSelectOption = { _, _ -> },
                    onImplementationSettled = {}, showFooter = false, onShowHistory = {},
                )
            }
        }
        rule.onNodeWithTag("exercise-ficha-photo").assertExists()
        save("exercise-ficha", "09-ficha-superior", "Approved offline catalog definition/configuration/cues/image. Example history65kg×8,RPE8,3days,estimated1RM82.3")
        rule.onNodeWithTag("exercise-ficha-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 300f * density) }
        rule.onNodeWithText("Tu historial").assertExists()
        save("exercise-ficha", "10-ficha-desplazada-historial", "Same real ficha scrolled300dp: real blur/darken/header and example history; no album/media invented")
    }
}
