package com.example.kpkn.domain.biomechanics.kinematics

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.testing.SynthCamera
import com.example.kpkn.domain.biomechanics.testing.SynthConfig
import com.example.kpkn.domain.biomechanics.testing.SynthMovement
import com.example.kpkn.domain.biomechanics.testing.SynthNoise
import com.example.kpkn.domain.biomechanics.testing.SynthRepKind
import com.example.kpkn.domain.biomechanics.testing.SynthSegment
import com.example.kpkn.domain.biomechanics.testing.SyntheticLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

class FrameGeometryTest {
    private fun track(w: Int, h: Int, vararg frames: PoseFrameV2, aspectKnown: Boolean = true) = PoseTrackV2(
        mediaId = "t",
        engine = PoseEngineInfo("test", "test", "CPU"),
        frameWidth = w,
        frameHeight = h,
        rotationApplied = 0,
        nominalFps = 30f,
        poseStride = 1,
        vfr = false,
        aspectKnown = aspectKnown,
        frames = frames.toList(),
    )

    private fun sample(x: Float, y: Float, vis: Float = 0.9f, pres: Float = 0.9f) = LandmarkSample(x, y, 0f, vis, pres)

    private fun frame(vararg pts: Pair<PoseLandmark, LandmarkSample>) =
        PoseFrameV2.of(0L, 1, pts.toMap(), null)

    @Test
    fun distancesAreMeasuredInFrameHeightsSoPortraitAndLandscapeAgree() {
        // Dos puntos separados 0,1 en x normalizada: en 720×1280 son 0,1·720/1280 alturas de fotograma; en 1280×720, 0,1·1280/720.
        val f = frame(PoseLandmark.LEFT_HIP to sample(0.4f, 0.5f), PoseLandmark.RIGHT_HIP to sample(0.5f, 0.5f))
        val portrait = FrameGeometry(track(720, 1280, f))
        val landscape = FrameGeometry(track(1280, 720, f))
        assertEquals(0.1 * 720 / 1280, portrait.distance(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP), 1e-6)
        assertEquals(0.1 * 1280 / 720, landscape.distance(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP), 1e-6)
        val a = portrait.point(0, PoseLandmark.LEFT_HIP)!!
        assertEquals(0.4 * 720 / 1280, a.x, 1e-6)
        assertEquals(0.5, a.y, 1e-6)
        assertEquals(720.0 / 1280.0, portrait.aspect, 1e-12)
        assertEquals(1, portrait.frameCount)
    }

    /** Ángulo del tronco medido con el aspecto corregido (Angles) y SIN corregirlo (el fallo de la v1). */
    private fun leanOfRdl(width: Int, height: Int, roll: Double = 0.0, geometryRoll: Double = roll): Pair<Double, Double> {
        // Peso muerto rumano con amplitud 0,4839: el tronco queda a 30,0° durante la pausa de 3 s.
        val result = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.RDL,
                camera = SynthCamera(yawDeg = 90.0, widthPx = width, heightPx = height, rollDeg = roll, distanceM = 3.0),
                script = listOf(
                    SynthSegment.Idle(0.5),
                    SynthSegment.Rep(1.0, 3.0, 1.0, 0.5, amplitude = 0.4839, kind = SynthRepKind.FULL),
                    SynthSegment.Idle(0.5),
                ),
                noise = SynthNoise.NONE,
            ),
        )
        val geo = FrameGeometry(result.track, rollDeg = geometryRoll)
        val k = (2.5 * 30).toInt() // en mitad de la pausa
        val shoulder = geo.mid(k, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)!!
        val hip = geo.mid(k, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)!!
        // De lado (ψ = 90) el atleta mira hacia la izquierda de la imagen: f = −1.
        val corrected = Angles.trunkLean(shoulder, hip, -1)
        val f = result.track.frames[k]
        val dx = (f.x(PoseLandmark.LEFT_SHOULDER) - f.x(PoseLandmark.LEFT_HIP)).toDouble()
        val dy = (f.y(PoseLandmark.LEFT_HIP) - f.y(PoseLandmark.LEFT_SHOULDER)).toDouble()
        val naive = Math.toDegrees(atan2(-dx, dy)) // sin el aspecto: x e y normalizadas tal cual
        return Pair(corrected, naive)
    }

    @Test
    fun aTrunkAtThirtyDegreesMeasuresThirtyInPortraitAndLandscape() {
        val (portrait, naivePortrait) = leanOfRdl(720, 1280)
        val (landscape, naiveLandscape) = leanOfRdl(1280, 720)
        println("tronco a 30°: vertical $portrait° (sin aspecto $naivePortrait°), horizontal $landscape° (sin aspecto $naiveLandscape°)")
        assertEquals(30.0, portrait, 1.0)
        assertEquals(30.0, landscape, 1.0)
        // Lo que hacía la v1 (sin corregir el aspecto) sale muy lejos de 30° en ambos formatos.
        assertTrue("vertical sin aspecto $naivePortrait", abs(naivePortrait - 30.0) > 10.0)
        assertTrue("horizontal sin aspecto $naiveLandscape", abs(naiveLandscape - 30.0) > 8.0)
    }

    @Test
    fun thePhoneRollIsCorrectedWithTheSameSignAsTheCamera() {
        // Con la cámara girada 10° y 8° de verdad en el tronco, el roll corregido deja 30° (no 40° ni 20°).
        val (uncorrected, _) = leanOfRdl(1280, 720, roll = 10.0, geometryRoll = 0.0)
        val (corrected, _) = leanOfRdl(1280, 720, roll = 10.0, geometryRoll = 10.0)
        val (wrongSign, _) = leanOfRdl(1280, 720, roll = 10.0, geometryRoll = -10.0)
        // El atleta mira a la izquierda (f = −1): con la imagen girada en sentido horario el tronco se ve MENOS inclinado.
        assertEquals(30.0 - 10.0, uncorrected, 1.0)
        assertEquals(30.0, corrected, 1.0)
        assertEquals(30.0 - 20.0, wrongSign, 1.5)
        // Con roll −10° pasa lo contrario.
        val (counter, _) = leanOfRdl(1280, 720, roll = -10.0, geometryRoll = -10.0)
        assertEquals(30.0, counter, 1.0)
        // El giro no cambia las distancias.
        val f = frame(PoseLandmark.LEFT_HIP to sample(0.4f, 0.5f), PoseLandmark.RIGHT_HIP to sample(0.5f, 0.6f))
        val flat = FrameGeometry(track(1280, 720, f))
        val rolled = FrameGeometry(track(1280, 720, f), rollDeg = 12.0)
        assertEquals(flat.distance(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP), rolled.distance(0, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP), 1e-9)
        val a = flat.point(0, PoseLandmark.LEFT_HIP)!!
        val b = flat.point(0, PoseLandmark.RIGHT_HIP)!!
        val ra = rolled.point(0, PoseLandmark.LEFT_HIP)!!
        val rb = rolled.point(0, PoseLandmark.RIGHT_HIP)!!
        assertEquals((b - a).norm(), (rb - ra).norm(), 1e-9)
    }

    @Test
    fun rollRotatesAroundTheCenterOfTheFrame() {
        // El centro del fotograma no se mueve.
        val center = frame(PoseLandmark.NOSE to sample(0.5f, 0.5f))
        val geo = FrameGeometry(track(1280, 720, center), rollDeg = 25.0)
        val p = geo.point(0, PoseLandmark.NOSE)!!
        assertEquals(0.5 * 1280 / 720, p.x, 1e-6)
        assertEquals(0.5, p.y, 1e-6)
        // Un punto justo encima del centro (la vertical) pasa a la izquierda con roll positivo: −roll lo gira en sentido
        // antihorario.
        val above = frame(PoseLandmark.NOSE to sample(0.5f, 0.2f))
        val g = FrameGeometry(track(1280, 720, above), rollDeg = 10.0).point(0, PoseLandmark.NOSE)!!
        val c = 0.5 * 1280 / 720
        assertTrue(g.x < c)
        assertEquals(0.3 * Math.sin(Math.toRadians(10.0)), c - g.x, 1e-6)
    }

    @Test
    fun weakPointsAreOnlySeenWithTheWeakFlag() {
        val f = frame(
            PoseLandmark.LEFT_KNEE to sample(0.5f, 0.5f, vis = 0.4f, pres = 0.9f),
            PoseLandmark.RIGHT_KNEE to sample(0.5f, 0.6f, vis = 0.2f, pres = 0.9f),
            PoseLandmark.LEFT_ANKLE to sample(0.5f, 0.7f, vis = 0.9f, pres = 0.4f),
            PoseLandmark.RIGHT_ANKLE to sample(0.5f, 0.8f, vis = 0.9f, pres = 0.9f),
        )
        val geo = FrameGeometry(track(720, 1280, f))
        // Visibilidad 0,4: no vale normalmente, vale como débil.
        assertNull(geo.point(0, PoseLandmark.LEFT_KNEE))
        assertNotNull(geo.point(0, PoseLandmark.LEFT_KNEE, weak = true))
        // Visibilidad 0,2: ni siquiera como débil.
        assertNull(geo.point(0, PoseLandmark.RIGHT_KNEE, weak = true))
        // Presencia baja: no vale (la presencia solo se exige a los puntos normales).
        assertNull(geo.point(0, PoseLandmark.LEFT_ANKLE))
        assertNotNull(geo.point(0, PoseLandmark.LEFT_ANKLE, weak = true))
        assertNotNull(geo.point(0, PoseLandmark.RIGHT_ANKLE))
        // El punto medio exige los dos.
        assertNull(geo.mid(0, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_ANKLE))
        assertNotNull(geo.mid(0, PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_ANKLE, weak = true))
        assertNull(geo.mid(0, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, weak = true))
        // Los umbrales se pueden cambiar.
        val lax = FrameGeometry(track(720, 1280, f), minVisibility = 0.35f, minPresence = 0.35f)
        assertNotNull(lax.point(0, PoseLandmark.LEFT_KNEE))
        assertNotNull(lax.point(0, PoseLandmark.LEFT_ANKLE))
        assertNull(lax.point(0, PoseLandmark.RIGHT_KNEE))
    }

    @Test
    fun midPointHandCenterAndMidFoot() {
        val f = frame(
            PoseLandmark.LEFT_WRIST to sample(0.40f, 0.50f),
            PoseLandmark.LEFT_INDEX to sample(0.40f, 0.60f),
            PoseLandmark.LEFT_PINKY to sample(0.44f, 0.58f),
            PoseLandmark.RIGHT_WRIST to sample(0.60f, 0.50f),
            PoseLandmark.RIGHT_INDEX to sample(0.60f, 0.60f, vis = 0.2f),
            PoseLandmark.RIGHT_PINKY to sample(0.64f, 0.58f),
            PoseLandmark.LEFT_HEEL to sample(0.30f, 0.90f),
            PoseLandmark.LEFT_FOOT_INDEX to sample(0.40f, 0.92f),
        )
        val geo = FrameGeometry(track(720, 1280, f))
        val aspect = 720.0 / 1280.0
        // HC = ½·muñeca + ¼·(índice + meñique).
        val hc = geo.handCenter(0, Side.LEFT)!!
        assertEquals((0.5 * 0.40 + 0.25 * (0.40 + 0.44)) * aspect, hc.x, 1e-6)
        assertEquals(0.5 * 0.50 + 0.25 * (0.60 + 0.58), hc.y, 1e-6)
        // Si falta un dedo (aquí por visibilidad), la muñeca.
        val hcr = geo.handCenter(0, Side.RIGHT)!!
        assertEquals(0.60 * aspect, hcr.x, 1e-6)
        assertEquals(0.50, hcr.y, 1e-6)
        // MF = ½·(talón + punta).
        val mf = geo.midFoot(0, Side.LEFT)!!
        assertEquals(0.35 * aspect, mf.x, 1e-6)
        assertEquals(0.91, mf.y, 1e-6)
        assertNull(geo.midFoot(0, Side.RIGHT))
        // Sin muñeca no hay centro de la mano.
        assertNull(FrameGeometry(track(720, 1280, frame(PoseLandmark.NOSE to sample(0.5f, 0.1f)))).handCenter(0, Side.LEFT))
        val m = geo.mid(0, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)!!
        assertEquals(0.5 * aspect, m.x, 1e-6)
    }

    @Test
    fun outOfRangeIndicesAndFramesWithoutPoseGiveNull() {
        val geo = FrameGeometry(track(720, 1280, PoseFrameV2.of(0L, 0, null, null), frame(PoseLandmark.NOSE to sample(0.5f, 0.1f))))
        assertEquals(2, geo.frameCount)
        assertNull(geo.point(-1, PoseLandmark.NOSE))
        assertNull(geo.point(2, PoseLandmark.NOSE))
        assertNull(geo.point(0, PoseLandmark.NOSE))
        assertNotNull(geo.point(1, PoseLandmark.NOSE))
        assertTrue(geo.distance(0, PoseLandmark.NOSE, PoseLandmark.LEFT_EAR).isNaN())
        assertEquals(false, geo.hasPose(0))
        assertEquals(true, geo.hasPose(1))
        assertEquals(false, geo.hasPose(5))
    }
}
