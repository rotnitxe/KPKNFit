package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

/** Un rol que sale en un reparto: su inicial en la casilla, su nombre en la leyenda y su color. */
internal data class SplitLegendEntry(val role: SplitDayRole, val initial: String, val name: String)

/**
 * Leyenda bajo el diagrama de la semana (P7): solo los roles que salen en ese reparto, en el orden en que aparecen, cada uno con su
 * círculo de 12 dp del color de la casilla («T Torso», «Pi Pierna»). El descanso no lleva entrada. Si no caben en una línea, la fila
 * se parte entre elementos y nunca dentro de un nombre.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitRoleLegend(
    pattern: List<String>,
    modifier: Modifier = Modifier,
) {
    val entries = splitLegendEntries(pattern)
    if (entries.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(splitDayAccent(entry.role) ?: WizardColors.text, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${entry.initial} ${entry.name}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Entradas de la leyenda de un patrón: un rol por entrada, sin descanso y en el orden de aparición. Los días «otros» se distinguen por su
 * texto, para que dos rótulos distintos con la misma inicial no se fundan en una sola entrada.
 */
internal fun splitLegendEntries(pattern: List<String>): List<SplitLegendEntry> {
    val entries = LinkedHashMap<String, SplitLegendEntry>()
    for (position in 0 until 7) {
        val label = splitLabelAt(pattern, position)
        val mark = splitDayMark(label)
        if (mark.role == SplitDayRole.REST) continue
        val key = if (mark.role == SplitDayRole.OTHER) canonicalSplitLabel(label) else mark.role.name
        entries.getOrPut(key) { SplitLegendEntry(mark.role, mark.initial, splitRoleName(mark.role, label)) }
    }
    return entries.values.toList()
}

/** Nombre de un rol en la leyenda. Los días «otros» usan su propio rótulo, sin traducirlo. */
private fun splitRoleName(role: SplitDayRole, label: String): String = when (role) {
    SplitDayRole.PUSH -> "Empuje"
    SplitDayRole.PULL -> "Tirón"
    SplitDayRole.LEGS -> "Pierna"
    SplitDayRole.TORSO -> "Torso"
    SplitDayRole.FULL_BODY -> "Cuerpo completo"
    SplitDayRole.OTHER -> label.trim()
    SplitDayRole.REST -> SPLIT_REST_LABEL
}
