package com.example.kpkn.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors

/**
 * Velo fijo bajo la barra de estado (I8, I12): negro pleno en todo el alto de la barra de estado y, debajo, 24 dp de degradado del negro
 * a transparente. Así lo que se desliza por arriba no se lee bajo la hora ni los iconos. No capta toques: el tacto pasa a la lista.
 *
 * Va por encima del contenido que se desplaza y por debajo de las islas de la pantalla (Inicio, Program Detail, Nutrición y Cuerpo).
 */
@Composable
fun KpknStatusVeil(modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(statusTop).background(WizardColors.background.copy(alpha = 1f)))
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Brush.verticalGradient(listOf(WizardColors.background, Color.Transparent))),
        )
    }
}
