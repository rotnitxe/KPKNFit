package com.example.kpkn.services.cardio

import android.Manifest
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowService

/**
 * START and RESUME reach the service through startForegroundService(): the system kills the process when
 * the new service instance returns from onStartCommand without startForeground(). These tests drive the
 * real service under Robolectric's ServiceController and check that every such intent calls
 * startForeground() (location type on API 29+) before the service decides the command is not for it.
 *
 * Location permission is denied on purpose: a valid START then ends in PERMISSION_DENIED instead of
 * reaching FusedLocation, which keeps the whole flow deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class CardioGpsForegroundServiceStartPromiseTest {

    private lateinit var application: Application
    private val controllers = mutableListOf<ServiceController<CardioGpsForegroundService>>()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        resetTracker()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.destroy() } }
        controllers.clear()
        resetTracker()
    }

    @Test
    fun resumeForAForeignSessionOnAFreshInstanceCallsStartForegroundAndThenLeaves() {
        val controller = freshController()
        val service = controller.get()
        val trackerBefore = CardioGpsTracker.state.value

        val result = service.onStartCommand(commandIntent(CardioGpsForegroundService.ACTION_RESUME, FOREIGN_KEY), 0, 7)

        assertForegroundPromiseKept(service)
        val shadow = shadowOf(service)
        // stopForeground() after startForeground(): the other order would leave isForegroundStopped false.
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals("stopSelf(startId), so a newer queued command keeps the service alive", 7, shadow.stopSelfId)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(trackerBefore, CardioGpsTracker.state.value)
        assertTrue(controllers.remove(controller))
        controller.destroy()
        assertEquals("a service that never owned a session must not touch the tracker", trackerBefore, CardioGpsTracker.state.value)
    }

    @Test
    fun resumeForAnotherSessionLeavesTheSessionHeldByTheTrackerUntouched() {
        runBlocking { CardioGpsTracker.restoreIfAvailable(application, TRACKER_KEY) }
        val trackerBefore = CardioGpsTracker.state.value
        assertEquals(TRACKER_KEY, trackerBefore.sessionKey)
        val controller = freshController()
        val service = controller.get()

        val result = service.onStartCommand(commandIntent(CardioGpsForegroundService.ACTION_RESUME, FOREIGN_KEY), 0, 1)

        assertForegroundPromiseKept(service)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY, result)
        assertEquals(trackerBefore, CardioGpsTracker.state.value)
        assertTrue(controllers.remove(controller))
        controller.destroy()
        assertEquals(trackerBefore, CardioGpsTracker.state.value)
    }

    @Test
    fun resumeWithoutAUsableSessionKeyOnAFreshInstanceCallsStartForegroundAndThenLeaves() {
        listOf(null, "", "   ").forEachIndexed { index, key ->
            val service = freshController().get()

            val result = service.onStartCommand(commandIntent(CardioGpsForegroundService.ACTION_RESUME, key), 0, index + 1)

            assertForegroundPromiseKept(service)
            val shadow = shadowOf(service)
            assertTrue("key=$key", shadow.isForegroundStopped)
            assertTrue("key=$key", shadow.isStoppedBySelf)
            assertEquals("key=$key", index + 1, shadow.stopSelfId)
            assertEquals("key=$key", Service.START_NOT_STICKY, result)
        }
        assertNull(CardioGpsTracker.state.value.sessionKey)
    }

    @Test
    fun startWithoutAUsableSessionKeyOnAFreshInstanceCallsStartForegroundAndThenLeaves() {
        listOf(null, "", "   ").forEachIndexed { index, key ->
            val service = freshController().get()

            val result = service.onStartCommand(
                commandIntent(CardioGpsForegroundService.ACTION_START, key, executionStartedAtMs = 5_000L),
                0,
                index + 1,
            )

            assertForegroundPromiseKept(service)
            val shadow = shadowOf(service)
            assertTrue("key=$key", shadow.isForegroundStopped)
            assertTrue("key=$key", shadow.isStoppedBySelf)
            assertEquals("key=$key", index + 1, shadow.stopSelfId)
            assertEquals("key=$key", Service.START_NOT_STICKY, result)
        }
        assertNull("a blank start must not create a GPS session", CardioGpsTracker.state.value.sessionKey)
    }

    @Test
    fun unknownOrMissingActionOnAFreshInstanceStillCallsStartForegroundAndThenLeaves() {
        listOf("com.example.kpkn.action.UNKNOWN_CARDIO_GPS_FGS", null).forEach { action ->
            val service = freshController().get()

            val result = service.onStartCommand(commandIntent(action, FOREIGN_KEY), 0, 5)

            assertForegroundPromiseKept(service)
            val shadow = shadowOf(service)
            assertTrue("action=$action", shadow.isForegroundStopped)
            assertTrue("action=$action", shadow.isStoppedBySelf)
            assertEquals("action=$action", 5, shadow.stopSelfId)
            assertEquals("action=$action", Service.START_NOT_STICKY, result)
        }
    }

    @Test
    fun nullIntentFromAStickyRestartKeepsLeavingWithoutAForegroundPromise() {
        val service = freshController().get()

        val result = service.onStartCommand(null, 0, 6)

        val shadow = shadowOf(service)
        assertEquals("a sticky restart did not come from startForegroundService()", 0, shadow.lastForegroundNotificationId)
        assertNull(shadow.lastForegroundNotification)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals(6, shadow.stopSelfId)
        assertEquals(Service.START_NOT_STICKY, result)
    }

    @Test
    fun pauseAndStopCommandsAreDeliveredWithStartServiceAndRaiseNoNotification() {
        listOf(CardioGpsForegroundService.ACTION_PAUSE, CardioGpsForegroundService.ACTION_STOP).forEach { action ->
            val service = freshController().get()

            val result = service.onStartCommand(commandIntent(action, FOREIGN_KEY), 0, 1)

            assertEquals("action=$action", 0, shadowOf(service).lastForegroundNotificationId)
            assertEquals("action=$action", Service.START_NOT_STICKY, result)
        }
    }

    @Test
    fun validStartEntersTheForegroundWithTheLocationTypeAndKeepsServingUntilTheTrackerAnswers() {
        val service = freshController().get()
        val shadow = shadowOf(service)

        val result = service.onStartCommand(
            commandIntent(CardioGpsForegroundService.ACTION_START, RUN_KEY, executionStartedAtMs = 1_000L),
            0,
            3,
        )

        assertEquals(Service.START_REDELIVER_INTENT, result)
        assertForegroundPromiseKept(service)
        assertNotificationPosted(service)
        assertFalse("a valid start keeps serving", shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)

        // Location permission is denied: the tracker answers PERMISSION_DENIED and the service ends itself,
        // exactly as before the foreground promise moved to the top of onStartCommand.
        assertTrue("the service should end itself after the tracker answered", awaitStoppedBySelf(shadow))
        assertEquals(3, shadow.stopSelfId)
        assertTrue(shadow.isForegroundStopped)
        assertEquals(RUN_KEY, CardioGpsTracker.state.value.sessionKey)
        assertEquals(CardioGpsStatus.PERMISSION_DENIED, CardioGpsTracker.state.value.status)
    }

    @Test
    fun resumeOfTheSessionHeldByTheTrackerEntersTheForegroundAndKeepsServingUntilTheTrackerAnswers() {
        runBlocking { CardioGpsTracker.restoreIfAvailable(application, RUN_KEY) }
        val service = freshController().get()
        val shadow = shadowOf(service)

        val result = service.onStartCommand(commandIntent(CardioGpsForegroundService.ACTION_RESUME, RUN_KEY), 0, 4)

        assertEquals(Service.START_REDELIVER_INTENT, result)
        assertForegroundPromiseKept(service)
        assertNotificationPosted(service)
        assertFalse("an owned resume keeps serving", shadow.isStoppedBySelf)

        // The restored session has no snapshot to resume: the tracker answers INACTIVE and the service leaves.
        assertTrue("the service should end itself after the tracker answered", awaitStoppedBySelf(shadow))
        assertEquals(4, shadow.stopSelfId)
        assertTrue(shadow.isForegroundStopped)
    }

    @Test
    fun foreignResumeOnALiveInstanceKeepsServingAndLeavesTheRunningExecutionIdentityAlone() {
        val service = freshController().get()
        val shadow = shadowOf(service)
        service.onStartCommand(
            commandIntent(CardioGpsForegroundService.ACTION_START, LIVE_KEY, executionStartedAtMs = 2_000L),
            0,
            1,
        )

        val result = service.onStartCommand(commandIntent(CardioGpsForegroundService.ACTION_RESUME, FOREIGN_KEY), 0, 2)

        assertEquals("a live instance keeps its restart policy", Service.START_REDELIVER_INTENT, result)
        assertForegroundPromiseKept(service)
        assertNotificationPosted(service)
        assertFalse("a foreign command must not stop the live service", shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
        assertNotEquals("a foreign command must not make the tracker adopt its key", FOREIGN_KEY, CardioGpsTracker.state.value.sessionKey)

        // The identity of the running execution is intact: the start's own PERMISSION_DENIED cleanup still
        // recognises its key (it only acts while activeServiceSessionKey == LIVE_KEY).
        assertTrue("the live execution should still own its key", awaitStoppedBySelf(shadow))
        assertEquals(1, shadow.stopSelfId)
        assertEquals(LIVE_KEY, CardioGpsTracker.state.value.sessionKey)
    }

    @Test
    fun staleStartOnALiveInstanceKeepsServingAndLeavesTheRunningExecutionIdentityAlone() {
        val service = freshController().get()
        val shadow = shadowOf(service)
        service.onStartCommand(
            commandIntent(CardioGpsForegroundService.ACTION_START, LIVE_KEY, executionStartedAtMs = 2_000L),
            0,
            1,
        )

        val result = service.onStartCommand(
            commandIntent(CardioGpsForegroundService.ACTION_START, OLD_KEY, executionStartedAtMs = 1_000L),
            0,
            2,
        )

        assertEquals(Service.START_NOT_STICKY, result)
        assertForegroundPromiseKept(service)
        assertNotificationPosted(service)
        assertFalse("a stale start must not stop the live service", shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)

        assertTrue("the live execution should still own its key", awaitStoppedBySelf(shadow))
        assertEquals(1, shadow.stopSelfId)
        assertEquals(LIVE_KEY, CardioGpsTracker.state.value.sessionKey)
    }

    @Test
    fun onlyStartAndResumeAreDispatchedWithStartForegroundService() {
        val context = RecordingContext(application)

        CardioGpsForegroundService.start(context, RUN_KEY, 1_000L)
        CardioGpsForegroundService.resume(context, RUN_KEY)
        CardioGpsForegroundService.pause(context, RUN_KEY)
        CardioGpsForegroundService.stop(context, RUN_KEY)

        assertEquals(
            listOf(CardioGpsForegroundService.ACTION_START, CardioGpsForegroundService.ACTION_RESUME),
            context.foregroundStarted.map { it.action },
        )
        assertEquals(
            listOf(CardioGpsForegroundService.ACTION_PAUSE, CardioGpsForegroundService.ACTION_STOP),
            context.plainStarted.map { it.action },
        )
        assertTrue(context.foregroundStarted.all { cardioGpsIntentPromisesForeground(it.action) })
        assertTrue(context.plainStarted.none { cardioGpsIntentPromisesForeground(it.action) })
    }

    private fun freshController(): ServiceController<CardioGpsForegroundService> =
        Robolectric.buildService(CardioGpsForegroundService::class.java).create().also { controllers += it }

    private fun commandIntent(
        action: String?,
        sessionKey: String?,
        executionStartedAtMs: Long? = null,
    ): Intent = Intent(application, CardioGpsForegroundService::class.java).apply {
        this.action = action
        if (sessionKey != null) putExtra(CardioGpsForegroundService.EXTRA_SESSION_KEY, sessionKey)
        if (executionStartedAtMs != null) {
            putExtra(CardioGpsForegroundService.EXTRA_EXECUTION_STARTED_AT_MS, executionStartedAtMs)
        }
    }

    /**
     * startForeground() ran with the service's notification id and the declared location type. The shadow
     * forgets the notification itself once stopForeground(REMOVE) ran, so that is checked separately by
     * [assertNotificationPosted] for the cases that keep serving.
     */
    private fun assertForegroundPromiseKept(service: CardioGpsForegroundService) {
        val shadow = shadowOf(service)
        assertEquals(CardioGpsForegroundService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, service.foregroundServiceType)
    }

    private fun assertNotificationPosted(service: CardioGpsForegroundService) {
        val shadow = shadowOf(service)
        assertNotNull(shadow.lastForegroundNotification)
        assertTrue(shadow.isLastForegroundNotificationAttached)
    }

    /** Runs the main looper until the service stops itself: its cleanup is posted to Main by a coroutine. */
    private fun awaitStoppedBySelf(shadow: ShadowService): Boolean {
        val deadlineNs = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadlineNs) {
            ShadowLooper.idleMainLooper()
            if (shadow.isStoppedBySelf) return true
            Thread.sleep(25)
        }
        return shadow.isStoppedBySelf
    }

    private fun resetTracker() {
        ALL_KEYS.forEach { CardioGpsTracker.clearSession(it) }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val foregroundStarted = mutableListOf<Intent>()
        val plainStarted = mutableListOf<Intent>()

        override fun startForegroundService(service: Intent?): ComponentName? {
            val intent = requireNotNull(service)
            foregroundStarted += intent
            return intent.component
        }

        override fun startService(service: Intent?): ComponentName? {
            val intent = requireNotNull(service)
            plainStarted += intent
            return intent.component
        }
    }

    private companion object {
        const val TRACKER_KEY = "run-held-by-tracker"
        const val FOREIGN_KEY = "run-foreign"
        const val RUN_KEY = "run-valid"
        const val LIVE_KEY = "run-live"
        const val OLD_KEY = "run-old"
        val ALL_KEYS = listOf(TRACKER_KEY, FOREIGN_KEY, RUN_KEY, LIVE_KEY, OLD_KEY)
    }
}
