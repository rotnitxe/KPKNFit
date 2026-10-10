package com.example.kpkn.data.account

import android.content.Context
import androidx.work.*
import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.repository.*
import com.example.kpkn.data.sync.*
import com.example.kpkn.domain.entitlements.EntitlementState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

/** Manual dependency injection. Each personal graph captures one UID and one fixed profile context. */
object AccountGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var profileJob: Job? = null
    private var foregroundSync: RoomSyncRepository? = null
    private var currentEntitlement: FirebaseEntitlementRepository? = null
    private var play: PlaySubscriptionRepository? = null
    private var platform: Context? = null
    private var session: FirebaseSession? = null
    private var accounts: FirebaseAccountRepository? = null
    private var attachedProfileKey: String? = null
    private val _entitlement = MutableStateFlow(EntitlementState())
    /** Último estado sin el interruptor de depuración: se vuelve a publicar al cambiarlo. */
    private var rawEntitlement = EntitlementState()
    val entitlement = _entitlement.asStateFlow()
    /** Única escritura de `_entitlement`: aplica el interruptor de depuración (en release, la identidad). Hilo principal. */
    private fun publish(value: EntitlementState) {
        rawEntitlement = value
        _entitlement.value = BuildEntitlementOverride.apply(value)
    }
    /** Vuelve a publicar el último estado sin tocar, p. ej. al encender el interruptor de depuración. Hilo principal. */
    internal fun republishEntitlement() = publish(rawEntitlement)
    fun account(context: Context): FirebaseAccountRepository = accounts ?: synchronized(this) {
        accounts ?: run {
            platform = ProfileStorage.platformContext(); session = FirebaseRuntime.get(requireNotNull(platform))
            FirebaseAccountRepository(requireNotNull(platform), session).also { accounts = it }
        }
    }
    fun subscriptions(context: Context): PlaySubscriptionRepository {
        account(context)
        return play ?: synchronized(this) { play ?: PlaySubscriptionRepository(requireNotNull(platform), scope) { currentEntitlement }.also { play = it } }
    }
    fun sync(): RoomSyncRepository? = foregroundSync
    suspend fun refreshEntitlement(uid: String) {
        check(ProfileStorage.current().uid == uid)
        currentEntitlement?.refresh()
    }
    suspend fun stop(resetEntitlement: Boolean = true) {
        if (attachedProfileKey != null) com.example.kpkn.data.diagnostics.CrashReportingController.setEnabled(false)
        attachedProfileKey = null
        profileJob?.cancelAndJoin(); profileJob = null; foregroundSync = null; currentEntitlement = null
        if (resetEntitlement) withContext(Dispatchers.Main) { publish(EntitlementState()) }
    }
    @OptIn(FlowPreview::class)
    suspend fun attach(context: Context) {
        stop()
        val account = account(context); withContext(Dispatchers.Main) { account.refresh() }
        com.example.kpkn.data.diagnostics.CrashReportingController.attachConsent(context)
        attachedProfileKey = ProfileStorage.current().key
        val uid = ProfileStorage.current().uid ?: return
        val runtime = session?.takeIf { it.auth.currentUser?.uid == uid } ?: return
        val fixed = ProfileStorage.context(context)
        val transport = AccountTransport(runtime)
        val database = KpknDatabase.getInstance(fixed)
        val entitlements = FirebaseEntitlementRepository(fixed, uid, transport)
        currentEntitlement = entitlements
        foregroundSync = RoomSyncRepository(fixed, uid, database, transport, PrivateDataTransport(fixed, uid, runtime), MediaTransfer(fixed, uid, runtime))
        runCatching { entitlements.load() }
        val writer = com.example.kpkn.data.backup.DataSessionWriter("account-profile-observers")
        profileJob = writer.launch(scope) {
            launch {
                var wasPro = false
                entitlements.state.collect { value ->
                    withContext(Dispatchers.Main) { publish(value) }
                    val pro = value.effectiveLevel == com.example.kpkn.domain.entitlements.PlanLevel.PRO
                    if (pro && !wasPro) {
                        database.syncJournalDao().releaseCapabilityWait()
                        val capabilities = fixed.getSharedPreferences("sync_capabilities", 0)
                        if (!capabilities.getBoolean("last_confirmed_pro", false) && database.syncJournalDao().configuration()?.enabled == true) {
                            database.withTransaction {
                                database.openHelper.writableDatabase.execSQL("""INSERT OR IGNORE INTO sync_mutations(entityTable,rawKey,dirtyVersion,deleted,baseRevision,operationId,operationJson,sentVersion,status)
                                    SELECT 'workout_media',m.id,1,0,COALESCE(s.serverRevision,0),NULL,NULL,0,'WAITING'
                                    FROM workout_media m LEFT JOIN sync_shadows s ON s.entityTable='workout_media' AND s.rawKey=m.id""".trimIndent())
                            }
                        }
                        check(capabilities.edit().putBoolean("last_confirmed_pro", true).commit())
                        if (database.syncJournalDao().configuration()?.enabled == true) enqueue(fixed, uid)
                    }
                    if (!pro) fixed.getSharedPreferences("sync_capabilities", 0).let { preferences ->
                        if (preferences.getBoolean("last_confirmed_pro", false)) check(preferences.edit().putBoolean("last_confirmed_pro", false).commit())
                    }
                    wasPro = pro
                }
            }
            launch { runCatching { entitlements.refresh() } }
            launch {
                var ticks = 0
                while (isActive) {
                    delay(30_000); entitlements.updateClock()
                    if (++ticks % 20 == 0) runCatching { entitlements.refresh() }
                }
            }
            launch {
                database.invalidationTracker.createFlow(*com.example.kpkn.data.db.SyncJournalSchema.keys.keys.toTypedArray(), emitInitialState = false)
                    .debounce(1000).collect { if (database.syncJournalDao().configuration()?.enabled == true) enqueue(fixed, uid) }
            }
            if (database.syncJournalDao().configuration()?.enabled == true || java.io.File(fixed.noBackupFilesDir, "pending-guest-import.json").isFile) enqueue(fixed, uid)
        }
    }
    fun enqueue(context: Context, uid: String) {
        val profile = ProfileStorage.current()
        if (com.example.kpkn.data.backup.PersonalDataErasure.pending()) return
        check(profile.uid == uid)
        val data = workDataOf("uid" to uid, "profileKey" to profile.key, "epoch" to profile.epoch)
        val request = OneTimeWorkRequestBuilder<AccountSyncWorker>().setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(profile.key).build()
        WorkManager.getInstance(ProfileStorage.platformContext()).enqueueUniqueWork("kpkn-sync-${profile.key}", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        val periodic = PeriodicWorkRequestBuilder<AccountSyncWorker>(15, TimeUnit.MINUTES).setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag(profile.key).build()
        WorkManager.getInstance(ProfileStorage.platformContext()).enqueueUniquePeriodicWork("kpkn-sync-periodic-${profile.key}", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }
    suspend fun refreshReaders(context: Context) {
        ProgramRepository.getInstance().refreshData()
        NutritionRepository.getInstance().refreshData(context)
        BodyProgressRepository.getInstance(context).refreshFromStorage()
        CustomExerciseRepository.refreshFromStorage()
        SessionTemplateRepository.getInstance(context).refreshFromStorage()
    }
}

class AccountSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val job = currentCoroutineContext()[Job] ?: return Result.failure()
        activeJobs[id] = job
        try { return synchronize() } finally { activeJobs.remove(id, job) }
    }
    private suspend fun synchronize(): Result {
        if (com.example.kpkn.data.backup.PersonalDataErasure.pending()) return Result.retry()
        val uid = inputData.getString("uid") ?: return Result.failure()
        val profile = ProfileStorage.current()
        if (profile.uid != uid || profile.key != inputData.getString("profileKey") || profile.epoch != inputData.getLong("epoch", -1)) return Result.success()
        if (com.example.kpkn.data.backup.AppDataSessions.coordinator.state.value !is com.example.kpkn.data.backup.DataSessionCoordinator.State.Ready) return Result.retry()
        val fixed = ProfileStorage.context(applicationContext, profile)
        val runtime = FirebaseRuntime.get(ProfileStorage.platformContext())?.takeIf { it.auth.currentUser?.uid == uid } ?: return Result.failure()
        return try {
            if (com.example.kpkn.data.backup.AppBootstrap.state.value != com.example.kpkn.data.backup.AppBootstrap.State.Ready)
                com.example.kpkn.data.backup.AppBootstrap.prepare(fixed)
            GuestImportCoordinator(ProfileStorage.platformContext(), runtime, AccountTransport(runtime)).resume(fixed, uid)
            runCatching { AccountGraph.refreshEntitlement(uid) }
            val database = KpknDatabase.getInstance(fixed)
            if (database.syncJournalDao().configuration()?.enabled == true) {
                val repository = AccountGraph.sync() ?: RoomSyncRepository(fixed, uid, database, AccountTransport(runtime), PrivateDataTransport(fixed, uid, runtime), MediaTransfer(fixed, uid, runtime))
                do { repository.synchronize() } while (database.syncJournalDao().configuration()?.enabled == true && database.syncJournalDao().pending().isNotEmpty())
                if (com.example.kpkn.data.backup.AppBootstrap.state.value == com.example.kpkn.data.backup.AppBootstrap.State.Ready) AccountGraph.refreshReaders(fixed)
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AccountServiceException) {
            if (error.code in setOf("PERMISSION_DENIED", "UNAUTHENTICATED")) Result.failure() else Result.retry()
        } catch (_: Exception) { Result.retry() }
    }
    companion object {
        private val activeJobs = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Job>()
        suspend fun awaitStopped() = withTimeout(60_000) { activeJobs.values.toList().joinAll() }
    }
}
