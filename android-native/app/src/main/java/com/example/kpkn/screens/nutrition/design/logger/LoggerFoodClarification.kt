package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.screens.nutrition.components.FoodClarificationPrompt
import com.example.kpkn.screens.nutrition.components.portionPresetLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknType

/**
 * Aclaración pendiente de un alimento (N3b): la pregunta en `body`, su detalle en `caption` y las respuestas en `KpknChip`. Cada respuesta
 * llama a su acción de [actions]. El chip de [FoodClarificationPrompt.reviewAmountLabel] abre los gramos ([onReviewAmount]) y el de
 * [FoodClarificationPrompt.pickOtherLabel] abre la corrección ([onPickOther]). [unsureEnabled] apaga la respuesta de «no estoy seguro»
 * cuando no hay una estimación que aceptar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoggerFoodClarification(
    question: ClarificationRequest,
    prompt: FoodClarificationPrompt,
    dryVsCooked: Boolean,
    unsureEnabled: Boolean,
    onReviewAmount: () -> Unit,
    onPickOther: () -> Unit,
    actions: LoggerFoodActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = prompt.title, style = KpknType.body, color = WizardColors.text)
        prompt.detail?.let { detail ->
            Text(text = detail, style = KpknType.caption, color = WizardColors.textMuted)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            prompt.reviewAmountLabel?.let { label ->
                KpknChip(text = label, selected = false, onClick = onReviewAmount)
            }
            when (question) {
                is ClarificationRequest.Identity -> {
                    question.candidateIds.zip(question.candidateLabels).take(4).forEach { (id, label) ->
                        KpknChip(text = label, selected = false, onClick = { actions.onIdentityChoice(id) })
                    }
                    if (question.requestId == "tortilla_composition") {
                        listOf("maíz", "huevo", "papas").forEach { composition ->
                            KpknChip(
                                text = "De $composition",
                                selected = false,
                                onClick = { actions.onDeclaredComposition(composition) },
                            )
                        }
                    }
                    prompt.pickOtherLabel?.let { label ->
                        KpknChip(text = label, selected = false, onClick = onPickOther)
                    }
                }
                is ClarificationRequest.WeightState -> {
                    KpknChip(
                        text = if (dryVsCooked) "Seco" else "Crudo",
                        selected = false,
                        onClick = { actions.onCookingChange(false) },
                    )
                    KpknChip(text = "Cocido", selected = false, onClick = { actions.onCookingChange(true) })
                }
                is ClarificationRequest.Oil -> {
                    question.options.forEach { option ->
                        KpknChip(
                            text = option.replaceFirstChar { it.uppercase() },
                            selected = false,
                            onClick = { actions.onOilLevelChange(option) },
                        )
                    }
                }
                is ClarificationRequest.Portion -> {
                    if (question.options.isNotEmpty()) {
                        question.options.forEach { option ->
                            KpknChip(text = option.label, selected = false, onClick = { actions.onPortionOption(option) })
                        }
                    } else {
                        PortionPreset.entries.take(3).forEach { preset ->
                            KpknChip(
                                text = portionPresetLabel(preset),
                                selected = false,
                                onClick = { actions.onPortionChange(preset) },
                            )
                        }
                    }
                }
            }
            KpknChip(
                text = prompt.unsureLabel,
                selected = false,
                enabled = unsureEnabled,
                onClick = actions.onUnsure,
            )
        }
    }
}
