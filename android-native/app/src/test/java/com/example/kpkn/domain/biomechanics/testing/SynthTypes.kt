package com.example.kpkn.domain.biomechanics.testing

import com.example.kpkn.domain.biomechanics.kinematics.Side
import com.example.kpkn.domain.biomechanics.pose.PoseLandmark
import com.example.kpkn.domain.biomechanics.pose.PoseTrackV2

/**
 * Los 16 movimientos del generador. Cada uno define una postura de INICIO (reposo del ciclo) y otra de GIRO (el
 * extremo lejano), y su arranque: E (la excéntrica sale del reposo) o C (la concéntrica sale del reposo).
 *
 * | Movimiento | Arranque | Inicio → giro | Ángulo principal (flexión: 0° = extendido) |
 * |---|---|---|---|
 * | SQUAT | E | de pie → abajo | rodilla |
 * | LUNGE | E | arriba → abajo (sentadilla dividida) | rodilla de la pierna delantera |
 * | DEADLIFT | C | barra en el suelo → bloqueo | cadera |
 * | RDL | E | de pie → bisagra | cadera |
 * | HIP_THRUST | C | cadera abajo → arriba | cadera |
 * | BENCH | E | brazos extendidos → barra en el pecho | codo |
 * | PUSH_UP | E | brazos extendidos → pecho abajo | codo |
 * | OHP | C | barra en los hombros → bloqueo | codo |
 * | PULL_UP | C | colgado → barbilla sobre la barra | codo |
 * | LAT_PULLDOWN | C | brazos arriba → barra en el pecho | codo |
 * | ROW | C | brazos colgando → barra en el abdomen | codo |
 * | CURL | C | brazos abajo → codos flexionados | codo |
 * | TRICEPS_PUSHDOWN | C | codos flexionados → extendidos | codo |
 * | LATERAL_RAISE | C | brazos abajo → brazos a 90° | β (brazo respecto de la vertical) |
 * | LEG_EXTENSION | C | rodilla a 90° → extendida | rodilla |
 * | CALF_RAISE | C | talones abajo → de puntillas | flexión plantar del tobillo |
 */
enum class SynthMovement {
    SQUAT, LUNGE, DEADLIFT, RDL, HIP_THRUST, BENCH, PUSH_UP, OHP, PULL_UP, LAT_PULLDOWN, ROW, CURL,
    TRICEPS_PUSHDOWN, LATERAL_RAISE, LEG_EXTENSION, CALF_RAISE,
}

/**
 * Tipo de repetición. Los nombres vienen del brief y se definen por lo que hacen (b = 0 es el reposo, b = 1 el giro):
 * - [FULL]: del reposo al giro y vuelta (b: 0 → A → 0).
 * - [PARTIAL_FAR]: el 55 % del recorrido desde el inicio (b: 0 → 0,55·A → 0). Para el segmentador es una parcial que no
 *   llega al extremo lejano (REST_END: pico < 85 % de la amplitud).
 * - [PARTIAL_REST]: el 55 % alrededor del giro (b: 0,45·A ⇄ A, sin volver al reposo entre repeticiones). Para el
 *   segmentador, una parcial que sí llega al extremo lejano (FAR_END). Las dos etiquetas siguen al brief, que las define
 *   por lo que hacen; los nombres no coinciden con los del segmentador.
 * - [FAILED]: la fase concéntrica llega al 50 % de su recorrido, se queda quieta 0,8 s y vuelve al punto desde el que
 *   empezó esa fase (el reposo con arranque C; el giro con arranque E: la repetición termina abajo).
 *
 * Si una repetición termina fuera del reposo (PARTIAL_REST, FAILED con arranque E), el tramo siguiente que no sea
 * una repetición empieza con 1 s de recuperación al reposo.
 */
enum class SynthRepKind { FULL, PARTIAL_FAR, PARTIAL_REST, FAILED }

/**
 * Cámara estenopeica. [yawDeg] es ψ, el ángulo entre la dirección de mirada del atleta y la cámara: 90 = de lado con el
 * lado izquierdo del atleta hacia la cámara, −90 = el lado derecho, 0 = de frente, 180 = de espaldas.
 * [pitchDeg] > 0 inclina la cámara hacia arriba. [rollDeg] > 0 gira la IMAGEN en sentido horario (la vertical se ve
 * inclinada hacia la derecha por arriba): es el mismo convenio de `FrameGeometry.rollDeg`.
 * [hfovDeg] es el campo de visión a lo largo del ANCHO de la imagen; la cámara mira al punto del suelo donde está el
 * atleta al principio y [heightM] es su altura sobre el suelo.
 */
data class SynthCamera(
    val yawDeg: Double = 90.0,
    val pitchDeg: Double = 0.0,
    val rollDeg: Double = 0.0,
    val distanceM: Double = 3.0,
    val heightM: Double = 1.0,
    val hfovDeg: Double = 70.0,
    val widthPx: Int = 720,
    val heightPx: Int = 1280,
)

/** Guion de la serie: una lista de tramos que se encadenan en el tiempo. */
sealed interface SynthSegment {
    /** Quieto en la postura de inicio. */
    data class Idle(val seconds: Double) : SynthSegment

    /** Desplaza la raíz [dxM] metros hacia delante del atleta (con paso si el reposo es de pie). */
    data class Walk(val seconds: Double, val dxM: Double) : SynthSegment

    /** Saca la barra del soporte: manos y barra se mueven en horizontal, sin ciclo vertical completo. */
    data class Unrack(val seconds: Double = 1.5) : SynthSegment

    /** Devuelve la barra al soporte (lo contrario de [Unrack]). */
    data class Rerack(val seconds: Double = 1.5) : SynthSegment

    /** Descanso en la postura de inicio. */
    data class Rest(val seconds: Double) : SynthSegment

    /**
     * Una repetición. Las fases se nombran como en el segmentador: [eccS] excéntrica, [p1S] pausa del giro tras ella,
     * [conS] concéntrica, [p2S] pausa tras ella. Con arranque E el orden es EXC, P1, CON, P2 (reposo al final); con
     * arranque C es CON, P2 (arriba), EXC, P1 (reposo al final). [amplitude] escala el cambio de ángulo y [side] elige
     * el brazo o la pierna que trabaja (null = ambos; con `alternating` se reparte solo). Solo lo respetan los movimientos
     * de brazos independientes (CURL, LATERAL_RAISE, TRICEPS_PUSHDOWN, OHP, ROW, LAT_PULLDOWN, BENCH) y la zancada, donde
     * es la pierna delantera de toda la serie (la de la primera repetición); en los demás se ignora y la verdad lleva `null`.
     */
    data class Rep(
        val eccS: Double,
        val p1S: Double,
        val conS: Double,
        val p2S: Double,
        val amplitude: Double = 1.0,
        val side: Side? = null,
        val kind: SynthRepKind = SynthRepKind.FULL,
    ) : SynthSegment
}

/**
 * Ruido y defectos de captura, deterministas con la semilla.
 * - [sigmaFh]: gaussiano en x e y, en unidades FH (alto de fotograma = 1); [outlierRate]: fracción de puntos con un
 *   error ×10σ; [dropRate]: fracción de fotogramas sin pose; [ptsJitterMs]: fluctuación del PTS, triangular y acotada a
 *   ±ms (la pose se evalúa en el instante con fluctuación). A 30 fps con ±3 ms, P95/P5 de ΔPTS ≈ 1,2 (bajo el umbral VFR
 *   de 1,3); a 60 fps pasa de 1,3 y la traza sale VFR: baja el valor (≤ 1 ms) en las pruebas de 60 fps.
 * - [farSideVisibility]: visibilidad del lado lejano de lado (ψ = ±90); de frente es 0,95.
 * - [swapGlitches]: tramos de 2 a 5 fotogramas con las piernas cruzadas; [secondPersonFraction]: fracción de fotogramas
 *   con `posesDetected = 2`.
 * - [occlusionWindowsS] × [occludedLandmarks]: visibilidad 0,1 y posición con ruido ×5 (sobre max(σ, 0,002)).
 * - [worldSigmaM]: ruido de los puntos «world», en metros (2 cm).
 */
data class SynthNoise(
    val sigmaFh: Double = 0.004,
    val outlierRate: Double = 0.01,
    val dropRate: Double = 0.01,
    val ptsJitterMs: Double = 3.0,
    val farSideVisibility: Float = 0.3f,
    val swapGlitches: Int = 0,
    val secondPersonFraction: Double = 0.0,
    val occlusionWindowsS: List<Pair<Double, Double>> = emptyList(),
    val occludedLandmarks: Set<PoseLandmark> = emptySet(),
    val worldSigmaM: Double = 0.02,
) {
    companion object {
        /** Todo a 0 y visibilidad lejana 0,95: la traza limpia. */
        val NONE: SynthNoise = SynthNoise(
            sigmaFh = 0.0,
            outlierRate = 0.0,
            dropRate = 0.0,
            ptsJitterMs = 0.0,
            farSideVisibility = 0.95f,
            swapGlitches = 0,
            secondPersonFraction = 0.0,
            worldSigmaM = 0.0,
        )
    }
}

/** Todo lo que define una serie sintética. */
data class SynthConfig(
    val movement: SynthMovement,
    val heightM: Double = 1.75,
    val camera: SynthCamera = SynthCamera(),
    val fps: Double = 30.0,
    val script: List<SynthSegment>,
    val noise: SynthNoise = SynthNoise(),
    val seed: Long = 42L,
    val alternating: Boolean = false,
)

/**
 * Verdad de una repetición. Los tiempos están en µs sobre la misma base que `PoseFrameV2.tUs`: salida del reposo,
 * llegada al giro, salida del giro y llegada de vuelta. [romDeg] es el cambio del ángulo principal entre la salida y
 * el giro.
 */
data class SynthRepTruth(
    val index: Int,
    val kind: SynthRepKind,
    val side: Side?,
    val departUs: Long,
    val turnStartUs: Long,
    val turnEndUs: Long,
    val arriveUs: Long,
    val romDeg: Double,
)

/** Dónde cae en el tiempo (µs, base de `PoseFrameV2.tUs`) un tramo del guion, sin la recuperación automática que lo precede. */
data class SynthSpanTruth(val segment: SynthSegment, val startUs: Long, val endUs: Long)

/**
 * Resultado: la traza y su verdad.
 *
 * - [primaryAngleDeg]: el ángulo principal del movimiento en cada fotograma, como FLEXIÓN en grados (0 = extendido):
 *   rodilla = 180 − θ(H,K,A), cadera = 180 − θ(S,H,K), codo = 180 − θ(S,E,W), β = ángulo del brazo con la vertical hacia
 *   abajo y, en gemelos, la flexión plantar del pie. Es el del lado que trabaja en ese momento (el izquierdo en reposo).
 * - [cleanTrack]: la misma traza sin ruido, atípicos, pérdidas, cruces ni oclusiones (mismos instantes y visibilidades).
 * - [swapGlitchFrames] / [droppedFrames]: qué fotogramas llevan las piernas cruzadas o no tienen pose.
 * - [bodyHeightFh]: la altura corporal que se ve en el plano medio del atleta, en unidades FH (la escala S verdadera).
 *   Las articulaciones del lado cercano están unos 17–23 cm más cerca de la cámara y se ven algo mayores (≈ +4 % a 4,5 m,
 *   ≈ +6 % a 3 m).
 * - [spans]: el intervalo de tiempo de cada tramo del guion (para saber qué es caminar, sacar la barra o descansar).
 */
class SynthResult(
    val track: PoseTrackV2,
    val reps: List<SynthRepTruth>,
    val primaryAngleDeg: DoubleArray,
    val cleanTrack: PoseTrackV2 = track,
    val swapGlitchFrames: BooleanArray = BooleanArray(0),
    val droppedFrames: BooleanArray = BooleanArray(0),
    val bodyHeightFh: Double = Double.NaN,
    val spans: List<SynthSpanTruth> = emptyList(),
)
