package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.screens.programdetail.StructureSubTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Arreglos v1 · p-head: la barra flotante se queda con atrás y las tres pestañas; ⋯ baja a la fila del nombre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramFloatingBarComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun headerState() = ProgramHeaderState(
        name = "Fuerza de base",
        status = ProgramHeaderStatus.ACTIVE,
        meta = "Fuerza · 12 semanas",
        identity = coverIdentityFor("generated:strength-muscle", null),
        photoUrl = null,
        bundled = null,
        progressLabel = "Semana 3 de 12 · Acumulación",
        primary = ProgramHeaderPrimary.Start("Torso A"),
    )

    @Test
    fun barHasBackAndThreeTabsButNoMore() {
        var back = 0
        var picked: StructureSubTab? = null
        composeRule.setContent {
            ProgramFloatingBar(
                selected = StructureSubTab.SEMANA,
                onSelect = { picked = it },
                onBack = { back++ },
                hazeState = null,
            )
        }
        composeRule.onNodeWithContentDescription("Atrás").performClick()
        composeRule.onNodeWithText("Progreso").performClick()
        composeRule.onNodeWithText("Estructura").assertExists()
        composeRule.onAllNodesWithContentDescription("Más opciones", substring = true).assertCountEquals(0)
        assertEquals(1, back)
        assertEquals(StructureSubTab.VOLUMEN, picked)
    }

    @Test
    fun moreSitsNextToTheNameAndOpensTheMenu() {
        var opened = 0
        composeRule.setContent {
            ProgramHeader(
                state = headerState(),
                actions = ProgramHeaderActions(onPrimary = {}, onMore = { opened++ }, moreDot = true),
            )
        }
        composeRule.onNodeWithText("Fuerza de base").assertExists()
        composeRule.onNodeWithContentDescription("Más opciones del programa, hay pendientes").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun moreWithoutPendingHasNoDotInItsLabel() {
        composeRule.setContent {
            ProgramHeader(
                state = headerState(),
                actions = ProgramHeaderActions(onPrimary = {}, onMore = {}, moreDot = false),
            )
        }
        composeRule.onNodeWithContentDescription("Más opciones del programa").assertExists()
    }
}
