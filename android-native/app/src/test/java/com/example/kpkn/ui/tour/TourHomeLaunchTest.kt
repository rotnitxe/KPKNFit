package com.example.kpkn.ui.tour

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Recorrido de Inicio: con `tourSeen` vacío, el primer paso cae sobre la sesión de hoy; visto, no aparece. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TourHomeLaunchTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeSeen(private val seen: Set<String>) : TourSeenStore {
        override fun seenIds(): Set<String> = seen

        override fun markSeen(tourId: String) {}
    }

    /** Como HomeScreen: el recorrido arranca al cargar la pantalla y la sesión de hoy lleva su ancla. */
    @Composable
    private fun PantallaInicio(host: KpknOverlayHostController) {
        LaunchedEffect(Unit) { host.startTour(TourIds.HOME, TourScripts.home(conTorre = true)) }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .padding(top = 160.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth()
                    .height(96.dp)
                    .testTag("sesion")
                    .tourAnchor(TourAnchors.HOME_SESION),
            ) {}
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
    fun con_tour_seen_vacio_el_primer_paso_aparece_sobre_la_sesion_de_hoy() {
        lanzar(KpknOverlayHostController(tourSeen = FakeSeen(emptySet())))

        rule.onNodeWithText("Sesión de hoy").assertIsDisplayed()
        val recorte = rule.onNodeWithTag("tour_cutout").fetchSemanticsNode().boundsInRoot
        val sesion = rule.onNodeWithTag("sesion").fetchSemanticsNode().boundsInRoot
        assertTrue("El recorte no rodea la sesión de hoy", recorte.covers(sesion))
    }

    @Test
    fun con_tour_home_ya_visto_no_aparece_ningun_paso() {
        lanzar(KpknOverlayHostController(tourSeen = FakeSeen(setOf(TourIds.HOME))))

        rule.onNodeWithText("Sesión de hoy").assertDoesNotExist()
    }

    private fun Rect.covers(inner: Rect): Boolean =
        left <= inner.left && top <= inner.top && right >= inner.right && bottom >= inner.bottom
}
