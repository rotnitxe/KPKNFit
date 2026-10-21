package com.example.kpkn.screens.programdetail.design

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverIdentityFor
import com.example.kpkn.ui.design.KpknSegmentedControl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cabecera, hoja «Editar programa» y pestañas de Program Detail (P1), en Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgramHeaderComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val program = Program(
        id = "generated:strength-muscle",
        name = "Fuerza de base",
        description = "Para empezar",
        mode = ProgramMode.POWERLIFTING,
    )

    private fun headerState(primary: ProgramHeaderPrimary) = ProgramHeaderState(
        name = program.name,
        status = ProgramHeaderStatus.ACTIVE,
        meta = "12 semanas · 4 días por semana · Fuerza",
        identity = coverIdentityFor(program.id, null),
        photoUrl = null,
        bundled = null,
        weekCount = 12,
        currentWeekIndex = 3,
        progressLabel = "Semana 3 de 12 · Acumulación",
        primary = primary,
    )

    private val noopActions = ProgramHeaderActions(onPrimary = {}, onStartPause = {}, onEdit = {})

    @Test
    fun primaryActionIsStartWhenTodayHasASession() {
        composeRule.setContent {
            ProgramHeader(state = headerState(ProgramHeaderPrimary.Start("Torso A")), actions = noopActions)
        }
        composeRule.onNodeWithText("Empezar Torso A").assertExists()
    }

    @Test
    fun primaryActionIsContinueWhenTheSessionIsOngoing() {
        composeRule.setContent {
            ProgramHeader(state = headerState(ProgramHeaderPrimary.Continue), actions = noopActions)
        }
        composeRule.onNodeWithText("Continuar sesión").assertExists()
    }

    @Test
    fun primaryActionIsActivateWhenTheProgramIsNotActive() {
        composeRule.setContent {
            ProgramHeader(state = headerState(ProgramHeaderPrimary.Activate), actions = noopActions)
        }
        composeRule.onNodeWithText("Activar programa").assertExists()
    }

    @Test
    fun saveIsDisabledWithoutChangesAndSavesOnce() {
        var draft by mutableStateOf(programEditDraftOf(program))
        var saves = 0
        composeRule.setContent {
            ProgramEditSheet(
                program = program,
                draft = draft,
                onDraftChange = { draft = it },
                onSave = { saves++ },
                onDismiss = {},
            )
        }
        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("Fuerza nueva")
        // Con cambios, «Guardar» tiene la acción de pulsar; se pulsa por semántica y no por coordenadas.
        composeRule.onNodeWithText("Guardar").assertIsEnabled().assertHasClickAction()
        composeRule.onNodeWithText("Guardar").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        assertEquals(1, saves)
    }

    @Test
    fun tabsExposeRoleTab() {
        composeRule.setContent {
            KpknSegmentedControl(
                options = listOf("Semana", "Estructura", "Volumen"),
                selectedIndex = 0,
                onSelect = {},
            )
        }
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(3)
    }
}
