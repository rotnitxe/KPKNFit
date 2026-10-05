package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.ConfigurationAvailability
import com.example.kpkn.domain.training.EffectiveEquipmentResult
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.PlanAdaptationReason
import com.example.kpkn.domain.training.PlanAdaptationRequest
import com.example.kpkn.domain.training.PlanAdaptationResolver
import com.example.kpkn.domain.training.PlanAdaptationResult
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.PlanTargetProfile
import com.example.kpkn.domain.training.ProgramRecipeValidator
import com.example.kpkn.domain.training.RequirementEvidence
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.UuidIdProvider

/**
 * Entradas de la preparación de UN plan de autor (PHUL/PHAT originales o
 * adaptados, §10.1). Todo es dominio puro: el ViewModel solo traduce el
 * borrador a estos datos, de modo que el preview, la activación y las pruebas
 * recorren exactamente la misma función.
 */
data class AuthoredPlanRequest(
    /** Entrada del catálogo con `recipe` y procedencia (`CatalogSource.PROTOCOL` + `authoredSource`). */
    val entry: CatalogEntry,
    /** Programa base (id estable del borrador, día de inicio, autorregulación…). */
    val baseProgram: Program,
    /** Equipo efectivo REAL ([TrainingOptions.resolveEffectiveEquipment]), nunca `general_gym` inventado. */
    val equipment: EffectiveEquipmentResult,
    /** Disponibilidad declarada (§13.1); null = ruta legacy de lectura. */
    val availability: EquipmentAvailability?,
    val catalog: ExerciseCatalogV2,
    val metadata: ExerciseCompositionMetadataProvider,
    val options: TrainingOptions,
    /** Frecuencia pedida; si la receta produce otra se rechaza con motivo tipado. */
    val expectedDaysPerWeek: Int? = null,
    val targetProfile: PlanTargetProfile? = null,
    val idProvider: IdProvider = UuidIdProvider,
)

/**
 * Preparación de un plan de autor para el wizard (§10.1, §13.4, §14.3, §15.2).
 *
 * - **Original** (procedencia ORIGINAL): la receta NO se modifica jamás. Si el
 *   material declarado no cubre alguna de sus configuraciones el plan se
 *   rechaza con `APPARATUS_ABSENT` / `APPARATUS_UNKNOWN` y los tokens que
 *   faltan; nunca se sustituye en secreto para que la validación pase.
 * - **Adaptado** (procedencia ADAPTED): [PlanAdaptationResolver.adapt] sobre el
 *   equipo efectivo REAL. Un `NotViable` se traduce al motivo cerrado de §15.2
 *   y no se publica nada; un `Adapted` conserva sus `slotChanges` en la
 *   procedencia del programa.
 * - La receta resultante pasa por el validador de composición (hallazgos HARD
 *   con su exención por día/regla) ANTES de materializar, con motivo tipado
 *   `COMPOSITION`, y se materializa con [PlanMaterializer].
 *
 * El programa devuelto lleva `sourceRecipe` y `planProvenance` iguales (JSON
 * serializado incluido): preview == programa activado. La duración real la
 * decide después el evaluador con el estimador común (§12.2).
 */
object AuthoredPlanMaterializer {

    /** Prefijo común de los rechazos por material (el wizard parsea los tokens que siguen a «:»). */
    internal const val MATERIAL_PREFIX = "Esta receta necesita material que no has declarado"

    private const val MAX_FINDINGS_IN_MESSAGE = 4

    fun prepare(request: AuthoredPlanRequest): Program {
        val entry = request.entry
        val source = entry.recipe ?: throw PlanMaterializationException(
            PlanEvaluationStage.CATALOG,
            PlanRejectionReason.RECIPE_UNAVAILABLE,
            "La entrada '${entry.id}' no trae su receta de autor",
        )
        val adapted = source.provenance?.category == PlanProvenanceClass.ADAPTED
        val recipe = if (adapted) adaptedRecipe(request, source) else originalRecipe(request, source)
        requireComposition(recipe, request)
        val materialized = PlanMaterializer.materialize(
            program = request.baseProgram,
            recipe = recipe,
            metadata = request.metadata,
            idProvider = request.idProvider,
            // Los hallazgos HARD ya se comprobaron arriba con motivo tipado.
            strict = false,
            options = request.options,
        )
        val prepared = materialized.copy(
            // Procedencia en el Program serializado (§14.1): categoría, edición,
            // fuente y slotChanges de ESTA receta, no del catálogo vigente.
            planProvenance = recipe.provenance,
            // Como los nativos: el id del plan publicado; la receta efectiva
            // (derivada si hubo adaptación) vive en `sourceRecipe`.
            structureTemplateId = entry.id,
        )
        return scheduled(prepared, request.expectedDaysPerWeek)
    }

    // ─── Original: receta intacta o rechazo por material ────────────────────

    private fun originalRecipe(request: AuthoredPlanRequest, source: TrainingPlanRecipe): TrainingPlanRecipe {
        val configurationIds = configurationIdsOf(source)
        val verdicts = PlanAdaptationResolver.missingMaterialOf(
            configurationIds,
            request.equipment,
            request.availability,
            request.catalog,
        )
        val unresolved = verdicts.filterValues { it is ConfigurationAvailability.Unresolved }.keys
        if (unresolved.isNotEmpty()) {
            throw PlanMaterializationException(
                PlanEvaluationStage.CATALOG,
                PlanRejectionReason.UNRESOLVED_CONFIGURATION,
                "La receta de autor usa configuraciones que no existen en el catálogo de ejercicios publicado: " +
                    unresolved.sorted().joinToString(", "),
                affectedSlots = unresolved.sorted(),
            )
        }
        val missing = verdicts.mapNotNull { (configurationId, verdict) ->
            (verdict as? ConfigurationAvailability.Missing)?.let { configurationId to it }
        }
        if (missing.isEmpty()) return source
        val reason = if (missing.any { (_, verdict) -> verdict.evidence == RequirementEvidence.ABSENT }) {
            PlanRejectionReason.APPARATUS_ABSENT
        } else {
            PlanRejectionReason.APPARATUS_UNKNOWN
        }
        throw PlanMaterializationException(
            PlanEvaluationStage.MATERIAL,
            reason,
            materialMessage(missing.flatMap { (_, verdict) -> verdict.missing }.toSet()),
            affectedSlots = missing.map { (configurationId, _) -> configurationId }.sorted(),
            missingRequirements = missing.flatMap { (configurationId, verdict) ->
                verdict.missing.map { token ->
                    if (token == "machine") "machine_config:$configurationId" else token
                }
            }.distinct().sorted(),
        )
    }

    // ─── Adaptado: PlanAdaptationResolver sobre el equipo efectivo real ─────

    private fun adaptedRecipe(request: AuthoredPlanRequest, source: TrainingPlanRecipe): TrainingPlanRecipe {
        val result = PlanAdaptationResolver.adapt(
            PlanAdaptationRequest(
                recipe = source,
                equipment = request.equipment,
                availability = request.availability,
                catalog = request.catalog,
                targetProfile = request.targetProfile,
                // El nivel de un plan de autor ordena la lista pero no la oculta
                // (§15.3 «sin ocultar los originales») y las sustituciones
                // conservan el RIR inicial 2 del autor (§10.2/§10.3).
                level = null,
                // La duración la decide el evaluador con el estimador común
                // (§12.2), no la aproximación previa del resolver.
                sessionBudgetMinutes = null,
            ),
        )
        return when (result) {
            is PlanAdaptationResult.Adapted -> result.recipe
            is PlanAdaptationResult.NotViable -> throw notViable(result)
        }
    }

    private fun notViable(result: PlanAdaptationResult.NotViable): PlanMaterializationException {
        val affected = listOfNotNull(
            result.slotId?.let { slot -> result.configurationId?.let { "$slot=$it" } ?: slot },
        )
        val (stage, reason) = when (result.reason) {
            PlanAdaptationReason.NO_VALID_SUBSTITUTION ->
                PlanEvaluationStage.MATERIAL to PlanRejectionReason.NO_VALID_SUBSTITUTION
            PlanAdaptationReason.APPARATUS_ABSENT ->
                PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_ABSENT
            PlanAdaptationReason.APPARATUS_UNKNOWN ->
                PlanEvaluationStage.MATERIAL to PlanRejectionReason.APPARATUS_UNKNOWN
            PlanAdaptationReason.UNRESOLVED_CONFIGURATION ->
                PlanEvaluationStage.CATALOG to PlanRejectionReason.UNRESOLVED_CONFIGURATION
            PlanAdaptationReason.LOAD_BASIS_UNREPRESENTABLE ->
                PlanEvaluationStage.REPRESENTABLE_LOAD to PlanRejectionReason.LOAD_BASIS_UNREPRESENTABLE
            PlanAdaptationReason.TIME_BUDGET ->
                PlanEvaluationStage.SESSION_DURATION to PlanRejectionReason.TIME_BUDGET
            PlanAdaptationReason.PROFILE_MISMATCH ->
                PlanEvaluationStage.PROFILE to PlanRejectionReason.PROFILE_MISMATCH
            PlanAdaptationReason.LEVEL_UNSUITABLE ->
                PlanEvaluationStage.PROFILE to PlanRejectionReason.LEVEL_UNSUITABLE
        }
        val message = when (result.reason) {
            // El mensaje es diagnóstico; la UI usa la lista estructurada de requisitos.
            PlanAdaptationReason.APPARATUS_ABSENT, PlanAdaptationReason.APPARATUS_UNKNOWN ->
                if (result.missingRequirements.isNotEmpty()) {
                    materialMessage(result.missingRequirements)
                } else {
                    result.detail.ifBlank { MATERIAL_PREFIX }
                }
            PlanAdaptationReason.NO_VALID_SUBSTITUTION -> buildString {
                append("Sin sustitución válida con tu material")
                result.slotId?.let { append(" para '").append(it).append('\'') }
                result.configurationId?.let { append(" (").append(it).append(')') }
                if (result.missingRequirements.isNotEmpty()) {
                    append(". Falta: ").append(result.missingRequirements.sorted().joinToString(", "))
                }
            }
            else -> result.detail.ifBlank { result.reason.name }
        }
        return PlanMaterializationException(
            stage,
            reason,
            message,
            affectedSlots = affected,
            requiredMinutes = result.requiredMinutes,
            missingRequirements = result.missingRequirements.map { token ->
                if (token == "machine" && result.configurationId != null) {
                    "machine_config:${result.configurationId}"
                } else token
            },
        )
    }

    // ─── Composición y calendario ────────────────────────────────────────────

    private fun requireComposition(recipe: TrainingPlanRecipe, request: AuthoredPlanRequest) {
        val hard = ProgramRecipeValidator.hardFindings(recipe, request.metadata)
        if (hard.isEmpty()) return
        val detail = hard.take(MAX_FINDINGS_IN_MESSAGE).joinToString("; ") { "${it.rule} ${it.scope}: ${it.message}" }
        throw PlanMaterializationException(
            PlanEvaluationStage.COMPOSITION,
            PlanRejectionReason.COMPOSITION,
            "La receta '${recipe.id}' no pasa la composición ($detail)",
            affectedSlots = hard.map { "${it.rule}@${it.scope}" }.distinct().take(MAX_FINDINGS_IN_MESSAGE * 2),
        )
    }

    private fun scheduled(program: Program, expectedDaysPerWeek: Int?): Program {
        val sessionDays = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }
            .flatMap { it.weeks }.flatMap { it.sessions }.mapNotNull { it.dayOfWeek }.toSet()
        if (expectedDaysPerWeek != null && sessionDays.size != expectedDaysPerWeek) {
            throw PlanMaterializationException(
                PlanEvaluationStage.FREQUENCY_SPLIT,
                PlanRejectionReason.FREQUENCY,
                "La receta fija produce ${SpanishPlurals.days(sessionDays.size)}, no $expectedDaysPerWeek",
            )
        }
        return program.copy(
            schedulePlan = ProgramSchedulePlan(
                weekStartDay = program.startDay,
                trainingDays = sessionDays,
            ),
        )
    }

    private fun configurationIdsOf(recipe: TrainingPlanRecipe): List<String> = recipe.weeks
        .flatMap { it.days }
        .flatMap { it.slots }
        .map { it.lift.configurationId }
        .distinct()

    private fun materialMessage(missing: Set<String>): String =
        "$MATERIAL_PREFIX: ${missing.sorted().joinToString(", ")}"
}
