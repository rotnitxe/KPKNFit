package com.example.kpkn.data.repository

import androidx.room.withTransaction
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionActiveStateEntity
import com.example.kpkn.data.db.SetupCommitReceiptEntity
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toSettings
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.domain.nutrition.NutritionGoalSource
import com.example.kpkn.domain.nutrition.dailyGoalSnapshotOf
import com.example.kpkn.domain.nutrition.planDayTargetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val commitReceiptJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Petición de guardado del editor nutricional directo.
 *
 * @param commitId identificador de IDEMPOTENCIA de ESTA operación de edición.
 *   Nunca se reutiliza entre operaciones: un doble tap o un reintento de la
 *   misma operación repite el mismo id y no duplica nada.
 * @param plan base revisada a persistir tal cual; null en modo solo registro
 *   (no se escribe ningún plan y NO se borran los planes anteriores).
 * @param activatePlan true = estado «plan activo»; false = estado «solo registro».
 * @param trackingOnly elección durable de solo registro persistida en ajustes.
 * @param settingsGoalMirror espejo de metas del plan en ajustes (null = no tocar).
 * @param pendingDraftId borrador pendiente (pauta profesional a medias) que se
 *   consume en esta misma transacción.
 */
data class NutritionPlanCommitRequest(
    val commitId: String,
    val plan: NutritionPlan?,
    val activatePlan: Boolean,
    val trackingOnly: Boolean,
    val derivedBodyGoals: List<BodyGoal> = emptyList(),
    val settingsGoalMirror: SettingsGoalMirror? = null,
    val pendingDraftId: String? = null,
    /** Fija el objetivo de HOY como snapshot histórico (INSERT IGNORE). */
    val captureTodaySnapshot: Boolean = true,
)

/** Espejo de las metas del plan en `Settings` (0 legítimo se conserva). */
data class SettingsGoalMirror(
    val calorieGoal: Int?,
    val proteinGoal: Int?,
    val carbGoal: Int?,
    val fatGoal: Int?,
)

data class NutritionPlanCommitResult(
    val commitId: String,
    val planId: String?,
    val activated: Boolean,
    val trackingOnly: Boolean,
    val bodyGoalIds: List<String>,
)

/**
 * Operación transaccional coordinada del editor nutricional directo: plan +
 * activación (o modo solo registro) + espejo de metas + metas derivadas de
 * cuerpo + snapshot histórico + consumo del borrador pendiente, todo en UNA
 * transacción y con recibo de idempotencia (reutiliza la tabla de recibos de
 * commit, `setup_commit_receipts`).
 *
 * Nunca hace `addNutritionPlan` + `activatePlan` como dos efectos sueltos y
 * nunca reescribe un [com.example.kpkn.data.models.DailyGoalSnapshot]: los
 * snapshots son insert-once (INSERT IGNORE) y los días pasados no se tocan.
 */
class NutritionPlanCommitCoordinator(
    private val db: KpknDatabase,
    private val nutritionRepository: NutritionRepository? = null,
    private val programRepository: ProgramRepository? = null,
) {
    private val mutex = Mutex()

    suspend fun commit(request: NutritionPlanCommitRequest): NutritionPlanCommitResult = mutex.withLock {
        PersistenceWriteCoordinator.mutex.withLock {
            require(request.commitId.isNotBlank()) { "Falta el identificador de idempotencia del guardado" }
            programRepository?.isReady?.first { it }
            withContext(Dispatchers.IO) {
                var committedSettings: Settings? = null
                val result: NutritionPlanCommitResult = db.withTransaction {
                    val prior = db.setupCommitReceiptDao().get(request.commitId)
                    if (prior != null) {
                        // Replay idempotente: la operación ya se aplicó entera.
                        request.pendingDraftId?.let { db.setupDraftDao().deleteDraft(it) }
                        val activeId = db.nutritionDao().getActiveState()?.activePlanId
                        committedSettings = db.settingsDao().get()?.toSettings()
                        return@withTransaction NutritionPlanCommitResult(
                            commitId = prior.commitId,
                            planId = prior.nutritionPlanId,
                            activated = prior.nutritionPlanId != null && activeId == prior.nutritionPlanId,
                            trackingOnly = committedSettings?.nutritionTrackingOnly == true,
                            bodyGoalIds = commitReceiptJson.decodeFromString(prior.bodyGoalIdsJson),
                        )
                    }

                    val currentSettings = db.settingsDao().get()?.toSettings() ?: Settings()
                    val withMode = currentSettings.copy(nutritionTrackingOnly = request.trackingOnly)
                    val updatedSettings = request.settingsGoalMirror?.let { mirror ->
                        withMode.copy(
                            dailyCalorieGoal = mirror.calorieGoal,
                            dailyProteinGoal = mirror.proteinGoal,
                            dailyCarbGoal = mirror.carbGoal,
                            dailyFatGoal = mirror.fatGoal,
                        )
                    } ?: withMode
                    db.settingsDao().upsert(updatedSettings.toEntity())

                    if (request.plan != null) {
                        if (request.activatePlan) {
                            db.nutritionDao().deactivateAllPlans()
                            db.nutritionDao().upsertPlan(request.plan.copy(isActive = true).toEntity())
                            db.nutritionDao().upsertActiveState(NutritionActiveStateEntity(activePlanId = request.plan.id))
                        } else {
                            db.nutritionDao().upsertPlan(request.plan.copy(isActive = false).toEntity())
                            if (db.nutritionDao().getActiveState()?.activePlanId == request.plan.id) {
                                db.nutritionDao().clearActiveState()
                            }
                        }
                        // Metas derivadas de cuerpo: filas derivadas del plan,
                        // nunca observaciones ni metas manuales/profesionales.
                        db.bodyProgressDao().deleteDerivedSetupGoals(request.plan.id)
                        request.derivedBodyGoals.forEach { db.bodyProgressDao().upsertGoal(it.toEntity()) }
                    } else {
                        // Solo registro: no se borra NINGÚN plan anterior; solo
                        // se desactiva el estado (el historial se conserva).
                        db.nutritionDao().deactivateAllPlans()
                        db.nutritionDao().clearActiveState()
                    }

                    if (request.activatePlan && request.plan != null && request.captureTodaySnapshot) {
                        // Evidencia histórica del día: insert-once. Editar el
                        // plan jamás reescribe un snapshot existente.
                        val today = LocalDate.now()
                        if (db.nutritionDao().getDailyGoalSnapshot(today.toString()) == null) {
                            val target = planDayTargetOf(request.plan, NutritionGoalSource.PLAN_FORECAST)
                            db.nutritionDao()
                                .insertDailyGoalSnapshot(dailyGoalSnapshotOf(target, today, System.currentTimeMillis()).toEntity())
                        }
                    }

                    request.pendingDraftId?.let { db.setupDraftDao().deleteDraft(it) }

                    val bodyGoalIds = request.derivedBodyGoals.map { it.id }
                    val next = NutritionPlanCommitResult(
                        commitId = request.commitId,
                        planId = request.plan?.id,
                        activated = request.plan != null && request.activatePlan,
                        trackingOnly = request.trackingOnly,
                        bodyGoalIds = bodyGoalIds,
                    )
                    db.setupCommitReceiptDao().insert(
                        SetupCommitReceiptEntity(
                            commitId = next.commitId,
                            draftId = request.pendingDraftId,
                            programId = null,
                            nutritionPlanId = next.planId,
                            bodyGoalIdsJson = commitReceiptJson.encodeToString(bodyGoalIds),
                            committedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    committedSettings = updatedSettings
                    next
                }
                // Publicación fuera de la transacción: mismas cachés que el resto.
                committedSettings?.let { settings ->
                    programRepository?.publishSetupCommit(settings, null, false)
                }
                nutritionRepository?.publishNutritionPlanCommit()
                result
            }
        }
    }
}

/**
 * Metas derivadas del plan para la función de cuerpo, con el mismo contrato que
 * las filas derivadas del setup: id estable `plan:<planId>:<métrica>`, origen
 * del plan y solo cuando hay meta explícita (0 no es meta corporal).
 */
fun derivedBodyGoalsFor(plan: NutritionPlan, nowEpochMs: Long = System.currentTimeMillis()): List<BodyGoal> {
    val typed = plan.typedBodyGoal ?: return emptyList()
    val target = typed.targetValueSi?.takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
    val metric = when (typed.metric) {
        GoalMetric.WEIGHT -> BodyMetric.WEIGHT
        GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT
        GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT
    }
    return listOf(
        BodyGoal(
            id = "plan:${plan.id}:${typed.metric.name}",
            metric = metric,
            targetValueSi = target,
            unitSi = typed.unitSi,
            origin = typed.origin.takeIf { it == CalculationOrigin.PROFESSIONAL } ?: CalculationOrigin.PLAN,
            linkedPlanId = plan.id,
            createdAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs,
        ),
    )
}
