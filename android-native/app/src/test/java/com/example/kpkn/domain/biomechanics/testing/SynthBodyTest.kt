package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El cuerpo 3D del generador: longitudes de los huesos exactas, nada bajo el suelo y ángulos principales razonables. */
class SynthBodyTest {
    private val height = 1.75
    private val anthro = Anthro(height)
    private val steps = listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)

    private fun dist(a: V3, b: V3) = (a - b).norm()

    private val problems = ArrayList<String>()

    private fun joints(m: SynthMovement, b: Double, front: Side = Side.LEFT): Joints {
        TwoLink.clamped = false
        val post = SynthMovements.posture(m, MotionState(b, b, 0.0, 0.0), anthro, front)
        val j = SynthBody.joints(post, anthro)
        if (TwoLink.clamped) problems.add("la cadena se recortó en $m con b = $b")
        return j
    }

    private fun check(message: String, expected: Double, actual: Double, tol: Double) {
        if (Math.abs(expected - actual) > tol) problems.add("$message: esperado $expected, hay $actual")
    }

    private fun assertNoProblems() = assertTrue(problems.joinToString(System.lineSeparator()), problems.isEmpty())

    private fun checkBones(label: String, j: Joints, a: Anthro) {
        for ((hip, knee, ankle) in listOf(
            Triple(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
            Triple(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
        )) {
            check("muslo $label", a.thigh, dist(j[hip], j[knee]), 1e-6)
            check("pierna $label", a.shank, dist(j[knee], j[ankle]), 1e-6)
        }
        for ((sh, el, wr) in listOf(
            Triple(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Triple(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
        )) {
            check("brazo $label", a.upperArm, dist(j[sh], j[el]), 1e-6)
            check("antebrazo $label", a.forearm, dist(j[el], j[wr]), 1e-6)
        }
        val hipMid = (j[PoseLandmark.LEFT_HIP] + j[PoseLandmark.RIGHT_HIP]) * 0.5
        val shoulderMid = (j[PoseLandmark.LEFT_SHOULDER] + j[PoseLandmark.RIGHT_SHOULDER]) * 0.5
        check("tronco $label", a.trunk, dist(hipMid, shoulderMid), 1e-6)
        check("anchura de hombros $label", a.shoulderHalf * 2, dist(j[PoseLandmark.LEFT_SHOULDER], j[PoseLandmark.RIGHT_SHOULDER]), 1e-9)
        check("anchura de caderas $label", a.hipHalf * 2, dist(j[PoseLandmark.LEFT_HIP], j[PoseLandmark.RIGHT_HIP]), 1e-9)
    }

    private fun joints(m: SynthMovement, st: MotionState, a: Anthro, front: Side, label: String): Joints {
        TwoLink.clamped = false
        val j = SynthBody.joints(SynthMovements.posture(m, st, a, front), a)
        if (TwoLink.clamped) problems.add("la cadena se recortó en $label")
        return j
    }

    @Test
    fun boneLengthsAreExactInEveryPostureOfEveryMovement() {
        for (m in SynthMovement.entries) {
            for (b in steps) checkBones("$m b=$b", joints(m, b), anthro)
        }
        assertNoProblems()
    }

    @Test
    fun boneLengthsStayExactWithUnrackGaitSingleArmsOtherHeightsAndTheRightLeg() {
        for (height in listOf(1.55, 1.95)) {
            val a = Anthro(height)
            for (m in SynthMovement.entries) {
                val def = SynthMovements.def(m)
                for (b in steps) {
                    for (u in listOf(0.0, 0.5, 1.0)) {
                        val st = MotionState(b, b, 0.0, u)
                        checkBones("$m h=$height b=$b u=$u", joints(m, st, a, Side.RIGHT, "$m h=$height b=$b u=$u"), a)
                    }
                    if (def.supportsSide) {
                        checkBones("$m h=$height bL=$b", joints(m, MotionState(b, 0.0), a, Side.LEFT, "$m bL=$b"), a)
                        checkBones("$m h=$height bR=$b", joints(m, MotionState(0.0, b), a, Side.RIGHT, "$m bR=$b"), a)
                    }
                }
                if (def.standingRest) {
                    for (amp in listOf(0.4, 1.0)) for (phase in listOf(0.0, 0.7, 1.6, 2.5, 3.4, 4.5, 5.6)) {
                        val st = MotionState(0.0, 0.0, 1.2, 0.0, amp, phase)
                        val label = "$m paso amp=$amp fase=$phase"
                        val j = joints(m, st, a, Side.LEFT, label)
                        checkBones(label, j, a)
                        for (lm in listOf(PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE, PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_FOOT_INDEX)) {
                            if (j[lm].y < -1e-6) problems.add("$lm bajo el suelo en $label")
                        }
                    }
                }
            }
        }
        assertNoProblems()
    }

    @Test
    fun theZoneThatTouchesTheGroundNeverGoesBelowIt() {
        for (m in SynthMovement.entries) {
            if (m == SynthMovement.PULL_UP) continue // colgado: sin suelo
            for (b in steps) {
                val j = joints(m, b)
                for (lm in listOf(
                    PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL, PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX,
                    PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
                )) {
                    if (j[lm].y < -1e-6) problems.add("$lm bajo el suelo en $m b=$b: ${j[lm].y}")
                }
            }
        }
        assertNoProblems()
    }

    @Test
    fun theAthleteHasTheFaceDirectionOfHisTrunk() {
        // De pie la cara mira hacia delante (+X); tumbado en banca, hacia arriba (+Y); en flexiones, hacia abajo.
        val stand = SynthMovements.posture(SynthMovement.SQUAT, MotionState(0.0, 0.0), anthro, Side.LEFT)
        assertEquals(1.0, SynthBody.faceDirection(stand).x, 1e-9)
        val bench = SynthMovements.posture(SynthMovement.BENCH, MotionState(0.0, 0.0), anthro, Side.LEFT)
        assertTrue(SynthBody.faceDirection(bench).y > 0.99)
        val pushUp = SynthMovements.posture(SynthMovement.PUSH_UP, MotionState(0.0, 0.0), anthro, Side.LEFT)
        assertTrue(SynthBody.faceDirection(pushUp).y < -0.5)
    }

    @Test
    fun primaryAngleGoesFromRestToTurnAndPrintsTheTable() {
        println("movimiento: ángulo principal (flexión) en b = 0 → 0,5 → 1")
        val expectedRom = mapOf(
            SynthMovement.SQUAT to 100.0..140.0,
            SynthMovement.LUNGE to 70.0..115.0,
            SynthMovement.DEADLIFT to 55.0..120.0,
            SynthMovement.RDL to 40.0..100.0,
            SynthMovement.HIP_THRUST to 40.0..110.0,
            SynthMovement.BENCH to 80.0..160.0,
            SynthMovement.PUSH_UP to 70.0..140.0,
            SynthMovement.OHP to 100.0..170.0,
            SynthMovement.PULL_UP to 80.0..165.0,
            SynthMovement.LAT_PULLDOWN to 80.0..165.0,
            SynthMovement.ROW to 60.0..140.0,
            SynthMovement.CURL to 120.0..150.0,
            SynthMovement.TRICEPS_PUSHDOWN to 70.0..110.0,
            SynthMovement.LATERAL_RAISE to 70.0..95.0,
            SynthMovement.LEG_EXTENSION to 60.0..100.0,
            SynthMovement.CALF_RAISE to 40.0..60.0,
        )
        for (m in SynthMovement.entries) {
            val angles = DoubleArray(11) { i ->
                val b = i / 10.0
                SynthMovements.primaryAngleDeg(m, joints(m, b), Side.LEFT, anthro)
            }
            println("%-17s %6.1f  %6.1f  %6.1f".format(m.name, angles[0], angles[5], angles[10]))
            val rom = Math.abs(angles[10] - angles[0])
            val range = expectedRom.getValue(m)
            if (rom !in range) problems.add("ROM de $m = $rom fuera de $range")
            // Monótono: el ángulo cambia siempre en el mismo sentido (con una tolerancia pequeña).
            val sign = Math.signum(angles[10] - angles[0])
            for (i in 1..10) {
                if (sign * (angles[i] - angles[i - 1]) <= -0.5) problems.add("no monótono en $m en b=${i / 10.0}")
            }
        }
        assertNoProblems()
    }
}
