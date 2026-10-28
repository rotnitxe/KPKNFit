package com.example.kpkn.screens.monthsummary

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.training.progress.MonthlyLiftGain
import com.example.kpkn.domain.training.progress.MonthlyMuscleSets
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.stories.MonthStoryTemplate
import com.example.kpkn.screens.stories.StoryPreview
import com.example.kpkn.screens.stories.drawMonthStory
import com.example.kpkn.screens.stories.monthStoryFrom
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType
import java.time.YearMonth
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Cada diapositiva dura este tiempo: su barra de arriba se llena y entonces pasa a la siguiente. */
private const val SLIDE_MILLIS = 6000

/** Entrada de cada bloque: se funde y sube 16 dp en este tiempo. */
private const val ENTER_MILLIS = 420

/** Retrasos de la entrada desde que aparece la diapositiva: cifra, titular, extra y cuerpo. */
private const val FIGURE_DELAY_MILLIS = 120
private const val HEADLINE_DELAY_MILLIS = 240
private const val EXTRA_DELAY_MILLIS = 240
private const val BODY_DELAY_MILLIS = 360

/** La cifra cuenta hacia arriba en este tiempo, con EaseOutCubic. */
private const val COUNT_MILLIS = 1100

/** Los trazos (líneas de Fuerza y rayos del radial) se dibujan en este tiempo. */
private const val DRAW_MILLIS = 900

/** El calendario: cada día aparece a los 300 ms más 35 ms por su número, en 420 ms. */
private const val CALENDAR_DELAY_MILLIS = 300
private const val CALENDAR_STEP_MILLIS = 35

/** Parte alta de la diapositiva que ocupa el arte de portada. */
private const val ART_FRACTION = 0.46f

/** Alto del radial de Músculos, escala mínima que se acepta al encajarlo y músculos que se pintan. */
private val RADIAL_HEIGHT = 260.dp
private const val RADIAL_MIN_SCALE = 0.4f
private const val RADIAL_MAX_MUSCLES = 9

/** Cifra grande de la diapositiva: Syne ExtraBold 72 sp con cifras tabulares. */
private val FIGURE_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 72.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Titular de la diapositiva: Syne ExtraBold 34 sp con interlineado de 1,05. */
private val HEADLINE_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 34.sp,
    lineHeight = 1.05f.em,
)

/** Titular de la portada: Syne ExtraBold 44 sp con interlineado de 1,05. */
private val COVER_HEADLINE_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 44.sp,
    lineHeight = 1.05f.em,
)

/** Ganancia de un ejercicio («+7,5 kg»): Syne Bold 22 sp con cifras tabulares. */
private val GAIN_STYLE = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Una cifra que puede contar: un entero o un entero con una décima y coma («14», «42,4»). */
private val COUNT_PATTERN = Regex("(\\d+)(?:,(\\d))?")

/** Meses en español en minúsculas, para no depender de la configuración regional del dispositivo. */
private val summaryMonthNames = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * Resumen del mes a pantalla completa, como una historia: barras de avance arriba y una diapositiva tras otra. Todo sale de [report] y de
 * [monthlySummarySlides]. Con [autoAdvance] pasa sola cada 6 s (si el sistema no pide movimiento reducido); tocar a la derecha (65 %)
 * avanza y a la izquierda (35 %) vuelve; la cruz y el gesto de atrás cierran. [startIndex] es la diapositiva con la que abre.
 */
@Composable
internal fun MonthlySummaryScreen(
    report: MonthlyReport,
    programName: String?,
    onShare: () -> Unit,
    onOpenProgress: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    autoAdvance: Boolean = true,
) {
    val slides = remember(report, programName) { monthlySummarySlides(report, programName) }
    val last = slides.lastIndex
    var index by rememberSaveable { mutableIntStateOf(startIndex.coerceIn(0, last)) }
    val reduced = wizardReducedMotion()
    val timed = autoAdvance && !reduced
    val barFill = remember { Animatable(0f) }

    BackHandler(onBack = onClose)

    // La barra de la diapositiva actual se llena en SLIDE_MILLIS y entonces pasa a la siguiente; en la última se queda. Cada cambio,
    // por toque o por tiempo, reinicia el reloj.
    LaunchedEffect(index, timed) {
        if (timed) {
            barFill.snapTo(0f)
            barFill.animateTo(1f, tween(durationMillis = SLIDE_MILLIS, easing = LinearEasing))
            if (index < last) index += 1
        }
    }

    val goBack: () -> Unit = { index = (index - 1).coerceAtLeast(0) }
    val goForward: () -> Unit = { index = (index + 1).coerceAtMost(last) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("resumen_mes"),
    ) {
        // Las zonas de toque van al fondo: el texto y los botones de encima reciben sus propios toques.
        Row(Modifier.fillMaxSize()) {
            TapZone(Modifier.weight(0.35f), label = "Anterior", onTap = goBack)
            TapZone(Modifier.weight(0.65f), label = "Siguiente", onTap = goForward)
        }
        AnimatedContent(
            targetState = index,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 240)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 160))
                }
            },
            label = "resumen_mes_diapositiva",
        ) { shown ->
            SlideFace(
                slide = slides[shown],
                report = report,
                programName = programName,
                reduced = reduced,
                onShare = onShare,
                onOpenProgress = onOpenProgress,
            )
        }
        // Arriba, encima de todo: barras de avance, la marca y la cruz.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { bar ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .drawBehind {
                                val share = when {
                                    bar < index -> 1f
                                    bar == index -> if (timed) barFill.value else 1f
                                    else -> 0f
                                }
                                if (share > 0f) drawRect(WizardColors.text, size = Size(size.width * share, size.height))
                            },
                    ) {}
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "KPKN · ${summaryMonthNames[report.month.monthValue - 1].uppercase()}",
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                KpknIconButton(
                    icon = KpknLineIcons.Close,
                    contentDescription = "Cerrar",
                    onClick = onClose,
                    modifier = Modifier.testTag("resumen_cerrar"),
                )
            }
        }
    }
}

/** Zona de toque invisible a alto completo, con su nombre para el lector de pantalla. */
@Composable
private fun TapZone(modifier: Modifier, label: String, onTap: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onTap,
            )
            .semantics { this.contentDescription = label },
    ) {}
}

/** Arte de la portada de las diapositivas que lo llevan; null si no lo llevan. */
private fun coverOf(kind: MonthSlideKind): LineCoverSpec? = when (kind) {
    MonthSlideKind.COVER -> LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, seed = 5)
    MonthSlideKind.WORK -> LineCoverSpec(LineCoverStyle.ONDAS, LineCoverBackground.CARBON, seed = 2)
    MonthSlideKind.STRENGTH -> LineCoverSpec(LineCoverStyle.PERFIL, LineCoverBackground.GRAFITO, seed = 3)
    MonthSlideKind.BEST_MARK -> LineCoverSpec(LineCoverStyle.ORBITAS, LineCoverBackground.NOCHE, seed = 4)
    MonthSlideKind.EFFORT -> LineCoverSpec(LineCoverStyle.CAMPO, LineCoverBackground.GRAFITO, seed = 7)
    MonthSlideKind.CONSISTENCY, MonthSlideKind.MUSCLES, MonthSlideKind.SHARE -> null
}

/**
 * Una diapositiva: el arte de la mitad alta (si lleva) y, abajo, el eyebrow, la cifra, el titular, lo propio del tipo y el cuerpo, cada
 * uno con su entrada escalonada.
 */
@Composable
private fun SlideFace(
    slide: MonthSlide,
    report: MonthlyReport,
    programName: String?,
    reduced: Boolean,
    onShare: () -> Unit,
    onOpenProgress: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        coverOf(slide.kind)?.let { spec ->
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(ART_FRACTION)) {
                LineCoverArt(spec = spec, modifier = Modifier.fillMaxSize(), animate = true, reduced = reduced)
                // El arte se funde con el negro en su 40 % inferior, donde empieza el texto.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black)),
                ) {}
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Entering(delayMillis = 0, reduced = reduced) {
                Text(text = slide.eyebrow.uppercase(), style = KpknType.eyebrow, color = SymbolPalette.musculo)
            }
            slide.bigValue?.let { value ->
                Entering(delayMillis = FIGURE_DELAY_MILLIS, reduced = reduced) {
                    Figure(value = value, unit = slide.bigUnit, reduced = reduced)
                }
            }
            slide.headline?.let { headline ->
                Entering(delayMillis = HEADLINE_DELAY_MILLIS, reduced = reduced) {
                    Text(
                        text = headline,
                        style = if (slide.kind == MonthSlideKind.COVER) COVER_HEADLINE_STYLE else HEADLINE_STYLE,
                        color = WizardColors.text,
                    )
                }
            }
            SlideExtra(
                kind = slide.kind,
                report = report,
                programName = programName,
                reduced = reduced,
                onShare = onShare,
                onOpenProgress = onOpenProgress,
            )
            slide.body?.let { body ->
                Entering(delayMillis = BODY_DELAY_MILLIS, reduced = reduced) {
                    Text(text = body, style = KpknType.body, color = WizardColors.textMuted)
                }
            }
        }
    }
}

/** Lo propio de cada tipo de diapositiva, entre la cifra o el titular y el cuerpo. */
@Composable
private fun SlideExtra(
    kind: MonthSlideKind,
    report: MonthlyReport,
    programName: String?,
    reduced: Boolean,
    onShare: () -> Unit,
    onOpenProgress: (() -> Unit)?,
) {
    when (kind) {
        MonthSlideKind.CONSISTENCY -> MonthCalendar(report = report, reduced = reduced)
        MonthSlideKind.STRENGTH -> Entering(delayMillis = EXTRA_DELAY_MILLIS, reduced = reduced) {
            LiftRows(gains = report.gains, reduced = reduced)
        }
        MonthSlideKind.MUSCLES -> MuscleRadial(muscles = report.muscles, reduced = reduced)
        MonthSlideKind.SHARE -> Entering(delayMillis = EXTRA_DELAY_MILLIS, reduced = reduced) {
            SharePanel(report = report, programName = programName, onShare = onShare, onOpenProgress = onOpenProgress)
        }
        else -> Unit
    }
}

/** Entrada de un bloque: se funde y sube 16 dp hasta su sitio, en [ENTER_MILLIS] y tras [delayMillis]. Sin movimiento si [reduced]. */
@Composable
private fun Entering(delayMillis: Int, reduced: Boolean, content: @Composable () -> Unit) {
    val enter = rememberEntry(delayMillis = delayMillis, reduced = reduced, durationMillis = ENTER_MILLIS)
    Box(
        Modifier.graphicsLayer {
            val p = enter.value
            alpha = p
            translationY = (1f - p) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * Avance 0..1 de una entrada: ya en 1 si [reduced]; si no, espera [delayMillis] y sube a 1 en [durationMillis], en lineal (cada uso
 * le da su curva). Se lee dentro de `graphicsLayer` o de `draw`, así que no recompone por cuadro.
 */
@Composable
private fun rememberEntry(delayMillis: Int, reduced: Boolean, durationMillis: Int): Animatable<Float, AnimationVector1D> {
    val entry = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            entry.snapTo(1f)
        } else {
            entry.snapTo(0f)
            delay(delayMillis.toLong())
            entry.animateTo(1f, tween(durationMillis = durationMillis, easing = LinearEasing))
        }
    }
    return entry
}

/**
 * Texto de una cifra mientras cuenta hacia arriba: [target] («14», «42,4») con sus mismos decimales, en la fracción [progress] de la curva
 * EaseOutCubic. Si no es una cifra o ya llegó, sale tal cual.
 */
private fun countUpText(target: String, progress: Float): String {
    val match = COUNT_PATTERN.matchEntire(target) ?: return target
    if (progress >= 1f) return target
    val decimal = match.groupValues[2].isNotEmpty()
    val factor = if (decimal) 10L else 1L
    val end = (match.groupValues[1].toLong() * factor) + (if (decimal) match.groupValues[2].toLong() else 0L)
    val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    val shown = (end * eased).roundToLong()
    return if (decimal) "${shown / 10},${shown % 10}" else "$shown"
}

/** La cifra de la diapositiva: cuenta hacia arriba y su [unit], si la hay, va alineada por la base de la cifra. */
@Composable
private fun Figure(value: String, unit: String?, reduced: Boolean) {
    val count = rememberEntry(delayMillis = FIGURE_DELAY_MILLIS, reduced = reduced, durationMillis = COUNT_MILLIS)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = countUpText(value, count.value),
            style = FIGURE_STYLE,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (unit != null) {
            Text(
                text = unit,
                style = KpknType.bodyStrong,
                color = WizardColors.textMuted,
                maxLines = 1,
                modifier = Modifier.alignByBaseline().padding(start = 10.dp),
            )
        }
    }
}

/**
 * Calendario del mes, lunes primero: los días entrenados rellenos, el día de la mejor marca en acento y el resto con un contorno.
 * Cada día aparece a su hora, con escala y fundido.
 */
@Composable
private fun MonthCalendar(report: MonthlyReport, reduced: Boolean) {
    val month = report.month
    val days = month.lengthOfMonth()
    val lead = month.atDay(1).dayOfWeek.value - 1
    val rows = (lead + days + 6) / 7
    val trained = remember(report) { report.trainedDays.toSet() }
    val markDay = report.bestMark?.date?.takeIf { YearMonth.from(it) == month }?.dayOfMonth
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0 until 7) {
                    val day = row * 7 + column - lead + 1
                    if (day in 1..days) {
                        DayCell(day = day, trained = day in trained, mark = day == markDay, reduced = reduced)
                    } else {
                        Spacer(Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

/** Un día del calendario: 26 dp, esquina de 6 dp; la marca en acento, los entrenados en tinta y el resto con un contorno de 1 dp. */
@Composable
private fun DayCell(day: Int, trained: Boolean, mark: Boolean, reduced: Boolean) {
    val enter = rememberEntry(
        delayMillis = CALENDAR_DELAY_MILLIS + day * CALENDAR_STEP_MILLIS,
        reduced = reduced,
        durationMillis = ENTER_MILLIS,
    )
    val shape = RoundedCornerShape(6.dp)
    val cell = Modifier
        .size(26.dp)
        .graphicsLayer {
            val p = enter.value
            alpha = p
            scaleX = 0.6f + 0.4f * p
            scaleY = 0.6f + 0.4f * p
        }
    when {
        mark -> Box(cell.clip(shape).background(SymbolPalette.musculo)) {}
        trained -> Box(cell.clip(shape).background(WizardColors.text)) {}
        else -> Box(cell.border(1.dp, Color.White.copy(alpha = 0.14f), shape)) {}
    }
}

/** Hasta tres ejercicios con lo ganado: nombre, su línea de evolución y la ganancia. */
@Composable
private fun LiftRows(gains: List<MonthlyLiftGain>, reduced: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        gains.take(3).forEach { gain -> LiftRow(gain = gain, reduced = reduced) }
    }
}

/** Un ejercicio de Fuerza: el nombre, la línea que se dibuja de izquierda a derecha y «+X kg». */
@Composable
private fun LiftRow(gain: MonthlyLiftGain, reduced: Boolean) {
    val draw = rememberEntry(delayMillis = EXTRA_DELAY_MILLIS, reduced = reduced, durationMillis = DRAW_MILLIS)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = gain.exerciseName,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Canvas(Modifier.size(width = 84.dp, height = 28.dp)) {
            drawLift(points = gain.points, progress = draw.value)
        }
        Text(
            text = gainText(gain.deltaKg),
            style = GAIN_STYLE,
            color = WizardColors.text,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Línea de evolución: los e1RM por sesión, normalizados a la caja, dibujados hasta [progress] de izquierda a derecha. */
private fun DrawScope.drawLift(points: List<Double>, progress: Float) {
    val low = points.minOrNull() ?: return
    val high = points.maxOrNull() ?: return
    if (points.size < 2 || progress <= 0f) return
    val pad = 2.dp.toPx()
    val path = Path()
    points.forEachIndexed { i, value ->
        val x = pad + (size.width - 2 * pad) * i / (points.size - 1)
        val norm = if (high == low) 0.5f else ((value - low) / (high - low)).toFloat()
        val y = pad + (size.height - 2 * pad) * (1f - norm)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * progress, drawn, true)
    drawPath(
        drawn,
        color = SymbolPalette.musculo,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** «+7,5 kg» o «+4 kg»: la ganancia con coma decimal y sin «,0». */
private fun gainText(deltaKg: Double): String = "+${kgText(deltaKg)} kg"

/** Kilos con una décima y coma, sin «,0»: 7,5 y 4. */
private fun kgText(kg: Double): String {
    val tenths = (kg * 10).roundToLong()
    return if (tenths % 10L == 0L) "${tenths / 10}" else "${tenths / 10},${abs(tenths % 10)}"
}

/** Series por músculo en un radial: un rayo por músculo, el más trabajado en acento y los que no se tocaron, apagados. */
@Composable
private fun MuscleRadial(muscles: List<MonthlyMuscleSets>, reduced: Boolean) {
    val shown = remember(muscles) { muscles.sortedByDescending { it.sets }.take(RADIAL_MAX_MUSCLES) }
    val measurer = rememberTextMeasurer()
    val labels = remember(shown, measurer) {
        shown.map { measurer.measure(text = it.muscle, style = KpknType.caption) }
    }
    val grow = rememberEntry(delayMillis = HEADLINE_DELAY_MILLIS, reduced = reduced, durationMillis = DRAW_MILLIS)
    Canvas(Modifier.fillMaxWidth().height(RADIAL_HEIGHT)) {
        drawRadial(shown = shown, labels = labels, grow = grow.value)
    }
}

/** Ángulo del rayo [index] de [count], desde arriba y en sentido horario. */
private fun radialAngle(index: Int, count: Int): Double = -PI / 2 + index.toDouble() / count * 2 * PI

/** Largo del rayo de [sets] series: 26 dp de base y, según su proporción sobre [max], hasta 110 dp, todo multiplicado por [scale]. */
private fun rayReach(sets: Int, max: Int, scale: Float, base: Float, span: Float): Float = base + scale * span * sets / max

/**
 * La mayor escala (de 1 hacia abajo, en pasos de 0,02, sin bajar de [RADIAL_MIN_SCALE]) con la que todas las etiquetas caben dentro del
 * lienzo de [width] × [height]. Con 26 + 110 dp y etiquetas de 13 sp, el radial completo no cabe en 260 dp.
 */
private fun radialScale(
    shown: List<MonthlyMuscleSets>,
    labels: List<TextLayoutResult>,
    width: Float,
    height: Float,
    base: Float,
    span: Float,
    gap: Float,
): Float {
    val max = shown.maxOf { it.sets }.coerceAtLeast(1)
    val centerX = width / 2f
    val centerY = height / 2f
    var scale = 1f
    while (scale > RADIAL_MIN_SCALE) {
        val fits = shown.indices.all { i ->
            val angle = radialAngle(i, shown.size)
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            val reach = rayReach(shown[i].sets, max, scale, base, span) + gap
            val label = labels[i]
            val w = label.size.width.toFloat()
            val h = label.size.height.toFloat()
            val anchorX = centerX + c * reach
            val anchorY = centerY + s * reach
            val left = when {
                c > 0.3f -> anchorX
                c < -0.3f -> anchorX - w
                else -> anchorX - w / 2f
            }
            left >= 0f && left + w <= width && anchorY - h / 2f >= 0f && anchorY + h / 2f <= height
        }
        if (fits) return scale
        scale -= 0.02f
    }
    return RADIAL_MIN_SCALE
}

/**
 * Radial: cuatro anillos guía y un rayo por músculo desde el centro. El primero, en acento y de 3 dp; el resto, en tinta de 1,4 dp; los que
 * tienen cero series, al 30 %. Cada nombre va junto a la punta, con su alineación hacia fuera; los rayos crecen con [grow].
 */
private fun DrawScope.drawRadial(shown: List<MonthlyMuscleSets>, labels: List<TextLayoutResult>, grow: Float) {
    if (shown.isEmpty()) return
    val max = shown.maxOf { it.sets }.coerceAtLeast(1)
    val base = 26.dp.toPx()
    val span = 110.dp.toPx()
    val gap = 10.dp.toPx()
    val scale = radialScale(shown, labels, size.width, size.height, base, span, gap)
    val center = Offset(size.width / 2f, size.height / 2f)
    for (ring in 1..4) {
        drawCircle(
            color = Color.White,
            radius = base + scale * span * ring / 4f,
            center = center,
            alpha = 0.08f,
            style = Stroke(width = 0.7f.dp.toPx()),
        )
    }
    shown.forEachIndexed { i, muscle ->
        val angle = radialAngle(i, shown.size)
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val length = rayReach(muscle.sets, max, scale, base, span)
        val lead = i == 0
        drawLine(
            color = if (lead) SymbolPalette.musculo else WizardColors.text,
            start = center,
            end = Offset(center.x + c * length * grow, center.y + s * length * grow),
            strokeWidth = if (lead) 3.dp.toPx() else 1.4f.dp.toPx(),
            cap = StrokeCap.Round,
            alpha = if (muscle.sets == 0) 0.3f else 1f,
        )
        val label = labels[i]
        val anchorX = center.x + c * (length + gap)
        val anchorY = center.y + s * (length + gap)
        val left = when {
            c > 0.3f -> anchorX
            c < -0.3f -> anchorX - label.size.width
            else -> anchorX - label.size.width / 2f
        }
        drawText(
            label,
            color = WizardColors.textMuted.copy(alpha = grow),
            topLeft = Offset(left, anchorY - label.size.height / 2f),
        )
    }
}

/** Historia del mes en miniatura (la misma que la de compartir) y, debajo, «Compartir» y, si se puede, «Ver progreso». */
@Composable
private fun SharePanel(
    report: MonthlyReport,
    programName: String?,
    onShare: () -> Unit,
    onOpenProgress: (() -> Unit)?,
) {
    val story = remember(report, programName) { monthStoryFrom(report, programName, null) }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StoryPreview(modifier = Modifier.width(200.dp)) { measurer ->
            drawMonthStory(story, MonthStoryTemplate.RESUMEN, measurer)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknCompactPill(
                text = "Compartir",
                onClick = onShare,
                modifier = Modifier.weight(1f).testTag("resumen_compartir"),
            )
            if (onOpenProgress != null) {
                KpknCompactPill(
                    text = "Ver progreso",
                    onClick = onOpenProgress,
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
            }
        }
    }
}
