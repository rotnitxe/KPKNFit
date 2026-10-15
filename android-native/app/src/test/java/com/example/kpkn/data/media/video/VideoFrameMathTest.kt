package com.example.kpkn.data.media.video

import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameMathTest {
    /** Aplica a (x, y) la parte 2×2 de una matriz 4×4 columna-mayor (z = 0, w = 1). */
    private fun transformed(matrix: FloatArray, x: Float, y: Float): Pair<Float, Float> =
        Pair(matrix[0] * x + matrix[4] * y + matrix[12], matrix[1] * x + matrix[5] * y + matrix[13])

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: Pair<Float, Float>) {
        assertEquals("x", expectedX, actual.first, 1e-6f)
        assertEquals("y", expectedY, actual.second, 1e-6f)
    }

    // ---- Tamaños

    @Test
    fun portraitFullHdGivesEvenOutputOf406By720() {
        // 1920×1080 con giro 90 se ve de 1080×1920; a 720 de lado largo el corto sale a 405, que es impar → el par más cercano.
        val display = VideoFrameMath.displaySize(1920, 1080, 90)
        assertEquals(VideoFrameMath.Size(1080, 1920), display)
        val out = VideoFrameMath.outputSize(display.width, display.height, 720)
        assertEquals(VideoFrameMath.Size(406, 720), out)
        assertEquals(0, out.width % 2)
        assertEquals(0, out.height % 2)
    }

    @Test
    fun landscapeKeepsTheLongSideOnTheTarget() {
        assertEquals(VideoFrameMath.Size(720, 406), VideoFrameMath.outputSize(1920, 1080, 720))
        assertEquals(VideoFrameMath.Size(720, 406), VideoFrameMath.outputSize(3840, 2160, 720))
        assertEquals(VideoFrameMath.Size(640, 480), VideoFrameMath.outputSize(1280, 960, 640))
    }

    @Test
    fun exactEvenShortSideIsKept() {
        assertEquals(VideoFrameMath.Size(720, 404), VideoFrameMath.outputSize(1800, 1010, 720))
        assertEquals(VideoFrameMath.Size(720, 720), VideoFrameMath.outputSize(1000, 1000, 720))
    }

    @Test
    fun outputNeverUpscales() {
        assertEquals(VideoFrameMath.Size(320, 240), VideoFrameMath.outputSize(320, 240, 720))
        assertEquals(VideoFrameMath.Size(240, 320), VideoFrameMath.outputSize(240, 320, 320))
        assertEquals(VideoFrameMath.Size(320, 240), VideoFrameMath.outputSize(320, 240, 0))
    }

    @Test
    fun rotationsAreNormalizedToQuarterTurns() {
        assertEquals(0, VideoFrameMath.normalizeRotation(0))
        assertEquals(90, VideoFrameMath.normalizeRotation(90))
        assertEquals(180, VideoFrameMath.normalizeRotation(-180))
        assertEquals(270, VideoFrameMath.normalizeRotation(-90))
        assertEquals(0, VideoFrameMath.normalizeRotation(360))
        assertEquals(90, VideoFrameMath.normalizeRotation(450))
        assertEquals(90, VideoFrameMath.normalizeRotation(89))
        assertEquals(0, VideoFrameMath.normalizeRotation(44))
    }

    @Test
    fun sidewaysRotationsSwapTheDisplaySize() {
        assertEquals(VideoFrameMath.Size(320, 240), VideoFrameMath.displaySize(320, 240, 0))
        assertEquals(VideoFrameMath.Size(240, 320), VideoFrameMath.displaySize(320, 240, 90))
        assertEquals(VideoFrameMath.Size(320, 240), VideoFrameMath.displaySize(320, 240, 180))
        assertEquals(VideoFrameMath.Size(240, 320), VideoFrameMath.displaySize(320, 240, 270))
    }

    // ---- Luma

    @Test
    fun lumaOfPureColors() {
        assertEquals(0, VideoFrameMath.luma(0, 0, 0))
        assertEquals(255, VideoFrameMath.luma(255, 255, 255))
        assertEquals(76, VideoFrameMath.luma(255, 0, 0))
        assertEquals(149, VideoFrameMath.luma(0, 255, 0))
        assertEquals(28, VideoFrameMath.luma(0, 0, 255))
        assertEquals(128, VideoFrameMath.luma(128, 128, 128))
    }

    @Test
    fun lumaFromRgbaReadsBytesAsUnsignedAndIgnoresAlpha() {
        val rgba = byteArrayOf(
            0, 0, 0, 255.toByte(), // negro
            255.toByte(), 255.toByte(), 255.toByte(), 0, // blanco con alfa 0
            255.toByte(), 0, 0, 255.toByte(), // rojo
            0, 255.toByte(), 0, 255.toByte(), // verde
            0, 0, 255.toByte(), 255.toByte(), // azul
        )
        val luma = ByteArray(5)
        VideoFrameMath.lumaFromRgba(rgba, 5, luma)
        assertArrayEquals(byteArrayOf(0, 255.toByte(), 76, 149.toByte(), 28), luma)
    }

    // ---- Giro: matriz del vértice

    @Test
    fun vertexMatrixRotatesCornersClockwiseWithoutFlip() {
        // Esquinas en coordenadas de dispositivo (Y hacia arriba): arriba-izq (−1, 1), arriba-der (1, 1), abajo-der (1, −1), abajo-izq (−1, −1).
        val quarter = VideoFrameMath.vertexMatrix(90, flipY = false)
        assertPoint(1f, 1f, transformed(quarter, -1f, 1f)) // arriba-izq → arriba-der
        assertPoint(1f, -1f, transformed(quarter, 1f, 1f)) // arriba-der → abajo-der
        assertPoint(-1f, -1f, transformed(quarter, 1f, -1f)) // abajo-der → abajo-izq
        assertPoint(-1f, 1f, transformed(quarter, -1f, -1f)) // abajo-izq → arriba-izq
        val half = VideoFrameMath.vertexMatrix(180, flipY = false)
        assertPoint(1f, -1f, transformed(half, -1f, 1f))
        val threeQuarters = VideoFrameMath.vertexMatrix(270, flipY = false)
        assertPoint(-1f, -1f, transformed(threeQuarters, -1f, 1f)) // arriba-izq → abajo-izq
        assertPoint(-1f, 1f, transformed(threeQuarters, 1f, 1f)) // arriba-der → arriba-izq
        val none = VideoFrameMath.vertexMatrix(0, flipY = false)
        assertPoint(-1f, 1f, transformed(none, -1f, 1f))
    }

    @Test
    fun vertexMatrixWithFlipPutsTheTopRowAtTheBottomOfTheReadback() {
        // glReadPixels lee de abajo arriba: la fila de arriba de la imagen debe quedar en y = −1.
        val upright = VideoFrameMath.vertexMatrix(0, flipY = true)
        assertPoint(-1f, -1f, transformed(upright, -1f, 1f))
        assertPoint(1f, 1f, transformed(upright, 1f, -1f))
        // Con 90° horario, el vídeo girado se ve con su esquina de arriba-izq saliendo de la de abajo-izq del codificado, y la
        // primera fila del buffer (y = −1) es la de arriba de ese vídeo girado.
        val quarter = VideoFrameMath.vertexMatrix(90, flipY = true)
        assertPoint(-1f, -1f, transformed(quarter, -1f, -1f)) // abajo-izq codificado → arriba-izq girado
        assertPoint(1f, -1f, transformed(quarter, -1f, 1f)) // arriba-izq codificado → arriba-der girado
        assertPoint(1f, 1f, transformed(quarter, 1f, 1f)) // arriba-der codificado → abajo-der girado
        assertPoint(-1f, 1f, transformed(quarter, 1f, -1f)) // abajo-der codificado → abajo-izq girado
    }

    @Test
    fun vertexMatrixIsAProperHomogeneousMatrix() {
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            for (flip in booleanArrayOf(false, true)) {
                val m = VideoFrameMath.vertexMatrix(rotation, flip)
                assertEquals(16, m.size)
                assertEquals(1f, m[10], 0f)
                assertEquals(1f, m[15], 0f)
                assertEquals(0f, m[12], 0f)
                assertEquals(0f, m[13], 0f)
                // Una rotación (con o sin volteo) conserva la longitud: determinante ±1.
                val determinant = m[0] * m[5] - m[4] * m[1]
                assertEquals(if (flip) -1f else 1f, determinant, 1e-6f)
            }
        }
    }

    @Test
    fun rotatePointFollowsTheClockwiseTurnOfTheImage() {
        val p = DoubleArray(2)
        // Imagen codificada de 320×240; el punto (100, 50) está a 100 px del borde izquierdo y a 50 del superior.
        VideoFrameMath.rotatePoint(100.0, 50.0, 320, 240, 0, p)
        assertEquals(100.0, p[0], 1e-9)
        assertEquals(50.0, p[1], 1e-9)
        VideoFrameMath.rotatePoint(100.0, 50.0, 320, 240, 90, p) // gira a 240 de ancho por 320 de alto
        assertEquals(190.0, p[0], 1e-9) // 240 − 50
        assertEquals(100.0, p[1], 1e-9)
        VideoFrameMath.rotatePoint(100.0, 50.0, 320, 240, 180, p)
        assertEquals(220.0, p[0], 1e-9)
        assertEquals(190.0, p[1], 1e-9)
        VideoFrameMath.rotatePoint(100.0, 50.0, 320, 240, 270, p)
        assertEquals(50.0, p[0], 1e-9)
        assertEquals(220.0, p[1], 1e-9) // 320 − 100
        // Las cuatro esquinas del codificado caen en las del girado.
        VideoFrameMath.rotatePoint(0.0, 0.0, 320, 240, 90, p) // arriba-izq → arriba-der de 240×320
        assertEquals(240.0, p[0], 1e-9)
        assertEquals(0.0, p[1], 1e-9)
    }

    // ---- Giro que el códec ya incorporó

    private fun textureMatrix(sx: Float, sy: Float, swapped: Int): FloatArray {
        // Matrices de SurfaceTexture: sin giro (x = s, y = 1 − t); 90° (x = 1 − t, y = 1 − s); 180° (x = 1 − s, y = t); 270° (x = t, y = s).
        val m = FloatArray(16)
        m[10] = 1f
        m[15] = 1f
        when (swapped) {
            0 -> { m[0] = sx; m[5] = -sy; m[13] = 1f }
            90 -> { m[1] = -sy; m[4] = -sx; m[12] = 1f; m[13] = 1f }
            180 -> { m[0] = -sx; m[5] = sy; m[12] = 1f }
            270 -> { m[1] = sy; m[4] = sx }
        }
        return m
    }

    @Test
    fun bakedRotationIsReadFromTheTextureMatrix() {
        for (turn in intArrayOf(0, 90, 180, 270)) {
            assertEquals("giro $turn", turn, VideoFrameMath.bakedRotationOf(textureMatrix(1f, 1f, turn)))
            // Con recorte (el decodificador alinea a 16 y el SurfaceTexture recorta) la lectura es la misma.
            assertEquals("giro $turn recortado", turn, VideoFrameMath.bakedRotationOf(textureMatrix(0.995f, 0.9926f, turn)))
        }
    }

    // ---- Tiempos

    @Test
    fun nonIncreasingPtsAreRepairedToThePreviousPlusOne() {
        assertEquals(0L, VideoFrameMath.repairedPts(0L, Long.MIN_VALUE))
        assertEquals(-5L, VideoFrameMath.repairedPts(-5L, Long.MIN_VALUE))
        assertEquals(33_333L, VideoFrameMath.repairedPts(33_333L, 0L))
        assertEquals(1_001L, VideoFrameMath.repairedPts(1_000L, 1_000L)) // repetido
        assertEquals(1_001L, VideoFrameMath.repairedPts(900L, 1_000L)) // hacia atrás
    }

    @Test
    fun nominalFpsIsTheMedianGapOfTheFirstSamplesInAnyOrder() {
        // 60 muestras a 30 fps, con el orden del contenedor cambiado (como con B-frames).
        val pts = LongArray(60) { it * 33_333L }
        pts[10] = 11 * 33_333L
        pts[11] = 10 * 33_333L
        pts[30] = 31 * 33_333L
        pts[31] = 30 * 33_333L
        assertEquals(30.0f, VideoFrameMath.nominalFpsFromPts(pts, 60)!!, 0.01f)
        // 60 fps con un hueco aislado: la mediana lo ignora.
        val fast = LongArray(60) { it * 16_667L }
        fast[40] += 20_000L
        fast.sort()
        assertEquals(60.0f, VideoFrameMath.nominalFpsFromPts(fast, 60)!!, 0.05f)
        assertNull(VideoFrameMath.nominalFpsFromPts(longArrayOf(5L), 1))
        assertNull(VideoFrameMath.nominalFpsFromPts(longArrayOf(7L, 7L, 7L), 3))
    }

    @Test
    fun retrieverPtsComeFromTheIndexAndTheNominalFps() {
        assertEquals(0L, VideoFrameMath.indexedPtsUs(0, 30f))
        assertEquals(33_333L, VideoFrameMath.indexedPtsUs(1, 30f))
        assertEquals(1_000_000L, VideoFrameMath.indexedPtsUs(30, 30f))
        assertEquals(0, VideoFrameMath.firstIndexAtOrAfter(0L, 30f))
        assertEquals(30, VideoFrameMath.firstIndexAtOrAfter(1_000_000L, 30f))
        assertEquals(31, VideoFrameMath.firstIndexAtOrAfter(1_000_001L, 30f))
        val index = VideoFrameMath.firstIndexAtOrAfter(2_500_000L, 29.97f)
        assertTrue(VideoFrameMath.indexedPtsUs(index, 29.97f) >= 2_500_000L)
        assertTrue(VideoFrameMath.indexedPtsUs(index - 1, 29.97f) < 2_500_000L)
    }

    // ---- Pool

    @Test
    fun poolRotatesFourDirectNativeOrderBuffers() {
        // Con un Channel(2) llegan a estar vivos 2 + 2 fotogramas: el pool no puede tener menos de cuatro huecos (VT4c).
        assertTrue(FramePool.SLOTS >= 2 + 2)
        val pool = FramePool(4, 2)
        val taken = List(FramePool.SLOTS) { pool.nextSlot() }
        assertEquals((0 until FramePool.SLOTS).toSet(), taken.toSet())
        val first = taken[0]
        val second = taken[1]
        assertEquals(first, pool.nextSlot()) // da la vuelta
        val buffer = pool.rgba(first)
        assertTrue(buffer.isDirect)
        assertEquals(ByteOrder.nativeOrder(), buffer.order())
        assertEquals(4 * 2 * 4, buffer.capacity())
        assertEquals(0, buffer.position())
        assertNotSame(pool.rgba(first), pool.rgba(second))
        assertSame(pool.rgba(first), pool.rgba(first))
        assertEquals(8, pool.luma(first).size)
        assertSame(pool.luma(first), pool.luma(first))
    }

    @Test
    fun poolFillsLumaFromTheRgbaBuffer() {
        val pool = FramePool(2, 2)
        val slot = pool.nextSlot()
        val rgba = pool.rgba(slot)
        rgba.put(byteArrayOf(255.toByte(), 0, 0, 255.toByte()))
        rgba.put(byteArrayOf(0, 255.toByte(), 0, 255.toByte()))
        rgba.put(byteArrayOf(0, 0, 255.toByte(), 255.toByte()))
        rgba.put(byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()))
        val luma = pool.fillLuma(slot)
        assertArrayEquals(byteArrayOf(76, 149.toByte(), 28, 255.toByte()), luma)
        assertNotNull(pool.rgba(slot))
        assertEquals(0, pool.rgba(slot).position())
    }
}
