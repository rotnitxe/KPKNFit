package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize

/**
 * Día en que empieza la semana del reparto (P5): siete segmentos compactos, una letra por día, con el lunes primero. Sustituye al menú
 * desplegable `WeekStartMenu`. [startDay] va de 1 (lunes) a 7 (domingo). Sin estado.
 */
@Composable
internal fun SplitStartDayControl(
    startDay: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknSegmentedControl(
        options = SPLIT_WEEKDAY_LETTERS,
        selectedIndex = startDay.coerceIn(1, 7) - 1,
        onSelect = { index -> onSelect(index + 1) },
        modifier = modifier,
        size = KpknSegmentedSize.Compact,
    )
}
