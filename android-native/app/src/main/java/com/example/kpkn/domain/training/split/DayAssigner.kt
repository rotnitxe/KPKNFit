package com.example.kpkn.domain.training.split

import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.domain.training.PatternFamily
import kotlin.math.abs

/** Un día de entreno del reparto con el día real de la semana en el que cae (1 = lunes … 7 = domingo). */
internal data class DayInfo(
    val index: Int,
    val weekday: Int,
    /** Etiqueta del reparto tal cual («Torso», «Pierna», «Pecho/Espalda»). */
    val label: String,
    /** Título corto de la sesión («Torso A»): la etiqueta, con letra si se repite. */
    val title: String,
    val focus: DayFocus,
)

/**
 * Resultado de repartir las unidades entre los días: el día de cada unidad (índice en [days]) y la afinidad de todas
 * las unidades con todos los días (la usan el informe y las notas).
 */
internal class Assignment(
    val dayOfUnit: IntArray,
    val affinity: Array<DoubleArray>,
    /** Unidades de fuerza que quedaron en un día donde encajan poco (< 0,4) porque era la única forma de llenar el día. */
    val misfits: List<Int>,
)

/**
 * Reparte las unidades de una semana entre los días de un reparto. Determinista: sin azar y con desempates por índice.
 *
 * Criterios (los del brief, por orden de peso en la función de coste):
 * 1. **Foco**: cada unidad solo puede ir a los días donde encaja casi tan bien como en el mejor ([ELIGIBLE_BAND]); una
 *    unidad sin día natural (cardio, core, un ejercicio sin clasificar) puede ir a cualquiera.
 * 2. **Mínimo de ejercicios**: ningún día vacío ni con menos de 3 ejercicios si hay de dónde sacar; para llenarlo se
 *    relaja el foco hasta [RELAXED_AFFINITY].
 * 3. **Minutos parejos**: varianza relativa de los segundos por día (con el estimador común de sesiones).
 * 4. **Frecuencia y mezcla**: dos ejercicios del mismo músculo se reparten entre días en vez de amontonarse, y un día de
 *    torso lleva de empuje y de tirón.
 * 5. **Sin consecutivos pesados**: dos compuestos pesados del mismo patrón no caen en días seguidos si hay otro hueco.
 * 6. **Reglas duras** ([W_RULE] por violación: solo se incumplen si no hay otro día donde ponerlas). Dos compuestos
 *    pesados del mismo patrón no van juntos el mismo día (los dos tirones cuentan como uno); máximo 2 compuestos de una
 *    familia y 3 ejercicios de un mismo músculo primario por día (H2 y H3 de `SessionCompositionPolicy`); y un día de
 *    cuerpo completo lleva un compuesto de pierna, uno de empuje y uno de tirón, si hay unidades para todos los días.
 * 7. **Fatiga y solape**: la fatiga del día (`efc + cnc` por serie, con el factor axial) se equilibra como los minutos; un
 *    mismo músculo primario en días seguidos cuesta según su solape; dos días de cuerpo completo seguidos alternan
 *    sentadilla y bisagra como compuesto de pierna principal cuando se puede.
 *
 * Primero un reparto voraz (las unidades más grandes primero, a su mejor día) y después una búsqueda local que aplica,
 * una a una, la mejora más grande entre mover una unidad o intercambiar dos, hasta que ninguna mejora el coste.
 */
internal object DayAssigner {

    private const val W_FIT = 12.0
    private const val W_BAL = 30.0
    private const val W_CROWD = 1.5

    /** El mismo ejercicio dos veces en un día (un plan de cuerpo completo repite su sentadilla en cada sesión). */
    private const val W_SAME = 6.0
    private const val W_ADJ = 3.0
    /**
     * Cada ejercicio que falta para el mínimo de un día cuesta el doble que una regla dura: el mínimo se cumple antes que
     * un choque de pesados o un tope de H2/H3 (si hay de dónde sacar).
     */
    private const val W_MIN = 120.0
    private const val W_EMPTY = 20.0
    private const val W_MIX = 4.0

    /** Coste de cada violación de una regla dura (dos pesados del mismo patrón, H2, H3, compuestos de cuerpo completo). */
    private const val W_RULE = 60.0

    /** Fatiga del día: varianza relativa, como los minutos. */

    /** Un mismo músculo primario en días seguidos, por solape. */
    private const val W_NEXT = 2.0

    /** Dos días de cuerpo completo seguidos con el mismo compuesto de pierna principal. */
    private const val W_ALTERNATE = 8.0

    /** H2 y H3 de `SessionCompositionPolicy`: compuestos de una familia y ejercicios de un músculo primario por día. */
    private const val MAX_COMPOUNDS_PER_FAMILY = 2
    private const val MAX_PER_PRIMARY_MUSCLE = 3

    /** Un día es elegible para una unidad si su afinidad llega a la del mejor menos esta banda. */
    const val ELIGIBLE_BAND = 0.12

    /** Por debajo de esta afinidad máxima la unidad no tiene día natural. */
    private const val NO_NATURAL_DAY = 0.35

    /** Afinidad mínima con la que se acepta una unidad en un día corto para llenarlo. */
    const val RELAXED_AFFINITY = 0.4

    /** Ejercicios mínimos por día cuando hay de dónde sacar. */
    const val MIN_EXERCISES = 3

    private const val MAX_PASSES = 200

    /** Con más unidades que esto no se prueban los intercambios de a dos (solo los movimientos): el coste crece con el cuadrado. */
    private const val MAX_SWAP_UNITS = 60
    private const val EPS = 1e-9

    /** Sin compuesto de pierna en la unidad (ver [legKeyOf]). */
    private const val NO_LEG = Int.MAX_VALUE

    private val PUSH_PATTERNS = setOf(PatternFamily.HORIZONTAL_PUSH, PatternFamily.VERTICAL_PUSH)
    private val PULL_PATTERNS = setOf(PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL)

    fun assign(units: List<MovableUnit>, days: List<DayInfo>): Assignment {
        val problem = Problem(units, days)
        return problem.solve()
    }

    /**
     * Clave de un patrón pesado: sentadilla, bisagra, empuje horizontal, empuje vertical y tirón (horizontal o vertical,
     * como uno solo). -1 si el patrón no cuenta como pesado.
     */
    private fun heavyKeyOf(pattern: PatternFamily): Int = when (pattern) {
        PatternFamily.SQUAT -> 0
        PatternFamily.HINGE -> 1
        PatternFamily.HORIZONTAL_PUSH -> 2
        PatternFamily.VERTICAL_PUSH -> 3
        PatternFamily.HORIZONTAL_PULL, PatternFamily.VERTICAL_PULL -> 4
        else -> -1
    }

    /**
     * Clave del compuesto de pierna de una unidad: tier y patrón (en el mismo tier, la sentadilla antes que la bisagra).
     * [NO_LEG] si no tiene ningún compuesto de pierna.
     */
    private fun legKeyOf(unit: MovableUnit): Int {
        var best = NO_LEG
        unit.compoundPatterns.forEach { pattern ->
            val bit = when (pattern) {
                PatternFamily.SQUAT -> 0
                PatternFamily.HINGE -> 1
                else -> return@forEach
            }
            best = minOf(best, maxOf(unit.tier, 0) * 2 + bit)
        }
        return best
    }

    /** Un día de cuerpo completo acoge los tres grupos sin énfasis («Cuerpo Completo A», «Full Body»), no «Torso/Full Body». */
    private fun acceptsEveryGroup(day: DayInfo): Boolean =
        day.focus.acceptedGroups.containsAll(setOf(SplitGroup.PUSH, SplitGroup.PULL, SplitGroup.LEGS)) &&
            day.focus.weights.values.all { it >= 1.0 - EPS }

    private class Problem(val units: List<MovableUnit>, val days: List<DayInfo>) {
        private val unitCount = units.size
        private val dayCount = days.size

        val affinity: Array<DoubleArray> = Array(unitCount) { i ->
            DoubleArray(dayCount) { j -> affinityOf(units[i], days[j]) }
        }
        private val eligible: Array<BooleanArray> = Array(unitCount) { i -> eligibilityOf(affinity[i]) }

        /** Solape entre dos unidades de fuerza: 1 si comparten un músculo primario, 0,3 si son compuestos del mismo grupo. */
        private val overlap: Array<DoubleArray> = Array(unitCount) { i ->
            DoubleArray(unitCount) { k -> if (i == k) 0.0 else overlapOf(units[i], units[k]) }
        }

        /** Pares de unidades que son el mismo ejercicio: mejor en días distintos. */
        private val same: Array<BooleanArray> = Array(unitCount) { i ->
            BooleanArray(unitCount) { k -> i != k && sameExercise(units[i], units[k]) }
        }

        /** Pares de compuestos pesados del mismo patrón o músculo: no deben caer en días consecutivos. */
        private val related: Array<BooleanArray> = Array(unitCount) { i ->
            BooleanArray(unitCount) { k -> i != k && relatedHeavy(units[i], units[k]) }
        }
        private val adjacentDays: Array<BooleanArray> = Array(dayCount) { a ->
            BooleanArray(dayCount) { b ->
                val diff = abs(days[a].weekday - days[b].weekday)
                a != b && (diff == 1 || diff == 6)
            }
        }

        /** Máscara de patrones pesados de cada unidad (un bit por [heavyKeyOf]): dos unidades con un bit común chocan el mismo día. */
        private val heavyMask: IntArray = IntArray(unitCount) { i ->
            units[i].heavyPatterns.fold(0) { mask, pattern ->
                val key = heavyKeyOf(pattern)
                if (key >= 0) mask or (1 shl key) else mask
            }
        }

        /** Familias de los compuestos de cada unidad (un índice de [PatternFamily] por ejercicio). */
        private val compoundFamilies: Array<IntArray> = Array(unitCount) { i ->
            units[i].compoundPatterns.map { it.ordinal }.toIntArray()
        }

        /** Músculo primario dominante de cada ejercicio de fuerza de la unidad (índice de [KpknMuscleGroup]). */
        private val dominantMuscles: Array<IntArray> = Array(unitCount) { i ->
            units[i].dominantMuscles.map { it.ordinal }.toIntArray()
        }

        /** Clave del compuesto de pierna de cada unidad ([legKeyOf]), o [NO_LEG]. */
        private val legKey: IntArray = IntArray(unitCount) { i -> legKeyOf(units[i]) }
        private val pushCompound: BooleanArray = BooleanArray(unitCount) { i ->
            units[i].compoundPatterns.any { it in PUSH_PATTERNS }
        }
        private val pullCompound: BooleanArray = BooleanArray(unitCount) { i ->
            units[i].compoundPatterns.any { it in PULL_PATTERNS }
        }

        /** Días de cuerpo completo: deben llevar un compuesto de cada tipo si hay unidades para todos ellos. */
        private val fullBodyDay: BooleanArray = BooleanArray(dayCount) { j -> acceptsEveryGroup(days[j]) }
        private val fullBodyCount: Int = fullBodyDay.count { it }
        private val enforceLeg: Boolean =
            fullBodyCount > 0 && (0 until unitCount).count { legKey[it] != NO_LEG } >= fullBodyCount
        private val enforcePush: Boolean =
            fullBodyCount > 0 && (0 until unitCount).count { pushCompound[it] } >= fullBodyCount
        private val enforcePull: Boolean =
            fullBodyCount > 0 && (0 until unitCount).count { pullCompound[it] } >= fullBodyCount

        private val totalSeconds: Double = units.sumOf { it.seconds.toDouble() }
        private val meanSeconds: Double = if (dayCount > 0) totalSeconds / dayCount else 0.0
        private val strengthTotal: Int = units.sumOf { it.strengthExerciseCount }
        private val minExercises: Int = if (dayCount == 0) 0 else minOf(MIN_EXERCISES, strengthTotal / dayCount)

        /** Grupos que cada día debe llevar (si hay unidades de sobra para todos los días que los acogen). */
        private val mixRequired: Array<Set<SplitGroup>> = Array(dayCount) { j ->
            val accepted = days[j].focus.acceptedGroups
            if (accepted.size < 2) {
                emptySet()
            } else {
                accepted.filterTo(linkedSetOf<SplitGroup>()) { group ->
                    val supply = units.count { it.kind == UnitKind.STRENGTH && it.group == group }
                    val demand = days.count { group in it.focus.acceptedGroups }
                    demand > 0 && supply >= demand
                }
            }
        }

        fun solve(): Assignment {
            if (unitCount == 0 || dayCount == 0) return Assignment(IntArray(0), affinity, emptyList())
            val assignment = IntArray(unitCount) { -1 }
            greedy(assignment)
            localSearch(assignment)
            fillEmptyDays(assignment)
            localSearch(assignment)
            val misfits = units.indices.filter { i ->
                units[i].kind == UnitKind.STRENGTH && units[i].isClassified && affinity[i][assignment[i]] < RELAXED_AFFINITY
            }
            return Assignment(assignment, affinity, misfits)
        }

        // ─── Reparto inicial ────────────────────────────────────────────────────────────────────

        private fun greedy(assignment: IntArray) {
            val order = units.indices.sortedWith(
                compareBy<Int> { units[it].tier }
                    .thenByDescending { units[it].seconds }
                    .thenBy { units[it].originSession }
                    .thenBy { units[it].originPosition }
                    .thenBy { it },
            )
            order.forEach { i ->
                var bestDay = -1
                var bestCost = Double.MAX_VALUE
                for (j in 0 until dayCount) {
                    if (!eligible[i][j]) continue
                    assignment[i] = j
                    val cost = cost(assignment, complete = false)
                    if (cost < bestCost - EPS) {
                        bestCost = cost
                        bestDay = j
                    }
                }
                assignment[i] = if (bestDay >= 0) bestDay else 0
            }
        }

        // ─── Búsqueda local ─────────────────────────────────────────────────────────────────────

        private fun localSearch(assignment: IntArray) {
            var current = cost(assignment, complete = true)
            repeat(MAX_PASSES) {
                val counts = strengthCounts(assignment)
                var bestDelta = -EPS
                var moveUnit = -1
                var moveDay = -1
                var swapA = -1
                var swapB = -1

                for (i in 0 until unitCount) {
                    val from = assignment[i]
                    for (to in 0 until dayCount) {
                        if (to == from) continue
                        val allowed = eligible[i][to] ||
                            (counts[to] < minExercises && affinity[i][to] >= RELAXED_AFFINITY)
                        if (!allowed) continue
                        assignment[i] = to
                        val delta = cost(assignment, complete = true) - current
                        assignment[i] = from
                        if (delta < bestDelta) {
                            bestDelta = delta
                            moveUnit = i
                            moveDay = to
                            swapA = -1
                            swapB = -1
                        }
                    }
                }
                for (i in 0 until (if (unitCount <= MAX_SWAP_UNITS) unitCount else 0)) {
                    for (k in i + 1 until unitCount) {
                        val dayI = assignment[i]
                        val dayK = assignment[k]
                        if (dayI == dayK || !eligible[i][dayK] || !eligible[k][dayI]) continue
                        assignment[i] = dayK
                        assignment[k] = dayI
                        val delta = cost(assignment, complete = true) - current
                        assignment[i] = dayI
                        assignment[k] = dayK
                        if (delta < bestDelta) {
                            bestDelta = delta
                            swapA = i
                            swapB = k
                            moveUnit = -1
                            moveDay = -1
                        }
                    }
                }

                when {
                    moveUnit >= 0 -> assignment[moveUnit] = moveDay
                    swapA >= 0 -> {
                        val dayA = assignment[swapA]
                        assignment[swapA] = assignment[swapB]
                        assignment[swapB] = dayA
                    }
                    else -> return
                }
                current += bestDelta
            }
        }

        /** Un día sin ejercicios de fuerza recibe, aunque encaje poco, la unidad que mejor le va desde un día con de sobra. */
        private fun fillEmptyDays(assignment: IntArray) {
            if (strengthTotal < dayCount) return
            for (day in 0 until dayCount) {
                val counts = strengthCounts(assignment)
                if (counts[day] > 0) continue
                val candidate = units.indices
                    .filter { i ->
                        val unit = units[i]
                        unit.kind == UnitKind.STRENGTH && counts[assignment[i]] - unit.strengthExerciseCount >= 1
                    }
                    .sortedWith(
                        compareByDescending<Int> { affinity[it][day] }
                            .thenByDescending { units[it].tier }
                            .thenBy { units[it].seconds }
                            .thenBy { it },
                    )
                    .firstOrNull()
                if (candidate != null) assignment[candidate] = day
            }
        }

        // ─── Función de coste ───────────────────────────────────────────────────────────────────

        private fun strengthCounts(assignment: IntArray): IntArray {
            val counts = IntArray(dayCount)
            for (i in 0 until unitCount) {
                if (assignment[i] >= 0) counts[assignment[i]] += units[i].strengthExerciseCount
            }
            return counts
        }

        /** Coste de un reparto (las unidades con día -1 aún no cuentan). [complete] suma los mínimos, la mezcla y el suelo de cuerpo completo. */
        private fun cost(assignment: IntArray, complete: Boolean): Double {
            val seconds = DoubleArray(dayCount)
            val counts = IntArray(dayCount)
            val groupCounts = Array(dayCount) { IntArray(SplitGroup.entries.size) }
            val familyCounts = Array(dayCount) { IntArray(PatternFamily.entries.size) }
            val muscleCounts = Array(dayCount) { IntArray(KpknMuscleGroup.entries.size) }
            val legs = IntArray(dayCount)
            val legLead = IntArray(dayCount) { NO_LEG }
            val pushes = IntArray(dayCount)
            val pulls = IntArray(dayCount)
            var fit = 0.0
            for (i in 0 until unitCount) {
                val day = assignment[i]
                if (day < 0) continue
                fit += 1.0 - affinity[i][day]
                seconds[day] += units[i].seconds.toDouble()
                counts[day] += units[i].strengthExerciseCount
                if (units[i].kind == UnitKind.STRENGTH) groupCounts[day][units[i].group.ordinal] += 1
                compoundFamilies[i].forEach { familyCounts[day][it]++ }
                dominantMuscles[i].forEach { muscleCounts[day][it]++ }
                if (legKey[i] != NO_LEG) {
                    legs[day]++
                    legLead[day] = minOf(legLead[day], legKey[i])
                }
                if (pushCompound[i]) pushes[day]++
                if (pullCompound[i]) pulls[day]++
            }
            var balance = 0.0
            if (meanSeconds > 0.0) {
                for (j in 0 until dayCount) {
                    val relative = (seconds[j] - meanSeconds) / meanSeconds
                    balance += relative * relative
                }
            }
            var crowd = 0.0
            var repeated = 0.0
            var adjacent = 0.0
            var nextDayOverlap = 0.0
            var heavyClash = 0.0
            for (i in 0 until unitCount) {
                val dayI = assignment[i]
                if (dayI < 0) continue
                for (k in i + 1 until unitCount) {
                    val dayK = assignment[k]
                    if (dayK < 0) continue
                    if (dayI == dayK) {
                        crowd += overlap[i][k]
                        if (same[i][k]) repeated += 1.0
                        if ((heavyMask[i] and heavyMask[k]) != 0) heavyClash += 1.0
                    } else if (adjacentDays[dayI][dayK]) {
                        nextDayOverlap += overlap[i][k]
                        if (related[i][k]) adjacent += 1.0
                    }
                }
            }
            var total = W_FIT * fit + W_BAL * balance + W_CROWD * crowd +
                W_SAME * repeated + W_ADJ * adjacent + W_NEXT * nextDayOverlap + W_RULE * heavyClash
            // H2 y H3 por día: cada compuesto de más de una familia y cada ejercicio de más de un músculo cuesta una violación.
            for (j in 0 until dayCount) {
                familyCounts[j].forEach { n ->
                    if (n > MAX_COMPOUNDS_PER_FAMILY) total += W_RULE * (n - MAX_COMPOUNDS_PER_FAMILY)
                }
                muscleCounts[j].forEach { n ->
                    if (n > MAX_PER_PRIMARY_MUSCLE) total += W_RULE * (n - MAX_PER_PRIMARY_MUSCLE)
                }
            }
            if (complete) {
                for (j in 0 until dayCount) {
                    if (counts[j] < minExercises) total += W_MIN * (minExercises - counts[j])
                    if (counts[j] == 0 && strengthTotal >= dayCount) total += W_EMPTY
                    mixRequired[j].forEach { group ->
                        if (groupCounts[j][group.ordinal] == 0) total += W_MIX
                    }
                    if (fullBodyDay[j]) {
                        if (enforceLeg && legs[j] == 0) total += W_RULE
                        if (enforcePush && pushes[j] == 0) total += W_RULE
                        if (enforcePull && pulls[j] == 0) total += W_RULE
                    }
                }
                // Dos días de cuerpo completo seguidos: el compuesto de pierna principal alterna sentadilla y bisagra.
                for (a in 0 until dayCount) {
                    for (b in a + 1 until dayCount) {
                        if (fullBodyDay[a] && fullBodyDay[b] && adjacentDays[a][b] &&
                            legLead[a] != NO_LEG && legLead[b] != NO_LEG && legLead[a] % 2 == legLead[b] % 2
                        ) {
                            total += W_ALTERNATE
                        }
                    }
                }
            }
            return total
        }

        // ─── Tablas por unidad ──────────────────────────────────────────────────────────────────

        private fun affinityOf(unit: MovableUnit, day: DayInfo): Double {
            val muscles = unit.muscles
            return if (unit.kind != UnitKind.STRENGTH || muscles == null) {
                SplitAffinity.NEUTRAL
            } else {
                SplitAffinity.of(muscles, day.focus, unit.chain)
            }
        }

        /**
         * Días donde la unidad puede ir: los que encajan casi tan bien como el mejor y los que su etiqueta abre a todo
         * («Torso/Full Body» admite un ejercicio de pierna con el encaje mínimo de ese día).
         */
        private fun eligibilityOf(row: DoubleArray): BooleanArray {
            val best = row.maxOrNull() ?: 0.0
            return if (best < NO_NATURAL_DAY) {
                BooleanArray(row.size) { true }
            } else {
                BooleanArray(row.size) { day ->
                    val floor = days[day].focus.floor
                    row[day] >= best - ELIGIBLE_BAND - EPS || (floor > 0.0 && row[day] >= floor - EPS)
                }
            }
        }

        private fun overlapOf(a: MovableUnit, b: MovableUnit): Double {
            if (a.kind != UnitKind.STRENGTH || b.kind != UnitKind.STRENGTH) return 0.0
            if (a.primaryAtoms.isEmpty() || b.primaryAtoms.isEmpty()) return 0.0
            if (a.primaryAtoms.any { it in b.primaryAtoms }) return 1.0
            return if (a.group == b.group && a.tier <= 1 && b.tier <= 1) 0.3 else 0.0
        }

        private fun sameExercise(a: MovableUnit, b: MovableUnit): Boolean =
            a.exerciseKeys.isNotEmpty() && a.exerciseKeys.any { it in b.exerciseKeys }

        private fun relatedHeavy(a: MovableUnit, b: MovableUnit): Boolean {
            if (!a.isHeavy || !b.isHeavy) return false
            if (a.pattern != null && a.pattern == b.pattern) return true
            return a.primaryAtoms.any { it in b.primaryAtoms }
        }
    }
}
