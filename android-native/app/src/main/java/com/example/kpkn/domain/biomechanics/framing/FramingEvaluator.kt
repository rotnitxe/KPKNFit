package com.example.kpkn.domain.biomechanics.framing

import com.example.kpkn.domain.biomechanics.kinematics.FrameGeometry
import com.example.kpkn.domain.biomechanics.kinematics.Vec2
import com.example.kpkn.domain.biomechanics.kinematics.ViewClassifier
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.max

/**
 * Comprueba el encuadre de una imagen fija antes de grabar (§4, punto 14). Es puro: recibe las poses que ve el
 * modelo en el fotograma (hasta dos), el [FramingSpec] del perfil, la inclinación del móvil y si se vio un disco.
 *
 * Los problemas salen en orden de prioridad (el de arriba es el que la interfaz muestra). Con histéresis: `ok`
 * exige [okStreakRequired] evaluaciones seguidas sin problemas, y cualquier problema devuelve la racha a 0.
 * Una instancia guarda esa racha, así que no se comparte entre grabaciones: se crea una por sesión o se llama a [reset].
 */
class FramingEvaluator(private val okStreakRequired: Int = 3) {
    private var okStreak = 0

    /** Vuelve la racha de evaluaciones correctas a 0 (al abrir el grabador o cambiar de perfil). */
    fun reset() {
        okStreak = 0
    }

    /**
     * Evalúa un fotograma. [poses] son las poses del fotograma tal como las da el estimador (puntos normalizados
     * 0..1 sobre `frameWidth×frameHeight`, ya girado), [rollDeg] la inclinación del móvil (`null` si no se conoce) y
     * [plateSeen] si se vio el disco (`null` si no se sabe: no se avisa).
     *
     * 1. NO_PERSON; 2. TWO_PEOPLE (2.ª pose con caja ≥ 30 % de la principal); 3. TOO_CLOSE (el cuerpo ocupa > 95 % del
     * alto, o faltan la cabeza y los pies a la vez); 4. FEET_OUT / HEAD_OUT (un punto fuera del cuadro o a < 5 % del
     * borde, según `needsFeet` / `needsFace`); 5. TOO_SMALL (< 40 % del alto); 6. WRONG_VIEW (solo ψ_2D); 7. FACE_HIDDEN;
     * 8. TILTED (|roll| > 3°); 9. NO_PLATE.
     */
    fun evaluate(
        poses: List<Map<PoseLandmark, LandmarkSample>>,
        frameWidth: Int,
        frameHeight: Int,
        spec: FramingSpec,
        rollDeg: Float?,
        plateSeen: Boolean?,
    ): FramingVerdict {
        val aspect = if (frameWidth > 0 && frameHeight > 0) frameWidth.toDouble() / frameHeight else 1.0

        // Personas de verdad: con suficientes puntos visibles, de mayor a menor caja.
        val people = poses.filter { visibleCount(it) >= MIN_VISIBLE_POINTS }.sortedByDescending { boxArea(it, aspect) }
        if (people.isEmpty()) {
            okStreak = 0
            return FramingVerdict(false, listOf(FramingIssue.NO_PERSON), null)
        }
        val main = people[0]
        val issues = ArrayList<FramingIssue>(4)

        if (people.size > 1 && boxArea(people[1], aspect) >= SECOND_PERSON_FRACTION * boxArea(main, aspect)) {
            issues.add(FramingIssue.TWO_PEOPLE)
        }

        val extent = bodyExtent(main, aspect)
        val headOut = pointOut(main, HEAD_POINTS)
        val feetOut = pointOut(main, FOOT_POINTS)
        val tooClose = extent > TOO_CLOSE_EXTENT || (headOut && feetOut)
        if (tooClose) issues.add(FramingIssue.TOO_CLOSE)
        if (spec.needsFeet && feetOut) issues.add(FramingIssue.FEET_OUT)
        if (spec.needsFace && headOut) issues.add(FramingIssue.HEAD_OUT)
        if (!tooClose && extent < TOO_SMALL_EXTENT) issues.add(FramingIssue.TOO_SMALL)

        val view = viewOf(main, aspect)
        if (view != null && spec.preferredViews.isNotEmpty() && view !in spec.preferredViews) {
            issues.add(FramingIssue.WRONG_VIEW)
        }
        if (spec.needsFace && !headOut && faceHidden(main)) issues.add(FramingIssue.FACE_HIDDEN)
        if (rollDeg != null && abs(rollDeg) > TILT_MAX_DEG) issues.add(FramingIssue.TILTED)
        if (spec.needsPlate && plateSeen == false) issues.add(FramingIssue.NO_PLATE)

        okStreak = if (issues.isEmpty()) okStreak + 1 else 0
        return FramingVerdict(issues.isEmpty() && okStreak >= max(1, okStreakRequired), issues, view)
    }

    private fun visibleCount(pose: Map<PoseLandmark, LandmarkSample>): Int =
        pose.values.count { !it.x.isNaN() && !it.y.isNaN() && it.visibility >= FrameGeometry.WEAK_VISIBILITY }

    /** Puntos visibles (≥ 0,3) en unidades FH; si ninguno lo es, vacío. */
    private fun visiblePoints(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double): List<Vec2> =
        pose.values
            .filter { !it.x.isNaN() && !it.y.isNaN() && it.visibility >= FrameGeometry.WEAK_VISIBILITY }
            .map { Vec2(it.x * aspect, it.y.toDouble()) }

    /** Área (FH²) de la caja de los puntos visibles. */
    private fun boxArea(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double): Double {
        val pts = visiblePoints(pose, aspect)
        if (pts.isEmpty()) return 0.0
        return (pts.maxOf { it.x } - pts.minOf { it.x }) * (pts.maxOf { it.y } - pts.minOf { it.y })
    }

    /**
     * Lado mayor de la caja de los puntos visibles, en fracciones del alto del cuadro (FH). Con el atleta de pie
     * manda el alto; tumbado (banca, flexiones) manda el ancho, y no se confunde un cuerpo horizontal con uno pequeño.
     */
    private fun bodyExtent(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double): Double {
        val pts = visiblePoints(pose, aspect)
        if (pts.isEmpty()) return 0.0
        return max(pts.maxOf { it.x } - pts.minOf { it.x }, pts.maxOf { it.y } - pts.minOf { it.y })
    }

    /**
     * Algún punto del grupo queda fuera del cuadro o a menos del 5 % del borde. También cuenta si no hay ningún punto
     * del grupo (el modelo no los da). Se mira la posición aunque la visibilidad sea baja: un punto cortado por el
     * borde suele salir con poca visibilidad y las coordenadas fuera de 0..1.
     */
    private fun pointOut(pose: Map<PoseLandmark, LandmarkSample>, group: Array<PoseLandmark>): Boolean {
        var present = 0
        for (lm in group) {
            val p = pose[lm] ?: continue
            if (p.x.isNaN() || p.y.isNaN()) continue
            present++
            if (p.x < EDGE_MARGIN || p.x > 1f - EDGE_MARGIN || p.y < EDGE_MARGIN || p.y > 1f - EDGE_MARGIN) return true
        }
        return present == 0
    }

    /** Vista por ψ_2D de los hombros y las caderas visibles; `null` si falta alguno. */
    private fun viewOf(pose: Map<PoseLandmark, LandmarkSample>, aspect: Double): ViewKind? {
        val sl = visiblePoint(pose, PoseLandmark.LEFT_SHOULDER, aspect) ?: return null
        val sr = visiblePoint(pose, PoseLandmark.RIGHT_SHOULDER, aspect) ?: return null
        val hl = visiblePoint(pose, PoseLandmark.LEFT_HIP, aspect) ?: return null
        val hr = visiblePoint(pose, PoseLandmark.RIGHT_HIP, aspect) ?: return null
        val psi = ViewClassifier.psi2dDeg(sl, sr, hl, hr)
        if (psi.isNaN()) return null
        return ViewClassifier.kindOfPsi(psi)
    }

    private fun visiblePoint(pose: Map<PoseLandmark, LandmarkSample>, lm: PoseLandmark, aspect: Double): Vec2? {
        val p = pose[lm] ?: return null
        if (p.x.isNaN() || p.y.isNaN() || p.visibility < FrameGeometry.WEAK_VISIBILITY) return null
        return Vec2(p.x * aspect, p.y.toDouble())
    }

    /** La nariz no se ve (visibilidad < 0,5 o ausente): no se ve la cara. */
    private fun faceHidden(pose: Map<PoseLandmark, LandmarkSample>): Boolean {
        val nose = pose[PoseLandmark.NOSE] ?: return true
        return !(nose.visibility >= FACE_VISIBILITY)
    }

    private companion object {
        /** Menos puntos visibles que estos y la «pose» no es una persona. */
        const val MIN_VISIBLE_POINTS = 5

        /** La 2.ª pose cuenta como segunda persona con una caja de al menos esta fracción de la principal. */
        const val SECOND_PERSON_FRACTION = 0.30

        const val TOO_CLOSE_EXTENT = 0.95
        const val TOO_SMALL_EXTENT = 0.40
        const val EDGE_MARGIN = 0.05f
        const val TILT_MAX_DEG = 3f
        const val FACE_VISIBILITY = 0.5f

        val HEAD_POINTS = arrayOf(PoseLandmark.NOSE, PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR)
        val FOOT_POINTS = arrayOf(
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL, PoseLandmark.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX, PoseLandmark.RIGHT_FOOT_INDEX,
        )
    }
}
