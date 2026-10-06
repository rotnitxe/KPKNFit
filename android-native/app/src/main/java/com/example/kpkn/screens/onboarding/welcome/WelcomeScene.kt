package com.example.kpkn.screens.onboarding.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Contrato de las escenas de la pantalla de bienvenida.
 *
 * Cada apartado base de KPKN (Entreno, Nutrición, Recuperación) tiene una ESCENA: la app simulada dentro de un
 * teléfono, que se reproduce sola en bucle (toques, desplazamientos, números que cambian).
 *
 *  - Una escena es una función PURA de `t` (segundos desde el inicio de su bucle, 0 ≤ t < periodo): no usa
 *    corrutinas, `Animatable` ni `withFrameNanos`. Quien la muestra (SetupWelcomeScreen) lleva el reloj, la
 *    repite, la pausa cuando no está a la vista y, con movimiento reducido, muestra un cuadro fijo.
 *  - Se compone SIEMPRE en el lienzo lógico [WelcomeSceneSize] (300 × 620 dp, la pantalla de un teléfono
 *    moderno); la bienvenida lo escala al tamaño real del teléfono dibujado. Así las letras y los márgenes se ven
 *    iguales en cualquier pantalla y no hay que medir nada.
 *  - La lógica de qué se ve en cada instante (qué fila existe, qué número vale) va en funciones puras con prueba
 *    unitaria; el composable solo la dibuja.
 *  - Los textos de las escenas están en español, son de ejemplo y se ven reales (nada de «Lorem ipsum»).
 */

/** Lienzo lógico de todas las escenas. */
internal val WelcomeSceneSize = DpSize(300.dp, 620.dp)

/** Paleta de las escenas (la de marca del kit y la de la demo del wizard). */
internal object WelcomeScenePalette {
    /** Fondo de la pantalla simulada. */
    val screen = Color(0xFF0E0F10)
    val panel = Color(0xFF1A1B1D)
    val line = Color(0xFF2A2B2E)
    val ink = Color(0xFFF2EEE6)
    val muted = Color(0xFF9A958D)
    val ok = Color(0xFF43D18C)
    val danger = Color(0xFFFF9B92)

    /** Los tres anillos de recuperación y la mente. */
    val columna = Color(0xFF8FB2FF)
    val musculo = Color(0xFFF49A6E)
    val energia = Color(0xFFF7CF73)
    val mente = Color(0xFFC9B8FF)

    /** Los macros, como en la pantalla Nutrición de la app. */
    val calorias = Color(0xFF42A5F5)
    val proteina = Color(0xFFEF5350)
    val hidratos = Color(0xFF7E57C2)
    val grasas = Color(0xFF26A69A)
}

/** Marcador temporal de una escena aún sin dibujar: título y reloj. Cada escena lo sustituye por la real. */
@Composable
internal fun WelcomeSceneStub(name: String, t: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(WelcomeScenePalette.screen),
        contentAlignment = Alignment.Center,
    ) {
        Text("$name · ${"%.1f".format(t)} s", color = WelcomeScenePalette.ink, fontSize = 16.sp)
    }
}
