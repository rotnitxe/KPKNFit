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
