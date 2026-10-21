package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.R
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.screens.sessioneditor.components.sheets.MobilityPickerSheet
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.MobilityTechniqueImage
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the current catalog and its portal-based definition sheet, with real packaged art. */
class ApprovedCatalogImagesInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null), "image-coverage-ui17").also { it.mkdirs() }

    private object NoTourSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()
        override fun markSeen(tourId: String) = Unit
    }

    @Test
    fun all_packaged_exercise_webp_resources_decode_in_the_current_app_version() {
        val app = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("KPKN Pre Release 17", app.versionName)
        assertEquals(50L, PackageInfoCompat.getLongVersionCode(app))
        val fields = R.drawable::class.java.fields.filter { it.name.startsWith("exercise_") }.sortedBy { it.name }
        assertEquals(623, fields.size)
        val rows = JSONArray()
        fields.forEach { field ->
            val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, field.getInt(null),
                BitmapFactory.Options().apply { inScaled = false })) { field.name }
            assertTrue(field.name, bitmap.hasAlpha())
            rows.put(JSONObject().put("resource", field.name).put("width", bitmap.width)
                .put("height", bitmap.height).put("hasAlpha", bitmap.hasAlpha()))
            bitmap.recycle()
        }
        File(output, "android-resource-decode.json").writeText(JSONObject().put("decoded", fields.size)
            .put("versionCode", PackageInfoCompat.getLongVersionCode(app)).put("versionName", app.versionName)
            .put("images", rows).toString(2))
    }

    @Test
    fun approved_machine_extension_is_visible_in_the_current_ficha() =
        catalogCase("quads_extension_cuadriceps", "quads_extension_cuadriceps__machine__bilateral", "machine-extension")

    @Test
    fun approved_cable_forward_lunge_is_visible_in_the_current_ficha() =
        catalogCase("forward_lunge", "forward_lunge__cable", "cable-forward-lunge")

    @Test
    fun approved_flat_pike_is_visible_in_the_current_ficha() =
        catalogCase("pike_push_up", "pike_push_up__flat", "pike-flat")

    @Test
    fun reviewed_elevated_pike_is_visible_in_the_current_ficha() =
        catalogCase("pike_push_up", "pike_push_up__feet_elevated", "pike-elevated")

    @Test
    fun reviewed_band_image_is_visible_in_the_current_mobility_sheet() {
        val mobility = MobilityExerciseCatalog.getAllMobilityExercises().first { it.id == "mob_ankle_band_dorsiflexion" }
        showWithOverlays { MobilityPickerSheet(onAdd = {}, onDismiss = {}) }
        composeRule.onNode(hasSetTextAction()).performTextInput(mobility.name)
        composeRule.waitForIdle()
        val openFicha = hasText("Ver ficha") and hasClickAction()
        composeRule.waitUntil(20_000) { composeRule.onAllNodes(openFicha).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(openFicha)[0].performClick()
        val image = composeRule.onNodeWithContentDescription("Técnica de ${mobility.name}")
        image.assertIsDisplayed()
        waitForPaint(image)
        save("mobility-sheet")
    }

    @Test
    fun reviewed_mobility_band_image_paints_on_a_dark_transparent_surface() {
        showWithOverlays {
            Box(Modifier.fillMaxSize().background(Color(0xff303030)), contentAlignment = Alignment.Center) {
                MobilityTechniqueImage("mob_ankle_band_dorsiflexion", "Movilidad con banda", Modifier.size(280.dp))
            }
        }
        val image = composeRule.onNodeWithContentDescription("Movilidad con banda")
        image.assertIsDisplayed()
        waitForPaint(image)
        save("mobility-band")
    }

    private fun showWithOverlays(content: @Composable () -> Unit) {
        composeRule.setContent {
            KPKNTheme {
                val overlays = remember { KpknOverlayHostController(tourSeen = NoTourSeen) }
                CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        content()
                        KpknOverlayHostContent(overlays)
                    }
                }
            }
        }
    }

    private fun catalogCase(definitionId: String, configurationId: String, name: String) {
        val catalog = context.assets.open("exercise_catalog_v2.json").bufferedReader().use {
            ExerciseCatalogV2Loader.decodeApproved(it.readText())
        }
        val definition = catalog.families.flatMap { it.definitions }.first { it.id == definitionId }
        val request = CatalogLaunchRequest(requestId = "image-coverage-$name",
            targetCatalogDefinitionId = definitionId, targetCatalogConfigurationId = configurationId,
            initialQuery = definition.canonicalName)
        showWithOverlays { ExerciseCatalogScreen(request, onResult = {}, onBack = {}, onOpenExerciseDetail = {}) }
        val row = hasText(definition.canonicalName) and hasClickAction() and !hasSetTextAction()
        composeRule.waitUntil(60_000) { composeRule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(row)[0].performClick()
        val openFicha = hasText("Ver ficha") and hasClickAction()
        composeRule.waitUntil(20_000) { composeRule.onAllNodes(openFicha).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(openFicha)[0].performClick()
        val matcher = hasContentDescription("Ilustración del ejercicio con", substring = true)
        composeRule.waitUntil(20_000) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val image = composeRule.onAllNodes(matcher)[0]
        image.assertIsDisplayed()
        waitForPaint(image)
        save(name)
    }

    private fun waitForPaint(image: SemanticsNodeInteraction) {
        composeRule.waitUntil(20_000) {
            val bitmap = image.captureToImage().asAndroidBitmap()
            val colors = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step maxOf(1, bitmap.height / 40)) {
                for (x in 0 until bitmap.width step maxOf(1, bitmap.width / 40)) colors += bitmap.getPixel(x, y)
            }
            colors.size > 32
        }
    }

    private fun save(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
