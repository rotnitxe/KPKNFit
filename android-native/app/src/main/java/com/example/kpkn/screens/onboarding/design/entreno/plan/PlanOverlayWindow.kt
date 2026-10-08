package com.example.kpkn.screens.onboarding.design.entreno.plan

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer
import kotlin.math.roundToInt

/*
 * La ventana común de los dos overlays de pantalla completa de este paquete (el «preparando» y el detalle del
 * programa): un `Dialog` sin ancho ni márgenes de plataforma, con el desenfoque del sistema detrás cuando el equipo lo
 * soporta (Android 12+) y un velo casi opaco cuando no. Es el mismo patrón que `ModuleCompleteOverlay`.
 */

/** Radio máximo del desenfoque del sistema detrás del overlay (píxeles), el mismo que el del overlay de hito. */
private const val BLUR_RADIUS_PX = 64

/** Color del velo del overlay (casi negro, el de la página). */
internal val OverlayScrim = Color(0xFF060606)

/**
 * Fuerza la rama del desenfoque de los overlays: `true` pide el desenfoque del sistema detrás de la ventana, `false` el velo
 * casi opaco de los equipos sin él, y `null` (lo normal) deja que decida el sistema. Solo lo pone el arnés de depuración (extra
 * `blur=on|off`) para ver las dos ramas en un teléfono que tiene el desenfoque desactivado.
 */
internal val LocalOverlayBlurOverride = compositionLocalOf<Boolean?> { null }

/**
 * La densidad (y escala de letra) que simula el arnés de depuración (extras `width` y `fontScale`): una ventana de diálogo trae
 * la suya, no hereda la que el arnés pone a la actividad, y sin esto los overlays se verían siempre a escala normal. `null` (lo
 * normal): la del propio diálogo, que es la del sistema.
 */
internal val LocalOverlayDensityOverride = compositionLocalOf<Density?> { null }

/**
 * Un overlay a pantalla completa. [shown] (0 a 1, se lee sin recomponer) lleva el desenfoque del sistema: entra y sale con
 * el contenido, para que no desaparezca de golpe. [content] recibe si hay desenfoque real: sin él el velo debe tapar casi
 * todo, o el texto de la página de atrás se mezcla con el del overlay.
 */
@Composable
internal fun BlurOverlayDialog(
    onDismissRequest: () -> Unit,
    dismissOnBack: Boolean,
    shown: () -> Float,
    content: @Composable (blur: Boolean) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBack,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        // El permiso de desenfoque del sistema puede cambiar con el overlay a la vista (ahorro de batería, «reducir transparencias»):
        // se escucha, y con el desenfoque apagado el velo vuelve a ser casi opaco en vez de dejar el texto de atrás medio visible.
        var systemBlur by remember { mutableStateOf(Build.VERSION.SDK_INT >= 31 && window?.windowManager?.isCrossWindowBlurEnabled == true) }
        if (Build.VERSION.SDK_INT >= 31) {
            DisposableEffect(window) {
                val manager = window?.windowManager
                val listener = Consumer<Boolean> { enabled -> systemBlur = enabled }
                runCatching { manager?.addCrossWindowBlurEnabledListener(view.context.mainExecutor, listener) }
                onDispose { runCatching { manager?.removeCrossWindowBlurEnabledListener(listener) } }
            }
        }
        val blur = LocalOverlayBlurOverride.current ?: systemBlur
        LaunchedEffect(window, blur) {
            window ?: return@LaunchedEffect
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= 31 && blur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                snapshotFlow { shown() }.collect { v ->
                    window.attributes = window.attributes.also { it.blurBehindRadius = (BLUR_RADIUS_PX * v).roundToInt() }
                }
            }
        }
        val simulatedDensity = LocalOverlayDensityOverride.current
        if (simulatedDensity != null) {
            CompositionLocalProvider(LocalDensity provides simulatedDensity) { content(blur) }
        } else {
            content(blur)
        }
    }
}

/** Un toque sin la onda de Material (el pulsado se expresa con un apagado leve en cada pieza), con su rol y su etiqueta de acción. */
internal fun Modifier.plainClickable(
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = role,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
}
