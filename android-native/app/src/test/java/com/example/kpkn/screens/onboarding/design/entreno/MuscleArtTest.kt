package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kpkn.domain.onboarding.MuscleSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Los trazos de los músculos son datos: estas pruebas leen cada cadena de path con el mismo `PathParser` que la app y
 * comprueban que existen los once, que se pueden leer, que cada músculo lleva SU encuadre, centrado en él, con el protagonista
 * ocupando al menos el 40 % de la celda (legible a 60 dp), que las familias comparten vista y marcas, que el gesto del brazo
 * llena la celda a lo alto, que la contracción conserva el área, se acorta a lo largo de las fibras y deja quieto su origen.
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

    /** Las familias del contrato: comparten vista, marcas y silueta (no encuadre). */
    private val families: Map<MuscleFamily, Set<MuscleSymbol>> = mapOf(
        MuscleFamily.TORSO_FRONT to setOf(MuscleSymbol.CHEST, MuscleSymbol.SHOULDERS, MuscleSymbol.ABS),
        MuscleFamily.TORSO_BACK to setOf(MuscleSymbol.BACK),
        MuscleFamily.ARM to setOf(MuscleSymbol.ARMS, MuscleSymbol.FOREARMS),
        MuscleFamily.LEG_FRONT to setOf(MuscleSymbol.QUADS),
        MuscleFamily.LEG_BACK to setOf(MuscleSymbol.GLUTES, MuscleSymbol.HAMSTRINGS, MuscleSymbol.CALVES),
    )

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

    /** Un polígono por subtrazo (cada M abre uno), con las curvas muestreadas. */
    private fun polygonsOf(d: String): List<List<Pair<Float, Float>>> {
        val polys = mutableListOf<List<Pair<Float, Float>>>()
        var cur = mutableListOf<Pair<Float, Float>>()
        var cx = 0f
        var cy = 0f
        var sx = 0f
        var sy = 0f
        for (n in PathParser().parsePathString(d).toNodes()) {
            when (n) {
                is PathNode.MoveTo -> {
                    if (cur.isNotEmpty()) polys.add(cur)
                    cur = mutableListOf()
                    cx = n.x
                    cy = n.y
                    sx = cx
                    sy = cy
                    cur.add(cx to cy)
                }
                is PathNode.LineTo -> {
                    cx = n.x
                    cy = n.y
                    cur.add(cx to cy)
                }
                is PathNode.CurveTo -> {
                    for (i in 1..12) {
                        val t = i / 12f
                        val m = 1f - t
                        val x = m * m * m * cx + 3 * m * m * t * n.x1 + 3 * m * t * t * n.x2 + t * t * t * n.x3
                        val y = m * m * m * cy + 3 * m * m * t * n.y1 + 3 * m * t * t * n.y2 + t * t * t * n.y3
                        cur.add(x to y)
                    }
                    cx = n.x3
                    cy = n.y3
                }
                PathNode.Close -> { cx = sx; cy = sy }
                else -> fail("comando que los datos no deberían usar: $n")
            }
        }
        if (cur.isNotEmpty()) polys.add(cur)
        return polys
    }

    /** Área total de [polys] (suma de las áreas de cada subtrazo), con [m] aplicado si lo hay. */
    private fun areaOf(polys: List<List<Pair<Float, Float>>>, m: Matrix?): Double {
        var total = 0.0
        for (poly in polys) {
            val pts = poly.map { p -> applied(m, p.first, p.second) }
            var twice = 0.0
            for (i in pts.indices) {
                val (x1, y1) = pts[i]
                val (x2, y2) = pts[(i + 1) % pts.size]
                twice += x1.toDouble() * y2 - x2.toDouble() * y1
            }
            total += abs(twice) / 2.0
        }
        return total
    }

    /** El punto (x, y) tras la transformación [m] (columna mayor, como `Matrix`); sin [m], el mismo punto. */
    private fun applied(m: Matrix?, x: Float, y: Float): Pair<Float, Float> {
        if (m == null) return x to y
        val v = m.values
        return (v[0] * x + v[4] * y + v[12]) to (v[1] * x + v[5] * y + v[13])
    }

    @Test
    fun `hay dibujo para los diez musculos`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            assertTrue("${m.name} sin región", spec.region.isNotBlank())
            assertTrue("${m.name} sin silueta de contexto ni fibras", spec.detail.isNotEmpty() || spec.context.isNotEmpty())
        }
        assertEquals(10, MuscleSymbol.entries.size)
    }

    @Test
    fun `todas las cadenas de path se leen y traen puntos`() {
        val all = mutableListOf(MuscleArtPaths.OUTLINE, MuscleArtPaths.HEAD)
        for (m in MuscleSymbol.entries) {
            val s = MuscleArt.spec(m)
            all += s.region
            all += s.detail
            s.outline?.let { all += it }
            s.accentPart?.let { all += it }
            s.moving?.let { all += it }
        }
        for (f in MuscleFamily.entries) all += f.context
        for (d in all) {
            val b = boundsOf(d)
            assertTrue("sin puntos: ${d.take(40)}", b.count >= 2)
            assertTrue("coordenadas no finitas en ${d.take(40)}", b.minX.isFinite() && b.maxX.isFinite() && b.minY.isFinite() && b.maxY.isFinite())
        }
    }

    @Test
    fun `cada region cabe dentro de su encuadre con un 8 por ciento de aire`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val w = spec.window
            val b = boundsOf(spec.region)
            val pad = w.size * 0.08f
            val tag = m.name
            assertTrue("$tag se sale por la izquierda (${b.minX} < ${w.left + pad})", b.minX >= w.left + pad)
            assertTrue("$tag se sale por la derecha (${b.maxX} > ${w.right - pad})", b.maxX <= w.right - pad)
            assertTrue("$tag se sale por arriba (${b.minY} < ${w.top + pad})", b.minY >= w.top + pad)
            assertTrue("$tag se sale por abajo (${b.maxY} > ${w.bottom - pad})", b.maxY <= w.bottom - pad)
        }
    }

    @Test
    fun `el protagonista ocupa al menos el 40 por ciento del ancho o del alto de su encuadre`() {
        // Legible a 60 dp: en cada celda el músculo es el protagonista, no una astilla dentro de la figura.
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val b = boundsOf(spec.region)
            val w = spec.window
            val fill = maxOf((b.maxX - b.minX) / w.size, (b.maxY - b.minY) / w.size)
            assertTrue("${m.name} muy pequeño en su celda ($fill)", fill >= 0.4f)
            assertTrue("${m.name} desbordado en su celda ($fill)", fill <= 0.96f)
        }
    }

    @Test
    fun `cada musculo va centrado en su region y tiene su propio encuadre`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val w = spec.window
            val b = boundsOf(spec.region)
            val cx = (b.minX + b.maxX) / 2f
            val cy = (b.minY + b.maxY) / 2f
            assertTrue("${m.name} descentrado en x: región $cx, encuadre ${w.cx}", abs(cx - w.cx) <= w.size * 0.1f)
            assertTrue("${m.name} descentrado en y: región $cy, encuadre ${w.cy}", abs(cy - w.cy) <= w.size * 0.1f)
        }
        // Cada músculo lleva su encuadre: no todos comparten el mismo.
        val windows = MuscleSymbol.entries.map { MuscleArt.spec(it).window }.map { Triple(it.cx, it.cy, it.size) }.toSet()
        assertTrue("los encuadres deberían ser propios de cada músculo (${windows.size} distintos)", windows.size >= 9)
    }

    @Test
    fun `las familias comparten vista, marcas y silueta, y el brazo mezcla vista por su gesto`() {
        for ((family, members) in families) {
            for (m in members) {
                val spec = MuscleArt.spec(m)
                assertEquals(m.name, family, spec.family)
                assertEquals(m.name, family.context, spec.context)
            }
        }
        for (family in families.keys) {
            if (family == MuscleFamily.ARM) continue
            val views = families.getValue(family).map { MuscleArt.spec(it).view }.toSet()
            assertEquals("${family.name} debe compartir vista", 1, views.size)
        }
        // El brazo: Brazos y antebrazo de frente, con su gesto (el doble bíceps de perfil y el puño).
        assertEquals(BodyView.FRONT, MuscleArt.spec(MuscleSymbol.ARMS).view)
        assertEquals(BodyView.FRONT, MuscleArt.spec(MuscleSymbol.FOREARMS).view)
        assertSame(MuscleFamily.ARM.context, MuscleArt.spec(MuscleSymbol.ARMS).context)
    }

    @Test
    fun `el gesto del brazo llena la celda a lo alto`() {
        // Brazos (doble bíceps con el puño arriba) y antebrazo con puño: el gesto (contorno y pieza móvil) ocupa la celda.
        for (m in listOf(MuscleSymbol.ARMS, MuscleSymbol.FOREARMS)) {
            val spec = MuscleArt.spec(m)
            val outline = checkNotNull(spec.outline) { "${m.name} sin contorno propio" }
            val b = boundsOf(listOfNotNull(outline, spec.moving).joinToString(" "))
            val high = (b.maxY - b.minY) / spec.window.size
            assertTrue("${m.name} no llena la celda a lo alto ($high)", high >= 0.6f)
        }
    }

    @Test
    fun `al contraerse la muneca flexiona y los omoplatos se juntan hacia la columna`() {
        val rest = Matrix()
        val full = Matrix()
        // Antebrazo: la mano (borde de arriba, x 44, y 7,6) gira con la muñeca (10°) y la barra se aprieta 1 u hacia el antebrazo.
        val forearm = MuscleArt.spec(MuscleSymbol.FOREARMS)
        MuscleArt.motionInto(forearm, 0f, rest)
        assertEquals(44f, applied(rest, 44f, 7.6f).first, 0.001f)
        MuscleArt.motionInto(forearm, 1f, full)
        val (fx, fy) = applied(full, 44f, 7.6f)
        assertTrue("la mano gira y aprieta ${fx - 44f}", (fx - 44f) in -3.5f..-1.5f)
        assertTrue("la mano no sube ni baja de golpe ($fy)", abs(fy - 7.6f) < 2f)
        // Espalda: un omóplato (x 25) se acerca a la columna unas 3 unidades; la columna (x 0) no se mueve.
        val back = MuscleArt.spec(MuscleSymbol.BACK)
        MuscleArt.motionInto(back, 1f, full)
        val (sx, _) = applied(full, 25f, 108f)
        assertTrue("el omóplato se acerca ${25f - sx}", (25f - sx) in 2.5f..4f)
        assertEquals(0f, applied(full, 0f, 110f).first, 0.001f)
        // Brazos: el codo cierra 12° alrededor del codo (41, 37): el antebrazo, en (46, 14), se va unas 5 u hacia el hombro.
        val arms = MuscleArt.spec(MuscleSymbol.ARMS)
        MuscleArt.motionInto(arms, 0f, rest)
        assertEquals(46f, applied(rest, 46f, 14f).first, 0.001f)
        MuscleArt.motionInto(arms, 1f, full)
        val (ex, _) = applied(full, 46f, 14f)
        assertTrue("el codo cierra ${ex - 46f}", (ex - 46f) in -5.5f..-4.3f)
        // Su parte de acento entero (el bíceps) está dentro de la región de Brazos.
        assertTrue(arms.region.contains(checkNotNull(arms.accentPart)))
    }

    @Test
    fun `la espalda se ve de espalda y el resto de frente`() {
        val back = setOf(
            MuscleSymbol.BACK,
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
        assertTrue("el contorno es simétrico", abs(outline.minX + outline.maxX) < 1f)
        assertTrue("la cabeza mide ≈ 50", head.maxY - head.minY in 40f..52f)
        assertTrue("la cabeza está arriba", head.minY < 10f && head.maxY <= 50f)
        assertTrue("el cuello arranca bajo la mandíbula", outline.minY in 40f..48f)
    }

    @Test
    fun `la contraccion conserva el area del musculo dentro de un 12 por ciento`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val polys = polygonsOf(spec.region)
            val rest = areaOf(polys, null)
            val full = Matrix()
            MuscleArt.contractInto(spec, 1f, full)
            val ratio = areaOf(polys, full) / rest
            assertTrue("${m.name} cambia de área al contraerse (×${"%.3f".format(ratio)})", abs(ratio - 1.0) <= 0.12)
        }
    }

    @Test
    fun `la contraccion acorta 6 por ciento en las fibras, ensancha 5 por ciento en perpendicular y deja quieto el origen`() {
        for (m in MuscleSymbol.entries) {
            val spec = MuscleArt.spec(m)
            val full = Matrix()
            MuscleArt.contractInto(spec, 1f, full)
            val th = spec.fiberAngleDeg * DEG
            val c = cos(th)
            val s = sin(th)
            val o = spec.origin
            // Un punto a 40 unidades del origen en la dirección de las fibras: queda a 37,6 (−6 %), sin desviarse.
            val (ax, ay) = applied(full, o.x + 40f * c, o.y + 40f * s)
            val along = (ax - o.x) * c + (ay - o.y) * s
            val across = -(ax - o.x) * s + (ay - o.y) * c
            assertTrue("${m.name}: a lo largo de las fibras $along, se esperaba 37,6", abs(along - 37.6f) <= 0.05f && abs(across) <= 0.05f)
            // Y uno en perpendicular: queda a 42 (+5 %).
            val (bx, by) = applied(full, o.x - 40f * s, o.y + 40f * c)
            val along2 = (bx - o.x) * c + (by - o.y) * s
            val across2 = -(bx - o.x) * s + (by - o.y) * c
            assertTrue("${m.name}: en perpendicular $across2, se esperaba 42", abs(across2 - 42f) <= 0.05f && abs(along2) <= 0.05f)
            // El origen no se mueve.
            val (ox, oy) = applied(full, o.x, o.y)
            assertTrue("${m.name}: el origen se movió", abs(ox - o.x) <= 0.001f && abs(oy - o.y) <= 0.001f)
        }
    }
}
