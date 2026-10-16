package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.catalog
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders.definition
import com.example.kpkn.screens.sessioneditor.design.CatalogHeaderContextTag
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 1: «Reemplazar» abre el CATÁLOGO con el ejercicio actual como punto de partida, no la ficha del ejercicio. Monta el selector
 * entero (el de la página completa) con un catálogo pequeño y comprueba lo que se ve al llegar y que la ficha sigue a un toque.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = android.app.Application::class)
class ExercisePickerReplaceOpensCatalogTest {

    @get:Rule
    val rule = createComposeRule()

    /** Sin recorridos guiados: el anfitrión de overlays no necesita los ajustes de la app. */
    private object NoTourSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()

        override fun markSeen(tourId: String) = Unit
    }

    private val bench = definition(
        "bench_press", "Press de Banca Plano", "horizontal_push", listOf("pectoralis"), listOf("barbell", "dumbbells"),
    )
    private val incline = definition("incline_press", "Press Inclinado", "horizontal_push", listOf("pectoralis"), listOf("barbell"))
    private val fly = definition("chest_fly", "Aperturas", "horizontal_adduction", listOf("pectoralis"), listOf("cable"))
    private val squat = definition("squat", "Sentadilla", "knee_dominant", listOf("quadriceps"), listOf("barbell"))

    private fun repository() = InMemoryExerciseCatalogRepositoryV2(catalog(fly, incline, bench, squat))
        .also { runBlocking { it.load() } }

    /** El selector como lo monta la página del catálogo al reemplazar el press plano con mancuernas. */
    private fun setReplaceContent(
        replacement: CatalogReplacementContext? = CatalogReplacementContext(exerciseName = "Press de Banca Plano · Mancuernas"),
        onSelect: (ExerciseMuscleInfo) -> Unit = {},
    ) {
        val repository = repository()
        // Como en la app: las hojas (la ficha) se presentan en el anfitrión de overlays, no dentro de la fila de la lista.
        val overlays = KpknOverlayHostController(tourSeen = NoTourSeen)
        rule.setContent {
            var query by remember { mutableStateOf("") }
            CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                Box(Modifier.fillMaxSize()) {
                    ExercisePickerV2Catalog(
                        repository = repository,
                        query = query,
                        onSearch = { query = it },
                        editingExisting = true,
                        selectedExercisesIds = emptySet(),
                        onSelect = onSelect,
                        onMultiSelect = { emptyList() },
                        onSelectionChange = {},
                        onOpenExerciseDetail = {},
                        onDismiss = {},
                        initialCatalogDefinitionId = "bench_press",
                        initialCatalogConfigurationId = "bench_press__dumbbells",
                        opaqueSurface = true,
                        replacement = replacement,
                    )
                    KpknOverlayHostContent(overlays)
                }
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText("Sentadilla").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun reemplazarAbreElCatalogoYNoLaFichaDelEjercicioActual() {
        setReplaceContent()

        // El catálogo: cabecera de reemplazo, el ejercicio actual arriba y la lista.
        rule.onNodeWithText("Reemplazar ejercicio").assertExists()
        rule.onNodeWithTag(CatalogHeaderContextTag).assertExists()
        rule.onNodeWithText("Reemplazando: Press de Banca Plano · Mancuernas").assertExists()
        rule.onNodeWithText("Primero los más parecidos al ejercicio actual").assertExists()
        rule.onAllNodesWithText(CatalogCurrentExerciseLabel, substring = true).fetchSemanticsNodes().let {
            assertEquals("solo una fila lleva el rótulo del ejercicio actual", 1, it.size)
        }

        // La ficha NO se abrió sola: su botón de cierre no existe.
        rule.onNodeWithContentDescription("Cerrar definición").assertDoesNotExist()
    }

    @Test
    fun laListaEmpiezaPorElActualYPorLosDeSuPatron() {
        setReplaceContent()

        val tops = listOf("Press de Banca Plano", "Press Inclinado", "Aperturas", "Sentadilla").associateWith { name ->
            rule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top
        }

        assertTrue(
            "actual, mismo patrón, mismo músculo y resto, en ese orden: $tops",
            tops.getValue("Press de Banca Plano") < tops.getValue("Press Inclinado") &&
                tops.getValue("Press Inclinado") < tops.getValue("Aperturas") &&
                tops.getValue("Aperturas") < tops.getValue("Sentadilla"),
        )
    }

    @Test
    fun laFichaDelActualSigueAUnToqueDesdeElCatalogo() {
        setReplaceContent()
        rule.onNodeWithContentDescription("Cerrar definición").assertDoesNotExist()

        // Una definición con variantes despliega sus implementos y «Ver ficha» abre la hoja de hoy.
        rule.onNodeWithText("Press de Banca Plano").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ver ficha").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Cerrar definición").assertExists()
    }

    @Test
    fun sinContextoDeReemplazoLaFichaTampocoSeAbreSola() {
        // El selector en hoja del editor pasa el ejercicio a reemplazar sin contexto de cabecera: la ficha tampoco debe abrirse sola.
        setReplaceContent(replacement = null)

        rule.onNodeWithText("Añadir ejercicios").assertExists()
        rule.onNodeWithContentDescription("Cerrar definición").assertDoesNotExist()
    }
}
