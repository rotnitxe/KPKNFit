package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.example.kpkn.screens.onboarding.design.WizardDarkSystemBars
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.welcome.WelcomePages
import com.example.kpkn.screens.onboarding.welcome.WelcomeShell
import com.example.kpkn.screens.onboarding.welcome.rememberAppInForeground
import com.example.kpkn.screens.onboarding.welcome.rememberWelcomeSceneClock
import kotlinx.coroutines.launch

/**
 * Bienvenida previa al wizard: un carrusel de tres páginas (Entreno, Nutrición, Recuperación) donde cada una
 * muestra un teléfono con la app en acción y, debajo, un mensaje breve. Este es el anfitrión con estado: levanta el
 * pager y el reloj de las escenas (que solo corre en la página elegida y con la app en primer plano) y se los pasa
 * a [WelcomeShell], que solo dibuja. Con «reducir movimiento» del sistema las escenas quedan en un cuadro fijo.
 *
 * Contrato con quien la usa: [onStart] al pulsar el botón (por defecto «Comenzar»); [onSecondary] y [onDetails]
 * son opcionales y solo se muestran si se pasan.
 */
@Composable
internal fun SetupWelcomeScreen(
    onStart: () -> Unit,
    actionLabel: String = "Comenzar",
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onDetails: (() -> Unit)? = null,
) {
    WizardDarkSystemBars()
    val reducedMotion = wizardReducedMotion()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { WelcomePages.size })
    val scope = rememberCoroutineScope()
    val inForeground = rememberAppInForeground()
    // El reloj es de la página ya asentada: durante el deslizado la que llega muestra su cuadro fijo y arranca al posarse.
    val clock = rememberWelcomeSceneClock(currentPage = pagerState.settledPage, enabled = inForeground && !reducedMotion)
    WelcomeShell(
        pagerState = pagerState,
        clock = clock,
        reducedMotion = reducedMotion,
        actionLabel = actionLabel,
        onStart = onStart,
        onSelectPage = { index ->
            scope.launch { if (reducedMotion) pagerState.scrollToPage(index) else pagerState.animateScrollToPage(index) }
        },
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary,
        onDetails = onDetails,
    )
}
