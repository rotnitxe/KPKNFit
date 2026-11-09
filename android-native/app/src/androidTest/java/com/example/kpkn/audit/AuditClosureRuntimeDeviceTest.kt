package com.example.kpkn.audit

import android.content.Context
import android.graphics.Bitmap
import android.view.WindowInsets as PlatformWindowInsets
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.wikilab.ConceptCategory
import com.example.kpkn.screens.home.HomeProgramsSection
import com.example.kpkn.screens.home.ConceptBottomFadeLead
import com.example.kpkn.screens.home.ConceptosClaveScreen
import com.example.kpkn.screens.home.LocalConceptsBottomOcclusion
import com.example.kpkn.screens.home.homeProgramCardTag
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioStyle
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityForProgram
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverRolesOf
import com.example.kpkn.screens.programdetail.design.ProgramPoster
import com.example.kpkn.screens.sessioneditor.components.EdStepperField
import com.example.kpkn.ui.components.KpknBottomBar
import com.example.kpkn.ui.components.KpknBottomBarItem
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.MeasuredKpknBottomDock
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
import com.example.kpkn.ui.theme.KPKNTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream

/** Android-runtime coverage for the changed editor and the real measured navigation dock. */
@RunWith(AndroidJUnit4::class)
class AuditClosureRuntimeDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = rule.activity.applicationContext

    @Test
    fun c4_timePickerKeepsDialAndKeyboardAvailableAtSystemFontScale() {
        WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
        assertTrue("el AVD está en la escala grande preparada para C4: ${rule.density.fontScale}", rule.density.fontScale in 1.4f..1.6f)
        rule.setContent {
            KPKNTheme {
                EditorOverlayHost {
                    Box(Modifier.fillMaxSize().testTag("c4_runtime_host")) {
                        KpknNativeTimePickerDialog(
                            title = "Descanso",
                            initialHour = 10,
                            initialMinute = 5,
                            hint = "Minutos : segundos",
                            onConfirm = { _, _ -> },
                            onDismiss = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("native-time-picker-dial", useUnmergedTree = true).assertIsDisplayed()
        saveRoot("audit-c4", "time-picker-dial.png")

        rule.onNodeWithContentDescription("Usar teclado").performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        assertEquals("el selector conserva ambos campos", 2, fields.fetchSemanticsNodes().size)
        fields[0].performClick()
        waitForSystemIme()
        fields[0].assertIsFocused()
        fields[0].assertIsDisplayed()
        saveRoot("audit-c4", "time-picker-keyboard.png")
    }

    @Test
    fun c4_seriesEditorKeepsKeyboardFocusAndStepperControlsAtLargeSystemFontScale() {
        WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
        assertTrue("el AVD conserva la escala grande preparada para C4: ${rule.density.fontScale}", rule.density.fontScale in 1.4f..1.6f)
        var committed = ""
        rule.setContent {
            KPKNTheme {
                Box(Modifier.size(width = 320.dp, height = 760.dp).padding(16.dp)) {
                    EdStepperField(
                        text = "181,8",
                        label = "Carga de prueba",
                        unit = "kg",
                        modifier = Modifier.testTag("c4_runtime_series_field"),
                        keyboard = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        onCommitText = { committed = it },
                        onStep = {},
                        minusDescription = "Menos carga de prueba",
                        plusDescription = "Más carga de prueba",
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("Carga de prueba").performClick()
        rule.waitForIdle()
        val editor = rule.onAllNodes(hasSetTextAction())[0]
        editor.assertIsDisplayed()
        waitForSystemIme()
        editor.assertIsFocused()
        rule.onNodeWithContentDescription("Menos carga de prueba").assertIsDisplayed()
        rule.onNodeWithContentDescription("Más carga de prueba").assertIsDisplayed()
        saveRoot("audit-c4", "series-field-keyboard.png")
        editor.performTextReplacement("181.8")
        editor.performImeAction()
        rule.waitForIdle()
        assertEquals("181.8", committed)
    }

    @Test
    fun c8_realNavigationInsetIsCountedOnceAndEachFinalConceptActionRemainsUsable() {
        WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
        var navigationInsetPx = -1
        rule.setContent {
            val density = LocalDensity.current
            val platformNavigationInsetPx = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
            SideEffect { navigationInsetPx = platformNavigationInsetPx }
            val haze = remember { HazeState() }
            var dockHeightPx by remember { mutableIntStateOf(0) }
            val dockHeight = with(density) { dockHeightPx.toDp() }.takeIf { it > 0.dp } ?: 72.dp
            CompositionLocalProvider(
                LocalConceptsBottomOcclusion provides dockHeight,
                LocalHazeState provides haze,
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize().hazeSource(state = haze).testTag("c8_real_host")) {
                        ConceptosClaveScreen(expandedConceptId = "creatina", onBack = {})
                        MeasuredKpknBottomDock(
                            hazeState = haze,
                            onMeasuredHeightChanged = { dockHeightPx = it },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("c8_measured_dock"),
                        ) {
                            KpknBottomBar(
                                items = listOf(
                                    KpknBottomBarItem("home", "Inicio", KpknLineIcons.Home),
                                    KpknBottomBarItem("training", "Entreno", KpknLineIcons.Training),
                                    KpknBottomBarItem("body", "Cuerpo", KpknLineIcons.Body),
                                ),
                                selectedRoute = "home",
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        val density = rule.density
        assertTrue("el sistema entrega un inset de navegación no negativo", navigationInsetPx >= 0)
        val navigationInset = with(density) { navigationInsetPx.toDp() }
        val dockHeight = 64.dp + 8.dp + navigationInset
        rule.onNodeWithTag("c8_measured_dock").assertHeightIsEqualTo(dockHeight)
        rule.onNodeWithTag("concept_bottom_fade").assertHeightIsEqualTo(dockHeight + ConceptBottomFadeLead)

        val readingTag = "concept_reading_creatina"
        rule.onNodeWithTag(readingTag).performScrollToNode(androidx.compose.ui.test.hasTestTag("concept_bottom_clearance"))
        assertClearance(dockHeight)
        rule.onNodeWithTag("concept_open_library").assertIsDisplayed()
        assertActionAboveDock("concept_open_library", dockHeight)
        saveRoot("audit-c8", "reading-final-action.png")
        rule.onNodeWithTag("concept_open_library").performClick()

        rule.onNodeWithTag("concept_search_toggle").assertIsDisplayed()
        rule.onNodeWithTag("concept_search_field").assertDoesNotExist()
        rule.onNodeWithTag("concept_library").performScrollToNode(androidx.compose.ui.test.hasTestTag("concept_bottom_clearance"))
        assertClearance(dockHeight)
        rule.onNodeWithTag("concept_library_last_action").assertIsDisplayed()
        assertActionAboveDock("concept_library_last_action", dockHeight)
        saveRoot("audit-c8", "library-final-action.png")
        rule.onNodeWithTag("concept_library_last_action").performClick()
        rule.onNodeWithTag("concept_top_button").assertIsDisplayed().performClick()
        rule.onNodeWithTag("concept_library").assertIsDisplayed()

        val categoryTag = "concept_topic_${ConceptCategory.SUPPLEMENTS.name}"
        rule.onNodeWithTag("concept_library").performScrollToNode(androidx.compose.ui.test.hasTestTag(categoryTag))
        liftTargetAboveDock("concept_library", dockHeight)
        assertActionAboveDock(categoryTag, dockHeight)
        rule.onNodeWithTag(categoryTag).assertIsDisplayed().performClick()
        val topicTag = "concept_topic_page_${ConceptCategory.SUPPLEMENTS.name}"
        rule.waitForIdle()
        rule.onNodeWithTag(topicTag).assertExists()
        rule.onNodeWithTag(topicTag).performScrollToNode(androidx.compose.ui.test.hasTestTag("concept_bottom_clearance"))
        assertClearance(dockHeight)
        rule.onNodeWithTag("concept_next_topic").assertIsDisplayed()
        assertActionAboveDock("concept_next_topic", dockHeight)
        saveRoot("audit-c8", "topic-final-action.png")
        rule.onNodeWithTag("concept_next_topic").performClick()
        rule.onNodeWithTag(topicTag).assertDoesNotExist()
    }

    @Test
    fun c8_libraryFinalActionRemainsAboveTheMeasuredDockWhileSystemImeIsOpen() {
        rule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
            rule.activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val navigationInsetPx = mutableIntStateOf(-1)
        val dockHeightPx = mutableIntStateOf(0)
        rule.setContent {
            val density = LocalDensity.current
            val systemNavigationInsetPx = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density)
            SideEffect { navigationInsetPx.intValue = systemNavigationInsetPx }
            val haze = remember { HazeState() }
            val dockHeight = with(density) { dockHeightPx.intValue.toDp() }.takeIf { it > 0.dp } ?: 72.dp
            CompositionLocalProvider(
                LocalConceptsBottomOcclusion provides dockHeight,
                LocalHazeState provides haze,
                LocalWizardReducedMotion provides true,
            ) {
                KPKNTheme {
                    Box(Modifier.fillMaxSize().hazeSource(state = haze).testTag("c8_ime_host")) {
                        ConceptosClaveScreen(expandedConceptId = null, onBack = {})
                        MeasuredKpknBottomDock(
                            hazeState = haze,
                            onMeasuredHeightChanged = { dockHeightPx.intValue = it },
                            modifier = Modifier.align(Alignment.BottomCenter).testTag("c8_ime_measured_dock"),
                        ) {
                            KpknBottomBar(
                                items = listOf(
                                    KpknBottomBarItem("home", "Inicio", KpknLineIcons.Home),
                                    KpknBottomBarItem("training", "Entreno", KpknLineIcons.Training),
                                    KpknBottomBarItem("body", "Cuerpo", KpknLineIcons.Body),
                                ),
                                selectedRoute = "home",
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("concept_search_toggle").performClick()
        rule.onNodeWithTag("concept_search_field").performClick()
        waitForSystemIme()
        assertTrue("el host C8 corre con fuente de sistema grande: ${rule.density.fontScale}", rule.density.fontScale >= 1.3f)
        assertTrue("se lee el inset real de navegación con IME abierto", navigationInsetPx.intValue >= 0)

        rule.onNodeWithTag("concept_library").performScrollToNode(androidx.compose.ui.test.hasTestTag("concept_bottom_clearance"))
        val dockHeight = with(rule.density) { dockHeightPx.intValue.toDp() }
        rule.onNodeWithTag("c8_ime_measured_dock").assertHeightIsEqualTo(dockHeight)
        assertClearance(dockHeight)
        rule.onNodeWithTag("concept_library_last_action").assertIsDisplayed()
        assertActionAboveDock("concept_library_last_action", dockHeight, hostTag = "c8_ime_host")
        saveRoot("audit-c8", "library-ime-final-action.png")

        rule.runOnUiThread {
            val manager = rule.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            manager?.hideSoftInputFromWindow(rule.activity.window.decorView.windowToken, 0)
        }
        rule.waitUntil(10_000L) { !systemImeVisible() }
        assertTrue("el teclado se cerró para la segunda medición", !systemImeVisible())
        rule.onNodeWithTag("concept_library").performScrollToNode(androidx.compose.ui.test.hasTestTag("concept_bottom_clearance"))
        assertClearance(dockHeight)
        assertActionAboveDock("concept_library_last_action", dockHeight, hostTag = "c8_ime_host")
        saveRoot("audit-c8", "library-closed-final-action.png")
    }

    @Test
    fun c5_personalizedPosterRendersSelectedWavesNightInsteadOfInheritedPlates() {
        val program = Program(
            id = "c5-emulator-personalized-cover",
            name = "Plan propio",
            structureTemplateId = "generated:powerlifting",
        )
        val inherited = coverIdentityFor(program.id, program.structureTemplateId)
        val waves = coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://WAVES/NOCHE"))
        val plates = coverIdentityForProgram(program.copy(coverImage = "kpkn-studio://PLATES/NOCHE"))
        assertEquals(CoverStudioStyle.PLATES, inherited.style)
        assertEquals(CoverPaletteKind.PAPEL, inherited.palette)
        assertEquals(LineCoverBackground.CREMA, inherited.lines?.background)
        assertEquals(CoverStudioStyle.WAVES, waves.style)
        assertEquals(CoverPaletteKind.NOCHE, waves.palette)
        assertEquals(LineCoverBackground.NOCHE, plates.lines?.background)

        rule.setContent {
            KPKNTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgramPoster(
                        identity = waves,
                        photoUrl = null,
                        modifier = Modifier.testTag("c5-poster-waves"),
                        animate = false,
                        width = 160.dp,
                        height = 200.dp,
                        radius = 0.dp,
                    )
                    ProgramPoster(
                        identity = plates,
                        photoUrl = null,
                        modifier = Modifier.testTag("c5-poster-plates"),
                        animate = false,
                        width = 160.dp,
                        height = 200.dp,
                        radius = 0.dp,
                    )
                }
            }
        }
        rule.waitForIdle()
        val wavesBitmap = rule.onNodeWithTag("c5-poster-waves").captureToImage().asAndroidBitmap()
        val platesBitmap = rule.onNodeWithTag("c5-poster-plates").captureToImage().asAndroidBitmap()
        try {
            val selectedBackground = coverRolesOf(waves).background.toArgb()
            val selectedBackgroundPixels = (0 until wavesBitmap.height).sumOf { y ->
                (0 until wavesBitmap.width).count { x -> wavesBitmap.getPixel(x, y) == selectedBackground }
            }
            assertTrue("La miniatura muestra el fondo Noche", selectedBackgroundPixels > wavesBitmap.width * wavesBitmap.height / 3)
            val differingPixels = (0 until wavesBitmap.height).sumOf { y ->
                (0 until wavesBitmap.width).count { x -> wavesBitmap.getPixel(x, y) != platesBitmap.getPixel(x, y) }
            }
            assertTrue("La miniatura pinta Ondas en vez de los Discos heredados", differingPixels > 1_000)
            saveBitmap("audit-c5", "poster-waves-night.png", wavesBitmap)
            saveBitmap("audit-c5", "poster-plates-night.png", platesBitmap)
        } finally {
            wavesBitmap.recycle()
            platesBitmap.recycle()
        }
    }

    @Test
    fun c5_homeShowsTheStoredStudioStyleAndPalette() {
        val program = Program(
            id = "c5-home-personalized-cover",
            name = "Plan propio",
            structureTemplateId = "generated:powerlifting",
            coverImage = "kpkn-studio://WAVES/NOCHE",
        )
        val selected = coverIdentityForProgram(program)
        val selectedBackground = coverRolesOf(selected).background.toArgb()
        val inheritedBackground = coverRolesOf(coverIdentityFor(program.id, program.structureTemplateId)).background.toArgb()
        assertEquals(CoverStudioStyle.WAVES, selected.style)
        assertEquals(CoverPaletteKind.NOCHE, selected.palette)

        rule.setContent {
            KPKNTheme {
                HomeProgramsSection(
                    programs = listOf(program),
                    activeProgramId = null,
                    onProgramClick = {},
                    onCreateProgram = {},
                )
            }
        }
        rule.waitForIdle()
        val cardTag = homeProgramCardTag(program.id)
        rule.onNodeWithTag(cardTag).assertIsDisplayed()
        val poster = rule.onNodeWithTag(cardTag).captureToImage().asAndroidBitmap()
        try {
            val selectedPixels = (0 until poster.height).sumOf { y ->
                (0 until poster.width).count { x -> poster.getPixel(x, y) == selectedBackground }
            }
            val inheritedPixels = (0 until poster.height).sumOf { y ->
                (0 until poster.width).count { x -> poster.getPixel(x, y) == inheritedBackground }
            }
            assertTrue("Home conserva suficiente fondo Noche en el póster guardado", selectedPixels > poster.width * poster.height / 3)
            assertTrue("Home no vuelve al fondo de la identidad heredada", selectedPixels > inheritedPixels)
            saveBitmap("audit-c5", "home-personalized-cover.png", poster)
        } finally {
            poster.recycle()
        }
    }

    @Test
    fun c5_personalPhotoLoadsAboveTheSelectedStudioPosterAtRuntime() {
        val identity = coverIdentityForProgram(
            Program(
                id = "c5-photo-priority-runtime",
                name = "Plan propio",
                structureTemplateId = "generated:powerlifting",
                coverImage = "kpkn-studio://WAVES/NOCHE",
            ),
        )
        val photoFile = File(context.cacheDir, "c5-personal-photo.png")
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.MAGENTA)
        }
        try {
            photoFile.outputStream().use { output ->
                assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val photoUri = android.net.Uri.fromFile(photoFile).toString()
            rule.setContent {
                KPKNTheme {
                    ProgramPoster(
                        identity = identity,
                        photoUrl = photoUri,
                        modifier = Modifier.testTag("c5-personal-photo-poster"),
                        animate = false,
                        width = 160.dp,
                        height = 200.dp,
                        radius = 0.dp,
                    )
                }
            }
            rule.waitUntil(15_000L) {
                val rendered = rule.onNodeWithTag("c5-personal-photo-poster").captureToImage().asAndroidBitmap()
                try {
                    rendered.getPixel(rendered.width / 2, rendered.height / 2) == android.graphics.Color.MAGENTA
                } finally {
                    rendered.recycle()
                }
            }
            val rendered = rule.onNodeWithTag("c5-personal-photo-poster").captureToImage().asAndroidBitmap()
            try {
                assertEquals(android.graphics.Color.MAGENTA, rendered.getPixel(rendered.width / 2, rendered.height / 2))
                saveBitmap("audit-c5", "poster-personal-photo.png", rendered)
            } finally {
                rendered.recycle()
            }
        } finally {
            source.recycle()
            photoFile.delete()
        }
    }

    private fun waitForSystemIme() {
        rule.waitUntil(10_000L) {
            systemImeVisible()
        }
        assertTrue(
            "el teclado del sistema aparece en el AVD",
            systemImeVisible(),
        )
    }

    private fun systemImeVisible(): Boolean {
        val activityInsetsVisible = rule.activity.window.decorView.rootWindowInsets
            ?.isVisible(PlatformWindowInsets.Type.ime()) == true
        val inputMethodWindowVisible = InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
        return activityInsetsVisible || inputMethodWindowVisible
    }

    private fun assertClearance(dockHeight: androidx.compose.ui.unit.Dp) {
        rule.onNodeWithTag("concept_bottom_clearance").assertHeightIsEqualTo(dockHeight)
        rule.onNodeWithTag("concept_bottom_fade").assertHeightIsEqualTo(dockHeight + ConceptBottomFadeLead)
    }

    private fun assertActionAboveDock(
        tag: String,
        dockHeight: androidx.compose.ui.unit.Dp,
        hostTag: String = "c8_real_host",
    ) {
        val hostBottom = rule.onNodeWithTag(hostTag).fetchSemanticsNode().boundsInRoot.bottom
        val dockTop = hostBottom - with(rule.density) { dockHeight.toPx() }
        val actionBounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag empieza dentro del dock: $actionBounds / top=$dockTop", actionBounds.top < dockTop)
        assertTrue("$tag queda cubierto por el dock: $actionBounds / top=$dockTop", actionBounds.bottom <= dockTop + 1f)
    }

    private fun liftTargetAboveDock(scrollTag: String, dockHeight: androidx.compose.ui.unit.Dp) {
        val scrollable = rule.onNodeWithTag(scrollTag)
        val size = scrollable.fetchSemanticsNode().size
        val safeBottom = with(rule.density) { (dockHeight + 8.dp).toPx() }
        val drag = with(rule.density) { 130.dp.toPx() }
        scrollable.performTouchInput {
            swipe(
                start = Offset(size.width / 2f, size.height - safeBottom),
                end = Offset(size.width / 2f, size.height - safeBottom - drag),
                durationMillis = 300,
            )
        }
        rule.waitForIdle()
    }

    private fun saveRoot(directoryName: String, fileName: String) {
        val directory = checkNotNull(context.getExternalFilesDir(directoryName))
        check(directory.isDirectory || directory.mkdirs())
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "captura de pantalla no disponible" }
        try {
            File(directory, fileName).outputStream().use { output ->
                assertTrue("escribe evidencia $fileName", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            emitEvidencePng(fileName, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveBitmap(directoryName: String, fileName: String, bitmap: Bitmap) {
        val directory = checkNotNull(context.getExternalFilesDir(directoryName))
        check(directory.isDirectory || directory.mkdirs())
        File(directory, fileName).outputStream().use { output ->
            assertTrue("escribe evidencia $fileName", bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        emitEvidencePng(fileName, bitmap)
    }

    private fun emitEvidencePng(fileName: String, bitmap: Bitmap) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            assertTrue("codifica evidencia visual $fileName", bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer))
        }.toByteArray()
        val label = fileName.removeSuffix(".png").uppercase().replace('-', '_')
        val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        encoded.chunked(3_000).forEachIndexed { index, chunk -> println("AUDIT_DEVICE_PNG_${label}_$index=$chunk") }
    }
}

@Composable
private fun EditorOverlayHost(content: @Composable () -> Unit) {
    val host = rememberKpknOverlayHostController()
    val haze = remember { HazeState() }
    CompositionLocalProvider(LocalKpknOverlayHost provides host, LocalHazeState provides haze) {
        Box(Modifier.fillMaxSize().hazeSource(state = haze)) {
            content()
            KpknOverlayHostContent(host)
        }
    }
}
