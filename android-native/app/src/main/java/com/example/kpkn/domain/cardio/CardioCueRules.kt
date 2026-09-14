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
        val voiceEnabled = hiit?.voiceCuesEnabled ?: true
        val beepsEnabled = hiit?.beepsEnabled ?: true
        val vibrationEnabled = hiit?.vibrationEnabled ?: true
        val speech = if (voiceEnabled) {
            val target = CardioPrescriptionFormatter.targetBits(block)?.let { " · $it" } ?: ""
            when (block.type) {
                CardioBlockType.WORK -> {
                    val suffix = if (curr.currentIndex >= curr.totalBlocks - 2) " · Última ronda" else ""
                    val workLabel = when (hiit?.protocol) {
                        com.example.kpkn.data.models.HiitProtocol.HIIT,
                        com.example.kpkn.data.models.HiitProtocol.SIT,
                        -> "¡Sprint!"
                        else -> "Trabajo"
                    }
                    "$workLabel ${block.durationSeconds} segundos$target$suffix"
                }
                CardioBlockType.RECOVER -> if (hiit?.restNature == HiitRestNature.PASSIVE) "Descanso, alto total" else "Descanso activo, muévete suave"
                CardioBlockType.WARMUP -> "Calentamiento ${block.durationSeconds} segundos$target"
                CardioBlockType.COOLDOWN -> "Vuelta a la calma$target"
            }
        } else null
        val vibration = if (vibrationEnabled) {
            when (block.type) {
                CardioBlockType.WORK -> VibCue.DOUBLE_WORK
                CardioBlockType.RECOVER, CardioBlockType.WARMUP, CardioBlockType.COOLDOWN -> VibCue.SHORT_TICK
            }
        } else null
        return CardioCuePlan(
            phaseChangeTone = beepsEnabled,
            vibration = vibration,
            speech = speech,
        )
    }

    fun countdownCue(
        remainingInBlock: Int,
        blockType: CardioBlockType,
        hiit: CardioHiitConfig?,
    ): CardioCuePlan {
        val beepsEnabled = hiit?.beepsEnabled ?: true
        val vibrationEnabled = hiit?.vibrationEnabled ?: true
        val beeps = if (beepsEnabled && remainingInBlock in 1..3) listOf(remainingInBlock) else emptyList()
        val vibration = if (vibrationEnabled && remainingInBlock == 1) VibCue.SHORT_TICK else null
        return CardioCuePlan(countdownBeeps = beeps, vibration = vibration)
    }
}
