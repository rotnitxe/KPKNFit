package com.example.kpkn.ui.tour

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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
import com.example.kpkn.ui.components.rememberKpknOverlayHostController
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

    @Test
    fun recorrido_con_lazy_column_y_tour_anchor_scroller_no_falla_con_host_de_compose() {
        val seen = FakeSeen()
        lateinit var host: KpknOverlayHostController
        rule.setContent {
            host = rememberKpknOverlayHostController(tourSeen = seen)
            val listState = rememberLazyListState()
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                TourAnchorScroller("item_0") { listState.animateScrollToItem(0) }
                TourAnchorScroller("item_4") { listState.animateScrollToItem(4) }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(5) { idx ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .padding(16.dp)
                                    .tourAnchor("item_$idx"),
                            ) {}
                        }
                    }
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            assertTrue(host.tourAnchors.hasScroller("item_4"))
            host.startTour("scroll-compose", listOf(step("item_0", "Inicio"), step("item_4", "Fondo")))
        }
        rule.waitForIdle()
        rule.onNodeWithText("Inicio").assertIsDisplayed()

        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Fondo").assertIsDisplayed()

        rule.onNodeWithTag("tour_prev").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Inicio").assertIsDisplayed()

        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()

        assertFalse(host.tour.state.value.isActive)
        assertEquals(listOf("scroll-compose"), seen.marked)
        rule.onNodeWithTag("tour_completed_toast").assertIsDisplayed()
    }

    @Test
    fun recorrido_con_lazy_column_y_controlador_sin_reloj_usa_reloj_del_scroller() {
        val seen = FakeSeen()
        val host = KpknOverlayHostController(tourSeen = seen)
        rule.setContent {
            val listState = rememberLazyListState()
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                TourAnchorScroller("item_0") { listState.animateScrollToItem(0) }
                TourAnchorScroller("item_4") { listState.animateScrollToItem(4) }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(5) { idx ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .padding(16.dp)
                                    .tourAnchor("item_$idx"),
                            ) {}
                        }
                    }
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            assertTrue(host.tourAnchors.hasScroller("item_4"))
            host.startTour("scroll-fallback", listOf(step("item_0", "Arriba"), step("item_4", "Abajo")))
        }
        rule.waitForIdle()
        rule.onNodeWithText("Arriba").assertIsDisplayed()

        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Abajo").assertIsDisplayed()

        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()

        assertFalse(host.tour.state.value.isActive)
        assertEquals(listOf("scroll-fallback"), seen.marked)
    }

    @Test
    fun transicion_animada_de_ancla_grande_a_pequena_no_lanza_por_overshoot_de_resorte() {
        val seen = FakeSeen()
        val host = KpknOverlayHostController(tourSeen = seen)
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides false,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(16.dp)
                            .tourAnchor("wide"),
                    ) {}
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 24.dp)
                            .size(28.dp)
                            .tourAnchor("tiny"),
                    ) {}
                    KpknOverlayHostContent(host)
                }
            }
        }
        rule.runOnIdle {
            host.startTour("spring", listOf(step("wide", "Grande"), step("tiny", "Pequeno")))
        }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("tour_next").performClick()
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        rule.onNodeWithText("Pequeno").assertIsDisplayed()
        rule.onNodeWithTag("tour_next").performClick()
        rule.waitForIdle()
        assertFalse(host.tour.state.value.isActive)
        assertEquals(listOf("spring"), seen.marked)
    }
}
