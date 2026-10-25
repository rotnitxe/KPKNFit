package com.example.kpkn.screens.programdetail.design

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.programdetail.ProgramDetailSamples
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34],qualifiers = "w360dp-h900dp-xxhdpi",application = android.app.Application::class)
class ProgramExerciseSwapEntryTest {
    companion object { @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install() }
    @get:Rule val rule = createComposeRule()
    @Test fun the_exercise_button_identifies_the_exact_session_and_exercise() {
        val exercise = Exercise("exercise","Press",catalogConfigurationId = CatalogIds.BP,
            sets = listOf(ExerciseSet("set",targetReps = 5,targetRIR = 2)))
        val session = Session("session","Torso",exercises = listOf(exercise))
        val day = ProgramDetailSamples.day(1,"Lunes","Lun",listOf(ProgramDetailSamples.sessionUi(session)))
        var selected: Pair<String,String>? = null
        rule.setContent {
            ProgramDayModule(day,true,{}, {}, {}, {}, {}, {}, { _,_ -> },{}, { _,_ -> },null,null,false,
                {},{ _,_ -> },{},{ _,_ -> },{},{},onSwapExercise = { sessionId,exerciseId -> selected = sessionId to exerciseId })
        }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.onNodeWithContentDescription("Cambiar ejercicio:",substring = true).performClick()
        rule.runOnIdle { assertEquals("session" to "exercise",selected) }
    }
}
