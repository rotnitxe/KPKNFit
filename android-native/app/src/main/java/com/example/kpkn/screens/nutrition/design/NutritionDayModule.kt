package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.nutrition.NutritionWeekDay
import com.example.kpkn.screens.onboarding.design.MacroRingFractions
import com.example.kpkn.screens.onboarding.design.WIZARD_RING_CALORIE_SCALE
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.WizardMacroRings
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.MacroColors
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.time.DayOfWeek
import java.time.LocalDate

/** Diámetro de los anillos de macros de «Tu día»: 112 dp, como en la nutrición de Home. */
private val NutritionRingSize = 112.dp

/** Aire entre los anillos y la columna de la cifra con su lectura. */
private val NutritionRingGap = 16.dp

/** Escala de letra a partir de la que la leyenda y las cifras se apilan (como Home). */
internal const val NutritionLargeTextScale = 1.15f

/** Clave de la memoria de la primera vez: las cifras de «Tu día» cuentan solo la primera vez que se ven en el proceso. */
private const val NUTRITION_FIGURE_KEY = "nutrition-day"

/** Número de cada día de la tira, encima de su anillo: Syne Bold 17 sp con cifras tabulares (el número de Home). */
private val NutritionDayNumber = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.Bold,
    fontSize = 17.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = "'tnum' 1",
)

/** Cifra de un macro en la leyenda de tres columnas: Inter SemiBold 15 sp con cifras tabulares. */
private val NutritionMacroValue = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1")

private val dayNamesEs = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Un macro del día: los gramos registrados y su meta en gramos (null = sin meta). */
data class NutritionMacroFigure(
    val grams: Int,
    val goal: Int?,
)

/**
 * Lo que se lee debajo de la tira de «Tu día» para un día: las kcal, su meta (null = sin meta) y los tres macros. [estimate] marca
 * las cifras de un total incierto, que se muestran con «≈».
 */
data class NutritionDayFigure(
    val kcal: Int,
    val kcalGoal: Int?,
    val estimate: Boolean,
    val protein: NutritionMacroFigure,
    val carbs: NutritionMacroFigure,
    val fat: NutritionMacroFigure,
)

/**
 * «Tu día» (NUTRITION_V1 §2.2): la tira de siete días con su mini anillo de kcal, y debajo, con un filete, los anillos de macros, la
 * cifra del día con su lectura («te quedan» o «te pasaste») y la leyenda de P, C y G. Tocar un día llama a [onSelectDate] y todo lo de
 * abajo cambia con `AnimatedContent`. Sin plan, ofrece «Definir objetivos» ([onDefineGoals]); con plan y sin meta, solo seguimiento.
 * Mientras el estado del usuario no se ha leído ([loaded] false) es un esqueleto: nunca «sin objetivos» por adelantado.
 */
@Composable
fun NutritionDayModule(
    loaded: Boolean,
    days: List<NutritionWeekDay>,
    selectedDate: String,
    totals: DailyMacroTotals,
    goals: DayGoalsResult,
    hasActivePlan: Boolean,
    onSelectDate: (String) -> Unit,
    onDefineGoals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!loaded) {
        KpknSkeletonModule(lines = 3, modifier = modifier)
        return
    }
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val largeText = density.fontScale > NutritionLargeTextScale
    val slidePx = with(density) { 12.dp.roundToPx() }
    val dayGoals = (goals as? DayGoalsResult.Present)?.goals
    val figure = NutritionDayFigure(
        kcal = kotlin.math.round(totals.calories).toInt(),
        kcalGoal = dayGoals?.calorieGoal?.takeIf { it > 0 },
        estimate = totals.isEstimate,
        protein = NutritionMacroFigure(kotlin.math.round(totals.protein).toInt(), dayGoals?.proteinGoal?.takeIf { it > 0 }),
        carbs = NutritionMacroFigure(kotlin.math.round(totals.carbs).toInt(), dayGoals?.carbGoal?.takeIf { it > 0 }),
        fat = NutritionMacroFigure(kotlin.math.round(totals.fats).toInt(), dayGoals?.fatGoal?.takeIf { it > 0 }),
    )
    KpknModule(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, top = 18.dp, end = 12.dp, bottom = 20.dp),
    ) {
        KpknModuleHeader(label = "Tu día", modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(14.dp))
        NutritionWeekStrip(days = days, selectedDate = selectedDate, onSelect = onSelectDate)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Spacer(Modifier.height(18.dp))
        AnimatedContent(
            targetState = selectedDate,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                // El día nuevo entra desde el lado del cambio (12 dp) con fundido cruzado de 220 ms; con movimiento reducido, solo fundido.
                val forward = targetState >= initialState
                if (reduced) {
                    fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 120))
                } else {
                    (fadeIn(animationSpec = tween(durationMillis = 220)) +
                        slideInHorizontally(animationSpec = tween(durationMillis = 220)) { if (forward) slidePx else -slidePx }) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 220))
                }
            },
            label = "nutritionDayContent",
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                NutritionDayFiguresBlock(
                    figure = figure,
                    hasActivePlan = hasActivePlan,
                    onDefineGoals = onDefineGoals,
                )
                Spacer(Modifier.height(16.dp))
                NutritionMacroLegend(figure = figure, largeText = largeText)
            }
        }
    }
}

/** La tira de los siete días. Sin días no pinta nada. */
@Composable
private fun NutritionWeekStrip(
    days: List<NutritionWeekDay>,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    if (days.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            NutritionDayColumn(
                day = day,
                selected = day.date == selectedDate,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Una columna de la tira, de arriba abajo: la letra del día y su mini anillo de 28 dp con el número dentro. Es un `Role.Tab`; la zona
 * táctil mide 48 dp de ancho aunque la columna sea algo más estrecha, para no pasar de los 16 dp de margen.
 */
@Composable
private fun NutritionDayColumn(
    day: NutritionWeekDay,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val date = remember(day.date) { runCatching { LocalDate.parse(day.date) }.getOrNull() }
    val interaction = remember { MutableInteractionSource() }
    val description = nutritionDayDescription(day, date)
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .requiredWidth(48.dp)
                .testTag("nutrition-day-${day.date}")
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelect(day.date) },
                )
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date?.let { dayLetter(it) }.orEmpty(),
                style = KpknType.chip,
                color = if (day.isToday) WizardColors.text else WizardColors.textFaint,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = date?.dayOfMonth?.toString().orEmpty(),
                style = NutritionDayNumber,
                color = if (day.isToday || selected) WizardColors.text else WizardColors.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            NutritionMiniRing(day = day, selected = selected)
        }
    }
}

/**
 * El mini anillo de kcal de un día: 28 dp, pista blanca al 14 % y arco con el % de la meta en energía (en `danger` si se pasa). Hoy, el
 * arco va en tinta. El día elegido se rellena de tinta por dentro. La cifra del día va encima, fuera del anillo.
 */
@Composable
private fun NutritionMiniRing(
    day: NutritionWeekDay,
    selected: Boolean,
) {
    val goal = day.kcalGoal
    val fraction = if (goal == null || day.kcal <= 0) 0f else (day.kcal.toFloat() / goal).coerceIn(0f, 1f)
    val over = goal != null && day.kcal > goal
    val arcColor = when {
        over -> WizardColors.danger
        day.isToday -> WizardColors.text
        else -> SymbolPalette.energia
    }
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = 3.dp.toPx()
        val origin = Offset(stroke / 2f, stroke / 2f)
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = WizardColors.glassBorder,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = origin,
            size = arc,
            style = Stroke(width = stroke),
        )
        if (fraction > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = origin,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (selected) {
            drawCircle(color = WizardColors.text, radius = size.minDimension / 2f - stroke * 1.2f)
        }
    }
}

/**
 * Lo que va debajo de la tira para el día elegido. Con meta de kcal: los anillos de macros y la cifra con su lectura, a la derecha si
 * caben y apilados si no. Sin meta: la cifra a todo el ancho y, sin plan, «Definir objetivos». Las cifras cuentan la primera vez.
 */
@Composable
private fun NutritionDayFiguresBlock(
    figure: NutritionDayFigure,
    hasActivePlan: Boolean,
    onDefineGoals: () -> Unit,
) {
    val reduced = wizardReducedMotion()
    // Las cifras cuentan la primera vez que se ven en el proceso; después, el valor final (el mismo criterio que Home).
    val firstSeen = remember { NutritionFigureMemory.firstTime(NUTRITION_FIGURE_KEY) }
    var appeared by remember { mutableStateOf(!firstSeen) }
    LaunchedEffect(Unit) { appeared = true }
    val countSpec: AnimationSpec<Int> =
        if (reduced) snap() else tween(durationMillis = 900, easing = FastOutSlowInEasing)
    val kcalShown by animateIntAsState(
        targetValue = if (appeared) figure.kcal else 0,
        animationSpec = countSpec,
        label = "nutrition-day-kcal",
    )
    val goal = figure.kcalGoal
    if (goal == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NutritionKcalFigure(value = kcalShown, estimate = figure.estimate, style = KpknType.metricXL)
            if (!hasActivePlan) {
                Spacer(Modifier.height(10.dp))
                Text(text = "Aún no tienes objetivos.", style = KpknType.body, color = WizardColors.textMuted)
                KpknTextAction(text = "Definir objetivos", onClick = onDefineGoals, icon = KpknLineIcons.ChevronRight)
            }
        }
    } else {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val ringsAndGapPx = with(density) { (NutritionRingSize + NutritionRingGap).roundToPx() }
            val sideBySide = remember(constraints.maxWidth, density, measurer) {
                nutritionFigureFits(measurer, density, availablePx = constraints.maxWidth - ringsAndGapPx)
            }
            if (sideBySide) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NutritionRings(figure = figure, appeared = appeared, reduced = reduced)
                    Spacer(Modifier.width(NutritionRingGap))
                    Column(modifier = Modifier.weight(1f)) {
                        NutritionKcalFigure(value = kcalShown, estimate = figure.estimate, style = KpknType.metricL)
                        NutritionGoalLines(kcal = figure.kcal, goal = goal, estimate = figure.estimate)
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NutritionKcalFigure(value = kcalShown, estimate = figure.estimate, style = KpknType.metricXL)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NutritionRings(figure = figure, appeared = appeared, reduced = reduced)
                        Spacer(Modifier.width(NutritionRingGap))
                        Column(modifier = Modifier.weight(1f)) {
                            NutritionGoalLines(kcal = figure.kcal, goal = goal, estimate = figure.estimate)
                        }
                    }
                }
            }
        }
    }
}

/** Los anillos de macros del día, con la etiqueta de su recorrido. */
@Composable
private fun NutritionRings(figure: NutritionDayFigure, appeared: Boolean, reduced: Boolean) {
    Box(modifier = Modifier.size(NutritionRingSize).tourAnchor(TourAnchors.NUTRITION_ANILLOS)) {
        WizardMacroRings(
            kcal = figure.kcal,
            fractions = nutritionRingFractions(figure),
            caption = "kcal",
            description = nutritionRingsDescription(figure),
            appeared = appeared,
            reducedMotion = reduced,
            modifier = Modifier.fillMaxSize(),
            // Sin cifra central: la kcal grande va al lado, así no se repite ni pisa los arcos.
            showCenter = false,
        )
    }
}

/** La cifra de kcal del día con su unidad. Con [estimate] lleva «≈». */
@Composable
private fun NutritionKcalFigure(value: Int, estimate: Boolean, style: TextStyle) {
    KpknMetric(
        value = NutritionDisplayFormat.approxPrefix(estimate) + formatKcalEs(value),
        unit = "kcal",
        style = style,
    )
}

/** Lo que se lee junto a la cifra con meta: «de 2.300 kcal» y lo que queda, en `danger` si se ha pasado. */
@Composable
private fun NutritionGoalLines(kcal: Int, goal: Int, estimate: Boolean) {
    Text(text = "de ${formatKcalEs(goal)} kcal", style = KpknType.caption, color = WizardColors.textMuted)
    Text(
        text = kcalRemainingText(eaten = kcal, goal = goal, approx = NutritionDisplayFormat.approxPrefix(estimate)),
        style = KpknType.caption,
        color = if (kcal > goal) WizardColors.danger else WizardColors.textMuted,
    )
}

/**
 * La leyenda de P, C y G en tres columnas: punto y nombre y, debajo, los gramos con su meta en `textFaint` en la línea siguiente. Con la
 * letra grande, cada macro es una fila: nombre a la izquierda y cifra con su meta a la derecha.
 */
@Composable
private fun NutritionMacroLegend(figure: NutritionDayFigure, largeText: Boolean) {
    val approx = NutritionDisplayFormat.approxPrefix(figure.estimate)
    val macros = listOf(
        Triple("Proteína", figure.protein, MacroColors.protein),
        Triple("Hidratos", figure.carbs, MacroColors.carbs),
        Triple("Grasas", figure.fat, MacroColors.fat),
    )
    if (largeText) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            macros.forEach { (name, macro, color) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MacroName(name = name, color = color)
                    MacroValue(macro = macro, approx = approx, stacked = true)
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            macros.forEach { (name, macro, color) ->
                Column(modifier = Modifier.weight(1f)) {
                    MacroName(name = name, color = color)
                    Spacer(Modifier.height(2.dp))
                    MacroValue(macro = macro, approx = approx, stacked = false)
                }
            }
        }
    }
}

/** El punto de color de un macro y su nombre. */
@Composable
private fun MacroName(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La cifra de un macro. En la leyenda de tres columnas, los gramos van en una línea y la meta («/ 160 g») debajo, en `textFaint`; con la
 * letra grande, gramos y meta van en la misma línea, en fila.
 */
@Composable
private fun MacroValue(macro: NutritionMacroFigure, approx: String, stacked: Boolean) {
    if (stacked) {
        Text(
            text = macroFigureText(macro = macro, approx = approx),
            style = KpknType.bodyStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Column {
            Text(
                text = "$approx${formatKcalEs(macro.grams)} g",
                style = NutritionMacroValue,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            macro.goal?.let { goal ->
                Text(
                    text = "/ ${formatKcalEs(goal)} g",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Gramos y meta de un macro en una sola línea: el espacio es sin corte, así la unidad nunca queda sola. */
private fun macroFigureText(macro: NutritionMacroFigure, approx: String): AnnotatedString = buildAnnotatedString {
    withStyle(
        SpanStyle(
            color = WizardColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = "'tnum' 1",
        ),
    ) {
        append("$approx${formatKcalEs(macro.grams)} g")
    }
    macro.goal?.let { goal ->
        withStyle(SpanStyle(color = WizardColors.textFaint, fontSize = 13.sp, fontWeight = FontWeight.Normal)) {
            append(" / ${formatKcalEs(goal)} g")
        }
    }
}

/** Anillos del wizard: kcal frente a la meta (1 = círculo entero = 1,25 × la meta) y cada macro frente a su meta. */
private fun nutritionRingFractions(figure: NutritionDayFigure): MacroRingFractions {
    val goal = figure.kcalGoal
    return MacroRingFractions(
        calories = if (goal == null) 0f else ((figure.kcal.toFloat() / goal) / WIZARD_RING_CALORIE_SCALE).coerceIn(0f, 1f),
        protein = macroShare(figure.protein),
        carbs = macroShare(figure.carbs),
        fat = macroShare(figure.fat),
        // Con meta, la marca del 100 % va en su sitio; sin meta no hay escala ni marca.
        referenceMark = if (goal == null) null else 1f / WIZARD_RING_CALORIE_SCALE,
    )
}

private fun macroShare(macro: NutritionMacroFigure): Float {
    val goal = macro.goal ?: return 0f
    return (macro.grams.toFloat() / goal).coerceIn(0f, 1f)
}

/** Lo que se lee de los anillos: kcal frente a la meta y cada macro. */
private fun nutritionRingsDescription(figure: NutritionDayFigure): String {
    val kcal = figure.kcalGoal?.let { "${formatKcalEs(figure.kcal)} kilocalorías de ${formatKcalEs(it)}" }
        ?: "${formatKcalEs(figure.kcal)} kilocalorías"
    return "Nutrición del día: $kcal. ${macroPhrase("Proteína", figure.protein)}. " +
        "${macroPhrase("Hidratos", figure.carbs)}. ${macroPhrase("Grasas", figure.fat)}."
}

private fun macroPhrase(name: String, macro: NutritionMacroFigure): String =
    macro.goal?.let { "$name ${macro.grams} de $it gramos" } ?: "$name ${macro.grams} gramos, sin meta"

/** Lo que queda de la meta: «te quedan 1.045», «meta cubierta» o «te pasaste 120». */
private fun kcalRemainingText(eaten: Int, goal: Int, approx: String): String = when {
    eaten > goal -> "te pasaste $approx${formatKcalEs(eaten - goal)}"
    eaten == goal -> "meta cubierta"
    else -> "te quedan $approx${formatKcalEs(goal - eaten)}"
}

/**
 * ¿Cabe la cifra más ancha que se prevé («12.345 kcal») junto a los anillos? Se mide con el mismo tipo y la misma escala de letra que
 * se pintan; [availablePx] es el ancho que queda para la columna de la cifra.
 */
private fun nutritionFigureFits(measurer: TextMeasurer, density: Density, availablePx: Int): Boolean {
    val value = measurer.measure(text = formatKcalEs(12345), style = KpknType.metricL, maxLines = 1).size.width
    val unit = measurer.measure(text = "kcal", style = KpknType.unit, maxLines = 1).size.width
    val gapPx = with(density) { 3.dp.roundToPx() }
    return value + gapPx + unit <= availablePx
}

/** Descripción de accesibilidad de un día: «jueves 2, hoy, 1.320 de 2.300 kilocalorías, 57 %» o «miércoles 1, sin registros». */
private fun nutritionDayDescription(day: NutritionWeekDay, date: LocalDate?): String {
    if (date == null) return day.date
    val weekday = dayNamesEs[date.dayOfWeek.value - 1]
    val goal = day.kcalGoal
    val intake = when {
        day.kcal <= 0 -> "sin registros"
        goal == null -> "${formatKcalEs(day.kcal)} kilocalorías, sin meta"
        else -> {
            val percent = kotlin.math.round(day.kcal * 100.0 / goal).toInt()
            "${formatKcalEs(day.kcal)} de ${formatKcalEs(goal)} kilocalorías, $percent %"
        }
    }
    return listOfNotNull("$weekday ${date.dayOfMonth}", "hoy".takeIf { day.isToday }, intake).joinToString(", ")
}

/** La letra de un día en español: L, M, X, J, V, S, D. */
private fun dayLetter(date: LocalDate): String = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

/** Cifras de «Tu día» que ya han contado en el proceso: solo la primera vez que se ven (el mismo criterio que Home). */
internal object NutritionFigureMemory {
    private val seen = HashSet<String>()

    /** true solo la primera vez que se pide [key] en el proceso. */
    @Synchronized
    fun firstTime(key: String): Boolean = seen.add(key)
}
