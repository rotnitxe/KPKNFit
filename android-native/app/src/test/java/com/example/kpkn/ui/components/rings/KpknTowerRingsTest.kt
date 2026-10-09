package com.example.kpkn.ui.components.rings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica pura de la torre de RINGS: barrido, bandas de toque, pista discontinua, descripción y geometría del logo. */
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
}
