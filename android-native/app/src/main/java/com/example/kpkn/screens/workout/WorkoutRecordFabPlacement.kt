package com.example.kpkn.screens.workout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.workout.components.WorkoutRecordFab

/** Separación entre el borde superior del teclado y el botón «Registrar serie». */
const val RECORD_FAB_IME_GAP_DP = 12f

/** Separación habitual entre el botón y el dock inferior cuando no hay teclado. */
const val RECORD_FAB_DOCK_GAP_DP = 12f

/** Content ends this far above the 60 dp record FAB. */
internal val LiveContentAboveRecordFab = 72.dp

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
 * A single animated offset must drive both the FAB and the content viewport.
 * Otherwise closing IME can reveal controls underneath a FAB that is still moving down.
 */
@Composable
internal fun rememberWorkoutRecordFabBottomOffset(dockBottomClearance: Dp): Dp {
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
    return bottom
}

@Composable
internal fun BoxScope.WorkoutRecordFabHost(
    sessionAccentColor: Color,
    isUpdateMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    dockBottomClearance: Dp,
    onVoiceClick: (() -> Unit)? = null,
    voiceEnabled: Boolean = false,
    bottomOffset: Dp? = null,
) {
    val bottom = bottomOffset ?: rememberWorkoutRecordFabBottomOffset(dockBottomClearance)
    val horizontalSafeInsets = WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)

    onVoiceClick?.let { onVoice ->
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .windowInsetsPadding(horizontalSafeInsets)
                .padding(start = 16.dp, bottom = bottom + 6.dp)
                .shadow(8.dp, CircleShape, clip = false)
                .size(48.dp)
                .clip(CircleShape)
                .testTag("workout-voice-fab")
                .semantics { contentDescription = if (voiceEnabled) "Desactivar voz" else "Activar voz" }
                .clickable(role = Role.Button, onClick = onVoice)
                .zIndex(12f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(if (voiceEnabled) Color(0xFFF2EEE6) else Color(0xFF202020)),
            )
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (voiceEnabled) Color.Black else Color(0xFFF2EEE6),
            )
        }
    }

    WorkoutRecordFab(
        sessionAccentColor = sessionAccentColor,
        isUpdateMode = isUpdateMode,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(horizontalSafeInsets)
            .padding(end = 16.dp, bottom = bottom)
            .zIndex(12f),
    )
}
