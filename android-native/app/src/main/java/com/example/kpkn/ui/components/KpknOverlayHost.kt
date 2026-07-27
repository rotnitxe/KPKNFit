package com.example.kpkn.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * Host for glass sheets/dialogs rendered as siblings of MainActivity's [hazeSource].
 *
 * Mirrors the Home overlay registration pattern: entries live in observable state that
 * MainActivity reads directly, so presenting a sheet always triggers a visible recomposition.
 */
class KpknOverlayHostController {
    var entries by mutableStateOf<Map<Any, @Composable () -> Unit>>(emptyMap())
        private set

    fun present(key: Any, content: @Composable () -> Unit) {
        entries = entries + (key to content)
    }

    fun dismiss(key: Any) {
        if (key in entries) {
            entries = entries - key
        }
    }
}

val LocalKpknOverlayHost = staticCompositionLocalOf<KpknOverlayHostController?> { null }

@Composable
fun KpknOverlayHostContent(controller: KpknOverlayHostController) {
    val snapshot = controller.entries
    snapshot.forEach { (entryKey, content) ->
        key(entryKey) {
            content()
        }
    }
}

/**
 * Renders [content] in the root overlay host (sibling of hazeSource) when available.
 * Falls back to in-place full-screen rendering if no host is provided.
 */
@Composable
fun KpknPortal(
    content: @Composable () -> Unit,
) {
    val host = LocalKpknOverlayHost.current
    val key = remember { Any() }
    val latestContent by rememberUpdatedState(content)

    if (host == null) {
        Box(modifier = Modifier.fillMaxSize().zIndex(400f)) {
            content()
        }
        return
    }

    DisposableEffect(host, key) {
        host.present(key) { latestContent() }
        onDispose { host.dismiss(key) }
    }
    // Refresh content pointer after caller recompositions (state inside the sheet/dialog).
    SideEffect {
        host.present(key) { latestContent() }
    }
}

@Composable
fun rememberKpknOverlayHostController(): KpknOverlayHostController =
    remember { KpknOverlayHostController() }
