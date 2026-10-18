package com.example.kpkn.screens.workout

import android.content.pm.ApplicationInfo
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.SessionIntensityResult
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.WorkoutMediaPreviewDialog
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
class Revision15PhotosDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val captures = JSONArray()
    private val callbackEvents = mutableListOf<String>()
    private lateinit var output: File
    private lateinit var presentation: FinishPresentationContext
    private var draft by mutableStateOf("Sentadilla sólida; revisar control del rumano.")
    private var open by mutableStateOf(true)
    private var albumPhoto by mutableStateOf<WorkoutMedia?>(null)
    private val tasks = mutableStateListOf(SessionChecklistItem("qa15-task", "Revisar control del rumano"))
    private val notes = mutableStateListOf(SessionSavedNote("qa15-note", "Mantener la pausa abajo", "2026-10-08T12:00:00Z"))
    private val squat = CompletedExercise("qa15-squat", "Sentadilla", canonicalExerciseId = "squat",
        sets = listOf(CompletedSet("qa15-set-1", weight = 85.0, reps = 6, rir = 2)))
    private val deadlift = CompletedExercise("qa15-deadlift", "Peso muerto rumano", canonicalExerciseId = "romanian_deadlift",
        sets = listOf(CompletedSet("qa15-set-2", weight = 72.5, reps = 8, rir = 2)))
    private val completed get() = listOf(squat, deadlift)
    private val milestones = listOf(SessionMilestone("qa15-pr", "qa15-squat", "Sentadilla", "pr_e1rm",
        "Nueva marca", 99.0, "Antes 80 kg × 6; hoy 85 kg × 6", "2026-10-16T12:00:00Z"))

    @Test fun captureProductionFinishWithPhotosAndAllStoryModes() {
        // The QA build is installed beside the signed release with its own applicationId.
        assumeTrue("Only the isolated debuggable KPKN target is permitted", context.packageName == "com.example.kpkn.dbg" &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assumeTrue("Only revision 15 / code 48 is accepted", installed.versionCode == 48 && installed.versionName == "KPKN Pre Release 15")
        output = File(requireNotNull(context.getExternalFilesDir(null)), "qa15-photos").apply { check(isDirectory || mkdirs()) }
        val generated = mutableListOf<File>()
        try {
            val inputs = (1..2).map { number ->
                File.createTempFile("qa15-photos-input-$number-", ".png", context.cacheDir).also { file ->
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
                        canvas.drawText("FIXTURE QA15 · FOTO $number", 24f, 58f, paint)
                        paint.textSize = 19f
                        canvas.drawText("Archivo sintético local; no es cámara", 24f, 676f, paint)
                        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
            }
            val history = listOf(WorkoutLog("qa15-history", "qa15-program", "qa15-session", "Pierna A",
                "2026-09-15T12:00:00Z", 40, listOf(squat.copy(sets = listOf(CompletedSet("qa15-old-set", weight = 80.0, reps = 6, rir = 2))), deadlift)))
            presentation = FinishPresentationContext(program = Program("qa15-program", "Base sólida", coverImage = inputs.first().absolutePath),
                weekNumber = 3, history = history, photos = listOf(
                    StoryPhoto(inputs[0].absolutePath, squat.exerciseName, "85 kg × 6 · Marca", setIndex = 0,
                        createdAtMs = java.time.Instant.parse("2026-10-17T12:00:00Z").toEpochMilli(), isPr = true),
                    StoryPhoto(inputs[1].absolutePath, deadlift.exerciseName, "72,5 kg × 8", setIndex = 1,
                        createdAtMs = java.time.Instant.parse("2026-10-17T12:10:00Z").toEpochMilli())), records = 1, today = LocalDate.of(2026, 10, 17))
            writeMetadata()
            show()
            listOf("Hoy", "Levantamientos", "Hitos", "Fotos", "Notas").forEachIndexed { index, title ->
                selectTab(title)
                capture("0${index + 1}-finish-${listOf("today", "lifts", "milestones", "photos", "notes")[index]}")
            }
            rule.onNodeWithTag("finish-notes").performTextReplacement("Borrador físico QA15: conservar al navegar")
            rule.onNodeWithTag("finish-task-qa15-task").performClick()
            rule.runOnIdle {
                assertTrue(tasks.single().done)
                assertTrue(callbackEvents.contains("toggle:qa15-task"))
                assertTrue(callbackEvents.any { it.startsWith("draft:") })
                assertFalse(callbackEvents.any { it.startsWith("confirm:") })
            }
            capture("06-notes-edited")
            selectTab("Fotos")
            // A real touch on the production AsyncImage opens the production summary photo Dialog.
            rule.onNodeWithContentDescription("Sentadilla", useUnmergedTree = true).performTouchInput { click() }
            rule.onNodeWithContentDescription("Cerrar foto").assertIsDisplayed()
            rule.onNodeWithText("Serie 1 · 85 kg × 6 · Marca").assertIsDisplayed()
            rule.onNodeWithTag("photo-viewer-share").assertIsEnabled()
            capture("07-finish-photo-viewer")
            rule.onNodeWithTag("photo-viewer-zoom").performClick()
            rule.onNodeWithTag("workout-photo-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ampliada"))
            capture("07b-finish-photo-expanded")
            rule.onNodeWithContentDescription("Foto siguiente").performClick()
            rule.onNodeWithTag("workout-photo-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ajustada"))
            rule.onNodeWithText("Serie 2 · 72,5 kg × 8").assertIsDisplayed()
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
            rule.onNodeWithTag("finish-notes").assertTextEquals("Borrador físico QA15: conservar al navegar")
            capture("12-notes-after-share")
            rule.runOnIdle { assertFalse(callbackEvents.any { it.startsWith("confirm:") }) }
            selectTab("Hitos")
            rule.onNodeWithTag("finish-evolution-qa15-squat").performScrollTo().performClick()
            waitPreview("Ejercicio")
            capture("13-contextual-exercise-story")
            rule.onNodeWithContentDescription("Volver al resumen").performClick()
            rule.onNodeWithTag("finish-page-2", useUnmergedTree = true).assertIsDisplayed()
            selectTab("Fotos")
            rule.onNodeWithTag("finish-photo-story").performClick()
            waitPreview("Fotos")
            capture("14-contextual-photo-story")
            rule.onNodeWithContentDescription("Volver al resumen").performClick()
            rule.onNodeWithTag("finish-page-3", useUnmergedTree = true).assertIsDisplayed()
            // The actual album entry delegates to the very same photo viewer; retain its delete callback.
            rule.runOnIdle { albumPhoto = WorkoutMedia("qa15-album-photo", WorkoutMediaKind.PHOTO,
                inputs[0].absolutePath, createdAtMs = java.time.Instant.parse("2026-10-17T12:00:00Z").toEpochMilli(),
                exerciseId = squat.exerciseId, exerciseName = squat.exerciseName, setIndex = 0, weightKg = 85.0, reps = 6, isPr = true) }
            rule.onNodeWithTag("workout-photo-viewer").assertIsDisplayed()
            rule.onNodeWithContentDescription("Borrar foto").assertIsDisplayed()
            rule.onNodeWithTag("photo-viewer-share").assertIsEnabled()
            rule.onNodeWithTag("photo-viewer-zoom").performClick()
            rule.onNodeWithTag("workout-photo-image").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ampliada"))
            capture("15-album-photo-expanded")
            rule.onNodeWithContentDescription("Borrar foto").performClick()
            rule.runOnIdle { assertNull(albumPhoto); assertTrue(callbackEvents.contains("delete-photo")) }
            // Inspect the real chooser intent and bytes without opening or sending to another app.
            val chooser = runBlocking { WorkoutShareService.createPhotoShareIntent(context, inputs[0].absolutePath) }
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val send = checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION") val photoUri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            try {
                assertEquals("content", photoUri.scheme)
                assertEquals(photoUri, send.clipData?.getItemAt(0)?.uri)
                context.contentResolver.openInputStream(photoUri)!!.use { assertArrayEquals(inputs[0].readBytes(), it.readBytes()) }
            } finally { context.contentResolver.delete(photoUri, null, null) }
            selectTab("Notas")
            rule.onNodeWithTag("finish-notes").assertTextEquals("Borrador físico QA15: conservar al navegar")
        } finally {
            generated.forEach { check(it.parentFile?.canonicalFile == context.cacheDir.canonicalFile); it.delete() }
            if (::output.isInitialized) writeMetadata()
        }
    }

    /** Separate physical chooser check: no destination/contact selection and no chooser screenshot/tree. */
    @Test fun individualPhotoShareOpensSystemChooserAndBackKeepsPhotoAndDraft() {
        assumeTrue("Only the isolated debuggable KPKN target is permitted", context.packageName == "com.example.kpkn.dbg" &&
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assumeTrue("Only revision 15 / code 48 is accepted", installed.versionCode == 48 && installed.versionName == "KPKN Pre Release 15")
        val folder = File(requireNotNull(context.getExternalFilesDir(null)), "qa15-photo-chooser").apply { check(isDirectory || mkdirs()) }
        // Resolve only the system chooser component, without querying destinations or recent contacts.
        val resolver = checkNotNull(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png" }, "Compartir foto")
            .resolveActivity(context.packageManager)) { "No Android chooser component is available" }
        check(resolver.packageName != context.packageName) { "The chooser must resolve outside KPKN" }
        val report = JSONObject().put("fixture", true).put("versionCode", installed.versionCode)
            .put("resolverComponent", resolver.flattenToShortString()).put("recipientSelected", false)
            .put("sent", false).put("chooserScreenshotOrTreeSaved", false).put("chooserClosedInFinally", false)
        val photo = File.createTempFile("qa15-chooser-input-", ".png", context.cacheDir)
        var observedResolver = false
        var returnedToPhoto = false
        var retainedDraft = false
        try {
            val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(34, 67, 81)) }
            try { photo.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            presentation = FinishPresentationContext(
                program = Program("qa15-program", "Base sólida", coverImage = "gradient://forest"),
                photos = listOf(StoryPhoto(photo.absolutePath, squat.exerciseName, "85 kg × 6 · Marca", setIndex = 0,
                    createdAtMs = java.time.Instant.parse("2026-10-17T12:00:00Z").toEpochMilli(), isPr = true)),
                today = LocalDate.of(2026, 10, 17))
            show()
            selectTab("Notas")
            val note = "Borrador sintético QA15: volver del selector Android"
            rule.onNodeWithTag("finish-notes").performTextReplacement(note)
            selectTab("Fotos")
            rule.onNodeWithContentDescription("Sentadilla", useUnmergedTree = true).performTouchInput { click() }
            rule.onNodeWithTag("workout-photo-viewer").assertIsDisplayed()
            rule.onNodeWithText("Serie 1 · 85 kg × 6 · Marca").assertIsDisplayed()
            rule.onNodeWithTag("photo-viewer-share").performClick()
            // While another Android window is active, use UiAutomation only; never fetch Compose/UI trees.
            assertTrue("The Android resolver must become the active package", waitForActivePackage(resolver.packageName, 15_000))
            observedResolver = true
            report.put("observedResolverPackage", activeWindowPackage())
            assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
            assertTrue("Back must return to KPKN", waitForActivePackage(context.packageName, 15_000))
            rule.waitUntil(15_000) {
                rule.onAllNodesWithTag("workout-photo-viewer", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
            rule.onNodeWithTag("workout-photo-viewer").assertIsDisplayed()
            rule.onNodeWithText("Serie 1 · 85 kg × 6 · Marca").assertIsDisplayed()
            returnedToPhoto = true
            rule.onNodeWithContentDescription("Cerrar foto").performClick()
            rule.onNodeWithTag("finish-page-3", useUnmergedTree = true).assertIsDisplayed()
            selectTab("Notas")
            rule.onNodeWithTag("finish-notes").assertTextEquals(note)
            rule.runOnIdle { assertTrue(open); assertFalse(callbackEvents.any { it.startsWith("confirm:") }) }
            retainedDraft = true
        } finally {
            // Cancel only the known resolver window, including assertion failures after it opened.
            if (activeWindowPackage() == resolver.packageName) {
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                report.put("chooserClosedInFinally", waitForActivePackage(context.packageName, 10_000))
            }
            report.put("resolverObserved", observedResolver).put("returnedToSamePhoto", returnedToPhoto)
                .put("draftRetained", retainedDraft)
            File(folder, "chooser-return.json").writeText(report.toString(2))
            check(photo.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
            photo.delete()
        }
    }

    /** Package only: do not inspect children, labels, recent contacts, or accessibility dumps. */
    private fun activeWindowPackage(): String? {
        val root = runCatching { instrumentation.uiAutomation.rootInActiveWindow }.getOrNull() ?: return null
        return try { root.packageName?.toString() } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun waitForActivePackage(expected: String, timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.uptimeMillis() + timeoutMs
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (activeWindowPackage() == expected) return true
            android.os.SystemClock.sleep(100)
        }
        return false
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
                                session = Session("qa15-session", "Pierna A"), completedSets = mapOf("qa15-squat_0" to squat.sets.first(), "qa15-deadlift_0" to deadlift.sets.first()),
                                completedExercises = completed, durationMinutes = 40,
                                sessionIntensityResult = SessionIntensityResult(8.0, 8.0, "Alta", false, false, 1.0, 1.0),
                                postSessionPreview = PostSessionPreview(72, 68, 64, emptyMap(), 8, 12, 6,
                                    finishOperationId = "qa15-operation", inputHash = "qa15-input", completionInstantIso = "2026-10-16T12:00:00Z",
                                    preSessionMuscular = 80, preSessionNeural = 80, preSessionSpinal = 70),
                                hazeState = haze, initialSessionNotes = draft, presentation = presentation,
                                sessionMilestones = milestones, sessionSavedNotes = notes, sessionChecklist = tasks,
                                onConfirm = { text, _, _, _ -> callbackEvents += "confirm:$text" },
                                onDismiss = { callbackEvents += "dismiss"; open = false }, onShare = { callbackEvents += "share" },
                                onSummaryReady = { callbackEvents += "summary-ready" },
                                onNotesDraft = { draft = it; callbackEvents += "draft:$it" },
                                onSaveSessionNote = { text -> notes += SessionSavedNote("qa15-new-note", text, "2026-10-16T12:00:00Z"); callbackEvents += "save-note:$text" },
                                onAddChecklistItem = { text -> tasks += SessionChecklistItem("qa15-added-${tasks.size}", text); callbackEvents += "add:$text" },
                                onToggleChecklistItem = { id -> val index = tasks.indexOfFirst { it.id == id }; if (index >= 0) tasks[index] = tasks[index].copy(done = !tasks[index].done); callbackEvents += "toggle:$id" },
                                onRemoveChecklistItem = { id -> tasks.removeAll { it.id == id }; callbackEvents += "remove:$id" })
                            KpknOverlayHostContent(host)
                            albumPhoto?.let { photo -> WorkoutMediaPreviewDialog(photo,
                                onDismiss = { albumPhoto = null }, onDelete = { callbackEvents += "delete-photo"; albumPhoto = null }) }
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

    private fun waitPreview(label: String) {
        rule.waitUntil(30_000) {
            rule.onAllNodesWithContentDescription("Vista previa de historia $label", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
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
