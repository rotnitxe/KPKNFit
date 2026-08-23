package com.example.kpkn.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge
import com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledgeKind
import com.example.kpkn.data.wikilab.TRAINING_CONCEPTS_DATABASE
import com.example.kpkn.ui.components.CanonicalKnowledgeTooltip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConceptosClaveUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeConceptSearchOpensSimpleConceptDetail() {
        var openedId by mutableStateOf<String?>(null)
        val concept = TRAINING_CONCEPTS_DATABASE.first()
        composeRule.setContent {
            MaterialTheme {
                if (openedId == null) {
                    ConceptosClaveScreen(onOpenConcept = { openedId = it }, onBack = {})
                } else {
                    ConceptoClaveDetailScreen(openedId!!, onBack = {})
                }
            }
        }

        composeRule.onNodeWithText(concept.name).assertExists().performClick()
        composeRule.runOnIdle { check(openedId == concept.id) }

        composeRule.onNodeWithText(concept.category.label).assertExists()
        composeRule.onNodeWithText(concept.shortDescription).assertExists()
        composeRule.onNodeWithText("Wikipedia", substring = true).assertDoesNotExist()
    }

    @Test
    fun canonicalTooltipKeepsOnlyNameAndIntroForAllKnowledgeKinds() {
        val entries = listOf(
            CanonicalKnowledge(CanonicalKnowledgeKind.MUSCLE, "m", "Músculo", "Intro músculo"),
            CanonicalKnowledge(CanonicalKnowledgeKind.JOINT, "j", "Articulación", "Intro articulación"),
            CanonicalKnowledge(CanonicalKnowledgeKind.STABILIZER, "s", "Estabilizador", "Intro estabilizador"),
            CanonicalKnowledge(CanonicalKnowledgeKind.PATTERN, "p", "Patrón", "Intro patrón"),
        )
        composeRule.setContent {
            MaterialTheme { entries.forEach { CanonicalKnowledgeTooltip(it) } }
        }
        entries.forEach {
            composeRule.onNodeWithText(it.name).assertExists()
            composeRule.onNodeWithText(it.description).assertExists()
        }
        composeRule.onNodeWithText("biomechanicalReason", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Drenaje", substring = true).assertDoesNotExist()
    }
}
