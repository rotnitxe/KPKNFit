package com.example.kpkn.screens.stories

import android.content.Context
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatSignedDecimalEs
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

// ----------------------------------------------------------------------------------------------------------- modelo

/** Plantillas de la historia de Cuerpo (escenas h-body-change y h-body-measures). */
internal enum class BodyStoryTemplate(val label: String) {
    CAMBIO("Cambio"),
    MEDIDAS("Medidas"),
}

/**
 * «Incluir» de Cuerpo: el peso actual (apagado: por defecto nunca sale el peso absoluto), el cambio en kilos, las medidas y el % de
 * grasa.
 */
internal data class BodyStoryOptions(
    val currentWeight: Boolean = false,
    val change: Boolean = true,
    val measures: Boolean = true,
    val fat: Boolean = true,
)

/** Una historia de Cuerpo ya en texto: cabecera, filas, la curva semanal (Cambio) y la cifra del fondo «Eco» (Medidas). */
internal data class BodyStoryPage(
    val eyebrow: String,
    val title: String,
    val sub: String?,
    val rows: List<StoryPair>,
    val curve: List<Double> = emptyList(),
    val ecoNumber: Int = 0,
)

/** Semanas que cubre la historia (las del lienzo). */
private const val BODY_WEEKS = 12

/** Pie de las historias de Cuerpo. */
internal const val BODY_FOOT = "Mi progreso"

/** Un perímetro: su nombre en la fila, su nombre tras «de» en la meta y cómo se lee de una medición. */
private class BodyMeasure(val label: String, val of: String, val read: (BodyHistoryRow) -> Double?)

private val bodyMeasures = listOf(
    BodyMeasure("Cintura", "de cintura") { it.waistCm },
    BodyMeasure("Cadera", "de cadera") { it.hipCm },
    BodyMeasure("Pecho", "de pecho") { it.chestCm },
    BodyMeasure("Brazo", "de brazo") { it.armCm },
    BodyMeasure("Muslo", "de muslo") { it.thighCm },
    BodyMeasure("Cuello", "de cuello") { it.neckCm },
)

/** Las mediciones de las últimas [BODY_WEEKS] semanas (hasta la más reciente), de la más antigua a la más reciente. */
private fun window(rows: List<BodyHistoryRow>): List<BodyHistoryRow> {
    val last = rows.maxOfOrNull { it.dateTime.toLocalDate() } ?: return emptyList()
    val start = last.minusWeeks(BODY_WEEKS.toLong())
    return rows.filter { !it.dateTime.toLocalDate().isBefore(start) }.sortedBy { it.timestampEpochMs }
}

/** Media semanal del peso en la ventana (semanas sin pesaje se saltan), de la más antigua a la más reciente, en kg. */
internal fun bodyWeeklyWeights(rows: List<BodyHistoryRow>): List<Double> {
    val inWindow = window(rows).filter { it.weightKg != null }
    val last = inWindow.maxOfOrNull { it.dateTime.toLocalDate() } ?: return emptyList()
    return inWindow
        .groupBy { (ChronoUnit.DAYS.between(it.dateTime.toLocalDate(), last) / 7).toInt() }
        .toSortedMap(compareByDescending { it })
        .values
        .map { week -> week.mapNotNull { it.weightKg }.average() }
}

/** Semanas entre el primer y el último dato de la ventana (al menos 1). */
private fun spanWeeks(rows: List<BodyHistoryRow>): Int {
    val dates = rows.map { it.dateTime.toLocalDate() }
    val first = dates.minOrNull() ?: return 1
    val last = dates.maxOrNull() ?: return 1
    return (ChronoUnit.DAYS.between(first, last) / 7.0).roundToInt().coerceIn(1, BODY_WEEKS)
}

/** «12 semanas», «1 semana». */
private fun weeksText(weeks: Int): String = if (weeks == 1) "1 semana" else "$weeks semanas"

/** Primer y último valor de [read] en la ventana, si hay dos mediciones distintas. */
private fun firstLast(rows: List<BodyHistoryRow>, read: (BodyHistoryRow) -> Double?): Pair<Double, Double>? {
    val values = rows.filter { read(it) != null }
    if (values.size < 2) return null
    return read(values.first())!! to read(values.last())!!
}

/** Cambio con signo y un decimal: «−6,1 cm», «+0,6 kg». */
private fun signed(value: Double, unit: String): String = "${formatSignedDecimalEs(value, 1)} $unit"

/** Plantillas con datos: Cambio pide dos semanas con peso; Medidas, un perímetro medido dos veces. */
internal fun bodyStoryTemplates(rows: List<BodyHistoryRow>): List<BodyStoryTemplate> {
    val inWindow = window(rows)
    return buildList {
        if (bodyWeeklyWeights(rows).size >= 2) add(BodyStoryTemplate.CAMBIO)
        if (bodyMeasures.any { firstLast(inWindow, it.read) != null }) add(BodyStoryTemplate.MEDIDAS)
    }
}

/** La página de [template] con las mediciones [rows] (cualquier orden) en la unidad [unit] y las [options] de «Incluir». */
internal fun bodyStoryPage(
    template: BodyStoryTemplate,
    rows: List<BodyHistoryRow>,
    unit: WizardMassUnit,
    options: BodyStoryOptions = BodyStoryOptions(),
): BodyStoryPage {
    val inWindow = window(rows)
    val weeks = spanWeeks(inWindow)
    val measured = bodyMeasures.mapNotNull { measure -> firstLast(inWindow, measure.read)?.let { measure to (it.second - it.first) } }
    return when (template) {
        BodyStoryTemplate.CAMBIO -> {
            val weekly = bodyWeeklyWeights(rows)
            val change = if (weekly.size >= 2) weekly.last() - weekly.first() else 0.0
            val shown = WizardWeightScale.toDisplay(change, unit)
            val rate = shown / weeks
            val steady = weekly.zipWithNext().count { (a, b) -> (b - a) * change > 0 } >= (weekly.size - 1) * 3 / 4
            val currentKg = inWindow.lastOrNull { it.weightKg != null }?.weightKg
            val fat = firstLast(inWindow) { it.bodyFatPercent }
            val lean = firstLast(inWindow) { row ->
                val w = row.weightKg
                val bf = row.bodyFatPercent
                if (w != null && bf != null) w * (1 - bf / 100.0) else null
            }
            val title = when {
                options.change -> signed(shown, unit.code)
                weekly.size >= 2 -> "${formatSignedDecimalEs(change / weekly.first() * 100.0, 1)} %"
                else -> weeksText(weeks)
            }
            BodyStoryPage(
                eyebrow = weeksText(weeks),
                title = title,
                sub = if (options.change) {
                    val pace = "${formatDecimalEs(abs(rate), 2)} ${unit.code} por semana"
                    if (steady) "De forma constante: $pace" else "Ritmo: $pace"
                } else {
                    null
                },
                rows = buildList {
                    if (options.currentWeight && currentKg != null) {
                        add(StoryPair("Peso actual", "${formatDecimalEs(WizardWeightScale.toDisplay(currentKg, unit), 1)} ${unit.code}"))
                    }
                    if (options.measures) measured.firstOrNull { it.first.label == "Cintura" }?.let { add(StoryPair("Cintura", signed(it.second, "cm"))) }
                    if (options.fat && fat != null) add(StoryPair("% grasa", "${formatSignedDecimalEs(fat.second - fat.first, 1)} puntos"))
                    if (options.fat && options.change && lean != null) {
                        add(StoryPair("Masa magra", signed(WizardWeightScale.toDisplay(lean.second - lean.first, unit), unit.code)))
                    }
                },
                curve = weekly,
            )
        }
        BodyStoryTemplate.MEDIDAS -> {
            val lead = measured.firstOrNull { it.first.label == "Cintura" } ?: measured.maxByOrNull { abs(it.second) }
            BodyStoryPage(
                eyebrow = "Medidas · ${weeksText(weeks)}",
                title = lead?.let { signed(it.second, "cm") } ?: weeksText(weeks),
                sub = lead?.first?.of,
                rows = buildList {
                    if (options.measures) measured.take(4).forEach { (measure, delta) -> add(StoryPair(measure.label, signed(delta, "cm"))) }
                    if (options.currentWeight) {
                        inWindow.lastOrNull { it.weightKg != null }?.weightKg?.let {
                            add(StoryPair("Peso actual", "${formatDecimalEs(WizardWeightScale.toDisplay(it, unit), 1)} ${unit.code}"))
                        }
                    }
                },
                // El fondo «Eco» repite la parte entera del cambio principal (6 en «−6,1 cm»).
                ecoNumber = lead?.let { abs(it.second).toInt() } ?: 0,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------------------- dibujo

/** Pinta la historia de Cuerpo [page] con [template] en el lienzo de 1080 × 1920. */
internal fun DrawScope.drawBodyStory(template: BodyStoryTemplate, page: BodyStoryPage, measurer: TextMeasurer, progress: Float = 1f) {
    when (template) {
        BodyStoryTemplate.CAMBIO -> {
            drawStoryPlain()
            // `.st-chart` en top 8 %, de 4 % a 96 %: la curva semanal con su escala (un poco de aire abajo y arriba).
            val low = page.curve.minOrNull() ?: 0.0
            val high = page.curve.maxOrNull() ?: 1.0
            val span = (high - low).takeIf { it > 0.0 } ?: 1.0
            drawStoryWeightCurve(page.curve, low - 0.15 * span, high + 0.1 * span, 0.04f * StoryCanvas.WIDTH, topAt(8f), 0.92f * StoryCanvas.WIDTH, progress)
            drawShell(
                topAt(52f),
                end = false,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellRows(page.rows, 0f, auto = true)?.let(::add)
                    add(measurer.shellFoot(BODY_FOOT, end = false))
                },
            )
        }
        BodyStoryTemplate.MEDIDAS -> {
            drawStoryBackdrop(
                LineCoverSpec(LineCoverStyle.ECO, LineCoverBackground.CARBON, seed = 3, weeks = page.ecoNumber),
                measurer,
                progress,
                artHeight(45f),
            )
            drawShell(
                topAt(44f),
                end = true,
                blocks = buildList {
                    addAll(measurer.shellHeader(page.eyebrow, page.title, page.sub))
                    measurer.shellRows(page.rows, SHELL_ROWS_GAP_END, auto = false)?.let(::add)
                    add(measurer.shellFoot(BODY_FOOT, end = true))
                },
            )
        }
    }
}

/** Pinta la historia de Cuerpo en un bitmap de 1080 × 1920, para el PNG. */
internal fun renderBodyStory(context: Context, template: BodyStoryTemplate, page: BodyStoryPage): android.graphics.Bitmap =
    renderStory(context) { drawBodyStory(template, page, it) }
