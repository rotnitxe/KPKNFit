package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.domain.training.cycle.WeeklyCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE entrega los toques sobre el panel recortado; LEGACY los pierde (BodyRecordSheetTest).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-xxhdpi", application = android.app.Application::class)
class WeeklyCheckinSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun answering_only_prepares_a_preview_and_confirmation_is_a_separate_action() {
        val detail = mutableStateOf<String?>(null)
        var answer: WeeklyCheckin? = null
        var confirmed = 0
        rule.setContent {
            WeeklyCheckinSheet(WeeklyCheckinRequest("w", muscleGroups = listOf("CHEST"), programRunId = "run",
                feedbackWeekId = "previous", feedbackCycleNumber = 1),
                previewDetail = detail.value, busy = false,
                onPreview = { answer = it; detail.value = "Pectorales: +1 serie, −1 RIR." },
                onConfirm = { confirmed++ }, onEdit = { detail.value = null },
                onOmit = { fail("no se eligió omitir") }, onDisable = {}, onDismiss = {})
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Revisar propuesta").assertDoesNotExist()
        rule.onNodeWithText("Fácil, superé el plan").performScrollTo().performClick()
        rule.onNodeWithText("Ninguna").performScrollTo().performClick()
        rule.onNodeWithText("Revisar propuesta").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(0, confirmed)
            assertEquals("run", answer!!.programRunId)
            assertEquals("previous", answer!!.feedbackWeekId)
            assertEquals(1, answer!!.feedbackCycleNumber)
            assertEquals(1, answer!!.muscles.single().performance)
            assertEquals(1, answer!!.muscles.single().soreness)
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Confirmar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun omitting_an_unanswered_week_does_not_invent_ratings() {
        var answer: WeeklyCheckin? = null
        rule.setContent {
            WeeklyCheckinSheet(WeeklyCheckinRequest("w", programRunId = "run"), previewDetail = null, busy = false,
                onPreview = { fail("no se eligió revisar") }, onConfirm = {}, onEdit = {},
                onOmit = { answer = it }, onDisable = {}, onDismiss = {})
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Omitir esta semana").performScrollTo().performClick()
        rule.runOnIdle {
            assertTrue(answer!!.skipped)
            assertTrue(answer!!.muscles.isEmpty())
            assertNull(answer!!.globalPerformance)
            assertNull(answer!!.globalSoreness)
        }
    }
}
