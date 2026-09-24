package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class WizardMilestoneState { DONE, CURRENT, PENDING }

data class WizardMilestoneItem(
    val block: WizardBlock,
    val title: String,
    val body: String,
    val state: WizardMilestoneState,
)

/**
 * Pantalla de hitos entre bloques: lista vertical de etapas con checks, como en
 * `Workouts/p1.jpg` y `Food/Screenshot_20260920_113434.jpg`.
 *
 * Marca como completado únicamente lo que ya quedó **revisado**; una etapa
 * disponible pero sin revisar aparece como actual, nunca como completada.
 */
@Composable
fun WizardMilestones(
    items: List<WizardMilestoneItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${item.title}: ${item.state.name}" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WizardMilestoneMarker(item)
                    if (index != items.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(
                                    if (item.state == WizardMilestoneState.DONE) item.block.accent
                                    else WizardColors.cardBorder,
                                ),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index == items.lastIndex) 0.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = WizardTypography.milestoneTitle,
                        color = when (item.state) {
                            WizardMilestoneState.PENDING -> WizardColors.textFaint
                            else -> WizardColors.text
                        },
                    )
                    Text(
                        text = item.body,
                        style = WizardTypography.milestoneBody,
                        color = when (item.state) {
                            WizardMilestoneState.DONE -> WizardColors.textMuted
                            WizardMilestoneState.CURRENT -> WizardColors.textMuted
                            WizardMilestoneState.PENDING -> WizardColors.textFaint
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardMilestoneMarker(item: WizardMilestoneItem) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (item.state) {
            WizardMilestoneState.DONE -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(item.block.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = WizardColors.ctaContent,
                    modifier = Modifier.size(16.dp),
                )
            }

            WizardMilestoneState.CURRENT -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WizardColors.background)
                    .border(2.dp, item.block.accent, CircleShape),
            )

            WizardMilestoneState.PENDING -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(WizardColors.background)
                    .border(1.5.dp, WizardColors.cardBorder, CircleShape),
            )
        }
    }
}
