package com.example.kpkn.screens.nutrition.scan

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.domain.nutrition.NutritionInterpretationBridge
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.TagOrigin
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import java.util.UUID

/**
 * Los gramos con los que se propone un producto escaneado: los mismos que el buscador del cajón da al tocar una fila (una porción de
 * casa declarada o la de su familia; nunca los 100 g del denominador ni el peso del envase).
 */
fun defaultGramsForScanned(food: FoodItem): Double = HouseholdPortions.eatenGramsForSearchPick(food, food.name)

/**
 * La ficha de comida que el cajón añade al confirmar un producto escaneado. Es el MISMO camino que tocar una fila del buscador: la
 * fila es la identidad comida (el código la fijó) y solo cambia la porción; el guardado de la comida es el de siempre. [grams] nulo
 * usa [defaultGramsForScanned]. La ficha va sin masa base, como la del buscador.
 */
fun scannedFoodTag(food: FoodItem, grams: Double? = null, id: String = UUID.randomUUID().toString()): ResolvedTag {
    val identity = HouseholdPortions.identityForSearchPick(food, food.name)
    val eaten = (grams ?: defaultGramsForScanned(identity)).takeIf { it.isFinite() && it > 0.0 } ?: defaultGramsForScanned(identity)
    val logged = scaleFoodByPortion(identity, amountGrams = eaten)
    val status = HouseholdPortions.operationalAutoStatus(
        food = identity,
        grams = eaten,
        brandHint = null,
        explicitKilogram = false,
        amountIntent = AmountIntent.UNSPECIFIED,
        explicitPick = true,
    )
    val tag = ResolvedTag(
        id = id,
        tag = identity.name,
        portion = PortionPreset.MEDIUM,
        quantity = 1.0,
        amountGrams = eaten,
        foodItem = identity,
        loggedFood = logged,
        isResolved = status == FoodResolutionStatus.AUTO,
        statusText = "",
        hasManualEdits = true,
        resolutionStatus = status,
        nutritionSource = NutrientBasis.source(identity),
        resolutionConfidence = 1.0,
        origin = TagOrigin.SEARCH,
    )
    return NutritionInterpretationBridge.refresh(tag.copy(foodQuery = food.name, confirmedDimensions = setOf("identity")))
}
