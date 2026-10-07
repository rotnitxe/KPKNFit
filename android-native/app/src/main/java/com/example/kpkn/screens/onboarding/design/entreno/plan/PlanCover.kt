package com.example.kpkn.screens.onboarding.design.entreno.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val running = animate && !reduced
    val clock = rememberSymbolClock(active = running && foreground && visible)
    val run = remember { SymbolRun() }
    // Antes de dibujar, anota desde cuándo corre: sin esto el primer cuadro saltaría.
    SideEffect { run.update(running, clock.peek()) }
    val pen = remember { SymbolPen() }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .onSymbolsVisible { visible = it }
            .drawWithCache {
                val brush = coverBrush(size.width, size.height, variant.angleDeg, accent)
                onDrawBehind { drawRect(brush) }
            },
    ) {
        val banner = when (layout) {
            CoverLayout.BANNER -> true
            CoverLayout.POSTER -> false
            CoverLayout.AUTO -> maxWidth.value / maxHeight.value > BANNER_RATIO
        }
        val widthDp = maxWidth.value
        val pad = (widthDp * 0.075f).coerceIn(16f, 26f).dp
        val artModifier = Modifier
        if (banner) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = pad, end = pad, bottom = pad, top = pad + topInset),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .weight(1.25f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    CoverHeader(model, accent)
                    Column {
                        CoverTitle(model.title, maxSp = 25f, minSp = TITLE_MIN_SP)
                        Spacer(Modifier.height(8.dp))
                        CoverFacts(model)
                    }
                }
                CoverArtCanvas(art, variant, pen, run, clock, running, parallax, artModifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = pad, end = pad, bottom = pad, top = pad + topInset),
            ) {
                // La esquina de arriba a la derecha queda libre para la marca de «hecho» del carrusel.
                CoverHeader(model, accent, Modifier.padding(end = HEADER_STAMP_GAP))
                CoverArtCanvas(art, variant, pen, run, clock, running, parallax, artModifier.weight(1f).fillMaxWidth())
                CoverTitle(model.title, maxSp = (widthDp * 0.108f).coerceIn(21f, 30f), minSp = TITLE_MIN_SP)
                Spacer(Modifier.height(8.dp))
                CoverFacts(model)
            }
        }
    }
}

// ---------------------------------------------------------------- texto de la portada

/** La insignia (si hay) y el kicker en mayúsculas, arriba a la izquierda. */
@Composable
private fun CoverHeader(model: PlanCardModel, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
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
private fun coverTitleStyle(sizeSp: Float) = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.1f).sp,
    letterSpacing = (-0.3).sp,
    lineBreak = LineBreak.Heading,
)

/** El tamaño más pequeño al que baja el título para que su palabra más larga quepa (Syne es muy ancha). */
private const val TITLE_MIN_SP = 14f

/** El título, hasta tres líneas, ajustado a su palabra más larga (ver [FittedDisplayText]). */
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
