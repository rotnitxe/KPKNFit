package com.example.kpkn.data.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardioProgramModeTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun modeDerivationUsesHiitThenIntervalsThenLegacySteady() {
        val steady = CardioDetails(CardioType.TREADMILL)
        assertEquals(CardioProgramMode.STEADY, steady.programMode())
        val intervals = steady.copy(intervalBlocks = listOf(CardioIntervalBlock(durationSeconds = 30)))
        assertEquals(CardioProgramMode.INTERVALS, intervals.programMode())
        val hiit = intervals.copy(hiit = CardioHiitConfig())
        assertEquals(CardioProgramMode.HIIT_SIT, hiit.programMode())
    }

    @Test
    fun oldJsonAndNewFieldsRoundTrip() {
        val old = json.decodeFromString<CardioDetails>("{\"type\":\"TREADMILL\",\"targetDurationSeconds\":1200}")
        assertEquals(CardioProgramMode.STEADY, old.programMode())
        val original = CardioDetails(
            type = CardioType.SLED,
            intervalBlocks = listOf(CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 10, targetKcal = 5.0)),
            hiit = CardioHiitConfig(protocol = HiitProtocol.SIT, targetRpe = 10.0),
        )
        val decoded = json.decodeFromString<CardioDetails>(json.encodeToString(original))
        assertEquals(CardioProgramMode.HIIT_SIT, decoded.programMode())
        assertEquals(5.0, decoded.intervalBlocks.single().targetKcal ?: -1.0, 0.0)
        assertNotNull(decoded.hiit)
    }
}
