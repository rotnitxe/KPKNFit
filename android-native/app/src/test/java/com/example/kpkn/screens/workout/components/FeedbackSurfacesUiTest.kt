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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.example.kpkn.screens.workout.LocalFeedbackDefer
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

    private val nbsp = " "
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
        onDefer: (() -> Unit)? = null,
    ) = content {
        CompositionLocalProvider(LocalFeedbackBounded provides true, LocalFeedbackDefer provides onDefer) {
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

    private val kneeInsight = com.example.kpkn.domain.discomfort.RecordedDiscomfortHistory.explain(
        "knee_patellar",
        com.example.kpkn.domain.discomfort.DiscomfortSession(listOf(
            com.example.kpkn.domain.discomfort.DiscomfortSessionExercise("sq","Sentadilla trasera",emptyList())),"sq"),
        com.example.kpkn.domain.discomfort.DiscomfortHistory(listOf(
            com.example.kpkn.data.models.WorkoutLog("reported","p","s","Original",java.time.Instant.ofEpochMilli(nowMs-3*day).toString(),17,
                postExerciseReports=listOf(com.example.kpkn.data.models.ExerciseDiscomfortReport(
                    exerciseId="sq",exerciseName="Sentadilla trasera",technicalQuality=5,discomfortIds=listOf("knee_patellar"))))),
            java.time.Instant.ofEpochMilli(nowMs)),
    )

    /** La fila «Molestias» del feedback abre la hoja «Molestias del ejercicio» (buscador, chips destacados y avisos del motor). */
    private fun openDiscomfortSheet() {
        rule.onNodeWithTag("rest-feedback-discomforts").performClick()
        rule.waitForIdle()
    }

    /** «Listo» cierra la hoja con una animación breve: se espera a que salga de la pantalla antes de seguir. */
    private fun closeDiscomfortSheet() {
        rule.onNodeWithText("Listo").performClick()
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("rest-discomforts-sheet").fetchSemanticsNodes().isEmpty() }
    }

    private fun openFromSearch(query: String, label: String) {
        openDiscomfortSheet()
        rule.onNode(hasSetTextAction()).performTextInput(query)
        rule.waitForIdle()
        // El último nodo es el de la hoja (se pinta encima): con una molestia elegida, la fila «Molestias» también la nombra.
        val matches = rule.onAllNodes(hasText(label) and hasClickAction())
        matches[matches.fetchSemanticsNodes().size - 1].performClick()
        rule.waitForIdle()
    }

    // ── B1: el panel de la pantalla completa ──

    @Test
    fun thePanelAsksHowItWentAndNoLongerAsksHowIntenseItWas() {
        panel()
        rule.onNodeWithText("EJERCICIO TERMINADO").assertExists()
        rule.onNodeWithText("Calidad técnica").assertExists()
        rule.onNodeWithTag("rest-feedback-discomforts").assertExists()
        rule.onNodeWithText("Guardar feedback").assertExists()
        // Sin planificar intensidad el panel de antes preguntaba esto; ahora el RIR/RPE se anota en cada serie.
        rule.onAllNodesWithText("Qué tan intenso fue", ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithText("Fallo").assertCountEquals(0)
        rule.onAllNodesWithText("Registrar feedback").assertCountEquals(0)
    }

    @Test
    fun theTechniqueStartsAtEightAndIsSavedFromTheSlider() {
        val saved = mutableListOf<List<PostExerciseFeedback>>()
        panel(onSave = { saved += it })
        rule.onNodeWithTag("rest-feedback-quality-sq").assertTextEquals("8/10")
        rule.onNodeWithContentDescription("Calidad técnica")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        rule.waitForIdle()
        rule.onNodeWithTag("rest-feedback-quality-sq").assertTextEquals("5/10")
        rule.onNodeWithText("Guardar feedback").performClick()
        val feedback = saved.single().single()
        assertEquals("sq", feedback.exerciseId)
        assertEquals(5, feedback.technicalQuality)
        assertEquals(listOf("none"), feedback.discomfortIds)
        assertEquals(null, feedback.perceivedIntensityRpe)
        assertEquals(false, feedback.perceivedFailure)
    }

    @Test
    fun highlightedChipsContainOnlyPreviouslyReportedZones() {
        panel(insights=FeedbackInsights(reported=listOf(ReportedDiscomfort("hip_front",lastReportedAtMs=nowMs-3*day))))
        openDiscomfortSheet()
        rule.onNode(hasText("Cadera anterior · la reportaste el",substring=true)).assertExists()
        rule.onAllNodesWithText("Frecuentes en",substring=true).assertCountEquals(0)
        rule.onAllNodesWithText("Rodilla anterior").assertCountEquals(0)
        rule.onAllNodesWithText("Lumbar").assertCountEquals(0)
    }

    @Test
    fun withoutAnythingFromTheEngineThereAreNoHighlightedChipsAndNoNotice() {
        panel()
        openDiscomfortSheet()
        rule.onAllNodesWithText("Frecuentes en", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Esta semana llevas", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Escribe para buscar la zona que molesta.").assertExists()
    }

    @Test
    fun reportedHistoryDoesNotCreateAPercentageOrLoadNotice() {
        panel(insights=FeedbackInsights(reported=listOf(ReportedDiscomfort("knee_patellar",lastReportedAtMs=nowMs-3*day))))
        openDiscomfortSheet()
        rule.onNode(hasText("Rodilla anterior · la reportaste el",substring=true)).assertExists()
        rule.onAllNodesWithText("35",substring=true).assertCountEquals(0)
        rule.onAllNodesWithText("Baja el volumen",substring=true).assertCountEquals(0)
    }

    @Test
    fun theFriendlySearchFindsTheKneeEvenWithATypo() {
        panel()
        openDiscomfortSheet()
        rule.onNode(hasSetTextAction()).performTextInput("rodila")
        rule.waitForIdle()
        rule.onNodeWithText("Rodilla anterior").assertExists()
        rule.onAllNodesWithText("Escribe para buscar la zona que molesta.").assertCountEquals(0)
    }

    @Test
    fun theSearchSaysSoWhenNothingMatches() {
        panel()
        openDiscomfortSheet()
        rule.onNode(hasSetTextAction()).performTextInput("zzzz")
        rule.waitForIdle()
        rule.onNode(hasText("No se encontraron resultados", substring = true)).assertExists()
    }

    @Test
    fun leaveForLaterShowsOnlyWhileTheRestRuns() {
        var deferred = 0
        panel(onDefer = { deferred++ })
        rule.onNodeWithText("Ahora no").performClick()
        assertEquals(1, deferred)
        rule.onNodeWithText("Guardar feedback").assertExists()
    }

    @Test
    fun withoutARestRunningThereIsNoLeaveForLater() {
        panel(onDefer = null)
        rule.onAllNodesWithText("Ahora no").assertCountEquals(0)
        rule.onNodeWithText("Guardar feedback").assertExists()
    }

    // ── B2: la ficha de la molestia, desde un chip ──

    @Test
    fun tappingAChipOpensTheSheetAndReportingAddsTheDiscomfortToTheFeedback() {
        val saved=mutableListOf<List<PostExerciseFeedback>>()
        panel(onSave={saved+=it})
        openFromSearch("rodilla","Rodilla anterior")
        rule.onNodeWithText("Molestia delante de la rodilla").assertExists()
        rule.onNodeWithText("CAUSAS COMUNES").assertExists()
        rule.onNodeWithText("QUÉ PUEDES HACER").assertExists()
        rule.onAllNodesWithText("REGISTROS ANTERIORES").assertCountEquals(0)
        rule.onNodeWithText(PROFESSIONAL_ADVICE).assertExists()
        rule.onAllNodesWithText("¿CUÁNTO MOLESTA?").assertCountEquals(0)
        rule.onNodeWithText("Reportar molestia").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Quitar").assertExists()
        // La hoja de molestias sigue abierta sobre el feedback: se cierra con «Listo» antes de guardar.
        closeDiscomfortSheet()
        rule.onNodeWithText("Guardar feedback").performClick()
        val feedback=saved.single().single()
        assertEquals(listOf("knee_patellar"),feedback.discomfortIds)
        assertEquals(listOf("knee_patellar"),feedback.stillPresentDiscomfortIds)
    }

    @Test
    fun theSheetOfAChosenDiscomfortOffersToTakeItOff() {
        val draft=PostExerciseFeedbackDraft.from(listOf(squat),emptyMap())
        draft.select("knee_patellar")
        panel(draft=draft)
        openFromSearch("rodilla","Rodilla anterior")
        rule.onNodeWithText("Quitar molestia").performClick()
        rule.waitForIdle()
        assertTrue(draft.discomfortIds.isEmpty())
    }

    @Test
    fun theSheetShowsOnlyActualPreviousReportsWithoutCausalOrLoadAdvice() {
        panel(explain={ id -> kneeInsight.takeIf { id=="knee_patellar" } })
        openFromSearch("rodilla","Rodilla anterior")
        rule.onNodeWithText("REGISTROS ANTERIORES").assertExists()
        rule.onNodeWithText("1 sesión anterior con esta molestia registrada para el mismo ejercicio.").assertExists()
        rule.onAllNodesWithText("EN TU CASO").assertCountEquals(0)
        rule.onAllNodesWithText("Esta semana").assertCountEquals(0)
        rule.onAllNodesWithText("Deja 2 repeticiones",substring=true).assertCountEquals(0)
        rule.onAllNodesWithText(PROFESSIONAL_ADVICE).assertCountEquals(1)
    }

    @Test
    fun withoutEvidenceTheSheetHasNoInYourCaseBlock() {
        panel()
        openFromSearch("lumbar","Lumbar")
        rule.onAllNodesWithText("REGISTROS ANTERIORES").assertCountEquals(0)
        rule.onNodeWithText(PROFESSIONAL_ADVICE).assertExists()
    }

    // ── B3: la tarjeta compacta y su paridad con la pantalla completa ──

    private fun card(
        draft: PostExerciseFeedbackDraft,
        save: () -> Unit,
        insights: FeedbackInsights = FeedbackInsights.Empty,
        onDefer: () -> Unit = {},
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
                onDefer = onDefer,
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
                onDefer = { skips++ },
                onExpand = { expands++ },
            ),
        )
        rule.onNodeWithText("2:41 · DESCANSO").assertExists()
        rule.onNodeWithText("Calidad técnica").assertExists()
        rule.onNodeWithTag("rest-feedback-discomforts").assertExists()
        rule.onNodeWithText("Guardar feedback").assertExists()
        rule.onNodeWithText("Ahora no").performClick()
        assertEquals(1, skips)
        rule.onNodeWithContentDescription("Expandir descanso").performClick()
        assertEquals(1, expands)
        // Lo reportado antes y lo frecuente van en la hoja «Molestias del ejercicio» (la tarjeta solo muestra la fila de molestias).
        openDiscomfortSheet()
        rule.onNodeWithText("Molestias del ejercicio").assertExists()
        rule.onNodeWithText("¿ALGUNA MOLESTIA?").assertExists()
        rule.onNode(hasText("Cadera anterior · ", substring = true)).assertExists()
        rule.onAllNodesWithText("Rodilla anterior").assertCountEquals(0)
        rule.onAllNodesWithText("Frecuentes en", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("35",substring=true).assertCountEquals(0)
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
        rule.onAllNodesWithTag("rest-feedback-quality-sq").assertCountEquals(2)
        rule.onAllNodesWithTag("rest-feedback-quality-sq")[0].assertTextEquals("4/10")
        rule.onAllNodesWithTag("rest-feedback-quality-sq")[1].assertTextEquals("4/10")
        // La fila «Molestias» muestra la zona corta de la molestia elegida (como el prototipo), en la tarjeta y en el panel.
        rule.onAllNodesWithText("Lumbar").assertCountEquals(2)
        // «Guardar» de la tarjeta y «Guardar» de la pantalla completa guardan exactamente lo mismo.
        val buttons = rule.onAllNodesWithText("Guardar feedback")
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
