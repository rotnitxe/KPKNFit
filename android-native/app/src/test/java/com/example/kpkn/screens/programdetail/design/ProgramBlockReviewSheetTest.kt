package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramDecision
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.block.*
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
class ProgramBlockReviewSheetTest {
    @get:Rule val rule = createComposeRule()
    private fun preview(): ProgramRepository.BlockReviewPreview {
        val review = BlockReview("b","Base",2,4,3,75,status = BlockReviewStatus.STABLE_RECORDS,summary = "3 de 4 sesiones registradas")
        val p = Program("p","Plan",pendingBlockReview = BlockReviewProposal(review,null))
        return ProgramRepository.BlockReviewPreview(p,p.copy(programDecisions = listOf(
            ProgramDecision("d",10,"BLOCK_REVIEW","Planificación conservada","Las sesiones mantienen su planificación."))),
            ProgramRepository.ExecutedTrainingEvidence(emptySet(),emptySet()))
    }
    @Test fun the_130_percent_sheet_explains_a_no_change_choice_and_confirms_only_on_tap() {
        var confirmed = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ProgramBlockReviewSheet(preview(),false,{ confirmed++ },{})
            }
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("3 de 4 sesiones registradas",substring = true,useUnmergedTree = true).performScrollTo()
        rule.runOnIdle { assertEquals(0,confirmed) }
        rule.onNodeWithText("Confirmar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,confirmed) }
    }
    @Test fun keeping_the_plan_is_its_own_action_and_there_is_no_back_button() {
        var kept = 0
        rule.setContent { ProgramBlockReviewSheet(preview(),false,{ fail("sin confirmar") },{},onKeepPlan = { kept++ }) }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Volver").assertDoesNotExist()
        rule.onNodeWithText("Conservar el plan").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,kept) }
    }
}
