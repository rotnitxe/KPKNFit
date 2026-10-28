package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import java.time.YearMonth

/** Meses en español, con mayúscula inicial, para las filas de la lista. */
private val monthTitles = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
)

/** Título de la fila del resumen de [month]: «Octubre 2026». */
internal fun monthlySummaryRowTitle(month: YearMonth): String = "${monthTitles[month.monthValue - 1]} ${month.year}"

/**
 * Módulo «Resúmenes mensuales» de Progreso: una fila por mes con resumen (del más reciente al más antiguo); al tocarla se abre la pantalla
 * completa del resumen de ese mes. Sin meses no se pinta nada.
 */
@Composable
internal fun ProgramMonthlySummariesModule(months: List<YearMonth>, onOpen: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    if (months.isEmpty()) return
    KpknModule(modifier = modifier.testTag("progreso_resumenes")) {
        KpknModuleHeader(label = "Resúmenes mensuales")
        months.forEachIndexed { index, month ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = WizardColors.text.copy(alpha = 0.08f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("progreso_resumen_$month")
                    .clickable(role = Role.Button) { onOpen(month) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = KpknLineIcons.Calendar, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
                Spacer(Modifier.width(10.dp))
                Text(text = monthlySummaryRowTitle(month), style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f))
                Icon(imageVector = KpknLineIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
            }
        }
    }
}
