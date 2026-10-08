package com.example.kpkn.data.backup

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

/** Process-owned maintenance survives disposal of the Settings ViewModel that requested it. */
object AppDataSessions {
    val coordinator = DataSessionCoordinator()
    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()
    private var activity = WeakReference<ComponentActivity>(null)
    private var stopStartup: suspend () -> Unit = {}
    private val attached = MutableStateFlow(0L)
    @Volatile private var currentContext: Context? = null

    fun attach(activity: ComponentActivity, cancelStartup: suspend () -> Unit) {
        this.activity = WeakReference(activity)
        this.stopStartup = cancelStartup
        attached.value += 1
    }

    init {
        coordinator.register(object : DataSessionCoordinator.Participant {
            override suspend fun quiesce() {
                com.example.kpkn.services.workout.ActiveWorkoutHolder.get()?.flushOngoingForBackgroundAndAwait()
                com.example.kpkn.services.cardio.CardioGpsTracker.pausePersonalWrites()
                runCatching { ProgramRepository.getInstance() }.getOrNull()?.flushPendingWrites()
                withContext(Dispatchers.Main) { activity.get()?.viewModelStore?.clear() }
                stopStartup()
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.quiesceForDataSession(requireNotNull(currentContext))
                com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.pauseForDataSession()
                com.example.kpkn.telemetry.nutrition.NutritionTelemetry.pauseForDataSession()
                com.example.kpkn.data.sync.OwnedStorageTasks.pauseAndAwait()
                com.example.kpkn.data.account.AccountGraph.stop()
                val context = requireNotNull(currentContext)
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.pauseForDataSession()
                WorkManager.getInstance(context).cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                com.example.kpkn.data.account.AccountSyncWorker.awaitStopped()
                withTimeout(30_000) {
                    MediaPersistenceOwner.scope.coroutineContext[Job]?.children?.toList()?.joinAll()
                }
                if (coordinator.state.value.let { (it as? DataSessionCoordinator.State.Maintaining)?.operation?.startsWith("export") != true })
                    com.example.kpkn.services.workout.WorkoutRestAlertManager(context).cancelRestAlerts(cancelFinished = true)
                NutritionRepository.closeForDataSession()
                BodyProgressRepository.closeForDataSession()
                CompetitionRepository.closeForDataSession()
                ProgramRepository.closeInstance()
                CustomExerciseRepository.closeForDataSession()
                SessionTemplateRepository.closeForDataSession()
                WorkoutMediaRepository.closeInstance()
                AugeRepository.closeForDataSession()
                NutritionCalibrationRepository.closeForDataSession()
                com.example.kpkn.data.preferences.PreferenceStores.closeForDataSession()
                com.example.kpkn.services.cardio.CardioGpsTracker.pausePersonalWrites()
            }
            override suspend fun reopen() {
                val context = requireNotNull(currentContext)
                AppBootstrap.prepare(context)
                com.example.kpkn.data.account.AccountGraph.attach(context)
                withContext(Dispatchers.Main) { _generation.value += 1 }
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.resumeForDataSession(context)
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.resumeForDataSession()
                com.example.kpkn.services.cardio.CardioGpsTracker.resumePersonalWrites()
                com.example.kpkn.data.sync.OwnedStorageTasks.resume()
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.resumeForDataSession(context)
                com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.initialize(context)
                com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.resumeForDataSession(context)
                com.example.kpkn.telemetry.nutrition.NutritionTelemetry.resumeForDataSession(context)
            }
        })
    }

    fun launch(context: Context, operation: String, onSuccess: () -> Unit, onError: (String) -> Unit,
        requiresInactiveWorkout: Boolean = true, action: suspend () -> Unit) {
        owner.launch {
            try {
                coordinator.maintenance(operation, activeWorkout = {
                    currentContext = context.applicationContext
                    val active = KpknDatabase.getInstance(context).stateDao().getOngoingWorkout() != null
                    (requiresInactiveWorkout && (active || hasActiveGpsRoute())) || MediaPersistenceOwner.activeCameraCaptures.isNotEmpty()
                }) {
                    if (!operation.startsWith("export")) com.example.kpkn.services.cardio.CardioGpsTracker.releaseProfileMemory()
                    action()
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { onError(error.message ?: "Data operation failed") }
            }
        }
    }
    fun retry(context: Context) = launch(context, "recover", {}, {}) { }

    /** Identity changes execute in the process owner, beyond the disposed caller ViewModel. */
    suspend fun changeIdentity(context: Context, discardOngoing: Boolean = false, action: suspend () -> String?) = withContext(NonCancellable + Dispatchers.IO) {
        coordinator.maintenance("account", activeWorkout = {
            currentContext = context.applicationContext
            (!discardOngoing && (KpknDatabase.getInstance(context).stateDao().getOngoingWorkout() != null || hasActiveGpsRoute())) || MediaPersistenceOwner.activeCameraCaptures.isNotEmpty()
        }) {
            val uid = action()
            com.example.kpkn.services.cardio.CardioGpsTracker.releaseProfileMemory()
            KpknDatabase.closeInstance()
            val selected = com.example.kpkn.data.account.ProfileStorage.select(uid, newGuest = uid == null)
            currentContext = com.example.kpkn.data.account.ProfileStorage.context(context, selected)
            val prior = attached.value
            withContext(Dispatchers.Main) { activity.get()?.recreate() }
            withTimeout(30_000) { attached.first { it > prior } }
        }
    }
    private fun hasActiveGpsRoute(): Boolean = com.example.kpkn.services.cardio.CardioGpsTracker.state.value.let {
        it.sessionKey != null && it.status in setOf(com.example.kpkn.services.cardio.CardioGpsStatus.REQUESTING_PERMISSION,
            com.example.kpkn.services.cardio.CardioGpsStatus.RECORDING, com.example.kpkn.services.cardio.CardioGpsStatus.PAUSED,
            com.example.kpkn.services.cardio.CardioGpsStatus.SIGNAL_LOST)
    }
}
