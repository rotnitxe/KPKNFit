package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Un ciclo activo de la lista (P3c): el icono de su tipo en un círculo `inset`, el nombre y «cada 4 ciclos». Toda la fila es un botón que
 * abre el editor. Un ciclo cancelado va apagado y lo dice en `danger`.
 */
@Composable
internal fun ProgramCycleRow(
    loop: ProgramCycleLoop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (loop.cancelled) WizardColors.textFaint else WizardColors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = loopTypeIcon(loop.type),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ink,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = loop.title,
                style = KpknType.bodyStrong,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (loop.cancelled) "${cycleCadenceLabel(loop.cadence)} · cancelado" else cycleCadenceLabel(loop.cadence),
                style = KpknType.caption,
                color = if (loop.cancelled) WizardColors.danger else WizardColors.textMuted,
            )
        }
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.textFaint,
        )
    }
}

/**
 * Una semana de ciclo próxima (P3c): el nombre del ciclo con el icono, el dónde («Semana 3 · ciclo 5 · jueves»), «Semana especial completa»
 * y las acciones de hoy. «Ver semana» es un texto normal; «Cancelar», en `danger`. Las acciones van en filas que saltan si el texto
 * crece con la letra grande.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCycleWeekRow(
    week: ProgramCycleWeek,
    onView: () -> Unit,
    onPostpone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = loopTypeIcon(week.loopType),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.text,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = week.loopTitle,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = week.countdown,
                style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.textMuted,
            )
        }
        Text(
            text = weekPlaceLine(week),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Text(
            text = "Semana especial completa",
            style = KpknType.caption,
            color = SymbolPalette.musculo,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KpknTextAction(text = "Ver semana", onClick = onView, icon = KpknLineIcons.ChevronRight)
            KpknTextAction(text = "Posponer", onClick = onPostpone)
            ProgramDangerAction(text = "Cancelar", onClick = onCancel)
        }
    }
}

/**
 * «Semana 9 · ciclo 5 (origen 4) · jueves»: dónde cae la semana. La cadencia va en la fila del ciclo; aquí, solo el sitio, para que la
 * línea no se parta en dos.
 */
private fun weekPlaceLine(week: ProgramCycleWeek): String {
    val origin = week.originCycle?.takeIf { it != week.cycle }?.let { " (origen $it)" }.orEmpty()
    return "${week.weekName} · ciclo ${week.cycle}$origin · ${week.dayLabel}"
}
