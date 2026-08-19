package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioIntervalPattern
import com.example.kpkn.data.models.CardioIntervalPrograms
import com.example.kpkn.data.models.CardioType
import java.util.UUID
import kotlin.math.roundToInt

/** Pure treadmill-style pattern scaler. */
object CardioIntervalProgramBuilder {
    fun build(
        pattern: CardioIntervalPattern,
        totalSeconds: Int,
        type: CardioType,
        baseLevel: Int,
    ): List<CardioIntervalBlock> {
        val units = CardioIntervalPrograms.spec(pattern).units
            .ifEmpty { CardioIntervalPrograms.spec(CardioIntervalPattern.CUSTOM).units }
        // A pattern cannot satisfy the 15 s safety floor when the requested
        // duration is shorter than its number of units.  Raise only that
        // pathological minimum instead of returning blocks longer than the
        // user's selected total.
        val total = totalSeconds.coerceAtLeast(units.size * 15 + 30)
        val (warmup, cooldown) = reservePair(total, units.size)
        val intervalBudget = (total - warmup - cooldown).coerceAtLeast(units.size * 15)
        val totalWeight = units.sumOf { it.durationWeight.coerceAtLeast(1) }.coerceAtLeast(1)
        val durations = scaledDurations(intervalBudget, units.map { it.durationWeight.coerceAtLeast(1) }, totalWeight)
        val blocks = buildList {
            add(block(CardioBlockType.WARMUP, warmup, 3, type))
            units.forEachIndexed { index, unit ->
                val level = (baseLevel + unit.intensityLevel - 6).coerceIn(1, 10)
                add(block(unit.type, durations[index], level, type))
            }
            add(block(CardioBlockType.COOLDOWN, cooldown, 2, type))
        }
        return blocks.filter { it.durationSeconds > 0 }
    }

    fun buildDetails(
        pattern: CardioIntervalPattern,
        totalSeconds: Int,
        type: CardioType,
        baseLevel: Int,
        base: CardioDetails? = null,
    ): CardioDetails {
        val blocks = build(pattern, totalSeconds, type, baseLevel)
        return (base ?: CardioDetails(type = type)).copy(
            type = type,
            targetDurationSeconds = blocks.sumOf { it.durationSeconds },
            intervalBlocks = blocks,
            intervalRounds = 1,
            hiit = null,
        )
    }

    private fun reservePair(total: Int, unitCount: Int): Pair<Int, Int> {
        val requested = (total * 0.10).roundToInt().coerceIn(120, 300)
        val maxEach = ((total - unitCount * 15) / 2).coerceAtLeast(15)
        val each = requested.coerceAtMost(maxEach)
        return each to each
    }

    private fun scaledDurations(budget: Int, weights: List<Int>, totalWeight: Int): List<Int> {
        if (weights.isEmpty()) return emptyList()
        val raw = weights.map { ((budget.toDouble() * it) / totalWeight).roundToInt().coerceAtLeast(15) }
        val rounded = raw.map { ((it / 5) * 5).coerceAtLeast(15) }.toMutableList()
        var delta = budget - rounded.sum()
        var cursor = rounded.lastIndex
        while (delta != 0 && rounded.isNotEmpty()) {
            if (delta > 0) {
                val add = minOf(5, delta)
                rounded[cursor] += add
                delta -= add
            } else {
                val removable = (rounded[cursor] - 15).coerceAtLeast(0)
                val subtract = minOf(5, -delta, removable)
                if (subtract == 0) {
                    cursor = (cursor - 1).takeIf { it >= 0 } ?: rounded.lastIndex
                    if (cursor == rounded.lastIndex && rounded.all { it == 15 }) break
                } else {
                    rounded[cursor] -= subtract
                    delta += subtract
                }
            }
            cursor = (cursor - 1).takeIf { it >= 0 } ?: rounded.lastIndex
        }
        return rounded
    }

    private fun block(type: CardioBlockType, duration: Int, level: Int, modality: CardioType): CardioIntervalBlock {
        val catalog = CardioCatalog.findByType(modality)
        val supportsSpeed = catalog?.supportsSpeed == true
        val speed = if (supportsSpeed) levelToSpeed(modality, level) else null
        val watts = if (!supportsSpeed && catalog?.supportsWatts == true) levelToWatts(level) else null
        return CardioIntervalBlock(
            id = UUID.randomUUID().toString(),
            type = type,
            durationSeconds = duration.coerceAtLeast(15),
            speedKmh = speed,
            watts = watts,
            intensityLevel = if (supportsSpeed) null else level,
        )
    }

    fun levelToSpeed(type: CardioType, level: Int): Double? = when (type) {
        CardioType.TREADMILL, CardioType.CURVED_TREADMILL, CardioType.RUN_OUTDOOR ->
            (4.5 + level * 0.85).coerceAtMost(19.0)
        CardioType.WALK -> (3.0 + level * 0.35).coerceAtMost(8.0)
        CardioType.BIKE_OUTDOOR -> (10.0 + level * 1.5).coerceAtMost(40.0)
        else -> null
    }

    fun levelToWatts(level: Int): Int = (40 + level.coerceIn(1, 10) * 20).coerceAtMost(260)
}
