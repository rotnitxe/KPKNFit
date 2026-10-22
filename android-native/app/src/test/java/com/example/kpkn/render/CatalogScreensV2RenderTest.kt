package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogRepositoryV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogStateV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.sessioneditor.components.CatalogReplacementContext
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders
import com.example.kpkn.screens.sessioneditor.components.ExercisePickerV2Catalog
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/** Mismo arnés Canvas/LocalView de las hojas existentes, ahora con la pantalla real a 360 × 880 px. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1000dp-mdpi", application = android.app.Application::class)
class CatalogScreensV2RenderTest {
    companion object {
        @JvmStatic @BeforeClass fun onlyWhenAsked() {
            assumeTrue("Requiere KPKN_RENDER_DIR", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule val rule = createComposeRule()
    private var hostView: View? = null
    private var fontScale by mutableStateOf(1f)
    private val ids = listOf("bench_press", "chest_supported_row", "romanian_deadlift", "gironda_row", "push_up")
    private fun name(id: String) = CatalogTestBuilders.realDefinition(id).canonicalName
    private object NoTourSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()
        override fun markSeen(tourId: String) = Unit
    }

    private fun mount(query: String = "", technique: Boolean = false, replacement: Boolean = false, loading: Boolean = false): MutableStateFlow<ExerciseCatalogStateV2> {
        val real = CatalogTestBuilders.real
        val catalog = real.copy(families = real.families.mapNotNull { family ->
            family.definitions.filter { it.id in ids }.takeIf { it.isNotEmpty() }?.let { family.copy(definitions = it) }
        })
        val delegate = InMemoryExerciseCatalogRepositoryV2(catalog).also { runBlocking { it.load() } }
        val catalogState = MutableStateFlow<ExerciseCatalogStateV2>(if (loading) ExerciseCatalogStateV2.Loading else ExerciseCatalogStateV2.Ready(catalog))
        val repository = object : ExerciseCatalogRepositoryV2 by delegate {
            override val state = catalogState
            override suspend fun load() { catalogState.value = ExerciseCatalogStateV2.Ready(catalog) }
        }
        val overlays = KpknOverlayHostController(tourSeen = NoTourSeen)
        rule.setContent {
            hostView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides overlays,
            ) {
                KPKNTheme {
                    Box(Modifier.width(360.dp).height(880.dp).background(Color.Black).testTag("catalog_screen_v2")) {
                        var text by remember { mutableStateOf(query) }
                        var selected by remember { mutableStateOf(emptyList<String>()) }
                        ExercisePickerV2Catalog(
                            repository = repository, query = text, onSearch = { text = it },
                            editingExisting = technique || replacement,
                            singleSelectionLabel = if (technique) "Elegir" else null,
                            selectedExercisesIds = selected.toSet(), onSelect = {}, onMultiSelect = { it.map { info -> info.id } },
                            onSelectionChange = { selected = it.map { info -> info.id } },
                            onOpenExerciseDetail = {}, onDismiss = {}, opaqueSurface = true,
                            targetGroupName = "Pierna A",
                            onCreateSupersetConfigured = { _, _ -> },
                            replacement = if (replacement) CatalogReplacementContext(name("bench_press"), "4 × 4–6 · RIR 2") else null,
                            initialCatalogDefinitionId = if (replacement) "bench_press" else null,
                            initialCatalogConfigurationId = if (replacement) "bench_press__barbell" else null,
                        )
                        KpknOverlayHostContent(overlays)
                    }
                }
            }
        }
        if (!loading && query.isBlank()) waitFor(name("bench_press"))
        return catalogState
    }

    private fun waitFor(text: String) = rule.waitUntil(20_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun select(id: String) {
        val description = "Añadir ${name(id)} a la selección"
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(description))
        rule.onNodeWithContentDescription(description).performClick()
    }

    private fun save(name: String, beforeCapture: () -> Unit = {}) {
        for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
            rule.runOnIdle { fontScale = scale }
            rule.waitForIdle()
            beforeCapture()
            rule.waitForIdle()
            val bounds = rule.onNodeWithTag("catalog_screen_v2").fetchSemanticsNode().boundsInRoot
            val view = requireNotNull(hostView)
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { view.draw(Canvas(full)) }
            val cropped = Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            assertEquals(360, cropped.width)
            assertEquals(880, cropped.height)
            val dir = File(requireNotNull(System.getenv("KPKN_RENDER_DIR"))).also { it.mkdirs() }
            FileOutputStream(File(dir, "${name}_$suffix.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            full.recycle(); cropped.recycle()
        }
        rule.runOnIdle { fontScale = 1f }
    }

    @Test fun e1AnadirYAporteReal() {
        mount()
        rule.onNodeWithText(name("bench_press")).performClick()
        rule.onNodeWithTag("catalog_muscle_contribution").assertExists()
        save("E1_anadir") {
            rule.onNodeWithTag("catalog_muscle_contribution").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun e2BusquedaAlturaYAgarre() {
        mount(query = "remo pecho apoyado polea")
        waitFor(name("chest_supported_row"))
        rule.onNodeWithText(name("chest_supported_row")).performClick()
        rule.onNodeWithContentDescription("Polea Baja").performClick()
        rule.onNodeWithText("Cerrado").performClick()
        select("chest_supported_row")
        save("E2_busqueda")
    }

    @Test fun e3SeleccionOrdenada() {
        mount(); select("bench_press"); select("chest_supported_row")
        rule.onNodeWithContentDescription("Ver los ejercicios elegidos").performClick()
        save("E3_seleccion")
    }

    @Test fun e4Superserie() {
        mount(); select("bench_press"); select("chest_supported_row")
        rule.onNodeWithText("Superserie").performClick()
        rule.onNodeWithText("Configurar superserie").assertExists()
        save("E4_superserie")
        rule.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Crear superserie"))
        save("E4_superserie_confirmar")
    }

    @Test fun e5Reemplazar() {
        mount(replacement = true); select("chest_supported_row")
        save("E5_reemplazar")
    }

    @Test fun e6SinCoincidencias() {
        mount(query = "remo invertido con toalla zzz")
        waitFor("Crear ejercicio"); save("E6_sin_coincidencias")
    }

    @Test fun e6FiltroSinResultados() {
        mount(query = "press")
        waitFor(name("bench_press"))
        rule.onNodeWithText("Tren inferior").performClick()
        waitFor("Ver en Todos"); save("E6_filtro_vacio")
    }

    @Test fun e8CargaYError() {
        val states = mount(loading = true)
        save("E8_cargando")
        rule.runOnIdle { states.value = ExerciseCatalogStateV2.Error("prueba") }
        save("E8_error")
    }

    @Test fun e9ElegirParaTecnica() {
        mount(technique = true); select("chest_supported_row")
        save("E9_elegir_tecnica")
    }
}
