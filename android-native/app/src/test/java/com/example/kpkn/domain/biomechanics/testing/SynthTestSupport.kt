package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.pose.LandmarkSample
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import com.example.kpkn.domain.biomechanics.pose.WorldSample

/** Ayudas comunes de las pruebas que usan el generador (kinematics, framing, testing, y las olas siguientes). */
object SynthTestSupport {
    /** Guion típico: reposo, [n] repeticiones iguales y reposo. */
    fun repScript(
        n: Int,
        eccS: Double = 2.0,
        p1S: Double = 0.5,
        conS: Double = 1.5,
        p2S: Double = 0.5,
        amplitude: Double = 1.0,
        side: com.example.kpkn.domain.biomechanics.kinematics.Side? = null,
        kind: SynthRepKind = SynthRepKind.FULL,
        lead: Double = 1.0,
        tail: Double = 1.0,
    ): List<SynthSegment> =
        listOf<SynthSegment>(SynthSegment.Idle(lead)) +
            List(n) { SynthSegment.Rep(eccS, p1S, conS, p2S, amplitude, side, kind) } +
            listOf(SynthSegment.Idle(tail))

    /** Cámara de lado en horizontal (1280×720) lejos y con zoom: el atleta llena el cuadro y la perspectiva casi no pesa. */
    val LANDSCAPE_FAR = SynthCamera(yawDeg = 90.0, distanceM = 4.5, heightM = 0.9, hfovDeg = 45.0, widthPx = 1280, heightPx = 720)

    /** Los puntos de una imagen como mapa, tal como los da el estimador (para el selector de atleta y el encuadre). */
    fun PoseFrameV2.toMap(): Map<PoseLandmark, LandmarkSample> {
        val out = LinkedHashMap<PoseLandmark, LandmarkSample>()
        for (lm in PoseLandmark.KEPT_IMAGE) point(lm)?.let { out[lm] = it }
        return out
    }

    /** Copia la traza transformando cada fotograma (mapas de imagen y «world»). */
    fun PoseTrackV2.mapFrames(
        transform: (index: Int, image: MutableMap<PoseLandmark, LandmarkSample>, world: MutableMap<PoseLandmark, WorldSample>) -> Unit,
    ): PoseTrackV2 {
        val out = ArrayList<PoseFrameV2>(frames.size)
        for ((i, f) in frames.withIndex()) {
            if (!f.hasPose) {
                out.add(f)
                continue
            }
            val image = LinkedHashMap<PoseLandmark, LandmarkSample>()
            for (lm in PoseLandmark.KEPT_IMAGE) f.point(lm)?.let { image[lm] = it }
            val world = LinkedHashMap<PoseLandmark, WorldSample>()
            for (lm in PoseLandmark.KEPT_WORLD) f.world(lm)?.let { world[lm] = it }
            transform(i, image, world)
            out.add(PoseFrameV2.of(f.tUs, f.posesDetected, image, if (world.isEmpty()) null else world))
        }
        return copy(frames = out)
    }

    /** Intercambia izquierda y derecha de los pares dados en el mapa de imagen (y en «world» si están). */
    fun swapPairs(
        image: MutableMap<PoseLandmark, LandmarkSample>,
        world: MutableMap<PoseLandmark, WorldSample>,
        pairs: List<Pair<PoseLandmark, PoseLandmark>>,
    ) {
        for ((l, r) in pairs) {
            val il = image[l]
            val ir = image[r]
            if (il != null && ir != null) {
                image[l] = ir
                image[r] = il
            }
            val wl = world[l]
            val wr = world[r]
            if (wl != null && wr != null) {
                world[l] = wr
                world[r] = wl
            }
        }
    }
}
