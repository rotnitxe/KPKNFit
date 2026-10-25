package com.example.kpkn.data.repository

import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionCheckInEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.NutritionPlanCalculationSnapshot
import com.example.kpkn.domain.nutrition.checkin.*
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

@Serializable
private data class StoredCheckin(val proposal: CheckinProposal? = null, val postponedUntil: String? = null)

/** Room conserva la decisión por semana; el recibo del coordinador resuelve reintentos tras una interrupción. */
class NutritionCheckinRepository(
    private val db: KpknDatabase,
    private val coordinator: NutritionPlanCommitCoordinator,
    private val today: () -> LocalDate = { LocalDate.now() },
) : CheckinStore {
    override val records: Flow<List<CheckinRecord>> = db.nutritionDao().observeCheckIns().map { rows ->
        rows.mapNotNull { row -> runCatching {
            CheckinRecord(LocalDate.parse(row.weekKey), CheckinDecision.valueOf(row.status),
                stored(row)?.postponedUntil?.let(LocalDate::parse), row.appliedPlanId)
        }.getOrNull() }
    }

    override suspend fun apply(plan: CheckinPlan) = decisionMutex.withLock {
        withContext(Dispatchers.IO) {
            val key = plan.weekKey.toString()
            val prior = db.nutritionDao().getCheckIn(key)
            if (prior?.status == CheckinDecision.APPLIED.name) return@withContext
            check(prior?.status != CheckinDecision.DECLINED.name) { "Esta semana ya decidiste conservar el objetivo." }
            val proposal = requireNotNull(plan.proposal) { "Aún no hay una propuesta disponible." }
            val receipt = db.setupCommitReceiptDao().get("checkin-$key")
            if (receipt == null) {
                check(plan.today == today()) { "Cambió el día. Revisa de nuevo la propuesta." }
                check(proposal.planId == plan.basePlan.id && proposal.beforeKcal == plan.basePlan.calorieTarget)
                write(key, CheckinDecision.PENDING, StoredCheckin(proposal))
            }
            val snapshot = plan.basePlan.calculationSnapshot
            // Confirmar sin cambios no reinicia el periodo de incertidumbre por cambio de objetivo.
            val calculationResult = snapshot?.result.orEmpty() + mapOf(
                "calorieTargetKcal" to proposal.afterKcal.toString(), "expenditureKcal" to proposal.expenditureKcal.toString(),
            ) + if (proposal.afterKcal != proposal.beforeKcal) mapOf("effectiveFrom" to proposal.effectiveFrom) else emptyMap()
            val updatedPlan = plan.basePlan.copy(
                calorieTarget = proposal.afterKcal,
                calculationOrigin = CalculationOrigin.CALIBRATED,
                engineVersion = proposal.modelVersion,
                calculationSnapshot = NutritionPlanCalculationSnapshot(
                    proposal.modelVersion, "Balance energético", snapshot?.inputs.orEmpty(), proposal.state,
                    calculationResult,
                    snapshot?.assumptions.orEmpty(), snapshot?.manualModifications.orEmpty(), Instant.now().toString(),
                ),
            )
            val result = coordinator.commit(NutritionPlanCommitRequest(
                commitId = "checkin-$key", plan = updatedPlan, activatePlan = true, trackingOnly = false,
                derivedBodyGoals = derivedBodyGoalsFor(updatedPlan),
                settingsGoalMirror = SettingsGoalMirror(updatedPlan.calorieTarget, updatedPlan.proteinGoal, updatedPlan.carbGoal, updatedPlan.fatGoal),
                preserveTodayFrom = plan.basePlan,
            ))
            val evidence = if (receipt != null) stored(prior) ?: StoredCheckin(proposal) else StoredCheckin(proposal)
            write(key, CheckinDecision.APPLIED, evidence, result.planId)
        }
    }

    override suspend fun decline(weekKey: LocalDate, proposal: CheckinProposal?) = decide(weekKey, CheckinDecision.DECLINED, StoredCheckin(proposal))

    override suspend fun postpone(weekKey: LocalDate, today: LocalDate, proposal: CheckinProposal?) =
        decide(weekKey, CheckinDecision.POSTPONED, StoredCheckin(proposal, today.plusDays(1).toString()))

    private suspend fun decide(key: LocalDate, status: CheckinDecision, data: StoredCheckin) = decisionMutex.withLock {
        withContext(Dispatchers.IO) {
            val previous = db.nutritionDao().getCheckIn(key.toString())
            if (previous?.status !in setOf(CheckinDecision.APPLIED.name, CheckinDecision.DECLINED.name)) {
                // Un recibo ya confirmado nunca se convierte en un rechazo por una interrupción de publicación.
                val receipt = db.setupCommitReceiptDao().get("checkin-$key")
                if (receipt != null) write(key.toString(), CheckinDecision.APPLIED, stored(previous) ?: data, receipt.nutritionPlanId)
                else write(key.toString(), status, data)
            }
        }
    }

    private fun stored(row: NutritionCheckInEntity?): StoredCheckin? = row?.proposalJson?.let {
        runCatching { dbJson.decodeFromString<StoredCheckin>(it) }.getOrNull()
    }

    private suspend fun write(key: String, status: CheckinDecision, data: StoredCheckin, planId: String? = null) {
        val now = Instant.now().toString()
        db.nutritionDao().upsertCheckIn(NutritionCheckInEntity(key, status.name,
            if (status == CheckinDecision.PENDING) null else now, dbJson.encodeToString(data), planId, now))
    }

    companion object { private val decisionMutex = Mutex() }
}
