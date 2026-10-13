package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.screens.sessioneditor.sessionCoverColors
import com.example.kpkn.ui.design.KpknSurface

/**
 * Franja de portada (SESSION_EDITOR_V1 §2, punto 2): 4 dp de alto a todo el ancho, bajo la fila superior, con el degradado o el sólido de
 * la portada de la sesión. Es decorativa: no la lee el lector de pantalla. La portada se elige en la hoja «Portada».
 */
@Composable
fun SessionEditorCoverStrip(
    background: SessionBackground?,
    modifier: Modifier = Modifier,
) {
    val colors = sessionCoverColors(background).ifEmpty { listOf(KpknSurface.inset) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Brush.horizontalGradient(colors))
            .clearAndSetSemantics { },
    ) {}
}
