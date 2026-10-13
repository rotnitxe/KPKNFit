package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolRun
import com.example.kpkn.screens.onboarding.design.entreno.onSymbolsVisible
import com.example.kpkn.screens.onboarding.design.entreno.rememberEntrenoForeground
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.COVER_INK
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverArtMode
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverPaletteKind
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.CoverStudioArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.assignedCoverIdentity
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.coverArtModeOf
import com.example.kpkn.ui.components.rememberProgramCover

/*
 * La portada de un programa: una imagen procedural tipo cartel. Un campo oscuro con un degradado muy suave hacia el
 * acento de la disciplina (permitido aquí: es una imagen, no un borde), una ilustración de línea grande de la disciplina,
 * el kicker en mayúsculas, el título en Syne y, abajo, tres datos (días · minutos · nivel).
 *
 * Se adapta a su caja: proporción 3:4 (la tarjeta del carrusel) o banda ancha (la cabecera del detalle). En las dos el
 * texto va en zonas del campo que nunca cubre la ilustración, así siempre se lee sobre el campo oscuro.
 */

/** Tamaño del texto del kicker y la insignia: mayúsculas de 13 sp con aire entre letras. */
private val KICKER_STYLE = WizardTypography.eyebrow.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 1.1.sp)

/** Lo que el encabezado deja libre a la derecha en la tarjeta: ahí va la marca de «hecho» del carrusel. */
private val HEADER_STAMP_GAP = 26.dp

/** Idioma de las mayúsculas del kicker. */
private val SPANISH: java.util.Locale = java.util.Locale.forLanguageTag("es")

/** A partir de esta razón ancho/alto la portada se organiza como banda (texto a la izquierda, ilustración a la derecha). */
private const val BANNER_RATIO = 1.2f

/** El margen de la portada: un 7,5 % de su ancho, entre 16 y 26 dp. */
internal fun coverPadDp(widthDp: Float): Float = (widthDp * 0.075f).coerceIn(16f, 26f)

/** Hasta qué tamaño sube el título en un cartel de [widthDp] de ancho (el título baja solo si no cabe). */
internal fun posterTitleMaxSp(widthDp: Float): Float = (widthDp * 0.108f).coerceIn(21f, 30f)

/** El ancho que tiene el título en un cartel de [widthDp] de ancho: lo que deja el margen a cada lado. */
internal fun posterTitleWidthDp(widthDp: Float): Float = widthDp - 2f * coverPadDp(widthDp)

/** Hueco entre la columna de texto y la ilustración de la banda. */
private val BANNER_GAP = 8.dp

/**
 * Peso de la columna de texto de la banda frente al de la ilustración (1): crece con la letra grande (de 1,25 a 1,9 entre el
 * 100 % y el 130 %) para que las palabras largas de un título («Powerbuilding») quepan enteras junto a ella.
 */
internal fun bannerTextWeight(fontScale: Float): Float = 1.25f + 0.65f * ((fontScale - 1f) / 0.3f).coerceIn(0f, 1f)

/** El ancho que tiene el título en una banda de [widthDp] de ancho: su parte de lo que deja el margen y el hueco. */
internal fun bannerTitleWidthDp(widthDp: Float, fontScale: Float): Float {
    val inner = widthDp - 2f * coverPadDp(widthDp) - BANNER_GAP.value
    val weight = bannerTextWeight(fontScale)
    return inner * weight / (weight + 1f)
}

/** Cómo se organiza la portada: según la proporción de su caja, o forzada (la cabecera del detalle es siempre una banda). */
internal enum class CoverLayout { AUTO, POSTER, BANNER }

/**
 * La portada de [model]. [animate] mueve la ilustración con un bucle suave (si el sistema no pide reducir el movimiento);
 * [parallax] (−1 a 1, se lee al dibujar) la desplaza un poco, para el deslizado del carrusel y el scroll del detalle;
 * [cornerRadius] redondea las esquinas de la imagen (0 para la cabecera del detalle, a sangre).
 */
@Composable
fun PlanCover(
    model: PlanCardModel,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    parallax: () -> Float = { 0f },
    cornerRadius: Dp = 20.dp,
) {
    PlanCover(model, modifier, animate, wizardReducedMotion(), parallax, cornerRadius, 0.dp, CoverLayout.AUTO)
}

/** La portada con el «reducir movimiento» decidido por quien llama y un margen superior ([topInset]) para la barra de estado. */
@Composable
internal fun PlanCover(
    model: PlanCardModel,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    reduced: Boolean,
    parallax: () -> Float = { 0f },
    cornerRadius: Dp = 20.dp,
    topInset: Dp = 0.dp,
    layout: CoverLayout = CoverLayout.AUTO,
) {
    val profile = model.profile
    val accent = goalAccent(profile)
    val art = remember(profile) { goalCoverArt(profile) }
    val variant = remember(model.coverSeed) { coverVariantOf(model.coverSeed) }
    // Los programas «a medida» tienen identidad propia (`CoverStudio`); el catálogo sigue con el arte de siempre.
    val identity = remember(model.id) { assignedCoverIdentity(model.id) }
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val running = animate && !reduced
    // El reloj del arte procedural solo corre si ese arte se pinta: con identidad, `CoverStudioArt` lleva el suyo.
    val clock = rememberSymbolClock(active = running && foreground && visible && identity == null)
    val run = remember { SymbolRun() }
    // Antes de dibujar, anota desde cuándo corre: sin esto el primer cuadro saltaría.
    SideEffect { run.update(running, clock.peek()) }
    val pen = remember { SymbolPen() }
    var cover = modifier
        .clip(RoundedCornerShape(cornerRadius))
        .onSymbolsVisible { visible = it }
    // El fondo de siempre lo pinta el arte procedural; con identidad, el motor cubre la caja entera.
    if (identity == null) {
        cover = cover.drawWithCache {
            val brush = coverBrush(size.width, size.height, variant.angleDeg, accent)
            onDrawBehind { drawRect(brush) }
        }
    }
    BoxWithConstraints(modifier = cover) {
        val banner = when (layout) {
            CoverLayout.BANNER -> true
            CoverLayout.POSTER -> false
            CoverLayout.AUTO -> maxWidth.value / maxHeight.value > BANNER_RATIO
        }
        val widthDp = maxWidth.value
        val pad = coverPadDp(widthDp).dp
        // Ancho de la caja para la franja de la cabecera: dentro de Row y Column no se ve maxWidth (el alcance de BoxWithConstraints).
        val coverWidth = maxWidth
        // Con portada propia (`assets/program_covers/`), la foto sustituye al arte y un degradado negro abajo deja leer el título.
        val photo = rememberProgramCover(model.id, with(LocalDensity.current) { maxWidth.roundToPx() })
        val mode = coverArtModeOf(hasPhoto = photo != null, identity = identity)
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(colorStops = arrayOf(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))),
            )
        }
        // Con fondo de tinta o de papel, la franja negra del kicker va detrás de la cabecera (ver CoverHeader).
        val studioBand = identity != null && mode == CoverArtMode.STUDIO && identity.palette != CoverPaletteKind.NOCHE
        if (identity != null && mode == CoverArtMode.STUDIO) {
            // El motor a sangre; encima, el velo de la parte baja y, en la banda, otro a la izquierda donde va el texto.
            CoverStudioArt(identity, Modifier.matchParentSize(), animate = animate, reduced = reduced, parallax = parallax)
            if (banner) CoverLeftScrim()
            CoverBottomVeil(solidBase = identity.palette != CoverPaletteKind.NOCHE)
        }
        val artModifier = Modifier
        if (banner) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = pad, end = pad, bottom = pad, top = pad + topInset),
                horizontalArrangement = Arrangement.spacedBy(BANNER_GAP),
            ) {
                Column(
                    Modifier
                        .weight(bannerTextWeight(LocalDensity.current.fontScale))
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    CoverHeader(model, accent, bandInset = pad, bandWidth = if (studioBand) coverWidth else null)
                    Column {
                        CoverTitle(model.title, maxSp = 25f, minSp = COVER_TITLE_MIN_SP)
                        Spacer(Modifier.height(8.dp))
                        CoverFacts(model)
                    }
                }
                if (mode == CoverArtMode.PROCEDURAL) {
                    CoverArtCanvas(art, variant, pen, run, clock, running, parallax, artModifier.weight(1f).fillMaxHeight())
                } else {
                    Spacer(artModifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = pad, end = pad, bottom = pad, top = pad + topInset),
            ) {
                // La esquina de arriba a la derecha queda libre para la marca de «hecho» del carrusel.
                CoverHeader(model, accent, Modifier.padding(end = HEADER_STAMP_GAP), bandInset = pad, bandWidth = if (studioBand) coverWidth else null)
                if (mode == CoverArtMode.PROCEDURAL) {
                    CoverArtCanvas(art, variant, pen, run, clock, running, parallax, artModifier.weight(1f).fillMaxWidth())
                } else {
                    Spacer(artModifier.weight(1f).fillMaxWidth())
                }
                CoverTitle(model.title, maxSp = posterTitleMaxSp(widthDp), minSp = COVER_TITLE_MIN_SP)
                Spacer(Modifier.height(8.dp))
                CoverFacts(model)
            }
        }
    }
}

/**
 * El velo de la parte baja, para que el título y los datos se lean sobre cualquier arte. En NOCHE, un degradado del 52 % al 100 % del
 * alto. En TINTA y PAPEL, una peana negra sólida desde el 56 % (la transición empieza en el 50 %): a 130 % de letra el título de tres
 * líneas sube hasta ese punto, y sobre el papel su primera línea quedaba en crema invisible. El título va en tinta sobre negro.
 */
@Composable
private fun BoxScope.CoverBottomVeil(solidBase: Boolean) {
    val stops = if (solidBase) {
        arrayOf(0.50f to Color.Transparent, 0.56f to COVER_INK, 1f to COVER_INK)
    } else {
        arrayOf(0.52f to Color.Transparent, 1f to COVER_INK.copy(alpha = 0.92f))
    }
    Box(Modifier.matchParentSize().background(Brush.verticalGradient(colorStops = stops)))
}

/**
 * El velo de la izquierda de la banda: el texto va en esa columna. Es una columna negra sólida hasta el 58 % del ancho, que se disuelve
 * hacia el 82 %; un degradado suave sobre el papel se veía gris y turbio, y con 130 % de letra el título no se leía.
 */
@Composable
private fun BoxScope.CoverLeftScrim() {
    Box(
        Modifier
            .matchParentSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(0f to COVER_INK.copy(alpha = 0.92f), 0.58f to COVER_INK.copy(alpha = 0.92f), 0.82f to Color.Transparent),
                ),
            ),
    )
}

// ---------------------------------------------------------------- texto de la portada

/**
 * La insignia (si hay) y el kicker en mayúsculas, arriba a la izquierda. Con [bandWidth], lleva detrás la franja negra al 70 % de la
 * portada: a todo el ancho ([bandWidth]), desde [bandInset] a la izquierda del texto y con 6 dp de aire arriba y abajo; así cubre
 * también un kicker de dos líneas.
 */
@Composable
private fun CoverHeader(model: PlanCardModel, accent: Color, modifier: Modifier = Modifier, bandInset: Dp = 0.dp, bandWidth: Dp? = null) {
    val withBand = if (bandWidth == null) modifier else modifier.drawBehind {
        val air = 6.dp.toPx()
        drawRect(COVER_INK.copy(alpha = 0.7f), topLeft = Offset(-bandInset.toPx(), -air), size = Size(bandWidth.toPx(), size.height + 2f * air))
    }
    Column(withBand) {
        val badge = model.badge
        if (!badge.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Diamond(accent, Modifier.size(8.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = badge.uppercase(SPANISH),
                    style = KICKER_STYLE,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
        }
        Text(
            text = model.kicker.uppercase(SPANISH),
            style = KICKER_STYLE,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** El título en Syne ExtraBold a [sizeSp]. */
internal fun coverTitleStyle(sizeSp: Float) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.1f).sp,
    letterSpacing = (-0.3).sp,
    lineBreak = LineBreak.Heading,
)

/** El tamaño más pequeño al que baja el título para que su palabra más larga quepa (Syne es muy ancha): el mínimo del wizard. */
internal const val COVER_TITLE_MIN_SP = 13f

/** El título, preferiblemente en tres líneas, ajustado a su palabra más larga y sin cortes ni «…» (ver [FittedDisplayText]). */
@Composable
private fun CoverTitle(title: String, maxSp: Float, minSp: Float, modifier: Modifier = Modifier) {
    FittedDisplayText(
        text = title,
        maxSp = maxSp,
        minSp = minSp,
        styleOf = ::coverTitleStyle,
        color = WizardColors.text,
        maxLines = 3,
        modifier = modifier,
    )
}

/** El separador de los datos de la portada. */
private const val FACTS_SEPARATOR = "  ·  "

private val FACTS_STYLE = WizardTypography.note.copy(fontWeight = FontWeight.Medium)

/**
 * Los tres datos de abajo: «4 días · 60 min · Intermedio». Si no caben en una línea se reparten en las que hagan falta sin
 * partir ningún dato ni dejar un punto colgando al final de una línea.
 */
@Composable
private fun CoverFacts(model: PlanCardModel, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val text = remember(model.daysLabel, model.minutesLabel, model.levelLabel, widthPx, density.fontScale, density.density) {
            flowFacts(coverFactList(model), FACTS_SEPARATOR) { line ->
                measurer.measure(text = line, style = FACTS_STYLE, maxLines = 1, softWrap = false).size.width <= widthPx
            }
        }
        Text(text = text, style = FACTS_STYLE, color = WizardColors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Los datos de la portada que no vienen vacíos, en orden: días, minutos y nivel. */
internal fun coverFactList(model: PlanCardModel): List<String> =
    listOf(model.daysLabel, model.minutesLabel, model.levelLabel).filter { it.isNotBlank() }

/** Los datos de la portada en una línea, unidos con puntos medios. */
internal fun coverFacts(model: PlanCardModel): String = coverFactList(model).joinToString(FACTS_SEPARATOR)

/**
 * Reparte [facts] en líneas: va llenando cada una (separados por [separator]) mientras [fits] diga que cabe, y salta de línea
 * ENTRE datos, nunca dentro de uno. Si un dato solo no cabe, va solo en su línea.
 */
internal fun flowFacts(facts: List<String>, separator: String, fits: (String) -> Boolean): String {
    val lines = mutableListOf<String>()
    var current = ""
    for (fact in facts) {
        val candidate = if (current.isEmpty()) fact else current + separator + fact
        if (current.isEmpty() || fits(candidate)) {
            current = candidate
        } else {
            lines += current
            current = fact
        }
    }
    if (current.isNotEmpty()) lines += current
    return lines.joinToString("\n")
}

private val DIAMOND: Path by lazy {
    Path().apply {
        moveTo(0.5f, 0f)
        lineTo(1f, 0.5f)
        lineTo(0.5f, 1f)
        lineTo(0f, 0.5f)
        close()
    }
}

/** Un pequeño rombo del color del acento, antes de la insignia. */
@Composable
private fun Diamond(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(DIAMOND, color)
        }
    }
}
