package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget

data class CardioUniformRepeat(
    val warmupSeconds: Int,
    val warmupOpen: Boolean = false,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val cooldownSeconds: Int,
    val cooldownOpen: Boolean = false,
    val protocol: HiitProtocol = HiitProtocol.HIIT,
    val targetRpe: Double = 9.0,
    val sets: Int = 1,
    val restBetweenSetsSeconds: Int = 120,
    val restNature: HiitRestNature = HiitRestNature.ACTIVE,
    val workTargetType: HiitWorkTarget = HiitWorkTarget.TIME,
    val workTargetValue: Double? = null,
) {
    fun toConfig(): CardioHiitConfig = CardioHiitConfig(
        warmupSeconds = if (warmupOpen) 0 else warmupSeconds.coerceAtLeast(0),
        workSeconds = workSeconds.coerceAtLeast(1),
        restSeconds = restSeconds.coerceAtLeast(0),
        rounds = rounds.coerceIn(1, 99),
        sets = sets.coerceIn(1, 5),
        restBetweenSetsSeconds = restBetweenSetsSeconds.coerceAtLeast(0),
        cooldownSeconds = if (cooldownOpen) 0 else cooldownSeconds.coerceAtLeast(0),
        workTargetType = workTargetType,
        workTargetValue = workTargetValue,
        protocol = protocol,
        targetRpe = targetRpe,
        restNature = restNature,
        warmupOpen = warmupOpen,
        cooldownOpen = cooldownOpen,
    )
}

sealed class CardioAuthoringShape {
    data class Steady(
        val durationSeconds: Int?,
        val distanceKm: Double?,
    ) : CardioAuthoringShape()

    data class Uniform(val repeat: CardioUniformRepeat) : CardioAuthoringShape()

    data class Irregular(val blocks: List<CardioIntervalBlock>) : CardioAuthoringShape()
}

object CardioRepeatGrammar {

    fun shape(details: CardioDetails): CardioAuthoringShape {
        details.hiit?.let { config ->
            return CardioAuthoringShape.Uniform(fromConfig(config))
        }
        if (!details.hasIntervals()) {
            return CardioAuthoringShape.Steady(details.targetDurationSeconds, details.targetDistanceKm)
        }
        val inferred = inferUniform(details.intervalBlocks)
        return if (inferred != null) {
            CardioAuthoringShape.Uniform(inferred)
        } else {
            CardioAuthoringShape.Irregular(details.intervalBlocks)
        }
    }

    fun fromConfig(config: CardioHiitConfig): CardioUniformRepeat = CardioUniformRepeat(
        warmupSeconds = config.warmupSeconds,
        warmupOpen = config.warmupOpen,
        workSeconds = config.workSeconds,
        restSeconds = config.restSeconds,
        rounds = config.rounds,
        cooldownSeconds = config.cooldownSeconds,
        cooldownOpen = config.cooldownOpen,
        protocol = config.protocol,
        targetRpe = config.targetRpe,
        sets = config.sets,
        restBetweenSetsSeconds = config.restBetweenSetsSeconds,
        restNature = config.restNature,
        workTargetType = config.workTargetType,
        workTargetValue = config.workTargetValue,
    )

    fun applyUniform(details: CardioDetails, repeat: CardioUniformRepeat): CardioDetails {
        val existing = details.hiit
        val config = repeat.toConfig().copy(
            beepsEnabled = existing?.beepsEnabled ?: true,
            voiceCuesEnabled = existing?.voiceCuesEnabled ?: true,
            vibrationEnabled = existing?.vibrationEnabled ?: true,
            keepScreenOn = existing?.keepScreenOn ?: true,
        )
        return CardioHiitProgramBuilder.buildDetails(config, details.type, details)
    }

    fun applySteady(
        details: CardioDetails,
        durationSeconds: Int?,
        distanceKm: Double? = details.targetDistanceKm,
    ): CardioDetails = details.copy(
        hiit = null,
        intervalBlocks = emptyList(),
        intervalRounds = 1,
        targetDurationSeconds = durationSeconds,
        targetDistanceKm = distanceKm,
    )

    fun inferUniform(blocks: List<CardioIntervalBlock>): CardioUniformRepeat? {
        if (blocks.size < 2) return null
        var start = 0
        var end = blocks.lastIndex
        var warmup = 0
        var cooldown = 0
        if (blocks[start].type == CardioBlockType.WARMUP) {
            warmup = blocks[start].durationSeconds
            start++
        }
        if (end >= start && blocks[end].type == CardioBlockType.COOLDOWN) {
            cooldown = blocks[end].durationSeconds
            end--
        }
        if (end < start) return null
        val core = blocks.subList(start, end + 1)
        if (core.isEmpty() || core.any { it.type == CardioBlockType.WARMUP || it.type == CardioBlockType.COOLDOWN }) {
            return null
        }
        val pairs = mutableListOf<Pair<CardioIntervalBlock, CardioIntervalBlock?>>()
        var index = 0
        while (index < core.size) {
            val work = core[index]
            if (work.type != CardioBlockType.WORK) return null
            val rest = core.getOrNull(index + 1)?.takeIf { it.type == CardioBlockType.RECOVER }
            pairs += work to rest
            index += if (rest != null) 2 else 1
        }
        if (pairs.isEmpty()) return null
        val workSeconds = pairs.first().first.durationSeconds
        val restSeconds = pairs.first().second?.durationSeconds ?: 0
        if (pairs.any { it.first.durationSeconds != workSeconds }) return null
        if (pairs.any { (it.second?.durationSeconds ?: 0) != restSeconds }) return null
        if (pairs.last().second == null && restSeconds > 0 && pairs.size > 1) return null
        return CardioUniformRepeat(
            warmupSeconds = warmup,
            workSeconds = workSeconds,
            restSeconds = restSeconds,
            rounds = pairs.size.coerceIn(1, 99),
            cooldownSeconds = cooldown,
        )
    }
}
