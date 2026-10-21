package com.example.kpkn.screens.workout.technique

import com.example.kpkn.screens.workout.components.MediaCameraBindingLease

/** Una petición tardía no enlaza; el cierre solo ejecuta la limpieza de los casos que pertenecen a este efecto. */
internal class RecorderCameraBindingOwner : java.io.Closeable {
    private val lease = MediaCameraBindingLease()
    private var releaseOwned: (() -> Unit)? = null

    fun bindIfActive(bind: () -> Unit) = lease.bindIfActive(bind)

    fun own(release: () -> Unit) {
        lease.bindIfActive { releaseOwned = release }
    }

    override fun close() {
        lease.close()
        val release = releaseOwned
        releaseOwned = null
        release?.invoke()
    }
}
