package com.example.kpkn.domain.biomechanics.profiles

/** Resultado de [AnalysisProfileResolver.resolve]. */
sealed interface ProfileResolution {
    /**
     * Se analiza con [profile]. [source] dice de dónde salió: `"user"` (elección del usuario), `"definition"` (excepción
     * por definición) o `"pattern"` (tabla por patrón). `"name"` existe en el contrato pero no se emite: el nombre
     * nunca resuelve solo.
     */
    data class Supported(val profile: AnalysisProfile, val source: String) : ProfileResolution

    /** No se analiza en v1. [reason] es un código estable de [UnsupportedReasons]; [text] es su texto para la UI. */
    data class Unsupported(val reason: String) : ProfileResolution {
        val text: String get() = UnsupportedReasons.textOf(reason)
    }

    /** Ejercicio propio (o sin patrón) sin perfil elegido: hay que preguntar. [guess] es la sugerencia, si la hay. */
    data class NeedsUserChoice(val guess: AnalysisProfileId?) : ProfileResolution
}

/**
 * Motivos de [ProfileResolution.Unsupported]: un código estable (el de `reason`) y un texto en español para la UI
 * (tipo frase, sin punto final). [NOT_YET] es para movimientos conocidos que v1 deja fuera sin un motivo propio;
 * [UNKNOWN_PATTERN] es solo para un patrón que no está en la tabla (el catálogo creció): la prueba de cobertura falla
 * si algún ejercicio del catálogo lo recibe.
 */
object UnsupportedReasons {
    const val OLYMPIC = "olympic"
    const val ISOMETRIC = "isometric"
    const val CORE = "core"
    const val GRIP = "grip"
    const val NECK = "neck"
    const val MACHINE_PLANE = "machine_plane"
    const val WALKING = "walking"
    const val ECCENTRIC_ONLY_V2 = "eccentric_only_v2"
    const val NOT_YET = "not_yet"
    const val UNKNOWN_PATTERN = "unknown_pattern"

    /** Código → texto para la UI. */
    val texts: Map<String, String> = linkedMapOf(
        OLYMPIC to "Los levantamientos olímpicos aún no se analizan",
        ISOMETRIC to "Los ejercicios isométricos aún no se analizan",
        CORE to "Los ejercicios de core aún no se analizan",
        GRIP to "Los ejercicios de agarre y antebrazo aún no se analizan",
        NECK to "Los ejercicios de cuello aún no se analizan",
        MACHINE_PLANE to "Este movimiento no se ve bien de lado",
        WALKING to "Graba sin desplazarte para poder contar",
        ECCENTRIC_ONLY_V2 to "Los ejercicios solo excéntricos aún no se analizan",
        NOT_YET to "Este ejercicio aún no se analiza",
        UNKNOWN_PATTERN to "Este tipo de ejercicio aún no se analiza",
    )

    /** Texto de [code]; un código desconocido da el de [UNKNOWN_PATTERN]. */
    fun textOf(code: String): String = texts[code] ?: texts.getValue(UNKNOWN_PATTERN)
}

/**
 * Del ejercicio al perfil de análisis (§5, «Resolver»). Orden:
 * 1. `userProfileOverride`;
 * 2. excepción por `definitionId`;
 * 3. tabla por `movementPatternId`;
 * 4. si el ejercicio es propio o no tiene patrón: [ProfileResolution.NeedsUserChoice] con la conjetura.
 *
 * El nombre nunca decide, y este resolver no importa nada fuera de `domain/biomechanics`: la conjetura de un ejercicio
 * propio llega en [ExerciseRef.guessedMovementPatternId] y se pasa por la misma tabla. Un ejercicio del catálogo con un
 * patrón que no está en la tabla da `Unsupported("unknown_pattern")`.
 */
object AnalysisProfileResolver {
    private const val SOURCE_USER = "user"
    private const val SOURCE_DEFINITION = "definition"
    private const val SOURCE_PATTERN = "pattern"

    /** Marca del id de configuración de los ejercicios sentados (`calf_raise__bilateral__seated_machine`). */
    private const val SEATED = "seated"

    /** Material con el que el disco puede ser la señal principal (§5, «Disco como señal principal»). */
    private val PLATE_EQUIPMENT = setOf("barbell", "smith_machine", "hex_bar", "safety_bar", "t_bar")

    /** Perfiles cuyo encuadre exige el disco en cuadro cuando se usa (SQUAT, DEADLIFT, BENCH y OHP). */
    private val PLATE_FRAMING_PROFILES = setOf(
        AnalysisProfileId.SQUAT, AnalysisProfileId.DEADLIFT, AnalysisProfileId.BENCH, AnalysisProfileId.OHP,
    )

    /** Press horizontales en máquina, polea o banda: se empuja desde el pecho, así que arrancan en la concéntrica (§5). */
    private val CONCENTRIC_BENCH_EQUIPMENT = setOf("machine", "cable", "band")

    /** Patrones que `ExercisePatternDetector` da y el catálogo no tiene: solo valen para la conjetura. */
    private val GUESS_ALIASES = mapOf("shoulder_abduction" to AnalysisProfileId.RAISE)

    /** Tabla por patrón de movimiento: los 63 patrones del catálogo (comprobado el 13-oct-2026). */
    private val PATTERN_RULES: Map<String, Rule> = ruleTable {
        use(AnalysisProfileId.SQUAT, "knee_dominant", "knee_hip_dominant")
        use(AnalysisProfileId.MACHINE_SQUAT, "knee_dominant_lengthened")
        use(AnalysisProfileId.LUNGE, "unilateral_knee_dominant", "unilateral_hip_dominant")
        use(AnalysisProfileId.DEADLIFT, "deadlift", "hip_hinge_deficit")
        use(AnalysisProfileId.HINGE, "romanian_deadlift", "romanian_deadlift_deficit", "hip_hinge", "hip_hinge_lengthened")
        use(AnalysisProfileId.HIP_THRUST, "hip_extension", "hip_extension_external_rotation")
        use(AnalysisProfileId.BACK_EXTENSION, "spinal_extension")
        use(AnalysisProfileId.BENCH, "horizontal_push")
        use(AnalysisProfileId.OHP, "vertical_push")
        use(AnalysisProfileId.PULL_VERTICAL, "vertical_pull", "vertical_pull_abduction")
        use(AnalysisProfileId.ROW, "horizontal_pull")
        use(AnalysisProfileId.CURL, "elbow_flexion")
        use(AnalysisProfileId.TRICEPS_EXTENDED_REST, "elbow_extension")
        use(AnalysisProfileId.RAISE, "shoulder_abduction_full_rom", "shoulder_abduction_diagonal", "shoulder_flexion")
        use(AnalysisProfileId.LEG_EXTENSION, "knee_extension")
        use(AnalysisProfileId.LEG_CURL, "knee_flexion")
        use(AnalysisProfileId.CALF, "plantar_flexion")
        use(AnalysisProfileId.SHRUG, "scapular_elevation")

        skip(UnsupportedReasons.OLYMPIC, "hip_hinge_explosive")
        skip(
            UnsupportedReasons.CORE,
            "anti_extension_isometric", "anti_extension_pelvic_control", "anti_extension_trunk", "anti_rotation_trunk",
            "hip_flexion", "lateral_trunk_flexion", "trunk_flexion", "trunk_rotation", "spinal_flexion",
        )
        skip(
            UnsupportedReasons.GRIP,
            "isometric_grip", "pinch_grip", "wrist_flexion", "wrist_extension", "wrist_flexion_extension",
        )
        skip(UnsupportedReasons.NECK, "neck_extension", "neck_flexion", "neck_lateral_flexion")
        // Planos que de lado no se ven (frontal o transversal): abductores, aductores, cosaco, Jefferson, Kelso, pec deck.
        skip(
            UnsupportedReasons.MACHINE_PLANE,
            "hip_abduction", "hip_adduction", "hip_adduction_dynamic", "hip_abduction_extension",
            "hip_abduction_external_rotation", "hip_abduction_stability", "hip_extension_abduction",
            "lateral_knee_dominant", "knee_dominant_asymmetric", "unilateral_knee_dominant_asymmetric",
            "scapular_retraction", "horizontal_abduction",
        )
        skip(UnsupportedReasons.ECCENTRIC_ONLY_V2, "eccentric_knee_flexion", "biarticular_lengthened")
        skip(UnsupportedReasons.NOT_YET, "diagonal_push", "ankle_dorsiflexion", "scapular_depression")
    }

    /** Excepciones por `definitionId`: ganan a la tabla por patrón. Un mismo id vale en todos los patrones donde aparezca. */
    private val DEFINITION_RULES: Map<String, Rule> = ruleTable {
        // knee_dominant, knee_hip_dominant.
        skip(UnsupportedReasons.ISOMETRIC, "wall_sit")
        use(
            AnalysisProfileId.MACHINE_SQUAT,
            "quads_prensa_piernas", "quads_sentadilla_hack", "quads_sentadilla_hack_invertida_maquina",
            "quads_sentadilla_v_squat", "quads_sentadilla_v_squat_invertida_maquina", "pendulum_squat",
        )
        // Anderson: se arranca desde abajo (los datos dan concéntrica primero).
        use(
            AnalysisProfileId.SQUAT, "quads_sentadilla_anderson", "quads_sentadilla_anderson_frontal_barra_recta",
            startPhase = StartPhase.CONCENTRIC_FIRST,
        )
        // unilateral_knee_dominant, unilateral_hip_dominant.
        use(
            AnalysisProfileId.STEP_UP, "step_up", "quads_step_up_cajon_frontal", "quads_step_up_cajon_zercher",
            "glutes_step_up_gluteo",
        )
        skip(
            UnsupportedReasons.WALKING,
            "walking_lunge", "quads_zancada_caminando_frontal_barra_recta", "quads_zancada_caminando_zercher_barra_recta",
        )
        use(AnalysisProfileId.SQUAT, "belt_squat")
        use(AnalysisProfileId.MACHINE_SQUAT, "quads_zancada_inversa_maquina_hack", "quads_zancada_inversa_maquina_v_squat")
        // deadlift, hip_hinge_deficit.
        use(AnalysisProfileId.HINGE, "hams_peso_muerto_piernas_rigidas_deficit")
        // hip_extension.
        use(AnalysisProfileId.BACK_EXTENSION, "glutes_hiperextension_45", "glutes_hiperextension_45_zercher")
        skip(UnsupportedReasons.NOT_YET, "glutes_patada_gluteo", "reverse_hyper")
        // spinal_extension.
        skip(UnsupportedReasons.NOT_YET, "back_superman_suelo")
        skip(UnsupportedReasons.WALKING, "zercher_carry")
        // horizontal_push.
        use(AnalysisProfileId.PUSH_UP, "push_up", "knee_push_up", "diamond_push_up", "archer_push_up")
        // Solo con mancuernas: en máquina o polea (pec deck, crossover) el movimiento es horizontal y la muñeca no baja.
        use(
            AnalysisProfileId.FLY, "flat_chest_fly", "incline_chest_fly", "decline_chest_fly",
            onlyEquipment = setOf("dumbbells"), otherwise = UnsupportedReasons.MACHINE_PLANE,
        )
        use(AnalysisProfileId.DIP, "tren_superior_fondos")
        skip(UnsupportedReasons.MACHINE_PLANE, "tren_superior_cruce_poleas")
        use(
            AnalysisProfileId.BENCH,
            "tren_superior_press_inclinado_maquina_convergente", "tren_superior_press_pecho_maquina_convergente",
            "tren_superior_press_unilateral_polea", "tren_superior_press_banda_resistencia",
            startPhase = StartPhase.CONCENTRIC_FIRST,
        )
        // vertical_push.
        use(AnalysisProfileId.PUSH_UP, "pike_push_up")
        skip(UnsupportedReasons.OLYMPIC, "push_jerk", "split_jerk")
        // vertical_pull.
        skip(UnsupportedReasons.NOT_YET, "lying_pullover", "pullover", "seated_machine_pullover")
        skip(UnsupportedReasons.ECCENTRIC_ONLY_V2, "negative_pull_up")
        // horizontal_abduction.
        use(AnalysisProfileId.RAISE, "rear_delt_raise")
        // elbow_extension.
        use(
            AnalysisProfileId.TRICEPS_FLEXED_REST,
            "triceps_pushdown", "triceps_patada", "katana_extension", "crossbody_triceps_extension",
            "triceps_press_maquina", "triceps_flexiones_esfinge",
        )
        use(AnalysisProfileId.DIP, "triceps_fondos_entre_bancos")
        // knee_flexion.
        skip(UnsupportedReasons.ECCENTRIC_ONLY_V2, "glute_ham_raise")
    }

    /**
     * MVT (m/s) del 1RM por velocidad: solo estas definiciones, con este material y con su perfil. El resto, `null`.
     * Si el usuario fuerza otro perfil, la MVT no se aplica.
     */
    private val MVT_RULES = listOf(
        MvtRule(
            setOf("high_bar_back_squat", "low_bar_back_squat", "paused_back_squat"),
            setOf("barbell", "smith_machine"), AnalysisProfileId.SQUAT, 0.30,
        ),
        MvtRule(setOf("bench_press", "paused_bench_press"), setOf("barbell", "smith_machine"), AnalysisProfileId.BENCH, 0.17),
        MvtRule(setOf("conventional_deadlift", "sumo_deadlift"), setOf("barbell"), AnalysisProfileId.DEADLIFT, 0.15),
        MvtRule(setOf("military_press"), setOf("barbell"), AnalysisProfileId.OHP, 0.19),
        MvtRule(setOf("seal_row"), setOf("barbell"), AnalysisProfileId.ROW, 0.50),
    )

    fun resolve(ref: ExerciseRef): ProfileResolution {
        val override = ref.userProfileOverride
        if (override != null) {
            return ProfileResolution.Supported(adjusted(AnalysisProfiles.of(override), null, ref), SOURCE_USER)
        }
        val byDefinition = ref.definitionId?.let { DEFINITION_RULES[it] }
        if (byDefinition != null) return resolved(byDefinition, ref, SOURCE_DEFINITION)

        val pattern = ref.movementPatternId
        if (pattern != null) {
            val byPattern = PATTERN_RULES[pattern]
            if (byPattern != null) return resolved(byPattern, ref, SOURCE_PATTERN)
            if (!ref.isCustom) return ProfileResolution.Unsupported(UnsupportedReasons.UNKNOWN_PATTERN)
        }
        return ProfileResolution.NeedsUserChoice(guessOf(ref.guessedMovementPatternId))
    }

    private fun resolved(rule: Rule, ref: ExerciseRef, source: String): ProfileResolution = when (rule) {
        is Rule.Use ->
            if (rule.onlyEquipment != null && ref.equipmentId !in rule.onlyEquipment) {
                ProfileResolution.Unsupported(rule.otherwise)
            } else {
                ProfileResolution.Supported(adjusted(AnalysisProfiles.of(rule.id), rule.startPhase, ref), source)
            }
        is Rule.Skip -> ProfileResolution.Unsupported(rule.reason)
    }

    /** La conjetura de un ejercicio propio: su patrón, pasado por la misma tabla; `null` si no da perfil. */
    private fun guessOf(patternId: String?): AnalysisProfileId? {
        if (patternId == null) return null
        GUESS_ALIASES[patternId]?.let { return it }
        return (PATTERN_RULES[patternId] as? Rule.Use)?.id
    }

    /**
     * El perfil base ajustado al ejercicio: gemelos sentado, arranque de la excepción, disco según el material y MVT.
     * Los gemelos sentado (`calf_raise__bilateral__seated_machine`) cambian de perfil: sentado el tronco no sube, sube la rodilla.
     */
    private fun adjusted(profile: AnalysisProfile, startPhase: StartPhase?, ref: ExerciseRef): AnalysisProfile {
        val base = if (profile.id == AnalysisProfileId.CALF && ref.configurationId?.contains(SEATED, ignoreCase = true) == true) {
            AnalysisProfiles.of(AnalysisProfileId.CALF_SEATED)
        } else {
            profile
        }
        val equipment = ref.equipmentId
        val usesPlate = base.plateSignal != null && equipment != null && equipment in PLATE_EQUIPMENT
        val start = startPhase
            ?: if (base.id == AnalysisProfileId.BENCH && equipment != null && equipment in CONCENTRIC_BENCH_EQUIPMENT) {
                StartPhase.CONCENTRIC_FIRST
            } else {
                base.startPhase
            }
        val framing = if (base.id in PLATE_FRAMING_PROFILES) base.framing.copy(needsPlate = usesPlate) else base.framing
        return base.copy(startPhase = start, framing = framing, usesPlate = usesPlate, mvtMps = mvtOf(base.id, ref))
    }

    private fun mvtOf(profileId: AnalysisProfileId, ref: ExerciseRef): Double? {
        val definition = ref.definitionId ?: return null
        val equipment = ref.equipmentId ?: return null
        return MVT_RULES.firstOrNull { it.profile == profileId && definition in it.definitions && equipment in it.equipment }?.mps
    }

    private class MvtRule(
        val definitions: Set<String>,
        val equipment: Set<String>,
        val profile: AnalysisProfileId,
        val mps: Double,
    )
}

/**
 * Qué hacer con un patrón o una definición: medirlo con un perfil (con otro arranque, si procede) o dejarlo fuera.
 * [Use.onlyEquipment] limita el perfil a ese material; con otro (o sin material) el ejercicio queda fuera con [Use.otherwise].
 */
private sealed interface Rule {
    data class Use(
        val id: AnalysisProfileId,
        val startPhase: StartPhase? = null,
        val onlyEquipment: Set<String>? = null,
        val otherwise: String = UnsupportedReasons.NOT_YET,
    ) : Rule

    data class Skip(val reason: String) : Rule
}

private class RuleTable {
    val rules = LinkedHashMap<String, Rule>()

    fun use(
        id: AnalysisProfileId,
        vararg keys: String,
        startPhase: StartPhase? = null,
        onlyEquipment: Set<String>? = null,
        otherwise: String = UnsupportedReasons.NOT_YET,
    ) {
        keys.forEach { put(it, Rule.Use(id, startPhase, onlyEquipment, otherwise)) }
    }

    fun skip(reason: String, vararg keys: String) {
        keys.forEach { put(it, Rule.Skip(reason)) }
    }

    private fun put(key: String, rule: Rule) {
        check(rules.put(key, rule) == null) { "Entrada repetida en la tabla de perfiles: $key" }
    }
}

private fun ruleTable(build: RuleTable.() -> Unit): Map<String, Rule> = RuleTable().apply(build).rules
