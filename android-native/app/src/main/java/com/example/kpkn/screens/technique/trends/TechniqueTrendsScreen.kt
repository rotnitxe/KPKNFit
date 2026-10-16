package com.example.kpkn.screens.technique.trends

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.trends.design.AnalyzedVideoRow
import com.example.kpkn.screens.technique.trends.design.LoadVelocityChart
import com.example.kpkn.screens.technique.trends.design.RomTrendChart
import com.example.kpkn.screens.technique.trends.design.TutChart
import com.example.kpkn.screens.technique.trends.design.VelocityLossChart
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

const val TECHNIQUE_TRENDS_SCREEN_TAG = "technique-trends-screen"
const val TECHNIQUE_TRENDS_COMPARE_BTN_TAG = "technique-trends-compare-btn"

/**
 * Pantalla de entrada para la navegación a «Tendencias por ejercicio» (VT9a).
 */
@Composable
fun TechniqueTrendsRoute(
    exerciseKey: String,
    onBack: () -> Unit,
    onOpenAnalysis: (mediaId: String) -> Unit,
    onCompare: (mediaA: String, mediaB: String) -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: TechniqueTrendsViewModel = viewModel(
        key = "technique-trends-$exerciseKey",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TechniqueTrendsViewModel(application, exerciseKey) as T
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TechniqueTrendsScreen(
        state = state,
        onBack = onBack,
        onOpenAnalysis = onOpenAnalysis,
        onCompare = onCompare,
        onToggleCompareMode = viewModel::toggleCompareMode,
        onSelectVideo = viewModel::toggleVideoSelection,
    )
}

/**
 * Pantalla completa de «Tendencias por ejercicio» (KPKN v2, Canvas).
 */
@Composable
fun TechniqueTrendsScreen(
    state: TechniqueTrendsUiState,
    onBack: () -> Unit,
    onOpenAnalysis: (mediaId: String) -> Unit,
    onCompare: (mediaA: String, mediaB: String) -> Unit,
    onToggleCompareMode: () -> Unit,
    onSelectVideo: (mediaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isCompareMode) {
        onToggleCompareMode()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KpknSurface.canvas)
            .testTag(TECHNIQUE_TRENDS_SCREEN_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Barra superior contextual
            KpknTopRow(
                onBack = onBack,
                actions = {
                    if (state.videos.size >= 2) {
                        KpknTextAction(
                            text = if (state.isCompareMode) "Cancelar" else "Comparar",
                            onClick = onToggleCompareMode,
                        )
                    }
                },
            )
            Text(
                text = state.exerciseName.ifBlank { "Tendencias" },
                style = KpknType.title,
                color = WizardColors.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (state.isEmpty) {
                // Estado vacío sin análisis
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KpknEmptyState(
                        icon = KpknLineIcons.TrendUp,
                        title = "Sin análisis de técnica",
                        body = "Graba o analiza series de este ejercicio para ver sus tendencias.",
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Módulo «Velocidad por carga»
                    state.loadVelocity?.let { lvState ->
                        KpknModule(modifier = Modifier.kpknEntrance("trends", 0)) {
                            KpknModuleHeader(label = "Velocidad por carga")
                            Spacer(modifier = Modifier.height(12.dp))
                            LoadVelocityChart(state = lvState)
                        }
                    }

                    // 2. Módulo «Pérdida de velocidad» (solo si hay datos de pérdida)
                    state.velocityLoss?.let { vlState ->
                        KpknModule(modifier = Modifier.kpknEntrance("trends", 1)) {
                            KpknModuleHeader(label = "Pérdida de velocidad")
                            Spacer(modifier = Modifier.height(12.dp))
                            VelocityLossChart(state = vlState)
                        }
                    }

                    // 3. Módulo «ROM y profundidad»
                    state.romTrend?.let { romState ->
                        KpknModule(modifier = Modifier.kpknEntrance("trends", 2)) {
                            KpknModuleHeader(label = "ROM y profundidad")
                            Spacer(modifier = Modifier.height(12.dp))
                            RomTrendChart(state = romState)
                        }
                    }

                    // 4. Módulo «TUT por serie»
                    state.tut?.let { tutState ->
                        KpknModule(modifier = Modifier.kpknEntrance("trends", 3)) {
                            KpknModuleHeader(label = "TUT por serie")
                            Spacer(modifier = Modifier.height(12.dp))
                            TutChart(state = tutState)
                        }
                    }

                    // 5. Módulo «Vídeos»
                    KpknModule(modifier = Modifier.kpknEntrance("trends", 4)) {
                        KpknModuleHeader(
                            label = "Vídeos",
                            action = {
                                if (state.isCompareMode && state.selectedMediaIds.isNotEmpty()) {
                                    Text(
                                        text = "${state.selectedMediaIds.size} de 2 elegidos",
                                        style = KpknType.caption,
                                        color = WizardColors.textMuted,
                                    )
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Column {
                            state.videos.forEachIndexed { index, videoItem ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(KpknSurface.divider),
                                    )
                                }
                                AnalyzedVideoRow(
                                    item = videoItem,
                                    isCompareMode = state.isCompareMode,
                                    onClick = {
                                        if (state.isCompareMode) {
                                            onSelectVideo(videoItem.mediaId)
                                        } else {
                                            onOpenAnalysis(videoItem.mediaId)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }

        // Barra inferior flotante cuando se eligen 2 vídeos para comparar
        if (state.isCompareMode && state.canLaunchCompare) {
            val list = state.selectedMediaIds.toList()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                KpknPrimaryPill(
                    text = "Comparar 2 vídeos",
                    onClick = { onCompare(list[0], list[1]) },
                    modifier = Modifier.testTag(TECHNIQUE_TRENDS_COMPARE_BTN_TAG),
                )
            }
        }
    }
}
