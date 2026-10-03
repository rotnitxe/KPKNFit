package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.RecipeSessionKind
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.training.PersonalizationReport
import com.example.kpkn.domain.training.ProgramExecutionContract
import com.example.kpkn.domain.training.SessionDurationBreakdown
import com.example.kpkn.domain.training.SessionDurationEstimator
import kotlinx.coroutines.CancellationException

/**
 * T-005 / §15.2 — evaluación ÚNICA de candidatos.
 *
 * `SetupTrainingPlanner` conserva solo ranking/prefiltros baratos; este
 * módulo evalúa UN candidato con el orden cerrado del plan:
 *
 *  1. catálogo publicado / receta completa
 *  2. perfil y nivel
 *  3. frecuencia / split
 *  4. adaptación / material
 *  5. carga representable
 *  6. composición
 *  7. materialización
 *  8. contrato ejecutable
 *  9. duración de cada sesión
 * 10. coherencia de modalidades / dosis
 *
 * El primer rechazo conserva todos los detalles útiles de SU etapa; nunca hay
 * `catch (Exception) { false }`: `CancellationException` se PROPAGA y un error
 * inesperado se convierte en [PlanRejectionReason.INTERNAL_MATERIALIZATION]
 * con etapa e id, sin datos personales ni el borrador entero.
 *
 * El evaluador NUNCA crea programas ni modifica programa activo/settings al
 * explorar: solo prepara (el guardado del borrador recuperable sigue siendo
 * trabajo del ViewModel).
 */

// ─── Etapas y motivos cerrados (§15.2) ──────────────────────────────────────

enum class PlanEvaluationStage {
    CATALOG,
    PROFILE,
    FREQUENCY_SPLIT,
    MATERIAL,
    REPRESENTABLE_LOAD,
    COMPOSITION,
    MATERIALIZATION,
    EXECUTABLE_CONTRACT,
    SESSION_DURATION,
    MODALITY_DOSE,
}

enum class PlanRejectionReason {
    CATALOG_NOT_READY,
    RECIPE_UNAVAILABLE,
    PROFILE_MISMATCH,
    LEVEL_UNSUITABLE,
    FREQUENCY,
    SPLIT,
    APPARATUS_UNKNOWN,
    APPARATUS_ABSENT,
    UNRESOLVED_CONFIGURATION,
    NO_VALID_SUBSTITUTION,
    LOAD_BASIS_UNREPRESENTABLE,
    COMPOSITION,
    TIME_BUDGET,
    INTERNAL_MATERIALIZATION,
}

/** Perfil de objetivo que la regla de compatibilidad evalúa. */
enum class PlanGoalProfile { STRENGTH, MUSCLE, STRENGTH_MUSCLE, COMPLETE_ATHLETE, LEGACY_MIXED, LEGACY_HEALTH }

// ─── Request / resultados ───────────────────────────────────────────────────

/**
 * Entradas NORMALIZADAS de un candidato (§15.2): nada de UI ni de borrador.
 * [inputKey] es la huella canónica completa (colecciones ordenadas, null ≠
 * vacío) que también usa la caché de sesión y el gate de activación.
 */
data class PlanCandidateRequest(
    val inputKey: String,
    val goalProfile: PlanGoalProfile,
    val level: CatalogLevel,
    val focus: TrainingFocus,
    /** Reference para los tres primeros perfiles; null en Atleta/legacy. */
    val reference: TrainingReference?,
    val daysPerWeek: Int,
    val weekdays: Set<Int>,
    val minutesPerSession: Int,
    val effectiveEquipment: Set<String>,
    val cardioMinutes: Int? = null,
    val requiresCardio: Boolean = false,
    val selectedSplitId: String? = null,
    val planCatalogRevision: String,
    val exerciseCatalogRevision: String?,
)

/** Snapshot inmutable del catálogo con el que se evalúa (una sola carga). */
data class PlanCatalogSnapshot(
    val entries: List<CatalogEntry>,
    val planRevision: String,
    /** null = el catálogo de ejercicios todavía no está decodificado. */
    val exerciseCatalogRevision: String?,
)

data class PlanDurationBreakdown(
    /** Breakdown compartido completo, uno por sesión materializada con contenido. */
    val sessionBreakdowns: List<SessionDurationBreakdown>,
) {
    val sessionMinutes: List<Int> get() = sessionBreakdowns.map { it.totalMinutes }

    val maxSessionMinutes: Int
        get() = sessionBreakdowns.maxOfOrNull { it.maxSessionMinutes } ?: 0

    fun fitsInto(minutes: Int): Boolean = maxSessionMinutes <= minutes
}

/**
 * Componentes semanales realmente presentes. Atleta completo exige los cuatro
 * (§15.1): `strength + hypertrophy + power + cardio`, NO `schedulesCardio`.
 */
data class PlanCoverage(
    val frequency: Int,
    val hasStrength: Boolean,
    val hasHypertrophy: Boolean,
    val hasPower: Boolean,
    val hasCardio: Boolean,
) {
    val completeAthlete: Boolean
        get() = hasStrength && hasHypertrophy && hasPower && hasCardio

    fun missingCompleteAthleteCapabilities(): List<String> = buildList {
        if (!hasStrength) add("strength")
        if (!hasHypertrophy) add("hypertrophy")
        if (!hasPower) add("power")
        if (!hasCardio) add("cardio")
    }
}

sealed interface PlanCandidateEvaluation {
    /** Carga del catálogo pendiente: la UI muestra computing y reintenta. */
    data object CatalogLoading : PlanCandidateEvaluation

    /**
     * Candidato listo: incluye el programa PREPARADO (no solo su id), la
     * receta, procedencia, duración, la huella de entradas, cargas de
     * entrenamiento pendientes (permitidas con RIR) y la cobertura.
     */
    data class Ready(
        val planId: String,
        val preparedPlan: Program,
        val recipeSnapshot: TrainingPlanRecipe?,
        val provenance: PlanProvenance?,
        val durationBreakdown: PlanDurationBreakdown,
        val inputKey: String,
        val unresolvedWorkoutLoads: List<String>,
        val coverage: PlanCoverage,
        val report: PersonalizationReport? = null,
    ) : PlanCandidateEvaluation

    data class Rejected(
        val planId: String,
        val stage: PlanEvaluationStage,
        val reasonCode: PlanRejectionReason,
        val affectedSlots: List<String> = emptyList(),
        val missingCapabilities: List<String> = emptyList(),
        val requiredMinutes: Int? = null,
        val details: String? = null,
        /**
         * Paquete A · B1: requisitos de material (tokens del vocabulario: `rack`, `bench`, `barbell`…) que
         * causaron un rechazo `APPARATUS_ABSENT` o `APPARATUS_UNKNOWN`. Vacío cuando el motivo es de otra
         * clase o cuando el motor no los informó; así la UI no tiene que leer el texto del mensaje.
         */
        val missingRequirements: List<String> = emptyList(),
    ) : PlanCandidateEvaluation
}

/**
 * Fallo TIPADO de la etapa de materialización. Sigue siendo
 * `IllegalStateException` para que los callers existentes que capturan ese
 * tipo no cambien de comportamiento; aporta etapa y motivo cerrados.
 */
class PlanMaterializationException(
    val stage: PlanEvaluationStage,
    val reason: PlanRejectionReason,
    message: String,
    val affectedSlots: List<String> = emptyList(),
    val requiredMinutes: Int? = null,
    /** Paquete A · B1: tokens de material que faltan (ver [PlanCandidateEvaluation.Rejected.missingRequirements]). */
    val missingRequirements: List<String> = emptyList(),
) : IllegalStateException(message)

/** Puerto de materialización: en producción el motor real del wizard. */
fun interface PlanMaterializationPort {
    suspend fun materialize(entry: CatalogEntry, request: PlanCandidateRequest): PlanMaterializationOutcome
}

data class PlanMaterializationOutcome(
    val program: Program,
    val recipe: TrainingPlanRecipe? = null,
    val report: PersonalizationReport? = null,
)

// ─── Evaluador ──────────────────────────────────────────────────────────────

object PlanCandidateEvaluator {

    /**
     * Evalúa UN candidato. El orden de las etapas es el de §15.2; el primer
     * rechazo se devuelve con SU etapa y todos sus detalles.
     */
    suspend fun evaluate(
        request: PlanCandidateRequest,
        snapshot: PlanCatalogSnapshot,
        entryId: String,
        engine: PlanMaterializationPort,
    ): PlanCandidateEvaluation {
        // A snapshot from another catalog generation cannot prove anything
        // about this request. Treat it like a not-yet-ready catalog so callers
        // retry rather than publishing a result against mixed revisions.
        if (snapshot.exerciseCatalogRevision == null ||
            request.planCatalogRevision != snapshot.planRevision ||
            request.exerciseCatalogRevision != snapshot.exerciseCatalogRevision
        ) return PlanCandidateEvaluation.CatalogLoading
        val entry = snapshot.entries.firstOrNull { it.id == entryId }
            ?: return PlanCandidateEvaluation.Rejected(entryId, PlanEvaluationStage.CATALOG, PlanRejectionReason.CATALOG_NOT_READY,
                details = "el id $entryId no está en el snapshot del catálogo")
        return evaluateEntry(request, entry, engine)
    }

    /** Evalúa una entrada ya resuelta del snapshot. */
    suspend fun evaluateEntry(
        request: PlanCandidateRequest,
        entry: CatalogEntry,
        engine: PlanMaterializationPort,
    ): PlanCandidateEvaluation {
        val planId = entry.id

        // 1 ─ Catálogo publicado / receta completa.
        if (entry.publication != PublicationState.PUBLISHED) {
            return PlanCandidateEvaluation.Rejected(planId, PlanEvaluationStage.CATALOG, PlanRejectionReason.RECIPE_UNAVAILABLE,
                details = "publicación=${entry.publication}")
        }
        val recipe = entry.recipe ?: entry.template?.recipe
        val generatesAtRuntime = entry.source == CatalogSource.NATIVE
        if (!generatesAtRuntime && recipe == null && entry.template == null) {
            return PlanCandidateEvaluation.Rejected(planId, PlanEvaluationStage.CATALOG, PlanRejectionReason.RECIPE_UNAVAILABLE,
                details = "la entrada publicada no trae receta ni plantilla")
        }

        // 2 ─ Perfil y nivel.
        val coverage = coverageOf(entry, recipe)
        if (request.goalProfile == PlanGoalProfile.COMPLETE_ATHLETE && !coverage.completeAthlete) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.PROFILE, PlanRejectionReason.PROFILE_MISMATCH,
                missingCapabilities = coverage.missingCompleteAthleteCapabilities(),
                details = "Atleta completo exige fuerza, hipertrofia, potencia y cardio en la misma receta",
            )
        }
        if (request.focus !in entry.supportedFocuses && entry.source != CatalogSource.PROTOCOL) {
            return PlanCandidateEvaluation.Rejected(planId, PlanEvaluationStage.PROFILE, PlanRejectionReason.PROFILE_MISMATCH,
                details = "enfoque ${request.focus} no soportado por la entrada")
        }
        if (request.reference != null && entry.references.isNotEmpty() &&
            request.reference !in entry.references && entry.source != CatalogSource.PROTOCOL
        ) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.PROFILE, PlanRejectionReason.PROFILE_MISMATCH,
                details = "referencia ${request.reference} ≠ ${entry.references}",
            )
        }

        // 3 ─ Frecuencia / split.
        if (!entry.supportedFrequencies.contains(request.daysPerWeek)) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.FREQUENCY_SPLIT, PlanRejectionReason.FREQUENCY,
                details = "frecuencia soportada ${entry.supportedFrequencies}, pedida ${request.daysPerWeek}",
            )
        }
        // Atleta completo materializa cardio por defecto cuando el usuario no
        // elige minutos explícitos; MIXED/legacy sí exigen preferencias (§15.2).
        if (request.requiresCardio && request.goalProfile != PlanGoalProfile.COMPLETE_ATHLETE) {
            if (request.cardioMinutes == null) {
                return PlanCandidateEvaluation.Rejected(
                    planId, PlanEvaluationStage.MODALITY_DOSE, PlanRejectionReason.APPARATUS_UNKNOWN,
                    details = "faltan las preferencias de cardio pedidas por el perfil",
                )
            }
        }

        // 4–8 ─ Adaptación/material, carga, composición, materialización y
        // contrato: el motor real decide y devuelve un fallo TIPADO (etapa +
        // motivo). La cancelación se PROPAGA; cualquier otro error inesperado
        // se documenta como INTERNAL_MATERIALIZATION con etapa e id.
        val outcome = try {
            engine.materialize(entry, request)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (typed: PlanMaterializationException) {
            return PlanCandidateEvaluation.Rejected(
                planId, typed.stage, typed.reason,
                affectedSlots = typed.affectedSlots,
                requiredMinutes = typed.requiredMinutes,
                details = typed.message,
                missingRequirements = typed.missingRequirements,
            )
        } catch (error: Throwable) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.MATERIALIZATION, PlanRejectionReason.INTERNAL_MATERIALIZATION,
                details = "${error::class.java.name}: ${error.message?.take(200).orEmpty()} @ $planId",
            )
        }

        val program = outcome.program
        val executableError = runCatching { ProgramExecutionContract.requireExecutable(program) }.exceptionOrNull()
        if (executableError is CancellationException) throw executableError
        if (executableError != null) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.EXECUTABLE_CONTRACT,
                if (executableError is PlanMaterializationException) executableError.reason else PlanRejectionReason.COMPOSITION,
                details = executableError.message,
            )
        }

        // 9 ─ Duración de cada sesión.
        val breakdown = PlanDurationBreakdown(sessionBreakdownsOf(program))
        if (!breakdown.fitsInto(request.minutesPerSession)) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.SESSION_DURATION, PlanRejectionReason.TIME_BUDGET,
                requiredMinutes = breakdown.maxSessionMinutes,
                details = "la sesión más larga estima ${breakdown.maxSessionMinutes} min y el presupuesto es ${request.minutesPerSession} min",
            )
        }

        // 10 ─ Coherencia de modalidades/dosis: el componente pedido existe en
        // la semana (para cardio/potencia) y la cobertura se publica en Ready.
        if (request.requiresCardio && !coverage.hasCardio) {
            return PlanCandidateEvaluation.Rejected(
                planId, PlanEvaluationStage.MODALITY_DOSE, PlanRejectionReason.PROFILE_MISMATCH,
                missingCapabilities = listOf("cardio"),
                details = "el perfil pedía cardio y la semana no lo programa",
            )
        }

        return PlanCandidateEvaluation.Ready(
            planId = planId,
            preparedPlan = program,
            // Receta EFECTIVA del motor cuando la aporta (plan de autor adaptado: la
            // derivada del resolver y su procedencia con slotChanges); si no, la
            // del catálogo. Preview, activación y reapertura leen la misma.
            recipeSnapshot = outcome.recipe ?: recipe,
            provenance = outcome.recipe?.provenance ?: entry.provenance,
            durationBreakdown = breakdown,
            inputKey = request.inputKey,
            unresolvedWorkoutLoads = unresolvedLoadsOf(program),
            coverage = coverage.copy(frequency = programSessionDays(program).size.ifZero(request.daysPerWeek)),
            report = outcome.report,
        )
    }

    // ─── Cobertura (§15.1): evidencia REAL de la receta, no etiquetas ───────

    /**
     * Componentes semanales de la entrada a partir de SU receta (o de la
     * plantilla): `strength + hypertrophy + power + cardio`. Un plan solo se
     * declara Atleta completo cuando la prescripción lo demuestra; la palabra
     * «Power» en un título no acredita potencia (P-105) y `schedulesCardio`
     * por sí solo tampoco.
     */
    fun coverageOf(entry: CatalogEntry, recipe: TrainingPlanRecipe?): PlanCoverage {
        val effective = recipe ?: entry.template?.recipe
        val days = effective?.weeks?.firstOrNull()?.days.orEmpty()
        val intents = days.flatMap { day -> day.slots.mapNotNull { it.intent } }
        val roles = days.flatMap { day -> day.slots.mapNotNull { it.role } }
        val hasCardio = days.any { day -> day.cardioBlocks.isNotEmpty() || day.sessionKind != RecipeSessionKind.STRENGTH } ||
            (effective == null && entry.schedulesCardio) || TrainingCapability.CARDIO in entry.capabilities
        val hasPower = SlotIntent.P in intents || SlotRole.SPEED in roles ||
            TrainingCapability.POWER in entry.capabilities
        val hasStrength = SlotIntent.F in intents || SlotIntent.FV in intents ||
            SlotRole.T1_MAIN in roles ||
            (effective?.liftSlots?.isNotEmpty() == true) ||
            TrainingCapability.STRENGTH in entry.capabilities
        val hasHypertrophy = SlotIntent.H in intents || SlotIntent.I in intents ||
            SlotRole.T2_SUPPLEMENTAL in roles || SlotRole.T3_ACCESSORY in roles ||
            TrainingReference.HYPERTROPHY in entry.references ||
            TrainingCapability.HYPERTROPHY in entry.capabilities
        return PlanCoverage(
            frequency = entry.supportedFrequencies.first,
            hasStrength = hasStrength,
            hasHypertrophy = hasHypertrophy,
            hasPower = hasPower,
            hasCardio = hasCardio,
        )
    }

    /** Per-session details from the shared §12.2 estimator, including structured parts. */
    fun sessionBreakdownsOf(program: Program): List<SessionDurationBreakdown> = program.macrocycles
        .flatMap { it.blocks }.flatMap { it.mesocycleWeeks() }.flatMap { it.sessions }
        .filter { session ->
            session.allExercises().isNotEmpty() || session.warmup.isNotEmpty() ||
                session.parts.any { part ->
                    part.targetDurationMinutes?.let { it > 0 } == true ||
                        part.mobilitySeries.isNotEmpty() ||
                        (part.mobilityConfig?.totalMinutes ?: 0) > 0
                }
        }
        .map(SessionDurationEstimator::estimate)

    /** Compatibility projection for callers that only need the rounded minutes. */
    fun sessionMinutesOf(program: Program): List<Int> =
        sessionBreakdownsOf(program).map { it.totalMinutes }

    /** Cargas de ENTRENAMIENTO sin resolver: ejercicios LOAD sin peso aún. */
    fun unresolvedLoadsOf(program: Program): List<String> = program.macrocycles
        .flatMap { it.blocks }.flatMap { it.mesocycleWeeks() }.flatMap { it.sessions }
        .flatMap { it.exercises }
        .filter { exercise ->
            exercise.sets.any { set -> set.loadModeV2 == LoadModeV2.LOAD && set.weight == null }
        }
        .map { it.name }
        .distinct()
        .sorted()

    fun programSessionDays(program: Program): Set<Int> = program.macrocycles
        .flatMap { it.blocks }.flatMap { it.mesocycleWeeks() }.flatMap { it.sessions }
        .mapNotNull { it.dayOfWeek }
        .toSet()

    private fun Int.ifZero(fallback: Int): Int = if (this == 0) fallback else this
}

/** Auxiliar local: semanas de un bloque con la forma real del modelo. */
private fun com.example.kpkn.data.models.Block.mesocycleWeeks(): List<com.example.kpkn.data.models.ProgramWeek> =
    mesocycles.flatMap { it.weeks }
