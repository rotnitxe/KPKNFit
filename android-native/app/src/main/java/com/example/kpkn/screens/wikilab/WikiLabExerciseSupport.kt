package com.example.kpkn.screens.wikilab

import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.resolveExercise
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.repository.WikiLabRepository
import com.example.kpkn.domain.exercises.catalogv2.AprendeOntology
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityBand
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityEngine
import com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityInput
import com.example.kpkn.domain.exercises.catalogv2.AprendeMuscleRole
import com.example.kpkn.domain.training.VolumeCalculator

internal data class WikiLabExerciseLink(
    val id: String,
    val name: String,
    val subtitle: String = "",
)

private val CANONICAL_MUSCLE_COLORS = mapOf(
    "Pectorales" to Color(0xFFE53935),
    "Dorsales" to Color(0xFF1E88E5),
    "Trapecio" to Color(0xFF1976D2),
    "Deltoides" to Color(0xFFFF8F00),
    "Tríceps" to Color(0xFF7B1FA2),
    "Bíceps" to Color(0xFF8E24AA),
    "Antebrazo" to Color(0xFF795548),
    "Abdomen" to Color(0xFF00897B),
    "Cuádriceps" to Color(0xFF43A047),
    "Isquiosurales" to Color(0xFF2E7D32),
    "Glúteos" to Color(0xFF558B2F),
    "Glúteo Medio" to Color(0xFF558B2F),
    "Aductores" to Color(0xFF7CB342),
    "Pantorrillas" to Color(0xFF33691E),
    "Core" to Color(0xFF00695C),
    "Erectores Espinales" to Color(0xFF1565C0),
    "Cuello" to Color(0xFF6D4C41),
)

internal fun wikilabMuscleColor(name: String): Color =
    CANONICAL_MUSCLE_COLORS[name] ?: Color(0xFF757575)

/** Local Aprende tokens. Anatomy is content, not a color legend. */
internal val APRENDE_BACKGROUND: Color = Color.Black
internal val APRENDE_PANEL: Color = Color(0xFF121212)
internal val APRENDE_PANEL_ELEVATED: Color = Color(0xFF181818)
internal val APRENDE_DIVIDER: Color = Color(0xFF2A2A2A)
internal val APRENDE_TEXT: Color = Color.White
internal val APRENDE_TEXT_SECONDARY: Color = Color.White.copy(alpha = 0.72f)
internal val APRENDE_TEXT_MUTED: Color = Color.White.copy(alpha = 0.5f)
internal val APRENDE_LINK_COLOR: Color = Color(0xFF9DB6C9)

/** Compatibility alias used by existing Learn surfaces. */
internal val APRENDE_MUTED_FILL: Color = APRENDE_PANEL

internal fun aprendeMuscleColor(@Suppress("UNUSED_PARAMETER") name: String): Color =
    Color(0xFF7F8D96)

internal fun resolveWikiLabExerciseLinks(
    ids: List<String>,
    subtitle: String = "",
): List<WikiLabExerciseLink> {
    // Static anatomy labels use only the versioned decisions in
    // AprendeOntology; an arbitrary display-name match is intentionally not
    // accepted here.
    val seen = linkedSetOf<String>()
    return ids.mapNotNull { requestedId ->
        val canonicalId = resolveExercise(requestedId)?.id
            ?: AprendeOntology.legacyExerciseId(requestedId)
            ?: return@mapNotNull null
        val exercise = resolveExercise(canonicalId) ?: return@mapNotNull null
        if (!seen.add(exercise.id)) return@mapNotNull null
        WikiLabExerciseLink(
            id = exercise.id,
            name = exercise.name,
            subtitle = subtitle,
        )
    }
}

internal fun resolveWikiLabMuscleId(muscleName: String): String? = when (muscleName) {
    "Pectorales" -> "pectoral"
    "Dorsales" -> "espalda"
    "Trapecio" -> "trapecio"
    "Deltoides" -> "deltoides"
    "Tríceps" -> "tríceps"
    "Bíceps" -> "bíceps"
    "Antebrazo" -> "antebrazo"
    "Abdomen" -> "abdomen"
    "Cuádriceps" -> "cuádriceps"
    "Isquiosurales" -> "isquiosurales"
    "Glúteos" -> "glúteos"
    "Aductores" -> "aductores"
    "Pantorrillas" -> "pantorrillas"
    "Core" -> "core"
    "Erectores Espinales" -> "erectores-espinales"
    "Cuello" -> "cuello"
    else -> null
}

internal fun canonicalMuscleDisplayName(raw: String, emphasis: String? = null): String =
    VolumeCalculator.normalizeCanonicalMuscleGroup(raw.replace('-', ' '), emphasis)

internal fun canonicalWikiLabMuscleId(raw: String, emphasis: String? = null): String? =
    resolveWikiLabMuscleId(canonicalMuscleDisplayName(raw, emphasis))

internal fun canonicalWikiLabMuscleIdFromCatalogId(catalogMuscleId: String): String? =
    AprendeOntology.wikiLabMuscleId(catalogMuscleId)

internal fun canonicalWikiLabPatternId(catalogPatternId: String): String? =
    AprendeOntology.wikiLabPatternId(catalogPatternId)

internal fun canonicalWikiLabMuscleIdFromEntityId(muscleId: String): String? {
    val entity = WikiLabRepository.getMuscleById(muscleId)
    val canonicalFromName = entity?.name?.let { canonicalWikiLabMuscleId(it) }
    if (canonicalFromName != null) return canonicalFromName
    val canonicalFromId = canonicalWikiLabMuscleId(muscleId)
    if (canonicalFromId != null) return canonicalFromId
    return entity?.let {
        if (
            it.id in setOf(
                "pectoral",
                "espalda",
                "hombros",
                "brazos",
                "piernas",
                "abdomen",
                "core",
                "deltoides",
                "bíceps",
                "tríceps",
                "antebrazo",
                "cuádriceps",
                "isquiosurales",
                "glúteos",
                "aductores",
                "pantorrillas",
                "erectores-espinales",
                "cuello",
                "trapecio",
            )
        ) it.id else null
    }
}

internal fun collapseInvolvedMusclesToCanonical(muscles: List<InvolvedMuscle>): List<InvolvedMuscle> {
    if (muscles.isEmpty()) return emptyList()

    val rolePriority = mapOf(
        MuscleRole.PRIMARY to 0,
        MuscleRole.SECONDARY to 1,
        MuscleRole.STABILIZER to 2,
        MuscleRole.NEUTRALIZER to 3,
    )

    val grouped = linkedMapOf<String, InvolvedMuscle>()
    muscles.forEach { item ->
        val canonical = canonicalMuscleDisplayName(item.muscle, item.emphasis)
        val existing = grouped[canonical]
        if (existing == null) {
            grouped[canonical] = item.copy(muscle = canonical)
        } else {
            val existingPriority = rolePriority[existing.role] ?: 99
            val incomingPriority = rolePriority[item.role] ?: 99
            val role = if (incomingPriority < existingPriority) item.role else existing.role
            val volumeContribution = maxOf(existing.volumeContribution ?: 0.0, item.volumeContribution ?: 0.0).takeIf { it > 0.0 }
            grouped[canonical] = existing.copy(muscle = canonical, role = role, volumeContribution = volumeContribution)
        }
    }
    return grouped.values.toList()
}

internal data class AprendeSimilarItem(
    val exercise: ExerciseMuscleInfo,
    val rationale: String,
    val score: Int,
)

internal data class AprendeExerciseRelations(
    val equivalent: List<AprendeSimilarItem> = emptyList(),
    val patternVariants: List<AprendeSimilarItem> = emptyList(),
    val anatomicalTransfer: List<AprendeSimilarItem> = emptyList(),
)

/**
 * Builds explicit relations from the resolved v2 profile. There is no
 * name-based or inferred setup fallback here. Custom exercises are kept in
 * the catalog display but do not receive synthetic v2 relations.
 */
internal fun buildAprendeExerciseRelations(
    info: ExerciseMuscleInfo,
    catalog: List<ExerciseMuscleInfo>,
    limit: Int = 4,
): AprendeExerciseRelations {
    val current = info.decodeCatalogRichMetadata() ?: return AprendeExerciseRelations()
    val currentInput = current.toAprendeSimilarityInput(info)
    val candidatePairs = catalog.asSequence()
        .filterNot { it.isCustom }
        .mapNotNull { candidate ->
            candidate.decodeCatalogRichMetadata()?.let { candidate to it.toAprendeSimilarityInput(candidate) }
        }
        // The shared index contains both the parent definition entry and the
        // explicit configuration entry for a default variant. They are the
        // same editorial record, so compare configuration identity rather
        // than allowing a relation list to show that record twice.
        .filterNot { (_, input) -> input.configurationId == current.identity.configurationId }
        .distinctBy { (_, input) -> input.configurationId ?: input.id }
        .toList()
    val candidateById = candidatePairs.associateBy { (candidate, _) -> candidate.id }
    val matches = AprendeSimilarityEngine.rank(
        current = currentInput,
        candidates = candidateById.values.map { (_, input) -> input },
        limit = maxOf(limit * 3, limit),
    )

    fun item(match: com.example.kpkn.domain.exercises.catalogv2.AprendeSimilarityMatch): AprendeSimilarItem {
        val candidate = candidateById.getValue(match.candidate.id).first
        val rationale = when {
            match.sameIntent -> "Misma intención de reemplazo: ${candidate.name}."
            match.sameDefinition -> "Otra configuración de la misma definición."
            match.samePattern && match.sharedJoints > 0 -> "Comparte patrón y articulaciones relevantes."
            match.sharedMuscles > 0 && match.sharedJoints > 0 -> "Comparte músculos y articulaciones relevantes."
            match.sharedMuscles > 0 -> "Comparte musculatura objetivo."
            else -> "Comparte el patrón de movimiento."
        }
        return AprendeSimilarItem(candidate, rationale, match.score)
    }

    fun top(band: AprendeSimilarityBand): List<AprendeSimilarItem> = matches
        .filter { it.band == band }
        .take(limit)
        .map(::item)

    return AprendeExerciseRelations(
        equivalent = top(AprendeSimilarityBand.EQUIVALENT),
        patternVariants = top(AprendeSimilarityBand.PATTERN_VARIANT),
        anatomicalTransfer = top(AprendeSimilarityBand.ANATOMICAL_TRANSFER),
    )
}

private fun com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2.toAprendeSimilarityInput(
    info: ExerciseMuscleInfo,
): AprendeSimilarityInput = AprendeSimilarityInput(
    id = info.id,
    displayName = info.name,
    definitionId = identity.definitionId,
    replacementGroup = replacement.replacementGroup,
    preservesIntent = replacement.preservesIntent.toSet(),
    movementPatternId = canonicalWikiLabPatternId(biomechanics.movementPatternId)
        ?: biomechanics.movementPatternId,
    muscles = (
        anatomy.primaryMuscles + anatomy.secondaryMuscles + anatomy.stabilizerMuscles
        ).toSet(),
    musclesByRole = mapOf(
        AprendeMuscleRole.PRIMARY to anatomy.primaryMuscles.toSet(),
        AprendeMuscleRole.SECONDARY to anatomy.secondaryMuscles.toSet(),
        AprendeMuscleRole.STABILIZER to anatomy.stabilizerMuscles.toSet(),
    ),
    joints = anatomy.jointInvolvement.map { it.jointId }.toSet(),
    bodyRegion = biomechanics.bodyRegion,
    kineticChain = biomechanics.kineticChain,
    laterality = biomechanics.laterality,
    equipmentId = biomechanics.equipmentId,
    configurationId = identity.configurationId,
)

/** Reverse links are derived from the current resolved catalog, never from
 * stale hand-authored example lists. */
internal fun catalogExercisesForMuscle(muscleId: String, limit: Int = 8): List<WikiLabExerciseLink> {
    val acceptedCatalogMuscleIds = AprendeOntology.catalogMuscleIdsForWikiLabEntity(muscleId)
    return approvedCatalogExerciseEntries()
        .filter { exercise ->
            if (exercise.isCustom) return@filter false
            val metadata = exercise.decodeCatalogRichMetadata() ?: return@filter false
            val ids = metadata.anatomy.primaryMuscles +
                metadata.anatomy.secondaryMuscles +
                metadata.anatomy.stabilizerMuscles
            ids.any { it in acceptedCatalogMuscleIds }
        }
        .sortedBy { it.name }
        .take(limit)
        .map { it.toWikiLabExerciseLink() }
}

internal fun catalogExercisesForJoint(jointId: String, limit: Int = 8): List<WikiLabExerciseLink> =
    approvedCatalogExerciseEntries()
        .filter { exercise ->
            exercise.decodeCatalogRichMetadata()?.anatomy?.jointInvolvement?.any { it.jointId == jointId } == true
        }
        .sortedBy { it.name }
        .take(limit)
        .map { it.toWikiLabExerciseLink() }

internal fun catalogExercisesForPattern(patternId: String, limit: Int = 8): List<WikiLabExerciseLink> =
    approvedCatalogExerciseEntries()
        .filter { exercise ->
            canonicalWikiLabPatternId(exercise.decodeCatalogRichMetadata()?.biomechanics?.movementPatternId.orEmpty()) == patternId
        }
        .sortedBy { it.name }
        .take(limit)
        .map { it.toWikiLabExerciseLink() }

private fun ExerciseMuscleInfo.toWikiLabExerciseLink(): WikiLabExerciseLink =
    WikiLabExerciseLink(
        id = id,
        name = name,
        subtitle = listOfNotNull(equipment, catalogVariantChips.takeIf { it.isNotEmpty() }?.joinToString(" · "))
            .joinToString(" · "),
    )

/** Full approved source for reverse links: defaults plus every explicit
 * configuration, with the default configuration deduplicated by identity. */
private fun approvedCatalogExerciseEntries(): List<ExerciseMuscleInfo> =
    catalogExerciseIndex()
        .values
        .asSequence()
        .filterNot { it.isCustom }
        .distinctBy { it.catalogConfigurationId ?: it.id }
        .toList()
