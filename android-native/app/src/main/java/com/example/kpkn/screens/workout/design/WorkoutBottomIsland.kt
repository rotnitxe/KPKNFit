package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknType

/**
 * Isla inferior de la sesión en vivo (LS2a, §2 punto 4), vidrio v2 de tipo isla. Sustituye al dock compacto del roadmap. A la izquierda,
 * «Ejercicio anterior» (si [previousEnabled]); en el centro, «Sigue: [nextLabel]» en `bodyStrong`, en una línea, que abre la ruta de la
 * sesión con [onOpenRoute]; sin [nextLabel] dice «Último ejercicio»; a la derecha, «Ejercicio siguiente» (si hay [nextLabel]).
 *
 * El ancla del recorrido `TourAnchors.WORKOUT_SIGUIENTE` la pone quien la monta, en [modifier]. Sin estado.
 */
@Composable
fun WorkoutBottomIsland(
    previousEnabled: Boolean,
    nextLabel: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .kpknGlassOrFallback(hazeState, KpknShape.island, variant = KpknGlassVariant.ISLAND)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (previousEnabled) {
            KpknIconButton(
                icon = KpknLineIcons.ChevronLeft,
                contentDescription = "Ejercicio anterior",
                onClick = onPrevious,
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Abrir la ruta de la sesión",
                    onClick = onOpenRoute,
                )
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (nextLabel != null) "Sigue: $nextLabel" else "Último ejercicio",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (nextLabel != null) {
            KpknIconButton(
                icon = KpknLineIcons.ChevronRight,
                contentDescription = "Ejercicio siguiente",
                onClick = onNext,
            )
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
