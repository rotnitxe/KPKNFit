package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

/**
 * Fila de un ejercicio en la ruta (KPKN LS4, §4): 64 dp. A la izquierda, el estado: check si está hecho, punto `musculo` si es el actual,
 * anillo `divider` si está pendiente, o el círculo de selección en modo dios. Después, el nombre en una línea y «2 de 4 series» debajo.
 * A la derecha, el asa [handleGesture] (48 dp), que solo se pinta con [showHandle]. Tocar [onTap] y la pulsación larga [onLongPress]
 * van en la parte de texto: el asa no abre la fila. Sin estado: la selección y el gesto los lleva quien la usa.
 *
 * La superserie (la barra de 3 dp y su rótulo) la pinta el bloque, no la fila.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkoutRouteRow(
    member: WorkoutRouteMember,
    selecting: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    showHandle: Boolean,
    handleGesture: Modifier,
    modifier: Modifier = Modifier,
) {
    val stateText = when {
        selecting -> if (selected) "Elegido" else "Sin elegir"
        member.isDone -> "Hecho"
        member.isCurrent -> "Ejercicio actual"
        else -> "Pendiente"
    }
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .combinedClickable(role = Role.Button, onClick = onTap, onLongClick = onLongPress)
                .semantics { stateDescription = stateText },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(start = 12.dp).size(24.dp), contentAlignment = Alignment.Center) {
                RouteStateMark(member = member, selecting = selecting, selected = selected)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    text = member.name,
                    style = KpknType.bodyStrong,
                    color = if (member.isDone && !selecting) WizardColors.textMuted else WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = workoutRouteSeriesLabel(member.setsDone, member.setsTotal),
                    style = KpknType.caption.copy(fontFeatureSettings = "tnum"),
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showHandle) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(handleGesture)
                    .semantics { contentDescription = "Mantén pulsado para reordenar" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Grip,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** La marca del estado de la fila (24 dp): el círculo de selección del catálogo en modo dios, o el estado de la serie. */
@Composable
private fun RouteStateMark(member: WorkoutRouteMember, selecting: Boolean, selected: Boolean) {
    when {
        selecting -> Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) WizardColors.text else Color.Transparent, CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) Color.Transparent else WizardColors.textFaint,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = WizardColors.ctaContent,
                )
            }
        }
        member.isDone -> Icon(
            imageVector = KpknLineIcons.Check,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.text,
        )
        member.isCurrent -> Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(SymbolPalette.musculo, CircleShape),
        )
        else -> Box(
            modifier = Modifier.size(16.dp).border(width = 1.5.dp, color = WizardColors.textFaint, shape = CircleShape),
        )
    }
}
