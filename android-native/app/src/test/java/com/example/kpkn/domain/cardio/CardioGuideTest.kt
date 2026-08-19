package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioGuideTest {
    @Test
    fun bajaRpeIsSubjectiveEasyGuide() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.WALK, CardioIntensity.BAJA))
        assertEquals(3, guide.rpeTarget)
        assertEquals("60–70% FCmáx (referencia)", guide.hrPercentRef)
        assertEquals(null, guide.cadenceRef)
    }

    @Test
    fun mediaRpeHasNoFatLossZoneLabel() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.BIKE_STATIONARY, CardioIntensity.MEDIA))
        assertEquals(6, guide.rpeTarget)
        assertEquals("70–80% FCmáx (referencia)", guide.hrPercentRef)
        assertEquals("Cadencia según equipo", guide.cadenceRef)
    }

    @Test
    fun altaRpeIsHardGuide() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.RUN_OUTDOOR, CardioIntensity.ALTA))
        assertEquals(8, guide.rpeTarget)
        assertEquals("80–90% FCmáx (referencia)", guide.hrPercentRef)
        assertEquals(null, guide.cadenceRef)
    }

    @Test
    fun veryHighRpeIsMaximumGuide() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.BIKE_OUTDOOR, CardioIntensity.MUY_ALTA))
        assertEquals(10, guide.rpeTarget)
        assertEquals(">90% FCmáx (referencia)", guide.hrPercentRef)
        assertEquals("Cadencia según equipo", guide.cadenceRef)
    }

    @Test
    fun hiitUsesExactConfiguredRpe() {
        val config = com.example.kpkn.data.models.CardioHiitConfig(targetRpe = 8.5)
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.AIR_BIKE, hiit = config))
        assertEquals(9, guide.rpeTarget)
        assertEquals("Cadencia según equipo", guide.cadenceRef)
    }
}
