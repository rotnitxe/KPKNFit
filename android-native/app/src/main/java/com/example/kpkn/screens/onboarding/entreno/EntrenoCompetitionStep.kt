package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.onboarding.CompetitionAnswer
import com.example.kpkn.domain.onboarding.CompetitionRules
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.periodization.Horizon
import com.example.kpkn.domain.training.periodization.PeriodLayout
import com.example.kpkn.domain.training.periodization.PhaseKind
import com.example.kpkn.domain.training.periodization.PhaseSpan
import com.example.kpkn.domain.training.periodization.ProgramVariant
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.seg
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_ART_SCALE
import com.example.kpkn.screens.onboarding.design.entreno.SCENE_CANVAS
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointChoice
import com.example.kpkn.screens.onboarding.design.entreno.StartingPointGrid
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.competitionArtOf
import com.example.kpkn.screens.onboarding.design.entreno.smooth
import com.example.kpkn.screens.onboarding.design.entreno.weekBleed
import com.example.kpkn.screens.onboarding.effectiveWeekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/*
 * PASO «¿Compites?» (COMPETITION, bloque Entreno). Tres símbolos de elección única (sí con fecha, compito sin fecha, no) que
 * cambian con la disciplina; con «Sí, tengo fecha», debajo: el calendario de mes propio (de hoy + 14 días a hoy + 18 meses),
 * el nombre opcional del evento y la línea de bloques con los tramos de `PeriodLayout` hasta la competición.
 */

/** Marca de prueba de una opción: `setup-competition-WITH_DATE`, `setup-competition-NO_DATE`, `setup-competition-NO`. */
internal fun competitionTag(answer: CompetitionAnswer): String = "setup-competition-${answer.name}"

/** Marca de prueba de un día del calendario: `competition-day-2027-03-14`. */
internal fun competitionDayTag(date: LocalDate): String = "competition-day-$date"

/** Nombre de un tramo de la línea de bloques (solo fases del periodizado hasta una fecha: `PeriodLayout.withDate`). */
internal fun competitionPhaseLabel(kind: PhaseKind): String = when (kind) {
    PhaseKind.ACCUMULATION -> "Acumulación"
    PhaseKind.INTENSIFICATION -> "Intensificación"
    PhaseKind.REALIZATION -> "Realización"
    PhaseKind.PEAK -> "Pico"
    PhaseKind.DELOAD -> "Descarga"
    PhaseKind.TAPER -> "Puesta a punto"
    PhaseKind.TEST -> "Prueba"
}

/** Tono de cada tramo: el pico va en el acento de la disciplina; las demás, en tinta con distinta intensidad. */
private fun competitionPhaseColor(kind: PhaseKind): Color = when (kind) {
    PhaseKind.PEAK -> SymbolPalette.musculo
    PhaseKind.INTENSIFICATION, PhaseKind.REALIZATION -> WizardColors.text.copy(alpha = 0.7f)
    PhaseKind.TEST -> WizardColors.text.copy(alpha = 0.9f)
    PhaseKind.TAPER -> WizardColors.text.copy(alpha = 0.5f)
    PhaseKind.ACCUMULATION -> WizardColors.text.copy(alpha = 0.38f)
    PhaseKind.DELOAD -> WizardColors.text.copy(alpha = 0.2f)
}

/**
 * Tramos del periodizado hasta la competición, con la semana que empieza en [weekStartDay]. Las pesadas (powerlifting,
 * halterofilia, strongman) tienen puesta a punto de dos semanas en el periodizado largo, como en el programa.
 */
internal fun competitionPhases(
    profile: TrainingGoalProfile?,
    today: LocalDate,
    meetDate: LocalDate,
    weekStartDay: Int,
): List<PhaseSpan> = PeriodLayout.phases(
    ProgramVariant.PERIODIZED,
    Horizon.ToMeet(anchor = today, meetDate = meetDate, weekStartDay = weekStartDay),
    heavy = profile != null && profile in CompetitionRules.heavy,
)

/** El paso dentro del ViewModel: lee el borrador y solo escribe por `setCompetition`, `setMeetDate` y `setMeetName`. */
@Composable
internal fun EntrenoCompetitionStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    CompetitionStepContent(
        profile = draft.goalProfile,
        answer = draft.competition,
        meetDateIso = draft.meetDateIso,
        meetName = draft.meetName,
        weekStartDay = draft.effectiveWeekStart(),
        onAnswer = { answer -> vm.setCompetition(answer) },
        onMeetDate = { iso -> vm.setMeetDate(iso) },
        onMeetName = { name -> vm.setMeetName(name) },
        keepProtocolExact = draft.keepProtocolExact,
        onKeepProtocolExact = vm::setKeepProtocolExact,
    )
}

/**
 * El control sin ViewModel: las pruebas observan qué escribe. [today] fija «hoy» (por defecto, la fecha del dispositivo).
 */
@Composable
internal fun CompetitionStepContent(
    profile: TrainingGoalProfile?,
    answer: CompetitionAnswer?,
    meetDateIso: String?,
    meetName: String,
    weekStartDay: Int,
    onAnswer: (CompetitionAnswer) -> Unit,
    onMeetDate: (String?) -> Unit,
    onMeetName: (String) -> Unit,
    today: LocalDate? = null,
    keepProtocolExact: Boolean = false,
    onKeepProtocolExact: (Boolean) -> Unit = {},
) {
    val reduced = wizardReducedMotion()
    val nowDate = remember { LocalDate.now() }
    val day = today ?: nowDate
    val arts = remember(profile, reduced) {
        CompetitionAnswer.entries.associateWith { option -> competitionArtOf(profile, option, reduced) }
    }
    val choices = CompetitionAnswer.entries.map { option ->
        StartingPointChoice(
            art = arts.getValue(option),
            label = option.label,
            tag = competitionTag(option),
            selected = option == answer,
            description = option.label,
            role = Role.RadioButton,
            onChoose = { if (option != answer) onAnswer(option) },
        )
    }
    // Tres columnas de 120 dp como mínimo: «Sí, sin fecha» cabe en dos líneas sin cortarse (el sangrado sale de los márgenes).
    StartingPointGrid(
        choices = choices,
        columns = choices.size,
        canvas = SCENE_CANVAS,
        artScale = SCENE_ART_SCALE,
        labelStyle = WizardTypography.controlLabel,
        modifier = Modifier.weekBleed(cells = choices.size, minCell = 120.dp, maxBleed = WizardSpacing.gutter),
    )
    val enter: EnterTransition = if (reduced) EnterTransition.None else expandVertically(tween(260)) + fadeIn(tween(260))
    val exit: ExitTransition = if (reduced) ExitTransition.None else shrinkVertically(tween(260)) + fadeOut(tween(260))
    AnimatedVisibility(visible = answer == CompetitionAnswer.WITH_DATE, enter = enter, exit = exit) {
        Column(verticalArrangement = Arrangement.spacedBy(WizardSpacing.sectionGap)) {
            val range = CompetitionRules.dateRange(day)
            CompetitionCalendar(
                selected = CompetitionRules.parseMeetDate(meetDateIso),
                range = range,
                today = day,
                weekStartDay = weekStartDay,
                onPick = { picked -> onMeetDate(picked.toString()) },
            )
            CompetitionNameField(meetName = meetName, onMeetName = onMeetName)
            val meetDate = CompetitionRules.parseMeetDate(meetDateIso)
            if (meetDate != null && CompetitionRules.isValidMeetDate(meetDateIso, day)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mantener el protocolo exacto aunque no termine en mi fecha",
                        modifier = Modifier.weight(1f), style = WizardTypography.bodySmall, color = WizardColors.text)
                    androidx.compose.material3.Switch(checked = keepProtocolExact,
                        onCheckedChange = onKeepProtocolExact,
                        modifier = Modifier.testTag("competition-keep-exact"))
                }
                CompetitionBlockLine(
                    profile = profile,
                    today = day,
                    meetDate = meetDate,
                    weekStartDay = weekStartDay,
                    reduced = reduced,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- calendario de mes propio

/**
 * Calendario de un mes con la semana que empieza en [weekStartDay]. Los días fuera de [range] se ven tenues y no se pueden tocar;
 * el elegido lleva un círculo de tinta con el número en negro, y hoy, un punto debajo. Los días de 48 dp como mínimo: la
 * rejilla sangra hacia los márgenes ([weekBleed]), igual que la tira de días de la semana.
 */
@Composable
private fun CompetitionCalendar(
    selected: LocalDate?,
    range: ClosedRange<LocalDate>,
    today: LocalDate,
    weekStartDay: Int,
    onPick: (LocalDate) -> Unit,
) {
    val firstMonth = YearMonth.from(range.start)
    val lastMonth = YearMonth.from(range.endInclusive)
    var shown by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: range.start).toString()) }
    val month = YearMonth.parse(shown)
    Column(modifier = Modifier.fillMaxWidth().weekBleed()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(
                description = "Mes anterior",
                enabled = month > firstMonth,
                forward = false,
                onClick = { shown = month.minusMonths(1).toString() },
            )
            Text(
                text = monthTitle(month),
                modifier = Modifier.weight(1f),
                style = WizardTypography.controlLabel.copy(fontWeight = FontWeight.Bold),
                color = WizardColors.text,
                textAlign = TextAlign.Center,
            )
            MonthArrow(
                description = "Mes siguiente",
                enabled = month < lastMonth,
                forward = true,
                onClick = { shown = month.plusMonths(1).toString() },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (column in 0 until 7) {
                val dayOfWeek = DayOfWeek.of(((weekStartDay - 1 + column) % 7) + 1)
                Text(
                    text = dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale("es")).uppercase(),
                    modifier = Modifier.weight(1f),
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        val leading = Math.floorMod(month.atDay(1).dayOfWeek.value - weekStartDay, 7)
        val length = month.lengthOfMonth()
        val rows = (leading + length + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    val dayNumber = index - leading + 1
                    if (dayNumber < 1 || dayNumber > length) {
                        Spacer(Modifier.weight(1f).height(WizardSpacing.touchTarget))
                    } else {
                        val date = month.atDay(dayNumber)
                        val enabled = date in range
                        CompetitionDayCell(
                            date = date,
                            enabled = enabled,
                            selected = date == selected,
                            isToday = date == today,
                            onPick = { onPick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** «marzo de 2027», en minúsculas como va en el texto del paso. */
private fun monthTitle(month: YearMonth): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale("es")))

/** Flecha de mes: 48 dp de objetivo táctil, con su nombre para TalkBack; apagada fuera del rango. */
@Composable
private fun MonthArrow(description: String, enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(WizardSpacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(14.dp)) {
            val color = if (enabled) WizardColors.text else WizardColors.textFaint
            val stroke = 2.dp.toPx()
            val top = Offset(if (forward) size.width * 0.35f else size.width * 0.65f, size.height * 0.15f)
            val tip = Offset(if (forward) size.width * 0.65f else size.width * 0.35f, size.height * 0.5f)
            val bottom = Offset(if (forward) size.width * 0.35f else size.width * 0.65f, size.height * 0.85f)
            drawLine(color = color, start = top, end = tip, strokeWidth = stroke)
            drawLine(color = color, start = tip, end = bottom, strokeWidth = stroke)
        }
    }
}

/**
 * Un día del calendario: un objetivo de 48 dp con su número. Elegido, un círculo de tinta con el número en negro; hoy, un punto
 * debajo. Fuera de rango, sin acción y con el número tenue. TalkBack lee la fecha completa («14 de marzo de 2027»).
 */
@Composable
private fun CompetitionDayCell(
    date: LocalDate,
    enabled: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onPick: () -> Unit,
    modifier: Modifier,
) {
    val description = buildString {
        append(CompetitionRules.dateLabel(date))
        if (isToday) append(", hoy")
    }
    Column(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(competitionDayTag(date))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val numberColor = when {
            selected -> WizardColors.ctaContent
            enabled -> WizardColors.text
            else -> WizardColors.textFaint
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(if (selected) Modifier.background(WizardColors.text, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = WizardTypography.controlValue.copy(fontSize = 18.sp),
                color = numberColor,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(if (isToday) WizardColors.textMuted else Color.Transparent, CircleShape),
        )
    }
}

// ---------------------------------------------------------------- nombre del evento

/**
 * Nombre opcional del evento, con el marco de los campos de texto del wizard (el del alias): hasta 40 caracteres, y «Nombre del
 * evento (opcional)» mientras está vacío.
 */
@Composable
private fun CompetitionNameField(meetName: String, onMeetName: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(meetName) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = WizardTypography.controlLabel.copy(color = WizardColors.text)
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val next = raw.take(CompetitionRules.MEET_NAME_MAX)
            text = next
            onMeetName(next)
        },
        textStyle = style,
        cursorBrush = SolidColor(WizardColors.text),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().testTag("competition-name"),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(WizardColors.cardFill, WizardShapes.card)
                    .border(
                        width = if (focused) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                        color = if (focused) WizardColors.selectedBorder else WizardColors.cardBorder,
                        shape = WizardShapes.card,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Nombre del evento (opcional)",
                        style = style,
                        color = WizardColors.textFaint,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

// ---------------------------------------------------------------- línea de bloques

/** Duración de un tramo al dibujarse, y el escalonado entre tramos (ms). */
private const val BLOCK_SEGMENT_MS = 900
private const val BLOCK_STEP_MS = 120

/** Duración de la bandera final (ms), que aparece al terminar el último tramo. */
private const val FLAG_MS = 300

/** Hueco entre tramos, en dp. */
private val BLOCK_GAP = 2.dp

/**
 * «Faltan 14 semanas» y la barra partida en tramos del periodizado hasta la competición, con su nombre y su número de semanas
 * en una leyenda bajo la barra. Con una semana o menos no hay tramos: se dice honestamente.
 */
@Composable
private fun CompetitionBlockLine(
    profile: TrainingGoalProfile?,
    today: LocalDate,
    meetDate: LocalDate,
    weekStartDay: Int,
    reduced: Boolean,
) {
    val weeks = CompetitionRules.weeksUntil(today, meetDate, weekStartDay)
    val phases = remember(profile, today, meetDate, weekStartDay) {
        competitionPhases(profile, today, meetDate, weekStartDay)
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (weeks == 1) "1 semana" else "$weeks semanas",
            style = WizardTypography.heroTitle.copy(fontSize = 20.sp),
            color = WizardColors.text,
            maxLines = 1,
        )
        Text(
            text = "hasta tu competición",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        if (phases.isEmpty()) {
            Text(
                text = "Tu competición está muy cerca: no da tiempo a un programa por bloques. " +
                    "Te propondremos una semana de puesta a punto.",
                style = WizardTypography.controlLabel,
                color = WizardColors.textMuted,
            )
        } else {
            CompetitionBar(phases = phases, reduced = reduced)
            CompetitionLegend(phases = phases)
        }
    }
}

/**
 * La barra de tramos: cada uno crece de izquierda a derecha en 900 ms y empieza 120 ms después del anterior; al final, una
 * bandera. Con «reducir movimiento» ya está dibujada.
 */
@Composable
private fun CompetitionBar(phases: List<PhaseSpan>, reduced: Boolean) {
    val totalWeeks = phases.sumOf { it.weeks }.coerceAtLeast(1)
    val durationMs = BLOCK_SEGMENT_MS + BLOCK_STEP_MS * (phases.size - 1)
    val progress = remember(phases) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(phases, reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = durationMs, easing = LinearEasing))
        }
    }
    val description = "Tramos: " + phases.joinToString(", ") { phase ->
        val weeksText = if (phase.weeks == 1) "1 semana" else "${phase.weeks} semanas"
        "${competitionPhaseLabel(phase.kind)}, $weeksText"
    }
    val flagPath = remember { Path() }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics { contentDescription = description },
    ) {
        val elapsed = progress.value * durationMs
        val unit = size.width / totalWeeks
        val bandTop = 8.dp.toPx()
        val bandHeight = 10.dp.toPx()
        val gap = BLOCK_GAP.toPx()
        var x = 0f
        phases.forEachIndexed { index, phase ->
            val start = index * BLOCK_STEP_MS.toFloat()
            val local = smooth(seg(elapsed, start, start + BLOCK_SEGMENT_MS))
            val width = phase.weeks * unit - gap
            drawRect(
                color = competitionPhaseColor(phase.kind),
                topLeft = Offset(x, bandTop),
                size = Size(width * local, bandHeight),
            )
            x += phase.weeks * unit
        }
        // Bandera al final: asta y banderín que aparecen con el último tramo.
        val flag = smooth(seg(elapsed, durationMs - FLAG_MS.toFloat(), durationMs.toFloat()))
        if (flag > 0f) {
            val poleX = size.width - 1.dp.toPx()
            val color = WizardColors.text.copy(alpha = flag)
            drawLine(color = color, start = Offset(poleX, 0f), end = Offset(poleX, size.height), strokeWidth = 1.5.dp.toPx())
            flagPath.reset()
            flagPath.moveTo(poleX, 0f)
            flagPath.lineTo(poleX - 9.dp.toPx() * flag, 3.5.dp.toPx())
            flagPath.lineTo(poleX, 7.dp.toPx())
            flagPath.close()
            drawPath(path = flagPath, color = color)
        }
    }
}

/** Leyenda de los tramos en orden: un punto del color del tramo, su nombre y sus semanas. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompetitionLegend(phases: List<PhaseSpan>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (phase in phases) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(competitionPhaseColor(phase.kind), CircleShape))
                Spacer(Modifier.width(6.dp))
                val weeksText = if (phase.weeks == 1) "1 semana" else "${phase.weeks} semanas"
                Text(
                    text = "${competitionPhaseLabel(phase.kind)} · $weeksText",
                    style = WizardTypography.note,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}
