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
import com.example.kpkn.domain.nutrition.NutrientGroup
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/**
 * La lista completa de una ración (n-ficha-vit, n-ficha-min, n-ficha-fat), para pintarla dentro de la hoja de la ficha: flecha atrás,
 * nombre, «1 filete · 150 g · % de tu día», chips de grupo y una fila por nutriente. Tocar una fila abre la hoja de ese nutriente.
 */
@Composable
fun NutrientFoodListContent(
    food: NutrientFoodModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialGroup: NutrientGroup = NutrientGroup.VITAMINS,
    hazeState: HazeState? = null,
) {
    var group by rememberSaveable(food.name) { mutableStateOf(initialGroup) }
    var open by rememberSaveable(food.name) { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrient-food-list")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(KpknLineIcons.ChevronLeft, "Volver", onBack)
            Column(Modifier.weight(1f)) {
                Text(food.name, style = NutrientSheetTitleStyle, color = WizardColors.text, modifier = Modifier.semantics { heading() })
                Text(food.listSubtitle, style = KpknType.caption, color = WizardColors.textFaint)
            }
        }
        NutrientGroupChips(group, { group = it }, Modifier.padding(top = 8.dp, bottom = 4.dp))
        NutrientRowsColumn(food.list.rowsOf(group), { open = it.name }, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 30.dp))
    }
    val total = open?.let { name -> food.totals.firstOrNull { it.nutrient.name == name } }
    if (total != null) {
        NutrientDetailSheet(NutrientFootprints.detail(total, food.profile, food.energyKcal, "esta ración"), onDismiss = { open = null }, hazeState = hazeState)
    }
}

/**
 * «La comida en detalle» (n-reg-list): hoja «Vitaminas y minerales / Desayuno · 4 alimentos · % de tu día», los puntos de cobertura,
 * chips de grupo y filas; si falta dato, el total lleva «≥» y la fila dice cuántos faltan. Tocar una fila abre de qué alimento sale cada
 * cantidad (n-nutrient).
 */
@Composable
fun NutrientMealListSheet(
    meal: NutrientMealModel,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    initialGroup: NutrientGroup = NutrientGroup.VITAMINS,
) {
    var group by rememberSaveable(meal.name) { mutableStateOf(initialGroup) }
    var open by rememberSaveable(meal.name) { mutableStateOf<String?>(null) }
    KpknSheet(onDismissRequest = onDismiss, hazeState = hazeState, maxHeightFraction = 0.88f, stableHeightFraction = 0.88f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("nutrient-meal-list")) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("Vitaminas y minerales", style = NutrientSheetTitleStyle, color = WizardColors.text,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp).semantics { heading() })
                Text(meal.listSubtitle, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.padding(bottom = 12.dp))
                if (meal.coverage.foods > 0) {
                    NutrientCoverageLine(NutrientFootprints.mealCoverageLine(meal.coverage), Modifier.padding(bottom = 6.dp),
                        leading = { NutrientCoverageDots(meal.coverage.kinds) })
                }
            }
            NutrientGroupChips(group, { group = it }, Modifier.padding(top = 8.dp, bottom = 4.dp))
            NutrientRowsColumn(meal.list.rowsOf(group), { open = it.name }, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 30.dp))
        }
    }
    val total = open?.let { name -> meal.totals.firstOrNull { it.nutrient.name == name } }
    if (total != null) {
        NutrientDetailSheet(NutrientFootprints.detail(total, meal.profile, meal.energyKcal, meal.name.lowercase().ifBlank { "esta comida" }),
            onDismiss = { open = null }, hazeState = hazeState)
    }
}
