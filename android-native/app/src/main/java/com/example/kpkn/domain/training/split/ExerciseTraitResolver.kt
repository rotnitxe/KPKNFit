package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
import com.example.kpkn.domain.training.CompositionTaxonomy
import com.example.kpkn.domain.training.PatternFamily
import com.example.kpkn.domain.training.VolumeCalculator

/**
 * Clasifica un ejercicio para el redistribuidor de repartos: devuelve sus [ExerciseTraits] (músculos, patrón, región,
 * articulaciones, compuesto o aislamiento) o `null` si no se puede saber nada (un ejercicio propio sin músculos ni un
 * nombre reconocible). Quien llama decide qué hace con los `null`: el redistribuidor los coloca en el día más corto y lo
 * avisa.
 */
fun interface ExerciseTraitResolver {
    fun traitsOf(exercise: Exercise): ExerciseTraits?
}

/**
 * Resolutor por defecto, sobre el catálogo de ejercicios v2. Busca el ejercicio por su identidad de catálogo
 * (configuración, definición) y, si no la trae, por su nombre; si tampoco hay nombre conocido usa los músculos que el
 * propio ejercicio declara y, como último recurso, palabras del nombre ([ExerciseNameTraits]).
 *
 * Es inmutable tras construirse: se puede compartir entre llamadas y hilos.
 */
class CatalogExerciseTraitResolver(catalog: ExerciseCatalogV2) : ExerciseTraitResolver {

    private class Entry(val definition: ExerciseDefinitionV2, val configuration: ExerciseConfigurationV2) {
        val traits: ExerciseTraits by lazy(LazyThreadSafetyMode.PUBLICATION) {
            CatalogTraits.of(configuration, definition.id)
        }
    }

    private val byConfiguration: Map<String, Entry>
    private val byDefinition: Map<String, Entry>
    private val byName: Map<String, Entry>

    init {
        val configurations = LinkedHashMap<String, Entry>()
        val definitions = LinkedHashMap<String, Entry>()
        val canonicalNames = LinkedHashMap<String, Entry>()
        val searchNames = LinkedHashMap<String, Entry>()
        catalog.families.forEach { family ->
            family.definitions.forEach { definition ->
                val defaultConfiguration = defaultConfigurationOf(definition)
                definition.configurations.forEach { configuration ->
                    configurations.putIfAbsent(configuration.id.trim().lowercase(), Entry(definition, configuration))
                }
                if (defaultConfiguration != null) {
                    val entry = configurations.getValue(defaultConfiguration.id.trim().lowercase())
                    definitions.putIfAbsent(definition.id.trim().lowercase(), entry)
                    canonicalNames.putIfAbsent(SplitText.normalize(definition.canonicalName), entry)
                    definition.searchTerms.forEach { term -> searchNames.putIfAbsent(SplitText.normalize(term), entry) }
                }
            }
        }
        byConfiguration = configurations
        byDefinition = definitions
        // El nombre canónico de una definición manda sobre el término de búsqueda de otra.
        byName = searchNames + canonicalNames
    }

    override fun traitsOf(exercise: Exercise): ExerciseTraits? {
        val configurationKeys = listOfNotNull(
            exercise.catalogConfigurationId,
            exercise.exerciseDbId,
            exercise.exerciseId,
            exercise.canonicalExerciseId,
        ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        configurationKeys.firstNotNullOfOrNull { byConfiguration[it] }?.let { return it.traits }

        val definitionKeys = (configurationKeys + listOfNotNull(exercise.catalogDefinitionId?.trim()?.lowercase()))
            .filter { it.isNotEmpty() }
            .distinct()
        definitionKeys.firstNotNullOfOrNull { byDefinition[it] }?.let { return it.traits }

        val normalizedName = SplitText.normalize(exercise.name)
        if (normalizedName.isNotEmpty()) byName[normalizedName]?.let { return it.traits }
        return ExerciseNameTraits.of(exercise)
    }

    private fun defaultConfigurationOf(definition: ExerciseDefinitionV2): ExerciseConfigurationV2? =
        definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
            ?: definition.configurations.firstOrNull()
}

/** Rasgos de una configuración del catálogo v2. Puro: los tests lo ejercitan sobre las 521 configuraciones. */
internal object CatalogTraits {

    /** Equipos con carga externa graduable: un compuesto con ellos puede ser «pesado» (el resto, no). */
    private val LOADABLE_EQUIPMENT = setOf(
        "barbell", "dumbbells", "machine", "cable", "smith_machine", "kettlebell",
        "ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar", "plate",
    )

    fun of(configuration: ExerciseConfigurationV2, definitionId: String? = null): ExerciseTraits {
        val profile = configuration.profile
        val pattern = CompositionTaxonomy.familyOf(profile.movementPatternId)
        val muscles = LinkedHashMap<KpknMuscleGroup, Double>()
        profile.primaryMuscles.forEach { id ->
            atomOf(id, profile.movementPatternId, configuration.id)?.let { atom ->
                muscles.merge(atom, ExerciseTraits.PRIMARY) { a, b -> maxOf(a, b) }
            }
        }
        profile.secondaryMuscles.forEach { id ->
            atomOf(id, profile.movementPatternId, configuration.id)?.let { atom ->
                muscles.merge(atom, ExerciseTraits.SECONDARY) { a, b -> maxOf(a, b) }
            }
        }
        val isolation = CompositionTaxonomy.isIsolation(pattern, profile.articulationType?.name, configuration.id)
        val isCompound = !isolation
        return ExerciseTraits(
            pattern = pattern,
            movementPatternId = profile.movementPatternId,
            muscles = muscles,
            region = SplitMuscles.regionOf(muscles),
            chain = when (profile.kineticChain) {
                ExerciseKineticChainV2.ANTERIOR -> KineticChain.ANTERIOR
                ExerciseKineticChainV2.POSTERIOR -> KineticChain.POSTERIOR
                ExerciseKineticChainV2.FULL -> KineticChain.FULL
            },
            joints = profile.jointInvolvement
                .filter { it.role != JointRoleV2.STABILIZER }
                .mapTo(linkedSetOf<String>()) { it.jointId },
            isCompound = isCompound,
            canBeHeavy = isCompound && profile.equipmentId in LOADABLE_EQUIPMENT,
            axialLoad = profile.axialLoadFactor,
            equipmentId = profile.equipmentId,
            definitionId = definitionId,
            source = TraitSource.CATALOG,
        )
    }

    /** Músculo del catálogo (ids en inglés) → músculo canónico de la app. El deltoides se reparte por cabeza. */
    fun atomOf(muscleId: String, movementPatternId: String, configurationId: String): KpknMuscleGroup? = when (muscleId) {
        "pectoralis" -> KpknMuscleGroup.CHEST
        "latissimus_dorsi" -> KpknMuscleGroup.BACK_LATS
        "trapezius", "rhomboids" -> KpknMuscleGroup.BACK_UPPER
        "quadriceps" -> KpknMuscleGroup.QUADS
        "hamstrings" -> KpknMuscleGroup.HAMS
        "gluteus_maximus", "gluteus_medius", "tensor_fasciae_latae" -> KpknMuscleGroup.GLUTES
        "erector_spinae" -> KpknMuscleGroup.ERECTORS
        "deltoid" -> deltoidHead(movementPatternId, configurationId)
        "biceps" -> KpknMuscleGroup.BICEPS
        "triceps" -> KpknMuscleGroup.TRICEPS
        "calves", "tibialis_anterior" -> KpknMuscleGroup.CALVES
        "core", "abdominals", "hip_flexors" -> KpknMuscleGroup.CORE
        "forearm" -> KpknMuscleGroup.FOREARMS
        "neck" -> KpknMuscleGroup.NECK
        "adductors" -> KpknMuscleGroup.ADDUCTORS
        else -> null
    }

    private fun deltoidHead(movementPatternId: String, configurationId: String): KpknMuscleGroup {
        val id = configurationId.lowercase()
        if (id.contains("face_pull") || id.contains("rear_delt") || id.contains("pull_apart") ||
            id.contains("reverse_fly") || id.contains("posterior")
        ) {
            return KpknMuscleGroup.DELT_REAR
        }
        return when (CompositionTaxonomy.familyOf(movementPatternId)) {
            PatternFamily.HORIZONTAL_PUSH,
            PatternFamily.VERTICAL_PUSH,
            PatternFamily.SHOULDER_FLEXION -> KpknMuscleGroup.DELT_FRONT
            PatternFamily.HORIZONTAL_PULL,
            PatternFamily.VERTICAL_PULL -> KpknMuscleGroup.DELT_REAR
            else -> KpknMuscleGroup.DELT_LATERAL
        }
    }
}

/**
 * Último recurso para un ejercicio que el catálogo no conoce (un ejercicio propio, un plan viejo): primero los músculos
 * que el ejercicio declara (`Exercise.effectiveMuscles`) y, si no los trae, palabras de su nombre en español o inglés.
 * Los rasgos que salen de aquí llevan `source != CATALOG`, para que el informe pueda decir cuántos se dedujeron.
 */
internal object ExerciseNameTraits {

    private class Rule(
        val any: List<String>,
        val pattern: PatternFamily,
        val primary: List<KpknMuscleGroup>,
        val secondary: List<KpknMuscleGroup> = emptyList(),
        val compound: Boolean,
        val none: List<String> = emptyList(),
    )

    private val C = KpknMuscleGroup.CHEST
    private val LATS = KpknMuscleGroup.BACK_LATS
    private val UPPER = KpknMuscleGroup.BACK_UPPER
    private val QUADS = KpknMuscleGroup.QUADS
    private val HAMS = KpknMuscleGroup.HAMS
    private val GLUTES = KpknMuscleGroup.GLUTES
    private val ERECTORS = KpknMuscleGroup.ERECTORS
    private val FRONT = KpknMuscleGroup.DELT_FRONT
    private val SIDE = KpknMuscleGroup.DELT_LATERAL
    private val REAR = KpknMuscleGroup.DELT_REAR
    private val BICEPS = KpknMuscleGroup.BICEPS
    private val TRICEPS = KpknMuscleGroup.TRICEPS
    private val CALVES = KpknMuscleGroup.CALVES
    private val CORE = KpknMuscleGroup.CORE
    private val FOREARMS = KpknMuscleGroup.FOREARMS
    private val NECK = KpknMuscleGroup.NECK
    private val ADDUCTORS = KpknMuscleGroup.ADDUCTORS

    /** El orden importa: las reglas más específicas van antes («curl femoral» antes que «curl», «remo» antes que «banca»). */
    private val RULES: List<Rule> = listOf(
        Rule(
            listOf("peso muerto rumano", "rumano", "romanian", "rdl", "stiff", "buenos dias", "good morning"),
            PatternFamily.HINGE, listOf(HAMS, GLUTES), listOf(ERECTORS, ADDUCTORS), compound = true,
        ),
        Rule(
            listOf("peso muerto", "deadlift"),
            PatternFamily.HINGE, listOf(GLUTES, HAMS), listOf(ERECTORS, QUADS, UPPER), compound = true,
        ),
        Rule(
            listOf("hip thrust", "empuje de cadera", "puente de gluteo", "puente gluteo", "glute bridge", "patada de gluteo"),
            PatternFamily.HIP_EXTENSION, listOf(GLUTES), listOf(HAMS), compound = true,
        ),
        Rule(
            listOf("curl femoral", "leg curl", "curl de pierna", "curl de isquio", "curl nordico", "nordic"),
            PatternFamily.KNEE_FLEXION, listOf(HAMS), listOf(CALVES), compound = false,
        ),
        Rule(
            listOf("extension de cuadriceps", "leg extension", "extension de piernas", "sillon de cuadriceps"),
            PatternFamily.KNEE_EXTENSION, listOf(QUADS), compound = false,
        ),
        Rule(
            listOf("pantorrilla", "gemelo", "calf", "soleo"),
            PatternFamily.CALF, listOf(CALVES), compound = false,
        ),
        Rule(
            listOf("sentadilla", "squat", "prensa", "leg press", "hack"),
            PatternFamily.SQUAT, listOf(QUADS, GLUTES), listOf(ADDUCTORS), compound = true,
        ),
        Rule(
            listOf("zancada", "lunge", "bulgara", "desplante", "step up", "subida al cajon"),
            PatternFamily.SQUAT, listOf(QUADS, GLUTES), listOf(HAMS, CALVES), compound = true,
        ),
        Rule(
            listOf("pajaros", "rear delt", "deltoide posterior", "deltoides posterior", "vuelo posterior", "face pull", "facepull", "reverse fly", "aperturas posteriores"),
            PatternFamily.HORIZONTAL_PULL, listOf(REAR), listOf(UPPER), compound = false,
        ),
        Rule(
            listOf("elevacion lateral", "elevaciones laterales", "lateral raise", "vuelos laterales", "abduccion de hombro"),
            PatternFamily.SHOULDER_ABDUCTION, listOf(SIDE), listOf(UPPER), compound = false,
        ),
        Rule(
            listOf("remo al menton", "upright row"),
            PatternFamily.SHOULDER_ABDUCTION, listOf(SIDE, UPPER), listOf(BICEPS), compound = false,
        ),
        Rule(
            listOf("triceps", "press frances", "skull", "extension sobre la cabeza", "patada de triceps", "pushdown", "jm press"),
            PatternFamily.ELBOW_EXTENSION, listOf(TRICEPS), compound = false,
        ),
        Rule(
            listOf("curl", "martillo", "hammer", "predicador", "preacher", "biceps"),
            PatternFamily.ELBOW_FLEXION, listOf(BICEPS), listOf(FOREARMS), compound = false,
        ),
        Rule(
            listOf("encogimiento", "shrug"),
            PatternFamily.SHRUG, listOf(UPPER), compound = false,
        ),
        Rule(
            listOf(
                "abdominal", "crunch", "plancha", "plank", "elevacion de piernas", "rueda abdominal", "pallof",
                "russian twist", "giro ruso", "core", "hollow", "dead bug", "toes to bar",
            ),
            PatternFamily.CORE, listOf(CORE), compound = false,
        ),
        Rule(
            listOf("antebrazo", "forearm", "muneca", "wrist", "farmer", "granjero", "pinza", "gripper"),
            PatternFamily.GRIP, listOf(FOREARMS), compound = false,
        ),
        Rule(
            listOf("cuello", "neck"),
            PatternFamily.NECK, listOf(NECK), compound = false,
        ),
        Rule(
            listOf("remo", "row"),
            PatternFamily.HORIZONTAL_PULL, listOf(LATS, UPPER), listOf(BICEPS, REAR), compound = true,
            none = listOf("rowing", "ergometro", "concept"),
        ),
        Rule(
            listOf("jalon", "pulldown", "pull down", "dominada", "pull up", "pullup", "chin up", "chinup", "barbilla"),
            PatternFamily.VERTICAL_PULL, listOf(LATS), listOf(BICEPS, UPPER), compound = true,
        ),
        Rule(
            listOf("pullover"),
            PatternFamily.VERTICAL_PULL, listOf(LATS), listOf(C), compound = false,
        ),
        Rule(
            listOf("aperturas", "apertura", "fly", "flyes", "cruce de poleas", "crossover", "cross over", "pec deck", "contractora"),
            PatternFamily.HORIZONTAL_PUSH, listOf(C), listOf(FRONT), compound = false,
        ),
        Rule(
            listOf(
                "press militar", "overhead press", "press de hombro", "press hombro", "shoulder press",
                "press arnold", "arnold press", "push press", "press sobre la cabeza", "ohp",
            ),
            PatternFamily.VERTICAL_PUSH, listOf(FRONT), listOf(TRICEPS, SIDE), compound = true,
        ),
        Rule(
            listOf(
                "press banca", "press de banca", "bench press", "banca", "press plano", "press inclinado",
                "press declinado", "incline press", "chest press", "press de pecho", "flexiones", "push up",
                "pushup", "fondos", "dips",
            ),
            PatternFamily.HORIZONTAL_PUSH, listOf(C), listOf(TRICEPS, FRONT), compound = true,
        ),
    )

    fun of(exercise: Exercise): ExerciseTraits? = fromDeclaredMuscles(exercise) ?: fromName(exercise.name)

    private fun fromName(name: String): ExerciseTraits? {
        val normalized = SplitText.normalize(name)
        if (normalized.isEmpty()) return null
        // Las palabras clave empiezan en el límite de una palabra («row» no casa con «narrow»; «curl» sí con «curls»).
        val padded = " $normalized"
        val rule = RULES.firstOrNull { rule ->
            rule.any.any { padded.contains(" $it") } && rule.none.none { padded.contains(" $it") }
        } ?: return null
        val muscles = LinkedHashMap<KpknMuscleGroup, Double>()
        rule.primary.forEach { muscles.merge(it, ExerciseTraits.PRIMARY) { a, b -> maxOf(a, b) } }
        rule.secondary.forEach { muscles.merge(it, ExerciseTraits.SECONDARY) { a, b -> maxOf(a, b) } }
        return ExerciseTraits(
            pattern = rule.pattern,
            movementPatternId = null,
            muscles = muscles,
            region = SplitMuscles.regionOf(muscles),
            chain = KineticChain.FULL,
            isCompound = rule.compound,
            canBeHeavy = rule.compound,
            source = TraitSource.NAME,
        )
    }

    private fun fromDeclaredMuscles(exercise: Exercise): ExerciseTraits? {
        val involved = exercise.effectiveMuscles.orEmpty()
        if (involved.isEmpty()) return null
        val muscles = LinkedHashMap<KpknMuscleGroup, Double>()
        involved.forEach { entry ->
            val weight = when (entry.role) {
                MuscleRole.PRIMARY -> ExerciseTraits.PRIMARY
                MuscleRole.SECONDARY -> ExerciseTraits.SECONDARY
                else -> return@forEach
            }
            val atom = atomOfCanonical(entry.muscle, entry.emphasis) ?: return@forEach
            muscles.merge(atom, weight) { a, b -> maxOf(a, b) }
        }
        if (muscles.isEmpty()) return null
        val isCompound = muscles.size >= 3
        return ExerciseTraits(
            pattern = null,
            movementPatternId = null,
            muscles = muscles,
            region = SplitMuscles.regionOf(muscles),
            chain = KineticChain.FULL,
            isCompound = isCompound,
            canBeHeavy = isCompound,
            source = TraitSource.EXERCISE_MUSCLES,
        )
    }

    /** Músculo en español (como lo escribe el editor o el catálogo antiguo) → músculo canónico. */
    private fun atomOfCanonical(muscle: String, emphasis: String?): KpknMuscleGroup? =
        when (VolumeCalculator.normalizeCanonicalMuscleGroup(muscle, emphasis)) {
            "Pectorales" -> KpknMuscleGroup.CHEST
            "Dorsales" -> KpknMuscleGroup.BACK_LATS
            "Trapecio", "Romboides" -> KpknMuscleGroup.BACK_UPPER
            "Deltoides" -> {
                val head = SplitText.normalize(emphasis.orEmpty())
                when {
                    head.contains("anterior") || head.contains("frontal") -> KpknMuscleGroup.DELT_FRONT
                    head.contains("posterior") -> KpknMuscleGroup.DELT_REAR
                    else -> KpknMuscleGroup.DELT_LATERAL
                }
            }
            "Bíceps" -> KpknMuscleGroup.BICEPS
            "Tríceps" -> KpknMuscleGroup.TRICEPS
            "Antebrazo" -> KpknMuscleGroup.FOREARMS
            "Erectores Espinales" -> KpknMuscleGroup.ERECTORS
            "Core", "Abdomen" -> KpknMuscleGroup.CORE
            "Glúteos" -> KpknMuscleGroup.GLUTES
            "Aductores" -> KpknMuscleGroup.ADDUCTORS
            "Cuádriceps" -> KpknMuscleGroup.QUADS
            "Isquiosurales" -> KpknMuscleGroup.HAMS
            "Pantorrillas", "Tibial Anterior" -> KpknMuscleGroup.CALVES
            "Cuello" -> KpknMuscleGroup.NECK
            else -> null
        }
}
