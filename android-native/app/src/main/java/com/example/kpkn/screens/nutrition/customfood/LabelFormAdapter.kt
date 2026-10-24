package com.example.kpkn.screens.nutrition.customfood

import com.example.kpkn.domain.nutrition.label.*
import java.math.BigDecimal

/** Conserva nombre, marca y código escritos; ningún nutriente no leído se rellena con cero. */
object LabelFormAdapter {
    val fields = mapOf(LabelField.ENERGY_KCAL to CustomFoodField.CALORIES, LabelField.PROTEIN_G to CustomFoodField.PROTEIN,
        LabelField.CARBS_G to CustomFoodField.CARBS, LabelField.FAT_G to CustomFoodField.FATS, LabelField.SATURATED_G to CustomFoodField.SATURATED,
        LabelField.FIBER_G to CustomFoodField.FIBER, LabelField.SUGAR_G to CustomFoodField.SUGAR, LabelField.SODIUM_MG to CustomFoodField.SODIUM)
    fun number(value: Double): String = BigDecimal.valueOf(value).round(java.math.MathContext(12, java.math.RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
    fun apply(before: CustomFoodForm, label: LabelParse): CustomFoodForm {
        fun value(field: LabelField) = label.fields[field]?.let { number(it.value) }.orEmpty()
        val byVolume = label.basis == LabelBasis.PER_100ML
        return before.copy(
            basis = if (label.basis == LabelBasis.PER_100G) CustomFoodBasis.PER_100G else CustomFoodBasis.PER_SERVING,
            servingAmount = if (byVolume) "100" else label.servingSize?.let(::number).orEmpty(),
            servingUnit = if (byVolume) "ml" else label.servingUnit ?: "g",
            calories = value(LabelField.ENERGY_KCAL), protein = value(LabelField.PROTEIN_G), carbs = value(LabelField.CARBS_G),
            fats = value(LabelField.FAT_G), saturatedFat = value(LabelField.SATURATED_G), fiber = value(LabelField.FIBER_G),
            sugar = value(LabelField.SUGAR_G), sodiumMg = value(LabelField.SODIUM_MG))
    }
    fun value(form: CustomFoodForm, field: CustomFoodField): String = when (field) {
        CustomFoodField.CALORIES -> form.calories; CustomFoodField.PROTEIN -> form.protein; CustomFoodField.CARBS -> form.carbs
        CustomFoodField.FATS -> form.fats; CustomFoodField.SATURATED -> form.saturatedFat; CustomFoodField.FIBER -> form.fiber
        CustomFoodField.SUGAR -> form.sugar; CustomFoodField.SODIUM -> form.sodiumMg; CustomFoodField.SERVING -> form.servingAmount + " " + form.servingUnit
        else -> ""
    }
    fun conversionFactor(form: CustomFoodForm, target: LabelBasis): Double? {
        val size = CustomFoodValidator.parseNumber(form.servingAmount)?.takeIf { it > 0.0 } ?: return null
        val hundred = if (form.servingUnit == "ml") LabelBasis.PER_100ML else LabelBasis.PER_100G
        val current = if (form.basis == CustomFoodBasis.PER_100G) LabelBasis.PER_100G else LabelBasis.PER_SERVING
        return when {
            current == target -> 1.0
            current == hundred && target == LabelBasis.PER_SERVING -> size / 100.0
            current == LabelBasis.PER_SERVING && target == hundred -> 100.0 / size
            else -> null
        }?.takeIf { it.isFinite() && it > 0.0 }
    }
    fun convertCurrent(form: CustomFoodForm, target: LabelBasis, factor: Double): CustomFoodForm? {
        fun scaled(text: String): String? = if (text.isBlank()) "" else CustomFoodValidator.parseNumber(text)?.times(factor)?.takeIf { it.isFinite() }?.let(::number)
        return form.copy(basis = if (target == LabelBasis.PER_100G) CustomFoodBasis.PER_100G else CustomFoodBasis.PER_SERVING,
            calories = scaled(form.calories) ?: return null, protein = scaled(form.protein) ?: return null, carbs = scaled(form.carbs) ?: return null,
            fats = scaled(form.fats) ?: return null, saturatedFat = scaled(form.saturatedFat) ?: return null, fiber = scaled(form.fiber) ?: return null,
            sugar = scaled(form.sugar) ?: return null, sodiumMg = scaled(form.sodiumMg) ?: return null)
    }
}
