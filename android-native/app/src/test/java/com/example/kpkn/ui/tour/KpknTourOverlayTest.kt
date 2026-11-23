package com.example.kpkn.ui.tour

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class KpknTourOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeSeen : TourSeenStore {
        val marked = mutableListOf<String>()

        override fun seenIds(): Set<String> = marked.toSet()

        override fun markSeen(tourId: String) {
            marked += tourId
        }
    }

    private fun step(anchor: String, title: String, kind: TourStepKind = TourStepKind.NEXT) =
        TourStep(anchorId = anchor, title = title, body = "Texto de $title.", kind = kind)

    private fun cutout(): Rect = rule.onNodeWithTag("tour_cutout").fetchSemanticsNode().boundsInRoot

    private fun boundsOf(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun Rect.covers(inner: Rect): Boolean =
        left <= inner.left && top <= inner.top && right >= inner.right && bottom >= inner.bottom

    @Test
    fun el_recorte_rodea_el_ancla_y_se_mueve_a_la_siguiente() {
        val host = KpknOverlayHostController(tourSeen = FakeSeen())
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 60.dp, start = 60.dp).size(120.dp).testTag("primera").tourAnchor("a")) {}
                    Box(Modifier.padding(top = 400.dp, start = 60.dp).size(120.dp).testTag("segunda").tourAnchor("b")) {}
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            host.startTour("demo", listOf(step("a", "Primero"), step("b", "Segundo")))
        }
        rule.waitForIdle()
        assertTrue(cutout().covers(boundsOf("primera")))

        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        assertTrue(cutout().covers(boundsOf("segunda")))
        assertFalse(cutout().covers(boundsOf("primera")))
    }

    @Test
    fun saltar_recorrido_cierra_el_overlay_y_lo_marca_visto() {
        val seen = FakeSeen()
        val host = KpknOverlayHostController(tourSeen = seen)
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 60.dp, start = 60.dp).size(120.dp).tourAnchor("a")) {}
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            host.startTour("demo", listOf(step("a", "Primero"), step("a", "Segundo")))
        }
        rule.waitForIdle()

        rule.onNodeWithTag("tour_skip").performClick()
        rule.waitForIdle()

        assertFalse(host.tour.state.value.isActive)
        assertEquals(listOf("demo"), seen.marked)
        rule.onAllNodesWithTag("tour_bubble").assertCountEquals(0)
    }

    @Test
    fun toque_fuera_del_recorte_no_llega_y_dentro_en_pruebalo_si() {
        val host = KpknOverlayHostController(tourSeen = FakeSeen())
        var insideTaps = 0
        var outsideTaps = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .padding(top = 60.dp, start = 60.dp)
                            .size(160.dp)
                            .testTag("dentro")
                            .tourAnchor("btn")
                            .clickable { insideTaps++ },
                    ) {}
                    Box(
                        Modifier
                            .padding(top = 760.dp, start = 60.dp)
                            .size(100.dp)
                            .testTag("fuera")
                            .clickable { outsideTaps++ },
                    ) {}
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            host.startTour(
                "demo",
                listOf(
                    step("btn", "Pruébalo", TourStepKind.TRY),
                    step("btn", "Siguiente paso"),
                ),
            )
        }
        rule.waitForIdle()

        rule.onNodeWithTag("fuera").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(0, outsideTaps)
        assertEquals(0, host.tour.state.value.index)

        rule.onNodeWithTag("dentro").performTouchInput { click() }
        rule.waitForIdle()
        assertEquals(1, insideTaps)
        assertEquals(1, host.tour.state.value.index)
    }

    @Test
    fun atras_y_escape_seguro_en_pruebalo_y_toast_al_completar() {
        val seen = FakeSeen()
        val host = KpknOverlayHostController(tourSeen = seen)
        var tryBtnTaps = 0
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(top = 60.dp, start = 60.dp).size(120.dp).testTag("primera").tourAnchor("a")) {}
                    Box(Modifier.padding(top = 340.dp, start = 60.dp).size(120.dp).testTag("segunda").tourAnchor("b")) {}
                    Box(
                        Modifier
                            .padding(top = 680.dp, start = 60.dp)
                            .size(width = 160.dp, height = 56.dp)
                            .testTag("try_btn")
                            .tourAnchor("btn")
                            .clickable { tryBtnTaps++ },
                    ) {}
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            host.startTour(
                "demo",
                listOf(
                    step("a", "Primero"),
                    TourStep(
                        anchorId = "b",
                        tryAnchorId = "btn",
                        title = "Segundo TRY",
                        body = "Toca el botón o continúa.",
                        kind = TourStepKind.TRY,
                        tryHint = "Toca el botón",
                    ),
                ),
            )
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("tour_prev").assertCountEquals(0)
        rule.onNodeWithText("Siguiente").assertIsDisplayed()

        // Avanza al paso TRY (index 1): ahora aparece Atrás y Continuar.
        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        assertEquals(1, host.tour.state.value.index)
        rule.onNodeWithTag("tour_prev").assertIsDisplayed()
        rule.onNodeWithText("Continuar").assertIsDisplayed()

        // Retrocede a paso 0 con Atrás.
        rule.onNodeWithTag("tour_prev").performClick()
        rule.waitForIdle()
        assertEquals(0, host.tour.state.value.index)

        // Vuelve a paso 1 y sale usando «Continuar» (escape seguro sin tocar try_btn).
        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()

        assertEquals(0, tryBtnTaps)
        assertFalse(host.tour.state.value.isActive)
        assertEquals(listOf("demo"), seen.marked)
        rule.onNodeWithTag("tour_completed_toast").assertIsDisplayed()
    }
}
