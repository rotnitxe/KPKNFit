package com.example.kpkn.screens.programdetail.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.kpkn.data.models.*
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
class ProgramContinuationSheetTest {
    @get:Rule val rule = createComposeRule()
    private fun preview(): ProgramRepository.ContinuationPreview {
        val original = Program("p","Plan")
        val exercise = Exercise("e","Press",sets = listOf(ExerciseSet("set",targetReps = 5,targetRIR = 4)))
        val week = ProgramWeek("w","Semana",sessions = listOf(Session("s","Torso",exercises = listOf(exercise))))
        val block = Block("b","B",mesocycles = listOf(Mesocycle("meso","M",weeks = listOf(week))))
        val next = Program("next","Mantenimiento · Plan",macrocycles = listOf(Macrocycle("m","M",listOf(block))))
        return ProgramRepository.ContinuationPreview(original,original,ContinuationChoice.MAINTENANCE_WEEK,next)
    }
    @Test fun a_real_week_is_reviewable_at_130_percent_before_creating_or_activating_it() {
        var accepted = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.3f)) {
                ProgramContinuationSheet(preview(),false,{ accepted++ },{})
            }
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Mitad de series, RIR 4",substring = true,useUnmergedTree = true).performScrollTo()
        rule.runOnIdle { assertEquals(0,accepted) }
        rule.onNodeWithText("Crear y activar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,accepted) }
    }
    @Test fun going_back_does_not_create_or_activate_anything() {
        var dismissed = 0
        rule.setContent { ProgramContinuationSheet(preview(),false,{ fail("sin confirmar") },{ dismissed++ }) }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithText("Cancelar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1,dismissed) }
    }
}
