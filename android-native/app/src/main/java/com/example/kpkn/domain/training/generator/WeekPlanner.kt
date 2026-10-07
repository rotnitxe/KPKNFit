package com.example.kpkn.domain.training.generator

import com.example.kpkn.domain.training.generator.RoutinePattern.BACK_EXTENSION
import com.example.kpkn.domain.training.generator.RoutinePattern.BICEPS
import com.example.kpkn.domain.training.generator.RoutinePattern.CALF
import com.example.kpkn.domain.training.generator.RoutinePattern.CARRY
import com.example.kpkn.domain.training.generator.RoutinePattern.CHEST_ISOLATION
import com.example.kpkn.domain.training.generator.RoutinePattern.CORE_ROTATION
import com.example.kpkn.domain.training.generator.RoutinePattern.CORE_STABILITY
import com.example.kpkn.domain.training.generator.RoutinePattern.GLUTE
import com.example.kpkn.domain.training.generator.RoutinePattern.GRIP
import com.example.kpkn.domain.training.generator.RoutinePattern.HAMSTRING_CURL
import com.example.kpkn.domain.training.generator.RoutinePattern.HINGE
import com.example.kpkn.domain.training.generator.RoutinePattern.HORIZONTAL_PULL
import com.example.kpkn.domain.training.generator.RoutinePattern.HORIZONTAL_PUSH
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
 * Reparto de la semana: qué sesiones lleva según los días, el modo, el nivel y los minutos, y en qué día cae cada una.
 *
 * Criterios de dominio (los repartos reales del catálogo de splits de la app, `fullbody_x3`, `ul_fb_x3`, `ul_x4`,
 * `ppl_ul` y `ppl_x6`, dan los nombres y el orden):
 * - 1 día = cuerpo completo; 2 = cuerpo completo A/B; 3 = cuerpo completo A/B/C (novato y retorno) o torso/pierna/cuerpo
 *   completo (intermedio o avanzado con ≥ 60 min); 4 = torso/pierna ×2; 5 = torso/pierna + empuje/tirón/pierna;
 *   6 = empuje/tirón/pierna ×2; 7 = como 6 más un día de movilidad y cardio suave (nunca siete días de pesas: sin un día
 *   de recuperación activa el volumen semanal de recuperación no se sostiene).
 * - Híbrido: sesiones de fuerza, de cardio y mixtas repartidas; el cardio no se recorta nunca.
 * - Funcional: cuerpo completo funcional en cada sesión (bisagra, sentadilla, empuje, tirón, acarreo, rotación) con
 *   potencia, cardio de zona 2 y movilidad.
 * - La sesión más exigente (la de más compuestos pesados de pierna) cae en el día más fresco: se gira el ciclo semanal
 *   entero (la secuencia de sesiones se conserva, así que el torso y la pierna siguen alternando).
 */
internal object WeekPlanner {

    private val MAIN = ItemRole.MAIN
    private val SECONDARY = ItemRole.SECONDARY
    private val ACCESSORY = ItemRole.ACCESSORY
    private val ISOLATION = ItemRole.ISOLATION
    private val CORE_ROLE = ItemRole.CORE
    private val CARRY_ROLE = ItemRole.CARRY
    private val POWER_ROLE = ItemRole.POWER

    private fun s(pattern: RoutinePattern, role: ItemRole, pair: Int? = null) = SlotSpec(pattern, role, pairKey = pair)

    private fun roleWeight(role: ItemRole): Double = when (role) {
        ItemRole.MAIN -> 1.0
        ItemRole.SECONDARY -> 0.6
        ItemRole.ACCESSORY -> 0.3
        ItemRole.ISOLATION -> 0.1
        ItemRole.POWER -> 0.4
        ItemRole.CARRY -> 0.2
        ItemRole.CORE -> 0.1
    }

    private fun demandOf(slots: List<SlotSpec>): Double =
        slots.sumOf { PatternCatalog.of(it.pattern).demandWeight * roleWeight(it.role) }

    internal fun plan(
        key: String,
        title: String,
        focus: String,
        kind: RoutineSessionKind,
        region: SessionRegion,
        slots: List<SlotSpec>,
        cardio: CardioMode? = null,
        mobility: MobilitySpec? = null,
        supersets: Boolean = false,
        fillers: List<RoutinePattern>? = null,
    ) = SessionPlan(key, title, focus, kind, region, slots, cardio, mobility, supersets, demandOf(slots), fillers)

    // ─── Bloques de fuerza y masa muscular ─────────────────────────────────────────────────────────────────

    private fun fullBody(title: String = "Cuerpo completo") = plan(
        "FB1", title, "Todo el cuerpo en una sesión: piernas, empuje y tirón",
        RoutineSessionKind.STRENGTH, SessionRegion.FULL,
        listOf(
            s(SQUAT, MAIN), s(HORIZONTAL_PUSH, MAIN), s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PULL, SECONDARY),
            s(HINGE, SECONDARY), s(VERTICAL_PUSH, SECONDARY), s(SINGLE_LEG, ACCESSORY), s(CORE_STABILITY, CORE_ROLE),
            s(SHOULDER_LATERAL, ISOLATION, 1), s(BICEPS, ISOLATION, 1), s(TRICEPS, ISOLATION, 2), s(CALF, ISOLATION, 2),
            s(GLUTE, ACCESSORY), s(REAR_DELT, ISOLATION),
        ),
    )

    private fun fbA(title: String = "Cuerpo completo A") = plan(
        "FBA", title, "Sentadilla, empuje horizontal y remo",
        RoutineSessionKind.STRENGTH, SessionRegion.FULL,
        listOf(
            s(SQUAT, MAIN), s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(HINGE, SECONDARY),
            s(CORE_STABILITY, CORE_ROLE), s(SHOULDER_LATERAL, ISOLATION, 1), s(BICEPS, ISOLATION, 1),
            s(CALF, ISOLATION, 2), s(TRICEPS, ISOLATION, 2), s(GLUTE, ACCESSORY), s(VERTICAL_PULL, ACCESSORY),
        ),
    )

    private fun fbB(title: String = "Cuerpo completo B") = plan(
        "FBB", title, "Bisagra, empuje vertical y tracción vertical",
        RoutineSessionKind.STRENGTH, SessionRegion.FULL,
        listOf(
            s(HINGE, MAIN), s(VERTICAL_PUSH, MAIN), s(VERTICAL_PULL, SECONDARY), s(SINGLE_LEG, SECONDARY),
            s(CORE_ROTATION, CORE_ROLE), s(REAR_DELT, ISOLATION, 1), s(TRICEPS, ISOLATION, 1), s(GLUTE, ACCESSORY),
            s(BICEPS, ISOLATION, 2), s(CALF, ISOLATION, 2), s(HORIZONTAL_PULL, ACCESSORY),
        ),
    )

    private fun fbC(title: String = "Cuerpo completo C") = plan(
        "FBC", title, "Sentadilla variante, remo y press inclinado",
        RoutineSessionKind.STRENGTH, SessionRegion.FULL,
        listOf(
            s(SQUAT, MAIN), s(HORIZONTAL_PULL, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(GLUTE, SECONDARY),
            s(VERTICAL_PULL, ACCESSORY), s(CORE_STABILITY, CORE_ROLE), s(BICEPS, ISOLATION, 1),
            s(SHOULDER_LATERAL, ISOLATION, 1), s(CALF, ISOLATION, 2), s(TRICEPS, ISOLATION, 2), s(VERTICAL_PUSH, ACCESSORY),
        ),
    )

    private fun torsoA(title: String = "Torso A") = plan(
        "TA", title, "Empuje y tirón horizontales con trabajo de hombros y brazos",
        RoutineSessionKind.STRENGTH, SessionRegion.UPPER,
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PUSH, SECONDARY), s(VERTICAL_PULL, SECONDARY),
            s(SHOULDER_LATERAL, ISOLATION, 1), s(REAR_DELT, ISOLATION, 1), s(TRICEPS, ISOLATION, 2), s(BICEPS, ISOLATION, 2),
            s(CORE_STABILITY, CORE_ROLE), s(CHEST_ISOLATION, ISOLATION), s(TRAPS, ISOLATION),
        ),
    )

    private fun torsoB(title: String = "Torso B") = plan(
        "TB", title, "Empuje y tirón verticales con volumen de espalda alta y brazos",
        RoutineSessionKind.STRENGTH, SessionRegion.UPPER,
        listOf(
            s(VERTICAL_PUSH, MAIN), s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(HORIZONTAL_PULL, SECONDARY),
            s(REAR_DELT, ISOLATION, 1), s(SHOULDER_LATERAL, ISOLATION, 1), s(BICEPS, ISOLATION, 2), s(TRICEPS, ISOLATION, 2),
            s(CORE_ROTATION, CORE_ROLE), s(CHEST_ISOLATION, ISOLATION), s(GRIP, ISOLATION), s(TRAPS, ISOLATION),
        ),
    )

    private fun legsA(title: String = "Pierna A") = plan(
        "LA", title, "Sentadilla, bisagra y trabajo de cuádriceps",
        RoutineSessionKind.STRENGTH, SessionRegion.LEGS,
        listOf(
            s(SQUAT, MAIN), s(HINGE, SECONDARY), s(SINGLE_LEG, ACCESSORY), s(HAMSTRING_CURL, ISOLATION, 1),
            s(QUAD_ISOLATION, ISOLATION, 1), s(CALF, ISOLATION, 2), s(CORE_STABILITY, CORE_ROLE, 2), s(GLUTE, ACCESSORY),
            s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun legsB(title: String = "Pierna B") = plan(
        "LB", title, "Bisagra de cadera, glúteo y sentadilla secundaria",
        RoutineSessionKind.STRENGTH, SessionRegion.LEGS,
        listOf(
            s(HINGE, MAIN), s(SQUAT, SECONDARY), s(GLUTE, ACCESSORY), s(SINGLE_LEG, ACCESSORY),
            s(HAMSTRING_CURL, ISOLATION, 1), s(CALF, ISOLATION, 1), s(CORE_ROTATION, CORE_ROLE), s(QUAD_ISOLATION, ISOLATION),
            s(BACK_EXTENSION, CORE_ROLE),
        ),
    )

    private fun pushA(title: String = "Empuje A") = plan(
        "PA", title, "Pecho, hombros y tríceps",
        RoutineSessionKind.STRENGTH, SessionRegion.PUSH,
        listOf(
            s(HORIZONTAL_PUSH, MAIN), s(VERTICAL_PUSH, SECONDARY), s(CHEST_ISOLATION, ISOLATION), s(SHOULDER_LATERAL, ISOLATION, 1),
            s(TRICEPS, ISOLATION, 1), s(TRICEPS, ISOLATION), s(CORE_STABILITY, CORE_ROLE), s(HORIZONTAL_PUSH, ACCESSORY),
        ),
    )

    private fun pushB(title: String = "Empuje B") = plan(
        "PB", title, "Hombros y pecho con volumen de brazos",
        RoutineSessionKind.STRENGTH, SessionRegion.PUSH,
        listOf(
            s(VERTICAL_PUSH, MAIN), s(HORIZONTAL_PUSH, SECONDARY), s(SHOULDER_LATERAL, ISOLATION, 1), s(TRICEPS, ISOLATION, 1),
            s(CHEST_ISOLATION, ISOLATION), s(SHOULDER_LATERAL, ISOLATION), s(CORE_ROTATION, CORE_ROLE), s(TRICEPS, ISOLATION),
        ),
    )

    private fun pullA(title: String = "Tirón A") = plan(
        "UA", title, "Espalda ancha, hombro posterior y bíceps",
        RoutineSessionKind.STRENGTH, SessionRegion.PULL,
        listOf(
            s(VERTICAL_PULL, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(REAR_DELT, ISOLATION, 1), s(BICEPS, ISOLATION, 1),
            s(BICEPS, ISOLATION), s(TRAPS, ISOLATION), s(BACK_EXTENSION, CORE_ROLE), s(GRIP, ISOLATION),
        ),
    )

    private fun pullB(title: String = "Tirón B") = plan(
        "UB", title, "Espalda gruesa, trapecio y brazos",
        RoutineSessionKind.STRENGTH, SessionRegion.PULL,
        listOf(
            s(HORIZONTAL_PULL, MAIN), s(VERTICAL_PULL, SECONDARY), s(REAR_DELT, ISOLATION, 1), s(BICEPS, ISOLATION, 1),
            s(TRAPS, ISOLATION), s(BICEPS, ISOLATION), s(CORE_ROTATION, CORE_ROLE), s(GRIP, ISOLATION),
        ),
    )

    internal fun recovery(): SessionPlan = plan(
        "REC", "Movilidad y cardio suave", "Recuperación activa: movilidad y una caminata fácil",
        RoutineSessionKind.MOBILITY, SessionRegion.NONE, emptyList(),
        cardio = CardioMode.LIGHT_FILL,
        mobility = MobilitySpec(MobilityFocus.RECOVERY, seconds = 0, fill = true),
    )

    /**
     * Sesiones de cuerpo completo que sustituyen a una sesión específica (empuje, tirón, pierna, torso) cuando el material de
     * su día no permite casi ninguno de sus huecos: p. ej. un «Tirón» sin barra, bandas ni mancuernas solo tendría un
     * ejercicio. Mejor trabajar el cuerpo entero con lo que hay que dejar el día casi vacío.
     */
    fun fullBodyFallbacks(): List<SessionPlan> = listOf(fbA("Cuerpo completo A"), fbB("Cuerpo completo B"), fbC("Cuerpo completo C"))

    private fun strengthWeek(ctx: GenContext, n: Int): List<SessionPlan> {
        val experienced = ctx.level == RoutineLevel.INTERMEDIATE || ctx.level == RoutineLevel.ADVANCED
        return when (n) {
            1 -> listOf(fullBody())
            2 -> listOf(fbA(), fbB())
            3 -> if (experienced && ctx.targetMinutes >= 60) {
                listOf(torsoA("Torso"), legsA("Pierna"), fbC("Cuerpo completo"))
            } else {
                listOf(fbA(), fbB(), fbC())
            }
            4 -> listOf(torsoA("Torso A"), legsA("Pierna A"), torsoB("Torso B"), legsB("Pierna B"))
            5 -> listOf(torsoA("Torso"), legsA("Pierna"), pushA("Empuje"), pullA("Tirón"), legsB("Pierna (bisagra y glúteo)"))
            6 -> listOf(pushA("Empuje A"), pullA("Tirón A"), legsA("Pierna A"), pushB("Empuje B"), pullB("Tirón B"), legsB("Pierna B"))
            else -> listOf(
                pushA("Empuje A"), pullA("Tirón A"), legsA("Pierna A"), pushB("Empuje B"), pullB("Tirón B"), legsB("Pierna B"),
                recovery(),
            )
        }
    }

    // ─── Híbrido: fuerza, cardio y mixtas ─────────────────────────────────────────────────────────────────

    private fun hybridStrength(base: SessionPlan, title: String, key: String): SessionPlan =
        base.copy(key = key, title = title)

    private fun cardioZone2(): SessionPlan = plan(
        "CZ2", "Cardio · Zona 2", "Cardio continuo a ritmo conversacional",
        RoutineSessionKind.CARDIO, SessionRegion.NONE, emptyList(),
        cardio = CardioMode.ZONE2_FILL,
    )

    private fun cardioPower(): SessionPlan = plan(
        "CPW", "Cardio y potencia", "Potencia explosiva y cardio por intervalos",
        RoutineSessionKind.MIXED, SessionRegion.NONE,
        listOf(s(RoutinePattern.POWER, POWER_ROLE), s(RoutinePattern.POWER, POWER_ROLE)),
        cardio = CardioMode.INTERVALS_FILL,
    )

    private fun mixed(): SessionPlan = plan(
        "MIX", "Mixta · Fuerza y cardio", "Fuerza compacta de cuerpo completo y un bloque de cardio",
        RoutineSessionKind.MIXED, SessionRegion.FULL,
        listOf(
            s(SQUAT, MAIN), s(HORIZONTAL_PUSH, MAIN), s(HORIZONTAL_PULL, SECONDARY), s(HINGE, SECONDARY),
            s(CORE_STABILITY, CORE_ROLE), s(VERTICAL_PULL, ACCESSORY), s(VERTICAL_PUSH, ACCESSORY), s(CARRY, CARRY_ROLE),
        ),
        cardio = CardioMode.BLOCK,
    )

    private fun hybridWeek(ctx: GenContext, n: Int): List<SessionPlan> {
        val mixedFeasible = ctx.targetMinutes >= 30
        val legs = hybridStrength(legsA("Fuerza · Pierna"), "Fuerza · Pierna", "HLA")
        val upper = hybridStrength(torsoA("Fuerza · Torso"), "Fuerza · Torso", "HTA")
        val full = hybridStrength(fbA("Fuerza · Cuerpo completo"), "Fuerza · Cuerpo completo", "HFB")
        val mix = if (mixedFeasible) mixed() else full.copy(key = "HFB2")
        return when (n) {
            1 -> listOf(if (mixedFeasible) mixed() else full)
            2 -> listOf(full, cardioPower())
            3 -> listOf(legs, cardioPower(), upper)
            4 -> listOf(legs, upper, cardioPower(), mix)
            5 -> listOf(legs, upper, cardioZone2(), mix, cardioPower())
            6 -> listOf(legs, upper, cardioZone2(), full.copy(key = "HFB3", title = "Fuerza · Cuerpo completo B"), cardioPower(), mix)
            else -> listOf(legs, upper, cardioZone2(), full.copy(key = "HFB3", title = "Fuerza · Cuerpo completo B"), cardioPower(), mix, recovery())
        }
    }

    // ─── Funcional ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Sesión funcional de cuerpo completo (tres plantillas que rotan). Cada superserie junta un patrón de pierna con uno de
     * torso (no compiten entre sí), y las parejas van ordenadas para que, con poco tiempo (45 min o menos: solo caben la
     * potencia, una o dos parejas y el acarreo), la semana siga cubriendo sentadilla, bisagra, una pierna, empujes y tirones:
     * A abre con sentadilla + remo; B con bisagra + empuje vertical; C con una pierna + empuje horizontal.
     */
    private fun functional(index: Int): SessionPlan {
        val letter = ('A' + (index % 6))
        val template = index % 3
        val slots = when (template) {
            0 -> listOf(
                s(RoutinePattern.POWER, POWER_ROLE),
                s(SQUAT, MAIN, 1), s(HORIZONTAL_PULL, MAIN, 1),
                s(HORIZONTAL_PUSH, MAIN, 2), s(HINGE, SECONDARY, 2),
                s(CARRY, CARRY_ROLE, 3), s(CORE_ROTATION, CORE_ROLE, 3),
                s(VERTICAL_PULL, ACCESSORY, 4), s(SINGLE_LEG, ACCESSORY, 4),
            )
            1 -> listOf(
                s(RoutinePattern.POWER, POWER_ROLE),
                s(HINGE, MAIN, 1), s(VERTICAL_PUSH, MAIN, 1),
                s(VERTICAL_PULL, MAIN, 2), s(SINGLE_LEG, SECONDARY, 2),
                s(CARRY, CARRY_ROLE, 3), s(CORE_STABILITY, CORE_ROLE, 3),
                s(SHOULDER_LATERAL, ISOLATION, 4), s(GLUTE, ACCESSORY, 4),
            )
            else -> listOf(
                s(RoutinePattern.POWER, POWER_ROLE),
                s(SINGLE_LEG, MAIN, 1), s(HORIZONTAL_PUSH, MAIN, 1),
                s(HORIZONTAL_PULL, MAIN, 2), s(SQUAT, SECONDARY, 2),
                s(CARRY, CARRY_ROLE, 3), s(CORE_ROTATION, CORE_ROLE, 3),
                s(REAR_DELT, ISOLATION, 4), s(CALF, ISOLATION, 4),
            )
        }
        val focus = when (template) {
            0 -> "Sentadilla, remo y empuje con acarreo, cardio suave y movilidad"
            1 -> "Bisagra, empuje y tracción verticales con acarreo, cardio suave y movilidad"
            else -> "Una pierna, empuje y remo con acarreo, cardio suave y movilidad"
        }
        return plan(
            "FN$index", "Funcional $letter", focus,
            RoutineSessionKind.STRENGTH, SessionRegion.FULL, slots,
            cardio = CardioMode.BLOCK,
            mobility = MobilitySpec(MobilityFocus.FULL, seconds = 240),
            supersets = true,
        )
    }

    private fun functionalWeek(n: Int): List<SessionPlan> {
        val sessions = (0 until minOf(n, 6)).map { functional(it) }
        return if (n >= 7) sessions + recovery() else sessions
    }

    // ─── Entrada ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Culturismo «a medida»: la semana se reparte por grupos musculares (cuerpo completo; torso/pierna; empuje, tirón y pierna)
     * en vez de por patrones de fuerza, con más frecuencia por músculo que un reparto de fuerza.
     */
    private fun bodybuildingWeek(n: Int): List<SessionPlan> = when (n) {
        1 -> listOf(fullBody())
        2 -> listOf(torsoA("Torso"), legsA("Pierna"))
        3 -> listOf(pushA("Empuje"), pullA("Tirón"), legsA("Pierna"))
        4 -> listOf(torsoA("Torso A"), legsA("Pierna A"), torsoB("Torso B"), legsB("Pierna B"))
        5 -> listOf(pushA("Empuje"), pullA("Tirón"), legsA("Pierna"), torsoB("Torso"), legsB("Pierna B"))
        6 -> listOf(pushA("Empuje A"), pullA("Tirón A"), legsA("Pierna A"), pushB("Empuje B"), pullB("Tirón B"), legsB("Pierna B"))
        else -> listOf(
            pushA("Empuje A"), pullA("Tirón A"), legsA("Pierna A"), pushB("Empuje B"), pullB("Tirón B"), legsB("Pierna B"),
            recovery(),
        )
    }

    /** Sesiones de la semana en orden CÍCLICO canónico (una por día de entreno). */
    fun plans(ctx: GenContext): List<SessionPlan> {
        val n = ctx.days.size
        val discipline = ctx.discipline
        val raw = (DisciplineWeeks.plans(ctx.mode, n) ?: when (ctx.mode) {
            RoutineMode.GENERAL_HYBRID -> hybridWeek(ctx, n)
            RoutineMode.GENERAL_FUNCTIONAL -> functionalWeek(n)
            RoutineMode.CUSTOM_BODYBUILDING -> bodybuildingWeek(n)
            else -> strengthWeek(ctx, n)
        }).map { plan ->
            // El relleno de la sesión sale de la disciplina (el antebrazo no se rellena con zancadas ni con elevaciones laterales).
            if (plan.fillers == null && plan.slots.isNotEmpty() && discipline?.fillers != null) plan.copy(fillers = discipline.fillers) else plan
        }
        return applyPriorities(raw, ctx)
    }

    /**
     * Las prioridades suben las series de los huecos de su músculo (+25 %, redondeado hacia arriba), los adelantan en
     * la sesión y añaden un hueco extra en cada sesión de fuerza que admita ese músculo (hasta dos por sesión).
     */
    fun applyPriorities(plans: List<SessionPlan>, ctx: GenContext): List<SessionPlan> {
        val symbols = ctx.request.priorityMuscles.distinct().take(5)
        if (symbols.isEmpty()) return plans
        val allowExtras = ctx.discipline?.priorityExtras ?: true
        val targets = symbols.map { PriorityTargets.of(it) }
        val boostedPatterns = targets.flatMap { it.patterns }.toSet()
        return plans.map { plan ->
            if (plan.slots.isEmpty() || plan.kind == RoutineSessionKind.CARDIO || plan.kind == RoutineSessionKind.MOBILITY) return@map plan
            val boosted = plan.slots.map { slot ->
                if (slot.pattern in boostedPatterns && slot.role != ItemRole.POWER) slot.copy(boosted = true) else slot
            }
            val extras = ArrayList<SlotSpec>()
            if (allowExtras) targets.forEach { target ->
                val inPlan = boosted.count { it.pattern in target.patterns }
                val candidates = target.extra.filter { (pattern, _) -> PatternCatalog.accepts(plan.region, pattern) }
                if (candidates.isEmpty()) return@forEach
                var needed = (2 - inPlan).coerceIn(0, 2)
                for ((pattern, role) in candidates) {
                    if (needed == 0) break
                    extras += SlotSpec(pattern, role, extra = true, boosted = true)
                    needed--
                }
            }
            val ordered = (boosted + extras).withIndex().sortedWith(
                compareBy<IndexedValue<SlotSpec>> { inclusionGroup(it.value) }.thenBy { it.index },
            ).map { it.value }
            plan.copy(slots = ordered, demand = demandOf(ordered))
        }
    }

    /** Orden de inclusión: compuestos primero; un hueco prioritario de accesorio o aislamiento pasa por delante del resto. */
    private fun inclusionGroup(slot: SlotSpec): Int = when {
        slot.role == ItemRole.POWER || slot.role == ItemRole.MAIN || slot.role == ItemRole.SECONDARY -> 0
        slot.boosted -> 1
        slot.role == ItemRole.ACCESSORY -> 2
        else -> 3
    }

    // ─── Día más fresco y reparto en días ──────────────────────────────────────────────────────────────────

    /**
     * Día de la sesión más exigente: [freshest] si se entrena ese día; si no, el primer día de entreno posterior (en el ciclo
     * semanal). Sin día declarado, el que sigue al descanso más largo (empate: el primero del ciclo).
     */
    fun mainDay(days: List<Int>, freshest: Int?): Int {
        if (days.size == 1) return days[0]
        if (freshest != null) {
            if (freshest in days) return freshest
            return days.minByOrNull { (it - freshest + 7) % 7 } ?: days[0]
        }
        var best = days[0]
        var bestGap = -1
        for (day in days) {
            val gap = days.filter { it != day }.minOf { other -> (day - other + 7) % 7 } - 1
            if (gap > bestGap) {
                bestGap = gap
                best = day
            }
        }
        return best
    }

    /** Reparte [plans] (orden cíclico canónico) en [days] girando el ciclo para que la sesión más exigente caiga en [mainDay]. */
    fun arrange(plans: List<SessionPlan>, days: List<Int>, mainDay: Int): List<SessionPlan> {
        val n = plans.size
        if (n <= 1) return plans
        val strongest = plans.indices.maxByOrNull { plans[it].demand } ?: 0
        // maxByOrNull devuelve el primero ante un empate: la plantilla que viene antes en el ciclo.
        val mainIndex = days.indexOf(mainDay).coerceAtLeast(0)
        return List(n) { dayIndex -> plans[Math.floorMod(dayIndex - mainIndex + strongest, n)] }
    }
}
