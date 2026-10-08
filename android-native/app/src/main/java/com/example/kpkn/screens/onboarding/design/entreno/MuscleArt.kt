package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kpkn.domain.onboarding.MuscleSymbol

/*
 * Dibujo de cada músculo popular: la región del músculo sobre una silueta de línea del cuerpo, encuadrada de cerca.
 *
 * Todo vive en el ESPACIO DEL CUERPO (x hacia la derecha de quien mira, eje en x = 0, y hacia abajo; cabeza = 50,
 * cuerpo = 400). La silueta es la misma para los doce: un trazo de cuerpo completo de frente y otro de espalda; lo que
 * cambia es la [MuscleWindow] (el encuadre, centrado en el músculo) y la región, escrita a mano como path SVG.
 * Los trazos de datos están en [MuscleArtPaths].
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

/** Los datos de un músculo (cadenas de path SVG): lo que prueban los tests y lo que se convierte en [MuscleShape]. */
internal class MuscleSpec(
    val view: BodyView,
    val window: MuscleWindow,
    /** La región del músculo (puede traer varios trazos: las dos mitades, los bloques del abdomen). */
    val region: String,
    /** Fibras y separaciones internas del músculo (tenues). */
    val detail: List<String>,
    /** Marcas del cuerpo que ayudan a ubicarlo (clavícula, rótula, columna…) (tenues). */
    val context: List<String>,
)

/** Un músculo listo para pintar: sus trazos ya convertidos a [Path] (se crean una sola vez y se reutilizan). */
internal class MuscleShape(val spec: MuscleSpec) {
    val region: Path = MuscleArt.parse(spec.region)
    val detail: List<Path> = spec.detail.map(MuscleArt::parse)
    val context: List<Path> = spec.context.map(MuscleArt::parse)

    /**
     * Todas las marcas de contexto y todas las fibras en un solo trazo cada una: se pintan con el mismo estilo, así que una llamada de
     * dibujo basta (antes eran hasta una veintena por celda, ciento sesenta entre los doce músculos). Un trazo único no acumula
     * opacidad donde dos líneas se cruzan: se ve igual o algo más limpio.
     */
    val contextAll: Path = Path().also { all -> context.forEach { all.addPath(it) } }
    val detailAll: Path = Path().also { all -> detail.forEach { all.addPath(it) } }
}

internal object MuscleArt {
    /** Alto del cuerpo en el espacio del cuerpo. */
    const val BODY_HEIGHT = 400f

    fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

    /** Los datos de [m]. */
    fun spec(m: MuscleSymbol): MuscleSpec = specs.getValue(m)

    private val specs: Map<MuscleSymbol, MuscleSpec> by lazy {
        mapOf(
            MuscleSymbol.CHEST to spec(
                MuscleArtPaths.CHEST_BACK, MuscleArtPaths.CHEST_WINDOW,
                MuscleArtPaths.CHEST_REGION, MuscleArtPaths.CHEST_DETAIL, MuscleArtPaths.CHEST_CONTEXT,
            ),
            MuscleSymbol.BACK to spec(
                MuscleArtPaths.BACK_BACK, MuscleArtPaths.BACK_WINDOW,
                MuscleArtPaths.BACK_REGION, MuscleArtPaths.BACK_DETAIL, MuscleArtPaths.BACK_CONTEXT,
            ),
            MuscleSymbol.SHOULDERS to spec(
                MuscleArtPaths.SHOULDERS_BACK, MuscleArtPaths.SHOULDERS_WINDOW,
                MuscleArtPaths.SHOULDERS_REGION, MuscleArtPaths.SHOULDERS_DETAIL, MuscleArtPaths.SHOULDERS_CONTEXT,
            ),
            MuscleSymbol.TRAPS to spec(
                MuscleArtPaths.TRAPS_BACK, MuscleArtPaths.TRAPS_WINDOW,
                MuscleArtPaths.TRAPS_REGION, MuscleArtPaths.TRAPS_DETAIL, MuscleArtPaths.TRAPS_CONTEXT,
            ),
            MuscleSymbol.BICEPS to spec(
                MuscleArtPaths.BICEPS_BACK, MuscleArtPaths.BICEPS_WINDOW,
                MuscleArtPaths.BICEPS_REGION, MuscleArtPaths.BICEPS_DETAIL, MuscleArtPaths.BICEPS_CONTEXT,
            ),
            MuscleSymbol.TRICEPS to spec(
                MuscleArtPaths.TRICEPS_BACK, MuscleArtPaths.TRICEPS_WINDOW,
                MuscleArtPaths.TRICEPS_REGION, MuscleArtPaths.TRICEPS_DETAIL, MuscleArtPaths.TRICEPS_CONTEXT,
            ),
            MuscleSymbol.FOREARMS to spec(
                MuscleArtPaths.FOREARMS_BACK, MuscleArtPaths.FOREARMS_WINDOW,
                MuscleArtPaths.FOREARMS_REGION, MuscleArtPaths.FOREARMS_DETAIL, MuscleArtPaths.FOREARMS_CONTEXT,
            ),
            MuscleSymbol.ABS to spec(
                MuscleArtPaths.ABS_BACK, MuscleArtPaths.ABS_WINDOW,
                MuscleArtPaths.ABS_REGION, MuscleArtPaths.ABS_DETAIL, MuscleArtPaths.ABS_CONTEXT,
            ),
            MuscleSymbol.GLUTES to spec(
                MuscleArtPaths.GLUTES_BACK, MuscleArtPaths.GLUTES_WINDOW,
                MuscleArtPaths.GLUTES_REGION, MuscleArtPaths.GLUTES_DETAIL, MuscleArtPaths.GLUTES_CONTEXT,
            ),
            MuscleSymbol.QUADS to spec(
                MuscleArtPaths.QUADS_BACK, MuscleArtPaths.QUADS_WINDOW,
                MuscleArtPaths.QUADS_REGION, MuscleArtPaths.QUADS_DETAIL, MuscleArtPaths.QUADS_CONTEXT,
            ),
            MuscleSymbol.HAMSTRINGS to spec(
                MuscleArtPaths.HAMSTRINGS_BACK, MuscleArtPaths.HAMSTRINGS_WINDOW,
                MuscleArtPaths.HAMSTRINGS_REGION, MuscleArtPaths.HAMSTRINGS_DETAIL, MuscleArtPaths.HAMSTRINGS_CONTEXT,
            ),
            MuscleSymbol.CALVES to spec(
                MuscleArtPaths.CALVES_BACK, MuscleArtPaths.CALVES_WINDOW,
                MuscleArtPaths.CALVES_REGION, MuscleArtPaths.CALVES_DETAIL, MuscleArtPaths.CALVES_CONTEXT,
            ),
        )
    }

    private fun spec(
        back: Boolean,
        window: FloatArray,
        region: String,
        detail: List<String>,
        context: List<String>,
    ): MuscleSpec = MuscleSpec(
        view = if (back) BodyView.BACK else BodyView.FRONT,
        window = MuscleWindow(window[0], window[1], window[2]),
        region = region,
        detail = detail,
        context = context,
    )

    private val shapes: Array<Lazy<MuscleShape>> = Array(MuscleSymbol.entries.size) { i ->
        lazy { MuscleShape(spec(MuscleSymbol.entries[i])) }
    }

    /** Los trazos de [m] como `Path` (creados la primera vez y reutilizados siempre). */
    fun shape(m: MuscleSymbol): MuscleShape = shapes[m.ordinal].value

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
