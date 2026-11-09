package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioAvisosTest {
    @Test
    fun continuousSummaryKeepsUsefulStartAndFinishCues() {
        val details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 600)
        assertEquals("Sonidos · Voz · Vibración · Pantalla encendida", cardioAvisosSummary(details))
        assertTrue(!cardioHasPhases(details))
    }
}
