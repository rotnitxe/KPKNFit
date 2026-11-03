package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Program
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Programa provisional del editor de una importación. La persistencia pertenece al borrador
 * de la entrada que lo abre; nunca escribe en el repositorio de programas de la biblioteca.
 */
class ImportedProgramEditorStore internal constructor(
    initial: Program,
    private val persist: suspend (Program) -> Boolean,
) {
    internal val instanceKey: String = java.util.UUID.randomUUID().toString()
    /** Vive con el borrador durante una rotación; se limpia al salir del editor de forma segura. */
    internal val editorViewModels = ViewModelStore()
    private val mutex = Mutex()
    private var closed = false
    private val _program = MutableStateFlow(initial)
    val program: StateFlow<Program> = _program.asStateFlow()

    /** Publica solo una escritura durable y nunca permite cambiar la identidad del borrador. */
    suspend fun mutate(transform: (Program) -> Program?): Boolean =
        mutateThrough(transform, forcePersist = false, persistNext = persist)

    /** Lets the owning draft commit adjacent metadata in the same durable write as a Program update. */
    internal suspend fun mutateThrough(
        transform: (Program) -> Program?,
        forcePersist: Boolean,
        persistNext: suspend (Program) -> Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock false
            val current = _program.value
            val next = transform(current) ?: return@withLock false
            if (next.id != current.id) return@withLock false
            if (next == current && !forcePersist) return@withLock true
            withContext(NonCancellable) {
                val saved = try {
                    persistNext(next)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    false
                }
                if (saved) _program.value = next
                saved
            }
        }
    }

    /** Espera cualquier escritura en curso y evita que un editor antiguo vuelva a escribir. */
    internal suspend fun closeForMutations() = withContext(Dispatchers.IO) {
        mutex.withLock { closed = true }
    }
}
