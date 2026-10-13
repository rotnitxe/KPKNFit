package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kpkn.domain.onboarding.MuscleSymbol
import kotlin.math.cos
import kotlin.math.sin

/*
 * Dibujo de cada músculo popular: la región del músculo sobre una silueta tenue del cuerpo, con marcas. Cada músculo lleva
 * SU encuadre, centrado en él, para que se lea a 60 dp: el músculo protagonista ocupa al menos el 40 % del ancho o del alto
 * de la celda. Las familias (torso de frente, torso de espalda, brazo, pierna de frente, pierna de espalda) comparten la vista,
 * el estilo de la silueta de contexto, las marcas del cuerpo y el grosor de trazo; no el encuadre.
 *
 * Todo vive en el ESPACIO DEL CUERPO (x hacia la derecha de quien mira, eje en x = 0, y hacia abajo; cabeza = 50,
 * cuerpo = 400). La silueta es la misma para todos (un trazo de cuerpo completo de frente y otro de espalda), salvo los gestos
 * del brazo (doble bíceps de perfil y antebrazo con puño), que llevan su propio contorno. Los trazos de datos están
 * en [MuscleArtPaths]. Los gestos del brazo van en las mismas unidades del lienzo de 64, que fijan su encuadre.
 */

/** Desde dónde se ve el cuerpo. */
internal enum class BodyView { FRONT, BACK }

/** Encuadre cuadrado en el espacio del cuerpo: centro y lado. */
internal class MuscleWindow(val cx: Float, val cy: Float, val size: Float) {
    val left: Float get() = cx - size / 2f
    val right: Float get() = cx + size / 2f
    val top: Float get() = cy - size / 2f
    val bottom: Float get() = cy + size / 2f
}

/**
 * Familia de músculos: comparten la vista, el estilo de la silueta de contexto y las marcas del cuerpo (clavículas,
 * columna, codo, rótula, pliegue del glúteo). No comparten encuadre: cada músculo lleva el suyo.
 */
internal enum class MuscleFamily(val context: List<String>) {
    /** Torso de frente: pecho, hombros y abdomen. */
    TORSO_FRONT(MuscleArtPaths.TORSO_FRONT_CONTEXT),

    /** Torso de espalda: espalda. */
    TORSO_BACK(MuscleArtPaths.TORSO_BACK_CONTEXT),

    /** Brazo: Brazos (bíceps y tríceps en perfil de «doble bíceps») y antebrazo (con puño). */
    ARM(MuscleArtPaths.ARM_CONTEXT),

    /** Pierna de frente: cuádriceps. */
    LEG_FRONT(MuscleArtPaths.LEG_FRONT_CONTEXT),

    /** Pierna de espalda: glúteos, isquios y pantorrillas. */
    LEG_BACK(MuscleArtPaths.LEG_BACK_CONTEXT),
}

/** Cómo se mueve la pieza móvil de un músculo al contraerse ([MuscleSpec.moving]), aparte de la región. */
internal enum class PartMotion {
    /** Sin movimiento propio: la pieza va con la silueta. */
    NONE,

    /** La muñeca flexiona: gira [MuscleArt.WRIST_FLEX_DEG] alrededor de [MuscleSpec.motionPivot]. */
    WRIST_FLEX,

    /** El codo cierra: el antebrazo gira [MuscleArt.ELBOW_CLOSE_DEG] alrededor del codo ([MuscleSpec.motionPivot]). */
    ELBOW_CLOSE,

    /** Los omóplatos se juntan hacia la columna: se acercan a x = 0 en [MuscleArt.SCAPULA_TUCK] de su distancia. */
    SCAPULA_IN,
}

/**
 * Los datos de un músculo: su vista, su familia (marcas), su encuadre propio, su región y fibras, y cómo se contrae: la
 * dirección de las fibras y el origen alrededor del cual se acorta. [outline] es el contorno propio del gesto (el brazo
 * flexionado o el puño); si no lo hay, la silueta del cuerpo de su vista.
 */
internal class MuscleSpec(
    val view: BodyView,
    val family: MuscleFamily,
    /** El encuadre de este músculo, centrado en él. */
    val window: MuscleWindow,
    /** La región del músculo (puede traer varios trazos: las dos mitades, los bloques del abdomen). */
    val region: String,
    /** Fibras y separaciones internas del músculo (tenues). */
    val detail: List<String>,
    /**
     * Dirección de las fibras en grados (0 = hacia la derecha, 90 = hacia abajo). Un único Matrix mueve las dos mitades de
     * un músculo doble: por eso los bilaterales van casi en vertical u horizontal y su contracción queda simétrica.
     */
    val fiberAngleDeg: Float,
    /** Origen del músculo: la contracción se acorta hacia él y él no se mueve. */
    val origin: Offset,
    /** Contorno propio del gesto (brazo flexionado, antebrazo con puño); `null` = silueta del cuerpo. */
    val outline: String? = null,
    /** La parte de [region] que lleva el acento entero (Brazos: el bíceps); el resto de la región va al 55 %. */
    val accentPart: String? = null,
    /** Pieza móvil del gesto (puño y mango, omóplatos): se dibuja aparte y se mueve según [motion]. */
    val moving: String? = null,
    /** Cómo se mueve la pieza móvil al contraerse. */
    val motion: PartMotion = PartMotion.NONE,
    /** Pivote del movimiento de la pieza móvil (la muñeca). */
    val motionPivot: Offset = Offset.Zero,
) {
    /** Las marcas del cuerpo de la familia. */
    val context: List<String> get() = family.context
}

/** Un músculo listo para pintar: sus trazos ya convertidos a [Path] (se crean una sola vez y se reutilizan). */
internal class MuscleShape(val spec: MuscleSpec) {
    val region: Path = MuscleArt.parse(spec.region)
    val detail: List<Path> = spec.detail.map(MuscleArt::parse)
    val context: List<Path> = spec.context.map(MuscleArt::parse)

    /** La parte de acento entero, si la hay (Brazos: el bíceps). */
    val accentPart: Path? = spec.accentPart?.let(MuscleArt::parse)

    /** La pieza móvil del gesto, si la hay (puño con mango, omóplatos). */
    val moving: Path? = spec.moving?.let(MuscleArt::parse)

    /** El contorno de contexto de este músculo: su gesto propio o la silueta del cuerpo de su vista (compartida). */
    val silhouette: Path = spec.outline?.let(MuscleArt::parse) ?: MuscleArt.silhouette(spec.view)

    /**
     * Todas las marcas de contexto y todas las fibras en un solo trazo cada una: se pintan con el mismo estilo, así que una llamada de
     * dibujo basta (antes eran hasta una veintena por celda y bastantes más en total). Un trazo único no acumula
     * opacidad donde dos líneas se cruzan: se ve igual o algo más limpio.
     */
    val contextAll: Path = Path().also { all -> context.forEach { all.addPath(it) } }
    val detailAll: Path = Path().also { all -> detail.forEach { all.addPath(it) } }
}

internal object MuscleArt {
    /** Alto del cuerpo en el espacio del cuerpo. */
    const val BODY_HEIGHT = 400f

    /** Contracción: el músculo se acorta esta fracción de su largo, en la dirección de sus fibras… */
    const val CONTRACT_ALONG = 0.06f

    /** …y se ensancha esta fracción en perpendicular. */
    const val CONTRACT_ACROSS = 0.05f

    /** Ángulo de la muñeca al flexionar, en grados (negativo: hacia la izquierda de quien mira). */
    const val WRIST_FLEX_DEG = -10f

    /** Ángulo con el que cierra el codo de Brazos al contraerse, en grados (negativo: el antebrazo sube hacia el hombro). */
    const val ELBOW_CLOSE_DEG = -12f

    /** Fracción en que los omóplatos se acercan a la columna al contraerse. */
    const val SCAPULA_TUCK = 0.15f

    fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

    /** Los datos de [m]. */
    fun spec(m: MuscleSymbol): MuscleSpec = specs.getValue(m)

    private val specs: Map<MuscleSymbol, MuscleSpec> by lazy {
        mapOf(
            MuscleSymbol.CHEST to spec(
                MuscleFamily.TORSO_FRONT, MuscleArtPaths.CHEST_BACK, MuscleWindow(0f, 97f, 120f),
                MuscleArtPaths.CHEST_REGION, MuscleArtPaths.CHEST_DETAIL, fiberAngleDeg = -15f, origin = Offset(0f, 104f),
            ),
            MuscleSymbol.BACK to spec(
                MuscleFamily.TORSO_BACK, MuscleArtPaths.BACK_BACK, MuscleWindow(0f, 117f, 130f),
                MuscleArtPaths.BACK_REGION, MuscleArtPaths.BACK_DETAIL, fiberAngleDeg = -75f, origin = Offset(0f, 117f),
                moving = MuscleArtPaths.BACK_SCAPULAE, motion = PartMotion.SCAPULA_IN,
            ),
            MuscleSymbol.SHOULDERS to spec(
                MuscleFamily.TORSO_FRONT, MuscleArtPaths.SHOULDERS_BACK, MuscleWindow(0f, 100f, 150f),
                MuscleArtPaths.SHOULDERS_REGION, MuscleArtPaths.SHOULDERS_DETAIL, fiberAngleDeg = 80f, origin = Offset(0f, 78f),
            ),
            MuscleSymbol.ARMS to spec(
                MuscleFamily.ARM, MuscleArtPaths.ARMS_BACK, MuscleWindow(28f, 34f, 58f),
                MuscleArtPaths.ARMS_REGION, MuscleArtPaths.ARMS_DETAIL, fiberAngleDeg = 0f, origin = Offset(6f, 40f),
                outline = MuscleArtPaths.ARMS_OUTLINE, accentPart = MuscleArtPaths.ARMS_BICEPS_REGION,
                moving = MuscleArtPaths.ARMS_FOREARM, motion = PartMotion.ELBOW_CLOSE, motionPivot = Offset(46f, 47f),
            ),
            MuscleSymbol.FOREARMS to spec(
                MuscleFamily.ARM, MuscleArtPaths.FOREARMS_BACK, MuscleWindow(32f, 39.5f, 72f),
                MuscleArtPaths.FOREARMS_REGION, MuscleArtPaths.FOREARMS_DETAIL, fiberAngleDeg = 90f, origin = Offset(32f, 60f),
                outline = MuscleArtPaths.FOREARMS_OUTLINE, moving = MuscleArtPaths.FOREARMS_MOVING,
                motion = PartMotion.WRIST_FLEX, motionPivot = Offset(32f, 18f),
            ),
            MuscleSymbol.ABS to spec(
                MuscleFamily.TORSO_FRONT, MuscleArtPaths.ABS_BACK, MuscleWindow(0f, 154.2f, 92f),
                MuscleArtPaths.ABS_REGION, MuscleArtPaths.ABS_DETAIL, fiberAngleDeg = 90f, origin = Offset(0f, 184f),
            ),
            MuscleSymbol.GLUTES to spec(
                MuscleFamily.LEG_BACK, MuscleArtPaths.GLUTES_BACK, MuscleWindow(0f, 212.4f, 112f),
                MuscleArtPaths.GLUTES_REGION, MuscleArtPaths.GLUTES_DETAIL, fiberAngleDeg = 75f, origin = Offset(0f, 186f),
            ),
            MuscleSymbol.QUADS to spec(
                MuscleFamily.LEG_FRONT, MuscleArtPaths.QUADS_BACK, MuscleWindow(0f, 259.5f, 112f),
                MuscleArtPaths.QUADS_REGION, MuscleArtPaths.QUADS_DETAIL, fiberAngleDeg = 90f, origin = Offset(0f, 226f),
            ),
            MuscleSymbol.HAMSTRINGS to spec(
                MuscleFamily.LEG_BACK, MuscleArtPaths.HAMSTRINGS_BACK, MuscleWindow(0f, 266.1f, 110f),
                MuscleArtPaths.HAMSTRINGS_REGION, MuscleArtPaths.HAMSTRINGS_DETAIL, fiberAngleDeg = 90f, origin = Offset(0f, 240f),
            ),
            MuscleSymbol.CALVES to spec(
                MuscleFamily.LEG_BACK, MuscleArtPaths.CALVES_BACK, MuscleWindow(0f, 329f, 92f),
                MuscleArtPaths.CALVES_REGION, MuscleArtPaths.CALVES_DETAIL, fiberAngleDeg = 90f, origin = Offset(0f, 304f),
            ),
        )
    }

    private fun spec(
        family: MuscleFamily,
        back: Boolean,
        window: MuscleWindow,
        region: String,
        detail: List<String>,
        fiberAngleDeg: Float,
        origin: Offset,
        outline: String? = null,
        accentPart: String? = null,
        moving: String? = null,
        motion: PartMotion = PartMotion.NONE,
        motionPivot: Offset = Offset.Zero,
    ): MuscleSpec = MuscleSpec(
        view = if (back) BodyView.BACK else BodyView.FRONT,
        family = family,
        window = window,
        region = region,
        detail = detail,
        fiberAngleDeg = fiberAngleDeg,
        origin = origin,
        outline = outline,
        accentPart = accentPart,
        moving = moving,
        motion = motion,
        motionPivot = motionPivot,
    )

    private val shapes: Array<Lazy<MuscleShape>> = Array(MuscleSymbol.entries.size) { i ->
        lazy { MuscleShape(spec(MuscleSymbol.entries[i])) }
    }

    /** Los trazos de [m] como `Path` (creados la primera vez y reutilizados siempre). */
    fun shape(m: MuscleSymbol): MuscleShape = shapes[m.ordinal].value

    /**
     * Escribe en [out] la contracción de [spec] con intensidad [k] (0 = reposo, 1 = contraído): el músculo se acorta
     * [CONTRACT_ALONG] en la dirección de sus fibras, alrededor de su origen, y se ensancha [CONTRACT_ACROSS] en
     * perpendicular. Es una transformación afín 2D que se escribe en un Matrix reutilizado: al pintar no se crea nada.
     */
    fun contractInto(spec: MuscleSpec, k: Float, out: Matrix) {
        out.reset()
        val sAlong = 1f - CONTRACT_ALONG * k
        val sAcross = 1f + CONTRACT_ACROSS * k
        val th = spec.fiberAngleDeg * DEG
        val c = cos(th)
        val s = sin(th)
        // R(θ) · diag(sAlong, sAcross) · R(−θ): escala en el eje de las fibras sin girar el músculo.
        val a = sAlong * c * c + sAcross * s * s
        val b = (sAlong - sAcross) * c * s
        val d = sAlong * s * s + sAcross * c * c
        val ox = spec.origin.x
        val oy = spec.origin.y
        // Columna mayor, como `Matrix`: x' = a·x + b·y + e, y' = b·x + d·y + f; (e, f) deja el origen quieto.
        val v = out.values
        v[0] = a
        v[1] = b
        v[4] = b
        v[5] = d
        v[12] = ox - (a * ox + b * oy)
        v[13] = oy - (b * ox + d * oy)
    }

    /**
     * Escribe en [out] el movimiento propio de la pieza móvil de [spec] con intensidad [k] (0 = reposo, 1 = contraído): la
     * muñeca gira alrededor de su pivote, o los omóplatos se acercan a la columna (escala en x alrededor de x = 0). Sin
     * movimiento propio, la identidad. Como [contractInto], escribe en un Matrix reutilizado.
     */
    fun motionInto(spec: MuscleSpec, k: Float, out: Matrix) {
        out.reset()
        when (spec.motion) {
            PartMotion.NONE -> Unit
            PartMotion.WRIST_FLEX -> rotateInto(WRIST_FLEX_DEG * k, spec.motionPivot, out)
            PartMotion.ELBOW_CLOSE -> rotateInto(ELBOW_CLOSE_DEG * k, spec.motionPivot, out)
            PartMotion.SCAPULA_IN -> out.values[0] = 1f - SCAPULA_TUCK * k
        }
    }

    /** Escribe en [out] una rotación de [deg] grados alrededor de [pivot], con el mismo convenio que [contractInto]. */
    private fun rotateInto(deg: Float, pivot: Offset, out: Matrix) {
        val th = deg * DEG
        val c = cos(th)
        val s = sin(th)
        val v = out.values
        v[0] = c
        v[1] = s
        v[4] = -s
        v[5] = c
        // x' = c·(x − px) − s·(y − py) + px; y' = s·(x − px) + c·(y − py) + py.
        v[12] = pivot.x - c * pivot.x + s * pivot.y
        v[13] = pivot.y - s * pivot.x - c * pivot.y
    }

    private val frontSilhouette: Lazy<Path> = lazy { silhouettePath() }
    private val backSilhouette: Lazy<Path> = lazy { silhouettePath() }

    /**
     * La silueta de [view]: contorno del cuerpo y cabeza en un solo `Path`, compartido por los músculos de esa vista
     * (uno por vista, no uno por músculo).
     */
    fun silhouette(view: BodyView): Path = when (view) {
        BodyView.FRONT -> frontSilhouette.value
        BodyView.BACK -> backSilhouette.value
    }

    private fun silhouettePath(): Path = Path().apply {
        addPath(parse(MuscleArtPaths.OUTLINE))
        addPath(parse(MuscleArtPaths.HEAD))
    }
}
