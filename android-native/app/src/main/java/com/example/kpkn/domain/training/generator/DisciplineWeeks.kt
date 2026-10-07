package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.training.generator.RoutinePattern.BACK_EXTENSION
import com.example.kpkn.domain.training.generator.RoutinePattern.BICEPS
import com.example.kpkn.domain.training.generator.RoutinePattern.CALF
import com.example.kpkn.domain.training.generator.RoutinePattern.CARRY
import com.example.kpkn.domain.training.generator.RoutinePattern.CORE_ROTATION
import com.example.kpkn.domain.training.generator.RoutinePattern.CORE_STABILITY
import com.example.kpkn.domain.training.generator.RoutinePattern.GLUTE
import com.example.kpkn.domain.training.generator.RoutinePattern.GRIP
import com.example.kpkn.domain.training.generator.RoutinePattern.HAMSTRING_CURL
import com.example.kpkn.domain.training.generator.RoutinePattern.HINGE
import com.example.kpkn.domain.training.generator.RoutinePattern.HORIZONTAL_PULL
import com.example.kpkn.domain.training.generator.RoutinePattern.HORIZONTAL_PUSH
import com.example.kpkn.domain.training.generator.RoutinePattern.POWER
import com.example.kpkn.domain.training.generator.RoutinePattern.QUAD_ISOLATION
import com.example.kpkn.domain.training.generator.RoutinePattern.REAR_DELT
import com.example.kpkn.domain.training.generator.RoutinePattern.SHOULDER_LATERAL
import com.example.kpkn.domain.training.generator.RoutinePattern.SINGLE_LEG
import com.example.kpkn.domain.training.generator.RoutinePattern.SQUAT
import com.example.kpkn.domain.training.generator.RoutinePattern.TRAPS
import com.example.kpkn.domain.training.generator.RoutinePattern.TRICEPS
import com.example.kpkn.domain.training.generator.RoutinePattern.VERTICAL_PULL
import com.example.kpkn.domain.training.generator.RoutinePattern.VERTICAL_PUSH

/**
 * Reparto de la semana de los modos de disciplina. Mismo criterio que `WeekPlanner` para la semana general (nunca siete días
 * de trabajo: el séptimo es movilidad y cardio suave; la sesión más exigente cae en el día más fresco), pero con las sesiones
 * propias de cada disciplina:
 *
 * - **Calistenia**: empuje, tirón, piernas y core con peso corporal (escaleras de progresión según las capacidades).
 * - **Armwrestling**: antebrazo y muñeca (flexión, extensión, pronación, supinación, curl inverso y agarre isométrico),
 *   espalda y bíceps, fuerza de base y una sesión de mesa y hombro.
 * - **Strongman**: peso muerto y espalda, sentadilla y press, acarreos y eventos (granjero, maletín y Zercher), empuje y espalda
 *   alta, piernas.
 * - **Base de halterofilia**: sentadilla y tirones, empuje sobre la cabeza y potencia (cargada o arranque de potencia, envión o
 *   push press y sentadilla de arranque desde el nivel intermedio), sentadilla frontal y piernas, tirones y espalda alta; movilidad
 *   en cada sesión. Cargada y arranque desde bloques y los complejos aún no están en el catálogo.
 * - **Powerlifting a medida**: sentadilla, banca y peso muerto (con sus variantes) y días de accesorios.
 */
internal object DisciplineWeeks {

    private val MAIN = ItemRole.MAIN
    private val SECONDARY = ItemRole.SECONDARY
    private val ACCESSORY = ItemRole.ACCESSORY
    private val ISOLATION = ItemRole.ISOLATION
    private val CORE_ROLE = ItemRole.CORE
    private val CARRY_ROLE = ItemRole.CARRY
    private val POWER_ROLE = ItemRole.POWER

    private fun s(pattern: RoutinePattern, role: ItemRole, tag: String? = null) = SlotSpec(pattern, role, tag = tag)

    private fun session(
        key: String,
        title: String,
        focus: String,
        slots: List<SlotSpec>,
        mobility: MobilitySpec? = null,
        cardio: CardioMode? = null,
        fillers: List<RoutinePattern>? = null,
        /** La sesión «insignia» de la disciplina pesa más que las demás para caer en el día más fresco (null = lo que dicen sus huecos). */
        flagship: Boolean = false,
    ): SessionPlan = WeekPlanner.plan(
        key, title, focus, RoutineSessionKind.STRENGTH, SessionRegion.FULL, slots,
        cardio = cardio, mobility = mobility, fillers = fillers,
    ).let { if (flagship) it.copy(demand = FLAGSHIP_DEMAND) else it }

    private const val FLAGSHIP_DEMAND = 99.0

    /** Arma la semana de [n] días con las sesiones de la disciplina: del 1.º al 6.º día, una sesión distinta; el 7.º, recuperación. */
    private fun cycle(n: Int, one: SessionPlan, sessions: List<SessionPlan>, repeats: List<SessionPlan> = emptyList()): List<SessionPlan> {
        if (n == 1) return listOf(one)
        val base = sessions + repeats
        val lifting = minOf(n, 6)
        val week = base.take(lifting)
        return if (n >= 7) week + WeekPlanner.recovery() else week
    }

    // ─── Calistenia ────────────────────────────────────────────────────────────────────────────────────────

    private fun calPush(suffix: String = "") = session(
        "CPU$suffix", "Empuje$suffix", "Flexiones, pica, fondos y tríceps con peso corporal",
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(VERTICAL_PUSH, SECONDARY), s(TRICEPS, ISOLATION),
            s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun calPull(suffix: String = "") = session(
        "CPL$suffix", "Tirón$suffix", "Dominadas, remo y agarre con peso corporal",
        listOf(
            s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(BICEPS, ISOLATION), s(GRIP, ISOLATION),
            s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun calLegs(suffix: String = "") = session(
        "CLG$suffix", "Piernas y core$suffix", "Sentadilla, una pierna, cadera y core con peso corporal",
        listOf(
            s(SQUAT, MAIN), s(SINGLE_LEG, MAIN), s(HINGE, SECONDARY), s(GLUTE, ACCESSORY), s(HAMSTRING_CURL, ISOLATION),
            s(CALF, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun calUpper() = session(
        "CUP", "Torso", "Empuje y tirón con peso corporal",
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(HORIZONTAL_PULL, SECONDARY),
            s(VERTICAL_PUSH, SECONDARY), s(TRICEPS, ISOLATION), s(BICEPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun calFull() = session(
        "CFB", "Cuerpo completo", "Tirón, empuje, piernas y core con peso corporal",
        listOf(
            s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PUSH, MAIN), s(SQUAT, SECONDARY), s(HORIZONTAL_PULL, SECONDARY),
            s(SINGLE_LEG, ACCESSORY), s(GLUTE, ACCESSORY), s(TRICEPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun calisthenics(n: Int): List<SessionPlan> = when (n) {
        1 -> listOf(calFull())
        2 -> listOf(calUpper(), calLegs())
        3 -> listOf(calPush(), calPull(), calLegs())
        4 -> listOf(calPush(), calPull(), calLegs(), calFull())
        5 -> listOf(calPush(), calPull(), calLegs(), calUpper(), calFull())
        6 -> listOf(calPush(" A"), calPull(" A"), calLegs(" A"), calPush(" B"), calPull(" B"), calLegs(" B"))
        else -> listOf(calPush(" A"), calPull(" A"), calLegs(" A"), calPush(" B"), calPull(" B"), calLegs(" B"), WeekPlanner.recovery())
    }

    // ─── Armwrestling ──────────────────────────────────────────────────────────────────────────────────────

    private fun awForearm(suffix: String = "") = session(
        "AAF$suffix", "Antebrazo y muñeca$suffix", "Flexión, extensión, pronación, supinación y agarre",
        listOf(
            s(GRIP, ISOLATION, DisciplinePools.WRIST_FLEXION), s(GRIP, ISOLATION, DisciplinePools.PRONATION),
            s(GRIP, ISOLATION, DisciplinePools.SUPINATION), s(GRIP, ISOLATION, DisciplinePools.WRIST_EXTENSION),
            s(BICEPS, ISOLATION), s(GRIP, ISOLATION, DisciplinePools.REVERSE_CURL), s(CARRY, CARRY_ROLE),
            s(GRIP, ISOLATION, DisciplinePools.HOLD),
        ),
        flagship = suffix.isEmpty(),
    )

    private fun awPull(suffix: String = "") = session(
        "AAP$suffix", "Espalda y bíceps$suffix", "Remo, dominada, bíceps y hombro posterior",
        listOf(
            s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PULL, SECONDARY), s(BICEPS, ISOLATION), s(REAR_DELT, ISOLATION),
            s(BICEPS, ISOLATION), s(TRAPS, ISOLATION), s(GRIP, ISOLATION, DisciplinePools.HOLD),
        ),
    )

    private fun awStrength() = session(
        "AAS", "Fuerza de base", "Sentadilla, peso muerto y empuje para el apoyo de piernas y espalda",
        listOf(
            s(SQUAT, MAIN), s(HINGE, SECONDARY), s(HORIZONTAL_PUSH, SECONDARY), s(VERTICAL_PUSH, ACCESSORY),
            s(TRICEPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE), s(CORE_ROTATION, CORE_ROLE),
        ),
    )

    private fun awTable() = session(
        "AAT", "Mesa y hombro", "Pronación, supinación, bíceps y hombro",
        listOf(
            s(GRIP, ISOLATION, DisciplinePools.PRONATION), s(GRIP, ISOLATION, DisciplinePools.SUPINATION),
            s(GRIP, ISOLATION, DisciplinePools.WRIST_FLEXION), s(BICEPS, ISOLATION), s(SHOULDER_LATERAL, ISOLATION),
            s(REAR_DELT, ISOLATION), s(TRICEPS, ISOLATION),
        ),
    )

    private fun awFull() = session(
        "AAB", "Cuerpo completo", "Espalda, antebrazo, bíceps y base de fuerza",
        listOf(
            s(HORIZONTAL_PULL, MAIN), s(HINGE, SECONDARY), s(HORIZONTAL_PUSH, SECONDARY), s(GRIP, ISOLATION, DisciplinePools.PRONATION),
            s(GRIP, ISOLATION, DisciplinePools.SUPINATION), s(BICEPS, ISOLATION), s(GRIP, ISOLATION, DisciplinePools.WRIST_FLEXION),
            s(GRIP, ISOLATION, DisciplinePools.WRIST_EXTENSION), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun armwrestling(n: Int): List<SessionPlan> = cycle(
        n, awFull(), listOf(awForearm(), awPull(), awStrength(), awTable()), listOf(awForearm(" B"), awPull(" B")),
    )

    // ─── Strongman ─────────────────────────────────────────────────────────────────────────────────────────

    private fun smDeadlift() = session(
        "SMD", "Peso muerto y espalda", "Peso muerto pesado, remo, dominada y agarre",
        listOf(
            s(HINGE, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(VERTICAL_PULL, ACCESSORY), s(GRIP, ISOLATION), s(TRAPS, ISOLATION),
            s(CORE_ROTATION, CORE_ROLE), s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun smSquatPress() = session(
        "SMS", "Sentadilla y press", "Sentadilla, press estricto y push press",
        listOf(
            s(SQUAT, MAIN), s(VERTICAL_PUSH, MAIN), s(SINGLE_LEG, ACCESSORY), s(HORIZONTAL_PUSH, SECONDARY),
            s(TRICEPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun smEvents(suffix: String = "") = session(
        "SME$suffix", "Acarreos y eventos$suffix", "Paseo del granjero, del maletín o Zercher, bisagra, envión o press explosivo y agarre",
        listOf(
            s(CARRY, CARRY_ROLE), s(HINGE, SECONDARY), s(VERTICAL_PUSH, SECONDARY), s(CARRY, CARRY_ROLE), s(GRIP, ISOLATION),
            s(CORE_ROTATION, CORE_ROLE),
        ),
        cardio = CardioMode.BLOCK,
    )

    private fun smUpper() = session(
        "SMU", "Empuje y espalda alta", "Press, remo y trabajo de hombro y brazo",
        listOf(
            s(VERTICAL_PUSH, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(HORIZONTAL_PULL, SECONDARY), s(REAR_DELT, ISOLATION),
            s(TRICEPS, ISOLATION), s(TRAPS, ISOLATION),
        ),
    )

    private fun smLegs() = session(
        "SMP", "Piernas y posterior", "Sentadilla secundaria, bisagra, zancadas y cadena posterior",
        listOf(
            s(SQUAT, SECONDARY), s(HINGE, SECONDARY), s(SINGLE_LEG, ACCESSORY), s(HAMSTRING_CURL, ISOLATION), s(GLUTE, ACCESSORY),
            s(CALF, ISOLATION), s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun smFull() = session(
        "SMB", "Cuerpo completo", "Peso muerto, press, sentadilla y acarreo",
        listOf(
            s(HINGE, MAIN), s(VERTICAL_PUSH, MAIN), s(SQUAT, SECONDARY), s(HORIZONTAL_PUSH, SECONDARY), s(CARRY, CARRY_ROLE),
            s(HORIZONTAL_PULL, SECONDARY), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun strongman(n: Int): List<SessionPlan> = cycle(
        n, smFull(), listOf(smDeadlift(), smSquatPress(), smEvents(), smUpper(), smLegs()), listOf(smEvents(" B")),
    )

    // ─── Base de halterofilia ──────────────────────────────────────────────────────────────────────────────

    private val liftingMobility = MobilitySpec(MobilityFocus.FULL, seconds = 8 * 60)

    private fun wlSquat() = session(
        "WLS", "Sentadilla y tirones", "Sentadilla, tirón (desde la rodilla o de cargada y arranque) y remo",
        listOf(
            s(SQUAT, MAIN), s(HINGE, SECONDARY), s(HORIZONTAL_PULL, SECONDARY), s(TRAPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
            s(BACK_EXTENSION, CORE_ROLE),
        ),
        mobility = liftingMobility,
    )

    /**
     * Día de la técnica olímpica: primero el hueco «olímpico» (cargada o arranque de potencia, con el cuerpo fresco), después el empuje
     * explosivo (envión o push press, o el swing sin barra), el press estricto, la sentadilla de arranque como accesorio de movilidad
     * y la espalda alta. Sin barra o con nivel de novato el hueco olímpico queda vacío (no hay cargada ni arranque) y la sesión sigue
     * con lo demás.
     */
    private fun wlOverhead(suffix: String = "") = session(
        "WLO$suffix", "Empuje sobre la cabeza y potencia$suffix",
        "Cargada o arranque de potencia, envión o push press, press estricto y espalda alta",
        listOf(
            s(POWER, POWER_ROLE, DisciplinePools.OLYMPIC), s(POWER, POWER_ROLE, DisciplinePools.OVERHEAD), s(VERTICAL_PUSH, MAIN),
            s(SQUAT, ACCESSORY), s(VERTICAL_PULL, SECONDARY), s(REAR_DELT, ISOLATION), s(TRICEPS, ISOLATION),
            s(CORE_ROTATION, CORE_ROLE),
        ),
        mobility = liftingMobility,
    )

    private fun wlFront(suffix: String = "") = session(
        "WLF$suffix", "Sentadilla frontal y piernas$suffix", "Sentadilla frontal, una pierna, cadera y core",
        listOf(
            s(SQUAT, MAIN), s(SINGLE_LEG, SECONDARY), s(HINGE, ACCESSORY), s(CALF, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
        ),
        mobility = liftingMobility,
    )

    private fun wlPull() = session(
        "WLT", "Tirones y espalda alta", "Tirón pesado, remo, trapecio y brazos",
        listOf(
            s(HINGE, MAIN), s(HORIZONTAL_PULL, MAIN), s(TRAPS, ISOLATION), s(REAR_DELT, ISOLATION), s(BICEPS, ISOLATION), s(GRIP, ISOLATION),
        ),
        mobility = liftingMobility,
    )

    /**
     * Sesión de un solo día: cargada o arranque de potencia, sentadilla y empuje sobre la cabeza (push press o envión, que la reserva
     * de empuje vertical de la halterofilia pone delante del press estricto en el hueco secundario). Con poco tiempo solo caben esos
     * tres huecos, y juntos ya son cuerpo completo (pierna y empuje); el tirón, el remo y el empuje horizontal entran con más minutos.
     */
    private fun wlFull() = session(
        "WLB", "Cuerpo completo", "Cargada o arranque de potencia, sentadilla, envión o push press, tirón y core",
        listOf(
            s(POWER, POWER_ROLE, DisciplinePools.OLYMPIC), s(SQUAT, MAIN), s(VERTICAL_PUSH, SECONDARY),
            s(HINGE, SECONDARY), s(HORIZONTAL_PULL, SECONDARY), s(HORIZONTAL_PUSH, SECONDARY), s(CORE_STABILITY, CORE_ROLE),
        ),
        mobility = liftingMobility,
    )

    private fun weightlifting(n: Int): List<SessionPlan> = cycle(
        n, wlFull(), listOf(wlSquat(), wlOverhead(), wlFront(), wlPull()), listOf(wlOverhead(" B"), wlFront(" B")),
    )

    // ─── Powerlifting a medida ─────────────────────────────────────────────────────────────────────────────

    private fun plSquat() = session(
        "PLS", "Sentadilla", "Sentadilla de competición, su variante, bisagra y cadena posterior",
        listOf(
            s(SQUAT, MAIN), s(SQUAT, SECONDARY), s(HINGE, ACCESSORY), s(HAMSTRING_CURL, ISOLATION), s(CORE_STABILITY, CORE_ROLE),
            s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun plBench() = session(
        "PLB", "Banca", "Press de banca, su variante, hombro, espalda y tríceps",
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(VERTICAL_PUSH, ACCESSORY), s(HORIZONTAL_PULL, SECONDARY),
            s(TRICEPS, ISOLATION), s(REAR_DELT, ISOLATION),
        ),
    )

    private fun plDeadlift() = session(
        "PLD", "Peso muerto", "Peso muerto, su variante, remo y agarre",
        listOf(
            s(HINGE, MAIN), s(HINGE, SECONDARY), s(HORIZONTAL_PULL, SECONDARY), s(VERTICAL_PULL, ACCESSORY), s(GRIP, ISOLATION),
            s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun plUpper() = session(
        "PLU", "Torso y accesorios", "Press inclinado y de hombro, tirones, bíceps y tríceps",
        listOf(
            s(HORIZONTAL_PUSH, SECONDARY), s(VERTICAL_PUSH, MAIN), s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PULL, SECONDARY),
            s(TRICEPS, ISOLATION), s(BICEPS, ISOLATION),
        ),
    )

    private fun plLegs() = session(
        "PLL", "Piernas accesorias", "Sentadilla secundaria, una pierna, glúteo, isquios y cuádriceps",
        listOf(
            s(SQUAT, SECONDARY), s(SINGLE_LEG, ACCESSORY), s(GLUTE, ACCESSORY), s(HAMSTRING_CURL, ISOLATION),
            s(QUAD_ISOLATION, ISOLATION), s(CALF, ISOLATION),
        ),
    )

    private fun plBack() = session(
        "PLX", "Espalda y peso muerto variante", "Peso muerto variante, remo, dominada y lumbar",
        listOf(
            s(HINGE, SECONDARY), s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PULL, SECONDARY), s(GRIP, ISOLATION), s(BACK_EXTENSION, CORE_ROLE),
            s(BICEPS, ISOLATION),
        ),
    )

    private fun plLowerHeavy() = session(
        "PLH", "Sentadilla y peso muerto", "Los dos levantamientos de pierna y su cadena posterior",
        listOf(
            s(SQUAT, MAIN), s(HINGE, MAIN), s(HAMSTRING_CURL, ISOLATION), s(CORE_STABILITY, CORE_ROLE), s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun plUpperHeavy() = session(
        "PLP", "Banca y espalda", "Press de banca y de hombro con remo y dominada",
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PUSH, SECONDARY), s(VERTICAL_PULL, SECONDARY),
            s(TRICEPS, ISOLATION), s(BICEPS, ISOLATION),
        ),
    )

    private fun plFull() = session(
        "PLB1", "Cuerpo completo", "Sentadilla, banca, peso muerto y remo",
        listOf(
            s(SQUAT, MAIN), s(HORIZONTAL_PUSH, MAIN), s(HINGE, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(CORE_STABILITY, CORE_ROLE),
        ),
    )

    private fun powerlifting(n: Int): List<SessionPlan> = when (n) {
        1 -> listOf(plFull())
        2 -> listOf(plLowerHeavy(), plUpperHeavy())
        3 -> listOf(plSquat(), plBench(), plDeadlift())
        4 -> listOf(plSquat(), plBench(), plDeadlift(), plUpper())
        5 -> listOf(plSquat(), plBench(), plDeadlift(), plUpper(), plLegs())
        6 -> listOf(plSquat(), plBench(), plDeadlift(), plUpper(), plLegs(), plBack())
        else -> listOf(plSquat(), plBench(), plDeadlift(), plUpper(), plLegs(), plBack(), WeekPlanner.recovery())
    }

    // ─── Entrada ───────────────────────────────────────────────────────────────────────────────────────────

    /** Semana de la disciplina (orden cíclico canónico) o null si el modo usa los repartos generales de fuerza. */
    fun plans(mode: RoutineMode, n: Int): List<SessionPlan>? = when (mode) {
        RoutineMode.DISCIPLINE_CALISTHENICS -> calisthenics(n)
        RoutineMode.DISCIPLINE_ARMWRESTLING -> armwrestling(n)
        RoutineMode.DISCIPLINE_STRONGMAN -> strongman(n)
        RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE -> weightlifting(n)
        RoutineMode.CUSTOM_POWERLIFTING -> powerlifting(n)
        else -> null
    }

    /** Frase corta del reparto de la semana para el resumen. */
    fun describe(mode: RoutineMode, n: Int): String {
        val recovery = if (n >= 7) ", más un día de movilidad y cardio suave" else ""
        return when (mode) {
            RoutineMode.DISCIPLINE_CALISTHENICS -> when (n) {
                1 -> "una sesión de cuerpo completo con peso corporal"
                2 -> "torso y piernas con peso corporal"
                3 -> "empuje, tirón y piernas con peso corporal"
                else -> "empuje, tirón y piernas con peso corporal y core$recovery"
            }
            RoutineMode.DISCIPLINE_ARMWRESTLING -> when (n) {
                1 -> "una sesión de antebrazo, espalda y base de fuerza"
                2 -> "antebrazo y muñeca, y espalda y bíceps"
                else -> "antebrazo y muñeca, espalda y bíceps, fuerza de base y mesa y hombro$recovery"
            }
            RoutineMode.DISCIPLINE_STRONGMAN -> when (n) {
                1 -> "una sesión de peso muerto, press, sentadilla y acarreo"
                2 -> "peso muerto y espalda, y sentadilla y press"
                else -> "peso muerto, sentadilla y press, acarreos y eventos, y trabajo de empuje y piernas$recovery"
            }
            RoutineMode.DISCIPLINE_WEIGHTLIFTING_BASE -> when (n) {
                1 -> "una sesión de sentadilla, potencia, empuje sobre la cabeza y tirones con movilidad"
                2 -> "sentadilla y tirones, y empuje sobre la cabeza y potencia, con movilidad"
                else -> "sentadilla, empuje sobre la cabeza y potencia, sentadilla frontal y tirones, con movilidad$recovery"
            }
            RoutineMode.CUSTOM_POWERLIFTING -> when (n) {
                1 -> "una sesión con sentadilla, banca y peso muerto"
                2 -> "sentadilla y peso muerto, y banca y espalda"
                3 -> "un día de sentadilla, uno de banca y uno de peso muerto"
                else -> "sentadilla, banca y peso muerto, más días de accesorios$recovery"
            }
            else -> ""
        }
    }
}
