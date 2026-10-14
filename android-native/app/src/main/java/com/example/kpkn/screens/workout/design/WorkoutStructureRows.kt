package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de acción de las hojas de la sesión en vivo: icono KPKN Line en un círculo `inset` de 36 dp, el título en `bodyStrong` y, si
 * hace falta, una línea en `caption`, con la flecha de navegación a la derecha. Mide 56 dp como mínimo y toda la fila es `Role.Button`,
 * sin ripple de Material. [destructive] pinta el icono y el título en `danger`. Deshabilitada, va al 38 %.
 */
@Composable
internal fun WorkoutStructureActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = if (destructive) WizardColors.danger else WizardColors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = KpknType.bodyStrong, color = ink)
            if (subtitle != null) {
                Text(text = subtitle, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            tint = WizardColors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Separador de 1 dp dentro de un módulo (`divider`, blanco al 6 %). */
@Composable
internal fun WorkoutStructureDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

/**
 * Fila de ajuste de 56 dp: el nombre a la izquierda en `bodyStrong` y el control ([control]) a la derecha. Con [subtitle], una línea
 * más en `caption` debajo del nombre. Quien la usa decide si toda la fila responde (p. ej. con `toggleable`) a través de [modifier].
 */
@Composable
internal fun WorkoutStructureSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(text = label, style = KpknType.bodyStrong, color = WizardColors.text)
            if (subtitle != null) {
                Text(text = subtitle, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}
