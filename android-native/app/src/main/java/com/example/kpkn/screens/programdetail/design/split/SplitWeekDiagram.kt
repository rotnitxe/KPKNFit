package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Lado de cada casilla del diagrama. */
private val CellSize = 36.dp

/**
 * Diagrama de la semana del reparto (P5): siete casillas de 36 dp en el orden de la semana del programa, desde [startDay]. Encima, la
 * letra del día en `caption`; dentro, la inicial del patrón en `bodyStrong`, sobre el color de su rol (ver [splitDayAccent]). Un día de
 * descanso es un anillo de `divider` sin texto. Un solo nodo accesible: lee la descripción de la semana, no las siete casillas.
 */
@Composable
internal fun SplitWeekDiagram(
    pattern: List<String>,
    startDay: Int,
    modifier: Modifier = Modifier,
) {
    val description = splitWeekDescription(pattern, startDay)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        for (position in 0 until 7) {
            SplitWeekCell(
                letter = SPLIT_WEEKDAY_LETTERS[splitWeekdayAt(startDay, position) - 1],
                mark = splitDayMark(splitLabelAt(pattern, position)),
            )
        }
    }
}

/** Una casilla del diagrama: la letra del día y, debajo, la casilla con la inicial sobre el color del rol. */
@Composable
private fun SplitWeekCell(letter: String, mark: SplitDayMark) {
    val accent = splitDayAccent(mark.role)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = letter,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        if (accent == null) {
            Box(
                modifier = Modifier
                    .size(CellSize)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = KpknSurface.divider, shape = CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(CellSize)
                    .clip(CircleShape)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mark.initial,
                    style = KpknType.bodyStrong,
                    color = WizardColors.ctaContent,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                )
            }
        }
    }
}
