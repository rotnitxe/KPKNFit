package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/**
 * Hoja «Comparar» (P5): los dos repartos uno encima del otro, cada uno con sus días por semana y su diagrama, y debajo qué cambia de
 * un día a otro. Sustituye al `CompareDialog` de siempre. Sin estado.
 */
@Composable
internal fun SplitCompareSheet(
    first: SplitTemplate,
    second: SplitTemplate,
    startDay: Int,
    onDismiss: () -> Unit,
) {
    val changed = splitChangedPositions(first.pattern, second.pattern)
    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("split_comparar"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Comparar repartos", style = KpknType.title, color = WizardColors.text)
            SplitCompareCard(split = first, startDay = startDay)
            SplitCompareCard(split = second, startDay = startDay)
            KpknModule {
                KpknModuleHeader(label = "Qué cambia")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = changeHeadline(changed.size),
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
                changed.forEach { position ->
                    Spacer(Modifier.height(6.dp))
                    val day = SPLIT_WEEKDAY_NAMES[splitWeekdayAt(startDay, position) - 1]
                    Text(
                        text = "$day: de ${splitLabelAt(first.pattern, position)} a ${splitLabelAt(second.pattern, position)}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Un reparto de la comparación: nombre, días por semana y su diagrama. */
@Composable
private fun SplitCompareCard(split: SplitTemplate, startDay: Int) {
    KpknModule {
        Text(text = split.name, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${splitDaysLabel(splitTrainingDays(split.pattern))} a la semana",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        SplitWeekDiagram(pattern = split.pattern, startDay = startDay)
    }
}

/** Titular de lo que cambia, en español y con el número exacto de días de la semana. */
private fun changeHeadline(changedDays: Int): String = when (changedDays) {
    0 -> "Los dos repartos ocupan la semana igual."
    1 -> "Cambia 1 día de la semana."
    else -> "Cambian $changedDays días de la semana."
}
