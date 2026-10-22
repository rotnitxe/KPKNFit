package com.example.kpkn.screens.habits.design

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.habits.HabitCalendarCell
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknType

private val CELL_SIZE = 12.dp
private val CELL_GAP = 3.dp
private val MONTH_ROW_HEIGHT = 18.dp

/**
 * Calendario anual: una columna por semana (de lunes a domingo, de arriba abajo) y una celda por día. Empieza en las semanas
 * más recientes y se desliza hacia atrás. Cada celda lleva su descripción accesible con la fecha y el registro.
 */
@Composable
fun HabitCalendarGrid(
    kind: HabitKind,
    cells: List<HabitCalendarCell>,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState(Int.MAX_VALUE)
    val weeks = cells.chunked(7)
    Row(modifier = modifier.horizontalScroll(scroll)) {
        weeks.forEach { week ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(CELL_SIZE).height(MONTH_ROW_HEIGHT)) {
                    val firstOfMonth = week.firstOrNull { it.date.dayOfMonth == 1 }
                    if (firstOfMonth != null) {
                        Text(
                            text = habitMonthShort(firstOfMonth.date),
                            style = KpknType.caption.copy(fontSize = 10.sp, lineHeight = 14.sp),
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true).clearAndSetSemantics { },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    week.forEach { cell ->
                        HabitDaySwatch(
                            kind = kind,
                            level = cell.level,
                            isToday = cell.isToday,
                            isFuture = cell.isFuture,
                            size = CELL_SIZE,
                            description = habitDayDescription(kind, cell),
                        )
                    }
                }
            }
            Spacer(Modifier.width(CELL_GAP))
        }
    }
}
