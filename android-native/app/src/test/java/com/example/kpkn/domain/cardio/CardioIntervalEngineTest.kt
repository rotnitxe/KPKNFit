package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CardioIntervalEngineTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun expandedRespectsRounds() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "a", type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 10.0),
                CardioIntervalBlock(id = "b", type = CardioBlockType.RECOVER, durationSeconds = 30, speedKmh = 5.0),
            ),
            intervalRounds = 3,
        )
        val expanded = CardioIntervalEngine.expandedBlocks(details)
        assertEquals(6, expanded.size)
        assertEquals("a", expanded[0].id)
        assertEquals("b", expanded[1].id)
        assertEquals("a", expanded[2].id)
    }

    @Test
    fun totalSecondsWithIntervals() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "1", durationSeconds = 60),
                CardioIntervalBlock(id = "2", durationSeconds = 90),
            ),
            intervalRounds = 2,
        )
        assertEquals(300, CardioIntervalEngine.totalSeconds(details))
        assertEquals(300, details.totalIntervalSeconds())
        assertEquals(300, details.effectiveDurationSeconds())
    }

    @Test
    fun progressAtBoundaries() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "w", type = CardioBlockType.WARMUP, durationSeconds = 60, speedKmh = 6.0),
                CardioIntervalBlock(id = "work", type = CardioBlockType.WORK, durationSeconds = 60, speedKmh = 10.0),
                CardioIntervalBlock(id = "rec", type = CardioBlockType.RECOVER, durationSeconds = 60, speedKmh = 5.0),
            ),
            intervalRounds = 1,
        )
        val p0 = CardioIntervalEngine.progressAt(details, 0)!!
        assertEquals(0, p0.currentIndex)
        assertEquals("w", p0.currentBlock?.id)
        assertEquals(60, p0.remainingInBlock)

        val p60 = CardioIntervalEngine.progressAt(details, 60)!!
        assertEquals(1, p60.currentIndex)
        assertEquals("work", p60.currentBlock?.id)

        val p179 = CardioIntervalEngine.progressAt(details, 179)!!
        assertEquals(2, p179.currentIndex)
        assertFalse(p179.isComplete)

        val p180 = CardioIntervalEngine.progressAt(details, 180)!!
        assertTrue(p180.isComplete)
        assertNull(p180.currentBlock)
    }

    @Test
    fun nullProgressWithoutIntervals() {
        val details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1200)
        assertNull(CardioIntervalEngine.progressAt(details, 0))
        assertFalse(details.hasIntervals())
        assertEquals(1200, details.effectiveDurationSeconds())
    }

    @Test
    fun serializationOldJsonWithoutIntervalsDecodes() {
        val oldJson = """{"type":"TREADMILL","targetDurationSeconds":1200}"""
        val decoded = json.decodeFromString<CardioDetails>(oldJson)
        assertEquals(CardioType.TREADMILL, decoded.type)
        assertEquals(0, decoded.intervalBlocks.size)
        assertEquals(1, decoded.intervalRounds)
        assertFalse(decoded.hasIntervals())
    }

    @Test
    fun serializationNewJsonRoundTrip() {
        val original = CardioDetails(
            type = CardioType.BIKE_STATIONARY,
            intervalBlocks = listOf(
                CardioIntervalBlock(id = "x", type = CardioBlockType.WORK, durationSeconds = 30, watts = 150),
            ),
            intervalRounds = 2,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CardioDetails>(encoded)
        assertEquals(1, decoded.intervalBlocks.size)
        assertEquals(30, decoded.intervalBlocks[0].durationSeconds)
        assertEquals(150, decoded.intervalBlocks[0].watts)
    }

    @Test
    fun metForBlockSpeedTakesPrecedence() {
        val block = CardioIntervalBlock(id = "m", durationSeconds = 60, speedKmh = 11.0)
        val met = CardioIntervalEngine.metForBlock(block, CardioType.TREADMILL, fallbackIntensityLevel = 6)
        // 11 km/h on treadmill should be ~11.5 per table
        assertTrue(met > 10.0)
    }
}
