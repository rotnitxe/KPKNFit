package com.example.kpkn.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledgeKind
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.domain.concepts.projectConceptoClave
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
    fun conceptsExpandInlineAndOnlyOneConceptRemainsOpen() {
        val ordered = searchConceptosClave("")
        val first = ordered.first()
        val second = ordered[1]
        composeRule.setContent {
            MaterialTheme {
                ConceptosClaveScreen(onBack = {})
            }
        }

        composeRule.onNodeWithText("Buscar conceptos").assertExists()
        composeRule.onNodeWithText(first.name).assertExists()
        composeRule.onNodeWithTag("concept_header_${first.id}").performClick()
        composeRule.onNodeWithText(first.description).assertExists()
        composeRule.onNodeWithTag("concept_body_expanded_${first.id}").assertExists()
        composeRule.onNodeWithText("Leer menos").assertExists()

        composeRule.onNodeWithTag("concept_header_${second.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("concept_body_expanded_${first.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("concept_body_expanded_${second.id}").assertExists()
        composeRule.onNodeWithText(second.description).assertExists()
        composeRule.onNodeWithText("Wikipedia", substring = true).assertDoesNotExist()
    }

    @Test
    fun homeAccordionExpandsWithoutNavigationToDetail() {
        val concept = projectConceptoClave(TRAINING_CONCEPTS_DATABASE.first())
        val toggled = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                ConceptoClaveAccordion(
                    concept = concept,
                    expanded = toggled.value,
                    onToggle = { toggled.value = !toggled.value },
                )
            }
        }

        composeRule.onNodeWithText(concept.name).performClick()
        composeRule.runOnIdle { check(toggled.value) }
        composeRule.onNodeWithText(concept.description).assertExists()
        composeRule.onNodeWithText("Leer menos").assertExists()
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
