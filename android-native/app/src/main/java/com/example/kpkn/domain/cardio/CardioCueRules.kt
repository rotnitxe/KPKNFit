package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.HiitRestNature

enum class VibCue { SHORT_TICK, DOUBLE_WORK, LONG_FINISH }

data class CardioCuePlan(
    val countdownBeeps: List<Int> = emptyList(),
    val phaseChangeTone: Boolean = false,
    val vibration: VibCue? = null,
    val speech: String? = null,
)

/** Pure cue policy; platform playback and settings gates live outside this class. */
object CardioCueRules {
    fun transitionCue(
        prev: CardioIntervalEngine.Progress?,
        curr: CardioIntervalEngine.Progress,
        hiit: CardioHiitConfig?,
    ): CardioCuePlan {
        val changed = prev?.currentIndex != curr.currentIndex
        if (!changed || curr.currentBlock == null) return CardioCuePlan()
        val block = curr.currentBlock
        val config = hiit
        val speech = if (config?.voiceCuesEnabled != false) {
            val target = CardioPrescriptionFormatter.targetBits(block)?.let { " · $it" } ?: ""
            when (block.type) {
                CardioBlockType.WORK -> {
                    val suffix = if (curr.currentIndex >= curr.totalBlocks - 2) " · Última ronda" else ""
                    "¡Sprint! ${block.durationSeconds} segundos$target$suffix"
                }
                CardioBlockType.RECOVER -> if (config?.restNature == HiitRestNature.PASSIVE) "Descanso, alto total" else "Descanso activo, muévete suave"
                CardioBlockType.WARMUP -> "Calentamiento ${block.durationSeconds} segundos$target"
                CardioBlockType.COOLDOWN -> "Vuelta a la calma$target"
            }
        } else null
        val vibration = if (config?.vibrationEnabled != false) {
            when (block.type) {
                CardioBlockType.WORK -> VibCue.DOUBLE_WORK
                CardioBlockType.RECOVER, CardioBlockType.WARMUP, CardioBlockType.COOLDOWN -> VibCue.SHORT_TICK
            }
        } else null
        return CardioCuePlan(
            phaseChangeTone = config?.beepsEnabled != false,
            vibration = vibration,
            speech = speech,
        )
    }

    fun countdownCue(
        remainingInBlock: Int,
        blockType: CardioBlockType,
        hiit: CardioHiitConfig?,
    ): CardioCuePlan {
        val beeps = if (hiit?.beepsEnabled != false && remainingInBlock in 1..3) listOf(remainingInBlock) else emptyList()
        val vibration = if (hiit?.vibrationEnabled != false && remainingInBlock == 1) VibCue.SHORT_TICK else null
        return CardioCuePlan(countdownBeeps = beeps, vibration = vibration)
    }
}
