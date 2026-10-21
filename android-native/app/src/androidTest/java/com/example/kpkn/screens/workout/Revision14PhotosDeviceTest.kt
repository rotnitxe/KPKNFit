package com.example.kpkn.screens.workout

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.*
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Source-only QA fixture: production composables, native device density/font scale, isolated in-memory data.
 * Generated raster inputs are actual local PNG files, explicitly synthetic (not camera evidence).
 * Does not save workout records, touch preferences/DB, or launch a sharing application.
 */
@RunWith(AndroidJUnit4::class)
class Revision14PhotosDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val captures = JSONArray()
    private val callbackEvents = mutableListOf<String>()
    private lateinit var output: File
    private lateinit var presentation: FinishPresentationContext
    private var draft by mutableStateOf("Sentadilla sólida; revisar control del rumano.")
    private var open by mutableStateOf(true)
    private val tasks = mutableStateListOf(SessionChecklistItem("qa14-task", "Revisar control del rumano"))
    private val notes = mutableStateListOf(SessionSavedNote("qa14-note", "Mantener la pausa abajo", "2026-10-08T12:00:00Z"))
    private val squat = CompletedExercise("qa14-squat", "Sentadilla", canonicalExerciseId = "squat",
        sets = listOf(CompletedSet("qa14-set-1", weight = 85.0, reps = 6, rir = 2)))
    private val deadlift = CompletedExercise("qa14-deadlift", "Peso muerto rumano", canonicalExerciseId = "romanian_deadlift",
        sets = listOf(CompletedSet("qa14-set-2", weight = 72.5, reps = 8, rir = 2)))
    private val completed get() = listOf(squat, deadlift)
    private val milestones = listOf(SessionMilestone("qa14-pr", "qa14-squat", "Sentadilla", "pr_e1rm",
        "Nueva marca", 99.0, "Antes 80 kg × 6; hoy 85 kg × 6", "2026-10-16T12:00:00Z"))

    @Test fun captureProductionFinishWithPhotosAndAllStoryModes() {
        // The QA build is installed beside the signed release with its own applicationId.
        assumeTrue("Only the isolated debuggable KPKN target is permitted", context.packageName == "com.example.kpkn.dbg" &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        output = File(requireNotNull(context.getExternalFilesDir(null)), "qa14-photos").apply { check(isDirectory || mkdirs()) }
        val generated = mutableListOf<File>()
        try {
            val inputs = (1..2).map { number ->
                File.createTempFile("qa14-photos-input-$number-", ".png", context.cacheDir).also { file ->
                    generated += file
                    val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
                    try {
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(if (number == 1) Color.rgb(34, 67, 81) else Color.rgb(90, 52, 42))
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        repeat(8) { index ->
                            paint.color = if (index % 2 == 0) Color.rgb(170, 188, 182) else Color.rgb(66, 94, 97)
                            canvas.drawRect((index * 70 - 80).toFloat(), 120f, (index * 70 - 40).toFloat(), 610f, paint)
                        }
                        paint.color = Color.rgb(232, 224, 207)
                        paint.textSize = 24f
                        canvas.drawText("FIXTURE QA14 · FOTO $number", 24f, 58f, paint)
                        paint.textSize = 19f
                        canvas.drawText("Archivo sintético local; no es cámara", 24f, 676f, paint)
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
            }
            val history = listOf(WorkoutLog("qa14-history", "qa14-program", "qa14-session", "Pierna A",
                "2026-09-15T12:00:00Z", 40, listOf(squat.copy(sets = listOf(CompletedSet("qa14-old-set", weight = 80.0, reps = 6, rir = 2))), deadlift)))
            presentation = FinishPresentationContext(program = Program("qa14-program", "Base sólida", coverImage = inputs.first().absolutePath),
                weekNumber = 3, history = history, photos = listOf(
                    StoryPhoto(inputs[0].absolutePath, squat.exerciseName, "85 kg × 6 · Marca"),
                    StoryPhoto(inputs[1].absolutePath, deadlift.exerciseName, "72,5 kg × 8")), records = 1, today = LocalDate.of(2026, 10, 16))
            writeMetadata()
            show()
            listOf("Hoy", "Levantamientos", "Hitos", "Fotos", "Notas").forEachIndexed { index, title ->
                selectTab(title)
                capture("0${index + 1}-finish-${listOf("today", "lifts", "milestones", "photos", "notes")[index]}")
            }
            rule.onNodeWithTag("finish-notes").performTextReplacement("Borrador físico QA14: conservar al navegar")
            rule.onNodeWithTag("finish-task-qa14-task").performClick()
            rule.runOnIdle {
                assertTrue(tasks.single().done)
                assertTrue(callbackEvents.contains("toggle:qa14-task"))
                assertTrue(callbackEvents.any { it.startsWith("draft:") })
                assertFalse(callbackEvents.any { it.startsWith("confirm:") })
            }
            capture("06-notes-edited")
            selectTab("Fotos")
            // A real touch on the production AsyncImage opens the production summary photo Dialog.
            rule.onNodeWithContentDescription("Sentadilla", useUnmergedTree = true).performTouchInput { click() }
            rule.onNodeWithContentDescription("Cerrar foto").assertIsDisplayed()
            capture("07-finish-photo-viewer")
            rule.onNodeWithText("Siguiente").performClick()
            capture("08-finish-photo-viewer-next")
            rule.onNodeWithContentDescription("Cerrar foto").performClick()
            rule.onNodeWithTag("finish-page-3", useUnmergedTree = true).assertIsDisplayed()
            rule.onNodeWithTag("finish-share").performClick()
            for ((index, template) in WorkoutStoryTemplate.entries.withIndex()) {
                rule.onNodeWithText(template.label).performClick()
                rule.waitUntil(30_000) {
                    rule.onAllNodesWithTag("story-preview", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
                capture("${index + 9}-selector-${template.name.lowercase()}")
                val storyData = WorkoutStoryData("Pierna A", presentation, 40, 1090.0, 2, 2.0, "Alta",
                    TowerRingValues(72, 68, 64), buildFinishLiftProgress(completed, presentation.history))
                val bitmap = runBlocking { renderWorkoutStory(context, storyData, WorkoutStoryOptions(template = template)) }
                try {
                    assertEquals(1080, bitmap.width); assertEquals(1920, bitmap.height)
                    File(output, "export-${template.name.lowercase()}.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { bitmap.recycle() }
            }
            rule.onNodeWithContentDescription("Volver al resumen").performClick()
            selectTab("Notas")
            rule.onNodeWithTag("finish-notes").assertTextEquals("Borrador físico QA14: conservar al navegar")
            capture("12-notes-after-share")
            rule.runOnIdle { assertFalse(callbackEvents.any { it.startsWith("confirm:") }) }
        } finally {
            generated.forEach { check(it.parentFile?.canonicalFile == context.cacheDir.canonicalFile); it.delete() }
            if (::output.isInitialized) writeMetadata()
        }
    }

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                KPKNTheme {
                    val host = remember { KpknOverlayHostController(tourSeen = object : TourSeenStore {
                        override fun seenIds() = emptySet<String>()
                        override fun markSeen(tourId: String) = Unit
                    }) }
                    val haze = remember { HazeState() }
                    CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                        Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(KpknSurface.canvas))
                            if (open) FinishWorkoutSheet(
                                session = Session("qa14-session", "Pierna A"), completedSets = mapOf("qa14-squat_0" to squat.sets.first(), "qa14-deadlift_0" to deadlift.sets.first()),
                                completedExercises = completed, durationMinutes = 40,
                                sessionIntensityResult = SessionIntensityResult(8.0, 8.0, "Alta", false, false, 1.0, 1.0),
                                postSessionPreview = PostSessionPreview(72, 68, 64, emptyMap(), 8, 12, 6,
                                    finishOperationId = "qa14-operation", inputHash = "qa14-input", completionInstantIso = "2026-10-16T12:00:00Z",
                                    preSessionMuscular = 80, preSessionNeural = 80, preSessionSpinal = 70),
                                hazeState = haze, initialSessionNotes = draft, presentation = presentation,
                                sessionMilestones = milestones, sessionSavedNotes = notes, sessionChecklist = tasks,
                                onConfirm = { text, _, _, _ -> callbackEvents += "confirm:$text" },
                                onDismiss = { callbackEvents += "dismiss"; open = false }, onShare = { callbackEvents += "share" },
                                onSummaryReady = { callbackEvents += "summary-ready" },
                                onNotesDraft = { draft = it; callbackEvents += "draft:$it" },
                                onSaveSessionNote = { text -> notes += SessionSavedNote("qa14-new-note", text, "2026-10-16T12:00:00Z"); callbackEvents += "save-note:$text" },
                                onAddChecklistItem = { text -> tasks += SessionChecklistItem("qa14-added-${tasks.size}", text); callbackEvents += "add:$text" },
                                onToggleChecklistItem = { id -> val index = tasks.indexOfFirst { it.id == id }; if (index >= 0) tasks[index] = tasks[index].copy(done = !tasks[index].done); callbackEvents += "toggle:$id" },
                                onRemoveChecklistItem = { id -> tasks.removeAll { it.id == id }; callbackEvents += "remove:$id" })
                            KpknOverlayHostContent(host)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun selectTab(title: String) {
        rule.onNodeWithText(title).performScrollTo().performClick()
        rule.waitForIdle()
        val index = listOf("Hoy", "Levantamientos", "Hitos", "Fotos", "Notas").indexOf(title)
        rule.onNodeWithTag("finish-page-$index", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        // Bounded settle for asynchronous image decode and the system-window screenshot.
        Thread.sleep(350)
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "UI screenshot unavailable" }
        try {
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            captures.put(JSONObject().put("file", "$name.png").put("width", bitmap.width).put("height", bitmap.height)
                .put("capture", "UiAutomation.takeScreenshot").put("fixture", true))
            writeMetadata()
        } finally { bitmap.recycle() }
    }

    private fun writeMetadata() {
        val metadata = JSONObject().put("fixture", true)
            .put("description", "Production FinishWorkoutSheet and story renderer; in-memory session, dated notes/tasks/milestones; two actual local synthetic PNG inputs, not camera evidence")
            .put("package", context.packageName).put("versionCode", context.packageManager.getPackageInfo(context.packageName, 0).versionCode)
            .put("reducedMotion", true).put("forcedDensityOrFontScale", false)
            .put("externalSharingInvoked", false).put("databaseOrPreferencesModified", false)
            .put("callbackEvents", JSONArray(callbackEvents.toList())).put("captures", captures)
        File(output, "metadata.json").writeText(metadata.toString(2))
    }
}
