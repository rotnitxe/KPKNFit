package com.example.kpkn.screens.workout

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * LV-C, punto 0: a 760 dp de alto la cabecera compacta dejaba la voz en 44 dp de objetivo táctil (la regresión de la QA de LV-BF).
 * Cada control de la cabecera mide al menos 48 dp con la letra al 100 % y al 130 %, y el título largo sigue acabando en elipsis.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h760dp-xxhdpi")
class LiveHeaderTouchTargetsTest {
    @get:Rule val rule = createComposeRule()
    private var density = 1f
    private lateinit var view: View

    private val longTitle = "Elevaciones laterales con mancuernas en polea baja unilateral"

    @Test fun headerTargetsAre48dpAt760WithNormalFont() = check(1f)

    @Test fun headerTargetsAre48dpAt760WithLargeFont() = check(1.3f)

    private fun check(font: Float) {
        rule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, font),
                LocalWizardReducedMotion provides true,
                LocalKpknOverlayHost provides null,
                LocalLiveSessionCompactLayout provides true,
            ) {
                KPKNTheme {
                    MaterialTheme {
                        Box(Modifier.requiredSize(360.dp, 760.dp).background(Color.Black).testTag("header-scene")) {
                            WorkoutHeaderBar(
                                longTitle, sessionName = "Fuerza A", groupName = "Principal", startTimeMs = 0L,
                                isComplete = false, background = null, sessionTimeRemainingSeconds = null, onAdjustTimeLimit = {},
                                onStoryClick = {}, onToggleVoice = {}, onMoreClick = {}, compactLayout = true,
                                statusBarInsets = WindowInsets(0, 0, 0, 0),
                            )
                        }
                    }
                }
            }
        }
        listOf("workout-story-header", "workout-voice-header", "workout-more-header").forEach { tag ->
            val node = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            val layout = node.boundsInRoot
            val touch = node.touchBoundsInRoot
            println("LV-C cabecera $tag font=$font: layout=${layout.width / density} x ${layout.height / density} dp, " +
                "toque=${touch.width / density} x ${touch.height / density} dp")
            // El layout compacto mide 44 dp para no quitarle alto al relator; el objetivo táctil real es de 48 dp.
            assertTrue("$tag mide al menos 48 dp de ancho táctil (${touch.width / density})", touch.width / density >= 47.5f)
            assertTrue("$tag mide al menos 48 dp de alto táctil (${touch.height / density})", touch.height / density >= 47.5f)
        }
        // Todo lo que se pulsa en la cabecera (chips incluidos) cumple lo mismo.
        val clickable = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("live-session-header")))
            .fetchSemanticsNodes()
        assertTrue("La cabecera tiene controles pulsables", clickable.isNotEmpty())
        // Para los chips (p. ej. el cronómetro, de 27 dp de alto visible) cuenta la zona que realmente recibe el toque: la de los
        // 48 dp mínimos que Compose les añade alrededor.
        clickable.forEach { node ->
            val b = node.touchBoundsInRoot
            println("LV-C cabecera pulsable ${node.id} font=$font: layout=${node.boundsInRoot.height / density} dp, toque=${b.width / density} x ${b.height / density} dp")
            assertTrue("Objetivo de ${node.id} con alto táctil ${b.height / density} dp", b.height / density >= 47.5f)
        }
        // La elipsis del título largo se conserva.
        val titleNode = rule.onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag("live-session-header")), useUnmergedTree = true)
        val layouts = mutableListOf<TextLayoutResult>()
        titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val title = layouts.single()
        assertTrue("El título largo acaba con elipsis", title.isLineEllipsized(0))
    }
}
