package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.screens.nutrition.NutritionEditorSamples
import com.example.kpkn.screens.nutrition.NutritionPlanEditorUiState
import com.example.kpkn.screens.nutrition.design.editor.EditorSaveIsland
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorActions
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorContent
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * HOJA DE DIBUJO del editor del plan de Nutrición (N4): el plan completo en tres posiciones de la lista (arriba, energía y final), el plan
 * nuevo sin datos, la meta de peso en libras, la procedencia profesional, con la meta de hoy fijada, con el error de guardado, cargando y la
 * isla de «Guardar» con cambios, sin cambios y guardando. Pinta a 360 dp, con la letra al 100 % y al 130 %, sin movimiento. No comprueba
 * nada: escribe PNG. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi", application = android.app.Application::class)
class NutritionPlanEditorRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    /** Los casos de la hoja, en orden. */
    private val cases = listOf(
        "completo_arriba", "completo_energia", "completo_final",
        "nuevo_sin_datos", "meta_lb", "procedencia_profesional", "meta_hoy_fijada", "error_guardado", "cargando",
        "isla_sin_cambios", "isla_con_cambios", "isla_guardando",
    )

    /** El caso que se dibuja ahora y la escala de letra. Fuera de la composición para poder cambiarlos desde la prueba. */
    private var current by mutableIntStateOf(0)
    private var fontScale by mutableStateOf(1f)

    /** La vista de Compose, para dibujarla a un bitmap. */
    private var hostView: View? = null

    /** Lo primero de cada prueba: sin `KPKN_RENDER_DIR` no se dibuja nada y la prueba se salta (no falla). */
    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    /** Alto de cada caso: el editor se ve como una pantalla de teléfono; la isla, como una franja. */
    private fun heightOf(name: String): Int = if (name.startsWith("isla")) 220 else 780

    /** Primer elemento visible de la lista: 4 es «Calorías y macros» y 6, «Procedencia». */
    private fun scrollIndexOf(name: String): Int = when (name) {
        "completo_energia" -> 4
        "completo_final" -> 6
        else -> 0
    }

    private fun stateOf(name: String): NutritionPlanEditorUiState = when (name) {
        "completo_arriba", "completo_energia", "completo_final" -> NutritionEditorSamples.editedPlanState()
        "nuevo_sin_datos" -> NutritionPlanEditorUiState(isLoading = false, draft = NutritionPlanEditorDraft())
        "meta_lb" -> NutritionEditorSamples.editedPlanState().let { sample ->
            sample.copy(draft = sample.draft.copy(weightUnit = "lb", weightText = "163,5", targetValueText = "160"))
        }
        "procedencia_profesional" -> NutritionEditorSamples.editedPlanState().let { sample ->
            sample.copy(
                draft = sample.draft.copy(
                    direction = PlanDirection.PROFESSIONAL,
                    provenance = NutritionEditorProvenance.PROFESSIONAL,
                ),
                isDirty = false,
            )
        }
        "meta_hoy_fijada" -> NutritionEditorSamples.editedPlanState().copy(todayGoalFixedKcal = 1800)
        "error_guardado" -> NutritionEditorSamples.editedPlanState().copy(
            errors = mapOf("commit" to "No se pudo guardar el plan. Inténtalo de nuevo."),
        )
        "cargando" -> NutritionPlanEditorUiState(isLoading = true)
        else -> NutritionEditorSamples.editedPlanState()
    }

    /** Pinta el caso [name]. */
    @Composable
    private fun Sheet(name: String) {
        when (name) {
            "isla_sin_cambios" -> IslandCase(changeText = "Sin cambios", canSave = false, saving = false)
            "isla_con_cambios" -> IslandCase(changeText = "2.300 → 2.150 kcal", canSave = true, saving = false)
            "isla_guardando" -> IslandCase(changeText = "2.300 → 2.150 kcal", canSave = false, saving = true)
            else -> EditorCase(name)
        }
    }

    /** La isla de «Guardar» sola, abajo, como queda sobre la pantalla. */
    @Composable
    private fun IslandCase(changeText: String, canSave: Boolean, saving: Boolean) {
        Box(Modifier.fillMaxSize()) {
            EditorSaveIsland(
                changeText = changeText,
                canSave = canSave,
                saving = saving,
                onSave = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }

    /** La raíz del editor, con su lista colocada en la posición del caso. */
    @Composable
    private fun EditorCase(name: String) {
        val listState = rememberLazyListState()
        LaunchedEffect(name) { listState.scrollToItem(scrollIndexOf(name)) }
        NutritionPlanEditorContent(
            state = stateOf(name),
            actions = NutritionPlanEditorActions(),
            isNew = name == "nuevo_sin_datos",
            goalOpen = false,
            // Con el plan completo, el miércoles elegido en la franja muestra su línea de macros.
            selectedDay = if (name.startsWith("completo")) 2 else null,
            listState = listState,
        )
    }

    /** Escribe el recorte de la hoja [tag] en `KPKN_RENDER_DIR/<name>.png`. */
    private fun save(tag: String, name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderNutritionPlanEditorSheets() {
        onlyWhenAsked()
        rule.setContent {
            val density = LocalDensity.current
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Box(
                    Modifier
                        .testTag("sheet")
                        .width(360.dp)
                        .height(heightOf(cases[current]).dp)
                        .background(Color.Black),
                ) {
                    Sheet(cases[current])
                }
            }
        }
        for ((index, name) in cases.withIndex()) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    current = index
                    fontScale = scale
                }
                rule.waitForIdle()
                save("sheet", "${name}_$suffix")
            }
        }
    }
}
