package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.LimitingFactorCategory
import com.example.kpkn.data.models.LimitingFactorReport
import com.example.kpkn.domain.exercises.catalogv2.AprendeOntology
import com.example.kpkn.domain.training.VolumeCalculator

/** Entrada curada del catálogo de factores limitantes post-ejercicio. */
data class LimitingFactorDefinition(
    val id: String,
    val label: String,
    val category: LimitingFactorCategory,
    val defaultMuscleIds: List<String> = emptyList(),
    val defaultJointIds: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

/**
 * Catálogo puro de cuellos de botella post-ejercicio:
 * - Trabajo concreto del movimiento (`Agarre` -> `forearm`, `Estabilidad de tronco` -> `core`).
 * - Músculo específico (`AprendeOntology.catalogMuscleToWikiLab` + agregado `espalda`).
 * - Articulación (`AprendeOntology.wikiLabJointIds`).
 * - Capacidad cardiovascular (`cardio`).
 *
 * Si el usuario reporta espalda/lumbar en un ejercicio cuyo músculo primario NO es espalda
 * (p. ej. sentadilla, peso muerto, remo/curl con compensación), enlaza automáticamente `core`.
 */
object LimitingFactorCatalog {

    private val BACK_PRIMARY_IDENTIFIERS = setOf(
        "espalda",
        "latissimus_dorsi",
        "dorsal-ancho",
        "dorsales",
        "erector_spinae",
        "erectores-espinales",
        "erectores espinales",
        "trapezius",
        "trapecio",
        "rhomboids",
        "romboides",
    )

    private val LUMBAR_OR_BACK_IDS = setOf(
        "espalda",
        "erector_spinae",
        "columna-lumbar",
    )

    val entries: List<LimitingFactorDefinition> = listOf(
        // Trabajo de movimiento
        LimitingFactorDefinition(
            id = "grip",
            label = "Agarre",
            category = LimitingFactorCategory.MOVEMENT_WORK,
            defaultMuscleIds = listOf("forearm"),
            defaultJointIds = listOf("muñeca"),
            keywords = listOf("agarre", "mano", "antebrazo", "straps", "dedos"),
        ),
        LimitingFactorDefinition(
            id = "bracing",
            label = "Estabilidad de tronco",
            category = LimitingFactorCategory.MOVEMENT_WORK,
            defaultMuscleIds = listOf("core", "abdominals", "erector_spinae"),
            defaultJointIds = listOf("columna-lumbar"),
            keywords = listOf("bracing", "tronco", "core", "abdomen", "faja"),
        ),
        LimitingFactorDefinition(
            id = "balance",
            label = "Equilibrio y control",
            category = LimitingFactorCategory.MOVEMENT_WORK,
            defaultMuscleIds = listOf("core", "gluteus_medius"),
            defaultJointIds = listOf("cadera", "tobillo"),
            keywords = listOf("equilibrio", "inestabilidad", "balance"),
        ),
        LimitingFactorDefinition(
            id = "lockout",
            label = "Bloqueo final",
            category = LimitingFactorCategory.MOVEMENT_WORK,
            defaultMuscleIds = listOf("triceps"),
            defaultJointIds = listOf("codo"),
            keywords = listOf("bloqueo", "triceps", "empuje"),
        ),
        // Cardio
        LimitingFactorDefinition(
            id = "cardio",
            label = "Capacidad cardiovascular",
            category = LimitingFactorCategory.CARDIO,
            keywords = listOf("cardio", "aire", "respiracion", "pulmon", "fatiga metabolica", "altas reps"),
        ),
        // Músculos frecuentes e integrados con AprendeOntology
        LimitingFactorDefinition(
            id = "espalda",
            label = "Espalda",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("erector_spinae", "latissimus_dorsi"),
            defaultJointIds = listOf("columna-lumbar"),
            keywords = listOf("espalda", "dorsal", "lumbar"),
        ),
        LimitingFactorDefinition(
            id = "erector_spinae",
            label = "Lumbar / erectores",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("erector_spinae"),
            defaultJointIds = listOf("columna-lumbar"),
            keywords = listOf("lumbar", "espalda baja", "erectores"),
        ),
        LimitingFactorDefinition(
            id = "forearm",
            label = "Antebrazo y mano",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("forearm"),
            defaultJointIds = listOf("muñeca"),
            keywords = listOf("antebrazo", "mano", "muneca"),
        ),
        LimitingFactorDefinition(
            id = "core",
            label = "Core",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("core"),
            keywords = listOf("core", "estabilidad"),
        ),
        LimitingFactorDefinition(
            id = "abdominals",
            label = "Abdomen",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("abdominals"),
            keywords = listOf("abdomen", "abs"),
        ),
        LimitingFactorDefinition(
            id = "biceps",
            label = "Bíceps",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("biceps"),
            defaultJointIds = listOf("codo"),
            keywords = listOf("biceps", "brazo"),
        ),
        LimitingFactorDefinition(
            id = "triceps",
            label = "Tríceps",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("triceps"),
            defaultJointIds = listOf("codo"),
            keywords = listOf("triceps", "brazo"),
        ),
        LimitingFactorDefinition(
            id = "deltoid",
            label = "Deltoides / hombros",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("deltoid"),
            defaultJointIds = listOf("glenohumeral"),
            keywords = listOf("hombro", "deltoides"),
        ),
        LimitingFactorDefinition(
            id = "pectoralis",
            label = "Pectorales",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("pectoralis"),
            keywords = listOf("pecho", "pectoral"),
        ),
        LimitingFactorDefinition(
            id = "latissimus_dorsi",
            label = "Dorsales",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("latissimus_dorsi"),
            keywords = listOf("dorsal"),
        ),
        LimitingFactorDefinition(
            id = "trapezius",
            label = "Trapecio",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("trapezius"),
            keywords = listOf("trapecio"),
        ),
        LimitingFactorDefinition(
            id = "rhomboids",
            label = "Romboides",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("rhomboids"),
            keywords = listOf("romboides"),
        ),
        LimitingFactorDefinition(
            id = "quadriceps",
            label = "Cuádriceps",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("quadriceps"),
            defaultJointIds = listOf("rodilla"),
            keywords = listOf("cuadriceps", "muslo"),
        ),
        LimitingFactorDefinition(
            id = "hamstrings",
            label = "Isquiosurales",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("hamstrings"),
            defaultJointIds = listOf("rodilla", "cadera"),
            keywords = listOf("isquio", "femoral"),
        ),
        LimitingFactorDefinition(
            id = "gluteus_maximus",
            label = "Glúteo mayor",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("gluteus_maximus"),
            defaultJointIds = listOf("cadera"),
            keywords = listOf("gluteo"),
        ),
        LimitingFactorDefinition(
            id = "gluteus_medius",
            label = "Glúteo medio",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("gluteus_medius"),
            defaultJointIds = listOf("cadera"),
            keywords = listOf("gluteo medio", "cadera"),
        ),
        LimitingFactorDefinition(
            id = "adductors",
            label = "Aductores",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("adductors"),
            keywords = listOf("aductor"),
        ),
        LimitingFactorDefinition(
            id = "calves",
            label = "Pantorrillas",
            category = LimitingFactorCategory.MUSCLE,
            defaultMuscleIds = listOf("calves"),
            defaultJointIds = listOf("tobillo"),
            keywords = listOf("gemelo", "pantorrilla"),
        ),
        // Articulaciones (respaldadas por AprendeOntology.wikiLabJointIds)
        LimitingFactorDefinition(
            id = "columna-lumbar",
            label = "Articulación lumbar",
            category = LimitingFactorCategory.JOINT,
            defaultMuscleIds = listOf("erector_spinae"),
            defaultJointIds = listOf("columna-lumbar"),
            keywords = listOf("lumbar", "espalda", "disco"),
        ),
        LimitingFactorDefinition(
            id = "glenohumeral",
            label = "Hombro (glenohumeral)",
            category = LimitingFactorCategory.JOINT,
            defaultMuscleIds = listOf("deltoid"),
            defaultJointIds = listOf("glenohumeral"),
            keywords = listOf("hombro", "manguito"),
        ),
        LimitingFactorDefinition(
            id = "codo",
            label = "Codo",
            category = LimitingFactorCategory.JOINT,
            defaultJointIds = listOf("codo"),
            keywords = listOf("codo", "tendon"),
        ),
        LimitingFactorDefinition(
            id = "muñeca",
            label = "Muñeca",
            category = LimitingFactorCategory.JOINT,
            defaultMuscleIds = listOf("forearm"),
            defaultJointIds = listOf("muñeca"),
            keywords = listOf("muneca", "agarre"),
        ),
        LimitingFactorDefinition(
            id = "cadera",
            label = "Cadera",
            category = LimitingFactorCategory.JOINT,
            defaultJointIds = listOf("cadera"),
            keywords = listOf("cadera", "flexor"),
        ),
        LimitingFactorDefinition(
            id = "rodilla",
            label = "Rodilla",
            category = LimitingFactorCategory.JOINT,
            defaultJointIds = listOf("rodilla"),
            keywords = listOf("rodilla", "rotula"),
        ),
        LimitingFactorDefinition(
            id = "tobillo",
            label = "Tobillo",
            category = LimitingFactorCategory.JOINT,
            defaultJointIds = listOf("tobillo"),
            keywords = listOf("tobillo", "dorsiflexion"),
        ),
        LimitingFactorDefinition(
            id = "columna-toracica",
            label = "Columna torácica",
            category = LimitingFactorCategory.JOINT,
            defaultJointIds = listOf("columna-toracica"),
            keywords = listOf("toracica", "espalda alta"),
        ),
        LimitingFactorDefinition(
            id = "columna-cervical",
            label = "Cuello / cervical",
            category = LimitingFactorCategory.JOINT,
            defaultMuscleIds = listOf("neck"),
            defaultJointIds = listOf("columna-cervical"),
            keywords = listOf("cuello", "cervical"),
        ),
        LimitingFactorDefinition(
            id = "sacroiliaca",
            label = "Zona sacroilíaca",
            category = LimitingFactorCategory.JOINT,
            defaultMuscleIds = listOf("gluteus_medius"),
            defaultJointIds = listOf("sacroiliaca"),
            keywords = listOf("sacroiliaca", "pelvis"),
        ),
    )

    val byId: Map<String, LimitingFactorDefinition> = entries.associateBy { it.id }

    fun findById(id: String): LimitingFactorDefinition? = byId[id]

    fun hasBackPrimary(primaryMuscles: Collection<String>): Boolean =
        primaryMuscles.any { raw ->
            val clean = raw.lowercase().trim()
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(raw).lowercase()
            clean in BACK_PRIMARY_IDENTIFIERS || canonical in BACK_PRIMARY_IDENTIFIERS
        }

    /**
     * Resuelve un factor limitante elegido a su [LimitingFactorReport] completo:
     * - `"grip"` incluye `"forearm"`.
     * - Espalda / lumbar cuando las primarias del ejercicio NO son de espalda incluye `"core"` (`autoLinkedCore = true`).
     */
    fun resolveReport(
        factorId: String,
        primaryMuscles: Collection<String> = emptyList(),
    ): LimitingFactorReport {
        val definition = byId[factorId]
            ?: if (factorId in AprendeOntology.wikiLabJointIds) {
                LimitingFactorDefinition(
                    id = factorId,
                    label = factorId.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    category = LimitingFactorCategory.JOINT,
                    defaultJointIds = listOf(factorId),
                )
            } else {
                LimitingFactorDefinition(
                    id = factorId,
                    label = factorId.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    category = LimitingFactorCategory.MUSCLE,
                    defaultMuscleIds = listOf(factorId),
                )
            }

        val muscles = linkedSetOf<String>().apply {
            addAll(definition.defaultMuscleIds)
            if (definition.id == "grip") add("forearm")
        }
        val shouldLinkCore = definition.id in LUMBAR_OR_BACK_IDS && !hasBackPrimary(primaryMuscles)
        if (shouldLinkCore) {
            muscles.add("core")
        }

        return LimitingFactorReport(
            id = definition.id,
            label = definition.label,
            category = definition.category,
            linkedMuscleIds = muscles.toList(),
            linkedJointIds = definition.defaultJointIds,
            autoLinkedCore = shouldLinkCore,
        )
    }

    /** Sugerencias rápidas ordenadas por relevancia para no saturar al usuario tras terminar un ejercicio. */
    fun suggestedIdsFor(primaryMuscles: Collection<String>, patternId: String? = null): List<String> {
        val primaryLower = primaryMuscles.map { VolumeCalculator.normalizeCanonicalMuscleGroup(it).lowercase() }.toSet()
        val rawLower = primaryMuscles.map { it.lowercase() }.toSet()
        val pattern = patternId?.lowercase().orEmpty()
        return buildList {
            if ("hinge" in pattern || "deadlift" in pattern || "pull" in pattern || "dorsales" in primaryLower || "isquiosurales" in primaryLower) {
                add("grip")
                add("erector_spinae")
            }
            if ("squat" in pattern || "cuádriceps" in primaryLower || "glúteos" in primaryLower || "hinge" in pattern) {
                add("espalda")
                add("bracing")
                add("rodilla")
            }
            if ("push" in pattern || "pectorales" in primaryLower || "deltoides" in primaryLower) {
                add("triceps")
                add("glenohumeral")
                add("muñeca")
            }
            if ("biceps" in rawLower || "bíceps" in primaryLower || "triceps" in rawLower || "tríceps" in primaryLower) {
                add("forearm")
                add("codo")
            }
            add("cardio")
            add("core")
        }.distinct().take(6)
    }

    fun categoryLabel(category: LimitingFactorCategory): String = when (category) {
        LimitingFactorCategory.MOVEMENT_WORK -> "Trabajo del movimiento"
        LimitingFactorCategory.MUSCLE -> "Grupo muscular"
        LimitingFactorCategory.JOINT -> "Articulación"
        LimitingFactorCategory.CARDIO -> "Capacidad cardio"
    }

    fun canonicalMuscleDisplayName(muscleId: String, emphasis: String? = null): String {
        val cleanKey = muscleId.lowercase().replace('-', '_').replace(' ', '_').trim()
        val mapped = AprendeOntology.catalogMuscleToWikiLab[cleanKey] ?: muscleId
        return when (cleanKey) {
            "espalda" -> "Dorsales"
            "forearm" -> "Antebrazo"
            "core" -> "Core"
            "abdominals", "recto_abdominal" -> "Abdomen"
            else -> VolumeCalculator.normalizeCanonicalMuscleGroup(mapped, emphasis)
        }
    }
}
