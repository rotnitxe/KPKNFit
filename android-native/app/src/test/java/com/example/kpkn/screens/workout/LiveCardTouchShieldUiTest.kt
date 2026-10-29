package com.example.kpkn.screens.workout

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.workout.components.PrepLiveCardTag
import com.example.kpkn.screens.workout.components.WarmupPhaseLiveCard
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Toques que atravesaban la tarjeta (defecto visto en el Z Flip5): el carrusel apila las tarjetas siguientes JUSTO debajo de la actual
 * (`liveCardStackTransform`), y en Compose un toque en una zona sin `pointerInput` de la hermana de encima pasa a la de debajo. Una zona
 * vacía de la tarjeta de aproximación enfocaba así el campo de carga de la serie efectiva que viene detrás. Esta prueba monta el mismo
 * carrusel (mismo relleno, separación, orden de dibujo y transformación de la pila) con la tarjeta de aproximación delante y un campo de
 * texto que ocupa toda la tarjeta de detrás.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h880dp-xxhdpi")
class LiveCardTouchShieldUiTest {
    @get:Rule val rule = createComposeRule()

    private var underText by mutableStateOf("")
    private var underFocusCount = 0
    private var density = 1f
    private lateinit var pager: PagerState

    private val rows = mutableStateListOf(
        WarmupPhaseRow("sent", null, 0, WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = .5, targetReps = 8), 50.0, null, false),
        WarmupPhaseRow("sent", null, 1, WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = .7, targetReps = 5), 70.0, null, false),
    )

    private fun carousel(shielded: Boolean) {
        rule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalWizardReducedMotion provides true, LocalKpknOverlayHost provides null) {
                KPKNTheme { MaterialTheme {
                    BoxWithConstraints(Modifier.requiredSize(360.dp, 760.dp).background(Color.Black)) {
                        val available = maxWidth
                        pager = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().testTag("pager"),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = LiveCardStackRoom),
                            verticalAlignment = Alignment.Top,
                            pageSpacing = 14.dp,
                            beyondViewportPageCount = 2,
                        ) { page ->
                            val offset = (page - pager.currentPage) - pager.currentPageOffsetFraction
                            val stride = with(LocalDensity.current) { (available - 48.dp + 14.dp).toPx() }
                            val pageModifier = Modifier.fillMaxWidth().zIndex(liveCardStackZ(offset)).graphicsLayer {
                                val stack = liveCardStackTransform(offset, stride, this.density, true)
                                translationX = stack.translationX
                                translationY = stack.translationY
                                scaleX = stack.scale
                                scaleY = stack.scale
                                alpha = stack.alpha
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            val content: @Composable BoxScope.() -> Unit = {
                                LiveSessionCardFrame {
                                    if (page == 0) warmupCard(isActive = pager.settledPage == 0) else underCard()
                                }
                            }
                            if (shielded) {
                                LiveCardTouchShield(isCurrent = page == pager.currentPage, modifier = pageModifier, content = content)
                            } else {
                                Box(pageModifier, content = content)
                            }
                        }
                    }
                } }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun warmupCard(isActive: Boolean) {
        WarmupPhaseLiveCard(
            rows = rows.toList(),
            sessionAccentColor = Color(0xFFF49A6E),
            onToggleComplete = { _, _, _ -> },
            onSkip = {},
            onContinue = {},
            modifier = Modifier.fillMaxSize(),
            isActivePage = isActive,
            workingWeightKg = 100.0,
        )
    }

    /** La tarjeta de serie efectiva que asoma detrás: aquí, un campo de texto que la ocupa entera. */
    @Composable
    private fun underCard() {
        BasicTextField(
            value = underText,
            onValueChange = { underText = it },
            modifier = Modifier.fillMaxSize().onFocusChanged { if (it.isFocused) underFocusCount++ }.testTag("under-field"),
        )
    }

    /** Un punto vacío de la tarjeta de aproximación: a la derecha del título «Acércate a tu carga.», sin ningún control. */
    private fun emptyZoneOfTheWarmupCard(): Offset {
        val card = rule.onNodeWithTag(PrepLiveCardTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val title = rule.onNodeWithText("Acércate a tu carga.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("El título deja hueco a su derecha", card.right - title.right > 30f * density)
        return Offset(card.right - 18f * density, title.center.y)
    }

    private fun tap(point: Offset) {
        rule.onRoot().performTouchInput { click(point) }
        rule.waitForIdle()
    }

    @Test fun withoutTheShieldATapOnAnEmptyZoneReachesTheCardBelow() {
        // Reproduce el defecto: sin la protección, el toque en la zona vacía enfoca el campo de la tarjeta apilada debajo.
        carousel(shielded = false)
        tap(emptyZoneOfTheWarmupCard())
        assertTrue("Sin protección el toque atraviesa la tarjeta (causa del defecto)", underFocusCount > 0)
    }

    @Test fun aTapOnAnEmptyZoneOfTheCurrentCardDoesNotFocusTheCardBelow() {
        carousel(shielded = true)
        tap(emptyZoneOfTheWarmupCard())
        assertEquals("El campo de la tarjeta de debajo no se enfoca", 0, underFocusCount)
        rule.onNodeWithTag("under-field").assertIsNotFocused()
        assertEquals("", underText)
        // La tarjeta de detrás (no actual) lleva su capa de bloqueo; la actual, no.
        rule.onNodeWithTag(LiveCardTouchBlockerTag).assertExists()
    }

    @Test fun aTapBetweenThePercentAndTheRepsDoesNotFocusTheCardBelow() {
        // La zona del defecto: entre «50 %» y «50 kg × 8» de la fila A1.
        carousel(shielded = true)
        val percent = rule.onNodeWithText("50 %", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val dose = rule.onNodeWithText("50 kg × 8", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Hay hueco entre el porcentaje y las reps", dose.left - percent.right > 8f * density)
        tap(Offset((percent.right + dose.left) / 2f, percent.center.y))
        assertEquals(0, underFocusCount)
        rule.onNodeWithTag("under-field").assertIsNotFocused()
    }

    @Test fun theVisibleEdgeOfAStackedCardDoesNotReceiveTaps() {
        carousel(shielded = true)
        val frame = rule.onNodeWithTag(PrepLiveCardTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // La tarjeta de debajo asoma 26 dp bajo la actual: su borde no es la tarjeta actual y su contenido no recibe toques.
        tap(Offset(frame.center.x, frame.bottom + 12f * density))
        assertEquals(0, underFocusCount)
        rule.onNodeWithTag("under-field").assertIsNotFocused()
    }

    @Test fun theCarouselStillSwipesAndTheNewCurrentCardReceivesTaps() {
        carousel(shielded = true)
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals("El deslizamiento horizontal sigue funcionando sobre la protección", 1, pager.settledPage)
        assertFalse(underFocusCount > 0)
        // Ahora la tarjeta del campo es la actual: sus controles vuelven a recibir toques.
        val field = rule.onNodeWithTag("under-field").fetchSemanticsNode().boundsInRoot
        tap(field.center)
        assertTrue("La tarjeta actual recibe sus toques", underFocusCount > 0)
    }
}
