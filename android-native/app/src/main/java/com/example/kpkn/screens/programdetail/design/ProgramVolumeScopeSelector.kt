package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Selector del alcance del volumen (P4): flecha anterior, el nombre del alcance en `bodyStrong` con su detalle en `caption`, y flecha
 * siguiente. Las flechas miden 48 dp de zona táctil y se apagan en los extremos: no dan vueltas. Sin estado: [scope] trae si puede
 * retroceder o avanzar, y las lambdas hacen el cambio.
 */
@Composable
fun ProgramVolumeScopeSelector(
    scope: ProgramVolumeScope,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgramVolumeStepButton(
            icon = KpknLineIcons.ChevronLeft,
            contentDescription = "Alcance anterior",
            enabled = scope.canPrevious,
            onClick = onPrevious,
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = scope.label,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (scope.detail.isNotEmpty()) {
                Text(
                    text = scope.detail,
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ProgramVolumeStepButton(
            icon = KpknLineIcons.ChevronRight,
            contentDescription = "Alcance siguiente",
            enabled = scope.canNext,
            onClick = onNext,
        )
    }
}

/** Flecha circular de 40 dp dentro de una zona táctil de 48 dp; apagada y sin acción si [enabled] es falso. */
@Composable
private fun ProgramVolumeStepButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(CircleShape)
                .background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.text,
            )
        }
    }
}
