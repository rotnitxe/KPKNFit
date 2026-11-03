package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknSurface

/**
 * Un trozo de módulo de la lista (SESSION_EDITOR_R1 §3): margen de 16 dp, relleno `module` con su filete y, si no es el último trozo, una
 * línea de 1 dp al 6 % al pie. El último trozo deja 12 dp de aire debajo. Va dentro del ítem que pinta, así que se mueve con él al arrastrar.
 * Con [highlighted] el filete se vuelve de tinta y de 2 dp: es la señal de «aquí se va a soltar» (ED-A · A3).
 *
 * Pulido v1 · EDITOR · A2: la línea de entre filas (`.ed-exline`: 16 dp de margen, 1 dp, #ffffff0f) se dibuja SOBRE el pie de la fila y no
 * ocupa altura: el paso entre filas es el de la fila (68 dp o lo que mida), como en el lienzo. Con [dividerVisible] en falso (se arrastra una fila)
 * no se pinta, igual que `.ed-list.drag .ed-exline{opacity:0}`.
 */
@Composable
internal fun SessionModuleSlot(
    edge: SessionModuleEdge,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dividerVisible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasDivider = dividerVisible && (edge == SessionModuleEdge.TOP || edge == SessionModuleEdge.MIDDLE)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = edge.bottomGap())
            .sessionModuleSurface(edge, highlighted)
            .drawWithContent {
                drawContent()
                if (hasDivider) {
                    val inset = 16.dp.toPx()
                    val thickness = 1.dp.toPx()
                    drawRect(
                        color = KpknSurface.divider,
                        topLeft = Offset(inset, size.height - thickness),
                        size = Size(size.width - 2 * inset, thickness),
                    )
                }
            },
    ) {
        content()
    }
}
