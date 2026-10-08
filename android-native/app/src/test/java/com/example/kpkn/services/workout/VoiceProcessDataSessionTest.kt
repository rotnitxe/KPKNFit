package com.example.kpkn.services.workout

import android.content.ComponentName
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.backup.DataSessionCoordinator
import java.lang.reflect.Proxy
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [34])
class VoiceProcessDataSessionTest {
    private class BindingContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        var accepted = true
        var nullBinding = false
        var dieOnRequest = true
        var disconnectOnRequest = false
        var requested = 0
        var unbound = 0
        var death: IBinder.DeathRecipient? = null
        var connection: ServiceConnection? = null
        lateinit var binder: IBinder
        private val service = Proxy.newProxyInstance(IWorkoutVoiceEngineService::class.java.classLoader,
            arrayOf(IWorkoutVoiceEngineService::class.java)) { _, method, _ ->
            when (method.name) {
                "asBinder" -> binder
                "quiesceForDataSession" -> {
                    requested++
                    if (disconnectOnRequest) connection?.onServiceDisconnected(ComponentName(this, WorkoutVoiceForegroundService::class.java))
                    if (dieOnRequest) death?.binderDied()
                    null
                }
                else -> null
            }
        } as IWorkoutVoiceEngineService
        init {
            binder = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { _, method, args ->
                when (method.name) {
                    "queryLocalInterface" -> service
                    "linkToDeath" -> { death = args!![0] as IBinder.DeathRecipient; null }
                    "unlinkToDeath" -> { death = null; true }
                    "isBinderAlive", "pingBinder" -> true
                    else -> null
                }
            } as IBinder
        }
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            assertEquals(Context.BIND_AUTO_CREATE, flags)
            assertNull("Maintenance must not start microphone capture", intent.action)
            connection = conn
            if (!accepted) return false
            if (nullBinding) conn.onNullBinding(intent.component) else conn.onServiceConnected(intent.component, binder)
            return true
        }
        override fun unbindService(conn: ServiceConnection) { assertSame(connection, conn); unbound++ }
    }

    @Test fun confirmedDeathAllowsMaintenanceAndReleasesBinding() = runBlocking {
        val context = BindingContext()
        VoiceProcessDataSession.stopAndAwait(context, 1000)
        assertEquals(1, context.requested)
        assertEquals(1, context.unbound)
        assertNull(context.death)
    }

    @Test fun requestReturningWithoutDeathCannotEraseData() = runBlocking {
        val context = BindingContext().apply { dieOnRequest = false }
        val coordinator = DataSessionCoordinator()
        coordinator.register(object : DataSessionCoordinator.Participant {
            override suspend fun quiesce() { VoiceProcessDataSession.stopAndAwait(context, 150) }
            override suspend fun reopen() = Unit
        })
        var erased = false
        try { coordinator.maintenance("erase", { false }) { erased = true }; fail("Missing death accepted") }
        catch (_: TimeoutCancellationException) { assertFalse(erased) }
        assertEquals(1, context.unbound)
        assertTrue(coordinator.state.value is DataSessionCoordinator.State.Failed)
    }

    @Test fun disconnectionAloneCannotCountAsProcessDeath() = runBlocking {
        val context = BindingContext().apply { dieOnRequest = false; disconnectOnRequest = true }
        try { VoiceProcessDataSession.stopAndAwait(context, 150); fail("Disconnection accepted") }
        catch (_: TimeoutCancellationException) { assertEquals(1, context.unbound) }
    }

    @Test fun rejectedBindingFailsBeforeShutdown() = runBlocking {
        val context = BindingContext().apply { accepted = false }
        try { VoiceProcessDataSession.stopAndAwait(context, 1000); fail("Rejected bind accepted") }
        catch (_: IllegalStateException) { assertEquals(0, context.requested); assertEquals(0, context.unbound) }
    }

    @Test fun nullBindingFailsAndReleasesBinding() = runBlocking {
        val context = BindingContext().apply { nullBinding = true }
        try { VoiceProcessDataSession.stopAndAwait(context, 1000); fail("Null bind accepted") }
        catch (_: IllegalStateException) { assertEquals(0, context.requested); assertEquals(1, context.unbound) }
    }
}
