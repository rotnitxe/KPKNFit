package com.example.kpkn.screens.nutrition.design.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.MealHistoryDay
import com.example.kpkn.screens.nutrition.MealHistoryMonth
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import java.util.Locale

/**
 * Un mes del historial (N5, NUTRITION_V1 §5): el nombre del mes como etiqueta de grupo (eyebrow en `textMuted`) y, debajo, un módulo con
 * una fila por día separada por filete. [index] escalona la entrada de cada mes. Es el mismo patrón que el historial de Cuerpo.
 */
@Composable
internal fun MealHistoryMonthModule(
    month: MealHistoryMonth,
    index: Int,
    onOpen: (MealHistoryDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp)
            .kpknEntrance("meal-history", index),
    ) {
        Text(
            text = month.label.uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        KpknModule(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            month.days.forEachIndexed { rowIndex, day ->
                if (rowIndex > 0) MealHistoryDivider()
                MealHistoryDayRow(day = day, onClick = { onOpen(day) })
            }
        }
    }
}

/** El filete de 1 dp entre dos días, con el mismo margen que las filas. */
@Composable
private fun MealHistoryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}
