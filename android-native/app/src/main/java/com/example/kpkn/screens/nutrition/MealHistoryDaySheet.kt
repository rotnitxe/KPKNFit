package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.nutrition.design.kcalRangeText
import com.example.kpkn.screens.nutrition.design.history.MealHistoryCaptionTnum
import com.example.kpkn.screens.nutrition.design.history.MealHistoryMealModule
import com.example.kpkn.screens.nutrition.design.history.mealHistoryMacros
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknType
import kotlin.math.round

/**
 * Detalle de un día del historial (N5): la hoja KPKN que se abre al tocar una fila. Dentro va [MealHistoryDayContent].
 */
@Composable
internal fun MealHistoryDaySheet(day: MealHistoryDay, onDismiss: () -> Unit, onGoToDay: (String) -> Unit) {
    KpknSheet(onDismissRequest = onDismiss) {
        MealHistoryDayContent(day = day, onGoToDay = onGoToDay)
    }
}

/**
 * El contenido del detalle de un día: la fecha larga («martes 13 de octubre»), las kcal del día con su meta, sus comidas en solo lectura y
 * «Ir a este día», que entrega la clave del día. Va suelto para probarlo: los toques de dentro de una hoja no llegan en Robolectric.
 */
@Composable
internal fun MealHistoryDayContent(day: MealHistoryDay, onGoToDay: (String) -> Unit, modifier: Modifier = Modifier) {
    val totals = day.totals
    val kcal = round(totals.calories).toInt()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = day.longDateLabel,
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KpknMetric(
                value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) + formatKcalEs(kcal),
                unit = "kcal",
                style = KpknType.metricXL,
            )
            day.kcalGoal?.let { goal ->
                Text(
                    text = "de ${formatKcalEs(goal)} kcal",
                    style = MealHistoryCaptionTnum,
                    color = WizardColors.textFaint,
                )
            }
        }
        kcalRangeText(totals)?.let { range ->
            Text(text = range, style = KpknType.caption, color = WizardColors.textMuted)
        }
        Text(text = mealHistoryMacros(totals), style = MealHistoryCaptionTnum, color = WizardColors.textMuted)
        MealSlots.mealOrder.forEach { mealType ->
            val logs = day.logs.filter { it.mealType == mealType }
            if (logs.isNotEmpty()) MealHistoryMealModule(mealType = mealType, logs = logs)
        }
        KpknPrimaryPill(
            text = "Ir a este día",
            onClick = { onGoToDay(day.dateKey) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
