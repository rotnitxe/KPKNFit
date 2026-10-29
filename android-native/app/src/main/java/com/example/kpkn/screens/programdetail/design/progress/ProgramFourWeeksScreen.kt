package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.SystemAppClock
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.screens.programdetail.design.ProgramShareIcon
import com.example.kpkn.screens.programdetail.design.structure.StructureFaint
import com.example.kpkn.screens.programdetail.design.structure.StructureInk
import com.example.kpkn.screens.programdetail.design.structure.StructureModule
import com.example.kpkn.screens.programdetail.design.structure.StructureMuted
import com.example.kpkn.screens.stories.ProgramShareSheet
import com.example.kpkn.screens.stories.ProgramStoryTemplate
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.ZoneId
import java.util.Locale
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * «Tus últimas 4 semanas» (lienzo Arreglos v1, escenas p-4w, p-4w-mid y p-4w-end; `pd4w()` de src/scenes-programa.js y `.pd-*` de
 * src/arreglos.css) y su tarjeta de entrada en Progreso (escena p-progress, `pdEntry()`). Cada medida es la del CSS, de px a dp; la letra
 * de 11–12,5 px sube a 13 sp como mínimo. Los datos los decide [buildProgramFourWeeks].
 */

// ── Colores del lienzo ──────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `--np-s1` (cajas de cifras y de semanas) y `--np-s2` (círculo de la medalla). */
private val FourWeeksSurface1 = Color(0xFF121212)
private val FourWeeksSurface2 = Color(0xFF1C1C1C)

/** `--np-hair`: filete entre filas. */
private val FourWeeksHair = Color(0x0FFFFFFF)

/** `--mus`: contorno de hoy. */
private val FourWeeksToday = Color(0xFFF49A6E)

/** Calendario: día sin nada (`#ffffff0a`) y contorno de saltada (`#ffffff40`). */
private val CalendarRest = Color(0x0AFFFFFF)
private val CalendarSkipped = Color(0x40FFFFFF)

/** Mini calendario de la entrada: día sin nada (`#ffffff14`) y contorno de saltada (`#ffffff4d`). */
private val DotRest = Color(0x14FFFFFF)
private val DotSkipped = Color(0x4DFFFFFF)

/** Barra de «Semana a semana»: pista (`#ffffff0a`) y raya del plan (`#ffffff59`). */
private val WeekTrack = Color(0x0AFFFFFF)
private val WeekPlanLine = Color(0x59FFFFFF)

/** Etiqueta de un músculo (`#ffffff0d`). */
private val TagFill = Color(0x0DFFFFFF)

/** Borde de la píldora secundaria (`#f2eee63d`). */
private val PillEdge = Color(0x3DF2EEE6)

// ── Letra del lienzo (11–12,5 px suben a 13 sp) ─────────────────────────────────────────────────────────────────────────────────

private val SpanishLocale = Locale.forLanguageTag("es")

/** `.ey`: Inter 600 con 1,2 de espaciado, en mayúsculas. */
private val EyebrowStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)

/** `.pd-h1`: Syne 800 28 / 1,05, −0,8. */
private val TitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 29.4.sp, letterSpacing = (-0.8).sp)

/** `.cap`: Inter 400 / 1,35. */
private val CapStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.5.sp)

/** `.pd-lead`: Inter 500 17 / 1,4. */
private val LeadStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.8.sp)

/** `.pd-kpis b`: Inter 600 22 / 1,1, −0,3, cifras tabulares; su `small`, Inter 500 13. */
private val KpiValueStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 24.2.sp,
    letterSpacing = (-0.3).sp,
    fontFeatureSettings = "'tnum' 1",
)
private val KpiSmallSpan = SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = StructureFaint, letterSpacing = 0.sp)

/** `.pd-sec`: Syne 700 18 / 1,2, −0,3; su `span`, Inter 500. */
private val SectionStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.6.sp, letterSpacing = (-0.3).sp)
private val SectionAsideStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp)

/** `strong` de las filas: Inter 600 14 / 1,25. */
private val RowTitleStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.5.sp)

/** `.pd-lift b`: Inter 600 14 tabular; `.pd-pr b`: 13,5; `.pd-weeks b`: 13. */
private val LiftDeltaStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = "'tnum' 1")
private val RecordKindStyle = LiftDeltaStyle.copy(fontSize = 13.5.sp)
private val WeekValueStyle = LiftDeltaStyle.copy(fontSize = 13.sp, lineHeight = 17.sp)

/** `.pd-mus`: Inter 13,5 (su `strong`, 600). */
private val MuscleNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, lineHeight = 18.sp)

/** `.pd-next strong`: Inter 600 14,5. */
private val NextTitleStyle = RowTitleStyle.copy(fontSize = 14.5.sp, lineHeight = 19.sp)

/** `.pd-entry strong`: Syne 700 17 / 1,2, −0,3. */
private val EntryTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 20.4.sp, letterSpacing = (-0.3).sp)

/** `.pill`: Inter 600 15. */
private val PillStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)

/** Hueco que deja el contenido bajo la barra (sin contar la barra de estado): 12 de margen + 48 + 16, como Program Detail. */
private val FourWeeksTopInset = 76.dp

/** La cuenta de las cifras al entrar: 1,1 s con salida cúbica, como `animateIn()` del lienzo. */
private const val COUNT_MILLIS = 1100
private val EaseOutCubic = Easing { x -> 1f - (1f - x).pow(3) }

// ── Datos ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Calcula [buildProgramFourWeeks] fuera del hilo de la interfaz. Null mientras calcula o sin [program]. Con [withMuscles] también los
 * músculos (la página); sin él, solo lo que pinta la tarjeta de entrada.
 */
@Composable
internal fun rememberProgramFourWeeks(
    program: Program?,
    logs: List<WorkoutLog>,
    currentWeekId: String?,
    withMuscles: Boolean,
    proposal: String? = null,
): ProgramFourWeeks? {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(zone) { SystemAppClock.today(zone) }
    val state = produceState<ProgramFourWeeks?>(null, program, logs, currentWeekId, withMuscles, proposal, today) {
        val source = program
        value = if (source == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                buildProgramFourWeeks(
                    program = source,
                    logs = logs,
                    currentWeekId = currentWeekId,
                    today = today,
                    zone = zone,
                    exerciseIndex = if (withMuscles) catalogExerciseIndex() else null,
                    proposal = proposal,
                )
            }
        }
    }
    return state.value
}

/**
 * La página desde Program Detail: comparte su ViewModel (el programa, sus registros, la semana activa y las propuestas pendientes).
 * «Compartir tus 4 semanas» abre la hoja de historias del programa.
 */
@Composable
internal fun ProgramFourWeeksRoute(viewModel: ProgramDetailViewModel, onBack: () -> Unit) {
    val program by viewModel.program.collectAsStateWithLifecycle()
    val logs by viewModel.programLogs.collectAsStateWithLifecycle()
    val active by viewModel.activeProgramState.collectAsStateWithLifecycle()
    val card by viewModel.nativeProgressionCard.collectAsStateWithLifecycle()
    val source = program
    val activeWeekId = active?.takeIf { source != null && it.programId == source.id }?.currentWeekId
    val currentWeekId = source?.let { fourWeeksCurrentWeekId(it, activeWeekId) }
    val proposal = remember(source, card) {
        source?.let { p ->
            val names = card.proposals.associate { it.id to it.title }
            fourWeeksProposalText(p.nativeProgressionProposals) { id -> names[id] }
        }
    }
    val model = rememberProgramFourWeeks(source, logs, currentWeekId, withMuscles = true, proposal = proposal)
    val phaseColors = remember(source) { source?.let { p -> progressWeeksOf(p).associate { it.id to it.color } }.orEmpty() }
    var sharing by rememberSaveable { mutableStateOf(false) }
    ProgramFourWeeksScreen(
        model = model,
        phaseColorOf = { weekId -> phaseColors[weekId] ?: ProgressMus },
        onBack = onBack,
        onShare = { sharing = true },
    )
    if (sharing && source != null) {
        ProgramShareSheet(program = source, logs = logs, onDismiss = { sharing = false }, initialTemplate = ProgramStoryTemplate.PROGRESO)
    }
}

// ── La página ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * `pd4w()`: la barra con solo «atrás» de vidrio y, debajo, el eyebrow, el título, el rango, la frase, las cuatro cifras, el calendario,
 * la fuerza, los récords, semana a semana, los músculos, el esfuerzo y las molestias, lo que viene y «Compartir tus 4 semanas». Una
 * sección sin datos no se pinta. Con [model] null (calculando), solo la barra.
 */
@Composable
internal fun ProgramFourWeeksScreen(
    model: ProgramFourWeeks?,
    phaseColorOf: (String) -> Color,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haze = remember { HazeState() }
    Box(modifier.fillMaxSize().background(Color.Black).testTag("cuatro_semanas")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = haze)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = FourWeeksTopInset, end = 16.dp, bottom = 40.dp),
        ) {
            if (model != null) FourWeeksContent(model, phaseColorOf, onShare)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        ) {
            GlassBackButton(onClick = onBack, haze = haze)
        }
    }
}

/** `.fb.glass`: círculo de vidrio de 48 dp con «atrás» de 20 dp, como la barra de Program Detail. */
@Composable
private fun GlassBackButton(onClick: () -> Unit, haze: HazeState) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .kpknGlassOrFallback(haze, CircleShape)
            .semantics { contentDescription = "Atrás" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = KpknLineIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp), tint = StructureInk)
    }
}

@Composable
private fun FourWeeksContent(model: ProgramFourWeeks, phaseColorOf: (String) -> Color, onShare: () -> Unit) {
    val animate = !wizardReducedMotion()
    Text(text = model.eyebrow.uppercase(SpanishLocale), style = EyebrowStyle, color = StructureMuted)
    Text(
        text = "Tus últimas 4 semanas",
        style = TitleStyle,
        color = StructureInk,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
    Text(text = model.range, style = CapStyle, color = StructureFaint)
    Text(
        text = buildAnnotatedString {
            append(model.lead.start)
            model.lead.bold?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) } }
            append(model.lead.rest)
            model.lead.muted?.let { withStyle(SpanStyle(color = StructureMuted)) { append(it) } }
        },
        style = LeadStyle,
        color = StructureInk,
        modifier = Modifier.padding(top = 14.dp, bottom = 16.dp),
    )
    FourWeeksKpis(model, animate)

    SectionTitle("Calendario", if (model.skipped == 1) "1 saltada" else if (model.skipped == 0) "ninguna saltada" else "${model.skipped} saltadas")
    StructureModule {
        CalendarGrid(model.shownDays)
        CalendarLegend(Modifier.padding(top = 10.dp))
    }

    if (model.lifts.isNotEmpty()) {
        SectionTitle("Fuerza", "mejor serie estimada")
        StructureModule(tight = true) {
            model.lifts.forEachIndexed { index, row -> LiftRow(row, divider = index < model.lifts.lastIndex) }
        }
    }

    if (model.records.isNotEmpty()) {
        val n = model.records.size
        SectionTitle("Récords", "$n en ${model.countedWeeks} ${if (model.countedWeeks == 1) "semana" else "semanas"}")
        StructureModule(tight = true) {
            model.records.forEachIndexed { index, record -> RecordRow(record, divider = index < model.records.lastIndex) }
        }
    }

    SectionTitle("Semana a semana", "series duras · hecho / plan")
    WeeksRow(model.weeks)

    if (model.muscles.isNotEmpty()) {
        SectionTitle("Músculos", "media por semana")
        StructureModule(tight = true) {
            model.muscles.forEachIndexed { index, muscle ->
                NoteRow(muscle.name, muscle.caption, muscle.tag?.label, muscle.tag?.low == true, divider = index < model.muscles.lastIndex)
            }
        }
    }

    if (model.effort.isNotEmpty()) {
        SectionTitle("Esfuerzo y molestias", null)
        StructureModule(tight = true) {
            model.effort.forEachIndexed { index, note ->
                NoteRow(note.title, note.caption, note.tag, note.low, divider = index < model.effort.lastIndex)
            }
        }
    }

    if (model.next.isNotEmpty()) {
        SectionTitle("Lo que viene", null)
        StructureModule {
            model.next.forEachIndexed { index, next ->
                NextRow(next, phaseColorOf(next.weekId), Modifier.padding(top = if (index == 0) 0.dp else 14.dp))
            }
        }
    }

    SecondaryPill(
        text = "Compartir tus 4 semanas",
        onClick = onShare,
        modifier = Modifier.padding(top = 22.dp).fillMaxWidth().testTag("cuatro_semanas_compartir"),
    )
}

/**
 * `.pd-sec`: 26 dp arriba, 10 abajo y 2 a los lados; el título a la izquierda y la nota a la derecha, sobre la misma línea base. El
 * título va siempre en una línea; si la nota no cabe entera a su lado (letra grande), baja bajo el título en vez de partirse en pedazos.
 */
@Composable
private fun SectionTitle(title: String, aside: String?) {
    Layout(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 26.dp, end = 2.dp, bottom = 10.dp),
        content = {
            Text(text = title, style = SectionStyle, color = StructureInk, softWrap = false, modifier = Modifier.semantics { heading() })
            if (aside != null) Text(text = aside, style = SectionAsideStyle, color = StructureFaint)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val titlePlaceable = measurables[0].measure(loose)
        val width = constraints.maxWidth
        if (measurables.size < 2) {
            return@Layout layout(width, titlePlaceable.height) { titlePlaceable.placeRelative(0, 0) }
        }
        val gap = 10.dp.roundToPx()
        val room = width - titlePlaceable.width - gap
        val asideMeasurable = measurables[1]
        if (asideMeasurable.maxIntrinsicWidth(titlePlaceable.height) <= room) {
            val asidePlaceable = asideMeasurable.measure(loose.copy(maxWidth = room.coerceAtLeast(0)))
            val titleBase = titlePlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: titlePlaceable.height
            val asideBase = asidePlaceable[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: asidePlaceable.height
            val base = maxOf(titleBase, asideBase)
            val titleY = base - titleBase
            val asideY = base - asideBase
            layout(width, maxOf(titleY + titlePlaceable.height, asideY + asidePlaceable.height)) {
                titlePlaceable.placeRelative(0, titleY)
                asidePlaceable.placeRelative(width - asidePlaceable.width, asideY)
            }
        } else {
            val asidePlaceable = asideMeasurable.measure(loose)
            val below = titlePlaceable.height + 2.dp.roundToPx()
            layout(width, below + asidePlaceable.height) {
                titlePlaceable.placeRelative(0, 0)
                asidePlaceable.placeRelative(0, below)
            }
        }
    }
}

// ── Cifras ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.pd-kpis`: rejilla de 2 × 2 con 8 dp de hueco; las tres primeras cifras cuentan hacia arriba al entrar. */
@Composable
private fun FourWeeksKpis(model: ProgramFourWeeks, animate: Boolean) {
    val sessions = countUp(model.sessionsDone.toDouble(), animate)
    val sets = countUp(model.hardSetsDone.toDouble(), animate)
    val tonnes = countUp(model.tonnageKg / 1000.0, animate)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiBox(countText(sessions, 0), model.sessionsPlanned.takeIf { it > 0 }?.let { "/$it" }, "sesiones")
            KpiBox(countText(sets, 0), model.hardSetsPlanned.takeIf { it > 0 }?.let { "/$it" }, "series duras")
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiBox(countText(tonnes, 1), "t", "tonelaje")
            KpiBox(durationText(model.minutes), null, "entrenando")
        }
    }
}

/** Una caja de `.pd-kpis`: `#121212`, radio 18, relleno 12 × 14; la cifra con su `small` a 2 dp y la etiqueta 4 dp debajo. */
@Composable
private fun RowScope.KpiBox(value: String, small: String?, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(FourWeeksSurface1, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = buildAnnotatedString {
                append(value)
                if (small != null) {
                    // `margin-left: 2px`: un espacio fino.
                    withStyle(KpiSmallSpan) { append(" $small") }
                }
            },
            style = KpiValueStyle,
            color = StructureInk,
            maxLines = 1,
        )
        Text(text = label, style = CapStyle, color = StructureFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** El valor que cuenta de 0 a [target] en 1,1 s (sin animación, directamente [target]). */
@Composable
private fun countUp(target: Double, animate: Boolean): Double {
    val value = remember { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(target, animate) {
        if (animate) value.animateTo(target.toFloat(), tween(durationMillis = COUNT_MILLIS, easing = EaseOutCubic)) else value.snapTo(target.toFloat())
    }
    return value.value.toDouble()
}

/** La cifra que cuenta con [decimals] decimales, coma decimal y punto de miles («1.986», «42,4»). */
private fun countText(value: Double, decimals: Int): String {
    val text = String.format(Locale.ROOT, "%,.${decimals}f", value)
    return text.replace(',', '\u0000').replace('.', ',').replace('\u0000', '.')
}

// ── Calendario ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.pd-cal`: L M X J V S D y una fila por semana, 7 columnas con 6 dp de hueco; cada día, 26 dp de alto y radio 7. */
@Composable
private fun CalendarGrid(days: List<FourWeeksDay>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { letter ->
                Text(text = letter, style = CapStyle, color = StructureFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day -> CalendarCell(day, Modifier.weight(1f)) }
            }
        }
    }
}

/** Un día: hecho en tinta, saltado con contorno de 1,5 dp `#ffffff40`, hoy con contorno de 2 dp naranja; si no, `#ffffff0a`. */
@Composable
private fun CalendarCell(day: FourWeeksDay, modifier: Modifier) {
    val corner = 7.dp
    Box(
        modifier
            .height(26.dp)
            .drawBehind {
                val radius = CornerRadius(corner.toPx())
                when (day.state) {
                    FourWeeksDayState.DONE -> drawRoundRect(StructureInk, cornerRadius = radius)
                    FourWeeksDayState.SKIPPED -> if (!day.isToday) insetOutline(CalendarSkipped, 1.5.dp.toPx(), radius)
                    FourWeeksDayState.REST -> if (!day.isToday) drawRoundRect(CalendarRest, cornerRadius = radius)
                }
                if (day.isToday) insetOutline(FourWeeksToday, 2.dp.toPx(), radius)
            },
    )
}

/** Contorno hacia dentro (`box-shadow: inset 0 0 0 Npx`). */
private fun DrawScope.insetOutline(color: Color, stroke: Float, radius: CornerRadius) {
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((radius.x - stroke / 2f).coerceAtLeast(0f)),
        style = Stroke(stroke),
    )
}

/** `.pd-calk`: Hecha, Saltada y Hoy, con su cuadrado de 10 dp (radio 3) a 6 dp; 14 dp entre ellas. */
@Composable
private fun CalendarLegend(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Hecha") { drawRoundRect(StructureInk, cornerRadius = CornerRadius(3.dp.toPx())) }
        LegendItem("Saltada") { insetOutline(CalendarSkipped, 1.5.dp.toPx(), CornerRadius(3.dp.toPx())) }
        LegendItem("Hoy") { insetOutline(FourWeeksToday, 2.dp.toPx(), CornerRadius(3.dp.toPx())) }
    }
}

@Composable
private fun LegendItem(text: String, mark: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).drawBehind(mark))
        Text(text = text, style = CapStyle, color = StructureFaint)
    }
}

// ── Fuerza y récords ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.pd-lift`: nombre y «142,5 kg · 6 sesiones», el cambio (verde si sube, rojo si baja, gris si no) y su línea de 74 × 28. */
@Composable
private fun LiftRow(row: ProgressLiftRow, divider: Boolean) {
    val color = when (row.status) {
        LiftTrendStatus.UP -> ProgressDone
        LiftTrendStatus.DOWN -> ProgressDanger
        else -> StructureMuted
    }
    RowWithHair(divider) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = row.name, style = RowTitleStyle, color = StructureInk)
                Text(text = fourWeeksLiftCaption(row), style = CapStyle, color = StructureFaint, modifier = Modifier.padding(top = 2.dp))
            }
            Text(text = fourWeeksDeltaText(row.deltaKg), style = LiftDeltaStyle, color = color, maxLines = 1, textAlign = TextAlign.End)
            ProgressSparkline(values = row.points, color = if (row.status == LiftTrendStatus.DOWN) ProgressDanger else StructureInk)
        }
    }
}

/** `.pd-pr`: la medalla (trofeo de 17 dp en ámbar sobre un círculo de 34), el ejercicio con su marca y «Mejor serie» / «Mejor marca». */
@Composable
private fun RecordRow(record: FourWeeksRecord, divider: Boolean) {
    RowWithHair(divider) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(34.dp).background(FourWeeksSurface2, CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = KpknLineIcons.Trophy, contentDescription = null, modifier = Modifier.size(17.dp), tint = ProgressEne)
            }
            Column(Modifier.weight(1f)) {
                Text(text = record.exercise, style = RowTitleStyle, color = StructureInk)
                Text(text = record.detail, style = CapStyle, color = StructureFaint)
            }
            Text(text = record.kind, style = RecordKindStyle, color = StructureInk, maxLines = 1)
        }
    }
}

/** Una fila con el filete `--np-hair` debajo si no es la última. */
@Composable
private fun RowWithHair(divider: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        content()
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(FourWeeksHair))
    }
}

// ── Semana a semana ──────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** `.pd-weeks`: 4 columnas de igual ancho con 8 dp de hueco; con menos semanas, las que haya a la izquierda. */
@Composable
private fun WeeksRow(weeks: List<FourWeeksColumn>) {
    val scale = weeks.maxOfOrNull { maxOf(it.hardSets, it.plannedSets) }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        weeks.forEach { week -> WeekColumn(week, scale, Modifier.weight(1f).fillMaxHeight()) }
        repeat((FOUR_WEEKS - weeks.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * Una semana: «S4 · hoy», la barra de 56 dp (hecho en tinta desde abajo; el plan, raya discontinua), «62/64» y «11,8 t · 3 h 38». Caja
 * `#121212`, radio 16, relleno 12 × 10.
 */
@Composable
private fun WeekColumn(week: FourWeeksColumn, scale: Int, modifier: Modifier) {
    Column(
        modifier
            .background(FourWeeksSurface1, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        // «S4 · hoy» no se corta: con letra grande encoge hasta 10 sp antes que perder el «hoy».
        BasicText(
            text = week.label,
            style = CapStyle.copy(fontWeight = FontWeight.SemiBold, color = StructureFaint),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = CapStyle.fontSize, stepSize = 0.5.sp),
            // La misma altura aunque encoja, para que las cuatro barras queden a la par.
            modifier = Modifier.height(with(LocalDensity.current) { CapStyle.lineHeight.toDp() }),
        )
        WeekBar(done = week.hardSets, planned = week.plannedSets, scale = scale, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = if (week.plannedSets > 0) "${week.hardSets}/${week.plannedSets}" else "${week.hardSets}",
            style = WeekValueStyle,
            color = StructureInk,
            maxLines = 1,
        )
        val tonnes = if (week.tonnageKg <= 0.0) "0" else fourWeeksTonnes(week.tonnageKg)
        // Cifra y unidad no se separan («3 h», «4,3 t»): si no cabe, la línea se parte por el « · ».
        Text(text = "$tonnes t · ${durationText(week.minutes).replace(' ', ' ')}", style = CapStyle, color = StructureFaint)
    }
}

/** `.bar`: pista `#ffffff0a` de 56 dp y radio 6; lo hecho, en tinta desde abajo; el plan, raya de 1 dp discontinua `#ffffff59`. */
@Composable
private fun WeekBar(done: Int, planned: Int, scale: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.fillMaxWidth().height(56.dp).clip(shape).background(WeekTrack, shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val fill = h * (done.toFloat() / scale).coerceIn(0f, 1f)
            if (fill > 0f) {
                drawRoundRect(StructureInk, topLeft = Offset(0f, h - fill), size = Size(size.width, fill), cornerRadius = CornerRadius(6.dp.toPx()))
            }
            if (planned > 0) {
                val y = h - h * (planned.toFloat() / scale).coerceIn(0f, 1f)
                val stroke = 1.dp.toPx()
                val lineY = (y - stroke / 2f).coerceAtLeast(stroke / 2f)
                drawLine(
                    color = WeekPlanLine,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                )
            }
        }
    }
}

// ── Músculos, esfuerzo y lo que viene ────────────────────────────────────────────────────────────────────────────────────────────

/** `.pd-mus`: el nombre en 600, el pie en `.cap` y, a la derecha, la etiqueta (`#ffffff0d`, radio 9, 3 × 9; ámbar si [low]). */
@Composable
private fun NoteRow(title: String, caption: String, tag: String?, low: Boolean, divider: Boolean) {
    RowWithHair(divider) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MuscleNameStyle, color = StructureInk)
                Text(text = caption, style = CapStyle, color = StructureFaint)
            }
            if (tag != null) {
                Text(
                    text = tag,
                    style = CapStyle,
                    color = if (low) ProgressEne else StructureMuted,
                    maxLines = 1,
                    modifier = Modifier.background(TagFill, RoundedCornerShape(9.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** `.pd-next`: la barra de 10 dp en el color de la fase (radio 5, a toda la altura), «Semana 5 · Intensificación» y su línea. */
@Composable
private fun NextRow(next: FourWeeksNext, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(color, RoundedCornerShape(5.dp)))
        Column(Modifier.weight(1f)) {
            Text(text = next.title, style = NextTitleStyle, color = StructureInk)
            if (next.line.isNotEmpty()) Text(text = next.line, style = CapStyle, color = StructureFaint)
        }
    }
}

/** `.pill.sec`: 52 dp, radio 26, filete `#f2eee63d`, Inter 600 15 con el icono de compartir de 18 dp a 8. */
@Composable
private fun SecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .border(1.dp, PillEdge, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = ProgramShareIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = StructureInk)
        Text(text = text, style = PillStyle, color = StructureInk, maxLines = 1)
    }
}

// ── La entrada en Progreso ───────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * `pdEntry()`: el módulo con «Tus últimas 4 semanas», debajo lo más importante en una línea y, a la derecha, el mini calendario de los 28
 * días y la flecha. Abre la página. Con [model] null (calculando o sin datos), el título y el calendario vacío.
 */
@Composable
internal fun ProgramFourWeeksEntry(model: ProgramFourWeeks?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val line = model?.entryLine
    StructureModule(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("progreso_4_semanas")
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull("Tus últimas 4 semanas", line).joinToString(". ")
                }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "Tus últimas 4 semanas", style = EntryTitleStyle, color = StructureInk)
                if (line != null) {
                    Text(text = line, style = CapStyle, color = StructureFaint, modifier = Modifier.padding(top = 4.dp))
                }
            }
            EntryDots(model?.days)
            Icon(imageVector = KpknLineIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = StructureInk)
        }
    }
}

/** `.pd-dots`: 7 columnas de cuadrados de 8 dp (radio 2) con 4 dp de hueco; hecha en tinta, saltada con contorno blanco al 30 %. */
@Composable
private fun EntryDots(days: List<FourWeeksDay>?) {
    val cells = days ?: emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(FOUR_WEEKS) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { day ->
                    val state = cells.getOrNull(week * 7 + day)?.state ?: FourWeeksDayState.REST
                    EntryDot(state, 8.dp)
                }
            }
        }
    }
}

@Composable
private fun EntryDot(state: FourWeeksDayState, size: Dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val radius = CornerRadius(2.dp.toPx())
                when (state) {
                    FourWeeksDayState.DONE -> drawRoundRect(StructureInk, cornerRadius = radius)
                    FourWeeksDayState.SKIPPED -> insetOutline(DotSkipped, 1.2.dp.toPx(), radius)
                    FourWeeksDayState.REST -> drawRoundRect(DotRest, cornerRadius = radius)
                }
            },
    )
}
