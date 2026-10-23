package com.example.kpkn.screens.workout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlinx.coroutines.delay

internal const val ExerciseDrainOverlayDurationMillis = 1_650L

/** Transient feedback stays above the entire V1 island, including navigation/IME insets. */
@Composable
internal fun ExerciseDrainOverlay(
    state: ExerciseDrainOverlayState?,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
) {
    if (state == null) return
    key(state.key) {
        var visible by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(ExerciseDrainOverlayDurationMillis)
            visible = false
        }
        AnimatedVisibility(
            visible = visible,
            modifier = modifier.windowInsetsPadding(insets)
                .padding(start = 14.dp, end = 14.dp, bottom = bottomClearance + 8.dp),
            enter = fadeIn(tween(120)), exit = fadeOut(tween(220)),
        ) {
            ExerciseDrainOverlayCard(state, Modifier.fillMaxWidth().testTag("exercise-drain-overlay")
                .clickable(role = Role.Button, onClickLabel = "Cerrar drenaje") { visible = false })
        }
    }
}

enum class ExerciseDrainOverlayChannel {
    ENERGY,
    BACK,
    MUSCLE,
}

data class ExerciseDrainOverlayItem(
    val label: String,
    val delta: Int,
    val channel: ExerciseDrainOverlayChannel,
)

data class ExerciseDrainOverlayState(
    val key: Long,
    val exerciseName: String,
    val items: List<ExerciseDrainOverlayItem>,
)

/**
 * Tarjeta de gasto tras una serie (LR1c1): el velo opaco de siempre, con el filete de `module`. Título en eyebrow.
 */
@Composable
internal fun ExerciseDrainOverlayCard(
    state: ExerciseDrainOverlayState,
    modifier: Modifier = Modifier,
) {
    val shape = KpknShape.module
    // Nested under workout hazeSource — FallbackScrim DarkMica (no dead live Haze).
    Box(
        modifier = modifier
            .kpknGlassOrFallback(null, shape)
            .border(1.dp, KpknSurface.moduleBorder, shape),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Drenaje de ${state.exerciseName}".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                // Hasta dos líneas antes que cortar el nombre del ejercicio al 130 % (LR1c1).
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.items.forEachIndexed { index, item ->
                ExerciseDrainAnimatedRow(
                    item = item,
                    index = index,
                )
            }
        }
    }
}

@Composable
internal fun ExerciseDrainAnimatedRow(
    item: ExerciseDrainOverlayItem,
    index: Int,
) {
    var shouldDrain by remember(item.label, item.delta) { mutableStateOf(false) }
    val baseFraction = remember(item.delta) {
        (item.delta / 24f).coerceIn(0.16f, 1f)
    }
    val animatedFraction by animateFloatAsState(
        targetValue = if (shouldDrain) 0f else baseFraction,
        animationSpec = tween(durationMillis = 620, delayMillis = index * 45),
        label = "exercise-drain-${item.label}",
    )
    // Los colores de los RINGS (LR1c1): Energía, Columna y Músculos.
    val accent = when (item.channel) {
        ExerciseDrainOverlayChannel.ENERGY -> SymbolPalette.energia
        ExerciseDrainOverlayChannel.BACK -> SymbolPalette.columna
        ExerciseDrainOverlayChannel.MUSCLE -> SymbolPalette.musculo
    }

    LaunchedEffect(item.label, item.delta) {
        shouldDrain = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            // Cifra en Syne con cifras tabulares, espacio inseparable antes del % y signo menos tipográfico.
            Text(
                text = "−${item.delta} %",
                style = KpknType.metricM,
                color = accent,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(KpknShape.pill)
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .background(accent),
            )
        }
    }
}
