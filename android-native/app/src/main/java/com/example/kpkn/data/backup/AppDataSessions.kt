package com.example.kpkn.data.backup

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.work.WorkManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.lang.ref.WeakReference

/** Process-owned maintenance survives disposal of the Settings ViewModel that requested it. */
object AppDataSessions {
    val coordinator = DataSessionCoordinator()
    private val _blockedUse = MutableStateFlow<BlockedDataUse?>(null)
    val blockedUse = _blockedUse.asStateFlow()
    fun dismissBlockedUse() { _blockedUse.value = null }
    private suspend fun publishBlockedUse(context: Context, operation: String) {
        val ongoing = runCatching {
            KpknDatabase.getInstance(context).stateDao().getOngoingWorkout()?.toOngoingWorkoutState()
        }.getOrNull()
        _blockedUse.value = BlockedDataUse(operation, ongoing?.let { MaintenanceWorkout(it.programId, it.session.id) })
    }

    private val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _generation = MutableStateFlow(0L)
    val generation = _generation.asStateFlow()
    private var activity = WeakReference<ComponentActivity>(null)
    private var stopStartup: suspend () -> Unit = {}
    private val attached = MutableStateFlow(0L)
    @Volatile private var currentContext: Context? = null
    private var currentCheckpoint: MaintenanceCheckpoint = MaintenanceCheckpoint.None

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
                DataSessionWriters.closeAdmission()
                withContext(Dispatchers.Main) { activity.get()?.viewModelStore?.clear() }
                stopStartup()
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.quiesceForDataSession(requireNotNull(currentContext))
                com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.pauseForDataSession()
                com.example.kpkn.telemetry.nutrition.NutritionTelemetry.pauseForDataSession()
                com.example.kpkn.data.sync.OwnedStorageTasks.pauseAndAwait()
                com.example.kpkn.data.account.AccountGraph.stop(resetEntitlement = false)
                val context = requireNotNull(currentContext)
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.pauseForDataSession()
                WorkManager.getInstance(context).cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                com.example.kpkn.data.account.AccountSyncWorker.awaitStopped()
                MediaPersistenceOwner.writes.pauseAndAwait()
                check(MediaPersistenceOwner.activeCameraCaptures.isEmpty()) {
                    "La captura de cámara debe terminar antes de cambiar los datos."
                }
                if (coordinator.state.value.let { (it as? DataSessionCoordinator.State.Maintaining)?.operation?.startsWith("export") != true })
                    com.example.kpkn.services.workout.WorkoutRestAlertManager(context).cancelRestAlerts(cancelFinished = true)
                com.example.kpkn.services.workout.WorkoutRestAlertManager.pauseForDataSession()
                retireRepositories()
                com.example.kpkn.services.cardio.CardioGpsTracker.pausePersonalWrites()
                DataSessionWriters.awaitStopped()
                com.example.kpkn.data.account.AccountGraph.stop()
            }
            override suspend fun reopen() {
                val context = requireNotNull(currentContext)
                // A failed early quiesce must not reuse an owner whose admission was retired.
                stopStartup()
                com.example.kpkn.services.workout.WorkoutRestAlertManager.pauseForDataSession()
                retireRepositories()
                DataSessionWriters.awaitStopped()
                DataSessionWriters.reopenAdmission()
                com.example.kpkn.services.workout.WorkoutRestAlertManager.resumeForDataSession()
                MediaPersistenceOwner.writes.resume()
                MediaPersistenceOwner.pendingCaptureRetryState.value = com.example.kpkn.data.media.WorkoutMediaPendingCaptureState()
                AppBootstrap.prepare(context, currentCheckpoint)
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

    private suspend fun retireRepositories() {
        WikiLabRepository.closeForDataSession()
        NutritionRepository.closeForDataSession()
        BodyProgressRepository.closeForDataSession()
        CompetitionRepository.closeForDataSession()
        ProgramRepository.closeInstance()
        CustomExerciseRepository.closeForDataSession()
        SessionTemplateRepository.closeForDataSession()
        WorkoutMediaRepository.closeInstance()
        AugeRepository.closeForDataSession()
        NutritionCalibrationRepository.closeForDataSession()
        com.example.kpkn.screens.sessioneditor.SessionEditorDraftStore.closeForDataSession()
        com.example.kpkn.screens.sessioneditor.RuleTemplateStore.closeForDataSession()
        com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore.closeForDataSession()
        com.example.kpkn.data.preferences.PreferenceStores.closeForDataSession()
    }

    fun launch(context: Context, operation: String, onSuccess: () -> Unit, onError: (String) -> Unit,
        requiresInactiveWorkout: Boolean = true, action: suspend () -> Unit) =
        launch(context, operation, onSuccess, onError, requiresInactiveWorkout, MaintenanceCheckpoint.None, action)

    internal fun launch(context: Context, operation: String, onSuccess: () -> Unit, onError: (String) -> Unit,
        requiresInactiveWorkout: Boolean, checkpoint: MaintenanceCheckpoint, action: suspend () -> Unit) {
        owner.launch {
            try {
                coordinator.maintenance(operation, activeWorkout = {
                    currentContext = context.applicationContext
                    currentCheckpoint = checkpoint
                    val active = KpknDatabase.getInstance(context).stateDao().getOngoingWorkout() != null
                    (requiresInactiveWorkout && (active || hasActiveGpsRoute())) || MediaPersistenceOwner.activeCameraCaptures.isNotEmpty()
                }) {
                    if (!operation.startsWith("export")) com.example.kpkn.services.cardio.CardioGpsTracker.releaseProfileMemory()
                    action()
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (blocked: ActiveDataUseException) {
                publishBlockedUse(context, operation)
            } catch (error: Exception) {
                withContext(Dispatchers.Main) { onError(error.message ?: "Data operation failed") }
            }
        }
    }
    fun retry(context: Context) = launch(context, "recover", {}, {}, requiresInactiveWorkout = false) { }

    /** Identity changes execute in the process owner, beyond the disposed caller ViewModel. */
    suspend fun changeIdentity(context: Context, discardOngoing: Boolean = false, action: suspend () -> String?) = withContext(NonCancellable + Dispatchers.IO) {
        try {
        coordinator.maintenance("account", activeWorkout = {
            currentContext = context.applicationContext
            currentCheckpoint = MaintenanceCheckpoint.None
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
        } catch (blocked: ActiveDataUseException) {
            publishBlockedUse(context, "account")
            throw blocked
        }
    }
    private fun hasActiveGpsRoute(): Boolean = com.example.kpkn.services.cardio.CardioGpsTracker.state.value.let {
        it.sessionKey != null && it.status in setOf(com.example.kpkn.services.cardio.CardioGpsStatus.REQUESTING_PERMISSION,
            com.example.kpkn.services.cardio.CardioGpsStatus.RECORDING, com.example.kpkn.services.cardio.CardioGpsStatus.PAUSED,
            com.example.kpkn.services.cardio.CardioGpsStatus.SIGNAL_LOST)
    }
}
