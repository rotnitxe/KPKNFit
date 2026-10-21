package com.example.kpkn.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknType

/** Una pestaña de la barra inferior: su ruta, su etiqueta visible y su icono de línea KPKN. */
data class KpknBottomBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Barra inferior de la app (KPKN_UI_V2 §3): pestañas de igual ancho y 64 dp de alto. La isla de vidrio la pone quien la usa.
 * Sin estado: [selectedRoute] marca la pestaña elegida y [onSelect] recibe la ruta de la que se pulsa.
 */
@Composable
fun KpknBottomBar(
    items: List<KpknBottomBarItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(64.dp)) {
        items.forEach { item ->
            KpknBottomBarTab(
                item = item,
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Pestaña: icono de 24 dp, etiqueta a 4 dp y, a 3 dp, un punto de 4 dp que solo se ve en la elegida. La elegida va en tinta; las
 * demás, en `textFaint`. Sin ripple de Material: el cambio de color y el punto son la respuesta.
 */
@Composable
private fun KpknBottomBarTab(
    item: KpknBottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val reduced = wizardReducedMotion()
    // El punto entra con escala de 0 a 1 en 160 ms. Con movimiento reducido cambia de golpe.
    val dot by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduced) 0 else 160),
        label = "kpknBottomBarDot",
    )
    val ink = if (selected) WizardColors.text else WizardColors.textFaint
    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = KpknType.navLabel,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dot
                    scaleY = dot
                }
                .background(SymbolPalette.musculo, CircleShape),
        )
    }
}
