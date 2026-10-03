package com.example.kpkn.screens.workout

import android.os.Build
import android.content.Intent
import android.os.StrictMode
import android.os.Trace
import android.util.Log
import com.example.kpkn.BuildConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors

/**
 * Trace markers for same-device before/after repair benchmarks.
 *
 * Markers are inert in every normal build. In repairBenchmark builds, a
 * selection request is paired with the first draw of that active step, and a
 * picker request is paired with the first draw of settled, non-empty results.
 */
internal object RepairBenchmarkTrace {
    private const val DISABLED_REQUEST_ID = 0L
    private const val MAX_STEP_KEY_LENGTH = 40

    private val enabled = BuildConfig.BUILD_TYPE == "repairBenchmark"
    private val requestSequence = AtomicLong()
    private val pendingStepRequests = ConcurrentHashMap<String, Long>()
    private val latestPickerRequest = AtomicLong()
    private val lastPickerResultsDrawn = AtomicLong()
    private val latestEditorRequest = AtomicLong()
    private val lastEditorReadyDrawn = AtomicLong()
    private val latestPickerSearchRequest = AtomicLong()
    private val lastPickerSearchResultsDrawn = AtomicLong()
    private data class PendingWorkoutEntry(val id: Long, val expectedSessionId: String)
    private data class PendingWorkoutExit(val id: Long, val mode: String, var navigationCommitted: Boolean = false)
    private val workoutLifecycleLock = Any()
    private var pendingWorkoutEntry: PendingWorkoutEntry? = null
    private var pendingWorkoutExit: PendingWorkoutExit? = null

    fun selectionRequested(stepKey: String) {
        if (!enabled) return
        val requestId = requestSequence.incrementAndGet()
        pendingStepRequests[stepKey] = requestId
        emit("KPKNRepair|selection_request|id=${requestId}|step=${traceToken(stepKey)}")
    }

    fun workoutBodyDrawn(activeStepKey: String?, completedSetCount: Int) {
        if (!enabled || activeStepKey.isNullOrBlank()) return
        val requestId = pendingStepRequests.remove(activeStepKey) ?: return
        emit(
            "KPKNRepair|workout_body_draw|id=${requestId}" +
                "|step=${traceToken(activeStepKey)}|sets=$completedSetCount",
        )
    }

    fun pickerOpenRequested(): Long {
        if (!enabled) return DISABLED_REQUEST_ID
        val requestId = requestSequence.incrementAndGet()
        latestPickerRequest.set(requestId)
        emit("KPKNRepair|picker_request|id=${requestId}")
        return requestId
    }

    fun pickerResultsDrawn(requestId: Long, definitionCount: Int, searchSettled: Boolean) {
        if (
            !enabled ||
            requestId == DISABLED_REQUEST_ID ||
            latestPickerRequest.get() != requestId ||
            definitionCount <= 0 ||
            !searchSettled
        ) {
            return
        }
        while (true) {
            val previous = lastPickerResultsDrawn.get()
            if (previous >= requestId) return
            if (lastPickerResultsDrawn.compareAndSet(previous, requestId)) break
        }
        emit(
            "KPKNRepair|picker_results_draw|id=${requestId}" +
                "|definitions=$definitionCount|settled=1",
        )
    }

    const val EXTRA_REPAIR_BENCHMARK_WORKOUT_ENTRY =
        "com.example.kpkn.extra.REPAIR_BENCHMARK_WORKOUT_ENTRY"
    const val EXTRA_REPAIR_BENCHMARK_WORKOUT_SESSION_ID =
        "com.example.kpkn.extra.REPAIR_BENCHMARK_WORKOUT_SESSION_ID"

    /** Start only for an explicitly tagged benchmark deep-link intent. */
    fun workoutEntryIntentReceived(intent: Intent?) {
        if (!enabled) return
        val benchmarkIntent = intent ?: return
        if (!benchmarkIntent.getBooleanExtra(EXTRA_REPAIR_BENCHMARK_WORKOUT_ENTRY, false)) return
        val expectedSessionId = benchmarkIntent.getStringExtra(EXTRA_REPAIR_BENCHMARK_WORKOUT_SESSION_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return
        val requestId = requestSequence.incrementAndGet()
        synchronized(workoutLifecycleLock) {
            pendingWorkoutEntry = PendingWorkoutEntry(requestId, expectedSessionId)
            pendingWorkoutExit = null
        }
        emit("KPKNRepair|workout_entry_request|id=$requestId|session=${traceToken(expectedSessionId)}")
    }

    /** Pair only after the requested session is loaded and the screen is no longer starting. */
    fun workoutReadyDrawn(actualSessionId: String?, isStartingWorkout: Boolean, sessionMissingFromProgram: Boolean) {
        if (!enabled || isStartingWorkout || sessionMissingFromProgram) return
        val event = synchronized(workoutLifecycleLock) {
            val request = pendingWorkoutEntry ?: return
            if (request.expectedSessionId != actualSessionId) return
            pendingWorkoutEntry = null
            "KPKNRepair|workout_ready_draw|id=${request.id}|ready=1"
        }
        emit(event)
    }

    /** Begin an exit interval at the actual UI action that can lead back to Home. */
    fun workoutExitRequested(mode: String): Long {
        if (!enabled) return DISABLED_REQUEST_ID
        val requestId = requestSequence.incrementAndGet()
        val safeMode = traceToken(mode)
        synchronized(workoutLifecycleLock) {
            pendingWorkoutEntry = null
            pendingWorkoutExit = PendingWorkoutExit(requestId, safeMode)
        }
        emit("KPKNRepair|workout_exit_request|id=$requestId|mode=$safeMode")
        return requestId
    }

    /** Do not accept a Home draw until pause navigation or successful finish committed. */
    fun workoutExitNavigationCommitted(requestId: Long? = null) {
        if (!enabled) return
        synchronized(workoutLifecycleLock) {
            val request = pendingWorkoutExit ?: return
            if (requestId != null && request.id != requestId) return
            request.navigationCommitted = true
        }
    }

    /** Called after Home draws; RESUMED excludes Home retained under the outgoing workout transition. */
    fun workoutHomeDrawn(isResumed: Boolean) {
        if (!enabled || !isResumed) return
        val request = synchronized(workoutLifecycleLock) {
            val candidate = pendingWorkoutExit ?: return
            if (!candidate.navigationCommitted) return
            pendingWorkoutExit = null
            candidate
        }
        emit("KPKNRepair|workout_home_draw|id=${request.id}|mode=${request.mode}|ready=1")
    }

    private fun traceToken(value: String): String = buildString(
        minOf(value.length, MAX_STEP_KEY_LENGTH),
    ) {
        value.forEach { character ->
            if (length >= MAX_STEP_KEY_LENGTH) return@forEach
            append(
                if (character.isLetterOrDigit() || character == '_' || character == '-' || character == '.') {
                    character
                } else {
                    '_'
                },
            )
        }
    }

    /**
     * Called once per Navigation back-stack entry, before SessionEditorScreen's
     * default ViewModel factory is evaluated. Reuse an undrawn request if the
     * route composition is recreated; after a ready draw, start a new interval.
     */
    fun editorCompositionRequested(): Long {
        if (!enabled) return DISABLED_REQUEST_ID
        val pendingRequestId = latestEditorRequest.get()
        if (pendingRequestId > lastEditorReadyDrawn.get()) return pendingRequestId

        val requestId = requestSequence.incrementAndGet()
        latestEditorRequest.set(requestId)
        emit("KPKNRepair|editor_open_request|id=$requestId")
        return requestId
    }

    fun editorReadyDrawn(requestId: Long, ready: Boolean) {
        if (!enabled || requestId == DISABLED_REQUEST_ID ||
            latestEditorRequest.get() != requestId || !ready
        ) return
        while (true) {
            val previous = lastEditorReadyDrawn.get()
            if (previous >= requestId) return
            if (lastEditorReadyDrawn.compareAndSet(previous, requestId)) break
        }
        emit("KPKNRepair|editor_ready_draw|id=$requestId|ready=1")
    }

    fun pickerSearchRequested(): Long {
        if (!enabled) return DISABLED_REQUEST_ID
        // If search precedes the first result draw, don't attribute that draw
        // to both the initial picker-open and the query interaction.
        latestPickerRequest.set(DISABLED_REQUEST_ID)
        val requestId = requestSequence.incrementAndGet()
        latestPickerSearchRequest.set(requestId)
        emit("KPKNRepair|picker_search_request|id=$requestId")
        return requestId
    }

    fun pickerSearchResultsDrawn(
        requestId: Long,
        query: String,
        latestRequestedQuery: String,
        committedQuery: String,
        projectionReady: Boolean,
        isSettled: Boolean,
        resultCount: Int,
    ) {
        if (!enabled || requestId == DISABLED_REQUEST_ID ||
            latestPickerSearchRequest.get() != requestId ||
            !projectionReady || !isSettled ||
            query != latestRequestedQuery || query != committedQuery || resultCount < 0
        ) return
        while (true) {
            val previous = lastPickerSearchResultsDrawn.get()
            if (previous >= requestId) return
            if (lastPickerSearchResultsDrawn.compareAndSet(previous, requestId)) break
        }
        // Search text is intentionally excluded from trace names.
        emit("KPKNRepair|picker_search_results_draw|id=$requestId|results=$resultCount|settled=1")
    }

    private const val DISABLED_IO_WINDOW_ID = 0L
    private const val IO_TAG = "KPKNRepairIO"
    private val ioWindowSequence = AtomicLong()
    private val ioWindowLock = Any()
    private val activeIoWindows = linkedMapOf<Long, String>()
    private var ioWindowThreadId: Long? = null
    private var previousThreadPolicy: StrictMode.ThreadPolicy? = null
    private val ioListenerExecutor by lazy(LazyThreadSafetyMode.NONE) {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kpkn-repair-strictmode").apply { isDaemon = true }
        }
    }
    /** Enable Main-thread disk-read/write diagnostics while an instrumented UI scope is composed. */
    fun beginUiIoWindow(scope: String): Long {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return DISABLED_IO_WINDOW_ID
        val threadId = Thread.currentThread().id
        synchronized(ioWindowLock) {
            val owner = ioWindowThreadId
            if (owner != null && owner != threadId) return DISABLED_IO_WINDOW_ID
            if (activeIoWindows.isEmpty()) {
                val originalPolicy = StrictMode.getThreadPolicy()
                val builder = StrictMode.ThreadPolicy.Builder(originalPolicy)
                    .detectDiskReads()
                    .detectDiskWrites()
                Api28StrictMode.addViolationListener(builder, ioListenerExecutor)
                previousThreadPolicy = originalPolicy
                ioWindowThreadId = threadId
                StrictMode.setThreadPolicy(builder.build())
            }
            val token = ioWindowSequence.incrementAndGet()
            activeIoWindows[token] = scope
            return token
        }
    }

    /** Restores the exact caller policy after the last nested UI window is disposed. */
    fun endUiIoWindow(token: Long) {
        if (!enabled || token == DISABLED_IO_WINDOW_ID) return
        synchronized(ioWindowLock) {
            if (ioWindowThreadId != Thread.currentThread().id) return
            if (activeIoWindows.remove(token) == null || activeIoWindows.isNotEmpty()) return
            val previous = previousThreadPolicy
            previousThreadPolicy = null
            ioWindowThreadId = null
            if (previous != null) StrictMode.setThreadPolicy(previous)
        }
    }

    private fun activeIoScopesForLog(): String = synchronized(ioWindowLock) {
        activeIoWindows.values.distinct().sorted().joinToString(",").ifBlank { "none" }
    }

    /**
     * Keep API 28-only listener references in a separately loaded class. The
     * main helper is called in normal builds on minSdk 24, even though its
     * benchmark gate is disabled.
     */
    private object Api28StrictMode {
        fun addViolationListener(
            builder: StrictMode.ThreadPolicy.Builder,
            executor: java.util.concurrent.Executor,
        ) {
            builder.penaltyListener(executor, StrictMode.OnThreadViolationListener { violation ->
                val stack = violation.stackTrace.asSequence()
                    .take(24)
                    .joinToString(";") { frame ->
                        "${frame.className.take(80)}#${frame.methodName.take(60)}:${frame.lineNumber}"
                    }
                    .ifBlank { "none" }
                val scopes = RepairBenchmarkTrace.activeIoScopesForLog()
                Log.w(RepairBenchmarkTrace.IO_TAG, "kind=${violation.javaClass.simpleName};active_scopes_at_log=$scopes;stack=$stack")
            })
        }
    }

    private fun emit(name: String) {
        Trace.beginSection(name.take(127))
        Trace.endSection()
    }
}
