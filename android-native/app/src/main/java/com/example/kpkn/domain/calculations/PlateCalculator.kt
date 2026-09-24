package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.PlateStock
import kotlin.math.abs
import kotlin.math.roundToLong

data class PlateResult(
    val platesPerSide: List<Double>,
    val achievedWeight: Double,
    val targetWeight: Double,
    val isExact: Boolean,
)

/**
 * Distribución de discos por lado respetando cantidades reales del inventario.
 *
 * Contrato:
 * - Nunca emite una carga que exija más piezas de las disponibles.
 * - Si la carga objetivo no es exactamente alcanzable, se marca con
 *   `isExact = false` y `achievedWeight` refleja la carga alcanzable más alta
 *   sin superar el objetivo (nunca se sustituye en silencio ni se inventan kg).
 * - La simetría entre lados se mantiene: la solución es por lado.
 * - No asume incrementos universales de 0,5 kg: la granularidad real es la del
 *   inventario (discos disponibles).
 */
object PlateCalculator {
    private const val MIN_PLATE_WEIGHT = 0.25
    private const val WEIGHT_EPSILON = 0.01
    private const val MILLI_PER_KG = 1000.0

    /** Compatibilidad legacy: lista de pesos de disco sin cantidad → ilimitados. */
    fun calculatePlates(
        targetWeight: Double,
        barbellWeight: Double,
        availablePlates: List<Double>,
    ): PlateResult = calculatePlatesFromStock(
        targetWeight = targetWeight,
        barbellWeight = barbellWeight,
        plates = availablePlates.map { PlateStock(weightKg = it, countPerSide = null) },
    )

    /** Cálculo contra el inventario principal (barra + discos con cantidades). */
    fun calculatePlates(
        targetWeight: Double,
        inventory: EquipmentInventory,
        barbellWeightFallbackKg: Double = 20.0,
    ): PlateResult = calculatePlatesFromStock(
        targetWeight = targetWeight,
        barbellWeight = inventory.resolvedBarbellWeightKg(barbellWeightFallbackKg),
        plates = inventory.plates,
    )

    /** Núcleo por stock con cantidades finitas; nombre propio para no colisionar en JVM con la firma legacy. */
    fun calculatePlatesFromStock(
        targetWeight: Double,
        barbellWeight: Double,
        plates: List<PlateStock>,
    ): PlateResult {
        if (targetWeight <= barbellWeight) {
            return PlateResult(
                platesPerSide = emptyList(),
                achievedWeight = barbellWeight,
                targetWeight = targetWeight,
                isExact = abs(targetWeight - barbellWeight) < WEIGHT_EPSILON,
            )
        }

        val targetPerSideMilli = ((targetWeight - barbellWeight) / 2.0 * MILLI_PER_KG).roundToLong().coerceAtLeast(0L)
        val kinds = plateKinds(plates, targetPerSideMilli)
        val solved = solvePerSide(targetPerSideMilli, kinds)
        val chosenCounts = reconstructCounts(solved.bestPerSideMilli, solved.parent)

        val platesPerSide = chosenCounts.entries
            .sortedByDescending { it.key }
            .flatMap { (weightMilli, count) -> List(count) { weightMilli / MILLI_PER_KG } }
        val achievedWeight = barbellWeight + (solved.bestPerSideMilli / MILLI_PER_KG) * 2.0
        return PlateResult(
            platesPerSide = platesPerSide,
            achievedWeight = achievedWeight,
            targetWeight = targetWeight,
            isExact = abs(achievedWeight - targetWeight) < WEIGHT_EPSILON,
        )
    }

    private data class PlateKind(val weightMilli: Long, val maxCount: Int)

    private data class SideSolution(val bestPerSideMilli: Long, val parent: IntArray)

    /** Fusiona pesos repetidos; `countPerSide = null` (o entradas null) = ilimitado. */
    private fun plateKinds(plates: List<PlateStock>, targetPerSideMilli: Long): List<PlateKind> {
        return plates
            .filter { it.weightKg > 0.0 }
            .groupBy { (it.weightKg * MILLI_PER_KG).roundToLong() }
            .filterKeys { it > 0L }
            .map { (weightMilli, stocks) ->
                val unlimited = stocks.any { it.countPerSide == null }
                val maxCount = when {
                    unlimited -> Int.MAX_VALUE
                    else -> stocks.sumOf { it.countPerSide?.coerceAtLeast(0) ?: 0 }
                        .coerceAtMost((targetPerSideMilli / weightMilli).toInt().coerceAtLeast(0))
                }
                PlateKind(weightMilli = weightMilli, maxCount = maxCount)
            }
            .sortedByDescending { it.weightMilli }
    }

    /**
     * Mochila acotada por lado: mayor peso alcanzable ≤ objetivo respetando
     * cantidades reales. `parent[w]` es el peso anterior en la cadena de
     * reconstrucción (-2 inicio, -1 inalcanzable); cada paso consume un disco.
     */
    private fun solvePerSide(targetPerSideMilli: Long, kinds: List<PlateKind>): SideSolution {
        val capacity = targetPerSideMilli.toInt()
        val parent = IntArray(capacity + 1) { -1 }
        parent[0] = -2
        for (kind in kinds) {
            val unit = kind.weightMilli.toInt()
            if (unit <= 0) continue
            val maxCount = if (kind.maxCount == Int.MAX_VALUE) {
                capacity / unit
            } else {
                minOf(kind.maxCount, capacity / unit)
            }
            val used = IntArray(capacity + 1) { -1 }
            for (current in 0..capacity) {
                used[current] = when {
                    parent[current] != -1 -> 0
                    current >= unit && used[current - unit] in 0 until maxCount -> used[current - unit] + 1
                    else -> -1
                }
            }
            for (current in 0..capacity) {
                if (parent[current] == -1 && used[current] >= 0) parent[current] = current - unit
            }
        }
        var best = capacity
        while (best > 0 && parent[best] == -1) best--
        return SideSolution(bestPerSideMilli = best.toLong(), parent = parent)
    }

    /** Peso del disco (mili-kg) → cantidad por lado usada. */
    private fun reconstructCounts(bestPerSideMilli: Long, parent: IntArray): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        var current = bestPerSideMilli.toInt()
        while (current > 0) {
            val previous = parent[current]
            if (previous < 0) break
            val weightMilli = (current - previous).toLong()
            if (weightMilli <= 0L) break
            counts[weightMilli] = (counts[weightMilli] ?: 0) + 1
            current = previous
        }
        return counts
    }
}
