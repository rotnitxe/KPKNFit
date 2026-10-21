package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import com.example.kpkn.screens.workout.SetVideoAnalysisUi
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Sugerencia sin estado: toda aplicación requiere pulsar una acción explícita del usuario. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutVideoRepsSuggestion(
    value: SetVideoAnalysisUi,
    onUse: (SetVideoAnalysisState.Suggest) -> Unit,
    onCorrect: (SetVideoAnalysisState.Mismatch) -> Unit,
    onMarkFailure: () -> Unit,
    onOpenAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetryAnalysis: ((String) -> Unit)? = null,
) {
    val state = value.state
    val text = workoutVideoRepsText(state)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = KpknType.caption, color = if (state is SetVideoAnalysisState.Unsupported) WizardColors.textFaint else WizardColors.textMuted, modifier = Modifier.semantics { contentDescription = text })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                is SetVideoAnalysisState.Suggest -> {
                    if (state.confidence != CountConfidence.BAJA) {
                        if (state.reps > 0 || state.partials > 0) KpknTextAction(
                            if (state.confidence == CountConfidence.MEDIA) "Usar ≈ ${state.reps}" else "Usar",
                            onClick = { onUse(state) }, modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                        )
                        if (state.failed > 0 && state.confidence == CountConfidence.ALTA) KpknTextAction("Marcar fallo", onClick = onMarkFailure)
                    }
                }
                is SetVideoAnalysisState.Mismatch -> {
                    if (state.confidence != CountConfidence.BAJA) {
                        if (state.countsDiffer && (state.counted > 0 || state.partials > 0)) KpknTextAction(
                            if (state.confidence == CountConfidence.MEDIA) "Corregir a ≈ ${state.counted}" else "Corregir",
                            onClick = { onCorrect(state) },
                        )
                        if (state.failed > 0 && state.confidence == CountConfidence.ALTA) KpknTextAction("Marcar fallo", onClick = onMarkFailure)
                    }
                }
                else -> Unit
            }
            KpknTextAction("Ver análisis", onClick = { onOpenAnalysis(value.mediaId) },
                modifier = Modifier.semantics { contentDescription = "Ver análisis" })
            if (state is SetVideoAnalysisState.Failed && onRetryAnalysis != null) {
                KpknTextAction("Reintentar", onClick = { onRetryAnalysis(value.mediaId) })
            }
        }
    }
}

/** Texto compartido por la pieza, TalkBack y las regresiones de confianza. */
internal fun workoutVideoRepsText(state: SetVideoAnalysisState): String {
    if (state is SetVideoAnalysisState.Analyzing) {
        return "Analizando el vídeo… ${state.progress.coerceIn(0, 100)}\u00A0%"
    }
    val line = visualModeAnalysisLine(state, setIndex = 0)
    val description = line.actionLabel?.let { line.text.removeSuffix(" · $it") } ?: line.text
    val rom = (state as? SetVideoAnalysisState.Suggest)
        ?.takeIf { it.confidence != CountConfidence.BAJA }
        ?.romPercent?.let { " · ROM\u00A0≈\u00A0$it\u00A0%" }.orEmpty()
    return description + rom
}
