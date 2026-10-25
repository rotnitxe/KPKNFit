package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.definitions.NativeProgressionContracts
import com.example.kpkn.data.protocols.definitions.NativeProgressionOutcome
import com.example.kpkn.domain.performance.StrengthModel
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToLong

/**
 * Ayudas de T4 para probar [LoadTargetSelector] sin Android: material discreto (independiente del
 * código de producción, para no dar por bueno lo que se quiere comprobar), un levantador con curva
 * de fuerza conocida y la regla de doble progresión del perfil (mismo contrato
 * [NativeProgressionContracts] que usa el runtime) con dos políticas: la heredada (siguiente escalón,
 * mismas repeticiones) y la nueva (par repeticiones-carga).
 */
internal object LoadTargetSimulationSupport {

    // ─── Material discreto ───────────────────────────────────────────────────

    sealed class Stock {
        abstract fun up(fromKg: Double): Double?
        abstract fun down(fromKg: Double): Double?
        abstract fun isMountable(kg: Double): Boolean

        /** Cargas montables a partir de [fromKg] en una dirección (hasta [steps] escalones). */
        fun ladder(fromKg: Double, increasing: Boolean, steps: Int): List<Double> {
            val rungs = mutableListOf<Double>()
            var from = fromKg
            while (rungs.size < steps) {
                val next = (if (increasing) up(from) else down(from)) ?: break
                rungs += next
                from = next
            }
            return rungs
        }
    }

    /** Pares de mancuernas completos, en kg por mancuerna. */
    class Dumbbells(weights: List<Double>) : Stock() {
        private val sorted = weights.sorted()
        override fun up(fromKg: Double) = sorted.firstOrNull { it > fromKg + 1e-6 }
        override fun down(fromKg: Double) = sorted.lastOrNull { it < fromKg - 1e-6 }
        override fun isMountable(kg: Double) = sorted.any { abs(it - kg) <= 1e-6 }
    }

    /** Máquina con carga base, incremento y rango. */
    class Machine(private val minKg: Double, private val maxKg: Double, private val incrementKg: Double) : Stock() {
        override fun up(fromKg: Double): Double? {
            val next = (floor((fromKg - minKg) / incrementKg + 1e-6) + 1) * incrementKg + minKg
            return next.takeIf { it <= maxKg + 1e-6 }
        }

        override fun down(fromKg: Double): Double? {
            val steps = kotlin.math.ceil((fromKg - minKg) / incrementKg - 1e-6) - 1
            return (steps * incrementKg + minKg).takeIf { it >= minKg - 1e-6 }
        }

        override fun isMountable(kg: Double): Boolean {
            val steps = (kg - minKg) / incrementKg
            return kg >= minKg - 1e-6 && kg <= maxKg + 1e-6 && abs(steps - steps.roundToLong()) <= 1e-6
        }
    }

    /** Barra con discos por lado (cantidad por lado); solo cargas formables y nunca bajo la barra vacía. */
    class Barbell(private val barKg: Double, plates: List<Pair<Double, Int>>) : Stock() {
        private val formable: List<Double>

        init {
            var sums = setOf(0L)
            plates.forEach { (weightKg, count) ->
                val milli = (weightKg * 1000).roundToLong()
                var next = sums
                var current = sums
                repeat(count) {
                    current = current.map { it + milli }.toSet()
                    next = next + current
                }
                sums = next
            }
            formable = sums.sorted().map { barKg + 2.0 * it / 1000.0 }
        }

        override fun up(fromKg: Double) = formable.firstOrNull { it > fromKg + 1e-6 }
        override fun down(fromKg: Double) = formable.lastOrNull { it < fromKg - 1e-6 }
        override fun isMountable(kg: Double) = formable.any { abs(it - kg) <= 1e-6 }
    }

    // ─── Levantador ──────────────────────────────────────────────────────────

    data class Scenario(
        val name: String,
        val stock: Stock,
        val repMin: Int,
        val repMax: Int,
        val targetRir: Int,
        val startLoadKg: Double,
        val startE1rmKg: Double,
        /** Ganancia semanal de fuerza real (fracción del e1RM inicial). */
        val weeklyGain: Double,
        val noise: Noise = Noise.DISCIPLINED,
    )

    /**
     * Ruido del levantador (desviaciones típicas): [day] sobre el e1RM de cada sesión (fracción),
     * [feel] sobre las repeticiones que decide hacer (a ojo, en repeticiones) y [report] sobre la
     * reserva que declara (en repeticiones). Los tres sorteos se hacen siempre, así que la regla
     * heredada y la nueva ven el mismo azar.
     */
    data class Noise(val day: Double, val feel: Double, val report: Double) {
        companion object {
            /** Sin azar: para probar la estabilidad con la fuerza constante. */
            val NONE = Noise(0.0, 0.0, 0.0)

            /** Variabilidad diaria del 1 % y un levantador que sabe parar a tiempo y declara su reserva real. */
            val DISCIPLINED = Noise(0.01, 0.0, 0.0)

            /** Además, se equivoca a ojo en ±0,35 repeticiones y declara la reserva con ±0,25 de error. */
            val SLOPPY = Noise(0.01, 0.35, 0.25)
        }
    }

    /** Serie hecha: repeticiones y reserva declarada (entera). */
    data class DoneSet(val reps: Int, val rir: Int)

    data class Session(val loadKg: Double, val sets: List<DoneSet>, val week: Int)

    /** La fatiga resta fuerza (no repeticiones fijas): 1,2 % del e1RM por serie ya hecha. */
    private const val SET_FATIGUE = 0.012
    const val SETS_PER_SESSION = 3
    const val SESSIONS_PER_WEEK = 2

    private fun gauss(random: Random) = random.nextGaussian()

    /**
     * El levantador hace las repeticiones que le dejan la reserva objetivo (redondeo al entero más
     * cercano), con tope en el rango del plan (doble progresión), y declara la reserva que le sobró
     * en enteros (también redondeada).
     */
    fun perform(scenario: Scenario, loadKg: Double, sessionIndex: Int, random: Random): Session {
        val week = sessionIndex / SESSIONS_PER_WEEK
        val trueE1rm = scenario.startE1rmKg *
            (1.0 + scenario.weeklyGain * (sessionIndex.toDouble() / SESSIONS_PER_WEEK)) *
            (1.0 + scenario.noise.day * gauss(random))
        val sets = (0 until SETS_PER_SESSION).map { index ->
            val capacity = StrengthModel.repsToFailureAt(loadKg, trueE1rm * (1.0 - SET_FATIGUE * index))
            val reps = round(capacity - scenario.targetRir + scenario.noise.feel * gauss(random)).toInt()
                .coerceIn(1, scenario.repMax)
            val rir = round(capacity - reps + scenario.noise.report * gauss(random)).toInt().coerceAtLeast(0)
            DoneSet(reps, rir)
        }
        return Session(loadKg, sets, week)
    }

    /** e1RM que muestra una sesión (primera serie con su reserva declarada). */
    fun shownE1rm(session: Session): Double {
        val first = session.sets.first()
        return StrengthModel.estimate1RM(session.loadKg, first.reps, rir = first.rir)
    }

    // ─── Regla de progresión + políticas ─────────────────────────────────────

    enum class Policy { LEGACY, PAIRED }

    data class Proposal(val sessionIndex: Int, val fromKg: Double, val toKg: Double, val reps: Int?, val increase: Boolean)

    data class RunResult(
        val sessions: List<Session>,
        val proposals: List<Proposal>,
        val keepNotices: Int,
        /** Propuestas cuyo par (repeticiones) cae en el rango del plan; vacío en la política heredada. */
        val repsInRange: List<Boolean>,
        val everyLoadMountable: Boolean,
        val oscillations: Int,
        val meanRirError: Double,
        val stagnantWeeks: Int,
    )

    /** Contrato de §12.4 con 2 exposiciones, el mismo que usan los perfiles nativos de carga. */
    private val contract = NativeProgressionContracts(NativeProgressionStrategy.REP_RANGE_THEN_LOAD, 2)

    fun run(scenario: Scenario, policy: Policy, seed: Long, weeks: Int = 12): RunResult {
        val random = Random(seed)
        val sessions = mutableListOf<Session>()
        val proposals = mutableListOf<Proposal>()
        val repsInRange = mutableListOf<Boolean>()
        var keepNotices = 0
        var mountable = scenario.stock.isMountable(scenario.startLoadKg)
        var load = scenario.startLoadKg
        var window = mutableListOf<Session>()

        repeat(weeks * SESSIONS_PER_WEEK) { index ->
            val session = perform(scenario, load, index, random)
            sessions += session
            window += session
            if (window.size < 2) return@repeat
            val recent = window.takeLast(2)
            val atTop = recent.all { s -> s.sets.all { it.reps >= scenario.repMax && it.rir >= scenario.targetRir } }
            val rirAtTarget = recent.all { s -> s.sets.all { it.rir >= scenario.targetRir } }
            val outcome = contract.exposureOutcome(recent.size, atTop, rirAtTarget)
            val below = recent.all { s -> s.sets.any { it.reps < scenario.repMin || it.rir < scenario.targetRir } }
            val increase = outcome == NativeProgressionOutcome.PROPOSE_INCREMENT
            val reduce = outcome == NativeProgressionOutcome.KEEP_OR_REGRESS && below
            if (!increase && !reduce) return@repeat

            // La regla ya decidió; aquí solo se elige QUÉ se propone. Las exposiciones se consumen.
            window = mutableListOf()
            val step = if (increase) scenario.stock.up(load) else scenario.stock.down(load)
            if (step == null) return@repeat // tope del material: aviso, sin cambio (igual en ambas políticas)

            val chosen: Double
            var reps: Int? = null
            if (policy == Policy.LEGACY) {
                chosen = step
            } else {
                val last = recent.last()
                val target = LoadTargetSelector.select(
                    sets = last.sets.map { LoadTargetSelector.SetEvidence(load, it.reps, it.rir) },
                    currentLoadKg = load,
                    repMin = scenario.repMin,
                    repMax = scenario.repMax,
                    targetRir = scenario.targetRir,
                    direction = if (increase) LoadTargetSelector.Direction.INCREASE else LoadTargetSelector.Direction.REDUCE,
                    availableLoads = scenario.stock.ladder(load, increase, 6),
                )
                if (target == null) {
                    chosen = step
                } else {
                    chosen = target.kg
                    reps = target.reps
                    repsInRange += target.reps in scenario.repMin..scenario.repMax
                    if (target.change == LoadTargetSelector.Change.KEEP) keepNotices += 1
                }
            }
            if (abs(chosen - load) > 1e-9) {
                mountable = mountable && scenario.stock.isMountable(chosen)
                proposals += Proposal(index, load, chosen, reps, chosen > load)
                load = chosen
            }
        }
        return RunResult(
            sessions = sessions,
            proposals = proposals,
            keepNotices = keepNotices,
            repsInRange = repsInRange,
            everyLoadMountable = mountable,
            oscillations = oscillationsOf(proposals),
            meanRirError = sessions.flatMap { it.sets }.map { abs(it.rir - scenario.targetRir).toDouble() }.average(),
            stagnantWeeks = stagnantWeeksOf(sessions, weeks),
        )
    }

    /** Un cambio de dirección entre dos cambios de carga seguidos (subir y luego bajar, o al revés). */
    fun oscillationsOf(proposals: List<Proposal>): Int =
        proposals.zipWithNext().count { (a, b) -> a.increase != b.increase }

    /**
     * Semanas de estancamiento: la fuerza que muestra el levantador (e1RM de su primera serie con la
     * reserva declarada, la misma señal de progreso que usa la app) se promedia por semana y por
     * pares de semanas seguidas; desde la 3.ª, una semana estancada es la que no supera el mejor
     * promedio anterior. El promedio evita contar como progreso los récords que solo son ruido.
     */
    fun stagnantWeeksOf(sessions: List<Session>, weeks: Int): Int {
        val weekly = (0 until weeks).map { week ->
            sessions.filter { it.week == week }.map { shownE1rm(it) }.average()
        }
        var best = 0.0
        var stagnant = 0
        (1 until weeks).forEach { week ->
            val pair = (weekly[week] + weekly[week - 1]) / 2.0
            if (week >= 2 && pair <= best) stagnant += 1
            best = maxOf(best, pair)
        }
        return stagnant
    }
}
