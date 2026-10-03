package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.NutritionLog

/**
 * Etiquetas de kcal que no presentan un centro estimado como exacto (WP-U15 / C22).
 *
 * Contrato `nutrition_interpretation_v2` («Interpretation, uncertainty and confirmation»): una fila reabierta conserva
 * el indicador de estimación y el rango guardado en vez de mostrar el centro como exacto. El formato vive aquí, sin
 * Compose ni Android, para probarlo en JVM.
 *
 * Las cifras siguen el formato que la app ya usaba: entero redondeado, sin separador de miles ni `Locale`
 * («1234 kcal»); el rango lleva guion largo, como el «Rango estimado» anterior («1100–1400»).
 */
object NutritionDisplayFormat {

    /** «≈ » delante de una cifra estimada; vacío si es exacta. */
    fun approxPrefix(isEstimate: Boolean): String = if (isEstimate) "≈ " else ""

    /** Solo la cifra de kcal: «1234» si es exacta, «≈ 1234» si es estimada. */
    fun kcalValue(totals: DailyMacroTotals): String =
        "${approxPrefix(totals.isEstimate)}${kcalInt(totals.calories)}"

    /** «1234 kcal» si es exacta, «≈ 1234 kcal» si es estimada. */
    fun kcalLabel(totals: DailyMacroTotals): String = "${kcalValue(totals)} kcal"

    /**
     * «rango 1100–1400 kcal» solo si la banda guardada tiene ancho (mín < máx ya redondeados, igual que la cifra que
     * se muestra). Sin banda, o de ancho cero, devuelve null: nunca un «rango» de 500 a 500.
     */
    fun kcalRangeLabel(totals: DailyMacroTotals): String? {
        val min = totals.caloriesMin ?: return null
        val max = totals.caloriesMax ?: return null
        val low = kcalInt(min)
        val high = kcalInt(max)
        return if (low < high) "rango $low–$high kcal" else null
    }

    /**
     * Resumen de kcal de un registro: «520 kcal» si es exacto, «≈ 520 kcal» si algún alimento es incierto y
     * «≈ 520 kcal · rango 450–600 kcal» si además la banda guardada tiene ancho. Suma los alimentos del registro
     * sea cual sea su estado: la fila de un registro planificado también se describe.
     */
    fun logKcalSummary(log: NutritionLog): String {
        val totals = computeFoodTotals(log.foods)
        val range = kcalRangeLabel(totals)
        return if (range == null) kcalLabel(totals) else "${kcalLabel(totals)} · $range"
    }

    private fun kcalInt(value: Double): Int = kotlin.math.round(value).toInt()
}
