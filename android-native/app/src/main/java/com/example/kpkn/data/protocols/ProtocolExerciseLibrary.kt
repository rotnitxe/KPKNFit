package com.example.kpkn.data.protocols

private const val CATALOG_V2_REVISION = "v2-approved-2026-08-02"

private val PERFORMANCE_PROFILE_BY_CONFIGURATION = mapOf(
    "back_dominadas__default" to "back_dominadas__bodyweight__dominadas",
    "back_jalon_pecho_polea__default" to "back_jalon_pecho_polea__cable__jalon_al_pecho_en_polea",
    "back_remo_pecho_apoyado_mancuernas__default" to "back_remo_pecho_apoyado_mancuernas__dumbbells__remo_con_pecho_apoyado_con_mancuernas",
    "biceps_curl__standing__barbell" to "biceps_curl__barbell__curl_de_biceps",
    "chest_fly__pec_deck__machine__machine" to "chest_fly__machine__aperturas_de_pecho",
    "core_plancha__default" to "core_plancha__bodyweight__plancha",
    "deltoides_press_hombros_sentado__default" to "deltoides_press_hombros_sentado__barbell__press_de_hombros_sentado",
    "glutes_hip_thrust__default" to "glutes_hip_thrust__barbell__hip_thrust",
    "hams_curl_femoral__default" to "hams_curl_femoral__sliders__curl_femoral",
    "hams_peso_muerto_convencional__default" to "hams_peso_muerto_convencional__barbell__peso_muerto_convencional",
    "hams_peso_muerto_sumo__default" to "hams_peso_muerto_sumo__barbell__peso_muerto_sumo",
    "hip_adduction__seated__machine__bilateral" to "hip_adduction__machine__aduccion_de_cadera",
    "lateral_raise__standing__cable__unilateral" to "lateral_raise__cable__elevacion_lateral",
    "quads_extension_cuadriceps__default" to "quads_extension_cuadriceps__machine__extension_de_cuadriceps",
    "quads_prensa_piernas__default" to "quads_prensa_piernas__machine__prensa_de_piernas",
    "quads_sentadilla_frontal__default" to "quads_sentadilla_frontal__barbell__sentadilla_frontal",
    "quads_sentadilla_trasera__default" to "quads_sentadilla_trasera__barbell__sentadilla_trasera",
    "romanian_deadlift__bilateral__barbell" to "romanian_deadlift__barbell__peso_muerto_rumano",
    "tren_superior_floor_press_barra__default" to "tren_superior_floor_press_barra__barbell__floor_press_con_barra",
    "tren_superior_fondos__default" to "tren_superior_fondos__bodyweight__fondos",
    "tren_superior_press_banca_plano_barra__default" to "tren_superior_press_banca_plano_barra__barbell__press_de_banca_con_barra",
    "triceps_pushdown__default" to "triceps_pushdown__cable__pushdown_de_triceps",
)

/**
 * Pool explícito de configuraciones v2 usado para compilar protocolos a
 * sesiones ejecutables. `exerciseDbId` conserva el nombre histórico del
 * campo de transporte, pero su valor es siempre un `configurationId` v2;
 * nunca un alias ni un ID de la fuente antigua.
 */
data class ProtocolLift(val name: String, val exerciseDbId: String) {
    val catalogRevision: String = CATALOG_V2_REVISION
    val catalogDefinitionId: String = exerciseDbId.substringBefore("__")
    val performanceProfileId: String = PERFORMANCE_PROFILE_BY_CONFIGURATION[exerciseDbId]
        ?: error("Protocol references an unregistered v2 configuration: $exerciseDbId")
}

enum class ProtocolLiftFocus { SQUAT, BENCH, DEADLIFT, OVERHEAD_PRESS, PULL, GENERAL }

object ProtocolExerciseLibrary {

    val SQUAT_MAIN = ProtocolLift("Sentadilla Trasera Barra Alta con Barra Recta", "quads_sentadilla_trasera__default")
    val SQUAT_TECHNIQUE = ProtocolLift("Sentadilla Frontal con Barra Recta", "quads_sentadilla_frontal__default")
    val BENCH_MAIN = ProtocolLift("Press de Banca Plano con Barra", "tren_superior_press_banca_plano_barra__default")
    val BENCH_TECHNIQUE = ProtocolLift("Floor Press con Barra", "tren_superior_floor_press_barra__default")
    val DEADLIFT_MAIN = ProtocolLift("Peso Muerto Convencional con Barra Recta", "hams_peso_muerto_convencional__default")
    val DEADLIFT_TECHNIQUE = ProtocolLift("Peso Muerto Sumo con Barra Recta", "hams_peso_muerto_sumo__default")
    val OHP_MAIN = ProtocolLift("Press de Hombros Sentado con Barra Recta", "deltoides_press_hombros_sentado__default")

    private val SQUAT_ACCESSORIES = listOf(
        ProtocolLift("Prensa de Piernas", "quads_prensa_piernas__default"),
        ProtocolLift("Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__default"),
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell"),
    )
    private val BENCH_ACCESSORIES = listOf(
        ProtocolLift("Extensión de Tríceps en Polea Alta", "triceps_pushdown__default"),
        ProtocolLift("Aperturas en Máquina Pec Deck", "chest_fly__pec_deck__machine__machine"),
        ProtocolLift("Fondos en Paralelas", "tren_superior_fondos__default"),
    )
    private val DEADLIFT_ACCESSORIES = listOf(
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell"),
        ProtocolLift("Curl Femoral con Sliders", "hams_curl_femoral__default"),
        ProtocolLift("Hip Thrust con Barra Recta", "glutes_hip_thrust__default"),
    )
    private val PULL_ACCESSORIES = listOf(
        ProtocolLift("Jalón al Pecho en Polea", "back_jalon_pecho_polea__default"),
        ProtocolLift("Remo con Pecho Apoyado con Mancuernas", "back_remo_pecho_apoyado_mancuernas__default"),
        ProtocolLift("Dominadas", "back_dominadas__default"),
    )
    private val SHOULDER_ARM_ACCESSORIES = listOf(
        ProtocolLift("Elevación Lateral de Pie en Polea", "lateral_raise__standing__cable__unilateral"),
        ProtocolLift("Curl de Bíceps de Pie", "biceps_curl__standing__barbell"),
    )
    private val CORE_ACCESSORIES = listOf(
        ProtocolLift("Plancha Frontal", "core_plancha__default"),
    )

    private val rotation = listOf(SQUAT_MAIN, BENCH_MAIN, DEADLIFT_MAIN, OHP_MAIN)

    /** Determina el foco principal de una sesión a partir de la etiqueta del día del split. */
    fun focusForDayLabel(dayLabel: String): ProtocolLiftFocus {
        val lower = dayLabel.lowercase()
        return when {
            "sentadilla" in lower || "squat" in lower || "pierna" in lower || "cuádriceps" in lower ||
                "cuadriceps" in lower || "quad" in lower || "lower" in lower -> ProtocolLiftFocus.SQUAT
            "peso muerto" in lower || "deadlift" in lower -> ProtocolLiftFocus.DEADLIFT
            "militar" in lower || "hombro" in lower -> ProtocolLiftFocus.OVERHEAD_PRESS
            "banca" in lower || "bench" in lower || "pecho" in lower || "empuje" in lower || "push" in lower -> ProtocolLiftFocus.BENCH
            "espalda" in lower || "tirón" in lower || "tiron" in lower || "pull" in lower -> ProtocolLiftFocus.PULL
            "torso" in lower || "upper" in lower -> ProtocolLiftFocus.BENCH
            else -> ProtocolLiftFocus.GENERAL
        }
    }

    /** Movimiento principal (competición) para un foco/índice de sesión dado. */
    fun mainLiftFor(focus: ProtocolLiftFocus, sessionIndex: Int): ProtocolLift = when (focus) {
        ProtocolLiftFocus.SQUAT -> SQUAT_MAIN
        ProtocolLiftFocus.BENCH -> BENCH_MAIN
        ProtocolLiftFocus.DEADLIFT -> DEADLIFT_MAIN
        ProtocolLiftFocus.OVERHEAD_PRESS -> OHP_MAIN
        ProtocolLiftFocus.PULL -> BENCH_MAIN
        ProtocolLiftFocus.GENERAL -> rotation[sessionIndex.mod(rotation.size)]
    }

    /** Variante técnica del movimiento principal, usada en bloques de acumulación. */
    fun techniqueVariantFor(main: ProtocolLift): ProtocolLift = when (main) {
        SQUAT_MAIN -> SQUAT_TECHNIQUE
        BENCH_MAIN -> BENCH_TECHNIQUE
        DEADLIFT_MAIN -> DEADLIFT_TECHNIQUE
        else -> main
    }

    /**
     * Accesorios reales para acompañar al movimiento principal. Rota levemente según
     * la semana (ondulación mínima) sin perder coherencia con el patrón de movimiento.
     */
    fun accessoriesFor(main: ProtocolLift, weekNumber: Int, count: Int): List<ProtocolLift> {
        if (count <= 0) return emptyList()
        val pool = when (main) {
            SQUAT_MAIN -> SQUAT_ACCESSORIES
            BENCH_MAIN -> BENCH_ACCESSORIES + SHOULDER_ARM_ACCESSORIES
            DEADLIFT_MAIN -> DEADLIFT_ACCESSORIES + PULL_ACCESSORIES
            OHP_MAIN -> SHOULDER_ARM_ACCESSORIES + BENCH_ACCESSORIES
            else -> PULL_ACCESSORIES + CORE_ACCESSORIES
        }
        if (pool.isEmpty()) return emptyList()
        val offset = (weekNumber - 1).mod(pool.size)
        return (0 until count).map { i -> pool[(offset + i).mod(pool.size)] }
    }
}
