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

    /** Pausar: dos barras verticales de trazo (el contrario de `Play`). */
    val Pause: ImageVector by lazy {
        icono(
            "KpknLine.Pause",
            listOf(
                "M9.25 6.25v11.5",
                "M14.75 6.25v11.5",
            ),
        )
    }

    // ── Cantidades ──────────────────────────────────────────────────────────────

    /** Menos: barra horizontal centrada, el complemento de `Plus` en los pasos de cantidad. */
    val Minus: ImageVector by lazy { icono("KpknLine.Minus", listOf("M5.75 12h12.5")) }

    /** Bandera: asta vertical y paño con una muesca. Marca la fase de competición del módulo «Fases» (P2). */
    val Flag: ImageVector by lazy {
        icono(
            "KpknLine.Flag",
            listOf(
                "M5.75 21.25V3.25",
                "M5.75 4.25h11.5l-2.6 3.75 2.6 3.5H5.75",
            ),
        )
    }

    /** Buscar: lupa con el círculo de radio 6,25 en (10,75; 10,75) y el mango hasta (20; 20). */
    val Search: ImageVector by lazy {
        icono(
            "KpknLine.Search",
            listOf(
                "M10.75 17a6.25 6.25 0 1 0 0-12.5 6.25 6.25 0 0 0 0 12.5z",
                "M15.2 15.2L20 20",
            ),
        )
    }

    /** Ruta: tres nodos (círculos de radio 2,25) unidos por una línea quebrada que entra y sale por el borde de cada uno. */
    val Route: ImageVector by lazy {
        icono(
            "KpknLine.Route",
            listOf(
                "M2.75 18a2.25 2.25 0 1 0 4.5 0 2.25 2.25 0 1 0-4.5 0",
                "M9.75 7a2.25 2.25 0 1 0 4.5 0 2.25 2.25 0 1 0-4.5 0",
                "M16.75 15a2.25 2.25 0 1 0 4.5 0 2.25 2.25 0 1 0-4.5 0",
                "M6.21 16.1L10.79 8.9",
                "M13.48 8.69L17.52 13.31",
            ),
        )
    }

    /** Plantillas o capas: rombo aplastado completo encima y, debajo, solo la mitad inferior de otro. */
    val Layers: ImageVector by lazy {
        icono(
            "KpknLine.Layers",
            listOf(
                "M12 5.5L20.5 9L12 12.5L3.5 9Z",
                "M3.5 15L12 18.5L20.5 15",
            ),
        )
    }

    /** Historial: reloj con agujas a las 12 y a las 3 y una flecha en sentido antihorario en su arco superior izquierdo. */
    val History: ImageVector by lazy {
        icono(
            "KpknLine.History",
            listOf(
                "M5.07 8A8 8 0 1 0 8 5.07",
                "M10.71 5.8L8 5.07L8.72 2.37",
                "M12 7.75V12h3.5",
            ),
        )
    }

    /** Papelera: tapa con asa, cuerpo con el fondo redondeado y dos líneas verticales dentro. */
    val Trash: ImageVector by lazy {
        icono(
            "KpknLine.Trash",
            listOf(
                "M4.25 6.75h15.5",
                "M9.25 6.75V5a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1v1.75",
                "M6.25 6.75l.85 11.35a1.5 1.5 0 0 0 1.5 1.4h6.8a1.5 1.5 0 0 0 1.5-1.4l.85-11.35",
                "M10 10.75v6",
                "M14 10.75v6",
            ),
        )
    }

    /** Cardio: el latido de un electrocardiograma (un pico sobre la línea de base). */
    val Pulse: ImageVector by lazy {
        icono("KpknLine.Pulse", listOf("M2.75 12h4.25l2.5-5.25 4 10.5 2.5-5.25h5.25"))
    }

    // ── Cardio por tipo (SE5): cabeza, tronco y extremidades en trazo; la misma familia que el resto del set ──

    /** Carrera: figura de perfil con las piernas en zancada y los brazos en flexión. */
    val Run: ImageVector by lazy {
        icono(
            "KpknLine.Run",
            listOf(
                "M15 2a1.75 1.75 0 1 0 0 3.5a1.75 1.75 0 1 0 0-3.5z",
                "M14.25 7.25L11.5 13.25",
                "M14 9.25L17 10.5L18.25 8.25",
                "M14 9.25L10.5 10.25L8.25 12.25",
                "M11.5 13.25L14.75 14.5L13.25 19.75",
                "M11.5 13.25L9 17.25L5 15.75",
            ),
        )
    }

    /** Caminar: figura erguida con las piernas abiertas en paso y un brazo adelantado. */
    val Walk: ImageVector by lazy {
        icono(
            "KpknLine.Walk",
            listOf(
                "M12 2a1.75 1.75 0 1 0 0 3.5a1.75 1.75 0 1 0 0-3.5z",
                "M12 7.5V13",
                "M12 8.75L15.25 11.5",
                "M12 8.75L8.75 11.5",
                "M12 13L14.75 17.5L14.25 21.5",
                "M12 13L9.25 17.5L9.75 21.5",
            ),
        )
    }

    /** Bici: dos ruedas, cuadro con sillín y manillar. */
    val Bike: ImageVector by lazy {
        icono(
            "KpknLine.Bike",
            listOf(
                "M5.75 13a3.75 3.75 0 1 0 0 7.5a3.75 3.75 0 1 0 0-7.5z",
                "M18.25 13a3.75 3.75 0 1 0 0 7.5a3.75 3.75 0 1 0 0-7.5z",
                "M5.75 16.75L11.5 16.75L9.25 8.75",
                "M7.75 8.75h3",
                "M9.25 8.75L16.25 8.5L11.5 16.75",
                "M16.25 8.5L18.25 16.75",
                "M16.25 8.5L16.5 6.5",
                "M15 6.5h2.5",
            ),
        )
    }

    /** Remo: persona sentada con los brazos al mango, las piernas extendidas y el carril. */
    val Row: ImageVector by lazy {
        icono(
            "KpknLine.Row",
            listOf(
                "M7.25 3.25a1.75 1.75 0 1 0 0 3.5a1.75 1.75 0 1 0 0-3.5z",
                "M7.25 7.75L9.75 13.25",
                "M7.5 8.75L15 10.25",
                "M9.75 13.25L14.5 13.25L19.5 15.5",
                "M7.5 16.75h4",
                "M2.75 19.75h18.5",
            ),
        )
    }

    /** Escaladora: cuatro peldaños que suben de izquierda a derecha. */
    val Stairs: ImageVector by lazy {
        icono("KpknLine.Stairs", listOf("M3.5 20.25h4.25v-4.25h4.25v-4.25h4.25V7.5h4.25"))
    }

    /** Flecha hacia arriba: el contrario de `ChevronDown`, para subir un bloque en las listas. */
    val ChevronUp: ImageVector by lazy { icono("KpknLine.ChevronUp", listOf("M5.75 14.5L12 8.25l6.25 6.25")) }

    /** Llama: calentamiento. Una gota de fuego con su lengua interior en la base. */
    val Flame: ImageVector by lazy {
        icono(
            "KpknLine.Flame",
            listOf(
                "M12 3c.5 3.2 4.75 5.6 4.75 10.25A4.75 4.75 0 0 1 12 18a4.75 4.75 0 0 1-4.75-4.75c0-2.1.95-3.75 2.05-4.9.25 1.3.8 2.15 1.6 2.7C10.6 7.5 11.2 5.2 12 3z",
                "M12 15.75c-.6 0-1.1-.45-1.1-1.1 0-.7.55-1.05.8-1.5.15.45.35.7.7.9.05-.5.2-.85.5-1.25.2.55.5.95.5 1.6 0 .65-.5 1.35-1.4 1.35z",
            ),
        )
    }

    /** Articulación: dos segmentos que forman un ángulo y el eje redondo donde se doblan. Movilidad. */
    val Joint: ImageVector by lazy {
        icono(
            "KpknLine.Joint",
            listOf(
                "M5.5 18.5L12 12l6.5 6.5",
                "M12 9.75a2.25 2.25 0 1 0 0 4.5a2.25 2.25 0 1 0 0-4.5z",
            ),
        )
    }

    /** Eslabón: dos medias anillas unidas por una barra. Superserie: ejercicios enlazados. */
    val Link: ImageVector by lazy {
        icono(
            "KpknLine.Link",
            listOf(
                "M9 17H7a5 5 0 0 1 0-10h2",
                "M15 7h2a5 5 0 0 1 0 10h-2",
                "M8 12h8",
            ),
        )
    }

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
