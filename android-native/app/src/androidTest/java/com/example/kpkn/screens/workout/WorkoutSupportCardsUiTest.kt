package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.workout.components.WorkoutWarmupDisplaySet
import com.example.kpkn.screens.workout.components.WorkoutWarmupSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class WorkoutSupportCardsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun live_guidance_card_shows_apply_and_coach_action_labels() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutLiveGuidanceCard(
                    weightSuggestion = WeightSuggestion(
                        suggestedWeight = 82.5,
                        reason = "Sube un poco: la serie previa salio comoda.",
                    ),
                    autoRegulation = SetAutoRegulation(
                        exerciseId = "squat",
                        nextSetIdx = 2,
                        adjustmentFactor = 1.04,
                        adjustedWeight = 82.5,
                        reason = "Recuperacion estable y ejecucion limpia.",
                    ),
                    coachMessage = CoachMessage(
                        key = "coach-1",
                        title = "Momentum controlado",
                        body = "Mantienes margen para empujar una progresion corta sin romper tecnica.",
                        severity = CoachSeverity.SUCCESS,
                        action = CoachAction.STAY_THE_COURSE,
                    ),
                    currentWeightText = "80",
                    onApplySuggestedLoad = {},
                )
            }
        }

        composeRule.onNodeWithText("Ajuste en vivo").assertExists()
        composeRule.onNodeWithText("Carga sugerida").assertExists()
        composeRule.onNodeWithText("82.5 kg").assertExists()
        composeRule.onNodeWithText("Usar").assertExists()
        composeRule.onNodeWithText("Mantener rumbo").assertExists()
        composeRule.onNodeWithText("Momentum controlado").assertExists()
    }

    @Test
    fun continuity_card_shows_feedback_actions_and_next_step() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutContinuityCard(
                    state = WorkoutContinuityState(
                        phase = WorkoutContinuityPhase.NEXT_EXERCISE,
                        eyebrow = "Bloque 2 de 4",
                        title = "Sigue con Press inclinado",
                        body = "Cierras esta estacion y el flujo te manda directo al siguiente patron.",
                        progressLabel = "Ejercicio 3 de 7",
                        nextExerciseName = "Press inclinado",
                        nextSetLabel = "Serie 1",
                        accentHex = "#4A7A4F",
                        feedbackPrompt = "Deja feedback rapido de la estacion anterior antes de continuar.",
                    ),
                    onOpenFeedback = {},
                    onDismissFeedbackPrompt = {},
                )
            }
        }

        composeRule.onNodeWithText("Sigue con Press inclinado").assertExists()
        composeRule.onNodeWithText("Siguiente punto").assertExists()
        composeRule.onNodeWithText("Press inclinado").assertExists()
        composeRule.onNodeWithText("Serie 1").assertExists()
        composeRule.onNodeWithText("Luego").assertExists()
        composeRule.onNodeWithText("Abrir feedback").assertExists()
    }

    @Test
    fun set_transition_banner_shows_flow_chip_and_recalc_copy() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutSetTransitionBanner(
                    transitionTarget = WorkoutStageTransitionTarget(
                        exerciseId = "press",
                        order = 2,
                        label = "Press banca · Serie 3",
                    ),
                    pulseToken = System.currentTimeMillis(),
                )
            }
        }

        composeRule.onNodeWithText("Press banca · Serie 3").assertExists()
        composeRule.onNodeWithText("Carga futura recalculada y lista para registrar.").assertExists()
        composeRule.onNodeWithText("Autoajuste").assertExists()
    }

    @Test
    fun set_transition_banner_without_recalc_shows_flow_active_chip() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutSetTransitionBanner(
                    transitionTarget = WorkoutStageTransitionTarget(
                        exerciseId = "row",
                        order = 1,
                        label = "Remo sentado · Serie 2",
                    ),
                    pulseToken = null,
                )
            }
        }

        composeRule.onNodeWithText("Remo sentado · Serie 2").assertExists()
        composeRule.onNodeWithText("Cambio de serie dentro del ejercicio actual.").assertExists()
        composeRule.onNodeWithText("Flujo activo").assertExists()
    }

    @Test
    fun warmup_sheet_shows_anchor_and_completion_action() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutWarmupSheet(
                    exercise = Exercise(
                        id = "bench",
                        name = "Press banca",
                        warmupSets = listOf(
                            WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 50.0, targetReps = 10),
                            WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 75.0, targetReps = 4),
                        ),
                    ),
                    warmupSets = listOf(
                        WorkoutWarmupDisplaySet(percentage = 50.0, reps = 10, targetWeight = 40.0),
                        WorkoutWarmupDisplaySet(percentage = 75.0, reps = 4, targetWeight = 60.0),
                    ),
                    workingWeight = 80.0,
                    isCompleted = false,
                    onDismiss = {},
                    onMarkCompleted = {},
                )
            }
        }

        composeRule.onNodeWithText("Warm-up inteligente").assertExists()
                        composeRule.onNodeWithText("80 kg estimados para la serie efectiva").assertExists()
        composeRule.onNodeWithText("Aproximacion 1").assertExists()
        composeRule.onNodeWithText("Marcar warm-up listo").assertExists()
    }

    @Test
    fun quick_actions_sheet_exposes_history_warmup_and_skip_actions() {
        var historyTapped = false
        var skipTapped = false

        composeRule.setContent {
            MaterialTheme {
                WorkoutExerciseQuickActionsSheet(
                    exercise = Exercise(id = "row", name = "Remo pecho"),
                    canMoveUp = true,
                    canMoveDown = true,
                    hasWarmup = true,
                    onDismiss = {},
                    onGoToExercise = {},
                    onOpenWarmup = {},
                    onOpenHistory = { historyTapped = true },
                    onOpenTags = {},
                    onOpenSetup = {},
                    onOpenReplace = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onSkip = { skipTapped = true },
                )
            }
        }

        composeRule.onNodeWithText("Quick actions").assertExists()
        composeRule.onNodeWithText("Ver historial").performClick()
        composeRule.onNodeWithText("Warm-up").assertExists()
        composeRule.onNodeWithText("Omitir ejercicio").performClick()

        composeRule.runOnIdle {
            assertTrue(historyTapped)
            assertTrue(skipTapped)
        }
    }
}
