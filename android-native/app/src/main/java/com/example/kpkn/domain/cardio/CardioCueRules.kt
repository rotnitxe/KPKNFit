package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.HiitRestNature

enum class VibCue { SHORT_TICK, DOUBLE_WORK, LONG_FINISH }

data class CardioCuePlan(
    val countdownBeeps: List<Int> = emptyList(),
    val phaseChangeTone: Boolean = false,
    val vibration: VibCue? = null,
    val speech: String? = null,
)

/** Apply the global sound and haptic switches without coupling either output to the other. */
fun CardioCuePlan.withSystemOutputPreferences(soundsEnabled: Boolean, hapticsEnabled: Boolean): CardioCuePlan = copy(
    countdownBeeps = countdownBeeps.takeIf { soundsEnabled }.orEmpty(),
    phaseChangeTone = phaseChangeTone && soundsEnabled,
    vibration = vibration.takeIf { hapticsEnabled },
)

/**
 * Los tres avisos de fase de un cardio (sonidos, voz y vibración). Salen de [CardioDetails.cueFlags]: lo que el usuario fijó para ese cardio
 * (también en «Intervalos», que no tiene [CardioHiitConfig]) y, si no fijó nada, lo de su protocolo por series o «activo».
 */
data class CardioCueFlags(
    val beeps: Boolean = true,
    val voice: Boolean = true,
    val vibration: Boolean = true,
) {
    companion object {
        fun of(hiit: CardioHiitConfig?): CardioCueFlags = CardioCueFlags(
            beeps = hiit?.beepsEnabled ?: true,
            voice = hiit?.voiceCuesEnabled ?: true,
            vibration = hiit?.vibrationEnabled ?: true,
        )
    }
}

/** Los avisos de fase de este cardio: el valor propio, o el del protocolo por series, o activo. */
fun CardioDetails.cueFlags(): CardioCueFlags = CardioCueFlags(
    beeps = beepsEnabled ?: hiit?.beepsEnabled ?: true,
    voice = voiceCuesEnabled ?: hiit?.voiceCuesEnabled ?: true,
    vibration = vibrationEnabled ?: hiit?.vibrationEnabled ?: true,
)

/** Pure cue policy; platform playback and settings gates live outside this class. */
object CardioCueRules {
    fun sessionStartCue(details: CardioDetails): CardioCuePlan {
        val flags = details.cueFlags()
        val firstBlock = details.intervalBlocks.firstOrNull()
        val label = firstBlock?.let { CardioIntervalEngine.blockTypeLabel(it.type) }
        val speech = if (flags.voice) {
            if (label == null) "Empieza el cardio. Buen trabajo." else "Empieza el cardio: $label, ${firstBlock.durationSeconds} segundos."
        } else null
        return CardioCuePlan(
            phaseChangeTone = flags.beeps,
            vibration = if (flags.vibration) VibCue.DOUBLE_WORK else null,
            speech = speech,
        )
    }

    fun sessionFinishCue(details: CardioDetails): CardioCuePlan {
        val flags = details.cueFlags()
        return CardioCuePlan(
            phaseChangeTone = flags.beeps,
            vibration = if (flags.vibration) VibCue.LONG_FINISH else null,
            speech = if (flags.voice) "Cardio terminado. Buen trabajo." else null,
        )
    }

    fun sessionCountdownCue(remaining: Int, details: CardioDetails): CardioCuePlan {
        val flags = details.cueFlags()
        val number = when (remaining) { 3 -> "Tres"; 2 -> "Dos"; 1 -> "Uno"; else -> null }
        return CardioCuePlan(
            countdownBeeps = if (flags.beeps && remaining in 1..3) listOf(remaining) else emptyList(),
            vibration = if (flags.vibration && remaining == 1) VibCue.SHORT_TICK else null,
            speech = number?.takeIf { flags.voice },
        )
    }

    fun transitionCue(
        prev: CardioIntervalEngine.Progress?,
        curr: CardioIntervalEngine.Progress,
        details: CardioDetails,
    ): CardioCuePlan = transitionCue(prev, curr, details.hiit, details.cueFlags())

    fun countdownCue(
        remainingInBlock: Int,
        blockType: CardioBlockType,
        details: CardioDetails,
    ): CardioCuePlan = countdownCue(remainingInBlock, blockType, details.hiit, details.cueFlags())

    fun transitionCue(
        prev: CardioIntervalEngine.Progress?,
        curr: CardioIntervalEngine.Progress,
        hiit: CardioHiitConfig?,
    ): CardioCuePlan = transitionCue(prev, curr, hiit, CardioCueFlags.of(hiit))

    fun countdownCue(
        remainingInBlock: Int,
        blockType: CardioBlockType,
        hiit: CardioHiitConfig?,
    ): CardioCuePlan = countdownCue(remainingInBlock, blockType, hiit, CardioCueFlags.of(hiit))

    private fun transitionCue(
        prev: CardioIntervalEngine.Progress?,
        curr: CardioIntervalEngine.Progress,
        hiit: CardioHiitConfig?,
        flags: CardioCueFlags,
    ): CardioCuePlan {
        val changed = prev?.currentIndex != curr.currentIndex
        if (!changed || curr.currentBlock == null) return CardioCuePlan()
        val block = curr.currentBlock
        val voiceEnabled = flags.voice
        val beepsEnabled = flags.beeps
        val vibrationEnabled = flags.vibration
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

    private fun countdownCue(
        remainingInBlock: Int,
        blockType: CardioBlockType,
        hiit: CardioHiitConfig?,
        flags: CardioCueFlags,
    ): CardioCuePlan {
        val beepsEnabled = flags.beeps
        val vibrationEnabled = flags.vibration
        val beeps = if (beepsEnabled && remainingInBlock in 1..3) listOf(remainingInBlock) else emptyList()
        val vibration = if (vibrationEnabled && remainingInBlock == 1) VibCue.SHORT_TICK else null
        val countdownSpeech = when (remainingInBlock) {
            3 -> "Tres"
            2 -> "Dos"
            1 -> "Uno"
            else -> null
        }.takeIf { flags.voice }
        return CardioCuePlan(countdownBeeps = beeps, vibration = vibration, speech = countdownSpeech)
    }
}
