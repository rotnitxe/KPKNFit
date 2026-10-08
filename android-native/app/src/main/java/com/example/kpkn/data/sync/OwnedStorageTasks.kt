package com.example.kpkn.data.sync

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** SDK cancellation is asynchronous: maintenance waits for completion before erasing owned files. */
object OwnedStorageTasks {
    private val monitor = Any()
    private val active = mutableMapOf<Task<*>, () -> Unit>()
    private var paused = false
    private class StreamTransfer(val job: Job) {
        val finished = CompletableDeferred<Unit>()
        @Volatile var cancelIo: (() -> Unit)? = null
    }
    private val streams = mutableSetOf<StreamTransfer>()
    private val direct = Executor { it.run() }

    suspend fun <T> run(factory: () -> Pair<Task<T>, () -> Unit>): T {
        val (task, cancel) = synchronized(monitor) {
            check(!paused) { "Personal file transfers are paused" }
            factory().also { active[it.first] = it.second }
        }
        task.addOnCompleteListener(direct) { synchronized(monitor) { active.remove(task) } }
        return suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener(direct) { result ->
                if (continuation.isActive) {
                    if (result.isSuccessful) continuation.resume(result.result)
                    else continuation.resumeWithException(result.exception ?: kotlinx.coroutines.CancellationException("File transfer canceled"))
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    suspend fun pauseAndAwait() {
        val (transfers, streamTransfers) = synchronized(monitor) { paused = true; active.toList() to streams.toList() }
        transfers.forEach { (_, cancel) -> cancel() }
        streamTransfers.forEach { transfer -> transfer.cancelIo?.invoke(); transfer.job.cancel() }
        withTimeout(60_000) {
            transfers.forEach { (task, _) -> suspendCancellableCoroutine<Unit> { continuation ->
                task.addOnCompleteListener(direct) { if (continuation.isActive) continuation.resume(Unit) }
            } }
            streamTransfers.forEach { it.finished.await() }
        }
        check(synchronized(monitor) { active.keys.none { !it.isComplete } })
        check(synchronized(monitor) { streams.isEmpty() })
    }
    /** Tracks blocking private HTTP streams until their streams/files have actually closed. */
    internal suspend fun <T> stream(block: suspend ((() -> Unit) -> Unit) -> T): T = coroutineScope {
        val transfer = StreamTransfer(requireNotNull(coroutineContext[Job]))
        synchronized(monitor) { check(!paused) { "Personal file transfers are paused" }; streams += transfer }
        try {
            coroutineContext.ensureActive()
            block { cancel ->
                transfer.cancelIo = cancel
                if (synchronized(monitor) { paused }) cancel()
            }
        } finally {
            synchronized(monitor) { streams.remove(transfer) }
            transfer.finished.complete(Unit)
        }
    }
    fun resume() { synchronized(monitor) { paused = false } }
}
