package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Duración del bucle de la escena de Entreno, en segundos. */
internal const val WelcomeEntrenoPeriod = 14f

/** Entreno: se crea una sesión y se registran series de un ejercicio. (Marcador: la escena real la escribe otro paso.) */
@Composable
internal fun WelcomeEntrenoScene(t: Float, modifier: Modifier = Modifier) = WelcomeSceneStub("Entreno", t, modifier)
