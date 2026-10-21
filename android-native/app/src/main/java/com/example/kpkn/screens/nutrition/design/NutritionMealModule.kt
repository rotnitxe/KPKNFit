package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Un módulo por comida (N2, NUTRITION_V1 §2.4): la cabecera con el icono de la comida en un círculo `inset` (con su marca de registrada),
 * el nombre, las kcal de la comida y el «+» que registra en ella; debajo, una fila por registro separada por filete. Sin registros, una
 * sola fila «Añadir» en `textFaint`. Siempre muestra sus filas: no se pliega. Sin estado: [onOpenLog] edita un registro y [onLongPressLog]
 * pide su menú (lo abre la pantalla).
 */
@Composable
fun NutritionMealModule(
    mealType: MealType,
    logs: List<NutritionLog>,
    totals: DailyMacroTotals,
    onAdd: () -> Unit,
    onOpenLog: (NutritionLog) -> Unit,
    onLongPressLog: (NutritionLog, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = MealSlots.mealLabel(mealType)
    val logged = logs.isNotEmpty()
    KpknModule(modifier = modifier) {
        MealHeader(mealType = mealType, label = label, logged = logged, totals = totals, onAdd = onAdd)
        if (logged) {
            logs.forEach { log ->
                MealDivider()
                NutritionFoodRow(
                    log = log,
                    onOpen = { onOpenLog(log) },
                    onLongPress = { anchor -> onLongPressLog(log, anchor) },
                )
            }
        } else {
            MealDivider()
            MealAddRow(label = label, onClick = onAdd)
        }
    }
}

/** La cabecera de una comida: icono, nombre, kcal (si hay registros) y el «+» que registra en ella. */
@Composable
private fun MealHeader(
    mealType: MealType,
    label: String,
    logged: Boolean,
    totals: DailyMacroTotals,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(36.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(KpknSurface.inset, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = mealIcon(mealType),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (logged) WizardColors.text else WizardColors.textFaint,
                )
            }
            if (logged) {
                // La marca de registrada: un check de 14 dp en `done` sobre un hueco del color del módulo, en la esquina (como en Home).
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .background(KpknSurface.module, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KpknLineIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = WizardColors.done,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (logged) {
            KpknMetric(
                value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) +
                    formatKcalEs(kotlin.math.round(totals.calories).toInt()),
                unit = "kcal",
                style = KpknType.metricM,
            )
            Spacer(Modifier.width(4.dp))
        }
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Añadir a $label",
            onClick = onAdd,
        )
    }
}

/** La fila «Añadir» de una comida vacía: en `textFaint`, con el «+», y abre el registro en esa comida. */
@Composable
private fun MealAddRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Añadir a $label", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.textFaint,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = "Añadir", style = KpknType.bodyStrong, color = WizardColors.textFaint)
    }
}

/** El filete de 1 dp entre filas del módulo. */
@Composable
private fun MealDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
