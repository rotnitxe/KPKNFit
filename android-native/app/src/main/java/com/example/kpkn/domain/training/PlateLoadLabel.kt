package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calculations.PlateCalculator
import com.example.kpkn.domain.calculations.PlateResult
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.roundToLong

data class PlateLoadLabel(val requestedKg: Double, val displayedKg: Double, val barKg: Double, val platesPerSide: List<Double>, val exact: Boolean) {
    private fun number(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.',',')
    val plateText: String get() = if(platesPerSide.isEmpty()) "barra sola" else platesPerSide.groupingBy { it }.eachCount().entries.joinToString(" + ") { (weight,count) ->
        if(count == 1) number(weight) else "$count × ${number(weight)}"
    }
    val text: String get() = if(exact) "por lado: $plateText" else "${number(displayedKg)} kg · más cercana montable · por lado: $plateText"
    val compact: String get() = if(exact) "por lado: $plateText" else "≈${number(displayedKg)} kg montable\n${plateText.replace(" + ","+")}/lado"
}

/** Mismo stock/convenio nativo, sin tara ni cantidades supuestas. No modifica la carga prescrita. */
object PlateLoadLabels {
    fun build(targetKg: Double, convention: LoadQuantityConvention, kind: NativeLoadConventions.StockKind, inventory: EquipmentInventory?): PlateLoadLabel? {
        if(convention != LoadQuantityConvention.TOTAL_EXTERNAL || kind != NativeLoadConventions.StockKind.BARBELL || inventory == null) return null
        val bar = inventory.barbellWeightKg?.takeIf { it.isFinite() && it > 0 } ?: return null
        if(!targetKg.isFinite() || targetKg <= 0 || targetKg > 2000 || inventory.plates.any { !it.weightKg.isFinite() || it.weightKg <= 0 || it.countPerSide == null || it.countPerSide < 0 }) return null
        if(inventory.plates.isEmpty()) return null
        val lower = PlateCalculator.calculatePlatesFromStock(targetKg,bar,inventory.plates)
        var chosen = lower
        if(!lower.isExact && lower.achievedWeight < targetKg) {
            // El calculador devuelve suelo. Buscar por su función monótona el primer montable superior dentro de la distancia al suelo.
            var lo = (targetKg*1000).roundToLong()
            var hi = ((targetKg + targetKg-lower.achievedWeight)*1000).roundToLong()
            var above: PlateResult? = null
            while(lo <= hi) {
                val middle = lo+(hi-lo)/2
                val candidate = PlateCalculator.calculatePlatesFromStock(middle/1000.0,bar,inventory.plates)
                if(candidate.achievedWeight > targetKg + .001) { above=candidate;hi=middle-1 } else lo=middle+1
            }
            if(above != null && abs(above.achievedWeight-targetKg) < abs(lower.achievedWeight-targetKg)-.001) chosen=above
        }
        return PlateLoadLabel(targetKg,chosen.achievedWeight,bar,chosen.platesPerSide,abs(chosen.achievedWeight-targetKg)<.01)
    }
}
