package com.example.kpkn.data.exercises

import com.example.kpkn.R

data class ExerciseTechniqueImageVariant(
    val implementation: String,
    val imageResId: Int,
    val label: String,
)

object ExerciseTechniqueImageLookup {
    const val FLAT_BENCH_PRESS_DEFINITION_ID = "bench_press"
    const val PAUSED_BENCH_PRESS_DEFINITION_ID = "paused_bench_press"
    const val HIGH_BAR_BACK_SQUAT_DEFINITION_ID = "high_bar_back_squat"
    const val LOW_BAR_BACK_SQUAT_DEFINITION_ID = "low_bar_back_squat"
    const val CONVENTIONAL_DEADLIFT_DEFINITION_ID = "conventional_deadlift"
    const val FRONT_SQUAT_DEFINITION_ID = "front_squat"
    const val GOBLET_SQUAT_DEFINITION_ID = "quads_sentadilla_copa"
    const val BULGARIAN_SPLIT_SQUAT_DEFINITION_ID = "bulgarian_split_squat"
    const val SUMO_SQUAT_DEFINITION_ID = "sumo_squat"
    const val HACK_SQUAT_DEFINITION_ID = "quads_sentadilla_hack"
    const val HACK_SQUAT_INVERTED_DEFINITION_ID = "quads_sentadilla_hack_invertida_maquina"
    const val ANDERSON_SQUAT_DEFINITION_ID = "quads_sentadilla_anderson"
    const val ANDERSON_FRONT_SQUAT_DEFINITION_ID = "quads_sentadilla_anderson_frontal_barra_recta"
    const val BAZUCA_SQUAT_DEFINITION_ID = "quads_sentadilla_bazuca"
    const val SOMERSAULT_SQUAT_DEFINITION_ID = "quads_sentadilla_somersault"
    const val V_SQUAT_DEFINITION_ID = "quads_sentadilla_v_squat"
    const val V_SQUAT_INVERTED_DEFINITION_ID = "quads_sentadilla_v_squat_invertida_maquina"
    const val SISSY_SQUAT_DEFINITION_ID = "sissy_squat"
    const val BULGARIAN_ZERCHER_DEFINITION_ID = "bulgarian_zercher"
    const val PENDULUM_SQUAT_DEFINITION_ID = "pendulum_squat"
    const val BELT_SQUAT_DEFINITION_ID = "belt_squat"
    const val ZERCHER_SQUAT_DEFINITION_ID = "quads_sentadilla_zercher_barra_recta"
    const val SUMO_DEADLIFT_DEFINITION_ID = "sumo_deadlift"
    const val ROMANIAN_DEADLIFT_DEFINITION_ID = "romanian_deadlift"
    const val ROMANIAN_SUMO_DEADLIFT_DEFINITION_ID = "romanian_sumo_deadlift"
    const val STIFF_LEG_DEADLIFT_DEFINITION_ID = "stiff_leg_deadlift"
    const val GOOD_MORNING_DEFINITION_ID = "good_morning"
    const val GOOD_MORNING_SEATED_DEFINITION_ID = "good_morning_seated"
    const val GOOD_MORNING_ZERCHER_DEFINITION_ID = "good_morning_zercher"
    const val ROMANIAN_ZERCHER_DEADLIFT_DEFINITION_ID = "hams_peso_muerto_rumano_zercher"
    const val PULL_THROUGH_DEFINITION_ID = "hams_pull_through"
    const val HIP_THRUST_DEFINITION_ID = "hip_thrust"
    const val CONVENTIONAL_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_convencional_deficit"
    const val SUMO_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_sumo_deficit"
    const val STIFF_LEG_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_piernas_rigidas_deficit"
    const val ROMANIAN_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_rumano_deficit"
    const val ROMANIAN_SUMO_DEADLIFT_DEFICIT_DEFINITION_ID = "hams_peso_muerto_rumano_sumo_deficit"
    const val KETTLEBELL_SWING_TWO_HANDS_DEFINITION_ID = "hams_swing_kettlebell_dos_manos"
    const val KETTLEBELL_SWING_ONE_HAND_DEFINITION_ID = "hams_swing_kettlebell_unilateral"
    const val STANDING_BICEPS_CURL_DEFINITION_ID = "standing_biceps_curl"
    const val SEATED_BICEPS_CURL_DEFINITION_ID = "biceps_curl_sentado_banco_plano"
    const val PREACHER_CURL_DEFINITION_ID = "preacher_curl"
    const val SPIDER_CURL_DEFINITION_ID = "spider_curl"
    const val CONCENTRATION_CURL_DEFINITION_ID = "concentration_curl"
    const val BAYESIAN_CURL_DEFINITION_ID = "biceps_curl_bayesian"
    const val HAMMER_CURL_DEFINITION_ID = "hammer_curl"
    const val REVERSE_CURL_DEFINITION_ID = "reverse_curl"
    const val DRAG_CURL_DEFINITION_ID = "biceps_curl_drag"
    const val ZOTTMAN_CURL_DEFINITION_ID = "biceps_curl_zottman"
    const val WAITER_CURL_DEFINITION_ID = "biceps_curl_waiter"
    const val CRUCIFIX_CURL_DEFINITION_ID = "biceps_curl_crucifijo"
    const val SUPERMAN_CURL_DEFINITION_ID = "biceps_curl_superman"
    const val TRX_BICEPS_CURL_DEFINITION_ID = "biceps_curl_trx"
    const val TRICEPS_PUSHDOWN_DEFINITION_ID = "triceps_pushdown"
    const val OVERHEAD_TRICEPS_DEFINITION_ID = "overhead_triceps_extension"
    const val FRENCH_PRESS_DEFINITION_ID = "triceps_press_frances"
    const val JM_PRESS_DEFINITION_ID = "jm_press"
    const val CALIFORNIA_PRESS_DEFINITION_ID = "california_press"
    const val TATE_PRESS_DEFINITION_ID = "tate_press"
    const val TRICEPS_KICKBACK_DEFINITION_ID = "triceps_patada"
    const val KATANA_EXTENSION_DEFINITION_ID = "katana_extension"
    const val CROSSBODY_TRICEPS_DEFINITION_ID = "crossbody_triceps_extension"
    const val TRICEPS_TRX_DEFINITION_ID = "triceps_extension"
    const val TRICEPS_PJR_DEFINITION_ID = "triceps_extension_pjr_mancuerna"
    const val SPHINX_PUSHUP_DEFINITION_ID = "triceps_flexiones_esfinge"
    const val BENCH_DIPS_DEFINITION_ID = "triceps_fondos_entre_bancos"
    const val TRICEPS_MACHINE_PRESS_DEFINITION_ID = "triceps_press_maquina"
    const val ROLLING_EXTENSION_DEFINITION_ID = "triceps_rolling_extension"
    const val INCLINE_BENCH_PRESS_DEFINITION_ID = "incline_bench_press"
    const val DECLINE_BENCH_PRESS_DEFINITION_ID = "decline_bench_press"
    const val FLAT_CHEST_FLY_DEFINITION_ID = "flat_chest_fly"
    const val INCLINE_CHEST_FLY_DEFINITION_ID = "incline_chest_fly"
    const val DECLINE_CHEST_FLY_DEFINITION_ID = "decline_chest_fly"
    const val FLOOR_PRESS_DEFINITION_ID = "floor_press"
    const val LYING_PULLOVER_DEFINITION_ID = "lying_pullover"
    const val SEATED_MACHINE_PULLOVER_DEFINITION_ID = "seated_machine_pullover"
    const val PUSH_UP_DEFINITION_ID = "push_up"
    const val CABLE_CROSSOVER_DEFINITION_ID = "tren_superior_cruce_poleas"
    const val PARALLEL_BAR_DIP_DEFINITION_ID = "tren_superior_fondos"
    const val RESISTANCE_BAND_CHEST_PRESS_DEFINITION_ID = "tren_superior_press_banda_resistencia"
    const val FLAT_CONVERGING_CHEST_PRESS_DEFINITION_ID = "tren_superior_press_pecho_maquina_convergente"
    const val SPOTO_PRESS_DEFINITION_ID = "tren_superior_press_spoto_barra"
    const val UNILATERAL_CABLE_CHEST_PRESS_DEFINITION_ID = "tren_superior_press_unilateral_polea"
    const val SQUEEZE_PRESS_DEFINITION_ID = "tren_superior_squeeze_press_mancuernas"
    const val INCLINE_CONVERGING_CHEST_PRESS_DEFINITION_ID = "tren_superior_press_inclinado_maquina_convergente"
    const val STANDING_CABLE_PULLOVER_DEFINITION_ID = "pullover"
    const val PULL_UP_DEFINITION_ID = "pull_up"
    const val GIRONDA_ROW_DEFINITION_ID = "gironda_row"
    const val BAND_PULL_APART_DEFINITION_ID = "back_band_pull_apart"
    const val GORILLA_ROW_DEFINITION_ID = "back_remo_gorilla_mancuernas"
    const val INVERTED_ROW_DEFINITION_ID = "back_remo_invertido"
    const val RENEGADE_ROW_DEFINITION_ID = "back_remo_renegado_mancuernas"
    const val PENDLAY_ROW_DEFINITION_ID = "pendlay_row"
    const val BAND_ROW_DEFINITION_ID = "back_remo_banda"
    const val FACE_PULL_DEFINITION_ID = "deltoides_face_pull"
    const val BENCH_PRESS_CHAINS_DEFINITION_ID = "tren_superior_press_banca_cadenas"
    const val ARNOLD_PRESS_DEFINITION_ID = "arnold_press"
    const val MILITARY_PRESS_DEFINITION_ID = "military_press"
    const val SEATED_SHOULDER_PRESS_DEFINITION_ID = "seated_shoulder_press"
    const val FRONT_RAISE_DEFINITION_ID = "deltoides_elevaciones_frontales"
    const val SEATED_LATERAL_RAISE_DEFINITION_ID = "seated_lateral_raise"
    const val STANDING_LATERAL_RAISE_DEFINITION_ID = "standing_lateral_raise"
    const val REAR_DELT_RAISE_DEFINITION_ID = "rear_delt_raise"
    const val Z_PRESS_DEFINITION_ID = "z_press"
    const val SHRUG_DEFINITION_ID = "back_encogimientos"
    const val Y_RAISE_DEFINITION_ID = "back_y_raises"
    const val INCLINE_BENCH_Y_RAISE_DEFINITION_ID = "deltoides_y_raises_sentado_banco_inclinado"
    const val SCAPULAR_PULL_UP_DEFINITION_ID = "back_dominadas_escapulares"
    const val PUSH_PRESS_DEFINITION_ID = "deltoides_push_press"
    const val UNILATERAL_LANDMINE_PRESS_DEFINITION_ID = "deltoides_press_landmine_unilateral"
    const val UPRIGHT_ROW_DEFINITION_ID = "deltoides_remo_menton"
    const val FOREARM_PLANK_DEFINITION_ID = "core_plancha"
    const val DRAGON_FLAG_DEFINITION_ID = "core_dragon_flag_banco_plano"
    const val FROG_PUMPS_DEFINITION_ID = "glutes_frog_pumps"
    const val FLOOR_CRUNCH_DEFINITION_ID = "core_crunch_suelo_peso_corporal"
    const val LEG_RAISE_DEFINITION_ID = "core_elevacion_piernas"
    const val SIDE_BEND_DEFINITION_ID = "core_inclinacion_lateral"
    const val AB_WHEEL_DEFINITION_ID = "core_rueda_abdominal"
    const val CLAMSHELL_DEFINITION_ID = "glutes_clamshells_banda"
    const val MONSTER_WALK_DEFINITION_ID = "glutes_monster_walk_banda"
    const val GLUTE_STEP_UP_DEFINITION_ID = "glutes_step_up_gluteo"
    const val COSSACK_SQUAT_DEFINITION_ID = "quads_sentadilla_cosaca"
    const val PISTOL_SQUAT_DEFINITION_ID = "quads_sentadilla_pistola"
    const val REVERSE_LUNGE_DEFINITION_ID = "reverse_lunge"
    const val REVERSE_PEC_FLY_DEFINITION_ID = "reverse_pec_fly"
    const val CALF_RAISE_DEFINITION_ID = "calf_raise"
    const val LYING_LEG_CURL_DEFINITION_ID = "lying_leg_curl"
    const val STEP_UP_DEFINITION_ID = "step_up"
    const val GLUTE_BRIDGE_DEFINITION_ID = "glutes_puente_gluteos"
    const val HIP_ABDUCTION_DEFINITION_ID = "hip_abduction"
    const val HIP_ADDUCTION_DEFINITION_ID = "hip_adduction"
    const val SEATED_LEG_CURL_DEFINITION_ID = "seated_leg_curl"
    const val STANDING_LEG_CURL_DEFINITION_ID = "standing_leg_curl"
    const val SLIDER_LEG_CURL_DEFINITION_ID = "curl_isquios_con_sliders"
    const val BALL_LEG_CURL_DEFINITION_ID = "curl_isquios_con_balon"
    const val NORDIC_CURL_DEFINITION_ID = "hams_curl_nordic_peso_corporal"
    const val REVERSE_NORDIC_CURL_DEFINITION_ID = "quads_reverse_nordic_peso_corporal"
    const val COPENHAGEN_PLANK_DEFINITION_ID = "copenhagen_plank"
    const val DYNAMIC_COPENHAGEN_PLANK_DEFINITION_ID = "copenhagen_plank_dynamic"
    const val MACHINE_CRUNCH_DEFINITION_ID = "core_crunch_maquina"
    const val DECLINE_PLATE_CRUNCH_DEFINITION_ID = "core_crunch_banco_declinado_lastrado_disco"
    const val HIGH_CABLE_CRUNCH_DEFINITION_ID = "core_crunch_en_polea_alta"
    const val CABLE_WOODCHOPPER_DEFINITION_ID = "core_lenador_polea"
    const val PALLOF_PRESS_DEFINITION_ID = "core_press_pallof"
    const val FLOOR_SUPERMAN_DEFINITION_ID = "back_superman_suelo"
    const val CONVENTIONAL_ROW_DEFINITION_ID = "conventional_row"
    const val CHEST_SUPPORTED_ROW_DEFINITION_ID = "chest_supported_row"
    const val LAT_PULLDOWN_DEFINITION_ID = "lat_pulldown"
    const val T_BAR_ROW_DEFINITION_ID = "t_bar_row"
    const val SEAL_ROW_DEFINITION_ID = "seal_row"
    const val WRIST_CURL_DEFINITION_ID = "forearms_curl_muneca_sentado"
    const val WRIST_EXTENSION_DEFINITION_ID = "forearms_curl_muneca_inverso_sentado"
    const val BEHIND_BACK_WRIST_CURL_DEFINITION_ID = "forearms_curl_muneca_de_pie_tras_espalda_barra"
    const val WRIST_ROLLER_DEFINITION_ID = "forearms_enrollamiento_muneca_rodillo"
    const val SUPINATION_DEFINITION_ID = "supination"
    const val PRONATION_DEFINITION_ID = "pronation"
    const val FARMER_WALK_DEFINITION_ID = "forearms_paseo_del_granjero"
    const val DEAD_HANG_DEFINITION_ID = "forearms_suspension_isometrica_barra_fija"
    const val PLATE_PINCH_DEFINITION_ID = "forearms_pinza_de_discos"

    const val HOLLOW_BODY_HOLD_DEFINITION_ID = "hollow_body_hold"
    const val BIRD_DOG_DEFINITION_ID = "bird_dog"
    const val SIDE_PLANK_DEFINITION_ID = "side_plank"
    const val FORWARD_LUNGE_DEFINITION_ID = "forward_lunge"
    const val SUITCASE_CARRY_DEFINITION_ID = "suitcase_carry"
    const val KNEE_PUSH_UP_DEFINITION_ID = "knee_push_up"
    const val DIAMOND_PUSH_UP_DEFINITION_ID = "diamond_push_up"
    const val ARCHER_PUSH_UP_DEFINITION_ID = "archer_push_up"
    const val PIKE_PUSH_UP_DEFINITION_ID = "pike_push_up"
    const val NEGATIVE_PULL_UP_DEFINITION_ID = "negative_pull_up"
    const val POWER_CLEAN_DEFINITION_ID = "power_clean"
    const val HANG_POWER_CLEAN_DEFINITION_ID = "hang_power_clean"
    const val SQUAT_CLEAN_DEFINITION_ID = "squat_clean"
    const val CLEAN_PULL_DEFINITION_ID = "clean_pull"
    const val POWER_SNATCH_DEFINITION_ID = "power_snatch"
    const val HANG_POWER_SNATCH_DEFINITION_ID = "hang_power_snatch"
    const val SQUAT_SNATCH_DEFINITION_ID = "squat_snatch"
    const val SNATCH_PULL_DEFINITION_ID = "snatch_pull"
    const val PUSH_JERK_DEFINITION_ID = "push_jerk"
    const val SPLIT_JERK_DEFINITION_ID = "split_jerk"

    const val DEAD_BUG_DEFINITION_ID = "dead_bug"
    const val CLOSE_GRIP_BENCH_PRESS_DEFINITION_ID = "close_grip_bench_press"
    const val INCLINE_BICEPS_CURL_DEFINITION_ID = "incline_biceps_curl"
    const val QUADS_PRENSA_PIERNAS_DEFINITION_ID = "quads_prensa_piernas"
    const val QUADS_SENTADILLA_SIN_CARGA_DEFINITION_ID = "quads_sentadilla_sin_carga"
    const val PAUSED_BACK_SQUAT_DEFINITION_ID = "paused_back_squat"
    const val OVERHEAD_SQUAT_DEFINITION_ID = "overhead_squat"
    const val QUADS_SENTADILLA_JEFFERSON_DEFINITION_ID = "quads_sentadilla_jefferson"
    const val QUADS_EXTENSION_CUADRICEPS_PIE_POLEA_DEFINITION_ID = "quads_extension_cuadriceps_pie_polea"
    const val QUADS_SENTADILLA_CAJON_DEFINITION_ID = "quads_sentadilla_cajon"
    const val QUADS_SENTADILLA_SUMO_FRONTAL_DEFINITION_ID = "quads_sentadilla_sumo_frontal"
    const val GLUTE_HAM_RAISE_DEFINITION_ID = "glute_ham_raise"
    const val ZERCHER_CARRY_DEFINITION_ID = "zercher_carry"
    const val BACK_JEFFERSON_CURL_DEFINITION_ID = "back_jefferson_curl"
    const val GLUTES_ZANCADA_CRUZADA_DEFINITION_ID = "glutes_zancada_cruzada"
    const val DEADLIFT_TO_KNEES_DEFINITION_ID = "deadlift_to_knees"
    const val QUADS_SENTADILLA_PISTOLA_ASISTIDA_TRX_DEFINITION_ID = "quads_sentadilla_pistola_asistida_trx"
    const val QUADS_STEP_UP_CAJON_FRONTAL_DEFINITION_ID = "quads_step_up_cajon_frontal"
    const val QUADS_STEP_UP_CAJON_ZERCHER_DEFINITION_ID = "quads_step_up_cajon_zercher"
    const val QUADS_ZANCADA_CAMINANDO_FRONTAL_BARRA_RECTA_DEFINITION_ID = "quads_zancada_caminando_frontal_barra_recta"
    const val WALKING_LUNGE_DEFINITION_ID = "walking_lunge"

    const val QUADS_EXTENSION_CUADRICEPS_DEFINITION_ID = "quads_extension_cuadriceps"
    const val BACK_HIPEREXTENSION_45_ZERCHER_ESPALDA_BAJA_DEFINITION_ID = "back_hiperextension_45_zercher_espalda_baja"
    const val QUADS_ZANCADA_CAMINANDO_ZERCHER_BARRA_RECTA_DEFINITION_ID = "quads_zancada_caminando_zercher_barra_recta"
    const val QUADS_ZANCADA_FRONTAL_ZERCHER_DEFINITION_ID = "quads_zancada_frontal_zercher"
    const val QUADS_SENTADILLA_SUMO_ZERCHER_DEFINITION_ID = "quads_sentadilla_sumo_zercher"

    private val NON_IMPLEMENT_TOKENS = setOf(
        "bilateral",
        "unilateral",
        "left",
        "right",
        "supinated",
        "neutral",
        "pronated",
    )

    private val KNOWN_EQUIPMENT_TOKENS = setOf(
        "ab_wheel", "band", "barbell", "bodyweight", "cable", "dumbbells", "ez_bar",
        "ghd", "h_bar", "hex_bar", "kettlebell", "leg_press_machine", "machine", "plate", "safety_bar",
        "seated_machine", "donkey_machine", "sliders", "smith_machine", "t_bar", "trx", "wrist_roller",
    )

    fun variants(definitionId: String): List<ExerciseTechniqueImageVariant> = when (definitionId) {
        QUADS_EXTENSION_CUADRICEPS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_quads_extension_cuadriceps_machine_batch20261008b, "Máquina"),
        )
        BACK_HIPEREXTENSION_45_ZERCHER_ESPALDA_BAJA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_back_hiperextension_45_zercher_espalda_baja_default, "Barra"),
        )
        QUADS_ZANCADA_CAMINANDO_ZERCHER_BARRA_RECTA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_zancada_caminando_zercher_barra_recta_barbell_batch20261008b, "Barra"),
        )
        QUADS_ZANCADA_FRONTAL_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_zancada_frontal_zercher_barbell_batch20261008b, "Barra"),
        )
        QUADS_SENTADILLA_SUMO_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_sentadilla_sumo_zercher_barbell, "Barra"),
        )
        "rack_chin" -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_rack_chin_bodyweight, "Peso corporal"),
        )
        "neck_flexion_lateral_cuello" -> listOf(
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_neck_flexion_lateral_cuello_plate, "Disco"),
        )
        "glutes_patada_gluteo_lateral" -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_glutes_patada_gluteo_lateral_cable, "Polea"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_glutes_patada_gluteo_lateral_band, "Banda"),
        )
        "neck_extension_cuello" -> listOf(
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_neck_extension_cuello_plate, "Disco"),
        )
        "neck_flexion_cuello" -> listOf(
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_neck_flexion_cuello_plate, "Disco"),
        )
        "quads_zancada_inversa_frontal" -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_zancada_inversa_frontal_barbell, "Barra"),
        )
        "quads_zancada_inversa_zercher" -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_zancada_inversa_zercher_barbell, "Barra"),
        )
        "lateral_raise_super_rom" -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_lateral_raise_super_rom_dumbbells, "Mancuernas"),
        )
        "back_encogimientos_kelso" -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_back_encogimientos_kelso_barbell, "Barra"),
        )
        "deltoides_lu_raises" -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_deltoides_lu_raises_dumbbells, "Mancuernas"),
        )
        DEAD_BUG_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_dead_bug_bodyweight_batch20261008, "Peso corporal"),
        )
        CLOSE_GRIP_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_close_grip_bench_press_barbell_batch20261008, "Barra"),
        )
        INCLINE_BICEPS_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_incline_biceps_curl_dumbbells_batch20261008, "Mancuernas"),
        )
        QUADS_PRENSA_PIERNAS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_quads_prensa_piernas_machine_batch20261008b, "Máquina"),
        )
        QUADS_SENTADILLA_SIN_CARGA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_quads_sentadilla_sin_carga_bodyweight_batch20261008b, "Peso corporal"),
        )
        PAUSED_BACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_paused_back_squat_barbell_batch20261008b, "Barra"),
        )
        OVERHEAD_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_overhead_squat_barbell_batch20261008b, "Barra"),
        )
        QUADS_SENTADILLA_JEFFERSON_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_sentadilla_jefferson_barbell_batch20261008b, "Barra"),
        )
        QUADS_EXTENSION_CUADRICEPS_PIE_POLEA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_quads_extension_cuadriceps_pie_polea_cable_batch20261008b, "Polea"),
        )
        QUADS_SENTADILLA_CAJON_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_sentadilla_cajon_barbell_batch20261008b, "Barra"),
        )
        QUADS_SENTADILLA_SUMO_FRONTAL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_sentadilla_sumo_frontal_barbell_batch20261008b, "Barra"),
        )
        GLUTE_HAM_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("ghd", R.drawable.exercise_glute_ham_raise_ghd_batch20261008b, "GHD"),
        )
        ZERCHER_CARRY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_zercher_carry_barbell_batch20261008b, "Barra"),
        )
        BACK_JEFFERSON_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_back_jefferson_curl_barbell_batch20261008b, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_back_jefferson_curl_dumbbells_batch20261008b, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_back_jefferson_curl_cable_batch20261008b, "Polea"),
        )
        GLUTES_ZANCADA_CRUZADA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_glutes_zancada_cruzada_dumbbells_batch20261008b, "Mancuernas"),
        )
        DEADLIFT_TO_KNEES_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_deadlift_to_knees_barbell_batch20261008b, "Barra"),
        )
        QUADS_SENTADILLA_PISTOLA_ASISTIDA_TRX_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("trx", R.drawable.exercise_quads_sentadilla_pistola_asistida_trx_trx_batch20261008b, "TRX"),
        )
        QUADS_STEP_UP_CAJON_FRONTAL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_step_up_cajon_frontal_barbell_batch20261008b, "Barra"),
        )
        QUADS_STEP_UP_CAJON_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_step_up_cajon_zercher_barbell_batch20261008b, "Barra"),
        )
        QUADS_ZANCADA_CAMINANDO_FRONTAL_BARRA_RECTA_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_quads_zancada_caminando_frontal_barra_recta_barbell_batch20261008b, "Barra"),
        )
        WALKING_LUNGE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_walking_lunge_barbell_batch20261008b, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_walking_lunge_dumbbells_batch20261008b, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_walking_lunge_kettlebell_batch20261008b, "Kettlebell"),
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_walking_lunge_bodyweight_batch20261008b, "Peso corporal"),
        )
        HOLLOW_BODY_HOLD_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_hollow_body_hold_bodyweight_batch20261008, "Peso corporal"),
        )
        BIRD_DOG_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_bird_dog_bodyweight_batch20261008, "Peso corporal"),
        )
        SIDE_PLANK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_side_plank_bodyweight_batch20261008, "Peso corporal"),
        )
        FORWARD_LUNGE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_forward_lunge_bodyweight_batch20261008, "Peso corporal"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_forward_lunge_dumbbells_batch20261008, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_forward_lunge_kettlebell_batch20261008, "Kettlebell"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_forward_lunge_barbell_batch20261008, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_forward_lunge_smith_machine_batch20261008, "Máquina Smith"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_forward_lunge_cable_batch20261008, "Polea"),
        )
        SUITCASE_CARRY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_suitcase_carry_dumbbells_batch20261008, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_suitcase_carry_kettlebell_batch20261008, "Kettlebell"),
        )
        KNEE_PUSH_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_knee_push_up_bodyweight_batch20261008, "Peso corporal"),
        )
        DIAMOND_PUSH_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_diamond_push_up_bodyweight_batch20261008, "Peso corporal"),
        )
        ARCHER_PUSH_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_archer_push_up_bodyweight_batch20261008, "Peso corporal"),
        )
        PIKE_PUSH_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_pike_push_up_bodyweight_batch20261008, "Peso corporal"),
        )
        NEGATIVE_PULL_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_negative_pull_up_bodyweight_batch20261008, "Peso corporal"),
        )
        POWER_CLEAN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_power_clean_barbell_batch20261008, "Barra"),
        )
        HANG_POWER_CLEAN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_hang_power_clean_barbell_batch20261008, "Barra"),
        )
        SQUAT_CLEAN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_squat_clean_barbell_batch20261008, "Barra"),
        )
        CLEAN_PULL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_clean_pull_barbell_batch20261008, "Barra"),
        )
        POWER_SNATCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_power_snatch_barbell_batch20261008, "Barra"),
        )
        HANG_POWER_SNATCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_hang_power_snatch_barbell_batch20261008, "Barra"),
        )
        SQUAT_SNATCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_squat_snatch_barbell_batch20261008, "Barra"),
        )
        SNATCH_PULL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_snatch_pull_barbell_batch20261008, "Barra"),
        )
        PUSH_JERK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_push_jerk_barbell_batch20261008, "Barra"),
        )
        SPLIT_JERK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_split_jerk_barbell_batch20261008, "Barra"),
        )
        HIGH_BAR_BACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_trasera_barra_alta, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_trasera_barra_alta_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_sentadilla_trasera_barra_alta_safety_bar, "Barra de Seguridad"),
        )
        LOW_BAR_BACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_trasera_barra_baja, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_trasera_barra_baja_smith, "Máquina Smith"),
        )
        FRONT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_frontal, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_frontal_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_frontal_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_frontal_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_sentadilla_frontal_polea, "Polea"),
        )
        GOBLET_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_quads_sentadilla_copa_batch8, "Mancuerna"),
        )
        SUMO_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_sumo, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_sumo_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_sumo_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_sumo_squat_bodyweight, "Peso corporal"),
        )
        BULGARIAN_SPLIT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_bulgarian_split_squat_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_bulgara_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_bulgara_barra, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_bulgara_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_bulgara_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_sentadilla_bulgara_polea, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_sentadilla_bulgara_kettlebell, "Kettlebell"),
        )
        BULGARIAN_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell__zercher", R.drawable.exercise_sentadilla_bulgara_zercher, "Barra Zercher"),
        )
        HACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_hack_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_sentadilla_hack_barra, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_hack_smith, "Máquina Smith"),
        )
        HACK_SQUAT_INVERTED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_hack_invertida, "Máquina"),
        )
        ANDERSON_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_anderson, "Barra"),
        )
        ANDERSON_FRONT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_anderson_frontal, "Barra"),
        )
        BAZUCA_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_bazuca, "Barra"),
        )
        SOMERSAULT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_somersault, "Barra"),
        )
        V_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_v_squat, "Máquina"),
        )
        V_SQUAT_INVERTED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_v_squat_invertida, "Máquina"),
        )
        SISSY_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_sissy_squat_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_sentadilla_sissy_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_sentadilla_sissy_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_sentadilla_sissy_mancuernas, "Mancuerna"),
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_sentadilla_sissy_disco, "Disco"),
        )
        PENDULUM_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_pendulo, "Máquina Pendular"),
        )
        BELT_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_belt_squat, "Máquina Belt Squat"),
        )
        ZERCHER_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_sentadilla_zercher, "Barra Recta"),
        )
        CONVENTIONAL_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_convencional, "Barra Libre"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_convencional_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_convencional_hex_bar, "Barra Hexagonal"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_convencional_mancuernas, "Mancuernas"),
        )
        SUMO_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_sumo, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_sumo_mancuernas, "Mancuernas"),
        )
        ROMANIAN_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_rumano, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_rumano_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_rumano_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_rumano_hex_bar, "Barra Hexagonal"),
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_romanian_deadlift_bodyweight, "Peso corporal"),
        )
        ROMANIAN_SUMO_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_rumano_sumo, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_rumano_sumo_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_rumano_sumo_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_rumano_sumo_hex_bar, "Barra Hexagonal"),
        )
        STIFF_LEG_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_peso_muerto_piernas_rigidas, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_peso_muerto_piernas_rigidas_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_peso_muerto_piernas_rigidas_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_peso_muerto_piernas_rigidas_hex_bar, "Barra Hexagonal"),
        )
        GOOD_MORNING_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_buenos_dias, "Barra"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_buenos_dias_safety_bar, "Barra de Seguridad"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_buenos_dias_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_buenos_dias_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_good_morning_bodyweight, "Peso corporal"),
        )
        GOOD_MORNING_SEATED_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_buenos_dias_sentado, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_buenos_dias_sentado_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("safety_bar", R.drawable.exercise_buenos_dias_sentado_safety_bar, "Barra de Seguridad"),
        )
        GOOD_MORNING_ZERCHER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_buenos_dias_zercher, "Barra Zercher"),
        )
        ROMANIAN_ZERCHER_DEADLIFT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_buenos_dias_zercher, "Barra Zercher"),
        )
        PULL_THROUGH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_pull_through, "Polea"),
        )
        HIP_THRUST_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_hip_thrust, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_hip_thrust_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_hip_thrust_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_hip_thrust_banda, "Banda"),
        )
        CONVENTIONAL_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_convencional_deficit, "Déficit"),
        )
        SUMO_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_sumo_deficit, "Déficit"),
        )
        STIFF_LEG_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_piernas_rigidas_deficit, "Déficit"),
        )
        ROMANIAN_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_rumano_deficit, "Déficit"),
        )
        ROMANIAN_SUMO_DEADLIFT_DEFICIT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_peso_muerto_rumano_sumo_deficit, "Déficit"),
        )
        KETTLEBELL_SWING_TWO_HANDS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_hams_swing_kettlebell_dos_manos_batch8, "Kettlebell"),
        )
        KETTLEBELL_SWING_ONE_HAND_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_hams_swing_kettlebell_unilateral_batch8, "Kettlebell"),
        )
        STANDING_BICEPS_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_curl_biceps_pie_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_curl_biceps_pie_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_biceps_pie_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_biceps_pie_polea, "Polea"),
        )
        SEATED_BICEPS_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_biceps_sentado_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_biceps_sentado_polea, "Polea"),
        )
        PREACHER_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_curl_predicador_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_curl_predicador_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_predicador_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_curl_predicador_maquina, "Máquina"),
        )
        SPIDER_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_arana_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_arana_polea, "Polea"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_curl_arana_barra, "Barra"),
        )
        CONCENTRATION_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_concentrado_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_concentrado_polea, "Polea"),
        )
        BAYESIAN_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_bayesian_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_bayesian_polea, "Polea"),
        )
        HAMMER_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("h_bar", R.drawable.exercise_curl_martillo_h_bar, "Barra H"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_martillo_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_martillo_polea, "Polea"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_curl_martillo_banda, "Banda"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_curl_martillo_kettlebell, "Kettlebell"),
        )
        REVERSE_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_invertido_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_invertido_polea, "Polea"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_curl_invertido_banda, "Banda"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_curl_invertido_kettlebell, "Kettlebell"),
        )
        DRAG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_curl_drag_barra, "Barra"),
        )
        ZOTTMAN_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_zottman_mancuernas, "Mancuernas"),
        )
        WAITER_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_curl_waiter_disco, "Disco"),
        )
        CRUCIFIX_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_curl_crucifijo, "Polea"),
        )
        SUPERMAN_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_curl_superman, "Polea"),
        )
        TRX_BICEPS_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_curl_biceps_trx, "TRX"),
        )
        TRICEPS_PUSHDOWN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_triceps_pushdown_polea, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_triceps_pushdown_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_triceps_pushdown_banda, "Banda"),
        )
        OVERHEAD_TRICEPS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_triceps_overhead_barra, "Barra"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_triceps_overhead_maquina, "Máquina"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_triceps_overhead_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_triceps_overhead_polea, "Polea"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_overhead_triceps_extension_ez_bar, "Barra EZ"),
        )
        FRENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_press_frances_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_press_frances_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_press_frances_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_press_frances_polea, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_press_frances_kettlebell, "Kettlebell"),
        )
        JM_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_jm_press_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_jm_press_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_jm_press_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_jm_press_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_jm_press_polea, "Polea"),
        )
        CALIFORNIA_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_press_california_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_press_california_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_press_california_mancuernas, "Mancuernas"),
        )
        TATE_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_tate_press_mancuernas, "Mancuernas"),
        )
        TRICEPS_KICKBACK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_triceps_patada_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_triceps_patada_polea, "Polea"),
        )
        KATANA_EXTENSION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_triceps_katana_polea, "Polea"),
        )
        CROSSBODY_TRICEPS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_triceps_cruzada_polea, "Polea"),
        )
        TRICEPS_TRX_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_extension_trx, "TRX"),
        )
        TRICEPS_PJR_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_pjr_mancuerna, "Mancuerna"),
        )
        SPHINX_PUSHUP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_flexion_esfinge, "Peso corporal"),
        )
        BENCH_DIPS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_fondos_bancos, "Bancos"),
        )
        TRICEPS_MACHINE_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_press_maquina, "Máquina"),
        )
        ROLLING_EXTENSION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_triceps_rolling_extension, "Mancuernas"),
        )
        WRIST_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_curl_muneca_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_curl_muneca_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_curl_muneca_ez, "Barra EZ"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_curl_muneca_polea, "Polea"),
        )
        WRIST_EXTENSION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_extension_muneca_polea, "Polea"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_extension_muneca_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_extension_muneca_barra, "Barra"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_extension_muneca_ez, "Barra EZ"),
        )
        BEHIND_BACK_WRIST_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_curl_muneca_tras_espalda, "Barra"),
        )
        WRIST_ROLLER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_enrollamiento_muneca_rodillo, "Rodillo"),
        )
        SUPINATION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_supinacion_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_supinacion_polea, "Polea"),
        )
        PRONATION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_pronacion_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_pronacion_polea, "Polea"),
        )
        FARMER_WALK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_paseo_granjero_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_paseo_granjero_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_paseo_granjero_discos, "Discos"),
            ExerciseTechniqueImageVariant("hex_bar", R.drawable.exercise_paseo_granjero_hex, "Hex bar"),
        )
        DEAD_HANG_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_dead_hang, "Barra fija"),
        )
        PLATE_PINCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_pinza_discos, "Discos"),
        )
        FLAT_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_press_de_banca_plano, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_press_de_banca_plano_mancuernas, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_press_de_banca_plano_smith, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_press_de_banca_plano_maquina_convergente, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_press_de_banca_plano_polea, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_press_de_banca_plano_kettlebell, "Kettlebell"),
        )
        INCLINE_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_incline_bench_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_incline_bench_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_incline_bench_press_smith_machine, "Máquina Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_incline_bench_press_machine, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_incline_bench_press_cable, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_incline_bench_press_kettlebell, "Kettlebell"),
        )
        DECLINE_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_decline_bench_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_decline_bench_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_decline_bench_press_smith_machine, "Máquina Smith"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_decline_bench_press_kettlebell, "Kettlebell"),
        )
        FLAT_CHEST_FLY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_flat_chest_fly_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_flat_chest_fly_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_flat_chest_fly_machine, "Máquina"),
        )
        DECLINE_CHEST_FLY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_decline_chest_fly_dumbbells, "Mancuernas"),
        )
        INCLINE_CHEST_FLY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_incline_chest_fly_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_incline_chest_fly_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_incline_chest_fly_machine, "Máquina"),
        )
        FLOOR_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_floor_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_floor_press_dumbbells, "Mancuernas"),
        )
        LYING_PULLOVER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_lying_pullover_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_lying_pullover_barbell, "Barra"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_lying_pullover_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_lying_pullover_cable, "Polea"),
        )
        SEATED_MACHINE_PULLOVER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_seated_machine_pullover, "Máquina"),
        )
        PUSH_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_push_up_bodyweight, "Peso corporal"),
        )
        CABLE_CROSSOVER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_cable_crossover_mid, "Polea"),
        )
        PARALLEL_BAR_DIP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_parallel_bar_dip_bodyweight, "Peso corporal"),
        )
        RESISTANCE_BAND_CHEST_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_resistance_band_chest_press, "Banda de resistencia"),
        )
        FLAT_CONVERGING_CHEST_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_flat_converging_chest_press_machine, "Máquina convergente"),
        )
        SPOTO_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_spoto_press_barbell, "Barra"),
        )
        UNILATERAL_CABLE_CHEST_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_unilateral_cable_chest_press, "Polea"),
        )
        SQUEEZE_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_squeeze_press_dumbbells, "Mancuernas"),
        )
        INCLINE_CONVERGING_CHEST_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_incline_converging_chest_press_machine, "Máquina convergente"),
        )
        STANDING_CABLE_PULLOVER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_standing_cable_pullover, "Polea"),
        )
        PULL_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_pull_up_pronated_medium, "Agarre prono medio"),
        )
        GIRONDA_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_gironda_row_cable, "Polea"),
        )
        BAND_PULL_APART_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_band_pull_apart, "Banda de resistencia"),
        )
        GORILLA_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_gorilla_row_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_gorilla_row_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_back_remo_gorilla_mancuernas_cable, "Polea"),
        )
        INVERTED_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_inverted_row_bodyweight, "Peso corporal"),
        )
        RENEGADE_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_renegade_row_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_renegade_row_kettlebell, "Kettlebell"),
        )
        PENDLAY_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_pendlay_row_barbell, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_pendlay_row_smith_machine, "Smith"),
        )
        BAND_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_band_row, "Banda de resistencia"),
        )
        FACE_PULL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_face_pull_cable, "Polea"),
        )
        BENCH_PRESS_CHAINS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_bench_press_chains, "Barra con cadenas"),
        )
        ARNOLD_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_arnold_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_arnold_press_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_arnold_press_cable, "Polea"),
        )
        MILITARY_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_military_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_military_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_military_press_smith_machine, "Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_military_press_machine, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_military_press_cable, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_military_press_kettlebell, "Kettlebell"),
        )
        SEATED_SHOULDER_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_seated_shoulder_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_seated_shoulder_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_seated_shoulder_press_smith_machine, "Smith"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_seated_shoulder_press_machine, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_seated_shoulder_press_cable, "Polea"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_seated_shoulder_press_kettlebell, "Kettlebell"),
        )
        FRONT_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_front_raise_cable, "Polea"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_front_raise_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_front_raise_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_front_raise_kettlebell, "Kettlebell"),
        )
        SEATED_LATERAL_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_seated_lateral_raise_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_seated_lateral_raise_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_seated_lateral_raise_machine, "Máquina"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_seated_lateral_raise_kettlebell, "Kettlebell"),
        )
        STANDING_LATERAL_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_standing_lateral_raise_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_standing_lateral_raise_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_standing_lateral_raise_machine, "Máquina"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_standing_lateral_raise_kettlebell, "Kettlebell"),
        )
        REAR_DELT_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_rear_delt_raise_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_rear_delt_raise_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_rear_delt_raise_machine, "Máquina"),
        )
        Z_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_z_press_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_z_press_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("ez_bar", R.drawable.exercise_z_press_ez_bar, "Barra EZ"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_z_press_kettlebell, "Kettlebell"),
        )
        SHRUG_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_back_encogimientos_barbell, "Barra"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_back_encogimientos_cable_batch8, "Polea"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_back_encogimientos_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_back_encogimientos_kettlebell, "Kettlebell"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_back_encogimientos_smith_machine, "Smith"),
        )
        Y_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_back_y_raises_dumbbells, "Mancuernas"),
        )
        INCLINE_BENCH_Y_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_deltoides_y_raises_sentado_banco_inclinado_dumbbells, "Mancuernas"),
        )
        SCAPULAR_PULL_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_back_dominadas_escapulares_bodyweight, "Peso corporal"),
        )
        PUSH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_deltoides_push_press_barbell, "Barra"),
        )
        UNILATERAL_LANDMINE_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_deltoides_press_landmine_unilateral_barbell, "Barra"),
        )
        UPRIGHT_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_deltoides_remo_menton_barbell, "Barra"),
        )
        FOREARM_PLANK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_core_plancha_bodyweight, "Peso corporal"),
        )
        DRAGON_FLAG_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_core_dragon_flag_banco_plano_batch8, "Peso corporal"),
        )
        FROG_PUMPS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_glutes_frog_pumps_batch8, "Peso corporal"),
        )
        FLOOR_CRUNCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_core_crunch_suelo_peso_corporal_batch8, "Peso corporal"),
        )
        LEG_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_core_elevacion_piernas_batch8, "Peso corporal"),
        )
        SIDE_BEND_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_core_inclinacion_lateral_batch8, "Mancuernas"),
        )
        AB_WHEEL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("ab_wheel", R.drawable.exercise_core_rueda_abdominal_batch8, "Rueda abdominal"),
        )
        CLAMSHELL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_glutes_clamshells_banda_batch8, "Banda"),
        )
        MONSTER_WALK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_glutes_monster_walk_banda_batch8, "Banda"),
        )
        GLUTE_STEP_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_glutes_step_up_gluteo_batch8, "Mancuernas"),
        )
        COSSACK_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_quads_sentadilla_cosaca_batch8, "Peso corporal"),
        )
        PISTOL_SQUAT_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_quads_sentadilla_pistola_batch8, "Peso corporal"),
        )
        REVERSE_LUNGE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_reverse_lunge_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_reverse_lunge_barbell_batch9, "Barra"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_reverse_lunge_cable_batch9, "Polea"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_reverse_lunge_dumbbells_batch9, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_reverse_lunge_kettlebell_batch9, "Kettlebell"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_reverse_lunge_smith_machine_batch9, "Smith"),
        )
        REVERSE_PEC_FLY_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_reverse_pec_fly_cable_batch9, "Polea"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_reverse_pec_fly_dumbbells_batch9, "Mancuernas"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_reverse_pec_fly_machine_batch9, "Máquina"),
        )
        CALF_RAISE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_calf_raise_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_calf_raise_barbell_batch9, "Barra"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_calf_raise_cable_batch9, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_calf_raise_machine_batch9, "Máquina"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_calf_raise_smith_machine_batch9, "Smith"),
            ExerciseTechniqueImageVariant("leg_press_machine", R.drawable.exercise_calf_raise_leg_press_machine, "Prensa de piernas"),
        )
        LYING_LEG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_lying_leg_curl_cable_batch9, "Polea"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_lying_leg_curl_dumbbells_batch9, "Mancuernas"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_lying_leg_curl_machine_batch9, "Máquina"),
        )
        STEP_UP_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_step_up_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_step_up_barbell_batch10, "Barra"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_step_up_smith_machine_batch10, "Smith"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_step_up_dumbbells_batch10, "Mancuernas"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_step_up_kettlebell_batch10, "Kettlebell"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_step_up_cable_batch10, "Polea"),
        )
        GLUTE_BRIDGE_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_glutes_puente_gluteos_bodyweight, "Peso corporal"),
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_glutes_puente_gluteos_barbell_batch10, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_glutes_puente_gluteos_dumbbells_batch10, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_glutes_puente_gluteos_smith_machine_batch10, "Smith"),
        )
        HIP_ABDUCTION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_hip_abduction_machine_batch10, "Máquina sentada"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_hip_abduction_cable_batch10, "Polea"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_hip_abduction_band_batch10, "Banda"),
        )
        HIP_ADDUCTION_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_hip_adduction_machine_batch10, "Máquina sentada"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_hip_adduction_cable_batch10, "Polea"),
            ExerciseTechniqueImageVariant("band", R.drawable.exercise_hip_adduction_band_batch10, "Banda"),
        )
        SEATED_LEG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_seated_leg_curl_machine_batch10, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_seated_leg_curl_cable_batch10, "Polea"),
        )
        STANDING_LEG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_standing_leg_curl_machine_batch10, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_standing_leg_curl_cable_batch10, "Polea"),
        )
        SLIDER_LEG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("sliders", R.drawable.exercise_curl_isquios_con_sliders_sliders_batch10, "Sliders"),
        )
        BALL_LEG_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_curl_isquios_con_balon_bodyweight_batch10, "Balón"),
        )
        NORDIC_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_hams_curl_nordic_peso_corporal_bodyweight_batch10, "Peso corporal"),
        )
        REVERSE_NORDIC_CURL_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_quads_reverse_nordic_peso_corporal_bodyweight_batch10, "Peso corporal"),
        )
        COPENHAGEN_PLANK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_copenhagen_plank_bodyweight_batch10, "Peso corporal"),
        )
        DYNAMIC_COPENHAGEN_PLANK_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_copenhagen_plank_dynamic_bodyweight_batch10, "Peso corporal"),
        )
        MACHINE_CRUNCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_core_crunch_maquina_machine_batch10, "Máquina"),
        )
        DECLINE_PLATE_CRUNCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("plate", R.drawable.exercise_core_crunch_banco_declinado_lastrado_disco_plate_batch10, "Disco"),
        )
        HIGH_CABLE_CRUNCH_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_core_crunch_en_polea_alta_cable_batch10, "Polea"),
        )
        CABLE_WOODCHOPPER_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_core_lenador_polea_cable_batch10, "Polea"),
        )
        PALLOF_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_core_press_pallof_cable_batch10, "Polea"),
        )
        FLOOR_SUPERMAN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("bodyweight", R.drawable.exercise_back_superman_suelo_bodyweight_batch10, "Peso corporal"),
        )
        CONVENTIONAL_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_conventional_row_barbell, "Barra"),
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_conventional_row_dumbbells, "Mancuernas"),
            ExerciseTechniqueImageVariant("smith_machine", R.drawable.exercise_conventional_row_smith_machine, "Máquina Smith"),
            ExerciseTechniqueImageVariant("kettlebell", R.drawable.exercise_conventional_row_kettlebell, "Kettlebell"),
        )
        CHEST_SUPPORTED_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("dumbbells", R.drawable.exercise_chest_supported_row_dumbbells_medium, "Mancuernas"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_chest_supported_row_machine_medium, "Máquina"),
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_chest_supported_row_cable_mid_medium, "Polea"),
        )
        LAT_PULLDOWN_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("cable", R.drawable.exercise_lat_pulldown_cable, "Polea"),
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_lat_pulldown_machine, "Máquina"),
        )
        T_BAR_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("machine", R.drawable.exercise_t_bar_row_machine, "Máquina"),
        )
        SEAL_ROW_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("barbell", R.drawable.exercise_seal_row_barbell, "Barra"),
        )
        PAUSED_BENCH_PRESS_DEFINITION_ID -> listOf(
            ExerciseTechniqueImageVariant("default", R.drawable.exercise_press_de_banca_plano, "Barra"),
        )
        else -> emptyList()
    }

    fun resolveDefinitionId(
        catalogDefinitionId: String?,
        exerciseDbId: String?,
        exerciseId: String,
        catalogConfigurationId: String? = null,
    ): String? {
        val candidates = listOfNotNull(
            catalogConfigurationId?.substringBefore("__"),
            catalogDefinitionId,
            exerciseDbId?.substringBefore("__"),
            exerciseId.substringBefore("__"),
        )
        for (raw in candidates) {
            val key = raw.trim().lowercase()
            if (key.isBlank()) continue
            if (variants(key).isNotEmpty()) return key
        }
        return null
    }

    fun resolveImageResId(
        catalogDefinitionId: String?,
        exerciseDbId: String?,
        exerciseId: String,
        catalogConfigurationId: String? = null,
        selectedImplementation: String? = null,
    ): Int? {
        // A catalog configuration is authoritative. Consult reviewed exact mappings before
        // the legacy implement restrictions; never try another ID when one was supplied.
        val authoritativeConfigurationId = catalogConfigurationId?.takeIf { it.isNotBlank() }
        val legacyDbId = exerciseDbId.takeIf { authoritativeConfigurationId == null }
        val legacyImplementation = selectedImplementation.takeIf { authoritativeConfigurationId == null }
        val exactId = authoritativeConfigurationId ?: legacyDbId ?: exerciseId
        ReviewedExerciseTechniqueImages.strengthImage(exactId)?.let { return it }
        ReviewedExerciseTechniqueImages.mobilityImage(catalogDefinitionId ?: exactId)?.let { return it }
        val definitionId = resolveDefinitionId(
            catalogDefinitionId = catalogDefinitionId,
            exerciseDbId = legacyDbId,
            exerciseId = exerciseId,
            catalogConfigurationId = authoritativeConfigurationId,
        ) ?: return null
        val list = variants(definitionId)
        if (list.isEmpty()) return null
        if (definitionId == PULL_UP_DEFINITION_ID && !isApprovedPullUpConfiguration(
                catalogConfigurationId = authoritativeConfigurationId,
                exerciseDbId = legacyDbId,
            )
        ) {
            // Only the reviewed pronated-medium pull-up is available so far.
            return null
        }
        if (definitionId == T_BAR_ROW_DEFINITION_ID && requestsTBarImplement(
                selectedImplementation = legacyImplementation,
                catalogConfigurationId = authoritativeConfigurationId,
                exerciseDbId = legacyDbId,
            )
        ) {
            // The approved image is the dedicated machine only; never show it for the landmine/T-bar implement.
            return null
        }
        if (definitionId == PENDLAY_ROW_DEFINITION_ID && requestsOneOfImplement(
                selectedImplementation = legacyImplementation,
                catalogConfigurationId = authoritativeConfigurationId,
                exerciseDbId = legacyDbId,
                tokens = setOf("dumbbells", "machine", "cable", "kettlebell"),
            )
        ) {
            // Do not substitute the approved barbell/Smith art for a different Pendlay implement.
            return null
        }
        if (definitionId in setOf(HIP_ABDUCTION_DEFINITION_ID, HIP_ADDUCTION_DEFINITION_ID) &&
            requestsOneOfImplement(
                selectedImplementation = legacyImplementation,
                catalogConfigurationId = authoritativeConfigurationId,
                exerciseDbId = legacyDbId,
                tokens = setOf("machine"),
            ) &&
            !isSeatedStation(authoritativeConfigurationId, legacyDbId)
        ) {
            // These reviewed machine photos show a seated station, not the separate standing station.
            return null
        }
        val requestedEquipment = sequenceOf(
            legacyImplementation,
            authoritativeConfigurationId?.substringAfter("__", ""),
            legacyDbId?.substringAfter("__", ""),
        )
            .filterNotNull()
            .flatMap { it.split("__").asSequence() }
            .map { it.trim().lowercase() }
            .firstOrNull { it in KNOWN_EQUIPMENT_TOKENS }
        if (requestedEquipment != null && list.none { variant ->
                variant.implementation == "default" ||
                    variant.implementation == requestedEquipment ||
                    variant.implementation.startsWith("${requestedEquipment}__")
            }
        ) {
            // A missing implement must not silently inherit the first image of another implement.
            return null
        }
        val impl = implementToken(
            selectedImplementation = legacyImplementation,
            catalogConfigurationId = authoritativeConfigurationId,
            exerciseDbId = legacyDbId,
            variants = list,
        )
        return list.firstOrNull { it.implementation == impl }?.imageResId ?: list.first().imageResId
    }

    private fun requestsTBarImplement(
        selectedImplementation: String?,
        catalogConfigurationId: String?,
        exerciseDbId: String?,
    ): Boolean = requestsOneOfImplement(
        selectedImplementation = selectedImplementation,
        catalogConfigurationId = catalogConfigurationId,
        exerciseDbId = exerciseDbId,
        tokens = setOf("t_bar"),
    )

    private fun isSeatedStation(catalogConfigurationId: String?, exerciseDbId: String?): Boolean =
        sequenceOf(catalogConfigurationId, exerciseDbId)
            .filterNotNull()
            .flatMap { it.split("__").asSequence() }
            .any { it.equals("seated", ignoreCase = true) }

    private fun requestsOneOfImplement(
        selectedImplementation: String?,
        catalogConfigurationId: String?,
        exerciseDbId: String?,
        tokens: Set<String>,
    ): Boolean = sequenceOf(selectedImplementation, catalogConfigurationId, exerciseDbId)
        .filterNotNull()
        .flatMap { value -> value.split("__").asSequence() }
        .map { token -> token.trim().lowercase() }
        .any { token -> token in tokens }

    private fun isApprovedPullUpConfiguration(
        catalogConfigurationId: String?,
        exerciseDbId: String?,
    ): Boolean = sequenceOf(catalogConfigurationId, exerciseDbId)
        .filterNotNull()
        .map { value -> value.split("__").map { it.trim().lowercase() }.toSet() }
        .any { tokens -> "pronated" in tokens && "medium" in tokens }

    private fun implementToken(
        selectedImplementation: String?,
        catalogConfigurationId: String?,
        exerciseDbId: String?,
        variants: List<ExerciseTechniqueImageVariant>,
    ): String? {
        val keys = variants.map { it.implementation }.toSet()
        fun usable(raw: String?): String? {
            val token = raw?.trim()?.lowercase().orEmpty()
            if (token.isEmpty() || token in NON_IMPLEMENT_TOKENS) return null
            return token.takeIf { it in keys }
        }
        usable(selectedImplementation)?.let { return it }
        fun partsOf(id: String?): List<String> {
            if (id.isNullOrBlank()) return emptyList()
            return id.split("__").drop(1).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
        val parts = partsOf(catalogConfigurationId) + partsOf(exerciseDbId)
        parts.lastOrNull { it in keys }?.let { return it }
        val joined = parts.filter { it !in NON_IMPLEMENT_TOKENS }.joinToString("__")
        if (joined in keys) return joined
        return parts.lastOrNull { it !in NON_IMPLEMENT_TOKENS }
    }
}
