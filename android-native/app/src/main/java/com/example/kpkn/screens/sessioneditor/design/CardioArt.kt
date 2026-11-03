package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Pulido v1 · CARDIO DEL EDITOR · punto 5: las 12 ilustraciones a medida (`ce-ilustraciones-ref`, `CE_ART` de `scenes-ce.js`).
 *
 * Lienzo de 64 × 64, trazo redondeado y dos tintas: la máquina o el lugar (crema apagada, grupo `m`) y la persona (azul cardio, grupo `f`).
 * Los trazos son EXACTAMENTE los del lienzo (círculos y rectángulos pasados a trazados con arcos); `ceArt` escala el grosor con el tamaño:
 * `min(2,6; 1,75 + max(0; s − 56) · 0,01)` dp, así a 56 dp mide 1,75 (como KPKN Line) y a 168 dp no pesa. Sin imágenes: solo `Path`.
 */

/** Los dos grupos de trazos de una modalidad, en datos de trazado SVG de un lienzo de 64 × 64. */
internal data class CardioArtPaths(val machine: String, val person: String)

/** Tinta de la máquina o del lugar (`.ce-art .m`). */
internal val CardioArtMachineInk = Color(0xFFC9C4BA)

/** Tinta de la persona (`.ce-art .f`, `--col`: el azul del cardio). */
internal val CardioArtPersonInk = Color(0xFF8FB2FF)

/** Grosor del trazo, en dp, de una ilustración de [size] dp (la fórmula de `ceArt`). */
internal fun cardioArtStrokeDp(size: Float): Float = min(2.6f, 1.75f + max(0f, size - 56f) * 0.01f)

/** Las 12 ilustraciones. Generadas desde `CE_ART` del lienzo: no se editan a mano. */
internal val CardioArtCatalog: Map<CardioType, CardioArtPaths> = mapOf(
        CardioType.TREADMILL to CardioArtPaths(
            machine = "M8.5 51h37a2.5 2.5 0 0 1 2.5 2.5v0a2.5 2.5 0 0 1 -2.5 2.5h-37a2.5 2.5 0 0 1 -2.5 -2.5v0a2.5 2.5 0 0 1 2.5 -2.5zM49 53L53 21M46 21h12",
            person = "M27.2 16.4a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM29 21L24 35M28.4 22.7L34 27L39 23M28.4 22.7L22 27L17 24M24 35L30 42L27 50M24 35L21 43L12 46",
        ),
        CardioType.ELLIPTICAL to CardioArtPaths(
            machine = "M6 57v-4a3 3 0 0 1 3 -3h37a3 3 0 0 1 3 3v4zM47 50L44 20M39 20h11M45 23L35 33M10 48h14M29 45h12",
            person = "M25 15.1a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM28 20L27 33M27.9 21.8L33 26L36 31M27 33L31 40L33 44M27 33L24 41L17 47",
        ),
        CardioType.ROW_MACHINE to CardioArtPaths(
            machine = "M16 52h44M60 52v5M16 52v5M4 40a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM13 40l3 -4M26 47l3 -7M39 49h12M36 35L22 39",
            person = "M48.8 28.6a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM50 33L44 45M49.2 34.6L43 35L36 35M44 45L35 40L28 46",
        ),
        CardioType.BIKE_STATIONARY to CardioArtPaths(
            machine = "M7 58h20M38 58h19M36 47a7 7 0 1 0 14 0a7 7 0 1 0 -14 0zM47 58L46 22M46 22h-9M17 58L21 36M15 35h11M21 51L40 48M28 50a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z",
            person = "M27.2 15.8a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM28 20L20 33M27.1 21.5L33 25L38 22M20 33L29 39L30 49",
        ),
        CardioType.STAIR_CLIMBER to CardioArtPaths(
            machine = "M4 57h12V49h10V41h10V33h9M48 33v24M42 20h12M48 20v13",
            person = "M25 14.1a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM28 19L27 32M27.9 20.8L34 25L42 21M27 32L34 36L32 40M27 32L25 40L21 48",
        ),
        CardioType.SKI_ERG to CardioArtPaths(
            machine = "M14 7h36a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2H14a2 2 0 0 1 -2 -2V9a2 2 0 0 1 2 -2zM29 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0zM8 58h48M22 17v10M42 17v10M19 27h6M39 27h6",
            person = "M28.6 29a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM32 34v13M32 36l-7 -3l-1 -6M32 36l7 -3l1 -6M32 47l-5 11M32 47l5 11",
        ),
        CardioType.RUN_OUTDOOR to CardioArtPaths(
            machine = "M3 40l9 -8l6 6l8 -10l9 11M36 49c-7 3 -17 3 -33 9M42 44c4 -1 10 0 19 -2M30 44l-4 4",
            person = "M36.2 17.4a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM38 22L33 36M37.4 23.7L43 28L48 24M37.4 23.7L31 28L26 25M33 36L39 43L36 51M33 36L29 44L20 47",
        ),
        CardioType.BIKE_OUTDOOR to CardioArtPaths(
            machine = "M7 46a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM39 46a9 9 0 1 0 18 0a9 9 0 1 0 -18 0zM16 46h15L26 30M26 30h16M31 46l11 -16M42 30l6 16M42 30h6q3 0 3 3M23 29h7M3 58c10 -1 20 0 30 1s20 0 28 -2",
            person = "M38.7 18.4a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM38 21L27 28M36.5 22L43 26L47 31M27 28L36 35L31 45",
        ),
        CardioType.WALK to CardioArtPaths(
            machine = "M30 33l9 -15l6 9l5 -7l10 13M4 58c14 -2 22 -8 34 -9s14 -6 22 -8",
            person = "M22.9 16.1a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM26 21L25 36M25.9 22.8L31 28L34 34M25.9 22.8L22 28L19 34M25 36L31 43L33 54M25 36L22 44L15 52M21 22h-5a2 2 0 0 0 -2 2v8a2 2 0 0 0 2 2h5",
        ),
        CardioType.AIR_BIKE to CardioArtPaths(
            machine = "M33 40a14 14 0 1 0 28 0a14 14 0 1 0 -28 0zM44 40a3 3 0 1 0 6 0a3 3 0 1 0 -6 0zM47 37V26M47 43v11M44 38l-10 -7M50 42l10 7M47 40L25 46M19 57L23 37M16 36h13M8 58h48M44 40L42 21M37 21h9",
            person = "M30.3 17.9a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM31 22L23 34M30 23.5L36 23L41 22M23 34L32 40L30 50",
        ),
        CardioType.CURVED_TREADMILL to CardioArtPaths(
            machine = "M5 42c4 11 14 15 28 15c14 0 22 -4 26 -13M11 50l2 -3M19 54l1 -3M27 56v-3M36 56v-3M44 54l-1 -3M52 50l-2 -2M58 44L61 24M53 24h11",
            person = "M34.2 17.8a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM35 22L27 35M34.1 23.5L41 28L46 24M34.1 23.5L29 29L23 26M27 35L35 42L33 51M27 35L24 43L14 47",
        ),
        CardioType.SLED to CardioArtPaths(
            machine = "M30 57h26c4 0 6 -3 4 -6M38 57L45 28M54 57L48 28M41 28h10M45 46a5 5 0 1 0 10 0a5 5 0 1 0 -10 0zM49 46a1 1 0 1 0 2 0a1 1 0 1 0 -2 0z",
            person = "M31.9 24.4a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0zM32 28L20 41M30.8 29.3L37 29L42 29M20 41L28 48L24 57M20 41L16 48L8 55",
        ),
)

private class ParsedArt(val machine: Path, val person: Path)

private fun parseArt(paths: CardioArtPaths) = ParsedArt(
    machine = PathParser().parsePathString(paths.machine).toPath(),
    person = PathParser().parsePathString(paths.person).toPath(),
)

/**
 * La ilustración de una modalidad de cardio a [size] dp. Va sin fondo: la ventana, la miniatura o el héroe la pone quien la usa.
 * No lleva descripción (la dice el nombre que tiene al lado).
 */
@Composable
internal fun CardioTypeArt(type: CardioType, size: Dp, modifier: Modifier = Modifier) {
    val parsed = remember(type) { CardioArtCatalog[type]?.let(::parseArt) }
    val sizeDp = size.value
    Canvas(modifier = modifier.size(size)) {
        val art = parsed ?: return@Canvas
        val k = this.size.width / 64f
        val stroke = Stroke(
            width = cardioArtStrokeDp(sizeDp).dp.toPx() / k,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        scale(scale = k, pivot = Offset.Zero) {
            drawPath(art.machine, CardioArtMachineInk, style = stroke)
            drawPath(art.person, CardioArtPersonInk, style = stroke)
        }
    }
}

/**
 * La miniatura de la fila y de la cabecera del menú (`.ce-thumb`): una baldosa de [tile] dp (56 en la fila, 60 en el menú), esquinas de 14 dp,
 * fondo `#1A1A1A` y la ilustración al 84 % (`round(s · 0,84)`).
 */
@Composable
internal fun CardioThumb(type: CardioType, tile: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(tile)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        CardioTypeArt(type = type, size = (tile.value * 0.84f).roundToInt().dp)
    }
}
