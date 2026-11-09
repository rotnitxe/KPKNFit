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

    @Test
    fun nonHiitIntervalsSayWorkInsteadOfSprint() {
        val z2 = details.copy(hiit = null)
        val previous = CardioIntervalEngine.progressAt(z2, 0)!!
        val current = CardioIntervalEngine.progressAt(z2, 10)!!
        val cue = CardioCueRules.transitionCue(previous, current, z2.hiit)
        assertTrue(cue.speech!!.startsWith("Trabajo"))
        assertFalse(cue.speech!!.contains("Sprint"))
        assertTrue(cue.phaseChangeTone)
        assertEquals(listOf(3), CardioCueRules.countdownCue(3, CardioBlockType.WORK, null).countdownBeeps)
    }

    @Test
    fun continuousCardioHasCountdownStartAndFinishCues() {
        val continuous = CardioDetails(
            type = CardioType.TREADMILL,
            targetDurationSeconds = 600,
        )
        assertEquals(listOf(3), CardioCueRules.sessionCountdownCue(3, continuous).countdownBeeps)
        assertEquals("Tres", CardioCueRules.sessionCountdownCue(3, continuous).speech)
        assertTrue(CardioCueRules.sessionStartCue(continuous).phaseChangeTone)
        assertEquals(VibCue.DOUBLE_WORK, CardioCueRules.sessionStartCue(continuous).vibration)
        assertTrue(CardioCueRules.sessionStartCue(continuous).speech!!.contains("Empieza"))
        assertTrue(CardioCueRules.sessionFinishCue(continuous).phaseChangeTone)
        assertEquals(VibCue.LONG_FINISH, CardioCueRules.sessionFinishCue(continuous).vibration)
        assertTrue(CardioCueRules.sessionFinishCue(continuous).speech!!.contains("terminado"))
    }

    @Test
    fun continuousCuePreferencesCanSilenceEachSignal() {
        val quiet = CardioDetails(
            type = CardioType.TREADMILL,
            targetDurationSeconds = 600,
            beepsEnabled = false,
            voiceCuesEnabled = false,
            vibrationEnabled = false,
        )
        listOf(
            CardioCueRules.sessionCountdownCue(3, quiet),
            CardioCueRules.sessionStartCue(quiet),
            CardioCueRules.sessionFinishCue(quiet),
        ).forEach { cue ->
            assertTrue(cue.countdownBeeps.isEmpty())
            assertFalse(cue.phaseChangeTone)
            assertEquals(null, cue.vibration)
            assertEquals(null, cue.speech)
        }
    }

    @Test
    fun globalSoundAndHapticPreferencesGateOnlyTheirOwnOutputs() {
        val cue = CardioCuePlan(
            countdownBeeps = listOf(3, 2, 1),
            phaseChangeTone = true,
            vibration = VibCue.DOUBLE_WORK,
            speech = "Trabajo",
        )

        val soundOff = cue.withSystemOutputPreferences(soundsEnabled = false, hapticsEnabled = true)
        assertTrue(soundOff.countdownBeeps.isEmpty())
        assertFalse(soundOff.phaseChangeTone)
        assertEquals(VibCue.DOUBLE_WORK, soundOff.vibration)
        assertEquals("Trabajo", soundOff.speech)

        val hapticsOff = cue.withSystemOutputPreferences(soundsEnabled = true, hapticsEnabled = false)
        assertEquals(listOf(3, 2, 1), hapticsOff.countdownBeeps)
        assertTrue(hapticsOff.phaseChangeTone)
        assertEquals(null, hapticsOff.vibration)
        assertEquals("Trabajo", hapticsOff.speech)
    }
}
