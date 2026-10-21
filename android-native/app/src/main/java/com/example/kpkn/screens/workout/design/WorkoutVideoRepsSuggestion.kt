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
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
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
) {
    val state = value.state
    val text = workoutVideoRepsText(state)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = KpknType.caption, color = if (state is SetVideoAnalysisState.Unsupported) WizardColors.textFaint else WizardColors.textMuted, modifier = Modifier.semantics { contentDescription = text })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                is SetVideoAnalysisState.Suggest -> {
                    if (state.confidence == CountConfidence.ALTA) {
                        if (state.reps > 0 || state.partials > 0) KpknTextAction("Usar", onClick = { onUse(state) }, modifier = Modifier.defaultMinSize(minWidth = 48.dp))
                        if (state.failed > 0) KpknTextAction("Marcar fallo", onClick = onMarkFailure)
                        KpknIconButton(icon = KpknLineIcons.ChevronRight, contentDescription = "Ver análisis", onClick = { onOpenAnalysis(value.mediaId) })
                    } else KpknTextAction("Revisar", onClick = { onOpenAnalysis(value.mediaId) })
                }
                is SetVideoAnalysisState.Mismatch -> {
                    if (state.confidence == CountConfidence.ALTA) {
                        if (state.countsDiffer && (state.counted > 0 || state.partials > 0)) KpknTextAction("Corregir", onClick = { onCorrect(state) })
                        if (state.failed > 0) KpknTextAction("Marcar fallo", onClick = onMarkFailure)
                        KpknIconButton(icon = KpknLineIcons.ChevronRight, contentDescription = "Ver análisis", onClick = { onOpenAnalysis(value.mediaId) })
                    } else KpknTextAction("Revisar", onClick = { onOpenAnalysis(value.mediaId) })
                }
                SetVideoAnalysisState.Inconclusive -> KpknTextAction("Ver análisis", onClick = { onOpenAnalysis(value.mediaId) })
                else -> Unit
            }
        }
    }
}

/** Texto compartido por la pieza, TalkBack y las regresiones de confianza. */
internal fun workoutVideoRepsText(state: SetVideoAnalysisState): String {
    fun extras(partials: Int, failed: Int): String = buildString {
        if (partials > 0) append(" · +$partials ${if (partials == 1) "parcial" else "parciales"}")
        if (failed > 0) append(" · $failed al fallo")
    }
    return when (state) {
        is SetVideoAnalysisState.Analyzing -> "Analizando el vídeo… ${state.progress} %"
        is SetVideoAnalysisState.Suggest -> when (state.confidence) {
            CountConfidence.ALTA -> if (state.reps == 0 && state.partials == 0 && state.failed > 0) "El vídeo muestra ${state.failed} ${if (state.failed == 1) "intento" else "intentos"} al fallo" else "El vídeo cuenta ${state.reps}" + extras(state.partials, state.failed) + (state.romPercent?.let { " · ROM ≈ $it %" } ?: "")
            CountConfidence.MEDIA -> "≈ ${state.reps} en el vídeo" + extras(state.partials, state.failed)
            CountConfidence.BAJA -> "Vídeo no concluyente"
        }
        is SetVideoAnalysisState.Mismatch -> when (state.confidence) {
            CountConfidence.ALTA -> if (state.countsDiffer && (state.counted > 0 || state.partials > 0)) {
                "Registraste ${state.logged}${if (state.loggedPartials > 0) " + ${state.loggedPartials} parciales" else ""}; el vídeo cuenta ${state.counted}" + extras(state.partials, state.failed)
            } else "El vídeo muestra ${state.failed} ${if (state.failed == 1) "intento" else "intentos"} al fallo"
            CountConfidence.MEDIA -> "≈ ${state.counted} en el vídeo" + extras(state.partials, state.failed)
            CountConfidence.BAJA -> "Vídeo no concluyente"
        }
        is SetVideoAnalysisState.Agrees -> "El vídeo confirma ${state.counted}"
        SetVideoAnalysisState.Inconclusive -> "Vídeo no concluyente"
        is SetVideoAnalysisState.Unsupported -> state.reasonText
    }
}
