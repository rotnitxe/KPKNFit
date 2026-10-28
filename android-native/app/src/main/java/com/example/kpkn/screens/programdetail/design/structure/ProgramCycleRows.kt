package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Un ciclo de la lista, como fila `.mi` del lienzo (e-cycles): el icono de su tipo, el nombre y «cada 4 ciclos». Toda la fila abre el
 * editor. Un ciclo cancelado lo dice en el detalle.
 */
@Composable
internal fun ProgramCycleRow(
    loop: ProgramCycleLoop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    StructureMenuRow(
        icon = loopTypeIcon(loop.type),
        title = loop.title,
        detail = if (loop.cancelled) "${cycleCadenceLabel(loop.cadence)} · cancelado" else cycleCadenceLabel(loop.cadence),
        divider = divider,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Una semana de ciclo próxima: la fila `.mi` con el nombre del ciclo y dónde cae («Semana 9 · ciclo 5 · jueves · en 3 semanas»), que abre
 * la semana; debajo, «Posponer» y «Cancelar» (este en `--danger`), alineados con el texto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCycleWeekRow(
    week: ProgramCycleWeek,
    onView: () -> Unit,
    onPostpone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StructureMenuRow(
            icon = loopTypeIcon(week.loopType),
            title = week.loopTitle,
            detail = listOf(weekPlaceLine(week), week.countdown).filter { it.isNotBlank() }.joinToString(" · "),
            divider = divider,
            onClick = onView,
        )
        FlowRow(
            modifier = Modifier.padding(start = 54.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StructureAction(text = "Posponer", onClick = onPostpone, faint = true, leadingIcon = KpknLineIcons.Clock)
            StructureAction(text = "Cancelar", onClick = onCancel, color = WizardColors.danger)
        }
    }
}

/** «Semana 9 · ciclo 5 (origen 4) · jueves»: dónde cae la semana. La cadencia va en la fila del ciclo; aquí, solo el sitio. */
private fun weekPlaceLine(week: ProgramCycleWeek): String {
    val origin = week.originCycle?.takeIf { it != week.cycle }?.let { " (origen $it)" }.orEmpty()
    return "${week.weekName} · ciclo ${week.cycle}$origin · ${week.dayLabel}"
}
