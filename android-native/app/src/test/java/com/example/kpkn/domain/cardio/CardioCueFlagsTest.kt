package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pulido v1 · cardio del editor: los avisos de fase se guardan por cardio también en «Intervalos» (sin protocolo por series). */
class CardioCueFlagsTest {
    private val blocks = listOf(
        CardioIntervalBlock(id = "warm", type = CardioBlockType.WARMUP, durationSeconds = 10),
        CardioIntervalBlock(id = "work", type = CardioBlockType.WORK, durationSeconds = 10),
    )
    private val intervals = CardioDetails(type = CardioType.TREADMILL, intervalBlocks = blocks)

    @Test
    fun withoutAnythingSavedEveryCueIsOn() {
        assertEquals(CardioCueFlags(beeps = true, voice = true, vibration = true), intervals.cueFlags())
    }

    @Test
    fun theSeriesProtocolIsTheFallback() {
        val quiet = intervals.copy(hiit = CardioHiitConfig(beepsEnabled = false, voiceCuesEnabled = true, vibrationEnabled = false))
        assertEquals(CardioCueFlags(beeps = false, voice = true, vibration = false), quiet.cueFlags())
    }

    @Test
    fun theCardioOwnValueBeatsTheProtocol() {
        val d = intervals.copy(hiit = CardioHiitConfig(beepsEnabled = false), beepsEnabled = true, voiceCuesEnabled = false)
        assertEquals(CardioCueFlags(beeps = true, voice = false, vibration = true), d.cueFlags())
    }

    @Test
    fun intervalsWithTheirOwnFlagsAreSilencedWithoutAProtocol() {
        val d = intervals.copy(beepsEnabled = false, voiceCuesEnabled = false, vibrationEnabled = false)
        assertNull(d.hiit)
        val previous = CardioIntervalEngine.progressAt(d, 0)!!
        val current = CardioIntervalEngine.progressAt(d, 10)!!
        val cue = CardioCueRules.transitionCue(previous, current, d)
        assertFalse(cue.phaseChangeTone)
        assertNull(cue.vibration)
        assertNull(cue.speech)
        assertTrue(CardioCueRules.countdownCue(2, CardioBlockType.WORK, d).countdownBeeps.isEmpty())
    }

    @Test
    fun theDetailsOverloadKeepsTheOldBehaviourWhenNothingIsSaved() {
        val previous = CardioIntervalEngine.progressAt(intervals, 0)!!
        val current = CardioIntervalEngine.progressAt(intervals, 10)!!
        val cue = CardioCueRules.transitionCue(previous, current, intervals)
        assertTrue(cue.phaseChangeTone)
        assertTrue(cue.speech!!.startsWith("Trabajo"))
        assertEquals(listOf(3), CardioCueRules.countdownCue(3, CardioBlockType.WORK, intervals).countdownBeeps)
    }

    @Test
    fun paceOnlyMakesSenseWhereYouRunOrWalk() {
        val withPace = CardioCatalog.items.filter { it.supportsPace }.map { it.type }.toSet()
        assertEquals(setOf(CardioType.TREADMILL, CardioType.CURVED_TREADMILL, CardioType.RUN_OUTDOOR, CardioType.WALK), withPace)
    }

    @Test
    fun applyingASeriesRepeatKeepsTheCardioOwnFlags() {
        val steady = CardioDetails(type = CardioType.ROW_MACHINE, beepsEnabled = false)
        val series = CardioRepeatGrammar.applyUniform(steady, CardioUniformRepeat(warmupSeconds = 60, workSeconds = 30, restSeconds = 30, rounds = 4, cooldownSeconds = 60))
        assertEquals(false, series.hiit!!.beepsEnabled)
        assertEquals(false, series.cueFlags().beeps)
        assertEquals(true, series.cueFlags().voice)
    }
}
