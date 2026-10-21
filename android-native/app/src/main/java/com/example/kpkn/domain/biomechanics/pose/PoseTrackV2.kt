package com.example.kpkn.domain.biomechanics.pose

/** Punto de imagen: x,y normalizados (0..1) sobre el fotograma YA GIRADO de análisis (frameWidth×frameHeight). */
data class LandmarkSample(val x: Float, val y: Float, val z: Float, val visibility: Float, val presence: Float)

/** Punto «world» en metros, centrado en la cadera (solo para vista, lado cercano y respaldo en oblicuo). */
data class WorldSample(val x: Float, val y: Float, val z: Float)

/** Motor de pose que generó la traza: `("mediapipe-pose", "full", "GPU")`. */
data class PoseEngineInfo(val id: String, val model: String, val delegate: String)

/**
 * Un fotograma: la pose del atleta elegido (o ninguna) y cuántas poses vio el modelo.
 *
 * Por dentro no hay mapas: un [FloatArray] de 27×5 (x, y, z, visibilidad, presencia, en el orden de
 * [PoseLandmark.KEPT_IMAGE]) y otro de 12×3 (x, y, z, en el orden de [PoseLandmark.KEPT_WORLD]).
 * Un punto que falta se guarda con x = NaN. Dos fotogramas con los mismos datos son iguales.
 */
class PoseFrameV2 private constructor(
    val tUs: Long,
    val posesDetected: Int,
    private val img: FloatArray?,
    private val wld: FloatArray?,
) {
    /**
     * Fábrica a partir de mapas (los puntos fuera de `KEPT_*` se ignoran y los que faltan quedan vacíos).
     * [image] = null significa que no había atleta en este fotograma; [world] = null, que no hay puntos «world».
     */
    constructor(
        tUs: Long,
        posesDetected: Int,
        image: Map<PoseLandmark, LandmarkSample>?,
        world: Map<PoseLandmark, WorldSample>?,
    ) : this(tUs, posesDetected, packImage(image), packWorld(world))

    /** `true` si el modelo encontró al atleta en este fotograma. */
    val hasPose: Boolean get() = img != null

    /** Punto de imagen de [lm], o `null` si no se guarda, no hay pose o falta el dato. */
    fun point(lm: PoseLandmark): LandmarkSample? {
        val a = img ?: return null
        val slot = PoseLandmark.imageSlot(lm)
        if (slot < 0) return null
        val o = slot * IMAGE_STRIDE
        val x = a[o]
        if (x.isNaN()) return null
        return LandmarkSample(x, a[o + 1], a[o + 2], a[o + 3], a[o + 4])
    }

    /** Punto «world» de [lm], o `null` si no se guarda, no hay pose o falta el dato. */
    fun world(lm: PoseLandmark): WorldSample? {
        val a = wld ?: return null
        val slot = PoseLandmark.worldSlot(lm)
        if (slot < 0) return null
        val o = slot * WORLD_STRIDE
        val x = a[o]
        if (x.isNaN()) return null
        return WorldSample(x, a[o + 1], a[o + 2])
    }

    // Accesores sin asignación para los bucles calientes: `point(lm)` crea un objeto por llamada, estos no.
    // Devuelven NaN si el punto no se guarda, no hay pose o falta el dato.

    /** x normalizada (0..1) del punto de imagen [lm], o NaN. */
    fun x(lm: PoseLandmark): Float = imageAt(lm, 0)

    /** y normalizada (0..1, hacia abajo) del punto de imagen [lm], o NaN. */
    fun y(lm: PoseLandmark): Float = imageAt(lm, 1)

    /** Profundidad relativa a la cadera del punto de imagen [lm] (menor = más cerca de la cámara), o NaN. */
    fun z(lm: PoseLandmark): Float = imageAt(lm, 2)

    /** Visibilidad (0..1) del punto de imagen [lm], o NaN. */
    fun vis(lm: PoseLandmark): Float = imageAt(lm, 3)

    /** Presencia (0..1) del punto de imagen [lm], o NaN. */
    fun pres(lm: PoseLandmark): Float = imageAt(lm, 4)

    /** x «world» (m) de [lm], o NaN. */
    fun worldX(lm: PoseLandmark): Float = worldAt(lm, 0)

    /** y «world» (m) de [lm], o NaN. */
    fun worldY(lm: PoseLandmark): Float = worldAt(lm, 1)

    /** z «world» (m) de [lm], o NaN. */
    fun worldZ(lm: PoseLandmark): Float = worldAt(lm, 2)

    private fun imageAt(lm: PoseLandmark, k: Int): Float {
        val a = img ?: return Float.NaN
        val slot = PoseLandmark.imageSlot(lm)
        if (slot < 0) return Float.NaN
        val o = slot * IMAGE_STRIDE
        return if (a[o].isNaN()) Float.NaN else a[o + k]
    }

    private fun worldAt(lm: PoseLandmark, k: Int): Float {
        val a = wld ?: return Float.NaN
        val slot = PoseLandmark.worldSlot(lm)
        if (slot < 0) return Float.NaN
        val o = slot * WORLD_STRIDE
        return if (a[o].isNaN()) Float.NaN else a[o + k]
    }

    /** Datos crudos de imagen (27×5), solo para el códec. */
    internal fun imageData(): FloatArray? = img

    /** Datos crudos «world» (12×3), solo para el códec. */
    internal fun worldData(): FloatArray? = wld

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PoseFrameV2) return false
        return tUs == other.tUs && posesDetected == other.posesDetected &&
            (img contentEquals other.img) && (wld contentEquals other.wld)
    }

    override fun hashCode(): Int {
        var h = tUs.hashCode()
        h = 31 * h + posesDetected
        h = 31 * h + (img?.contentHashCode() ?: 0)
        h = 31 * h + (wld?.contentHashCode() ?: 0)
        return h
    }

    override fun toString(): String =
        "PoseFrameV2(tUs=$tUs, posesDetected=$posesDetected, hasPose=$hasPose, hasWorld=${wld != null})"

    companion object {
        /** Floats por punto de imagen: x, y, z, visibilidad, presencia. */
        internal const val IMAGE_STRIDE = 5

        /** Floats por punto «world»: x, y, z. */
        internal const val WORLD_STRIDE = 3

        /** Fábrica pública (ver el constructor con mapas). */
        fun of(
            tUs: Long,
            posesDetected: Int,
            image: Map<PoseLandmark, LandmarkSample>?,
            world: Map<PoseLandmark, WorldSample>?,
        ): PoseFrameV2 = PoseFrameV2(tUs, posesDetected, image, world)

        /**
         * Constructor interno a partir de los arrays ya empaquetados (27×5 y 12×3), para el códec.
         * No copia: el llamador no debe reutilizar los arrays.
         */
        internal fun fromArrays(tUs: Long, posesDetected: Int, image: FloatArray?, world: FloatArray?): PoseFrameV2 {
            require(image == null || image.size == PoseLandmark.KEPT_IMAGE.size * IMAGE_STRIDE) { "image: tamaño inválido" }
            require(world == null || world.size == PoseLandmark.KEPT_WORLD.size * WORLD_STRIDE) { "world: tamaño inválido" }
            return PoseFrameV2(tUs, posesDetected, image, world)
        }

        private fun packImage(image: Map<PoseLandmark, LandmarkSample>?): FloatArray? {
            if (image == null) return null
            val a = FloatArray(PoseLandmark.KEPT_IMAGE.size * IMAGE_STRIDE) { Float.NaN }
            for ((lm, s) in image) {
                val slot = PoseLandmark.imageSlot(lm)
                if (slot < 0) continue
                val o = slot * IMAGE_STRIDE
                a[o] = s.x
                a[o + 1] = s.y
                a[o + 2] = s.z
                a[o + 3] = s.visibility
                a[o + 4] = s.presence
            }
            return a
        }

        private fun packWorld(world: Map<PoseLandmark, WorldSample>?): FloatArray? {
            if (world == null) return null
            val a = FloatArray(PoseLandmark.KEPT_WORLD.size * WORLD_STRIDE) { Float.NaN }
            for ((lm, s) in world) {
                val slot = PoseLandmark.worldSlot(lm)
                if (slot < 0) continue
                val o = slot * WORLD_STRIDE
                a[o] = s.x
                a[o + 1] = s.y
                a[o + 2] = s.z
            }
            return a
        }
    }
}

/**
 * Traza de pose v2 de un vídeo (§3.1). Las coordenadas de imagen son normalizadas sobre el fotograma girado
 * de [frameWidth]×[frameHeight]; la geometría que mide ángulos DEBE corregir el aspecto con esas dimensiones.
 */
data class PoseTrackV2(
    val mediaId: String,
    val engine: PoseEngineInfo,
    val frameWidth: Int,
    val frameHeight: Int,                  // píxeles del fotograma girado que vio el estimador
    val rotationApplied: Int,              // 0/90/180/270 aplicada al decodificar
    val nominalFps: Float,                 // 1/mediana(ΔPTS) de los fotogramas con pose
    val poseStride: Int,                   // 1 = cada fotograma; 2 = uno de cada dos (60 fps)
    val vfr: Boolean,
    val captureFps: Float? = null,         // cámara lenta de galería
    val focalLengthPx: Float? = null,      // de CameraCharacteristics; null en galería
    val gravityRollDeg: Float? = null,
    val gravityPitchDeg: Float? = null,
    val aspectKnown: Boolean = true,       // false en trazas v1 → solo reps
    val frames: List<PoseFrameV2>,
    val version: Int = 2,
)
