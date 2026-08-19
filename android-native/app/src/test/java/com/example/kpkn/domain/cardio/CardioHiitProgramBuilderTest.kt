package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitWorkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioHiitProgramBuilderTest {
    @Test
    fun tabataMaterializesWarmupRoundsAndCooldownExactly() {
        val config = CardioHiitConfig(warmupSeconds = 300, workSeconds = 20, restSeconds = 10, rounds = 8, cooldownSeconds = 180)
        val details = CardioHiitProgramBuilder.buildDetails(config, CardioType.AIR_BIKE)
        assertEquals(720, details.effectiveDurationSeconds())
        assertEquals(18, details.intervalBlocks.size)
        assertEquals(1, details.intervalBlocks.count { it.type == CardioBlockType.WARMUP })
        assertEquals(8, details.intervalBlocks.count { it.type == CardioBlockType.WORK })
        assertEquals(8, details.intervalBlocks.count { it.type == CardioBlockType.RECOVER })
        assertEquals(1, details.intervalBlocks.count { it.type == CardioBlockType.COOLDOWN })
        assertEquals(1, details.intervalRounds)
    }

    @Test
    fun sitAndTargetsAreMaterializedOnlyOnWorkBlocks() {
        val config = CardioHiitConfig(
            workSeconds = 10,
            restSeconds = 60,
            rounds = 8,
            protocol = HiitProtocol.SIT,
            targetRpe = 10.0,
            workTargetType = HiitWorkTarget.KCAL,
            workTargetValue = 12.0,
        )
        val blocks = CardioHiitProgramBuilder.build(config, CardioType.SLED)
        assertTrue(blocks.filter { it.type == CardioBlockType.WORK }.all { it.intensityLevel == 10 })
        assertTrue(blocks.filter { it.type != CardioBlockType.WORK }.all { it.targetKcal == null && it.targetDistanceMeters == null })
        assertTrue(blocks.filter { it.type == CardioBlockType.WORK }.all { it.targetKcal == 12.0 })
    }
}
