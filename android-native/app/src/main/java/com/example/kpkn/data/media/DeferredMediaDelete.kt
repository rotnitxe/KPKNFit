package com.example.kpkn.data.media

import com.example.kpkn.data.models.WorkoutMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Borrado aplazado de fotos y vídeos con «Deshacer» (LV8, álbum por ejercicio).
 *
 * Al borrar, el elemento deja de verse enseguida pero NO se toca nada: ni su fila ni sus archivos. El borrado definitivo ([commit])
 * ocurre al caducar el aviso ([undoWindowMs], unos 5 s), al llegar otro borrado (el aviso anterior caduca) o al salir del álbum
 * ([flush]); nunca antes. Si se deshace ([undo]), no se toca nada.
 *
 * Vive en `data/` y no sabe de pantallas: quien lo usa le da el [scope] del que cuelga el temporizador y el [commit] que borra de verdad
 * (`WorkoutMediaRepository.delete`, que quita la fila y los archivos). [commit] no suspende: quien lo da decide en qué hilo corre.
 */
class DeferredMediaDelete(
    private val scope: CoroutineScope,
    private val commit: (List<WorkoutMedia>) -> Unit,
    private val undoWindowMs: Long = UNDO_WINDOW_MS,
    /** El borrado definitivo falló: lo que no se pudo borrar sigue en su sitio (la fila y los archivos siguen ahí). */
    private val onCommitFailed: (List<WorkoutMedia>, Throwable) -> Unit = { _, _ -> },
) {
    /** Lo borrado que aún se puede deshacer. [token] distingue un aviso del siguiente. */
    data class Pending(val items: List<WorkoutMedia>, val token: Long)

    private val lock = Any()
    private var timer: Job? = null
    private var counter = 0L
    private val _pending = MutableStateFlow<Pending?>(null)
    private val _committedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Sigue oculto entre el fin del aviso y la siguiente lectura de Room: no reaparece mientras se borra. */
    val committedIds: StateFlow<Set<String>> = _committedIds.asStateFlow()

    /** Si falla el trabajo asíncrono, el registro vuelve a verse y puede reintentarse. */
    fun restoreFailed(items: List<WorkoutMedia>) {
        synchronized(lock) { _committedIds.value = _committedIds.value - items.map { it.id }.toSet() }
    }

    /** El borrado en espera (el que muestra «Deshacer»), o nulo. Sus elementos no se ven. */
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** Ids de lo borrado en espera: la pantalla los quita de su lista. */
    fun hiddenIds(): Set<String> = _committedIds.value + (_pending.value?.items?.map { it.id } ?: emptyList())

    /** Borra [items] con «Deshacer». Un borrado anterior todavía en espera caduca ahora: solo el último se puede deshacer. */
    fun schedule(items: List<WorkoutMedia>) {
        if (items.isEmpty()) return
        val previous: Pending?
        synchronized(lock) {
            previous = _pending.value
            previous?.let { _committedIds.value = _committedIds.value + it.items.map { item -> item.id } }
            timer?.cancel()
            val mine = Pending(items = items, token = ++counter)
            _pending.value = mine
            timer = scope.launch {
                delay(undoWindowMs)
                expire(mine.token)
            }
        }
        if (previous != null) commitSafely(previous.items)
    }

    /** Deshace el último borrado: vuelve a verse y no se toca nada. Devuelve lo que volvió, o nulo si ya no había nada en espera. */
    fun undo(): List<WorkoutMedia>? {
        synchronized(lock) {
            val current = _pending.value ?: return null
            timer?.cancel()
            timer = null
            _pending.value = null
            return current.items
        }
    }

    /** Sale del álbum: lo que seguía en espera se borra ya (el aviso deja de poder deshacerse). */
    fun flush() {
        val current = synchronized(lock) {
            val pending = _pending.value
            pending?.let { _committedIds.value = _committedIds.value + it.items.map { item -> item.id } }
            timer?.cancel()
            timer = null
            _pending.value = null
            pending
        } ?: return
        commitSafely(current.items)
    }

    private fun expire(token: Long) {
        val done = synchronized(lock) {
            val current = _pending.value
            if (current != null && current.token == token) {
                _committedIds.value = _committedIds.value + current.items.map { it.id }
                _pending.value = null
                timer = null
                current
            } else {
                null
            }
        } ?: return
        commitSafely(done.items)
    }

    private fun commitSafely(items: List<WorkoutMedia>) {
        try {
            commit(items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            restoreFailed(items)
            onCommitFailed(items, failure)
        }
    }

    companion object {
        /** Lo que dura el aviso «Borrado · Deshacer». */
        const val UNDO_WINDOW_MS = 5_000L
    }
}
