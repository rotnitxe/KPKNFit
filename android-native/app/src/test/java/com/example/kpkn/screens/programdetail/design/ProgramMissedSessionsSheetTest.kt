package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.schedule.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las sesiones sin registrar (fila de «Pendiente» → su hoja final) y su revisión antes de confirmar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34],qualifiers = "w360dp-h900dp-xxhdpi",application = android.app.Application::class)
class ProgramMissedSessionsSheetTest {
    @get:Rule val rule = createComposeRule()
    private val original = Program("p","Plan",runState = ProgramRunState("run",weekId = "w"),
        pendingMissedSessions = MissedSessionsRequest("w","inst_c1_w",listOf("s1","s2"),programRunId = "run"))
    private val result = original.copy(pendingMissedSessions = null,skippedSessionKeys = listOf("key1","key2"),
        programDecisions = listOf(ProgramDecision("decision",10,"MISSED_SESSION","Sesiones saltadas","Se declararán dos sesiones saltadas.")))

    @Test fun no_record_is_not_presented_as_no_training_and_choice_only_requests_review() {
        var chosen: MissedSessionChoice? = null
        rule.setContent { ProgramMissedSessionsSheet(original,original.pendingMissedSessions,null,false,{ chosen = it },{},{}) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("2 sesiones sin registrar").assertExists()
        rule.onNodeWithText("Sin registro no quiere decir sin entrenar: elige cómo sigue el plan.").assertExists()
        rule.onNodeWithText("Hacerlas esta semana").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(MissedSessionChoice.DO_THIS_WEEK,chosen) }
    }

    @Test fun skipped_session_count_and_originals_are_reviewed_at_130_percent_before_confirmation() {
        var confirmed = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ProgramScheduleReviewSheet(original,result,false,{ confirmed++ },{})
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("2 sesiones se declararán saltadas. Los registros de entrenamiento se conservan.",substring = true,useUnmergedTree = true).performScrollTo()
        rule.runOnIdle { assertEquals(0,confirmed) }
        rule.onNodeWithText("Confirmar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,confirmed) }
    }

    @Test fun the_review_has_no_back_button_and_closing_keeps_the_plan() {
        rule.setContent { ProgramScheduleReviewSheet(original,result,false,{ fail("sin confirmar") },{}) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Volver").assertDoesNotExist()
        rule.onNodeWithText("Revisar continuidad").assertExists()
    }
}
