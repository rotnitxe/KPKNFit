package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknSurface

/**
 * Velo inferior de una columna con scroll (KPKN LS5b): mientras haya más contenido debajo, un degradado del negro al transparente de 28 dp
 * tapa el corte de la última fila, para que se lea como desplazamiento y no como un error. No captura toques y no guarda estado.
 */
fun Modifier.workoutBottomFade(scrollState: ScrollState): Modifier = drawWithContent {
    drawContent()
    if (scrollState.canScrollForward) {
        val fadeHeight = 28.dp.toPx()
        val top = size.height - fadeHeight
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, KpknSurface.canvas),
                startY = top,
                endY = size.height,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, fadeHeight),
        )
    }
}
