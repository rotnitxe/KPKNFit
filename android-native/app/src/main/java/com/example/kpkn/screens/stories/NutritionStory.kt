package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.domain.nutrition.MacroGoals
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.weekdayInitialEs
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------------------------------------- modelo

/** Un día de la semana de Nutrición: sus kcal registradas (null = sin registros) y su meta (null = sin meta). Nunca qué comiste. */
data class NutritionStoryDay(val date: LocalDate, val kcal: Int?, val goal: Int?)

/** Media diaria de macros (g) de los [days] días registrados y sus objetivos (null = sin objetivo). */
data class NutritionStoryMacros(
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val proteinTargetG: Double? = null,
    val carbsTargetG: Double? = null,
    val fatTargetG: Double? = null,
    val days: Int,
)

/** Racha del hábito «Comidas»: días seguidos registrando ([current]) y la mejor ([best]). */
data class NutritionStoryStreak(val current: Int, val best: Int)

/**
 * Media diaria de macros de los días de [dates] con algo registrado (lo planificado no cuenta) y el objetivo medio de esos
 * mismos días cuando todos lo tienen. Solo cifras: nunca qué comiste. Null si no hay ningún día registrado.
 */
fun nutritionStoryMacrosOf(
    logs: List<NutritionLog>,
    dates: List<LocalDate>,
    goalsByDate: Map<LocalDate, MacroGoals?>,
): NutritionStoryMacros? {
    val keys = dates.map { it.toString() }.toSet()
    val byDay = logs.filter { it.status != NutritionStatus.PLANNED && it.date.take(10) in keys }.groupBy { it.date.take(10) }
    if (byDay.isEmpty()) return null
    val days = byDay.size
    fun average(of: (LoggedFood) -> Double): Double = byDay.values.sumOf { day -> day.sumOf { log -> log.foods.sumOf(of) } } / days
    val goals = byDay.keys.map { goalsByDate[LocalDate.parse(it)] }
    fun target(of: (MacroGoals) -> Int?): Double? =
        goals.map { it?.let(of) }.takeIf { all -> all.all { value -> value != null } }?.filterNotNull()?.average()
    return NutritionStoryMacros(
        proteinG = average { it.protein },
        carbsG = average { it.carbs },
        fatG = average { it.fats },
        proteinTargetG = target { it.proteinGoal },
        carbsTargetG = target { it.carbGoal },
        fatTargetG = target { it.fatGoal },
        days = days,
    )
}

/** Plantillas de Nutrición (escenas h-nutri-week, h-nutri-macros y h-nutri-streak). */
internal enum class NutritionStoryTemplate(val label: String) {
    SEMANA("Semana"),
    MACROS("Macros"),
    RACHA("Racha"),
}

/** «Incluir» de Nutrición: las kcal (media y objetivo en cifras) y el objetivo (días en objetivo y barras frente a objetivo). */
internal data class NutritionStoryOptions(val calories: Boolean = true, val goal: Boolean = true)

/** Una barra de `.st-macros`: nombre, valor, fracción llena (0..1) y color. */
internal data class NutritionMacroBar(val label: String, val value: String, val fill: Float, val color: Color)

/** Una columna de `.st-days`: la letra del día, el % de la barra (0 = sin barra) y si se pasó del objetivo (acento). */
internal data class NutritionDayBar(val letter: String, val percent: Int, val over: Boolean)

/** Una historia de Nutrición ya en texto. */
internal data class NutritionStoryPage(
    val eyebrow: String,
    val title: String,
    val sub: String?,
    val days: List<NutritionDayBar> = emptyList(),
    val ringCenter: String? = null,
    val ringFill: Float = 1f,
    val ringParts: List<Pair<Float, Color>> = emptyList(),
    val macros: List<NutritionMacroBar> = emptyList(),
    val badge: String? = null,
    val streakWeeks: Int = 1,
)

/** Pie de las historias de Nutrición. */
internal const val NUTRITION_FOOT = "Mi nutrición"

/** Banda de «en objetivo»: del 85 % al 110 % de la meta (por encima de 110 % la barra va en acento, como en el lienzo). */
private const val GOAL_LOW = 85
private const val GOAL_HIGH = 110

/** Techo del % de una barra de día, para que un día muy alto no se salga de la historia. */
private const val DAY_PERCENT_CAP = 140

/** % de [kcal] sobre [goal], redondeado. */
private fun percentOf(kcal: Int, goal: Int): Int = (kcal * 100.0 / goal).roundToInt()

/** «1 día», «23 días». */
internal fun storyDays(n: Int): String = if (n == 1) "1 día" else "$n días"

/** Plantillas con datos: Semana con algún día registrado, Macros con media de algún día y Racha con al menos un día. */
internal fun nutritionStoryTemplates(
    days: List<NutritionStoryDay>,
    macros: NutritionStoryMacros?,
    streak: NutritionStoryStreak?,
): List<NutritionStoryTemplate> = buildList {
    if (days.any { it.kcal != null }) add(NutritionStoryTemplate.SEMANA)
    if (macros != null && macros.days > 0) add(NutritionStoryTemplate.MACROS)
    if (streak != null && streak.current >= 1) add(NutritionStoryTemplate.RACHA)
}

/** La meta de referencia: la del día registrado más reciente que la tenga. */
private fun weekGoal(days: List<NutritionStoryDay>): Int? = days.sortedBy { it.date }.lastOrNull { it.goal != null && it.goal > 0 }?.goal

/** La página de [template]. Solo usa kcal, macros y la racha: nunca alimentos ni el peso. */
internal fun nutritionStoryPage(
    template: NutritionStoryTemplate,
    days: List<NutritionStoryDay>,
    macros: NutritionStoryMacros?,
    streak: NutritionStoryStreak?,
    options: NutritionStoryOptions = NutritionStoryOptions(),
): NutritionStoryPage {
    val week = days.sortedBy { it.date }
    val registered = week.mapNotNull { it.kcal }
    val average = if (registered.isEmpty()) null else registered.average().roundToInt()
    val goal = weekGoal(week)
    return when (template) {
        NutritionStoryTemplate.SEMANA -> {
            val withGoal = options.goal && goal != null
            val inTarget = week.count { day ->
                val g = day.goal ?: goal
                day.kcal != null && g != null && g > 0 && percentOf(day.kcal, g) in GOAL_LOW..GOAL_HIGH
            }
            // Sin objetivo, las barras se miden contra el día más alto (que llega a 100 %).
            val peak = registered.maxOrNull()?.coerceAtLeast(1) ?: 1
            NutritionStoryPage(
                eyebrow = "Mi semana · nutrición",
                title = "${if (withGoal) inTarget else registered.size} de ${storyDays(week.size)}",
                sub = when {
                    withGoal && options.calories -> "En mi objetivo de ${formatKcalEs(goal!!)} kcal"
                    withGoal -> "En mi objetivo"
                    else -> "Registrando mis comidas"
                },
                days = week.map { day ->
                    val g = (day.goal ?: goal).takeIf { withGoal && it != null && it > 0 }
                    val percent = when {
                        day.kcal == null -> 0
                        g != null -> percentOf(day.kcal, g)
                        else -> (day.kcal * 100.0 / peak).roundToInt()
                    }.coerceIn(0, DAY_PERCENT_CAP)
                    NutritionDayBar(weekdayInitialEs(day.date.dayOfWeek), percent, over = g != null && percent > GOAL_HIGH)
                },
                ringCenter = average?.takeIf { options.calories }?.let(::formatKcalEs),
                ringFill = if (average != null && goal != null && goal > 0) (average.toFloat() / goal).coerceIn(0f, 1f) else 1f,
                ringParts = macroShares(macros),
            )
        }
        NutritionStoryTemplate.MACROS -> {
            val m = macros ?: NutritionStoryMacros(0.0, 0.0, 0.0, days = 0)
            val kcal = average ?: (m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9).roundToInt()
            val met = goal != null && goal > 0 && percentOf(kcal, goal) in GOAL_LOW..GOAL_HIGH
            val bars = listOf(
                Triple("Proteína", m.proteinG to m.proteinTargetG, StoryProtein),
                Triple("Carbos", m.carbsG to m.carbsTargetG, StoryCarbs),
                Triple("Grasa", m.fatG to m.fatTargetG, StoryFat),
            )
            val biggest = max(1.0, maxOf(m.proteinG, m.carbsG, m.fatG))
            NutritionStoryPage(
                eyebrow = "Media diaria · ${storyDays(m.days)}",
                title = "${m.proteinG.roundToInt()} g de proteína",
                sub = when {
                    options.calories && options.goal && met -> "${formatKcalEs(kcal)} kcal · objetivo cumplido"
                    options.calories -> "${formatKcalEs(kcal)} kcal de media"
                    options.goal && met -> "Objetivo cumplido"
                    else -> null
                },
                macros = bars.map { (label, values, color) ->
                    val (grams, target) = values
                    val reference = target?.takeIf { options.goal && it > 0 } ?: biggest
                    NutritionMacroBar(label, "${grams.roundToInt()} g", (grams / reference).toFloat().coerceIn(0f, 1f), color)
                },
            )
        }
        NutritionStoryTemplate.RACHA -> {
            val s = streak ?: NutritionStoryStreak(0, 0)
            NutritionStoryPage(
                eyebrow = "Racha",
                title = storyDays(s.current),
                sub = "Registrando todas mis comidas",
                badge = if (s.current >= 2 && s.current >= s.best) "Mi mejor racha" else null,
                streakWeeks = ((s.current + 6) / 7).coerceAtLeast(1),
            )
        }
    }
}

/** Reparto del anillo por kcal de cada macro (4/4/9); sin macros, un solo arco de tinta. */
private fun macroShares(macros: NutritionStoryMacros?): List<Pair<Float, Color>> {
    if (macros == null) return listOf(1f to StoryCanvas.Ink)
    val kcal = listOf(macros.proteinG * 4, macros.carbsG * 4, macros.fatG * 9)
    val total = kcal.sum()
    if (total <= 0.0) return listOf(1f to StoryCanvas.Ink)
    return kcal.zip(listOf(StoryProtein, StoryCarbs, StoryFat)).map { (k, c) -> (k / total).toFloat() to c }
}

// ------------------------------------------------------------------------------------------------------------- dibujo

/** `.st-days`: siete columnas (hueco 2 cqw) con la barra (tinta o acento) sobre la letra; las barras crecen desde abajo. */
private fun TextMeasurer.nutritionDays(days: List<NutritionDayBar>, progress: Float): ShellBlock? {
    if (days.isEmpty()) return null
    val gap = 2f * CQW
    val column = (StoryCanvas.COLUMN_WIDTH - gap * (days.size - 1)) / days.size
    val letters = days.map { storyText(it.letter, ShellType.dayLetter) }
    // La altura de la barra va en cqw enteros, como `Math.round(pct * .22)` del lienzo.
    val heights = days.map { (it.percent * 0.22f).roundToInt() * CQW }
    val letterHeight = letters.maxOf { it.size.height }.toFloat()
    val labelGap = 1.4f * CQW
    val height = heights.max() + labelGap + letterHeight
    return ShellBlock(height, auto = true) { top ->
        val base = top + heights.max()
        days.forEachIndexed { i, day ->
            val left = StoryCanvas.LEFT + i * (column + gap)
            val h = heights[i] * progress.coerceIn(0f, 1f)
            if (h > 0f) {
                drawRoundRect(
                    color = if (day.over) StoryCanvas.Accent else StoryCanvas.Ink,
                    topLeft = Offset(left, base - h),
                    size = Size(column, h),
                    cornerRadius = CornerRadius(1.4f * CQW),
                )
            }
            val letter = letters[i]
            drawText(letter, StoryCanvas.Faint, Offset(left + (column - letter.size.width) / 2f, base + labelGap))
        }
    }
}

/** `.st-macros`: rejilla 22 cqw | barra | 22 cqw, hueco 3 cqw, filas a 2,6 cqw; barra de 1,4 cqw con el objetivo de fondo. */
private fun TextMeasurer.nutritionMacros(bars: List<NutritionMacroBar>, progress: Float): ShellBlock? {
    if (bars.isEmpty()) return null
    val side = 22f * CQW
    val gap = 3f * CQW
    val rowGap = 2.6f * CQW
    val barHeight = 1.4f * CQW
    val measured = bars.map { storyText(it.label, ShellType.macroName, width = side.toInt(), maxLines = 1, ellipsis = true) to storyText(it.value, ShellType.macroValue, maxLines = 1) }
    val rowHeights = measured.map { (n, v) -> max(n.size.height, v.size.height).toFloat() }
    val height = rowHeights.sum() + rowGap * (bars.size - 1)
    return ShellBlock(height, auto = true) { top ->
        var y = top
        bars.forEachIndexed { i, bar ->
            val (name, value) = measured[i]
            val rowHeight = rowHeights[i]
            drawText(name, StoryCanvas.Muted, Offset(StoryCanvas.LEFT, y + (rowHeight - name.size.height) / 2f))
            val barLeft = StoryCanvas.LEFT + side + gap
            val barWidth = StoryCanvas.COLUMN_WIDTH - 2 * side - 2 * gap
            val barTop = y + (rowHeight - barHeight) / 2f
            drawRoundRect(Color.White, Offset(barLeft, barTop), Size(barWidth, barHeight), CornerRadius(1f * CQW), alpha = 0x14 / 255f)
            val filled = barWidth * bar.fill * progress.coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(bar.color, Offset(barLeft, barTop), Size(filled, barHeight), CornerRadius(1f * CQW))
            drawText(value, StoryCanvas.Ink, Offset(StoryCanvas.RIGHT - value.size.width, y + (rowHeight - value.size.height) / 2f))
            y += rowHeight + rowGap
        }
    }
}

/** El anillo de la Semana (`.st-ring` en top 6 %, de 24 % a 76 %) con la media en el centro. */
private fun DrawScope.drawNutritionRing(page: NutritionStoryPage, measurer: TextMeasurer, progress: Float) {
    val left = 0.24f * StoryCanvas.WIDTH
    val width = 0.52f * StoryCanvas.WIDTH
    val center = drawStoryRing(left, topAt(6f), width, page.ringFill * progress.coerceIn(0f, 1f), page.ringParts)
    val value = page.ringCenter ?: return
    val number = measurer.storyText(value, ShellType.ringValue)
    val unit = measurer.storyText("kcal de media", ShellType.ringUnit)
    val gap = 8f
    val total = number.size.height + gap + unit.size.height
    val top = center.y - total / 2f
    drawText(number, StoryCanvas.Ink, Offset(center.x - number.size.width / 2f, top))
    drawText(unit, StoryCanvas.Faint, Offset(center.x - unit.size.width / 2f, top + number.size.height + gap))
}

/** Pinta la historia de Nutrición [page] con [template] en el lienzo de 1080 × 1920. */
internal fun DrawScope.drawNutritionStory(template: NutritionStoryTemplate, page: NutritionStoryPage, measurer: TextMeasurer, progress: Float = 1f) {
    when (template) {
        NutritionStoryTemplate.SEMANA -> {
            drawStoryPlain()
            drawNutritionRing(page, measurer, progress)
            drawShell(
                topAt(40f),
                end = false,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.nutritionDays(page.days, progress)?.let(::add)
                    add(measurer.shellFoot(NUTRITION_FOOT, end = false))
                },
            )
        }
        NutritionStoryTemplate.MACROS -> {
            drawStoryBackdrop(LineCoverSpec(LineCoverStyle.BRUMA, LineCoverBackground.CARBON, seed = 2), measurer, progress, artHeight(48f))
            drawShell(
                topAt(42f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.nutritionMacros(page.macros, progress)?.let(::add)
                    add(measurer.shellFoot(NUTRITION_FOOT, end = true))
                },
            )
        }
        NutritionStoryTemplate.RACHA -> {
            drawStoryBackdrop(
                LineCoverSpec(LineCoverStyle.ESPIRAL, LineCoverBackground.CARBON, seed = 1, currentWeek = page.streakWeeks - 1, weeks = page.streakWeeks),
                measurer,
                progress,
                artHeight(42f),
            )
            drawShell(
                topAt(48f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    page.badge?.let { add(measurer.shellBadge(it, ShellIcon.FIRE)) }
                    add(measurer.shellFoot(NUTRITION_FOOT, end = true))
                },
            )
        }
    }
}

/** Pinta la historia de Nutrición en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderNutritionStory(context: Context, template: NutritionStoryTemplate, page: NutritionStoryPage): android.graphics.Bitmap =
    renderStory(context) { drawNutritionStory(template, page, it) }
