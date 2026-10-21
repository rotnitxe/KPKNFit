package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/**
 * Mientras el programa no está cargado (P1): la fila superior con «Atrás» y dos módulos de esqueleto, para que la pantalla no salte al
 * llegar los datos. Solo cuando el repositorio ya leyó sus programas y el id no existe, dice «Programa no encontrado».
 */
@Composable
internal fun ProgramDetailPlaceholder(
    programsReady: Boolean,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Box(modifier = Modifier.fillMaxSize().background(KpknSurface.canvas)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            KpknTopRow(onBack = onBack)
            if (programsReady) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = "Programa no encontrado", style = KpknType.body, color = WizardColors.textMuted)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpknSkeletonModule(lines = 2)
                    KpknSkeletonModule(lines = 3)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) { KpknSnackbar(it) }
    }
}
