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
 * cuerpo = 400). La silueta es la misma para todos (un trazo de cuerpo completo de frente y otro de espalda), salvo el brazo
 * flexionado del bíceps y el antebrazo con puño, que llevan su propio contorno. Los trazos de datos están en [MuscleArtPaths].
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

    /** Brazo: bíceps (flexionado, de frente), tríceps (extendido, de espalda) y antebrazo (con puño). */
    ARM(MuscleArtPaths.ARM_CONTEXT),

    /** Pierna de frente: cuádriceps. */
    LEG_FRONT(MuscleArtPaths.LEG_FRONT_CONTEXT),

    /** Pierna de espalda: glúteos, isquios y pantorrillas. */
    LEG_BACK(MuscleArtPaths.LEG_BACK_CONTEXT),
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
) {
    /** Las marcas del cuerpo de la familia. */
    val context: List<String> get() = family.context
}

/** Un músculo listo para pintar: sus trazos ya convertidos a [Path] (se crean una sola vez y se reutilizan). */
internal class MuscleShape(val spec: MuscleSpec) {
    val region: Path = MuscleArt.parse(spec.region)
    val detail: List<Path> = spec.detail.map(MuscleArt::parse)
    val context: List<Path> = spec.context.map(MuscleArt::parse)

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
                MuscleFamily.TORSO_BACK, MuscleArtPaths.BACK_BACK, MuscleWindow(0f, 148.7f, 130f),
                MuscleArtPaths.BACK_REGION, MuscleArtPaths.BACK_DETAIL, fiberAngleDeg = -75f, origin = Offset(0f, 182f),
            ),
            MuscleSymbol.SHOULDERS to spec(
                MuscleFamily.TORSO_FRONT, MuscleArtPaths.SHOULDERS_BACK, MuscleWindow(0f, 100f, 150f),
                MuscleArtPaths.SHOULDERS_REGION, MuscleArtPaths.SHOULDERS_DETAIL, fiberAngleDeg = 80f, origin = Offset(0f, 78f),
            ),
            MuscleSymbol.BICEPS to spec(
                MuscleFamily.ARM, MuscleArtPaths.BICEPS_BACK, MuscleWindow(50.55f, 126.25f, 108f),
                MuscleArtPaths.BICEPS_REGION, MuscleArtPaths.BICEPS_DETAIL, fiberAngleDeg = 76f, origin = Offset(45f, 102f),
                outline = MuscleArtPaths.BICEPS_OUTLINE,
            ),
            MuscleSymbol.TRICEPS to spec(
                MuscleFamily.ARM, MuscleArtPaths.TRICEPS_BACK, MuscleWindow(50.95f, 128.35f, 80f),
                MuscleArtPaths.TRICEPS_REGION, MuscleArtPaths.TRICEPS_DETAIL, fiberAngleDeg = 90f, origin = Offset(51f, 100f),
            ),
            MuscleSymbol.FOREARMS to spec(
                MuscleFamily.ARM, MuscleArtPaths.FOREARMS_BACK, MuscleWindow(56.2f, 186.15f, 100f),
                MuscleArtPaths.FOREARMS_REGION, MuscleArtPaths.FOREARMS_DETAIL, fiberAngleDeg = 90f, origin = Offset(56f, 160f),
                outline = MuscleArtPaths.FOREARMS_OUTLINE,
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
    ): MuscleSpec = MuscleSpec(
        view = if (back) BodyView.BACK else BodyView.FRONT,
        family = family,
        window = window,
        region = region,
        detail = detail,
        fiberAngleDeg = fiberAngleDeg,
        origin = origin,
        outline = outline,
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
