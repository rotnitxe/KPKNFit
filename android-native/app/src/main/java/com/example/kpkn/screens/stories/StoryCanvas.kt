package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.drawLineCover
import java.util.Locale
import kotlin.math.max

/**
 * Piezas comunes de las historias de Instagram, en píxeles del lienzo de 1080 × 1920. [Density1] hace que 1 sp sea 1 px en
 * cualquier pantalla, así que las medidas del texto son las del PNG. Las historias de entreno y de mes reutilizan estas piezas.
 */
internal object StoryCanvas {
    /** Ancho del lienzo de historia en px. */
    const val WIDTH = 1080

    /** Alto del lienzo de historia en px. */
    const val HEIGHT = 1920

    /** Margen izquierdo de la columna de texto. */
    const val LEFT = 75.6f

    /** Borde derecho de la columna de texto (75.6 + 928.8). */
    const val RIGHT = 1004.4f

    /** Ancho de la columna de texto, de [LEFT] a [RIGHT]. */
    const val COLUMN_WIDTH = 928.8f

    /** Hasta dónde llega el arte de portada: el 58 % de arriba del lienzo. */
    const val ART_BOTTOM = 1113.6f

    /** Altura donde empieza la cabecera (eyebrow). */
    const val HEADER_TOP = 729.6f

    /** Borde inferior del pie («KPKN» y el texto del pie). */
    const val FOOT_BOTTOM = 1824f

    /** Separación entre el bloque de contenido y el pie. */
    const val CONTENT_GAP = 43.2f

    /** Fondo de todo el lienzo. */
    val Background = Color(0xFF0A0A0A)

    /** Tinta principal: títulos, letras y valores. */
    val Ink = Color(0xFFF2EEE6)

    /** Tinta secundaria: metas y nombres. */
    val Muted = Color(0xFFC4BFB6)

    /** Tinta terciaria: eyebrow y texto del pie. */
    val Faint = Color(0xFF8E8980)

    /** Descansos: gris apagado. */
    val Off = Color(0xFF5F5C57)

    /** Acento de la marca (no lo usa esta historia; lo reservan las demás). */
    val Accent = Color(0xFFF49A6E)

    /** Densidad 1:1 con 1 sp = 1 px: toda medida de historia se hace con ella. */
    val Density1 = Density(1f, 1f)
}

/** Eyebrow: Inter SemiBold 38.9 px con interletraje de 0.25 em (se escribe en mayúsculas). */
private val eyebrowStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 38.9f.sp,
    letterSpacing = 0.25f.em,
)

/** Interlineado del nombre: cada línea ocupa 113.4 px, lo mismo que el tamaño de la letra. */
private const val NAME_LINE = 113.4f

/** Nombre del programa: Syne ExtraBold 113.4 px con interlineado de [NAME_LINE] px y el reparto de líneas de títulos. */
private val nameStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 113.4f.sp,
    lineHeight = NAME_LINE.sp,
    letterSpacing = (-0.04f).em,
    lineBreak = LineBreak.Heading,
)

/** El mismo nombre con el interlineado natural de la fuente: dice cuánto sobra en cada línea frente a [NAME_LINE]. */
private val nameNaturalStyle = nameStyle.copy(lineHeight = TextUnit.Unspecified)

/** Meta del programa: Inter Medium 41 px. */
private val metaStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 41f.sp,
)

/** Marca «KPKN» del pie: Syne ExtraBold 43.2 px con interletraje de -0.03 em. */
private val brandStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 43.2f.sp,
    letterSpacing = (-0.03f).em,
)

/** Texto del pie («Mi programa de entreno»): Inter Medium 30.2 px. */
private val captionStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.Medium,
    fontSize = 30.2f.sp,
)

/**
 * Mide [text] con [style] en el lienzo de historia (1 sp = 1 px). [width] limita el ancho (por defecto, sin límite); con [ellipsis]
 * el texto se corta con puntos suspensivos al llegar a [maxLines].
 */
internal fun TextMeasurer.storyText(
    text: String,
    style: TextStyle,
    width: Int = Constraints.Infinity,
    maxLines: Int = Int.MAX_VALUE,
    ellipsis: Boolean = false,
): TextLayoutResult = measure(
    text = text,
    style = style,
    overflow = if (ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
    maxLines = maxLines,
    constraints = Constraints(maxWidth = width),
    layoutDirection = LayoutDirection.Ltr,
    density = StoryCanvas.Density1,
)

/**
 * Fondo de la historia: #0A0A0A en todo el lienzo, la portada [cover] en el rectángulo de arriba (0, 0)–(1080, [artBottom]) y un
 * degradado que la funde con el negro desde el 55 % de esa altura. [progress] es el avance de la portada (1 = completa).
 */
internal fun DrawScope.drawStoryBackdrop(
    cover: LineCoverSpec,
    measurer: TextMeasurer,
    progress: Float = 1f,
    artBottom: Float = StoryCanvas.ART_BOTTOM,
) {
    val width = StoryCanvas.WIDTH.toFloat()
    drawRect(StoryCanvas.Background, topLeft = Offset.Zero, size = Size(width, StoryCanvas.HEIGHT.toFloat()))
    // El inset deja el DrawScope de (1080 × artBottom), tanto en el lienzo real como en la vista previa escalada.
    clipRect(0f, 0f, width, artBottom) {
        inset(left = 0f, top = 0f, right = size.width - width, bottom = size.height - artBottom) {
            drawLineCover(cover, measurer, progress)
        }
    }
    val fadeTop = artBottom * 0.55f
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(StoryCanvas.Background.copy(alpha = 0f), StoryCanvas.Background),
            startY = fadeTop,
            endY = artBottom,
        ),
        topLeft = Offset(0f, fadeTop),
        size = Size(width, artBottom - fadeTop),
    )
}

/**
 * Cabecera: eyebrow en mayúsculas desde [top], el nombre [name] (hasta tres líneas) y, si hay, la meta [meta] (una línea con
 * puntos suspensivos). Devuelve la y donde acaba la cabecera.
 */
internal fun DrawScope.drawStoryHeader(
    measurer: TextMeasurer,
    eyebrow: String,
    name: String,
    meta: String?,
    top: Float = StoryCanvas.HEADER_TOP,
): Float {
    val column = StoryCanvas.COLUMN_WIDTH.toInt()
    val eyebrowLayout = measurer.storyText(eyebrow.uppercase(Locale.forLanguageTag("es")), eyebrowStyle, width = column, maxLines = 1)
    drawText(eyebrowLayout, color = StoryCanvas.Faint, topLeft = Offset(StoryCanvas.LEFT, top))

    val nameTop = top + eyebrowLayout.size.height + 27f
    val nameLayout = measurer.storyText(name, nameStyle, width = column, maxLines = 3, ellipsis = true)
    // Compose pega cada línea de 113.4 px arriba; el CSS del prototipo reparte lo que sobra a ambos lados: se sube esa mitad.
    val natural = measurer.storyText(name, nameNaturalStyle, width = column, maxLines = 3, ellipsis = true)
    val naturalLine = natural.size.height.toFloat() / natural.lineCount.coerceAtLeast(1)
    drawText(nameLayout, color = StoryCanvas.Ink, topLeft = Offset(StoryCanvas.LEFT, nameTop + (NAME_LINE - naturalLine) / 2f))
    // El bloque del nombre mide líneas × 113.4 px, no el alto de Compose (que incluye el descenso de la última línea).
    val nameBottom = nameTop + nameLayout.lineCount * NAME_LINE
    if (meta == null) return nameBottom

    val metaTop = nameBottom + 21.6f
    val metaLayout = measurer.storyText(meta, metaStyle, width = column, maxLines = 1, ellipsis = true)
    drawText(metaLayout, color = StoryCanvas.Muted, topLeft = Offset(StoryCanvas.LEFT, metaTop))
    return metaTop + metaLayout.size.height
}

/**
 * Pie: «KPKN» a la izquierda y [caption] a la derecha, centrados en vertical entre sí, con el borde inferior en [bottom].
 * Devuelve la y superior del pie (sobre ella va el bloque de contenido).
 */
internal fun DrawScope.drawStoryFoot(
    measurer: TextMeasurer,
    caption: String,
    bottom: Float = StoryCanvas.FOOT_BOTTOM,
): Float {
    val brand = measurer.storyText("KPKN", brandStyle)
    val note = measurer.storyText(caption, captionStyle, maxLines = 1)
    val rowHeight = max(brand.size.height, note.size.height).toFloat()
    val top = bottom - rowHeight
    drawText(brand, color = StoryCanvas.Ink, topLeft = Offset(StoryCanvas.LEFT, top + (rowHeight - brand.size.height) / 2f))
    drawText(
        note,
        color = StoryCanvas.Faint,
        topLeft = Offset(StoryCanvas.RIGHT - note.size.width, top + (rowHeight - note.size.height) / 2f),
    )
    return top
}

/**
 * Pinta una historia en un bitmap de 1080 × 1920 con [draw] y lo devuelve, para el PNG. Las fuentes se resuelven con el [context]
 * de la app.
 */
internal fun renderStory(context: Context, draw: DrawScope.(TextMeasurer) -> Unit): android.graphics.Bitmap {
    val image = ImageBitmap(StoryCanvas.WIDTH, StoryCanvas.HEIGHT)
    val measurer = TextMeasurer(createFontFamilyResolver(context), StoryCanvas.Density1, LayoutDirection.Ltr)
    CanvasDrawScope().draw(
        StoryCanvas.Density1,
        LayoutDirection.Ltr,
        Canvas(image),
        Size(StoryCanvas.WIDTH.toFloat(), StoryCanvas.HEIGHT.toFloat()),
    ) { draw(measurer) }
    return image.asAndroidBitmap()
}

/**
 * Vista previa de una historia dentro de la app: la misma pintura que [renderStory], escalada al ancho de la caja (9:16, esquinas
 * de 18 dp). Como el texto se mide en el lienzo de 1080 px, la vista previa sale igual que el PNG.
 */
@Composable
internal fun StoryPreview(modifier: Modifier = Modifier, draw: DrawScope.(TextMeasurer) -> Unit) {
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(18.dp))
            .drawWithCache {
                onDrawBehind {
                    scale(size.width / StoryCanvas.WIDTH.toFloat(), pivot = Offset.Zero) { draw(measurer) }
                }
            },
    ) {}
}
