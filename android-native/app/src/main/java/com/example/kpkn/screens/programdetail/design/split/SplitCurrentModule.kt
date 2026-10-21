package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/**
 * «Tu reparto» (P5): el reparto que lleva la semana desde la que se abre la página, con su diagrama. Sin reparto elegido lo dice,
 * sin inventar uno. Sin estado.
 *
 * @param split el reparto actual, o null si el programa no tiene ninguno.
 * @param startDay día en que empieza la semana del programa.
 */
@Composable
internal fun SplitCurrentModule(
    split: SplitTemplate?,
    startDay: Int,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth().kpknEntrance("split-page", 0)) {
        KpknModuleHeader(label = "Tu reparto")
        Spacer(Modifier.height(10.dp))
        if (split == null) {
            Text(
                text = "Sin reparto elegido",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Elige uno del catálogo para repartir tus días de entreno.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        } else {
            Text(
                text = split.name,
                style = KpknType.title,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${splitDaysLabel(splitTrainingDays(split.pattern))} a la semana",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            SplitWeekDiagram(pattern = split.pattern, startDay = startDay)
            Spacer(Modifier.height(8.dp))
            SplitRoleLegend(pattern = split.pattern)
        }
    }
}
