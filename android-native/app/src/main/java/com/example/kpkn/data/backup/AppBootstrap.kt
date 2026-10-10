package com.example.kpkn.data.backup

import android.content.Context
import com.example.kpkn.data.account.ProfileStorage
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Personal readiness is independent of catalog preparation and never converts unreadable rows to success. */
object AppBootstrap {
    sealed interface State { data object Preparing : State; data object Ready : State; data object Recovery : State }
    private val _state = MutableStateFlow<State>(State.Preparing)
    val state = _state.asStateFlow()
    private val mutex = Mutex()
    suspend fun prepare(context: Context) = prepare(context, MaintenanceCheckpoint.None)
    internal suspend fun prepare(context: Context, checkpoint: MaintenanceCheckpoint) = withContext(Dispatchers.IO) { mutex.withLock {
        _state.value = State.Preparing
        try {
            checkpoint.reached(MaintenancePhase.REOPEN_STARTED)
            if (PersonalDataErasure.pending()) {
                com.example.kpkn.data.sync.OwnedStorageTasks.pauseAndAwait()
                androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get(10, java.util.concurrent.TimeUnit.SECONDS)
                com.example.kpkn.data.account.AccountSyncWorker.awaitStopped()
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.quiesceForDataSession(context)
                com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.pauseForDataSession()
                com.example.kpkn.telemetry.nutrition.NutritionTelemetry.pauseForDataSession()
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.pauseForDataSession()
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.pauseForDataSession()
                PersonalDataErasure.finishStores(context)
                com.example.kpkn.data.diagnostics.KpknDiagnosticLogger.resumeForDataSession(context)
                com.example.kpkn.services.diagnostics.KpknDiagnosticStorage.resumeForDataSession()
                com.example.kpkn.services.workout.WorkoutVoiceRuntime.resumeForDataSession(context)
                com.example.kpkn.data.sync.OwnedStorageTasks.resume()
                com.example.kpkn.services.workout.WorkoutVoiceDiagnosticLogger.initialize(context)
                com.example.kpkn.services.workout.WorkoutVoiceExitInfoCollector.resumeForDataSession(context)
                com.example.kpkn.telemetry.nutrition.NutritionTelemetry.resumeForDataSession(context)
            }
            check(!ProfileStorage.recoveryNeeded.value) { "Profile identity requires recovery" }
            PortableBackup.recover(context, checkpoint)
            com.example.kpkn.domain.nutrition.FoodKnowledge.install(com.example.kpkn.data.food.FoodKnowledgeStore.load(context))
            val programs = ProgramRepository.init(context)
            CompetitionRepository.init(context)
            AugeRepository.getInstance(context)
            WorkoutMediaRepository.init(context)
            CustomExerciseRepository.initialize(context)
            SessionTemplateRepository.getInstance(context).refreshFromStorage()
            val nutrition = NutritionRepository.init(context)
            withTimeout(60_000) { programs.isReady.first { it }; nutrition.awaitPersonalData(); BodyProgressRepository.getInstance(context).awaitReady() }
            val database = KpknDatabase.getInstance(context)
            val ids = database.programDao().getAllHeaders().map { it.id }.toSet()
            val logs = database.openHelper.readableDatabase.query("SELECT id FROM workout_logs").use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            check(programs.programs.value.map { it.id }.toSet() == ids && programs.history.value.map { it.id }.toSet() == logs)
            check(nutrition.nutritionLogs.value.size == database.nutritionDao().getAllLogs().size)
            database.settingsDao().get()?.let { com.example.kpkn.data.db.dbJson.decodeFromString(com.example.kpkn.data.models.Settings.serializer(), it.data) }
            com.example.kpkn.data.account.AccountGraph.account(context)
            checkpoint.reached(MaintenancePhase.REOPEN_READBACK)
            withContext(Dispatchers.Main) { _state.value = State.Ready }
        } catch (error: Throwable) { withContext(Dispatchers.Main) { _state.value = State.Recovery }; throw error }
    } }
}
