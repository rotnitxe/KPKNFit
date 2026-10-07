package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.eOutCubic
import com.example.kpkn.screens.onboarding.design.seg
import kotlin.math.sin

/*
 * «¿Qué días puedes entrenar?»: una vista de calendario semanal. Una tira de siete días ordenada desde el día en que
 * arranca la semana; cada día es un símbolo circular que se colorea al tocarlo (el relleno crece desde el centro y la marca de
 * «hecho» se dibuja). Encima, el número de días en grande; debajo, «La semana empieza el jueves», que despliega las siete
 * opciones en línea y reordena la tira deslizando los días como una cinta. Con varios lugares, cada día elegido lleva el
 * glifo de su lugar (se toca para recorrerlos).
 */

/** Franja sobre los discos donde vive el sol del día más fuerte (y su nota). */
private val WeekSunSlot = 22.dp

/** Alto de la zona del disco. */
private val WeekDiscBox = 48.dp

/** Alto de la zona táctil del lugar de cada día. */
private val WeekPlaceSlotHeight = 44.dp

/** Tamaño del sol que marca el día más fuerte. */
private val WeekSunSize = 18.dp

/** Duración del deslizado de la tira al cambiar el inicio de semana. */
private const val WEEK_SLIDE_MS = 520

/** Texto de la marca del día más fuerte (COPY: «Calendario»). */
private const val WEEK_STRONGEST_NOTE = "Tu sesión más fuerte"

/** Rótulo del selector de lugar por día (COPY: «Calendario»). */
private const val WEEK_PLACE_CAPTION = "¿Dónde entrenas ese día?"

/**
 * Calendario semanal para elegir los días de entreno.
 *
 * - [weekStartDay] (1 = lunes … 7 = domingo) es el primer día de la tira; [selectedDays] los días de entreno (1 a 7 de ellos:
 *   «al menos uno» lo valida quien usa el componente). [onToggleDay] alterna un día; [onWeekStartChange] cambia el inicio.
 * - [freshestDay] lleva la marca «Tu sesión más fuerte» (un sol sobre el día) cuando está entre los elegidos.
 * - Con dos o más [places] aparece, bajo cada día elegido, el glifo de su lugar ([dayPlaces] sin entrada = el primero en orden
 *   gimnasio, casa, espacios públicos); tocarlo pasa al siguiente lugar y avisa con [onDayPlace]. Con un solo lugar no hay nada.
 *
 * Marcas de prueba: `setup-weekday-<n>` (cada día; `Role.Checkbox`), `setup-weekplace-<n>` (el lugar de ese día),
 * `setup-weekstart` (el selector de inicio) y `setup-weekstart-<n>` (sus opciones).
 */
@Composable
fun WeekCalendar(
    weekStartDay: Int,
    selectedDays: Set<Int>,
    freshestDay: Int?,
    onToggleDay: (Int) -> Unit,
    onWeekStartChange: (Int) -> Unit,
    places: Set<TrainingPlace>,
    dayPlaces: Map<Int, TrainingPlace>,
    onDayPlace: (Int, TrainingPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = weekReducedMotion()
    val start = if (isWeekDay(weekStartDay)) weekStartDay else 1
    val chosen = remember(selectedDays) { selectedDays.filter { isWeekDay(it) }.toSet() }
    val sunDay = freshestDay?.takeIf { it in chosen }
    val showPlaces = showsDayPlaces(places)
    val clock = rememberWeekClock(active = sunDay != null && !reduced)
    val measurer = rememberTextMeasurer()
    val letters = remember(measurer) {
        List(WEEK_DAY_COUNT) { measurer.measureWeekLetter(dayInitial(it + 1), WizardTypography.measure) }
    }
    var pickingStart by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        WeekDaysCounter(count = chosen.size, reduced = reduced)
        Spacer(Modifier.height(14.dp))
        WeekStrip(
            start = start,
            chosen = chosen,
            sunDay = sunDay,
            places = places,
            dayPlaces = dayPlaces,
            showPlaces = showPlaces,
            letters = letters,
            clock = clock,
            reduced = reduced,
            onToggle = onToggleDay,
            onDayPlace = onDayPlace,
        )
        AnimatedVisibility(
            visible = showPlaces && chosen.isNotEmpty(),
            enter = if (reduced) EnterTransition.None else fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(160)) + shrinkVertically(tween(160)),
        ) {
            Text(
                text = WEEK_PLACE_CAPTION,
                style = WizardTypography.note,
                color = WeekPalette.faint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        WeekStartPicker(
            start = start,
            expanded = pickingStart,
            reduced = reduced,
            onExpandedChange = { pickingStart = it },
            onPick = onWeekStartChange,
        )
    }
}

// ─── Contador ────────────────────────────────────────────────────────────────────────────────────────────────

/** El número de días en grande (Syne) con «días por semana» al lado; el número rueda al cambiar. */
@Composable
private fun WeekDaysCounter(count: Int, reduced: Boolean) {
    val numberColor by animateColorAsState(
        targetValue = if (count > 0) WeekPalette.ink else WeekPalette.faint,
        animationSpec = if (reduced) snap() else tween(220),
        label = "weekCountColor",
    )
    val unit = daysPerWeekUnit(count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("setup-week-count")
            .clearAndSetSemantics { contentDescription = "$count $unit" },
        verticalAlignment = Alignment.Bottom,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val up = targetState > initialState
                    (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(200))) togetherWith
                        (slideOutVertically(tween(200)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(140)))
                }
            },
            label = "weekCount",
        ) { number ->
            Text(
                text = weekCounterText(number),
                style = WizardTypography.controlValue,
                color = numberColor,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = unit,
            style = WizardTypography.controlLabel,
            color = WeekPalette.muted,
            maxLines = 1,
        )
    }
}

// ─── Tira de la semana ───────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekStrip(
    start: Int,
    chosen: Set<Int>,
    sunDay: Int?,
    places: Set<TrainingPlace>,
    dayPlaces: Map<Int, TrainingPlace>,
    showPlaces: Boolean,
    letters: List<WeekLetter>,
    clock: WeekClock,
    reduced: Boolean,
    onToggle: (Int) -> Unit,
    onDayPlace: (Int, TrainingPlace) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stripWidth = maxWidth
        val cellWidth = stripWidth / WEEK_DAY_COUNT
        val cellWidthPx = with(LocalDensity.current) { cellWidth.toPx() }
        // La celda mide ≥ 44 dp en un teléfono de 360 dp; el disco deja un hueco entre vecinos.
        val disc = (cellWidth - 5.dp).coerceIn(34.dp, 46.dp)

        // Posición CONTINUA y circular del inicio de semana: al cambiar, recorre el camino más corto y cada día se coloca
        // con `weekConveyorSlot` (los que salen por un borde vuelven a entrar por el otro, ocultos por su opacidad).
        val startPos = remember { Animatable(start.toFloat()) }
        val slideMillis = weekMillis(WEEK_SLIDE_MS)
        LaunchedEffect(start, reduced) {
            val delta = circularShortestDelta(startPos.value, start.toFloat())
            val target = startPos.value + delta
            if (reduced || delta == 0f) {
                startPos.snapTo(target)
            } else {
                startPos.animateTo(target, tween(slideMillis, easing = FastOutSlowInEasing))
            }
        }

        Box(Modifier.fillMaxWidth()) {
            for (day in 1..WEEK_DAY_COUNT) {
                key(day) {
                    val isChosen = day in chosen
                    val place = if (showPlaces && isChosen) placeOfDay(day, places, dayPlaces) else null
                    WeekDayCell(
                        day = day,
                        selected = isChosen,
                        isSun = day == sunDay,
                        place = place,
                        reservePlaceSlot = showPlaces,
                        letter = letters[day - 1],
                        disc = disc,
                        clock = clock,
                        reduced = reduced,
                        onToggle = { onToggle(day) },
                        onPlaceTap = {
                            nextPlace(place, places)?.let { onDayPlace(day, it) }
                        },
                        modifier = Modifier
                            .width(cellWidth)
                            .graphicsLayer {
                                val slot = weekConveyorSlot(day, startPos.value)
                                translationX = slot * cellWidthPx
                                alpha = weekConveyorAlpha(slot)
                            },
                    )
                }
            }
            // Nota del día más fuerte: junto al sol, en la franja de arriba, del lado con más sitio.
            WeekStrongestNote(
                slot = sunDay?.let { weekSlotOf(it, start) },
                stripWidth = stripWidth,
                cellWidth = cellWidth,
                reduced = reduced,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** «Tu sesión más fuerte» al lado del sol; al reordenar la semana se desvanece y reaparece junto al sol en su nuevo sitio. */
@Composable
private fun WeekStrongestNote(
    slot: Int?,
    stripWidth: Dp,
    cellWidth: Dp,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = slot,
        modifier = modifier
            .fillMaxWidth()
            .height(WeekSunSlot),
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(220, delayMillis = 260)) togetherWith fadeOut(tween(120))
            }
        },
        label = "weekStrongestNote",
    ) { target ->
        if (target != null) {
            val sunCenter = cellWidth * (target + 0.5f)
            val gap = WeekSunSize / 2 + 6.dp
            val roomRight = stripWidth - sunCenter - gap
            val roomLeft = sunCenter - gap
            val onRight = roomRight >= roomLeft
            // La nota es solo visual: la descripción del propio día ya dice que es el de la sesión más fuerte.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(WeekSunSlot)
                    .testTag("setup-week-strongest")
                    .clearAndSetSemantics { },
            ) {
                Text(
                    text = WEEK_STRONGEST_NOTE,
                    style = WizardTypography.note,
                    color = WeekPalette.energia.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (onRight) TextAlign.Start else TextAlign.End,
                    modifier = if (onRight) {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = sunCenter + gap)
                            .widthIn(max = roomRight.coerceAtLeast(40.dp))
                    } else {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = stripWidth - sunCenter + gap)
                            .widthIn(max = roomLeft.coerceAtLeast(40.dp))
                    },
                )
            }
        }
    }
}

// ─── Un día ──────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekDayCell(
    day: Int,
    selected: Boolean,
    isSun: Boolean,
    place: TrainingPlace?,
    reservePlaceSlot: Boolean,
    letter: WeekLetter,
    disc: Dp,
    clock: WeekClock,
    reduced: Boolean,
    onToggle: () -> Unit,
    onPlaceTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fillSpec: AnimationSpec<Float> = if (reduced) snap() else tween(weekMillis(340), easing = FastOutSlowInEasing)
    val sunSpec: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.42f, stiffness = 380f)
    val fill by animateFloatAsState(if (selected) 1f else 0f, fillSpec, label = "weekFill")
    val sun by animateFloatAsState(if (selected && isSun) 1f else 0f, sunSpec, label = "weekSun")
    val press by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "weekPress",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) WeekPalette.ink else WeekPalette.faint,
        animationSpec = if (reduced) snap() else tween(220),
        label = "weekLabel",
    )
    val description = dayChoiceDescription(day, selected, strongest = selected && isSun)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup-weekday-$day")
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
            Canvas(Modifier.fillMaxWidth().height(WeekSunSlot + WeekDiscBox)) {
                val cx = size.width / 2f
                val center = Offset(cx, WeekSunSlot.toPx() + WeekDiscBox.toPx() / 2f)
                val r = disc.toPx() / 2f * press
                val f = fill.coerceIn(0f, 1f)

                // Disco de vidrio neutro (el estado tenue).
                drawCircle(WizardColors.glassFill, r, center)
                drawCircle(WizardColors.glassBorder, r - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
                // Relleno de columna que crece desde el centro.
                if (f > 0.002f) drawCircle(WeekPalette.columna, r * eOutCubic(f), center)
                // La inicial se va mientras entra la marca de «hecho».
                val letterAlpha = 1f - seg(f, 0f, 0.45f)
                if (letterAlpha > 0.01f) {
                    drawWeekLetter(letter, center, WeekPalette.muted.copy(alpha = letterAlpha))
                }
                drawWeekCheck(center, r, seg(f, 0.35f, 1f), WeekPalette.onColumna, 2.4.dp.toPx())

                // Sol del día más fuerte: aparece con resorte.
                val s = sun.coerceAtLeast(0f)
                if (s > 0.01f) {
                    drawWeekSun(
                        center = Offset(cx, WeekSunSlot.toPx() / 2f + 1.dp.toPx()),
                        size = WeekSunSize.toPx() * s,
                        rotationDeg = if (reduced) 0f else clock.seconds * 18f,
                        pulse = if (reduced) 0f else 0.5f + 0.5f * sin(clock.seconds * 2.4f),
                        color = WeekPalette.energia,
                        strokePx = 1.6.dp.toPx(),
                    )
                }
            }
            Text(
                text = dayShortName(day),
                style = WizardTypography.note,
                color = labelColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
        if (reservePlaceSlot) {
            WeekPlaceSlot(day = day, place = place, reduced = reduced, onTap = onPlaceTap)
        }
    }
}

/** Bajo un día elegido, el glifo de su lugar; tocarlo pasa al siguiente. Sin día elegido, el hueco queda vacío (sin saltos). */
@Composable
private fun WeekPlaceSlot(day: Int, place: TrainingPlace?, reduced: Boolean, onTap: () -> Unit) {
    val dayName = dayFullName(day).lowercase()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WeekPlaceSlotHeight)
            .testTag("setup-weekplace-$day")
            .then(
                if (place != null) {
                    Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Cambiar el lugar del $dayName",
                            onClick = onTap,
                        )
                        .semantics { contentDescription = "Lugar del $dayName: ${place.label}" }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = place,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), initialScale = 0.5f) + fadeIn(tween(160))) togetherWith
                        (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
                }
            },
            label = "weekPlace",
        ) { shown ->
            if (shown != null) {
                Canvas(Modifier.size(28.dp)) {
                    drawWeekPlaceGlyph(shown, Offset(size.width / 2f, size.height / 2f), size.minDimension, WeekPalette.ink, 1.7.dp.toPx())
                }
            }
        }
    }
}

// ─── Inicio de semana ────────────────────────────────────────────────────────────────────────────────────────

/**
 * «La semana empieza el jueves» con un chevrón: tocarlo despliega en línea las siete opciones; elegir una recoge el
 * selector y reordena la tira. No bloquea nada: solo cambia el orden en que se ve la semana.
 */
@Composable
private fun WeekStartPicker(
    start: Int,
    expanded: Boolean,
    reduced: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (Int) -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(240, easing = FastOutSlowInEasing),
        label = "weekChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dayName = dayFullName(start).lowercase()
    val label = buildAnnotatedString {
        append("La semana empieza el ")
        withStyle(SpanStyle(color = WeekPalette.ink, fontWeight = FontWeight.SemiBold)) { append(dayName) }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("setup-weekstart")
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) "Cerrar las opciones" else "Cambiar el primer día de la semana",
                    onClick = { onExpandedChange(!expanded) },
                )
                // Va DESPUÉS de `clickable`: conserva su rol y su acción y descarta el texto suelto de los hijos.
                .clearAndSetSemantics {
                    contentDescription = weekStartLabel(start)
                    stateDescription = if (expanded) "Desplegado" else "Plegado"
                }
                .graphicsLayer { alpha = if (pressed) 0.6f else 1f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = WizardTypography.controlLabel,
                color = WeekPalette.muted,
            )
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(14.dp)) {
                drawWeekChevron(
                    center = Offset(size.width / 2f, size.height / 2f),
                    size = size.width * 0.8f,
                    rotationDeg = chevron,
                    color = WeekPalette.muted,
                    strokePx = 1.8.dp.toPx(),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) EnterTransition.None else fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = if (reduced) ExitTransition.None else fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (day in 1..WEEK_DAY_COUNT) {
                    WeekStartOption(
                        day = day,
                        selected = day == start,
                        reduced = reduced,
                        onClick = {
                            onPick(day)
                            onExpandedChange(false)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Una de las siete opciones de inicio: el nombre corto del día, con una rayita bajo el elegido. Sin caja. */
@Composable
private fun WeekStartOption(
    day: Int,
    selected: Boolean,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) WeekPalette.ink else WeekPalette.faint,
        animationSpec = if (reduced) snap() else tween(200),
        label = "weekStartColor",
    )
    val bar by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "weekStartBar",
    )
    val description = "Empezar la semana el ${dayFullName(day).lowercase()}, " + if (selected) "elegido" else "sin elegir"
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("setup-weekstart-$day")
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = dayShortName(day),
            style = WizardTypography.note.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = color,
            maxLines = 1,
        )
        Canvas(Modifier.padding(top = 4.dp).width(16.dp).height(2.dp)) {
            val w = size.width * bar
            if (w > 0.5f) {
                drawLine(
                    color = WeekPalette.ink,
                    start = Offset((size.width - w) / 2f, size.height / 2f),
                    end = Offset((size.width + w) / 2f, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
