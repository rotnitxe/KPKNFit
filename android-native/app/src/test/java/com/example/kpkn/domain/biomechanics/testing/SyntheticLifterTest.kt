package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Angles
import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.signal.Resampler
import com.example.kpkn.domain.biomechanics.testing.SynthTestSupport.repScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class SyntheticLifterTest {
    private fun squat(
        camera: SynthCamera = SynthCamera(),
        noise: SynthNoise = SynthNoise.NONE,
        reps: Int = 3,
        seed: Long = 42L,
    ) = SyntheticLifter.generate(
        SynthConfig(SynthMovement.SQUAT, camera = camera, script = repScript(reps), noise = noise, seed = seed),
    )

    @Test
    fun sameSeedGivesTheSameTrackAndDifferentSeedDoesNot() {
        val a = squat(noise = SynthNoise(), seed = 7L)
        val b = squat(noise = SynthNoise(), seed = 7L)
        assertEquals(a.track, b.track)
        assertEquals(a.reps, b.reps)
        assertTrue(a.primaryAngleDeg.contentEquals(b.primaryAngleDeg))
        val c = squat(noise = SynthNoise(), seed = 8L)
        assertNotEquals(a.track, c.track)
        // La verdad no depende de la semilla.
        assertEquals(a.reps, c.reps)
    }

    @Test
    fun theNumberOfTrueRepsMatchesTheScript() {
        val script = listOf<SynthSegment>(
            SynthSegment.Idle(1.0),
            SynthSegment.Rep(2.0, 0.5, 1.5, 0.5),
            SynthSegment.Rep(2.0, 0.5, 1.5, 0.5),
            SynthSegment.Rep(2.0, 0.5, 1.5, 0.5, amplitude = 0.8),
            SynthSegment.Rep(2.0, 0.0, 1.5, 0.0, kind = SynthRepKind.PARTIAL_FAR),
            SynthSegment.Rep(2.0, 0.0, 1.5, 0.0, kind = SynthRepKind.FAILED),
            SynthSegment.Rest(5.0),
        )
        val r = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT, script = script, noise = SynthNoise.NONE))
        assertEquals(5, r.reps.size)
        assertEquals(listOf(0, 1, 2, 3, 4), r.reps.map { it.index })
        assertEquals(
            listOf(SynthRepKind.FULL, SynthRepKind.FULL, SynthRepKind.FULL, SynthRepKind.PARTIAL_FAR, SynthRepKind.FAILED),
            r.reps.map { it.kind },
        )
        var previousArrive = 0L
        for (rep in r.reps) {
            assertTrue(rep.departUs >= previousArrive)
            assertTrue(rep.departUs < rep.turnStartUs)
            assertTrue(rep.turnStartUs <= rep.turnEndUs)
            assertTrue(rep.turnEndUs < rep.arriveUs)
            assertTrue(rep.romDeg > 10.0)
            previousArrive = rep.arriveUs
        }
        // El tiempo de una FULL (arranque E): EXC + P1 + CON.
        val first = r.reps[0]
        assertEquals(2.0, (first.turnStartUs - first.departUs) / 1e6, 1e-3)
        assertEquals(0.5, (first.turnEndUs - first.turnStartUs) / 1e6, 1e-3)
        assertEquals(1.5, (first.arriveUs - first.turnEndUs) / 1e6, 1e-3)
        // La amplitud escala el ROM; la parcial lejana hace el 55 % del recorrido de rodilla.
        assertEquals(0.8, r.reps[2].romDeg / r.reps[0].romDeg, 0.03)
        assertEquals(0.55, r.reps[3].romDeg / r.reps[0].romDeg, 0.03)
    }

    @Test
    fun framesAreRegularWithTheRequestedPtsJitter() {
        val r = squat(noise = SynthNoise(ptsJitterMs = 3.0, dropRate = 0.0, outlierRate = 0.0), reps = 4)
        val t = r.track.frames.map { it.tUs }
        for (k in 1 until t.size) assertTrue(t[k] > t[k - 1])
        for ((k, us) in t.withIndex()) {
            val nominal = k / 30.0 * 1e6
            assertTrue("fotograma $k: ${abs(us - nominal)} µs", abs(us - nominal) <= 3_001.0)
        }
        val clean = squat(noise = SynthNoise.NONE, reps = 4)
        for ((k, f) in clean.track.frames.withIndex()) assertEquals((k / 30.0 * 1e6).roundToLong(), f.tUs)
        assertEquals(clean.track.frames.size, r.track.frames.size)
        assertEquals(30f, r.track.nominalFps, 0f)
        assertTrue(r.track.aspectKnown)
    }

    @Test
    fun squatSideViewKneeAngleMatchesTheTruthInPortraitAndLandscape() {
        val cameras = listOf(
            "vertical 720×1280" to SynthCamera(yawDeg = 90.0, widthPx = 720, heightPx = 1280),
            "horizontal 1280×720" to SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720),
        )
        for ((name, camera) in cameras) {
            val r = squat(camera = camera)
            val geo = FrameGeometry(r.track)
            var worst = 0.0
            var compared = 0
            for (k in r.track.frames.indices) {
                val h = geo.point(k, PoseLandmark.LEFT_HIP) ?: continue
                val kn = geo.point(k, PoseLandmark.LEFT_KNEE) ?: continue
                val an = geo.point(k, PoseLandmark.LEFT_ANKLE) ?: continue
                val measured = 180.0 - Angles.included(h, kn, an)
                worst = max(worst, abs(measured - r.primaryAngleDeg[k]))
                compared++
            }
            println("sentadilla $name: $compared fotogramas, peor error de rodilla = ${"%.3f".format(worst)}°")
            assertTrue(compared == r.track.frames.size)
            assertTrue("$name: error $worst°", worst <= 2.0)
            // Y el ROM de la verdad es el de la sentadilla: ~120° de flexión.
            assertEquals(121.4, r.reps[0].romDeg, 1.0)
        }
    }

    @Test
    fun pullUpKeepsTheHandsFixedOnTheBar() {
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.PULL_UP, script = repScript(3, eccS = 2.0, conS = 1.5), noise = SynthNoise.NONE),
        )
        for (lm in listOf(PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST)) {
            val xs = r.track.frames.map { it.x(lm) }
            val ys = r.track.frames.map { it.y(lm) }
            assertTrue("$lm x", (xs.max() - xs.min()) < 1e-4f)
            assertTrue("$lm y", (ys.max() - ys.min()) < 1e-4f)
        }
        // Mientras tanto el hombro sube: la verdad del codo recorre unos 140°.
        val shoulderY = r.track.frames.map { it.y(PoseLandmark.LEFT_SHOULDER) }
        assertTrue(shoulderY.max() - shoulderY.min() > 0.02f)
        assertTrue(r.reps.all { it.romDeg in 125.0..160.0 })
    }

    @Test
    fun everyMovementProducesCountedRepsWhoseTruthMatchesItsOwnAngleSeries() {
        for (m in SynthMovement.entries) {
            val script = repScript(2, eccS = 2.0, p1S = 1.0, conS = 2.0, p2S = 1.0)
            val r = SyntheticLifter.generate(
                SynthConfig(m, camera = SynthCamera(yawDeg = 90.0, distanceM = 4.0), script = script, noise = SynthNoise.NONE),
            )
            assertEquals("repeticiones de $m", 2, r.reps.size)
            val frames = r.track.frames
            for (f in frames) assertTrue("$m: sin pose", f.hasPose)
            fun angleAt(us: Long): Double {
                val k = ((us / 1e6) * 30.0).roundToInt().coerceIn(0, frames.size - 1)
                return r.primaryAngleDeg[k]
            }
            for (rep in r.reps) {
                val series = abs(angleAt(rep.turnStartUs) - angleAt(rep.departUs))
                assertEquals("ROM de $m", rep.romDeg, series, 2.0)
                assertTrue("ROM de $m = ${rep.romDeg}", rep.romDeg > 30.0)
                // El inicio y el giro se reconocen: el ángulo en el giro difiere del del reposo.
                assertTrue(abs(angleAt(rep.turnStartUs) - angleAt(rep.departUs)) > 20.0)
            }
            // Al terminar, la postura vuelve al reposo (mismo ángulo que al empezar).
            assertEquals("reposo de $m", r.primaryAngleDeg.first(), r.primaryAngleDeg.last(), 1.0)
        }
    }

    @Test
    fun repKindsDrawTheRightShapeOnTheKneeAngle() {
        // Sentadilla (arranque E): el ángulo de rodilla es lineal en el progreso b.
        val rest = 3.6
        val full = 125.0 - rest
        fun run(kind: SynthRepKind, reps: Int = 1): SynthResult = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.SQUAT,
                script = repScript(reps, eccS = 2.0, p1S = 1.0, conS = 2.0, p2S = 1.0, kind = kind, lead = 1.0, tail = 3.0),
                noise = SynthNoise.NONE,
            ),
        )
        val partialFar = run(SynthRepKind.PARTIAL_FAR)
        assertEquals(rest + 0.55 * full, partialFar.primaryAngleDeg.max(), 1.0)

        // PARTIAL_REST: la primera baja entera y sube al 45 %; la siguiente oscila entre el 45 % y el 100 %.
        val partialRest = run(SynthRepKind.PARTIAL_REST, reps = 3)
        val tail = partialRest.primaryAngleDeg
        assertEquals(rest + full, tail.max(), 1.0)
        val secondRep = partialRest.reps[1]
        val k1 = (secondRep.departUs / 1e6 * 30.0).roundToInt()
        assertEquals(rest + 0.45 * full, tail[k1], 1.5)

        // FAILED (arranque E): baja entera, la concéntrica llega a la mitad, se queda quieta 0,8 s y vuelve abajo.
        val failed = run(SynthRepKind.FAILED)
        val rep = failed.reps[0]
        val stallStart = rep.turnEndUs + 2_000_000L
        val kStall = (stallStart / 1e6 * 30.0).roundToInt()
        val plateau = (kStall until kStall + 24).map { failed.primaryAngleDeg[it] }
        assertEquals(rest + 0.5 * full, plateau.average(), 1.5)
        assertTrue("la meseta es plana", plateau.max() - plateau.min() < 1.0)
        assertEquals(rest + full, failed.primaryAngleDeg[(rep.arriveUs / 1e6 * 30.0).roundToInt()], 1.5)
    }

    @Test
    fun alternatingCurlSplitsTheRepsBetweenTheArms() {
        val r = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.CURL,
                camera = SynthCamera(yawDeg = 0.0),
                script = repScript(4, eccS = 1.5, p1S = 0.0, conS = 1.5, p2S = 0.0),
                noise = SynthNoise.NONE,
                alternating = true,
            ),
        )
        assertEquals(listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT), r.reps.map { it.side })
        // En cada repetición solo se mueve la muñeca de su lado.
        val frames = r.track.frames
        fun wristRange(lm: PoseLandmark, from: Long, to: Long): Float {
            val a = (from / 1e6 * 30).roundToInt()
            val b = (to / 1e6 * 30).roundToInt()
            val ys = (a..b).map { frames[it].y(lm) }
            return ys.max() - ys.min()
        }
        val first = r.reps[0]
        assertTrue(wristRange(PoseLandmark.LEFT_WRIST, first.departUs, first.arriveUs) > 0.03f)
        assertTrue(wristRange(PoseLandmark.RIGHT_WRIST, first.departUs, first.arriveUs) < 1e-4f)
        val second = r.reps[1]
        assertTrue(wristRange(PoseLandmark.RIGHT_WRIST, second.departUs, second.arriveUs) > 0.03f)
        assertTrue(wristRange(PoseLandmark.LEFT_WRIST, second.departUs, second.arriveUs) < 1e-4f)
        // Sin alternar y sin lado, trabajan los dos brazos.
        val both = SyntheticLifter.generate(
            SynthConfig(SynthMovement.CURL, camera = SynthCamera(yawDeg = 0.0), script = repScript(2), noise = SynthNoise.NONE),
        )
        assertEquals(listOf(null, null), both.reps.map { it.side })
    }

    @Test
    fun visibilityIsHighOnTheNearSideAndDropsOnTheFarSideAndForAFaceTurnedAway() {
        fun vis(yaw: Double, lm: PoseLandmark, far: Float = 0.3f): Float {
            val r = SyntheticLifter.generate(
                SynthConfig(
                    SynthMovement.SQUAT,
                    camera = SynthCamera(yawDeg = yaw),
                    script = listOf(SynthSegment.Idle(0.2)),
                    noise = SynthNoise.NONE.copy(farSideVisibility = far),
                ),
            )
            return r.track.frames[0].vis(lm)
        }
        // De lado (izquierda): el izquierdo a 0,95 y el derecho a 0,3; la cara a la vista.
        assertEquals(0.95f, vis(90.0, PoseLandmark.LEFT_KNEE), 1e-6f)
        assertEquals(0.3f, vis(90.0, PoseLandmark.RIGHT_KNEE), 1e-6f)
        assertEquals(0.95f, vis(90.0, PoseLandmark.NOSE), 1e-6f)
        // De lado (derecha): al revés.
        assertEquals(0.95f, vis(-90.0, PoseLandmark.RIGHT_KNEE), 1e-6f)
        assertEquals(0.3f, vis(-90.0, PoseLandmark.LEFT_KNEE), 1e-6f)
        // De frente: los dos lados a 0,95.
        assertEquals(0.95f, vis(0.0, PoseLandmark.LEFT_KNEE), 1e-6f)
        assertEquals(0.95f, vis(0.0, PoseLandmark.RIGHT_KNEE), 1e-6f)
        // En 45° el lejano queda a medio camino.
        assertEquals(0.95f - 0.65f * 0.7071f, vis(45.0, PoseLandmark.RIGHT_KNEE), 1e-3f)
        // De espaldas, la nariz y las orejas caen a 0,1 y el resto del cuerpo no.
        assertEquals(0.1f, vis(180.0, PoseLandmark.NOSE), 1e-3f)
        assertEquals(0.1f, vis(180.0, PoseLandmark.LEFT_EAR), 1e-3f)
        assertEquals(0.1f, vis(180.0, PoseLandmark.RIGHT_EAR), 1e-3f)
        assertEquals(0.95f, vis(180.0, PoseLandmark.LEFT_SHOULDER), 1e-6f)
        // Con ruido nulo, el lado lejano a 0,95 (la traza «limpia»).
        assertEquals(0.95f, vis(90.0, PoseLandmark.RIGHT_KNEE, far = 0.95f), 1e-6f)
    }

    @Test
    fun noiseAndDefectsAreInjectedAndReported() {
        val noise = SynthNoise(
            sigmaFh = 0.004,
            outlierRate = 0.02,
            dropRate = 0.03,
            swapGlitches = 3,
            secondPersonFraction = 0.25,
            occlusionWindowsS = listOf(2.0 to 4.0),
            occludedLandmarks = setOf(PoseLandmark.LEFT_WRIST),
        )
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.LUNGE, camera = SynthCamera(yawDeg = 90.0), script = repScript(10), noise = noise, seed = 5L),
        )
        val frames = r.track.frames
        val n = frames.size
        // Pérdidas: del orden de dropRate, y coinciden con la lista de la verdad.
        val drops = frames.count { !it.hasPose }
        assertTrue("pérdidas $drops de $n", drops in 1..(0.08 * n).toInt())
        for (k in 0 until n) assertEquals(r.droppedFrames[k], !frames[k].hasPose)
        // Segunda persona: ≈ 25 % de los fotogramas con pose.
        val withTwo = frames.count { it.posesDetected == 2 }
        assertEquals(0.25, withTwo.toDouble() / (n - drops), 0.07)
        assertTrue(frames.filter { it.hasPose }.all { it.posesDetected in 1..2 })
        // Cruces: tres tramos de 2 a 5 fotogramas, en los que la traza difiere de la limpia en las piernas y en nada más.
        val glitched = r.swapGlitchFrames.count { it }
        assertTrue("fotogramas cruzados $glitched", glitched in 4..15)
        // Oclusión de la muñeca izquierda entre 2 y 4 s: visibilidad 0,1 dentro y 0,95 fuera.
        for ((k, f) in frames.withIndex()) {
            if (!f.hasPose) continue
            val t = f.tUs / 1e6
            val v = f.vis(PoseLandmark.LEFT_WRIST)
            if (t in 2.0..4.0) assertEquals("t=$t", 0.1f, v, 1e-6f) else assertEquals("t=$t", 0.95f, v, 1e-6f)
        }
        // La traza limpia no tiene defectos y conserva los mismos instantes.
        assertTrue(r.cleanTrack.frames.all { it.hasPose && it.posesDetected == 1 })
        assertEquals(frames.map { it.tUs }, r.cleanTrack.frames.map { it.tUs })
    }

    @Test
    fun rollRotatesTheImageClockwise() {
        // De pie y quieto, con la cámara girada 10° (roll > 0), la vertical (cadera → hombro) se inclina a la derecha por arriba.
        fun lean(roll: Double): Pair<Double, Double> {
            val r = SyntheticLifter.generate(
                SynthConfig(
                    SynthMovement.SQUAT,
                    camera = SynthCamera(yawDeg = 90.0, rollDeg = roll, widthPx = 1280, heightPx = 720),
                    script = listOf(SynthSegment.Idle(0.2)),
                    noise = SynthNoise.NONE,
                ),
            )
            val f = r.track.frames[0]
            val aspect = 1280.0 / 720.0
            val dx = (f.x(PoseLandmark.LEFT_SHOULDER) - f.x(PoseLandmark.LEFT_HIP)) * aspect
            val dy = (f.y(PoseLandmark.LEFT_HIP) - f.y(PoseLandmark.LEFT_SHOULDER)).toDouble()
            return Pair(dx, Math.toDegrees(Math.atan2(dx, dy)))
        }
        assertEquals(0.0, lean(0.0).second, 0.3)
        val (dxPlus, angPlus) = lean(10.0)
        assertTrue(dxPlus > 0.0)
        assertEquals(10.0, angPlus, 0.5)
        assertEquals(-10.0, lean(-10.0).second, 0.5)
        // El encabezado de la traza lleva el roll para que el análisis lo corrija.
        val tilted = SyntheticLifter.generate(
            SynthConfig(SynthMovement.SQUAT, camera = SynthCamera(rollDeg = 7.0, pitchDeg = -3.0), script = listOf(SynthSegment.Idle(0.2))),
        )
        assertEquals(7f, tilted.track.gravityRollDeg!!, 1e-6f)
        assertEquals(-3f, tilted.track.gravityPitchDeg!!, 1e-6f)
    }

    @Test
    fun walkMovesTheRootAndUnrackShiftsTheBarHorizontally() {
        val camera = SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720)
        val walk = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.SQUAT,
                camera = camera,
                script = listOf(SynthSegment.Idle(0.5), SynthSegment.Walk(3.0, 2.0), SynthSegment.Idle(0.5)),
                noise = SynthNoise.NONE,
            ),
        )
        val first = walk.track.frames.first()
        val last = walk.track.frames.last()
        // El atleta avanza 2 m: de lado (ψ = 90) hacia la izquierda de la imagen (x decrece).
        val shiftFh = (first.x(PoseLandmark.LEFT_HIP) - last.x(PoseLandmark.LEFT_HIP)) * 1280.0 / 720.0
        val expected = walk.bodyHeightFh / 1.75 * 2.0
        assertEquals(expected, shiftFh, 0.08 * expected)

        // Sacar la barra en banca: las muñecas se mueven en horizontal más que en vertical (ΔX > 1,5·ΔY).
        val bench = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.BENCH,
                camera = camera,
                script = listOf(SynthSegment.Idle(0.5), SynthSegment.Unrack(1.5), SynthSegment.Idle(0.5)),
                noise = SynthNoise.NONE,
            ),
        )
        val w0 = bench.track.frames.first()
        val w1 = bench.track.frames.last()
        val dx = abs(w1.x(PoseLandmark.LEFT_WRIST) - w0.x(PoseLandmark.LEFT_WRIST)) * 1280.0 / 720.0
        val dy = abs(w1.y(PoseLandmark.LEFT_WRIST) - w0.y(PoseLandmark.LEFT_WRIST))
        assertTrue("ΔX=$dx ΔY=$dy", dx > 1.5 * dy && dx > 0.02)
        assertTrue(bench.reps.isEmpty())

        // Sentadilla: sacar la barra desplaza a todo el atleta hacia atrás 0,3 m; recolocarla lo devuelve.
        val squat = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.SQUAT,
                camera = camera,
                script = listOf(SynthSegment.Idle(0.5), SynthSegment.Unrack(1.5), SynthSegment.Idle(0.5), SynthSegment.Rerack(1.5), SynthSegment.Idle(0.5)),
                noise = SynthNoise.NONE,
            ),
        )
        val s0 = squat.track.frames.first()
        val sMid = squat.track.frames[(2.5 * 30).roundToInt()]
        val sEnd = squat.track.frames.last()
        val back = (sMid.x(PoseLandmark.LEFT_HIP) - s0.x(PoseLandmark.LEFT_HIP)) * 1280.0 / 720.0
        assertTrue("al sacarla, hacia atrás = a la derecha en la imagen: $back", back > 0.0)
        assertEquals(squat.bodyHeightFh / 1.75 * 0.30, back, 0.1 * back)
        assertEquals(s0.x(PoseLandmark.LEFT_HIP), sEnd.x(PoseLandmark.LEFT_HIP), 0.01f)
    }

    @Test
    fun worldPointsFollowTheCameraAxesAndTheBodyHeightIsTheCentralPlaneOne() {
        // De lado el «world» de hombros y caderas está en profundidad (z) y no en x; de frente, al revés.
        fun worldSpread(yaw: Double): Pair<Float, Float> {
            val r = SyntheticLifter.generate(
                SynthConfig(SynthMovement.SQUAT, camera = SynthCamera(yawDeg = yaw), script = listOf(SynthSegment.Idle(0.2)), noise = SynthNoise.NONE),
            )
            val f = r.track.frames[0]
            val dx = abs(f.worldX(PoseLandmark.LEFT_SHOULDER) - f.worldX(PoseLandmark.RIGHT_SHOULDER))
            val dz = abs(f.worldZ(PoseLandmark.LEFT_SHOULDER) - f.worldZ(PoseLandmark.RIGHT_SHOULDER))
            return Pair(dx, dz)
        }
        val side = worldSpread(90.0)
        assertTrue(side.first < 0.01f && abs(side.second - 0.259f * 1.75f) < 0.005f)
        val front = worldSpread(0.0)
        assertTrue(front.second < 0.01f && abs(front.first - 0.259f * 1.75f) < 0.005f)
        // El centro de las caderas es el origen del «world».
        val r = SyntheticLifter.generate(
            SynthConfig(SynthMovement.SQUAT, camera = SynthCamera(yawDeg = 30.0), script = listOf(SynthSegment.Idle(0.2)), noise = SynthNoise.NONE),
        )
        val f = r.track.frames[0]
        assertEquals(0f, f.worldX(PoseLandmark.LEFT_HIP) + f.worldX(PoseLandmark.RIGHT_HIP), 1e-5f)
        assertEquals(0f, f.worldZ(PoseLandmark.LEFT_HIP) + f.worldZ(PoseLandmark.RIGHT_HIP), 1e-5f)
        // Altura aparente en el plano medio: focal (en alturas de fotograma) × altura / distancia.
        val cam = SynthCamera(yawDeg = 90.0, widthPx = 1280, heightPx = 720, hfovDeg = 60.0, distanceM = 3.5)
        val body = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT, camera = cam, script = listOf(SynthSegment.Idle(0.2)))).bodyHeightFh
        val focalFh = (640.0 / Math.tan(Math.toRadians(30.0))) / 720.0
        assertEquals(focalFh * 1.75 / 3.5, body, 1e-9)
        assertFalse(body.isNaN())
    }

    @Test
    fun failedRepOfACStartMovementStallsAtHalfwayAndReturnsToRest() {
        // Peso muerto (arranque C): la concéntrica llega al 50 % del recorrido, se queda quieta 0,8 s y baja al suelo.
        val r = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.DEADLIFT,
                script = repScript(1, eccS = 2.0, p1S = 0.0, conS = 2.0, p2S = 0.0, kind = SynthRepKind.FAILED, lead = 1.0, tail = 2.0),
                noise = SynthNoise.NONE,
            ),
        )
        val rep = r.reps[0]
        // Salida → mitad (CON, 2 s) → quieto 0,8 s → baja (EXC, 2 s).
        assertEquals(2.0, (rep.turnStartUs - rep.departUs) / 1e6, 1e-3)
        assertEquals(0.8, (rep.turnEndUs - rep.turnStartUs) / 1e6, 1e-3)
        assertEquals(2.0, (rep.arriveUs - rep.turnEndUs) / 1e6, 1e-3)
        val anthro = Anthro(1.75)
        fun angleAtB(b: Double): Double = SynthMovements.primaryAngleDeg(
            SynthMovement.DEADLIFT,
            SynthBody.joints(SynthMovements.posture(SynthMovement.DEADLIFT, MotionState(b, b), anthro, Side.LEFT), anthro),
            Side.LEFT,
            anthro,
        )
        val kStall = ((rep.turnStartUs + 100_000L) / 1e6 * 30.0).roundToInt()
        val plateau = (kStall until kStall + 20).map { r.primaryAngleDeg[it] }
        assertEquals(angleAtB(0.5), plateau.average(), 1.0)
        assertTrue(plateau.max() - plateau.min() < 0.5)
        // Vuelve al reposo: el ángulo final es el inicial (el ROM de la verdad es el de la mitad).
        assertEquals(r.primaryAngleDeg.first(), r.primaryAngleDeg.last(), 0.5)
        assertEquals(abs(angleAtB(0.5) - angleAtB(0.0)), rep.romDeg, 1e-6)
    }

    @Test
    fun restPauseMiniSetsAndPartialRestRepsRecoverTheRestPostureBeforeResting() {
        val mini = listOf<SynthSegment>(
            SynthSegment.Idle(1.0),
            SynthSegment.Rep(1.5, 0.0, 1.0, 0.0), SynthSegment.Rep(1.5, 0.0, 1.0, 0.0), SynthSegment.Rep(1.5, 0.0, 1.0, 0.0),
            SynthSegment.Rest(12.0),
            SynthSegment.Rep(1.5, 0.0, 1.0, 0.0), SynthSegment.Rep(1.5, 0.0, 1.0, 0.0),
            SynthSegment.Rest(12.0),
            SynthSegment.Rep(1.5, 0.0, 1.0, 0.0), SynthSegment.Rep(1.5, 0.0, 1.0, 0.0), SynthSegment.Rep(1.5, 0.0, 1.0, 0.0),
        )
        val r = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT, script = mini, noise = SynthNoise.NONE))
        assertEquals(8, r.reps.size)
        // Durante los descansos la rodilla está en el reposo.
        val restStart = (r.reps[2].arriveUs / 1e6 * 30).roundToInt() + 5
        for (k in restStart until restStart + 300) assertEquals(3.6, r.primaryAngleDeg[k], 0.01)

        // Parciales «alrededor del giro» seguidos de un descanso: antes de descansar se levanta al reposo (1 s de recuperación).
        val partial = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.SQUAT,
                script = repScript(3, 1.5, 0.0, 1.0, 0.0, kind = SynthRepKind.PARTIAL_REST, lead = 1.0, tail = 0.0) + listOf(SynthSegment.Rest(6.0)),
                noise = SynthNoise.NONE,
            ),
        )
        assertEquals(3.6, partial.primaryAngleDeg.last(), 0.01)
        val lastArrive = partial.reps.last().arriveUs
        val kRest = ((lastArrive / 1e6 + 1.2) * 30).roundToInt()
        assertEquals(3.6, partial.primaryAngleDeg[kRest], 0.5)
    }

    @Test
    fun walkMovesRootAlsoInMovementsWithoutAStandingRest() {
        val r = SyntheticLifter.generate(
            SynthConfig(
                SynthMovement.DEADLIFT,
                camera = SynthTestSupport.LANDSCAPE_FAR,
                script = listOf(SynthSegment.Idle(0.5), SynthSegment.Walk(2.0, 1.0), SynthSegment.Idle(0.5)),
                noise = SynthNoise.NONE,
            ),
        )
        val f0 = r.track.frames.first()
        val f1 = r.track.frames.last()
        val shift = (f0.x(PoseLandmark.LEFT_HIP) - f1.x(PoseLandmark.LEFT_HIP)) * 1280.0 / 720.0
        assertTrue("desplazamiento $shift", shift > 0.1)
        // Sin paso: la postura no cambia, solo se traslada.
        assertEquals(f0.y(PoseLandmark.LEFT_KNEE), f1.y(PoseLandmark.LEFT_KNEE), 1e-5f)
    }

    @Test
    fun lateralRaiseBetaIsTheSameFromTheSideAndFromTheFront() {
        // β = acos((Y_E − Y_S)/L̂_brazo) usa solo la componente vertical, así que no depende de hacia dónde mira el atleta.
        fun betaAtTurn(yaw: Double): Pair<Double, Double> {
            val r = SyntheticLifter.generate(
                SynthConfig(
                    SynthMovement.LATERAL_RAISE,
                    // Cámara a la altura de los hombros: con el móvil más bajo, el codo que viene hacia la cámara se ve más
                    // alto por la perspectiva y β sale unos 8° de más (el brief pide el móvil a media altura del recorrido).
                    camera = SynthTestSupport.LANDSCAPE_FAR.copy(yawDeg = yaw, heightM = 1.4),
                    script = repScript(2, eccS = 1.5, p1S = 1.0, conS = 1.5, p2S = 1.0),
                    noise = SynthNoise.NONE,
                ),
            )
            val geo = FrameGeometry(r.track, minVisibility = 0.3f)
            val frames = r.track.frames.indices
            // L̂ = P90 de la longitud 2D del brazo (sale entera cuando el brazo cuelga).
            val lengths = frames.map { geo.distance(it, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW) }.sorted()
            val armLength = lengths[(0.9 * (lengths.size - 1)).toInt()]
            fun beta(k: Int): Double {
                val s = geo.point(k, PoseLandmark.LEFT_SHOULDER)!!
                val e = geo.point(k, PoseLandmark.LEFT_ELBOW)!!
                return Math.toDegrees(Math.acos(((e.y - s.y) / armLength).coerceIn(-1.0, 1.0)))
            }
            val rep = r.reps[0]
            val turn = ((rep.turnStartUs + rep.turnEndUs) / 2e6 * 30.0).roundToInt()
            val mid = ((rep.departUs + rep.turnStartUs) / 2e6 * 30.0).roundToInt()
            return Pair(abs(beta(turn) - r.primaryAngleDeg[turn]), abs(beta(mid) - r.primaryAngleDeg[mid]))
        }
        for (yaw in listOf(90.0, 0.0)) {
            val (atTurn, atMid) = betaAtTurn(yaw)
            println("laterales ψ=$yaw: error de β en el giro ${"%.2f".format(atTurn)}°, a mitad ${"%.2f".format(atMid)}°")
            assertTrue("ψ=$yaw giro: $atTurn°", atTurn <= 4.0)
            assertTrue("ψ=$yaw mitad: $atMid°", atMid <= 7.0)
        }
    }

    @Test
    fun framesPerSecondAndDurationFollowTheScript() {
        for (fps in listOf(24.0, 30.0, 60.0)) {
            val r = SyntheticLifter.generate(
                SynthConfig(SynthMovement.SQUAT, fps = fps, script = repScript(2), noise = SynthNoise.NONE),
            )
            val seconds = 1.0 + 2 * 4.5 + 1.0
            assertEquals((seconds * fps).toInt() + 1, r.track.frames.size)
            assertEquals(fps.toFloat(), r.track.nominalFps, 0f)
            assertEquals(1.0 / fps, (r.track.frames[1].tUs - r.track.frames[0].tUs) / 1e6, 1e-6)
            assertEquals(r.track.frames.size, r.primaryAngleDeg.size)
        }
    }

    @Test
    fun ptsJitterOfThreeMillisecondsKeepsThirtyFpsConstantButMakesSixtyFpsVariable() {
        // El PTS fluctúa (triangular, acotado a ±3 ms): P95/P5 de ΔPTS ≈ 1,2 a 30 fps (bajo el umbral VFR de 1,3) y ≈ 1,4 a 60 fps.
        fun ratio(fps: Double, jitter: Double): Pair<Double, Boolean> {
            val r = SyntheticLifter.generate(
                SynthConfig(
                    SynthMovement.SQUAT,
                    fps = fps,
                    script = repScript(4),
                    noise = SynthNoise(ptsJitterMs = jitter, dropRate = 0.0, outlierRate = 0.0),
                ),
            )
            val tb = Resampler.timebase(LongArray(r.track.frames.size) { r.track.frames[it].tUs })
            println("fps=$fps jitter=$jitter ms: fs=${"%.2f".format(tb.fs)} P95/P5=${"%.3f".format(tb.p95OverP5)} vfr=${tb.vfr}")
            return Pair(tb.fs, tb.vfr)
        }
        val (fs30, vfr30) = ratio(30.0, 3.0)
        assertEquals(30.0, fs30, 0.3)
        assertFalse(vfr30)
        assertTrue(ratio(60.0, 3.0).second)
        assertFalse(ratio(60.0, 0.0).second)
        assertFalse(ratio(60.0, 1.0).second)
    }

    @Test
    fun spansTellWhereEachScriptSegmentFallsInTime() {
        val script = listOf<SynthSegment>(
            SynthSegment.Idle(0.5),
            SynthSegment.Walk(3.0, 2.0),
            SynthSegment.Unrack(1.5),
            SynthSegment.Rep(2.0, 0.5, 1.5, 0.5),
            SynthSegment.Rep(2.0, 0.5, 1.5, 0.5, kind = SynthRepKind.PARTIAL_REST),
            SynthSegment.Rest(2.0),
            SynthSegment.Rerack(1.5),
        )
        val r = SyntheticLifter.generate(SynthConfig(SynthMovement.SQUAT, script = script, noise = SynthNoise.NONE))
        assertEquals(script, r.spans.map { it.segment })
        assertEquals(listOf(0L, 500_000L, 3_500_000L, 5_000_000L), r.spans.take(4).map { it.startUs })
        assertEquals(500_000L, r.spans[0].endUs)
        assertEquals(3_500_000L, r.spans[1].endUs)
        // Cada repetición coincide con su tramo; el descanso tras los parciales empieza tras 1 s de recuperación.
        assertEquals(r.reps[0].departUs, r.spans[3].startUs)
        assertEquals(r.reps[1].departUs, r.spans[4].startUs)
        assertEquals(r.spans[4].endUs + 1_000_000L, r.spans[5].startUs)
        for (i in 1 until r.spans.size) assertTrue(r.spans[i].startUs >= r.spans[i - 1].endUs)
    }
}
