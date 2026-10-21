package com.example.kpkn.screens.technique.analysis

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.technique.TechniqueSamples
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_BREAKDOWN_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_OBSERVATIONS_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_PLATE_CONFIRM_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_PLATE_OPEN_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_PROFILE_ANALYZE_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_REP_CHART_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_RETRY_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_STATUS_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_SUMMARY_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_ANALYZE_TAG
import com.example.kpkn.screens.technique.analysis.design.ANALYSIS_TIPS_TAG
import com.example.kpkn.screens.technique.analysis.design.AnalysisStatusModule
import com.example.kpkn.screens.technique.analysis.design.ObservationsModule
import com.example.kpkn.screens.technique.analysis.design.PlateCalibrationModule
import com.example.kpkn.screens.technique.analysis.design.RepBreakdownModule
import com.example.kpkn.screens.technique.analysis.design.analysisObservationSeeTag
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import com.example.kpkn.screens.technique.player.TechniquePlayerState
import com.example.kpkn.ui.design.KpknSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los módulos de «Análisis de técnica» y la pantalla entera por su semántica (VT7b): lo que lee TalkBack, que todo lo tocable mida al
 * menos 48 dp, que cada toque llegue a su lambda y que elegir una repetición, ver una observación o marcar el disco hagan lo que dice
 * el brief. Sin reproductor real: la pantalla recibe un hueco vacío en su lugar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h1800dp-xxhdpi")
class TechniqueAnalysisScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun content(block: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
                    Column(Modifier.padding(16.dp)) { block() }
                }
            }
        }
    }

    // ── Observaciones ─────────────────────────────────────────────────────────

    @Test
    fun every_see_button_is_big_enough_and_jumps_to_its_observation() {
        val items = listOf(
            ObservationUi("Primera observación", seekUs = 1_000_000L, repPosition = 2),
            ObservationUi("Segunda, sin nada que ver", seekUs = null, repPosition = -1),
            ObservationUi("Tercera observación", seekUs = null, repPosition = 4),
        )
        val seen = ArrayList<ObservationUi>()
        content { ObservationsModule(items = items, onSee = { seen += it }) }

        rule.onNodeWithText("Primera observación").assertIsDisplayed()
        rule.onNodeWithText("Segunda, sin nada que ver").assertIsDisplayed()
        rule.onNodeWithTag(analysisObservationSeeTag(1)).assertDoesNotExist()
        rule.onNodeWithTag(analysisObservationSeeTag(0)).assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(analysisObservationSeeTag(2)).performClick()
        assertEquals(listOf(items[0], items[2]), seen)
    }

    @Test
    fun without_observations_the_module_is_not_painted() {
        content { ObservationsModule(items = emptyList(), onSee = {}) }
        rule.onNodeWithTag(ANALYSIS_OBSERVATIONS_TAG).assertDoesNotExist()
    }

    // ── Por repetición ────────────────────────────────────────────────────────

    private val breakdown: RepBreakdownUi by lazy {
        val report = TechniqueSamples.analysisReport
        buildBreakdown(report, TechniqueOverlayData.from(null, null, report, 720, 1280).reps)!!
    }

    @Test
    fun tapping_and_dragging_the_chart_choose_repetitions() {
        val picks = ArrayList<Int>()
        content {
            RepBreakdownModule(breakdown, RepMetricKind.VELOCITY, onSelectMetric = {}, selectedRep = 2, onSelectRep = { picks += it })
        }
        val chart = rule.onNodeWithTag(ANALYSIS_REP_CHART_TAG)
        // Diez barras: un cuarto del ancho cae en la tercera (posición 2).
        chart.performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        chart.performTouchInput { click(Offset(width * 0.99f, height / 2f)) }
        chart.performTouchInput { click(Offset(width * 0.01f, height / 2f)) }
        assertEquals(listOf(2, 9, 0), picks)

        picks.clear()
        chart.performTouchInput { swipe(Offset(2f, height / 2f), Offset(width - 2f, height / 2f), 400L) }
        assertTrue(picks.toString(), picks.isNotEmpty())
        assertEquals(0, picks.first())
        assertEquals(9, picks.last())
        assertEquals("arrastrar solo avisa al cambiar de barra, siempre hacia delante", picks.sorted().distinct(), picks)
    }

    @Test
    fun the_chart_reads_as_a_summary_and_can_be_walked_without_touching_it() {
        val picks = ArrayList<Int>()
        content {
            RepBreakdownModule(breakdown, RepMetricKind.VELOCITY, onSelectMetric = {}, selectedRep = 4, onSelectRep = { picks += it })
        }
        val chart = rule.onNodeWithTag(ANALYSIS_REP_CHART_TAG)
        chart.assertContentDescriptionEquals(breakdown.metrics.first { it.kind == RepMetricKind.VELOCITY }.description)
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Repetición 5 de 10 elegida"))
        val actions = chart.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Repetición anterior", "Repetición siguiente"), actions.map { it.label })
        rule.runOnUiThread {
            actions.first { it.label == "Repetición siguiente" }.action()
            actions.first { it.label == "Repetición anterior" }.action()
        }
        assertEquals(listOf(5, 3), picks)
    }

    @Test
    fun the_segments_switch_the_measure_and_the_detail_follows_the_chosen_rep() {
        val metrics = ArrayList<RepMetricKind>()
        content {
            RepBreakdownModule(breakdown, RepMetricKind.TIME, onSelectMetric = { metrics += it }, selectedRep = 2, onSelectRep = {})
        }
        rule.onNodeWithText("Velocidad").performClick()
        rule.onNodeWithText("ROM").performClick()
        assertEquals(listOf(RepMetricKind.VELOCITY, RepMetricKind.ROM), metrics)
        rule.onNodeWithText("Rep 3 · parcial · bajada 3,0 s").assertIsDisplayed()
        // Con una parcial y una fallida, la leyenda las explica.
        rule.onNodeWithText("Parcial").assertIsDisplayed()
        rule.onNodeWithText("Fallida").assertIsDisplayed()
    }

    @Test
    fun without_a_chosen_rep_the_chart_invites_to_tap_and_a_single_measure_has_no_segments() {
        content {
            RepBreakdownModule(
                breakdown = breakdown.copy(metrics = breakdown.metrics.filter { it.kind == RepMetricKind.TIME }),
                selectedMetric = RepMetricKind.VELOCITY,
                onSelectMetric = {},
                selectedRep = -1,
                onSelectRep = {},
            )
        }
        rule.onNodeWithText("Toca una barra para ver esa repetición en el vídeo.").assertIsDisplayed()
        rule.onNodeWithText("Velocidad").assertDoesNotExist()
        rule.onNodeWithText("Tiempo").assertDoesNotExist()
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    @Test
    fun analyzing_shows_the_title_the_percentage_and_the_bar() {
        content { AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 42, updating = false, queued = false)) }
        rule.onNodeWithText("Analizando el vídeo…").assertIsDisplayed()
        rule.onNodeWithContentDescription("42 %").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertRangeInfoEquals(ProgressBarRangeInfo(0.42f, 0f..1f))
    }

    @Test
    fun an_engine_update_and_a_queued_job_have_their_own_words() {
        content {
            AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 70, updating = true, queued = false))
            AnalysisStatusModule(AnalysisStatusUi.Analyzing(progressPct = 0, updating = false, queued = true))
        }
        rule.onNodeWithText("Actualizando el análisis…").assertIsDisplayed()
        rule.onNodeWithText("En cola: empieza en cuanto el móvil esté libre.").assertIsDisplayed()
    }

    @Test
    fun the_profile_list_preselects_the_guess_and_only_analyzes_with_a_choice() {
        val chosen = ArrayList<AnalysisProfileId>()
        var confirmed = 0
        var toggled = 0
        content {
            AnalysisStatusModule(
                status = AnalysisStatusUi.NeedsProfile(AnalysisProfileId.ROW),
                selectedProfile = AnalysisProfileId.ROW,
                onSelectProfile = { chosen += it },
                onToggleProfiles = { toggled++ },
                onConfirmProfile = { confirmed++ },
            )
        }
        rule.onNodeWithText("¿Qué movimiento es?").assertIsDisplayed()
        rule.onNodeWithText("Remo").assertIsDisplayed()
        rule.onNodeWithText("Sentadilla").performClick()
        rule.onNodeWithText("Ver los 24 movimientos").performClick()
        rule.onNodeWithTag(ANALYSIS_PROFILE_ANALYZE_TAG).assertIsEnabled().performClick()
        assertEquals(listOf(AnalysisProfileId.SQUAT), chosen)
        assertEquals(1, toggled)
        assertEquals(1, confirmed)
    }

    @Test
    fun without_a_guess_or_a_choice_the_analyze_button_is_off() {
        content { AnalysisStatusModule(status = AnalysisStatusUi.NeedsProfile(null), selectedProfile = null) }
        rule.onNodeWithTag(ANALYSIS_PROFILE_ANALYZE_TAG).assertIsNotEnabled()
    }

    @Test
    fun failed_not_analyzed_and_missing_report_each_offer_their_action_and_done_paints_nothing() {
        var retried = 0
        var analyzed = 0
        content {
            AnalysisStatusModule(AnalysisStatusUi.Failed("El móvil se quedó sin memoria al analizarlo."), onRetry = { retried++ })
            AnalysisStatusModule(AnalysisStatusUi.NotAnalyzed, onAnalyze = { analyzed++ })
            AnalysisStatusModule(AnalysisStatusUi.Unsupported("Los ejercicios isométricos aún no se analizan"))
            AnalysisStatusModule(AnalysisStatusUi.Done)
        }
        rule.onNodeWithText("No pudimos analizar este vídeo").assertIsDisplayed()
        rule.onNodeWithText("El móvil se quedó sin memoria al analizarlo.").assertIsDisplayed()
        rule.onNodeWithText("Los ejercicios isométricos aún no se analizan").assertIsDisplayed()
        rule.onNodeWithTag(ANALYSIS_RETRY_TAG).performClick()
        rule.onNodeWithTag(ANALYSIS_ANALYZE_TAG).performClick()
        assertEquals(1, retried)
        assertEquals(1, analyzed)
    }

    // ── El disco ──────────────────────────────────────────────────────────────

    private fun plateModule(state: PlateModuleState, mps: Boolean = true, diameterMm: Int = 450) = PlateModuleUi(
        state = state,
        diameterMm = diameterMm,
        circle = if (state == PlateModuleState.NOT_FOUND) null else PlateSeed(2_700_000L, 300f, 520f, 55f, state == PlateModuleState.CONFIRMED),
        frameUs = 2_700_000L,
        frameWidth = 720,
        frameHeight = 1280,
        inMetersPerSecond = mps,
        warnings = listOf("El disco se ve ovalado: la cámara no estaba de frente a la barra."),
    )

    @Test
    fun a_found_disc_asks_for_its_diameter_and_confirms_or_adjusts() {
        val calls = ArrayList<String>()
        content {
            PlateCalibrationModule(
                plate = plateModule(PlateModuleState.SUGGESTED),
                frame = null,
                selectedDiameterMm = 450,
                onSelectDiameter = { calls += "diámetro:$it" },
                onPickOtherDiameter = { calls += "otro" },
                onConfirm = { calls += "confirmar" },
                onOpenCalibration = { calls += "ajustar" },
            )
        }
        rule.onNodeWithText("¿Es este el disco?").assertIsDisplayed()
        rule.onNodeWithText("El disco se ve ovalado: la cámara no estaba de frente a la barra.").assertIsDisplayed()
        rule.onNodeWithText("40").performClick()
        rule.onNodeWithText("35").performClick()
        rule.onNodeWithText("Otro").performClick()
        rule.onNodeWithTag(ANALYSIS_PLATE_CONFIRM_TAG).assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag(ANALYSIS_PLATE_OPEN_TAG).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("diámetro:400", "diámetro:350", "otro", "confirmar", "ajustar"), calls)
    }

    @Test
    fun a_custom_diameter_is_shown_on_the_other_chip() {
        content {
            PlateCalibrationModule(plateModule(PlateModuleState.SUGGESTED, diameterMm = 425), null, 425, {}, {}, {}, {})
        }
        rule.onNodeWithText("42,5 cm").assertIsDisplayed()
        rule.onNodeWithText("Otro").assertDoesNotExist()
    }

    @Test
    fun a_confirmed_disc_says_its_diameter_and_only_promises_speed_when_it_has_it() {
        content {
            PlateCalibrationModule(plateModule(PlateModuleState.CONFIRMED, mps = true), null, 450, {}, {}, {}, {})
        }
        rule.onNodeWithText("Disco de 45 cm").assertIsDisplayed()
        rule.onNodeWithText("Velocidad en m/s").assertIsDisplayed()
        rule.onNodeWithText("Cambiar").assertIsDisplayed()
    }

    @Test
    fun a_confirmed_disc_without_speed_does_not_promise_meters_per_second() {
        content {
            PlateCalibrationModule(plateModule(PlateModuleState.CONFIRMED, mps = false), null, 450, {}, {}, {}, {})
        }
        rule.onNodeWithText("Disco de 45 cm").assertIsDisplayed()
        rule.onNodeWithText("Velocidad en m/s").assertDoesNotExist()
        rule.onNodeWithText("Con este vídeo no se pudo medir la velocidad en m/s.").assertIsDisplayed()
    }

    @Test
    fun without_a_disc_it_offers_to_mark_it() {
        var opened = 0
        content {
            PlateCalibrationModule(plateModule(PlateModuleState.NOT_FOUND), null, 450, {}, {}, {}, { opened++ })
        }
        rule.onNodeWithText("No encontramos el disco").assertIsDisplayed()
        rule.onNodeWithText("Marcarlo").performClick()
        assertEquals(1, opened)
    }

    // ── La pantalla entera ────────────────────────────────────────────────────

    private class Calls {
        val trends = ArrayList<String>()
        val profiles = ArrayList<AnalysisProfileId>()
        var reanalyzed = 0
        val saved = ArrayList<Pair<PlateSeed, Int>>()
        val confirmed = ArrayList<Int>()
        var back = 0

        fun actions() = TechniqueAnalysisActions(
            onBack = { back++ },
            onOpenTrends = { trends += it },
            onReanalyze = { reanalyzed++ },
            onAnalyze = {},
            onChooseProfile = { profiles += it },
            onConfirmPlate = { confirmed += it },
            onSavePlate = { seed, mm -> saved += seed to mm },
            onDismissNotice = {},
            onRequestPlateFrame = {},
        )
    }

    private lateinit var player: TechniquePlayerState
    private val scroll = ScrollState(0)

    private fun screen(state: TechniqueAnalysisUiState, calls: Calls, frame: ImageBitmap? = null) {
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                player = remember { TechniquePlayerState(initialPositionUs = TechniqueSamples.analysisPositionUs, initialRep = -1) }
                SideEffect {
                    player.updateOverlay(state.overlay)
                    player.reportDurationMs(TechniqueSamples.analysisDurationUs / 1000L)
                }
                TechniqueAnalysisScreen(
                    state = state,
                    player = player,
                    plateFrame = frame,
                    actions = calls.actions(),
                    videoSlot = { modifier -> Box(modifier.size(10.dp)) },
                    scroll = scroll,
                    viewportHeight = 800.dp,
                    magnifierEnabled = false,
                )
            }
        }
    }

    @Test
    fun the_ready_screen_has_every_module_in_order_and_the_menu_offers_what_applies() {
        val calls = Calls()
        val state = TechniqueSamples.analysisUiState(
            report = TechniqueSamples.analysisReport.copy(flags = setOf(AnalysisFlag.PLATE_ELLIPSE)),
            usesPlate = true,
        )
        screen(state, calls)

        rule.onNodeWithText("Sentadilla trasera").assertIsDisplayed()
        rule.onNodeWithText("Serie 3 · 80 kg × 8 · 13 oct").assertIsDisplayed()
        for (tag in listOf(ANALYSIS_SUMMARY_TAG, ANALYSIS_BREAKDOWN_TAG, ANALYSIS_OBSERVATIONS_TAG, ANALYSIS_TIPS_TAG)) {
            rule.onNodeWithTag(tag).assertExists()
        }
        // Hecho el análisis, el módulo de estado no pinta nada.
        rule.onNodeWithTag(ANALYSIS_STATUS_TAG).assertDoesNotExist()

        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_MENU_TAG).performClick()
        rule.onNodeWithText("Reanalizar").assertIsDisplayed()
        // «Ver tendencia» está en el menú y en el pie de la pantalla; el menú se compone el último.
        rule.onAllNodesWithText("Ver tendencia").onLast().assertIsDisplayed()
        rule.onNodeWithText("Elegir el tipo de movimiento").assertDoesNotExist()
        rule.onAllNodesWithText("Ver tendencia").onLast().performClick()
        assertEquals(listOf("back_squat"), calls.trends)
        rule.onNodeWithText("Reanalizar").assertDoesNotExist()
    }

    @Test
    fun reanalyze_from_the_menu_and_trends_from_the_footer() {
        val calls = Calls()
        screen(TechniqueSamples.analysisUiState(), calls)
        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_MENU_TAG).performClick()
        rule.onNodeWithText("Reanalizar").performClick()
        assertEquals(1, calls.reanalyzed)

        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_TRENDS_TAG).performScrollTo().performClick()
        assertEquals(listOf("back_squat"), calls.trends)
    }

    @Test
    fun a_custom_exercise_can_change_its_movement_from_the_menu() {
        val calls = Calls()
        screen(TechniqueSamples.analysisUiState().copy(canChooseMovement = true), calls)
        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_MENU_TAG).performClick()
        rule.onNodeWithText("Elegir el tipo de movimiento").performClick()
        // Aparece la lista con el movimiento del análisis (sentadilla) preseleccionado.
        rule.onNodeWithText("¿Qué movimiento es?").assertIsDisplayed()
        rule.onNodeWithTag(ANALYSIS_PROFILE_ANALYZE_TAG).assertIsEnabled().performClick()
        assertEquals(listOf(AnalysisProfileId.SQUAT), calls.profiles)
    }

    @Test
    fun see_on_an_observation_chooses_its_rep_and_brings_the_video_back_into_view() {
        val calls = Calls()
        screen(TechniqueSamples.analysisUiState(), calls)
        rule.onNodeWithTag(analysisObservationSeeTag(0)).performScrollTo().performClick()
        rule.waitForIdle()
        // La 1.ª observación es de la repetición 9 (posición 8) y la pantalla sube hasta el reproductor.
        assertEquals(8, player.selectedRep)
        assertEquals(0, scroll.value)
    }

    @Test
    fun a_bar_of_the_chart_chooses_the_rep_in_the_player() {
        val calls = Calls()
        screen(TechniqueSamples.analysisUiState(), calls)
        val chart = rule.onNodeWithTag(ANALYSIS_REP_CHART_TAG).performScrollTo()
        chart.performTouchInput { click(Offset(width * 0.35f, height / 2f)) }
        assertEquals(3, player.selectedRep)
    }

    @Test
    fun while_analyzing_there_are_no_results_and_no_menu() {
        val calls = Calls()
        screen(TechniqueSamples.statusUiState(AnalysisStatusUi.Analyzing(42, updating = false, queued = false)), calls)
        rule.onNodeWithText("Analizando el vídeo…").assertIsDisplayed()
        rule.onNodeWithTag(ANALYSIS_SUMMARY_TAG).assertDoesNotExist()
        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_MENU_TAG).assertDoesNotExist()
        rule.onNodeWithTag(TECHNIQUE_ANALYSIS_TRENDS_TAG).assertDoesNotExist()
    }

    @Test
    fun a_notice_is_shown_and_can_be_closed() {
        val calls = Calls()
        var dismissed = 0
        val state = TechniqueSamples.analysisUiState().copy(notice = "No se pudo guardar el disco. Inténtalo de nuevo.")
        rule.setContent {
            CompositionLocalProvider(LocalWizardReducedMotion provides true) {
                val p = remember { TechniquePlayerState() }
                TechniqueAnalysisScreen(
                    state = state,
                    player = p,
                    plateFrame = null,
                    actions = TechniqueAnalysisActions(
                        {}, {}, {}, {}, {}, {}, { _, _ -> }, { dismissed++ }, {},
                    ),
                    videoSlot = { modifier -> Box(modifier.size(10.dp)) },
                    viewportHeight = 800.dp,
                )
            }
        }
        rule.onNodeWithText("No se pudo guardar el disco. Inténtalo de nuevo.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar el aviso").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, calls.back)
    }

    // ── El diálogo del disco, dentro de la pantalla ───────────────────────────

    /** Un fotograma liso del tamaño del análisis; `ImageBitmap(w, h)` no funciona con los gráficos heredados de Robolectric. */
    private fun blankFrame(): ImageBitmap =
        android.graphics.Bitmap.createBitmap(TechniqueSamples.VIDEO_WIDTH, TechniqueSamples.VIDEO_HEIGHT, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun suggestedState(): TechniqueAnalysisUiState = TechniqueSamples.analysisUiState(
        bar = TechniqueSamples.analysisPlate.copy(seed = TechniqueSamples.analysisSeed),
        usesPlate = true,
    )

    @Test
    fun confirming_the_found_disc_uses_the_chosen_diameter() {
        val calls = Calls()
        screen(suggestedState(), calls, frame = blankFrame())
        rule.onNodeWithText("40").performScrollTo().performClick()
        rule.onNodeWithTag(ANALYSIS_PLATE_CONFIRM_TAG).performScrollTo().performClick()
        assertEquals(listOf(400), calls.confirmed)
    }

    @Test
    fun marking_the_disc_returns_the_circle_in_analysis_pixels_with_the_chosen_diameter() {
        val calls = Calls()
        val state = suggestedState()
        val plate = state.results!!.plate!!
        val seed = plate.circle!!
        screen(state, calls, frame = blankFrame())

        rule.onNodeWithTag(ANALYSIS_PLATE_OPEN_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(PLATE_DIALOG_TAG).assertIsDisplayed()
        rule.onNodeWithText("Marca el disco").assertIsDisplayed()

        // «Otro» pide los centímetros en un campo propio y deja el diámetro en el chip (el del diálogo es el último «Otro»).
        rule.onAllNodesWithText("Otro").onLast().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("42,5")
        rule.onNodeWithTag(PLATE_OTHER_ACCEPT_TAG).performClick()
        rule.onNodeWithText("42,5 cm").assertIsDisplayed()

        rule.onNodeWithTag(PLATE_DIALOG_SAVE_TAG).performClick()
        assertEquals(1, calls.saved.size)
        val (saved, mm) = calls.saved.single()
        assertEquals(425, mm)
        assertTrue("al guardar queda confirmado por el usuario", saved.userConfirmed)
        assertEquals(plate.frameUs, saved.tUs)
        assertEquals(seed.cx, saved.cx, 0.05f)
        assertEquals(seed.cy, saved.cy, 0.05f)
        assertEquals(seed.r, saved.r, 0.05f)
        rule.onNodeWithTag(PLATE_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun an_invalid_custom_diameter_cannot_be_accepted_and_closing_saves_nothing() {
        val calls = Calls()
        screen(suggestedState(), calls, frame = blankFrame())
        rule.onNodeWithTag(ANALYSIS_PLATE_OPEN_TAG).performScrollTo().performClick()
        rule.onAllNodesWithText("Otro").onLast().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("5")
        rule.onNodeWithText("Entre 10 y 70 cm").assertIsDisplayed()
        rule.onNodeWithTag(PLATE_OTHER_ACCEPT_TAG).assertIsNotEnabled()
        assertEquals(null, parsePlateDiameterMm("hola"))
        assertEquals(425, parsePlateDiameterMm("42,5"))
        assertEquals(425, parsePlateDiameterMm(" 42.5 "))

        rule.onNode(hasSetTextAction()).performTextReplacement("70")
        rule.onNodeWithTag(PLATE_OTHER_ACCEPT_TAG).assertIsEnabled()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onNodeWithContentDescription("Cerrar sin guardar").performClick()
        rule.onNodeWithTag(PLATE_DIALOG_TAG).assertDoesNotExist()
        assertTrue(calls.saved.isEmpty())
        assertNotNull(player)
    }
}
