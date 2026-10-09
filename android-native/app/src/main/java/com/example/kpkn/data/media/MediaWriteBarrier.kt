package com.example.kpkn.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** Closes admission before draining, including work accepted outside a ViewModel scope. */
internal class MediaWriteBarrier {
    private val monitor = Any()
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inherited = ThreadLocal<Permit?>()
    private val active = mutableSetOf<Permit>()
    private var paused = false
    private var generation = 0L

    private class Permit(val generation: Long) {
        val finished = CompletableDeferred<Unit>()
    }

    fun session(): Long = synchronized(monitor) {
        if (paused) throw unavailable()
        generation
    }

    private fun acquire(session: Long): Permit = synchronized(monitor) {
        // An accepted worker may finish its nested ingestion while admission is closed.
        val parent = inherited.get()
        val nested = parent != null && parent.generation == session && parent in active
        if (!nested && (paused || session != generation)) throw unavailable()
        Permit(session).also(active::add)
    }

    private fun release(permit: Permit) = synchronized(monitor) {
        active.remove(permit)
        permit.finished.complete(Unit)
        Unit
    }

    suspend fun <T> write(session: Long, block: suspend () -> T): T {
        val permit = acquire(session)
        try {
            return withContext(Dispatchers.IO + inherited.asContextElement(permit)) { block() }
        } finally {
            release(permit)
        }
    }

    fun <T> immediate(session: Long, block: () -> T): T {
        val permit = acquire(session)
        val previous = inherited.get()
        try {
            inherited.set(permit)
            return block()
        } finally {
            inherited.set(previous)
            release(permit)
        }
    }

    /** Denials are failed Deferreds so late ActivityResults reach the normal error observer. */
    fun <T> submit(session: Long, start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend () -> T): Deferred<T> {
        val permit = try { acquire(session) } catch (error: IOException) {
            return CompletableDeferred<T>().also { it.completeExceptionally(error) }
        }
        val work = owner.async(inherited.asContextElement(permit), start = CoroutineStart.LAZY) { block() }
        work.invokeOnCompletion { release(permit) }
        if (start != CoroutineStart.LAZY) work.start()
        return work
    }

    suspend fun pauseAndAwait() {
        synchronized(monitor) {
            if (!paused) {
                paused = true
                generation++ // Old repository references remain invalid after reopening.
            }
        }
        withTimeout(30_000) {
            while (true) {
                val pending = synchronized(monitor) { active.map { it.finished } }
                if (pending.isEmpty()) break
                pending.forEach { it.await() }
            }
        }
    }

    fun resume() = synchronized(monitor) {
        if (paused) {
            check(active.isEmpty()) { "Media writers have not stopped" }
            paused = false
        }
    }

    private fun unavailable() = IOException("Los datos personales están cambiando. Volvé a abrir la captura.")
}
