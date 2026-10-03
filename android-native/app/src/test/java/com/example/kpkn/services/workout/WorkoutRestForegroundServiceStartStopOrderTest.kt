package com.example.kpkn.services.workout

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Reproduces the order that killed the process on a real device (manual cancel right after a rest timer
 * started): startForegroundService() immediately followed by stop(). A recording context plays the part of
 * ActivityManager, which crashes the app with ForegroundServiceDidNotStartInTimeException when the service
 * is stopped while it is still waiting for startForeground(). The service itself runs under Robolectric's
 * ServiceController, so the real onStartCommand decides what happens when the system delivers the intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class WorkoutRestForegroundServiceStartStopOrderTest {

    private lateinit var contract: FgsContract
    private lateinit var context: RecordingContext

    @Before
    fun setUp() {
        WorkoutRestForegroundService.startGate.resetForTests()
        contract = FgsContract()
        context = RecordingContext(ApplicationProvider.getApplicationContext<Context>(), contract)
    }

    @After
    fun tearDown() {
        WorkoutRestForegroundService.startGate.resetForTests()
    }

    @Test
    fun recordingContextFlagsStopServiceIssuedWhileAStartIsPending() {
        // The defect this suite guards: the old stop() was a bare stopService() right after the start.
        context.startForegroundService(serviceIntent(WorkoutRestForegroundService.ACTION_START))
        context.stopService(serviceIntent(null))

        assertEquals(1, contract.violations.size)
    }

    @Test
    fun stopRightAfterStartIsDeferredAndTheServiceStopsItselfAfterStartForeground() {
        WorkoutRestForegroundService.start(
            context = context,
            sessionName = "Sesion",
            exerciseName = "Sentadilla",
            setInfo = "Serie 1",
            endAt = System.currentTimeMillis() + 60_000L,
        )
        WorkoutRestForegroundService.stop(context)

        assertEquals(
            "only the start may reach the system while startForeground() is still pending",
            listOf(startedWith(WorkoutRestForegroundService.ACTION_START)),
            contract.calls,
        )
        assertTrue(contract.violations.isEmpty())
        assertTrue(WorkoutRestForegroundService.startGate.isStopDeferred())

        // The system now delivers the start intent: onStartCommand must startForeground() and then leave.
        val service = deliver(context.startedIntents.single(), startId = 1)
        val shadow = shadowOf(service)

        assertEquals(WorkoutRestForegroundService.NOTIF_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals("stopSelf(startId) so a newer queued start would keep the service alive", 1, shadow.stopSelfId)
        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())
        assertFalse(WorkoutRestForegroundService.startGate.isStopDeferred())
        assertFalse("the deferred stop is run by the service, never as stopService()", contract.calls.contains(STOP_SERVICE))
        assertTrue(contract.violations.isEmpty())
    }

    @Test
    fun startWithoutAStopKeepsTheServiceInTheForeground() {
        WorkoutRestForegroundService.start(
            context = context,
            sessionName = "Sesion",
            exerciseName = "Sentadilla",
            endAt = System.currentTimeMillis() + 60_000L,
        )

        val service = deliver(context.startedIntents.single(), startId = 1)
        val shadow = shadowOf(service)

        assertEquals(WorkoutRestForegroundService.NOTIF_ID, shadow.lastForegroundNotificationId)
        assertNotNull(shadow.lastForegroundNotification)
        assertTrue(shadow.isLastForegroundNotificationAttached)
        assertFalse(shadow.isForegroundStopped)
        assertFalse(shadow.isStoppedBySelf)
        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())
    }

    @Test
    fun stopAfterTheServiceEnteredTheForegroundIsAPlainStopService() {
        WorkoutRestForegroundService.start(
            context = context,
            sessionName = "Sesion",
            exerciseName = "Sentadilla",
            endAt = System.currentTimeMillis() + 60_000L,
        )
        deliver(context.startedIntents.single(), startId = 1)

        WorkoutRestForegroundService.stop(context)

        assertEquals(
            listOf(startedWith(WorkoutRestForegroundService.ACTION_START), STOP_SERVICE),
            contract.calls,
        )
        assertTrue(contract.violations.isEmpty())
    }

    @Test
    fun startAfterADeferredStopRearmsTheServiceWithTheLatestTimer() {
        val first = System.currentTimeMillis() + 60_000L
        val second = first + 30_000L
        WorkoutRestForegroundService.start(context, "Sesion", "Sentadilla", endAt = first)
        WorkoutRestForegroundService.stop(context)
        WorkoutRestForegroundService.start(context, "Sesion", "Press banca", endAt = second)

        assertFalse(WorkoutRestForegroundService.startGate.isStopDeferred())
        assertEquals(2, context.startedIntents.size)
        assertTrue(contract.violations.isEmpty())

        val controller = Robolectric.buildService(WorkoutRestForegroundService::class.java).create()
        controller.withIntent(context.startedIntents[0]).startCommand(0, 1)
        controller.withIntent(context.startedIntents[1]).startCommand(0, 2)
        val shadow = shadowOf(controller.get())

        assertFalse("the second start must keep the service alive", shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
        assertEquals(second, shadow.lastForegroundNotification!!.`when`)
        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())
    }

    @Test
    fun updateIntentOnAFreshServiceKeepsTheForegroundPromiseAndThenLeaves() {
        WorkoutRestForegroundService.updateEndTime(context, System.currentTimeMillis() + 60_000L)

        assertEquals(
            listOf(startedWith(WorkoutRestForegroundService.ACTION_UPDATE)),
            contract.calls,
        )
        assertEquals(1, WorkoutRestForegroundService.startGate.pendingStartCount())

        // A stale update: nothing to update, but the startForegroundService() promise is still kept.
        val service = deliver(context.startedIntents.single(), startId = 1)
        val shadow = shadowOf(service)

        assertEquals(WorkoutRestForegroundService.NOTIF_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals(1, shadow.stopSelfId)
        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())
        assertTrue(contract.violations.isEmpty())
    }

    @Test
    fun updateIntentOnALiveForegroundServiceReplacesTheCountdownAndKeepsRunning() {
        val first = System.currentTimeMillis() + 60_000L
        val extended = first + 15_000L
        WorkoutRestForegroundService.start(context, "Sesion", "Sentadilla", endAt = first)
        val controller = Robolectric.buildService(WorkoutRestForegroundService::class.java).create()
        controller.withIntent(context.startedIntents.single()).startCommand(0, 1)
        contract.awaitingStartForeground = false

        WorkoutRestForegroundService.updateEndTime(context, extended)
        controller.withIntent(context.startedIntents.last()).startCommand(0, 2)
        val shadow = shadowOf(controller.get())

        assertFalse(shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
        assertEquals(extended, shadow.lastForegroundNotification!!.`when`)
        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())
    }

    @Test
    fun unknownActionDeliveredThroughStartForegroundServiceStillCallsStartForeground() {
        context.startForegroundService(serviceIntent("com.example.kpkn.action.UNKNOWN_REST_ACTION"))
        WorkoutRestForegroundService.startGate.dispatchStart { }

        val service = deliver(context.startedIntents.single(), startId = 1)
        val shadow = shadowOf(service)

        assertEquals(WorkoutRestForegroundService.NOTIF_ID, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isStoppedBySelf)
        assertTrue(contract.violations.isEmpty())
    }

    @Test
    fun manualCancelRightAfterSchedulingNeverStopsTheServiceBeforeStartForeground() {
        val manager = WorkoutRestAlertManager(context)

        manager.scheduleRestEnd(durationSeconds = 60, sessionName = "Sesion", exerciseName = "Sentadilla")
        manager.cancelRestAlerts(cancelFinished = true)

        assertEquals(
            "scheduleRestEnd clears the previous timer first (nothing pending then); the cancel that follows " +
                "the start must not reach stopService() while the start is pending",
            listOf(STOP_SERVICE, startedWith(WorkoutRestForegroundService.ACTION_START)),
            contract.calls,
        )
        assertTrue(contract.violations.isEmpty())

        val service = deliver(context.startedIntents.single(), startId = 1)
        val shadow = shadowOf(service)

        assertEquals(WorkoutRestForegroundService.NOTIF_ID, shadow.lastForegroundNotificationId)
        assertTrue("cancel semantics are kept: the rest service ends up stopped", shadow.isStoppedBySelf)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(contract.violations.isEmpty())
        drainPreferenceIo(manager)
    }

    @Test
    fun rejectedForegroundStartFallsBackWithoutBlockingTheNextCancel() {
        context.rejectForegroundStarts = true
        val manager = WorkoutRestAlertManager(context)

        // ForegroundServiceStartNotAllowedException stand-in: the manager degrades to a plain notification.
        manager.scheduleRestEnd(durationSeconds = 60, sessionName = "Sesion", exerciseName = "Sentadilla")

        assertEquals(0, WorkoutRestForegroundService.startGate.pendingStartCount())

        manager.cancelRestAlerts()

        assertEquals(STOP_SERVICE, contract.calls.last())
        assertFalse(WorkoutRestForegroundService.startGate.isStopDeferred())
        assertTrue(contract.violations.isEmpty())
        drainPreferenceIo(manager)
    }

    private fun serviceIntent(action: String?): Intent =
        Intent(context, WorkoutRestForegroundService::class.java).apply { this.action = action }

    private fun startedWith(action: String): String = "startForegroundService:$action"

    /**
     * Plays the system's delivery of [intent] to a fresh service instance (onCreate + onStartCommand).
     * Once the service has called startForeground() the fake ActivityManager stops waiting for it.
     */
    private fun deliver(intent: Intent, startId: Int): WorkoutRestForegroundService {
        val controller: ServiceController<WorkoutRestForegroundService> =
            Robolectric.buildService(WorkoutRestForegroundService::class.java).create()
        controller.withIntent(intent).startCommand(0, startId)
        val service = controller.get()
        if (shadowOf(service).lastForegroundNotificationId == WorkoutRestForegroundService.NOTIF_ID) {
            contract.awaitingStartForeground = false
        }
        return service
    }

    private fun drainPreferenceIo(manager: WorkoutRestAlertManager) {
        val drained = CountDownLatch(1)
        manager.launchOnPreferenceOwnerIo { drained.countDown() }
        assertTrue("queued preference cleanup should finish", drained.await(3, TimeUnit.SECONDS))
    }

    /** The part of ActivityManager that matters: stopping a service that awaits startForeground() is fatal. */
    private class FgsContract {
        val calls = mutableListOf<String>()
        val violations = mutableListOf<String>()
        var awaitingStartForeground = false
    }

    private class RecordingContext(
        base: Context,
        private val contract: FgsContract,
    ) : ContextWrapper(base) {
        val startedIntents = mutableListOf<Intent>()
        var rejectForegroundStarts = false

        override fun getApplicationContext(): Context = this

        override fun startForegroundService(service: Intent?): ComponentName? {
            val intent = requireNotNull(service)
            if (rejectForegroundStarts) {
                contract.calls += "startForegroundService:rejected"
                throw IllegalStateException("startForegroundService is not allowed from the background")
            }
            contract.calls += "startForegroundService:${intent.action}"
            contract.awaitingStartForeground = true
            startedIntents += intent
            return intent.component
        }

        override fun startService(service: Intent?): ComponentName? {
            val intent = requireNotNull(service)
            contract.calls += "startService:${intent.action}"
            startedIntents += intent
            return intent.component
        }

        override fun stopService(name: Intent?): Boolean {
            contract.calls += STOP_SERVICE
            if (contract.awaitingStartForeground) {
                contract.violations += "stopService issued while startForeground() is still pending"
            }
            return true
        }
    }

    private companion object {
        const val STOP_SERVICE = "stopService"
    }
}
