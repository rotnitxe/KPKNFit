package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Qué tipo de programa es, para elegir sus herramientas: por bloques, simple cíclico o simple calendarizado. */
internal enum class ProgramToolsKind(val label: String) {
    BLOCKS("Por bloques"),
    CYCLIC("Cíclico"),
    CALENDARIZED("Calendarizado"),
}

/**
 * Una baldosa de la rejilla de Herramientas: icono, etiqueta, cifra opcional y acción. Sin [enabled] va apagada y no se puede tocar.
 * [contentDescription] es lo que lee TalkBack; [tag], la etiqueta de prueba.
 */
internal data class ProgramTool(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val count: Int? = null,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val tag: String? = null,
)

/**
 * Módulo «Herramientas» de la pestaña Estructura (P3a, sustituye a la barra `MacrocycleToolbar`): el eyebrow, el tipo del programa a la
 * derecha, una línea de cifras y una rejilla de baldosas. Cada tipo de programa muestra solo las suyas. No guarda estado: las acciones
 * llegan como lambdas.
 */
@Composable
internal fun ProgramToolsModule(
    kind: ProgramToolsKind,
    blockCount: Int,
    weekCount: Int,
    sessionCount: Int,
    keyDateCount: Int,
    historyCount: Int,
    onCalendar: () -> Unit,
    onKeyDates: () -> Unit,
    onRoute: () -> Unit,
    onCycles: () -> Unit,
    onTemplates: () -> Unit,
    onHistory: () -> Unit,
    onCalendarize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = when (kind) {
        ProgramToolsKind.BLOCKS -> listOf(
            ProgramTool(label = "Calendario", icon = KpknLineIcons.Calendar, onClick = onCalendar),
            keyDatesTool(keyDateCount, onKeyDates),
            ProgramTool(label = "Ruta", icon = KpknLineIcons.Route, onClick = onRoute),
            ProgramTool(label = "Plantillas", icon = KpknLineIcons.Layers, onClick = onTemplates),
            historyTool(historyCount, onHistory),
        )
        ProgramToolsKind.CYCLIC -> listOf(
            ProgramTool(label = "Calendarizar", icon = KpknLineIcons.Calendar, onClick = onCalendarize),
            ProgramTool(label = "Ciclos", icon = KpknLineIcons.Repeat, onClick = onCycles),
            ProgramTool(label = "Plantillas", icon = KpknLineIcons.Layers, onClick = onTemplates),
            historyTool(historyCount, onHistory),
        )
        ProgramToolsKind.CALENDARIZED -> listOf(
            ProgramTool(label = "Calendario", icon = KpknLineIcons.Calendar, onClick = onCalendarize),
            ProgramTool(label = "Plantillas", icon = KpknLineIcons.Layers, onClick = onTemplates),
            historyTool(historyCount, onHistory),
        )
    }
    KpknModule(modifier = modifier) {
        KpknModuleHeader(
            label = "Herramientas",
            action = { Text(text = kind.label, style = KpknType.caption, color = WizardColors.textMuted) },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = statsLine(kind, blockCount, weekCount, sessionCount),
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        ProgramToolGrid(tools = tools)
    }
}

/** «Fechas clave» con la cifra de las que no son la competición (la competición se edita en el calendario). */
private fun keyDatesTool(count: Int, onClick: () -> Unit): ProgramTool = ProgramTool(
    label = "Fechas clave",
    icon = KpknLineIcons.Flag,
    onClick = onClick,
    count = count.takeIf { it > 0 },
    contentDescription = if (count > 0) "Fechas clave, $count" else "Fechas clave",
)

/** «Historial»: apagado sin copias; con copias, la cifra es el número. Conserva la etiqueta de prueba del botón anterior. */
private fun historyTool(copies: Int, onClick: () -> Unit): ProgramTool = ProgramTool(
    label = "Historial",
    icon = KpknLineIcons.History,
    onClick = onClick,
    count = copies.takeIf { it > 0 },
    enabled = copies > 0,
    contentDescription = when (copies) {
        0 -> "Historial, sin copias"
        1 -> "Historial, 1 copia"
        else -> "Historial, $copies copias"
    },
    tag = "macrocycle_history_button",
)

/** «4 bloques · 11 semanas · 44 sesiones»; sin bloques en un programa simple, que es una sola cadena de semanas. */
private fun statsLine(kind: ProgramToolsKind, blocks: Int, weeks: Int, sessions: Int): String =
    listOfNotNull(
        blocks.takeIf { kind == ProgramToolsKind.BLOCKS }?.let { numberWithUnit(SpanishPlurals.blocks(it)) },
        numberWithUnit(SpanishPlurals.weeks(weeks)),
        numberWithUnit(SpanishPlurals.sessions(sessions)),
    ).joinToString(" · ")

/** Separación entre baldosas y entre filas: 8 dp. */
private val TileGap = 8.dp

/** Margen interior de cada baldosa. */
private val TilePadding = 10.dp

/** Alto mínimo de cada baldosa (76 dp). Crece si la etiqueta necesita más líneas, por ejemplo al 130 % de letra. */
private val TileMinHeight = 76.dp

private val TileShape = RoundedCornerShape(16.dp)

/** Cómo se escribe la etiqueta: una línea en `bodyStrong` si todas caben; si no, `caption` en dos líneas como mucho, partidas entre palabras. */
private data class ToolLabelType(val style: TextStyle, val maxLines: Int)

/**
 * Rejilla de baldosas. Tres columnas solo si cada palabra cabe entera en una baldosa de tres a la letra de `caption`; si no, dos columnas.
 * Así ninguna palabra se parte a media (a 130 % de letra, «Calendario» no cabe en tres y pasaría a dos). Con cuatro baldosas, siempre dos.
 */
@Composable
private fun ProgramToolGrid(tools: List<ProgramTool>) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val threeInnerPx = with(density) { ((maxWidth - TileGap * 2) / 3 - TilePadding * 2).toPx() }
        val words = tools.flatMap { tool -> tool.label.split(' ') }
        val columns = when {
            tools.size == 4 -> 2
            words.all { word -> fitsOneLine(measurer, word, KpknType.caption, threeInnerPx) } -> 3
            else -> 2
        }
        val innerPx = with(density) { ((maxWidth - TileGap * (columns - 1)) / columns - TilePadding * 2).toPx() }
        val labelType = remember(tools, innerPx) { toolLabelType(measurer, tools.map { it.label }, innerPx) }
        Column(verticalArrangement = Arrangement.spacedBy(TileGap)) {
            tools.chunked(columns).forEach { row ->
                // Las baldosas de una fila comparten alto: la más alta marca la fila.
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    row.forEach { tool ->
                        ProgramToolTile(tool = tool, labelType = labelType, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Si el texto cabe en una sola línea dentro de [availablePx] con ese estilo. */
private fun fitsOneLine(measurer: TextMeasurer, text: String, style: TextStyle, availablePx: Float): Boolean =
    measurer.measure(AnnotatedString(text), style, maxLines = 1, softWrap = false).size.width <= availablePx

/** Una línea en `bodyStrong` si todas las etiquetas caben en [availablePx]; si no, `caption` en dos líneas como mucho. */
private fun toolLabelType(measurer: TextMeasurer, labels: List<String>, availablePx: Float): ToolLabelType =
    if (labels.all { fitsOneLine(measurer, it, KpknType.bodyStrong, availablePx) }) {
        ToolLabelType(KpknType.bodyStrong, maxLines = 1)
    } else {
        ToolLabelType(KpknType.caption.copy(lineHeight = 16.sp), maxLines = 2)
    }

/**
 * Baldosa: superficie `inset` de 16 dp de radio y 76 dp de alto mínimo. Arriba, el icono a la izquierda y la cifra a la derecha; abajo,
 * la etiqueta. El icono y la etiqueta van en filas distintas, así que una etiqueta de dos líneas nunca se pisa con el icono. Toda la
 * baldosa es un botón (`Role.Button`) con su descripción; apagada, no se toca y el texto va en `textFaint`.
 */
@Composable
private fun ProgramToolTile(tool: ProgramTool, labelType: ToolLabelType, modifier: Modifier = Modifier) {
    val ink = if (tool.enabled) WizardColors.text else WizardColors.textFaint
    Column(
        modifier = modifier
            .heightIn(min = TileMinHeight)
            .background(KpknSurface.inset, TileShape)
            .then(if (tool.tag != null) Modifier.testTag(tool.tag) else Modifier)
            .then(if (tool.enabled) Modifier.clickable(role = Role.Button, onClick = tool.onClick) else Modifier)
            .semantics {
                contentDescription = tool.contentDescription
                if (!tool.enabled) disabled()
            }
            .padding(TilePadding),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = ink,
            )
            Spacer(Modifier.weight(1f))
            tool.count?.let { count ->
                Text(
                    text = count.toString(),
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.textMuted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(6.dp))
        Text(
            text = tool.label,
            style = labelType.style,
            color = ink,
            maxLines = labelType.maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
