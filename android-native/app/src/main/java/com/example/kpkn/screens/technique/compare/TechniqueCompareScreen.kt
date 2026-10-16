package com.example.kpkn.screens.technique.compare

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.analysis.AnalysisFormat
import com.example.kpkn.screens.technique.compare.design.OverlaidPathsChart
import com.example.kpkn.screens.technique.player.TechniqueVideoPlayer
import com.example.kpkn.screens.technique.player.rememberTechniquePlayerState
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import kotlin.math.max

const val TECHNIQUE_COMPARE_SCREEN_TAG = "technique-compare-screen"

/**
 * Pantalla de entrada para la navegación a «Comparar dos vídeos» (VT9a).
 */
@Composable
fun TechniqueCompareRoute(
    mediaIdA: String,
    mediaIdB: String,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: TechniqueCompareViewModel = viewModel(
        key = "technique-compare-$mediaIdA-$mediaIdB",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TechniqueCompareViewModel(application, mediaIdA, mediaIdB) as T
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TechniqueCompareScreen(
        state = state,
        onBack = onBack,
    )
}

/**
 * Pantalla de comparación de dos vídeos de técnica con reproductores sincronizados,
 * trayectorias de barra superpuestas y tabla de diferencias (KPKN v2).
 */
@Composable
fun TechniqueCompareScreen(
    state: TechniqueCompareUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .testTag(TECHNIQUE_COMPARE_SCREEN_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            KpknTopRow(onBack = onBack)
            Text(
                text = "Comparar vídeos",
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Cargando comparación…",
                            style = KpknType.body,
                            color = WizardColors.textMuted,
                        )
                    }
                }
                state.hasError -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        KpknEmptyState(
                            icon = KpknLineIcons.Info,
                            title = "No se pudieron comparar",
                            body = "No encontramos los archivos o análisis de los dos vídeos.",
                        )
                    }
                }
                else -> {
                    CompareContent(state = state)
                }
            }
        }
    }
}

@Composable
private fun CompareContent(state: TechniqueCompareUiState) {
    val scrollState = rememberScrollState()

    // Estados independientes de cada reproductor
    val playerStateA = rememberTechniquePlayerState(state.overlayA)
    val playerStateB = rememberTechniquePlayerState(state.overlayB)

    // Instantes de inicio de la primera repetición en cada vídeo
    val rep1StartUsA = remember(state.overlayA) {
        state.overlayA?.reps?.firstOrNull()?.startUs ?: 0L
    }
    val rep1StartUsB = remember(state.overlayB) {
        state.overlayB?.reps?.firstOrNull()?.startUs ?: 0L
    }

    val maxRelativeDurationUs = remember(playerStateA.durationUs, playerStateB.durationUs, rep1StartUsA, rep1StartUsB) {
        val durA = maxOf(0L, playerStateA.durationUs - rep1StartUsA)
        val durB = maxOf(0L, playerStateB.durationUs - rep1StartUsB)
        max(durA, durB).coerceAtLeast(1_000_000L)
    }

    var isPlayingBoth by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }

    // Resincronización «lo mejor posible»: cuando A pasa el límite de una repetición, B se recoloca
    LaunchedEffect(isPlayingBoth, playerStateA.positionUs) {
        if (isPlayingBoth && state.overlayA != null && state.overlayB != null) {
            val repsA = state.overlayA.reps
            val repsB = state.overlayB.reps
            val currentPosA = playerStateA.positionUs

            // Buscar en cuál repetición de A estamos
            val repIndexA = repsA.indexOfFirst { currentPosA in it.startUs..it.endUs }
            if (repIndexA in repsB.indices) {
                val repB = repsB[repIndexA]
                val repA = repsA[repIndexA]
                // Si B se ha desfasado significativamente (> 200 ms) respecto al progreso de A en esa rep:
                val progressInRepA = (currentPosA - repA.startUs).toFloat() / max(1L, repA.endUs - repA.startUs)
                val expectedPosB = (repB.startUs + progressInRepA * (repB.endUs - repB.startUs)).toLong()
                if (kotlin.math.abs(playerStateB.positionUs - expectedPosB) > 200_000L) {
                    playerStateB.seekToUs(expectedPosB)
                }
            }
        }
    }

    // Actualizar progreso relativo del deslizador desde el reloj de A
    LaunchedEffect(playerStateA.positionUs) {
        if (!playerStateA.isPlaying && !playerStateB.isPlaying && !isPlayingBoth) return@LaunchedEffect
        val relativeUs = maxOf(0L, playerStateA.positionUs - rep1StartUsA)
        scrubProgress = (relativeUs.toFloat() / maxRelativeDurationUs.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 1. Reproductores apilados con onTap = null ──────────────────────────────
        KpknModule(modifier = Modifier.kpknEntrance("compare", 0)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Vídeo A (arriba)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape),
                        )
                        Text(
                            text = "Vídeo A · ${state.labelA}",
                            style = KpknType.bodyStrong,
                            color = Color.White,
                        )
                    }

                    if (state.fileA != null) {
                        TechniqueVideoPlayer(
                            file = state.fileA,
                            overlay = state.overlayA,
                            state = playerStateA,
                            onTap = null,
                            description = "Vídeo A: ${state.labelA}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KpknSurface.inset),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "Vídeo A sin archivo", style = KpknType.caption, color = WizardColors.textMuted)
                        }
                    }
                }

                // Vídeo B (abajo)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SymbolPalette.columna, CircleShape),
                        )
                        Text(
                            text = "Vídeo B · ${state.labelB}",
                            style = KpknType.bodyStrong,
                            color = SymbolPalette.columna,
                        )
                    }

                    if (state.fileB != null) {
                        TechniqueVideoPlayer(
                            file = state.fileB,
                            overlay = state.overlayB,
                            state = playerStateB,
                            onTap = null,
                            description = "Vídeo B: ${state.labelB}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(KpknSurface.inset),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "Vídeo B sin archivo", style = KpknType.caption, color = WizardColors.textMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── 2. Deslizador y Play compartidos ─────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KpknIconButton(
                        icon = if (isPlayingBoth) KpknLineIcons.Close else KpknLineIcons.PlayFilled,
                        contentDescription = if (isPlayingBoth) "Pausar ambos vídeos" else "Reproducir ambos vídeos",
                        onClick = {
                            if (isPlayingBoth) {
                                playerStateA.pause()
                                playerStateB.pause()
                                isPlayingBoth = false
                            } else {
                                playerStateA.play()
                                playerStateB.play()
                                isPlayingBoth = true
                            }
                        },
                    )

                    Slider(
                        value = scrubProgress,
                        onValueChange = { frac ->
                            scrubProgress = frac
                            val targetRelativeUs = (frac * maxRelativeDurationUs).toLong()
                            playerStateA.seekToUs(rep1StartUsA + targetRelativeUs)
                            playerStateB.seekToUs(rep1StartUsB + targetRelativeUs)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )

                    val currentSec = ((scrubProgress * maxRelativeDurationUs) / 1_000_000L)
                    Text(
                        text = "${currentSec}s",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
        }

        // ── 3. Trayectorias superpuestas ─────────────────────────────────────────
        KpknModule(modifier = Modifier.kpknEntrance("compare", 1)) {
            KpknModuleHeader(label = "Recorrido de la barra")
            Spacer(modifier = Modifier.height(12.dp))
            OverlaidPathsChart(
                pathA = state.pathA,
                pathB = state.pathB,
                labelA = state.labelA,
                labelB = state.labelB,
                isCalibrated = state.isCalibrated,
            )
        }

        // ── 4. Tabla de diferencias ──────────────────────────────────────────────
        if (state.metricsTable.isNotEmpty()) {
            KpknModule(modifier = Modifier.kpknEntrance("compare", 2)) {
                KpknModuleHeader(label = "Comparativa de métricas")
                Spacer(modifier = Modifier.height(12.dp))

                Column {
                    // Cabecera de la tabla
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Métrica",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(1.5f),
                        )
                        Text(
                            text = "A",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(0.9f),
                        )
                        Text(
                            text = "B",
                            style = KpknType.caption,
                            color = SymbolPalette.columna,
                            modifier = Modifier.weight(0.9f),
                        )
                        Text(
                            text = "Δ",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.weight(0.8f),
                        )
                    }

                    state.metricsTable.forEachIndexed { index, row ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(KpknSurface.divider),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = row.label,
                                style = KpknType.bodyStrong,
                                color = Color.White,
                                modifier = Modifier.weight(1.5f),
                            )
                            Text(
                                text = row.valueA,
                                style = KpknType.body,
                                color = Color.White,
                                modifier = Modifier.weight(0.9f),
                            )
                            Text(
                                text = row.valueB,
                                style = KpknType.body,
                                color = SymbolPalette.columna,
                                modifier = Modifier.weight(0.9f),
                            )
                            Text(
                                text = row.diff,
                                style = KpknType.bodyStrong,
                                color = if (row.diff.startsWith("+")) WizardColors.done else Color.White,
                                modifier = Modifier.weight(0.8f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
