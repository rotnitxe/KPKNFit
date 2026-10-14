package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSkeleton
import com.example.kpkn.ui.design.KpknType
import kotlinx.coroutines.delay

/** Pista del análisis que aparece solo si tarda más de este tiempo: un cálculo rápido no parpadea con texto. */
private const val AnalysisHintDelayMillis = 600L

/**
 * Carga del asistente (SE4): dos bloques de esqueleto. Si el análisis tarda más de 600 ms, debajo sale «Calculando análisis…» en `caption`.
 */
@Composable
fun SessionAssistantLoading(modifier: Modifier = Modifier) {
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(AnalysisHintDelayMillis)
        showHint = true
    }
    Column(modifier = modifier.fillMaxWidth()) {
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(96.dp))
        Spacer(Modifier.height(12.dp))
        KpknSkeleton(modifier = Modifier.fillMaxWidth().height(72.dp))
        if (showHint) {
            Text(
                text = "Calculando análisis…",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
