package com.example.kpkn.screens.workout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.workout.components.WorkoutRecordFab

/** Separación entre el borde superior del teclado y el botón «Registrar serie». */
const val RECORD_FAB_IME_GAP_DP = 12f

/** Separación habitual entre el botón y el dock inferior cuando no hay teclado. */
const val RECORD_FAB_DOCK_GAP_DP = 12f

/**
 * Margen inferior (medido desde el borde inferior de la ventana) del botón redondo
 * «Registrar serie» de la sesión en vivo. Todos los valores están en dp.
 *
 * - Sin teclado: se conserva el anclaje de siempre, sobre la barra de navegación y el dock
 *   (barra de navegación + altura del dock + 12 dp).
 * - Con teclado visible: el botón sube hasta quedar [imeGapDp] por encima del teclado. La altura
 *   del teclado ya incluye la zona de la barra de navegación y tapa el dock, así que no se suma
 *   ninguno de los dos (sumarlos dejaba el botón demasiado alto sobre los campos que se editan).
 *
 * Con la ventana en modo borde a borde, nada reserva espacio para el teclado: el botón se
 * quedaba detrás de él aunque siguiera en el árbol de accesibilidad.
 */
fun resolveRecordFabBottomOffsetDp(
    imeBottomDp: Float,
    navigationBarBottomDp: Float,
    dockBottomClearanceDp: Float,
    imeGapDp: Float = RECORD_FAB_IME_GAP_DP,
    dockGapDp: Float = RECORD_FAB_DOCK_GAP_DP,
): Float {
    val ime = imeBottomDp.coerceAtLeast(0f)
    return if (ime > 0f) {
        ime + imeGapDp
    } else {
        navigationBarBottomDp.coerceAtLeast(0f) +
            dockBottomClearanceDp.coerceAtLeast(0f) +
            dockGapDp
    }
}

/**
 * Botón «Registrar serie» anclado al teclado (ver [resolveRecordFabBottomOffsetDp]).
 *
 * Lee los insets aquí dentro y no en la pantalla: así solo se recompone este botón mientras el
 * teclado se anima, no todo el cuerpo del entrenamiento. El margen se anima para que el botón
 * suba y baje con suavidad también en Android sin animación nativa del teclado.
 */
@Composable
internal fun BoxScope.WorkoutRecordFabHost(
    sessionAccentColor: Color,
    isUpdateMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    dockBottomClearance: Dp,
) {
    val density = LocalDensity.current
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val navigationBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val targetBottom = resolveRecordFabBottomOffsetDp(
        imeBottomDp = imeBottomDp.value,
        navigationBarBottomDp = navigationBarBottomDp.value,
        dockBottomClearanceDp = dockBottomClearance.value,
    ).dp
    val bottom by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "recordFabBottom",
    )
    WorkoutRecordFab(
        sessionAccentColor = sessionAccentColor,
        isUpdateMode = isUpdateMode,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = bottom)
            .zIndex(12f),
    )
}
