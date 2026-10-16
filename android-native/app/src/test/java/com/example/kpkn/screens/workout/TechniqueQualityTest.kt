package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La escala de la calidad técnica del feedback (LV6, F2): palabra, color y postura de la figura, todo puro. */
class TechniqueQualityTest {

    @Test
    fun theScaleIsOneToTenAndStartsAtEight() {
        assertEquals(1, TechniqueQuality.MIN)
        assertEquals(10, TechniqueQuality.MAX)
        assertEquals(8, TechniqueQuality.DEFAULT)
        assertEquals(1, TechniqueQuality.clamp(-4))
        assertEquals(10, TechniqueQuality.clamp(42))
        assertEquals(7, TechniqueQuality.clamp(7))
    }

    @Test
    fun eachNoteHasItsWord() {
        val expected = listOf(
            "muy irregular", "muy irregular", "irregular", "irregular", "aceptable",
            "aceptable", "buena", "limpia", "muy limpia", "perfecta",
        )
        expected.forEachIndexed { index, word -> assertEquals(word, TechniqueQuality.word(index + 1)) }
        // Fuera de la escala se queda con el extremo.
        assertEquals("muy irregular", TechniqueQuality.word(0))
        assertEquals("perfecta", TechniqueQuality.word(11))
    }

    @Test
    fun theColorGoesFromRedThroughAmberToGreen() {
        assertEquals(0xFFEF6F64.toInt(), TechniqueQuality.colorArgb(1))
        assertEquals(0xFF43D18C.toInt(), TechniqueQuality.colorArgb(10))
        assertEquals(0xFFEF6F64.toInt(), TechniqueQuality.redArgb())
        // 5 y 6 quedan a cada lado del ámbar (#F7CF73): 5 todavía tira a rojo, 6 ya tira a verde.
        assertEquals(0xFFF6C471.toInt(), TechniqueQuality.colorArgb(5))
        assertEquals(0xFFE3CF76.toInt(), TechniqueQuality.colorArgb(6))
        assertEquals(0xFF93D081.toInt(), TechniqueQuality.colorArgb(8))
    }

    @Test
    fun theGreenChannelRisesFromRedToAmberAndTheRedOneFallsFromAmberToGreen() {
        fun red(note: Int) = (TechniqueQuality.colorArgb(note) shr 16) and 0xFF
        fun green(note: Int) = (TechniqueQuality.colorArgb(note) shr 8) and 0xFF
        for (note in 1 until 5) assertTrue("el verde sube hasta el ámbar", green(note + 1) > green(note))
        for (note in 6 until 10) assertTrue("el rojo baja hasta el verde", red(note + 1) < red(note))
    }

    @Test
    fun thePoseFollowsTheNote() {
        for (note in 1..4) assertEquals("nota $note", TechniquePose.SHAKY, TechniqueQuality.pose(note))
        for (note in 5..7) assertEquals("nota $note", TechniquePose.LEANING, TechniqueQuality.pose(note))
        for (note in 8..10) assertEquals("nota $note", TechniquePose.UPRIGHT, TechniqueQuality.pose(note))
    }

    @Test
    fun onlyEightOrMoreSparkles() {
        assertFalse(TechniqueQuality.isClean(7))
        assertTrue(TechniqueQuality.isClean(8))
        assertTrue(TechniqueQuality.isClean(10))
    }

    @Test
    fun theTrackFillsAsTheNoteOverTen() {
        assertEquals(0.1f, TechniqueQuality.fraction(1), 1e-6f)
        assertEquals(0.8f, TechniqueQuality.fraction(8), 1e-6f)
        assertEquals(1f, TechniqueQuality.fraction(10), 1e-6f)
    }
}
