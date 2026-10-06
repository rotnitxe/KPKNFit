package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Duración del bucle de la escena de Recuperación, en segundos. */
internal const val WelcomeRingsPeriod = 12f

/** Recuperación: los Rings (columna, músculo, energía) cambian de porcentaje con el entreno y el descanso. (Marcador.) */
@Composable
internal fun WelcomeRingsScene(t: Float, modifier: Modifier = Modifier) = WelcomeSceneStub("Recuperación", t, modifier)
