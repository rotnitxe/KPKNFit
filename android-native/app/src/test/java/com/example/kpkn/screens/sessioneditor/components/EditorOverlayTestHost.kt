package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.rememberKpknOverlayHostController

/** Usa el mismo portal a pantalla completa que MainActivity, también al probar una fila aislada. */
@Composable
internal fun EditorOverlayTestHost(content: @Composable () -> Unit) {
    val host = rememberKpknOverlayHostController()
    CompositionLocalProvider(LocalKpknOverlayHost provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            KpknOverlayHostContent(host)
        }
    }
}
