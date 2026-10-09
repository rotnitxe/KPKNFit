package com.example.kpkn.data.backup

import com.example.kpkn.data.media.MediaWriteBarrier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** One owner lifetime; an accepted operation may finish through its sibling repositories. */
internal class DataSessionWriter(val role: String) {
    internal val session = DataSessionWriters.barrier.session()
    internal var retiredThrough: Long? = null
    init { DataSessionWriters.register(this) }

    suspend fun <T> write(block: suspend () -> T): T {
        DataSessionWriters.retain(this)
        try { return DataSessionWriters.barrier.write(session, block) }
        finally { DataSessionWriters.release(this) }
    }
    suspend fun <T> durableWrite(block: suspend () -> T): T {
        DataSessionWriters.retain(this)
        try { return DataSessionWriters.barrier.writeOn(session, NonCancellable + Dispatchers.IO, block) }
        finally { DataSessionWriters.release(this) }
    }
    fun <T> immediate(block: () -> T): T {
        DataSessionWriters.retain(this)
        try { return DataSessionWriters.barrier.immediate(session, block) }
        finally { DataSessionWriters.release(this) }
    }
    fun <T> submit(scope: CoroutineScope, context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> T): Deferred<T> {
        try { DataSessionWriters.retain(this) }
        catch (error: IOException) { return CompletableDeferred<T>().also { it.completeExceptionally(error) } }
        return try {
            DataSessionWriters.barrier.submitOn(scope, session, context, start, block).also { work ->
                work.invokeOnCompletion { DataSessionWriters.release(this) }
            }
        } catch (error: Throwable) { DataSessionWriters.release(this); throw error }
    }
    fun launch(scope: CoroutineScope, context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> Unit): Job {
        try { DataSessionWriters.retain(this) }
        catch (error: IOException) {
            return Job().also { it.cancel(CancellationException(error.message).also { cause -> cause.initCause(error) }) }
        }
        return try {
            DataSessionWriters.barrier.launchOn(scope, session, context, start) {
                try { block() }
                catch (closed: com.example.kpkn.data.media.DataSessionClosedException) {
                    throw CancellationException(closed.message).also { it.initCause(closed) }
                }
            }.also { work ->
                work.invokeOnCompletion { DataSessionWriters.release(this) }
            }
        } catch (error: Throwable) { DataSessionWriters.release(this); throw error }
    }
    fun closeAdmission() = DataSessionWriters.retire(this)
    suspend fun awaitStopped() = DataSessionWriters.awaitOwner(this)
}

/** A single session generation blocks top-level writers while accepted dependency chains drain. */
internal object DataSessionWriters {
    internal val barrier = MediaWriteBarrier()
    private val monitor = Any()
    private val owners = mutableListOf<WeakReference<DataSessionWriter>>()
    private val active = mutableMapOf<DataSessionWriter, Int>()
    private val changed = MutableStateFlow(0L)

    fun register(writer: DataSessionWriter) = synchronized(monitor) {
        owners.removeAll { it.get() == null }
        owners += WeakReference(writer)
    }
    fun retain(writer: DataSessionWriter) = synchronized(monitor) {
        writer.retiredThrough?.let { checkpoint ->
            if (!barrier.hasAcceptedParent(writer.session, checkpoint))
                throw com.example.kpkn.data.media.DataSessionClosedException("El propietario de datos ya está cerrado.")
        }
        active[writer] = (active[writer] ?: 0) + 1
        changed.value += 1
    }
    fun release(writer: DataSessionWriter) = synchronized(monitor) {
        val remaining = (active[writer] ?: error("Unregistered writer release")) - 1
        if (remaining == 0) active.remove(writer) else active[writer] = remaining
        changed.value += 1
    }
    fun retire(writer: DataSessionWriter) = synchronized(monitor) {
        if (writer.retiredThrough == null) writer.retiredThrough = barrier.admissionCheckpoint()
    }
    fun closeAdmission() = barrier.closeAdmission()
    suspend fun awaitOwner(writer: DataSessionWriter) = awaitCounts { (active[writer] ?: 0) == 0 }
    private suspend fun awaitCounts(done: () -> Boolean) = withTimeout(30_000) {
        while (true) {
            val revision = synchronized(monitor) { if (done()) null else changed.value } ?: break
            changed.first { it != revision }
        }
    }
    suspend fun awaitStopped() {
        barrier.pauseAndAwait()
        awaitCounts { active.isEmpty() }
    }
    fun reopenAdmission() = synchronized(monitor) {
        check(active.isEmpty()) { "Personal writers have not stopped" }
        owners.clear()
        barrier.resume()
    }
}

internal class DataSessionWriterScope(private val delegate: CoroutineScope,
    private val writer: DataSessionWriter) : CoroutineScope {
    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext
    fun launch(context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit): Job = writer.launch(delegate, context, start, block)
    fun <T> async(context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T): Deferred<T> = writer.submit(delegate, context, start, block)
}

/** Preserve explicit profile/decorator injection; only the app's dynamic context needs capture. */
internal fun capturedPersonalContext(context: android.content.Context): android.content.Context {
    val application = context.applicationContext
    return if (application is com.example.kpkn.data.account.ProfileContext) application
    else if (application is com.example.kpkn.KpknApplication && com.example.kpkn.data.account.ProfileStorage.initialized())
        com.example.kpkn.data.account.ProfileStorage.context(application)
    else application
}
