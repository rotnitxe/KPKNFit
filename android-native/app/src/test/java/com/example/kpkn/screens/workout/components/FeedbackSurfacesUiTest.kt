package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.domain.discomfort.DiscomfortEvidence
import com.example.kpkn.domain.discomfort.DiscomfortEvidenceKind
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.screens.workout.FeedbackInsights
import com.example.kpkn.screens.workout.LocalFeedbackBounded
import com.example.kpkn.screens.workout.LocalFeedbackRestSkip
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.screens.workout.PostExerciseFeedbackPanel
import com.example.kpkn.screens.workout.ReportedDiscomfort
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.feedbackSave
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Las superficies del feedback tras el ejercicio (LV6, B1–B3): el panel de la pantalla completa (F2), la tarjeta compacta (F4) y la ficha
 * de una molestia (F7). Comprueban lo que la persona ve y que las dos superficies comparten un solo borrador y guardan lo mismo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class FeedbackSurfacesUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val nbsp = "\u00A0"
    private val day = 24L * 60L * 60L * 1000L
    private val nowMs = 1_792_152_000_000L // 16-oct-2026

    /** Sin planificar intensidad: así el panel de antes preguntaba «Qué tan intenso fue». */
    private val squat = Exercise(
        id = "sq",
        name = "Sentadilla trasera",
        trainingMode = TrainingMode.REPS,
        sets = listOf(
            ExerciseSet(id = "sq-1", targetReps = 8, weight = 80.0, loadModeV2 = LoadModeV2.LOAD, unitModeV2 = UnitModeV2.REPS),
        ),
    )

    private fun content(body: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalRestClockFrozen provides 1.7f) { body() }
                }
            }
        }
    }

    private fun panel(
        onSave: (List<PostExerciseFeedback>) -> Unit = {},
        draft: PostExerciseFeedbackDraft? = null,
        insights: FeedbackInsights = FeedbackInsights.Empty,
        explain: suspend (String) -> DiscomfortInsight? = { null },
        onSkipRest: (() -> Unit)? = null,
    ) = content {
        CompositionLocalProvider(LocalFeedbackBounded provides true, LocalFeedbackRestSkip provides onSkipRest) {
            Box(Modifier.size(360.dp, 840.dp).background(Color.Black).padding(horizontal = 16.dp)) {
                PostExerciseFeedbackPanel(
                    feedbackExercises = listOf(squat),
                    postExerciseFeedbackByExerciseId = emptyMap(),
                    sessionAccentColor = Color(0xFFF49A6E),
                    canonicalKeyOf = { it.id },
                    onSave = onSave,
                    draft = draft,
                    insights = insights,
                    explainDiscomfort = explain,
                    nowMs = nowMs,
                )
            }
        }
    }

    private fun evidence(kind: DiscomfortEvidenceKind, text: String, advice: String) = DiscomfortEvidence(
        kind = kind,
        number = 35.0,
        text = text,
        headline = text,
        advice = advice,
        strength = 0.8,
    )

    private val kneeInsight = DiscomfortInsight(
        discomfortId = "knee_patellar",
        headline = "Esta semana llevas un 35${nbsp}% más de series de cuádriceps que tu media. Si la rodilla avisa, empieza por ahí.",
        today = listOf(
            evidence(DiscomfortEvidenceKind.REPORTED_HISTORY, "4 series pesadas de sentadilla", "Deja 2 repeticiones en reserva en las series pesadas."),
        ),
        week = listOf(
            evidence(
                DiscomfortEvidenceKind.REPORTED_HISTORY,
                "+35${nbsp}% de series de cuádriceps sobre tu media",
                "Baja el volumen de cuádriceps a tu media unos días.",
            ),
        ),
        advice = listOf(
            "Baja el volumen de cuádriceps a tu media unos días.",
            "Deja 2 repeticiones en reserva en las series pesadas.",
            PROFESSIONAL_ADVICE,
        ),
        persistent = false,
        strength = 0.8,
    )

    // ── B1: el panel de la pantalla completa ──

    @Test
    fun thePanelAsksHowItWentAndNoLongerAsksHowIntenseItWas() {
        panel()
        rule.onNodeWithText("¿Cómo te salió?").assertExists()
        rule.onNodeWithText("CALIDAD TÉCNICA").assertExists()
        rule.onNodeWithText("¿ALGUNA MOLESTIA?").assertExists()
        rule.onNodeWithText("Guardar").assertExists()
        // Sin planificar intensidad el panel de antes preguntaba esto; ahora el RIR/RPE se anota en cada serie.
        rule.onAllNodesWithText("Qué tan intenso fue", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Fallo").assertCountEquals(0)
        rule.onAllNodesWithText("Registrar feedback").assertCountEquals(0)
    }

    @Test
    fun theTechniqueStartsAtEightAndIsSavedFromTheSlider() {
        val saved = mutableListOf<List<PostExerciseFeedback>>()
        panel(onSave = { saved += it })
        rule.onNodeWithText("/ 10 · limpia").assertExists()
        rule.onNodeWithContentDescription("Calidad técnica")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.waitForIdle()
        rule.onNodeWithText("/ 10 · aceptable").assertExists()
        rule.onNodeWithText("Guardar").performClick()
        val feedback = saved.single().single()
        assertEquals("sq", feedback.exerciseId)
        assertEquals(5, feedback.technicalQuality)
        assertEquals(listOf("none"), feedback.discomfortIds)
        assertEquals(null, feedback.perceivedIntensityRpe)
        assertEquals(false, feedback.perceivedFailure)
    }

    @Test
    fun theHighlightedChipsListWhatWasReportedBeforeAndThenWhatIsFrequentInTheExercise() {
        panel(
            insights = FeedbackInsights(
                
                reported = listOf(ReportedDiscomfort("hip_front", lastReportedAtMs = nowMs - 3 * day)),
            ),
        )
        rule.onNode(hasText("Cadera anterior · la reportaste el", substring = true)).assertExists()
        rule.onNodeWithText("Frecuentes en Sentadilla trasera").assertExists()
        rule.onNodeWithText("Rodilla anterior").assertExists()
        rule.onNodeWithText("Lumbar").assertExists()
        val reportedTop = rule.onNode(hasText("Cadera anterior", substring = true)).getBoundsInRoot().top
        val frequentTop = rule.onNodeWithText("Frecuentes en Sentadilla trasera").getBoundsInRoot().top
        assertTrue("lo reportado antes va primero", reportedTop < frequentTop)
    }

    @Test
    fun withoutAnythingFromTheEngineThereAreNoHighlightedChipsAndNoNotice() {
        panel()
        rule.onAllNodesWithText("Frecuentes en", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Esta semana llevas", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Escribe para buscar la zona que molesta.").assertExists()
    }

    @Test
    fun theNoticeOfTheEngineShowsOnlyWhenThereIsEvidence() {
        panel(insights = FeedbackInsights())
        rule.onNodeWithText(kneeInsight.headline!!).assertExists()
    }

    @Test
    fun theFriendlySearchFindsTheKneeEvenWithATypo() {
        panel()
        rule.onNode(hasSetTextAction()).performTextInput("rodila")
        rule.waitForIdle()
        rule.onNodeWithText("Rodilla anterior").assertExists()
        rule.onAllNodesWithText("Escribe para buscar la zona que molesta.").assertCountEquals(0)
    }

    @Test
    fun theSearchSaysSoWhenNothingMatches() {
        panel()
        rule.onNode(hasSetTextAction()).performTextInput("zzzz")
        rule.waitForIdle()
        rule.onNode(hasText("No se encontraron resultados", substring = true)).assertExists()
    }

    @Test
    fun skipRestShowsOnlyWhileTheRestRuns() {
        var skips = 0
        panel(onSkipRest = { skips++ })
        rule.onNodeWithText("Saltar descanso").performClick()
        assertEquals(1, skips)
        rule.onNodeWithText("Guardar").assertExists()
    }

    @Test
    fun withoutARestRunningThereIsNoSkipRest() {
        panel(onSkipRest = null)
        rule.onAllNodesWithText("Saltar descanso").assertCountEquals(0)
        rule.onNodeWithText("Guardar").assertExists()
    }

    // ── B2: la ficha de la molestia, desde un chip ──

    @Test
    fun tappingAChipOpensTheSheetAndReportingAddsTheDiscomfortToTheFeedback() {
        val saved = mutableListOf<List<PostExerciseFeedback>>()
        panel(onSave = { saved += it }, insights = FeedbackInsights())
        rule.onNodeWithText("Rodilla anterior").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Molestia delante de la rodilla").assertExists()
        rule.onNodeWithText("CAUSAS COMUNES").assertExists()
        rule.onNodeWithText("QUÉ PUEDES HACER").assertExists()
        rule.onNodeWithText(PROFESSIONAL_ADVICE).assertExists()
        rule.onNodeWithText("Reportar molestia").assertExists()
        // La app no mide la gravedad: no hay selector.
        rule.onAllNodesWithText("¿CUÁNTO MOLESTA?").assertCountEquals(0)
        rule.onAllNodesWithText("Leve").assertCountEquals(0)
        rule.onNodeWithText("Reportar molestia").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Molestia delante de la rodilla").assertCountEquals(0)
        rule.onNodeWithContentDescription("Quitar").assertExists()
        rule.onNodeWithText("Guardar").performClick()
        val feedback = saved.single().single()
        assertEquals(listOf("knee_patellar"), feedback.discomfortIds)
        assertEquals(listOf("knee_patellar"), feedback.stillPresentDiscomfortIds)
    }

    @Test
    fun theSheetOfAChosenDiscomfortOffersToTakeItOff() {
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        draft.select("knee_patellar")
        panel(draft = draft, insights = FeedbackInsights())
        rule.onNode(hasText("Rodilla anterior") and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Quitar molestia").assertExists()
        rule.onNodeWithText("Quitar molestia").performClick()
        rule.waitForIdle()
        assertTrue(draft.discomfortIds.isEmpty())
    }

    @Test
    fun theSheetShowsInYourCaseOnlyWhatTheEngineProves() {
        panel(
            insights = FeedbackInsights(),
            explain = { id -> kneeInsight.takeIf { id == "knee_patellar" } },
        )
        rule.onNodeWithText("Rodilla anterior").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("EN TU CASO").assertExists()
        rule.onNodeWithText("Hoy").assertExists()
        rule.onNodeWithText("Esta semana").assertExists()
        rule.onNodeWithText("4 series pesadas de sentadilla").assertExists()
        rule.onNodeWithText("+35${nbsp}% de series de cuádriceps sobre tu media").assertExists()
        rule.onNodeWithText("Deja 2 repeticiones en reserva en las series pesadas.").assertExists()
        // El cierre común sale una sola vez aunque el motor también lo traiga.
        rule.onAllNodesWithText(PROFESSIONAL_ADVICE).assertCountEquals(1)
    }

    @Test
    fun withoutEvidenceTheSheetHasNoInYourCaseBlock() {
        panel(insights = FeedbackInsights())
        rule.onNodeWithText("Lumbar").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("EN TU CASO").assertCountEquals(0)
        rule.onNodeWithText(PROFESSIONAL_ADVICE).assertExists()
    }

    // ── B3: la tarjeta compacta y su paridad con la pantalla completa ──

    private fun card(
        draft: PostExerciseFeedbackDraft,
        save: () -> Unit,
        insights: FeedbackInsights = FeedbackInsights.Empty,
        onSkip: () -> Unit = {},
        onExpand: (() -> Unit)? = {},
    ): @Composable () -> Unit = {
        Box(Modifier.size(272.dp, 388.dp).background(Color.Black)) {
            RestFeedbackCard(
                remainingSeconds = 161,
                totalSeconds = 249,
                shape = RestClockShape.TOWER,
                feedbackExercises = listOf(squat),
                draft = draft,
                insights = insights,
                explainDiscomfort = { null },
                linked = emptyList(),
                onSave = save,
                onSkipRest = onSkip,
                onExpand = onExpand,
                clockActive = false,
                nowMs = nowMs,
            )
        }
    }

    @Test
    fun theCardShowsTheSameFeedbackInCompact() {
        var skips = 0
        var expands = 0
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        content(
            card(
                draft = draft,
                save = {},
                insights = FeedbackInsights(
                    
                    reported = listOf(ReportedDiscomfort("hip_front", lastReportedAtMs = nowMs - 3 * day)),
                    
                ),
                onSkip = { skips++ },
                onExpand = { expands++ },
            ),
        )
        rule.onNodeWithText("2:41").assertExists()
        rule.onNodeWithText("de 4:09").assertExists()
        rule.onNodeWithText("TÉCNICA").assertExists()
        rule.onNodeWithText("¿ALGUNA MOLESTIA?").assertExists()
        // Lo reportado antes y lo frecuente van en una sola fila, sin el rótulo «Frecuentes en…» (la tarjeta no tiene sitio).
        rule.onNode(hasText("Cadera anterior · ", substring = true)).assertExists()
        rule.onNodeWithText("Rodilla anterior").assertExists()
        rule.onAllNodesWithText("Frecuentes en", substring = true).assertCountEquals(0)
        rule.onNodeWithText(kneeInsight.headline!!).assertExists()
        rule.onNodeWithText("Guardar").assertExists()
        rule.onNodeWithText("Saltar").performClick()
        assertEquals(1, skips)
        rule.onNodeWithContentDescription("Expandir descanso").performClick()
        assertEquals(1, expands)
    }

    @Test
    fun theCardAndTheFullScreenShareOneDraftAndSaveTheSame() {
        val saved = mutableListOf<List<PostExerciseFeedback>>()
        val draft = PostExerciseFeedbackDraft.from(listOf(squat), emptyMap())
        val save = feedbackSave(
            feedbackExercises = listOf(squat),
            draft = draft,
            previousFeedback = emptyMap(),
            linked = emptyList(),
            canonicalKeyOf = { it.id },
            onSave = { saved += it },
        )
        content {
            Column {
                card(draft = draft, save = save).invoke()
                CompositionLocalProvider(LocalFeedbackBounded provides true) {
                    Box(Modifier.size(360.dp, 700.dp).background(Color.Black).padding(horizontal = 16.dp)) {
                        PostExerciseFeedbackPanel(
                            feedbackExercises = listOf(squat),
                            postExerciseFeedbackByExerciseId = emptyMap(),
                            sessionAccentColor = Color(0xFFF49A6E),
                            canonicalKeyOf = { it.id },
                            onSave = { saved += it },
                            draft = draft,
                            nowMs = nowMs,
                        )
                    }
                }
            }
        }
        // Lo que se cambia en la tarjeta se ve en el panel: es el mismo borrador.
        rule.onAllNodesWithContentDescription("Calidad técnica")[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        rule.waitForIdle()
        rule.runOnIdle { draft.select("lumbar") }
        rule.waitForIdle()
        assertEquals(4, draft.technical("sq"))
        rule.onAllNodesWithText("/ 10 · irregular").assertCountEquals(1)
        rule.onAllNodesWithText("4 / 10 · irregular").assertCountEquals(1)
        rule.onAllNodesWithText("Lumbar").assertCountEquals(2)
        // «Guardar» de la tarjeta y «Guardar» de la pantalla completa guardan exactamente lo mismo.
        val buttons = rule.onAllNodesWithText("Guardar")
        buttons[0].performClick()
        buttons[1].performClick()
        assertEquals(2, saved.size)
        assertEquals(saved[0], saved[1])
        val feedback = saved[0].single()
        assertEquals(4, feedback.technicalQuality)
        assertEquals(listOf("lumbar"), feedback.discomfortIds)
        assertNotNull(feedback.stillPresentDiscomfortIds)
    }
}
