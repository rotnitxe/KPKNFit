package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.KpknModalTracker
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import dev.chrisbanes.haze.HazeState

/**
 * La isla «Registrar comida» (N2, NUTRITION_V1 §2.8): píldora de vidrio v2 de 56 dp con «+» y el texto en `bodyStrong`. Es el único acceso
 * primario al registro. Abre el registro (lo decide la pantalla, con [onClick]) y conserva el ancla del recorrido. Se esconde mientras hay
 * una hoja o un diálogo abiertos ([KpknModalTracker]); entra con un fundido y 8 dp de subida (220 ms). Con movimiento reducido, solo
 * fundidos de 120 ms. Quien la pone decide dónde cae (sobre la barra inferior).
 */
@Composable
fun NutritionLogIsland(
    hazeState: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    val risePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    AnimatedVisibility(
        visible = KpknModalTracker.openCount == 0,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(animationSpec = tween(durationMillis = 120))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInVertically(animationSpec = tween(durationMillis = 220)) { risePx }
        },
        exit = fadeOut(animationSpec = tween(durationMillis = if (reduced) 120 else 140)),
    ) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .kpknGlass(hazeState = hazeState, shape = KpknShape.pill, variant = KpknGlassVariant.ISLAND)
                .tourAnchor(TourAnchors.NUTRITION_REGISTRO)
                .clickable(role = Role.Button, onClickLabel = "Abrir el registro de comida", onClick = onClick)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = KpknLineIcons.Plus,
                // El texto de al lado ya dice «Registrar comida»: así TalkBack no lo lee dos veces.
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Registrar comida",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
            )
        }
    }
}
