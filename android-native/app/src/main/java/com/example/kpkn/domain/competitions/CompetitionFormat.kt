package com.example.kpkn.domain.competitions

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Textos con cifras del lienzo Registros v4: coma decimal, meses cortos sin punto y ordinales («2.º intento», «1.er intento»). */
object CompetitionFormat {
    private val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val weekdays = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    private val es = Locale.forLanguageTag("es")

    /** 192.5 → «192,5»; 130.0 → «130». Como `kg()` del lienzo. */
    fun kg(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        val rounded = (value * 100).roundToLong() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.').replace('.', ',')
    }

    /** Puntos con un decimal («79,4»). */
    fun points(value: Double?): String =
        if (value == null || !value.isFinite()) "—" else String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

    /** Diferencia con signo: «+12,5», «+0», «−2,5» (signo menos tipográfico). */
    fun signedKg(delta: Double): String = when {
        abs(delta) < 0.005 -> "+0"
        delta > 0 -> "+${kg(delta)}"
        else -> "−${kg(-delta)}"
    }

    /** «16 nov 2026». */
    fun date(date: LocalDate?): String = date?.let { "${it.dayOfMonth} ${months[it.monthValue - 1]} ${it.year}" } ?: "Sin fecha"

    /** «16 nov» (c-run-next, c-run). */
    fun dayMonth(date: LocalDate?): String = date?.let { "${it.dayOfMonth} ${months[it.monthValue - 1]}" } ?: "Sin fecha"

    /** «dom 16 nov» (campo Fecha de c-next). */
    fun weekdayDate(date: LocalDate?): String =
        date?.let { "${weekdays[it.dayOfWeek.value - 1]} ${it.dayOfMonth} ${months[it.monthValue - 1]}" } ?: "Elegir"

    /** Nombre del mes («abril»), para «desde abril». */
    fun monthName(date: LocalDate): String = date.month.getDisplayName(java.time.format.TextStyle.FULL, es)

    fun parseDate(raw: String?): LocalDate? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Ordinal masculino: 1 → «1.º», 5 → «5.º». */
    fun ordinal(n: Int): String = "$n.º"

    /** Ordinal femenino (2.ª categoría). */
    fun ordinalF(n: Int): String = "$n.ª"

    /** «1.er intento», «2.º intento», «3.er intento». */
    fun attemptOrdinal(n: Int): String = if (n == 1 || n == 3) "$n.er" else "$n.º"

    /** Tiempo en h:mm:ss («1:38:12») o m:ss si no llega a la hora («36:10»). */
    fun duration(seconds: Double?): String {
        if (seconds == null || !seconds.isFinite() || seconds < 0) return "—"
        val total = seconds.roundToLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    }

    /** Ritmo por km («4:39»). */
    fun pace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || !secondsPerKm.isFinite() || secondsPerKm <= 0) return "—"
        val total = secondsPerKm.roundToLong()
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    /** Diferencia de tiempo con signo («−3:41»). */
    fun signedDuration(deltaSeconds: Double): String = (if (deltaSeconds < 0) "−" else "+") + duration(abs(deltaSeconds))

    /** «21,1 km», «42,2 km», «10 km». */
    fun km(distance: Double?): String {
        if (distance == null || !distance.isFinite()) return "—"
        val one = floor(distance * 10 + 0.5) / 10
        return if (one % 1.0 == 0.0) "${one.toLong()} km" else String.format(Locale.ROOT, "%.1f", one).replace('.', ',') + " km"
    }

    /** «1.204»: miles con punto. */
    fun thousands(n: Int): String = String.format(Locale.ROOT, "%,d", n).replace(',', '.')

    /** Lee «4:39», «1:38:12» o «36» (minutos) como segundos. */
    fun parseDuration(raw: String): Double? {
        val parts = raw.trim().replace(',', '.').split(':').map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return when (numbers.size) {
            1 -> numbers[0] * 60
            2 -> numbers[0] * 60 + numbers[1]
            3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            else -> null
        }
    }

    /** Lee un peso escrito con coma o punto. */
    fun parseKg(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    /** Categoría de peso en el formato del lienzo: «83» → «-83», «120+» → «+120»; ya formateada se respeta. */
    fun weightClass(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value.startsWith("-") || value.startsWith("+")) return value.removeSuffix(" kg")
        return if (value.endsWith("+")) "+${value.removeSuffix("+")}" else "-$value"
    }

    /** Límite superior de una categoría («-83» → 83), o null si no tiene («+120»). */
    fun weightClassLimit(raw: String?): Double? {
        val value = weightClass(raw) ?: return null
        if (value.startsWith("+")) return null
        return value.removePrefix("-").replace(',', '.').toDoubleOrNull()
    }
}
