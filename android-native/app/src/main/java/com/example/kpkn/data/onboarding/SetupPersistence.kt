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
import com.example.kpkn.data.db.toBodyObservation
import com.example.kpkn.data.db.toNutritionPlan
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.db.toWellbeingLog
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.training.ProgramActiveStateEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

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
    /**
     * Modo durable de «solo registro» elegido explícitamente en el alta:
     * excluyente con [activateNutrition] y [nutritionPlan]. En la misma
     * transacción desactiva la nutrición (historial de planes conservado, sin
     * metas derivadas ni snapshot) y persiste [Settings.nutritionTrackingOnly]
     * = true. La activación previa requiere confirmación por UI del dueño del
     * flujo; este coordinador solo aplica la desactivación transaccional.
     * El mismo criterio aplica si el modo lo declara [settingsPatch]: la intención
     * efectiva (request O parche) es excluyente con plan, activación, metas
     * derivadas o snapshot, y activar un plan en el alta resetea el modo a false.
     */
    val nutritionTrackingOnly: Boolean = false,
    /**
     * Observaciones corporales reales fechadas: peso/grasa actuales declarados y
     * pesajes históricos explícitos. Tendencias o máximos previos nunca se
     * convierten en observaciones aquí. Cada fila exige id no vacío, fecha
     * representable y no futura, y valor dentro de rango: cualquier fila
     * inválida o un mismo id con contenido contradictorio rechaza el alta
     * (rollback transaccional). Duplicados exactos e ids ya existentes con
     * contenido idéntico son idempotentes y nunca pisan filas ajenas al alta.
     * Las estimaciones visuales deben declarar [BodyObservationQuality.ESTIMATED]
     * como provenance (nunca MEASURED); el commit la conserva tal cual.
     */
    val bodyObservations: List<BodyObservation> = emptyList(),
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
                    // Replay = solo re-publicar lo ya confirmado: NUNCA repite
                    // efectos. La transacción original ya escribió el pendiente y
                    // borró el borrador principal, así que cualquier fila que hoy
                    // exista con esos ids es un artefacto NUEVO del usuario (otra
                    // revisión, otro payload) y no se pisa ni se borra.
                    request.pendingNutritionDraft?.let { replayKeepsPendingNutritionDraft(it) }
                    shouldPublish = true
                } else {
                    val currentSettings = db.settingsDao().get()?.toSettings() ?: request.settings
                    // Valida TODAS las observaciones antes de tocar nada: una fila
                    // inválida, futura o contradictoria rechaza el alta sin efectos
                    // parciales (rollback del resto de escrituras de la transacción).
                    validateBodyObservationsOrThrow(request.bodyObservations, nowEpochMs = System.currentTimeMillis())
                    // Intención efectiva de «solo registro»: la declara el
                    // request O el parche. Una sola fuente de verdad, excluyente
                    // con cualquier escritura de nutrición de este mismo alta
                    // (plan, activación, metas derivadas o snapshot), sea por
                    // request o por parche.
                    val patchTrackingOnly = request.settingsPatch?.nutritionTrackingOnly
                    require(!(request.nutritionTrackingOnly && patchTrackingOnly is SetupPatchField.Set && !patchTrackingOnly.value)) {
                        "El modo solo registro se declara de forma contradictoria entre el alta y su parche"
                    }
                    val declaredTrackingOnly = request.nutritionTrackingOnly ||
                        (patchTrackingOnly is SetupPatchField.Set && patchTrackingOnly.value)
                    require(!declaredTrackingOnly || (request.nutritionPlan == null && !request.activateNutrition)) {
                        "El modo solo registro es excluyente con un plan de nutrición o su activación"
                    }
                    require(!declaredTrackingOnly || request.derivedBodyGoals.isEmpty()) {
                        "El modo solo registro no admite metas derivadas"
                    }
                    require(!declaredTrackingOnly || request.dailyGoalSnapshot == null) {
                        "El modo solo registro no admite snapshot de metas"
                    }
                    val touchesNutritionScope = request.nutritionPlan != null ||
                        request.activateNutrition ||
                        request.derivedBodyGoals.isNotEmpty() ||
                        request.dailyGoalSnapshot != null
                    val patchedSettings = request.settingsPatch?.applyTo(currentSettings) ?: request.settings
                    val settingsToPersist = when {
                        // Modo declarado: se fuerza true (y ya sabemos que no hay
                        // plan/activación/metas/snapshot en este alta).
                        declaredTrackingOnly -> patchedSettings.copy(nutritionTrackingOnly = true)
                        // Activar o guardar un plan es un campo deliberado del
                        // alta que resetea el modo solo registro: nunca se
                        // persiste «plan activo + solo registro».
                        touchesNutritionScope -> patchedSettings.copy(nutritionTrackingOnly = false)
                        // Alcance que no toca nutrición: el modo sobrevive tal cual.
                        else -> patchedSettings
                    }
                    val trackingOnly = settingsToPersist.nutritionTrackingOnly
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
                    if (trackingOnly && request.nutritionPlan == null) {
                        // Solo registro: los planes anteriores se conservan; solo se
                        // desactiva el estado activo. Nunca se borran ni se tocan metas.
                        db.nutritionDao().deactivateAllPlans()
                        db.nutritionDao().clearActiveState()
                    }
                    db.settingsDao().upsert(settingsToPersist.toEntity())
                    request.nutritionPlan?.let { plan -> db.bodyProgressDao().deleteDerivedSetupGoals(plan.id) }
                    request.derivedBodyGoals.forEach { db.bodyProgressDao().upsertGoal(it.toEntity()) }
                    persistBodyObservations(request.bodyObservations)
                    request.initialWellbeing?.let { incoming ->
                        val existing = db.augeDao().getWellbeingForDate(incoming.date)?.toWellbeingLog()
                        db.augeDao().upsertWellbeing(mergeSetupWellbeing(existing, incoming).toEntity())
                    }
                    request.dailyGoalSnapshot?.let { db.nutritionDao().insertDailyGoalSnapshot(it.toEntity()) }
                    // Activación efectiva leída de la MISMA transacción ya
                    // confirmada (fresh). En el replay se calcula igual, desde la
                    // BD: nunca se publica «activado» por el flag del request.
                    committedProgramIsActive = request.program != null &&
                        db.stateDao().getActiveProgram()?.toActiveProgramState()?.programId == request.program.id
                    committedNutritionIsActive = request.nutritionPlan != null &&
                        db.nutritionDao().getActiveState()?.activePlanId == request.nutritionPlan.id
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
                // Efecto posterior a una transacción ya confirmada: si el job se
                // cancela aquí, la BD queda bien pero la memoria (Settings/active)
                // quedaría desfasada. Borde estrecho con NonCancellable, sin
                // cambiar la semántica asíncrona del resto del commit.
                withContext(NonCancellable) {
                    val settings = requireNotNull(committedSettings)
                    // Fresh vs replay: lo que se publica es el estado ACTUAL de la
                    // BD (calculado dentro de la transacción), no
                    // request.activateProgram. Un replay de un receipt antiguo no
                    // reactiva en memoria un programa o plan ya sustituidos.
                    programRepository?.publishSetupCommit(settings, committedProgram, committedProgramIsActive)
                    nutritionRepository?.publishSetupCommit(committedNutritionPlan, committedNutritionIsActive)
                    if (settings.nutritionTrackingOnly) {
                        // Solo registro: publishSetupCommit no publica nutrición con plan
                        // nulo; el estado activo desactivado debe reflejarse en las cachés.
                        nutritionRepository?.publishNutritionPlanCommit()
                    }
                }
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

    /**
     * Efecto idempotente del replay para el borrador de nutrición pendiente: solo
     * reafirmaría el MISMO artefacto comprometido. Un `draftId` igual con otra
     * revisión u otro payload es un borrador NUEVO escrito después del alta: no se
     * pisa con el payload del replay ni se borra, y el replay sigue siendo
     * idempotente (no falla). Si ya no existe, tampoco se resucita.
     */
    private suspend fun replayKeepsPendingNutritionDraft(pending: PendingNutritionDraft) {
        val current = db.setupDraftDao().getDraft(pending.draftId) ?: return
        val sameArtifact = current.revision == pending.revision && current.payloadJson == pending.payloadJson
        if (!sameArtifact) return
        upsertPendingNutritionDraft(pending)
    }

    /**
     * Acepta una observación tal cual la declara el alta. El id vacío, la fecha
     * no representable o futura, la zona horaria inválida y el valor fuera de
     * rango son errores; los duplicados exactos se idempotentizan y un mismo id
     * con contenido distinto es conflicto (nunca se elige first en silencio).
     */
    private fun validateBodyObservationsOrThrow(observations: List<BodyObservation>, nowEpochMs: Long) {
        observations.groupBy { it.id }.forEach { (id, group) ->
            require(id.isNotBlank()) { "La observación declarada tiene un id vacío" }
            require(group.distinct().size == 1) {
                "Id de observación duplicado con contenido distinto: $id"
            }
            val observation = group.first()
            require(observation.timestampEpochMs > 0) {
                "La observación $id tiene una fecha inválida"
            }
            require(runCatching { ZoneId.of(observation.zoneId) }.isSuccess) {
                "La observación $id tiene una zona horaria inválida"
            }
            require(observation.timestampEpochMs <= nowEpochMs) {
                "La observación $id tiene una fecha futura"
            }
            val validation = validateBodyValue(observation.metric, observation.valueSi)
            require(validation.valid) {
                validation.reason?.let { "La observación $id es inválida: $it" }
                    ?: "La observación $id tiene un valor inválido"
            }
        }
    }

    /**
     * Escritura post-validación dentro de la transacción. Un id ya existente con
     * contenido idéntico es idempotente (no se reescribe); contenido distinto es
     * conflicto que revierte el alta, de modo que nunca se pisa una observación
     * que no haya creado este mismo commit.
     */
    private suspend fun persistBodyObservations(observations: List<BodyObservation>) {
        val existingById = db.bodyProgressDao().getAllObservations().associateBy { it.id }
        observations.distinct().forEach { observation ->
            val existing = existingById[observation.id]?.toBodyObservation()
            require(existing == null || existing == observation) {
                "La observación ${observation.id} ya existe y no fue creada por este alta"
            }
            if (existing == null) db.bodyProgressDao().upsertObservation(observation.toEntity())
        }
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

}

/**
 * Merge por campos del check-in compartido entre el commit del alta y su
 * vista previa. El desconocimiento nunca toca lo existente; los "muscular"
 * (sensación muscular global), "energy" y "structure" preservan ceros
 * explícitos y los canales manuales no tocados quedan intactos.
 */
internal fun mergeSetupWellbeing(existing: DailyWellbeingLog?, incoming: DailyWellbeingLog): DailyWellbeingLog {
    if (existing == null) return incoming
    val fields = existing.capturedFields + incoming.capturedFields
    val incomingHas = { key: String -> key in incoming.capturedFields }
    return existing.copy(
        id = existing.id,
        manualMuscularBattery = if (incomingHas("muscular")) incoming.manualMuscularBattery else existing.manualMuscularBattery,
        manualMuscleBatteries = if (incomingHas("muscle_batteries")) existing.manualMuscleBatteries + incoming.manualMuscleBatteries else existing.manualMuscleBatteries,
        manualMuscleOverridesV2 = if (incomingHas("muscle_batteries")) existing.manualMuscleOverridesV2 + incoming.manualMuscleOverridesV2 else existing.manualMuscleOverridesV2,
        manualBatteryAnchorMs = if (incomingHas("muscle_batteries")) incoming.manualBatteryAnchorMs else existing.manualBatteryAnchorMs,
        manualNeuralBattery = if (incomingHas("energy")) incoming.manualNeuralBattery else existing.manualNeuralBattery,
        manualSpinalBattery = if (incomingHas("structure")) incoming.manualSpinalBattery else existing.manualSpinalBattery,
        preWorkoutDiscomforts = if (incomingHas("discomforts")) incoming.preWorkoutDiscomforts else existing.preWorkoutDiscomforts,
        capturedFields = fields,
    )
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
