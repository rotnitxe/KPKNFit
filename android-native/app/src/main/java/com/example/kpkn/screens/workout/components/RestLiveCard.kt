package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.screens.workout.liveSessionCardHeight

/**
 * El descanso como tarjeta del paginador (`restCard` del Refinamiento 02): «DESCANSO» con expandir, el eclipse, −15 s · pausa · +15 s,
 * SIGUIENTE y el descanso sugerido al fondo; el pie es «Jugar mientras descansas», que abre los juegos dentro de la misma tarjeta.
 * La forma de RINGS, la fila de vídeo y la referencia del plan del diseño anterior ya no se pintan aquí.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun RestLiveCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    sessionAccentColor: Color,
    restState: WorkoutRestModalState?,
    pendingRestSuggestion: PendingRestSuggestion?,
    lastSetOutcome: SetOutcomeV2?,
    lastCompletedSet: CompletedSet?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    onUseAdaptive: (() -> Unit)?,
    onExpand: (() -> Unit)?,
    isAdaptiveActive: Boolean = false,
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RestClockShape = RestClockShape.TOWER,
    nextSet: RestNextSetSummary? = null,
    recordStore: RestGameRecordStore? = null,
    lastCompletedCustomUnit: String? = null,
    clockActive: Boolean = true,
    initiallyPlaying: Boolean = false,
    gameEngine: RestGameEngine? = null,
    videoSlot: (@Composable () -> Unit)? = null,
    isPaused: Boolean = false,
    onTogglePause: () -> Unit = {},
) {
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val safeTotal = totalSeconds.coerceAtLeast(safeRemaining).coerceAtLeast(1)
    val progress = RestClockMath.progress(safeTotal, safeRemaining)
    var playing by rememberSaveable(restState?.startedAtMs) { mutableStateOf(initiallyPlaying) }
    var suggestionOpen by remember { mutableStateOf(false) }
    val compact = liveSessionCardHeight() < 440.dp
    val kicker = when (restState?.kind) {
        RestTimerKind.SUPERSET_INTRA -> "DESCANSO · SUPERSERIE"
        RestTimerKind.SUPERSET_ROUND -> "DESCANSO · RONDA"
        RestTimerKind.WARMUP -> "DESCANSO · APROX."
        RestTimerKind.BETWEEN_SIDES -> "DESCANSO · LADOS"
        RestTimerKind.STANDARD, null -> "DESCANSO"
    }
    val suggestion = restSuggestionLine(
        plannedSeconds = restState?.plannedSeconds ?: 0,
        totalSeconds = safeTotal,
        suggestedSeconds = pendingRestSuggestion?.adaptiveSeconds?.takeIf { onUseAdaptive != null },
        adaptiveActive = isAdaptiveActive,
    )
    if (playing) {
        RestRef02Card(compact = compact, modifier = modifier, tag = "rest-card-games") {
            RestArcade(
                remainingSeconds = remainingSeconds, totalSeconds = safeTotal, nextSet = nextSet,
                onBack = { playing = false }, onSkip = onSkip, recordStore = recordStore, runner = gameEngine,
                running = clockActive && !isPaused, compact = true,
                isPaused = isPaused, onDecrease = onDecrease, onTogglePause = onTogglePause, onIncrease = onIncrease,
            )
        }
    } else {
        RestRef02Card(
            compact = compact,
            modifier = modifier,
            footer = {
                RestRef02Button("Jugar mientras descansas", { playing = true }, primary = false, modifier = Modifier.fillMaxWidth(),
                    icon = KpknLineIcons.Play, tag = "rest-play-games")
            },
        ) {
            RestRef02CardClockPanel(
                compact = compact,
                kicker = kicker,
                remainingSeconds = safeRemaining,
                progress = progress,
                isPaused = isPaused,
                animationsOn = clockActive,
                onExpand = onExpand,
                onDecrease = onDecrease,
                onTogglePause = onTogglePause,
                onIncrease = onIncrease,
                nextTitle = nextSet?.let { restRef02NextTitle(it, restState?.exerciseName.orEmpty(), card = true) },
                nextDose = nextSet?.let { if (it.cardio) it.durationSeconds?.let { s -> "${s / 60} min" } else it.doseText() },
                nextNote = if (nextSet?.cardio == true) "Al terminar el descanso pasas al espacio de cardio" else null,
                suggestion = suggestion,
                onOpenSuggestion = { suggestionOpen = true },
            )
        }
    }
    val pending = pendingRestSuggestion
    if (suggestionOpen && pending != null && onUseAdaptive != null) {
        RestSuggestionSheet(
            plannedSeconds = restState?.plannedSeconds?.takeIf { it > 0 } ?: pending.plannedSeconds,
            suggestedSeconds = pending.adaptiveSeconds,
            lastSetText = (lastCompletedSet ?: pending.lastSet).let { restRef02LastSetText(it, lastCompletedCustomUnit) },
            elapsedSeconds = safeTotal - safeRemaining,
            onApply = onUseAdaptive,
            onDismiss = { suggestionOpen = false },
        )
    }
}

/** Pista de la forma sobre el módulo de la tarjeta: un tono por encima del módulo para que se vea. */
internal val CardTrackColor = Color(0xFF242424)
