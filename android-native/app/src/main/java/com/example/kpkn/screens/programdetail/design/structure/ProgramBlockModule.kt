package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.screens.programdetail.design.ProgramWeekCircle
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.util.Locale

/** Primer índice de `kpknEntrance` de los bloques: después de las herramientas (0), el calendario (1) y el aviso (2) de P3a. */
private const val BLOCK_ENTRANCE_FIRST = 3

/**
 * Módulo de un bloque (P3b, KPKN_UI_V2 §1): el eyebrow con la fase en su color (con bandera si es competición), el nombre y «3 semanas ·
 * 12 sesiones»; a la derecha, «Opciones del bloque» si hay [onMore]. Debajo, sus mesociclos como filas (solo si hay más de uno), las
 * semanas en círculos de 40 dp y, si toca, el pie con «Añadir semana» (solo en el último bloque). «Añadir mesociclo» y el resto de acciones
 * van al menú «⋯»; en un programa simple, sin menú, también van al pie. Sin estado: la pantalla decide los menús y las hojas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramBlockModule(
    block: ProgramBlockUi,
    selectedWeekId: String?,
    onFocusWeek: (weekId: String) -> Unit,
    onWeekLongPress: (weekId: String, anchor: Rect) -> Unit,
    onEditMeso: (mesoIndex: Int) -> Unit,
    onAddWeek: (() -> Unit)? = null,
    onAddMeso: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onMore: ((anchor: Rect) -> Unit)? = null,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                block.eyebrow?.let { eyebrow -> BlockEyebrow(text = eyebrow, phase = block.phase) }
                Text(
                    text = block.name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = countsLine(weekCount = block.weeks.size, sessionCount = block.sessionCount),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            if (onMore != null) {
                Spacer(Modifier.width(8.dp))
                BlockMoreButton(onMore = onMore)
            }
        }
        if (block.mesocycles.size > 1) {
            Spacer(Modifier.height(12.dp))
            block.mesocycles.forEachIndexed { index, meso ->
                MesocycleRow(meso = meso, onClick = { onEditMeso(index) })
            }
        }
        Spacer(Modifier.height(12.dp))
        // Círculos de 48 dp de columna sin hueco entre columnas: seis semanas caben en una fila de 288 dp (el ancho del módulo).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.weeks.forEach { week ->
                ProgramWeekCircle(
                    week = week,
                    isSelected = week.id == selectedWeekId,
                    onClick = { onFocusWeek(week.id) },
                    onLongClick = { anchor -> onWeekLongPress(week.id, anchor) },
                )
            }
        }
        block.cycleLabel?.let { cycle ->
            Spacer(Modifier.height(6.dp))
            Text(text = cycle, style = KpknType.caption, color = WizardColors.textMuted)
        }
        // Las acciones de bloque van al menú «⋯»; el pie solo guarda «Añadir semana» en el último bloque. Con el texto al 130 % las
        // acciones pasan a otra línea en vez de cortarse.
        if (onAddWeek != null || onAddMeso != null) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                onAddWeek?.let { KpknTextAction(text = "Añadir semana", onClick = it) }
                onAddMeso?.let { KpknTextAction(text = "Añadir mesociclo", onClick = it) }
            }
        }
    }
}

/**
 * La lista de bloques de la pestaña Estructura (P3b): un módulo por bloque, en el orden del programa, y al final la acción de crear. Un
 * programa simple muestra su bloque sin menú y termina con «Convertir en programa por bloques»; sin bloques, la acción principal es
 * «Añadir el primer bloque». Emite sus módulos en la columna de la pantalla, sin contenedor propio.
 */
@Composable
internal fun ProgramBlockList(
    blocks: List<ProgramBlockUi>,
    isSimple: Boolean,
    selectedWeekId: String?,
    onFocusWeek: (blockId: String, weekId: String) -> Unit,
    onWeekLongPress: (blockId: String, weekId: String, anchor: Rect) -> Unit,
    onBlockMore: (blockId: String, anchor: Rect) -> Unit,
    onEditMeso: (blockId: String, mesoIndex: Int) -> Unit,
    onAddWeekToBlock: (blockId: String) -> Unit,
    onAddMesoToBlock: (blockId: String) -> Unit,
    onAddBlock: () -> Unit,
    onConvert: () -> Unit,
) {
    blocks.forEachIndexed { index, block ->
        val onMoreBlock: (Rect) -> Unit = { anchor -> onBlockMore(block.id, anchor) }
        ProgramBlockModule(
            block = block,
            selectedWeekId = selectedWeekId,
            onFocusWeek = { weekId -> onFocusWeek(block.id, weekId) },
            onWeekLongPress = { weekId, anchor -> onWeekLongPress(block.id, weekId, anchor) },
            onEditMeso = { mesoIndex -> onEditMeso(block.id, mesoIndex) },
            // El menú «⋯» de cada bloque ofrece semana y mesociclo; el pie guarda «Añadir semana» solo en el último bloque.
            onAddWeek = if (index == blocks.lastIndex) ({ onAddWeekToBlock(block.id) }) else null,
            onAddMeso = if (isSimple) ({ onAddMesoToBlock(block.id) }) else null,
            onMore = if (isSimple) null else onMoreBlock,
            modifier = Modifier.kpknEntrance("programdetail.estructura", BLOCK_ENTRANCE_FIRST + index),
        )
    }
    when {
        blocks.isEmpty() -> KpknPrimaryPill(
            text = "Añadir el primer bloque",
            onClick = onAddBlock,
            modifier = Modifier.fillMaxWidth(),
        )
        isSimple -> ProgramWrapSecondaryPill(
            text = "Convertir en programa por bloques",
            onClick = onConvert,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> KpknSecondaryPill(
            text = "Añadir bloque",
            onClick = onAddBlock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** El eyebrow del bloque: la fase en mayúsculas y en su color, con bandera si es competición. */
@Composable
private fun BlockEyebrow(text: String, phase: ProgramPhase?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (phase == ProgramPhase.COMPETITION) {
            Icon(
                imageVector = KpknLineIcons.Flag,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.danger,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = phase?.color ?: WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(4.dp))
}

/** El botón «Opciones del bloque»: 48 dp de zona táctil; entrega dónde está en la ventana, para anclar el menú. */
@Composable
private fun BlockMoreButton(onMore: (Rect) -> Unit) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }) {
        KpknIconButton(
            icon = KpknLineIcons.More,
            contentDescription = "Opciones del bloque",
            onClick = { onMore(bounds) },
        )
    }
}

/** Una fila de mesociclo: nombre y objetivo, con `ChevronRight`. Toda la fila es un botón de 48 dp como mínimo. */
@Composable
private fun MesocycleRow(meso: ProgramMesoUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meso.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meso.goalLabel,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = KpknLineIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = WizardColors.textMuted,
        )
    }
}

/** «3 semanas · 12 sesiones», con la unidad pegada a su cifra. */
private fun countsLine(weekCount: Int, sessionCount: Int): String =
    listOf(
        numberWithUnit(SpanishPlurals.weeks(weekCount)),
        numberWithUnit(SpanishPlurals.sessions(sessionCount)),
    ).joinToString(" · ")

/**
 * Píldora secundaria que puede partirse en dos líneas (P3b): la acción de convertir es larga y, al 130 % de letra, no cabe en una sola.
 * Mismo aspecto que [KpknSecondaryPill] (borde de 1 dp en tinta al 24 %, texto en tinta, sin ripple), con 56 dp de alto como mínimo.
 */
@Composable
private fun ProgramWrapSecondaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(KpknShape.pill)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = WizardTypography.cta,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
        )
    }
}
