package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.workout.components.LocalLivePagerWorkingSetVisualHeightPx
import com.example.kpkn.screens.workout.components.publishLivePagerWorkingSetVisualHeight

/** Fixed frame per approved viewport: 312×394, 345×470 and 364×488. */
internal fun liveSessionCardWidthDp(screenWidthDp: Int): Dp =
    (screenWidthDp - 48).coerceIn(240, 364).dp

/**
 * The height comes from the viewport, never from the content: 360×740 → 394 dp,
 * 360×880 (Flip) → 430 dp, 393×852 → 470 dp, 412×1007 → 488 dp. Keeping it independent of the card width
 * prevents the frame from changing when text scaling or the keyboard alter the
 * available space.
 */
internal fun liveSessionCardHeightDp(screenWidthDp: Int, screenHeightDp: Int = 0): Dp {
    val base = when {
        screenWidthDp <= 380 -> 394.dp
        screenWidthDp <= 405 -> 470.dp
        else -> 488.dp
    }
    // Con pantalla estrecha y alta la tarjeta crece un poco para respirar (pedido del usuario, 29-oct): +36 dp en el Flip (360 × 880 →
    // 430). Las anchas ya tienen su tarjeta alta: sumarles 36 haría que el roadmap tapase el pie (393 × 852).
    return if (base == 394.dp && screenHeightDp >= LiveCardTallScreenHeightDp) base + 36.dp else base
}

/** Desde este alto de pantalla la tarjeta usa su altura amplia. */
internal const val LiveCardTallScreenHeightDp = 820

private val LocalLiveSessionCardHeight = staticCompositionLocalOf { 0.dp }
internal val LocalLiveSessionCompactLayout = staticCompositionLocalOf { false }

/** @deprecated La franja permanente del Relator se retiró (Refinamiento 02). Se conserva para el andamiaje de pruebas. */
@Deprecated("La franja del Relator se retiró en Refinamiento 02")
/**
 * Altura de la banda del relator en vivo para un viewport dado. Compartido por el
 * layout de producción y los contratos visuales (CAL-3/QA) de `sharedTest`, cuyo
 * compilación no ve internals de main; por eso es público además de puro.
 */
fun liveSessionRelatorBandHeight(viewportHeightDp: Float, headerHeightDp: Float, cardHeightDp: Float, bottomReservedDp: Float): Float =
    (viewportHeightDp - headerHeightDp - cardHeightDp - bottomReservedDp - 12f).coerceAtLeast(0f)

@Composable
internal fun liveSessionCardHeight(): Dp {
    val supplied = LocalLiveSessionCardHeight.current
    if (supplied > 0.dp) return supplied
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        liveSessionCardHeightDp(configuration.screenWidthDp, configuration.screenHeightDp)
    }
}

/** A single measured frame for recording, preparation, rest and both reverse faces. */
@Composable
internal fun LiveSessionCardFrame(content: @Composable BoxScope.() -> Unit) {
    val measuredHeight = LocalLivePagerWorkingSetVisualHeightPx.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val frameWidth = liveSessionCardWidthDp(screenWidthDp)
    val frameHeight = liveSessionCardHeightDp(screenWidthDp, LocalConfiguration.current.screenHeightDp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.width(frameWidth).height(frameHeight).testTag("live-card-frame")
                .onSizeChanged { publishLivePagerWorkingSetVisualHeight(measuredHeight, it.height) },
        ) {
            CompositionLocalProvider(LocalLiveSessionCardHeight provides frameHeight) { content() }
        }
    }
}
