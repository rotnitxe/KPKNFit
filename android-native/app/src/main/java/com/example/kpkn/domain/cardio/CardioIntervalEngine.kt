package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import java.util.UUID

/**
 * Pure engine for cardio intervals / circuits.
 * Stateless: given elapsedSeconds it derives the current block.
 */
object CardioIntervalEngine {

    data class Progress(
        val currentBlock: CardioIntervalBlock?,
        val currentIndex: Int,
        val totalBlocks: Int,
        val elapsedInBlock: Int,
        val remainingInBlock: Int,
        val elapsedTotal: Int,
        val totalSeconds: Int,
        val nextBlock: CardioIntervalBlock?,
        val isComplete: Boolean,
    )

    fun expandedBlocks(details: CardioDetails): List<CardioIntervalBlock> {
        if (details.intervalBlocks.isEmpty()) return emptyList()
        val rounds = details.intervalRounds.coerceIn(1, 99)
        val valid = details.intervalBlocks.filter { it.durationSeconds > 0 }
        if (valid.isEmpty()) return emptyList()
        return buildList(valid.size * rounds) {
            repeat(rounds) { addAll(valid) }
        }
    }

    fun totalSeconds(details: CardioDetails): Int =
        if (details.hasIntervals()) details.totalIntervalSeconds()
        else (details.targetDurationSeconds ?: 0).coerceAtLeast(0)

    fun progressAt(details: CardioDetails, elapsedSeconds: Int): Progress? {
        val expanded = expandedBlocks(details)
        if (expanded.isEmpty()) return null
        val total = expanded.sumOf { it.durationSeconds }
        val elapsed = elapsedSeconds.coerceIn(0, total)
        if (elapsed >= total) {
            return Progress(
                currentBlock = null,
                currentIndex = expanded.size,
                totalBlocks = expanded.size,
                elapsedInBlock = 0,
                remainingInBlock = 0,
                elapsedTotal = elapsed,
                totalSeconds = total,
                nextBlock = null,
                isComplete = true,
            )
        }
        var acc = 0
        for ((idx, block) in expanded.withIndex()) {
            val nextAcc = acc + block.durationSeconds
            if (elapsed < nextAcc) {
                val inBlock = elapsed - acc
                return Progress(
                    currentBlock = block,
                    currentIndex = idx,
                    totalBlocks = expanded.size,
                    elapsedInBlock = inBlock,
                    remainingInBlock = block.durationSeconds - inBlock,
                    elapsedTotal = elapsed,
                    totalSeconds = total,
                    nextBlock = expanded.getOrNull(idx + 1),
                    isComplete = false,
                )
            }
            acc = nextAcc
        }
        return null
    }

    fun createBlock(
        type: CardioBlockType = CardioBlockType.WORK,
        durationSeconds: Int = 60,
        speedKmh: Double? = null,
        inclinePercent: Double? = null,
        rpm: Int? = null,
        watts: Int? = null,
        intensityLevel: Int? = null,
    ): CardioIntervalBlock = CardioIntervalBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        durationSeconds = durationSeconds.coerceAtLeast(5),
        speedKmh = speedKmh,
        inclinePercent = inclinePercent,
        rpm = rpm,
        watts = watts,
        intensityLevel = intensityLevel,
    )

    fun metForBlock(block: CardioIntervalBlock, type: CardioType, fallbackIntensityLevel: Int): Double {
        // Speed-based MET tables (compendium approximations)
        block.speedKmh?.takeIf { it > 0 }?.let { kmh ->
            return speedToMet(type, kmh)
        }
        block.watts?.takeIf { it > 0 }?.let { w ->
            return wattsToMet(type, w)
        }
        val level = block.intensityLevel ?: fallbackIntensityLevel
        return levelToMet(type, level)
    }

    private fun speedToMet(type: CardioType, kmh: Double): Double = when (type) {
        CardioType.CURVED_TREADMILL -> speedToMet(CardioType.TREADMILL, kmh) + 0.5
        CardioType.TREADMILL, CardioType.RUN_OUTDOOR, CardioType.WALK -> when {
            kmh < 5.0 -> 3.5
            kmh < 6.5 -> 5.0
            kmh < 8.0 -> 7.0
            kmh < 9.5 -> 8.3
            kmh < 10.5 -> 10.0
            kmh < 11.5 -> 11.5
            kmh < 12.8 -> 12.5
            kmh < 14.0 -> 13.5
            kmh < 15.5 -> 15.0
            else -> 16.0
        }
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR, CardioType.AIR_BIKE -> when {
            kmh < 15.0 -> 5.5
            kmh < 20.0 -> 7.0
            kmh < 25.0 -> 8.5
            kmh < 30.0 -> 10.5
            else -> 12.0
        }
        CardioType.ROW_MACHINE -> when {
            kmh < 8.0 -> 6.0
            kmh < 12.0 -> 8.0
            else -> 10.0
        }
        else -> levelToMet(type, 6)
    }

    private fun wattsToMet(type: CardioType, watts: Int): Double = when (type) {
        CardioType.AIR_BIKE -> when {
            watts < 80 -> 6.5
            watts < 120 -> 8.0
            watts < 160 -> 9.5
            watts < 200 -> 11.5
            watts < 250 -> 13.0
            else -> 15.0
        }
        CardioType.SKI_ERG -> when {
            watts < 100 -> 6.0
            watts < 150 -> 8.0
            watts < 200 -> 10.0
            else -> 12.0
        }
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> when {
            watts < 80 -> 5.5
            watts < 120 -> 7.0
            watts < 160 -> 8.5
            watts < 200 -> 10.5
            watts < 250 -> 12.0
            else -> 14.0
        }
        CardioType.ROW_MACHINE -> when {
            watts < 100 -> 6.0
            watts < 150 -> 8.0
            watts < 200 -> 10.0
            else -> 12.0
        }
        else -> 7.0
    }

    private fun levelToMet(type: CardioType, level: Int): Double {
        val idx = when {
            level <= 4 -> 0
            level <= 6 -> 1
            level <= 8 -> 2
            else -> 3
        }
        return when (type) {
            CardioType.TREADMILL -> doubleArrayOf(7.0, 10.0, 12.5, 14.0)[idx]
            CardioType.ELLIPTICAL -> doubleArrayOf(5.0, 7.0, 9.0, 11.0)[idx]
            CardioType.ROW_MACHINE -> doubleArrayOf(6.0, 8.0, 10.0, 12.0)[idx]
            CardioType.BIKE_STATIONARY -> doubleArrayOf(5.5, 8.5, 10.5, 12.0)[idx]
            CardioType.RUN_OUTDOOR -> doubleArrayOf(7.0, 9.0, 11.0, 13.0)[idx]
            CardioType.BIKE_OUTDOOR -> doubleArrayOf(6.0, 8.0, 10.0, 12.0)[idx]
            CardioType.WALK -> doubleArrayOf(3.5, 5.0, 6.0, 7.5)[idx]
            CardioType.STAIR_CLIMBER -> doubleArrayOf(6.0, 8.5, 10.5, 12.5)[idx]
            CardioType.AIR_BIKE -> doubleArrayOf(5.0, 8.0, 11.0, 14.0)[idx]
            CardioType.SKI_ERG -> doubleArrayOf(5.5, 7.5, 9.5, 12.0)[idx]
            CardioType.CURVED_TREADMILL -> doubleArrayOf(7.5, 10.5, 13.0, 14.5)[idx]
            CardioType.SLED -> doubleArrayOf(7.0, 9.0, 11.0, 13.0)[idx]
        }
    }

    fun blockTypeLabel(type: CardioBlockType): String = when (type) {
        CardioBlockType.WARMUP -> "Calentamiento"
        CardioBlockType.WORK -> "Trabajo"
        CardioBlockType.RECOVER -> "Recuperación"
        CardioBlockType.COOLDOWN -> "Vuelta a la calma"
    }

    fun blockTypeShortLabel(type: CardioBlockType): String = when (type) {
        CardioBlockType.WARMUP -> "CAL"
        CardioBlockType.WORK -> "WORK"
        CardioBlockType.RECOVER -> "REC"
        CardioBlockType.COOLDOWN -> "COOL"
    }
}
