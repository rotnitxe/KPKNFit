package com.example.kpkn.screens.workout

sealed interface RecordSetResult {
    data class Created(val setKey: String) : RecordSetResult
    data class Updated(val setKey: String) : RecordSetResult
    data class Rejected(val reason: String) : RecordSetResult
    data class PersistenceFailed(val cause: Throwable) : RecordSetResult
    val succeeded: Boolean get() = this is Created || this is Updated
}

class StaleWorkoutExecutionException : IllegalStateException("La ejecución del entreno cambió.")
