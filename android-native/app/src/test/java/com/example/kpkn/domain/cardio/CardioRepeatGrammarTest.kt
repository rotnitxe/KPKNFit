package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioRepeatGrammarTest {

    @Test
    fun hiitConfigIsUniformShape() {
        val details = CardioHiitProgramBuilder.buildDetails(
            CardioHiitConfig(warmupSeconds = 300, workSeconds = 20, restSeconds = 10, rounds = 8, cooldownSeconds = 180),
            CardioType.AIR_BIKE,
        )
        val shape = CardioRepeatGrammar.shape(details)
        assertTrue(shape is CardioAuthoringShape.Uniform)
        val repeat = (shape as CardioAuthoringShape.Uniform).repeat
        assertEquals(300, repeat.warmupSeconds)
        assertEquals(20, repeat.workSeconds)
        assertEquals(10, repeat.restSeconds)
        assertEquals(8, repeat.rounds)
        assertEquals(180, repeat.cooldownSeconds)
    }

    @Test
    fun uniformBlocksWithoutHiitStillInferRepeat() {
        val blocks = listOf(
            CardioIntervalBlock(type = CardioBlockType.WARMUP, durationSeconds = 180),
            CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 30),
            CardioIntervalBlock(type = CardioBlockType.RECOVER, durationSeconds = 30),
            CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 30),
            CardioIntervalBlock(type = CardioBlockType.RECOVER, durationSeconds = 30),
            CardioIntervalBlock(type = CardioBlockType.COOLDOWN, durationSeconds = 120),
        )
        val inferred = CardioRepeatGrammar.inferUniform(blocks)!!
        assertEquals(180, inferred.warmupSeconds)
        assertEquals(30, inferred.workSeconds)
        assertEquals(30, inferred.restSeconds)
        assertEquals(2, inferred.rounds)
        assertEquals(120, inferred.cooldownSeconds)
    }

    @Test
    fun irregularPyramidDoesNotInferUniform() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 60),
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 120),
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 180),
            ),
        )
        assertTrue(CardioRepeatGrammar.shape(details) is CardioAuthoringShape.Irregular)
    }
}
