package com.example.kpkn.screens.workout.technique

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureMetadataRecorderTest {

    private val g = 9.81f

    @Test
    fun rollForFrameDegMandatoryTests() {
        val rad10 = Math.toRadians(10.0)
        val sin10 = (g * sin(rad10)).toFloat()
        val cos10 = (g * cos(rad10)).toFloat()

        // 1. (0, g) con ROTATION_0 -> 0
        val r1 = rollForFrameDeg(0f, g, Surface.ROTATION_0)
        assertEquals(0f, r1!!, 0.01f)

        // 2. (g * sin 10°, g * cos 10°) con ROTATION_0 -> +10
        val r2 = rollForFrameDeg(sin10, cos10, Surface.ROTATION_0)
        assertEquals(10f, r2!!, 0.05f)

        // 3. (-g * sin 10°, g * cos 10°) -> -10
        val r3 = rollForFrameDeg(-sin10, cos10, Surface.ROTATION_0)
        assertEquals(-10f, r3!!, 0.05f)

        // 4. (g, 0) con ROTATION_90 -> 0
        val r4 = rollForFrameDeg(g, 0f, Surface.ROTATION_90)
        assertEquals(0f, r4!!, 0.01f)

        // 5. (-g, 0) con ROTATION_270 -> 0
        val r5 = rollForFrameDeg(-g, 0f, Surface.ROTATION_270)
        assertEquals(0f, r5!!, 0.01f)

        // 6. (0, -g) con ROTATION_180 -> 0
        val r6 = rollForFrameDeg(0f, -g, Surface.ROTATION_180)
        assertEquals(0f, r6!!, 0.01f)

        // 7. (0, -g) con ROTATION_0 -> null
        val r7 = rollForFrameDeg(0f, -g, Surface.ROTATION_0)
        assertNull(r7)
    }

    @Test
    fun rollForFrameDegThreshold45() {
        val rad44 = Math.toRadians(44.0)
        val rInside = rollForFrameDeg((g * sin(rad44)).toFloat(), (g * cos(rad44)).toFloat(), Surface.ROTATION_0)
        assertEquals(44f, rInside!!, 0.05f)

        val rad46 = Math.toRadians(46.0)
        val rOutside = rollForFrameDeg((g * sin(rad46)).toFloat(), (g * cos(rad46)).toFloat(), Surface.ROTATION_0)
        assertNull(rOutside)
    }

    @Test
    fun focalPxCalculations() {
        val f1920 = focalPx(focalMm = 4.38f, sensorLongEdgeMm = 5.6f, videoLongEdgePx = 1920f)
        assertEquals(1501.714f, f1920!!, 0.1f)

        val f1280 = focalPx(focalMm = 4.38f, sensorLongEdgeMm = 5.6f, videoLongEdgePx = 1280f)
        assertEquals(1001.143f, f1280!!, 0.1f)

        val f720 = focalPx(focalMm = 4.38f, sensorLongEdgeMm = 5.6f, videoLongEdgePx = 720f)
        assertEquals(563.143f, f720!!, 0.1f)

        assertNull(focalPx(focalMm = 0f, sensorLongEdgeMm = 5.6f, videoLongEdgePx = 1920f))
        assertNull(focalPx(focalMm = 4.38f, sensorLongEdgeMm = 0f, videoLongEdgePx = 1920f))
        assertNull(focalPx(focalMm = 4.38f, sensorLongEdgeMm = -5.6f, videoLongEdgePx = 1920f))
        assertNull(focalPx(focalMm = 4.38f, sensorLongEdgeMm = 5.6f, videoLongEdgePx = 0f))
    }
}
