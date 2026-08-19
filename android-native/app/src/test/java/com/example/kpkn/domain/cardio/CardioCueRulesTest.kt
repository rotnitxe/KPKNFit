package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioCueRulesTest {
    private val details = CardioDetails(
        type = CardioType.AIR_BIKE,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "warm", type = CardioBlockType.WARMUP, durationSeconds = 10),
            CardioIntervalBlock(id = "work", type = CardioBlockType.WORK, durationSeconds = 10),
        ),
        hiit = CardioHiitConfig(protocol = HiitProtocol.HIIT),
    )

    @Test
    fun transitionAndCountdownCueAreScopedToPhaseChanges() {
        val previous = CardioIntervalEngine.progressAt(details, 0)!!
        val current = CardioIntervalEngine.progressAt(details, 10)!!
        val transition = CardioCueRules.transitionCue(previous, current, details.hiit)
        assertTrue(transition.phaseChangeTone)
        assertEquals(VibCue.DOUBLE_WORK, transition.vibration)
        assertTrue(transition.speech!!.contains("Sprint"))
        assertEquals(listOf(3), CardioCueRules.countdownCue(3, CardioBlockType.WORK, details.hiit).countdownBeeps)
        assertTrue(CardioCueRules.transitionCue(current, current, details.hiit).speech == null)
    }

    @Test
    fun disabledFlagsSilenceSignals() {
        val quiet = details.hiit!!.copy(beepsEnabled = false, voiceCuesEnabled = false, vibrationEnabled = false)
        val previous = CardioIntervalEngine.progressAt(details, 0)!!
        val current = CardioIntervalEngine.progressAt(details, 10)!!
        val cue = CardioCueRules.transitionCue(previous, current, quiet)
        assertFalse(cue.phaseChangeTone)
        assertEquals(null, cue.vibration)
        assertEquals(null, cue.speech)
        assertTrue(CardioCueRules.countdownCue(2, CardioBlockType.WORK, quiet).countdownBeeps.isEmpty())
    }
}
