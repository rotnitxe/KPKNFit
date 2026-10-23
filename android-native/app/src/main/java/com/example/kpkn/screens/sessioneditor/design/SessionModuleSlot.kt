package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknSurface

/**
 * Un trozo de módulo de la lista (SESSION_EDITOR_R1 §3): margen de 16 dp, relleno `module` con su filete y, si no es el último trozo, una
 * línea de 1 dp al 6 % al pie. El último trozo deja 12 dp de aire debajo. Va dentro del ítem que pinta, así que se mueve con él al arrastrar.
 * Con [highlighted] el filete se vuelve de tinta y de 2 dp: es la señal de «aquí se va a soltar» (ED-A · A3).
 */
@Composable
internal fun SessionModuleSlot(
    edge: SessionModuleEdge,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = edge.bottomGap())
            .sessionModuleSurface(edge, highlighted),
    ) {
        content()
        if (edge == SessionModuleEdge.TOP || edge == SessionModuleEdge.MIDDLE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(KpknSurface.divider),
            )
        }
    }
}
