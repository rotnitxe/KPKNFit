package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioGuideTest {
    @Test
    fun bajaZoneIsCalentamiento() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.WALK, CardioIntensity.BAJA))
        assertEquals("Calentamiento", guide.zoneName)
        assertEquals("50-60%", guide.heartRatePercent)
        assertEquals("50-60 RPM", guide.cadenceRpm)
    }

    @Test
    fun mediaZoneIsQuemaGrasa() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.BIKE_STATIONARY, CardioIntensity.MEDIA))
        assertEquals("Quema grasa", guide.zoneName)
        assertEquals("60-70%", guide.heartRatePercent)
        assertEquals("60-70 RPM", guide.cadenceRpm)
    }

    @Test
    fun altaZoneIsAerobico() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.RUN_OUTDOOR, CardioIntensity.ALTA))
        assertEquals("Aeróbico", guide.zoneName)
        assertEquals("70-80%", guide.heartRatePercent)
        assertEquals("70-85 RPM", guide.cadenceRpm)
    }

    @Test
    fun muyAltaZoneIsAnaerobico() {
        val guide = CardioGuideEngine.guide(CardioDetails(CardioType.BIKE_OUTDOOR, CardioIntensity.MUY_ALTA))
        assertEquals("Anaeróbico", guide.zoneName)
        assertEquals("80-90%", guide.heartRatePercent)
        assertEquals("85+ RPM", guide.cadenceRpm)
    }
}
