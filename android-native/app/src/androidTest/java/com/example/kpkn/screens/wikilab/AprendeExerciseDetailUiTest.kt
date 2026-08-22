package com.example.kpkn.screens.wikilab

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.exercises.approvedExerciseCatalogV2
import com.example.kpkn.data.exercises.catalogv2.toLegacyConfigurationLookup
import com.example.kpkn.data.models.ExerciseMuscleInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AprendeExerciseDetailUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun similar_relations_render_three_editorial_bands_without_drain_or_rpe() {
        val source = ExerciseMuscleInfo(id = "source", name = "Ejercicio actual")
        val equivalent = ExerciseMuscleInfo(id = "equivalent", name = "Sustituto equivalente")
        val variant = ExerciseMuscleInfo(id = "variant", name = "Variante del patrón")
        val transfer = ExerciseMuscleInfo(id = "transfer", name = "Transferencia de cadera")
        var openedId: String? = null

        composeRule.setContent {
            MaterialTheme {
                ExerciseSimilarThreeBand(
                    info = source,
                    catalog = listOf(source, equivalent, variant, transfer),
                    relations = AprendeExerciseRelations(
                        equivalent = listOf(AprendeSimilarItem(equivalent, "Misma intención", 100)),
                        patternVariants = listOf(AprendeSimilarItem(variant, "Mismo patrón", 40)),
                        anatomicalTransfer = listOf(AprendeSimilarItem(transfer, "Misma anatomía", 20)),
                    ),
                    onOpenExercise = { openedId = it },
                )
            }
        }

        composeRule.onNodeWithText("Equivalentes · misma intención").assertExists()
        composeRule.onNodeWithText("Variantes del patrón").assertExists()
        composeRule.onNodeWithText("Transferencia anatómica").assertExists()
        composeRule.onNodeWithText("Sustituto equivalente").performClick()
        composeRule.runOnIdle { assertEquals("equivalent", openedId) }
        composeRule.onNodeWithText("Drenaje", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("RPE", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Fatiga General", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun exercise_index_search_uses_the_approved_definition_catalog() {
        initializeExerciseDatabase(composeRule.activity)
        assertEquals(196, exerciseCatalogSnapshot().size)

        composeRule.setContent {
            MaterialTheme {
                WikiLabScreen(onOpenExercise = {}, onBack = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("Abducciones")
        composeRule.onNodeWithText("Abducciones de Pierna", substring = true).assertExists()
        composeRule.onNodeWithText("Buscar ejercicio...", substring = true).assertDoesNotExist()
    }

    @Test
    fun exact_configuration_detail_renders_editorial_content_without_fatigue_surface() {
        initializeExerciseDatabase(composeRule.activity)
        val catalog = requireNotNull(approvedExerciseCatalogV2())
        val definition = catalog.families
            .asSequence()
            .flatMap { it.definitions.asSequence() }
            .first { it.configurations.size > 1 }
        val configuration = definition.configurations.first { it.id != definition.defaultConfigurationId }
        val exercise = requireNotNull(catalog.toLegacyConfigurationLookup()[configuration.id])

        composeRule.setContent {
            MaterialTheme {
                ExerciseDetailScreen(
                    exercise = exercise,
                    onNavigateToMuscle = {},
                    onNavigateToJoint = {},
                    onNavigateToPattern = {},
                    onNavigateToExercise = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(configuration.profile.richMetadata!!.display.displayName).assertExists()
        composeRule.onNodeWithText("Configuración seleccionada").assertExists()
        composeRule.onNodeWithText(configuration.profile.variantRationale, substring = true).assertExists()
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Claves técnicas"))
        composeRule.onNodeWithText("Claves técnicas").assertExists()
        composeRule.onNodeWithText("Drenaje", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("RPE", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Fatiga General", useUnmergedTree = true).assertDoesNotExist()
    }
}
