package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType

/** Ancho de cada lado de la fila superior: el mismo a los dos lados, para que el reloj quede centrado aunque la voz esté activa. */
private val TopRowSideWidth = 104.dp

/**
 * Fila superior de la sesión en vivo (LS2a, §2 punto 1): sobre el negro y sin isla. A la izquierda, «Minimizar», que vuelve a la app y
 * deja la sesión en curso. En el centro, [center]: el cronómetro con su ritmo. A la derecha, el punto `ok` con «Voz» si la voz está
 * activa, y «Más opciones», que entrega su posición en ventana por [onMore] para que quien lo abre ancle el menú.
 *
 * Los dos lados tienen el mismo ancho fijo, así que el centro es el centro de la pantalla. El centro va como hueco ([center]) porque el
 * cronómetro guarda su propio reloj y su diálogo de tiempo. Sin estado de negocio.
 */
@Composable
fun WorkoutTopRow(
    onMinimize: () -> Unit,
    onMore: (Rect) -> Unit,
    voiceActive: Boolean,
    center: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = modifier
            .zIndex(1f)
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.width(TopRowSideWidth), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(
                icon = KpknLineIcons.ChevronDown,
                contentDescription = "Minimizar",
                onClick = onMinimize,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            center()
        }
        Row(
            modifier = Modifier.width(TopRowSideWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            if (voiceActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Control por voz activo" },
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SymbolPalette.ok, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Voz", style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                }
                Spacer(Modifier.width(4.dp))
            }
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Más opciones de la sesión",
                onClick = { onMore(moreBounds) },
                modifier = Modifier.onGloballyPositioned { moreBounds = it.boundsInWindow() },
            )
        }
    }
}
