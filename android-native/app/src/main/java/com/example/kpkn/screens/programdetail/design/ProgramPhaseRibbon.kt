package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/** Una semana de la cinta: el color de su fase, si ya está hecha y si es la actual. */
internal data class ProgramRibbonWeek(val color: Color, val done: Boolean, val current: Boolean)

/** Filete crema por fuera del anillo de la semana actual (`#F2EEE6` al 67 %). */
private val RibbonRingCream = Color(0xAAF2EEE6)

/**
 * La cinta de fases bajo el nombre: un segmento por semana, iguales y con 3 dp de hueco, del color de su fase. Las semanas hechas van
 * más intensas; la actual, más alta y con un anillo. Con [onSelectWeek], cada segmento se toca y devuelve su índice.
 */
@Composable
internal fun ProgramPhaseRibbon(
    weeks: List<ProgramRibbonWeek>,
    modifier: Modifier = Modifier,
    onSelectWeek: ((Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeks.forEachIndexed { index, week ->
            RibbonSegment(week = week, index = index, count = weeks.size, onSelectWeek = onSelectWeek)
        }
    }
}

/**
 * El color de una fase en la cinta: los `--ph-*` del prototipo. Sin fase conocida, `textFaint`. No es [ProgramPhase.color], que usa
 * otra paleta para descarga y realización.
 */
internal fun programPhaseColor(phase: ProgramPhase?): Color = when (phase) {
    ProgramPhase.ACCUMULATION -> Color(0xFFF49A6E)
    ProgramPhase.INTENSIFICATION -> Color(0xFFF7CF73)
    ProgramPhase.REALIZATION -> Color(0xFF8FB2FF)
    ProgramPhase.DELOAD -> Color(0xFF7FCFA4)
    ProgramPhase.COMPETITION -> Color(0xFFFF9B92)
    null -> WizardColors.textFaint
}

/** Un segmento: 6 dp, o 10 dp si es la actual. Con [onSelectWeek], su zona táctil mide 24 dp de alto como mínimo. */
@Composable
private fun RowScope.RibbonSegment(week: ProgramRibbonWeek, index: Int, count: Int, onSelectWeek: ((Int) -> Unit)?) {
    val label = "Semana ${index + 1} de $count"
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(3.dp)
    val fillAlpha = when {
        week.current -> 1f
        week.done -> 0.85f
        else -> 0.28f
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .then(
                if (onSelectWeek != null) {
                    Modifier
                        .heightIn(min = 24.dp)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClick = { onSelectWeek?.invoke(index) },
                        )
                } else {
                    Modifier
                },
            )
            .semantics { if (week.current || onSelectWeek != null) contentDescription = label },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (week.current) 10.dp else 6.dp)
                .drawBehind { if (week.current) drawCurrentRing() }
                .clip(shape)
                .background(week.color.copy(alpha = fillAlpha), shape),
        )
    }
}

/**
 * El anillo de la semana actual: 2 dp en negro y, por fuera, 1 dp crema. Se dibuja fuera del segmento para no mover el diseño.
 */
private fun DrawScope.drawCurrentRing() {
    val black = 2.dp.toPx()
    val cream = 3.dp.toPx()
    val radius = 3.dp.toPx()
    drawRoundRect(
        color = RibbonRingCream,
        topLeft = Offset(-cream, -cream),
        size = Size(size.width + 2 * cream, size.height + 2 * cream),
        cornerRadius = CornerRadius(radius + cream),
    )
    drawRoundRect(
        color = Color.Black,
        topLeft = Offset(-black, -black),
        size = Size(size.width + 2 * black, size.height + 2 * black),
        cornerRadius = CornerRadius(radius + black),
    )
}
