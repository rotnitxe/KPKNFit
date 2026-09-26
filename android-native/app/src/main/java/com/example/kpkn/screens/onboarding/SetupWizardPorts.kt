package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.onboarding.PersistenceFactory
import com.example.kpkn.data.onboarding.SetupCommitRequest
import com.example.kpkn.data.onboarding.SetupCommitResult
import com.example.kpkn.data.onboarding.SetupDraft
import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.persistenceFactory
import com.example.kpkn.data.repository.BodyProgressRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import kotlinx.coroutines.flow.first

/**
 * Persistence boundary consumed by [SetupWizardViewModel]. It replaces direct
 * access to `PersistenceFactory`/`SetupDraftRepository` so the orchestration
 * logic can run against an in-memory fake without Room, while production keeps
 * the real adapters. Implementations own their dispatch (the real adapters
 * inherit Room's `Dispatchers.IO`); the ViewModel never blocks on them.
 */
interface SetupWizardPersistence {
    suspend fun load(draftId: String): SetupDraft?
    suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft
    suspend fun discard(draftId: String)
    suspend fun listRecoverable(): List<SetupDraftCandidate>
}

/** Real adapter over [PersistenceFactory]; the factory is built lazily. */
class RealSetupWizardPersistence(
    factory: PersistenceFactory,
) : SetupWizardPersistence {
    private val drafts = factory.drafts
    private val resolver = SetupDraftResolver(factory.database)

    override suspend fun load(draftId: String): SetupDraft? = drafts.load(draftId)

    override suspend fun save(draftId: String, payloadJson: String, revision: Long, catalogRevision: String?): SetupDraft =
        drafts.save(draftId, payloadJson, revision, catalogRevision)

    override suspend fun discard(draftId: String) = drafts.discard(draftId)

    override suspend fun listRecoverable(): List<SetupDraftCandidate> = resolver.listRecoverable()
}

/**
 * Commit boundary. It exists so the orchestration tests can drive the REAL
 * commit flow (review gate, payload assembly, receipt) through a fake instead
 * of re-implementing the state machine; production wires the untouched
 * [com.example.kpkn.data.onboarding.SetupCommitCoordinator], which owns the
 * transaction and the idempotency receipt.
 */
interface SetupWizardCommits {
    suspend fun commit(request: SetupCommitRequest): SetupCommitResult
}

/** Real adapter over the coordinator held by [PersistenceFactory]. */
class RealSetupWizardCommits(
    factory: PersistenceFactory,
) : SetupWizardCommits {
    private val coordinator = factory.commits

    override suspend fun commit(request: SetupCommitRequest): SetupCommitResult = coordinator.commit(request)
}

/**
 * Puerto de materialización de previews/candidatos: el ViewModel usa su motor
 * real por defecto y las tests pueden inyectar el suyo para controlar orden y
 * tiempo (carrera cachedA → intermediaB → vueltaA, respuesta tardía de B) sin
 * fabricar previews ni tocar la cola real.
 */
fun interface SetupWizardMaterializer {
    suspend fun materialize(draft: SetupWizardDraft): SetupPreview
}

/**
 * Read-only environment boundary: everything the wizard needs from
 * [ProgramRepository]/[NutritionRepository]. Keeping it behind an interface lets
 * the orchestration tests run without touching the production singletons.
 */
interface SetupWizardEnvironment {
    suspend fun awaitReady()
    val settings: Settings
    fun activeProgramId(): String?
    fun activeNutritionPlanId(): String?
    fun activeNutritionPlan(): NutritionPlan?
    fun nutritionPlan(id: String): NutritionPlan?
    fun hasInitialRecoveryEvidence(): Boolean

    /**
     * Re-reads Body Progress after a successful commit so the newly written
     * observations show up without restarting the app. Implementations must
     * never throw: a refresh failure must not turn a real commit into a lie.
     */
    suspend fun refreshBodyProgress()
}

/** Real adapter over the [ProgramRepository]/[NutritionRepository] singletons. */
class RealSetupWizardEnvironment(
    private val context: android.content.Context,
) : SetupWizardEnvironment {
    override suspend fun awaitReady() {
        ProgramRepository.getInstance().isReady.first { it }
    }

    override val settings: Settings
        get() = ProgramRepository.getInstance().settings.value

    override fun activeProgramId(): String? =
        ProgramRepository.getInstance().activeProgramState.value?.programId

    override fun activeNutritionPlanId(): String? =
        NutritionRepository.getInstance().activeNutritionPlanId.value

    override fun activeNutritionPlan(): NutritionPlan? {
        val id = NutritionRepository.getInstance().activeNutritionPlanId.value ?: return null
        return NutritionRepository.getInstance().nutritionPlans.value.firstOrNull { it.id == id }
    }

    override fun nutritionPlan(id: String): NutritionPlan? =
        NutritionRepository.getInstance().nutritionPlans.value.firstOrNull { it.id == id }

    override fun hasInitialRecoveryEvidence(): Boolean =
        ProgramRepository.getInstance().settings.value.initialRecoveryEvidence != null

    override suspend fun refreshBodyProgress() {
        try {
            BodyProgressRepository.getInstance(context).refreshFromStorage()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Un fallo de refresco nunca convierte un commit real en un error:
            // los datos ya están escritos; la lectura se repetirá en la próxima
            // entrada a Body Progress.
        }
    }
}

/** Production wiring helper kept close to the ports it implements. */
fun realSetupWizardPersistence(context: android.content.Context): SetupWizardPersistence =
    RealSetupWizardPersistence(persistenceFactory(context))

/** Production commit adapter; same factory, same transaction as persistence. */
fun realSetupWizardCommits(context: android.content.Context): SetupWizardCommits =
    RealSetupWizardCommits(persistenceFactory(context))