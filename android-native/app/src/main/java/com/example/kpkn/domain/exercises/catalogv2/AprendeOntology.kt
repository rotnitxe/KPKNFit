package com.example.kpkn.domain.exercises.catalogv2

/**
 * Canonical bridge between the approved exercise catalog and Aprende's
 * anatomy vocabulary.  The catalog owns the source ids; these maps are the
 * versioned, explicit decisions that let the two contexts link without
 * guessing from a display name.
 */
object AprendeOntology {
    const val REVISION = "wikilab-v3-2026-08-08"

    /** Joint ids are shared directly by the catalog and the anatomy atlas. */
    val wikiLabJointIds: Set<String> = setOf(
        "glenohumeral", "acromioclavicular", "esternoclavicular", "codo",
        "radiocubital-proximal", "muñeca", "columna-cervical", "columna-toracica",
        "columna-lumbar", "sacroiliaca", "cadera", "rodilla", "tobillo", "subtalar",
        "escapulotoracica",
    )

    /** Every catalog muscle id is deliberately accounted for. */
    val catalogMuscleToWikiLab: Map<String, String> = mapOf(
        "abdominals" to "recto-abdominal",
        "adductors" to "aductores",
        "biceps" to "bíceps",
        "calves" to "pantorrillas",
        "deltoid" to "deltoides",
        "erector_spinae" to "erectores-espinales",
        "forearm" to "antebrazo",
        "gluteus_maximus" to "glúteo-mayor",
        "gluteus_medius" to "glúteo-medio",
        "hamstrings" to "isquiosurales",
        // The bundled anatomy atlas has no separate hip-flexor entity yet;
        // recto femoral is its closest explicit muscle entry.
        "hip_flexors" to "recto-femoral",
        "latissimus_dorsi" to "dorsal-ancho",
        "neck" to "cuello",
        "pectoralis" to "pectoral",
        "quadriceps" to "cuádriceps",
        "rhomboids" to "romboides",
        // The tensor fasciae latae is represented by the atlas' gluteal
        // stabilizer entry until a dedicated entity is curated.
        "tensor_fasciae_latae" to "glúteo-medio",
        "tibialis_anterior" to "tibial-anterior",
        "trapezius" to "trapecio",
        "triceps" to "tríceps",
        "core" to "core",
    )

    /** Explicit aggregate atlas entities used by reverse anatomy links. */
    val wikiLabAggregateMusclesToCatalog: Map<String, Set<String>> = mapOf(
        "espalda" to setOf("latissimus_dorsi", "trapezius", "rhomboids", "erector_spinae"),
        "hombros" to setOf("deltoid"),
        "brazos" to setOf("biceps", "triceps", "forearm"),
        "piernas" to setOf(
            "quadriceps", "hamstrings", "gluteus_maximus", "gluteus_medius",
            "adductors", "calves", "tibialis_anterior",
        ),
        "glúteos" to setOf("gluteus_maximus", "gluteus_medius"),
        "abdomen" to setOf("abdominals", "core"),
    )

    /**
     * Explicit movement decisions. A null value is intentional: combined or
     * underspecified source patterns do not get a misleading definition chip.
     */
    val catalogPatternToWikiLab: Map<String, String?> = mapOf(
        "ankle_dorsiflexion" to "ankle-dorsiflexion",
        "anti_extension_isometric" to "anti-extension",
        "anti_extension_pelvic_control" to "anti-extension",
        "anti_extension_trunk" to "anti-extension",
        "anti_rotation_trunk" to "anti-rotation",
        "biarticular_lengthened" to null,
        "deadlift" to "hinge",
        "diagonal_push" to "diagonal-push",
        "eccentric_knee_flexion" to "knee-flexion",
        "elbow_extension" to "elbow-extension",
        "elbow_flexion" to "elbow-flexion",
        "hip_abduction" to "hip-abduction",
        "hip_abduction_extension" to null,
        "hip_abduction_external_rotation" to null,
        "hip_abduction_stability" to null,
        "hip_adduction" to "hip-adduction",
        "hip_adduction_dynamic" to "hip-adduction",
        "hip_extension" to "hip-extension",
        "hip_extension_abduction" to null,
        "hip_extension_external_rotation" to null,
        "hip_flexion" to "hip-flexion",
        "hip_hinge" to "hinge",
        "hip_hinge_deficit" to "hinge",
        "hip_hinge_explosive" to "hinge",
        "hip_hinge_lengthened" to "hinge",
        "horizontal_abduction" to "horizontal-abduction",
        "horizontal_pull" to "horizontal-pull",
        "horizontal_push" to "horizontal-push",
        "isometric_grip" to "isometric-grip",
        "knee_dominant" to "squat",
        "knee_dominant_asymmetric" to "lunge",
        "knee_dominant_lengthened" to "squat",
        "knee_extension" to "knee-extension",
        "knee_flexion" to "knee-flexion",
        "knee_hip_dominant" to null,
        "knee_hip_extension" to null,
        "knee_hip_flexion" to null,
        "lateral_knee_dominant" to "lateral-knee-dominant",
        "lateral_trunk_flexion" to "lateral-trunk-flexion",
        "neck_extension" to "neck-extension",
        "neck_flexion" to "neck-flexion",
        "neck_lateral_flexion" to "neck-lateral-flexion",
        "pinch_grip" to "pinch-grip",
        "plantar_flexion" to "plantar-flexion",
        "romanian_deadlift" to "hinge",
        "romanian_deadlift_deficit" to "hinge",
        "scapular_depression" to "scapular-depression",
        "scapular_elevation" to "scapular-elevation",
        "shoulder_abduction_diagonal" to null,
        "shoulder_abduction_full_rom" to "shoulder-abduction",
        "shoulder_flexion" to "shoulder-flexion",
        "spinal_extension" to "spinal-extension",
        "spinal_flexion" to "spinal-flexion",
        "trunk_flexion" to "trunk-flexion",
        "trunk_rotation" to "rotation",
        "unilateral_hip_dominant" to "lunge",
        "unilateral_knee_dominant" to "lunge",
        "unilateral_knee_dominant_asymmetric" to "lunge",
        "vertical_pull" to "vertical-pull",
        "vertical_pull_abduction" to null,
        "vertical_push" to "vertical-push",
        "wrist_extension" to "wrist-extension",
        "wrist_flexion" to "wrist-flexion",
        "wrist_flexion_extension" to null,
    )

    /** Legacy ids found in static anatomy assets. Null means explicit removal. */
    val legacyExerciseDecisions: Map<String, String?> = mapOf(
        "db_ab_wheel" to "core_rueda_abdominal",
        "db_barbell_row" to "conventional_row",
        "db_bench_press_tng" to "bench_press",
        "db_chin_up" to "pull_up",
        "db_deadlift" to "conventional_deadlift",
        "db_dips" to "tren_superior_fondos",
        "db_dumbbell_bench_press" to "bench_press",
        "db_dumbbell_row" to "conventional_row",
        "db_dumbbell_shoulder_press" to "seated_shoulder_press",
        "db_exp_face_pull" to "deltoides_face_pull",
        "db_exp_hammer_curl" to "hammer_curl",
        "db_front_squat" to "front_squat",
        "db_goblet_squat" to "quads_sentadilla_copa",
        "db_hanging_leg_raises" to "core_elevacion_piernas",
        "db_lat_pulldown" to "lat_pulldown",
        "db_lateral_raise" to "standing_lateral_raise",
        "db_overhead_press" to "military_press",
        "db_plank" to "core_plancha",
        "db_pull_up" to "pull_up",
        "db_push_up" to "push_up",
        "db_romanian_deadlift" to "romanian_deadlift",
        "db_seated_cable_row" to "conventional_row",
        "db_squat_high_bar" to "high_bar_back_squat",
        "db_squat_low_bar" to "low_bar_back_squat",
        "db_sumo_deadlift" to "sumo_deadlift",
        "db_triceps_pushdown" to "triceps_pushdown",
        "nuevo_farmer_walk_kettlebells" to "forearms_paseo_del_granjero",
        "tren_inferior_bulgara_mancuernas" to "bulgarian_split_squat",
        "tren_inferior_curl_femoral_sentado" to "seated_leg_curl",
        "tren_inferior_curl_nordico" to "hams_curl_nordic_peso_corporal",
        "tren_inferior_elevacion_talones_pie_maquina" to "calf_raise",
        "tren_inferior_elevacion_talones_sentado" to "calf_raise",
        "tren_inferior_extension_cuadriceps" to "quads_extension_cuadriceps",
        "tren_inferior_hip_thrust_barra" to "hip_thrust",
        "tren_inferior_peso_muerto_barra_hexagonal" to "conventional_deadlift",
        "tren_inferior_peso_muerto_convencional" to "conventional_deadlift",
        "tren_inferior_peso_muerto_rumano" to "romanian_deadlift",
        "tren_inferior_salto_caida" to null,
        "tren_inferior_salto_cajon" to null,
        "tren_inferior_salto_longitud" to null,
        "tren_inferior_sentadilla_barra_alta" to "high_bar_back_squat",
        "tren_inferior_sentadilla_frontal" to "front_squat",
        "tren_inferior_sentadilla_goblet_mancuerna" to "quads_sentadilla_copa",
        "tren_inferior_subida_cajon_mancuernas" to "step_up",
        "tren_inferior_zancada_caminando_mancuernas" to "walking_lunge",
        "tren_inferior_zancada_inversa_mancuernas" to "reverse_lunge",
        "tren_superior_curl_martillo_mancuernas" to "hammer_curl",
        "tren_superior_dominadas_supinas" to "pull_up",
        "tren_superior_extension_triceps_polea_cuerda" to "triceps_pushdown",
        "tren_superior_face_pull_polea" to "deltoides_face_pull",
        "tren_superior_jalon_pecho_prono" to "lat_pulldown",
        "tren_superior_press_banca_inclinado_mancuernas" to "incline_bench_press",
        "tren_superior_press_banca_plano_mancuernas" to "bench_press",
        "tren_superior_press_hombros_sentado_mancuernas" to "seated_shoulder_press",
        "tren_superior_press_militar_pie_barra" to "military_press",
        "tren_superior_remo_sentado_polea_baja" to "conventional_row",
        "tren_superior_remo_una_mano_mancuerna" to "conventional_row",
        "ultimo_bicho_muerto" to "core_crunch_suelo_peso_corporal",
        "ultimo_lanzamiento_rotacional_balon" to null,
        "ultimo_lenador_polea" to "core_lenador_polea",
        "ultimo_paseo_granjero_mancuernas" to "forearms_paseo_del_granjero",
        "ultimo_paseo_granjero_unilateral" to "forearms_paseo_del_granjero",
        "ultimo_plancha_frontal" to "core_plancha",
        "ultimo_plancha_rodillo" to "core_rueda_abdominal",
        "ultimo_press_landmine_rotacional" to null,
        "ultimo_press_pallof_arrodillado" to "core_press_pallof",
    )

    /** Natural-language labels that still exist in static anatomy assets.
     * They are explicit editorial decisions, never a runtime name matcher. */
    val legacyExerciseNameDecisions: Map<String, String?> = mapOf(
        "Chin-ups" to "pull_up",
        "Curl con barra" to "standing_biceps_curl",
        "Curl nórdico" to "hams_curl_nordic_peso_corporal",
        "Elevación de talones" to "calf_raise",
        "Estocadas" to null,
        "Extensión de cuádriceps" to "quads_extension_cuadriceps",
        "Extensión de tríceps" to "triceps_pushdown",
        "Fondos" to "tren_superior_fondos",
        "Peso muerto" to "conventional_deadlift",
        "Peso muerto convencional" to "conventional_deadlift",
        "Peso muerto rumano" to "romanian_deadlift",
        "Press de banca" to "bench_press",
        "Press por encima de la cabeza" to "military_press",
        "Remo" to "conventional_row",
        "Salto" to null,
        "Sentadilla" to null,
        "Sentadilla frontal" to "front_squat",
        "Sentadilla profunda" to null,
        "Sentadilla unilateral" to null,
    )

    /** All static references audited together, preserving the 66 legacy IDs
     * and the separate display-label decisions above. */
    val allLegacyExerciseDecisions: Map<String, String?> =
        legacyExerciseDecisions + legacyExerciseNameDecisions

    fun wikiLabMuscleId(catalogMuscleId: String): String? =
        catalogMuscleToWikiLab[catalogMuscleId]

    fun wikiLabPatternId(catalogPatternId: String): String? =
        catalogPatternToWikiLab[catalogPatternId]

    fun catalogMuscleIdsForWikiLabEntity(wikiLabMuscleId: String): Set<String> =
        catalogMuscleToWikiLab
            .filterValues { it == wikiLabMuscleId }
            .keys
            .toSet() + wikiLabAggregateMusclesToCatalog[wikiLabMuscleId].orEmpty()

    fun legacyExerciseId(legacyId: String): String? =
        allLegacyExerciseDecisions[legacyId]
}
