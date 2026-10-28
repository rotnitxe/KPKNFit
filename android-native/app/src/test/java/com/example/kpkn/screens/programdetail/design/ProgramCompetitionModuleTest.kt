package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.AttemptPlanner
import com.example.kpkn.screens.programdetail.ProgramCompetitionState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34],qualifiers = "w360dp-h900dp-xxhdpi",application = android.app.Application::class)
class ProgramCompetitionModuleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun another_sport_opens_its_record_without_offering_powerlifting_attempts() {
        var opened = 0
        val state = ProgramCompetitionState(CompetitionRecord("m","Meet",sportType = CompetitionTemplateType.WEIGHTLIFTING),
            10,null,null,emptyMap(),supportsSbdPlanning = false)
        rule.setContent { ProgramCompetitionModule(state,false,{ fail("SBD no aplica") },{ fail("SBD no aplica") },
            { fail("SBD no aplica") },{ opened++ }) }
        rule.onNodeWithText("Planificar intentos").assertDoesNotExist()
        rule.onNodeWithContentDescription("Ficha de competición").performClick()
        rule.runOnIdle { assertEquals(1,opened) }
    }

    @Test fun proposed_attempts_are_reviewed_at_130_percent_before_saving() {
        var saved: List<PlannedAttempt>? = null
        val state = ProgramCompetitionState(CompetitionRecord("m","Meet"),10,null,null,mapOf(CompetitionMovementType.SQUAT to 200.0))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                CompetitionPlanSheet(state,false,{ attempts,_,_ -> saved = attempts },{})
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Revisar planificación").performScrollTo().performClick()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Guardar planificación").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf(182.5,192.5,200.0),saved!!.map { it.weightKg }) }
    }

    @Test fun invalid_first_attempt_simulates_repeating_the_opener_without_saving_a_result() {
        var closed = 0
        val attempts = AttemptPlanner.plan(CompetitionMovementType.SQUAT,200.0)
        rule.setContent { MeetDaySheet(attempts) { closed++ } }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("meet-simulation:SQUAT:1:NO_LIFT").performScrollTo().performClick()
        rule.onNodeWithText("Siguiente intento orientativo: 182.5 kg",useUnmergedTree = true).assertExists()
        rule.runOnIdle { assertEquals(0,closed) }
        rule.onNodeWithText("Cerrar simulación").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,closed) }
    }

    @Test fun valid_record_marks_need_a_separate_confirmation() {
        var confirmed = 0
        val p = Program("p","Plan",powerliftingProfile = PowerliftingProfile(squat1RM = 190.0))
        val preview = ProgramRepository.MeetResultPreview(p,p.copy(powerliftingProfile = PowerliftingProfile(squat1RM = 200.0)),
            CompetitionRecord("m","Meet",status = CompetitionRecordStatus.COMPLETED),"Sentadilla válida: 200 kg",Settings(),
            ProgramRepository.ExecutedTrainingEvidence(emptySet(),emptySet()))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                MeetResultReviewSheet(preview,false,{ confirmed++ },{})
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Sentadilla válida: 200 kg",useUnmergedTree = true).performScrollTo()
        rule.runOnIdle { assertEquals(0,confirmed) }
        rule.onNodeWithText("Confirmar marcas").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,confirmed) }
    }
}
