package com.example.kpkn.data.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

internal class DataSessionClosedException(message: String) : IOException(message)

/** Closes admission before draining, including work accepted outside a ViewModel scope. */
internal class MediaWriteBarrier {
    private val monitor = Any()
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inherited = ThreadLocal<Permit?>()
    private val active = mutableSetOf<Permit>()
    private var paused = false
    private var generation = 0L
    private var lastRoot = 0L

    private class Permit(val generation: Long, val root: Long) {
        val finished = CompletableDeferred<Unit>()
    }

    fun session(): Long = synchronized(monitor) {
        if (paused) {
            val parent = inherited.get()
            if (parent != null && parent in active) return@synchronized parent.generation
            throw unavailable()
        }
        generation
    }

    fun admissionCheckpoint(): Long = synchronized(monitor) { lastRoot }

    fun hasAcceptedParent(session: Long, admittedThrough: Long = Long.MAX_VALUE): Boolean = synchronized(monitor) {
        val parent = inherited.get()
        parent != null && parent.generation == session && parent.root <= admittedThrough && parent in active
    }

    private fun acquire(session: Long): Permit = synchronized(monitor) {
        // An accepted worker may finish its nested ingestion while admission is closed.
        val parent = inherited.get()
        val nested = parent != null && parent.generation == session && parent in active
        if (!nested && (paused || session != generation)) throw unavailable()
        Permit(session, if (nested) requireNotNull(parent).root else ++lastRoot).also(active::add)
    }

    private fun release(permit: Permit) = synchronized(monitor) {
        active.remove(permit)
        permit.finished.complete(Unit)
        Unit
    }

    suspend fun <T> write(session: Long, block: suspend () -> T): T =
        writeOn(session, Dispatchers.IO, block)

    suspend fun <T> writeOn(session: Long, context: CoroutineContext, block: suspend () -> T): T {
        val permit = acquire(session)
        try {
            return withContext(context + inherited.asContextElement(permit)) { block() }
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

    fun <T> submitOn(scope: CoroutineScope, session: Long,
        context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T): Deferred<T> {
        val permit = try { acquire(session) } catch (error: IOException) {
            return CompletableDeferred<T>().also { it.completeExceptionally(error) }
        }
        val work = scope.async(context + inherited.asContextElement(permit), start = start, block = block)
        work.invokeOnCompletion { release(permit) }
        if (start != CoroutineStart.LAZY) work.start()
        return work
    }

    fun launchOn(scope: CoroutineScope, session: Long,
        context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit): Job {
        val permit = try { acquire(session) } catch (error: IOException) {
            return Job().also { it.cancel(CancellationException(error.message).also { failure -> failure.initCause(error) }) }
        }
        val work = scope.launch(context + inherited.asContextElement(permit), start = start, block = block)
        work.invokeOnCompletion { release(permit) }
        if (start != CoroutineStart.LAZY) work.start()
        return work
    }

    fun closeAdmission() = synchronized(monitor) {
        if (!paused) {
            paused = true
            generation++
        }
    }

    suspend fun pauseAndAwait() {
        closeAdmission()
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

    private fun unavailable() = DataSessionClosedException("Los datos personales están cambiando. Volvé a abrir la captura.")
}
