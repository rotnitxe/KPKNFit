package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.Instant
import java.time.ZoneId

/**
 * Módulo «Objetivo» de Cuerpo (BODY_V1 §2.5): la meta, desde cuándo y «Editar». Con un plan de nutrición activo, la meta vive en el plan:
 * «Editar» y «Fijar objetivo» llevan a su editor y se dice en el módulo. Sin plan, abren la hoja de objetivo propio ([onEditOwn]).
 * Sin estado.
 */
@Composable
fun BodyGoalModule(
    goal: ResolvedBodyGoal?,
    weightUnit: WizardMassUnit,
    activePlan: Boolean,
    onEditInPlan: () -> Unit,
    onEditOwn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edit: () -> Unit = if (activePlan) onEditInPlan else onEditOwn
    KpknModule(modifier = modifier.fillMaxWidth()) {
        if (goal == null) {
            KpknModuleHeader(label = "Objetivo")
            Spacer(Modifier.height(10.dp))
            Text(text = "Aún no tienes objetivo", style = KpknType.bodyStrong, color = WizardColors.text)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (activePlan) {
                    "Tu objetivo vive en tu plan de nutrición"
                } else {
                    "Fija un peso o un porcentaje de grasa para ver cuánto te acercas."
                },
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            KpknPrimaryPill(text = "Fijar objetivo", onClick = edit, modifier = Modifier.fillMaxWidth())
        } else {
            KpknModuleHeader(label = "Objetivo", action = { KpknTextAction(text = "Editar", onClick = edit) })
            Spacer(Modifier.height(10.dp))
            Text(text = bodyGoalLabel(goal.metric), style = KpknType.caption, color = WizardColors.textMuted)
            KpknMetric(
                value = bodyGoalValue(goal.metric, goal.targetValueSi, weightUnit),
                unit = bodyGoalUnit(goal.metric, weightUnit),
                style = KpknType.metricL,
            )
            val since = goal.createdAtEpochMs?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() }
            if (since != null) {
                Spacer(Modifier.height(4.dp))
                Text(text = "Desde el ${bodyDayMonthEs(since)}", style = KpknType.caption, color = WizardColors.textMuted)
            }
            if (activePlan) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tu objetivo vive en tu plan de nutrición",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        }
    }
}

/** Nombre de la meta en `caption`: «Meta de peso», «Meta de grasa corporal» o «Meta de músculo». */
internal fun bodyGoalLabel(metric: GoalMetric): String = when (metric) {
    GoalMetric.WEIGHT -> "Meta de peso"
    GoalMetric.BODY_FAT -> "Meta de grasa corporal"
    GoalMetric.MUSCLE_MASS -> "Meta de músculo"
}

/** Cifra de la meta en la unidad del usuario: el peso en kilos o libras, y los porcentajes tal cual. */
internal fun bodyGoalValue(metric: GoalMetric, targetKg: Double, unit: WizardMassUnit): String = when (metric) {
    GoalMetric.WEIGHT -> bodyTargetEs(WizardWeightScale.toDisplay(targetKg, unit))
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> bodyTargetEs(targetKg)
}

/** Unidad de la meta: «kg» o «lb» para el peso, «%» para los porcentajes. */
internal fun bodyGoalUnit(metric: GoalMetric, unit: WizardMassUnit): String = when (metric) {
    GoalMetric.WEIGHT -> unit.code
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> "%"
}
