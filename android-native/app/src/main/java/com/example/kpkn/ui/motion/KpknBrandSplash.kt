package com.example.kpkn.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** Fondo tinta de la marca: el mismo que pinta el splash del sistema (`kpkn_ink`), para que no haya salto. */
private val SplashInk = Color(0xFF121212)

/** Pausa tras el último cuadro de la animación antes de desvanecerse, y espera antes de poder omitirla con un toque. */
private const val HOLD_AFTER_ANIMATION_MS = 350L
private const val SKIP_AFTER_MS = 700L
private const val FADE_OUT_MS = 280

/**
 * Splash animado de marca: al abrir la app, la Torre anidada se apila y aparece KPKN (animación
 * [KpknAnim.APILAR] del kit de marca) sobre el fondo tinta. Sustituye a la pantalla de arranque estática: el
 * splash del sistema solo pinta ese mismo fondo y esta animación continúa desde ahí sin salto.
 *
 * Se dibuja ENCIMA de la app (que ya se está componiendo debajo), así el arranque real no espera a la
 * animación. Se desvanece sola al terminar y un toque la omite pasado un instante. Con las animaciones del
 * sistema apagadas no se muestra ([KpknMotion] avisa de inmediato).
 *
 * @param onFinished se llama una vez, cuando el splash ya se desvaneció del todo; quien lo muestra debe retirarlo.
 */
@Composable
fun KpknBrandSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anim = KpknAnim.APILAR
    val finished by rememberUpdatedState(onFinished)
    var leaving by remember { mutableStateOf(false) }
    var skippable by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(durationMillis = FADE_OUT_MS),
        label = "kpknSplashFade",
        finishedListener = { if (leaving) finished() },
    )
    LaunchedEffect(Unit) {
        delay(SKIP_AFTER_MS)
        skippable = true
    }
    LaunchedEffect(Unit) {
        // KpknMotion mantiene el último cuadro un buen rato; aquí solo hace falta la animación y una pausa corta.
        delay((anim.dur * 1000f).toLong() + HOLD_AFTER_ANIMATION_MS)
        leaving = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(SplashInk)
            // Siempre captura los toques: mientras suena la animación no debe llegar ninguno a la app de debajo.
            .pointerInput(Unit) { detectTapGestures { if (skippable) leaving = true } }
            .semantics { contentDescription = "KPKN" },
    ) {
        KpknMotion(
            anim = anim,
            modifier = Modifier.fillMaxSize(),
            background = SplashInk,
            loop = false,
            onFinished = { leaving = true },
        )
    }
}
