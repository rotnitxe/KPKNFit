package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kpkn.domain.onboarding.MuscleSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Los trazos de los músculos son datos: estas pruebas leen cada cadena de path con el mismo `PathParser` que la
 * app y comprueban que existen los doce, que se pueden leer, que cada región cabe en su encuadre (así el relleno
 * nunca se corta) y que cada vista es la que dice el contrato (espalda para espalda, trapecio, tríceps, glúteos,
 * isquios y pantorrillas).
 */
class MuscleArtTest {

    private class Bounds {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var count = 0

        fun add(x: Float, y: Float) {
            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
            count++
        }
    }

    /** Límites reales del trazo: extremos y puntos muestreados de cada curva (no solo los puntos de control). */
    private fun boundsOf(d: String): Bounds {
        val nodes = PathParser().parsePathString(d).toNodes()
        val b = Bounds()
        var cx = 0f
        var cy = 0f
        var sx = 0f
        var sy = 0f
        for (n in nodes) {
            when (n) {
                is PathNode.MoveTo -> { cx = n.x; cy = n.y; sx = cx; sy = cy; b.add(cx, cy) }
                is PathNode.LineTo -> { cx = n.x; cy = n.y; b.add(cx, cy) }
                is PathNode.CurveTo -> {
                    for (i in 1..12) {
                        val t = i / 12f
                        val m = 1f - t
                        val x = m * m * m * cx + 3 * m * m * t * n.x1 + 3 * m * t * t * n.x2 + t * t * t * n.x3
                        val y = m * m * m * cy + 3 * m * m * t * n.y1 + 3 * m * t * t * n.y2 + t * t * t * n.y3
                        b.add(x, y)
                    }
                    cx = n.x3
                    cy = n.y3
                }
                PathNode.Close -> { cx = sx; cy = sy }
                else -> fail("comando que los datos no deberían usar: $n")
            }
        }
        return b
    }

    @Test
    fun `hay dibujo para los doce musculos`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            assertTrue("${m.name} sin región", spec.region.isNotBlank())
            assertTrue("${m.name} sin silueta de contexto ni fibras", spec.detail.isNotEmpty() || spec.context.isNotEmpty())
        }
        assertEquals(12, MuscleSymbol.entries.size)
    }

    @Test
    fun `todas las cadenas de path se leen y traen puntos`() {
        val all = mutableListOf(MuscleArtPaths.OUTLINE, MuscleArtPaths.HEAD)
        for (m in MuscleSymbol.entries) {
            val s = MuscleArt.spec(m)
            all += s.region
            all += s.detail
            all += s.context
        }
        for (d in all) {
            val b = boundsOf(d)
            assertTrue("sin puntos: ${d.take(40)}", b.count >= 2)
            assertTrue("coordenadas no finitas en ${d.take(40)}", b.minX.isFinite() && b.maxX.isFinite() && b.minY.isFinite() && b.maxY.isFinite())
        }
    }

    @Test
    fun `cada region cabe dentro de su encuadre con aire para el borde`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val w = spec.window
            val b = boundsOf(spec.region)
            val pad = w.size * 0.04f
            val tag = m.name
            assertTrue("$tag se sale por la izquierda (${b.minX} < ${w.left + pad})", b.minX >= w.left + pad)
            assertTrue("$tag se sale por la derecha (${b.maxX} > ${w.right - pad})", b.maxX <= w.right - pad)
            assertTrue("$tag se sale por arriba (${b.minY} < ${w.top + pad})", b.minY >= w.top + pad)
            assertTrue("$tag se sale por abajo (${b.maxY} > ${w.bottom - pad})", b.maxY <= w.bottom - pad)
        }
    }

    @Test
    fun `la region ocupa una parte razonable del encuadre`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val b = boundsOf(spec.region)
            val w = spec.window
            val fillW = (b.maxX - b.minX) / w.size
            val fillH = (b.maxY - b.minY) / w.size
            assertTrue("${m.name} muy pequeño ($fillW × $fillH)", fillW >= 0.18f && fillH >= 0.3f)
            assertTrue("${m.name} desbordado ($fillW × $fillH)", fillW <= 0.96f && fillH <= 0.96f)
        }
    }

    @Test
    fun `el encuadre esta dentro del cuerpo y centrado en el musculo`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val w = spec.window
            val b = boundsOf(spec.region)
            assertTrue("${m.name} encuadre sobre la cabeza", w.top >= 20f)
            assertTrue("${m.name} encuadre bajo los pies", w.bottom <= MuscleArt.BODY_HEIGHT + 10f)
            val cx = (b.minX + b.maxX) / 2f
            val cy = (b.minY + b.maxY) / 2f
            assertTrue("${m.name} descentrado en x: región $cx, encuadre ${w.cx}", kotlin.math.abs(cx - w.cx) <= w.size * 0.22f)
            assertTrue("${m.name} descentrado en y: región $cy, encuadre ${w.cy}", kotlin.math.abs(cy - w.cy) <= w.size * 0.22f)
        }
    }

    @Test
    fun `la espalda se ve de espalda y el resto de frente`() {
        val back = setOf(
            MuscleSymbol.BACK, MuscleSymbol.TRAPS, MuscleSymbol.TRICEPS,
            MuscleSymbol.GLUTES, MuscleSymbol.HAMSTRINGS, MuscleSymbol.CALVES,
        )
        for (m in MuscleSymbol.entries) {
            val expected = if (m in back) BodyView.BACK else BodyView.FRONT
            assertEquals(m.name, expected, MuscleArt.spec(m).view)
        }
    }

    @Test
    fun `la silueta mide lo que dice y su cabeza queda sobre el torso`() {
        val outline = boundsOf(MuscleArtPaths.OUTLINE)
        val head = boundsOf(MuscleArtPaths.HEAD)
        assertTrue("el contorno llega a los pies", outline.maxY in 395f..405f)
        assertTrue("el contorno es simétrico", kotlin.math.abs(outline.minX + outline.maxX) < 1f)
        assertTrue("la cabeza mide ≈ 50", head.maxY - head.minY in 40f..52f)
        assertTrue("la cabeza está arriba", head.minY < 10f && head.maxY <= 50f)
        assertTrue("el cuello arranca bajo la mandíbula", outline.minY in 40f..48f)
    }
}
