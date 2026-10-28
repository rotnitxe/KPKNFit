package com.example.kpkn.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledgeKind
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.domain.concepts.searchConceptosClave
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConceptosClaveUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun libraryOpensTheReadingAndBackReturnsToTheLibrary() {
        val first = searchConceptosClave("").first()
        composeRule.setContent { MaterialTheme { ConceptosClaveScreen(onBack = {}) } }

        composeRule.onNodeWithText("Buscar conceptos").assertExists()
        // La portada es perezosa (áreas + índice A-Z): la fila se alcanza con el buscador.
        composeRule.onNodeWithTag("concept_search_field").performTextInput(first.name)
        composeRule.onNodeWithTag("concept_row_${first.id}").performClick()
        composeRule.onNodeWithTag("concept_reading_${first.id}").assertExists()
        composeRule.onNodeWithTag("concept_body_${first.id}_0").assertExists()
        composeRule.onNodeWithTag("concept_top_button").performClick()
        composeRule.onNodeWithTag("concept_library").assertExists()
    }

    @Test
    fun linkOpensTheReadingDirectlyAndCloseLeavesTheScreen() {
        val concept = TRAINING_CONCEPTS_DATABASE.first()
        val closed = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme { ConceptosClaveScreen(expandedConceptId = concept.id, onBack = { closed.value = true }) }
        }

        composeRule.onNodeWithTag("concept_reading_${concept.id}").assertExists()
        composeRule.onNodeWithText(concept.name).assertExists()
        composeRule.onNodeWithTag("concept_top_button").performClick()
        composeRule.runOnIdle { check(closed.value) }
    }

    @Test
    fun canonicalOverlayKeepsOnlyNameAndIntroForAllKnowledgeKinds() {
        val entries = listOf(
            CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "m", "Músculo", "Intro músculo"),
            CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "j", "Articulación", "Intro articulación"),
            CanonicalKnowledge(CanonicalKnowledgeKind.STABILIZER, "s", "Estabilizador", "Intro estabilizador"),
            CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "p", "Patrón", "Intro patrón"),
        )
        val selected = mutableStateOf(entries.first())
        composeRule.setContent {
            MaterialTheme {
                CanonicalKnowledgeOverlay(
                    knowledge = selected.value,
                    onDismiss = {},
                )
            }
        }
        entries.forEach { entry ->
            composeRule.runOnIdle { selected.value = entry }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(entry.name).assertExists()
            composeRule.onNodeWithText(entry.description).assertExists()
        }
        composeRule.onNodeWithText("biomechanicalReason", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Drenaje", substring = true).assertDoesNotExist()
    }
}
