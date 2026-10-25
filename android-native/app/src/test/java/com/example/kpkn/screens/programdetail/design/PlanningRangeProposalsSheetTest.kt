package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.training.calibration.PlanningRangeProposal
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
class PlanningRangeProposalsSheetTest {
    @get:Rule val rule = createComposeRule()
    private val before = PlanningVolumeTarget(KpknMuscleGroup.CHEST, 8, 22)
    private val proposal = PlanningRangeProposal("p", 1, before, before.copy(maxSets = 24), listOf("w1", "w2", "w3", "w4", "w5"),
        "Cinco respuestas declaradas y dosis planificada próxima al máximo elegido. Se propone 24 series de máximo de planificación.")

    @Test fun values_are_reviewable_and_confirmation_is_explicit_at_large_font() {
        var accepted = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                PlanningRangeProposalsSheet(listOf(proposal), busy = false, onAccept = { accepted++ }, onReject = {}, onDismiss = {})
            }
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Antes: 8–22 series por semana\nPropuesto: 8–24", useUnmergedTree = true).performScrollTo()
        rule.runOnIdle { assertEquals(0, accepted) }
        rule.onNodeWithText("Confirmar rangos propuestos").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, accepted) }
    }

    @Test fun later_does_not_apply_or_reject_the_proposal() {
        var dismissed = 0
        rule.setContent {
            PlanningRangeProposalsSheet(listOf(proposal), busy = false, onAccept = { fail("no se confirmó") },
                onReject = { fail("no se descartó") }, onDismiss = { dismissed++ })
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Más tarde").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, dismissed) }
    }
}
