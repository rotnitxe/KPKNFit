package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.cardio.CardioIntervalEngine

data class CardioRingDrain(
    val cns: Double,
    val muscular: Double,
    val spinal: Double,
    val muscleDrains: Map<String, Double>,
)

/** Conservative, deterministic cardio contribution to the three AUGE rings. */
object CardioRingDrainEngine {
    private const val CNS_SCALE = 0.45
    private const val MUSCLE_SCALE = 0.06
    private const val SPINAL_SCALE = 0.65

    fun drain(
        details: CardioDetails,
        durationSeconds: Int,
        rpeEffective: Double,
        settings: Settings,
    ): CardioRingDrain {
        val tanks = AugeFatigueEngine.calculatePersonalizedBatteryTanks(settings)
        val safeRpe = rpeEffective.coerceIn(1.0, 10.0)
        val blocks = CardioIntervalEngine.expandedBlocks(details)
        val duration = durationSeconds.coerceAtLeast(0)
        if (duration == 0) {
            return CardioRingDrain(0.0, 0.0, 0.0, emptyMap())
        }
        val source = if (blocks.isEmpty()) {
            listOf(CardioBlockType.WORK to duration)
        } else {
            blocks.map { it.type to it.durationSeconds }.let { list ->
                val planned = list.sumOf { it.second }.coerceAtLeast(1)
                if (duration > 0 && duration < planned) {
                    val factor = duration.toDouble() / planned
                    list.map { it.first to (it.second * factor).toInt().coerceAtLeast(0) }
                } else list
            }
        }
        val mets = if (blocks.isEmpty()) {
            CardioIntervalEngine.metForBlock(
                com.example.kpkn.data.models.CardioIntervalBlock(type = CardioBlockType.WORK, intensityLevel = safeRpe.toInt()),
                details.type,
                safeRpe.toInt(),
            ) * (duration / 60.0)
        } else {
            blocks.zip(source).sumOf { (block, pair) ->
                CardioIntervalEngine.metForBlock(block, details.type, safeRpe.toInt()) * (pair.second / 60.0)
            }
        }
        val workMinutes = source.sumOf { (type, seconds) ->
            val coefficient = when (type) {
                CardioBlockType.WORK -> 1.0
                CardioBlockType.RECOVER -> if (details.hiit?.restNature == HiitRestNature.ACTIVE) 0.3 else 0.1
                CardioBlockType.WARMUP, CardioBlockType.COOLDOWN -> 0.2
            }
            seconds / 60.0 * coefficient
        }
        val totalMinutes = source.sumOf { it.second } / 60.0
        val config = details.hiit
        val workSeconds = source.filter { it.first == CardioBlockType.WORK }.sumOf { it.second }
        val recoverySeconds = source.filter { it.first == CardioBlockType.RECOVER }.sumOf { it.second }
        val density = config?.let {
            it.workSeconds.toDouble() / (it.workSeconds + it.restSeconds).coerceAtLeast(1)
        } ?: (workSeconds.toDouble() / (workSeconds + recoverySeconds).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val protocolMultiplier = when (config?.protocol) {
            HiitProtocol.SIT -> 1.25
            HiitProtocol.HIIT -> 1.0
            null -> 0.6
        }
        val rpeMultiplier = AugeFatigueEngine.calculateRpeMultiplier(safeRpe)
        val cnsPoints = 6.0 * workMinutes * (rpeMultiplier - 1.0) * protocolMultiplier * (0.7 + 0.6 * density) * CNS_SCALE
        val musclePoints = 2.2 * mets * (0.6 + 0.4 * (safeRpe / 10.0)) * (0.8 + 0.4 * density) * MUSCLE_SCALE
        val spinalPoints = 9.0 * impactFactor(details.type) * totalMinutes * (0.5 + 0.5 * safeRpe / 10.0) * SPINAL_SCALE
        val cns = percent(cnsPoints, tanks.cns)
        val muscular = percent(musclePoints, tanks.muscular)
        val spinal = percent(spinalPoints, tanks.spinal)
        val muscleDrains = distributeMuscleDrain(details.type, muscular)
        return CardioRingDrain(cns, muscular, spinal, muscleDrains)
    }

    private fun percent(points: Double, tank: Double): Double = (points / tank.coerceAtLeast(1.0) * 100.0).coerceIn(0.0, 100.0)

    private fun impactFactor(type: CardioType): Double = when (type) {
        CardioType.RUN_OUTDOOR, CardioType.CURVED_TREADMILL, CardioType.TREADMILL -> 1.0
        CardioType.WALK -> 0.5
        CardioType.STAIR_CLIMBER -> 0.8
        CardioType.SLED -> 0.6
        CardioType.ROW_MACHINE -> 0.35
        CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR, CardioType.AIR_BIKE,
        CardioType.SKI_ERG, CardioType.ELLIPTICAL -> 0.2
    }

    private fun distributeMuscleDrain(type: CardioType, muscular: Double): Map<String, Double> {
        val weighted = when (type) {
            CardioType.TREADMILL, CardioType.RUN_OUTDOOR, CardioType.CURVED_TREADMILL -> mapOf("Cuádriceps" to 1.0, "Isquiosurales" to 0.5, "Glúteos" to 0.5, "Pantorrillas" to 0.5)
            CardioType.WALK -> mapOf("Cuádriceps" to 1.0, "Pantorrillas" to 0.5, "Glúteos" to 0.3)
            CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR -> mapOf("Cuádriceps" to 1.0, "Glúteos" to 0.5, "Pantorrillas" to 0.5)
            CardioType.AIR_BIKE -> mapOf("Cuádriceps" to 1.0, "Hombros" to 0.5, "Pectorales" to 0.5, "Dorsales" to 0.5)
            CardioType.ROW_MACHINE -> mapOf("Dorsales" to 1.0, "Cuádriceps" to 1.0, "Bíceps" to 0.5, "Core" to 0.5)
            CardioType.SKI_ERG -> mapOf("Dorsales" to 1.0, "Core" to 0.5, "Tríceps" to 0.5)
            CardioType.ELLIPTICAL -> mapOf("Cuádriceps" to 0.8, "Glúteos" to 0.6)
            CardioType.STAIR_CLIMBER -> mapOf("Glúteos" to 1.0, "Cuádriceps" to 1.0, "Pantorrillas" to 0.5)
            CardioType.SLED -> mapOf("Cuádriceps" to 1.0, "Glúteos" to 1.0, "Pantorrillas" to 0.5, "Core" to 0.5)
        }
        val total = weighted.values.sum().coerceAtLeast(1.0)
        return weighted.mapValues { (_, weight) -> muscular * weight / total }
    }
}
