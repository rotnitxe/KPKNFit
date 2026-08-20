package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure materializer for the authoring representation of HIIT/SIT. */
object CardioHiitProgramBuilder {

    data class EffectiveHiitStructure(
        val sets: Int,
        val rounds: Int,
        val lastWorkSeconds: Int,
        val workTimeTargetSeconds: Int?,
    )

    /** Rounds repeat until the accumulated work time reaches the target (single set). */
    fun effectiveStructure(config: CardioHiitConfig): EffectiveHiitStructure {
        val workSeconds = config.workSeconds.coerceAtLeast(1)
        val workTimeTargetSeconds = config.workTargetValue
            ?.takeIf { config.workTargetType == HiitWorkTarget.TIME }
            ?.toInt()
            ?.takeIf { it > 0 }
        val sets = if (workTimeTargetSeconds != null) 1 else config.sets.coerceIn(1, 5)
        val rounds = if (workTimeTargetSeconds != null) {
            ceil(workTimeTargetSeconds.toDouble() / workSeconds).toInt()
        } else {
            config.rounds.coerceIn(1, 99)
        }
        val lastWorkSeconds = if (workTimeTargetSeconds != null) {
            (workTimeTargetSeconds - (rounds - 1) * workSeconds).coerceAtLeast(1)
        } else {
            workSeconds
        }
        return EffectiveHiitStructure(sets, rounds, lastWorkSeconds, workTimeTargetSeconds)
    }

    fun build(config: CardioHiitConfig, type: CardioType): List<CardioIntervalBlock> {
        val blocks = mutableListOf<CardioIntervalBlock>()
        val workSeconds = config.workSeconds.coerceAtLeast(1)
        val restSeconds = config.restSeconds.coerceAtLeast(0)
        val effective = effectiveStructure(config)
        val rounds = effective.rounds
        val sets = effective.sets
        val workLevel = config.targetRpe.roundToInt().coerceIn(1, 10)
        val recoverLevel = if (config.restNature == HiitRestNature.ACTIVE) 3 else null

        if (config.warmupSeconds > 0) {
            blocks += block(
                type = CardioBlockType.WARMUP,
                durationSeconds = config.warmupSeconds,
                intensityLevel = 3,
                typeOfCardio = type,
            )
        }

        repeat(sets) { setIndex ->
            repeat(rounds) { roundIndex ->
                blocks += block(
                    type = CardioBlockType.WORK,
                    durationSeconds = if (roundIndex == rounds - 1) effective.lastWorkSeconds else workSeconds,
                    intensityLevel = workLevel,
                    typeOfCardio = type,
                    targetKcal = config.workTargetValue?.takeIf { config.workTargetType == HiitWorkTarget.KCAL },
                    targetDistanceMeters = config.workTargetValue?.takeIf { config.workTargetType == HiitWorkTarget.DISTANCE },
                )
                val trailingRest = effective.workTimeTargetSeconds != null && roundIndex == rounds - 1
                if (restSeconds > 0 && !trailingRest) {
                    blocks += block(
                        type = CardioBlockType.RECOVER,
                        durationSeconds = restSeconds,
                        intensityLevel = recoverLevel,
                        typeOfCardio = type,
                    )
                }
            }
            if (setIndex < sets - 1 && config.restBetweenSetsSeconds > 0) {
                blocks += block(
                    type = CardioBlockType.RECOVER,
                    durationSeconds = config.restBetweenSetsSeconds,
                    intensityLevel = recoverLevel,
                    typeOfCardio = type,
                )
            }
        }

        if (config.cooldownSeconds > 0) {
            blocks += block(
                type = CardioBlockType.COOLDOWN,
                durationSeconds = config.cooldownSeconds,
                intensityLevel = 2,
                typeOfCardio = type,
            )
        }
        return blocks
    }

    fun buildDetails(
        config: CardioHiitConfig,
        type: CardioType,
        base: CardioDetails? = null,
    ): CardioDetails {
        val blocks = build(config, type)
        val fallback = base ?: CardioDetails(type = type)
        return fallback.copy(
            type = type,
            intervalBlocks = blocks,
            intervalRounds = 1,
            targetDurationSeconds = blocks.sumOf { it.durationSeconds },
            hiit = config,
        )
    }

    private fun block(
        type: CardioBlockType,
        durationSeconds: Int,
        intensityLevel: Int?,
        typeOfCardio: CardioType,
        targetKcal: Double? = null,
        targetDistanceMeters: Double? = null,
    ): CardioIntervalBlock = CardioIntervalBlock(
        id = UUID.randomUUID().toString(),
        type = type,
        durationSeconds = durationSeconds.coerceAtLeast(1),
        speedKmh = suggestedSpeed(typeOfCardio, intensityLevel),
        watts = suggestedWatts(typeOfCardio, intensityLevel),
        intensityLevel = intensityLevel,
        targetKcal = targetKcal?.takeIf { it > 0.0 },
        targetDistanceMeters = targetDistanceMeters?.takeIf { it > 0.0 },
    )

    private fun suggestedSpeed(type: CardioType, level: Int?): Double? {
        if (level == null) return null
        return when (type) {
            CardioType.TREADMILL, CardioType.CURVED_TREADMILL -> 5.0 + level * 0.9
            CardioType.RUN_OUTDOOR -> 5.0 + level * 0.9
            CardioType.WALK -> 3.5 + level * 0.35
            CardioType.BIKE_OUTDOOR -> 10.0 + level * 1.4
            else -> null
        }
    }

    private fun suggestedWatts(type: CardioType, level: Int?): Int? {
        if (level == null) return null
        return when (type) {
            CardioType.BIKE_STATIONARY,
            CardioType.ROW_MACHINE,
            CardioType.AIR_BIKE,
            CardioType.SKI_ERG,
            -> (40 + level * 20).coerceAtMost(260)
            else -> null
        }
    }
}
