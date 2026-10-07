package com.example.kpkn.domain.training.split

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
    private const val W_MIN = 8.0
    private const val W_EMPTY = 20.0
    private const val W_MIX = 4.0

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

    fun assign(units: List<MovableUnit>, days: List<DayInfo>): Assignment {
        val problem = Problem(units, days)
        return problem.solve()
    }

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

        /** Coste de un reparto (las unidades con día -1 aún no cuentan). [complete] suma los mínimos y la mezcla de grupos. */
        private fun cost(assignment: IntArray, complete: Boolean): Double {
            val seconds = DoubleArray(dayCount)
            val counts = IntArray(dayCount)
            val groupCounts = Array(dayCount) { IntArray(SplitGroup.entries.size) }
            var fit = 0.0
            for (i in 0 until unitCount) {
                val day = assignment[i]
                if (day < 0) continue
                fit += 1.0 - affinity[i][day]
                seconds[day] += units[i].seconds.toDouble()
                counts[day] += units[i].strengthExerciseCount
                if (units[i].kind == UnitKind.STRENGTH) groupCounts[day][units[i].group.ordinal] += 1
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
            for (i in 0 until unitCount) {
                val dayI = assignment[i]
                if (dayI < 0) continue
                for (k in i + 1 until unitCount) {
                    val dayK = assignment[k]
                    if (dayK < 0) continue
                    if (dayI == dayK) {
                        crowd += overlap[i][k]
                        if (same[i][k]) repeated += 1.0
                    } else if (related[i][k] && adjacentDays[dayI][dayK]) {
                        adjacent += 1.0
                    }
                }
            }
            var total = W_FIT * fit + W_BAL * balance + W_CROWD * crowd + W_SAME * repeated + W_ADJ * adjacent
            if (complete) {
                for (j in 0 until dayCount) {
                    if (counts[j] < minExercises) total += W_MIN * (minExercises - counts[j])
                    if (counts[j] == 0 && strengthTotal >= dayCount) total += W_EMPTY
                    mixRequired[j].forEach { group ->
                        if (groupCounts[j][group.ordinal] == 0) total += W_MIX
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
