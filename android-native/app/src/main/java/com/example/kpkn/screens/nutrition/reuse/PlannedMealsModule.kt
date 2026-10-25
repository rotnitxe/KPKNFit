package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.computeFoodTotals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Las comidas PLANIFICADAS de un día (copias a fechas futuras, N4). No cuentan como lo comido: viven aparte de los módulos de comida.
 * Cuando el día ya llegó ([canMarkConsumed]), «Pasar a consumida» las convierte en registro; siempre se pueden quitar. Si no hay
 * comidas planificadas no pinta nada. Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannedMealsModule(
    logs: List<NutritionLog>,
    canMarkConsumed: Boolean,
    onMarkConsumed: (NutritionLog) -> Unit,
    onRemove: (NutritionLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (logs.isEmpty()) return
    KpknModule(modifier = modifier.testTag("planned-meals")) {
        KpknModuleHeader(label = "Planificado")
        Text(
            if (canMarkConsumed) "Aún no cuentan como lo que comiste. Pásalas a consumidas cuando las comas."
            else "Es un plan: no cuenta como lo que comiste ni en tu gasto estimado.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        logs.forEachIndexed { index, log ->
            Spacer(Modifier.height(8.dp))
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            val kcal = kotlin.math.round(computeFoodTotals(log.foods).calories).toInt()
            Row(Modifier.fillMaxWidth().padding(top = if (index > 0) 8.dp else 0.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(MealSlots.mealLabel(log.mealType), style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(
                        log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida planificada" },
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                KpknMetric(value = formatKcalEs(kcal), unit = "kcal", style = KpknType.metricM)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (canMarkConsumed) {
                    KpknTextAction(
                        text = "Pasar a consumida",
                        onClick = { onMarkConsumed(log) },
                        modifier = Modifier.testTag("planned-consume-${log.id}"),
                        leadingIcon = KpknLineIcons.Check,
                    )
                }
                KpknTextAction(
                    text = "Quitar",
                    onClick = { onRemove(log) },
                    modifier = Modifier.testTag("planned-remove-${log.id}"),
                    leadingIcon = KpknLineIcons.Trash,
                    color = WizardColors.textMuted,
                )
            }
        }
    }
}
