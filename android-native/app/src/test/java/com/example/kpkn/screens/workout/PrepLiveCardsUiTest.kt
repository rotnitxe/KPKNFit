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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
 * tarjeta de 312 × 425 dp, a 880 y a 740 dp de alto y con la letra al 100 % y al 130 %.
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

    // ---- Escena: cabecera, relator, tarjeta y línea ---------------------------------------------------------------------

    private fun scene(height: Int, font: Float = 1f, card: @Composable () -> Unit) {
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
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
                            val band = liveSessionRelatorBandHeight(height.toFloat(), header.value, 425f, 144f).dp
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
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 156.dp).width(312.dp)) {
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
    private fun boundsOfDescription(text: String): Rect =
        rule.onNode(hasContentDescription(text), useUnmergedTree = false).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
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

    private fun assertTouch(name: String, b: Rect) {
        assertTrue("$name mide al menos 48 dp de ancho (${b.width / density})", b.width / density >= 47.5f)
        assertTrue("$name mide al menos 48 dp de alto (${b.height / density})", b.height / density >= 47.5f)
    }

    /** La tarjeta mide 312 × 425 dp, llena el marco y no hay nada pulsable entre el relator y ella ni entre ella y la línea. */
    private fun assertFrame(height: Int): Rect {
        val frame = bounds("live-card-frame")
        val card = bounds(PrepLiveCardTag)
        println("LV-C marco a $height dp: frame=$frame card=$card density=$density")
        assertEquals("ancho de la tarjeta a $height", 312f, frame.width / density, .5f)
        assertEquals("alto de la tarjeta a $height", 425f, frame.height / density, .5f)
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
                isActivePage = true,
                onWeightDraft = { _, text -> h.drafts += text },
                workingWeightKg = working,
                effectiveSetCount = effectiveSets,
            )
        }
    }

    private fun assertWarmupControlsInside(height: Int, rest: Boolean) {
        val card = assertFrame(height)
        listOf("Añadir aproximación").forEach {
            val b = boundsOfDescription(it)
            assertInside(card, it, b); assertTouch(it, b)
        }
        val skip = bounds("prep-skip"); assertInside(card, "Saltar fase", skip); assertTouch("Saltar fase", skip)
        assertInside(card, "mensajes", bounds("prep-status"))
        assertInside(card, "lista", bounds("warmup-list"))
        if (rest) {
            assertInside(card, "descanso", bounds("prep-rest"))
            val b = bounds("prep-rest-skip"); assertInside(card, "Saltar descanso", b); assertTouch("Saltar descanso", b)
        }
        // Con el descanso a la vista la lista se acorta y las filas de abajo se desplazan: solo cuentan las visibles enteras.
        (1..(if (rest) 1 else 3)).forEach { n ->
            val check = boundsOfDescription("Marcar hecha aproximación $n")
            assertInside(card, "marca $n", check); assertTouch("marca $n", check)
            val field = boundsOfDescription("Carga de aproximación $n en kg")
            assertInside(card, "kg $n", field)
        }
        rule.onNodeWithText("Aproximación").assertIsDisplayed()
        rule.onNodeWithText("Primera serie efectiva · 100 kg").assertIsDisplayed()
    }

    @Test fun h5ControlsStayInsideTheCardAt880() { showWarmup(WarmupHarness(threeRows())); assertWarmupControlsInside(880, false) }
    @Test fun h5ControlsStayInsideTheCardAt740() { showWarmup(WarmupHarness(threeRows()), 740); assertWarmupControlsInside(740, false) }
    @Test fun h5ControlsStayInsideTheCardAt880WithLargeFont() { showWarmup(WarmupHarness(threeRows()), 880, 1.3f); assertWarmupControlsInside(880, false) }
    @Test fun h5ControlsStayInsideTheCardAt740WithLargeFont() { showWarmup(WarmupHarness(threeRows()), 740, 1.3f); assertWarmupControlsInside(740, false) }

    @Test fun h5ShortRestStaysInsideAt740() {
        val h = WarmupHarness(threeRows()); h.restSeconds = 45
        showWarmup(h, 740)
        assertWarmupControlsInside(740, true)
        rule.onNodeWithTag("prep-rest-time", useUnmergedTree = true).assertTextEquals("0:45")
        rule.onNodeWithContentDescription("Saltar descanso").performClick()
        assertEquals(1, h.restSkips)
        rule.onNodeWithTag("prep-rest", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun h5ShortRestStaysInsideAt880WithLargeFont() {
        val h = WarmupHarness(threeRows()); h.restSeconds = 45
        showWarmup(h, 880, 1.3f)
        assertWarmupControlsInside(880, true)
    }

    @Test fun h5KgAcceptsACommaAndTheMessageSaysSo() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        rule.onNode(hasContentDescription("Carga de aproximación 1 en kg") and hasSetTextAction()).performTextReplacement("62,5")
        assertEquals("62,5", h.drafts.last())
        rule.onNodeWithContentDescription("Marcar hecha aproximación 1").performClick()
        rule.waitForIdle()
        assertEquals(0 to 62.5, h.recorded)
        rule.onNodeWithTag("prep-status").assertTextEquals("Aproximación 1 hecha · 62,5 kg × 8")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("1/3")
        // Desmarcar la devuelve a pendiente.
        rule.onNodeWithContentDescription("Desmarcar aproximación 1").performClick()
        rule.onNodeWithTag("prep-status").assertTextEquals("Aproximación pendiente")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0/3")
    }

    @Test fun h5WaitsForCommittedRowsBeforeClaimingCompletion() {
        val h = WarmupHarness(threeRows()).apply { deferCommit = true }
        showWarmup(h)
        rule.onNode(hasContentDescription("Carga de aproximación 1 en kg") and hasSetTextAction()).performTextReplacement("62,5")
        rule.onNodeWithContentDescription("Marcar hecha aproximación 1").performClick()
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0/3")
        rule.onNodeWithTag("prep-status").assertTextEquals("Marca cada aproximación al terminar.")
        rule.runOnIdle { h.rows[0] = h.rows[0].copy(isCompleted = true, actualWeightKg = 62.5) }
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("1/3")
        rule.onNodeWithTag("prep-status").assertTextEquals("Aproximación 1 hecha · 62,5 kg × 8")
    }

    @Test fun h5ShowsRealValuesPercentRepsAndRest() {
        showWarmup(WarmupHarness(threeRows()))
        rule.onNodeWithText("A1 · 50 %").assertIsDisplayed()
        rule.onNodeWithText("A3 · 70 %").assertIsDisplayed()
        rule.onNodeWithText("× 8").assertIsDisplayed()
        rule.onNodeWithText("× 4").assertIsDisplayed()
        // 60 s de descanso cuando la aproximación no trae el suyo, y el suyo (90 s) cuando lo trae.
        assertTrue(rule.onAllNodesWithText("1:00").fetchSemanticsNodes().size >= 2)
        rule.onNodeWithText("1:30").assertIsDisplayed()
    }

    @Test fun h5AddAppendsARowAndReportsIt() {
        val h = WarmupHarness(threeRows())
        showWarmup(h, effectiveSets = 0)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        assertEquals(1, h.adds)
        assertEquals(4, h.rows.size)
        rule.onNodeWithTag("prep-status").assertTextEquals("Aproximación añadida")
        rule.onNodeWithTag("warmup-count", useUnmergedTree = true).assertTextEquals("0/4")
    }

    @Test fun h5AddingBeyondTheRecommendedMaxUsesTheWarmupRulesText() {
        // Con 1 serie efectiva el máximo recomendado es max(4, 2 × 1) = 4: la quinta aproximación lo supera.
        val h = WarmupHarness(threeRows() + warmupRow(3, 0.8, 3))
        showWarmup(h, effectiveSets = 1)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("prep-status")
            .assertTextEquals("Demasiadas aproximaciones: máximo recomendado 4 para 1 series efectivas.")
    }

    @Test fun h5AddingAtEightyFivePercentOrMoreWarnsLikeTheEditor() {
        val h = WarmupHarness(listOf(warmupRow(0, 0.5, 8), warmupRow(1, 0.6, 6), warmupRow(2, 0.8, 4)))
        showWarmup(h, effectiveSets = 3)
        rule.onNodeWithContentDescription("Añadir aproximación").performClick()
        rule.waitForIdle()
        // 80 % + 15 puntos, con tope de 90 %: la nueva queda por encima del 85 %.
        rule.onNodeWithTag("prep-status")
            .assertTextEquals("Una aproximación al 85% o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.")
    }

    @Test fun h5SkipPhaseStopsAndShowsTheOmittedPage() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        rule.onNodeWithTag("prep-skip").performClick()
        rule.waitForIdle()
        assertEquals(1, h.skips)
        rule.onNodeWithText("Fase omitida").assertIsDisplayed()
        rule.onNodeWithText("Las series efectivas siguen pendientes.").assertIsDisplayed()
        rule.onNodeWithTag("prep-status").assertTextEquals("Fase de aproximación omitida")
        rule.onNodeWithTag("prep-continue").performClick()
        assertEquals(1, h.continues)
    }

    @Test fun h5MarkingEverythingOffersContinueAndHidesSkip() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        (1..3).forEach { rule.onNodeWithContentDescription("Marcar hecha aproximación $it").performClick() }
        rule.waitForIdle()
        rule.onNodeWithTag("prep-skip").assertDoesNotExist()
        rule.onNodeWithTag("prep-status").assertTextEquals("Aproximación 3 hecha · 70 kg × 4")
        rule.onNodeWithTag("prep-continue").assertIsDisplayed().performClick()
        assertEquals(1, h.continues)
        assertInside(bounds(PrepLiveCardTag), "Continuar", bounds("prep-continue"))
    }

    @Test fun h5LongListScrollsInsideTheCard() {
        val rows = (0 until 12).map { warmupRow(it, 0.3 + it * 0.05, 5) }
        val h = WarmupHarness(rows)
        showWarmup(h, 740)
        val card = assertFrame(740)
        val list = bounds("warmup-list")
        assertInside(card, "lista", list)
        rule.onNodeWithContentDescription("Marcar hecha aproximación 12").assertIsNotDisplayed()
        rule.onNodeWithTag("warmup-list").performScrollToNode(hasContentDescription("Marcar hecha aproximación 12"))
        val last = boundsOfDescription("Marcar hecha aproximación 12")
        assertTrue("La última fila queda dentro de la lista tras desplazar: $last frente a $list",
            last.top >= list.top - 1.5f && last.bottom <= list.bottom + 1.5f)
        // El pie y los mensajes no se mueven con la lista.
        assertInside(card, "Saltar fase", bounds("prep-skip"))
        assertInside(card, "mensajes", bounds("prep-status"))
    }

    @Test fun h5CheckInTheLineMarksTheNextRow() {
        val h = WarmupHarness(threeRows())
        showWarmup(h)
        rule.runOnIdle { h.holder.action?.invoke() }
        rule.waitForIdle()
        assertEquals(0 to 50.0, h.recorded)
        assertTrue(h.rows[0].isCompleted)
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
                isActivePage = true,
                nextIsWarmup = nextIsWarmup,
            )
        }
    }

    private fun assertMobilityControlsInside(height: Int, rest: Boolean = false) {
        val card = assertFrame(height)
        listOf("Abrir reloj global de movilidad", "Ver lista de movimientos", "Marcar hecho y avanzar al siguiente movimiento").forEach {
            val b = boundsOfDescription(it); assertInside(card, it, b); assertTouch(it, b)
        }
        val skip = bounds("prep-skip"); assertInside(card, "Saltar fase", skip); assertTouch("Saltar fase", skip)
        assertInside(card, "mensajes", bounds("prep-status"))
        assertInside(card, "panel del movimiento", bounds("mob-movement-pane"))
        if (rest) {
            assertInside(card, "descanso", bounds("prep-rest"))
            val b = bounds("prep-rest-skip"); assertInside(card, "Saltar descanso", b); assertTouch("Saltar descanso", b)
        }
        rule.onNodeWithText("Movilidad").assertIsDisplayed()
    }

    private fun selectFromList(name: String) {
        rule.onNodeWithContentDescription("Ver lista de movimientos").performClick()
        rule.onNodeWithContentDescription("Seleccionar $name").performClick()
        rule.waitForIdle()
    }

    @Test fun h6RepsMovementControlsStayInsideAt880() { showMobility(MobilityHarness(mobilityItems())); assertMobilityControlsInside(880) }
    @Test fun h6RepsMovementControlsStayInsideAt740() { showMobility(MobilityHarness(mobilityItems()), 740); assertMobilityControlsInside(740) }
    @Test fun h6RepsMovementControlsStayInsideAt880WithLargeFont() { showMobility(MobilityHarness(mobilityItems()), 880, 1.3f); assertMobilityControlsInside(880) }
    @Test fun h6RepsMovementControlsStayInsideAt740WithLargeFont() { showMobility(MobilityHarness(mobilityItems()), 740, 1.3f); assertMobilityControlsInside(740) }

    @Test fun h6TimedMovementControlsStayInsideAt740WithLargeFont() {
        showMobility(MobilityHarness(mobilityItems()), 740, 1.3f)
        selectFromList("Movilidad de cadera")
        assertMobilityControlsInside(740)
        val card = bounds(PrepLiveCardTag)
        listOf("Iniciar temporizador del movimiento", "Añadir treinta segundos al movimiento", "Reiniciar el temporizador del movimiento").forEach {
            val b = boundsOfDescription(it); assertInside(card, it, b); assertTouch(it, b)
        }
    }

    @Test fun h6ShortRestStaysInsideAt740() {
        val h = MobilityHarness(mobilityItems()); h.restSeconds = 20
        showMobility(h, 740)
        assertMobilityControlsInside(740, rest = true)
    }

    @Test fun h6ShowsTheExerciseNameWhenTheBlockMixesSeveralExercises() {
        val mixed = mobilityItems().mapIndexed { i, item -> if (i == 1) item.copy(exerciseId = "otro", exerciseName = "Peso muerto") else item }
        showMobility(MobilityHarness(mixed))
        rule.onNodeWithText("Movimiento 1 de 3 · Sentadilla trasera").assertIsDisplayed()
    }

    @Test fun h6ARepsMovementShowsItsDoseAndHasNoClock() {
        showMobility(MobilityHarness(mobilityItems()))
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("10 reps")
        rule.onNodeWithText("A tu ritmo").assertIsDisplayed()
        rule.onNodeWithTag("mob-plus").assertDoesNotExist()
        rule.onNodeWithTag("mob-play").assertDoesNotExist()
        rule.onNodeWithTag("mob-dose-summary", useUnmergedTree = true).assertTextEquals("10 reps")
        rule.onNodeWithText("Movimiento 1 de 3").assertIsDisplayed()
        rule.onNodeWithText("Respira con calma.").assertIsDisplayed()
        rule.onNodeWithTag("prep-status").assertTextEquals("Completa cada movimiento para avanzar.")
    }

    @Test fun h6MovementClockStartsPausesResetsAndFinishesWithoutAdvancing() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        selectFromList("Movilidad de cadera")
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:30")
        rule.onNodeWithTag("mob-dose-summary", useUnmergedTree = true).assertTextEquals("30 s")

        rule.onNodeWithContentDescription("Iniciar temporizador del movimiento").performClick()
        rule.onNodeWithTag("prep-status").assertTextEquals("Temporizador en marcha")
        rule.mainClock.advanceTimeBy(10_100)
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:20")

        rule.onNodeWithContentDescription("Pausar temporizador del movimiento").performClick()
        rule.onNodeWithTag("prep-status").assertTextEquals("Temporizador en pausa")
        rule.mainClock.advanceTimeBy(10_000)
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:20")

        rule.onNodeWithTag("mob-plus").performClick()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:50")

        rule.onNodeWithTag("mob-reset").performClick()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:30")
        rule.onNodeWithTag("prep-status").assertTextEquals("Temporizador reiniciado")

        // Finalizar: llega a cero, avisa y NO avanza ni marca nada.
        rule.onNodeWithContentDescription("Iniciar temporizador del movimiento").performClick()
        rule.onNodeWithTag("prep-status").assertTextEquals("Temporizador en marcha")
        rule.mainClock.advanceTimeBy(31_500)
        rule.waitForIdle()
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:00")
        rule.onNodeWithTag("prep-status").assertTextEquals("Tiempo cumplido. Marca el movimiento como hecho.")
        rule.onNodeWithText("Movimiento 2 de 3").assertIsDisplayed()
        assertTrue("llegar a cero no marca el movimiento", h.toggles.isEmpty())
        // Con el reloj global intacto: es independiente.
        rule.onNodeWithText("5:00").assertIsDisplayed()
        assertEquals(0, h.starts)
    }

    @Test fun h6DoneMarksTheMovementAndAdvancesToTheNext() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.onNodeWithContentDescription("Marcar hecho y avanzar al siguiente movimiento").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m1" to true), h.toggles)
        rule.onNodeWithTag("prep-status").assertTextEquals("Movimiento 1 hecho.")
        rule.onNodeWithTag("mob-dose", useUnmergedTree = true).assertTextEquals("0:30")
        rule.onNodeWithText("Movimiento 2 de 3").assertIsDisplayed()
    }

    @Test fun h6ListPageShowsEveryMovementAndMarksFromIt() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.onNodeWithContentDescription("Ver lista de movimientos").performClick()
        rule.onNodeWithText("0/3 movimientos hechos").assertIsDisplayed()
        rule.onNodeWithText("Gato-vaca").assertIsDisplayed()
        rule.onNodeWithText("Movilidad de cadera").assertIsDisplayed()
        rule.onNodeWithText("30 s").assertIsDisplayed()
        rule.onNodeWithText("8 reps").assertIsDisplayed()
        val card = bounds(PrepLiveCardTag)
        assertInside(card, "lista", bounds("mob-list-pane"))
        val check = boundsOfDescription("Marcar hecho Bisagra con palo"); assertTouch("marca de lista", check); assertInside(card, "marca de lista", check)
        rule.onNodeWithContentDescription("Marcar hecho Bisagra con palo").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m3" to true), h.toggles)
        // Vuelve al movimiento (el primero pendiente) y la lista cuenta uno hecho.
        rule.onNodeWithTag("mob-movement-pane").assertIsDisplayed()
        rule.onNodeWithContentDescription("Ver lista de movimientos").performClick()
        rule.onNodeWithText("1/3 movimientos hechos").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar lista de movimientos").performClick()
        rule.onNodeWithTag("mob-movement-pane").assertIsDisplayed()
    }

    @Test fun h6LongListScrollsInsideTheCard() {
        val h = MobilityHarness(mobilityItems(14))
        showMobility(h, 740)
        rule.onNodeWithContentDescription("Ver lista de movimientos").performClick()
        val card = assertFrame(740)
        val list = bounds("mob-list-pane")
        assertInside(card, "lista", list)
        rule.onNodeWithContentDescription("Seleccionar Movimiento largo 14").assertIsNotDisplayed()
        rule.onNodeWithTag("mob-list-pane").performScrollToNode(hasContentDescription("Seleccionar Movimiento largo 14"))
        val last = boundsOfDescription("Seleccionar Movimiento largo 14")
        assertTrue("La última fila queda dentro de la lista tras desplazar", last.top >= list.top - 1.5f && last.bottom <= list.bottom + 1.5f)
        assertInside(card, "Saltar fase", bounds("prep-skip"))
        assertInside(card, "mensajes", bounds("prep-status"))
    }

    @Test fun h6GlobalClockPageHasItsOwnIndependentControls() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h, 740)
        rule.onNodeWithContentDescription("Abrir reloj global de movilidad").performClick()
        val card = assertFrame(740)
        rule.onNodeWithText("Objetivo global · 5 min").assertIsDisplayed()
        rule.onNodeWithTag("mob-global-time", useUnmergedTree = true).assertTextEquals("5:00")
        // Si la página no cupiera entera, el último botón se alcanza desplazando por dentro de la tarjeta.
        rule.onNodeWithTag("mob-global-pane").performScrollToNode(androidx.compose.ui.test.hasTestTag("mob-global-back"))
        listOf("mob-global-play", "mob-global-plus", "mob-global-reset", "mob-global-back").forEach {
            val b = bounds(it); assertInside(card, it, b); assertTouch(it, b)
        }
        rule.onNodeWithTag("mob-global-play").performClick()
        assertEquals(1, h.starts)
        rule.onNodeWithContentDescription("Pausar temporizador global").performClick()
        assertEquals(1, h.pauses)
        rule.onNodeWithTag("mob-global-plus").performClick()
        assertEquals(listOf(30), h.added)
        rule.onNodeWithTag("mob-global-time", useUnmergedTree = true).assertTextEquals("5:30")
        rule.onNodeWithTag("mob-global-reset").performClick()
        assertEquals(1, h.resets)
        rule.onNodeWithTag("mob-global-time", useUnmergedTree = true).assertTextEquals("5:00")
        // El reloj del movimiento no se tocó: sigue sin marcar nada y vuelve a su página.
        rule.onNodeWithTag("mob-global-back").performClick()
        rule.onNodeWithTag("mob-movement-pane").assertIsDisplayed()
        assertTrue(h.toggles.isEmpty())
    }

    @Test fun h6GlobalClockReachingZeroOffersToStartAgain() {
        val h = MobilityHarness(mobilityItems()); h.globalRemaining = 0
        showMobility(h)
        rule.onNodeWithContentDescription("Abrir reloj global de movilidad").performClick()
        rule.onNodeWithTag("mob-global-time", useUnmergedTree = true).assertTextEquals("0:00")
        rule.onNodeWithContentDescription("Iniciar temporizador global").assertIsDisplayed()
    }

    @Test fun h6SkipPhaseCallsTheSkipAndShowsTheOmittedPage() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.onNodeWithTag("prep-skip").performClick()
        rule.waitForIdle()
        assertEquals(1, h.skips)
        rule.onNodeWithText("Fase omitida").assertIsDisplayed()
        rule.onNodeWithText("Puedes continuar con las aproximaciones.").assertIsDisplayed()
        rule.onNodeWithTag("prep-status").assertTextEquals("Fase de movilidad omitida")
        rule.onNodeWithText("Continuar con aproximación").performClick()
        assertEquals(1, h.continues)
    }

    @Test fun h6FinishingEverythingOffersContinueWithoutAutoAdvance() {
        val h = MobilityHarness(mobilityItems()); h.completed = h.items.map { it.stepKey }.toSet()
        showMobility(h, nextIsWarmup = false)
        rule.onNodeWithText("Movilidad completada").assertIsDisplayed()
        rule.onNodeWithText("3 de 3 movimientos hechos.").assertIsDisplayed()
        assertEquals(0, h.continues)
        rule.onNodeWithText("Continuar con series").performClick()
        assertEquals(1, h.continues)
        assertInside(bounds(PrepLiveCardTag), "Continuar", bounds("prep-continue"))
    }

    @Test fun h6CheckInTheLineMarksTheMovementOnScreen() {
        val h = MobilityHarness(mobilityItems())
        showMobility(h)
        rule.runOnIdle { h.holder.action?.invoke() }
        rule.waitForIdle()
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
    @Test fun renderH6TimedAt880() { showMobility(MobilityHarness(mobilityItems())); selectFromList("Movilidad de cadera"); assertFrame(880); render("h6-tiempo-880") }
    @Test fun renderH6TimedAt740() { showMobility(MobilityHarness(mobilityItems()), 740); selectFromList("Movilidad de cadera"); assertFrame(740); render("h6-tiempo-740") }
    @Test fun renderH6ListAt880() { showMobility(MobilityHarness(mobilityItems(8))); rule.onNodeWithContentDescription("Ver lista de movimientos").performClick(); assertFrame(880); render("h6-lista-880") }
    @Test fun renderH6ListAt740() { showMobility(MobilityHarness(mobilityItems(8)), 740); rule.onNodeWithContentDescription("Ver lista de movimientos").performClick(); assertFrame(740); render("h6-lista-740") }
    @Test fun renderH6GlobalAt880() { showMobility(MobilityHarness(mobilityItems())); rule.onNodeWithContentDescription("Abrir reloj global de movilidad").performClick(); assertFrame(880); render("h6-global-880") }
    @Test fun renderH6GlobalAt740() { showMobility(MobilityHarness(mobilityItems()), 740); rule.onNodeWithContentDescription("Abrir reloj global de movilidad").performClick(); assertFrame(740); render("h6-global-740") }
    @Test fun renderH6FinishedAt740() { val h = MobilityHarness(mobilityItems()); h.completed = h.items.map { it.stepKey }.toSet(); showMobility(h, 740); assertFrame(740); render("h6-fin-740") }

    @Test fun cardKeepsTheSetCardSkin() {
        // Mismo radio de la tarjeta de serie: 28 dp (CardShape) y el mismo relleno.
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(28.dp), com.example.kpkn.screens.workout.components.WorkoutUiTokens.CardShape)
        assertNotNull(PrepLiveCardTag)
    }
}
