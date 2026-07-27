package com.example.kpkn.data.protocols

/**
 * Pool mínimo de ejercicios reales (con exerciseDbId del catálogo) usado para
 * compilar protocolos a sesiones ejecutables. No pretende reemplazar los
 * SessionTemplates completos: solo cubre los movimientos principales de
 * powerlifting/powerbuilding/culturismo y sus acompañantes más comunes,
 * reutilizando los mismos IDs que [com.example.kpkn.data.sessions.SessionTemplates].
 */
data class ProtocolLift(val name: String, val exerciseDbId: String)

enum class ProtocolLiftFocus { SQUAT, BENCH, DEADLIFT, OVERHEAD_PRESS, PULL, GENERAL }

object ProtocolExerciseLibrary {

    val SQUAT_MAIN = ProtocolLift("Sentadilla Trasera Barra Alta con Barra Recta", "quads_sentadilla_trasera_barra_alta")
    val SQUAT_TECHNIQUE = ProtocolLift("Sentadilla Frontal con Barra Recta", "quads_sentadilla_frontal_barra_recta")
    val BENCH_MAIN = ProtocolLift("Press de Banca Plano con Barra", "tren_superior_press_banca_plano_barra")
    val BENCH_TECHNIQUE = ProtocolLift("Press de Banca Inclinado con Barra", "tren_superior_press_banca_inclinado_barra")
    val DEADLIFT_MAIN = ProtocolLift("Peso Muerto Convencional con Barra Recta", "hams_peso_muerto_convencional_barra_recta")
    val DEADLIFT_TECHNIQUE = ProtocolLift("Peso Muerto Sumo con Barra Recta", "hams_peso_muerto_sumo_barra_recta")
    val OHP_MAIN = ProtocolLift("Press de Hombros Sentado con Barra Recta", "deltoides_press_hombros_sentado_barra_recta")

    private val SQUAT_ACCESSORIES = listOf(
        ProtocolLift("Prensa de Piernas Horizontal en Máquina", "quads_prensa_piernas_horizontal_maquina"),
        ProtocolLift("Extensión de Cuádriceps en Máquina", "quads_extension_cuadriceps_maquina"),
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "hams_peso_muerto_rumano_barra_recta"),
    )
    private val BENCH_ACCESSORIES = listOf(
        ProtocolLift("Extensión de Tríceps en Polea Alta", "triceps_pushdown_polea"),
        ProtocolLift("Aperturas en Máquina Pec Deck", "tren_superior_aperturas_pec_deck"),
        ProtocolLift("Fondos en Paralelas", "tren_superior_fondos_paralelas"),
    )
    private val DEADLIFT_ACCESSORIES = listOf(
        ProtocolLift("Peso Muerto Rumano con Barra Recta", "hams_peso_muerto_rumano_barra_recta"),
        ProtocolLift("Curl Femoral Sentado en Máquina", "hams_curl_femoral_sentado_maquina"),
        ProtocolLift("Hip Thrust con Barra Recta", "glutes_hip_thrust_barra_recta"),
    )
    private val PULL_ACCESSORIES = listOf(
        ProtocolLift("Jalón al Pecho en Polea (Agarre Ancho)", "back_jalon_pecho_polea_ancho"),
        ProtocolLift("Remo con Pecho Apoyado con Mancuernas", "back_remo_pecho_apoyado_mancuernas"),
        ProtocolLift("Dominadas Pronas", "back_dominadas_pronas"),
    )
    private val SHOULDER_ARM_ACCESSORIES = listOf(
        ProtocolLift("Elevaciones Laterales de Pie en Polea", "deltoides_elevaciones_laterales_de_pie_polea"),
        ProtocolLift("Curl de Bíceps de Pie con Mancuernas", "biceps_curl_de_pie_supino_mancuernas"),
    )
    private val CORE_ACCESSORIES = listOf(
        ProtocolLift("Plancha Frontal Isométrica", "core_plancha_frontal_iso"),
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
