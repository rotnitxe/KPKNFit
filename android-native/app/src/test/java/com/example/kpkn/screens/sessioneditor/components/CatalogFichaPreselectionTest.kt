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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 2, de punta a punta: con el selector entero y las fotos reales, la ficha de un ejercicio con varios implementos se abre en la
 * foto de su implemento por defecto (el que dice la fila y el que «Añadir» añadiría), no en la primera de la lista de fotos. Antes se
 * abría en la primera y la dejaba elegida: salía preseleccionado un implemento que no era el suyo, con su foto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = android.app.Application::class)
class CatalogFichaPreselectionTest {

    @get:Rule
    val rule = createComposeRule()

    /** Sin recorridos guiados: el anfitrión de overlays no necesita los ajustes de la app. */
    private object NoTourSeen : TourSeenStore {
        override fun seenIds(): Set<String> = emptySet()

        override fun markSeen(tourId: String) = Unit
    }

    /** Un catálogo con solo esa definición real (con su familia), para que la fila sea la única de la lista. */
    private fun repositoryWith(definition: ExerciseDefinitionV2): InMemoryExerciseCatalogRepositoryV2 {
        val real = CatalogTestBuilders.real
        val family = real.families.first { candidate -> candidate.definitions.any { it.id == definition.id } }
        return InMemoryExerciseCatalogRepositoryV2(real.copy(families = listOf(family.copy(definitions = listOf(definition)))))
            .also { runBlocking { it.load() } }
    }

    /** Monta el selector normal del catálogo, despliega las variantes de la fila y abre su ficha («Ver ficha»). */
    private fun openFicha(definition: ExerciseDefinitionV2) {
        val repository = repositoryWith(definition)
        val overlays = KpknOverlayHostController(tourSeen = NoTourSeen)
        rule.setContent {
            var query by remember { mutableStateOf("") }
            CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                Box(Modifier.fillMaxSize()) {
                    ExercisePickerV2Catalog(
                        repository = repository,
                        query = query,
                        onSearch = { query = it },
                        editingExisting = false,
                        selectedExercisesIds = emptySet(),
                        onSelect = {},
                        onMultiSelect = { emptyList() },
                        onSelectionChange = {},
                        onOpenExerciseDetail = {},
                        onDismiss = {},
                        opaqueSurface = true,
                    )
                    KpknOverlayHostContent(overlays)
                }
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(definition.canonicalName).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(definition.canonicalName).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ver ficha").performClick()
        rule.waitForIdle()
    }

    private fun assertFichaOpensOnTheDefaultImplementPhoto(id: String) {
        val definition = CatalogTestBuilders.realDefinition(id)
        val defaultImplement = definition.configurations
            .first { it.id == definition.defaultConfigurationId }
            .selectedOptions.getValue("implement")
        val photos = ExerciseTechniqueImageLookup.variants(definition.id)
        val expected = photos.first { it.implementation == defaultImplement }
        // El caso del fallo: la primera foto de la lista NO es la del implemento por defecto.
        assertNotEquals("$id: la primera foto no es la del implemento por defecto", expected.implementation, photos.first().implementation)

        openFicha(definition)

        rule.onNodeWithContentDescription("Ilustración del ejercicio con ${expected.label}").assertExists()
    }

    @Test
    fun laFichaDelCurlMartilloSeAbreEnLaFotoDeLasMancuernas() = assertFichaOpensOnTheDefaultImplementPhoto("hammer_curl")

    @Test
    fun laFichaDelJmPressSeAbreEnLaFotoDeLaBarraEz() = assertFichaOpensOnTheDefaultImplementPhoto("jm_press")

    @Test
    fun laFichaDeLaZancadaInversaSeAbreEnLaFotoDeLasMancuernas() = assertFichaOpensOnTheDefaultImplementPhoto("reverse_lunge")
}
