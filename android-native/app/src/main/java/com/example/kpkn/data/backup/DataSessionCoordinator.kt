package com.example.kpkn.data.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A maintenance operation must have exclusive ownership of every participating writer. */
class DataSessionCoordinator {
    interface Participant {
        suspend fun quiesce()
        suspend fun reopen()
    }
    sealed interface State {
        data object Ready : State
        data class Maintaining(val operation: String) : State
        data class Failed(val operation: String) : State
    }
    private val mutex = Mutex()
    private val participants = mutableListOf<Participant>()
    private val _state = MutableStateFlow<State>(State.Ready)
    val state = _state.asStateFlow()
    fun register(participant: Participant) { synchronized(participants) { participants += participant } }

    suspend fun <T> maintenance(operation: String, activeWorkout: suspend () -> Boolean, action: suspend () -> T): T = mutex.withLock {
        check(!activeWorkout()) { "Finish or explicitly cancel the active workout before changing data" }
        val snapshot = synchronized(participants) { participants.toList() }
        _state.value = State.Maintaining(operation)
        var actionError: Throwable? = null
        val paused = mutableListOf<Participant>()
        try {
            snapshot.forEach { participant -> paused += participant; participant.quiesce() }
            action()
        } catch (error: Throwable) { actionError = error; throw error }
        finally {
            var reopenError: Throwable? = null
            withContext(NonCancellable) {
                for (participant in paused.asReversed()) {
                    try { participant.reopen() }
                    catch (error: Throwable) {
                        if (reopenError == null) reopenError = error else reopenError?.addSuppressed(error)
                    }
                }
                _state.value = if (actionError == null && reopenError == null) State.Ready else State.Failed(operation)
            }
            reopenError?.let { error -> if (actionError == null) throw error else actionError?.addSuppressed(error) }
        }
    }
}
