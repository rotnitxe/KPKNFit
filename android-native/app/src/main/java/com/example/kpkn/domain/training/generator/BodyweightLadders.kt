package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill

/*
 * Escaleras de progresión de peso corporal (dato nuevo, en Kotlin; el catálogo no las trae).
 *
 * Cada escalera tiene tres tramos: EASY (la persona aún no lo logra: una regresión), STANDARD (algunas repeticiones del
 * ejercicio estándar) y HARD (le sale con holgura: una variante más difícil). `capabilities` elige el tramo
 * (NONE → EASY, SOME → STANDARD, MANY → HARD); si la persona no declaró esa habilidad decide el nivel
 * (`defaultCapability`). Dentro de un tramo vale la primera rung ejecutable con el material del día; si el tramo
 * pedido no es ejecutable se baja (HARD → STANDARD → EASY) y NUNCA se sube: no se prescribe lo que la persona no hace.
 * El novato nunca arranca en una rung de dificultad técnica del catálogo > 5,2, ni en una con `minLevel` por encima de su nivel
 * (la pica, aunque puntúe 4,8 y 5,0, pide ya una flexión estándar: no se ofrece a quien empieza).
 *
 * Una misma configuración puede aparecer en varios tramos con otra `label` (el remo invertido: rodillas dobladas, piernas
 * estiradas, pies elevados): el catálogo describe la dificultad por el ángulo del cuerpo, no por configuración.
 *
 * Los isométricos (plancha, plancha lateral, hollow body, sentadilla en pared, colgado) se prescriben por TIEMPO: su rung lleva
 * `kind = TIMED` y `seconds`, como `core_plancha__default`.
 *
 * Huecos conocidos del catálogo (van al informe del paquete): no hay pino (handstand push-up) ni flexión a una mano, ni remo en
 * anillas, ni dominada asistida con goma o con lastre, ni puente a una pierna sin carga, ni salto al cajón. El lote BW-1 cerró
 * el empuje vertical (pica), una regresión de dominada (la negativa), las zancadas y el step-up, la bisagra sin carga, el core
 * antiextensión y el lateral.
 */

internal enum class LadderTier { EASY, STANDARD, HARD }

internal data class Rung(
    val id: String,
    /** Texto de variante visible (`Exercise.variantName`). */
    val label: String? = null,
    val requires: List<String> = emptyList(),
    val kind: ExKind = ExKind.COMPOUND,
    /** Repeticiones objetivo propias de la rung (null = las del tipo). */
    val reps: IntRange? = null,
    /** Segundos por serie si es isométrico. */
    val seconds: Int? = null,
    /** Nivel mínimo recomendado (como `PoolEntry.minLevel`): por debajo de él la rung no se ofrece aunque la capacidad la pida. */
    val minLevel: RoutineLevel = RoutineLevel.NOVICE,
)

private val RET = RoutineLevel.RETURNING
private val INT = RoutineLevel.INTERMEDIATE

private fun r(
    id: String,
    label: String? = null,
    vararg requires: String,
    kind: ExKind = ExKind.COMPOUND,
    reps: IntRange? = null,
    seconds: Int? = null,
    minLevel: RoutineLevel = RoutineLevel.NOVICE,
): Rung = Rung(id, label, requires.toList(), kind, reps, seconds, minLevel)

internal data class Ladder(
    val pattern: RoutinePattern,
    val skill: CapabilitySkill?,
    val easy: List<Rung>,
    val standard: List<Rung>,
    val hard: List<Rung>,
) {
    fun rungs(tier: LadderTier): List<Rung> = when (tier) {
        LadderTier.EASY -> easy
        LadderTier.STANDARD -> standard
        LadderTier.HARD -> hard
    }

    val allIds: Set<String> get() = (easy + standard + hard).map { it.id }.toSet()
}

internal object BodyweightLadders {

    /**
     * Flexión: rodillas → estándar → pies elevados → diamante → estándar lenta → arquero. El diamante (6,0) y el arquero (6,5)
     * van en el tramo difícil y nunca llegan a un novato (la dificultad pasa de 5,2; además llevan `minLevel`): son para quien ya
     * hace varias flexiones estándar con holgura. El arquero va el último: es unilateral y el más exigente.
     */
    val pushUp = Ladder(
        pattern = RoutinePattern.HORIZONTAL_PUSH,
        skill = CapabilitySkill.PUSH_UP,
        easy = listOf(
            r("push_up__hands_elevated", "Inclinada: manos en un apoyo alto", reps = 8..15),
            r("knee_push_up__default", "Con rodillas apoyadas", reps = 8..15),
        ),
        standard = listOf(
            r("push_up__flat", reps = 6..15),
        ),
        hard = listOf(
            r("push_up__feet_elevated", "Con pies elevados", "support", reps = 6..12),
            r("diamond_push_up__default", reps = 6..12, minLevel = RET),
            r("push_up__flat", "Bajada lenta de 3 s", reps = 8..12),
            r("archer_push_up__default", reps = 4..8, minLevel = INT),
        ),
    )

    /**
     * Empuje vertical sin carga: pica plana → pica con los pies elevados. Sin tramo fácil: quien aún no hace flexiones no
     * empieza por la pica (carga más los hombros y las manos que una flexión). La elevada solo se ofrece a quien pide el tramo
     * difícil y SIEMPRE con la plana detrás (si no hay apoyo para los pies, cae a la plana): nunca la elevada sin la plana.
     * Ninguna de las dos se ofrece a un novato aunque el catálogo las puntúe bajo 5,2.
     */
    val verticalPush = Ladder(
        pattern = RoutinePattern.VERTICAL_PUSH,
        skill = CapabilitySkill.PUSH_UP,
        easy = emptyList(),
        standard = listOf(
            r("pike_push_up__flat", reps = 6..12, minLevel = RET),
        ),
        hard = listOf(
            r("pike_push_up__feet_elevated", "Con pies elevados", "support", reps = 5..10, minLevel = RET),
            r("pike_push_up__flat", "Bajada lenta de 3 s", reps = 6..10, minLevel = RET),
        ),
    )

    val dips = Ladder(
        pattern = RoutinePattern.HORIZONTAL_PUSH,
        skill = CapabilitySkill.DIP,
        easy = emptyList(),
        standard = listOf(r("tren_superior_fondos__default", "Fondos en paralelas", "dip_bars", reps = 5..10)),
        hard = listOf(r("tren_superior_fondos__default", "Fondos en paralelas, bajada lenta", "dip_bars", reps = 6..12)),
    )

    /**
     * Tracción vertical: colgado y escapulares → pies apoyados → dominada negativa → dominada. La negativa (bajada lenta desde
     * arriba) es la regresión más directa de la dominada y va en el tramo fácil, justo antes de los `pull_up__*` del estándar.
     */
    val pullUp = Ladder(
        pattern = RoutinePattern.VERTICAL_PULL,
        skill = CapabilitySkill.PULL_UP,
        easy = listOf(
            r("rack_chin__default", "Con los pies apoyados", "low_bar_support", reps = 6..10),
            r("negative_pull_up__default", "Bajada lenta de 4 s", "pull_up_bar", reps = 3..6),
            r("lat_pulldown__bilateral__band", "Jalón con banda anclada a la barra", "pull_up_bar", reps = 8..15),
            r("back_dominadas_escapulares__default", "Dominadas escapulares", "pull_up_bar", kind = ExKind.ISOLATION, reps = 6..12),
            r("forearms_suspension_isometrica_barra_fija__default", "Colgado de la barra", "pull_up_bar", kind = ExKind.TIMED, seconds = 25),
        ),
        standard = listOf(
            r("pull_up__supinated__medium", "Agarre supino", "pull_up_bar", reps = 4..8),
            r("pull_up__pronated__medium", "Agarre prono", "pull_up_bar", reps = 4..8),
        ),
        hard = listOf(
            r("pull_up__pronated__wide", "Agarre ancho", "pull_up_bar", reps = 5..10),
            r("pull_up__neutral__wide", "Agarre neutro ancho", "pull_up_bar", reps = 5..10),
            r("pull_up__pronated__medium", "Bajada lenta de 3 s", "pull_up_bar", reps = 5..10),
        ),
    )

    /** Tracción horizontal de peso corporal; la habilidad de referencia es la dominada (fuerza de tirón). */
    val row = Ladder(
        pattern = RoutinePattern.HORIZONTAL_PULL,
        skill = CapabilitySkill.PULL_UP,
        easy = listOf(
            r("back_remo_invertido__default", "Rodillas dobladas (más fácil)", "low_bar_support", reps = 8..12),
            r("back_remo_banda__default", "Remo con banda", reps = 10..15),
        ),
        standard = listOf(
            r("back_remo_invertido__default", "Piernas estiradas", "low_bar_support", reps = 6..12),
            r("back_remo_banda__default", "Remo con banda", reps = 10..15),
        ),
        hard = listOf(
            r("back_remo_invertido__default", "Pies elevados (más difícil)", "low_bar_support", reps = 6..10),
            r("back_remo_banda__default", "Remo con banda", reps = 12..20),
        ),
    )

    /**
     * Zancadas y una pierna: inversa, frontal, caminando y step-up (fácil) → pistol asistida, cosaca y búlgara (estándar) →
     * pistol y cosaca (difícil). El step-up y la búlgara piden un apoyo elevado (`support`): sin banco, cajón, rack ni paralelas
     * no se ofrecen.
     */
    val singleLeg = Ladder(
        pattern = RoutinePattern.SINGLE_LEG,
        skill = CapabilitySkill.PISTOL_SQUAT,
        easy = listOf(
            r("reverse_lunge__bodyweight", reps = 8..12),
            r("forward_lunge__bodyweight", reps = 8..12),
            r("step_up__bodyweight", null, "support", reps = 8..12),
            r("walking_lunge__bodyweight", reps = 10..16),
        ),
        standard = listOf(
            r("quads_sentadilla_pistola_asistida_trx__default", "Pistol asistida con anillas", "rings", reps = 5..8),
            r("quads_sentadilla_cosaca__default", reps = 6..10),
            r("bulgarian_split_squat__bodyweight", null, "support", reps = 6..10),
            r("reverse_lunge__bodyweight", "Con pausa abajo de 2 s", reps = 8..12),
        ),
        hard = listOf(
            r("quads_sentadilla_pistola__default", reps = 3..6),
            r("quads_sentadilla_cosaca__default", reps = 8..12),
            r("bulgarian_split_squat__bodyweight", "Bajada lenta de 3 s", "support", reps = 6..10),
        ),
    )

    /**
     * Sentadilla sin carga: sentadilla, sumo y sentadilla en pared (isométrica, por tiempo; quien empieza la hace en la reserva
     * del cuádriceps aislado, no como su sentadilla principal). Sin variantes corporales a dos
     * piernas más difíciles en el catálogo salvo la sissy: el tramo difícil sube con la bajada lenta, con la sissy (desde
     * nivel intermedio) y con más tiempo en la pared.
     */
    val squat = Ladder(
        pattern = RoutinePattern.SQUAT,
        skill = null,
        easy = listOf(
            r("quads_sentadilla_sin_carga__default", reps = 10..20),
        ),
        standard = listOf(
            r("quads_sentadilla_sin_carga__default", reps = 12..25),
            r("sumo_squat__bodyweight", reps = 12..20),
            r("wall_sit__default", kind = ExKind.TIMED, seconds = 40),
        ),
        hard = listOf(
            r("quads_sentadilla_sin_carga__default", "Bajada lenta de 3 s", reps = 12..20),
            r("sumo_squat__bodyweight", "Bajada lenta de 3 s", reps = 10..15),
            // La sissy (5,2) no es de novatos: es la sentadilla sin carga más exigente del catálogo para el cuádriceps.
            r("sissy_squat__bodyweight", kind = ExKind.ISOLATION, reps = 6..12, minLevel = INT),
            r("wall_sit__default", kind = ExKind.TIMED, seconds = 60),
        ),
    )

    /**
     * Bisagra de cadera sin carga: buenos días sin carga → rumano a una pierna. El puente de glúteos NO es una bisagra (es
     * extensión de cadera): solo es la última opción de quien empieza, y a partir del nivel intermedio la bisagra se queda con los
     * dos ejercicios de verdad y el puente va al hueco del glúteo (un ejercicio no se repite en la sesión).
     */
    val hinge = Ladder(
        pattern = RoutinePattern.HINGE,
        skill = null,
        easy = listOf(
            r("good_morning__bilateral__bodyweight", reps = 10..15),
            r("romanian_deadlift__unilateral__bodyweight", reps = 8..12, minLevel = RET),
            r("glutes_puente_gluteos__bilateral__bodyweight", reps = 10..20),
        ),
        standard = listOf(
            r("romanian_deadlift__unilateral__bodyweight", reps = 8..12),
            r("good_morning__bilateral__bodyweight", "Con pausa abajo de 2 s", reps = 10..15),
        ),
        hard = listOf(
            r("romanian_deadlift__unilateral__bodyweight", "Bajada lenta de 3 s", reps = 8..12),
            r("good_morning__bilateral__bodyweight", "Bajada lenta de 3 s", reps = 10..15),
        ),
    )

    val glute = Ladder(
        pattern = RoutinePattern.GLUTE,
        skill = null,
        easy = listOf(
            r("glutes_puente_gluteos__bilateral__bodyweight", kind = ExKind.ISOLATION, reps = 12..20),
            // Segunda opción en el tramo fácil: si la bisagra ya usó el puente, un ejercicio no se repite en la sesión.
            r("glutes_frog_pumps__default", kind = ExKind.ISOLATION, reps = 15..25),
        ),
        standard = listOf(
            r("glutes_frog_pumps__default", kind = ExKind.ISOLATION, reps = 15..25),
            r("glutes_puente_gluteos__bilateral__bodyweight", "Con pausa de 2 s", kind = ExKind.ISOLATION, reps = 12..20),
        ),
        hard = listOf(
            r("glutes_frog_pumps__default", kind = ExKind.ISOLATION, reps = 20..30),
        ),
    )

    /**
     * Core de flexión y estabilidad: el tramo lo decide el nivel (no hay capacidad declarable para el core). Dead bug y crunch
     * (fácil) → plancha, hollow body y elevación de piernas (estándar) → piernas rectas, dragon flag y plancha larga (difícil).
     * La plancha y el hollow body se prescriben por tiempo.
     */
    val core = Ladder(
        pattern = RoutinePattern.CORE_STABILITY,
        skill = null,
        easy = listOf(
            r("core_crunch_suelo_peso_corporal__default", kind = ExKind.CORE_DYNAMIC, reps = 10..15),
            r("dead_bug__default", kind = ExKind.CORE_DYNAMIC, reps = 8..12),
            r("core_plancha__default", kind = ExKind.TIMED, seconds = 25),
        ),
        standard = listOf(
            r("core_plancha__default", kind = ExKind.TIMED, seconds = 40),
            r("hollow_body_hold__default", kind = ExKind.TIMED, seconds = 25),
            r("core_elevacion_piernas__default", "Piernas dobladas", "pull_up_bar", kind = ExKind.CORE_DYNAMIC, reps = 8..12),
            r("core_crunch_suelo_peso_corporal__default", kind = ExKind.CORE_DYNAMIC, reps = 12..20),
            r("dead_bug__default", kind = ExKind.CORE_DYNAMIC, reps = 10..14),
        ),
        hard = listOf(
            r("core_elevacion_piernas__default", "Piernas rectas", "pull_up_bar", kind = ExKind.CORE_DYNAMIC, reps = 8..15),
            r("core_dragon_flag_banco_plano__default", "Dragon flag con rodillas flexionadas", "bench", kind = ExKind.CORE_DYNAMIC, reps = 4..8),
            r("hollow_body_hold__default", kind = ExKind.TIMED, seconds = 40),
            r("core_plancha__default", kind = ExKind.TIMED, seconds = 60),
        ),
    )

    /**
     * Core de rotación y lateral sin material: bird dog (antirrotación) y plancha lateral (flexión lateral, isométrica y por
     * tiempo). El tramo lo decide el nivel.
     */
    val coreLateral = Ladder(
        pattern = RoutinePattern.CORE_ROTATION,
        skill = null,
        easy = listOf(
            r("bird_dog__default", kind = ExKind.CORE_DYNAMIC, reps = 8..12),
            r("side_plank__default", kind = ExKind.TIMED, seconds = 20),
        ),
        standard = listOf(
            r("side_plank__default", kind = ExKind.TIMED, seconds = 30),
            r("bird_dog__default", kind = ExKind.CORE_DYNAMIC, reps = 10..14),
        ),
        hard = listOf(
            r("side_plank__default", kind = ExKind.TIMED, seconds = 45),
            r("bird_dog__default", "Con pausa de 3 s", kind = ExKind.CORE_DYNAMIC, reps = 8..12),
        ),
    )

    /** Pantorrilla sin material: de puntillas (el catálogo no trae la variante unilateral corporal). */
    val calf = Ladder(
        pattern = RoutinePattern.CALF,
        skill = null,
        easy = listOf(r("calf_raise__bilateral__bodyweight", kind = ExKind.ISOLATION, reps = 15..25)),
        standard = listOf(r("calf_raise__bilateral__bodyweight", "Con pausa arriba de 2 s", kind = ExKind.ISOLATION, reps = 15..25)),
        hard = listOf(r("calf_raise__bilateral__bodyweight", "Bajada lenta de 3 s", kind = ExKind.ISOLATION, reps = 12..20)),
    )

    /**
     * Potencia sin material: el catálogo no trae saltos ni lanzamientos, así que la potencia corporal es la sentadilla y
     * la flexión «rápidas» (intención de velocidad, sin salto ni despegar las manos) con pocas repeticiones. Solo se usa en
     * la sesión dedicada de cardio y potencia (ver `ExerciseSelector.candidates`). El tramo lo decide la capacidad de
     * flexión: quien aún no las hace estándar no recibe una flexión explosiva.
     */
    val power = Ladder(
        pattern = RoutinePattern.POWER,
        skill = CapabilitySkill.PUSH_UP,
        easy = listOf(
            r("quads_sentadilla_sin_carga__default", "Rápida y sin salto", kind = ExKind.BALLISTIC, reps = 4..6),
            r("knee_push_up__default", "Explosiva sin despegar las manos", kind = ExKind.BALLISTIC, reps = 3..5),
        ),
        standard = listOf(
            r("quads_sentadilla_sin_carga__default", "Rápida y sin salto", kind = ExKind.BALLISTIC, reps = 4..6),
            r("push_up__flat", "Explosiva sin despegar las manos", kind = ExKind.BALLISTIC, reps = 3..5),
        ),
        hard = listOf(
            r("quads_sentadilla_sin_carga__default", "Rápida y sin salto", kind = ExKind.BALLISTIC, reps = 5..8),
            r("push_up__flat", "Explosiva sin despegar las manos", kind = ExKind.BALLISTIC, reps = 4..6),
        ),
    )

    /** Escaleras que participan en cada patrón (pueden ser varias; todas ofrecen candidatas). */
    val byPattern: Map<RoutinePattern, List<Ladder>> = mapOf(
        RoutinePattern.HORIZONTAL_PUSH to listOf(pushUp, dips),
        RoutinePattern.VERTICAL_PUSH to listOf(verticalPush),
        RoutinePattern.VERTICAL_PULL to listOf(pullUp),
        RoutinePattern.HORIZONTAL_PULL to listOf(row),
        RoutinePattern.SINGLE_LEG to listOf(singleLeg),
        RoutinePattern.SQUAT to listOf(squat),
        RoutinePattern.HINGE to listOf(hinge),
        RoutinePattern.GLUTE to listOf(glute),
        RoutinePattern.CORE_STABILITY to listOf(core),
        RoutinePattern.CORE_ROTATION to listOf(coreLateral),
        RoutinePattern.CALF to listOf(calf),
        RoutinePattern.POWER to listOf(power),
    )

    val all: List<Ladder> by lazy { byPattern.values.flatten().distinct() }

    /** Capacidad que se asume cuando la persona no la declaró: decide el nivel (el novato nunca da por hecho lo difícil). */
    fun defaultCapability(level: RoutineLevel, skill: CapabilitySkill?): CapabilityLevel = when (level) {
        RoutineLevel.NOVICE -> CapabilityLevel.NONE
        RoutineLevel.RETURNING -> when (skill) {
            CapabilitySkill.PUSH_UP -> CapabilityLevel.SOME
            else -> CapabilityLevel.NONE
        }
        RoutineLevel.INTERMEDIATE -> when (skill) {
            CapabilitySkill.PISTOL_SQUAT -> CapabilityLevel.NONE
            else -> CapabilityLevel.SOME
        }
        RoutineLevel.ADVANCED -> when (skill) {
            CapabilitySkill.PISTOL_SQUAT -> CapabilityLevel.SOME
            else -> CapabilityLevel.MANY
        }
    }

    /** Tramo pedido: la capacidad declarada o, si falta, la que el nivel deja suponer. */
    fun tierFor(ladder: Ladder, capabilities: Map<CapabilitySkill, CapabilityLevel>, level: RoutineLevel): LadderTier {
        val skill = ladder.skill
        val capability = if (skill == null) {
            // Sin habilidad declarable (core, glúteo, pantorrilla…): decide el nivel.
            when (level) {
                RoutineLevel.NOVICE, RoutineLevel.RETURNING -> CapabilityLevel.NONE
                RoutineLevel.INTERMEDIATE -> CapabilityLevel.SOME
                RoutineLevel.ADVANCED -> CapabilityLevel.MANY
            }
        } else {
            capabilities[skill] ?: defaultCapability(level, skill)
        }
        return when (capability) {
            CapabilityLevel.NONE -> LadderTier.EASY
            CapabilityLevel.SOME -> LadderTier.STANDARD
            CapabilityLevel.MANY -> LadderTier.HARD
        }
    }
}
