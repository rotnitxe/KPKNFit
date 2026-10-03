package com.example.kpkn.services.workout

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutRestAlertManagerPreferenceIoTest {

    @Test
    fun preloadLoadsOnIoAndQueuedCancelClearDoesNotEraseNewTimer(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()

        val preferences = TrackedPreferences(base.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        val context = TrackingContext(base, preferences)
        val manager = WorkoutRestAlertManager(context)

        manager.preloadPreferences()
        manager.preloadPreferences()

        assertEquals("SharedPreferences should be acquired once and then cached", 1, context.lookupCount.get())
        assertFalse("getSharedPreferences must run off Main", context.lastLookupWasMain)
        assertFalse("SharedPreferences.all must load off Main", preferences.lastAllReadWasMain)

        val gate = AllReadGate()
        preferences.blockNextAllRead(gate)
        manager.cancelRestAlerts()
        assertTrue("cancel clear should reach the queued IO read", gate.entered.await(3, TimeUnit.SECONDS))

        val scheduler = Executors.newSingleThreadExecutor()
        try {
            val scheduled = scheduler.submit<String> {
                manager.scheduleRestEnd(
                    durationSeconds = 60,
                    sessionName = "Nueva sesión",
                    exerciseName = "Sentadilla",
                    isAdjustment = true,
                )
            }
            val newTimerId = scheduled.get(3, TimeUnit.SECONDS)
            assertNotNull(newTimerId)

            gate.release.countDown()
            val ioQueueDrained = CountDownLatch(1)
            manager.launchOnPreferenceOwnerIo { ioQueueDrained.countDown() }
            assertTrue("queued cleanup should finish", ioQueueDrained.await(3, TimeUnit.SECONDS))

            assertEquals(
                "a cancel queued before scheduling must not remove the newer timer",
                newTimerId,
                base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TIMER_ID, null),
            )
        } finally {
            gate.release.countDown()
            scheduler.shutdownNow()
        }
    }

    private class TrackingContext(
        base: Context,
        private val preferences: SharedPreferences,
    ) : ContextWrapper(base) {
        val lookupCount = AtomicInteger()
        @Volatile var lastLookupWasMain = false
            private set

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            if (name != PREFS) return super.getSharedPreferences(name, mode)
            lookupCount.incrementAndGet()
            lastLookupWasMain = Looper.myLooper() == Looper.getMainLooper()
            return preferences
        }
    }

    private class TrackedPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        private val nextAllReadGate = AtomicReference<AllReadGate?>(null)
        @Volatile var lastAllReadWasMain = false
            private set

        fun blockNextAllRead(gate: AllReadGate) {
            nextAllReadGate.set(gate)
        }

        override fun getAll(): MutableMap<String, *> {
            lastAllReadWasMain = Looper.myLooper() == Looper.getMainLooper()
            nextAllReadGate.getAndSet(null)?.let { gate ->
                gate.entered.countDown()
                check(gate.release.await(5, TimeUnit.SECONDS)) { "timed out waiting to release SharedPreferences load" }
            }
            return delegate.all
        }
    }

    private class AllReadGate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
    }

    private companion object {
        const val PREFS = "workout_rest_alerts"
        const val KEY_TIMER_ID = "active_timer_id"
    }
}
