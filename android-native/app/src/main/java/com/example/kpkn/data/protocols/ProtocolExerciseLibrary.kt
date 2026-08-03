package com.example.kpkn.data.protocols

private const val CATALOG_V2_REVISION = "v2-approved-2026-08-02-c"

private val PERFORMANCE_PROFILE_BY_CONFIGURATION = mapOf(
    "pull_up__pronated__medium" to "pull_up__pronated__wide",
    "lat_pulldown__bilateral__cable" to "lat_pulldown__bilateral__cable",
    "chest_supported_row__dumbbells__wide" to "chest_supported_row__dumbbells__wide",
    "standing_biceps_curl__barbell" to "standing_biceps_curl__barbell",
    "flat_chest_fly__machine" to "flat_chest_fly__machine",
    "core_plancha__default" to "core_plancha__default",
    "seated_shoulder_press__barbell" to "seated_shoulder_press__barbell",
    "hip_thrust__bilateral__barbell" to "hip_thrust__bilateral__barbell",
    "curl_isquios_con_sliders__default" to "curl_isquios_con_sliders__default",
    "conventional_deadlift__bilateral__barbell" to "conventional_deadlift__bilateral__barbell",
    "sumo_deadlift__barbell" to "sumo_deadlift__barbell",
    "hip_adduction__seated__machine__bilateral" to "hip_adduction__seated__machine__bilateral",
    "standing_lateral_raise__cable" to "standing_lateral_raise__cable",
    "quads_extension_cuadriceps__machine__bilateral" to "quads_extension_cuadriceps__machine__bilateral",
    "quads_prensa_piernas__bilateral" to "quads_prensa_piernas__bilateral",
    "front_squat__barbell" to "front_squat__barbell",
    "high_bar_back_squat__barbell" to "high_bar_back_squat__barbell",
    "romanian_deadlift__bilateral__barbell" to "romanian_deadlift__bilateral__barbell",
    "floor_press__barbell" to "floor_press__barbell",
    "tren_superior_fondos__default" to "tren_superior_fondos__default",
    "bench_press__barbell" to "bench_press__barbell",
    "triceps_pushdown__bilateral__cable" to "triceps_pushdown__bilateral__cable",
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

    val SQUAT_MAIN = ProtocolLift("Sentadilla Trasera Barra Alta con Barra Recta", "high_bar_back_squat__barbell")
    val SQUAT_TECHNIQUE = ProtocolLift("Sentadilla Frontal con Barra Recta", "front_squat__barbell")
    val BENCH_MAIN = ProtocolLift("Press de Banca Plano con Barra", "bench_press__barbell")
    val BENCH_TECHNIQUE = ProtocolLift("Floor Press con Barra", "floor_press__barbell")
    val DEADLIFT_MAIN = ProtocolLift("Peso Muerto Convencional con Barra Recta", "conventional_deadlift__bilateral__barbell")
    val DEADLIFT_TECHNIQUE = ProtocolLift("Peso Muerto Sumo con Barra Recta", "sumo_deadlift__barbell")
    val OHP_MAIN = ProtocolLift("Press de Hombros Sentado con Barra Recta", "seated_shoulder_press__barbell")

    private val SQUAT_ACCESSORIES = listOf(
        ProtocolLift("Prensa de Piernas", "quads_prensa_piernas__bilateral"),
        ProtocolLift("Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps__machine__bilateral"),
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell"),
    )
    private val BENCH_ACCESSORIES = listOf(
        ProtocolLift("Extensión de Tríceps en Polea Alta", "triceps_pushdown__bilateral__cable"),
        ProtocolLift("Aperturas en Máquina Pec Deck", "flat_chest_fly__machine"),
        ProtocolLift("Fondos en Paralelas", "tren_superior_fondos__default"),
    )
    private val DEADLIFT_ACCESSORIES = listOf(
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "romanian_deadlift__bilateral__barbell"),
        ProtocolLift("Curl Femoral con Sliders", "curl_isquios_con_sliders__default"),
        ProtocolLift("Hip Thrust con Barra Recta", "hip_thrust__bilateral__barbell"),
    )
    private val PULL_ACCESSORIES = listOf(
        ProtocolLift("Jalón al Pecho en Polea", "lat_pulldown__bilateral__cable"),
        ProtocolLift("Remo con Pecho Apoyado con Mancuernas", "chest_supported_row__dumbbells__wide"),
        ProtocolLift("Dominadas", "pull_up__pronated__medium"),
    )
    private val SHOULDER_ARM_ACCESSORIES = listOf(
        ProtocolLift("Elevación Lateral de Pie en Polea", "standing_lateral_raise__cable"),
        ProtocolLift("Curl de Bíceps de Pie", "standing_biceps_curl__barbell"),
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
