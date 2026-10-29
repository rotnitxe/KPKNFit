package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.nutrition.NutritionWeekDay
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
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
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.time.DayOfWeek
import java.time.LocalDate

/** Diámetro del anillo fino de «Tu día»: 104 dp (`npRing(2240, 2300, 104)`). */
private val NutritionRingSize = 104.dp

/** Aire entre el anillo y las filas de macros (`.np-day{gap:18px}`). */
private val NutritionRingGap = 18.dp

/** Etiqueta de prueba del contenedor del anillo de «Tu día» (solo se pinta con meta de kcal). */
internal const val NUTRITION_DAY_RING_TAG = "nutrition-day-ring"

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
    /** Kcal de entreno del día (0 = sin entreno: no hay línea ni se cuenta en la descripción). */
    val trainingBurnKcal: Int = 0,
)

/**
 * «Tu día» (NUTRITION_V1 §2.2; Arreglos v1): la tira de siete días con su mini anillo de kcal, y debajo, con un filete, el anillo fino
 * monocromo del día (cifra dentro y «de 2.300» debajo) con proteína, carbos y grasa como filas con barra fina en tinta. Tocar un día llama a [onSelectDate] y todo lo de
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
    /** Gasto de entreno del día elegido: con más de 0 sale su línea («Entreno · 320 kcal»); sin entreno, no hay línea. */
    trainingBurnKcal: Int = 0,
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
        trainingBurnKcal = trainingBurnKcal.coerceAtLeast(0),
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
                    largeText = largeText,
                    onDefineGoals = onDefineGoals,
                )
            }
        }
    }
}

/** Ancho del subrayado que marca el día elegido, como en «Tu semana» de Home. */
private val NutritionUnderlineWidth: Dp = 18.dp

/**
 * La tira de los siete días con el patrón de «Tu semana» de Home: un subrayado de músculo que se desliza bajo la columna elegida (420 ms;
 * con movimiento reducido, salto). Sin días no pinta nada.
 */
@Composable
private fun NutritionWeekStrip(
    days: List<NutritionWeekDay>,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    if (days.isEmpty()) return
    val reduced = wizardReducedMotion()
    val selectedIndex = days.indexOfFirst { it.date == selectedDate }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidth = maxWidth / days.size
        val spec: AnimationSpec<Dp> = if (reduced) snap() else tween(durationMillis = 420, easing = FastOutSlowInEasing)
        val underlineX by animateDpAsState(
            targetValue = columnWidth * selectedIndex.coerceAtLeast(0) + (columnWidth - NutritionUnderlineWidth) / 2,
            animationSpec = spec,
            label = "nutritionWeekUnderline",
        )
        Column(modifier = Modifier.fillMaxWidth()) {
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
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (selectedIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(underlineX.roundToPx(), 0) }
                            .size(width = NutritionUnderlineWidth, height = 2.dp)
                            .background(SymbolPalette.musculo, RoundedCornerShape(1.dp)),
                    )
                }
            }
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
                style = KpknType.eyebrow,
                color = if (day.isToday) WizardColors.text else WizardColors.textFaint,
            )
            Spacer(Modifier.height(6.dp))
            // El número va en un círculo: relleno de tinta si es hoy, con borde de 1,5 dp si es el día elegido (como en Home).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .then(
                        when {
                            day.isToday -> Modifier.clip(CircleShape).background(WizardColors.text)
                            selected -> Modifier.border(1.5.dp, WizardColors.text, CircleShape)
                            else -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date?.dayOfMonth?.toString().orEmpty(),
                    style = NutritionDayNumber,
                    color = when {
                        day.isToday -> WizardColors.background
                        selected -> WizardColors.text
                        else -> WizardColors.textMuted
                    },
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            NutritionMiniRing(day = day)
        }
    }
}

/**
 * Lo que va debajo de la tira para el día elegido (lienzo `v-day`, `.np-day`): con meta de kcal, el anillo fino monocromo de 104 dp con
 * la cifra a su medida y «de 2.300» debajo, y a su derecha proteína, carbos y grasa como filas con barra fina en tinta (sin color por
 * macro); con la letra grande, el anillo arriba y las filas a todo el ancho. Sin meta: la cifra a todo el ancho, las filas sin barra y,
 * sin plan, «Definir objetivos». El gasto del entreno, si lo hay, va como una línea más debajo. Las cifras cuentan la primera vez.
 */
@Composable
private fun NutritionDayFiguresBlock(
    figure: NutritionDayFigure,
    hasActivePlan: Boolean,
    largeText: Boolean,
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
    val approx = NutritionDisplayFormat.approxPrefix(figure.estimate)
    val macros = listOf(
        NutritionMacroLine("Proteína", figure.protein.grams, figure.protein.goal, approx),
        NutritionMacroLine("Carbos", figure.carbs.grams, figure.carbs.goal, approx),
        NutritionMacroLine("Grasa", figure.fat.grams, figure.fat.goal, approx),
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
            NutritionMacroLines(lines = macros, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
            if (figure.trainingBurnKcal > 0) {
                Spacer(Modifier.height(12.dp))
                NutritionTrainingLine(burnKcal = figure.trainingBurnKcal)
            }
        }
    } else {
        val ring: @Composable () -> Unit = {
            // El contenedor lleva la etiqueta de prueba y el ancla del recorrido; el anillo pone su propia descripción.
            Box(Modifier.tourAnchor(TourAnchors.NUTRITION_ANILLOS).testTag(NUTRITION_DAY_RING_TAG)) {
                NutritionThinRing(
                    kcal = figure.kcal,
                    goal = goal,
                    size = NutritionRingSize,
                    appeared = appeared,
                    reducedMotion = reduced,
                    approx = approx,
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (largeText) {
                // A escala de letra grande las filas no caben junto al anillo: el anillo arriba y las filas a todo el ancho.
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ring()
                    NutritionMacroLines(lines = macros, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NutritionRingGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ring()
                    NutritionMacroLines(lines = macros, modifier = Modifier.weight(1f))
                }
            }
            if (figure.trainingBurnKcal > 0) {
                Spacer(Modifier.height(12.dp))
                NutritionTrainingLine(burnKcal = figure.trainingBurnKcal)
            }
        }
    }
}

/** La cifra de kcal del día sin meta, con su unidad. Con [estimate] lleva «≈». */
@Composable
private fun NutritionKcalFigure(value: Int, estimate: Boolean, style: TextStyle) {
    KpknMetric(
        value = NutritionDisplayFormat.approxPrefix(estimate) + formatKcalEs(value),
        unit = "kcal",
        style = style,
    )
}

/** «Entreno · 320 kcal» con un punto de músculo: el gasto del entreno del día, solo si lo hay. */
@Composable
private fun NutritionTrainingLine(burnKcal: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(SymbolPalette.musculo, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text = "Entreno · ${formatKcalEs(burnKcal)} kcal", style = KpknType.caption, color = WizardColors.textMuted)
    }
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
internal fun dayLetter(date: LocalDate): String = when (date.dayOfWeek) {
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
