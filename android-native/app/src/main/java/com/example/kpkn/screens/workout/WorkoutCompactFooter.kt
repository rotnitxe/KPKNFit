package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Altura que ocupa «Registrar serie» entre la tira y la isla: 56 dp de píldora y 12 dp de aire. */
internal const val WORKOUT_RECORD_PILL_SLOT_DP = 68

/**
 * Pie compacto de la sesión en vivo (KPKN LS2b, §2 punto 3), de arriba abajo: la tira de series, «Registrar serie» y la isla de LS2a.
 *
 * Con el teclado abierto, la tira y la isla quedan debajo del teclado (como la barra del dock de antes) y «Registrar serie» sube
 * sobre él: es la única pieza que sigue a la vista mientras se escribe. Los insets del teclado se leen aquí dentro, para que solo se
 * recomponga este pie mientras el teclado se anima.
 */
@Composable
internal fun WorkoutCompactFooter(
    stripSlot: @Composable () -> Unit,
    recordSlot: @Composable () -> Unit,
    island: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = !imeOpen,
            enter = fadeIn(tween(durationMillis = 120)),
            exit = fadeOut(tween(durationMillis = 120)),
        ) {
            stripSlot()
        }
        recordSlot()
        AnimatedVisibility(
            visible = !imeOpen,
            enter = fadeIn(tween(durationMillis = 120)),
            exit = fadeOut(tween(durationMillis = 120)),
        ) {
            island()
        }
    }
}
