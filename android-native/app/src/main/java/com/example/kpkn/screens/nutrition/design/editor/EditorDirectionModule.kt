package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.NutritionPlanEditorDraft
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknTextAction

/** Dirección en tres segmentos. «Profesional» no está aquí: esa dirección la fija la procedencia. */
private val DirectionOptions = listOf("Definir", "Mantener", "Volumen")
private val DirectionChoices = listOf(PlanDirection.DEFICIT, PlanDirection.MAINTENANCE, PlanDirection.SURPLUS)

private val MetricOptions = listOf("Peso", "% grasa", "% músculo")
private val MetricChoices = listOf(GoalMetric.WEIGHT, GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS)

/**
 * Módulo «Dirección y meta». La dirección va en tres segmentos con su lectura; con procedencia profesional se apaga y lo dice. La meta
 * se elige con su control: la regla de peso en la unidad del borrador, o un campo numérico con «%» para la grasa y el músculo. Sin meta
 * escrita, «Fijar una meta»; con [goalOpen], el control aparece vacío hasta que se escribe algo. Sin estado.
 */
@Composable
fun EditorDirectionModule(
    draft: NutritionPlanEditorDraft,
    goalOpen: Boolean,
    onDirection: (PlanDirection) -> Unit,
    onGoalMetric: (GoalMetric) -> Unit,
    onTargetValue: (String) -> Unit,
    onOpenGoal: () -> Unit,
    onRemoveGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val professional = draft.direction == PlanDirection.PROFESSIONAL
    val directionIndex = draft.direction?.let { DirectionChoices.indexOf(it) } ?: -1
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Dirección y meta")
        Spacer(Modifier.height(12.dp))
        EditorCaption("Dirección")
        Spacer(Modifier.height(8.dp))
        if (directionIndex >= 0 && !professional) {
            KpknSegmentedControl(
                options = DirectionOptions,
                selectedIndex = directionIndex,
                onSelect = { onDirection(DirectionChoices[it]) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Sin dirección elegida, o con la de tercero: ningún segmento parece elegido; con tercero, todo queda apagado.
            EditorSegmentedTrack(
                options = DirectionOptions,
                selectedIndex = null,
                onSelect = { onDirection(DirectionChoices[it]) },
                enabled = !professional,
            )
        }
        Spacer(Modifier.height(8.dp))
        EditorCaption(editorDirectionLine(draft.direction))
        Spacer(Modifier.height(20.dp))
        EditorCaption("Meta")
        Spacer(Modifier.height(8.dp))
        KpknSegmentedControl(
            options = MetricOptions,
            selectedIndex = MetricChoices.indexOf(draft.goalMetric),
            onSelect = { onGoalMetric(MetricChoices[it]) },
            modifier = Modifier.fillMaxWidth(),
            size = KpknSegmentedSize.Compact,
        )
        Spacer(Modifier.height(12.dp))
        val hasTarget = draft.targetValueText.isNotBlank()
        if (hasTarget || goalOpen) {
            if (draft.goalMetric == GoalMetric.WEIGHT) {
                EditorWeightGoal(draft = draft, onTargetValue = onTargetValue)
            } else {
                EditorNumberField(
                    value = draft.targetValueText,
                    onValueChange = onTargetValue,
                    unit = "%",
                    placeholder = "Ej.: 15",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(4.dp))
            KpknTextAction(text = "Quitar meta", onClick = onRemoveGoal)
        } else {
            KpknTextAction(text = "Fijar una meta", onClick = onOpenGoal)
        }
    }
}

/**
 * Meta de peso: la cifra en `metricL` con su unidad y la regla debajo, en la unidad del borrador (kg o lb). Sin meta escrita, la regla
 * arranca en el peso actual, que es un punto de partida y no una respuesta: la cifra dice «—» hasta que se mueve.
 */
@Composable
private fun EditorWeightGoal(draft: NutritionPlanEditorDraft, onTargetValue: (String) -> Unit) {
    val unit = editorWeightUnit(draft.weightUnit)
    val targetKg = editorWeightTargetKg(draft.targetValueText, draft.weightUnit)
    val anchorKg = targetKg ?: editorWeightTargetKg(draft.weightText, draft.weightUnit)
    KpknMetric(
        value = targetKg?.let { editorWeightTargetText(it, unit) } ?: "—",
        unit = if (targetKg == null) null else unit.code,
    )
    Spacer(Modifier.height(8.dp))
    WizardWeightRule(
        unit = unit,
        valueKg = anchorKg,
        onValueChange = { kg -> onTargetValue(editorWeightTargetText(kg, unit)) },
    )
}
