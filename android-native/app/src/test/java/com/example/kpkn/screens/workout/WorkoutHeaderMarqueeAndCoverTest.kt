package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi")
class WorkoutHeaderMarqueeAndCoverTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nombreLargoUsaCarruselYCortoNoLógica() {
        // Un texto más ancho que el contenedor activa el carrusel continuo
        assertTrue(workoutHeaderShouldUseMarquee(textWidth = 500, containerWidth = 300, reducedMotion = false))
        // Un texto que cabe en el contenedor no activa el carrusel
        assertFalse(workoutHeaderShouldUseMarquee(textWidth = 200, containerWidth = 300, reducedMotion = false))
    }

    @Test
    fun conReducirMovimientoNoHayCarrusel() {
        // Con movimiento reducido nunca se activa el carrusel, aunque el texto sea muy ancho
        assertFalse(workoutHeaderShouldUseMarquee(textWidth = 600, containerWidth = 300, reducedMotion = true))

        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(3f, 1f),
                com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion provides true,
            ) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 120.dp)) {
                        WorkoutHeaderBar(
                            exerciseName = "Sentadilla Trasera con Barra Alta y Pausa Abajo en Cajón Profundo",
                            sessionName = "Pierna A",
                            groupName = null,
                            startTimeMs = 0L,
                            isComplete = false,
                            background = null,
                            sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {},
                            onHistoryClick = {},
                            voiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
                            onVoiceCaptureModeChange = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("workout_header_title_marquee").assertDoesNotExist()
    }

    @Test
    fun nombreLargoActivaMarqueeEnComposicion() {
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(3f, 1f),
                com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion provides false,
            ) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 120.dp)) {
                        WorkoutHeaderBar(
                            exerciseName = "Sentadilla Trasera con Barra Alta y Pausa Abajo en Cajón Profundo",
                            sessionName = "Pierna A",
                            groupName = null,
                            startTimeMs = 0L,
                            isComplete = false,
                            background = null,
                            sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {},
                            onHistoryClick = {},
                            voiceCaptureMode = com.example.kpkn.data.models.VoiceCaptureMode.HANDS_FREE,
                            onVoiceCaptureModeChange = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("workout_header_title_marquee").assertExists()
    }

    @Test
    fun nombreCortoNoActivaMarqueeEnComposicion() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 120.dp)) {
                        WorkoutHeaderBar(
                            exerciseName = "Curl",
                            sessionName = "Brazos",
                            groupName = null,
                            startTimeMs = 0L,
                            isComplete = false,
                            background = null,
                            sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("workout_header_title_marquee").assertDoesNotExist()
    }

    @Test
    fun sinPortadaNoSeDibujaFondo() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 120.dp)) {
                        WorkoutHeaderBar(
                            exerciseName = "Press Militar",
                            sessionName = "Hombro",
                            groupName = null,
                            startTimeMs = 0L,
                            isComplete = false,
                            background = null,
                            programCover = null,
                            sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("workout_header_cover_background").assertDoesNotExist()
    }

    @Test
    fun conPortadaSeDibujaFondo() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 120.dp)) {
                        WorkoutHeaderBar(
                            exerciseName = "Press Militar",
                            sessionName = "Hombro",
                            groupName = null,
                            startTimeMs = 0L,
                            isComplete = false,
                            background = null,
                            programCover = "kpkn-studio://PLATES/NOCHE",
                            sessionTimeRemainingSeconds = null,
                            onAdjustTimeLimit = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("workout_header_cover_background").assertExists()
    }
}
