package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.cardio.CardioHiitProgramBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioPrescriptionFormatterTest {

    @Test
    fun tabataSentenceIncludesWarmupRepeatAndCooldown() {
        val template = CardioHiitTemplates.findById("hiit_tabata_20_10")!!
        val details = CardioHiitProgramBuilder.buildDetails(template.toConfig(), CardioType.TREADMILL)
        val sentence = CardioPrescriptionFormatter.sentence(details)
        assertTrue(sentence.contains("Cinta"))
        assertTrue(sentence.contains("Calentamiento 5 min"))
        assertTrue(sentence.contains("8×"))
        assertTrue(sentence.contains("20 s esfuerzo"))
        assertTrue(sentence.contains("10 s pausa"))
        assertTrue(sentence.contains("Enfriamiento 3 min"))
        assertTrue(sentence.contains("12 min"))
        assertEquals(12 * 60, details.effectiveDurationSeconds())
    }

    @Test
    fun steadySentenceUsesDurationAndRpeAnchor() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            targetDurationSeconds = 20 * 60,
            intensityLevel = 6,
            intensity = CardioIntensity.MEDIA,
        )
        val sentence = CardioPrescriptionFormatter.sentence(details)
        assertTrue(sentence, sentence.contains("20 min"))
        assertTrue(sentence, sentence.contains("RPE 6"))
        assertTrue(sentence, sentence.contains("Algo duro"))
        assertFalse(sentence.contains("bloques"))
    }

    @Test
    fun pyramidSentenceListsWorkSteps() {
        val details = CardioDetails(
            type = CardioType.TREADMILL,
            intervalBlocks = listOf(
                CardioIntervalBlock(type = CardioBlockType.WARMUP, durationSeconds = 300),
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 60),
                CardioIntervalBlock(type = CardioBlockType.RECOVER, durationSeconds = 60),
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 120),
                CardioIntervalBlock(type = CardioBlockType.RECOVER, durationSeconds = 120),
                CardioIntervalBlock(type = CardioBlockType.WORK, durationSeconds = 180),
                CardioIntervalBlock(type = CardioBlockType.COOLDOWN, durationSeconds = 180),
            ),
        )
        val sentence = CardioPrescriptionFormatter.sentence(details)
        assertTrue(sentence, sentence.contains("1 min → 2 min → 3 min"))
    }
}
