package com.example.kpkn.services.cardio

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.StrictMode
import android.provider.Settings
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import com.example.kpkn.MainActivity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.AugeRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.workout.WorkoutViewModel
import com.example.kpkn.services.workout.WorkoutRestAlertManager
import com.example.kpkn.services.workout.WorkoutVoiceController
import com.example.kpkn.services.workout.VoiceSessionCommand
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * End-to-end Android location and foreground-service exercise. It requires the
 * paired `workout-gps-avd-qa.ps1` host driver, which feeds explicitly synthetic
 * coordinates with `adb emu geo fix` to the single authorized audit AVD.
 */
@RunWith(AndroidJUnit4::class)
class CardioGpsAvdLifecycleInstrumentedTest {
    private val suffix = System.nanoTime().toString()
    private val programId = "androidtest-gps-$suffix"
    private val sessionId = "androidtest-gps-session-$suffix"
    private val cardioId = "androidtest-run-$suffix"
    private val mobilityId = "androidtest-mobility-$suffix"
    private val viewModelStore = ViewModelStore()

    private lateinit var context: Context
    private lateinit var repository: ProgramRepository
    private lateinit var db: KpknDatabase
    private var activityScenario: ActivityScenario<MainActivity>? = null
    private var viewModel: WorkoutViewModel? = null

    private val cardioExercise = Exercise(
        id = cardioId,
        name = "Carrera GPS sintética",
        exerciseDbId = cardioId,
        exerciseId = cardioId,
        canonicalExerciseId = cardioId,
        sets = listOf(ExerciseSet(id = "run-set-a"), ExerciseSet(id = "run-set-b")),
        cardioDetails = CardioDetails(
            type = CardioType.RUN_OUTDOOR,
            requiresGps = true,
            targetDurationSeconds = 3_600,
        ),
    )

    private val mobilityExercise = Exercise(
        id = mobilityId,
        name = "Movilidad de cierre",
        exerciseDbId = mobilityId,
        exerciseId = mobilityId,
        canonicalExerciseId = mobilityId,
        sets = listOf(ExerciseSet(id = "mobility-set")),
        mobilitySeries = listOf(
            MobilitySeries(
                id = "mobility-series",
                name = "Rotaciones de cadera",
                sets = 1,
                durationSeconds = 60,
                unit = MobilityUnit.SECONDS,
            ),
        ),
        mobilityConfig = MobilityConfig(mode = MobilityMode.ENFOCADO, totalMinutes = 1),
    )

    private val fixtureSession = Session(
        id = sessionId,
        name = "Prueba AVD GPS y temporizadores",
        exercises = listOf(cardioExercise, mobilityExercise),
        targetDurationMinutes = 5,
    )

    @Before
    fun setUp() {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        context = IsolatedWorkoutContext(targetContext, targetContext, suffix)
        runBlocking {
            withContext(Dispatchers.IO) {
                val probe = context.getSharedPreferences("fixture-path-probe", Context.MODE_PRIVATE)
                assertTrue(
                    "Las preferencias aisladas deben poder confirmarse en el almacenamiento del paquete app",
                    probe.edit().putString("probe", suffix).commit(),
                )
                assertEquals(suffix, probe.getString("probe", null))
            }
        }
        // MainActivity nutrition owns the database selected by this fixture.
        // Cancel its previous owner before replacing/closing the test Room singleton.
        NutritionRepository.closeInstance()
        clearSingleton(AugeRepository::class.java)
        repository = ProgramRepository.initForTests(context)
        db = repository.databaseForTests()
        setDatabaseSingletonForTest(db)
        org.junit.Assert.assertSame(db, KpknDatabase.getInstance(context))
        runBlocking {
            awaitGpsPhase("setup.repository-ready", 30_000L, vm = null) { repository.isReady.first { it } }
            assertTrue(repository.addProgramNow(fixtureProgram()).isSuccess)
        }
        // Keep the target app resumed so Android permits a location foreground service.
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        // This GPS lifecycle test is not measuring the app's cold nutrition import. Let MainActivity's
        // existing bootstrap finish before starting the 30-second workout-session readiness window.
        runBlocking {
            withTimeout(180_000L) {
                NutritionRepository.getInstance().foodDatabase.first { it.isNotEmpty() }
            }
        }
    }

    @After
    fun tearDown() {
        runCatching {
            viewModel?.let { vm ->
                if (!vm.uiState.value.wasCancelled && repository.ongoingWorkout.value != null) {
                    onMain { vm.cancelWorkout() }
                    runBlocking {
                        awaitGpsPhase("teardown.workout-cancelled", 15_000L, vm = vm) {
                            vm.uiState.first { it.wasCancelled }
                        }
                    }
                }
            }
        }
        runCatching {
            val activeKey = CardioGpsTracker.state.value.sessionKey
            if (!activeKey.isNullOrBlank()) {
                CardioGpsTracker.stop(activeKey)
                CardioGpsForegroundService.stop(context, activeKey)
                runBlocking { awaitGpsServiceRunning(false) }
                CardioGpsTracker.clearSession(activeKey)
            }
        }
        runCatching {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModelStore.clear()
                viewModel = null
            }
        }
        runCatching { activityScenario?.close() }
        activityScenario = null
        runCatching { clearSingleton(AugeRepository::class.java) }
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        runCatching { KpknDatabase.closeInstance() }
        runCatching { setDatabaseSingletonForTest(null) }
        runCatching { com.example.kpkn.data.repository.WorkoutMediaRepository.closeInstance() }
        runCatching { (context as? IsolatedWorkoutContext)?.clearIsolatedPreferences() }
    }

    @Test
    fun fgs_uses_distinct_series_keys_pauses_restores_and_stops_all_timers_on_discard(): Unit = runBlocking {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        assertTrue(
            "El host debe habilitar ubicación del emulador",
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0,
        )

        val vm = createViewModelAndAwaitActiveSession()
        val keyA = vm.cardioGpsSessionKey(cardioId)
        assertTrue(keyA.endsWith("::$cardioId::run-set-a"))

        onMain { vm.startCardioGps() }
        awaitRecording(keyA)
        awaitGpsServiceForeground(true)
        val firstSegment = awaitGpsPhase("first-segment-two-points-with-distance", 30_000L, vm = vm) {
            CardioGpsTracker.state.first {
                it.sessionKey == keyA && it.pointCount >= 2 && it.distanceMeters > 0.0
            }
        }
        assertTrue("Se esperaban al menos dos geo fixes sintéticos con desplazamiento", firstSegment.pointCount >= 2)
        assertTrue("Los geo fixes sintéticos aceptados deben producir desplazamiento", firstSegment.distanceMeters > 0.0)

        onMain { vm.pauseCardioGps() }
        withTimeout(10_000L) {
            CardioGpsTracker.state.first { it.sessionKey == keyA && it.status == CardioGpsStatus.PAUSED }
        }
        onMain { vm.restoreCardioGpsIfAvailable(cardioExercise) }
        delay(300L)
        assertEquals(CardioGpsStatus.PAUSED, CardioGpsTracker.state.value.status)
        val pausedPointCount = CardioGpsTracker.state.value.pointCount
        // Host geo fixes continue during this interval; PAUSED must reject them.
        delay(6_500L)
        assertEquals(pausedPointCount, CardioGpsTracker.state.value.pointCount)

        onMain { vm.resumeCardioGps() }
        awaitRecording(keyA)
        awaitGpsPhase("resume-accepts-next-point", 30_000L, vm = vm) {
            CardioGpsTracker.state.first { it.sessionKey == keyA && it.pointCount > pausedPointCount }
        }

        assertTrue(
            "La primera serie cardio debe confirmar con su ruta GPS",
            onMainSuspending { vm.recordCardioSet(durationSeconds = 30, distanceKm = null, averageHeartRate = null) },
        )
        awaitGpsPhase("set.first-cardio-committed", 15_000L, vm = vm, requestedGpsKey = keyA) {
            vm.uiState.first { it.currentSetIdx == 1 && it.completedSets.containsKey("${cardioId}_0") }
        }
        awaitGpsServiceRunning(false)

        val keyB = vm.cardioGpsSessionKey(cardioId)
        assertTrue("La siguiente serie debe tener una clave de ejecución distinta", keyB != keyA)
        assertTrue(keyB.endsWith("::$cardioId::run-set-b"))
        onMain { vm.startCardioGps() }
        awaitRecording(keyB)
        awaitGpsServiceForeground(true)
        CardioGpsForegroundService.stop(context, keyA)
        delay(350L)
        assertEquals("Un STOP tardío de la serie previa no debe afectar la actual", keyB, CardioGpsTracker.state.value.sessionKey)
        assertTrue(isGpsServiceForeground())
        awaitGpsPhase("next-series-two-points", 30_000L, vm = vm) {
            CardioGpsTracker.state.first { it.sessionKey == keyB && it.pointCount >= 2 }
        }

        onMain {
            vm.startMobilityGlobalTimer(mobilityId, totalMinutes = 1)
            vm.startSessionTimer(totalSeconds = 180)
            vm.startRestTimer(seconds = 120)
        }
        assertTrue(vm.uiState.value.mobilityTotalTimerState?.isRunning == true)
        assertTrue(vm.uiState.value.isRestTimerRunning)
        assertTrue(vm.sessionTimeRemainingSeconds.value != null)
        // Mobility's elapsed ticks are intentionally exposed as a separate
        // StateFlow instead of rewriting the persisted god-state every second.
        val mobilityBeforeCancel = vm.mobilityTimerRemaining.value
        val sessionBeforeCancel = vm.sessionTimeRemainingSeconds.value!!
        val restBeforeCancel = vm.restTimerRemaining.value
        delay(1_300L)
        assertTrue(vm.mobilityTimerRemaining.value < mobilityBeforeCancel)
        assertTrue(vm.sessionTimeRemainingSeconds.value!! < sessionBeforeCancel)
        assertTrue(vm.restTimerRemaining.value < restBeforeCancel)

        onMain { vm.cancelWorkout() }
        val cancelled = withTimeout(20_000L) { vm.uiState.first { it.wasCancelled } }
        awaitGpsServiceRunning(false)
        assertFalse("FGS cardio debe quedar detenido al descartar", isGpsServiceRunning())
        assertFalse(vm.uiState.value.isRestTimerRunning)
        assertEquals(0, vm.restTimerRemaining.value)
        assertNull(vm.sessionTimeRemainingSeconds.value)
        assertFalse("El timer global de movilidad debe quedar terminal", cancelled.mobilityTotalTimerState?.isRunning == true)
        assertEquals(CardioGpsStatus.INACTIVE, CardioGpsTracker.state.value.status)
        assertFalse(
            "La notificación FGS del cardio debe retirarse",
            activeNotifications().any { it.id == CARDIO_GPS_NOTIFICATION_ID },
        )
        assertNull(db.stateDao().getOngoingWorkout())
        assertTrue("Descartar no crea un log parcial", db.workoutLogDao().getAll().isEmpty())

        val mobilityAfterCancel = cancelled.mobilityTotalTimerState?.remainingSeconds
        delay(1_300L)
        assertEquals(mobilityAfterCancel, vm.uiState.value.mobilityTotalTimerState?.remainingSeconds)
        assertEquals(0, vm.restTimerRemaining.value)
        assertNull(vm.sessionTimeRemainingSeconds.value)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun viewmodel_constructor_start_record_and_cancel_have_no_main_thread_disk_io(): Unit = runBlocking {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION),
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        assertTrue(Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, 0) != 0)

        val violations = ConcurrentLinkedQueue<String>()
        val listenerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cardio-gps-strictmode-test").apply { isDaemon = true }
        }
        var previousPolicy: StrictMode.ThreadPolicy? = null
        var strictPolicyInstalled = false
        try {
            previousPolicy = onMain {
                StrictMode.getThreadPolicy().also {
                    StrictMode.setThreadPolicy(
                        StrictMode.ThreadPolicy.Builder()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .penaltyListener(
                                listenerExecutor,
                                StrictMode.OnThreadViolationListener { violation ->
                                    val route = violation.stackTrace.asSequence()
                                        .filter { frame -> frame.className.startsWith("com.example.kpkn.") }
                                        .take(10)
                                        .joinToString(";") { frame ->
                                            "${frame.className}#${frame.methodName}:${frame.lineNumber}"
                                        }
                                    if (route.isNotBlank()) violations += "${violation.javaClass.simpleName}: $route"
                                },
                            )
                            .build(),
                    )
                }
            }
            strictPolicyInstalled = true

            // Constructor and its session hydration execute while Main's disk guard is active.
            val vm = createViewModelAndAwaitActiveSession()
            val firstKey = vm.cardioGpsSessionKey(cardioId)
            onMain { vm.startCardioGps() }
            awaitRecording(firstKey)
            awaitGpsPhase("strictmode-first-point", 30_000L, vm = vm) {
                CardioGpsTracker.state.first { it.sessionKey == firstKey && it.pointCount >= 1 }
            }

            val recorded = onMainSuspending {
                vm.recordCardioSet(durationSeconds = 30, distanceKm = null, averageHeartRate = null)
            }
            assertTrue("El registro cardio se confirma con la sesión activa", recorded)
            awaitGpsPhase("strictmode.first-cardio-committed", 15_000L, vm = vm, requestedGpsKey = firstKey) {
                vm.uiState.first { it.currentSetIdx == 1 && it.completedSets.containsKey("${cardioId}_0") }
            }
            awaitGpsServiceRunning(false)

            val nextKey = vm.cardioGpsSessionKey(cardioId)
            assertTrue("La siguiente serie abre una ejecución GPS distinta", nextKey != firstKey)
            onMain {
                vm.startMobilityGlobalTimer(mobilityId, totalMinutes = 1)
                vm.startSessionTimer(totalSeconds = 180)
                vm.startRestTimer(seconds = 120)
            }
            assertTrue(
                "El gate permite iniciar el timer cardio del siguiente set por la ruta de voz",
                onMain { vm.startCardioFromVoice() },
            )
            withTimeout(5_000L) {
                vm.uiState.first {
                    it.cardioTimerState?.exerciseId == cardioId &&
                        it.cardioTimerState?.status?.name == "RUNNING"
                }
            }
            onMain { vm.startCardioGps() }
            awaitRecording(nextKey)
            // Dispatch an already-parsed voice command through the real controller
            // callback. This does not exercise acoustic recognition or a microphone.
            onMain { dispatchParsedVoiceCommand(vm, VoiceSessionCommand.CancelSession) }
            val cancelled = withTimeout(20_000L) { vm.uiState.first { it.wasCancelled } }
            awaitGpsServiceRunning(false)
            assertFalse("El FGS cardio termina al cancelar por voz", isGpsServiceRunning())
            assertEquals(CardioGpsStatus.INACTIVE, CardioGpsTracker.state.value.status)
            assertFalse(vm.uiState.value.isRestTimerRunning)
            assertEquals(0, vm.restTimerRemaining.value)
            assertNull(vm.sessionTimeRemainingSeconds.value)
            assertFalse("El timer de movilidad termina al cancelar por voz", cancelled.mobilityTotalTimerState?.isRunning == true)
            assertNull(db.stateDao().getOngoingWorkout())
            assertTrue(db.workoutLogDao().getAll().isEmpty())

            val mobilityAfterCancel = cancelled.mobilityTotalTimerState?.remainingSeconds
            delay(1_300L)
            assertEquals(mobilityAfterCancel, vm.uiState.value.mobilityTotalTimerState?.remainingSeconds)
            assertEquals(0, vm.restTimerRemaining.value)
            assertNull(vm.sessionTimeRemainingSeconds.value)

            onMain {
                viewModelStore.clear()
                viewModel = null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            listenerExecutor.submit(Runnable {}).get(5, TimeUnit.SECONDS)
            assertTrue(
                "Workout constructor/start/record/cancel performed disk IO on Main:\n${violations.joinToString("\n")}",
                violations.isEmpty(),
            )
        } finally {
            if (strictPolicyInstalled) onMain { previousPolicy?.let { StrictMode.setThreadPolicy(it) }; Unit }
            listenerExecutor.shutdownNow()
        }
    }

    private suspend fun <T> awaitGpsPhase(
        phase: String,
        timeoutMs: Long,
        vm: WorkoutViewModel? = viewModel,
        requestedGpsKey: String? = null,
        block: suspend () -> T,
    ): T = try {
        withTimeout(timeoutMs) { block() }
    } catch (timeout: TimeoutCancellationException) {
        val workout = vm?.uiState?.value
        val gps = CardioGpsTracker.state.value
        val ongoingSession = repository.ongoingWorkout.value?.session?.id
        val serviceRunning = runCatching { isGpsServiceRunning() }
            .fold(onSuccess = { it.toString() }, onFailure = { "unavailable:${it.javaClass.simpleName}" })
        val serviceForeground = runCatching { isGpsServiceForeground() }
            .fold(onSuccess = { it.toString() }, onFailure = { "unavailable:${it.javaClass.simpleName}" })
        val message = listOf(
            "Timed out in phase=" + phase + " after " + timeoutMs + "ms",
            "repositoryReady=" + repository.isReady.value,
            "ongoingSession=" + ongoingSession,
            "vmSession=" + workout?.session?.id,
            "vmStarting=" + workout?.isStartingWorkout,
            "vmWasCancelled=" + workout?.wasCancelled,
            "requestedGpsKey=" + (requestedGpsKey ?: gps.sessionKey ?: "<none>"),
            "gpsSession=" + gps.sessionKey,
            "gpsStatus=" + gps.status,
            "gpsPoints=" + gps.pointCount,
            "gpsDistanceMeters=" + gps.distanceMeters,
            "gpsServiceRunning=" + serviceRunning,
            "gpsServiceForeground=" + serviceForeground,
        ).joinToString("; ")
        throw AssertionError(message, timeout)
    }

    private suspend fun createViewModelAndAwaitActiveSession(): WorkoutViewModel {
        val vm = onMain {
            WorkoutViewModel(
                appContext = context,
                programId = programId,
                sessionId = sessionId,
                restAlertManager = WorkoutRestAlertManager(context),
            ).also {
                viewModel = it
                viewModelStore.put("gps-lifecycle-$suffix", it)
            }
        }
        awaitGpsPhase("viewmodel.active-session", 30_000L, vm = vm) {
            vm.uiState.first { state ->
                state.session?.id == sessionId && !state.isStartingWorkout &&
                    repository.ongoingWorkout.value?.session?.id == sessionId
            }
        }
        return vm
    }

    private suspend fun awaitRecording(sessionKey: String) {
        awaitGpsPhase(
            phase = "gps.tracker-recording",
            timeoutMs = 15_000L,
            requestedGpsKey = sessionKey,
        ) {
            CardioGpsTracker.state.first {
                it.sessionKey == sessionKey && it.status == CardioGpsStatus.RECORDING
            }
        }
    }

    private suspend fun awaitGpsServiceForeground(expected: Boolean) {
        awaitGpsPhase(
            phase = "gps.service-foreground.expected-$expected",
            timeoutMs = 15_000L,
            requestedGpsKey = CardioGpsTracker.state.value.sessionKey,
        ) {
            while (isGpsServiceForeground() != expected) delay(100L)
        }
    }

    private suspend fun awaitGpsServiceRunning(expected: Boolean) {
        awaitGpsPhase(
            phase = "gps.service-running.expected-$expected",
            timeoutMs = 15_000L,
            requestedGpsKey = CardioGpsTracker.state.value.sessionKey,
        ) {
            while (isGpsServiceRunning() != expected) delay(100L)
        }
    }

    private fun isGpsServiceRunning(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == CardioGpsForegroundService::class.java.name }

    private fun isGpsServiceForeground(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == CardioGpsForegroundService::class.java.name && it.foreground }

    private fun activeNotifications() =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications.toList()

    private fun <T : Any> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private suspend fun <T> onMainSuspending(block: suspend () -> T): T =
        withContext(Dispatchers.Main.immediate) { block() }

    private fun dispatchParsedVoiceCommand(vm: WorkoutViewModel, command: VoiceSessionCommand) {
        val field = WorkoutViewModel::class.java.getDeclaredField("voiceController").apply { isAccessible = true }
        val controller = field.get(vm) as WorkoutVoiceController
        requireNotNull(controller.onCommandDetected) { "WorkoutViewModel must wire the parsed-voice callback" }
            .invoke(command)
    }

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
            .set(null, database)
    }

    private fun clearSingleton(owner: Class<*>) {
        val field = singletonField(owner)
        field.isAccessible = true
        field.set(singletonReceiver(owner, field), null)
    }

    private fun singletonField(owner: Class<*>): Field =
        (sequenceOf(owner) + owner.declaredClasses.asSequence())
            .flatMap { it.declaredFields.asSequence() }
            .first { field ->
                (field.name.equals("instance", ignoreCase = true) || field.name == "INSTANCE") &&
                    owner.isAssignableFrom(field.type)
            }

    private fun singletonReceiver(owner: Class<*>, field: Field): Any? {
        if (Modifier.isStatic(field.modifiers)) return null
        return owner.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
    }

    private fun fixtureProgram() = Program(
        id = programId,
        name = "Programa AVD GPS",
        macrocycles = listOf(
            Macrocycle(
                id = "macro-$suffix",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block-$suffix",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-$suffix",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week-$suffix",
                                        name = "Semana",
                                        sessions = listOf(fixtureSession),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    companion object {
        // CardioGpsForegroundService.NOTIFICATION_ID is private; this fixed ID is its public runtime contract.
        private const val CARDIO_GPS_NOTIFICATION_ID = 42042
    }

    private class IsolatedWorkoutContext(
        targetContext: Context,
        private val preferenceContext: Context,
        private val suffix: String,
    ) : ContextWrapper(targetContext) {
        private val isolatedNames = linkedSetOf<String>()

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val isolatedName = "workout-gps-$suffix-$name"
            synchronized(isolatedNames) { isolatedNames += isolatedName }
            return preferenceContext.getSharedPreferences(isolatedName, mode)
        }

        fun clearIsolatedPreferences() {
            val names = synchronized(isolatedNames) { isolatedNames.toList().also { isolatedNames.clear() } }
            names.forEach(preferenceContext::deleteSharedPreferences)
        }
    }
}
