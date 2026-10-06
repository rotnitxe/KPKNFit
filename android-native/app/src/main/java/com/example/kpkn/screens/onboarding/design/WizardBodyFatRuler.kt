package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Marca de prueba de la regla de porcentaje de grasa corporal. */
const val BODY_FAT_RULER_TAG = "setup-bodyfat-ruler"

/**
 * Matemática de la regla de grasa corporal, sin Compose para poder probarla en JVM.
 *
 * La regla es vertical y crece hacia abajo, como una regla de verdad: el valor más bajo ([MIN_PERCENT]) va
 * arriba y el más alto ([MAX_PERCENT]) abajo. Una marca por cada 1 %, una intermedia cada [MID_EVERY] % y una
 * larga, con número, cada [MAJOR_EVERY] %. Todo se mide sobre la «pista»: el tramo vertical entre la marca
 * del valor más bajo y la del más alto (el resto del alto es aire para el cursor y los números de los extremos).
 */
object WizardBodyFatScale {
    /** Valor de arriba de la regla. */
    const val MIN_PERCENT = 5

    /** Valor de abajo de la regla. */
    const val MAX_PERCENT = 50

    /** Rango de la regla del alta, ambos extremos incluidos. */
    val DEFAULT_RANGE: IntRange = MIN_PERCENT..MAX_PERCENT

    /** Cada cuántos puntos hay una marca intermedia. */
    const val MID_EVERY = 5

    /** Cada cuántos puntos hay una marca larga con número. */
    const val MAJOR_EVERY = 10

    /** Tipo de marca: fina (cada 1 %), intermedia (cada 5 %) o larga con número (cada 10 %). */
    enum class Tick { MINOR, MID, MAJOR }

    fun tickAt(percent: Int): Tick = when {
        percent % MAJOR_EVERY == 0 -> Tick.MAJOR
        percent % MID_EVERY == 0 -> Tick.MID
        else -> Tick.MINOR
    }

    /** Posición en la pista, de 0 (arriba, el valor más bajo) a 1 (abajo, el más alto). Fuera de rango se acota. */
    fun fractionOf(percent: Double, range: IntRange = DEFAULT_RANGE): Float {
        val span = (range.last - range.first).toDouble()
        if (span <= 0.0 || percent.isNaN()) return 0f
        return ((percent - range.first) / span).coerceIn(0.0, 1.0).toFloat()
    }

    /** Entero más cercano a una posición de la pista (0..1), siempre dentro del rango. */
    fun percentAt(fraction: Float, range: IntRange = DEFAULT_RANGE): Int {
        val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return (range.first + clamped * (range.last - range.first)).roundToInt().coerceIn(range.first, range.last)
    }

    /** Altura, en píxeles, de un porcentaje sobre una pista que empieza en [trackTopPx] y mide [trackHeightPx]. */
    fun yOf(percent: Double, range: IntRange, trackTopPx: Float, trackHeightPx: Float): Float =
        trackTopPx + fractionOf(percent, range) * trackHeightPx

    /** Porcentaje entero bajo el punto vertical [y] (en píxeles); fuera de la pista se acota al extremo. */
    fun percentAtY(y: Float, range: IntRange, trackTopPx: Float, trackHeightPx: Float): Int =
        percentAt(if (trackHeightPx > 0f) (y - trackTopPx) / trackHeightPx else 0f, range)
}

/** Entero más cercano de un porcentaje, dentro de [range]; algo que no es un número cae en el extremo de arriba. */
private fun Double.toRulerInt(range: IntRange): Int =
    if (isNaN()) range.first else roundToInt().coerceIn(range.first, range.last)

/**
 * Número de un porcentaje tal como se lee: «25», o «17,5» si trae un decimal (un dato antiguo o de Ajustes).
 * Nunca «25,0». Sirve para el texto grande, para la lectura en voz alta y para la descripción accesible.
 */
fun wizardBodyFatNumber(percent: Double): String {
    if (!percent.isFinite()) return "0"
    val tenths = (percent * 10.0).roundToInt()
    val decimal = abs(tenths % 10)
    return if (decimal == 0) (tenths / 10).toString() else "${tenths / 10},$decimal"
}

// ─── Medidas (dp) ────────────────────────────────────────────────────────────
//
// De izquierda a derecha, con la figura a la izquierda de la regla:
//
//   punta ─ cursor ─ [marcas] ──── número
//   0      8         10 …… 28     34 …… (ancho del «50»)
//
// Las marcas nacen todas en SpineX y crecen hacia la derecha; el cursor las cruza y termina un poco más
// allá de la marca larga, sin llegar a los números, para no tachar la cifra cuando el valor cae en un múltiplo
// de 10. La punta del cursor mira hacia la figura (a la izquierda).

/** Largo de la punta triangular del cursor. */
private val TipLength = 8.dp

/** Mitad del alto de la punta. */
private val TipHalfHeight = 5.dp

/** Dónde nacen todas las marcas (a la derecha de la punta). */
private val SpineX = 10.dp

private val MinorLength = 6.dp
private val MidLength = 11.dp
private val MajorLength = 18.dp

/** Lo que el cursor sobrepasa la marca larga. */
private val CursorOverhang = 4.dp

/** Hueco entre la marca larga y el número. */
private val LabelGap = 6.dp

/** Aire a la derecha del número. */
private val EndPadding = 4.dp

/** Aire extra, sobre media línea de texto, arriba y abajo de la pista. */
private val TrackPadding = 2.dp

/** Grosor de las marcas: un filete. */
private val HairlineWidth = 1.dp

/** Grosor del cursor: fino, pero más que las marcas. */
private val CursorWidth = 2.dp

private val MinorTickColor = WizardColors.textMuted.copy(alpha = 0.32f)
private val MidTickColor = WizardColors.textMuted.copy(alpha = 0.55f)
private val MajorTickColor = WizardColors.textMuted.copy(alpha = 0.80f)
private val LabelColor = WizardColors.textMuted.copy(alpha = 0.72f)

/** Opacidad del cursor mientras el valor no está declarado: se ve, pero no parece una respuesta. */
private const val UNDECLARED_CURSOR_ALPHA = 0.6f

/**
 * Regla vertical de porcentaje de grasa corporal, a la derecha de la figura. Sin estado: lo que se ve
 * sale de [percent] y todo cambio sale por los callbacks.
 *
 * - **Dibujo**: un solo `Canvas` con una marca fina cada 1 %, una intermedia cada 5 % y una larga con
 *   número cada 10 % (5 arriba y 50 abajo). Un cursor horizontal fino, en el verde de las reglas de altura
 *   y peso, con una pequeña punta hacia la figura, marca el valor. Estilo sobrio: filetes de un píxel de
 *   marca en gris tenue y los números en el rol `note`.
 * - **Gestos**: tocar un punto de la regla coloca el cursor ahí al instante y arrastrar en vertical lo
 *   mueve con el dedo. El valor se ajusta a enteros y cada cambio de entero da un toque háptico leve.
 *   Por eso mismo el toque es una respuesta aunque no cambie el valor: la posición de arranque no lo es.
 * - **Callbacks**: [onPercentChange] se emite en el primer contacto de cada gesto y en cada cambio de
 *   entero mientras dura. [onPercentChangeFinished] se emite una vez, al soltar, con el valor final (y tras
 *   un ajuste de accesibilidad): es el momento de guardar. Así quien llama puede pintar al vuelo sin
 *   escribir en el borrador a cada entero.
 * - **Estado**: [declared] solo atenúa el cursor mientras el valor es la posición de arranque.
 * - **Accesibilidad**: se anuncia como deslizador («Porcentaje de grasa corporal», «25 por ciento»), con
 *   `setProgress` y rango para TalkBack. Si el valor aún no está declarado, tocar dos veces lo declara tal cual.
 *
 * El ancho sale de lo que mide el número más ancho (con letra grande crece); el alto lo pone quien llama.
 * Un [percent] fuera de [range] (un dato antiguo de 3 %) deja el cursor en el extremo.
 */
@Composable
fun WizardBodyFatRuler(
    percent: Double,
    declared: Boolean,
    onPercentChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPercentChangeFinished: (Int) -> Unit = {},
    range: IntRange = WizardBodyFatScale.DEFAULT_RANGE,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = WizardTypography.note

    // Los números se miden una vez (y otra si cambia la escala de fuente): fijan el ancho de la regla y el
    // aire de arriba y abajo para que ningún número se corte.
    val labels = remember(range, textMeasurer, labelStyle) {
        range
            .filter { value -> WizardBodyFatScale.tickAt(value) == WizardBodyFatScale.Tick.MAJOR }
            .associateWith { value -> textMeasurer.measure(value.toString(), labelStyle) }
    }
    val labelWidthPx = labels.values.maxOfOrNull { it.size.width } ?: 0
    val labelHeightPx = labels.values.maxOfOrNull { it.size.height } ?: 0
    val padPx = with(density) { maxOf(labelHeightPx / 2f, TipHalfHeight.toPx()) + TrackPadding.toPx() }
    val widthDp = with(density) {
        ((SpineX + MajorLength + LabelGap + EndPadding).toPx() + labelWidthPx).toDp()
    }

    val currentPercent by rememberUpdatedState(percent)
    val currentDeclared by rememberUpdatedState(declared)
    val currentOnChange by rememberUpdatedState(onPercentChange)
    val currentOnFinished by rememberUpdatedState(onPercentChangeFinished)

    val shownValue = percent.coerceIn(range.first.toDouble(), range.last.toDouble())
    val spoken = "${wizardBodyFatNumber(percent)} por ciento"

    Box(
        modifier = modifier
            .width(widthDp)
            .testTag(BODY_FAT_RULER_TAG)
            .semantics {
                contentDescription = "Porcentaje de grasa corporal"
                stateDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = shownValue.toFloat(),
                    range = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                )
                setProgress { target ->
                    val value = target.roundToInt().coerceIn(range.first, range.last)
                    onPercentChange(value)
                    onPercentChangeFinished(value)
                    true
                }
                // Con el valor de arranque todavía sin declarar, doble toque = «uso lo que muestra».
                if (!declared) {
                    onClick(label = "Usar el valor mostrado") {
                        val value = percent.toRulerInt(range)
                        onPercentChange(value)
                        onPercentChangeFinished(value)
                        true
                    }
                }
            }
            .pointerInput(range, padPx) {
                awaitEachGesture {
                    val trackHeightPx = size.height - 2f * padPx
                    fun valueAt(y: Float) = WizardBodyFatScale.percentAtY(y, range, padPx, trackHeightPx)

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    // Lo que se ve ahora: solo un cambio respecto a eso suena y se emite durante el arrastre.
                    var last = currentPercent.toRulerInt(range)
                    val first = valueAt(down.position.y)
                    // El primer contacto siempre se emite: tocar el valor de arranque también lo declara.
                    if (first != last || !currentDeclared) {
                        if (first != last) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        last = first
                        currentOnChange(first)
                    }
                    drag(down.id) { change ->
                        val value = valueAt(change.position.y)
                        if (value != last) {
                            last = value
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnChange(value)
                        }
                        change.consume()
                    }
                    // Soltar, cancelar o perder el gesto: el valor que quedó es el definitivo.
                    currentOnFinished(last)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackTopPx = padPx
            val trackHeightPx = size.height - 2f * padPx
            val hairlinePx = HairlineWidth.toPx().coerceAtLeast(1f)
            val spineXPx = SpineX.toPx()
            val majorLengthPx = MajorLength.toPx()

            for (value in range) {
                val y = WizardBodyFatScale.yOf(value.toDouble(), range, trackTopPx, trackHeightPx).roundToInt().toFloat()
                val tick = WizardBodyFatScale.tickAt(value)
                val lengthPx = when (tick) {
                    WizardBodyFatScale.Tick.MINOR -> MinorLength.toPx()
                    WizardBodyFatScale.Tick.MID -> MidLength.toPx()
                    WizardBodyFatScale.Tick.MAJOR -> majorLengthPx
                }
                val color = when (tick) {
                    WizardBodyFatScale.Tick.MINOR -> MinorTickColor
                    WizardBodyFatScale.Tick.MID -> MidTickColor
                    WizardBodyFatScale.Tick.MAJOR -> MajorTickColor
                }
                drawLine(
                    color = color,
                    start = Offset(spineXPx, y),
                    end = Offset(spineXPx + lengthPx, y),
                    strokeWidth = hairlinePx,
                )
                if (tick == WizardBodyFatScale.Tick.MAJOR) {
                    labels[value]?.let { layout ->
                        drawText(
                            textLayoutResult = layout,
                            color = LabelColor,
                            topLeft = Offset(spineXPx + majorLengthPx + LabelGap.toPx(), y - layout.size.height / 2f),
                        )
                    }
                }
            }

            // Cursor: una línea que cruza las marcas y una punta que mira a la figura.
            val cursorY = WizardBodyFatScale.yOf(percent, range, trackTopPx, trackHeightPx).roundToInt().toFloat()
            val tipLengthPx = TipLength.toPx()
            val tipHalfPx = TipHalfHeight.toPx()
            val cursorColor = if (declared) {
                WizardColors.ruleCursor
            } else {
                WizardColors.ruleCursor.copy(alpha = UNDECLARED_CURSOR_ALPHA)
            }
            drawLine(
                color = cursorColor,
                start = Offset(tipLengthPx, cursorY),
                end = Offset(spineXPx + majorLengthPx + CursorOverhang.toPx(), cursorY),
                strokeWidth = CursorWidth.toPx(),
            )
            val tip = Path().apply {
                moveTo(0f, cursorY)
                lineTo(tipLengthPx, cursorY - tipHalfPx)
                lineTo(tipLengthPx, cursorY + tipHalfPx)
                close()
            }
            drawPath(path = tip, color = cursorColor)
        }
    }
}
