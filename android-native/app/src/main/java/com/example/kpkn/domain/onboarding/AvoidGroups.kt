package com.example.kpkn.domain.onboarding

/**
 * Qué definiciones del catálogo (`exercise_catalog_v2.json`) cubre cada grupo de «ejercicios que prefieres evitar»
 * (paso AVOIDED_EXERCISES). El filtro es por DEFINICIÓN, no por configuración: al evitar una definición se evitan también
 * todas sus configuraciones (al evitar la sentadilla frontal se evita la de mancuernas y la de kettlebell).
 *
 * Solo se incluyen definiciones cuyo patrón es el del grupo: la hack o la sentadilla sumo de peso corporal, por ejemplo,
 * quedan fuera a propósito. [JUMPS] y [RUNNING] están vacíos porque el catálogo no tiene ninguna definición de saltos ni
 * de carrera: su control existe en el paso, pero no cambia ningún ejercicio hasta que haya un lote de catálogo que los dé
 * de alta.
 *
 * `AvoidGroupsTest` comprueba que cada id existe en el catálogo.
 */
object AvoidGroups {

    private val definitionIds: Map<AvoidGroup, Set<String>> = mapOf(
        AvoidGroup.BARBELL_SQUAT to setOf(
            "front_squat", "high_bar_back_squat", "low_bar_back_squat", "paused_back_squat", "overhead_squat",
            "quads_sentadilla_anderson", "quads_sentadilla_anderson_frontal_barra_recta", "quads_sentadilla_bazuca",
            "quads_sentadilla_somersault", "quads_sentadilla_zercher_barra_recta", "quads_sentadilla_sumo_frontal",
            "quads_sentadilla_sumo_zercher", "quads_sentadilla_cajon", "quads_sentadilla_jefferson",
        ),
        AvoidGroup.DEADLIFT to setOf(
            "conventional_deadlift", "sumo_deadlift", "romanian_deadlift", "romanian_sumo_deadlift",
            "stiff_leg_deadlift", "deadlift_to_knees", "hams_peso_muerto_convencional_deficit",
            "hams_peso_muerto_piernas_rigidas_deficit", "hams_peso_muerto_sumo_deficit",
            "hams_peso_muerto_rumano_deficit", "hams_peso_muerto_rumano_sumo_deficit",
        ),
        AvoidGroup.BENCH_PRESS to setOf(
            "bench_press", "incline_bench_press", "decline_bench_press", "paused_bench_press",
            "close_grip_bench_press", "tren_superior_press_banca_cadenas", "tren_superior_press_spoto_barra",
        ),
        AvoidGroup.OVERHEAD_PRESS to setOf(
            "military_press", "seated_shoulder_press", "arnold_press", "deltoides_push_press",
        ),
        AvoidGroup.PULL_UPS to setOf("pull_up", "rack_chin", "negative_pull_up", "back_dominadas_escapulares"),
        AvoidGroup.DIPS to setOf("tren_superior_fondos", "triceps_fondos_entre_bancos"),
        AvoidGroup.LUNGES to setOf(
            "forward_lunge", "reverse_lunge", "walking_lunge", "bulgarian_split_squat", "bulgarian_zercher",
            "glutes_zancada_cruzada", "quads_zancada_caminando_frontal_barra_recta",
            "quads_zancada_caminando_zercher_barra_recta", "quads_zancada_frontal_zercher",
            "quads_zancada_inversa_frontal", "quads_zancada_inversa_maquina_hack",
            "quads_zancada_inversa_maquina_v_squat", "quads_zancada_inversa_zercher",
            "quads_sentadilla_bulgara_somersault", "quads_sentadilla_bulgara_jefferson",
        ),
        AvoidGroup.HIP_THRUST to setOf("hip_thrust"),
        // Sin definiciones en el catálogo actual (ver el KDoc de la clase).
        AvoidGroup.JUMPS to emptySet(),
        AvoidGroup.RUNNING to emptySet(),
    )

    /** Definiciones del catálogo que cubre [group] (vacío si el catálogo aún no tiene ninguna). */
    fun definitionIdsOf(group: AvoidGroup): Set<String> = definitionIds.getValue(group)
}
