package com.example.kpkn.screens.workout.technique

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import com.example.kpkn.data.media.technique.CaptureMetadataFile
import com.example.kpkn.data.media.technique.TechniqueSidecars
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

    private class FakeGravity : CaptureGravitySource {
        var listener: ((Float, Float, Float) -> Unit)? = null
        var starts = 0
        var closes = 0
        override fun start(onGravity: (Float, Float, Float) -> Unit) { starts++; listener = onGravity }
        override fun close() { closes++; listener = null }
        fun emit(x: Float, y: Float, z: Float = 0f) { listener?.invoke(x, y, z) }
    }

    @Test fun preview_measures_roll_before_rec_and_only_recording_samples_enter_the_median() {
        val gravity = FakeGravity()
        val recorder = CaptureMetadataRecorder(org.robolectric.RuntimeEnvironment.getApplication(), null, 30f, 30f, gravity)
        recorder.startPreview(Surface.ROTATION_0)
        val tilted = Math.toRadians(12.0)
        gravity.emit((g * sin(tilted)).toFloat(), (g * cos(tilted)).toFloat())
        assertEquals(12f, recorder.currentRollDeg!!, 0.05f)
        recorder.start()
        gravity.emit(0f, g)
        val metadata = recorder.stop()
        assertEquals(0f, metadata.gravityRollDeg!!, 0.01f)
        assertNull(metadata.focalLengthPx)
        assertEquals(1, gravity.starts)
        gravity.emit((g * sin(tilted)).toFloat(), (g * cos(tilted)).toFloat())
        assertEquals(12f, recorder.currentRollDeg!!, 0.05f)
        recorder.close()
        assertEquals(1, gravity.closes)
        assertNull(recorder.currentRollDeg)
    }

    @Test fun preview_uses_current_rotation_without_waiting_for_another_sensor_event() {
        val gravity = FakeGravity()
        val recorder = CaptureMetadataRecorder(org.robolectric.RuntimeEnvironment.getApplication(), null, 30f, 30f, gravity)
        recorder.startPreview()
        gravity.emit(g, 0f)
        assertNull(recorder.currentRollDeg)
        recorder.updateTargetRotation(Surface.ROTATION_90)
        assertEquals(0f, recorder.currentRollDeg!!, 0.01f)
        recorder.start(Surface.ROTATION_90)
        gravity.emit(g, 0f)
        assertEquals(0f, recorder.stop().gravityRollDeg!!, 0.01f)
        recorder.close()
    }

    @Test fun focal_requires_real_finite_video_dimensions() {
        assertNull(recordedFocalPx(4.38f, 5.6f, null))
        assertNull(recordedFocalPx(4.38f, 5.6f, Float.NaN))
        assertNull(recordedFocalPx(4.38f, 5.6f, Float.POSITIVE_INFINITY))
        assertNull(recordedFocalPx(null, 5.6f, 1280f))
        assertEquals(1001.143f, recordedFocalPx(4.38f, 5.6f, 1280f)!!, 0.1f)
    }
    @get:Rule val files = TemporaryFolder()

    @Test fun actual_start_excludes_preparation_and_freeze_excludes_finalize_io_delay_while_preview_stays_live() {
        val gravity = FakeGravity()
        val recorder = CaptureMetadataRecorder(org.robolectric.RuntimeEnvironment.getApplication(), null, 30f, 30f, gravity)
        recorder.startPreview()
        val interval = TechniqueCaptureMetadataInterval(recorder, Surface.ROTATION_0)
        val tilt = Math.toRadians(20.0)
        gravity.emit((g * sin(tilt)).toFloat(), (g * cos(tilt)).toFloat())
        assertTrue(interval.onStarted())
        gravity.emit(0f, g)
        interval.stopCollecting()
        gravity.emit((g * sin(tilt)).toFloat(), (g * cos(tilt)).toFloat())
        assertEquals(20f, recorder.currentRollDeg!!, 0.05f)
        interval.stopCollecting()
        assertFalse(interval.onStarted())
        val video = files.newFile("stopped.mp4")
        interval.save(video)
        val saved = CaptureMetadataFile.read(TechniqueSidecars.captureFile(video))
        assertEquals(0f, saved.gravityRollDeg!!, 0.01f)
        assertNotNull(saved.recordedAtMs)
        recorder.close()
    }

    @Test fun cancelled_preparation_and_late_start_never_collect_or_invent_recorded_at() {
        val gravity = FakeGravity()
        val recorder = CaptureMetadataRecorder(org.robolectric.RuntimeEnvironment.getApplication(), null, 30f, 30f, gravity)
        recorder.startPreview()
        val interval = TechniqueCaptureMetadataInterval(recorder, Surface.ROTATION_0)
        interval.stopCollecting()
        assertFalse(interval.onStarted())
        gravity.emit(0f, g)
        val video = files.newFile("cancelled.mp4")
        interval.save(video)
        val saved = CaptureMetadataFile.read(TechniqueSidecars.captureFile(video))
        assertNull(saved.recordedAtMs)
        assertNull(saved.gravityRollDeg)
        assertEquals(0f, recorder.currentRollDeg!!, 0.01f)
        recorder.close()
    }

    @Test fun delayed_save_keeps_its_own_frozen_samples_when_another_attempt_starts() {
        val gravity = FakeGravity()
        val recorder = CaptureMetadataRecorder(org.robolectric.RuntimeEnvironment.getApplication(), null, 30f, 30f, gravity)
        recorder.startPreview()
        val first = TechniqueCaptureMetadataInterval(recorder, Surface.ROTATION_0)
        first.onStarted()
        gravity.emit(0f, g)
        first.stopCollecting()
        val startedAt = recorder.stop().recordedAtMs
        val second = TechniqueCaptureMetadataInterval(recorder, Surface.ROTATION_0)
        second.onStarted()
        val tilt = Math.toRadians(20.0)
        gravity.emit((g * sin(tilt)).toFloat(), (g * cos(tilt)).toFloat())
        val video = files.newFile("first.mp4")
        first.save(video)
        val saved = CaptureMetadataFile.read(TechniqueSidecars.captureFile(video))
        assertEquals(0f, saved.gravityRollDeg!!, 0.01f)
        assertEquals(startedAt, saved.recordedAtMs)
        assertEquals(20f, recorder.stop().gravityRollDeg!!, 0.05f)
        val queuedSensorCallback = gravity.listener!!
        recorder.close()
        // Un callback de sensor ya en cola tampoco puede contaminar el intervalo cerrado.
        queuedSensorCallback(0f, g, 0f)
        assertEquals(20f, recorder.stop().gravityRollDeg!!, 0.05f)
    }

}
