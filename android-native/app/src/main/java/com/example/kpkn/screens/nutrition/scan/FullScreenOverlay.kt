package com.example.kpkn.screens.nutrition.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.KpknPortal
import com.example.kpkn.ui.design.KpknSurface

/** Capa de las pantallas a pantalla completa de N3: por encima de la hoja del registro (350) y por debajo de los diálogos (360). */
internal const val FULL_SCREEN_OVERLAY_Z = 355f

/**
 * Una pantalla a pantalla completa sobre la hoja del registro de comidas: sale por el anfitrión de superposiciones de la app (el
 * mismo de las hojas y los diálogos, así que un diálogo de confirmación queda por encima), recoge los toques y responde a «Atrás» con
 * [onBack]. Mientras está abierta, la isla superior se oculta como con cualquier hoja.
 */
@Composable
internal fun FullScreenOverlay(onBack: () -> Unit, content: @Composable () -> Unit) {
    DisposableEffect(Unit) {
        KpknModalTracker.onOpened()
        onDispose { KpknModalTracker.onClosed() }
    }
    KpknPortal {
        BackHandler(onBack = onBack)
        Box(
            Modifier.fillMaxSize().zIndex(FULL_SCREEN_OVERLAY_Z).background(KpknSurface.canvas)
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding().navigationBarsPadding().imePadding(),
        ) { content() }
    }
}
