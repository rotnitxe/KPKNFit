package com.example.kpkn.screens.habits.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitSummary
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/** Módulo del calendario anual: cabecera, la cuadrícula, la leyenda del hábito y el recuento de días. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitCalendarModule(
    kind: HabitKind,
    summary: HabitSummary,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Último año")
        Spacer(Modifier.height(14.dp))
        HabitCalendarGrid(kind = kind, cells = summary.calendar)
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            habitLegend(kind).forEach { (level, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HabitDaySwatch(
                        kind = kind,
                        level = level,
                        isToday = false,
                        isFuture = false,
                        size = 12.dp,
                        description = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = habitRecordedCaption(kind, summary.recordedInWindow),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}
