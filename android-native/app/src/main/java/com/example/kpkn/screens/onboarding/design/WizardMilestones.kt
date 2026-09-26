package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class WizardMilestoneState { DONE, CURRENT, PENDING }

data class WizardMilestoneItem(
    val block: WizardBlock,
    val title: String,
    val body: String,
    val state: WizardMilestoneState,
)

/**
 * Pantalla de hitos (transición entre bloques), como `Workouts/p1.jpg` y
 * `Food/Screenshot_20260920_113434.jpg`: héroe grande ("GET STARTED" + subtítulo)
 * y etapas numeradas — el círculo blanco marca la actual, los checks las ya
 * revisadas y los círculos grises las futuras. Los conectores son neutros.
 *
 * Solo la etapa **actual** muestra su párrafo; las pasadas y futuras quedan
 * resumidas a número y título, como en las referencias.
 *
 * Marca como completado únicamente lo que ya quedó **revisado**; una etapa
 * disponible pero sin revisar aparece como actual, nunca como completada.
 */
@Composable
fun WizardMilestones(
    items: List<WizardMilestoneItem>,
    modifier: Modifier = Modifier,
    heroTitle: String? = null,
    heroSubtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (heroTitle != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WizardSpacing.sectionGap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = heroTitle,
                    style = WizardTypography.heroTitle,
                    color = WizardColors.text,
                    textAlign = TextAlign.Center,
                )
                if (heroSubtitle != null) {
                    Text(
                        text = heroSubtitle,
                        style = WizardTypography.heroSubtitle,
                        color = WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        items.forEachIndexed { index, item ->
            val showBody = item.state == WizardMilestoneState.CURRENT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "${index + 1}. ${item.title}: ${item.state.name}" },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WizardMilestoneMarker(number = index + 1, item = item)
                    if (index != items.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(if (showBody) 30.dp else 24.dp)
                                .background(WizardColors.cardBorder),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index == items.lastIndex) 0.dp else if (showBody) 18.dp else 12.dp),
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
                    if (showBody) {
                        Text(
                            text = item.body,
                            style = WizardTypography.milestoneBody,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Círculo numerado de la referencia: blanco relleno con check (pasado) o con el
 * número (actual), gris con número tenue para las etapas futuras y sin acento de
 * bloque.
 */
@Composable
private fun WizardMilestoneMarker(number: Int, item: WizardMilestoneItem) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (item.state) {
            WizardMilestoneState.DONE -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WizardColors.selectedBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = WizardColors.ctaContent,
                    modifier = Modifier.size(15.dp),
                )
            }

            WizardMilestoneState.CURRENT -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WizardColors.selectedBorder),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = WizardTypography.milestoneTitle,
                    color = WizardColors.ctaContent,
                )
            }

            WizardMilestoneState.PENDING -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WizardColors.cardBorder),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = number.toString(),
                    style = WizardTypography.milestoneTitle,
                    color = WizardColors.textFaint,
                )
            }
        }
    }
}
