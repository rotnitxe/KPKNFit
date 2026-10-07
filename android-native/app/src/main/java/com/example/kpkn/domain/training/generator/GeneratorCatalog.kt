package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.exercises.catalogv2.CatalogCompositionMetadataProvider
import com.example.kpkn.data.exercises.catalogv2.toLegacyInfo
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.resolveMuscleVolumeContribution
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseArticulationTypeV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.approach.ApproachExerciseInfo
import com.example.kpkn.domain.training.approach.ApproachInfoProvider
import com.example.kpkn.domain.training.machineConfigToken
import com.example.kpkn.domain.training.supportRequirementsFor

/** Familia de material de una configuración (sale del `equipmentId` del catálogo, nunca del id ni del nombre). */
internal enum class EquipmentTier {
    BARBELL, DUMBBELL, MACHINE, CABLE, SMITH, KETTLEBELL, BAND, RINGS, BODYWEIGHT, OTHER;

    companion object {
        fun of(equipmentId: String): EquipmentTier = when (equipmentId) {
            "barbell", "ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar" -> BARBELL
            "dumbbells" -> DUMBBELL
            "machine" -> MACHINE
            "cable" -> CABLE
            "smith_machine" -> SMITH
            "kettlebell" -> KETTLEBELL
            "band" -> BAND
            "trx" -> RINGS
            "bodyweight" -> BODYWEIGHT
            else -> OTHER
        }
    }
}

/** Aporte de UNA serie de un ejercicio a un músculo canónico (mismos pesos que `VolumeCalculator`: 1,0 / 0,5 / 0,4). */
internal data class MuscleContribution(val direct: Double, val secondary: Double, val stabilizer: Double) {
    val indirect: Double get() = secondary + stabilizer
}

/** Una configuración del catálogo con lo que el generador necesita de ella, calculado una sola vez. */
internal class CatalogEntry(
    val familyId: String,
    val definition: ExerciseDefinitionV2,
    val configuration: ExerciseConfigurationV2,
    private val catalogRevision: String,
) {
    val id: String get() = configuration.id
    val definitionId: String get() = definition.id
    val equipmentId: String = configuration.profile.equipmentId
    val tier: EquipmentTier = EquipmentTier.of(configuration.profile.equipmentId)
    val difficulty: Double get() = configuration.profile.technicalDifficulty
    val setupSeconds: Int get() = configuration.profile.setupTimeSeconds ?: 30
    val isCompound: Boolean get() = configuration.profile.articulationType == ExerciseArticulationTypeV2.MULTIARTICULAR
    val loadMode: String get() = configuration.profile.loadMode
    val axialLoadFactor: Double get() = configuration.profile.axialLoadFactor

    /** Soportes que exige la configuración según el contrato compartido de la app (`supportRequirementsFor`). */
    val sharedRequirements: Set<String> by lazy { supportRequirementsFor(configuration.id) }

    /** Token de máquina concreta (`machine_config:<id>`), calculado una vez. */
    val machineToken: String by lazy { machineConfigToken(configuration.id) }

    /** Identidad y músculos tal como los entrega el adaptador del resto de la app (nombre, músculos implicados, perfil). */
    val legacy: ExerciseMuscleInfo by lazy {
        definition.toLegacyInfo(
            familyId = familyId,
            catalogRevision = catalogRevision,
            configuration = configuration,
            legacyId = configuration.id,
        )
    }

    val contributions: Map<String, MuscleContribution> by lazy { MuscleContributions.of(legacy.involvedMuscles) }
}

internal object MuscleContributions {
    /** Misma regla que `VolumeCalculator.calculateRoleSeparatedMuscleVolume` para UNA serie de un ejercicio. */
    fun of(involved: List<InvolvedMuscle>): Map<String, MuscleContribution> {
        val acc = LinkedHashMap<String, DoubleArray>()
        involved.forEach { involvement ->
            if (involvement.role == MuscleRole.NEUTRALIZER) return@forEach
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
            if (canonical !in VolumeCalculator.standardVolumeMuscles) return@forEach
            val contribution = resolveMuscleVolumeContribution(involvement)
            val bucket = acc.getOrPut(canonical) { DoubleArray(3) }
            when (involvement.role) {
                MuscleRole.PRIMARY -> bucket[0] = maxOf(bucket[0], contribution)
                MuscleRole.SECONDARY -> bucket[1] = maxOf(bucket[1], contribution)
                MuscleRole.STABILIZER -> bucket[2] = maxOf(bucket[2], contribution)
                MuscleRole.NEUTRALIZER -> Unit
            }
        }
        return acc.mapValues { (_, b) -> MuscleContribution(b[0], b[1], b[2]) }
    }
}

/** Índice del catálogo v2 por id de configuración (solo APPROVED). */
internal class GeneratorCatalog private constructor(val catalog: ExerciseCatalogV2) {
    val revision: String = catalog.catalogRevision
    val entries: Map<String, CatalogEntry>

    init {
        val map = HashMap<String, CatalogEntry>(1024)
        catalog.families.forEach { family ->
            family.definitions.forEach { definition ->
                definition.configurations.forEach { configuration ->
                    if (configuration.evidence.reviewStatus == CatalogReviewStatusV2.APPROVED) {
                        map[configuration.id] = CatalogEntry(family.id, definition, configuration, catalog.catalogRevision)
                    }
                }
            }
        }
        entries = map
    }

    fun entry(id: String): CatalogEntry? = entries[id]

    /**
     * Lo que el planificador de aproximación sabe de cada ejercicio (articulaciones, compuesto, admite carga pesada): el MISMO
     * proveedor, con los mismos metadatos del catálogo, que usa el materializador de planes. Así la aproximación que arma el
     * generador y la que completaría el materializador deciden igual y una segunda pasada no cambia nada.
     */
    val approachInfoOf: (Exercise) -> ApproachExerciseInfo? by lazy {
        ApproachInfoProvider.fromMetadata(CatalogCompositionMetadataProvider.fromCatalog(catalog))
    }

    companion object {
        @Volatile
        private var last: GeneratorCatalog? = null

        /** Índice del catálogo recibido; se reutiliza mientras llegue la MISMA instancia (identidad, no igualdad profunda). */
        fun of(catalog: ExerciseCatalogV2): GeneratorCatalog {
            val cached = last
            if (cached != null && cached.catalog === catalog) return cached
            val built = GeneratorCatalog(catalog)
            last = built
            return built
        }
    }
}
