package com.example.kpkn.screens.nutrition.nutrients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetTitle
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import dev.chrisbanes.haze.HazeState

/**
 * La lista completa de una ración (`npList`), para pintarla dentro de la hoja de la ficha: flecha atrás, nombre, «1 filete · 150 g · % de tu
 * día» y una fila por nutriente, grupo tras grupo. Tocar una fila abre la hoja de ese nutriente.
 */
@Composable
fun NutrientFoodListContent(
    food: NutrientFoodModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    var open by rememberSaveable(food.name) { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrient-food-list")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(KpknLineIcons.ChevronLeft, "Volver", onBack)
            Column(Modifier.weight(1f)) {
                Text(food.name, style = LoggerText.sheetTitle, color = LoggerColors.ink, modifier = Modifier.semantics { heading() })
                Text(food.listSubtitle, style = LoggerText.sub, color = LoggerColors.muted)
            }
        }
        NutrientGroupedRows(food.list, { open = it.name }, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 30.dp))
    }
    val total = open?.let { name -> food.totals.firstOrNull { it.nutrient.name == name } }
    if (total != null) {
        NutrientDetailSheet(NutrientFootprints.detail(total, food.profile, food.energyKcal, "esta ración"), onDismiss = { open = null }, hazeState = hazeState)
    }
}

/**
 * La lista de una comida (`npList`): hoja «Vitaminas y minerales / Desayuno · 4 alimentos · % de tu día» y una fila por nutriente, grupo
 * tras grupo; si falta dato, el total lleva «≥» y la barra va discontinua. Tocar una fila abre de qué alimento sale cada cantidad.
 */
@Composable
fun NutrientMealListSheet(
    meal: NutrientMealModel,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    var open by rememberSaveable(meal.name) { mutableStateOf<String?>(null) }
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, maxHeightFraction = 0.88f, stableHeightFraction = 0.88f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrient-meal-list")) {
            LoggerSheetTitle("Vitaminas y minerales", Modifier.padding(horizontal = 18.dp), sub = meal.listSubtitle)
            NutrientGroupedRows(meal.list, { open = it.name }, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 30.dp))
        }
    }
    val total = open?.let { name -> meal.totals.firstOrNull { it.nutrient.name == name } }
    if (total != null) {
        NutrientDetailSheet(NutrientFootprints.detail(total, meal.profile, meal.energyKcal, meal.name.lowercase().ifBlank { "esta comida" }),
            onDismiss = { open = null }, hazeState = hazeState)
    }
}
