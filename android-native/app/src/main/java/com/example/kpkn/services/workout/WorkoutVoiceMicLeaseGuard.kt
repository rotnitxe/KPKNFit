package com.example.kpkn.services.workout

/**
 * Invariante defensivo: una instancia del motor sólo puede publicar una captura.
 *
 * El actor ya serializa lifecycle; este guard evita que una futura regresión
 * acepte dos aperturas y ofrece un contrato JVM verificable.
 */
internal class WorkoutVoiceMicLeaseGuard {
    data class Lease(val generation: Long, val sessionId: Int)

    private var current: Lease? = null

    @Synchronized
    fun tryAcquire(generation: Long, sessionId: Int): Boolean {
        if (current != null) return false
        current = Lease(generation, sessionId)
        return true
    }

    @Synchronized
    fun accepts(generation: Long, sessionId: Int): Boolean =
        current == Lease(generation, sessionId)

    @Synchronized
    fun releaseCurrent(): Lease? = current.also { current = null }

    @Synchronized
    fun activeLeaseCount(): Int = if (current == null) 0 else 1
}
