package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Duración del bucle de la escena de Nutrición, en segundos. */
internal const val WelcomeNutricionPeriod = 13f

/** Nutrición: se describe una comida con palabras propias y KPKN calcula calorías y macros. (Marcador.) */
@Composable
internal fun WelcomeNutricionScene(t: Float, modifier: Modifier = Modifier) = WelcomeSceneStub("Nutrición", t, modifier)
