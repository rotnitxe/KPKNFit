package com.example.kpkn.domain.training.generator

/*
 * Modos de disciplina del generador (Fase 2): calistenia, armwrestling, strongman, base de halterofilia y las versiones «a
 * medida» de powerlifting, powerbuilding y culturismo.
 *
 * El generador es el mismo (huecos de patrón → ejercicios del material del día → ajuste a minutos); un modo de disciplina
 * solo cambia CINCO cosas, declaradas aquí y en `DisciplineWeeks` / `Prescriber`:
 * 1. el reparto de la semana (sesiones propias de la disciplina);
 * 2. las reservas de ejercicios que se anteponen a las generales (`DisciplinePools`);
 * 3. el material permitido (la calistenia no usa pesas aunque la persona las tenga);
 * 4. la prescripción (series, repeticiones y descansos de la disciplina);
 * 5. los techos de volumen de los músculos que la disciplina carga de forma distinta (el antebrazo en armwrestling).
 *
 * El catálogo actual NO cubre las disciplinas enteras: cada programa sale rotulado como «versión inicial» y dice
 * exactamente qué falta ([DisciplineSpec.missing]). Con el lote OL-1 (cargadas, arranques, tirones, enviones, sentadilla de
 * arranque, maletín y Zercher) esa lista se acortó: lo que ya existe se quitó de ella y lo que sigue sin existir (yugo, piedras,
 * tronco, eje, trineo, neumático, saco y barril; cargada y arranque desde bloques; complejos) sigue declarado.
 */

/** Qué cambia un modo de disciplina respecto al generador general. */
internal data class DisciplineSpec(
    val mode: RoutineMode,
    /** Material permitido (null = todo lo que tenga el lugar del día). */
    val allowedTiers: Set<EquipmentTier>? = null,
    /** Grupos que se anteponen a la reserva general de cada patrón. */
    val pools: Map<RoutinePattern, List<PoolGroup>> = emptyMap(),
    /** Qué falta del catálogo para cubrir la disciplina entera (vacío = el catálogo actual la cubre). */
    val missing: List<String> = emptyList(),
    /** Con qué ejercicios se sustituye lo que falta (una frase para la nota de «versión inicial»). */
    val substitutes: String = "",
    /** Patrones de relleno propios (null = los generales, filtrados por la región de la sesión). */
    val fillers: List<RoutinePattern>? = null,
    /** Multiplicadores del volumen semanal por músculo canónico (techo duro y objetivo blando). */
    val volumeScale: Map<String, Double> = emptyMap(),
    /** ¿Una prioridad muscular puede añadir huecos extra? En calistenia no: casi ningún aislamiento es de peso corporal. */
    val priorityExtras: Boolean = true,
    /** Levantamientos que el programa debería llevar: nombre y ejercicios que valen. Si alguno falta, se dice en la nota. */
    val requiredLifts: List<Pair<String, Set<String>>> = emptyList(),
)

internal object Disciplines {

    private val bodyweightTiers = setOf(EquipmentTier.BODYWEIGHT, EquipmentTier.RINGS, EquipmentTier.BAND)

    private val specs: Map<RoutineMode, DisciplineSpec> = listOf(
        DisciplineSpec(
            mode = RoutineMode.DISCIPLINE_CALISTHENICS,
            allowedTiers = bodyweightTiers,
            missing = listOf(
                "muscle-up",
                "flexión en pino (handstand push-up) y flexión a una mano",
                "dominadas con lastre, arqueras y a una mano",
                "fondos con lastre",
                "palancas (front lever, back lever) y bandera humana",
                "L-sit, V-sit y planche",
                "remo en anillas",
                "saltos y trabajo pliométrico",
            ),
            substitutes = "Mientras tanto usa las escaleras de flexión, pica (el paso hacia el pino), dominada, fondo, remo invertido, pistol, core y glúteo del catálogo, con las anillas si las tienes.",
            fillers = listOf(
                RoutinePattern.CORE_STABILITY, RoutinePattern.TRICEPS, RoutinePattern.BICEPS, RoutinePattern.GLUTE,
                RoutinePattern.CALF, RoutinePattern.BACK_EXTENSION, RoutinePattern.GRIP,
            ),
            priorityExtras = false,
        ),
        DisciplineSpec(
            mode = RoutineMode.DISCIPLINE_ARMWRESTLING,
            pools = DisciplinePools.byMode.getValue(RoutineMode.DISCIPLINE_ARMWRESTLING),
            missing = listOf(
                "desviación radial y cubital de la muñeca",
                "presión lateral y de elevación (cable atado a la mesa)",
                "trabajo de dedos y de agarre dinámico (grippers, rodillos de pinza)",
                "rotación interna del hombro con polea",
                "trabajo sobre la mesa de brazo de hierro",
            ),
            substitutes = "Mientras tanto carga antebrazo y bíceps con flexión y extensión de muñeca, pronación, supinación, curl inverso y agarre isométrico, más espalda y base de fuerza.",
            fillers = listOf(RoutinePattern.GRIP, RoutinePattern.BICEPS, RoutinePattern.REAR_DELT, RoutinePattern.CORE_ROTATION, RoutinePattern.TRICEPS),
            volumeScale = mapOf(Muscles.FOREARMS to 1.5, Muscles.BICEPS to 1.25),
        ),
        DisciplineSpec(
            mode = RoutineMode.DISCIPLINE_STRONGMAN,
            pools = DisciplinePools.byMode.getValue(RoutineMode.DISCIPLINE_STRONGMAN),
            missing = listOf(
                "yugo",
                "piedra de atlas",
                "press con log y con axle",
                "trineo (arrastre y empuje)",
                "volteo de neumático",
                "acarreos de yugo y con saco de arena o barril",
                "peso muerto con axle",
            ),
            substitutes = "Mientras tanto usa peso muerto, sentadilla (también Zercher), press estricto, push press y envión, paseo del granjero, del maletín y Zercher, y trabajo de agarre.",
            fillers = listOf(RoutinePattern.GRIP, RoutinePattern.CORE_ROTATION, RoutinePattern.TRAPS, RoutinePattern.BACK_EXTENSION, RoutinePattern.CORE_STABILITY),
            volumeScale = mapOf(Muscles.FOREARMS to 1.5, Muscles.TRAPS to 1.25, Muscles.ERECTORS to 1.25),
            requiredLifts = listOf(
                "peso muerto con barra" to setOf(
                    "conventional_deadlift__bilateral__barbell", "sumo_deadlift__barbell", "deadlift_to_knees__barbell",
                ),
                "press por encima de la cabeza" to setOf("military_press__barbell", "deltoides_push_press__default"),
            ),
        ),
        DisciplineSpec(
            mode = RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE,
            pools = DisciplinePools.byMode.getValue(RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE),
            missing = listOf(
                "cargada y arranque desde bloques",
                "la cargada con envión como complejo (los dos levantamientos seguidos)",
            ),
            substitutes = "Mientras tanto trabaja la cargada, el arranque y el envión por separado (de potencia y desde colgado), con sus tirones, la sentadilla de arranque, la sentadilla frontal y trasera, el push press, la espalda alta y el core, con movilidad en cada sesión.",
            fillers = listOf(RoutinePattern.CORE_STABILITY, RoutinePattern.TRAPS, RoutinePattern.REAR_DELT, RoutinePattern.SINGLE_LEG, RoutinePattern.CORE_ROTATION),
            requiredLifts = listOf(
                "sentadilla frontal o trasera" to setOf("front_squat__barbell", "high_bar_back_squat__barbell", "paused_back_squat__barbell"),
                "push press o envión" to setOf("deltoides_push_press__default", "push_jerk__barbell", "split_jerk__barbell"),
            ),
        ),
        DisciplineSpec(
            mode = RoutineMode.CUSTOM_POWERLIFTING,
            pools = DisciplinePools.byMode.getValue(RoutineMode.CUSTOM_POWERLIFTING),
            fillers = listOf(
                RoutinePattern.TRICEPS, RoutinePattern.CORE_STABILITY, RoutinePattern.BACK_EXTENSION, RoutinePattern.REAR_DELT,
                RoutinePattern.BICEPS, RoutinePattern.HAMSTRING_CURL, RoutinePattern.CORE_ROTATION,
            ),
            requiredLifts = listOf(
                "sentadilla de competición" to setOf(
                    "low_bar_back_squat__barbell", "high_bar_back_squat__barbell", "paused_back_squat__barbell",
                ),
                "press de banca" to setOf("bench_press__barbell", "close_grip_bench_press__barbell"),
                "peso muerto" to setOf("conventional_deadlift__bilateral__barbell", "sumo_deadlift__barbell"),
            ),
        ),
        // Powerbuilding y culturismo «a medida»: el catálogo los cubre (fuerza con básicos y aislamientos); solo cambia la prescripción.
        DisciplineSpec(mode = RoutineMode.CUSTOM_POWERBUILDING),
        DisciplineSpec(mode = RoutineMode.CUSTOM_BODYBUILDING),
    ).associateBy { it.mode }

    fun of(mode: RoutineMode): DisciplineSpec? = specs[mode]
}
