package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.components.oneSeriesVolumeContributions
import com.example.kpkn.screens.sessioneditor.components.roleVolumeLabel
import com.example.kpkn.ui.design.KpknType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

internal fun catalogEffectiveSetsLabel(value: Double): String {
    val amount = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString().replace('.', ',')
    return "$amount ${if (value == 1.0) "serie" else "series"}"
}

/** Reutiliza el mismo cálculo de EMI y sus aportes explícitos; no convierte el aporte en un porcentaje. */
@Composable
internal fun CatalogMuscleContribution(exercise: ExerciseMuscleInfo, modifier: Modifier = Modifier) {
    val contributions = remember(exercise) { oneSeriesVolumeContributions(exercise) }
    if (contributions.isEmpty()) return
    Column(modifier.testTag("catalog_muscle_contribution"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Aporte por 1 serie efectiva", style = KpknType.caption, color = WizardColors.textMuted)
        contributions.forEach { contribution ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contribution.muscle, style = KpknType.caption, color = WizardColors.text, modifier = Modifier.weight(1f))
                Text(
                    "${roleVolumeLabel(contribution.role).substringBefore(" · ")} · ${catalogEffectiveSetsLabel(contribution.seriesEquivalent)}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
