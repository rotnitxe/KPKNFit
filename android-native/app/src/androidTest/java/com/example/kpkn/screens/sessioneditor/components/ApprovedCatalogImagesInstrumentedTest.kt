package com.example.kpkn.screens.sessioneditor.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.R
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.screens.sessioneditor.CatalogLaunchRequest
import com.example.kpkn.ui.components.MobilityTechniqueImage
import com.example.kpkn.ui.theme.KPKNTheme
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders the production catalog without completing onboarding or editing a session. */
class ApprovedCatalogImagesInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val output get() = File(context.getExternalFilesDir(null), "image-coverage-ui").also { it.mkdirs() }

    @Test
    fun all_packaged_exercise_webp_resources_decode_with_android_alpha_support() {
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
            .put("images", rows).toString(2))
    }

    @Test
    fun approved_machine_extension_is_visible_in_the_real_catalog() =
        catalogCase("quads_extension_cuadriceps", "quads_extension_cuadriceps__machine__bilateral", "machine-extension")

    @Test
    fun approved_cable_forward_lunge_is_visible_in_the_real_catalog() =
        catalogCase("forward_lunge", "forward_lunge__cable", "cable-forward-lunge")

    @Test
    fun approved_flat_pike_is_visible_in_the_real_catalog() =
        catalogCase("pike_push_up", "pike_push_up__flat", "pike-flat")

    @Test
    fun reviewed_elevated_pike_is_visible_in_the_real_catalog() =
        catalogCase("pike_push_up", "pike_push_up__feet_elevated", "pike-elevated")

    @Test
    fun reviewed_mobility_band_image_paints_on_a_dark_transparent_surface() {
        composeRule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize().background(Color(0xff303030)), contentAlignment = Alignment.Center) {
                    MobilityTechniqueImage("mob_ankle_band_dorsiflexion", "Movilidad con banda",
                        Modifier.size(280.dp))
                }
            }
        }
        val image = composeRule.onNodeWithContentDescription("Movilidad con banda")
        image.assertIsDisplayed()
        waitForPaint(image)
        save("mobility-band")
    }

    private fun catalogCase(definitionId: String, configurationId: String, name: String) {
        val catalog = context.assets.open("exercise_catalog_v2.json").bufferedReader().use {
            ExerciseCatalogV2Loader.decodeApproved(it.readText())
        }
        val definition = catalog.families.flatMap { it.definitions }.first { it.id == definitionId }
        val request = CatalogLaunchRequest(requestId = "image-coverage-$name",
            targetCatalogDefinitionId = definitionId, targetCatalogConfigurationId = configurationId,
            initialQuery = definition.canonicalName)
        composeRule.setContent {
            KPKNTheme {
                ExerciseCatalogScreen(request, onResult = {}, onBack = {}, onOpenExerciseDetail = {})
            }
        }
        val matcher = hasContentDescription("Ilustración del ejercicio con", substring = true)
        composeRule.waitUntil(60_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
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
