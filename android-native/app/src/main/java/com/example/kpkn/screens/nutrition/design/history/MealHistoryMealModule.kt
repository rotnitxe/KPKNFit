package com.example.kpkn.screens.nutrition.design.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.domain.nutrition.computeFoodTotals
import com.example.kpkn.screens.nutrition.design.logPortionText
import com.example.kpkn.screens.nutrition.design.mealIcon
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.round

/**
 * Una comida del detalle de un día (N5), en solo lectura y con el lenguaje de los módulos de comida de la raíz: el icono de la comida en un
 * círculo `inset`, su nombre y sus kcal y, debajo, una fila por alimento con su porción en `caption` y sus kcal a la derecha.
 */
@Composable
fun MealHistoryMealModule(mealType: MealType, logs: List<NutritionLog>, modifier: Modifier = Modifier) {
    val foods = logs.flatMap { it.foods }
    val totals = computeFoodTotals(foods)
    KpknModule(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(KpknSurface.inset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mealIcon(mealType),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.text,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = MealSlots.mealLabel(mealType),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KpknMetric(
                value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) + formatKcalEs(round(totals.calories).toInt()),
                unit = "kcal",
                style = KpknType.metricM,
            )
        }
        foods.forEach { food ->
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            MealHistoryFoodRow(food = food)
        }
    }
}

/** Una fila por alimento: el nombre, su porción en `caption` y las kcal tabulares a la derecha. */
@Composable
private fun MealHistoryFoodRow(food: LoggedFood) {
    val totals = computeFoodTotals(listOf(food))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.foodName.ifBlank { "Comida registrada" },
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // La porción sale del mismo texto que la fila de la raíz: un registro de un solo alimento.
            logPortionText(NutritionLog(foods = listOf(food)))?.let { portion ->
                Text(
                    text = portion,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        KpknMetric(
            value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) + formatKcalEs(round(totals.calories).toInt()),
            unit = "kcal",
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
        )
    }
}
