package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pulido v1 · cardio del editor: la escala del selector único (`CE_CFG` de `scenes-ce.js`) y las fichas de las 12 modalidades. */
class CardioValueScaleTest {

    @Test
    fun theDurationReadsLikeTheCanvas() {
        val k = CardioValueKind.DURATION
        assertEquals("Libre" to "sin objetivo de tiempo", k.big(0))
        assertEquals("18" to "min", k.big(18))
        assertEquals("1:30" to "h", k.big(90))
        assertEquals("1 h 30 min", k.hint(90))
        assertEquals("", k.hint(45))
        assertEquals("Máximo 3 h", k.maxMessage)
        assertEquals(listOf("Libre", "10 min", "15 min", "20 min", "30 min", "45 min", "60 min"), listOfNotNull(k.noneN).plus(k.shortcuts).map(k::shortcutLabel))
    }

    @Test
    fun typedTimesAreEnteredLikeAMicrowave() {
        // 1, 3, 0 = 1:30 → 90 s → 18 posiciones de 5 s; el cero inicial no cuenta.
        assertEquals(18, CardioValueKind.WORK.parseTyped("130"))
        assertEquals("1:30", CardioValueKind.WORK.typedDisplay("130"))
        assertEquals("5", CardioValueKind.WORK.typedDisplay("5"))
        assertEquals("0", CardioValueKind.WORK.typedDisplay(""))
        assertEquals("12:00", CardioValueKind.WARMUP.typedDisplay("1200"))
        assertEquals(330, CardioValueKind.PACE.parseTyped("530"))
        assertNull(CardioValueKind.WORK.parseTyped(""))
        assertEquals(25, CardioValueKind.DURATION.parseTyped("25"))
    }

    @Test
    fun anOutOfRangeValueIsAnError() {
        val k = CardioValueKind.DURATION
        assertTrue(k.isInRange(k.parseTyped("180")!!))
        assertFalse(k.isInRange(k.parseTyped("250")!!))
        assertFalse(CardioValueKind.SPEED.isInRange(CardioValueKind.SPEED.parseTyped("2")!!))
        assertTrue(CardioValueKind.SPEED.isInRange(CardioValueKind.SPEED.parseTyped("12,5")!!))
        // La posición «sin objetivo» del ritmo vale aunque quede fuera de la regla.
        assertTrue(CardioValueKind.PACE.isInRange(0))
        assertEquals(60, CardioValueKind.PACE.rulerN(0))
    }

    @Test
    fun decimalsUseTheSpanishComma() {
        val d = CardioValueKind.DISTANCE
        assertEquals(55, d.parseTyped("5,5"))
        assertEquals(5, d.parseTyped("0,5"))
        assertEquals(125, CardioValueKind.SPEED.parseTyped("12.5"))
        assertEquals("5,0" to "km", d.big(50))
        assertEquals("5,5" to "km", d.big(55))
        assertEquals("—" to "sin objetivo de distancia", d.big(0))
        assertEquals(listOf("Sin objetivo", "1 km", "3 km", "5 km", "10 km"), listOfNotNull(d.noneN).plus(d.shortcuts).map(d::shortcutLabel))
        assertEquals("6", CardioValueKind.tenths(60))
        assertEquals("6,5", CardioValueKind.tenths(65))
        assertEquals("1", d.tickLabel(10))
    }

    @Test
    fun theWorkAndRestShortcutsAreTheRealTemplates() {
        assertEquals(listOf("0:20", "0:30", "1:00", "2:00", "3:00"), CardioValueKind.WORK.shortcuts.map(CardioValueKind.WORK::shortcutLabel))
        assertEquals("Sin pausa" to "", CardioValueKind.REST.big(0))
        assertEquals("30" to "s", CardioValueKind.WORK.big(6))
        assertEquals("1:00" to "min", CardioValueKind.WORK.big(12))
        assertEquals("120 W", CardioValueKind.WATTS.shortcutLabel(24))
        assertEquals("50", CardioValueKind.WATTS.tickLabel(10))
    }

    @Test
    fun everyPositionIsAnExactValueAndEveryShortcutFitsTheRuler() {
        for (k in CardioValueKind.entries) {
            for (n in k.min..k.max) assertEquals("${k.name} $n", n, k.nOf(k.valueOf(n)))
            for (s in k.shortcuts) assertTrue("${k.name} atajo $s", s in k.min..k.max)
            assertTrue("${k.name} min < max", k.min < k.max)
            assertTrue("${k.name} marca larga", k.major >= 1 && k.tickDp > 0)
            // Todas las marcas largas tienen rótulo y todas las cifras grandes de la regla se pueden decir.
            for (n in k.min..k.max step k.major.coerceAtLeast(1)) {
                assertTrue(k.tickLabel(n).isNotEmpty())
                assertTrue(k.big(n).first.isNotEmpty())
            }
        }
    }

    @Test
    fun theTimesKeepTheirUnits() {
        assertEquals(90, CardioValueKind.WORK.valueOf(18))
        assertEquals(300, CardioValueKind.WARMUP.valueOf(20))
        assertEquals(600, CardioValueKind.BLOCK_DURATION.valueOf(40))
        assertEquals(2000, CardioValueKind.WORK_METERS.valueOf(40))
        assertTrue(CardioValueKind.WARMUP.hasModes && CardioValueKind.COOLDOWN.hasModes)
        assertFalse(CardioValueKind.WORK.hasModes)
    }

    @Test
    fun heartRateZonesRoundTripThroughTheirMidpoint() {
        for (z in 1..5) assertEquals(z, CardioZones.zoneOfPercent(CardioZones.midpointPercent(z)))
        assertEquals(listOf(55, 65, 75, 85, 95), (1..5).map(CardioZones::midpointPercent))
        assertEquals(1, CardioZones.zoneOfRpe(1))
        assertEquals(4, CardioZones.zoneOfRpe(8))
        assertEquals(5, CardioZones.zoneOfRpe(10))
        assertEquals(listOf("1–2", "3–4", "5–6", "7–8", "9–10"), (1..5).map(CardioZones::rpeRangeLabel))
    }

    @Test
    fun everyCardioHasItsOwnGuideWithRealTemplates() {
        val templateIds = CardioFeaturedTemplates.all.map { it.id }.toSet()
        for (type in CardioType.entries) {
            val guide = CardioFichaGuides.of(type)
            assertTrue("$type qué es", guide.what.length > 40)
            assertTrue("$type qué entrena", guide.trains.isNotEmpty())
            assertEquals("$type formatos", 3, guide.formats.size)
            assertTrue("$type formatos reales", guide.formats.all { it in templateIds })
            assertTrue("$type técnica", guide.tips.size >= 3)
        }
        assertEquals(12, CardioFichaGuides.all.size)
    }
}
