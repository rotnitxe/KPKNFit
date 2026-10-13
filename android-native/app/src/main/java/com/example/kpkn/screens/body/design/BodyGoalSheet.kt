package com.example.kpkn.screens.body.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.screens.onboarding.design.WizardBodyFatRuler
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Métricas que se pueden fijar como objetivo propio, en el orden del selector. */
private val GOAL_METRICS = listOf(GoalMetric.WEIGHT, GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS)

/** Etiquetas del selector de métrica de la hoja. */
private val GOAL_METRIC_LABELS = listOf("Peso", "% grasa", "% músculo")

/** Rango de la regla de % de músculo: la de grasa no cubre las metas musculares habituales. */
private val MUSCLE_RULE_RANGE = 20..60

/** Punto de partida de la regla de % antes de tocarla: no es una respuesta y no se guarda. */
private const val RULE_START_PERCENT = 20.0

/** Hoja de objetivo propio (BODY_V1 §2.5), sin plan activo. Sin estado: el borrador vive en quien la abre. */
@Composable
fun BodyGoalSheet(
    draft: BodyGoalDraft,
    weightUnit: WizardMassUnit,
    canRemove: Boolean,
    onDraftChange: (BodyGoalDraft) -> Unit,
    onSave: (BodyGoal) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        BodyGoalSheetContent(
            draft = draft,
            weightUnit = weightUnit,
            canRemove = canRemove,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onRemove = onRemove,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Contenido de [BodyGoalSheet] sin la hoja. «Guardar» solo se activa con una meta fijada por el usuario. «Quitar objetivo» aparece solo
 * si hay un objetivo propio que quitar.
 */
@Composable
fun BodyGoalSheetContent(
    draft: BodyGoalDraft,
    weightUnit: WizardMassUnit,
    canRemove: Boolean,
    onDraftChange: (BodyGoalDraft) -> Unit,
    onSave: (BodyGoal) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = draft.target
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = "Objetivo",
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        KpknSegmentedControl(
            options = GOAL_METRIC_LABELS,
            selectedIndex = GOAL_METRICS.indexOf(draft.metric).coerceAtLeast(0),
            onSelect = { index -> onDraftChange(draft.withMetric(GOAL_METRICS[index])) },
        )
        Spacer(Modifier.height(16.dp))
        when (draft.metric) {
            GoalMetric.WEIGHT -> WizardWeightRule(
                unit = weightUnit,
                valueKg = target,
                onValueChange = { kg -> onDraftChange(draft.copy(target = kg)) },
            )
            GoalMetric.BODY_FAT -> WizardBodyFatRuler(
                percent = target ?: RULE_START_PERCENT,
                declared = target != null,
                onPercentChange = { percent -> onDraftChange(draft.copy(target = percent.toDouble())) },
                modifier = Modifier.fillMaxWidth(),
            )
            GoalMetric.MUSCLE_MASS -> WizardBodyFatRuler(
                percent = target ?: RULE_START_PERCENT,
                declared = target != null,
                onPercentChange = { percent -> onDraftChange(draft.copy(target = percent.toDouble())) },
                modifier = Modifier.fillMaxWidth(),
                range = MUSCLE_RULE_RANGE,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (target == null) {
                "Desliza la regla para fijar tu meta."
            } else {
                "Meta: ${bodyGoalValue(draft.metric, target, weightUnit)}${bodyGoalSuffix(draft.metric, weightUnit)}"
            },
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(16.dp))
        KpknPrimaryPill(
            text = "Guardar",
            onClick = { bodyManualGoalFor(draft, System.currentTimeMillis())?.let(onSave) },
            modifier = Modifier.fillMaxWidth(),
            enabled = target != null,
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            if (canRemove) {
                BodyDangerAction(text = "Quitar objetivo", onClick = onRemove)
            }
        }
    }
}

/**
 * Acción de texto en `danger` (KPKN_UI_V2 §6), para lo que quita algo. Zona táctil de 48 dp y `Role.Button`. Sin estado.
 */
@Composable
internal fun BodyDangerAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = KpknType.action, color = WizardColors.danger)
    }
}

/** Sufijo de la cifra en el texto de la hoja: «%» pegado, y «kg» o «lb» con un espacio. */
private fun bodyGoalSuffix(metric: GoalMetric, unit: WizardMassUnit): String = when (metric) {
    GoalMetric.WEIGHT -> " ${bodyGoalUnit(metric, unit)}"
    GoalMetric.BODY_FAT, GoalMetric.MUSCLE_MASS -> bodyGoalUnit(metric, unit)
}
