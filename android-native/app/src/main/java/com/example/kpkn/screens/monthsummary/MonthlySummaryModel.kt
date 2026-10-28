package com.example.kpkn.screens.monthsummary

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.progress.MonthlyBestMark
import com.example.kpkn.domain.training.progress.MonthlyReport
import com.example.kpkn.domain.training.progress.MonthlyReportEngine
import com.example.kpkn.screens.stories.trainingTimeText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToLong

/** Diapositivas del Resumen del mes, en el orden en que se enseñan. */
internal enum class MonthSlideKind { COVER, CONSISTENCY, WORK, STRENGTH, BEST_MARK, MUSCLES, EFFORT, SHARE }

/**
 * Una diapositiva del Resumen del mes: el [eyebrow] pequeño, el [headline] (titular), la cifra grande [bigValue] con su [bigUnit] y el
 * [body] de texto. Lo que va null no se pinta.
 */
internal data class MonthSlide(
    val kind: MonthSlideKind,
    val eyebrow: String,
    val headline: String? = null,
    val bigValue: String? = null,
    val bigUnit: String? = null,
    val body: String? = null,
)

/** Hasta este día del mes (incluido) se avisa en Inicio del resumen del mes anterior. */
private const val HOME_NOTICE_LAST_DAY = 10

/** Meses en español en minúsculas, para no depender de la configuración regional del dispositivo. */
private val spanishMonths = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/**
 * Diapositivas del Resumen del mes de [report]. [programName] null = todos los programas; [plannedRir] null = el plan no fija el RIR,
 * así que Esfuerzo solo cifra el RIR medio.
 */
internal fun monthlySummarySlides(report: MonthlyReport, programName: String?, plannedRir: Double? = null): List<MonthSlide> {
    val month = spanishMonths[report.month.monthValue - 1]
    val previousMonth = spanishMonths[report.month.minusMonths(1).monthValue - 1]
    return listOfNotNull(
        coverSlide(report, month, programName),
        consistencySlide(report),
        workSlide(report, previousMonth),
        strengthSlide(report),
        report.bestMark?.let(::bestMarkSlide),
        musclesSlide(report),
        report.rirMean?.let { effortSlide(it, plannedRir) },
        MonthSlide(kind = MonthSlideKind.SHARE, eyebrow = "Tu $month"),
    )
}

/** Clave del resumen de [month] del programa [programId], p. ej. «p1|2026-09». No puede llevar saltos de línea. */
internal fun summaryKey(programId: String, month: YearMonth): String = "$programId|$month"

/**
 * Meses con resumen del programa [programId]: los anteriores a [today] con sesiones suficientes ([MonthlyReport.eligible]), del más
 * reciente al más antiguo y como mucho [limit].
 */
internal fun summaryMonths(logs: List<WorkoutLog>, programId: String, today: LocalDate, limit: Int = 12): List<YearMonth> {
    val current = YearMonth.from(today)
    val candidates = logs.asSequence()
        .filter { it.programId == programId }
        .mapNotNull { logDate(it) }
        .map { YearMonth.from(it) }
        .filter { it.isBefore(current) }
        .distinct()
        .sortedDescending()
        .toList()
    return candidates.asSequence()
        .filter { MonthlyReportEngine.build(logs, it, programId).eligible }
        .take(limit)
        .toList()
}

/**
 * Mes cuyo resumen se avisa en Inicio: el anterior a [today], si hoy es de los primeros días ([HOME_NOTICE_LAST_DAY]), ese mes tiene
 * resumen ([eligibleMonths]) y aún no se ha visto ([seenKeys]). Null si no hay aviso.
 */
internal fun homeNoticeMonth(today: LocalDate, eligibleMonths: List<YearMonth>, seenKeys: Set<String>, programId: String): YearMonth? {
    if (today.dayOfMonth > HOME_NOTICE_LAST_DAY) return null
    val previous = YearMonth.from(today).minusMonths(1)
    val pending = previous in eligibleMonths && summaryKey(programId, previous) !in seenKeys
    return previous.takeIf { pending }
}

/** Mes del registro, o null si su fecha no se lee (se toman los diez primeros caracteres de [WorkoutLog.date]). */
private fun logDate(log: WorkoutLog): LocalDate? = runCatching { LocalDate.parse(log.date.take(10)) }.getOrNull()

private fun coverSlide(report: MonthlyReport, month: String, programName: String?): MonthSlide = MonthSlide(
    kind = MonthSlideKind.COVER,
    eyebrow = "${month.replaceFirstChar { it.uppercaseChar() }} ${report.month.year}",
    headline = if (programName.isNullOrBlank()) "Tu $month" else "Tu $month en $programName",
    body = "Un mes de entreno. Esto es lo que pasó.",
)

private fun consistencySlide(report: MonthlyReport): MonthSlide = MonthSlide(
    kind = MonthSlideKind.CONSISTENCY,
    eyebrow = "Constancia",
    bigValue = report.sessions.toString(),
    bigUnit = if (report.sessions == 1) "sesión" else "sesiones",
    body = buildString {
        append("${trainingTimeText(report.minutes)} entrenando.")
        if (report.longestStreak >= 2) append(" Tu racha más larga: ${report.longestStreak} sesiones seguidas.")
    },
)

private fun workSlide(report: MonthlyReport, previousMonth: String): MonthSlide {
    val sets = countText(report.hardSets, "serie dura", "series duras")
    val reps = countText(report.reps, "repetición", "repeticiones")
    val comparison = report.tonnageVsPreviousPct?.let { comparisonText(it, previousMonth) } ?: ""
    return MonthSlide(
        kind = MonthSlideKind.WORK,
        eyebrow = "Trabajo total",
        bigValue = oneDecimal(report.tonnageKg / 1000.0),
        bigUnit = "toneladas",
        body = "$sets y $reps.$comparison",
    )
}

private fun strengthSlide(report: MonthlyReport): MonthSlide? {
    if (report.gains.isEmpty()) return null
    return MonthSlide(
        kind = MonthSlideKind.STRENGTH,
        eyebrow = "Fuerza",
        headline = "Subiste en ${countText(report.gains.size, "ejercicio", "ejercicios")}",
        body = "e1RM de tu mejor serie de cada sesión.",
    )
}

private fun bestMarkSlide(mark: MonthlyBestMark): MonthSlide {
    val rir = mark.rir?.let { " con RIR $it" } ?: ""
    return MonthSlide(
        kind = MonthSlideKind.BEST_MARK,
        eyebrow = "Tu mejor marca",
        headline = mark.exerciseName,
        bigValue = atMostOneDecimal(mark.e1rm),
        bigUnit = "kg",
        body = "${atMostOneDecimal(mark.weight)} kg × ${mark.reps}$rir, el ${spanishDay(mark.date.dayOfWeek)} ${mark.date.dayOfMonth}.",
    )
}

private fun musclesSlide(report: MonthlyReport): MonthSlide? {
    if (report.muscles.isEmpty()) return null
    val top = report.muscles.maxBy { it.sets }
    val untrained = report.untrainedMuscles.map { it.lowercase() }
    return MonthSlide(
        kind = MonthSlideKind.MUSCLES,
        eyebrow = "Lo que más trabajaste",
        headline = "${countText(top.sets, "serie", "series")} de ${top.muscle.lowercase()}",
        body = untrained.takeIf { it.isNotEmpty() }?.let { "Sin trabajo directo de ${spanishList(it)} en todo el mes." },
    )
}

private fun effortSlide(rirMean: Double, plannedRir: Double?): MonthSlide = MonthSlide(
    kind = MonthSlideKind.EFFORT,
    eyebrow = "Esfuerzo",
    bigValue = oneDecimal(rirMean),
    bigUnit = "RIR medio",
    headline = plannedRir?.let { effortHeadline(rirMean, it) },
    body = plannedRir?.let { "El plan pedía ${atMostOneDecimal(it)}." },
)

/** Titular de Esfuerzo frente al RIR que pedía el plan. La diferencia se mide en décimas: 0,5 exacto cuenta como cerca. */
private fun effortHeadline(rirMean: Double, plannedRir: Double): String {
    val mean = (rirMean * 10).roundToLong()
    val planned = (plannedRir * 10).roundToLong()
    return when {
        abs(mean - planned) <= 5L -> "Cerca de lo que pedía el plan"
        mean < planned -> "Más cerca del fallo de lo pedido"
        else -> "Más lejos del fallo de lo pedido"
    }
}

/** Frase de la comparación con el mes anterior, con su espacio delante: « +9 % que en septiembre.». */
private fun comparisonText(pct: Int, previousMonth: String): String = when {
    pct > 0 -> " +$pct % que en $previousMonth."
    pct < 0 -> " −${abs(pct)} % que en $previousMonth."
    else -> " Igual que en $previousMonth."
}

/** Cantidad con su palabra en singular o plural: «1 serie», «246 series duras», «1.986 repeticiones». */
private fun countText(count: Int, singular: String, plural: String): String =
    "${thousandsDot(count)} ${if (count == 1) singular else plural}"

/** Entero con punto como separador de miles: 246 y 1.986. */
private fun thousandsDot(value: Int): String = value.toString().reversed().chunked(3).joinToString(".").reversed()

/** Un decimal con coma, siempre: 42,4 y 1,0. */
private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10},${abs(tenths % 10)}"
}

/** Hasta un decimal con coma y sin «,0»: 140, 142,5 y 2. */
private fun atMostOneDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${abs(tenths % 10)}"
}

/** Día de la semana en español y en minúsculas. */
private fun spanishDay(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}

/** Lista en español: «a», «a y b», «a, b y c». */
private fun spanishList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items.single()
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}
