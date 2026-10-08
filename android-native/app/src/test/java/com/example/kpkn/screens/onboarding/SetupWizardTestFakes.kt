package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftScope

/*
 * Fakes compartidos de las pruebas del ViewModel del asistente: persistencia en memoria con el MISMO guard de revisión que
 * Room (`SetupDraftRepository.save`) y entorno hermético (sin singletons, sin dispatch, sin assets).
 */

/** Persistence fake replicando el guard de Room (SetupPersistence @122-134). */
internal class FakeSetupWizardPersistence : SetupWizardPersistence {
    data class Row(val payloadJson: String, val revision: Long, val catalogRevision: String?) {
        fun toDraft(draftId: String) = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
    }

    val rows = LinkedHashMap<String, Row>()
    val saveLog = mutableListOf<SetupDraft>()
    var failNextSaves = 0

    /** Las escrituras que el guard rechazó (revisión antigua o conflicto), con su causa: nunca debería haber ninguna. */
    val rejectedSaves = mutableListOf<String>()

    override suspend fun load(draftId: String): SetupDraft? = rows[draftId]?.toDraft(draftId)

    override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft {
        if (failNextSaves > 0) {
            failNextSaves--
            throw java.io.IOException("Fallo de guardado inyectado")
        }
        val current = rows[draftId]
        val model = SetupDraft(draftId, payloadJson, revision, catalogRevision, 0L)
        return when {
            current == null -> {
                rows[draftId] = Row(payloadJson, revision, catalogRevision)
                saveLog += model
                model
            }
            revision < current.revision -> {
                rejectedSaves += "antigua: $revision < ${current.revision}"
                throw IllegalArgumentException("La revisión del borrador es antigua")
            }
            revision == current.revision && payloadJson != current.payloadJson -> {
                rejectedSaves += "conflicto: $revision"
                throw IllegalArgumentException("Conflicto de revisión del borrador")
            }
            revision == current.revision -> current.toDraft(draftId)
            else -> {
                rows[draftId] = Row(payloadJson, revision, catalogRevision)
                saveLog += model
                model
            }
        }
    }

    override suspend fun discard(draftId: String) {
        rows.remove(draftId)
    }

    override suspend fun listRecoverable(): List<SetupDraftCandidate> =
        rows.map { (id, row) -> SetupDraftCandidate(id, SetupDraftScope.FULL, row.revision, 0L, row.catalogRevision) }
}

/** Environment hermético: sin singletons, sin dispatch, sin assets. */
internal class FakeSetupWizardEnvironment(
    override val settings: Settings = Settings(),
) : SetupWizardEnvironment {
    var bodyProgressRefreshes = 0
        private set

    override suspend fun awaitReady() = Unit
    override fun activeProgramId(): String? = null
    override fun activeNutritionPlanId(): String? = null
    override fun activeNutritionPlan(): NutritionPlan? = null
    override fun nutritionPlan(id: String): NutritionPlan? = null
    override fun hasInitialRecoveryEvidence(): Boolean = false
    override suspend fun refreshBodyProgress() {
        bodyProgressRefreshes++
    }
}
