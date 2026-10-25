package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.training.calibration.PlanningVolumeTarget
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
class ProgramPlanningVolumeTargetSheetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun a_range_is_an_explicit_choice_and_invalid_values_cannot_be_saved() {
        var saved: PlanningVolumeTarget? = null
        rule.setContent {
            ProgramPlanningVolumeTargetSheet(emptyList(), null, emptyList(), null, busy = false,
                onSave = { saved = it }, onReset = {}, onDismiss = {})
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Mínimo semanal").performScrollTo().performTextReplacement("6")
        rule.onNodeWithText("Máximo semanal").performScrollTo().performTextReplacement("4")
        rule.onNodeWithText("Guardar este rango").assertDoesNotExist()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithText("Máximo semanal").performScrollTo().performTextReplacement("20")
        rule.onNodeWithText("Nota opcional").performScrollTo().performTextReplacement("Mi elección")
        rule.onNodeWithText("Guardar este rango").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(KpknMuscleGroup.CHEST, saved!!.muscleGroup)
            assertEquals(6, saved!!.minSets)
            assertEquals(20, saved!!.maxSets)
            assertEquals("Mi elección", saved!!.notes)
        }
    }

    @Test fun restoring_the_reference_targets_only_the_selected_muscle() {
        var reset: KpknMuscleGroup? = null
        rule.setContent {
            ProgramPlanningVolumeTargetSheet(listOf(PlanningVolumeTarget(KpknMuscleGroup.CHEST, 6, 20)),
                null, emptyList(), null, busy = false, onSave = {}, onReset = { reset = it }, onDismiss = {})
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Usar referencia editorial").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(KpknMuscleGroup.CHEST, reset) }
    }
}
