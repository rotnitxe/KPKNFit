package com.example.kpkn.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.kpkn.ui.components.LocalKpknOverlayHost

/**
 * Límites, en coordenadas de raíz, de cada ancla visible. Los escribe [tourAnchor] y los leen el
 * controlador (¿está el ancla?) y el overlay (dónde va el recorte). También guarda callbacks de scroll
 * opcionales por ancla para revelar elementos fuera del viewport antes de mostrar el recorte.
 */
class TourAnchorRegistry {
    private class Anchor(val owner: Any, val bounds: Rect)

    private val anchors = mutableStateMapOf<String, Anchor>()
    private val scrollers = mutableMapOf<String, suspend () -> Unit>()

    /** Límites actuales del ancla [id], o null si no está compuesta. */
    fun boundsOf(id: String): Rect? = anchors[id]?.bounds

    fun contains(id: String): Boolean = anchors.containsKey(id)

    fun hasScroller(id: String): Boolean = scrollers.containsKey(id)

    fun registerScroller(anchorId: String, onReveal: suspend () -> Unit) {
        scrollers[anchorId] = onReveal
    }

    fun unregisterScroller(anchorId: String, onReveal: suspend () -> Unit) {
        if (scrollers[anchorId] === onReveal) scrollers.remove(anchorId)
    }

    suspend fun reveal(anchorId: String): Boolean {
        val scroller = scrollers[anchorId] ?: return false
        scroller()
        return true
    }

    internal fun put(id: String, owner: Any, bounds: Rect) {
        val current = anchors[id]
        if (current != null && current.owner === owner && current.bounds == bounds) return
        anchors[id] = Anchor(owner, bounds)
    }

    /** Quita el ancla solo si la registró este mismo elemento; dos elementos con el mismo id no se borran entre sí. */
    internal fun remove(id: String, owner: Any) {
        if (anchors[id]?.owner === owner) anchors.remove(id)
    }
}

/**
 * Registra un callback suspendible para revelar [anchorId] (p. ej. `LazyListState.animateScrollToItem` o
 * `ScrollState.animateScrollTo`) antes de que el overlay pinte el recorte.
 */
@Composable
fun TourAnchorScroller(
    anchorId: String,
    onReveal: suspend () -> Unit,
) {
    val host = LocalKpknOverlayHost.current
    val registry = host?.tourAnchors
    val latestOnReveal by rememberUpdatedState(onReveal)
    val wrapper = remember(anchorId) {
        val block: suspend () -> Unit = { latestOnReveal() }
        block
    }
    DisposableEffect(registry, anchorId, wrapper) {
        registry?.registerScroller(anchorId, wrapper)
        onDispose { registry?.unregisterScroller(anchorId, wrapper) }
    }
}

/**
 * Marca el elemento como ancla [id] del recorrido: registra sus límites mientras está compuesto y avisa de
 * cada toque completo al controlador. Observa sin consumir el toque, así el elemento reacciona igual.
 */
fun Modifier.tourAnchor(id: String): Modifier = composed {
    val host = LocalKpknOverlayHost.current
    val registry = host?.tourAnchors
    val owner = remember { Any() }
    DisposableEffect(registry, id) {
        onDispose { registry?.remove(id, owner) }
    }
    Modifier
        .onGloballyPositioned { coordinates -> registry?.put(id, owner, coordinates.boundsInRoot()) }
        .pointerInput(host, id) {
            awaitPointerEventScope {
                observeTaps(viewConfiguration.touchSlop) { host?.tour?.onAnchorTapped(id) }
            }
        }
}

/** Toque completo (bajada y subida sin salir del umbral de arrastre), en la fase inicial y sin consumirlo. */
private suspend fun AwaitPointerEventScope.observeTaps(touchSlop: Float, onTap: () -> Unit) {
    while (true) {
        val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.changedToDown() } ?: continue
        var moved = false
        var upped = false
        var lost = false
        while (!moved && !upped && !lost) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            when {
                change == null -> lost = true
                change.changedToUp() -> upped = true
                (change.position - down.position).getDistance() > touchSlop -> moved = true
            }
        }
        if (upped) onTap()
    }
}
