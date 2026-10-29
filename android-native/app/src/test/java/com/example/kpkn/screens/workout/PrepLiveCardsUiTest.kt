package com.example.kpkn.screens.workout

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.liveModeExercise
import com.example.kpkn.data.models.TrainingMode
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import com.example.kpkn.screens.workout.components.MobilityPhaseLiveCard
import com.example.kpkn.screens.workout.components.PrepLiveCardTag
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LV-C: las tarjetas de aproximación (H5) y movilidad (H6) en el marco real de la sesión en vivo. Cada control vive DENTRO de la
 * tarjeta de 312 dp de ancho y alto según `liveSessionCardHeightDp` (394 dp, o 430 dp con pantalla alta), a 880 y a 740 dp de alto y con la letra al 100 % y al 130 %.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class PrepLiveCardsUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View
    private var density = 1f

    // ---- Datos -------------------------------------------------------------------------------------------------------

    private fun warmupRow(index: Int, percent: Double, reps: Int, done: Boolean = false, kg: Double? = null, rest: Int? = null) =
        WarmupPhaseRow(
            exerciseId = "sent",
            exerciseBadge = null,
            index = index,
            warmup = WarmupSetDefinition(id = "w${index + 1}", percentageOfWorkingWeight = percent, targetReps = reps, restBetween = rest),
            suggestedWeightKg = 100.0 * percent,
            actualWeightKg = kg,
            isCompleted = done,
        )

    private fun threeRows() = listOf(warmupRow(0, 0.5, 8), warmupRow(1, 0.6, 6), warmupRow(2, 0.7, 4, rest = 90))

    private fun mobilityItems(count: Int = 3): List<WorkoutMobilityChecklistItem> {
        val base = listOf(
            MobilitySeries(id = "m1", name = "Gato-vaca", reps = "10", notes = "Respira con calma."),
            MobilitySeries(id = "m2", name = "Movilidad de cadera", durationSeconds = 30),
            MobilitySeries(id = "m3", name = "Bisagra con palo", reps = "8"),
        )
        val all = base + (4..count).map { MobilitySeries(id = "m$it", name = "Movimiento largo $it", durationSeconds = 45) }
        return all.take(count).map {
            WorkoutMobilityChecklistItem(exerciseId = "sent", exerciseName = "Sentadilla trasera", mobility = it, stepKey = it.id)
        }
    }

    /** Alto esperado de la tarjeta (dp) para un viewport de 360 dp de ancho y `height` dp de alto, calculado con la regla de producción. */
    private fun cardHeight(height: Int): Float = liveSessionCardHeightDp(360, height).value

    // ---- Escena: cabecera, relator, tarjeta y línea ---------------------------------------------------------------------

    private fun scene(height: Int, font: Float = 1f, card: @Composable () -> Unit) {
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            // El viewport emulado manda también sobre la configuración: la tarjeta decide su alto con `screenHeightDp`.
            val baseConfiguration = LocalConfiguration.current
            val viewportConfiguration = remember(baseConfiguration, height) {
                android.content.res.Configuration(baseConfiguration).apply { screenHeightDp = height }
            }
            CompositionLocalProvider(
                LocalConfiguration provides viewportConfiguration,
                LocalDensity provides Density(density, font),
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null,
                LocalLiveSessionCompactLayout provides (height < 800),
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("prep-scene")) {
                            var headerPx by remember { mutableIntStateOf(0) }
                            val localDensity = LocalDensity.current
                            val header = with(localDensity) { headerPx.toDp() }
                            val band = liveSessionRelatorBandHeight(height.toFloat(), header.value, cardHeight(height), 144f).dp
                            Box(
                                Modifier.align(Alignment.TopCenter).padding(top = 45.dp)
                                    .onSizeChanged { headerPx = it.height + with(localDensity) { 45.dp.roundToPx() } },
                            ) {
                                WorkoutHeaderBar(
                                    "Sentadilla trasera", sessionName = "Fuerza A", groupName = "Principal", startTimeMs = 0L,
                                    isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                                    onStoryClick = {}, onToggleVoice = {}, onMoreClick = {}, compactLayout = height < 800,
                                    statusBarInsets = WindowInsets(0, 0, 0, 0),
                                )
                            }
                            WorkoutLiveRelatorLine(
                                "Prepara la carga antes de tus series efectivas.", "prep", reduceMotionOverride = true,
                                modifier = Modifier.align(Alignment.TopCenter).offset(y = header).height(band).testTag("prep-relator"),
                            )
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LiveSessionLineHeight + 44.dp).width(312.dp)) {
                                LiveSessionCardFrame {
                                    Box(Modifier.fillMaxSize()) { card() }
                                }
                            }
                            LiveSessionLine(
                                listOf(liveModeExercise(TrainingMode.RM)), "mode-exercise", liveLineTestSnapshot(4, preparation = true), {}, {}, {}, {}, {}, true, true,
                                onAddSet = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun nodeOfTag(tag: String): SemanticsNode = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun nodeOfDescription(text: String): SemanticsNode =
        rule.onNode(hasContentDescription(text), useUnmergedTree = false).assertIsDisplayed().fetchSemanticsNode()
    private fun boundsOfDescription(text: String): Rect = nodeOfDescription(text).boundsInRoot
    private fun boundsOfText(text: String): Rect =
        rule.onNode(hasText(text), useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot

    private fun assertInside(card: Rect, name: String, b: Rect) {
        val tolerance = 1.5f
        assertTrue(
            "$name queda dentro de la tarjeta: $b frente a $card",
            b.left >= card.left - tolerance && b.right <= card.right + tolerance &&
                b.top >= card.top - tolerance && b.bottom <= card.bottom + tolerance,
        )
    }

    /** Zona que realmente recibe el toque: el visual puede ser menor, Compose añade hasta los 48 dp mínimos alrededor. */
    private fun assertTouch(name: String, node: SemanticsNode) {
        val b = node.touchBoundsInRoot
        assertTrue("$name mide al menos 48 dp de ancho táctil (${b.width / density})", b.width / density >= 47.5f)
        assertTrue("$name mide al menos 48 dp de alto táctil (${b.height / density})", b.height / density >= 47.5f)
    }

    /** El mensaje de estado ya no es una línea visible: se anuncia como estado de la zona, con región viva cortés. */
    private fun assertStatus(tag: String, expected: String) {
        rule.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private fun assertNoStatus(tag: String) {
        rule.onNodeWithTag(tag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    /** La tarjeta mide 312 dp de ancho y lo que dice `liveSessionCardHeightDp` para el viewport (394 dp, o 430 dp con pantalla alta), llena el marco y no hay nada pulsable entre el relator y ella ni entre ella y la línea. */
    private fun assertFrame(height: Int): Rect {
        val frame = bounds("live-card-frame")
        val card = bounds(PrepLiveCardTag)
        println("LV-C marco a $height dp: frame=$frame card=$card density=$density")
        assertEquals("ancho de la tarjeta a $height", 312f, frame.width / density, .5f)
        assertEquals("alto de la tarjeta a $height", cardHeight(height), frame.height / density, .5f)
        assertEquals(frame.width, card.width, 1.5f)
        assertEquals(frame.height, card.height, 1.5f)
        assertEquals(frame.left, card.left, 1.5f)
        assertEquals(frame.top, card.top, 1.5f)
        // Posición: 24 dp de margen lateral y 156 dp hasta el borde inferior (la línea de 112 dp más 12 dp de separación).
        assertEquals(24f, frame.left / density, .5f)
        val line = bounds("live-session-line")
        assertEquals(12f, (line.top - frame.bottom) / density, .5f)
        val relator = bounds("prep-relator")
        assertTrue("El relator termina antes de la tarjeta", relator.bottom <= frame.top + 1f)
        // Nada pulsable en los huecos.
        rule.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().forEach { n ->
            val b = n.boundsInRoot
            val inGapAbove = b.top >= relator.bottom - 1f && b.bottom <= frame.top + 1f && b.height > 0f
            val inGapBelow = b.top >= frame.bottom - 1f && b.bottom <= line.top + 1f && b.height > 0f
            assertFalse("Control entre el relator y la tarjeta: $b", inGapAbove && b.height / density > 4f)
            assertFalse("Control entre la tarjeta y la línea: $b", inGapBelow && b.height / density > 4f)
        }
        return card
    }

    // ---- H5 · aproximación ------------------------------------------------------------------------------------------

    private class WarmupHarness(initial: List<WarmupPhaseRow>) {
        val rows: SnapshotStateList<WarmupPhaseRow> = mutableStateListOf<WarmupPhaseRow>().apply { addAll(initial) }
        var restSeconds by mutableIntStateOf(-1)
        var recorded: Pair<Int, Double>? = null
        var toggles = 0
        var skips = 0
        var continues = 0
        var restSkips = 0
        var adds = 0
        var drafts = mutableListOf<String>()
        var deferCommit = false
        val holder = RecordActionHolder()
        val fab = RecordFabHolder()
        var active by mutableStateOf(true)
        val scope = Any()
    }

    private fun showWarmup(h: WarmupHarness, height: Int = 880, font: Float = 1f, effectiveSets: Int = 0, working: Double? = 100.0) {
        scene(height, font) {
            WarmupPhaseLiveCard(
                rows = h.rows.toList(),
                sessionAccentColor = Color(0xFFF49A6E),
                onToggleComplete = { row, completed, kg ->
                    h.toggles++
                    if (completed) h.recorded = row.index to kg
                    val at = h.rows.indexOfFirst { it.warmup.id == row.warmup.id }
                    if (!h.deferCommit) h.rows[at] = row.copy(isCompleted = completed, actualWeightKg = if (completed) kg else null)
                },
                onSkip = {
                    h.skips++
                    h.rows.indices.forEach { h.rows[it] = h.rows[it].copy(isCompleted = true) }
                },
                onContinue = { h.continues++ },
                modifier = Modifier.fillMaxSize(),
                onAddSet = {
                    h.adds++
                    val last = h.rows.last().warmup
                    val pct = minOf(0.9, last.percentageOfWorkingWeight + 0.15)
                    h.rows += warmupRow(h.rows.size, pct, maxOf(1, last.targetReps - 1), rest = 60)
                },
                inlineRestRemainingSeconds = h.restSeconds.takeIf { it >= 0 },
                inlineRestTotalSeconds = if (h.restSeconds >= 0) 60 else null,
                onSkipInlineRest = { h.restSkips++; h.restSeconds = -1 },
                recordActionHolder = h.holder,
                recordActionScopeOwner = h.scope,
                recordActionPageKey = "warmup-page",
                recordFabHolder = h.fab,
                isActivePage = h.active,
                onWeightDraft = { _, text -> h.drafts += text },
                workingWeightKg = working,
                effectiveSetCount = effectiveSets,
            )
        }
    }

    /** El botón principal del roadmap: lo que hace al pulsarlo y lo que dice (la tarjeta ya no tiene botones de registrar/avanzar). */
    private fun roadmapPress(holder: RecordActionHolder) {
        rule.runOnIdle { holder.action?.invoke() }
        rule.waitForIdle()
    }

    private fun assertRoadmapLabel(fab: RecordFabHolder, expected: String) {
        rule.waitForIdle()
        assertEquals(expected, fab.label)
    }

    /** Los botones de registrar/avanzar propios de las tarjetas de preparación ya no existen: solo queda «Omitir» mientras la fase sigue viva. */
    private fun assertNoOwnRecordButtons() {
        rule.onNodeWithTag("prep-go-working").assertDoesNotExist()
        rule.onNodeWithTag("prep-continue").assertDoesNotExist()
        rule.onNodeWithTag("mob-done").assertDoesNotExist()
        rule.onNodeWithContentDescription("Marcar hecho y avanzar al siguiente movimiento").assertDoesNotExist()
        rule.onNodeWithText("Ir a efectivas").assertDoesNotExist()
        rule.onNodeWithText("Continuar").assertDoesNotExist()
        rule.onNodeWithText("Ir a las series").assertDoesNotExist()
        rule.onNodeWithText("Ir a la aproximación").assertDoesNotExist()
    }

    private fun assertWarmupControlsInside(height: Int, h: WarmupHarness) {
        val card = assertFrame(height)
        val add = nodeOfDescription("Añadir aproximación")
        assertInside(card, "Añadir aproximación", add.boundsInRoot); assertTouch("Añadir aproximación", add)
        val skip = nodeOfTag("prep-skip"); assertInside(card, "Omitir", skip.boundsInRoot); assertTouch("Omitir", skip)
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
        assertInside(card, "lista", bounds("warmup-list"))
        assertInside(card, "referencia de trabajo", bounds("warmup-anchor"))
        assertInside(card, "Ajustar", bounds("warmup-adjust"))
        (1..3).forEach { n ->
            val check = nodeOfDescription("Completar aproximación A$n")
            assertInside(card, "marca A$n", check.boundsInRoot); assertTouch("marca A$n", check)
        }
        rule.onNodeWithText("APROXIMACIONES").assertIsDisplayed()
        rule.onNodeWithText("Acércate a tu carga.").assertIsDisplayed()
        rule.onNodeWithTag("warmup-anchor").assertTextEquals("Trabajo · 100 kg")
    }

    /** El descanso corto sustituye el contenido de la tarjeta: reloj, siguiente aproximación y «Terminar descanso» en el pie. */
    private fun assertRestInside(height: Int, kicker: String, time: String) {
        val card = assertFrame(height)
        val end = nodeOfTag("prep-rest-skip"); assertInside(card, "Terminar descanso", end.boundsInRoot); assertTouch("Terminar descanso", end)
        assertInside(card, "reloj del descanso", bounds("rest-eclipse"))
        assertInside(card, "siguiente", bounds("rest-next"))
        rule.onNodeWithTag("rest-kicker", useUnmergedTree = true).assertTextEquals(kicker)
        rule.onNodeWithTag("rest-eclipse-clock", useUnmergedTree = true).assertTextEquals(time)
    }

    private fun setWarmupKg(index: Int, percent: String, reps: Int, text: String) {
        rule.onNodeWithTag("warmup-adjust").performClick()
        rule.onNode(hasContentDescription("A${index + 1} · $percent · × $reps · kg") and hasSetTextAction()).performTextReplacement(text)
        rule.onNodeWithText("Listo").performClick()
        rule.waitForIdle()
    }

    @Test fun h5ControlsStayInsideTheCardAt880() { val h = WarmupHarness(threeRows()); showWarmup(h); assertWarmupControlsInside(880, h) }
    @Test fun h5ControlsStayInsideTheCardAt740() { val h = WarmupHarness(threeRows()); showWarmup(h, 740); assertWarmupControlsInside(740, h) }
    @Test fun h5ControlsStayInsideTheCardAt880WithLargeFont() { val h = WarmupHarness(threeRows()); showWarmup(h, 880, 1.3f); assertWarmupControlsInside(880, h) }
    @Test fun h5ControlsStayInsideTheCardAt740WithLargeFont() { val h = WarmupHarness(threeRows()); showWarmup(h, 740, 1.3f); assertWarmupControlsInside(740, h) }

    @Test fun h5ShortRestStaysInsideAt740() {
        val h = WarmupHarness(threeRows()); h.restSeconds = 45
        showWarmup(h, 740)
        assertRestInside(740, "DESCANSO · APROX.", "0:45")
        rule.onNodeWithText("Aproximación A1").assertIsDisplayed()
        rule.onNodeWithText("50 kg × 8").assertIsDisplayed()
        rule.onNodeWithTag("prep-rest-skip").performClick()
        assertEquals(1, h.restSkips)
        rule.onNodeWithTag("rest-eclipse", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("warmup-list").assertIsDisplayed()
    }

    @Test fun h5ShortRestStaysInsideAt880WithLargeFont() {
        val h = WarmupHarness(threeRows()); h.restSeconds = 45
        showWarmup(h, 880, 1.3f)
        assertRestInside(880, "DESCANSO · APROX.", "0:45")
    }

    @Test fun h5KgAcceptsACommaAndTheMessageSaysSo() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        // Los kg de cada aproximación se ajustan en la hoja «Carga de las aproximaciones», con coma decimal.
        setWarmupKg(0, "50 %", 8, "62,5")
        assertEquals("62,5", h.drafts.last())
        rule.onNodeWithContentDescription("Completar aproximación A1").performClick()
        rule.waitForIdle()
        assertEquals(0 to 62.5, h.recorded)
        assertStatus("warmup-list", "Aproximación 1 hecha · 62,5 kg × 8")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("1 / 3")
        // Desmarcar la devuelve a pendiente.
        rule.onNodeWithContentDescription("Desmarcar aproximación A1").performClick()
        assertStatus("warmup-list", "Aproximación pendiente")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0 / 3")
    }

    @Test fun h5WaitsForCommittedRowsBeforeClaimingCompletion() {
        val h = WarmupHarness(threeRows()).apply { deferCommit = true }
        showWarmup(h)
        setWarmupKg(0, "50 %", 8, "62,5")
        rule.onNodeWithContentDescription("Completar aproximación A1").performClick()
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0 / 3")
        // Sin filas confirmadas no hay mensaje de hecha.
        assertNoStatus("warmup-list")
        rule.runOnIdle { h.rows[0] = h.rows[0].copy(isCompleted = true, actualWeightKg = 62.5) }
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("1 / 3")
        assertStatus("warmup-list", "Aproximación 1 hecha · 62,5 kg × 8")
    }

    @Test fun h5ShowsRealValuesPercentAndReps() {
        showWarmup(WarmupHarness(threeRows()))
        rule.onNodeWithText("A1").assertIsDisplayed()
        rule.onNodeWithText("50 %").assertIsDisplayed()
        rule.onNodeWithText("50 kg × 8").assertIsDisplayed()
        rule.onNodeWithText("A3").assertIsDisplayed()
        rule.onNodeWithText("70 %").assertIsDisplayed()
        rule.onNodeWithText("70 kg × 4").assertIsDisplayed()
    }

    @Test fun h5AddAppendsARowAndReportsIt() {
        val h = WarmupHarness(threeRows())
        showWarmup(h, effectiveSets = 0)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        assertEquals(1, h.adds)
        assertEquals(4, h.rows.size)
        assertStatus("warmup-list", "Aproximación añadida")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0 / 4")
    }

    @Test fun h5AddingBeyondTheRecommendedMaxUsesTheWarmupRulesText() {
        // Con 1 serie efectiva el máximo recomendado es max(4, 2 × 1) = 4: la quinta aproximación lo supera.
        val h = WarmupHarness(threeRows() + warmupRow(3, 0.8, 3))
        showWarmup(h, effectiveSets = 1)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        assertStatus("warmup-list", "Demasiadas aproximaciones: máximo recomendado 4 para 1 series efectivas.")
    }

    @Test fun h5AddingAtEightyFivePercentOrMoreWarnsLikeTheEditor() {
        val h = WarmupHarness(listOf(warmupRow(0, 0.5, 8), warmupRow(1, 0.6, 6), warmupRow(2, 0.8, 4)))
        showWarmup(h, effectiveSets = 3)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        // 80 % + 15 puntos, con tope de 90 %: la nueva queda por encima del 85 %.
        assertStatus("warmup-list", "Una aproximación al 85% o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.")
    }

    @Test fun h5SkipPhaseStopsAndShowsTheOmittedPage() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        rule.onNodeWithTag("prep-skip").performClick()
        rule.waitForIdle()
        assertEquals(1, h.skips)
        rule.onNodeWithText("Fase omitida").assertIsDisplayed()
        rule.onNodeWithText("Las series efectivas siguen pendientes.").assertIsDisplayed()
        assertStatus("warmup-skipped-pane", "Fase de aproximación omitida")
        // Omitida no hay pie ni botón propio: seguir a las series es el botón del roadmap.
        rule.onNodeWithTag("prep-skip").assertDoesNotExist()
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Ir a las series")
        assertEquals(0, h.continues)
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h5MarkingEverythingOffersContinueInTheRoadmapAndHidesSkip() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
        (1..3).forEach { rule.onNodeWithContentDescription("Completar aproximación A$it").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("prep-skip").assertDoesNotExist()
        assertStatus("warmup-list", "Aproximación 3 hecha · 70 kg × 4")
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Ir a las series")
        assertEquals(0, h.continues)
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h5RoadmapLabelFollowsTheFirstPendingRow() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
        rule.onNodeWithContentDescription("Completar aproximación A1").performClick()
        assertRoadmapLabel(h.fab, "Registrar aproximación A2")
        // Marcar A3 antes que A2 no cambia la primera pendiente.
        rule.onNodeWithContentDescription("Completar aproximación A3").performClick()
        assertRoadmapLabel(h.fab, "Registrar aproximación A2")
        rule.onNodeWithContentDescription("Completar aproximación A2").performClick()
        assertRoadmapLabel(h.fab, "Ir a las series")
    }

    @Test fun h5RoadmapLabelIsClearedWhenThePageIsNotActive() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
        rule.runOnIdle { h.active = false }
        rule.waitForIdle()
        assertEquals(null, h.fab.label)
        assertEquals(null, h.holder.action)
        rule.runOnIdle { h.active = true }
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
    }

    @Test fun h5LongListPagesInsideTheCard() {
        // La lista muestra tres filas por página y se recorre con la paginación del pie, siempre dentro de la tarjeta.
        val rows = (0 until 12).map { warmupRow(it, 0.3 + it * 0.05, 5) }
        val h = WarmupHarness(rows)
        showWarmup(h, 740)
        val card = assertFrame(740)
        val list = bounds("warmup-list")
        assertInside(card, "lista", list)
        rule.onNodeWithContentDescription("Completar aproximación A12").assertDoesNotExist()
        rule.onNodeWithText("1/4").assertIsDisplayed()
        val next = nodeOfDescription("Página siguiente")
        assertInside(card, "Página siguiente", next.boundsInRoot); assertTouch("Página siguiente", next)
        repeat(3) { rule.onNodeWithContentDescription("Página siguiente").performClick() }
        rule.onNodeWithText("4/4").assertIsDisplayed()
        val last = boundsOfDescription("Completar aproximación A12")
        assertTrue("La última fila queda dentro de la lista en su página: $last frente a $list",
            last.top >= list.top - 1.5f && last.bottom <= list.bottom + 1.5f)
        // El pie y la paginación no se mueven con la lista.
        assertInside(card, "Omitir", bounds("prep-skip"))
        assertInside(card, "Página anterior", boundsOfDescription("Página anterior"))
    }

    @Test fun h5RoadmapActionMarksTheNextRow() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        assertRoadmapLabel(h.fab, "Registrar aproximación A1")
        roadmapPress(h.holder)
        assertEquals(0 to 50.0, h.recorded)
        assertTrue(h.rows[0].isCompleted)
        assertRoadmapLabel(h.fab, "Registrar aproximación A2")
    }

    // ---- H6 · movilidad -------------------------------------------------------------------------------------------------

    private class MobilityHarness(val items: List<WorkoutMobilityChecklistItem>) {
        var completed by mutableStateOf(emptySet<String>())
        var globalRemaining by mutableIntStateOf(300)
        var globalRunning by mutableStateOf(false)
        var toggles = mutableListOf<Pair<String, Boolean>>()
        var starts = 0
        var pauses = 0
        var added = mutableListOf<Int>()
        var resets = 0
        var skips = 0
        var continues = 0
        var restSeconds by mutableIntStateOf(-1)
        val holder = RecordActionHolder()
        val fab = RecordFabHolder()
        var active by mutableStateOf(true)
        val scope = Any()
    }

    private fun showMobility(h: MobilityHarness, height: Int = 880, font: Float = 1f, nextIsWarmup: Boolean = true) {
        scene(height, font) {
            MobilityPhaseLiveCard(
                items = h.items,
                completedStepKeys = h.completed,
                remainingSeconds = h.globalRemaining,
                totalMinutes = 5,
                isTimerRunning = h.globalRunning,
                sessionAccentColor = Color(0xFFF49A6E),
                onToggleComplete = { item, done ->
                    h.toggles += item.stepKey to done
                    h.completed = if (done) h.completed + item.stepKey else h.completed - item.stepKey
                },
                onStartTimer = { h.starts++; h.globalRunning = true },
                onPauseTimer = { h.pauses++; h.globalRunning = false },
                onAddTimerSeconds = { h.added += it; h.globalRemaining += it },
                onResetTimer = { h.resets++; h.globalRemaining = 300; h.globalRunning = false },
                onSkip = { h.skips++; h.completed = h.items.map { it.stepKey }.toSet() },
                onContinue = { h.continues++ },
                modifier = Modifier.fillMaxSize(),
                inlineRestRemainingSeconds = h.restSeconds.takeIf { it >= 0 },
                inlineRestTotalSeconds = if (h.restSeconds >= 0) 60 else null,
                onSkipInlineRest = { h.restSeconds = -1 },
                recordActionHolder = h.holder,
                recordActionScopeOwner = h.scope,
                recordActionPageKey = "mobility-page",
                recordFabHolder = h.fab,
                isActivePage = h.active,
                nextIsWarmup = nextIsWarmup,
            )
        }
    }


    /** Pestañas de la tarjeta: Checklist (la que abre), Movimiento (0) y Articulaciones (2). */
    private fun openMovement() { rule.onNodeWithTag("mob-view-0").performClick() }

    /** La guía de un movimiento (con su reloj, si es de tiempo) vive en su hoja; se abre desde el «i» de la fila del Checklist. */
    private fun openGuide(name: String) {
        rule.onNodeWithContentDescription("Detalle de $name").performClick()
        rule.waitForIdle()
    }

    private fun assertMobilityControlsInside(height: Int, h: MobilityHarness) {
        val card = assertFrame(height)
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Registrar movimiento 1")
        val skip = nodeOfTag("prep-skip"); assertInside(card, "Omitir", skip.boundsInRoot); assertTouch("Omitir", skip)
        (0..2).forEach { assertInside(card, "pestaña $it", bounds("mob-view-$it")) }
        assertInside(card, "mensajes", bounds("mob-status"))
        assertInside(card, "lista", bounds("mob-list-pane"))
        rule.onNodeWithText("MOVILIDAD").assertIsDisplayed()
        rule.onNodeWithText("Prepara el movimiento.").assertIsDisplayed()
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("0 / 3")
        openMovement()
        assertInside(card, "panel del movimiento", bounds("mob-movement-pane"))
        val guide = nodeOfTag("mob-open-guide"); assertInside(card, "Ver guía", guide.boundsInRoot); assertTouch("Ver guía", guide)
    }

    /** El descanso corto sustituye el contenido de la tarjeta de movilidad. */
    private fun assertMobilityRestInside(height: Int) = assertRestInside(height, "DESCANSO · MOVILIDAD", "0:20")

    @Test fun h6RepsMovementControlsStayInsideAt880() { val h = MobilityHarness(mobilityItems()); showMobility(h); assertMobilityControlsInside(880, h) }
    @Test fun h6RepsMovementControlsStayInsideAt740() { val h = MobilityHarness(mobilityItems()); showMobility(h, 740); assertMobilityControlsInside(740, h) }
    @Test fun h6RepsMovementControlsStayInsideAt880WithLargeFont() { val h = MobilityHarness(mobilityItems()); showMobility(h, 880, 1.3f); assertMobilityControlsInside(880, h) }
    @Test fun h6RepsMovementControlsStayInsideAt740WithLargeFont() { val h = MobilityHarness(mobilityItems()); showMobility(h, 740, 1.3f); assertMobilityControlsInside(740, h) }

    @Test fun h6TimedMovementClockLivesInTheGuideSheetWithTouchTargets() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h, 740, 1.3f)
        assertMobilityControlsInside(740, h)
        rule.onNodeWithTag("mob-view-1").performClick()
        openGuide("Movilidad de cadera")
        listOf("mob-play", "mob-plus", "mob-reset").forEach {
            val node = rule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode()
            assertTouch(it, node)
        }
    }

    @Test fun h6ShortRestStaysInsideAt740() {
        val h = MobilityHarness(mobilityItems()); h.restSeconds = 20
        showMobility(h, 740)
        assertMobilityRestInside(740)
        rule.onNodeWithText("Gato-vaca").assertIsDisplayed()
        rule.onNodeWithTag("prep-rest-skip").performClick()
        rule.onNodeWithTag("rest-eclipse", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun h6ShowsTheExerciseNameWhenTheBlockMixesSeveralExercises() {
        val mixed = mobilityItems().mapIndexed { i, item -> if (i == 1) item.copy(exerciseId = "otro", exerciseName = "Peso muerto") else item }
        showMobility(MobilityHarness(mixed))
        rule.onNodeWithText("Gato-vaca · Sentadilla trasera").assertIsDisplayed()
        rule.onNodeWithText("Movilidad de cadera · Peso muerto").assertIsDisplayed()
    }

    @Test fun h6ARepsMovementShowsItsDoseAndHasNoClock() {
        showMobility(MobilityHarness(mobilityItems()))
        assertNoStatus("mob-status")
        openMovement()
        rule.onNodeWithTag("mob-movement-pane").assertIsDisplayed()
        rule.onNodeWithText("10 reps").assertIsDisplayed()
        rule.onNodeWithText("Gato-vaca").assertIsDisplayed()
        rule.onNodeWithTag("mob-open-guide").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mob-dose-summary", useUnmergedTree = true).assertTextEquals("10 reps")
        rule.onNodeWithText("Respira con calma.").assertIsDisplayed()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("mob-play").assertDoesNotExist()
        rule.onNodeWithTag("mob-plus").assertDoesNotExist()
    }

    @Test fun h6MovementClockStartsPausesResetsAndFinishesWithoutAdvancing() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        openGuide("Movilidad de cadera")
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:30")
        rule.onNodeWithTag("mob-dose-summary", useUnmergedTree = true).assertTextEquals("30 s")

        rule.onNodeWithTag("mob-play").performClick()
        assertStatus("mob-status", "Temporizador en marcha")
        rule.mainClock.advanceTimeBy(10_100)
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:20")

        rule.onNodeWithTag("mob-play").performClick()
        assertStatus("mob-status", "Temporizador en pausa")
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:20")

        rule.onNodeWithTag("mob-plus").performClick()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:50")

        rule.onNodeWithTag("mob-reset").performClick()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:30")
        assertStatus("mob-status", "Temporizador reiniciado")

        // Finalizar: llega a cero, avisa y NO avanza ni marca nada.
        rule.onNodeWithTag("mob-play").performClick()
        assertStatus("mob-status", "Temporizador en marcha")
        rule.mainClock.advanceTimeBy(31_500)
        rule.waitForIdle()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:00")
        assertStatus("mob-status", "Tiempo cumplido. Marca el movimiento como hecho.")
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("0 / 3")
        assertTrue("llegar a cero no marca el movimiento", h.toggles.isEmpty())
        // El reloj del movimiento es propio de la tarjeta: no toca el de la sesión.
        assertEquals(0, h.starts)
    }

    @Test fun h6RoadmapActionMarksTheMovementAndAdvancesToTheNext() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        assertRoadmapLabel(h.fab, "Registrar movimiento 1")
        roadmapPress(h.holder)
        assertEquals(listOf("m1" to true), h.toggles)
        assertStatus("mob-status", "Movimiento 1 hecho.")
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("1 / 3")
        assertRoadmapLabel(h.fab, "Registrar movimiento 2")
        // El movimiento en pantalla pasa al siguiente pendiente.
        openMovement()
        rule.onNodeWithText("Movilidad de cadera").assertIsDisplayed()
    }

    @Test fun h6RoadmapLabelEndsWithContinueToWarmup() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        repeat(3) { roadmapPress(h.holder) }
        assertEquals(listOf("m1" to true, "m2" to true, "m3" to true), h.toggles)
        assertRoadmapLabel(h.fab, "Ir a la aproximación")
        assertEquals(0, h.continues)
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h6RoadmapLabelEndsWithContinueToSetsWhenNoWarmupFollows() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h, nextIsWarmup = false)
        repeat(3) { roadmapPress(h.holder) }
        assertRoadmapLabel(h.fab, "Ir a las series")
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h6RoadmapLabelIsClearedWhenThePageIsNotActive() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        assertRoadmapLabel(h.fab, "Registrar movimiento 1")
        rule.runOnIdle { h.active = false }
        rule.waitForIdle()
        assertEquals(null, h.fab.label)
        assertEquals(null, h.holder.action)
        rule.runOnIdle { h.active = true }
        assertRoadmapLabel(h.fab, "Registrar movimiento 1")
    }

    @Test fun h6JointsShowTheZonesInSpanish() {
        val zoned = mobilityItems().mapIndexed { i, item ->
            when (i) {
                0 -> item.copy(mobility = item.mobility.copy(bodyZones = listOf("lower", "thoracic_spine")))
                1 -> item.copy(mobility = item.mobility.copy(bodyZones = listOf("hip", "lower")))
                else -> item
            }
        }
        showMobility(MobilityHarness(zoned))
        rule.onNodeWithTag("mob-view-2").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tren inferior").assertIsDisplayed()
        rule.onNodeWithText("Columna torácica").assertIsDisplayed()
        rule.onNodeWithText("Cadera").assertIsDisplayed()
        rule.onNodeWithText("lower").assertDoesNotExist()
        rule.onNodeWithText("thoracic_spine").assertDoesNotExist()
        // La hoja de la zona usa el mismo rótulo en español.
        rule.onNodeWithTag("mob-joint-lower").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("mob-joint-sheet", useUnmergedTree = true).assertExists()
        // El título de la hoja y la fila de la lista dicen lo mismo: «Tren inferior», nunca «lower».
        assertTrue(rule.onAllNodesWithText("Tren inferior", useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
        rule.onAllNodesWithText("lower", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun h6ListPageShowsEveryMovementAndMarksFromIt() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("0 / 3")
        rule.onNodeWithText("Gato-vaca").assertIsDisplayed()
        rule.onNodeWithText("Movilidad de cadera").assertIsDisplayed()
        rule.onNodeWithText("Bisagra con palo").assertIsDisplayed()
        val card = bounds(PrepLiveCardTag)
        assertInside(card, "lista", bounds("mob-list-pane"))
        val row = nodeOfTag("mob-row-m3"); assertTouch("fila de lista", row); assertInside(card, "fila de lista", row.boundsInRoot)
        rule.onNodeWithTag("mob-row-m3").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m3" to true), h.toggles)
        assertStatus("mob-status", "Movimiento 3 hecho.")
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("1 / 3")
        // Volver a tocar la fila la devuelve a pendiente.
        rule.onNodeWithTag("mob-row-m3").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m3" to true, "m3" to false), h.toggles)
        assertStatus("mob-status", "Movimiento pendiente")
        rule.onNodeWithTag("mob-count", useUnmergedTree = true).assertTextEquals("0 / 3")
    }

    @Test fun h6LongListScrollsInsideTheCard() {
        val h = MobilityHarness(mobilityItems(14))
        showMobility(h, 740)
        val card = assertFrame(740)
        val list = bounds("mob-list-pane")
        assertInside(card, "lista", list)
        rule.onNodeWithTag("mob-row-m14").assertIsNotDisplayed()
        rule.onNodeWithTag("mob-list-pane").performScrollToNode(hasTestTag("mob-row-m14"))
        val last = bounds("mob-row-m14")
        assertTrue("La última fila queda dentro de la lista tras desplazar", last.top >= list.top - 1.5f && last.bottom <= list.bottom + 1.5f)
        assertInside(card, "Omitir", bounds("prep-skip"))
        assertInside(card, "mensajes", bounds("mob-status"))
    }

    @Test fun h6SkipPhaseCallsTheSkipAndShowsTheOmittedPage() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.onNodeWithTag("prep-skip").performClick()
        rule.waitForIdle()
        assertEquals(1, h.skips)
        rule.onNodeWithText("Fase omitida").assertIsDisplayed()
        rule.onNodeWithText("Puedes continuar con las aproximaciones.").assertIsDisplayed()
        assertStatus("mob-status", "Fase de movilidad omitida")
        // Omitida no hay pie ni botón propio: seguir a la aproximación es el botón del roadmap.
        rule.onNodeWithTag("prep-skip").assertDoesNotExist()
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Ir a la aproximación")
        assertEquals(0, h.continues)
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h6FinishingEverythingOffersContinueInTheRoadmapWithoutAutoAdvance() {
        val h = MobilityHarness(mobilityItems()); h.completed = h.items.map { it.stepKey }.toSet()
        showMobility(h, nextIsWarmup = false)
        rule.onNodeWithText("Movilidad completada").assertIsDisplayed()
        rule.onNodeWithText("3 de 3 movimientos hechos.").assertIsDisplayed()
        // Terminada no hay pie: ni «Omitir» ni «Continuar» dentro de la tarjeta.
        rule.onNodeWithTag("prep-skip").assertDoesNotExist()
        assertNoOwnRecordButtons()
        assertRoadmapLabel(h.fab, "Ir a las series")
        assertEquals(0, h.continues)
        roadmapPress(h.holder)
        assertEquals(1, h.continues)
    }

    @Test fun h6RoadmapActionMarksTheMovementOnScreen() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        roadmapPress(h.holder)
        assertEquals(listOf("m1" to true), h.toggles)
    }


    // ---- Renders de H5 y H6 (solo escriben PNG si hay KPKN_RENDER_DIR) ---------------------------------------------------

    private fun render(name: String) = saveLiveQaRender(rule, view, "lv-c-$name")

    @Test fun renderH5At880() { showWarmup(WarmupHarness(threeRows().mapIndexed { i, r -> if (i == 0) r.copy(isCompleted = true, actualWeightKg = 50.0) else r })); assertFrame(880); render("h5-880") }
    @Test fun renderH5At740() { showWarmup(WarmupHarness(threeRows()), 740); assertFrame(740); render("h5-740") }
    @Test fun renderH5RestAt880() { val h = WarmupHarness(threeRows()); h.restSeconds = 45; showWarmup(h); assertFrame(880); render("h5-descanso-880") }
    @Test fun renderH5LongListAt740() { showWarmup(WarmupHarness((0 until 8).map { warmupRow(it, 0.3 + it * 0.05, 5) }), 740); assertFrame(740); render("h5-lista-larga-740") }
    @Test fun renderH5At740WithLargeFont() { showWarmup(WarmupHarness(threeRows()), 740, 1.3f); assertFrame(740); render("h5-740-130") }
    @Test fun renderH6RepsAt880() { showMobility(MobilityHarness(mobilityItems())); assertFrame(880); render("h6-reps-880") }
    @Test fun renderH6RepsAt740() { showMobility(MobilityHarness(mobilityItems()), 740); assertFrame(740); render("h6-reps-740") }
    @Test fun renderH6MovementAt880() { showMobility(MobilityHarness(mobilityItems())); openMovement(); assertFrame(880); render("h6-movimiento-880") }
    @Test fun renderH6MovementAt740() { showMobility(MobilityHarness(mobilityItems()), 740); openMovement(); assertFrame(740); render("h6-movimiento-740") }
    @Test fun renderH6ListAt880() { showMobility(MobilityHarness(mobilityItems(8))); assertFrame(880); render("h6-lista-880") }
    @Test fun renderH6ListAt740() { showMobility(MobilityHarness(mobilityItems(8)), 740); assertFrame(740); render("h6-lista-740") }
    @Test fun renderH6JointsAt880() { showMobility(MobilityHarness(mobilityItems())); rule.onNodeWithTag("mob-view-2").performClick(); assertFrame(880); render("h6-articulaciones-880") }
    @Test fun renderH6JointsAt740() { showMobility(MobilityHarness(mobilityItems()), 740); rule.onNodeWithTag("mob-view-2").performClick(); assertFrame(740); render("h6-articulaciones-740") }
    @Test fun renderH6FinishedAt740() { val h = MobilityHarness(mobilityItems()); h.completed = h.items.map { it.stepKey }.toSet(); showMobility(h, 740); assertFrame(740); render("h6-fin-740") }

    @Test fun cardKeepsTheSetCardSkin() {
        // Mismo radio de la tarjeta de serie: 28 dp (CardShape) y el mismo relleno.
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(28.dp), com.example.kpkn.screens.workout.components.WorkoutUiTokens.CardShape)
        assertNotNull(PrepLiveCardTag)
    }
}
