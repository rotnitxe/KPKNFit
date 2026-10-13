package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Navegador de sesiones (SESSION_EDITOR_V1 §2, punto 2): «Lun · Torso A» con `ChevronDown` en una píldora de 32 dp con el aspecto de
 * `KpknChip`. Es un botón que abre la hoja, no un filtro que se marca, así que no usa `KpknChip` (que expone `Role.Checkbox`). Su zona
 * táctil mide 48 dp.
 */
@Composable
fun SessionNavigatorChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Cambiar de sesión", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionPillLabel(label = label, chevron = true)
    }
}

/** Píldora de 32 dp con el aspecto de `KpknChip` (relleno `inset`, filete blanco al 8 %, texto en tinta) y chevron opcional. Sin estado. */
@Composable
internal fun SessionPillLabel(
    label: String,
    chevron: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.08f), KpknShape.pill)
            .padding(start = 14.dp, end = if (chevron) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KpknType.chip,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = KpknLineIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.text,
            )
        }
    }
}
