package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.programdetail.design.ProgramPhaseWeekUi
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.kpknEntrance

/** Forma de `.blk`: radio 22. */
private val BlockShape = RoundedCornerShape(22.dp)

/**
 * e-blocks, tarjeta `.blk` de un bloque: la franja de 3 dp con el color de su fase a la izquierda, el nombre (Syne 700 16) y
 * «Intensificación · 2 semanas · Top set + back-off» (`.cap`), y el ⋯ del bloque (`.ib`, 44 dp). Debajo, sus semanas en círculos `.wkc`
 * (6 dp de hueco) y el círculo «+» que añade una semana al bloque. Desplegada ([expanded] con [progression]), el filete y «Progresión de
 * este bloque» con la vista previa del motor. Tocar la cabecera la despliega o la pliega.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramBlockModule(
    block: ProgramBlockUi,
    expanded: Boolean,
    progression: String?,
    onToggle: () -> Unit,
    onFocusWeek: (weekId: String) -> Unit,
    onWeekLongPress: (weekId: String, anchor: Rect) -> Unit,
    onAddWeek: () -> Unit,
    onMore: (anchor: Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stripe = structurePhaseColor(block.phase)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(Color(0xFF111111), BlockShape)
            .border(1.dp, Color(0x12FFFFFF), BlockShape)
            .drawBehind { drawRect(color = stripe, topLeft = Offset.Zero, size = Size(3.dp.toPx(), size.height)) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = progression != null,
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .semantics {
                        if (progression != null) stateDescription = if (expanded) "Desplegado" else "Plegado"
                    },
            ) {
                Text(text = block.name, style = StructureBlockNameStyle, color = StructureInk)
                Text(text = blockSummaryLine(block), style = StructureCaptionStyle, color = StructureFaint)
            }
            Spacer(Modifier.width(8.dp))
            BlockMoreButton(onMore = onMore)
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.weeks.forEach { week ->
                BlockWeekCircle(
                    week = week,
                    onClick = { onFocusWeek(week.id) },
                    onLongClick = { anchor -> onWeekLongPress(week.id, anchor) },
                )
            }
            StructureAddWeekCircle(onClick = onAddWeek, contentDescription = "Añadir semana")
        }
        block.cycleLabel?.let { cycle ->
            Spacer(Modifier.height(8.dp))
            StructureCaption(text = cycle)
        }
        if (expanded && progression != null) {
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(StructureDivider))
            StructureCaption(text = "Progresión de este bloque")
            Text(
                text = progression,
                style = StructureBodyStyle,
                color = StructureMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Los bloques de la pestaña Estructura (e-blocks), en el orden del programa, con 10 dp entre tarjetas (`.blk`). [expandedBlockId] es el
 * bloque desplegado; [progressionOf] da la vista previa de cada uno. Emite las tarjetas en la columna de la pantalla, sin contenedor.
 */
@Composable
internal fun ProgramBlockList(
    blocks: List<ProgramBlockUi>,
    expandedBlockId: String?,
    progressionOf: (blockId: String) -> String?,
    onToggleBlock: (blockId: String) -> Unit,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onWeekLongPress: (blockId: String, weekId: String, anchor: Rect) -> Unit,
    onBlockMore: (blockId: String, anchor: Rect) -> Unit,
    onAddWeekToBlock: (blockId: String) -> Unit,
    entranceFirst: Int = 0,
) {
    blocks.forEachIndexed { index, block ->
        ProgramBlockModule(
            block = block,
            expanded = block.id == expandedBlockId,
            progression = progressionOf(block.id),
            onToggle = { onToggleBlock(block.id) },
            onFocusWeek = { weekId -> onFocusWeek(block.id, weekId) },
            onWeekLongPress = { weekId, anchor -> onWeekLongPress(block.id, weekId, anchor) },
            onAddWeek = { onAddWeekToBlock(block.id) },
            onMore = { anchor -> onBlockMore(block.id, anchor) },
            modifier = Modifier
                .padding(bottom = 10.dp)
                .kpknEntrance("programdetail.estructura", entranceFirst + index),
        )
    }
}

/** El ⋯ del bloque (`.ib`): 44 dp, sin relleno, icono en `--muted`; entrega dónde está en la ventana, para anclar el menú. */
@Composable
private fun BlockMoreButton(onMore: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable(role = Role.Button, onClick = { onMore(bounds) })
            .semantics { contentDescription = "Más opciones del bloque" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = KpknLineIcons.More, contentDescription = null, modifier = Modifier.size(20.dp), tint = StructureMuted)
    }
}

/** Un círculo de semana del bloque: tocarlo la abre en Semana; la pulsación larga abre el ⋯ de la semana en su sitio. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockWeekCircle(week: ProgramPhaseWeekUi, onClick: () -> Unit, onLongClick: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val state = when {
        week.isCurrent -> ", en curso"
        week.isDone -> ", hecha"
        else -> ""
    }
    StructureWeekCircle(
        text = "${week.number}",
        current = week.isCurrent,
        done = week.isDone,
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClickLabel = "Opciones de la semana",
                onLongClick = { onLongClick(bounds) },
            )
            .semantics { contentDescription = "Semana ${week.number}$state" },
    )
}
