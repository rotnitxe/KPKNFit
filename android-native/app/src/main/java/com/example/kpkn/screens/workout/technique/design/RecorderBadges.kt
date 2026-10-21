package com.example.kpkn.screens.workout.technique.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Insignia de 60 fps (VT8a).
 * Se muestra en la barra superior solo si el nivel de cadencia concedido por la cámara es 60 fps.
 */
@Composable
fun FpsBadge(
    fps: Int = 60,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = KpknShape.pill,
        color = KpknSurface.inset,
        border = BorderStroke(1.dp, WizardColors.text.copy(alpha = 0.16f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                modifier = Modifier.size(6.dp),
                shape = KpknShape.pill,
                color = WizardColors.done,
            ) {}
            Text(
                text = "$fps FPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WizardColors.text.copy(alpha = 0.92f),
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/**
 * Insignia y selector del diámetro del disco de calibración (VT8a).
 * Muestra "Disco 45 cm" (o el seleccionado) y despliega los chips 45/40/35 al tocarla.
 */
@Composable
fun PlateDiameterBadge(
    diameterMm: Int?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectDiameterMm: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    onCustomSelected: () -> Unit = {},
) {
    val reducedMotion = wizardReducedMotion()
    val label = when (diameterMm) {
        450 -> "Disco 45 cm"
        400 -> "Disco 40 cm"
        350 -> "Disco 35 cm"
        null -> "Disco sin calibrar"
        else -> "Disco ${diameterMm / 10} cm"
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onToggleExpanded),
            shape = KpknShape.pill,
            color = if (expanded) KpknSurface.modulePressed else KpknSurface.inset,
            border = BorderStroke(1.dp, WizardColors.text.copy(alpha = if (expanded) 0.3f else 0.16f)),
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WizardColors.text.copy(alpha = 0.90f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (reducedMotion) fadeIn() else fadeIn() + expandVertically(),
            exit = if (reducedMotion) fadeOut() else fadeOut() + shrinkVertically(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiameterChip(
                    text = "45 cm",
                    selected = diameterMm == 450,
                    onClick = {
                        onSelectDiameterMm(450)
                        onToggleExpanded()
                    },
                )
                DiameterChip(
                    text = "40 cm",
                    selected = diameterMm == 400,
                    onClick = {
                        onSelectDiameterMm(400)
                        onToggleExpanded()
                    },
                )
                DiameterChip(
                    text = "35 cm",
                    selected = diameterMm == 350,
                    onClick = {
                        onSelectDiameterMm(350)
                        onToggleExpanded()
                    },
                )
            }
        }
    }
}

@Composable
private fun DiameterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.defaultMinSize(minHeight = 48.dp, minWidth = 48.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) WizardColors.text.copy(alpha = 0.22f) else KpknSurface.inset,
        border = BorderStroke(1.dp, if (selected) WizardColors.text.copy(alpha = 0.40f) else WizardColors.text.copy(alpha = 0.12f)),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) WizardColors.text else WizardColors.text.copy(alpha = 0.72f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
