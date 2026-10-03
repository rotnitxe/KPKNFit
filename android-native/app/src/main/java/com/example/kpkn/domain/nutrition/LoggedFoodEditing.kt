package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Editar una comida ya registrada (WP-U11, C9). Pura: sin Android, sin Room y sin estado.
 *
 * Reabrir una comida no es volver a interpretar su texto: cada alimento guardado vuelve como una tarjeta que ya estaba
 * decidida ([loggedFoodToEditableTag]). Por contrato (`docs/contracts/nutrition_interpretation_v2.md`, «Saving and
 * learning»), aceptar una estimación no confirma identidad, preparación ni porción: la tarjeta reabierta conserva su
 * indicador de estimación y su rango, no hace ninguna pregunta nueva y editarla nunca enseña un hábito.
 */

/** Largo del día (`yyyy-MM-dd`) dentro de `NutritionLog.date`; el resto es la hora con la que se guardó. */
private const val DAY_LENGTH = 10

/**
 * Sufijo que `FoodInterpretationV2Engine.interpretResolved` agrega a la nota de referencia de una estimación sin ficha o
 * con la composición por confirmar. Se reconoce aquí para devolver la nota tal como se guardó: `LoggedFoodEditingTest`
 * fija que el viaje de ida y vuelta no la duplica.
 */
private const val UNMATCHED_NOTE_SUFFIX = " Rango orientativo; composición sin confirmar."

/**
 * La tarjeta de un alimento ya registrado, lista para editar.
 *
 * Es una estimación aceptada (`explicitDecision` sin dimensiones confirmadas): [NutritionInterpretationBridge.enrich] no
 * le abre ninguna pregunta de identidad, estado, aceite ni porción, conserva el rango guardado, la deja finalizable y
 * `confirmedLearning()` es null, así que guardarla no enseña nada. La masa es la guardada (`EXPLICIT_MASS`) y es también
 * el ancla de todo recálculo posterior. El origen [TagOrigin.EDIT] la fija: volver a analizar el texto no la borra.
 *
 * El alimento se toma por lo que quedó guardado, sin volver a buscarlo en el catálogo ("Do not recalculate historical logs
 * after catalog updates"). La cantidad se lee como gramos porque eso es lo que guarda todo registro nuevo; un registro
 * antiguo con otra unidad conserva su número (y su unidad si no se toca, ver [mergeEditedFoods]).
 */
fun loggedFoodToEditableTag(food: LoggedFood): ResolvedTag {
    val grams = food.amount
    return NutritionInterpretationBridge.refresh(
        ResolvedTag(
            tag = food.foodName,
            quantity = food.quantity.takeIf { it.isFinite() && it > 0.0 } ?: 1.0,
            amountGrams = grams,
            baseAmountGrams = grams,
            portionMinGrams = grams,
            portionMaxGrams = grams,
            cookingMethod = food.cookingMethod,
            loggedFood = food,
            baseLoggedFood = food,
            isResolved = true,
            analysisSource = food.analysisSource ?: AnalysisSource.RULES,
            statusText = "",
            hasManualEdits = true,
            amountIntent = AmountIntent.EXPLICIT_MASS,
            resolutionStatus = FoodResolutionStatus.CONFIRMED_ESTIMATE,
            nutritionSource = editableNutritionSource(food),
            isUncertain = food.isUncertain,
            explicitDecision = true,
            confirmedDimensions = emptySet(),
            nutritionEstimate = savedEstimateOf(food),
            origin = TagOrigin.EDIT,
        ),
    )
}

/**
 * De dónde salieron los nutrientes de un alimento guardado, para que la tarjeta reabierta no lo degrade ni lo promueva.
 *
 * Manda la evidencia que se guardó con él (`evidenceJson.source`): un alimento elegido por búsqueda no lleva
 * `analysisSource` pero sí su procedencia. Sin evidencia (registros antiguos) solo `DATABASE` cuenta como verificado;
 * lo demás es una estimación, porque una fuente desconocida no se declara verificada.
 */
internal fun editableNutritionSource(food: LoggedFood): NutritionSourceKind = when (evidenceSource(food)) {
    "HEURISTIC" -> NutritionSourceKind.HEURISTIC_ESTIMATE
    "DATASET_SEMANTIC" -> NutritionSourceKind.DATASET_ESTIMATE
    "MANUAL", "CUSTOM_CONFIRMED" -> NutritionSourceKind.USER_PROVIDED
    "USDA_FOUNDATION", "USDA_FNDDS", "USDA_SR_LEGACY", "USDA_BRANDED", "OPEN_FOOD_FACTS" -> NutritionSourceKind.VERIFIED_GLOBAL
    "CURATED_LOCAL" -> NutritionSourceKind.CURATED_LOCAL
    else -> if (food.analysisSource == AnalysisSource.DATABASE) {
        NutritionSourceKind.CURATED_LOCAL
    } else {
        NutritionSourceKind.HEURISTIC_ESTIMATE
    }
}

/** Valor de `source` en el JSON de evidencia, o null si no hay evidencia legible. */
private fun evidenceSource(food: LoggedFood): String? {
    val json = food.evidenceJson?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { Json.parseToJsonElement(json).jsonObject["source"]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

/**
 * Un alimento que se guardó con una nota de referencia («Asumí …; composición sin confirmar») es una estimación de
 * densidad: la nota y el rango por 100 g deben seguir en la tarjeta para que un cambio de gramos vuelva a producir un
 * intervalo y no un valor exacto («an unmatched estimate must not pretend … a zero-width nutrient interval»). Se
 * reconstruye desde el rango guardado de la porción comida.
 */
private fun savedEstimateOf(food: LoggedFood): NutritionEstimateEvidence? {
    val note = food.nutritionReferenceNote?.takeIf { it.isNotBlank() } ?: return null
    val grams = food.amount.takeIf { it.isFinite() && it > 0.0 } ?: return null
    fun per100(value: Double?, central: Double) = (value ?: central) * 100.0 / grams
    val unmatched = note.endsWith(UNMATCHED_NOTE_SUFFIX)
    return NutritionEstimateEvidence(
        assumption = if (unmatched) note.removeSuffix(UNMATCHED_NOTE_SUFFIX) else note,
        minPer100g = NutritionProfile(
            calories = per100(food.caloriesMin, food.calories),
            protein = per100(food.proteinMin, food.protein),
            carbs = per100(food.carbsMin, food.carbs),
            fats = per100(food.fatsMin, food.fats),
        ),
        maxPer100g = NutritionProfile(
            calories = per100(food.caloriesMax, food.calories),
            protein = per100(food.proteinMax, food.protein),
            carbs = per100(food.carbsMax, food.carbs),
            fats = per100(food.fatsMax, food.fats),
        ),
        isUnmatchedFallback = unmatched,
    )
}

/**
 * Vuelve a interpretar una tarjeta tras una edición del usuario (gramos, porción, aceite, macros, ficha).
 *
 * Para una tarjeta del texto o de la búsqueda es lo de siempre: la dimensión editada queda confirmada, la decisión
 * explícita se limpia y el intervalo se reconstruye desde la nueva fuente o cantidad. Una tarjeta [TagOrigin.EDIT] ya
 * estaba decidida cuando se guardó: sigue siendo una estimación aceptada, así que editarla no le abre una pregunta
 * (identidad, crudo/cocido, aceite) ni le agrega una dimensión confirmada. Editar nunca enseña un hábito.
 */
fun refreshAfterEdit(tag: ResolvedTag, dimension: String? = null): ResolvedTag {
    val reopened = tag.origin == TagOrigin.EDIT
    val base = if (dimension == null) tag.rebaseManualNutrients() else tag
    val confirmed = if (dimension == null || reopened) tag.confirmedDimensions else tag.confirmedDimensions + dimension
    return NutritionInterpretationBridge.refresh(
        base.copy(
            confirmedDimensions = confirmed,
            explicitDecision = if (dimension == null) tag.explicitDecision else reopened,
            // Rebuild intervals from the newly selected source/amount.
            loggedFood = tag.loggedFood?.copy(
                caloriesMin = null, caloriesMax = null, proteinMin = null, proteinMax = null,
                carbsMin = null, carbsMax = null, fatsMin = null, fatsMax = null,
            ),
        ),
    )
}

/**
 * Los alimentos que se guardan al cerrar una edición. Un alimento que el usuario no tocó vuelve EXACTAMENTE como estaba
 * guardado (unidad, rango, procedencia y nota incluidas): el logger regenera la evidencia desde la tarjeta, y esa
 * regeneración perdería la ficha de origen y reescribiría la unidad de un registro antiguo. Un alimento que sí cambió
 * (otros gramos, otros macros, otra ficha) o uno nuevo se guarda como lo calculó el logger.
 *
 * Se empareja por el id del alimento, que la tarjeta reabierta conserva. Un id vacío o repetido no empareja nada: sin
 * identidad fiable es preferible el valor calculado a reutilizar el alimento equivocado.
 */
fun mergeEditedFoods(edited: List<LoggedFood>, original: List<LoggedFood>): List<LoggedFood> {
    val byId = original.filter { it.id.isNotBlank() }
        .groupBy { it.id }
        .mapNotNull { (id, foods) -> foods.singleOrNull()?.let { id to it } }
        .toMap()
    return edited.map { food ->
        val before = byId[food.id]
        if (before != null && sameFood(food, before)) before else food
    }
}

/** Mismo alimento, con la misma cantidad y los mismos nutrientes centrales. */
private fun sameFood(a: LoggedFood, b: LoggedFood): Boolean =
    a.foodName == b.foodName && a.amount == b.amount && a.cookingMethod == b.cookingMethod &&
        a.calories == b.calories && a.protein == b.protein && a.carbs == b.carbs && a.fats == b.fats

/**
 * La comida editada lista para guardar: [original] con la comida y los alimentos nuevos. Conserva su id (guardar
 * reemplaza la fila, nunca la duplica), sus notas y su estado. La fecha se conserva tal como estaba mientras el día no
 * cambie, para no reescribir la hora con la que se guardó; si cambia, usa el formato del logger.
 */
fun applyEdit(original: NutritionLog, editedFoods: List<LoggedFood>, mealType: MealType, logDate: String): NutritionLog =
    original.copy(
        date = if (original.date.take(DAY_LENGTH) == logDate) original.date else "${logDate}T12:00:00.000Z",
        mealType = mealType,
        foods = mergeEditedFoods(editedFoods, original.foods),
    )
