package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.PlanWarning
import com.example.kpkn.domain.nutrition.RiskSeverity

/**
 * Orden fijo en que se reservan los avisos en pantalla: lo más grave primero. Cada aviso tiene SU hueco
 * (aparece y desaparece solo, con animación) para que un aviso nuevo no mueva los demás.
 */
val WizardPlanWarningOrder: List<PlanWarning> = listOf(
    PlanWarning.LOW_CALORIES_HARD,
    PlanWarning.PACE_EXTREME,
    PlanWarning.PACE_AGGRESSIVE,
    PlanWarning.LOW_CALORIES_SOFT,
    PlanWarning.LOW_PROTEIN,
    PlanWarning.LOW_FAT,
)

/** Alto que se reserva para el primer aviso (una píldora de una línea): así el primero no empuja los deslizadores. */
private val WarningSlotHeight = 36.dp

/**
 * Píldora de aviso: icono y como mucho seis palabras, con el color de su gravedad (ámbar al avisar, rojo
 * al ser peligroso). Sin párrafos ni resplandores.
 */
@Composable
fun WizardPlanWarningChip(
    warning: PlanWarning,
    text: String,
    modifier: Modifier = Modifier,
) {
    val color = WizardNutritionPalette.severity(warning.severity)
    val icon = if (warning.severity == RiskSeverity.DANGER) Icons.Filled.Error else Icons.Filled.Warning
    Row(
        modifier = modifier
            .testTag("setup-nutrition-warning")
            .clip(WizardShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .clearAndSetSemantics { contentDescription = "Aviso: $text" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = WizardTypography.note.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 2,
        )
    }
}

/**
 * Avisos vigentes del plan, cada uno en su hueco con una pequeña entrada animada. [loss] decide el texto de
 * los avisos de ritmo (no se explica igual perder muy rápido que ganar muy rápido).
 */
@Composable
fun WizardPlanWarnings(
    warnings: List<PlanWarning>,
    loss: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WarningSlotHeight)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WizardPlanWarningOrder.forEach { warning ->
            key(warning) {
                AnimatedVisibility(
                    visible = warning in warnings,
                    enter = if (reducedMotion) {
                        EnterTransition.None
                    } else {
                        fadeIn(tween(durationMillis = 220)) + expandVertically(tween(durationMillis = 260))
                    },
                    exit = if (reducedMotion) {
                        ExitTransition.None
                    } else {
                        fadeOut(tween(durationMillis = 150)) + shrinkVertically(tween(durationMillis = 200))
                    },
                ) {
                    WizardPlanWarningChip(warning = warning, text = planWarningText(warning, loss))
                }
            }
        }
    }
}
