package com.example.kpkn.screens.workout.components

import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider

/** Serializable owner for bindings made through the process-wide CameraX provider. */
internal class ExclusiveUseCaseLeaseCoordinator<P : Any, U : Any>(
    private val unbind: (provider: P, useCases: List<U>) -> Unit,
) {
    private data class Lease<P : Any, U : Any>(
        val provider: P,
        val owner: Any,
        val useCases: List<U>,
        val onSuspended: () -> Unit,
        val bind: () -> Unit,
    )

    private val leases = mutableListOf<Lease<P, U>>()
    private var current: Lease<P, U>? = null

    @Synchronized
    fun bind(
        provider: P,
        owner: Any,
        useCases: List<U>,
        onSuspended: () -> Unit = {},
        bind: () -> Unit,
    ) {
        val previousLease = leases.firstOrNull { it.provider === provider && it.owner === owner }
        if (previousLease != null) {
            if (current === previousLease) deactivate(previousLease)
            leases.remove(previousLease)
        }
        // Rebinding a previously suspended owner still has to preempt whichever owner is current now.
        current?.let(::deactivate)

        val lease = Lease(provider, owner, useCases, onSuspended, bind)
        leases += lease
        current = lease
        try {
            bind()
        } catch (failure: Throwable) {
            deactivate(lease)
            leases.remove(lease)
            resumeLatest()
            throw failure
        }
    }

    @Synchronized
    fun release(provider: P, owner: Any) {
        val lease = leases.firstOrNull { it.provider === provider && it.owner === owner } ?: return
        val wasCurrent = current === lease
        if (wasCurrent) deactivate(lease)
        leases.remove(lease)
        if (wasCurrent) resumeLatest()
    }

    private fun deactivate(lease: Lease<P, U>) {
        if (current !== lease) return
        runCatching { unbind(lease.provider, lease.useCases) }
        runCatching(lease.onSuspended)
        current = null
    }

    private fun resumeLatest() {
        while (current == null && leases.isNotEmpty()) {
            val lease = leases.last()
            current = lease
            try {
                lease.bind()
                return
            } catch (_: Throwable) {
                deactivate(lease)
                leases.remove(lease)
            }
        }
    }
}

/** The single owner shared by all app routes that bind the process-wide CameraX provider. */
internal object CameraXProcessUseCaseOwner {
    private val coordinator = ExclusiveUseCaseLeaseCoordinator<ProcessCameraProvider, UseCase> { provider, useCases ->
        provider.unbind(*useCases.toTypedArray())
    }

    fun bind(
        provider: ProcessCameraProvider,
        owner: Any,
        useCases: List<UseCase>,
        onSuspended: () -> Unit = {},
        bind: () -> Unit,
    ) = coordinator.bind(provider, owner, useCases, onSuspended, bind)

    fun release(provider: ProcessCameraProvider, owner: Any) = coordinator.release(provider, owner)
}