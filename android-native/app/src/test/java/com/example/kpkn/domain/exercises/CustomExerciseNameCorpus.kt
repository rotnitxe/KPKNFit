package com.example.kpkn.domain.exercises

/**
 * Corpus de evaluación de «Crear ejercicio» (AUD1 §3.2): 36 nombres cotidianos con errores de escritura, inglés/español, sinónimos y
 * material, con las definiciones de referencia que se aceptan como parecido. Es una muestra exploratoria (no un benchmark
 * independiente): sirve de línea base y para comparar contra la búsqueda del catálogo. El conjunto de validación (nombres que no se
 * usaron para ajustar nada) vive aparte en [HELD_OUT_CORPUS].
 */
internal data class NameCase(
    val number: Int,
    val name: String,
    /** Definiciones del catálogo v2 que cuentan como parecido válido. */
    val accepted: Set<String>,
    /** Material que el nombre pide de forma explícita (id de implemento v2), si lo hay. */
    val implement: String? = null,
)

internal val AUD1_CORPUS: List<NameCase> = listOf(
    NameCase(1, "sentadila trasera barra alta", setOf("high_bar_back_squat"), "barbell"),
    NameCase(2, "sentadilla barra baja", setOf("low_bar_back_squat"), "barbell"),
    NameCase(3, "front squat barra", setOf("front_squat"), "barbell"),
    NameCase(4, "peso mueto rumano con mancuernas", setOf("romanian_deadlift"), "dumbbells"),
    NameCase(5, "RDL con barra", setOf("romanian_deadlift"), "barbell"),
    NameCase(6, "peso muerto sumo barra", setOf("sumo_deadlift"), "barbell"),
    NameCase(7, "deadlift trap bar", setOf("conventional_deadlift"), "hex_bar"),
    NameCase(
        8, "press banca con barra",
        setOf("bench_press", "paused_bench_press", "close_grip_bench_press", "tren_superior_press_spoto_barra"), "barbell",
    ),
    NameCase(9, "bench press dumbbells", setOf("bench_press"), "dumbbells"),
    NameCase(10, "pres inclinado mancuernas", setOf("incline_bench_press"), "dumbbells"),
    NameCase(11, "press militar con barra", setOf("military_press"), "barbell"),
    NameCase(12, "seated shoulder press machine", setOf("seated_shoulder_press"), "machine"),
    NameCase(13, "remo pendlay barra", setOf("pendlay_row"), "barbell"),
    NameCase(14, "remo pecho apoyado mancuernas", setOf("chest_supported_row", "seal_row"), "dumbbells"),
    NameCase(15, "lat pulldown polea", setOf("lat_pulldown", "close_grip_lat_pulldown"), "cable"),
    NameCase(16, "jalon al pecho agarre neutro", setOf("lat_pulldown", "close_grip_lat_pulldown")),
    NameCase(17, "dominadas pronas", setOf("pull_up")),
    NameCase(18, "chin ups supinas", setOf("pull_up")),
    NameCase(19, "curl martiyo mancuernas", setOf("hammer_curl"), "dumbbells"),
    NameCase(20, "bayesian curl polea", setOf("biceps_curl_bayesian"), "cable"),
    NameCase(21, "curl predicador maquina", setOf("preacher_curl"), "machine"),
    NameCase(22, "triceps pushdown cuerda", setOf("triceps_pushdown")),
    NameCase(23, "rompecraneos barra ez", setOf("triceps_press_frances"), "ez_bar"),
    NameCase(24, "lateral raises cable", setOf("standing_lateral_raise", "seated_lateral_raise"), "cable"),
    NameCase(25, "vuelo lateral con mancuernas", setOf("standing_lateral_raise", "seated_lateral_raise"), "dumbbells"),
    NameCase(26, "facepull polea", setOf("deltoides_face_pull"), "cable"),
    NameCase(27, "hip thrust barra", setOf("hip_thrust"), "barbell"),
    NameCase(28, "curl femoral sentado maquina", setOf("seated_leg_curl"), "machine"),
    NameCase(29, "extension de piernas maquina", setOf("quads_extension_cuadriceps"), "machine"),
    NameCase(30, "prensa piernas", setOf("quads_prensa_piernas")),
    NameCase(31, "bulgarian split squat dumbbells", setOf("bulgarian_split_squat"), "dumbbells"),
    NameCase(32, "goblet squat mancuerna", setOf("quads_sentadilla_copa"), "dumbbells"),
    NameCase(33, "plancha abdominal", setOf("core_plancha")),
    NameCase(34, "pallof press cable", setOf("core_press_pallof"), "cable"),
    NameCase(35, "ab wheel", setOf("core_rueda_abdominal")),
    NameCase(36, "calf raise maquina", setOf("calf_raise"), "machine"),
)

/** Controles de abstención de AUD1 §3.2: nombres que no deben acabar con anatomía ni texto certificados. */
internal val ABSTENTION_CONTROLS: List<String> = listOf(
    "XQZ 123",
    "mi ejercicio inventado",
    "dragón lunar mancuernas",
)

/**
 * Conjunto de validación: nombres escritos ANTES de diseñar el motor unificado y que no se usaron para ajustar ningún alias ni peso.
 * Mide si el motor generaliza más allá de las 36 consultas de AUD1 (E7: «conjunto separado de nombres nuevos»).
 */
internal val HELD_OUT_CORPUS: List<NameCase> = listOf(
    NameCase(101, "sentadilla con barra", setOf("high_bar_back_squat", "low_bar_back_squat"), "barbell"),
    NameCase(102, "back squat", setOf("high_bar_back_squat", "low_bar_back_squat")),
    NameCase(103, "sentadilla frontal", setOf("front_squat")),
    NameCase(104, "hack squat maquina", setOf("quads_sentadilla_hack"), "machine"),
    NameCase(105, "sentadilla sumo con mancuerna", setOf("sumo_squat"), "dumbbells"),
    NameCase(106, "zancadas con mancuernas", setOf("forward_lunge", "reverse_lunge", "walking_lunge"), "dumbbells"),
    NameCase(107, "peso muerto convencional", setOf("conventional_deadlift")),
    NameCase(108, "stiff con barra", setOf("stiff_leg_deadlift", "romanian_deadlift"), "barbell"),
    NameCase(109, "buenos dias con barra", setOf("good_morning"), "barbell"),
    NameCase(110, "press de banca inclinado con mancuernas", setOf("incline_bench_press"), "dumbbells"),
    NameCase(111, "press declinado barra", setOf("decline_bench_press"), "barbell"),
    NameCase(112, "aperturas con mancuernas", setOf("flat_chest_fly", "incline_chest_fly", "decline_chest_fly"), "dumbbells"),
    NameCase(113, "cruces en polea", setOf("tren_superior_cruce_poleas"), "cable"),
    NameCase(114, "fondos en paralelas", setOf("tren_superior_fondos")),
    NameCase(115, "flexiones", setOf("push_up")),
    NameCase(116, "remo con barra", setOf("conventional_row", "pendlay_row"), "barbell"),
    NameCase(117, "remo en barra t", setOf("t_bar_row")),
    NameCase(118, "jalon al pecho", setOf("lat_pulldown")),
    NameCase(119, "dominadas", setOf("pull_up")),
    NameCase(120, "pullover con mancuerna", setOf("lying_pullover", "pullover"), "dumbbells"),
    NameCase(121, "curl de biceps con barra", setOf("standing_biceps_curl"), "barbell"),
    NameCase(122, "curl martillo", setOf("hammer_curl")),
    NameCase(123, "curl concentrado", setOf("concentration_curl")),
    NameCase(124, "press frances con barra ez", setOf("triceps_press_frances"), "ez_bar"),
    NameCase(125, "extension de triceps en polea", setOf("triceps_pushdown", "overhead_triceps_extension"), "cable"),
    NameCase(126, "elevaciones frontales", setOf("deltoides_elevaciones_frontales")),
    NameCase(127, "pajaros mancuernas", setOf("rear_delt_raise", "reverse_pec_fly"), "dumbbells"),
    NameCase(128, "encogimientos de hombros", setOf("back_encogimientos")),
    NameCase(129, "curl femoral tumbado", setOf("lying_leg_curl")),
    NameCase(130, "hiperextensiones", setOf("back_extension_lumbar", "glutes_hiperextension_45")),
    NameCase(131, "puente de gluteos", setOf("glutes_puente_gluteos")),
    NameCase(132, "elevacion de talones sentado", setOf("calf_raise")),
    NameCase(133, "plancha lateral", setOf("side_plank")),
    NameCase(134, "crunch en polea", setOf("core_crunch_en_polea_alta"), "cable"),
    NameCase(135, "elevacion de piernas colgado", setOf("core_elevacion_piernas")),
    NameCase(136, "press arnold", setOf("arnold_press")),
    NameCase(137, "swing kettlebell", setOf("hams_swing_kettlebell_dos_manos", "hams_swing_kettlebell_unilateral"), "kettlebell"),
    NameCase(138, "farmer walk", setOf("forearms_paseo_del_granjero")),
    NameCase(139, "overhead squat", setOf("overhead_squat")),
    NameCase(140, "face pull", setOf("deltoides_face_pull")),
)

/** Nombres genéricos o sin sentido: el motor debe abstenerse (sin referencia confirmada). */
internal val HELD_OUT_ABSTENTIONS: List<String> = listOf(
    "ejercicio de brazos",
    "xyz",
    "nuevo ejercicio",
    "baile de salon",
    "mi rutina favorita",
)

/**
 * Corpus FRESCO: escrito después de que el motor unificado alcanzara el 100 % en las 36 consultas de AUD1 y en el conjunto de
 * validación (cuyas tablas de alias se redactaron con ese conjunto a la vista, así que su cifra es optimista). Estos nombres se
 * midieron UNA vez antes de tocar nada: su resultado es la estimación honesta de generalización y no se ajustó nada con él.
 */
internal val FRESH_CORPUS: List<NameCase> = listOf(
    NameCase(201, "press de hombros con mancuernas sentado", setOf("seated_shoulder_press"), "dumbbells"),
    NameCase(202, "press militar de pie", setOf("military_press")),
    NameCase(203, "elevaciones laterales en polea", setOf("standing_lateral_raise", "seated_lateral_raise"), "cable"),
    NameCase(204, "peso muerto sumo", setOf("sumo_deadlift")),
    NameCase(205, "peso muerto rumano a una pierna", setOf("romanian_deadlift")),
    NameCase(206, "hip thrust con mancuerna", setOf("hip_thrust"), "dumbbells"),
    NameCase(207, "sentadilla en smith", setOf("high_bar_back_squat", "low_bar_back_squat", "front_squat", "sumo_squat"), "smith_machine"),
    NameCase(208, "curl predicador con barra z", setOf("preacher_curl"), "ez_bar"),
    NameCase(209, "extension de cuadriceps", setOf("quads_extension_cuadriceps")),
    NameCase(210, "curl de piernas tumbado", setOf("lying_leg_curl")),
    NameCase(211, "jalon con agarre cerrado", setOf("close_grip_lat_pulldown", "lat_pulldown")),
    NameCase(212, "remo en polea baja", setOf("conventional_row", "chest_supported_row"), "cable"),
    NameCase(213, "aperturas en maquina", setOf("flat_chest_fly", "incline_chest_fly"), "machine"),
    NameCase(214, "fondos", setOf("tren_superior_fondos", "triceps_fondos_entre_bancos")),
    NameCase(215, "zancada caminando con mancuernas", setOf("walking_lunge"), "dumbbells"),
    NameCase(216, "step up con mancuernas", setOf("step_up"), "dumbbells"),
    NameCase(217, "elevacion de talones de pie en maquina", setOf("calf_raise"), "machine"),
    NameCase(
        218, "crunch abdominal",
        setOf("core_crunch_suelo_peso_corporal", "core_crunch_maquina", "core_crunch_en_polea_alta", "core_crunch_banco_declinado_lastrado_disco"),
    ),
    NameCase(219, "plancha", setOf("core_plancha")),
    NameCase(220, "dead bug", setOf("dead_bug")),
    NameCase(221, "press de pecho en maquina", setOf("tren_superior_press_pecho_maquina_convergente", "bench_press"), "machine"),
    NameCase(222, "pullover en polea", setOf("pullover"), "cable"),
    NameCase(223, "curl de biceps con mancuernas", setOf("standing_biceps_curl"), "dumbbells"),
    NameCase(224, "triceps en polea con cuerda", setOf("triceps_pushdown"), "cable"),
    NameCase(225, "press frances con mancuernas", setOf("triceps_press_frances"), "dumbbells"),
    NameCase(226, "encogimientos con barra", setOf("back_encogimientos"), "barbell"),
    NameCase(227, "remo con mancuerna a una mano", setOf("back_remo_gorilla_mancuernas", "conventional_row", "chest_supported_row"), "dumbbells"),
    NameCase(228, "buenos dias", setOf("good_morning")),
    NameCase(229, "sentadilla goblet", setOf("quads_sentadilla_copa")),
    NameCase(230, "peso muerto trap bar", setOf("conventional_deadlift"), "hex_bar"),
    NameCase(231, "clean", setOf("power_clean", "squat_clean", "hang_power_clean")),
    NameCase(232, "snatch", setOf("power_snatch", "squat_snatch", "hang_power_snatch")),
    NameCase(233, "paseo del granjero", setOf("forearms_paseo_del_granjero")),
    NameCase(234, "curl de muñeca", setOf("forearms_curl_muneca_sentado")),
    NameCase(235, "extensiones de cuello", setOf("neck_extension_cuello")),
    NameCase(236, "hiperextension 45", setOf("glutes_hiperextension_45", "back_extension_lumbar")),
    NameCase(237, "sissy squat", setOf("sissy_squat")),
    NameCase(238, "pistol squat", setOf("quads_sentadilla_pistola")),
    NameCase(239, "nordic curl", setOf("hams_curl_nordic_peso_corporal")),
    NameCase(240, "dominada australiana", setOf("back_remo_invertido")),
    NameCase(241, "sentadila bulgara con mancuernas", setOf("bulgarian_split_squat"), "dumbbells"),
    NameCase(242, "pres de banca con barra", setOf("bench_press", "paused_bench_press", "close_grip_bench_press"), "barbell"),
    NameCase(243, "remo penday", setOf("pendlay_row")),
    NameCase(244, "curl martillo con cuerda", setOf("hammer_curl"), "cable"),
    NameCase(245, "abdominales en rueda", setOf("core_rueda_abdominal")),
)

/** Nombres sin equivalente en el catálogo: el motor debe abstenerse de certificar una referencia. */
internal val FRESH_ABSTENTIONS: List<String> = listOf(
    "burpees",
    "salto al cajon",
    "ejercicio loco",
    "abc",
    "yoga",
    "sentadilla sobre bosu",
    "turkish get up",
)
