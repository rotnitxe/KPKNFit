package com.example.kpkn.domain.biomechanics.profiles

import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId.*

/**
 * La tabla del brief VT2a escrita aparte, con la forma del brief (una fila por patrón, y debajo sus excepciones por
 * definición), para contrastarla con la del resolver sobre el catálogo real. `profile == null` es UNSUPPORTED.
 */
internal object ProfileExpectations {
    /** [onlyEquipment]: el perfil vale solo con ese material; con otro, UNSUPPORTED (aperturas con mancuernas). */
    class Expected(val profile: AnalysisProfileId?, val startPhase: StartPhase? = null, val onlyEquipment: Set<String>? = null)

    /**
     * El perfil esperado para una configuración concreta de la tabla: aplica el material (aperturas) y los gemelos
     * sentado (VT2b: `calf_raise__bilateral__seated_machine` → CALF_SEATED). `null` es UNSUPPORTED.
     */
    fun profileFor(expected: Expected, equipmentId: String, configurationId: String): AnalysisProfileId? {
        val id = expected.profile ?: return null
        if (expected.onlyEquipment != null && equipmentId !in expected.onlyEquipment) return null
        return if (id == CALF && "seated" in configurationId) CALF_SEATED else id
    }

    private class Rows {
        val map = LinkedHashMap<String, Expected>()

        fun row(expected: Expected, vararg keys: String) {
            keys.forEach { key -> check(map.put(key, expected) == null) { "Repetido en la expectativa: $key" } }
        }
    }

    private fun rows(build: Rows.() -> Unit): Map<String, Expected> = Rows().apply(build).map

    private fun use(profile: AnalysisProfileId, startPhase: StartPhase? = null, onlyEquipment: Set<String>? = null) =
        Expected(profile, startPhase, onlyEquipment)

    private val unsupported = Expected(null)

    /** Los 63 patrones del catálogo (comprobado el 13-oct): perfil por defecto. */
    val byPattern: Map<String, Expected> = rows {
        row(use(SQUAT), "knee_dominant", "knee_hip_dominant")
        row(use(MACHINE_SQUAT), "knee_dominant_lengthened")
        row(use(LUNGE), "unilateral_knee_dominant", "unilateral_hip_dominant")
        row(use(DEADLIFT), "deadlift", "hip_hinge_deficit")
        row(use(HINGE), "romanian_deadlift", "romanian_deadlift_deficit", "hip_hinge", "hip_hinge_lengthened")
        row(use(HIP_THRUST), "hip_extension", "hip_extension_external_rotation")
        row(use(BACK_EXTENSION), "spinal_extension")
        row(use(BENCH), "horizontal_push")
        row(use(OHP), "vertical_push")
        row(unsupported, "diagonal_push")
        row(use(PULL_VERTICAL), "vertical_pull", "vertical_pull_abduction")
        row(use(ROW), "horizontal_pull")
        row(unsupported, "horizontal_abduction")
        row(use(CURL), "elbow_flexion")
        row(use(TRICEPS_EXTENDED_REST), "elbow_extension")
        row(use(RAISE), "shoulder_abduction_full_rom", "shoulder_abduction_diagonal", "shoulder_flexion")
        row(use(LEG_EXTENSION), "knee_extension")
        row(use(LEG_CURL), "knee_flexion")
        row(use(CALF), "plantar_flexion")
        row(use(SHRUG), "scapular_elevation")
        row(
            unsupported,
            "hip_hinge_explosive", "wrist_flexion", "wrist_extension", "wrist_flexion_extension", "pinch_grip",
            "isometric_grip", "hip_abduction", "hip_adduction", "hip_adduction_dynamic", "hip_abduction_extension",
            "hip_abduction_external_rotation", "hip_abduction_stability", "hip_extension_abduction",
            "scapular_retraction", "scapular_depression", "trunk_flexion", "trunk_rotation", "lateral_trunk_flexion",
            "spinal_flexion", "anti_extension_isometric", "anti_extension_pelvic_control", "anti_extension_trunk",
            "anti_rotation_trunk", "hip_flexion", "ankle_dorsiflexion", "biarticular_lengthened",
            "eccentric_knee_flexion", "knee_dominant_asymmetric", "lateral_knee_dominant",
            "unilateral_knee_dominant_asymmetric", "neck_extension", "neck_flexion", "neck_lateral_flexion",
        )
    }

    /** Excepciones por definitionId de la tabla del brief. */
    val byDefinition: Map<String, Expected> = rows {
        // knee_dominant, knee_hip_dominant
        row(unsupported, "wall_sit")
        row(
            use(MACHINE_SQUAT),
            "quads_prensa_piernas", "quads_sentadilla_hack", "quads_sentadilla_hack_invertida_maquina",
            "quads_sentadilla_v_squat", "quads_sentadilla_v_squat_invertida_maquina", "pendulum_squat",
        )
        row(
            use(SQUAT, StartPhase.CONCENTRIC_FIRST),
            "quads_sentadilla_anderson", "quads_sentadilla_anderson_frontal_barra_recta",
        )
        // unilateral_knee_dominant
        row(use(STEP_UP), "step_up", "quads_step_up_cajon_frontal", "quads_step_up_cajon_zercher")
        row(
            unsupported,
            "walking_lunge", "quads_zancada_caminando_frontal_barra_recta", "quads_zancada_caminando_zercher_barra_recta",
        )
        row(use(SQUAT), "belt_squat")
        row(use(MACHINE_SQUAT), "quads_zancada_inversa_maquina_hack", "quads_zancada_inversa_maquina_v_squat")
        // unilateral_hip_dominant
        row(use(STEP_UP), "glutes_step_up_gluteo")
        // deadlift, hip_hinge_deficit
        row(use(HINGE), "hams_peso_muerto_piernas_rigidas_deficit")
        // hip_extension
        row(use(BACK_EXTENSION), "glutes_hiperextension_45", "glutes_hiperextension_45_zercher")
        row(unsupported, "glutes_patada_gluteo", "reverse_hyper")
        // spinal_extension
        row(unsupported, "back_superman_suelo", "zercher_carry")
        // horizontal_push
        row(use(PUSH_UP), "push_up", "knee_push_up", "diamond_push_up", "archer_push_up")
        row(use(FLY, onlyEquipment = setOf("dumbbells")), "flat_chest_fly", "incline_chest_fly", "decline_chest_fly")
        row(use(DIP), "tren_superior_fondos")
        row(unsupported, "tren_superior_cruce_poleas")
        row(
            use(BENCH, StartPhase.CONCENTRIC_FIRST),
            "tren_superior_press_inclinado_maquina_convergente", "tren_superior_press_pecho_maquina_convergente",
            "tren_superior_press_unilateral_polea", "tren_superior_press_banda_resistencia",
        )
        // vertical_push
        row(use(PUSH_UP), "pike_push_up")
        row(unsupported, "push_jerk", "split_jerk")
        // vertical_pull
        row(unsupported, "lying_pullover", "pullover", "seated_machine_pullover", "negative_pull_up")
        // horizontal_abduction
        row(use(RAISE), "rear_delt_raise")
        // elbow_extension
        row(
            use(TRICEPS_FLEXED_REST),
            "triceps_pushdown", "triceps_patada", "katana_extension", "crossbody_triceps_extension",
            "triceps_press_maquina", "triceps_flexiones_esfinge",
        )
        row(use(DIP), "triceps_fondos_entre_bancos")
        // knee_flexion
        row(unsupported, "glute_ham_raise")
    }
}
