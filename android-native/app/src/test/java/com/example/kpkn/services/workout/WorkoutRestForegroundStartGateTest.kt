package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure JVM contract of the start/stop ordering gate behind [WorkoutRestForegroundService].
 * The recorded events stand for the calls the system sees: "start" is startForegroundService(),
 * "stopService" is Context.stopService() and "startForeground" is what onStartCommand does.
 * Issuing "stopService" while a start still awaits "startForeground" is what killed the process with
 * ForegroundServiceDidNotStartInTimeException.
 */
class WorkoutRestForegroundStartGateTest {

    private var nowMs = 0L
    private val gate = WorkoutRestForegroundStartGate(clockMs = { nowMs }, pendingStartTtlMs = TTL_MS)
    private val events = mutableListOf<String>()

    private fun dispatchStart() = gate.dispatchStart { events += "start" }

    private fun dispatchStop(): Boolean = gate.dispatchStop { events += "stopService" }

    @Test
    fun stopWithoutPendingStartRunsStopServiceImmediately() {
        assertTrue(dispatchStop())

        assertEquals(listOf("stopService"), events)
        assertFalse(gate.isStopDeferred())
    }

    @Test
    fun stopRightAfterStartIsDeferredAndNoStopServiceReachesTheSystemBeforeStartForeground() {
        dispatchStart()
        val ranImmediately = dispatchStop()

        assertFalse("a stop during a pending start must be deferred", ranImmediately)
        assertEquals("only the start may have reached the system", listOf("start"), events)
        assertEquals(1, gate.pendingStartCount())
        assertTrue(gate.isStopDeferred())

        // onStartCommand: startForeground() first, then the delivery report.
        events += "startForeground"
        assertTrue("the service must now stop itself", gate.onStartDelivered())

        assertEquals(listOf("start", "startForeground"), events)
        assertEquals(0, gate.pendingStartCount())
        assertFalse(gate.isStopDeferred())
    }

    @Test
    fun deferredStopWaitsForEveryPendingStart() {
        dispatchStart()
        dispatchStart()
        assertFalse(dispatchStop())

        assertFalse("first delivery: another start is still queued", gate.onStartDelivered())
        assertTrue(gate.isStopDeferred())
        assertTrue("last delivery releases the deferred stop", gate.onStartDelivered())
        assertFalse(gate.isStopDeferred())
        assertEquals(listOf("start", "start"), events)
    }

    @Test
    fun startRequestedAfterADeferredStopRearmsTheService() {
        dispatchStart()
        assertFalse(dispatchStop())
        dispatchStart()

        assertFalse("the latest request wins: no stop is pending any more", gate.isStopDeferred())
        assertFalse(gate.onStartDelivered())
        assertFalse(gate.onStartDelivered())
        assertEquals(listOf("start", "start"), events)

        // With nothing pending a later stop is a plain stopService().
        assertTrue(dispatchStop())
        assertEquals(listOf("start", "start", "stopService"), events)
    }

    @Test
    fun stopAfterTheServiceEnteredTheForegroundRunsImmediately() {
        dispatchStart()
        events += "startForeground"
        assertFalse(gate.onStartDelivered())

        assertTrue(dispatchStop())

        assertEquals(listOf("start", "startForeground", "stopService"), events)
    }

    @Test
    fun rejectedStartIsRolledBackSoItNeverBlocksTheNextStop() {
        try {
            gate.dispatchStart { throw IllegalStateException("start not allowed from the background") }
            fail("the failure of the start call must reach the caller")
        } catch (expected: IllegalStateException) {
            assertEquals("start not allowed from the background", expected.message)
        }

        assertEquals(0, gate.pendingStartCount())
        assertTrue(dispatchStop())
        assertEquals(listOf("stopService"), events)
    }

    @Test
    fun rejectedStartRestoresAStopDeferredByAnEarlierPendingStart() {
        dispatchStart()
        assertFalse(dispatchStop())

        try {
            gate.dispatchStart { throw IllegalStateException("start not allowed from the background") }
            fail("the failure of the start call must reach the caller")
        } catch (expected: IllegalStateException) {
            assertEquals("start not allowed from the background", expected.message)
        }

        assertEquals(1, gate.pendingStartCount())
        assertTrue("the earlier stop request must survive the rejected start", gate.isStopDeferred())
        assertTrue(gate.onStartDelivered())
        assertEquals(listOf("start"), events)
    }

    @Test
    fun pendingStartThatIsNeverDeliveredStopsBlockingAfterTheTtl() {
        dispatchStart()

        nowMs = TTL_MS
        assertFalse("at exactly the TTL the start still counts as pending", dispatchStop())
        assertEquals(listOf("start"), events)

        nowMs = TTL_MS + 1L
        assertTrue("an expired start no longer blocks the stop", dispatchStop())
        assertEquals(listOf("start", "stopService"), events)
        assertEquals(0, gate.pendingStartCount())
        assertFalse(gate.isStopDeferred())
    }

    @Test
    fun deliveryWithoutAPendingStartIsIgnored() {
        // System-initiated sticky restarts (null intent) report nothing, but a stray report must be harmless.
        assertFalse(gate.onStartDelivered())

        assertEquals(0, gate.pendingStartCount())
        assertFalse(gate.isStopDeferred())
    }

    @Test
    fun resetForgetsPendingStartsAndDeferredStops() {
        dispatchStart()
        assertFalse(dispatchStop())

        gate.resetForTests()

        assertEquals(0, gate.pendingStartCount())
        assertFalse(gate.isStopDeferred())
        assertTrue(dispatchStop())
    }

    private companion object {
        const val TTL_MS = 10_000L
    }
}
