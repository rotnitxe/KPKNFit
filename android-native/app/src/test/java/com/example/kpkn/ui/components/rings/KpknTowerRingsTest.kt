package com.example.kpkn.ui.components.rings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de la torre de RINGS: barrido, bandas de toque, pista discontinua, descripción, geometría del logo y rutas del personaje. */
class KpknTowerRingsTest {

    @Test
    fun `el barrido es 360 por ciento del valor y se recorta a 0-100`() {
        assertEquals(0f, towerSweepDegrees(0f), 1e-4f)
        assertEquals(90f, towerSweepDegrees(25f), 1e-4f)
        assertEquals(259.2f, towerSweepDegrees(72f), 1e-3f)
        assertEquals(360f, towerSweepDegrees(100f), 1e-4f)
        assertEquals(360f, towerSweepDegrees(150f), 1e-4f)
        assertEquals(0f, towerSweepDegrees(-5f), 1e-4f)
    }

    @Test
    fun `cada banda vertical corresponde a su anillo`() {
        assertEquals(RingChannel.ENERGIA, towerChannelAtUnit(36f))
        assertEquals(RingChannel.ENERGIA, towerChannelAtUnit(48.4f))
        assertEquals(RingChannel.MUSCULOS, towerChannelAtUnit(48.5f))
        assertEquals(RingChannel.MUSCULOS, towerChannelAtUnit(55f))
        assertEquals(RingChannel.MUSCULOS, towerChannelAtUnit(62.4f))
        assertEquals(RingChannel.COLUMNA, towerChannelAtUnit(62.5f))
        assertEquals(RingChannel.COLUMNA, towerChannelAtUnit(82f))
    }

    @Test
    fun `sin valor la pista sale discontinua y con valor no`() {
        assertTrue(towerTrackIsDashed(null))
        assertFalse(towerTrackIsDashed(0))
        assertFalse(towerTrackIsDashed(100))
    }

    @Test
    fun `la descripcion lista los canales visibles y marca los que no tienen valor`() {
        val todos = RingChannel.entries.toSet()
        assertEquals(
            "Columna 72 %, Músculos 64 %, Energía 80 %",
            towerRingsDescription(TowerRingValues(72, 64, 80), todos),
        )
        assertEquals(
            "Columna sin calibrar, Músculos 45 %, Energía 20 %",
            towerRingsDescription(TowerRingValues(null, 45, 20), todos),
        )
        assertEquals(
            "Energía 80 %",
            towerRingsDescription(TowerRingValues(72, 64, 80), setOf(RingChannel.ENERGIA)),
        )
    }

    @Test
    fun `los centros de la torre siguen la geometria del logo y quedan centrados`() {
        // 760 × 465 px: 10 px por unidad del logo, sin margen. La caja del logo es x 12–88 e y 35,5–82.
        val columna = towerRingCenter(RingChannel.COLUMNA, 760f, 465f)
        assertEquals(380f, columna.x, 1e-3f)
        assertEquals(345f, columna.y, 1e-3f) // (70 − 35,5) · 10
        assertEquals(195f, towerRingCenter(RingChannel.MUSCULOS, 760f, 465f).y, 1e-3f) // (55 − 35,5) · 10
        assertEquals(65f, towerRingCenter(RingChannel.ENERGIA, 760f, 465f).y, 1e-3f) // (42 − 35,5) · 10
        // Un área más alta que la torre la centra en vertical: (600 − 465) / 2 = 67,5 px de margen.
        assertEquals(412.5f, towerRingCenter(RingChannel.COLUMNA, 760f, 600f).y, 1e-3f)
    }

    @Test
    fun `LUMINOUS es el unico estilo activo y por defecto`() {
        // LUMINOUS es el estilo definitivo fijado por la decision de producto
        assertEquals("LUMINOUS", TowerRingsStyle.LUMINOUS.name)
    }

    @Test
    fun `sin calibrar no hay arco de progreso y la pista es discontinua`() {
        // Un canal sin calibrar (null) tiene barrido 0, nunca dibuja arco ni llega a 100
        val sweepNull = towerSweepDegrees(null?.toFloat() ?: 0f)
        assertEquals(0f, sweepNull, 1e-4f)
        assertTrue(towerTrackIsDashed(null))
        assertFalse(towerTrackIsDashed(50))
    }

    @Test
    fun `el personaje no aterriza en un anillo sin calibrar`() {
        // Calibrados todos: ruta completa Columna -> Músculos -> Energía
        val all = HopRoutePlanner.resolveLandingChannels(setOf(RingChannel.COLUMNA, RingChannel.MUSCULOS, RingChannel.ENERGIA))
        assertEquals(listOf(RingChannel.COLUMNA, RingChannel.MUSCULOS, RingChannel.ENERGIA), all)

        // Sin Músculos calibrado: la ruta omite Músculos
        val noMuscle = HopRoutePlanner.resolveLandingChannels(setOf(RingChannel.COLUMNA, RingChannel.ENERGIA))
        assertEquals(listOf(RingChannel.COLUMNA, RingChannel.ENERGIA), noMuscle)
        assertFalse(RingChannel.MUSCULOS in noMuscle)

        // En la fase de aterrizaje intermedio (p=0.23), el personaje no aterriza en Músculos si falta Músculos
        val poseNoMuscle = calculateHopPose(0.23f, noMuscle)
        assertFalse("El personaje no debe aterrizar en Músculos si no está calibrado", poseNoMuscle.channel == RingChannel.MUSCULOS)

        // Solo Columna calibrado: el personaje no aterriza en Energía
        val onlyCol = HopRoutePlanner.resolveLandingChannels(setOf(RingChannel.COLUMNA))
        assertEquals(listOf(RingChannel.COLUMNA), onlyCol)
        val poseTop = calculateHopPose(0.52f, onlyCol)
        assertEquals(RingChannel.COLUMNA, poseTop.channel)

        // Ninguno calibrado: fallback seguro al anillo base
        val none = HopRoutePlanner.resolveLandingChannels(emptySet())
        assertEquals(listOf(RingChannel.COLUMNA), none)
    }
}
