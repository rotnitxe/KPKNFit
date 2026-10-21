package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseEngineInfo
import com.example.kpkn.domain.biomechanics.pose.PoseFrameV2
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Generador sintético de levantamientos: la herramienta de pruebas de toda la ola VT (conteo, fases, métricas, disco,
 * canal de análisis). Produce una [PoseTrackV2] como la que daría el estimador, más la verdad de cada repetición.
 *
 * Cómo funciona:
 * 1. **Cuerpo 3D** con la antropometría de Drillis–Contini a partir de la altura ([Anthro]); ejes del atleta X
 *    adelante, Y arriba, Z a su izquierda. Cada movimiento ([SynthMovements]) calcula una postura de longitudes
 *    exactas (cadenas de dos segmentos) a partir de un progreso b (0 = reposo, 1 = giro).
 * 2. **Tiempo:** el guion ([SynthSegment]) se compila en tramos de jerk mínimo s(τ) = 10τ³ − 15τ⁴ + 6τ⁵ ([SynthTimeline]).
 * 3. **Cámara** estenopeica con yaw, pitch, roll, distancia y campo de visión ([CameraModel]); salida: x e y normalizados
 *    sobre el fotograma, z = profundidad relativa a la cadera (misma escala que x), visibilidad (0,95 en el lado
 *    cercano, hasta [SynthNoise.farSideVisibility] en el lejano de lado; la cara baja si mira al otro lado), presencia 0,99 y
 *    puntos «world» (3D − centro de las caderas, en ejes de cámara, + ruido de 2 cm).
 * 4. **Ruido y defectos** deterministas con la semilla; cada defecto tiene su propia secuencia aleatoria, así que
 *    activar uno no cambia la realización de los demás.
 * 5. **Verdad:** salida, inicio y fin del giro, llegada y ROM por repetición, y el ángulo principal en cada fotograma.
 */
object SyntheticLifter {
    private const val NEAR_VISIBILITY = 0.95
    private const val MIN_FACE_VISIBILITY = 0.1
    private const val PRESENCE = 0.99f

    fun generate(config: SynthConfig): SynthResult {
        val anthro = Anthro(config.heightM)
        val timeline = SynthTimeline.build(config, anthro)
        val cam = CameraModel(config.camera)
        val noise = config.noise
        val fps = config.fps
        val n = floor(timeline.totalSeconds * fps + 1e-9).toInt() + 1
        val aspect = cam.widthPx / cam.heightPx
        val sinPsi = kotlin.math.sin(Math.toRadians(config.camera.yawDeg))

        val rngPts = rng(config.seed, 1)
        val rngNoise = rng(config.seed, 2)
        val rngWorld = rng(config.seed, 3)
        val rngDrop = rng(config.seed, 4)
        val rngSecond = rng(config.seed, 5)
        val glitches = pickGlitches(n, noise.swapGlitches, rng(config.seed, 6))

        val frames = ArrayList<PoseFrameV2>(n)
        val clean = ArrayList<PoseFrameV2>(n)
        val angles = DoubleArray(n)
        val dropped = BooleanArray(n)
        val tmp = DoubleArray(3)
        val keptImage = PoseLandmark.KEPT_IMAGE
        val keptWorld = PoseLandmark.KEPT_WORLD
        val maxJitterS = noise.ptsJitterMs / 1000.0

        for (k in 0 until n) {
            // Fluctuación triangular acotada a ±ptsJitterMs (suma de dos uniformes): el PTS nunca se desvía más de lo pedido.
            val jitter = if (k == 0 || maxJitterS <= 0.0) 0.0 else (rngPts.nextDouble() + rngPts.nextDouble() - 1.0) * maxJitterS
            val t = max(0.0, k / fps + jitter)
            val tUs = (t * 1e6).roundToLong()

            val st = timeline.stateAt(t)
            val post = SynthMovements.posture(config.movement, st, anthro, timeline.frontSide)
            val local = SynthBody.joints(post, anthro)
            val joints = local.translated(st.rootX)
            val hipMid = V3(post.hip.x + st.rootX, post.hip.y, 0.0)
            val zHip = max(cam.depth(hipMid), 0.05)

            val primarySide = when {
                config.movement == SynthMovement.LUNGE -> timeline.frontSide
                st.bR > st.bL + 1e-9 -> Side.RIGHT
                else -> Side.LEFT
            }
            angles[k] = SynthMovements.primaryAngleDeg(config.movement, joints, primarySide, anthro)

            // Dirección de la cara frente a la cámara: la nariz y las orejas bajan si mira hacia el otro lado.
            val face = SynthBody.faceDirection(post)
            val nose = joints[PoseLandmark.NOSE]
            val toCamera = (cam.position - nose).unit()
            val faceVisibilityFactor = faceFactor(face.dot(toCamera))

            val imgClean = FloatArray(keptImage.size * 5)
            for ((slot, lm) in keptImage.withIndex()) {
                cam.project(joints[lm], tmp)
                val o = slot * 5
                imgClean[o] = tmp[0].toFloat()
                imgClean[o + 1] = tmp[1].toFloat()
                imgClean[o + 2] = ((tmp[2] - zHip) * (cam.focalPx / zHip) / cam.widthPx).toFloat()
                imgClean[o + 3] = visibility(lm, sinPsi, noise.farSideVisibility, faceVisibilityFactor).toFloat()
                imgClean[o + 4] = PRESENCE
            }
            val wldClean = FloatArray(keptWorld.size * 3)
            for ((slot, lm) in keptWorld.withIndex()) {
                cam.worldOf(joints[lm], hipMid, tmp)
                val o = slot * 3
                wldClean[o] = tmp[0].toFloat()
                wldClean[o + 1] = tmp[1].toFloat()
                wldClean[o + 2] = tmp[2].toFloat()
            }
            clean.add(PoseFrameV2.fromArrays(tUs, 1, imgClean, wldClean))

            // Defectos de captura sobre copias de la traza limpia. Siempre se consumen las mismas muestras aleatorias.
            val img = imgClean.copyOf()
            val wld = wldClean.copyOf()
            for ((slot, lm) in keptImage.withIndex()) {
                val gx = rngNoise.nextGaussian()
                val gy = rngNoise.nextGaussian()
                val gz = rngNoise.nextGaussian()
                val outlier = rngNoise.nextDouble() < noise.outlierRate
                var sigma = noise.sigmaFh * (if (outlier) 10.0 else 1.0)
                val o = slot * 5
                if (isOccluded(noise, lm, t)) {
                    sigma = max(sigma, 5.0 * max(noise.sigmaFh, 0.002))
                    img[o + 3] = 0.1f
                }
                img[o] += (sigma * gx / aspect).toFloat()
                img[o + 1] += (sigma * gy).toFloat()
                img[o + 2] += (sigma * gz / aspect).toFloat()
            }
            for (i in wld.indices) wld[i] += (noise.worldSigmaM * rngWorld.nextGaussian()).toFloat()

            val drop = rngDrop.nextDouble() < noise.dropRate
            val second = rngSecond.nextDouble() < noise.secondPersonFraction
            if (drop) {
                dropped[k] = true
                frames.add(PoseFrameV2.fromArrays(tUs, 0, null, null))
            } else {
                if (glitches[k]) swapLegs(img, wld)
                frames.add(PoseFrameV2.fromArrays(tUs, if (second) 2 else 1, img, wld))
            }
        }

        val glitchFrames = BooleanArray(n) { glitches[it] && !dropped[it] }
        val bodyHeightFh = (cam.focalPx / cam.heightPx) * anthro.height / max(cam.depth(V3(0.0, 0.0, 0.0)), 0.05)
        val header = { list: List<PoseFrameV2> ->
            PoseTrackV2(
                mediaId = "synthetic-${config.movement.name.lowercase()}",
                engine = PoseEngineInfo("synthetic", "lifter", "CPU"),
                frameWidth = config.camera.widthPx,
                frameHeight = config.camera.heightPx,
                rotationApplied = 0,
                nominalFps = fps.toFloat(),
                poseStride = 1,
                vfr = false,
                captureFps = null,
                focalLengthPx = cam.focalPx.toFloat(),
                gravityRollDeg = config.camera.rollDeg.toFloat(),
                gravityPitchDeg = config.camera.pitchDeg.toFloat(),
                aspectKnown = true,
                frames = list,
            )
        }
        return SynthResult(
            track = header(frames),
            reps = timeline.reps,
            primaryAngleDeg = angles,
            cleanTrack = header(clean),
            swapGlitchFrames = glitchFrames,
            droppedFrames = dropped,
            bodyHeightFh = bodyHeightFh,
            spans = timeline.spans,
        )
    }

    /** Secuencia aleatoria propia de cada defecto, derivada de la semilla. */
    private fun rng(seed: Long, salt: Int): Random = Random(seed * -7046029254386353131L + salt * 1442695040888963407L)

    private fun sideOf(lm: PoseLandmark): Side? = when (lm) {
        PoseLandmark.NOSE, PoseLandmark.MOUTH_LEFT, PoseLandmark.MOUTH_RIGHT -> null
        else -> when {
            lm.name.startsWith("LEFT_") -> Side.LEFT
            lm.name.startsWith("RIGHT_") -> Side.RIGHT
            else -> null
        }
    }

    private fun isFace(lm: PoseLandmark) = lm == PoseLandmark.NOSE || lm == PoseLandmark.LEFT_EAR ||
        lm == PoseLandmark.RIGHT_EAR || lm == PoseLandmark.MOUTH_LEFT || lm == PoseLandmark.MOUTH_RIGHT

    /** 0,95 en el lado cercano; en el lejano, de 0,95 (de frente) a [farVisibility] (de lado); la cara baja si mira hacia otro lado. */
    private fun visibility(lm: PoseLandmark, sinPsi: Double, farVisibility: Float, faceFactor: Double): Double {
        val side = sideOf(lm)
        var v = NEAR_VISIBILITY
        if (side != null) {
            val near = (side == Side.LEFT && sinPsi >= 0.0) || (side == Side.RIGHT && sinPsi <= 0.0)
            if (!near) v = NEAR_VISIBILITY - (NEAR_VISIBILITY - farVisibility) * abs(sinPsi)
        }
        if (isFace(lm)) v *= faceFactor
        return v
    }

    /** 1 si la cara mira hacia la cámara o de perfil (cos ≥ −0,1); baja hasta 0,1/0,95 con la cara de espaldas (cos ≤ −0,6). */
    private fun faceFactor(dot: Double): Double {
        val floorRatio = MIN_FACE_VISIBILITY / NEAR_VISIBILITY
        return when {
            dot >= -0.1 -> 1.0
            dot <= -0.6 -> floorRatio
            else -> 1.0 + (dot + 0.1) / 0.5 * (1.0 - floorRatio)
        }
    }

    private fun isOccluded(noise: SynthNoise, lm: PoseLandmark, t: Double): Boolean {
        if (lm !in noise.occludedLandmarks) return false
        for ((a, b) in noise.occlusionWindowsS) if (t >= a && t <= b) return true
        return false
    }

    /** Fotogramas con las piernas cruzadas: [count] tramos de 2 a 5 fotogramas, sin tocarse, lejos de los bordes. */
    private fun pickGlitches(n: Int, count: Int, rng: Random): BooleanArray {
        val flags = BooleanArray(n)
        if (count <= 0 || n < 40) return flags
        var placed = 0
        var attempts = 0
        while (placed < count && attempts < 2000) {
            attempts++
            val length = 2 + rng.nextInt(4)
            val start = 10 + rng.nextInt(n - 20 - length)
            var free = true
            for (i in (start - 8)..(start + length + 8)) if (i in 0 until n && flags[i]) free = false
            if (!free) continue
            for (i in start until start + length) flags[i] = true
            placed++
        }
        return flags
    }

    private val LEG_PAIRS = arrayOf(
        PoseLandmark.LEFT_KNEE to PoseLandmark.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE to PoseLandmark.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL to PoseLandmark.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX to PoseLandmark.RIGHT_FOOT_INDEX,
    )

    /** Cruza las piernas: intercambia izquierda y derecha (imagen y «world») de rodilla, tobillo, talón y punta. */
    private fun swapLegs(img: FloatArray, wld: FloatArray) {
        for ((l, r) in LEG_PAIRS) {
            swap(img, PoseLandmark.KEPT_IMAGE.indexOf(l), PoseLandmark.KEPT_IMAGE.indexOf(r), 5)
            val wl = PoseLandmark.KEPT_WORLD.indexOf(l)
            val wr = PoseLandmark.KEPT_WORLD.indexOf(r)
            if (wl >= 0 && wr >= 0) swap(wld, wl, wr, 3)
        }
    }

    private fun swap(a: FloatArray, slotA: Int, slotB: Int, stride: Int) {
        for (q in 0 until stride) {
            val tmp = a[slotA * stride + q]
            a[slotA * stride + q] = a[slotB * stride + q]
            a[slotB * stride + q] = tmp
        }
    }
}
