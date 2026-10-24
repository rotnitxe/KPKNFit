package com.example.kpkn.screens.nutrition.customfood

import com.example.kpkn.data.models.FatBreakdown
import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.Gtin
import com.example.kpkn.domain.nutrition.NutrientBasis
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** La base de los nutrientes que la persona teclea: los de una porción o los de 100 g. */
enum class CustomFoodBasis { PER_SERVING, PER_100G }

enum class CustomFoodField { NAME, BRAND, BARCODE, SERVING, BASIS, CALORIES, PROTEIN, CARBS, FATS, FIBER, SUGAR, SODIUM, SATURATED }

/**
 * Lo que hay escrito en el formulario de un alimento propio, tal cual (texto). Los números admiten coma o punto decimal.
 * [servingUnit] es «g» o «ml».
 */
data class CustomFoodForm(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val servingAmount: String = "",
    val servingUnit: String = "g",
    val basis: CustomFoodBasis = CustomFoodBasis.PER_SERVING,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fats: String = "",
    val fiber: String = "",
    val sugar: String = "",
    val sodiumMg: String = "",
    val saturatedFat: String = "",
)

/**
 * El resultado de validar el formulario. [errors] bloquean el guardado; [warning] avisa pero no bloquea (kcal que no cuadran con los
 * macros, fibra o azúcar mayores que los carbohidratos). [food] solo existe si no hay errores.
 */
data class CustomFoodValidation(
    val errors: Map<CustomFoodField, String>,
    val warning: String?,
    val food: FoodItem?,
) {
    val isValid: Boolean get() = errors.isEmpty() && food != null
}

/** Validación y conversión del formulario de alimento propio. Pura: no toca Room ni Android. */
object CustomFoodValidator {
    private const val MAX_NAME = 80
    private const val MAX_BRAND = 60
    private const val MAX_SERVING = 5_000.0
    private const val MAX_SODIUM_MG = 100_000.0

    /** Un alimento «por 100 g» no puede declarar más de 900 kcal ni más de 100 g de macros en 100 g. */
    private const val MAX_KCAL_PER_100G = 900.0
    private const val MAX_MACRO_PER_100G = 100.0
    private const val MACRO_SUM_SLACK = 0.5
    private const val MAX_KCAL_PER_SERVING = 5_000.0
    private const val MAX_MACRO_PER_SERVING = 1_000.0

    /** Margen con el que las kcal escritas «cuadran» con 4·P + 4·C + 9·G: el mayor de 30 kcal o el 15 % (alcohol, fibra y redondeos). */
    private const val ENERGY_TOLERANCE_KCAL = 30.0
    private const val ENERGY_TOLERANCE_FRACTION = 0.15

    /** Desde esta diferencia el registro de comidas ya no daría el alimento por verificable (misma regla que `FoodIdentity`). */
    private const val LOGGER_REJECT_KCAL = 80.0
    private const val LOGGER_REJECT_FRACTION = 0.70

    private val NUMBER = Regex("^(\\d+([.,]\\d*)?|[.,]\\d+)$")

    /** El número escrito (coma o punto), o null si no es un número simple sin signo ni letras. */
    fun parseNumber(text: String): Double? {
        val trimmed = text.trim()
        if (!NUMBER.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /**
     * Valida [form]. Con [id] y, si se edita, el alimento anterior en [base] (conserva lo que el formulario no edita: uso,
     * última vez, etiquetas y los micronutrientes distintos del sodio). Un código de barras válido se guarda en su forma canónica de
     * 13 dígitos.
     */
    fun validate(form: CustomFoodForm, id: String, base: FoodItem? = null): CustomFoodValidation {
        val errors = linkedMapOf<CustomFoodField, String>()
        val warnings = mutableListOf<String>()
        val per100 = form.basis == CustomFoodBasis.PER_100G

        val name = form.name.trim()
        when {
            name.isEmpty() -> errors[CustomFoodField.NAME] = "Escribe un nombre."
            name.length > MAX_NAME -> errors[CustomFoodField.NAME] = "Máximo $MAX_NAME caracteres."
        }
        val brand = form.brand.trim()
        if (brand.length > MAX_BRAND) errors[CustomFoodField.BRAND] = "Máximo $MAX_BRAND caracteres."

        var barcode: String? = null
        if (form.barcode.isNotBlank()) {
            barcode = Gtin.normalize(form.barcode)
            if (barcode == null) errors[CustomFoodField.BARCODE] = "Código no válido: revisa los dígitos (8, 12 o 13)."
        }

        val unit = form.servingUnit.trim().lowercase(Locale.ROOT).takeIf { it == "g" || it == "ml" }
        val serving = parseNumber(form.servingAmount)
        when {
            form.servingAmount.isBlank() -> errors[CustomFoodField.SERVING] = "Indica la cantidad de la porción."
            serving == null -> errors[CustomFoodField.SERVING] = "Escribe un número, sin signo ni letras."
            serving <= 0.0 -> errors[CustomFoodField.SERVING] = "La porción tiene que ser mayor que 0."
            serving > MAX_SERVING -> errors[CustomFoodField.SERVING] = "La porción no puede pasar de ${MAX_SERVING.toInt()}."
        }
        if (unit == null) errors[CustomFoodField.SERVING] = errors[CustomFoodField.SERVING] ?: "Elige gramos o mililitros."
        if (per100 && unit == "ml") errors[CustomFoodField.BASIS] = "«Por 100 g» solo vale con gramos; elige «Por porción» para mililitros."

        val maxKcal = if (per100) MAX_KCAL_PER_100G else MAX_KCAL_PER_SERVING
        val maxMacro = if (per100) MAX_MACRO_PER_100G else MAX_MACRO_PER_SERVING
        val kcal = required(form.calories, CustomFoodField.CALORIES, maxKcal, "kcal", errors)
        val protein = required(form.protein, CustomFoodField.PROTEIN, maxMacro, "g", errors)
        val carbs = required(form.carbs, CustomFoodField.CARBS, maxMacro, "g", errors)
        val fats = required(form.fats, CustomFoodField.FATS, maxMacro, "g", errors)
        val fiber = optional(form.fiber, CustomFoodField.FIBER, maxMacro, "g", errors)
        val sugar = optional(form.sugar, CustomFoodField.SUGAR, maxMacro, "g", errors)
        val sodium = optional(form.sodiumMg, CustomFoodField.SODIUM, MAX_SODIUM_MG, "mg", errors)
        val saturated = optional(form.saturatedFat, CustomFoodField.SATURATED, maxMacro, "g", errors)

        if (per100 && protein != null && carbs != null && fats != null && protein + carbs + fats > MAX_MACRO_PER_100G + MACRO_SUM_SLACK) {
            errors[CustomFoodField.PROTEIN] = errors[CustomFoodField.PROTEIN] ?: "Proteína, carbohidratos y grasas suman más de 100 g en 100 g."
        }

        if (kcal != null && protein != null && carbs != null && fats != null) {
            val expected = 4.0 * protein + 4.0 * carbs + 9.0 * fats
            val diff = abs(kcal - expected)
            val tolerance = max(ENERGY_TOLERANCE_KCAL, ENERGY_TOLERANCE_FRACTION * max(kcal, expected))
            if (diff > tolerance) {
                val rejected = diff > max(LOGGER_REJECT_KCAL, LOGGER_REJECT_FRACTION * kcal)
                warnings += "Las kcal no cuadran con los macros (4·P + 4·C + 9·G ≈ ${expected.toInt()} kcal)." +
                    if (rejected) " Con tanta diferencia el registro de comidas podría no aceptar este alimento." else " Puedes guardarlo igual."
            }
        }
        if (fats != null && saturated != null && saturated > fats) warnings += "Las grasas saturadas son mayores que las grasas totales."
        if (carbs != null && fiber != null && fiber > carbs) warnings += "La fibra es mayor que los carbohidratos."
        if (carbs != null && sugar != null && sugar > carbs) warnings += "El azúcar es mayor que los carbohidratos."

        if (errors.isNotEmpty() || kcal == null || protein == null || carbs == null || fats == null || serving == null || unit == null) {
            return CustomFoodValidation(errors, warnings.joinToString(" ").ifBlank { null }, null)
        }

        val zeroEnergy = kcal <= NutrientBasis.ZERO_ENERGY_MAX_KCAL && protein == 0.0 && carbs == 0.0 && fats == 0.0
        val seed = base ?: FoodItem()
        val food = seed.copy(
            id = id,
            name = name,
            brand = brand.ifEmpty { null },
            normalizedName = null,
            normalizedBrand = null,
            servingSize = serving,
            servingUnit = unit,
            unit = unit,
            calories = kcal,
            protein = protein,
            carbs = carbs,
            fats = fats,
            isCustom = true,
            isAiInferred = false,
            fatBreakdown = when {
                saturated != null -> (seed.fatBreakdown ?: FatBreakdown()).copy(saturated = saturated)
                seed.fatBreakdown != null -> seed.fatBreakdown.copy(saturated = 0.0).takeIf { it.monounsaturated > 0.0 || it.polyunsaturated > 0.0 || it.trans > 0.0 }
                else -> null
            },
            carbBreakdown = if ((fiber ?: 0.0) > 0.0 || (sugar ?: 0.0) > 0.0) CarbBreakdown(fiber = fiber ?: 0.0, sugar = sugar ?: 0.0) else null,
            micronutrients = micronutrientsConDeclaraciones(seed.micronutrients, sodium, fiber, sugar),
            tags = if (zeroEnergy) listOf(NutrientBasis.ZERO_ENERGY_TAG) else emptyList(),
            source = "custom",
            sourceRecordId = barcode,
            nutritionBasis = if (per100) "PER_100G_AS_SOLD" else "PER_SERVING",
            portionGrams = if (unit == "g") serving else null,
            qualityFlags = emptyList(),
        )
        return CustomFoodValidation(errors, warnings.joinToString(" ").ifBlank { null }, food)
    }

    /** El formulario que corresponde a un alimento propio ya guardado (para editarlo). */
    fun fromFood(food: FoodItem): CustomFoodForm = CustomFoodForm(
        name = food.name,
        brand = food.brand.orEmpty(),
        barcode = food.sourceRecordId.orEmpty(),
        servingAmount = format(food.servingSize),
        servingUnit = if (food.unit.equals("ml", ignoreCase = true)) "ml" else "g",
        basis = if (food.nutritionBasis.startsWith("PER_100G")) CustomFoodBasis.PER_100G else CustomFoodBasis.PER_SERVING,
        calories = format(food.calories),
        protein = format(food.protein),
        carbs = format(food.carbs),
        fats = format(food.fats),
        saturatedFat = food.fatBreakdown?.let { format(it.saturated) }.orEmpty(),
        fiber = if (tieneCeroDeclarado(food, "Fibra")) "0" else food.carbBreakdown?.fiber?.takeIf { it > 0.0 }?.let { format(it) }.orEmpty(),
        sugar = if (tieneCeroDeclarado(food, "Azúcar")) "0" else food.carbBreakdown?.sugar?.takeIf { it > 0.0 }?.let { format(it) }.orEmpty(),
        sodiumMg = food.micronutrients.firstOrNull { it.name.equals("Sodio", ignoreCase = true) }?.amount?.let { format(it) }.orEmpty(),
    )

    private fun tieneCeroDeclarado(food: FoodItem, name: String): Boolean =
        food.micronutrients.any { it.name.equals(name, ignoreCase = true) && it.amount == 0.0 }

    /** El cero legado de CarbBreakdown también significa ausencia: solo el cero escrito se declara aparte. */
    private fun micronutrientsConDeclaraciones(
        base: List<Micronutrient>, sodiumMg: Double?, fiber: Double?, sugar: Double?,
    ): List<Micronutrient> {
        val otros = base.filterNot { micro ->
            micro.name.equals("Sodio", ignoreCase = true) ||
                (micro.amount == 0.0 && (micro.name.equals("Fibra", ignoreCase = true) || micro.name.equals("Azúcar", ignoreCase = true)))
        }
        return buildList {
            addAll(otros)
            if (sodiumMg != null && sodiumMg >= 0.0) add(Micronutrient("Sodio", sodiumMg, "mg"))
            if (fiber == 0.0) add(Micronutrient("Fibra", 0.0, "g"))
            if (sugar == 0.0) add(Micronutrient("Azúcar", 0.0, "g"))
        }
    }

    private fun required(text: String, field: CustomFoodField, max: Double, unit: String, errors: MutableMap<CustomFoodField, String>): Double? {
        if (text.isBlank()) {
            errors[field] = "Obligatorio. Si no tiene, escribe 0."
            return null
        }
        return number(text, field, max, unit, errors)
    }

    private fun optional(text: String, field: CustomFoodField, max: Double, unit: String, errors: MutableMap<CustomFoodField, String>): Double? {
        if (text.isBlank()) return null
        return number(text, field, max, unit, errors)
    }

    private fun number(text: String, field: CustomFoodField, max: Double, unit: String, errors: MutableMap<CustomFoodField, String>): Double? {
        val value = parseNumber(text)
        when {
            value == null -> errors[field] = "Escribe un número, sin signo ni letras."
            value > max -> errors[field] = "Máximo ${format(max)} $unit${if (max == MAX_KCAL_PER_100G) " en 100 g" else ""}."
            else -> return value
        }
        return null
    }

    /** Un número para mostrarlo en el campo: entero si lo es; si no, hasta 2 decimales con coma. */
    internal fun format(value: Double): String {
        if (value % 1.0 == 0.0) return value.toLong().toString()
        return String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.').replace('.', ',')
    }
}
