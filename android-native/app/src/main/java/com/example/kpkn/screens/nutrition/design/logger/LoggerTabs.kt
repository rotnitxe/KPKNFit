package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize

/**
 * Pestañas del registro, «Describir» y «Buscar», en un control segmentado de tamaño normal. Elegir una pestaña solo cambia lo que se
 * muestra debajo; el borrador se conserva.
 */
@Composable
fun LoggerTabs(
    activeTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknSegmentedControl(
        options = listOf("Describir", "Buscar"),
        selectedIndex = activeTab,
        onSelect = onTabSelect,
        modifier = modifier,
        size = KpknSegmentedSize.Regular,
    )
}
