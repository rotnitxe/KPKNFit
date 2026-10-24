package com.example.kpkn.screens.habits.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitSummary
import com.example.kpkn.domain.habits.HabitUnit
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/**
 * Módulo de la racha: la actual y la mejor, con su unidad. Sin dato (carga pendiente) muestra «—», nunca un cero.
 */
@Composable
fun HabitStreakModule(
    kind: HabitKind,
    summary: HabitSummary?,
    modifier: Modifier = Modifier,
) {
    val streak = summary?.streak
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Racha")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            StreakFigure(
                label = "Actual",
                count = streak?.current,
                unit = streak?.unit,
                modifier = Modifier.weight(1f),
            )
            StreakFigure(
                label = "Mejor",
                count = streak?.best,
                unit = streak?.unit,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = habitStreakHint(kind),
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}

/** Una cifra de la racha con su unidad en la misma línea base; se lee como una sola frase. */
@Composable
private fun StreakFigure(
    label: String,
    count: Int?,
    unit: HabitUnit?,
    modifier: Modifier = Modifier,
) {
    val word = if (count != null && unit != null) habitUnitWord(unit, count) else null
    val spoken = if (count != null && word != null) "$label: $count $word" else "$label: sin datos"
    Column(modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Text(label.uppercase(), style = KpknType.eyebrow, color = WizardColors.textMuted)
        KpknMetric(
            value = count?.toString() ?: "—",
            unit = word,
            style = KpknType.metricL,
        )
    }
}
