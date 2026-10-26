package com.example.kpkn.domain.competitions

import java.util.Locale

data class WeighInForecast(val daysToMeet: Int,val currentKg: Double,val targetKg: Double,
    val projectedKg: Double,val makesWeight: Boolean,val message: String,val projectionAvailable: Boolean = true)

/** Extrapolación aritmética de una tendencia observada; no prescribe una pérdida de peso. */
object WeighInPlanner {
    fun forecast(currentKg: Double,targetKg: Double,weeklyRateKg: Double?,daysToMeet: Int): WeighInForecast {
        require(currentKg.isFinite() && currentKg > 0 && targetKg.isFinite() && targetKg > 0 && daysToMeet >= 0)
        val rate = weeklyRateKg?.takeIf { it.isFinite() }
        val rawProjection = currentKg + (rate ?: 0.0) * daysToMeet / 7.0
        val validProjection = rawProjection.isFinite() && rawProjection > 0.0
        val available = validProjection && (daysToMeet == 0 || rate != null)
        val projected = if (available) rawProjection else currentKg
        val makesWeight = available && projected <= targetKg + 0.05
        fun n(v: Double) = String.format(Locale.forLanguageTag("es-CL"),"%.1f",v)
        val message = when {
            rate == null -> "Peso registrado: ${n(currentKg)} kg; objetivo declarado: ${n(targetKg)} kg. Sin tendencia suficiente para proyectar el pesaje."
            daysToMeet == 0 -> "Día del pesaje: ${n(currentKg)} kg registrados frente a ${n(targetKg)} kg de objetivo. Confirma el peso real."
            !validProjection -> "Peso registrado: ${n(currentKg)} kg; objetivo declarado: ${n(targetKg)} kg. La tendencia no permite una proyección válida para este horizonte."
            makesWeight -> "Proyección lineal: ${n(projected)} kg, dentro del objetivo declarado. No garantiza el peso del pesaje."
            else -> "Proyección lineal: ${n(projected)} kg, ${n(projected - targetKg)} kg sobre el objetivo. La diferencia actual equivale a ${n((currentKg - targetKg).coerceAtLeast(0.0) * 7 / daysToMeet)} kg/semana; es una comparación matemática, no una pauta de pérdida de peso."
        }
        return WeighInForecast(daysToMeet,currentKg,targetKg,projected,makesWeight,message,available)
    }
}
