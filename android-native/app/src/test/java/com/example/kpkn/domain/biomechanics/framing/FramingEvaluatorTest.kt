package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingEvaluatorTest {
    private val w = 720
    private val h = 1280
    private val side = FramingSpec(preferredViews = setOf(ViewKind.SIDE), needsPlate = false, needsFeet = true)

    /**
     * Un atleta de lado, de pie y bien encuadrado, escalado: [scale] = 1 ocupa del 12 % al 90 % del alto; la cara a la
     * vista (nariz 0,95); [shoulderHalf] regula la anchura de los hombros (casi 0 = de lado).
     */
    private fun athlete(
        scale: Float = 1f,
        cx: Float = 0.5f,
        top: Float = 0.12f,
        shoulderHalf: Float = 0.01f,
        noseVis: Float = 0.95f,
        feetY: Float? = null,
    ): Map<PoseLandmark, LandmarkSample> {
        fun y(v: Float) = top + (v - 0.12f) * scale
        fun p(x: Float, yy: Float, vis: Float = 0.95f) = LandmarkSample(cx + x * scale, yy, 0f, vis, 0.99f)
        val foot = feetY ?: y(0.90f)
        return mapOf(
            PoseLandmark.NOSE to p(0.02f, y(0.12f), noseVis),
            PoseLandmark.LEFT_EAR to p(0f, y(0.13f), noseVis),
            PoseLandmark.RIGHT_EAR to p(0f, y(0.13f), noseVis),
            PoseLandmark.LEFT_SHOULDER to p(shoulderHalf, y(0.28f)),
            PoseLandmark.RIGHT_SHOULDER to p(-shoulderHalf, y(0.28f)),
            PoseLandmark.LEFT_HIP to p(0.01f, y(0.52f)),
            PoseLandmark.RIGHT_HIP to p(-0.01f, y(0.52f)),
            PoseLandmark.LEFT_KNEE to p(0.03f, y(0.72f)),
            PoseLandmark.RIGHT_KNEE to p(-0.03f, y(0.72f)),
            PoseLandmark.LEFT_ANKLE to p(0f, foot - 0.02f * scale),
            PoseLandmark.RIGHT_ANKLE to p(0f, foot - 0.02f * scale),
            PoseLandmark.LEFT_HEEL to p(-0.02f, foot),
            PoseLandmark.RIGHT_HEEL to p(-0.02f, foot),
            PoseLandmark.LEFT_FOOT_INDEX to p(0.06f, foot),
            PoseLandmark.RIGHT_FOOT_INDEX to p(0.06f, foot),
        )
    }

    private fun evaluate(
        poses: List<Map<PoseLandmark, LandmarkSample>>,
        spec: FramingSpec = side,
        roll: Float? = 0f,
        plate: Boolean? = null,
        evaluator: FramingEvaluator = FramingEvaluator(),
    ): FramingVerdict = evaluator.evaluate(poses, w, h, spec, roll, plate)

    private fun issues(poses: List<Map<PoseLandmark, LandmarkSample>>, spec: FramingSpec = side, roll: Float? = 0f, plate: Boolean? = null) =
        evaluate(poses, spec, roll, plate).issues

    @Test
    fun aWellFramedSideViewHasNoIssuesAndIsSideOnTheImage() {
        val v = evaluate(listOf(athlete()))
        assertTrue(v.issues.isEmpty())
        assertEquals(ViewKind.SIDE, v.view)
    }

    @Test
    fun noPerson() {
        assertEquals(listOf(FramingIssue.NO_PERSON), issues(emptyList()))
        val v = evaluate(emptyList())
        assertFalse(v.ok)
        assertEquals(null, v.view)
        // Una «pose» con casi todos los puntos invisibles tampoco es una persona.
        val ghost = athlete().mapValues { (_, p) -> LandmarkSample(p.x, p.y, p.z, 0.05f, p.presence) }
        assertEquals(listOf(FramingIssue.NO_PERSON), issues(listOf(ghost)))
    }

    @Test
    fun twoPeopleWhenTheSecondBoxIsAtLeastThirtyPercentOfTheMain() {
        // La segunda con el 60 % de la escala: caja del 36 %.
        assertTrue(FramingIssue.TWO_PEOPLE in issues(listOf(athlete(), athlete(scale = 0.6f, cx = 0.15f))))
        // Con el 50 % (25 % de la caja) no cuenta como segunda persona.
        assertFalse(FramingIssue.TWO_PEOPLE in issues(listOf(athlete(), athlete(scale = 0.5f, cx = 0.15f))))
        // El orden de las poses da igual: la principal es la mayor.
        assertTrue(FramingIssue.TWO_PEOPLE in issues(listOf(athlete(scale = 0.6f, cx = 0.15f), athlete())))
    }

    @Test
    fun tooCloseWhenTheBodyFillsTheFrameOrBothEndsAreMissing() {
        // De cabeza a pies ocupa más del 95 % del alto.
        val huge = athlete(scale = 1.25f, top = 0.0f)
        val closeIssues = issues(listOf(huge))
        assertEquals(FramingIssue.TOO_CLOSE, closeIssues.first())
        assertFalse(FramingIssue.TOO_SMALL in closeIssues)
        // Cabeza y pies fuera a la vez (aunque ocupe menos del 95 % de lo visible): también demasiado cerca.
        val cutBothEnds = athlete(scale = 1.0f, top = 0.02f, feetY = 0.985f)
        assertEquals(FramingIssue.TOO_CLOSE, issues(listOf(cutBothEnds)).first())
        // Solo los pies fuera: FEET_OUT y no TOO_CLOSE.
        val feetOnly = issues(listOf(athlete(feetY = 0.99f)))
        assertTrue(FramingIssue.FEET_OUT in feetOnly)
        assertFalse(FramingIssue.TOO_CLOSE in feetOnly)
    }

    @Test
    fun feetOutAndHeadOutWhenAPointIsOutsideOrWithinFivePercentOfTheEdge() {
        // Pies a menos del 5 % del borde inferior.
        assertEquals(listOf(FramingIssue.FEET_OUT), issues(listOf(athlete(feetY = 0.96f))))
        // En el límite (0,94) todavía vale.
        assertTrue(issues(listOf(athlete(feetY = 0.94f))).isEmpty())
        // Pies fuera del cuadro.
        assertTrue(FramingIssue.FEET_OUT in issues(listOf(athlete(feetY = 1.05f))))
        // Cabeza a menos del 5 % del borde superior.
        val high = athlete(top = 0.03f, scale = 0.9f)
        assertEquals(listOf(FramingIssue.HEAD_OUT), issues(listOf(high)))
        // Lateral: la nariz pegada al borde izquierdo.
        assertTrue(FramingIssue.HEAD_OUT in issues(listOf(athlete(cx = 0.02f))))
        // Si el perfil no necesita los pies (needsFeet = false) o la cara, no se avisa.
        val noFeet = side.copy(needsFeet = false)
        assertTrue(issues(listOf(athlete(feetY = 0.99f)), noFeet).isEmpty())
        val noFace = side.copy(needsFace = false)
        assertTrue(issues(listOf(high), noFace).isEmpty())
    }

    @Test
    fun tooSmallWhenTheBodyIsUnderFortyPercentOfTheHeight() {
        // scale 0.5: del 12 % al 51 % del alto = 39 %.
        assertEquals(listOf(FramingIssue.TOO_SMALL), issues(listOf(athlete(scale = 0.49f, top = 0.3f))))
        // 0.55 → 43 %: bien.
        assertTrue(issues(listOf(athlete(scale = 0.55f, top = 0.3f))).isEmpty())
    }

    @Test
    fun wrongViewUsesOnlyPsi2dOfTheShouldersAndHips() {
        // Hombros anchos = de frente: no vale si el perfil pide SIDE.
        val front = athlete(shoulderHalf = 0.17f)
        val v = evaluate(listOf(front))
        assertEquals(listOf(FramingIssue.WRONG_VIEW), v.issues)
        assertEquals(ViewKind.FRONT, v.view)
        // Si el perfil acepta de frente, sin problema; si acepta cualquiera (conjunto vacío), tampoco.
        assertTrue(issues(listOf(front), side.copy(preferredViews = setOf(ViewKind.FRONT, ViewKind.OBLIQUE))).isEmpty())
        assertTrue(issues(listOf(front), side.copy(preferredViews = emptySet())).isEmpty())
        // Oblicuo.
        val oblique = evaluate(listOf(athlete(shoulderHalf = 0.11f)))
        assertEquals(ViewKind.OBLIQUE, oblique.view)
        assertEquals(listOf(FramingIssue.WRONG_VIEW), oblique.issues)
    }

    @Test
    fun faceHiddenWhenTheNoseIsNotVisible() {
        assertEquals(listOf(FramingIssue.FACE_HIDDEN), issues(listOf(athlete(noseVis = 0.2f))))
        assertTrue(issues(listOf(athlete(noseVis = 0.2f)), side.copy(needsFace = false)).isEmpty())
        assertTrue(issues(listOf(athlete(noseVis = 0.6f))).isEmpty())
    }

    @Test
    fun tiltedWhenTheRollIsOverThreeDegrees() {
        assertEquals(listOf(FramingIssue.TILTED), issues(listOf(athlete()), roll = 3.5f))
        assertEquals(listOf(FramingIssue.TILTED), issues(listOf(athlete()), roll = -4f))
        assertTrue(issues(listOf(athlete()), roll = 2.9f).isEmpty())
        assertTrue(issues(listOf(athlete()), roll = null).isEmpty())
    }

    @Test
    fun noPlateOnlyWhenThePlateIsNeededAndWasNotSeen() {
        val plate = side.copy(needsPlate = true)
        assertEquals(listOf(FramingIssue.NO_PLATE), issues(listOf(athlete()), plate, plate = false))
        assertTrue(issues(listOf(athlete()), plate, plate = true).isEmpty())
        assertTrue(issues(listOf(athlete()), plate, plate = null).isEmpty())
        assertTrue(issues(listOf(athlete()), side, plate = false).isEmpty())
    }

    @Test
    fun issuesComeInPriorityOrder() {
        val spec = side.copy(needsPlate = true)
        val v = evaluate(
            poses = listOf(athlete(feetY = 0.99f, noseVis = 0.2f, shoulderHalf = 0.17f), athlete(scale = 0.8f, cx = 0.15f, shoulderHalf = 0.17f)),
            spec = spec,
            roll = 8f,
            plate = false,
        )
        assertEquals(
            listOf(
                FramingIssue.TWO_PEOPLE,
                FramingIssue.FEET_OUT,
                FramingIssue.WRONG_VIEW,
                FramingIssue.FACE_HIDDEN,
                FramingIssue.TILTED,
                FramingIssue.NO_PLATE,
            ),
            v.issues,
        )
        assertFalse(v.ok)
        // Y el orden del enum es el de prioridad.
        assertEquals(FramingIssue.entries.sortedBy { it.ordinal }, FramingIssue.entries.toList())
        assertEquals(FramingIssue.NO_PERSON, FramingIssue.entries.first())
    }

    @Test
    fun okNeedsThreeConsecutiveCleanEvaluationsAndAProblemResetsTheStreak() {
        val evaluator = FramingEvaluator()
        val good = listOf(athlete())
        val bad = listOf(athlete(feetY = 0.99f))
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        assertTrue(evaluate(good, evaluator = evaluator).ok)
        assertTrue(evaluate(good, evaluator = evaluator).ok)
        // Un problema rompe la racha…
        val broken = evaluate(bad, evaluator = evaluator)
        assertFalse(broken.ok)
        assertEquals(listOf(FramingIssue.FEET_OUT), broken.issues)
        // …y hacen falta otras tres evaluaciones buenas.
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        assertTrue(evaluate(good, evaluator = evaluator).ok)
        // Dos buenas, una mala, dos buenas: no.
        evaluator.reset()
        evaluate(good, evaluator = evaluator)
        evaluate(good, evaluator = evaluator)
        evaluate(bad, evaluator = evaluator)
        evaluate(good, evaluator = evaluator)
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        assertTrue(evaluate(good, evaluator = evaluator).ok)
        // reset() vuelve la racha a cero.
        evaluator.reset()
        assertFalse(evaluate(good, evaluator = evaluator).ok)
        // Con okStreakRequired = 1, basta una.
        assertTrue(evaluate(good, evaluator = FramingEvaluator(okStreakRequired = 1)).ok)
        // Sin personas también rompe la racha.
        val e2 = FramingEvaluator()
        evaluate(good, evaluator = e2)
        evaluate(good, evaluator = e2)
        evaluate(emptyList(), evaluator = e2)
        assertFalse(evaluate(good, evaluator = e2).ok)
    }
}
