package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** El reloj de la figura se para cuando el scroll la oculta y continúa al volver a verla. */
@RunWith(AndroidJUnit4::class)
class FeedbackAnimationVisibilityDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scrollingTheFigureOutOfTheViewportStopsItsClock() {
        lateinit var visibility: FeedbackAnimationVisibility
        lateinit var clock: RestClock
        lateinit var scroll: androidx.compose.foundation.ScrollState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val position = rememberScrollState()
            Box(Modifier.size(100.dp)) {
                Column(Modifier.verticalScroll(position)) {
                    val figureVisibility = rememberFeedbackAnimationVisibility()
                    val figureClock = rememberRestClock(figureVisibility.visible)
                    SideEffect {
                        visibility = figureVisibility
                        clock = figureClock
                        scroll = position
                    }
                    Box(Modifier.size(64.dp).then(figureVisibility.modifier))
                    Spacer(Modifier.height(400.dp))
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(160)
        rule.runOnIdle {
            assertTrue(visibility.visible)
            assertTrue("la figura visible tiene movimiento", clock.seconds > 0f)
            runBlocking { scroll.scrollTo(200) }
        }
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        var paused = 0f
        rule.runOnIdle {
            assertFalse("el recorte del scroll oculta la figura", visibility.visible)
            paused = clock.seconds
        }
        rule.mainClock.advanceTimeBy(160)
        rule.runOnIdle { assertEquals("fuera de pantalla no hay ticks", paused, clock.seconds, 0f) }
        rule.runOnIdle { runBlocking { scroll.scrollTo(0) } }
        // El layout publica de nuevo la visibilidad; después se recompone y arranca el efecto del reloj.
        // Avanzar de golpe antes de ese layout solo adelanta el reloj de la prueba, con el efecto aún parado.
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(160)
        rule.runOnIdle {
            assertTrue(visibility.visible)
            assertTrue("al volver continúa", clock.seconds > paused)
        }
    }
}
