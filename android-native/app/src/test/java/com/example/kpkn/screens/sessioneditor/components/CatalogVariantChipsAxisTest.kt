package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LV5, bug 2 (variantes): los chips bajo una fila son las variantes del PRIMER eje de la definición, que no siempre es el implemento
 * (agarre, lateralidad, ángulo de apoyo…). Antes tocar un chip llamaba siempre a «implement»: con un eje distinto no cambiaba nada y la
 * variante que la persona acababa de tocar no quedaba elegida. Con las definiciones reales del catálogo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xxhdpi", application = android.app.Application::class)
class CatalogVariantChipsAxisTest {

    @get:Rule
    val rule = createComposeRule()

    /** Un catálogo con solo esa definición real (con su familia), para que la fila sea la única de la lista. */
    private fun repositoryWith(definition: ExerciseDefinitionV2): InMemoryExerciseCatalogRepositoryV2 {
        val real = CatalogTestBuilders.real
        val family = real.families.first { candidate -> candidate.definitions.any { it.id == definition.id } }
        return InMemoryExerciseCatalogRepositoryV2(real.copy(families = listOf(family.copy(definitions = listOf(definition)))))
            .also { runBlocking { it.load() } }
    }

    /** Monta el selector normal del catálogo con esa definición y despliega las variantes bajo su fila. */
    private fun openVariants(definition: ExerciseDefinitionV2) {
        val repository = repositoryWith(definition)
        rule.setContent {
            var query by remember { mutableStateOf("") }
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
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(definition.canonicalName).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(definition.canonicalName).performClick()
        rule.waitForIdle()
    }

    /** El chip de una variante: el nodo con su rótulo que además se puede marcar (lleva el estado de «elegido»). */
    private fun chip(definition: ExerciseDefinitionV2, value: String): SemanticsMatcher =
        hasText(exerciseCatalogVariantTagLabel(value, definition.id)) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

    @Test
    fun elChipDeUnAgarreEligeElAgarreYSePuedeCambiar() {
        val definition = CatalogTestBuilders.realDefinition("gironda_row")
        // El primer eje de esta definición es el ancho del agarre, no el implemento.
        assertEquals("grip_width", definition.optionAxes.first())
        openVariants(definition)

        rule.onNode(chip(definition, "wide")).assertIsOff()
        rule.onNode(chip(definition, "close")).assertIsOff()

        rule.onNode(chip(definition, "wide")).performClick()
        rule.waitForIdle()
        rule.onNode(chip(definition, "wide")).assertIsOn()
        rule.onNode(chip(definition, "close")).assertIsOff()

        // Y se puede cambiar de variante: la nueva queda elegida y la anterior no.
        rule.onNode(chip(definition, "close")).performClick()
        rule.waitForIdle()
        rule.onNode(chip(definition, "close")).assertIsOn()
        rule.onNode(chip(definition, "wide")).assertIsOff()
    }

    @Test
    fun elChipDelAnguloDeApoyoEligeSuPropioEje() {
        val definition = CatalogTestBuilders.realDefinition("push_up")
        assertNotEquals("implement", definition.optionAxes.first())
        openVariants(definition)

        rule.onNode(chip(definition, "feet_elevated")).assertIsOff()
        rule.onNode(chip(definition, "feet_elevated")).performClick()
        rule.waitForIdle()

        rule.onNode(chip(definition, "feet_elevated")).assertIsOn()
    }

    @Test
    fun elChipDeLateralidadEligeSuPropioEje() {
        val definition = CatalogTestBuilders.realDefinition("belt_squat")
        assertNotEquals("implement", definition.optionAxes.first())
        openVariants(definition)

        rule.onNode(chip(definition, "unilateral")).assertIsOff()
        rule.onNode(chip(definition, "unilateral")).performClick()
        rule.waitForIdle()

        rule.onNode(chip(definition, "unilateral")).assertIsOn()
        rule.onNode(chip(definition, "bilateral")).assertIsOff()
    }
}
