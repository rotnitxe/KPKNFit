package com.example.kpkn.ui.components.rings

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lógica pura del ajuste oficial de los anillos (LV10b): del toque al valor, los pasos, las palabras, los tonos y qué pilar
 * es cada zona del cuerpo. Sin Compose ni Android.
 */
class RingAdjustMathTest {

    // ── ángulo ↔ valor ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `los cuatro puntos cardinales dan 0, 25, 50 y 75`() {
        assertEquals("arriba", 0, ringAdjustValueAt(0f, -1f))
        assertEquals("derecha", 25, ringAdjustValueAt(1f, 0f))
        assertEquals("abajo", 50, ringAdjustValueAt(0f, 1f))
        assertEquals("izquierda", 75, ringAdjustValueAt(-1f, 0f))
    }

    @Test
    fun `justo a la izquierda de arriba es 100 y justo a la derecha, 0`() {
        assertEquals(100, ringAdjustValueAt(-0.001f, -1f))
        assertEquals(0, ringAdjustValueAt(0.001f, -1f))
    }

    @Test
    fun `el valor sigue el sentido horario`() {
        // A las 2 en punto (60° desde arriba) el valor es la sexta parte de la vuelta: 17 (16,7 redondeado).
        val angle = 60.0 * PI / 180.0
        assertEquals(17, ringAdjustValueAt(sin(angle).toFloat(), -cos(angle).toFloat()))
        // A las 10 en punto (300°): 83.
        val back = 300.0 * PI / 180.0
        assertEquals(83, ringAdjustValueAt(sin(back).toFloat(), -cos(back).toFloat()))
    }

    @Test
    fun `valor y angulo son inversos para todo el recorrido`() {
        for (value in 0..99) {
            val angle = ringAdjustAngleDegrees(value).toDouble() * PI / 180.0
            val back = ringAdjustValueAt(sin(angle).toFloat(), -cos(angle).toFloat())
            assertEquals("valor $value", value, back)
        }
    }

    @Test
    fun `el angulo de un valor va de 0 a 360 grados y se recorta`() {
        assertEquals(0f, ringAdjustAngleDegrees(0), 0.001f)
        assertEquals(90f, ringAdjustAngleDegrees(25), 0.001f)
        assertEquals(360f, ringAdjustAngleDegrees(100), 0.001f)
        assertEquals(360f, ringAdjustAngleDegrees(250), 0.001f)
        assertEquals(0f, ringAdjustAngleDegrees(-4), 0.001f)
    }

    // ── arrastrar por arriba no salta de 100 a 0 ─────────────────────────────────────────────────

    @Test
    fun `dar la vuelta por arriba en sentido horario se queda en 100`() {
        assertEquals(100, ringAdjustDragValue(previous = 96, raw = 3))
    }

    @Test
    fun `dar la vuelta por arriba en sentido antihorario se queda en 0`() {
        assertEquals(0, ringAdjustDragValue(previous = 4, raw = 97))
    }

    @Test
    fun `un arrastre normal sigue al dedo`() {
        assertEquals(45, ringAdjustDragValue(previous = 40, raw = 45))
        assertEquals(60, ringAdjustDragValue(previous = 100, raw = 60))
        assertEquals(0, ringAdjustDragValue(previous = 10, raw = 0))
    }

    // ── pasos y háptica ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `los pasos no se salen de 0 a 100`() {
        assertEquals(100, ringAdjustStep(98, RingAdjustDialStep))
        assertEquals(0, ringAdjustStep(3, -RingAdjustDialStep))
        assertEquals(45, ringAdjustStep(50, -RingAdjustDialStep))
        assertEquals(100, ringAdjustStep(95, RingAdjustMuscleStep))
        assertEquals(0, ringAdjustStep(7, -RingAdjustMuscleStep))
    }

    @Test
    fun `los botones mueven 5 en Energia y Columna y 10 en un musculo`() {
        assertEquals(5, RingAdjustDialStep)
        assertEquals(10, RingAdjustMuscleStep)
    }

    @Test
    fun `la haptica suena al cruzar un multiplo de 5`() {
        assertTrue(ringAdjustCrossedTick(4, 5))
        assertTrue(ringAdjustCrossedTick(10, 9))
        assertTrue(ringAdjustCrossedTick(9, 10))
        assertFalse(ringAdjustCrossedTick(5, 6))
        assertFalse(ringAdjustCrossedTick(6, 9))
        assertFalse(ringAdjustCrossedTick(12, 12))
    }

    @Test
    fun `arrastrar sobre la fila afina de uno en uno`() {
        assertEquals(55, ringAdjustFineValue(start = 50, dragPx = 25f, pxPerPoint = 5f))
        assertEquals(48, ringAdjustFineValue(start = 50, dragPx = -12f, pxPerPoint = 5f))
        assertEquals(100, ringAdjustFineValue(start = 98, dragPx = 100f, pxPerPoint = 5f))
        assertEquals(0, ringAdjustFineValue(start = 3, dragPx = -100f, pxPerPoint = 5f))
        assertEquals(50, ringAdjustFineValue(start = 50, dragPx = 4f, pxPerPoint = 5f))
    }

    // ── dónde empieza un gesto ──────────────────────────────────────────────────────────────────

    @Test
    fun `solo el anillo y el liquido responden, no el centro ni las esquinas`() {
        val half = 140f
        assertFalse("centro", ringAdjustIsOnBand(0f, 0f, half))
        assertFalse("junto al centro", ringAdjustIsOnBand(30f, 20f, half))
        assertTrue("el liquido", ringAdjustIsOnBand(0f, -80f, half))
        assertTrue("el anillo", ringAdjustIsOnBand(118f, 0f, half))
        assertFalse("la esquina", ringAdjustIsOnBand(130f, 130f, half))
    }

    // ── palabras y tonos ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `las palabras de Energia siguen las bandas compartidas`() {
        assertEquals("Con mucha energía", ringAdjustEnergyWord(100))
        assertEquals("Con mucha energía", ringAdjustEnergyWord(85))
        assertEquals("Energía normal", ringAdjustEnergyWord(84))
        assertEquals("Energía normal", ringAdjustEnergyWord(70))
        assertEquals("Algo cansado", ringAdjustEnergyWord(69))
        assertEquals("Algo cansado", ringAdjustEnergyWord(50))
        assertEquals("Energía baja", ringAdjustEnergyWord(49))
        assertEquals("Energía baja", ringAdjustEnergyWord(35))
        assertEquals("Muy cansado", ringAdjustEnergyWord(34))
        assertEquals("Muy cansado", ringAdjustEnergyWord(0))
    }

    @Test
    fun `las palabras de Columna siguen las bandas compartidas`() {
        assertEquals("Espalda descansada", ringAdjustSpineWord(85))
        assertEquals("Carga normal", ringAdjustSpineWord(84))
        assertEquals("Carga normal", ringAdjustSpineWord(70))
        assertEquals("Algo cargada", ringAdjustSpineWord(69))
        assertEquals("Algo cargada", ringAdjustSpineWord(50))
        assertEquals("Cargada", ringAdjustSpineWord(49))
        assertEquals("Cargada", ringAdjustSpineWord(35))
        assertEquals("Muy cargada", ringAdjustSpineWord(34))
    }

    @Test
    fun `cada canal usa su propia palabra`() {
        assertEquals("Energía normal", ringAdjustWord(RingChannel.ENERGIA, 72))
        assertEquals("Carga normal", ringAdjustWord(RingChannel.COLUMNA, 72))
        assertEquals("en rango", ringAdjustWord(RingChannel.MUSCULOS, 72))
    }

    @Test
    fun `un musculo sigue las cinco bandas compartidas`() {
        assertEquals(RingAdjustMuscleTone.RECOVERED, ringAdjustMuscleTone(85))
        assertEquals(RingAdjustMuscleTone.NORMAL, ringAdjustMuscleTone(70))
        assertEquals(RingAdjustMuscleTone.MID, ringAdjustMuscleTone(50))
        assertEquals(RingAdjustMuscleTone.LOADED, ringAdjustMuscleTone(35))
        assertEquals(RingAdjustMuscleTone.CRITICAL, ringAdjustMuscleTone(34))
        assertEquals(0xFF43D18C, colorArgb(RingAdjustMuscleTone.RECOVERED))
        assertEquals(0xFF43D18C, colorArgb(RingAdjustMuscleTone.NORMAL))
        assertEquals(0xFFF7CF73, colorArgb(RingAdjustMuscleTone.MID))
        assertEquals(0xFFF49A6E, colorArgb(RingAdjustMuscleTone.LOADED))
        assertEquals(0xFFEF4444, colorArgb(RingAdjustMuscleTone.CRITICAL))
        assertEquals("recuperado", RingAdjustMuscleTone.RECOVERED.word)
        assertEquals("en rango", RingAdjustMuscleTone.NORMAL.word)
        assertEquals("algo cargado", RingAdjustMuscleTone.MID.word)
        assertEquals("cargado", RingAdjustMuscleTone.LOADED.word)
        assertEquals("muy cargado", RingAdjustMuscleTone.CRITICAL.word)
    }

    private fun colorArgb(tone: RingAdjustMuscleTone): Long {
        val c = tone.color
        val a = (c.alpha * 255f + 0.5f).toLong()
        val r = (c.red * 255f + 0.5f).toLong()
        val g = (c.green * 255f + 0.5f).toLong()
        val b = (c.blue * 255f + 0.5f).toLong()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `el porcentaje lleva espacio inseparable`() {
        assertEquals("72\u00A0%", ringAdjustPercent(72))
    }

    // ── el cuerpo ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `las zonas del cuerpo cubren los 13 pilares y ninguno mas`() {
        val pillars = (RingAdjustBodyData.FrontZones + RingAdjustBodyData.BackZones).map { it.pillar }.toSet()
        assertEquals(RingAdjustBodyPillars.toSet(), pillars)
        assertEquals(13, RingAdjustBodyPillars.size)
    }

    @Test
    fun `de frente se ven siete zonas y de espalda ocho, con el deltoides en las dos`() {
        assertEquals(
            listOf("Deltoides", "Pectorales", "Bíceps", "Abdomen", "Core", "Cuádriceps", "Pantorrillas"),
            RingAdjustBodyData.zonesOf(RingAdjustBodyView.FRONT).map { it.pillar },
        )
        assertEquals(
            listOf("Trapecio", "Dorsales", "Deltoides", "Tríceps", "Erectores Espinales", "Glúteos", "Isquiosurales", "Pantorrillas"),
            RingAdjustBodyData.zonesOf(RingAdjustBodyView.BACK).map { it.pillar },
        )
    }

    @Test
    fun `cada forma es un poligono dentro del lienzo`() {
        val all = RingAdjustBodyData.FrontZones + RingAdjustBodyData.BackZones
        for (zone in all) {
            assertTrue("${zone.pillar} tiene formas", zone.shapes.isNotEmpty())
            for (shape in ringAdjustWithMirrors(zone.shapes)) {
                assertTrue("${zone.pillar}: al menos 3 puntos", shape.size >= 6 && shape.size % 2 == 0)
                for (i in shape.indices step 2) {
                    assertTrue("${zone.pillar}: x dentro", shape[i] in 0f..RingAdjustBodyWidth)
                    assertTrue("${zone.pillar}: y dentro", shape[i + 1] in 0f..RingAdjustBodyHeight)
                }
                assertTrue("${zone.pillar}: con área", ringAdjustArea(shape) > 20f)
            }
        }
    }

    @Test
    fun `la silueta tambien cabe en el lienzo`() {
        val pieces = listOf(
            RingAdjustBodyData.HeadRight, RingAdjustBodyData.Neck, RingAdjustBodyData.TorsoRight,
            RingAdjustBodyData.ArmRight, RingAdjustBodyData.LegRight,
        )
        for (piece in pieces) {
            for (i in piece.indices step 2) {
                assertTrue(piece[i] in 0f..RingAdjustBodyWidth)
                assertTrue(piece[i + 1] in 0f..RingAdjustBodyHeight)
            }
        }
    }

    @Test
    fun `reflejar una forma dos veces la deja igual`() {
        val shape = RingAdjustBodyData.ArmRight
        assertTrue(shape.contentEquals(ringAdjustMirror(ringAdjustMirror(shape))))
        // Un punto del lado derecho (x = 170) pasa a x = 50.
        assertEquals(50f, ringAdjustMirror(floatArrayOf(170f, 80f))[0], 0.001f)
    }

    @Test
    fun `cerrar un contorno por su reflejo no repite los puntos del eje`() {
        val right = floatArrayOf(110f, 10f, 130f, 20f, 125f, 40f, 110f, 50f)
        val closed = ringAdjustJoinMirrored(right, dropFirst = true, dropLast = true)
        // 4 puntos de la derecha + los 2 del reflejo que no están en el eje = 6 puntos.
        assertEquals(12, closed.size)
        // El reflejo vuelve por el otro lado: (125, 40) → (95, 40), luego (130, 20) → (90, 20).
        assertEquals(95f, closed[8], 0.001f)
        assertEquals(40f, closed[9], 0.001f)
        assertEquals(90f, closed[10], 0.001f)
        assertEquals(20f, closed[11], 0.001f)
    }

    @Test
    fun `tocar el centro de una zona devuelve su pilar, de un lado y del otro`() {
        val front = RingAdjustBodyData.FrontZones
        // Cuádriceps: el muslo derecho y su reflejo.
        assertEquals("Cuádriceps", ringAdjustBodyZoneAt(front, 138f, 232f, slop = 0f))
        assertEquals("Cuádriceps", ringAdjustBodyZoneAt(front, 220f - 138f, 232f, slop = 0f))
        assertEquals("Pectorales", ringAdjustBodyZoneAt(front, 135f, 96f, slop = 0f))
        assertEquals("Deltoides", ringAdjustBodyZoneAt(front, 168f, 90f, slop = 0f))
        assertEquals("Bíceps", ringAdjustBodyZoneAt(front, 171f, 130f, slop = 0f))
        assertEquals("Pantorrillas", ringAdjustBodyZoneAt(front, 141f, 308f, slop = 0f))
        assertEquals("Core", ringAdjustBodyZoneAt(front, 143f, 146f, slop = 0f))
        // Abdomen: un bloque de los cuatro pares.
        assertEquals("Abdomen", ringAdjustBodyZoneAt(front, 120f, 126f, slop = 0f))
        val back = RingAdjustBodyData.BackZones
        assertEquals("Trapecio", ringAdjustBodyZoneAt(back, 125f, 80f, slop = 0f))
        assertEquals("Glúteos", ringAdjustBodyZoneAt(back, 132f, 196f, slop = 0f))
        assertEquals("Isquiosurales", ringAdjustBodyZoneAt(back, 138f, 250f, slop = 0f))
        assertEquals("Erectores Espinales", ringAdjustBodyZoneAt(back, 117f, 150f, slop = 0f))
        assertEquals("Tríceps", ringAdjustBodyZoneAt(back, 171f, 130f, slop = 0f))
        assertEquals("Dorsales", ringAdjustBodyZoneAt(back, 150f, 118f, slop = 0f))
    }

    @Test
    fun `una zona pequena amplia su area tactil hasta el margen`() {
        val front = RingAdjustBodyData.FrontZones
        // Un punto fuera del bíceps pero a menos de 24 unidades de él lo elige; sin margen, no.
        assertNull(ringAdjustBodyZoneAt(front, 195f, 130f, slop = 0f))
        assertEquals("Bíceps", ringAdjustBodyZoneAt(front, 195f, 130f, slop = 24f))
    }

    @Test
    fun `fuera de todo margen no se elige nada`() {
        assertNull(ringAdjustBodyZoneAt(RingAdjustBodyData.FrontZones, 10f, 10f, slop = 24f))
        assertNull(ringAdjustBodyZoneAt(RingAdjustBodyData.BackZones, 205f, 340f, slop = 24f))
    }

    @Test
    fun `entre dos zonas gana la mas cercana`() {
        val front = RingAdjustBodyData.FrontZones
        // Un punto entre el pecho y el abdomen, algo más cerca del abdomen.
        val chosen = ringAdjustBodyZoneAt(front, 118f, 117f, slop = 24f)
        assertNotNull(chosen)
        assertEquals("Abdomen", chosen)
    }

    // ── de un nombre de músculo a su pilar ───────────────────────────────────────────────────────

    @Test
    fun `los pilares se reconocen tal cual y con los nombres de otras listas`() {
        assertEquals("Pectorales", ringAdjustBodyPillarOf("Pectorales"))
        assertEquals("Pectorales", ringAdjustBodyPillarOf("Pecho"))
        assertEquals("Deltoides", ringAdjustBodyPillarOf("Hombros"))
        assertEquals("Isquiosurales", ringAdjustBodyPillarOf("Isquiotibiales"))
        assertEquals("Erectores Espinales", ringAdjustBodyPillarOf("Erectores Espinales"))
        assertEquals("Core", ringAdjustBodyPillarOf("Core"))
        assertEquals("Abdomen", ringAdjustBodyPillarOf("Abdomen"))
    }

    @Test
    fun `un musculo sin zona no tiene pilar de cuerpo`() {
        assertNull(ringAdjustBodyPillarOf("Antebrazo"))
        assertNull(ringAdjustBodyPillarOf("Aductores"))
        assertNull(ringAdjustBodyPillarOf("Cuello"))
    }

    @Test
    fun `el nombre de un pilar se lee en minusculas con la primera en mayuscula`() {
        assertEquals("Erectores espinales", ringAdjustPillarLabel("Erectores Espinales"))
        assertEquals("Cuádriceps", ringAdjustPillarLabel("Cuádriceps"))
        assertEquals("Bíceps", ringAdjustPillarLabel("Bíceps"))
    }

    @Test
    fun `cada pilar sabe por que lado del cuerpo se ve`() {
        assertEquals(RingAdjustBodyView.BACK, ringAdjustPreferredView("Trapecio"))
        assertEquals(RingAdjustBodyView.BACK, ringAdjustPreferredView("Isquiosurales"))
        assertEquals(RingAdjustBodyView.FRONT, ringAdjustPreferredView("Pectorales"))
        assertEquals(RingAdjustBodyView.FRONT, ringAdjustPreferredView("Cuádriceps"))
        assertEquals(RingAdjustBodyView.FRONT, ringAdjustPreferredView("Pantorrillas"))
    }

    @Test
    fun `el nombre de cada canal`() {
        assertEquals("Energía", ringAdjustChannelName(RingChannel.ENERGIA))
        assertEquals("Columna", ringAdjustChannelName(RingChannel.COLUMNA))
        assertEquals("Músculos", ringAdjustChannelName(RingChannel.MUSCULOS))
    }
}
