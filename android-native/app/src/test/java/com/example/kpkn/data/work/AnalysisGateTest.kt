package com.example.kpkn.data.work

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** La puerta del análisis: la cámara ocupada y el calor lo pausan, y solo pasa un análisis a la vez. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class AnalysisGateTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val defaultThermal = AnalysisGate.thermalStatus
    private val defaultPoll = AnalysisGate.thermalPollMs

    @Before
    fun setUp() {
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = { -1 }
        AnalysisGate.thermalPollMs = 20L
    }

    @After
    fun tearDown() {
        AnalysisGate.cameraBusy.value = false
        AnalysisGate.thermalStatus = defaultThermal
        AnalysisGate.thermalPollMs = defaultPoll
    }

    @Test
    fun with_a_free_camera_and_a_cool_phone_it_does_not_wait() = runBlocking {
        withTimeout(2_000L) { AnalysisGate.awaitClear(context) }
        assertFalse(AnalysisGate.isTooHot(context))
    }

    @Test
    fun while_the_camera_records_it_waits_and_releasing_it_resumes() = runBlocking {
        AnalysisGate.cameraBusy.value = true

        val waiting = async(Dispatchers.Default) { AnalysisGate.awaitClear(context) }
        assertNull("con la cámara grabando no pasa", withTimeoutOrNull(300L) { waiting.await() })

        AnalysisGate.cameraBusy.value = false
        withTimeout(2_000L) { waiting.await() }
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun a_severe_thermal_status_blocks_until_the_phone_cools_down() = runBlocking {
        val status = AtomicInteger(AnalysisGate.THERMAL_SEVERE)
        AnalysisGate.thermalStatus = { status.get() }

        val waiting = async(Dispatchers.Default) { AnalysisGate.awaitClear(context) }
        assertNull("en SEVERE no pasa", withTimeoutOrNull(300L) { waiting.await() })

        status.set(AnalysisGate.THERMAL_SEVERE - 1) // SEVERE es el primer estado que bloquea: uno menos ya deja pasar
        withTimeout(2_000L) { waiting.await() }
        assertTrue(waiting.isCompleted)
    }

    @Test
    fun a_hot_phone_and_a_busy_camera_must_both_clear() = runBlocking {
        val status = AtomicInteger(AnalysisGate.THERMAL_SEVERE + 1) // CRITICAL
        AnalysisGate.thermalStatus = { status.get() }
        AnalysisGate.cameraBusy.value = true

        val waiting = async(Dispatchers.Default) { AnalysisGate.awaitClear(context) }
        status.set(0)
        assertNull("la cámara sigue grabando", withTimeoutOrNull(300L) { waiting.await() })

        AnalysisGate.cameraBusy.value = false
        withTimeout(2_000L) { waiting.await() }
    }

    @Test
    fun the_real_thermal_reading_comes_from_the_power_manager() {
        AnalysisGate.thermalStatus = defaultThermal
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val shadow = Shadows.shadowOf(powerManager)

        shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_MODERATE)
        assertFalse("MODERATE todavía deja analizar", AnalysisGate.isTooHot(context))
        shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        assertTrue(AnalysisGate.isTooHot(context))
        shadow.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SHUTDOWN)
        assertTrue(AnalysisGate.isTooHot(context))
        assertEquals(PowerManager.THERMAL_STATUS_SEVERE, AnalysisGate.THERMAL_SEVERE)
    }

    @Test
    fun the_mutex_lets_one_analysis_in_at_a_time() = runBlocking {
        val inside = AtomicInteger(0)
        val highest = AtomicInteger(0)
        val release = CompletableDeferred<Unit>()

        val jobs = List(3) {
            async(Dispatchers.Default) {
                AnalysisGate.mutex.withLock {
                    highest.accumulateAndGet(inside.incrementAndGet()) { a, b -> maxOf(a, b) }
                    release.await()
                    delay(20L)
                    inside.decrementAndGet()
                }
            }
        }
        delay(200L)
        assertEquals("solo uno ha entrado", 1, inside.get())
        release.complete(Unit)
        withTimeout(5_000L) { jobs.forEach { it.await() } }

        assertEquals(1, highest.get())
        assertFalse(AnalysisGate.mutex.isLocked)
    }
}
