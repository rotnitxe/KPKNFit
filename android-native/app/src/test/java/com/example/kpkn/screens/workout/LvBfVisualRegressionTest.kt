package com.example.kpkn.screens.workout

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Production frame, header and narrator, including the short viewport from the supplied QA. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LvBfVisualRegressionTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var view: View
    private var density = 1f

    @Test fun rmEditorLeavesTheNarratorVisibleAt740() = scene(TrainingMode.RM, 740)
    @Test fun rmEditorLeavesTheNarratorVisibleAt740WithLargeText() = scene(TrainingMode.RM, 740, 1.3f)
    @Test fun rmEditorLeavesTheNarratorVisibleAt880() = scene(TrainingMode.RM, 880)
    @Test fun distanceSeparatesItsTargetAndFitsOrEllipsizesItsTitle() = scene(TrainingMode.DISTANCE, 740)
    @Test fun customSeparatesItsTargetWithLargeText() = scene(TrainingMode.CUSTOM, 740, 1.3f)
    @Test fun soloRpeEllipsizesItsLongTitleAt880() = scene(TrainingMode.SOLO_RPE, 880)

    private fun scene(mode: TrainingMode, height: Int, font: Float = 1f) {
        val exercise = liveModeExercise(mode)
        val phrase = if (mode == TrainingMode.RM) "Tu referencia: 125 kg de 1RM. Plan: 80 %." else "Registra cómo se sintió tu serie."
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font), LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null, LocalLiveSessionCompactLayout provides (height < 800)) {
                KPKNTheme { MaterialTheme {
                    Box(Modifier.requiredSize(360.dp, height.dp).background(Color.Black).testTag("bf-scene")) {
                        var headerPx by remember { mutableIntStateOf(0) }
                        val localDensity = LocalDensity.current
                        val header = with(localDensity) { headerPx.toDp() }
                        val band = liveSessionRelatorBandHeight(height.toFloat(), header.value, 394f, 144f).dp
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 45.dp)
                            .onSizeChanged { headerPx = it.height + with(localDensity) { 45.dp.roundToPx() } }) {
                            WorkoutHeaderBar(exercise.name, sessionName = "Fuerza A", groupName = "Principal", startTimeMs = 0L,
                                isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                                onStoryClick = {}, onToggleVoice = {}, onMoreClick = {}, compactLayout = height < 800,
                                statusBarInsets = WindowInsets(0, 0, 0, 0))
                        }
                        WorkoutLiveRelatorLine(phrase, "bf", reduceMotionOverride = true,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = header).height(band).testTag("bf-relator"))
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = LiveSessionLineHeight + 44.dp).width(312.dp)) {
                            LiveSessionCardFrame {
                                SetInputCardV2(exercise, 0, exercise.sets.single(), null,
                                    onShowHistory = {}, onSetBodyWeight = {}, initialBodyWeight = 80.0,
                                    recordActionHolder = remember { RecordActionHolder() },
                                    onRecordV2 = { _, _, _, _, _, _, _, _, _, _ -> })
                            }
                        }
                        LiveSessionLine(listOf(exercise), exercise.id, liveLineTestSnapshot(4), {}, {}, {}, {}, {}, true, true,
                            onAddSet = {}, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
                    }
                } }
            }
        }
        // Refinamiento 02: el editor de RM vive en la hoja «Referencia de RM» (live-rm-sheet), que abre la referencia de la tarjeta.
        if (mode == TrainingMode.RM) rule.onNodeWithTag("live-card-reference-history").performClick()
        val frame = rule.onNodeWithTag("live-card-frame").fetchSemanticsNode().boundsInRoot
        val actions = rule.onNodeWithTag("live-card-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val line = rule.onNodeWithTag("live-session-line").fetchSemanticsNode().boundsInRoot
        val narrator = rule.onNodeWithText(phrase, useUnmergedTree = true).assertIsDisplayed()
        val narratorBounds = narrator.fetchSemanticsNode().boundsInRoot
        val layout = textLayout(narrator)
        println("LV-BF $mode $height/$font: frame=$frame; relator=$narratorBounds; layout=${layout.size}; lastLine=${layout.getLineBottom(layout.lineCount - 1)}")
        assertTrue("El relator conserva toda la altura de sus líneas", layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
        assertTrue("El relator termina antes de la tarjeta", narratorBounds.bottom <= frame.top)
        assertFalse("La frase del relator queda completa", layout.isLineEllipsized(layout.lineCount - 1))
        assertEquals(312f, frame.width / density, .5f)
        assertEquals(394f, frame.height / density, .5f)
        assertEquals(44f, actions.height / density, .5f)
        assertEquals(12f, (line.top - frame.bottom) / density, .5f)
        // Refinamiento 02: «Historia» vive en el menú «Tu sesión»; la cabecera conserva voz y ⋯.
        listOf("workout-voice-header", "workout-more-header").forEach { tag ->
            val target = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag conserva 44dp táctiles", target.width / density >= 43.5f && target.height / density >= 43.5f)
        }
        if (mode == TrainingMode.DISTANCE || mode == TrainingMode.CUSTOM) {
            // Refinamiento 02: el frente ya no pinta «Objetivo» ni las etiquetas de LiveModeSetContent. La cantidad se lee en su cabecera
            // (set_reps_header, 21 dp) y el campo va justo debajo: la etiqueta cabe en su cabecera y la cabecera no pisa el campo.
            val unitLabel = if (mode == TrainingMode.DISTANCE) "DISTANCIA" else exercise.customUnit.orEmpty().uppercase()
            val header = rule.onNodeWithTag("set_reps_header", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val label = rule.onNodeWithText(unitLabel, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val field = rule.onNodeWithTag("live-card-quantity-input", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("La etiqueta de la cantidad cabe en su cabecera", label.top >= header.top - 1f && label.bottom <= header.bottom + 1f)
            assertTrue("La cabecera de la cantidad no pisa su campo", header.bottom <= field.top + 1f)
        }
        if (mode == TrainingMode.DISTANCE || mode == TrainingMode.SOLO_RPE) {
            val titleNode = rule.onNode(hasText(exercise.name) and hasAnyAncestor(hasTestTag("live-session-header")), useUnmergedTree = true)
            val title = textLayout(titleNode)
            assertTrue("El título se mide dentro de su ventana", title.size.width <= titleNode.fetchSemanticsNode().boundsInRoot.width + 1f)
            println("Título $mode: size=${title.size}; lineBottom=${title.getLineBottom(0)}; intrinsicWidth=${title.multiParagraph.intrinsics.maxIntrinsicWidth}; ellipsis=${title.isLineEllipsized(0)}")
            assertTrue("La línea del título conserva su altura", title.getLineBottom(0) <= title.size.height + 1f)
            if (title.multiParagraph.intrinsics.maxIntrinsicWidth > title.size.width + 1f) {
                assertTrue("Un título que desborda en ancho acaba con elipsis", title.isLineEllipsized(0))
            }
        }
        saveLiveQaRender(rule, view, "lv-bf-${mode.name.lowercase()}-$height-${(font * 100).toInt()}")
    }

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

}
