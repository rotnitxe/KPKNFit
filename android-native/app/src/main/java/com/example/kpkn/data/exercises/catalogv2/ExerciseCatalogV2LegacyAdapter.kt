package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseMetadataV2
import kotlinx.serialization.json.Json

/**
 * Transitional presentation adapter. It materializes one default
 * configuration per definition for legacy screens while preserving the v2
 * identity. It deliberately does not expose v2 axes as legacy
 * TechnicalAspect values because that API can create invalid cartesian
 * combinations. The v2 picker must use the repository compatibility API.
 */
private val catalogRichMetadataCodec = Json { encodeDefaults = true; ignoreUnknownKeys = false }

fun ExerciseCatalogV2.toLegacyDefaultCatalog(): List<ExerciseMuscleInfo> =
    families.flatMap { family ->
        family.definitions.mapNotNull { definition ->
            definition.defaultConfiguration()?.let { configuration ->
                definition.toLegacyInfo(
                    familyId = family.id,
                    catalogRevision = catalogRevision,
                    configuration = configuration,
                )
            }
        }
    }

private fun ExerciseDefinitionV2.defaultConfiguration(): ExerciseConfigurationV2? =
    configurations.firstOrNull { it.id == defaultConfigurationId }

internal fun ExerciseDefinitionV2.toLegacyInfo(
    familyId: String,
    catalogRevision: String,
    configuration: ExerciseConfigurationV2,
    legacyId: String = id,
): ExerciseMuscleInfo {
    val profile = configuration.profile
    val primary = profile.primaryMuscles.mapNotNull(::muscleLabel)
    val secondary = profile.secondaryMuscles.mapNotNull(::muscleLabel)
    val stabilizers = profile.stabilizerMuscles.mapNotNull(::muscleLabel)
    val involved = buildList {
        primary.forEach { add(InvolvedMuscle(it, MuscleRole.PRIMARY)) }
        secondary.forEach { add(InvolvedMuscle(it, MuscleRole.SECONDARY)) }
        stabilizers.forEach { add(InvolvedMuscle(it, MuscleRole.STABILIZER)) }
    }
    return ExerciseMuscleInfo(
        id = legacyId,
        name = canonicalName,
        alias = searchTerms.joinToString(", ").ifBlank { null },
        // The curated description is already localized/contextual. Do not append the
        // raw compiler display summary (e.g. "machine · guided") to user-facing text.
        description = description.trim(),
        involvedMuscles = involved,
        equipment = equipmentLabel(profile.equipmentId),
        category = if (kind.name == "SPECIALTY") "Especialidad" else "Fuerza",
        type = if (kind.name == "SPECIALTY") "Especialidad" else "Accesorio",
        force = movementLabel(profile.movementPatternId),
        chain = profile.kineticChain.name.lowercase(),
        bodyPart = profile.bodyRegion.name.lowercase(),
        isCommon = true,
        efc = profile.efc,
        cnc = profile.cnc,
        ssc = profile.ssc,
        ttc = profile.ttc,
        axialLoadFactor = profile.axialLoadFactor,
        technicalDifficulty = profile.technicalDifficulty,
        commonMistakes = profile.commonMistakes.map { com.example.kpkn.data.models.CommonMistake(it, "Reduce la carga y repite el cue técnico.") },
        setupCues = profile.setupCues,
        executionCues = profile.executionCues,
        replacementGroup = profile.replacementGroup,
        replacementPriority = profile.replacementPriority,
        movementPattern = profile.movementPatternId,
        catalogRevision = catalogRevision,
        catalogDefinitionId = id,
        catalogConfigurationId = configuration.id,
        performanceProfileId = profile.performanceProfileId,
        catalogReviewStatus = configuration.evidence.reviewStatus.name,
        catalogRichMetadataJson = profile.richMetadata?.let { metadata ->
            catalogRichMetadataCodec.encodeToString(ResolvedExerciseMetadataV2.serializer(), metadata)
        },
    )
}

/** Materializes the exact selected v2 configuration for a legacy callback. */
fun ExerciseCatalogV2.toLegacySelection(selection: com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2): ExerciseMuscleInfo? {
    if (selection.catalogRevision != catalogRevision) return null
    val match = families.asSequence().flatMap { family ->
        family.definitions.asSequence().mapNotNull { definition ->
            val configuration = definition.configurations.firstOrNull { it.id == selection.configurationId }
                ?: return@mapNotNull null
            if (definition.id != selection.definitionId) return@mapNotNull null
            definition.toLegacyInfo(
                familyId = family.id,
                catalogRevision = catalogRevision,
                configuration = configuration,
                legacyId = definition.id,
            )
        }
    }.firstOrNull()
    return match
}
/** Builds a lookup containing every explicit configuration while keeping the
 * visible catalog list parent-level. Configuration ids are the only keys used
 * for non-default profiles in AUGE/history lookups. */
fun ExerciseCatalogV2.toLegacyConfigurationLookup(): Map<String, ExerciseMuscleInfo> =
    families
        .flatMap { family ->
            family.definitions.flatMap { definition ->
                definition.configurations.map { configuration ->
                    definition.toLegacyInfo(
                        familyId = family.id,
                        catalogRevision = catalogRevision,
                        configuration = configuration,
                        legacyId = configuration.id,
                    )
                }
            }
        }
        .associateBy { it.id.lowercase() }
private fun muscleLabel(id: String): String? = mapOf(
    "pectoralis" to "Pectorales",
    "deltoid" to "Deltoides",
    "triceps" to "Tríceps",
    "biceps" to "Bíceps",
    "forearm" to "Antebrazo",
    "latissimus_dorsi" to "Dorsales",
    "erector_spinae" to "Erectores Espinales",
    "hamstrings" to "Isquiosurales",
    "gluteus_maximus" to "Glúteos",
    "quadriceps" to "Cuádriceps",
    "calves" to "Pantorrillas",
    "tibialis_anterior" to "Tibial Anterior",
    "hip_flexors" to "Flexores Cadera",
    "neck" to "Cuello",
    "adductors" to "Aductores",
    "tensor_fasciae_latae" to "Tensor Fascia Lata",
    "trapezius" to "Trapecio",
    "rhomboids" to "Romboides",
    "abdominals" to "Abdomen",
    "core" to "Core",
)[id]

private fun equipmentLabel(id: String): String = mapOf(
    "barbell" to "Barra",
    "dumbbells" to "Mancuerna",
    "machine" to "Máquina",
    "cable" to "Polea",
    "bodyweight" to "Peso Corporal",
    "plate" to "Disco",
    "band" to "Banda",
    "kettlebell" to "Kettlebell",
    "ez_bar" to "Barra EZ",
    "trx" to "TRX",
    "smith_machine" to "Máquina Smith",
)[id] ?: id

private fun movementLabel(id: String): String = mapOf(
    "hip_hinge" to "Bisagra",
    "hip_abduction" to "Abducción Cadera",
    "hip_adduction" to "Aducción Cadera",
    "hip_adduction_isometric" to "Aducción Isométrica",
    "hip_adduction_dynamic" to "Aducción Dinámica",
    "horizontal_push" to "Empuje Horizontal",
    "horizontal_abduction" to "Abducción Horizontal",
    "unilateral_knee_dominant" to "Dominante de Rodilla Unilateral",
    "unilateral_knee_dominant_asymmetric" to "Dominante de Rodilla Unilateral Asimétrico",
    "elbow_flexion" to "Flexión Codo",
    "shoulder_abduction" to "Abducción Hombro",
    "shoulder_abduction_full_rom" to "Abducción Completa",
)[id] ?: id
