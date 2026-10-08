package com.example.kpkn.services.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A cached :voice process can still own writers after its foreground service stops. */
internal object VoiceProcessDataSession {
    suspend fun stopAndAwait(context: Context, timeoutMs: Long = 15_000L) = withContext(Dispatchers.IO) {
        val connected = CompletableDeferred<IBinder>()
        val died = CompletableDeferred<Unit>()
        val deathRecipient = IBinder.DeathRecipient { died.complete(Unit) }
        var linked: IBinder? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) connected.completeExceptionally(IllegalStateException("Voice maintenance returned no binder"))
                else connected.complete(binder)
            }
            // A lost binding alone does not prove that the process and its writers died.
            override fun onServiceDisconnected(name: ComponentName?) = Unit
            override fun onBindingDied(name: ComponentName?) = Unit
            override fun onNullBinding(name: ComponentName?) {
                connected.completeExceptionally(IllegalStateException("Voice maintenance returned no binder"))
            }
        }
        // Bind without starting capture or a microphone foreground service. AUTO_CREATE
        // also reaches a cached process whose old service has already been destroyed.
        val accepted = context.bindService(Intent(context, WorkoutVoiceForegroundService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(accepted) { "Unable to stop the voice writer for data maintenance" }
        try {
            withTimeout(timeoutMs) {
                val binder = connected.await()
                linked = binder
                try {
                    binder.linkToDeath(deathRecipient, 0)
                    IWorkoutVoiceEngineService.Stub.asInterface(binder).quiesceForDataSession()
                } catch (_: DeadObjectException) {
                    // Confirmed process death itself is a writer barrier.
                    died.complete(Unit)
                }
                died.await()
            }
        } finally {
            linked?.let { runCatching { it.unlinkToDeath(deathRecipient, 0) } }
            context.unbindService(connection)
        }
    }
}
