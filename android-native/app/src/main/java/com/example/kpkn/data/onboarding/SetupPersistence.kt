package com.example.kpkn.data.onboarding

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.db.ActiveProgramEntity
import com.example.kpkn.data.db.BodyGoalEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.ProgramEntity
import com.example.kpkn.data.db.SettingsEntity
import com.example.kpkn.data.db.SetupCommitReceiptEntity
import com.example.kpkn.data.db.SetupDraftEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramActiveStateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val persistenceJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
private val receiptJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class SetupDraft(
    val draftId: String,
    val payloadJson: String,
    val revision: Long,
    val catalogRevision: String? = null,
    val updatedAtEpochMs: Long,
)

data class PendingNutritionDraft(
    val draftId: String,
    val payloadJson: String,
    val revision: Long,
    val catalogRevision: String? = null,
)

data class SetupCommitRequest(
    val commitId: String,
    val draftId: String?,
    val settings: Settings,
    val program: Program?,
    val nutritionPlan: NutritionPlan?,
    val activateProgram: Boolean,
    val activateNutrition: Boolean,
    val derivedBodyGoals: List<BodyGoal> = emptyList(),
    /** Optional partial wellbeing payload; absent when there is no explicit adjustment/discomfort. */
    val initialWellbeing: DailyWellbeingLog? = null,
    /** Preferred path: merge only the fields touched by this setup scope. */
    val settingsPatch: SetupSettingsPatch? = null,
    /** Historical daily target captured atomically when nutrition is activated. */
    val dailyGoalSnapshot: DailyGoalSnapshot? = null,
    /** Deferred professional nutrition draft written in the same transaction that deletes the main draft. */
    val pendingNutritionDraft: PendingNutritionDraft? = null,
)

data class SetupCommitResult(
    val commitId: String,
    val programId: String?,
    val nutritionPlanId: String?,
    val bodyGoalIds: List<String>,
)

class SetupDraftRepository(private val db: KpknDatabase) {
    private val writeMutex = Mutex()

    suspend fun load(draftId: String): SetupDraft? = withContext(Dispatchers.IO) {
        db.setupDraftDao().getDraft(draftId)?.toModel()
    }

    suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String? = null): SetupDraft = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            require(draftId.isNotBlank())
            require(revision >= 0)
            db.withTransaction {
                val current = db.setupDraftDao().getDraft(draftId)
                when {
                    current == null -> {
                        val entity = SetupDraftEntity(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis())
                        db.setupDraftDao().upsertDraft(entity)
                        entity.toModel()
                    }
                    revision < current.revision -> {
                        throw IllegalArgumentException("La revisión del borrador es antigua")
                    }
                    revision == current.revision && payloadJson != current.payloadJson -> {
                        throw IllegalArgumentException("Conflicto de revisión del borrador")
                    }
                    revision == current.revision -> current.toModel()
                    else -> {
                        val entity = SetupDraftEntity(draftId, payloadJson, revision, catalogRevision, System.currentTimeMillis())
                        db.setupDraftDao().upsertDraft(entity)
                        entity.toModel()
                    }
                }
            }
        }
    }

    suspend fun discard(draftId: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) { db.setupDraftDao().deleteDraft(draftId) }
    }
}

class SetupCommitCoordinator(
    private val db: KpknDatabase,
    private val programRepository: ProgramRepository? = null,
    private val nutritionRepository: NutritionRepository? = null,
) {
    private val mutex = Mutex()

    suspend fun commit(request: SetupCommitRequest): SetupCommitResult = mutex.withLock {
        PersistenceWriteCoordinator.mutex.withLock {
            require(request.commitId.isNotBlank())
            programRepository?.isReady?.first { it }
            withContext(Dispatchers.IO) {
            var result: SetupCommitResult? = null
            var shouldPublish = false
            var committedSettings: Settings? = null
            var committedProgram: Program? = null
            var committedNutritionPlan: NutritionPlan? = null
            var committedProgramIsActive = false
            var committedNutritionIsActive = false
            db.withTransaction {
                val prior = db.setupCommitReceiptDao().get(request.commitId)
                if (prior != null) {
                    result = prior.toResult()
                    committedSettings = db.settingsDao().get()?.toSettings() ?: request.settings
                    committedProgram = prior.programId?.let { db.programDao().getById(it)?.toProgram() }
                    committedNutritionPlan = prior.nutritionPlanId?.let { id ->
                        db.nutritionDao().getAllPlans().firstOrNull { it.id == id }?.toNutritionPlan()
                    }
                    committedProgramIsActive = prior.programId != null && db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId == prior.programId
                    committedNutritionIsActive = prior.nutritionPlanId != null && db.nutritionDao().getActiveState()?.activePlanId == prior.nutritionPlanId
                    request.pendingNutritionDraft?.let { upsertPendingNutritionDraft(it) }
                    request.draftId?.let { db.setupDraftDao().deleteDraft(it) }
                    shouldPublish = true
                } else {
                    val currentSettings = db.settingsDao().get()?.toSettings() ?: request.settings
                    val settingsToPersist = request.settingsPatch?.applyTo(currentSettings) ?: request.settings
                    request.program?.let { program ->
                        check(db.programDao().getById(program.id) == null) {
                            "El programa ya existe. Usa la acción de reemplazo desde su detalle."
                        }
                        check(!request.activateProgram || db.stateDao().getOngoingWorkout() == null) {
                            "Termina la sesión en curso antes de activar otro plan."
                        }
                        com.example.kpkn.domain.training.ProgramExecutionContract.requireExecutable(program)
                        db.programDao().upsert(program.toEntity())
                        if (request.activateProgram) {
                            val active = ProgramActiveStateEngine.repairForProgram(
                                program = program,
                                state = ActiveProgramState(programId = program.id),
                            ) ?: ActiveProgramState(programId = program.id)
                            db.stateDao().upsertActiveProgram(active.toEntity())
                        }
                    }
                    request.nutritionPlan?.let { plan ->
                        val wasActive = !request.activateNutrition && db.nutritionDao().getActiveState()?.activePlanId == plan.id
                        if (request.activateNutrition) db.nutritionDao().deactivateAllPlans()
                        db.nutritionDao().upsertPlan(plan.copy(isActive = request.activateNutrition || wasActive).toEntity())
                        if (request.activateNutrition) {
                            db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = plan.id))
                        }
                    }
                    db.settingsDao().upsert(settingsToPersist.toEntity())
                    request.nutritionPlan?.let { plan -> db.bodyProgressDao().deleteDerivedSetupGoals(plan.id) }
                    request.derivedBodyGoals.forEach { db.bodyProgressDao().upsertGoal(it.toEntity()) }
                    request.initialWellbeing?.let { incoming ->
                        val existing = db.augeDao().getWellbeingForDate(incoming.date)?.toWellbeingLog()
                        db.augeDao().upsertWellbeing(mergeWellbeing(existing, incoming).toEntity())
                    }
                    request.dailyGoalSnapshot?.let { db.nutritionDao().insertDailyGoalSnapshot(it.toEntity()) }
                    val next = SetupCommitResult(
                        commitId = request.commitId,
                        programId = request.program?.id,
                        nutritionPlanId = request.nutritionPlan?.id,
                        bodyGoalIds = request.derivedBodyGoals.map { it.id },
                    )
                    db.setupCommitReceiptDao().insert(next.toEntity(request.draftId))
                    request.pendingNutritionDraft?.let { upsertPendingNutritionDraft(it) }
                    request.draftId?.let { db.setupDraftDao().deleteDraft(it) }
                    result = next
                    committedSettings = settingsToPersist
                    committedProgram = request.program
                    committedNutritionPlan = request.nutritionPlan
                    shouldPublish = true
                }
            }
            val committed = requireNotNull(result)
            if (shouldPublish) {
                val settings = requireNotNull(committedSettings)
                programRepository?.publishSetupCommit(settings, committedProgram, committedProgram != null && (committedProgramIsActive || request.activateProgram))
                nutritionRepository?.publishSetupCommit(committedNutritionPlan, committedNutritionPlan != null && (committedNutritionIsActive || request.activateNutrition))
            }
            committed
            }
        }
    }

    private suspend fun upsertPendingNutritionDraft(pending: PendingNutritionDraft) {
        db.setupDraftDao().upsertDraft(
            SetupDraftEntity(
                draftId = pending.draftId,
                payloadJson = pending.payloadJson,
                revision = pending.revision,
                catalogRevision = pending.catalogRevision,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun SetupCommitReceiptEntity.toResult() = SetupCommitResult(
        commitId = commitId,
        programId = programId,
        nutritionPlanId = nutritionPlanId,
        bodyGoalIds = receiptJson.decodeFromString(bodyGoalIdsJson),
    )

    private fun SetupCommitResult.toEntity(draftId: String?) = SetupCommitReceiptEntity(
        commitId = commitId,
        draftId = draftId,
        programId = programId,
        nutritionPlanId = nutritionPlanId,
        bodyGoalIdsJson = persistenceJson.encodeToString(bodyGoalIds),
        committedAtEpochMs = System.currentTimeMillis(),
    )

    private fun mergeWellbeing(existing: DailyWellbeingLog?, incoming: DailyWellbeingLog): DailyWellbeingLog {
        if (existing == null) return incoming
        val fields = existing.capturedFields + incoming.capturedFields
        val incomingHas = { key: String -> key in incoming.capturedFields }
        return existing.copy(
            id = existing.id,
            manualMuscleBatteries = if (incomingHas("muscle_batteries")) existing.manualMuscleBatteries + incoming.manualMuscleBatteries else existing.manualMuscleBatteries,
            manualMuscleOverridesV2 = if (incomingHas("muscle_batteries")) existing.manualMuscleOverridesV2 + incoming.manualMuscleOverridesV2 else existing.manualMuscleOverridesV2,
            manualBatteryAnchorMs = if (incomingHas("muscle_batteries")) incoming.manualBatteryAnchorMs else existing.manualBatteryAnchorMs,
            manualNeuralBattery = if (incomingHas("energy")) incoming.manualNeuralBattery else existing.manualNeuralBattery,
            manualSpinalBattery = if (incomingHas("structure")) incoming.manualSpinalBattery else existing.manualSpinalBattery,
            preWorkoutDiscomforts = if (incomingHas("discomforts")) incoming.preWorkoutDiscomforts else existing.preWorkoutDiscomforts,
            capturedFields = fields,
        )
    }
}

class PersistenceFactory private constructor(
    val database: KpknDatabase,
    val drafts: SetupDraftRepository,
    val commits: SetupCommitCoordinator,
) {
    companion object {
        fun getInstance(context: Context): PersistenceFactory = create(context)

        fun create(context: Context): PersistenceFactory {
            val db = KpknDatabase.getInstance(context.applicationContext)
            val programs = ProgramRepository.init(context.applicationContext)
            val nutrition = NutritionRepository.init(context.applicationContext)
            return PersistenceFactory(
                database = db,
                drafts = SetupDraftRepository(db),
                commits = SetupCommitCoordinator(db, programs, nutrition),
            )
        }
    }
}

fun persistenceFactory(context: Context): PersistenceFactory = PersistenceFactory.getInstance(context)

private fun SetupDraftEntity.toModel() = SetupDraft(draftId, payloadJson, revision, catalogRevision, updatedAtEpochMs)
