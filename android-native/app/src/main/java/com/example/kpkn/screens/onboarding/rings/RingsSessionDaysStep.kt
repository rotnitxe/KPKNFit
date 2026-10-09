package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.RingsSessionDaysState
import com.example.kpkn.screens.onboarding.SetupRingsAnswers
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.entreno.RingsCalendarArt
import com.example.kpkn.screens.onboarding.design.entreno.RingsRestArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCell
import com.example.kpkn.screens.onboarding.design.entreno.WEEK_DAY_COUNT
import com.example.kpkn.screens.onboarding.design.entreno.WeekLetter
import com.example.kpkn.screens.onboarding.design.entreno.WeekPalette
import com.example.kpkn.screens.onboarding.design.entreno.dayFullName
import com.example.kpkn.screens.onboarding.design.entreno.dayInitial
import com.example.kpkn.screens.onboarding.design.entreno.drawWeekCheck
import com.example.kpkn.screens.onboarding.design.entreno.drawWeekLetter
import com.example.kpkn.screens.onboarding.design.entreno.measureWeekLetter
import com.example.kpkn.screens.onboarding.design.entreno.rememberSymbolClock
import com.example.kpkn.screens.onboarding.design.entreno.weekBleed
import com.example.kpkn.screens.onboarding.design.entreno.weekMillis
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import java.time.LocalDate

/** Disco de cada día (la misma medida que la fila de días del Entreno) y su zona de dibujo. */
private val DayDiscBox = 48.dp
private val DayDiscMin = 34.dp
private val DayDiscMax = 42.dp

/** Duración del relleno del día al elegirlo (antes de escalar por la prueba de animaciones). */
private const val DAY_FILL_MS = 340

/** Tamaño de la zona de dibujo de los símbolos exclusivos y su escala dentro de ella. */
private val ExclusiveCanvas = 64.dp
private const val ExclusiveArtScale = 0.9f

/** Nota de la pantalla: la ventana de siete días. */
private const val WINDOW_NOTE = "Cuentan los últimos 7 días: lo anterior ya casi no pesa en tus RINGS."

/**
 * Paso `RINGS_SESSION_DAYS`: los siete días previos, de hace seis a hoy, para marcar en cuáles entrenaste; y dos símbolos
 * exclusivos, «No entrené» y «No lo recuerdo». Escribe con los setters de E3b; el cursor no se mueve.
 */
@Composable
internal fun RingsSessionDaysStep(state: SetupWizardState, vm: SetupWizardViewModel, modifier: Modifier = Modifier) {
    val today = remember { LocalDate.now() }
    RingsSessionDaysContent(
        answers = state.draft.ringsAnswers,
        today = today,
        onToggleDay = { offset -> vm.toggleTrainedDay(offset) },
        onNoTraining = { vm.setNoTraining() },
        onUnknown = { vm.setTrainingUnknown() },
        modifier = modifier,
    )
}

/**
 * Cuerpo del paso sin estado propio (lo usa también la hoja de render). [answers] es lo que hay en el borrador; [today] fija la
 * fecha de cada día: el día de hace seis días va a la izquierda y hoy, a la derecha.
 */
@Composable
internal fun RingsSessionDaysContent(
    answers: SetupRingsAnswers?,
    today: LocalDate,
    onToggleDay: (Int) -> Unit,
    onNoTraining: () -> Unit,
    onUnknown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = answers?.sessionDaysState ?: RingsSessionDaysState.NOT_ANSWERED
    val trained = if (status == RingsSessionDaysState.DECLARED) answers?.trainedDayOffsets.orEmpty() else emptySet()
    val none = status == RingsSessionDaysState.NONE
    val unknown = status == RingsSessionDaysState.UNKNOWN
    val reduced = wizardReducedMotion()
    val clock = rememberSymbolClock(active = !reduced && (none || unknown))
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap),
    ) {
        RingsDayRow(trained = trained, today = today, reduced = reduced, onToggleDay = onToggleDay)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
        ) {
            SymbolCell(
                art = RingsRestArt,
                label = "No entrené",
                tag = "rings-days-none",
                selected = none,
                clock = clock,
                reducedMotion = reduced,
                onToggle = onNoTraining,
                canvasModifier = Modifier.size(ExclusiveCanvas),
                labelStyle = WizardTypography.note,
                artScale = ExclusiveArtScale,
                badgeRadius = 9.dp,
                labelGap = 4.dp,
                modifier = Modifier.weight(1f),
            )
            SymbolCell(
                art = RingsCalendarArt,
                label = "No lo recuerdo",
                tag = "rings-days-unknown",
                selected = unknown,
                clock = clock,
                reducedMotion = reduced,
                onToggle = onUnknown,
                canvasModifier = Modifier.size(ExclusiveCanvas),
                labelStyle = WizardTypography.note,
                artScale = ExclusiveArtScale,
                badgeRadius = 9.dp,
                labelGap = 4.dp,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = WINDOW_NOTE,
            style = WizardTypography.note,
            color = WizardColors.textMuted,
        )
    }
}

/** La fila de los siete días: de hace seis días (izquierda) a hoy (derecha), con selección múltiple. */
@Composable
private fun RingsDayRow(trained: Set<Int>, today: LocalDate, reduced: Boolean, onToggleDay: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    // Una letra por día, indexada por su desplazamiento (0 = hoy).
    val letters = remember(today, measurer) {
        List(WEEK_DAY_COUNT) { offset ->
            measurer.measureWeekLetter(dayInitial(today.minusDays(offset.toLong()).dayOfWeek.value), WizardTypography.measure)
        }
    }
    // Como la fila del Entreno: los días sangran hacia los márgenes para que cada uno sea un objetivo táctil de 48 dp.
    BoxWithConstraints(Modifier.fillMaxWidth().weekBleed()) {
        val disc = (maxWidth / WEEK_DAY_COUNT - 8.dp).coerceIn(DayDiscMin, DayDiscMax)
        Row(Modifier.fillMaxWidth()) {
            for (offset in WEEK_DAY_COUNT - 1 downTo 0) {
                RingsDayCell(
                    offset = offset,
                    date = today.minusDays(offset.toLong()),
                    selected = offset in trained,
                    letter = letters[offset],
                    disc = disc,
                    reduced = reduced,
                    onToggle = { onToggleDay(offset) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Un día: el disco de vidrio con su letra; elegido, se llena de columna y aparece la marca de «hecho» (como la fila del Entreno).
 * Debajo, «Hoy», «Ayer» o el número del día. `Role.Checkbox`, descripción «Martes 7, entrenado».
 */
@Composable
private fun RingsDayCell(
    offset: Int,
    date: LocalDate,
    selected: Boolean,
    letter: WeekLetter,
    disc: Dp,
    reduced: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(weekMillis(DAY_FILL_MS), easing = FastOutSlowInEasing),
        label = "ringsDayFill",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "ringsDayPress",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) WeekPalette.ink else WeekPalette.faint,
        animationSpec = if (reduced) snap() else tween(220),
        label = "ringsDayLabel",
    )
    val description = "${dayFullName(date.dayOfWeek.value)} ${date.dayOfMonth}, " + if (selected) "entrenado" else "sin entrenar"
    Column(
        modifier = modifier
            .testTag("rings-day-$offset")
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            // Va DESPUÉS de `toggleable`: conserva su rol y su estado y descarta el texto de la etiqueta.
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(DayDiscBox)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = disc.toPx() / 2f * press
            val f = fill.coerceIn(0f, 1f)
            // Disco de vidrio neutro; el relleno de columna crece desde el centro.
            drawCircle(WizardColors.glassFill, r, center)
            drawCircle(WizardColors.glassBorder, r - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            if (f > 0.002f) drawCircle(WeekPalette.columna, r * eOutCubic(f), center)
            // La letra se va mientras entra la marca de «hecho».
            val letterAlpha = 1f - seg(f, 0f, 0.45f)
            if (letterAlpha > 0.01f) drawWeekLetter(letter, center, WeekPalette.muted.copy(alpha = letterAlpha))
            drawWeekCheck(center, r, seg(f, 0.35f, 1f), WeekPalette.onColumna, 2.4.dp.toPx())
        }
        Text(
            text = dayLabel(offset, date.dayOfMonth),
            style = WizardTypography.note,
            color = labelColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** La etiqueta de un día: «Hoy», «Ayer» o el número del día del mes. */
internal fun dayLabel(offset: Int, dayOfMonth: Int): String = when (offset) {
    0 -> "Hoy"
    1 -> "Ayer"
    else -> dayOfMonth.toString()
}
