package com.example.kpkn.screens.nutrition.design.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.nutrition.MealHistoryDay
import com.example.kpkn.screens.nutrition.NutritionWeekDay
import com.example.kpkn.screens.nutrition.design.NutritionMiniRing
import com.example.kpkn.screens.nutrition.design.kcalRangeText
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.round

/** Las cifras tabulares de las líneas de macros y de meta: caption con `tnum`, para que las columnas de los días se alineen. */
internal val MealHistoryCaptionTnum = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1")

/** Las macros de un día en una línea: «P 120 · H 210 · G 70 g». H son los hidratos. */
internal fun mealHistoryMacros(totals: DailyMacroTotals): String =
    // Cada par letra-cifra y su unidad van con espacio sin ruptura: al 130 % solo se parte entre macros, nunca dentro de una.
    "P ${round(totals.protein).toInt()} · H ${round(totals.carbs).toInt()} · G ${round(totals.fats).toInt()} g"

/**
 * Una fila del historial (N5, NUTRITION_V1 §5): el mini anillo de kcal frente a la meta (28 dp), la fecha en `bodyStrong`, las macros en
 * `caption` tabular y, a la derecha, las kcal en `metricM` con la meta debajo en `textFaint`. Un día con rango incierto lleva «≈» en la
 * cifra y su rango en `caption`. Mide 56 dp como mínimo; tocarla abre el detalle del día.
 */
@Composable
internal fun MealHistoryDayRow(day: MealHistoryDay, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val totals = day.totals
    val kcal = round(totals.calories).toInt()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(rowShape)
            .background(if (pressed) KpknSurface.modulePressed else Color.Transparent, rowShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Ver el día",
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NutritionMiniRing(
            day = NutritionWeekDay(date = day.dateKey, kcal = kcal, kcalGoal = day.kcalGoal, isToday = day.isToday),
            size = 28.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.dateLabel,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mealHistoryMacros(totals),
                style = MealHistoryCaptionTnum,
                color = WizardColors.textMuted,
                maxLines = 2,
            )
            kcalRangeText(totals)?.let { range ->
                Text(
                    // Sin elipsis: el rango entero se lee aunque ocupe dos líneas al 130 %; «kcal» baja a su línea sin partirse.
                    text = range,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            KpknMetric(
                value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) + formatKcalEs(kcal),
                unit = "kcal",
                style = KpknType.metricM,
            )
            day.kcalGoal?.let { goal ->
                Text(
                    text = "de ${formatKcalEs(goal)}",
                    style = MealHistoryCaptionTnum,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
            }
        }
    }
}
