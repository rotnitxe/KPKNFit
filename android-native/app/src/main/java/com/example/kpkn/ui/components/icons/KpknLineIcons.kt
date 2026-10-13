package com.example.kpkn.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Set KPKN Line: los iconos de las pantallas rediseñadas. Sustituyen a los de Material en esas pantallas.
 *
 * Reglas del set:
 * - Retícula de 24 dp con zona viva de 2 a 22.
 * - Trazo de 1,75 dp con extremos y uniones redondeados. Sin relleno, salvo `PlayFilled` y `More`.
 * - El color lo pone quien usa el icono: activo = tinta (`WizardColors.text`), inactivo = `WizardColors.textFaint`,
 *   destructivo = `WizardColors.danger`.
 *
 * Uso: `Icon(KpknLineIcons.Home, contentDescription = "Inicio", tint = WizardColors.text)`.
 * 24 dp en la navegación y 20 dp dentro de botones e islas.
 */
object KpknLineIcons {

    // ── Navegación ──────────────────────────────────────────────────────────────

    /** Inicio: casa con puerta. */
    val Home: ImageVector by lazy {
        icono(
            "KpknLine.Home",
            listOf(
                "M3.5 10.6 12 3.75l8.5 6.85",
                "M5.75 9v9.75a1.5 1.5 0 0 0 1.5 1.5h9.5a1.5 1.5 0 0 0 1.5-1.5V9",
                "M10 20.25v-4.5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v4.5",
            ),
        )
    }

    /** Entreno: barra con discos, de perfil. */
    val Training: ImageVector by lazy {
        icono(
            "KpknLine.Training",
            listOf(
                "M2.75 12h2.5",
                "M8.25 12h7.5",
                "M18.75 12h2.5",
                "M6.25 6.25h1a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-9.5a1 1 0 0 1 1-1z",
                "M16.75 6.25h1a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-9.5a1 1 0 0 1 1-1z",
            ),
        )
    }

    /** Nutrición: cuenco con brote. */
    val Nutrition: ImageVector by lazy {
        icono(
            "KpknLine.Nutrition",
            listOf(
                "M3.75 11.75h16.5A8.25 7 0 0 1 3.75 11.75z",
                "M12 11.75c0-3.5 2.2-6 6-6.75-.25 3.75-2.5 6.25-6 6.75z",
                "M12 11.75c-.4-2.6-2-4.2-4.75-4.6.15 2.7 1.8 4.4 4.75 4.6z",
            ),
        )
    }

    /**
     * Cuerpo: cinta métrica, con el carrete, su cinta y las marcas. Antes era una báscula, que se leía como un medidor.
     */
    val Body: ImageVector by lazy {
        icono(
            "KpknLine.Body",
            listOf(
                "M9.5 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12z",
                "M9.5 13.75a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5z",
                "M9.5 18h11v-2.5",
                "M13.5 18v-1.75",
                "M16.75 18v-1.75",
            ),
        )
    }

    // ── Acciones y controles ────────────────────────────────────────────────────

    /** Reproducir, en trazo. */
    val Play: ImageVector by lazy { icono("KpknLine.Play", listOf(TRIANGULO_PLAY)) }

    /** Reproducir, relleno. Junto a `More`, el único icono sólido del set. */
    val PlayFilled: ImageVector by lazy { icono("KpknLine.PlayFilled", listOf(TRIANGULO_PLAY), relleno = true) }

    /** Añadir. */
    val Plus: ImageVector by lazy {
        icono(
            "KpknLine.Plus",
            listOf(
                "M12 5.25v13.5",
                "M5.25 12h13.5",
            ),
        )
    }

    /** Editar: lápiz con la banda del casquillo. */
    val Edit: ImageVector by lazy {
        icono(
            "KpknLine.Edit",
            listOf(
                "M14.75 5.25l4 4L9 19l-4.75.75L5 15z",
                "M12.75 7.25l4 4",
            ),
        )
    }

    /** Información: círculo con «i». */
    val Info: ImageVector by lazy {
        icono(
            "KpknLine.Info",
            listOf(
                CIRCULO_GRANDE,
                "M12 11v5.25",
                "M12 7.75h.01",
            ),
        )
    }

    /** Flecha hacia la derecha. */
    val ChevronRight: ImageVector by lazy { icono("KpknLine.ChevronRight", listOf("M9.5 5.75 15.75 12 9.5 18.25")) }

    /** Flecha hacia la izquierda. */
    val ChevronLeft: ImageVector by lazy { icono("KpknLine.ChevronLeft", listOf("M14.5 5.75 8.25 12l6.25 6.25")) }

    /** Flecha hacia abajo. */
    val ChevronDown: ImageVector by lazy { icono("KpknLine.ChevronDown", listOf("M5.75 9.5 12 15.75l6.25-6.25")) }

    /** Cerrar: una X. */
    val Close: ImageVector by lazy {
        icono(
            "KpknLine.Close",
            listOf(
                "M6.25 6.25l11.5 11.5",
                "M17.75 6.25 6.25 17.75",
            ),
        )
    }

    /** Confirmar: marca de check. */
    val Check: ImageVector by lazy { icono("KpknLine.Check", listOf("M4.75 12.75l4.5 4.5 10-10.5")) }

    /** Hora: reloj con dos agujas. */
    val Clock: ImageVector by lazy {
        icono(
            "KpknLine.Clock",
            listOf(
                CIRCULO_GRANDE,
                "M12 7.25V12l3.25 2.25",
            ),
        )
    }

    /** Calendario con dos anillas. */
    val Calendar: ImageVector by lazy {
        icono(
            "KpknLine.Calendar",
            listOf(
                "M5.25 5.25h13.5a1.5 1.5 0 0 1 1.5 1.5v12a1.5 1.5 0 0 1-1.5 1.5H5.25a1.5 1.5 0 0 1-1.5-1.5v-12a1.5 1.5 0 0 1 1.5-1.5z",
                "M3.75 10h16.5",
                "M8 3.25v4",
                "M16 3.25v4",
            ),
        )
    }

    /** Avisos: campana con badajo. Subida 0,5 dp para que el badajo no salga de la zona viva. */
    val Bell: ImageVector by lazy {
        icono(
            "KpknLine.Bell",
            listOf(
                "M6.25 15.75V10.5a5.75 5.75 0 0 1 11.5 0v5.25l1.5 2H4.75z",
                "M10 20.25a2.1 2.1 0 0 0 4 0",
            ),
        )
    }

    /** Libro abierto. */
    val Book: ImageVector by lazy {
        icono(
            "KpknLine.Book",
            listOf(
                "M12 6.75C10.25 5.4 7.75 4.75 4.25 4.75v13c3.5 0 6 .65 7.75 2 1.75-1.35 4.25-2 7.75-2v-13c-3.5 0-6 .65-7.75 2z",
                "M12 6.75v13",
            ),
        )
    }

    /** Tendencia al alza. */
    val TrendUp: ImageVector by lazy {
        icono(
            "KpknLine.TrendUp",
            listOf(
                "M3.75 16.75l5.5-5.5 4 4 7-7",
                "M14.75 8.25h5.5v5.5",
            ),
        )
    }

    /** Tendencia a la baja. */
    val TrendDown: ImageVector by lazy {
        icono(
            "KpknLine.TrendDown",
            listOf(
                "M3.75 7.25l5.5 5.5 4-4 7 7",
                "M14.75 15.75h5.5v-5.5",
            ),
        )
    }

    /** Repetir: dos flechas que cierran un bucle. */
    val Repeat: ImageVector by lazy {
        icono(
            "KpknLine.Repeat",
            listOf(
                "M16.75 3.75l3 3-3 3",
                "M19.75 6.75H8.25a4 4 0 0 0-4 4v1",
                "M7.25 20.25l-3-3 3-3",
                "M4.25 17.25h11.5a4 4 0 0 0 4-4v-1",
            ),
        )
    }

    /** Más opciones: tres puntos rellenos. */
    val More: ImageVector by lazy {
        icono(
            "KpknLine.More",
            listOf(
                "M4.45 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z",
                "M10.7 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z",
                "M16.95 12a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0-2.6 0z",
            ),
            relleno = true,
        )
    }

    /** Ajustes: dos reguladores. */
    val Sliders: ImageVector by lazy {
        icono(
            "KpknLine.Sliders",
            listOf(
                "M4 7.25h9",
                "M17 7.25h3",
                "M15 9.25a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
                "M4 16.75h3",
                "M11 16.75h9",
                "M9 18.75a2 2 0 1 0 0-4 2 2 0 0 0 0 4z",
            ),
        )
    }

    /** Perfil: busto. */
    val Person: ImageVector by lazy {
        icono(
            "KpknLine.Person",
            listOf(
                "M12 11.75a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z",
                "M4.75 20.25c.75-3.5 3.6-5.5 7.25-5.5s6.5 2 7.25 5.5",
            ),
        )
    }

    // ── Comidas ─────────────────────────────────────────────────────────────────

    /**
     * Desayuno: medio sol de radio 5,25 sobre el horizonte, con tres rayos en la misma banda (radio 7 a 9,25) para que
     * se lean a 20 dp y tengan el mismo tamaño que el sol de Almuerzo.
     */
    val Sunrise: ImageVector by lazy {
        icono(
            "KpknLine.Sunrise",
            listOf(
                "M2.75 17.5h18.5",
                "M6.75 17.5a5.25 5.25 0 0 1 10.5 0",
                "M12 10.5V8.25",
                "M16.95 12.55l1.59-1.59",
                "M7.05 12.55l-1.59-1.59",
            ),
        )
    }

    /** Almuerzo: sol con rayos. */
    val Sun: ImageVector by lazy {
        icono(
            "KpknLine.Sun",
            listOf(
                "M12 15.75a3.75 3.75 0 1 0 0-7.5 3.75 3.75 0 0 0 0 7.5z",
                "M12 3.75V6",
                "M12 18v2.25",
                "M3.75 12H6",
                "M18 12h2.25",
                "M6.16 6.16l1.6 1.6",
                "M16.24 16.24l1.6 1.6",
                "M6.16 17.84l1.6-1.6",
                "M16.24 7.76l1.6-1.6",
            ),
        )
    }

    /** Merienda: manzana con tallo. */
    val Apple: ImageVector by lazy {
        icono(
            "KpknLine.Apple",
            listOf(
                "M12 8.1c-1.7-1.2-4.6-1.3-6.15.95C4 11.9 5 16.6 7.4 19.1c1.25 1.3 2.75 1.55 4.6.75 1.85.8 3.35.55 4.6-.75 2.4-2.5 3.4-7.2 1.55-10.05C16.6 6.8 13.7 6.9 12 8.1z",
                "M12 8.1c0-1.7.4-3.2 2.25-4.6",
            ),
        )
    }

    /** Cena: luna creciente con destellos. */
    val Moon: ImageVector by lazy {
        icono(
            "KpknLine.Moon",
            listOf(
                "M19.75 14.5A8.25 8.25 0 1 1 9.5 4.25a6.5 6.5 0 0 0 10.25 10.25z",
                "M17.5 3.75v2.5",
                "M16.25 5h2.5",
            ),
        )
    }

    // ── Marca ───────────────────────────────────────────────────────────────────

    /** Torre de la marca KPKN: tres elipses apiladas. */
    val Tower: ImageVector by lazy {
        icono(
            "KpknLine.Tower",
            listOf(
                "M12 19.75c4.4 0 8-1.3 8-2.9s-3.6-2.9-8-2.9-8 1.3-8 2.9 3.6 2.9 8 2.9z",
                "M12 13.1c3.3 0 6-.95 6-2.15S15.3 8.8 12 8.8s-6 .95-6 2.15 2.7 2.15 6 2.15z",
                "M12 7.35c2.2 0 4-.65 4-1.45s-1.8-1.45-4-1.45-4 .65-4 1.45 1.8 1.45 4 1.45z",
            ),
        )
    }

    // ── Cantidades ──────────────────────────────────────────────────────────────

    /** Menos: barra horizontal centrada, el complemento de `Plus` en los pasos de cantidad. */
    val Minus: ImageVector by lazy { icono("KpknLine.Minus", listOf("M5.75 12h12.5")) }

    /** Trazado del triángulo de `Play` y `PlayFilled`. */
    private const val TRIANGULO_PLAY =
        "M8.25 5.6v12.8a.9.9 0 0 0 1.37.77l10.2-6.4a.9.9 0 0 0 0-1.54l-10.2-6.4a.9.9 0 0 0-1.37.77z"

    /** Círculo de radio 8,75 centrado en (12, 12): base de `Info` y `Clock`. */
    private const val CIRCULO_GRANDE = "M12 20.75a8.75 8.75 0 1 0 0-17.5 8.75 8.75 0 0 0 0 17.5z"

    /**
     * Construye un icono con los trazados [trazados] (cadenas SVG `d` sobre la retícula de 24). Con [relleno] se pinta
     * sólido y sin trazo; si no, en trazo de 1,75 dp con extremos y uniones redondeados.
     */
    private fun icono(nombre: String, trazados: List<String>, relleno: Boolean = false): ImageVector {
        val tinta = SolidColor(Color.Black)
        val builder = ImageVector.Builder(
            name = nombre,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in trazados) {
            val nodos = PathParser().parsePathString(d).toNodes()
            if (relleno) {
                builder.addPath(pathData = nodos, fill = tinta)
            } else {
                builder.addPath(
                    pathData = nodos,
                    stroke = tinta,
                    strokeLineWidth = 1.75f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return builder.build()
    }
}
