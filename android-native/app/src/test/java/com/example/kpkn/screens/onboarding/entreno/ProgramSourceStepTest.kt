package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.domain.onboarding.ProgramSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PROGRAM_SOURCE · «¿Cómo quieres tu programa?»: tres símbolos de elección única (que KPKN lo recomiende, armarlo después o
 * importarlo), la importación oculta mientras no está disponible y la nota que cambia con la elección.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ProgramSourceStepTest {

    @get:Rule
    val rule = createComposeRule()

    /** Monta el paso como lo hace el asistente, sin ViewModel: [chosen] guarda la elección y [log] lo que se pidió. */
    private fun setStep(chosen: MutableState<ProgramSource?>, log: MutableList<ProgramSource>, importEnabled: Boolean) {
        rule.setContent {
            Column {
                ProgramSourceContent(
                    selected = chosen.value,
                    onChoose = { source ->
                        log += source
                        chosen.value = source
                    },
                    importEnabled = importEnabled,
                )
                EntrenoStepNote(programSourceNote(chosen.value))
            }
        }
    }

    @Test
    fun theOptionsAreRadioButtonsAndImportStaysHiddenWhileItIsNotAvailable() {
        setStep(mutableStateOf(null), mutableListOf(), importEnabled = false)
        for (source in listOf(ProgramSource.RECOMMEND, ProgramSource.LATER)) {
            rule.onNodeWithTag(programSourceTag(source))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        rule.onAllNodesWithTag(programSourceTag(ProgramSource.IMPORT)).assertCountEquals(0)
    }

    @Test
    fun importAppearsAsAThirdRadioButtonWhenItIsAvailable() {
        setStep(mutableStateOf(null), mutableListOf(), importEnabled = true)
        rule.onNodeWithTag(programSourceTag(ProgramSource.IMPORT))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun choosingReportsTheOptionOnlyTheChosenOneStaysSelectedAndTappingItAgainIsSilent() {
        val log = mutableListOf<ProgramSource>()
        setStep(mutableStateOf(null), log, importEnabled = false)
        rule.onNodeWithTag(programSourceTag(ProgramSource.LATER)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(programSourceTag(ProgramSource.LATER)).assertIsSelected()
        rule.onNodeWithTag(programSourceTag(ProgramSource.RECOMMEND)).assertIsNotSelected()
        // Tocar la opción ya elegida no vuelve a escribirla.
        rule.onNodeWithTag(programSourceTag(ProgramSource.LATER)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ProgramSource.LATER), log)
    }

    @Test
    fun theNoteFollowsTheChosenOption() {
        setStep(mutableStateOf(null), mutableListOf(), importEnabled = false)
        rule.onNodeWithTag(programSourceTag(ProgramSource.RECOMMEND)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Seguimos con unas preguntas y te proponemos programas para tu disciplina.").assertIsDisplayed()
        rule.onNodeWithTag(programSourceTag(ProgramSource.LATER)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Podrás crear tu programa desde Programas cuando quieras.").assertIsDisplayed()
    }
}
