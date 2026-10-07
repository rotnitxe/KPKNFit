package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.onboarding.LiftMark

/*
 * Reservas curadas de ejercicios por patrón de movimiento («huecos» de una sesión).
 *
 * Una reserva es una lista ORDENADA de grupos; un grupo reúne alternativas equivalentes del MISMO implemento
 * (back squat y front squat, press con mancuernas inclinado y plano…). El orden entre grupos es la preferencia «natural»;
 * el orden real lo decide `ExerciseSelector` reordenando por nivel de material (barra → mancuerna → polea/máquina → Smith →
 * kettlebell → banda → anillas → peso corporal para quien ya entrena; máquina/mancuerna primero para quien aprende; libre
 * y kettlebell primero en funcional). `variantSeed` rota entre las alternativas de un grupo.
 *
 * Regla STOP: cada id existe y está APPROVED en el catálogo v2 (`GeneratorPoolsCatalogTest` lo comprueba para TODOS los
 * ids de este archivo y de `BodyweightLadders`). El catálogo no marca cuáles son los básicos de fuerza ni su nivel, así
 * que `basic`, `minLevel` y `kind` son datos curados aquí.
 *
 * `requires` lista el material EXTRA que pide el ejercicio: como mínimo lo que devuelve `supportRequirementsFor` (el
 * mismo contrato que usa el resto de la app; una prueba lo exige) y, además, lo que ese archivo no sabe (banco para el
 * hip thrust con barra, banco inclinado para el remo con apoyo de pecho, cajón o banco para el step-up…). Una entrada con
 * «a|b» se cumple con cualquiera de las dos llaves.
 *
 * Material de gimnasio (paquete D1b): el símbolo «Máquinas» abre una sala completa (las 73 configuraciones `machine`), la barra
 * trae discos en cualquier lugar y, con gimnasio entre los lugares, barra hexagonal y barra T; las máquinas traen además el
 * GHD y la rueda abdominal. Esos implementos NO se piden en `requires`: ya los decide el filtro compartido por el `equipmentId`
 * de la configuración (`hex_bar`, `t_bar`, `plate`, `ghd`, `ab_wheel`). Los que el contrato de soportes pide aparte (banco
 * declinado y de hiperextensión) sí van en `requires`. Las máquinas más raras de una sala (pendular, belt squat, V-squat, barra T
 * en máquina…) comparten grupo con las de siempre cuando son equivalentes (así rotan con la semilla) o van en un grupo propio con
 * `bias` cuando son otra cosa. Los implementos que `EquipmentTier` agrupa como `OTHER` (discos, GHD, rueda) llevan un `bias`
 * negativo: sin él su rango (7,0) los dejaría siempre detrás de todo y nunca saldrían.
 */

/** Naturaleza del ejercicio: decide repeticiones, descansos y cómo se prescribe. */
internal enum class ExKind {
    /** Compuesto de barra o similar donde se busca carga alta (3–6 reps para quien ya entrena). */
    HEAVY,
    COMPOUND,
    ISOLATION,

    /** Core dinámico (encogimientos, elevaciones de pierna…): repeticiones. */
    CORE_DYNAMIC,

    /** Isométricos y acarreos: segundos por serie. */
    TIMED,

    /** Balísticos (swing, push press): explosivos. */
    BALLISTIC,
}

/** Una entrada de reserva. */
internal data class PoolEntry(
    val id: String,
    val requires: List<String> = emptyList(),
    val kind: ExKind = ExKind.COMPOUND,
    /** Un básico se puede repetir en hasta 3 sesiones de la semana (el resto, en 2). */
    val basic: Boolean = false,
    /** Nivel mínimo recomendado. */
    val minLevel: RoutineLevel = RoutineLevel.NOVICE,
    /** Si el hueco es de este rol, la entrada encaja mejor. */
    val fitsRole: ItemRole? = null,
    /** Marca que lleva la carga porcentual de este ejercicio y qué fracción de esa marca equivale a su 1RM. */
    val mark: LiftMark? = null,
    val markFactor: Double = 1.0,
    /** Texto de variante que se muestra (`Exercise.variantName`); null = ninguno. */
    val variant: String? = null,
    /** Repeticiones propias del ejercicio (balísticos); null = las del tipo. */
    val reps: IntRange? = null,
    /**
     * Familia de movimiento dentro del patrón (solo las reservas de disciplina): un hueco con etiqueta (`SlotSpec.tag`) solo
     * admite entradas con esa misma etiqueta. Así una sesión de antebrazo pide flexión de muñeca, extensión, pronación y
     * supinación en vez de cuatro curls de muñeca con barra.
     */
    val tag: String? = null,
)

/** Alternativas equivalentes del mismo implemento. [bias] suma al rango de preferencia (más alto = más atrás). */
internal data class PoolGroup(val entries: List<PoolEntry>, val bias: Double = 0.0)

private fun e(
    id: String,
    vararg requires: String,
    kind: ExKind = ExKind.COMPOUND,
    basic: Boolean = false,
    minLevel: RoutineLevel = RoutineLevel.NOVICE,
    fitsRole: ItemRole? = null,
    mark: LiftMark? = null,
    factor: Double = 1.0,
    variant: String? = null,
    reps: IntRange? = null,
    tag: String? = null,
): PoolEntry = PoolEntry(id, requires.toList(), kind, basic, minLevel, fitsRole, mark, factor, variant, reps, tag)

private fun g(vararg entries: PoolEntry, bias: Double = 0.0): PoolGroup = PoolGroup(entries.toList(), bias)

internal object MovementPools {

    private val INT = RoutineLevel.INTERMEDIATE
    private val ADV = RoutineLevel.ADVANCED
    private val RET = RoutineLevel.RETURNING

    private val squat = listOf(
        g(
            e("high_bar_back_squat__barbell", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.SQUAT),
            e("front_squat__barbell", "rack", kind = ExKind.HEAVY, minLevel = INT, mark = LiftMark.SQUAT, factor = 0.8),
        ),
        g(
            e("quads_sentadilla_copa__default", basic = true),
            e("front_squat__dumbbells"),
            e("sumo_squat__dumbbells"),
        ),
        // Máquinas guiadas de la sala: la prensa y la hack son las de siempre; la V-squat, la pendular y la belt squat son la
        // misma familia (rotan con la semilla).
        g(
            e("quads_prensa_piernas__bilateral", basic = true),
            e("quads_sentadilla_hack__machine", basic = true),
            e("quads_sentadilla_v_squat__default"),
            e("pendulum_squat__bilateral"),
            e("belt_squat__bilateral"),
        ),
        g(
            e("high_bar_back_squat__smith_machine", basic = true),
            e("front_squat__smith_machine", minLevel = INT),
        ),
        g(
            e("front_squat__kettlebell", basic = true),
            e("sumo_squat__kettlebell"),
        ),
        // Las versiones invertidas de la hack y la V-squat cargan más el glúteo: detrás de las anteriores.
        g(
            e("quads_sentadilla_hack_invertida_maquina__default"),
            e("quads_sentadilla_v_squat_invertida_maquina__default"),
            bias = 0.4,
        ),
    )

    private val singleLeg = listOf(
        g(
            e("reverse_lunge__dumbbells", fitsRole = ItemRole.ACCESSORY),
            e("walking_lunge__dumbbells"),
            e("forward_lunge__dumbbells"),
        ),
        g(
            e("bulgarian_split_squat__dumbbells", "bench|plyo_box", minLevel = INT),
            e("step_up__dumbbells", "bench|plyo_box"),
        ),
        g(
            e("reverse_lunge__kettlebell"),
            e("walking_lunge__kettlebell"),
            e("step_up__kettlebell", "bench|plyo_box"),
        ),
        g(
            e("reverse_lunge__smith_machine"),
            e("bulgarian_split_squat__smith_machine", "bench|plyo_box", minLevel = INT),
        ),
        g(
            e("reverse_lunge__barbell", minLevel = INT),
            e("walking_lunge__barbell", minLevel = INT),
            bias = 2.0,
        ),
        // Una pierna en máquina: prensa unilateral, belt squat y pendular a una pierna, búlgara y zancada en la hack y la V-squat.
        g(
            e("quads_prensa_piernas__unilateral"),
            e("belt_squat__unilateral"),
            e("pendulum_squat__unilateral"),
            e("bulgarian_split_squat__machine"),
            e("quads_zancada_inversa_maquina_hack__default"),
            e("quads_zancada_inversa_maquina_v_squat__default"),
            bias = 0.8,
        ),
    )

    private val hinge = listOf(
        g(
            e("conventional_deadlift__bilateral__barbell", kind = ExKind.HEAVY, basic = true, minLevel = RET, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("romanian_deadlift__bilateral__barbell", basic = true, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.7),
            e("stiff_leg_deadlift__bilateral__barbell", minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.65),
            // Barra hexagonal (con gimnasio entre los lugares): el peso muerto más cómodo de aprender y sus variantes. Con marca de
            // peso muerto, el factor de la convencional queda del lado seguro (con la hexagonal se suele levantar algo más).
            e("conventional_deadlift__bilateral__hex_bar", kind = ExKind.HEAVY, basic = true, minLevel = RET, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT, factor = 0.9),
            e("romanian_deadlift__bilateral__hex_bar", basic = true, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.7),
            e("stiff_leg_deadlift__bilateral__hex_bar", minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.65),
        ),
        g(
            e("romanian_deadlift__bilateral__dumbbells", basic = true, fitsRole = ItemRole.SECONDARY),
            e("conventional_deadlift__bilateral__dumbbells", basic = true, fitsRole = ItemRole.MAIN),
            e("romanian_deadlift__unilateral__dumbbells", minLevel = INT, fitsRole = ItemRole.ACCESSORY),
        ),
        g(
            e("romanian_deadlift__bilateral__smith_machine", basic = true),
            e("conventional_deadlift__bilateral__smith_machine", minLevel = INT, fitsRole = ItemRole.MAIN),
        ),
        g(e("hams_pull_through__default", fitsRole = ItemRole.ACCESSORY)),
        g(e("hams_swing_kettlebell_dos_manos__default", kind = ExKind.BALLISTIC, basic = true, reps = 10..15)),
        g(e("good_morning__bilateral__machine"), bias = 0.8),
    )

    private val glute = listOf(
        g(
            e("hip_thrust__bilateral__barbell", "bench", basic = true, fitsRole = ItemRole.SECONDARY),
            e("glutes_puente_gluteos__bilateral__barbell"),
        ),
        g(
            e("glutes_puente_gluteos__bilateral__dumbbells", basic = true),
            e("glutes_puente_gluteos__unilateral__dumbbells", minLevel = INT),
        ),
        g(
            e("hip_thrust__bilateral__smith_machine", "bench"),
            e("glutes_puente_gluteos__bilateral__smith_machine"),
        ),
        g(
            e("glutes_patada_gluteo__cable", kind = ExKind.ISOLATION),
            e("glutes_patada_gluteo_polea_diagonal__default", kind = ExKind.ISOLATION),
        ),
        g(
            e("hip_thrust__bilateral__band", "bench"),
            e("glutes_patada_gluteo__band", kind = ExKind.ISOLATION),
        ),
        // Máquinas: hip thrust guiado, reverse hyper (5,0: no es de novatos) y abducción de cadera (glúteo medio).
        g(
            e("hip_thrust__bilateral__machine", basic = true),
            e("hip_thrust__unilateral__machine"),
        ),
        g(e("reverse_hyper__machine", kind = ExKind.ISOLATION, minLevel = INT), bias = 0.6),
        g(
            e("hip_abduction__seated__machine__bilateral", kind = ExKind.ISOLATION),
            e("hip_abduction__standing__machine__bilateral", kind = ExKind.ISOLATION),
            bias = 0.4,
        ),
        // Hiperextensión a 45° con disco (banco de hiperextensión de gimnasio).
        g(e("glutes_hiperextension_45__plate", "hyperextension_bench", kind = ExKind.ISOLATION), bias = -2.0),
    )

    private val horizontalPush = listOf(
        g(
            e("bench_press__barbell", "bench", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.BENCH),
            e("incline_bench_press__barbell", "bench", "bench_incline", "rack", kind = ExKind.HEAVY, minLevel = INT, mark = LiftMark.BENCH, factor = 0.8),
            e("close_grip_bench_press__barbell", "bench", "rack", minLevel = INT, mark = LiftMark.BENCH, factor = 0.9),
        ),
        g(
            e("floor_press__barbell", kind = ExKind.HEAVY, mark = LiftMark.BENCH, factor = 0.9),
            bias = 1.5,
        ),
        g(
            e("bench_press__dumbbells", "bench", basic = true),
            e("incline_bench_press__dumbbells", "bench", "bench_incline", fitsRole = ItemRole.SECONDARY),
        ),
        g(e("floor_press__dumbbells", basic = true), bias = 0.8),
        g(
            e("tren_superior_press_pecho_maquina_convergente__default", basic = true),
            e("tren_superior_press_inclinado_maquina_convergente__default"),
        ),
        g(e("tren_superior_press_unilateral_polea__default")),
        g(
            e("bench_press__smith_machine", "bench", basic = true),
            e("incline_bench_press__smith_machine", "bench", "bench_incline", fitsRole = ItemRole.SECONDARY),
        ),
        g(e("tren_superior_press_banda_resistencia__default")),
        // Press de banca y banca inclinada en máquina: el contrato de soportes pide además un banco y su dificultad en el
        // catálogo (7,0) no es de novato.
        g(
            e("bench_press__machine", "bench", minLevel = INT),
            e("incline_bench_press__machine", "bench", "bench_incline", minLevel = INT),
            bias = 1.0,
        ),
    )

    private val verticalPush = listOf(
        g(e("military_press__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.OVERHEAD_PRESS)),
        g(
            e("military_press__dumbbells", basic = true),
            e("seated_shoulder_press__dumbbells", "bench"),
            e("arnold_press__dumbbells", minLevel = INT, fitsRole = ItemRole.SECONDARY),
        ),
        g(
            e("military_press__smith_machine", basic = true),
            e("seated_shoulder_press__smith_machine", "bench"),
        ),
        g(
            e("military_press__kettlebell", basic = true),
            e("seated_shoulder_press__kettlebell", "bench"),
        ),
        g(e("military_press__cable")),
        g(
            e("seated_shoulder_press__machine", basic = true),
            e("military_press__machine"),
        ),
    )

    private val horizontalPull = listOf(
        g(
            e("conventional_row__barbell", basic = true),
            e("pendlay_row__barbell", minLevel = INT),
            e("t_bar_row__t_bar__medium"),
        ),
        g(
            e("conventional_row__dumbbells", basic = true),
            e("chest_supported_row__dumbbells__medium", "bench", "bench_incline", fitsRole = ItemRole.SECONDARY),
        ),
        g(
            e("conventional_row__cable", basic = true),
            e("gironda_row__medium"),
        ),
        g(
            e("conventional_row__smith_machine", basic = true),
            e("pendlay_row__smith_machine", minLevel = INT),
        ),
        g(
            e("conventional_row__kettlebell", basic = true),
            e("chest_supported_row__kettlebell__medium", "bench", "bench_incline"),
        ),
        // Remos en máquina: con apoyo de pecho (tres agarres) y convencional.
        g(
            e("chest_supported_row__machine__medium", basic = true),
            e("chest_supported_row__machine__wide"),
            e("chest_supported_row__machine__close"),
            e("conventional_row__machine", basic = true),
        ),
        // Remo en barra T en máquina y, con otros agarres, libre (la barra T acompaña a la barra con gimnasio entre los lugares).
        g(
            e("t_bar_row__machine__medium"),
            e("t_bar_row__machine__wide"),
            e("t_bar_row__machine__close"),
            bias = 0.4,
        ),
        g(
            e("t_bar_row__t_bar__wide"),
            e("t_bar_row__t_bar__close"),
            bias = 0.3,
        ),
    )

    private val verticalPull = listOf(
        g(
            e("lat_pulldown__bilateral__cable", basic = true),
            e("close_grip_lat_pulldown__cable"),
        ),
        g(e("lying_pullover__dumbbells", "bench", kind = ExKind.ISOLATION), bias = 3.0),
        // Jalón en máquina (un poco detrás del de polea, que es el de siempre) y pullover sentado en máquina.
        g(
            e("lat_pulldown__bilateral__machine", basic = true),
            e("lat_pulldown__unilateral__machine"),
            bias = 0.3,
        ),
        g(e("seated_machine_pullover__machine", kind = ExKind.ISOLATION), bias = 1.5),
    )

    private val chestIsolation = listOf(
        g(
            e("flat_chest_fly__dumbbells", "bench", kind = ExKind.ISOLATION),
            e("incline_chest_fly__dumbbells", "bench", "bench_incline", kind = ExKind.ISOLATION),
        ),
        g(
            e("tren_superior_cruce_poleas__mid", kind = ExKind.ISOLATION),
            e("flat_chest_fly__cable", "bench", kind = ExKind.ISOLATION),
            e("incline_chest_fly__cable", "bench", "bench_incline", kind = ExKind.ISOLATION),
        ),
        g(
            e("flat_chest_fly__machine", kind = ExKind.ISOLATION),
            e("incline_chest_fly__machine", kind = ExKind.ISOLATION),
        ),
    )

    private val shoulderLateral = listOf(
        g(
            e("standing_lateral_raise__dumbbells", kind = ExKind.ISOLATION),
            e("seated_lateral_raise__dumbbells", "bench", kind = ExKind.ISOLATION),
        ),
        g(e("standing_lateral_raise__cable", kind = ExKind.ISOLATION)),
        g(e("standing_lateral_raise__kettlebell", kind = ExKind.ISOLATION)),
        g(
            e("seated_lateral_raise__machine", kind = ExKind.ISOLATION),
            e("standing_lateral_raise__machine", kind = ExKind.ISOLATION),
            e("lateral_raise_super_rom__machine", kind = ExKind.ISOLATION),
            bias = 0.3,
        ),
    )

    private val rearDelt = listOf(
        g(
            e("reverse_pec_fly__bilateral__dumbbells", kind = ExKind.ISOLATION),
            e("rear_delt_raise__dumbbells", kind = ExKind.ISOLATION),
        ),
        g(
            e("reverse_pec_fly__bilateral__cable", kind = ExKind.ISOLATION),
            e("deltoides_face_pull__default", kind = ExKind.ISOLATION),
            e("rear_delt_raise__cable", kind = ExKind.ISOLATION),
        ),
        g(e("back_band_pull_apart__default", kind = ExKind.ISOLATION)),
        g(
            e("reverse_pec_fly__bilateral__machine", kind = ExKind.ISOLATION),
            e("rear_delt_raise__machine", kind = ExKind.ISOLATION),
            e("reverse_pec_fly__unilateral__machine", kind = ExKind.ISOLATION),
        ),
    )

    private val biceps = listOf(
        g(
            e("standing_biceps_curl__barbell", kind = ExKind.ISOLATION),
            e("standing_biceps_curl__ez_bar", kind = ExKind.ISOLATION),
        ),
        g(
            e("standing_biceps_curl__dumbbells", kind = ExKind.ISOLATION),
            e("hammer_curl__dumbbells", kind = ExKind.ISOLATION),
            e("incline_biceps_curl__dumbbells", "bench", "bench_incline", kind = ExKind.ISOLATION, minLevel = INT),
        ),
        g(
            e("standing_biceps_curl__cable", kind = ExKind.ISOLATION),
            e("hammer_curl__cable", kind = ExKind.ISOLATION),
        ),
        g(e("hammer_curl__kettlebell", kind = ExKind.ISOLATION)),
        g(e("hammer_curl__band", kind = ExKind.ISOLATION)),
        g(e("biceps_curl_trx__supinated", "rings", kind = ExKind.ISOLATION)),
        g(e("preacher_curl__machine", kind = ExKind.ISOLATION), bias = 0.4),
        // Curl waiter con un disco (los discos acompañan a la barra).
        g(e("biceps_curl_waiter__plate", kind = ExKind.ISOLATION), bias = -2.0),
    )

    private val triceps = listOf(
        g(
            e("overhead_triceps__barbell", kind = ExKind.ISOLATION),
            e("skullcrusher__default", "bench", kind = ExKind.ISOLATION, minLevel = INT),
        ),
        g(
            e("overhead_triceps__dumbbells", kind = ExKind.ISOLATION),
            e("triceps_press_frances__dumbbells", "bench", kind = ExKind.ISOLATION),
            e("triceps_patada__dumbbells__bilateral", kind = ExKind.ISOLATION),
        ),
        g(
            e("triceps_pushdown__bilateral__cable", kind = ExKind.ISOLATION),
            e("overhead_triceps__cable", kind = ExKind.ISOLATION),
        ),
        g(e("triceps_press_frances__kettlebell", "bench", kind = ExKind.ISOLATION)),
        g(e("triceps_pushdown__bilateral__band", kind = ExKind.ISOLATION)),
        g(e("triceps_extension__default", "rings", kind = ExKind.ISOLATION)),
        g(e("triceps_flexiones_esfinge__default", kind = ExKind.ISOLATION)),
        g(e("triceps_fondos_entre_bancos__default", "bench", kind = ExKind.ISOLATION)),
        g(
            e("triceps_pushdown__bilateral__machine", kind = ExKind.ISOLATION),
            e("triceps_press_maquina__default", kind = ExKind.ISOLATION),
            e("overhead_triceps__machine", kind = ExKind.ISOLATION),
            bias = 0.3,
        ),
    )

    private val traps = listOf(
        g(e("back_encogimientos__barbell", kind = ExKind.ISOLATION)),
        g(e("back_encogimientos__dumbbells", kind = ExKind.ISOLATION)),
        g(e("back_encogimientos__cable", kind = ExKind.ISOLATION)),
        g(e("back_encogimientos__smith_machine", kind = ExKind.ISOLATION)),
        g(e("back_encogimientos__kettlebell", kind = ExKind.ISOLATION)),
        g(e("back_encogimientos_kelso__machine", kind = ExKind.ISOLATION)),
    )

    private val grip = listOf(
        g(e("forearms_curl_muneca_sentado__dumbbells", kind = ExKind.ISOLATION)),
        g(e("forearms_suspension_isometrica_barra_fija__default", "pull_up_bar", kind = ExKind.TIMED)),
        // Pinza con discos: agarre isométrico, por tiempo.
        g(e("forearms_pinza_de_discos__default", kind = ExKind.TIMED), bias = -2.0),
    )

    private val carry = listOf(
        g(e("forearms_paseo_del_granjero__dumbbells", kind = ExKind.TIMED)),
        g(e("forearms_paseo_del_granjero__kettlebell", kind = ExKind.TIMED)),
        // Paseo del granjero con la barra hexagonal (gimnasio) y con discos: detrás de las mancuernas, que son las de siempre.
        g(e("forearms_paseo_del_granjero__hex_bar", kind = ExKind.TIMED), bias = 1.2),
        g(e("forearms_paseo_del_granjero__plate", kind = ExKind.TIMED), bias = -2.5),
    )

    private val quadIsolation = listOf(
        g(
            e("quads_extension_cuadriceps__machine__bilateral", kind = ExKind.ISOLATION),
            e("quads_extension_cuadriceps__machine__unilateral", kind = ExKind.ISOLATION),
        ),
        g(e("quads_extension_cuadriceps_pie_polea__bilateral", kind = ExKind.ISOLATION)),
        g(e("quads_reverse_nordic_peso_corporal__default", kind = ExKind.ISOLATION, minLevel = ADV)),
        // La sissy (5,2: no es de novatos) en máquina y con disco, y sin material: la sissy y la sentadilla en pared, isométrica y
        // por tiempo (la única que pueden hacer quienes empiezan).
        g(e("sissy_squat__machine", kind = ExKind.ISOLATION, minLevel = INT), bias = 0.2),
        g(e("sissy_squat__plate", kind = ExKind.ISOLATION, minLevel = INT), bias = -2.0),
        g(
            e("sissy_squat__bodyweight", kind = ExKind.ISOLATION, minLevel = INT),
            e("wall_sit__default", kind = ExKind.TIMED),
        ),
    )

    private val hamstringCurl = listOf(
        g(
            e("lying_leg_curl__bilateral__machine", kind = ExKind.ISOLATION),
            e("seated_leg_curl__bilateral__machine", kind = ExKind.ISOLATION),
            e("standing_leg_curl__bilateral__machine", kind = ExKind.ISOLATION),
        ),
        g(
            e("seated_leg_curl__bilateral__cable", kind = ExKind.ISOLATION),
            e("standing_leg_curl__bilateral__cable", kind = ExKind.ISOLATION),
        ),
        g(e("lying_leg_curl__bilateral__dumbbells", "bench", kind = ExKind.ISOLATION)),
        g(e("curl_isquios_con_balon__default", "ball", kind = ExKind.ISOLATION)),
        // Glute-ham raise en el GHD (5,2: no es de novatos).
        g(e("glute_ham_raise__default", minLevel = INT), bias = -3.0),
    )

    private val calf = listOf(
        g(
            e("calf_raise__bilateral__machine", kind = ExKind.ISOLATION),
            e("calf_raise__bilateral__seated_machine", kind = ExKind.ISOLATION),
            e("calf_raise__bilateral__leg_press_machine", kind = ExKind.ISOLATION),
            e("calf_raise__bilateral__donkey_machine", kind = ExKind.ISOLATION),
            e("calf_raise__unilateral__machine", kind = ExKind.ISOLATION),
        ),
        g(e("calf_raise__bilateral__smith_machine", kind = ExKind.ISOLATION)),
        g(e("calf_raise__bilateral__barbell", kind = ExKind.ISOLATION)),
        g(e("calf_raise__bilateral__cable", kind = ExKind.ISOLATION)),
        g(e("calf_raise__bilateral__bodyweight", kind = ExKind.ISOLATION)),
    )

    private val coreStability = listOf(
        g(e("core_crunch_en_polea_alta__default", kind = ExKind.CORE_DYNAMIC)),
        g(e("core_crunch_maquina__default", kind = ExKind.CORE_DYNAMIC), bias = 0.2),
        // Rueda abdominal (3,5, pero es un ejercicio difícil: no es de novatos) y crunch con disco en banco declinado.
        g(e("core_rueda_abdominal__default", kind = ExKind.CORE_DYNAMIC, minLevel = INT), bias = -3.5),
        g(e("core_crunch_banco_declinado_lastrado_disco__default", "decline_bench", kind = ExKind.CORE_DYNAMIC), bias = -3.5),
    )

    private val coreRotation = listOf(
        g(
            e("core_press_pallof__default", kind = ExKind.CORE_DYNAMIC),
            e("core_lenador_polea__default", kind = ExKind.CORE_DYNAMIC),
        ),
        g(e("core_inclinacion_lateral__default", kind = ExKind.CORE_DYNAMIC)),
        g(e("copenhagen_plank__default", "bench", kind = ExKind.TIMED, minLevel = INT)),
    )

    private val backExtension = listOf(
        // El bird dog es la alternativa más suave al superman (erectores como primer músculo principal).
        g(
            e("back_superman_suelo__default", kind = ExKind.CORE_DYNAMIC),
            e("bird_dog__default", kind = ExKind.CORE_DYNAMIC),
        ),
        g(e("back_extension_lumbar__default", kind = ExKind.ISOLATION), bias = 0.3),
    )

    private val power = listOf(
        g(e("hams_swing_kettlebell_dos_manos__default", kind = ExKind.BALLISTIC, basic = true, reps = 10..15)),
        g(e("deltoides_push_press__default", kind = ExKind.BALLISTIC, minLevel = INT, reps = 3..5)),
    )

    /** Reserva de cada patrón (sin las escaleras de peso corporal, que viven en [BodyweightLadders]). */
    val byPattern: Map<RoutinePattern, List<PoolGroup>> = mapOf(
        RoutinePattern.SQUAT to squat,
        RoutinePattern.SINGLE_LEG to singleLeg,
        RoutinePattern.HINGE to hinge,
        RoutinePattern.GLUTE to glute,
        RoutinePattern.HORIZONTAL_PUSH to horizontalPush,
        RoutinePattern.VERTICAL_PUSH to verticalPush,
        RoutinePattern.HORIZONTAL_PULL to horizontalPull,
        RoutinePattern.VERTICAL_PULL to verticalPull,
        RoutinePattern.CHEST_ISOLATION to chestIsolation,
        RoutinePattern.SHOULDER_LATERAL to shoulderLateral,
        RoutinePattern.REAR_DELT to rearDelt,
        RoutinePattern.BICEPS to biceps,
        RoutinePattern.TRICEPS to triceps,
        RoutinePattern.TRAPS to traps,
        RoutinePattern.GRIP to grip,
        RoutinePattern.QUAD_ISOLATION to quadIsolation,
        RoutinePattern.HAMSTRING_CURL to hamstringCurl,
        RoutinePattern.CALF to calf,
        RoutinePattern.CORE_STABILITY to coreStability,
        RoutinePattern.CORE_ROTATION to coreRotation,
        RoutinePattern.BACK_EXTENSION to backExtension,
        RoutinePattern.CARRY to carry,
        RoutinePattern.POWER to power,
    )

    /** Todos los ids citados por las reservas (para la prueba de catálogo). */
    val allIds: Set<String> by lazy {
        byPattern.values.flatten().flatMap { it.entries }.map { it.id }.toSet()
    }
}

/**
 * Reservas propias de cada modo de disciplina (Fase 2). Se ANTEPONEN a la reserva general del patrón (`MovementPools`), que
 * queda de recurso cuando falta el material (p. ej. sin barra, las sentadillas de strongman bajan a mancuernas). Salen del
 * catálogo actual: lo que no tiene la disciplina va en `DisciplineSpec.missing`.
 */
internal object DisciplinePools {

    private val INT = RoutineLevel.INTERMEDIATE
    private val ADV = RoutineLevel.ADVANCED

    // Familias de movimiento del antebrazo (armwrestling).
    const val WRIST_FLEXION = "wrist_flexion"
    const val WRIST_EXTENSION = "wrist_extension"
    const val PRONATION = "pronation"
    const val SUPINATION = "supination"
    const val REVERSE_CURL = "reverse_curl"
    const val HOLD = "hold"

    /**
     * Antebrazo y muñeca: las diez definiciones del catálogo con el antebrazo como músculo principal que se pueden
     * ejecutar con barra, mancuernas, polea, barra EZ, banda y kettlebell, repartidas por familia de movimiento.
     */
    private val armwrestlingGrip = listOf(
        g(
            e("forearms_curl_muneca_sentado__barbell", kind = ExKind.ISOLATION, tag = WRIST_FLEXION),
            e("forearms_curl_muneca_de_pie_tras_espalda_barra__default", kind = ExKind.ISOLATION, tag = WRIST_FLEXION),
            e("forearms_curl_muneca_inverso_sentado__barbell", kind = ExKind.ISOLATION, tag = WRIST_EXTENSION),
        ),
        g(
            e("forearms_curl_muneca_sentado__ez_bar", kind = ExKind.ISOLATION, tag = WRIST_FLEXION),
            e("forearms_curl_muneca_inverso_sentado__ez_bar", kind = ExKind.ISOLATION, tag = WRIST_EXTENSION),
        ),
        g(
            e("forearms_curl_muneca_sentado__dumbbells", kind = ExKind.ISOLATION, tag = WRIST_FLEXION),
            e("forearms_curl_muneca_inverso_sentado__dumbbells", kind = ExKind.ISOLATION, tag = WRIST_EXTENSION),
            e("pronation__dumbbells", kind = ExKind.ISOLATION, tag = PRONATION),
            e("supination__dumbbells", kind = ExKind.ISOLATION, tag = SUPINATION),
            e("reverse_curl__dumbbells", kind = ExKind.ISOLATION, tag = REVERSE_CURL),
        ),
        g(
            e("forearms_curl_muneca_sentado__cable", kind = ExKind.ISOLATION, tag = WRIST_FLEXION),
            e("forearms_curl_muneca_inverso_sentado__cable", kind = ExKind.ISOLATION, tag = WRIST_EXTENSION),
            e("pronation__cable", kind = ExKind.ISOLATION, tag = PRONATION),
            e("supination__cable", kind = ExKind.ISOLATION, tag = SUPINATION),
            e("reverse_curl__cable", kind = ExKind.ISOLATION, tag = REVERSE_CURL),
        ),
        g(e("reverse_curl__kettlebell", kind = ExKind.ISOLATION, tag = REVERSE_CURL)),
        g(e("reverse_curl__band", kind = ExKind.ISOLATION, tag = REVERSE_CURL)),
        g(e("forearms_suspension_isometrica_barra_fija__default", "pull_up_bar", kind = ExKind.TIMED, tag = HOLD)),
    )

    /** Sentadilla y peso muerto del strongman: Zercher además de las de siempre, y peso muerto de piso con sus variantes. */
    private val strongmanSquat = listOf(
        g(
            e("quads_sentadilla_zercher_barra_recta__default", "rack", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.SQUAT, factor = 0.8),
        ),
    )
    private val strongmanHinge = listOf(
        g(
            e("conventional_deadlift__bilateral__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("sumo_deadlift__barbell", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("deadlift_to_knees__barbell", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.85),
            e("good_morning_zercher__default", minLevel = ADV, fitsRole = ItemRole.ACCESSORY),
        ),
    )
    private val strongmanSingleLeg = listOf(
        g(
            e("quads_zancada_caminando_zercher_barra_recta__default", minLevel = INT),
            e("quads_zancada_frontal_zercher__default", minLevel = INT),
            e("quads_zancada_inversa_zercher__default", minLevel = INT),
        ),
    )
    private val strongmanVerticalPush = listOf(
        g(
            e("military_press__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.OVERHEAD_PRESS),
            e("deltoides_push_press__default", kind = ExKind.BALLISTIC, minLevel = INT, fitsRole = ItemRole.MAIN, mark = LiftMark.OVERHEAD_PRESS, factor = 1.1, reps = 3..5),
        ),
    )

    /** Halterofilia base: sentadilla frontal antes que la trasera, push press, y el peso muerto hasta la rodilla como tirón. */
    private val weightliftingSquat = listOf(
        g(
            e("front_squat__barbell", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.SQUAT, factor = 0.8),
            e("high_bar_back_squat__barbell", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.SQUAT),
            e("paused_back_squat__barbell", "rack", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.SQUAT, factor = 0.85),
        ),
    )
    private val weightliftingHinge = listOf(
        g(
            e("deadlift_to_knees__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT, factor = 0.85),
            e("conventional_deadlift__bilateral__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("romanian_deadlift__bilateral__barbell", basic = true, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.7),
        ),
    )
    private val weightliftingPower = listOf(
        g(
            e("deltoides_push_press__default", kind = ExKind.BALLISTIC, basic = true, reps = 3..5, mark = LiftMark.OVERHEAD_PRESS, factor = 1.1),
        ),
        g(e("hams_swing_kettlebell_dos_manos__default", kind = ExKind.BALLISTIC, basic = true, reps = 8..12)),
    )

    /** Powerlifting: los tres levantamientos de competición primero, y sus variantes (pausa, agarre cerrado, déficit parcial). */
    private val powerliftingSquat = listOf(
        g(
            e("low_bar_back_squat__barbell", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.SQUAT),
            e("high_bar_back_squat__barbell", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.SQUAT),
            e("paused_back_squat__barbell", "rack", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.SQUAT, factor = 0.85),
            e("front_squat__barbell", "rack", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.SQUAT, factor = 0.8),
        ),
    )
    private val powerliftingBench = listOf(
        g(
            e("bench_press__barbell", "bench", "rack", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.BENCH),
            e("close_grip_bench_press__barbell", "bench", "rack", kind = ExKind.HEAVY, fitsRole = ItemRole.SECONDARY, mark = LiftMark.BENCH, factor = 0.9),
            e("incline_bench_press__barbell", "bench", "bench_incline", "rack", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.BENCH, factor = 0.8),
        ),
    )
    private val powerliftingHinge = listOf(
        g(
            e("conventional_deadlift__bilateral__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("sumo_deadlift__barbell", kind = ExKind.HEAVY, basic = true, fitsRole = ItemRole.MAIN, mark = LiftMark.DEADLIFT),
            e("deadlift_to_knees__barbell", kind = ExKind.HEAVY, minLevel = INT, fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.85),
            e("romanian_deadlift__bilateral__barbell", fitsRole = ItemRole.SECONDARY, mark = LiftMark.DEADLIFT, factor = 0.7),
            e("stiff_leg_deadlift__bilateral__barbell", minLevel = INT, fitsRole = ItemRole.ACCESSORY, mark = LiftMark.DEADLIFT, factor = 0.65),
        ),
    )

    /** Reservas que cada modo antepone, por patrón. */
    val byMode: Map<RoutineMode, Map<RoutinePattern, List<PoolGroup>>> = mapOf(
        RoutineMode.DISCIPLINE_ARMWRESTLING to mapOf(RoutinePattern.GRIP to armwrestlingGrip),
        RoutineMode.DISCIPLINE_STRONGMAN to mapOf(
            RoutinePattern.SQUAT to strongmanSquat,
            RoutinePattern.HINGE to strongmanHinge,
            RoutinePattern.SINGLE_LEG to strongmanSingleLeg,
            RoutinePattern.VERTICAL_PUSH to strongmanVerticalPush,
        ),
        RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE to mapOf(
            RoutinePattern.SQUAT to weightliftingSquat,
            RoutinePattern.HINGE to weightliftingHinge,
            RoutinePattern.POWER to weightliftingPower,
        ),
        RoutineMode.CUSTOM_POWERLIFTING to mapOf(
            RoutinePattern.SQUAT to powerliftingSquat,
            RoutinePattern.HORIZONTAL_PUSH to powerliftingBench,
            RoutinePattern.HINGE to powerliftingHinge,
        ),
    )

    /** Todos los ids que citan las reservas de disciplina (para la prueba de catálogo). */
    val allEntries: List<Pair<String, PoolEntry>> by lazy {
        byMode.flatMap { (mode, patterns) ->
            patterns.flatMap { (pattern, groups) -> groups.flatMap { group -> group.entries.map { "${mode.name}/${pattern.name}" to it } } }
        }
    }
}
