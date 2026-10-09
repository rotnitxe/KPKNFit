package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.AprendeMuscleRole
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityBand
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityEngine
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityInput
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.AvoidGroup
import com.example.kpkn.domain.onboarding.AvoidGroups
import com.example.kpkn.domain.training.generator.CatalogEntry
import com.example.kpkn.domain.training.generator.DayEquipment
import com.example.kpkn.domain.training.generator.GeneratorCatalog

/*
 * Entreno v2 · «Cambiar» un ejercicio desde el detalle del programa. Función pura: dado el ejercicio, el material del lugar de
 * su día y el catálogo, las alternativas que esa persona puede hacer con ese material. No conoce la pantalla.
 */

/** Por qué se ofrece una alternativa: misma intención (equivalente) o mismo patrón de movimiento. */
enum class SwapKind { EQUIVALENT, SAME_PATTERN }

/**
 * Una alternativa para cambiar un ejercicio.
 *
 * - [configId]: la configuración del catálogo que entraría en su lugar (`ExerciseConfigurationV2.id`).
 * - [displayName]: el nombre verbatim de la definición; [variant], el matiz de la configuración (implemento, agarre…) o vacío.
 * - [pattern]: el patrón de movimiento del catálogo (`movementPatternId`).
 */
data class SwapOption(
    val configId: String,
    val displayName: String,
    val variant: String,
    val pattern: String,
    val kind: SwapKind,
)

object ExerciseSwapOptions {

    /**
     * Hasta [limit] alternativas para [exercise]: primero las equivalentes (misma intención, según el ranking de
     * [AprendeSimilarityEngine]) y luego las del mismo patrón. Solo configuraciones que el material [sessionPlaceAvailability]
     * del lugar de ese día permite (el mismo equipo efectivo y el mismo filtro que el generador: [DayEquipment]); nunca la
     * misma configuración ni una de los grupos que la persona evita ([avoided], vía [AvoidGroups]). Vacío si el ejercicio no
     * tiene identidad de catálogo.
     */
    fun optionsFor(
        exercise: Exercise,
        sessionPlaceAvailability: EquipmentAvailability,
        catalog: ExerciseCatalogV2,
        avoided: Set<AvoidGroup> = emptySet(),
        limit: Int = 6,
    ): List<SwapOption> {
        if (limit <= 0) return emptyList()
        val index = GeneratorCatalog.of(catalog)
        val current = exercise.catalogConfigurationId?.let(index::entry) ?: return emptyList()
        val equipment = DayEquipment(sessionPlaceAvailability)
        val avoidedDefinitions = avoided.flatMapTo(mutableSetOf()) { AvoidGroups.definitionIdsOf(it) }
        // Orden fijo por id: el ranking desempata por nombre, así que el resultado no depende del orden del mapa.
        val pool = index.entries.values
            .filter { it.id != current.id && it.definitionId !in avoidedDefinitions }
            .filter { equipment.allows(it, requires = emptyList()) }
            .sortedBy { it.id }
        val byId = pool.associateBy { it.id }
        val ranked = AprendeSimilarityEngine.rank(current.similarityInput(), pool.map { it.similarityInput() }, limit = pool.size)
        // Primero las equivalentes; cada banda conserva el orden de puntuación del ranking.
        val ordered = ranked.filter { it.band == AprendeSimilarityBand.EQUIVALENT }.map { it.candidate.id to SwapKind.EQUIVALENT } +
            ranked.filter { it.band == AprendeSimilarityBand.PATTERN_VARIANT }.map { it.candidate.id to SwapKind.SAME_PATTERN }
        return ordered.take(limit).mapNotNull { (id, kind) ->
            val entry = byId[id] ?: return@mapNotNull null
            SwapOption(
                configId = entry.id,
                displayName = entry.definition.canonicalName.trim(),
                variant = entry.legacy.catalogVariantChips.joinToString(" · "),
                pattern = entry.configuration.profile.movementPatternId,
                kind = kind,
            )
        }
    }
}

/** La entrada del motor de similitud de una configuración del catálogo (los mismos datos que su perfil resuelto). */
private fun CatalogEntry.similarityInput(): AprendeSimilarityInput {
    val profile = configuration.profile
    return AprendeSimilarityInput(
        id = configuration.id,
        displayName = definition.canonicalName.trim(),
        definitionId = definition.id,
        replacementGroup = profile.replacementGroup,
        // Igual que el cargador del catálogo: la intención es el mismo patrón y el mismo músculo primario.
        preservesIntent = profile.primaryMuscles.mapTo(linkedSetOf()) { "${profile.movementPatternId}:$it" },
        movementPatternId = profile.movementPatternId,
        muscles = (profile.primaryMuscles + profile.secondaryMuscles + profile.stabilizerMuscles).toSet(),
        musclesByRole = mapOf(
            AprendeMuscleRole.PRIMARY to profile.primaryMuscles.toSet(),
            AprendeMuscleRole.SECONDARY to profile.secondaryMuscles.toSet(),
            AprendeMuscleRole.STABILIZER to profile.stabilizerMuscles.toSet(),
        ),
        joints = profile.jointInvolvement.mapTo(linkedSetOf()) { it.jointId },
        bodyRegion = profile.bodyRegion,
        kineticChain = profile.kineticChain,
        laterality = profile.laterality,
        equipmentId = profile.equipmentId,
        configurationId = configuration.id,
    )
}
