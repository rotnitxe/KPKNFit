package com.example.kpkn.ui.tour

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recorrido de Inicio:
 * - Con `tourSeen` vacío, muestra la invitación suave no bloqueante (no oscurece la app al entrar).
 * - Pulsar «Empezar» lanza el recorrido sobre la sesión de hoy.
 * - Pulsar «Luego» o entrar con `TourIds.HOME` visto oculta la invitación y no muestra el overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TourHomeLaunchTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeSeen(initial: Set<String> = emptySet()) : TourSeenStore {
        private val seen = initial.toMutableSet()

        override fun seenIds(): Set<String> = seen.toSet()

        override fun markSeen(tourId: String) {
            seen += tourId
        }
    }

    @Composable
    private fun PantallaInicio(host: KpknOverlayHostController) {
        val tourUi by host.tour.state.collectAsState()
        val showInvite = !host.isTourSeen(TourIds.HOME) && !tourUi.isActive
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
                if (showInvite) {
                    HomeTourInviteBanner(
                        onStart = { host.startTour(TourIds.HOME, TourScripts.home()) },
                        onLater = { host.markTourSeen(TourIds.HOME) },
                    )
                }
                Box(
                    Modifier
                        .padding(top = 64.dp)
                        .fillMaxWidth()
                        .height(96.dp)
                        .testTag("sesion")
                        .tourAnchor(TourAnchors.HOME_SESION),
                ) {}
            }
            KpknOverlayHostContent(host)
        }
    }

    private fun lanzar(host: KpknOverlayHostController) {
        rule.setContent {
            CompositionLocalProvider(
                LocalKpknOverlayHost provides host,
                LocalWizardReducedMotion provides true,
            ) {
                PantallaInicio(host)
            }
        }
    }

    @Test
    fun con_tour_seen_vacio_muestra_invitacion_suave_y_empezar_lanza_sobre_sesion() {
        lanzar(KpknOverlayHostController(tourSeen = FakeSeen(emptySet())))

        rule.onNodeWithTag("tour_invite_banner").assertIsDisplayed()
        rule.onNodeWithTag("tour_bubble").assertDoesNotExist()

        rule.onNodeWithTag("tour_invite_start").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("tour_invite_banner").assertDoesNotExist()
        rule.onNodeWithText("Sesión de hoy").assertIsDisplayed()
        val recorte = rule.onNodeWithTag("tour_cutout").fetchSemanticsNode().boundsInRoot
        val sesion = rule.onNodeWithTag("sesion").fetchSemanticsNode().boundsInRoot
        assertTrue("El recorte no rodea la sesión de hoy", recorte.covers(sesion))
    }

    @Test
    fun pulsar_luego_oculta_invitacion_y_marca_visto() {
        val seen = FakeSeen(emptySet())
        lanzar(KpknOverlayHostController(tourSeen = seen))

        rule.onNodeWithTag("tour_invite_later").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("tour_invite_banner").assertDoesNotExist()
        rule.onNodeWithTag("tour_bubble").assertDoesNotExist()
        assertTrue(TourIds.HOME in seen.seenIds())
    }

    @Test
    fun con_tour_home_ya_visto_no_aparece_invitacion_ni_pasos() {
        lanzar(KpknOverlayHostController(tourSeen = FakeSeen(setOf(TourIds.HOME))))

        rule.onNodeWithTag("tour_invite_banner").assertDoesNotExist()
        rule.onNodeWithText("Sesión de hoy").assertDoesNotExist()
    }

    private fun Rect.covers(inner: Rect): Boolean =
        left <= inner.left && top <= inner.top && right >= inner.right && bottom >= inner.bottom
}
