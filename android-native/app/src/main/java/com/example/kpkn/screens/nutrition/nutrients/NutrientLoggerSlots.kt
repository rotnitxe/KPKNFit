package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.example.kpkn.data.food.catalog.NutritionCatalogHolder
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.nutrients.withCatalogNutrients
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.ui.components.KpknSheet
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * Las piezas de la huella que se enchufan en los huecos del registro (FoodLoggerDrawer): la comida (n-reg), la hoja de ajuste (n-edit),
 * la ficha del buscador (n-ficha, n-ficha-label), la media hoja de Inicio (n-home) y el guardado (n-saved). [DriProfile] es el perfil
 * del plan (`DriTargets.profileFor`); la energía, el objetivo de kcal del día. Antes de guardar, el alimento aún no lleva la instantánea
 * del paquete de catálogo: se le pone aquí, fuera del hilo principal, para que la huella sea la misma que tendrá el día.
 */

/**
 * Los alimentos con la instantánea de 38 nutrientes del catálogo (si hay paquete). Al cambiar la cantidad se queda el valor anterior
 * hasta tener el nuevo, para que las barras no bajen a «sin dato» un instante.
 */
@Composable
private fun rememberWithCatalogNutrients(foods: List<LoggedFood>): List<LoggedFood> {
    val enriched by produceState(foods, foods) {
        val port = NutritionCatalogHolder.current
        value = if (port == null) foods else withContext(Dispatchers.IO) {
            try {
                foods.map { it.withCatalogNutrients(port) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { foods }
        }
    }
    return enriched
}

/** n-reg: el módulo de la comida entera bajo la tarjeta resumen (huella radial); tocarlo abre su lista. */
@Composable
fun NutrientMealSlot(mealName: String, foods: List<LoggedFood>, profile: DriProfile?, energyKcal: Double?, hazeState: HazeState? = null) {
    if (foods.isEmpty()) return
    val withData = rememberWithCatalogNutrients(foods)
    val meal = remember(mealName, withData, profile, energyKcal) { NutrientFootprints.meal(mealName, withData, profile, energyKcal) }
    var open by rememberSaveable { mutableStateOf(false) }
    NutrientMealModule(meal, onOpen = { open = true })
    if (open) NutrientMealListSheet(meal, onDismiss = { open = false }, hazeState = hazeState)
}

/**
 * n-edit: la huella radial de esa cantidad bajo «Aporta» y «Lo que más aporta»; con −/+ se mueve a la vez que las kcal. Va dentro de la
 * hoja, sin fondo propio: [background] es el color de lo que tiene detrás (la hoja, o la tarjeta si la hoja la envuelve en una).
 */
@Composable
fun NutrientFoodSlot(food: LoggedFood, profile: DriProfile?, energyKcal: Double?, background: Color = LoggerColors.sheet) {
    val withData = rememberWithCatalogNutrients(listOf(food)).first()
    val model = remember(withData, profile, energyKcal) { NutrientFootprints.fromFoods(listOf(withData), profile, energyKcal) }
    NutrientFootprintSection(model, size = NutrientRadialSizes.Edit, background = background)
}

/**
 * n-ficha: la huella radial de la ficha para los gramos elegidos, «Lo que más aporta» y la fila «N de 38 nutrientes con dato ›», que abre la
 * lista completa. Si la ficha solo trae la etiqueta (n-ficha-label), el aviso con «Completar con una ficha propia». [background] como en
 * [NutrientFoodSlot].
 */
@Composable
fun NutrientCatalogSlot(
    food: FoodItem,
    grams: Double,
    profile: DriProfile?,
    energyKcal: Double?,
    onCompleteWithOwnFood: (() -> Unit)?,
    hazeState: HazeState? = null,
    background: Color = LoggerColors.sheet,
) {
    val scaled = remember(food, grams) { scaleFoodByPortion(food, amountGrams = grams) }
    val withData = rememberWithCatalogNutrients(listOf(scaled)).first()
    val model = remember(withData, profile, energyKcal) {
        NutrientFootprints.food(withData, "${grams.roundToInt()} g", profile, energyKcal)
    }
    var showList by rememberSaveable(food.id) { mutableStateOf(false) }
    NutrientFootprintSection(
        model.footprint,
        labelOnlyText = model.labelOnlyText,
        onSeeAll = { showList = true },
        onCompleteWithOwnFood = onCompleteWithOwnFood,
        background = background,
    )
    if (showList) {
        KpknSheet(onDismissRequest = { showList = false }, hazeState = hazeState, maxHeightFraction = 0.88f, stableHeightFraction = 0.88f) {
            NutrientFoodListContent(model, onBack = { showList = false }, hazeState = hazeState)
        }
    }
}

/**
 * n-saved: «Lo que más sumó: …» al guardar. Se calcula sin la instantánea del catálogo (el guardado la añade después, en el repositorio):
 * con los datos que trae el alimento.
 */
fun nutrientSavedLineOf(foods: List<LoggedFood>, profile: DriProfile?, energyKcal: Double?): String? =
    foods.takeIf { it.isNotEmpty() }?.let { NutrientFootprints.savedLine(NutrientFootprints.fromFoods(it, profile, energyKcal)) }
