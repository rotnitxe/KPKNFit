package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.v2.FoodStateV2
import kotlin.math.roundToInt

/**
 * «Cómo lo preparaste» (Arreglos v1, hoja «Ajustar un alimento»): una sola elección que sustituye a «Cómo lo cocinaste» y a
 * «Variante». El orden de [entries] es el orden fijo de las chips: Crudo · Cocido · Al vapor · A la plancha · Al horno · Salteado
 * · Frito.
 */
enum class FoodPreparation(val label: String) {
    RAW("Crudo"),
    COOKED("Cocido"),
    STEAMED("Al vapor"),
    GRILLED("A la plancha"),
    BAKED("Al horno"),
    SAUTEED("Salteado"),
    FRIED("Frito"),
}

/** Una opción de la fila «Aceite», con el nivel de aceite que ya entiende el registrador. */
enum class OilAmount(val label: String, val level: String) {
    NONE("Sin", "sin aceite"),
    LITTLE("Poco", "poco"),
    NORMAL("Normal", "medio"),
    LOTS("Mucho", "abundante"),
    ;

    companion object {
        /** El nivel guardado en un alimento («sin aceite», «poco», «medio», «abundante») como opción; null si no es ninguno. */
        fun fromLevel(level: String?): OilAmount? = entries.firstOrNull { it.level.equals(level?.trim(), ignoreCase = true) }
    }
}

/**
 * Las preparaciones de un alimento y el aceite que suma cada una. Puro: sin Android ni estado.
 *
 * Regla de «Crudo»: el catálogo no dice si un alimento se come crudo (sus fichas crudas son pesos en crudo: arroz, pasta, carne),
 * así que «Crudo» solo se ofrece cuando hay ficha cruda y el grupo del alimento se come crudo: nunca en tubérculos y papas, cereales
 * y panes, pastas y arroz, legumbres, carnes (aves incluidas) y huevos ([NO_RAW_CATEGORIES]). Excepción: si el alimento ya está
 * registrado en crudo (lo escribiste así), «Crudo» se queda para que la elección actual se vea y se pueda volver a ella.
 */
object FoodPreparations {
    /** Grupos del catálogo (`ConceptV2.category`) que no se comen crudos. */
    val NO_RAW_CATEGORIES: Set<String> = setOf("TUBERCULOS", "CEREALES_PANES", "PASTAS_ARROZ", "LEGUMBRES", "CARNES", "HUEVOS")

    /** Si un alimento de [category] se come crudo (sin grupo conocido, sí). */
    fun eatenRaw(category: String?): Boolean = category?.trim()?.uppercase() !in NO_RAW_CATEGORIES

    /** La preparación de un estado del catálogo v2. */
    fun of(state: FoodStateV2): FoodPreparation = when (state) {
        FoodStateV2.RAW -> FoodPreparation.RAW
        FoodStateV2.COOKED -> FoodPreparation.COOKED
        FoodStateV2.STEAMED -> FoodPreparation.STEAMED
        FoodStateV2.GRILLED -> FoodPreparation.GRILLED
        FoodStateV2.BAKED -> FoodPreparation.BAKED
        FoodStateV2.FRIED -> FoodPreparation.FRIED
    }

    /** La preparación de un método de cocción del registrador clásico; null si no tiene equivalente (ahumado). */
    fun of(method: CookingMethod): FoodPreparation? = when (method) {
        CookingMethod.CRUDO -> FoodPreparation.RAW
        CookingMethod.COCIDO, CookingMethod.OLLA, CookingMethod.GUISADO -> FoodPreparation.COOKED
        CookingMethod.VAPOR -> FoodPreparation.STEAMED
        CookingMethod.PLANCHA, CookingMethod.ASADO_PARRILLA -> FoodPreparation.GRILLED
        CookingMethod.HORNO -> FoodPreparation.BAKED
        CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO -> FoodPreparation.FRIED
        CookingMethod.AHUMADO -> null
    }

    /** La preparación de la etiqueta de una chip de estado («Crudo», «A la plancha»…); null si no es una preparación. */
    fun fromLabel(label: String): FoodPreparation? =
        FoodPreparation.entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }

    /**
     * Las chips de «Cómo lo preparaste», en el orden fijo: las preparaciones con ficha en el catálogo ([withRecord]) más la actual
     * ([current]); «Salteado» (ficha cocida + aceite) cuando hay «Cocido»; «Crudo» solo si [category] se come crudo o [keepRaw]
     * (el alimento está o estaba registrado en crudo).
     */
    fun available(
        withRecord: Collection<FoodPreparation>,
        category: String?,
        current: FoodPreparation?,
        keepRaw: Boolean = current == FoodPreparation.RAW,
    ): List<FoodPreparation> {
        val set = withRecord.toMutableSet()
        current?.let(set::add)
        if (FoodPreparation.COOKED in set) set += FoodPreparation.SAUTEED
        if (FoodPreparation.RAW in set && !eatenRaw(category) && !keepRaw && current != FoodPreparation.RAW) set -= FoodPreparation.RAW
        return FoodPreparation.entries.filter { it in set }
    }

    /** La preparación cuya ficha usa [preparation]: «Salteado» se calcula con la cocida; el resto, con la suya. */
    fun recordOf(preparation: FoodPreparation): FoodPreparation =
        if (preparation == FoodPreparation.SAUTEED) FoodPreparation.COOKED else preparation

    /** Las opciones de «Aceite»: frito y salteado Poco · Normal · Mucho; horno y plancha Sin · Poco · Normal; el resto, ninguna. */
    fun oilOptions(preparation: FoodPreparation?): List<OilAmount> = when (preparation) {
        FoodPreparation.FRIED, FoodPreparation.SAUTEED -> listOf(OilAmount.LITTLE, OilAmount.NORMAL, OilAmount.LOTS)
        FoodPreparation.BAKED, FoodPreparation.GRILLED -> listOf(OilAmount.NONE, OilAmount.LITTLE, OilAmount.NORMAL)
        else -> emptyList()
    }

    /** El aceite al elegir una preparación: «Normal» si admite aceite; null si no. */
    fun defaultOil(preparation: FoodPreparation?): OilAmount? =
        OilAmount.NORMAL.takeIf { oilOptions(preparation).isNotEmpty() }

    /**
     * El aceite que ya trae la ficha de [preparation] tal cual: la frita lleva el de freír («Normal»); horno y plancha, ninguno
     * («Sin»). Es lo que la hoja marca mientras no se toca la fila. Salteado no tiene ficha propia (cocida + aceite): «Normal».
     */
    fun recordOil(preparation: FoodPreparation?): OilAmount? = when (preparation) {
        FoodPreparation.FRIED, FoodPreparation.SAUTEED -> OilAmount.NORMAL
        FoodPreparation.BAKED, FoodPreparation.GRILLED -> OilAmount.NONE
        else -> null
    }

    /** La línea que sustituye a la fila de aceite cuando la preparación no lo lleva; null si sí lo lleva. */
    fun noOilLine(preparation: FoodPreparation?): String? = when (preparation) {
        FoodPreparation.COOKED -> "Cocido en agua: no hay aceite que sumar."
        FoodPreparation.STEAMED -> "Al vapor: no hay aceite que sumar."
        FoodPreparation.RAW -> "Crudo: no hay aceite que sumar."
        else -> null
    }

    /**
     * Cuánto del aceite medio de freír ([OilAbsorptionCategory.mediumGrams] por 100 g) lleva cada opción. Freír absorbe más que
     * saltear, hornear o hacer a la plancha (lienzo: frito 14 / 24,7 / 35 g y horno o salteado 5 / 12 / 20 g por 200 g de papas).
     */
    private fun oilFactor(preparation: FoodPreparation, amount: OilAmount): Double = when (preparation) {
        FoodPreparation.FRIED -> when (amount) {
            OilAmount.NONE -> 0.0
            OilAmount.LITTLE -> 0.6
            OilAmount.NORMAL -> 1.0
            OilAmount.LOTS -> 1.45
        }
        FoodPreparation.SAUTEED, FoodPreparation.BAKED, FoodPreparation.GRILLED -> when (amount) {
            OilAmount.NONE -> 0.0
            OilAmount.LITTLE -> 0.2
            OilAmount.NORMAL -> 0.5
            OilAmount.LOTS -> 0.85
        }
        else -> 0.0
    }

    /** Gramos de aceite de [grams] de alimento preparado así ([category]: cuánto absorbe el alimento). 0 si no admite aceite. */
    fun oilGrams(preparation: FoodPreparation?, amount: OilAmount?, category: OilAbsorptionCategory, grams: Double): Double {
        if (preparation == null || amount == null || amount !in oilOptions(preparation)) return 0.0
        if (!grams.isFinite() || grams <= 0.0) return 0.0
        return oilFactor(preparation, amount) * category.mediumGrams * grams / 100.0
    }

    /** Las kcal del aceite (9 por gramo), redondeadas: lo que dice la cabecera de la fila («+222 kcal»). */
    fun oilKcal(preparation: FoodPreparation?, amount: OilAmount?, category: OilAbsorptionCategory, grams: Double): Int =
        (oilGrams(preparation, amount, category, grams) * 9.0).roundToInt()

    /** La cabecera derecha de la fila «Aceite»: «+222 kcal» o «sin aceite». */
    fun oilCaption(kcal: Int): String = if (kcal > 0) "+${formatThousands(kcal)} kcal" else "sin aceite"

    /**
     * Gramos de aceite que hay que sumar a la ficha de [preparation] para llegar a [amount]: la frita ya trae el de «Normal», así que
     * «Poco» resta y «Mucho» suma; horno, plancha y salteado parten de una ficha sin aceite.
     */
    fun oilDeltaGrams(preparation: FoodPreparation?, amount: OilAmount?, category: OilAbsorptionCategory, grams: Double): Double {
        val wanted = oilGrams(preparation, amount, category, grams)
        val inRecord = if (preparation == FoodPreparation.FRIED) oilGrams(preparation, OilAmount.NORMAL, category, grams) else 0.0
        return wanted - inRecord
    }

    /** [logged] con [oilGrams] de aceite más (o menos, si es negativo): grasa y kcal (9 por gramo), sin bajar de cero. */
    fun withOil(logged: LoggedFood, oilGrams: Double): LoggedFood {
        if (!oilGrams.isFinite() || oilGrams == 0.0) return logged
        return logged.copy(
            fats = ((logged.fats + oilGrams).coerceAtLeast(0.0) * 10.0).roundToInt() / 10.0,
            calories = (logged.calories + oilGrams * 9.0).coerceAtLeast(0.0).roundToInt().toDouble(),
        )
    }

    private fun formatThousands(value: Int): String {
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (value < 0) "-$grouped" else grouped
    }
}
