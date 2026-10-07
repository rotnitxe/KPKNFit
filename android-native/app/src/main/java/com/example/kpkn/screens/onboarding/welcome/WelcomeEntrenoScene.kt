package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Duración del bucle de la escena de Entreno, en segundos. */
internal const val WelcomeEntrenoPeriod = 16f

/** Entreno: se crea una sesión y se registran series de un ejercicio. (Marcador: el dibujo real llega en el siguiente cambio.) */
@Composable
internal fun WelcomeEntrenoScene(t: Float, modifier: Modifier = Modifier) = WelcomeSceneStub("Entreno", t, modifier)
