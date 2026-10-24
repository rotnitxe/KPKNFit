package com.example.kpkn.screens.workout

import androidx.compose.ui.graphics.Color
import com.example.kpkn.domain.exercises.exerciseFichaEffects
import org.junit.Assert.*
import org.junit.Test

class LiveQaPresentationTest {
    @Test fun fichaTitlesNeverAppearAtTheSameTimeThroughoutTheCrossover() {
        for (scroll in 0..500) {
            val effects = exerciseFichaEffects(scroll.toFloat())
            assertTrue("Only one title at scroll $scroll", effects.titleAlpha == 0f || effects.headerAlpha == 0f)
        }
        assertEquals(1f, exerciseFichaEffects(0f).titleAlpha, 0f)
        assertEquals(1f, exerciseFichaEffects(310f).headerAlpha, 0f)
    }

    @Test fun explicitRestShapeWinsAcrossAdjustmentsAndOverrun() {
        val rest = WorkoutRestModalState(startedAtMs = 100000L, endsAtMs = 190000L, activeSeconds = 90)
        val adjusted = rest.copy(endsAtMs = 205000L, activeSeconds = 75, isManualOverride = true)
        val overrun = RestOverrun(endedAtMs = 205000L, totalSeconds = 105, startedAtMs = rest.startedAtMs)
        RestClockShape.entries.forEach { choice ->
            assertEquals(choice, restClockShapeForEvent(rest, choice = choice))
            assertEquals(choice, restClockShapeForEvent(adjusted, choice = choice))
            assertEquals(choice, restClockShapeForEvent(null, overrun, choice))
        }
        assertEquals(restClockShapeForEvent(rest), restClockShapeForEvent(adjusted))
        assertEquals(restClockShapeForEvent(rest), restClockShapeForEvent(null, overrun))
    }
}
