package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import java.text.Normalizer

/**
 * Mapa articulación → movilidad (Entreno v2). Las 15 articulaciones de `assets/wikilab/joints.json`
 * (las mismas que usa `jointInvolvement` en el catálogo de ejercicios) apuntan a 2–3 movimientos curados de
 * [MobilityExerciseCatalog]. Se prioriza lo que no pide material; solo se admiten banda, palo/banda, pared o un
 * apoyo cuando mejoran mucho el gesto (rotaciones con banda, «pass-through», sentadilla profunda con apoyo).
 *
 * Cada lista va en orden de preferencia: el planificador toma primero el primer movimiento de cada articulación
 * nueva y, si queda tiempo, el segundo. Un movimiento puede cubrir más de una articulación (la sentadilla profunda
 * moviliza cadera, rodilla y tobillo a la vez): [jointsOf] lo expresa y evita repetir movilidad.
 *
 * Los ids se verifican contra el catálogo real en `JointMobilityTest`: la prueba falla si un movimiento desaparece
 * o deja de corresponder a su región corporal.
 */
object JointMobility {
    const val GLENOHUMERAL = "glenohumeral"
    const val ACROMIOCLAVICULAR = "acromioclavicular"
    const val STERNOCLAVICULAR = "esternoclavicular"
    const val ELBOW = "codo"
    const val PROXIMAL_RADIOULNAR = "radiocubital-proximal"
    const val WRIST = "muñeca"
    const val CERVICAL_SPINE = "columna-cervical"
    const val THORACIC_SPINE = "columna-toracica"
    const val LUMBAR_SPINE = "columna-lumbar"
    const val SACROILIAC = "sacroiliaca"
    const val HIP = "cadera"
    const val KNEE = "rodilla"
    const val ANKLE = "tobillo"
    const val SUBTALAR = "subtalar"
    const val SCAPULOTHORACIC = "escapulotoracica"

    /** Las 15 articulaciones de `joints.json`, de más a menos relevantes para preparar una carga. */
    val allJointIds: List<String> = listOf(
        GLENOHUMERAL, HIP, KNEE, ANKLE, LUMBAR_SPINE, SCAPULOTHORACIC, THORACIC_SPINE, ELBOW, WRIST,
        CERVICAL_SPINE, SACROILIAC, PROXIMAL_RADIOULNAR, ACROMIOCLAVICULAR, STERNOCLAVICULAR, SUBTALAR,
    )

    /** Movimientos curados por articulación, en orden de preferencia (ids de [MobilityExerciseCatalog]). */
    private val curated: Map<String, List<String>> = linkedMapOf(
        // Hombro: rotación externa con banda, «pass-through» con palo o banda y deslizamientos en pared.
        GLENOHUMERAL to listOf("mob_shoulder_band_rotation", "mob_stick_dislocates", "mob_wall_slides"),
        // Escápula: círculos escapulares en cuatro apoyos, alcance del serrato y ángeles de pared.
        SCAPULOTHORACIC to listOf("mob_scapular_circles_quadruped", "mob_serratus_wall_reach", "mob_wall_angels"),
        ACROMIOCLAVICULAR to listOf("mob_crossbody_stretch", "mob_scapular_elevation_depression"),
        STERNOCLAVICULAR to listOf("mob_scapular_elevation_depression", "mob_scapular_protraction_retraction"),
        ELBOW to listOf("mob_elbow_flexion_extension", "mob_elbow_car"),
        PROXIMAL_RADIOULNAR to listOf("mob_forearm_supination", "mob_elbow_car"),
        WRIST to listOf("mob_wrist_circles", "mob_wrist_mobilization", "mob_quadruped_wrist_rock_back"),
        CERVICAL_SPINE to listOf("mob_neck_retraction", "mob_neck_rotation_seated", "mob_neck_flexion_extension_nods"),
        // Columna torácica: apertura en cuadrupedia (rotación con mano en la nuca), libro abierto y aguja.
        THORACIC_SPINE to listOf("mob_quadruped_thoracic_rotation", "mob_open_book", "mob_thread_needle"),
        LUMBAR_SPINE to listOf("mob_cat_cow", "mob_pelvic_tilts", "mob_quadruped_rockback"),
        SACROILIAC to listOf("mob_pelvic_clock", "mob_pelvic_tilts"),
        // Cadera: sentadilla profunda con apoyo (cadera, rodilla y tobillo), 90/90 y puente articulado.
        HIP to listOf("mob_supported_deep_squat", "mob_90_90_hip", "mob_bridge_articulation"),
        KNEE to listOf("mob_knee_rockback", "mob_knee_car", "mob_heel_slides"),
        // Tobillo: rodilla a la pared, con desvío hacia el segundo dedo y estiramiento de gemelo en pared.
        ANKLE to listOf("mob_ankle_dorsiflexion_wall", "mob_ankle_knee_to_wall_lateral", "mob_calf_stretch_wall"),
        SUBTALAR to listOf("mob_ankle_inversion_eversion", "mob_foot_arch_shift", "mob_ankle_circles"),
    )

    /** Término articular del catálogo de movilidad → id de `joints.json` (las articulaciones del pie y la mano no se cuentan). */
    private val jointByCatalogTerm: Map<String, String> = mapOf(
        "glenohumeral" to GLENOHUMERAL,
        "hombro" to GLENOHUMERAL,
        "escapulotorácica" to SCAPULOTHORACIC,
        "torácica" to THORACIC_SPINE,
        "cervical" to CERVICAL_SPINE,
        "lumbar" to LUMBAR_SPINE,
        "sacroilíaca" to SACROILIAC,
        "coxofemoral" to HIP,
        "pelvis" to HIP,
        "rodilla" to KNEE,
        "tibiofemoral" to KNEE,
        "femoropatelar" to KNEE,
        "tobillo" to ANKLE,
        "subastragalina" to SUBTALAR,
        "codo" to ELBOW,
        "radiocubital" to PROXIMAL_RADIOULNAR,
        "muñeca" to WRIST,
    ).mapKeys { (term, _) -> nfc(term) }

    private val catalogById: Map<String, MobilityExercise> by lazy {
        MobilityExerciseCatalog.getAllMobilityExercises().associateBy { it.id }
    }

    data class Recommendation(
        val movement: MobilityExercise,
        val score: Int,
        val matchedPrimaryJoints: Set<String>,
        val matchedSecondaryJoints: Set<String>,
        val matchedMuscles: Set<String>,
    ) {
        val reason: String
            get() = when {
                matchedPrimaryJoints.isNotEmpty() -> "Articulación principal: ${matchedPrimaryJoints.joinToString { jointLabel(it) }}"
                matchedSecondaryJoints.isNotEmpty() -> "Articulación secundaria: ${matchedSecondaryJoints.joinToString { jointLabel(it) }}"
                else -> "Músculos implicados: ${matchedMuscles.joinToString()}"
            }
    }

    /** Recommend preparation movements from exact exercise metadata; unknown metadata yields no guess. */
    fun recommendationsFor(
        primaryJointIds: List<String>,
        secondaryJointIds: List<String>,
        primaryMuscleIds: List<String>,
        secondaryMuscleIds: List<String>,
        excludedMovementIds: Set<String> = emptySet(),
        limit: Int = 4,
    ): List<Recommendation> {
        val primary = primaryJointIds.mapNotNull(::canonicalJointId).toSet()
        val secondary = secondaryJointIds.mapNotNull(::canonicalJointId).toSet() - primary
        val targetMuscles = (primaryMuscleIds + secondaryMuscleIds).flatMap(::muscleAliases).toSet()
        if (primary.isEmpty() && secondary.isEmpty() && targetMuscles.isEmpty()) return emptyList()
        return catalogById.values.asSequence()
            .filterNot { it.id in excludedMovementIds }
            .mapNotNull { movement ->
                val covered = jointsOf(movement.id)
                val matchedPrimary = covered intersect primary
                val matchedSecondary = covered intersect secondary
                val muscleMatches = movement.targetMuscles
                    .filter { muscle -> muscleAliases(muscle).any { it in targetMuscles } }
                    .toSet()
                val score = matchedPrimary.size * 100 + matchedSecondary.size * 30 + muscleMatches.size * 5
                if (score == 0) null else Recommendation(movement, score, matchedPrimary, matchedSecondary, muscleMatches)
            }
            .sortedWith(compareByDescending<Recommendation> { it.score }.thenBy { it.movement.name })
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private val jointsByMovement: Map<String, Set<String>> by lazy {
        val result = HashMap<String, MutableSet<String>>()
        curated.forEach { (joint, ids) -> ids.forEach { id -> result.getOrPut(id) { linkedSetOf() } += joint } }
        catalogById.forEach { (id, movement) ->
            movement.joints.forEach { term ->
                jointByCatalogTerm[nfc(term)]?.let { joint -> result.getOrPut(id) { linkedSetOf() } += joint }
            }
        }
        result
    }

    /** Ids de movimiento curados para [jointId] (vacío si la articulación no existe). */
    fun movementIdsFor(jointId: String): List<String> = curated[jointId].orEmpty()

    /** Movimientos curados de [jointId] ya resueltos contra el catálogo de movilidad. */
    fun movementsFor(jointId: String): List<MobilityExercise> =
        movementIdsFor(jointId).mapNotNull { catalogById[it] }

    /** El movimiento del catálogo con ese id, si existe. */
    fun movement(id: String): MobilityExercise? = catalogById[id]

    fun canonicalJointId(value: String): String? {
        val normalized = nfc(value.trim().lowercase())
        return normalized.takeIf { it in allJointIds } ?: jointByCatalogTerm[normalized]
    }

    private val muscleAliasesByCatalogId = mapOf(
        "latissimus_dorsi" to setOf("dorsal ancho", "dorsales", "dorsal"),
        "trapezius" to setOf("trapecio", "trapecio superior"),
        "rhomboids" to setOf("romboides"),
        "deltoid" to setOf("deltoides", "deltoide"),
        "pectoralis" to setOf("pectoral mayor", "pectoral", "pecho"),
        "biceps" to setOf("bíceps", "biceps"),
        "triceps" to setOf("tríceps", "triceps"),
        "forearm" to setOf("antebrazo", "flexores de dedos"),
        "gluteus_maximus" to setOf("glúteo mayor", "glúteos", "glúteo"),
        "gluteus_medius" to setOf("glúteo medio"),
        "hamstrings" to setOf("isquiosurales", "isquios", "isquiotibiales"),
        "quadriceps" to setOf("cuádriceps", "recto femoral"),
        "adductors" to setOf("aductores"),
        "calves" to setOf("gemelos", "sóleo", "pantorrilla"),
        "tibialis_anterior" to setOf("tibial anterior"),
        "erector_spinae" to setOf("erectores espinales", "extensores torácicos"),
        "hip_flexors" to setOf("flexores de cadera", "psoas", "recto femoral"),
        "neck" to setOf("cervicales", "cuello"),
        "abdominals" to setOf("abdominales", "oblicuos"),
        "core" to setOf("core", "abdominales"),
    )

    private fun muscleAliases(value: String): Set<String> {
        val raw = value.trim()
        if (raw.isBlank()) return emptySet()
        val aliases = muscleAliasesByCatalogId[raw.lowercase()] ?: setOf(raw)
        return aliases.map(::normalizeTerm).filter { it.isNotBlank() }.toSet()
    }

    private fun jointLabel(id: String): String = when (id) {
        GLENOHUMERAL -> "hombro"
        SCAPULOTHORACIC -> "escápula"
        THORACIC_SPINE -> "columna torácica"
        LUMBAR_SPINE -> "columna lumbar"
        CERVICAL_SPINE -> "columna cervical"
        PROXIMAL_RADIOULNAR -> "antebrazo"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    private fun normalizeTerm(value: String): String =
        Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()

    /** Articulaciones (ids de `joints.json`) que moviliza el movimiento [movementId]; vacío si no se conoce. */
    fun jointsOf(movementId: String): Set<String> = jointsByMovement[movementId].orEmpty()

    /** Articulaciones cubiertas por las movilidades ya prescritas a un ejercicio (las que no se conocen no cuentan). */
    fun jointsCoveredBy(series: List<MobilitySeries>): Set<String> {
        if (series.isEmpty()) return emptySet()
        val covered = linkedSetOf<String>()
        series.forEach { item ->
            val key = item.catalogConfigurationId?.takeIf { it.isNotBlank() }
                ?: item.exerciseDbId?.takeIf { it.isNotBlank() }
                ?: item.id
            covered += jointsOf(key)
        }
        return covered
    }

    /**
     * La movilidad lista para colgar de un ejercicio: una serie temporizada con la misma forma que crea el editor
     * (`Exercise.mobilitySeries`), con el id del catálogo como identidad (así dos pasadas del planificador producen
     * exactamente lo mismo y la sesión en vivo la reconoce).
     */
    fun seriesFor(movement: MobilityExercise, ownerName: String, seconds: Int): MobilitySeries = MobilitySeries(
        id = movement.id,
        exerciseDbId = movement.id,
        name = movement.name,
        sets = 1,
        durationSeconds = seconds,
        notes = "Movilidad asociada a $ownerName",
        associatedDiscomforts = movement.discomfortIds,
        bodyZones = listOf(movement.bodyRegion),
        movementPatterns = listOf(movement.category),
        unit = MobilityUnit.SECONDS,
        catalogConfigurationId = movement.id,
    )

    private fun nfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
}
