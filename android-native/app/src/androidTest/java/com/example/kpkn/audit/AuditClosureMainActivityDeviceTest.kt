package com.example.kpkn.audit

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.MainActivity
import com.example.kpkn.R
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.wikilab.ConceptCategory
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File

/** C8 validation through the production Activity, NavHost, measured dock and platform insets. */
@RunWith(AndroidJUnit4::class)
class AuditClosureMainActivityDeviceTest {
    private var repository: ProgramRepository? = null
    private var priorOnboardingCompleted = false
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun preparePostOnboardingRoute() {
        val repo = ProgramRepository.getInstance()
        repository = repo
        runBlocking {
            withTimeout(60_000) { repo.isReady.first { it } }
            priorOnboardingCompleted = repo.settings.value.onboardingCompleted
            if (!priorOnboardingCompleted) setOnboarding(repo, true)
        }
    }

    @After
    fun restoreOnboardingFlag() {
        val repo = repository
        if (repo != null && !priorOnboardingCompleted) runBlocking { setOnboarding(repo, false) }
    }

    @Test
    fun c8_mainActivityKeepsFinalConceptActionsUsableWithLargeFontImeAndVisibleDock() {
        assertTrue("solo se admite KPKN Dev", context.packageName == "com.example.kpkn.dbg")
        dismissNotificationPermissionPrompt()
        composeRule.activity.runOnUiThread {
            composeRule.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val fontScale = composeRule.density.fontScale
        assertTrue("el AVD corre C8 con fuente grande: $fontScale", fontScale >= 1.4f)
        assertTrue("la precondición de ruta pos-onboarding está activa", ProgramRepository.getInstance().settings.value.onboardingCompleted)
        saveScreenshot("main-start.png")

        val deepLink = Intent(Intent.ACTION_VIEW).setData(Uri.parse("kpkn://concept/creatina"))
        composeRule.waitUntil(15_000L) { nodeExists("main_measured_bottom_dock") }
        composeRule.activity.runOnUiThread {
            // Deliver the platform callback to the same ActivityScenario root. Launching a second
            // MainActivity makes the test rule lose its root. Restore the launch identity after
            // delivery because ActivityScenario filters lifecycle callbacks by Activity.intent.
            val activity = composeRule.activity
            val launchIntent = activity.intent
            MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply {
                isAccessible = true
            }.invoke(activity, deepLink)
            activity.setIntent(launchIntent)
        }
        waitForTag("concept_reading_creatina")
        assertNavigationDockVisible()
        composeRule.onNodeWithTag("concept_reading_creatina").performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertMainActionAboveDock("concept_open_library")
        saveScreenshot("reading-final-action.png")
        composeRule.onNodeWithTag("concept_open_library").performClick()

        waitForTag("concept_library")
        composeRule.onNodeWithTag("concept_library").performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertMainActionAboveDock("concept_library_last_action")
        saveScreenshot("library-final-action.png")
        composeRule.onNodeWithTag("concept_library_last_action").performClick()
        composeRule.onNodeWithTag("concept_top_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("concept_search_toggle").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("concept_search_field").assertIsDisplayed().performClick()
        waitForIme()
        composeRule.onNodeWithTag("concept_library").performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertMainActionAboveDock("concept_library_last_action")
        saveScreenshot("library-ime-final-action.png")

        composeRule.activity.runOnUiThread {
            composeRule.activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(composeRule.activity.window.decorView.windowToken, 0)
        }
        composeRule.waitUntil(10_000L) { !systemImeVisible() }
        assertTrue("el IME se cerró", !systemImeVisible())
        composeRule.onNodeWithContentDescription("Cerrar búsqueda").performClick()
        composeRule.onNodeWithTag("concept_library").performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertMainActionAboveDock("concept_library_last_action")
        saveScreenshot("library-closed-final-action.png")

        val categoryTag = "concept_topic_${ConceptCategory.SUPPLEMENTS.name}"
        composeRule.onNodeWithTag("concept_library").performScrollToNode(hasTestTag(categoryTag))
        val dock = composeRule.onNodeWithTag("main_measured_bottom_dock").fetchSemanticsNode().boundsInRoot
        val categoryNode = composeRule.onNodeWithTag(categoryTag).fetchSemanticsNode()
        val category = categoryNode.boundsInRoot
        val visibleHeight = (dock.top - category.top).coerceAtMost(category.height)
        assertTrue("$categoryTag expone una zona táctil sobre el dock: action=$category dock=$dock", visibleHeight >= 48f)
        saveScreenshot("library-topic-tile-visible.png")
        composeRule.onNodeWithTag(categoryTag).performTouchInput {
            click(Offset(categoryNode.size.width / 2f, minOf(72f, visibleHeight / 2f)))
        }
        val topicTag = "concept_topic_page_${ConceptCategory.SUPPLEMENTS.name}"
        composeRule.waitUntil(10_000L) { nodeExists(topicTag) }
        composeRule.onNodeWithTag(topicTag).performScrollToNode(hasTestTag("concept_bottom_clearance"))
        assertMainActionAboveDock("concept_next_topic")
        saveScreenshot("topic-final-action.png")
        composeRule.onNodeWithTag("concept_next_topic").performClick()

        println(
            "AUDIT_C8_MAIN_ACTIVITY fontScale=$fontScale navInsetPx=${composeRule.activity.window.decorView.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: -1} " +
                "dockBounds=${composeRule.onNodeWithTag("main_measured_bottom_dock").fetchSemanticsNode().boundsInRoot}",
        )
    }

    private fun assertNavigationDockVisible() {
        composeRule.onNodeWithTag("main_measured_bottom_dock").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.nav_home)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.nav_training)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.nav_body)).assertIsDisplayed()
    }

    private fun assertMainActionAboveDock(tag: String) {
        composeRule.waitForIdle()
        val dock = composeRule.onNodeWithTag("main_measured_bottom_dock").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag queda completamente sobre el dock: action=$action dock=$dock", action.bottom <= dock.top + 1f)
        println("AUDIT_C8_ACTION tag=$tag action=$action dock=$dock")
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(20_000L) { nodeExists(tag) }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun nodeExists(tag: String): Boolean = runCatching {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        true
    }.getOrDefault(false)

    private fun waitForIme() {
        composeRule.waitUntil(10_000L) { systemImeVisible() }
        assertTrue("el teclado del sistema está abierto", systemImeVisible())
    }

    private fun systemImeVisible(): Boolean {
        val decorInsets = composeRule.activity.window.decorView.rootWindowInsets
            ?.isVisible(android.view.WindowInsets.Type.ime()) == true
        val imeWindow = InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        return decorInsets || imeWindow
    }

    /**
     * Completing onboarding can surface Android's notification permission sheet before the
     * post-onboarding route is composed. Dismiss it through the visible system UI so the test
     * keeps the app's real denied-permission state and can continue against MainActivity.
     */
    private fun dismissNotificationPermissionPrompt() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.setServiceInfo(
            AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            },
        )
        val deadline = SystemClock.uptimeMillis() + 60_000L
        var dismissedCount = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val roots = automation.windows.mapNotNull { window -> runCatching { window.root }.getOrNull() } +
                listOfNotNull(runCatching { automation.rootInActiveWindow }.getOrNull())
            val denyButton = roots.firstNotNullOfOrNull(::findNotificationDenyButton)
            if (denyButton != null) {
                val clicked = runCatching { denyButton.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    .getOrDefault(false)
                check(clicked) { "Android mostró un permiso, pero no permitió pulsar No permitir" }
                dismissedCount++
                SystemClock.sleep(500L)
                continue
            }
            if (nodeExists("main_measured_bottom_dock")) {
                if (dismissedCount > 0) {
                    val appPermissionSheetAppeared = runCatching {
                        composeRule.waitUntil(8_000L) { nodeExists("permissions-dialog") }
                    }.isSuccess
                    if (appPermissionSheetAppeared) {
                        composeRule.onNodeWithTag("permissions-skip").assertIsDisplayed().performClick()
                        composeRule.waitUntil(5_000L) { !nodeExists("permissions-dialog") }
                        println("AUDIT_C8_PERMISSION_PROMPT appSheet=dismissed-skip")
                    }
                }
                check(!nodeExists("permissions-dialog")) { "El diálogo propio de permisos sigue cubriendo MainActivity" }
                val homeTourAppeared = runCatching {
                    composeRule.waitUntil(8_000L) { nodeExists("tour_skip") }
                }.isSuccess
                if (homeTourAppeared) {
                    composeRule.onNodeWithTag("tour_skip").assertIsDisplayed().performClick()
                    composeRule.waitUntil(5_000L) { !nodeExists("tour_skip") }
                    println("AUDIT_C8_HOME_TOUR dismissed=skip")
                }
                println("AUDIT_C8_PERMISSION_PROMPT dismissed=$dismissedCount; main route ready")
                return
            }
            SystemClock.sleep(200L)
        }
        val windows = automation.windows.joinToString { window ->
            val root = runCatching { window.root }.getOrNull()
            "type=${window.type},title=${window.title},package=${root?.packageName}"
        }
        check(nodeExists("main_measured_bottom_dock")) {
            val activeRoot = runCatching { automation.rootInActiveWindow }.getOrNull()
            "No apareció el dock de MainActivity tras esperar permisos; dismissals=$dismissedCount windows=[$windows] activePackage=${activeRoot?.packageName} activeText=${activeRoot?.text}"
        }
    }

    private fun findNotificationDenyButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val id = node.viewIdResourceName.orEmpty()
        val label = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val denyId = id.endsWith("permission_deny_button") ||
            id.endsWith("permission_deny_and_dont_ask_again_button")
        val denyLabel = sequenceOf(label, description).any {
            it.equals("Don't allow", ignoreCase = true) ||
                it.equals("Don’t allow", ignoreCase = true) ||
                it.equals("No permitir", ignoreCase = true) ||
                it.equals("No permitir ahora", ignoreCase = true)
        }
        if (node.isClickable && (denyId || denyLabel)) return AccessibilityNodeInfo.obtain(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findNotificationDenyButton(child)?.let { return it }
        }
        return null
    }

    private fun saveScreenshot(name: String) {
        composeRule.waitForIdle()
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val directory = checkNotNull(context.getExternalFilesDir("audit-c8-main"))
            check(directory.isDirectory || directory.mkdirs())
            File(directory, name).outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val encoded = ByteArrayOutputStream().also { buffer ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer))
            }.toByteArray().let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            encoded.chunked(1_500).forEachIndexed { index, chunk ->
                println("AUDIT_C8_MAIN_${name.removeSuffix(".png").uppercase().replace('-', '_')}_$index=$chunk")
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun setOnboarding(repo: ProgramRepository, completed: Boolean) {
        repo.updateSettings { it.copy(onboardingCompleted = completed) }
        withTimeout(15_000) {
            while (repo.databaseForTests().settingsDao().get()?.toSettings()?.onboardingCompleted != completed) {
                delay(50)
            }
        }
    }
}
